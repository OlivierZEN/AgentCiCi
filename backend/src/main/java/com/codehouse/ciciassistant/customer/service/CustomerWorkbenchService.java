package com.codehouse.ciciassistant.customer.service;

import com.codehouse.ciciassistant.agent.service.AgentDefinitionService;
import com.codehouse.ciciassistant.ai.service.ChatOrchestratorService;
import com.codehouse.ciciassistant.cloudcc.CloudccOpenApiService;
import com.codehouse.ciciassistant.cloudcc.CloudccOpenApiService.CloudccApiException;
import com.codehouse.ciciassistant.cloudcc.CloudccOpenApiService.WriteResult;
import com.codehouse.ciciassistant.common.error.ConflictException;
import com.codehouse.ciciassistant.customer.domain.CustomerCrmWriteAuditEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerCrmWriteAuditRepository;
import com.codehouse.ciciassistant.customer.domain.CustomerInteractionEventEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerInteractionEventRepository;
import com.codehouse.ciciassistant.customer.domain.CustomerRecommendationFeedbackEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerRecommendationFeedbackRepository;
import com.codehouse.ciciassistant.customer.domain.CustomerWorkbenchRecommendationEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerWorkbenchRecommendationRepository;
import com.codehouse.ciciassistant.customer.domain.CustomerWorkbenchSnapshotEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerWorkbenchSnapshotRepository;
import com.codehouse.ciciassistant.integration.service.CloudccAccessTokenService;
import com.codehouse.ciciassistant.skill.service.SkillDefinitionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CustomerWorkbenchService {

    public static final String SKILL_CODE = "customer-interaction-workbench";
    public static final String ASSISTANT_AGENT_ID = "cici-system";

    private static final TypeReference<Map<String, Object>> MAP_REF = new TypeReference<>() {};

    private final CustomerWorkbenchSnapshotRepository snapshotRepository;
    private final CustomerInteractionEventRepository eventRepository;
    private final CustomerWorkbenchRecommendationRepository recommendationRepository;
    private final CustomerRecommendationFeedbackRepository recommendationFeedbackRepository;
    private final CustomerCrmWriteAuditRepository writeAuditRepository;
    private final CustomerCrmProjectionService crmProjectionService;
    private final CloudccOpenApiService cloudccOpenApiService;
    private final CloudccAccessTokenService cloudccAccessTokenService;
    private final SkillDefinitionService skillDefinitionService;
    private final AgentDefinitionService agentDefinitionService;
    private final ChatOrchestratorService chatOrchestratorService;
    private final ObjectMapper objectMapper;

    public CustomerWorkbenchService(CustomerWorkbenchSnapshotRepository snapshotRepository,
                                    CustomerInteractionEventRepository eventRepository,
                                    CustomerWorkbenchRecommendationRepository recommendationRepository,
                                    CustomerRecommendationFeedbackRepository recommendationFeedbackRepository,
                                    CustomerCrmWriteAuditRepository writeAuditRepository,
                                    CustomerCrmProjectionService crmProjectionService,
                                    CloudccOpenApiService cloudccOpenApiService,
                                    CloudccAccessTokenService cloudccAccessTokenService,
                                    SkillDefinitionService skillDefinitionService,
                                    AgentDefinitionService agentDefinitionService,
                                    ChatOrchestratorService chatOrchestratorService,
                                    ObjectMapper objectMapper) {
        this.snapshotRepository = snapshotRepository;
        this.eventRepository = eventRepository;
        this.recommendationRepository = recommendationRepository;
        this.recommendationFeedbackRepository = recommendationFeedbackRepository;
        this.writeAuditRepository = writeAuditRepository;
        this.crmProjectionService = crmProjectionService;
        this.cloudccOpenApiService = cloudccOpenApiService;
        this.cloudccAccessTokenService = cloudccAccessTokenService;
        this.skillDefinitionService = skillDefinitionService;
        this.agentDefinitionService = agentDefinitionService;
        this.chatOrchestratorService = chatOrchestratorService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> queue(String orgId, String userId, CustomerCrmProjectionService.QueueQuery query) {
        if (isCrmReady(orgId, userId)) {
            return crmProjectionService.queue(orgId, userId, query);
        }
        ensureDemoData(orgId, userId);
        List<Map<String, Object>> modeItems = snapshotRepository.findByOrgIdOrderByUpdatedAtDesc(orgId).stream()
                .map(item -> accountListView(orgId, item)).toList();
        if ("new".equalsIgnoreCase(query.mode())) {
            modeItems = modeItems.stream().filter(item -> List.of("NEW", "RISK").contains(String.valueOf(item.get("segment")))).toList();
        } else if ("existing".equalsIgnoreCase(query.mode())) {
            modeItems = modeItems.stream().filter(item -> List.of("EXISTING", "STRATEGIC").contains(String.valueOf(item.get("segment")))).toList();
        }
        Map<String, Long> counts = mapOfLong(
                "all", modeItems.size(),
                "focus", modeItems.stream().filter(item -> intValue(item.get("progressScore")) >= 70).count(),
                "follow", modeItems.stream().filter(item -> intValue(item.get("nextActionCount")) > 0).count(),
                "risk", modeItems.stream().filter(item -> intValue(item.get("riskCount")) > 0).count(),
                "recommendations", modeItems.stream().filter(item -> intValue(item.get("pendingRecommendationCount")) > 0).count(),
                "renewal", modeItems.stream().filter(item -> String.valueOf(item.get("stage")).contains("续约")).count(),
                "health", modeItems.stream().filter(item -> intValue(item.get("healthScore")) < 75).count(),
                "service", modeItems.stream().filter(item -> intValue(item.get("riskCount")) > 0).count(),
                "expansion", modeItems.stream().filter(item -> String.valueOf(item.get("stage")).contains("增购")).count());
        String needle = blankToEmpty(query.query()).toLowerCase(Locale.ROOT);
        List<Map<String, Object>> filtered = modeItems.stream()
                .filter(item -> demoFilterMatches(item, query.filter()))
                .filter(item -> needle.isBlank() || (item.get("name") + " " + item.get("owner") + " " + item.get("stage"))
                        .toLowerCase(Locale.ROOT).contains(needle))
                .toList();
        int size = Math.max(1, Math.min(100, query.size()));
        int page = Math.max(1, query.page());
        int from = Math.min(filtered.size(), (page - 1) * size);
        int to = Math.min(filtered.size(), from + size);
        int totalPages = filtered.isEmpty() ? 0 : (int) Math.ceil((double) filtered.size() / size);
        return mapOf("items", filtered.subList(from, to), "page", page, "size", size,
                "totalElements", filtered.size(), "totalPages", totalPages, "filterCounts", counts,
                "source", "DEMO_FALLBACK", "mode", query.mode());
    }

    public List<Map<String, Object>> listAccounts(String orgId, String userId) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) queue(orgId, userId,
                new CustomerCrmProjectionService.QueueQuery("all", "all", "priority", "desc", "", 1, 100, false))
                .get("items");
        return items;
    }

    @Transactional
    public Map<String, Object> accountDetail(String orgId, String userId, String accountId) {
        if (isCrmReady(orgId, userId)) {
            Map<String, Object> view = new LinkedHashMap<>(crmProjectionService.detail(orgId, userId, accountId, false));
            ensureLiveRecommendations(orgId, accountId, view);
            view.put("recommendations", recommendations(orgId, userId, accountId));
            return view;
        }
        ensureDemoData(orgId, userId);
        CustomerWorkbenchSnapshotEntity snapshot = requireSnapshot(orgId, accountId);
        Map<String, Object> view = new LinkedHashMap<>(snapshotView(snapshot));
        view.put("timeline", timeline(orgId, userId, accountId));
        view.put("recommendations", recommendations(orgId, userId, accountId));
        view.put("crmConnection", crmConnectionView(orgId, userId));
        view.put("source", "DEMO_FALLBACK");
        return view;
    }

    public List<Map<String, Object>> timeline(String orgId, String userId, String accountId) {
        if (isCrmReady(orgId, userId)) {
            return crmProjectionService.timeline(orgId, userId, accountId, false);
        }
        return eventRepository.findByOrgIdAndCrmAccountIdOrderByOccurredAtDesc(orgId, accountId).stream()
                .map(this::eventView).toList();
    }

    public List<Map<String, String>> assistantHistory(String orgId, String userId, String accountId) {
        assertAccountAccess(orgId, userId, accountId);
        try {
            return chatOrchestratorService.sessionMessages(orgId, userId, assistantSessionId(userId, accountId)).stream()
                    .map(item -> Map.of(
                            "role", item.getOrDefault("role", "assistant"),
                            "content", sanitizeAssistantHistoryContent(item.getOrDefault("content", "")),
                            "createdAt", item.getOrDefault("createdAt", "")))
                    .filter(item -> !item.get("content").isBlank())
                    .toList();
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().value() == 404) return List.of();
            throw ex;
        }
    }

    @Transactional
    public Map<String, Object> saveInteraction(String orgId, String userId, String accountId, InteractionCommand command) {
        assertAccountAccess(orgId, userId, accountId);
        String sourceType = normalizeInteractionSource(command == null ? "" : command.sourceType());
        String content = command == null ? "" : blankToEmpty(command.content());
        if (content.length() < 10 || content.length() > 10_000) {
            throw new IllegalArgumentException("互动内容长度需在 10 到 10000 个字符之间");
        }
        String subject = command == null ? "" : blankToEmpty(command.subject());
        if (subject.isBlank()) subject = switch (sourceType) {
            case "WECHAT" -> "微信沟通记录";
            case "PHONE" -> "电话沟通记录";
            case "MEETING" -> "客户会议记录";
            default -> "客户反馈记录";
        };
        if (subject.length() > 256) subject = subject.substring(0, 256);
        Instant occurredAt = parseInteractionTime(command == null ? null : command.occurredAt());
        String publicId = "cwi_" + sha256(orgId + ":" + accountId + ":" + sourceType + ":" + content).substring(0, 40);
        var existing = eventRepository.findByOrgIdAndPublicId(orgId, publicId);
        if (existing.isPresent()) {
            Map<String, Object> view = new LinkedHashMap<>(eventView(existing.get()));
            view.put("deduplicated", true);
            return view;
        }
        Map<String, Object> account = isCrmReady(orgId, userId)
                ? crmProjectionService.detail(orgId, userId, accountId, false)
                : snapshotView(requireSnapshot(orgId, accountId));
        String lifecycle = "EXISTING".equals(account.get("customerMode")) ? "EXISTING_CUSTOMER" : "NEW_CUSTOMER";
        List<String> tags = extractInteractionTags(content);
        CustomerInteractionEventEntity saved = eventRepository.save(new CustomerInteractionEventEntity(
                publicId, orgId, accountId, "", sourceType, occurredAt, subject, content,
                summarizeInteraction(content), content.contains("投诉") || content.contains("风险") ? "NEGATIVE" : "NEUTRAL",
                toJson(tags), lifecycle));
        Map<String, Object> view = new LinkedHashMap<>(eventView(saved));
        view.put("deduplicated", false);
        return view;
    }

    @Transactional
    public List<Map<String, Object>> recommendations(String orgId, String userId, String accountId) {
        assertAccountAccess(orgId, userId, accountId);
        return recommendationRepository.findByOrgIdAndCrmAccountIdOrderByUpdatedAtDesc(orgId, accountId).stream()
                .map(item -> recommendationView(item, userId))
                .toList();
    }

    @Transactional
    public Map<String, Object> recommendationFeedback(String orgId, String userId, String publicId,
                                                      RecommendationFeedbackCommand command) {
        CustomerWorkbenchRecommendationEntity recommendation = requireRecommendation(orgId, publicId);
        assertRecommendationAccess(orgId, userId, recommendation);
        String rating = command == null ? "" : blankToEmpty(command.rating()).toUpperCase(Locale.ROOT);
        if (!List.of("HELPFUL", "NOT_HELPFUL").contains(rating)) {
            throw new IllegalArgumentException("建议反馈仅支持有帮助或需改进");
        }
        String comment = command == null ? "" : blankToEmpty(command.comment());
        if (comment.length() > 1000) throw new IllegalArgumentException("反馈说明不能超过 1000 个字符");
        CustomerRecommendationFeedbackEntity feedback = recommendationFeedbackRepository
                .findByOrgIdAndUserIdAndRecommendationId(orgId, userId, publicId)
                .orElseGet(() -> new CustomerRecommendationFeedbackEntity(orgId, userId, publicId, rating, comment));
        feedback.update(rating, comment);
        feedback = recommendationFeedbackRepository.save(feedback);
        return mapOf("recommendationId", publicId, "rating", feedback.getRating(),
                "comment", feedback.getCommentText(), "updatedAt", feedback.getUpdatedAt().toString());
    }

    public Map<String, Object> supervisorSummary(String orgId, String userId) {
        List<Map<String, Object>> accounts = new ArrayList<>();
        int page = 1;
        int totalPages;
        do {
            Map<String, Object> result = queue(orgId, userId,
                    new CustomerCrmProjectionService.QueueQuery("all", "all", "risk", "desc", "", page, 100, false));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) result.getOrDefault("items", List.of());
            accounts.addAll(items);
            totalPages = intValue(result.get("totalPages"));
            page++;
        } while (page <= totalPages);
        Set<String> visibleAccountIds = accounts.stream().map(item -> String.valueOf(item.get("accountId")))
                .collect(java.util.stream.Collectors.toSet());
        List<CustomerWorkbenchRecommendationEntity> recommendations = recommendationRepository.findByOrgIdOrderByUpdatedAtDesc(orgId).stream()
                .filter(item -> visibleAccountIds.contains(item.getCrmAccountId())).toList();
        List<CustomerCrmWriteAuditEntity> audits = writeAuditRepository.findByOrgIdAndUserIdOrderByCreatedAtDesc(orgId, userId);
        long writeSucceeded = audits.stream().filter(item -> "SUCCEEDED".equals(item.getStatus())).count();
        long writeFailed = audits.stream().filter(item -> List.of("FAILED", "UNKNOWN").contains(item.getStatus())).count();
        long completedWrites = writeSucceeded + writeFailed;
        return mapOf(
                "visibleAccounts", accounts.size(),
                "riskAccounts", accounts.stream().filter(item -> intValue(item.get("riskCount")) > 0).count(),
                "followedAccounts", accounts.stream().filter(item -> Boolean.parseBoolean(String.valueOf(item.get("followed")))).count(),
                "openActions", accounts.stream().mapToInt(item -> intValue(item.get("nextActionCount"))).sum(),
                "pendingRecommendations", recommendations.stream().filter(item -> List.of(
                        CustomerWorkbenchRecommendationEntity.STATUS_PENDING,
                        CustomerWorkbenchRecommendationEntity.STATUS_ACCEPTED,
                        CustomerWorkbenchRecommendationEntity.STATUS_CONFIRMED).contains(item.getStatus())).count(),
                "writeSucceeded", writeSucceeded,
                "writeFailed", writeFailed,
                "writeSuccessRate", completedWrites == 0 ? 0 : Math.round(writeSucceeded * 1000D / completedWrites) / 10D,
                "dataAsOf", Instant.now().toString());
    }

    @Transactional
    public Map<String, Object> acceptRecommendation(String orgId, String userId, String publicId) {
        CustomerWorkbenchRecommendationEntity recommendation = requireRecommendation(orgId, publicId);
        assertRecommendationAccess(orgId, userId, recommendation);
        try {
            recommendation.accept();
        } catch (IllegalStateException ex) {
            throw new ConflictException(ex.getMessage());
        }
        return recommendationView(recommendationRepository.save(recommendation));
    }

    @Transactional
    public Map<String, Object> updateRecommendation(String orgId, String userId, String publicId, RecommendationDraft command) {
        CustomerWorkbenchRecommendationEntity recommendation = requireRecommendation(orgId, publicId);
        assertRecommendationAccess(orgId, userId, recommendation);
        Map<String, Object> payload = command == null || command.crmPayload() == null
                ? readMap(recommendation.getCrmPayload()) : command.crmPayload();
        try {
            recommendation.updateDraft(
                    command == null || blankToEmpty(command.title()).isBlank() ? recommendation.getTitle() : command.title().trim(),
                    command == null || blankToEmpty(command.rationale()).isBlank() ? recommendation.getRationale() : command.rationale().trim(),
                    command == null || command.confidence() == null ? recommendation.getConfidence() : command.confidence(),
                    toJson(payload),
                    command == null || blankToEmpty(command.targetObject()).isBlank() ? recommendation.getTargetObject() : command.targetObject().trim(),
                    command == null || blankToEmpty(command.targetRecordId()).isBlank() ? recommendation.getTargetRecordId() : command.targetRecordId().trim());
        } catch (IllegalStateException ex) {
            throw new ConflictException(ex.getMessage());
        }
        return recommendationView(recommendationRepository.save(recommendation));
    }

    @Transactional
    public Map<String, Object> dismissRecommendation(String orgId, String userId, String publicId, RecommendationDismiss command) {
        CustomerWorkbenchRecommendationEntity recommendation = requireRecommendation(orgId, publicId);
        assertRecommendationAccess(orgId, userId, recommendation);
        try {
            recommendation.dismiss(command == null || blankToEmpty(command.reason()).isBlank() ? "用户选择忽略" : command.reason().trim());
        } catch (IllegalStateException ex) {
            throw new ConflictException(ex.getMessage());
        }
        return recommendationView(recommendationRepository.save(recommendation));
    }

    @Transactional
    public Map<String, Object> confirmRecommendation(String orgId, String userId, String publicId) {
        CustomerWorkbenchRecommendationEntity recommendation = requireRecommendation(orgId, publicId);
        assertRecommendationAccess(orgId, userId, recommendation);
        try {
            recommendation.confirm(userId);
        } catch (IllegalStateException ex) {
            throw new ConflictException(ex.getMessage());
        }
        return recommendationView(recommendationRepository.save(recommendation));
    }

    public Map<String, Object> applyRecommendation(String orgId, String userId, String publicId) {
        CustomerWorkbenchRecommendationEntity recommendation = requireRecommendation(orgId, publicId);
        assertRecommendationAccess(orgId, userId, recommendation);
        if (CustomerWorkbenchRecommendationEntity.STATUS_APPLIED.equals(recommendation.getStatus())) {
            Map<String, Object> existing = recommendationView(recommendation);
            existing.put("message", "该建议已执行，未重复写入 CRM。");
            existing.put("idempotent", true);
            return existing;
        }
        if (!isCrmReady(orgId, userId)) throw new ConflictException("CloudCC CRM 未连接，禁止执行写回");
        String targetObject = firstNonBlank(recommendation.getTargetObject(), targetObject(recommendation.getRecommendationType()));
        Map<String, Object> crmPayload = normalizedCrmPayload(recommendation, targetObject);
        String operation = blankToEmpty(recommendation.getTargetRecordId()).isBlank() ? "INSERT" : "UPDATE";
        if ("UPDATE".equals(operation)) crmPayload.put("id", recommendation.getTargetRecordId());
        String requestHash = sha256(toJson(crmPayload));
        String idempotencyKey = recommendation.getPublicId() + ":" + requestHash.substring(0, 16);
        var prior = writeAuditRepository.findByOrgIdAndUserIdAndIdempotencyKey(orgId, userId, idempotencyKey);
        if (prior.isPresent() && "SUCCEEDED".equals(prior.get().getStatus())) {
            recommendation.apply(prior.get().getRemoteRecordId());
            Map<String, Object> view = recommendationView(recommendationRepository.save(recommendation));
            view.put("message", "已根据幂等审计恢复此前的 CRM 写入结果。");
            view.put("idempotent", true);
            return view;
        }
        if (prior.isPresent() && !blankToEmpty(prior.get().getRemoteRecordId()).isBlank()) {
            CustomerCrmWriteAuditEntity audit = prior.get();
            String remoteId = audit.getRemoteRecordId();
            Map<String, Object> readback = cloudccOpenApiService.queryRecordById(orgId, userId, targetObject,
                    readbackFields(targetObject), remoteId).orElse(Map.of());
            audit.markSucceeded(remoteId, toJson(mapOf("recovered", true, "readback", readback)));
            writeAuditRepository.save(audit);
            recommendation.apply(remoteId);
            crmProjectionService.invalidate(orgId, userId);
            Map<String, Object> view = recommendationView(recommendationRepository.save(recommendation));
            view.put("writeMode", "CLOUDCC_LIVE");
            view.put("verified", !readback.isEmpty());
            view.put("readback", readback);
            view.put("message", "已根据审计中的 CRM 记录 ID 恢复写入结果，未重复创建记录。");
            view.put("idempotent", true);
            return view;
        }
        if (prior.isPresent() && ("STARTED".equals(prior.get().getStatus()) || "UNKNOWN".equals(prior.get().getStatus()))) {
            throw new ConflictException("上次 CRM 写入结果未知，已禁止重复执行；请先在 CRM 中核对或修改建议后重试");
        }
        if (!CustomerWorkbenchRecommendationEntity.STATUS_CONFIRMED.equals(recommendation.getStatus())
                && !CustomerWorkbenchRecommendationEntity.STATUS_FAILED.equals(recommendation.getStatus())
                && !CustomerWorkbenchRecommendationEntity.STATUS_APPLYING.equals(recommendation.getStatus())) {
            throw new ConflictException("建议必须先确认，才能写入 CRM");
        }
        CustomerCrmWriteAuditEntity audit = prior.isPresent() ? prior.get() : new CustomerCrmWriteAuditEntity(
                auditId(orgId, idempotencyKey), orgId, userId, recommendation.getPublicId(), idempotencyKey,
                targetObject, operation, "STARTED", requestHash, null, null, null, toJson(crmPayload), "{}");
        audit.markStarted();
        audit = writeAuditRepository.save(audit);
        recommendation.markApplying();
        recommendation = recommendationRepository.save(recommendation);
        WriteResult result;
        String remoteId;
        Map<String, Object> readback;
        try {
            result = cloudccOpenApiService.writeRecords(orgId, userId, operation, targetObject, List.of(crmPayload));
            remoteId = result.remoteIds().stream().findFirst().orElse(blankToEmpty(recommendation.getTargetRecordId()));
            if (remoteId.isBlank()) throw new IllegalStateException("CloudCC 写入成功但未返回记录 ID，无法完成回读校验");
            audit.markSucceeded(remoteId, toJson(mapOf("result", result)));
            readback = cloudccOpenApiService.queryRecordById(orgId, userId, targetObject,
                    readbackFields(targetObject), remoteId).orElse(Map.of());
        } catch (RuntimeException ex) {
            String code = ex instanceof CloudccApiException cloudccEx ? cloudccEx.code() : ex.getClass().getSimpleName();
            recommendation.markFailed(code, ex.getMessage());
            boolean resultUnknown = ex.getMessage() != null && (ex.getMessage().contains("timed out")
                    || ex.getMessage().contains("timeout") || ex.getMessage().contains("连接重置"));
            audit.markFailed(code, ex.getMessage(), resultUnknown);
            writeAuditRepository.save(audit);
            Map<String, Object> view = recommendationView(recommendationRepository.save(recommendation));
            view.put("writeMode", "CLOUDCC_LIVE");
            view.put("message", "CRM 写入失败：" + ex.getMessage());
            return view;
        }
        boolean verified = !readback.isEmpty();
        audit.markSucceeded(remoteId, toJson(mapOf("result", result, "readback", readback)));
        writeAuditRepository.save(audit);
        recommendation.apply(remoteId);
        crmProjectionService.invalidate(orgId, userId);
        Map<String, Object> view = recommendationView(recommendationRepository.save(recommendation));
        view.put("writeMode", "CLOUDCC_LIVE");
        view.put("verified", verified);
        view.put("readback", readback);
        view.put("message", verified ? "已写入 CloudCC CRM 并完成回读校验。" : "已写入 CloudCC CRM，但当前用户无权回读该记录。");
        return view;
    }

    public Map<String, Object> assistant(String orgId, String userId, AssistantCommand command) {
        agentDefinitionService.warmupBuiltinAgents(orgId);
        skillDefinitionService.ensurePhaseOneDefaults(orgId);
        String text = command == null || command.message() == null ? "" : command.message().trim();
        String accountId = command == null ? "" : blankToEmpty(command.accountId());
        if (accountId.isBlank()) {
            List<Map<String, Object>> accounts = listAccounts(orgId, userId);
            if (accounts.isEmpty()) throw new IllegalArgumentException("当前用户没有可见客户");
            accountId = String.valueOf(accounts.get(0).get("accountId"));
        }
        Map<String, Object> customer = accountDetail(orgId, userId, accountId);
        Map<String, Object> crmConnection = crmConnectionView(orgId, userId);
        String sessionId = assistantSessionId(userId, accountId);
        String prompt = buildAssistantPrompt(userId, text, customer, crmConnection);
        Map<String, Object> agentResult = chatOrchestratorService.chat(
                orgId,
                userId,
                sessionId,
                prompt,
                List.of(),
                ASSISTANT_AGENT_ID,
                SKILL_CODE,
                Map.of("source", "customer-workbench", "crmAccountId", accountId)
        );
        Object answer = agentResult.get("answer");
        Map<String, Object> uiAction = resolveUiAction(text, customer);
        return mapOf(
                "reply", answer == null || String.valueOf(answer).isBlank() ? "智能体暂未生成有效回复，请重试。" : String.valueOf(answer),
                "action", uiAction.get("type"),
                "actionPayload", uiAction.get("payload"),
                "uiActions", List.of(uiAction),
                "account", customer,
                "crmConnection", crmConnection,
                "agentId", agentResult.getOrDefault("agentId", ASSISTANT_AGENT_ID),
                "sessionId", agentResult.getOrDefault("sessionId", sessionId),
                "runId", agentResult.getOrDefault("runId", ""),
                "model", agentResult.getOrDefault("model", Map.of()),
                "resolvedSkills", agentResult.getOrDefault("resolvedSkills", List.of()),
                "activeSkillCode", agentResult.getOrDefault("activeSkillCode", SKILL_CODE)
        );
    }

    private String assistantSessionId(String userId, String accountId) {
        String seed = blankToEmpty(userId) + ":" + blankToEmpty(accountId);
        return "customer-workbench:" + UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    private String sanitizeAssistantHistoryContent(String content) {
        int marker = content.lastIndexOf("[用户问题]");
        return marker >= 0 ? content.substring(marker + "[用户问题]".length()).trim() : content.trim();
    }

    private String normalizeInteractionSource(String sourceType) {
        return switch (blankToEmpty(sourceType).toUpperCase(Locale.ROOT)) {
            case "WECHAT", "PHONE", "MEETING", "CUSTOMER_FEEDBACK" -> blankToEmpty(sourceType).toUpperCase(Locale.ROOT);
            default -> throw new IllegalArgumentException("互动来源仅支持微信、电话、会议或客户反馈");
        };
    }

    private Instant parseInteractionTime(String raw) {
        if (raw == null || raw.isBlank()) return Instant.now();
        try { return Instant.parse(raw); }
        catch (DateTimeParseException ex) { throw new IllegalArgumentException("互动时间格式不正确"); }
    }

    private String summarizeInteraction(String content) {
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 240 ? normalized : normalized.substring(0, 237) + "...";
    }

    private List<String> extractInteractionTags(String content) {
        List<String> tags = new ArrayList<>();
        if (content.contains("预算") || content.contains("报价")) tags.add("预算商务");
        if (content.contains("需求") || content.contains("方案")) tags.add("需求方案");
        if (content.contains("风险") || content.contains("投诉")) tags.add("风险反馈");
        if (content.contains("续约") || content.contains("增购")) tags.add("续约增购");
        if (tags.isEmpty()) tags.add("客户互动");
        return List.copyOf(tags);
    }

    private String buildAssistantPrompt(String userId,
                                        String userMessage,
                                        Map<String, Object> customer,
                                        Map<String, Object> crmConnection) {
        Object recentTimeline = customer.getOrDefault("timeline", List.of());
        Object pendingRecommendations = customer.getOrDefault("recommendations", List.of());
        return """
                [客户互动工作台上下文]
                当前用户：%s
                当前客户 CRM Account Id：%s
                当前客户快照 JSON：%s
                最近互动 JSON：%s
                CRM 落地建议 JSON：%s
                CRM 连接状态 JSON：%s

                [回答要求]
                1. 你必须以客户互动工作台 AI 客户助理身份回答。
                2. 只能基于上方工作台上下文、已授权 CRM 查询工具或用户输入回答；缺少事实时写“待确认”，不要编造。
                3. 输出中文，结构紧凑，优先覆盖事实、推断、风险/机会、下一步行动、待确认项。
                4. 涉及 CRM 写回、价格承诺、合同解释、服务责任归因、关键人判断或客户敏感信息外发时，只能形成建议并要求用户确认。
                5. 如果用户要求生成跟进任务、整理微信记录、查看风险或分析新/老客户经营，请直接给出可落地建议，不要说自己只是规则助手。

                [用户问题]
                %s
                """.formatted(
                userId,
                customer.get("accountId"),
                toJson(customer),
                toJson(recentTimeline),
                toJson(pendingRecommendations),
                toJson(crmConnection),
                userMessage == null || userMessage.isBlank() ? "请根据当前客户互动上下文给出推进建议。" : userMessage);
    }

    public Map<String, Object> integrationStatus(String orgId, String userId) {
        if (!isCrmReady(orgId, userId)) return crmConnectionView(orgId, userId);
        Map<String, Object> status = new LinkedHashMap<>(crmProjectionService.integrationStatus(orgId, userId));
        cloudccAccessTokenService.getSessionContext(orgId, userId).ifPresent(context -> status.put("baseUrl", context.baseUrl()));
        return status;
    }

    public Map<String, Object> follow(String orgId, String userId, String accountId, boolean followed) {
        if (!isCrmReady(orgId, userId)) throw new ConflictException("CloudCC CRM 未连接，无法保存关注状态");
        return crmProjectionService.follow(orgId, userId, accountId, followed);
    }

    public List<Map<String, Object>> notifications(String orgId, String userId) {
        return isCrmReady(orgId, userId) ? crmProjectionService.notifications(orgId, userId) : List.of();
    }

    private boolean isCrmReady(String orgId, String userId) {
        return cloudccAccessTokenService.getSessionContext(orgId, userId).isPresent();
    }

    private void assertRecommendationAccess(String orgId, String userId,
                                            CustomerWorkbenchRecommendationEntity recommendation) {
        assertAccountAccess(orgId, userId, recommendation.getCrmAccountId());
    }

    private void assertAccountAccess(String orgId, String userId, String accountId) {
        if (isCrmReady(orgId, userId)) {
            crmProjectionService.detail(orgId, userId, accountId, false);
            return;
        }
        requireSnapshot(orgId, accountId);
    }

    @Transactional
    protected void ensureLiveRecommendations(String orgId, String accountId, Map<String, Object> detail) {
        if (!recommendationRepository.findByOrgIdAndCrmAccountIdOrderByUpdatedAtDesc(orgId, accountId).isEmpty()) return;
        String name = String.valueOf(detail.getOrDefault("name", "客户"));
        List<?> signals = detail.get("signals") instanceof List<?> list ? list : List.of();
        Object firstSignalDetail = signals.isEmpty() || !(signals.get(0) instanceof Map<?, ?> firstSignal)
                ? null : firstSignal.get("detail");
        String rationale = firstSignalDetail == null ? "当前客户没有明确的下一步 CRM 任务，建议由负责人确认跟进安排。"
                : String.valueOf(firstSignalDetail);
        CustomerWorkbenchRecommendationEntity task = new CustomerWorkbenchRecommendationEntity(
                stableRecommendationId(orgId, accountId, "CREATE_TASK"), orgId, accountId, "CREATE_TASK",
                "创建下一次跟进任务", rationale, BigDecimal.valueOf(0.88),
                toJson(mapOf("subject", "跟进 " + name, "relateid", accountId, "relateobj", "Account",
                        "status", "未开始", "priority", "普通", "expiredate", LocalDate.now().plusDays(3).toString(),
                        "remark", rationale)));
        task.configureTarget("Task", "", toJson(signals.stream().limit(3).toList()));
        recommendationRepository.save(task);
        Object opportunityCount = detail.getOrDefault("opportunityCount", 0);
        if (Integer.parseInt(String.valueOf(opportunityCount)) == 0) {
            CustomerWorkbenchRecommendationEntity opportunity = new CustomerWorkbenchRecommendationEntity(
                    stableRecommendationId(orgId, accountId, "CREATE_OPPORTUNITY"), orgId, accountId, "CREATE_OPPORTUNITY",
                    "创建业务机会并补齐推进信息", "当前客户没有可见业务机会，建议确认名称、阶段和下一步后写入 CRM。",
                    BigDecimal.valueOf(0.76), toJson(mapOf("name", name + " 新业务机会", "khmc", accountId,
                            "jieduan", "1-发现机会", "xyb", "确认需求、预算和关键决策人")));
            opportunity.configureTarget("Opportunity", "", "[]");
            recommendationRepository.save(opportunity);
        }
    }

    private String stableRecommendationId(String orgId, String accountId, String type) {
        String seed = orgId + ":" + accountId + ":" + type;
        return "cwr_" + UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
    }

    private String targetObject(String type) {
        return switch (blankToEmpty(type).toUpperCase(Locale.ROOT)) {
            case "CREATE_OPPORTUNITY", "UPDATE_OPPORTUNITY" -> "Opportunity";
            case "UPDATE_CASE" -> "cloudcccase";
            default -> "Task";
        };
    }

    private Map<String, Object> normalizedCrmPayload(CustomerWorkbenchRecommendationEntity recommendation, String targetObject) {
        Map<String, Object> source = readMap(recommendation.getCrmPayload());
        source.remove("objectApiName");
        source.remove("operation");
        source.remove("accountId");
        if ("Task".equals(targetObject)) {
            source.putIfAbsent("subject", recommendation.getTitle());
            source.putIfAbsent("relateid", recommendation.getCrmAccountId());
            source.putIfAbsent("relateobj", "Account");
            source.putIfAbsent("status", "未开始");
            source.putIfAbsent("priority", "普通");
            source.putIfAbsent("expiredate", LocalDate.now().plusDays(3).toString());
            source.putIfAbsent("remark", recommendation.getRationale());
        } else if ("Opportunity".equals(targetObject)) {
            source.putIfAbsent("khmc", recommendation.getCrmAccountId());
            source.putIfAbsent("name", recommendation.getTitle());
            source.putIfAbsent("jieduan", "1-发现机会");
            source.putIfAbsent("xyb", recommendation.getRationale());
        }
        return new LinkedHashMap<>(source);
    }

    private String readbackFields(String targetObject) {
        return switch (targetObject) {
            case "Opportunity" -> "id,name,khmc,jieduan,xyb,lastmodifydate";
            case "cloudcccase" -> "id,name,khmc,zhuangtai,yxj,zhuti,lastmodifydate";
            default -> "id,name,subject,relateid,status,priority,expiredate,remark,lastmodifydate";
        };
    }

    private String auditId(String orgId, String idempotencyKey) {
        return "cwa_" + UUID.nameUUIDFromBytes((orgId + ":" + idempotencyKey).getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
    }

    private String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("无法生成写入幂等摘要", ex);
        }
    }

    private Map<String, Object> resolveUiAction(String text, Map<String, Object> customer) {
        String input = blankToEmpty(text);
        if (input.contains("老客户") || input.contains("客户经营"))
            return mapOf("type", "SWITCH_MODE", "payload", Map.of("mode", "existing"), "requiresConfirmation", false);
        if (input.contains("新客户") || input.contains("客户推进"))
            return mapOf("type", "SWITCH_MODE", "payload", Map.of("mode", "new"), "requiresConfirmation", false);
        if (input.contains("下一个客户"))
            return mapOf("type", "SELECT_NEXT_ACCOUNT", "payload", Map.of(), "requiresConfirmation", false);
        if (input.contains("建议") || input.contains("落地"))
            return mapOf("type", "OPEN_TAB", "payload", Map.of("tab", "recommendations"), "requiresConfirmation", false);
        if (input.contains("风险") || input.contains("信号"))
            return mapOf("type", "OPEN_TAB", "payload", Map.of("tab", "signals"), "requiresConfirmation", false);
        if (input.contains("任务") || input.contains("写入"))
            return mapOf("type", "PROPOSE_RECOMMENDATION", "payload", Map.of("accountId", customer.get("accountId"),
                    "recommendationType", "CREATE_TASK"), "requiresConfirmation", true);
        return mapOf("type", "NONE", "payload", Map.of(), "requiresConfirmation", false);
    }

    @Transactional
    public Map<String, Object> seedDemoData(String orgId, String userId, boolean reset) {
        if (reset) {
            recommendationRepository.deleteAll(recommendationRepository.findAll().stream()
                    .filter(item -> orgId.equals(item.getOrgId()))
                    .toList());
            eventRepository.deleteAll(eventRepository.findAll().stream()
                    .filter(item -> orgId.equals(item.getOrgId()))
                    .toList());
            snapshotRepository.deleteAll(snapshotRepository.findAll().stream()
                    .filter(item -> orgId.equals(item.getOrgId()))
                    .toList());
        }
        ensureDemoData(orgId, userId);
        return mapOf(
                "accounts", snapshotRepository.countByOrgId(orgId),
                "events", eventRepository.countByOrgId(orgId),
                "recommendations", recommendationRepository.countByOrgId(orgId)
        );
    }

    private void ensureDemoData(String orgId, String userId) {
        skillDefinitionService.ensurePhaseOneDefaults(orgId);
        if (snapshotRepository.countByOrgId(orgId) > 0) {
            return;
        }
        List<DemoAccount> accounts = demoAccounts();
        String publicIdPrefix = publicIdPrefix(orgId);
        for (DemoAccount account : accounts) {
            snapshotRepository.save(new CustomerWorkbenchSnapshotEntity(
                    publicIdPrefix + "_cw_" + account.id(),
                    orgId,
                    account.id(),
                    account.name(),
                    account.owner(),
                    account.segment(),
                    account.health(),
                    account.progress(),
                    account.risks().size(),
                    account.nextActions().size(),
                    toJson(mapOf(
                            "industry", account.industry(),
                            "contact", account.contact(),
                            "lastInteraction", account.lastInteraction(),
                            "stage", account.stage(),
                            "summary", account.summary(),
                            "risks", account.risks(),
                            "newCustomerSignals", account.newSignals(),
                            "existingCustomerSignals", account.existingSignals(),
                            "nextActions", account.nextActions(),
                            "tags", account.tags()
                    ))
            ));
            int i = 0;
            for (String interaction : account.interactions()) {
                i++;
                eventRepository.save(new CustomerInteractionEventEntity(
                        publicIdPrefix + "_cwe_" + account.id() + "_" + i,
                        orgId,
                        account.id(),
                        "contact-" + account.id(),
                        i % 3 == 0 ? "MEETING" : (i % 3 == 1 ? "WECHAT" : "PHONE"),
                        Instant.parse("2026-07-0" + Math.min(6, i + 1) + "T0" + Math.min(9, i + 1) + ":30:00Z"),
                        account.name() + "互动摘要 " + i,
                        interaction,
                        interaction,
                        account.segment().equals("RISK") ? "NEGATIVE" : "NEUTRAL",
                        toJson(account.tags()),
                        account.segment().equals("NEW") ? "NEW_CUSTOMER" : (account.segment().equals("EXISTING") ? "EXISTING_CUSTOMER" : "MIXED")
                ));
            }
            seedRecommendation(publicIdPrefix, orgId, account.id(), "CREATE_TASK", "创建下一次跟进任务",
                    "最近互动中出现明确待办，建议写入 CRM 任务并设置截止时间。",
                    0.91, mapOf("objectApiName", "Task", "subject", "跟进 " + account.name(), "accountId", account.id()));
            seedRecommendation(publicIdPrefix, orgId, account.id(), account.segment().equals("NEW") ? "CREATE_OPPORTUNITY" : "UPDATE_RISK",
                    account.segment().equals("NEW") ? "创建商机推进记录" : "更新客户经营风险",
                    account.segment().equals("NEW") ? "客户已出现预算、需求或决策链信号。" : "客户服务或续约信号需要主管关注。",
                    0.82, mapOf("accountId", account.id(), "source", "customer-interaction-workbench"));
        }
    }

    private void seedRecommendation(String publicIdPrefix,
                                    String orgId,
                                    String accountId,
                                    String type,
                                    String title,
                                    String rationale,
                                    double confidence,
                                    Map<String, Object> payload) {
        recommendationRepository.save(new CustomerWorkbenchRecommendationEntity(
                publicIdPrefix + "_cwr_" + accountId + "_" + type.toLowerCase(Locale.ROOT),
                orgId,
                accountId,
                type,
                title,
                rationale,
                BigDecimal.valueOf(confidence),
                toJson(payload)
        ));
    }

    private String publicIdPrefix(String orgId) {
        String normalized = orgId == null ? "" : orgId.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return "org_unknown";
        }
        return "org_" + (normalized.length() > 12 ? normalized.substring(0, 12) : normalized);
    }

    private Map<String, Object> accountListView(String orgId, CustomerWorkbenchSnapshotEntity item) {
        Map<String, Object> snapshot = readMap(item.getSnapshotJson());
        boolean existing = List.of("EXISTING", "STRATEGIC").contains(item.getSegment());
        return mapOf(
                "accountId", item.getCrmAccountId(),
                "name", item.getAccountName(),
                "owner", item.getOwnerName(),
                "segment", item.getSegment(),
                "customerMode", existing ? "EXISTING" : "NEW",
                "healthScore", item.getHealthScore(),
                "progressScore", item.getProgressScore(),
                "riskCount", item.getRiskCount(),
                "nextActionCount", item.getNextActionCount(),
                "pendingRecommendationCount", recommendationRepository.countByOrgIdAndCrmAccountIdAndStatus(
                        orgId, item.getCrmAccountId(), CustomerWorkbenchRecommendationEntity.STATUS_PENDING),
                "opportunityCount", existing ? 0 : 1,
                "renewalDays", String.valueOf(snapshot.getOrDefault("stage", "")).contains("续约") ? 60 : -1,
                "lastInteraction", snapshot.getOrDefault("lastInteraction", ""),
                "stage", snapshot.getOrDefault("stage", ""),
                "tags", snapshot.getOrDefault("tags", List.of()),
                "updatedAt", item.getUpdatedAt().toString()
        );
    }

    private Map<String, Object> snapshotView(CustomerWorkbenchSnapshotEntity item) {
        Map<String, Object> snapshot = readMap(item.getSnapshotJson());
        boolean existing = List.of("EXISTING", "STRATEGIC").contains(item.getSegment());
        List<String> risks = stringList(snapshot.get("risks"));
        List<String> newSignals = stringList(snapshot.get("newCustomerSignals"));
        List<String> existingSignals = stringList(snapshot.get("existingCustomerSignals"));
        List<String> nextActions = stringList(snapshot.get("nextActions"));
        int renewalDays = String.valueOf(snapshot.getOrDefault("stage", "")).contains("续约") ? 60 : -1;
        List<Map<String, Object>> signals = demoSignals(existing ? "EXISTING" : "NEW", risks,
                existing ? existingSignals : newSignals);
        String contact = String.valueOf(snapshot.getOrDefault("contact", ""));
        String[] contactParts = contact.trim().split("\\s+", 2);
        snapshot.putAll(mapOf(
                "accountId", item.getCrmAccountId(),
                "name", item.getAccountName(),
                "owner", item.getOwnerName(),
                "segment", item.getSegment(),
                "customerMode", existing ? "EXISTING" : "NEW",
                "healthScore", item.getHealthScore(),
                "progressScore", item.getProgressScore(),
                "riskCount", item.getRiskCount(),
                "nextActionCount", item.getNextActionCount(),
                "pendingRecommendationCount", recommendationRepository.countByOrgIdAndCrmAccountIdAndStatus(
                        item.getOrgId(), item.getCrmAccountId(), CustomerWorkbenchRecommendationEntity.STATUS_PENDING),
                "opportunityCount", existing ? 0 : 1,
                "renewalDays", renewalDays,
                "signals", signals,
                "metrics", mapOf(
                        "health", demoMetric(item.getHealthScore(), "客户健康度演示规则", "DEMO", "overview", item.getUpdatedAt()),
                        "risks", demoMetric(risks.size(), "未关闭风险信号", "DEMO", existing ? "service" : "signals", item.getUpdatedAt()),
                        "nextActions", demoMetric(nextActions.size(), "待执行行动", "DEMO", "actions", item.getUpdatedAt()),
                        "renewalDays", demoMetric(renewalDays, "最近合同到期日", "DEMO", "renewal", item.getUpdatedAt()),
                        "openIssues", demoMetric(risks.size(), "演示服务风险", "DEMO", "service", item.getUpdatedAt()),
                        "expansionSignals", demoMetric(existingSignals.stream().filter(value -> value.contains("增购") || value.contains("扩展")).count(), "增购信号", "DEMO", "renewal", item.getUpdatedAt())
                ),
                "serviceIssues", risks.stream().map(value -> mapOf("id", stableDemoId(item, value), "number", "演示服务项",
                        "title", value, "status", "待处理", "priority", "中", "dueAt", "", "description", value)).toList(),
                "valueItems", existingSignals.stream().map(value -> mapOf("id", stableDemoId(item, value), "title", value,
                        "status", "待复盘", "amount", 0, "source", "演示客户经营事实")).toList(),
                "relationshipMap", contact.isBlank() ? List.of() : List.of(mapOf("id", "demo-contact-" + item.getCrmAccountId(),
                        "name", contactParts[0], "title", contactParts.length > 1 ? contactParts[1] : "职务待补",
                        "role", "关键联系人", "owner", item.getOwnerName(), "lastContactAt", item.getUpdatedAt().toString())),
                "renewal", mapOf("days", renewalDays,
                        "contracts", renewalDays < 0 ? List.of() : List.of(mapOf("id", "demo-contract-" + item.getCrmAccountId(),
                                "title", item.getAccountName() + " 续约合同", "status", "续约准备", "amount", 0, "source", "演示合同")),
                        "opportunities", existingSignals.stream().filter(value -> value.contains("增购") || value.contains("扩展"))
                                .map(value -> mapOf("name", value, "stage", "机会培育", "nextStep", "确认范围、预算和负责人")).toList()),
                "opportunities", existing ? List.of() : newSignals.stream().limit(1)
                        .map(value -> mapOf("id", "demo-opportunity-" + item.getCrmAccountId(), "name", value,
                                "stage", snapshot.getOrDefault("stage", "机会培育"), "nextStep", nextActions.isEmpty() ? "待确认" : nextActions.get(0))).toList(),
                "dataAsOf", item.getUpdatedAt().toString()
        ));
        return snapshot;
    }

    private List<Map<String, Object>> demoSignals(String mode, List<String> risks, List<String> facts) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < risks.size(); i++) {
            out.add(mapOf("mode", mode, "type", "DEMO_RISK_" + i, "title", risks.get(i), "detail", risks.get(i),
                    "severity", "HIGH", "evidence", List.of("演示客户互动事实")));
        }
        for (int i = 0; i < facts.size(); i++) {
            out.add(mapOf("mode", mode, "type", "DEMO_SIGNAL_" + i, "title", facts.get(i), "detail", facts.get(i),
                    "severity", "MEDIUM", "evidence", List.of("演示客户互动事实")));
        }
        return List.copyOf(out);
    }

    private Map<String, Object> demoMetric(Number value, String definition, String source, String target, Instant calculatedAt) {
        return mapOf("value", value, "definition", definition, "source", source,
                "lastCalculatedAt", calculatedAt.toString(), "drilldownTarget", target);
    }

    private String stableDemoId(CustomerWorkbenchSnapshotEntity item, String value) {
        return "demo_" + UUID.nameUUIDFromBytes((item.getCrmAccountId() + ":" + value).getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(String::valueOf).filter(item -> !item.isBlank()).toList();
    }

    private Map<String, Object> eventView(CustomerInteractionEventEntity item) {
        return mapOf(
                "eventId", item.getPublicId(),
                "accountId", item.getCrmAccountId(),
                "sourceType", item.getSourceType(),
                "occurredAt", item.getOccurredAt().toString(),
                "subject", item.getSubject(),
                "summary", item.getAiSummary(),
                "sentiment", item.getSentiment(),
                "intentTags", readList(item.getIntentTags()),
                "lifecycleArea", item.getLifecycleArea()
        );
    }

    private Map<String, Object> recommendationView(CustomerWorkbenchRecommendationEntity item) {
        Object evidence = readJson(item.getEvidenceJson());
        if (!(evidence instanceof List<?> list) || list.isEmpty()) {
            evidence = List.of(mapOf("title", "建议生成依据", "detail", item.getRationale(), "source", "推荐规则"));
        }
        return mapOf(
                "recommendationId", item.getPublicId(),
                "accountId", item.getCrmAccountId(),
                "type", item.getRecommendationType(),
                "title", item.getTitle(),
                "rationale", item.getRationale(),
                "confidence", item.getConfidence(),
                "status", item.getStatus(),
                "crmPayload", readMap(item.getCrmPayload()),
                "appliedCrmId", item.getAppliedCrmId(),
                "targetObject", item.getTargetObject(),
                "targetRecordId", item.getTargetRecordId(),
                "evidence", evidence,
                "dismissalReason", item.getDismissalReason(),
                "confirmedBy", item.getConfirmedBy(),
                "confirmedAt", item.getConfirmedAt() == null ? "" : item.getConfirmedAt().toString(),
                "appliedAt", item.getAppliedAt() == null ? "" : item.getAppliedAt().toString(),
                "lastErrorCode", item.getLastErrorCode(),
                "lastErrorMessage", item.getLastErrorMessage(),
                "version", item.getVersion(),
                "updatedAt", item.getUpdatedAt().toString()
        );
    }

    private Map<String, Object> recommendationView(CustomerWorkbenchRecommendationEntity item, String userId) {
        Map<String, Object> view = new LinkedHashMap<>(recommendationView(item));
        recommendationFeedbackRepository.findByOrgIdAndUserIdAndRecommendationId(item.getOrgId(), userId, item.getPublicId())
                .ifPresent(feedback -> view.put("feedback", mapOf("rating", feedback.getRating(),
                        "comment", feedback.getCommentText(), "updatedAt", feedback.getUpdatedAt().toString())));
        return view;
    }

    private Map<String, Object> crmConnectionView(String orgId, String userId) {
        boolean ready = cloudccAccessTokenService.getSessionContext(orgId, userId).isPresent();
        return mapOf(
                "ready", ready,
                "mode", ready ? "BOUND" : "DEMO",
                "label", ready ? "CloudCC CRM 已连接" : "演示模式"
        );
    }

    private CustomerWorkbenchSnapshotEntity requireSnapshot(String orgId, String accountId) {
        return snapshotRepository.findByOrgIdAndCrmAccountId(orgId, accountId)
                .orElseThrow(() -> new IllegalArgumentException("客户不存在"));
    }

    private CustomerWorkbenchRecommendationEntity requireRecommendation(String orgId, String publicId) {
        return recommendationRepository.findByOrgIdAndPublicId(orgId, publicId)
                .orElseThrow(() -> new IllegalArgumentException("建议不存在"));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("JSON 序列化失败", ex);
        }
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, MAP_REF);
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    private List<Object> readList(String json) {
        Object value = readJson(json);
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return List.of();
    }

    private Object readJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private String joinList(Object value) {
        if (value instanceof List<?> list && !list.isEmpty()) {
            return String.join("；", list.stream().map(String::valueOf).toList());
        }
        return "暂无明确信号";
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "";
    }

    private boolean demoFilterMatches(Map<String, Object> item, String filter) {
        return switch (blankToEmpty(filter)) {
            case "focus" -> intValue(item.get("progressScore")) >= 70;
            case "follow" -> intValue(item.get("nextActionCount")) > 0;
            case "risk", "service" -> intValue(item.get("riskCount")) > 0;
            case "recommendations" -> intValue(item.get("pendingRecommendationCount")) > 0;
            case "health" -> intValue(item.get("healthScore")) < 75;
            case "renewal" -> String.valueOf(item.get("stage")).contains("续约");
            case "expansion" -> String.valueOf(item.get("stage")).contains("增购");
            default -> true;
        };
    }

    private static int intValue(Object value) {
        try { return Integer.parseInt(String.valueOf(value)); }
        catch (Exception ignored) { return 0; }
    }

    private static Map<String, Long> mapOfLong(Object... values) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) map.put(String.valueOf(values[i]), ((Number) values[i + 1]).longValue());
        return map;
    }

    private static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }

    private List<DemoAccount> demoAccounts() {
        return List.of(
                demo("demo-account-001", "北京智造科技有限公司", "NEW", "张伟", "制造业", "李娜 技术负责人", "方案评审中", 72, 86,
                        "最近三次沟通围绕 MES 集成、实施周期和预算窗口展开。",
                        List.of("决策链未完全确认", "预算审批窗口较紧"),
                        List.of("MES 集成需求明确", "方案评审会已约定", "预算窗口在 7 月中旬"),
                        List.of("暂无存量经营信号"),
                        List.of("约技术评审复盘", "补齐决策链联系人", "创建商机产品明细"),
                        List.of("重点推进", "方案沟通", "预算窗口"),
                        List.of("微信沟通确认 MES 集成是首要关注点。", "电话回访中客户询问实施周期和费用拆分。", "方案评审会约定由技术和采购共同参与。")),
                demo("demo-account-002", "上海云链信息技术有限公司", "RISK", "李娜", "软件服务", "陈峰 客户经理", "服务风险中", 48, 58,
                        "客户连续反馈响应慢，存在续约延期风险。",
                        List.of("续约窗口临近", "服务响应满意度下降", "关键人态度转弱"),
                        List.of("有增购数据治理模块兴趣"),
                        List.of("续约窗口 45 天内", "服务问题需闭环", "可用客户成功拜访挽回"),
                        List.of("安排客户成功主管回访", "创建服务风险", "准备续约价值复盘"),
                        List.of("续约风险", "服务反馈", "主管关注"),
                        List.of("客户反馈上周问题未及时回复。", "电话中提到续约暂缓，需要先看到整改计划。", "售后会议记录显示数据同步问题已复现。")),
                demo("demo-account-003", "广州海创智联有限公司", "EXISTING", "王磊", "装备制造", "周倩 信息化总监", "增购识别", 82, 67,
                        "一期系统稳定运行，客户开始讨论售后服务和移动端扩展。",
                        List.of("移动端预算尚未确认"),
                        List.of("售后场景有扩展机会"),
                        List.of("使用满意度较高", "增购意向明确", "关键人关系稳定"),
                        List.of("准备增购方案", "邀请客户参加成功案例交流"),
                        List.of("健康客户", "增购机会", "关系稳定"),
                        List.of("微信中客户询问移动端巡检能力。", "季度回访确认一期上线效果稳定。", "客户希望看到同行案例。")),
                demo("demo-account-004", "深圳未来视界科技有限公司", "NEW", "陈晨", "高科技", "刘洋 采购经理", "竞品比较", 64, 74,
                        "客户正在比较两家竞品，重点关注总拥有成本和权限体系。",
                        List.of("竞品方案仍在评估", "采购只看价格缺少业务价值材料"),
                        List.of("权限治理需求强", "已有明确采购角色"),
                        List.of("暂无存量经营信号"),
                        List.of("补充 TCO 对比", "安排权限治理演示", "确认业务决策人"),
                        List.of("竞品对比", "权限关注", "待演示"),
                        List.of("客户要求补充权限体系说明。", "电话中明确正在比较竞品报价。", "采购希望本周拿到 TCO 对比。")),
                demo("demo-account-005", "杭州数智动力有限公司", "RISK", "刘洋", "数据服务", "郭敏 运营负责人", "响应放缓", 55, 52,
                        "最近两周互动频率明显下降，原计划试点被客户内部项目挤压。",
                        List.of("连续三次触达未回复", "试点优先级下降"),
                        List.of("数据质量痛点仍存在"),
                        List.of("老客户扩展动力不足"),
                        List.of("换用价值复盘话术", "请求主管协助触达", "降低试点启动门槛"),
                        List.of("响应放缓", "试点受阻", "需主管协助"),
                        List.of("微信消息两天未回复。", "电话中助理表示负责人在忙内部项目。", "上次会议确认数据质量问题仍未解决。")),
                demo("demo-account-006", "成都智云互联有限公司", "EXISTING", "周敏", "云服务", "马杰 CIO", "健康经营", 88, 70,
                        "客户使用稳定，对知识库和客服场景的扩展接受度高。",
                        List.of("预算需要 Q3 确认"),
                        List.of("客服知识场景可推进"),
                        List.of("健康度高", "关键人愿意共创", "续约风险低"),
                        List.of("准备客服场景 PoC", "沉淀成功案例", "维护 CIO 关系"),
                        List.of("健康客户", "扩展机会", "案例共创"),
                        List.of("客户称当前系统稳定。", "会议中 CIO 希望探索客服知识场景。", "客户愿意提供内部案例素材。")),
                demo("demo-account-007", "南京星河软件有限公司", "NEW", "张伟", "软件服务", "孙菲 销售经理", "初步接触", 60, 61,
                        "客户刚完成首次沟通，需求集中在销售过程管理和报表。",
                        List.of("决策链未知", "预算未确认"),
                        List.of("销售管理需求明确"),
                        List.of("暂无存量经营信号"),
                        List.of("安排需求澄清会", "确认预算和项目窗口"),
                        List.of("首次接触", "需求澄清", "报表关注"),
                        List.of("活动后首次沟通完成。", "客户关注销售漏斗和主管报表。", "尚未透露预算。")),
                demo("demo-account-008", "武汉联创节能科技有限公司", "EXISTING", "赵鹏", "能源", "何涛 总经理", "续约准备", 78, 66,
                        "合同还有 60 天到期，客户满意但希望降低运维成本。",
                        List.of("续约价格敏感"),
                        List.of("能源项目看板可增购"),
                        List.of("续约窗口明确", "总经理关系稳定", "运维成本是谈判点"),
                        List.of("准备续约价值报告", "列出运维降本证据"),
                        List.of("续约窗口", "价格敏感", "价值复盘"),
                        List.of("电话确认续约窗口为 60 天内。", "客户提到运维成本压力。", "总经理认可当前项目价值。")),
                demo("demo-account-009", "苏州精密制造集团", "STRATEGIC", "李娜", "制造业", "许强 副总裁", "战略客户经营", 84, 76,
                        "集团客户多部门并行推进，存在跨业务线协同机会。",
                        List.of("集团采购流程复杂", "多部门需求口径不一"),
                        List.of("集团级平台机会", "多业务线扩展"),
                        List.of("战略客户", "关系多点覆盖", "可进入集团规划"),
                        List.of("补齐权力地图", "组织集团级方案会", "拆分业务线机会"),
                        List.of("战略客户", "集团机会", "权力地图"),
                        List.of("副总裁提到集团统一平台规划。", "两个业务线分别提出不同诉求。", "采购流程需要集团审批。")),
                demo("demo-account-010", "青岛港航物流有限公司", "EXISTING", "王磊", "物流", "邓丽 运营总监", "服务改进", 69, 62,
                        "客户对派工效率认可，但投诉报表不够及时。",
                        List.of("投诉报表滞后", "运营团队希望看到整改节奏"),
                        List.of("服务看板增购机会"),
                        List.of("核心流程使用稳定", "局部服务体验需改善"),
                        List.of("创建服务改进任务", "准备服务看板方案"),
                        List.of("服务风险", "看板机会", "运营关注"),
                        List.of("客户反馈投诉报表滞后。", "运营总监认可派工效率提升。", "希望每周看到整改节奏。")),
                demo("demo-account-011", "宁波启明医疗器械有限公司", "NEW", "陈晨", "医疗器械", "郑琳 信息主管", "合规评估", 68, 71,
                        "客户关注合规审计、权限和国产化部署能力。",
                        List.of("合规资料待补", "信息安全审批较严"),
                        List.of("审计和权限需求明确", "部署窗口在 Q3"),
                        List.of("暂无存量经营信号"),
                        List.of("补合规资料包", "安排安全架构评审"),
                        List.of("合规关注", "安全评审", "Q3窗口"),
                        List.of("客户要求补充审计能力说明。", "电话中提到国产化部署要求。", "安全审批需要信息主管背书。")),
                demo("demo-account-012", "天津北辰装备有限公司", "EXISTING", "周敏", "装备制造", "韩旭 生产部长", "增购培育", 75, 64,
                        "生产部门认可现有流程，正在关注移动巡检和备件管理。",
                        List.of("IT 预算优先级待确认"),
                        List.of("移动巡检和备件管理有机会"),
                        List.of("生产部门满意", "IT 预算待确认", "可通过试点推动"),
                        List.of("做移动巡检小范围试点", "拉 IT 负责人参会"),
                        List.of("增购培育", "生产认可", "试点建议"),
                        List.of("生产部长认可现有流程。", "客户询问移动巡检是否支持离线。", "备件管理被列为下季度优化项。"))
        );
    }

    private DemoAccount demo(String id,
                             String name,
                             String segment,
                             String owner,
                             String industry,
                             String contact,
                             String stage,
                             int health,
                             int progress,
                             String summary,
                             List<String> risks,
                             List<String> newSignals,
                             List<String> existingSignals,
                             List<String> nextActions,
                             List<String> tags,
                             List<String> interactions) {
        return new DemoAccount(id, name, segment, owner, industry, contact, stage, health, progress, summary,
                risks, newSignals, existingSignals, nextActions, tags, interactions);
    }

    private record DemoAccount(String id,
                               String name,
                               String segment,
                               String owner,
                               String industry,
                               String contact,
                               String stage,
                               int health,
                               int progress,
                               String summary,
                               List<String> risks,
                               List<String> newSignals,
                               List<String> existingSignals,
                               List<String> nextActions,
                               List<String> tags,
                               List<String> interactions) {
        String lastInteraction() {
            return interactions.isEmpty() ? "" : interactions.get(0);
        }
    }

    public record AssistantCommand(String accountId, String message) {}
    public record InteractionCommand(String sourceType, String subject, String content, String occurredAt) {}
    public record RecommendationFeedbackCommand(String rating, String comment) {}
    public record RecommendationDraft(String title, String rationale, BigDecimal confidence,
                                      Map<String, Object> crmPayload, String targetObject, String targetRecordId) {}
    public record RecommendationDismiss(String reason) {}
    public record FollowCommand(boolean followed) {}
}
