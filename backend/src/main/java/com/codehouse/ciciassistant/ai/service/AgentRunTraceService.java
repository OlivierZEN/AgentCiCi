package com.codehouse.ciciassistant.ai.service;

import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionRepository;
import com.codehouse.ciciassistant.agent.service.AgentWorkflowExecutionLogService;
import com.codehouse.ciciassistant.agent.service.AgentWorkflowRuntimeService;
import com.codehouse.ciciassistant.ai.domain.AgentRunTraceEntity;
import com.codehouse.ciciassistant.ai.domain.AgentRunTraceRepository;
import com.codehouse.ciciassistant.ai.domain.ChatMessageEntity;
import com.codehouse.ciciassistant.ai.domain.ChatMessageRepository;
import com.codehouse.ciciassistant.ai.domain.ChatSessionEntity;
import com.codehouse.ciciassistant.ai.domain.ChatSessionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgentRunTraceService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<Object>> LIST_OBJECT_REF = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> MAP_OBJECT_REF = new TypeReference<>() {};
    private static final Pattern WAITING_PATTERN = Pattern.compile("(请补充|请确认|等待确认|需要.*确认|需要.*补充)");
    private static final Pattern FAILED_PATTERN = Pattern.compile("(失败|异常|错误|无法|超时|error|failed|exception)", Pattern.CASE_INSENSITIVE);
    private static final int ADMIN_DETAIL_TEXT_MAX_LENGTH = 12_000;

    private final AgentRunTraceRepository traceRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AgentDefinitionRepository agentDefinitionRepository;

    public AgentRunTraceService(AgentRunTraceRepository traceRepository,
                                ChatSessionRepository chatSessionRepository,
                                ChatMessageRepository chatMessageRepository,
                                AgentDefinitionRepository agentDefinitionRepository) {
        this.traceRepository = traceRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.agentDefinitionRepository = agentDefinitionRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordChatRun(ChatRunTraceInput input) {
        Instant endedAt = input.endedAt() == null ? Instant.now() : input.endedAt();
        Instant startedAt = input.startedAt() == null ? endedAt : input.startedAt();
        int elapsedMs = (int) Math.max(0L, Duration.between(startedAt, endedAt).toMillis());
        List<ToolTrace> tools = buildToolTraces(input);
        List<String> activatedSkillCodes = buildActivatedSkillCodes(input);
        List<String> boundSkillCodes = input.skillCodes() == null ? List.of() : input.skillCodes().stream()
                .filter(item -> item != null && !item.isBlank())
                .distinct()
                .toList();
        List<String> knowledgeBaseNames = input.ragResult() == null
                ? List.of()
                : input.ragResult().knowledgeBases().stream()
                        .map(RagService.RetrievedKnowledgeBase::name)
                        .filter(name -> name != null && !name.isBlank())
                        .distinct()
                        .toList();
        List<Map<String, Object>> nodes = buildNodes(input, tools, activatedSkillCodes, boundSkillCodes, startedAt, endedAt, elapsedMs);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("request", Map.of(
                "question", clip(input.question(), 800),
                "questionDetail", redactedDetailText(input.question()),
                "requestedKnowledgeBaseIds", input.requestedKnowledgeBaseIds() == null ? List.of() : input.requestedKnowledgeBaseIds(),
                "effectiveKnowledgeBaseIds", input.effectiveKnowledgeBaseIds() == null ? List.of() : input.effectiveKnowledgeBaseIds()
        ));
        detail.put("response", Map.of(
                "answer", clip(input.answer(), 1200),
                "answerDetail", redactedDetailText(input.answer())
        ));
        detail.put("model", Map.of("modelName", emptyToBlank(input.modelName())));
        detail.put("rag", ragDetail(input.ragResult()));
        detail.put("tools", tools.stream().map(ToolTrace::toPayload).toList());
        detail.put("skills", Map.of(
                "activeSkillCode", emptyToBlank(input.activeSkillCode()),
                "skillNames", activatedSkillCodes,
                "activatedSkillCodes", activatedSkillCodes,
                "boundSkillCodes", boundSkillCodes,
                "resolvedSkillVersions", input.resolvedSkillVersions() == null ? List.of() : input.resolvedSkillVersions()
        ));
        detail.put("modelCalls", input.modelCalls() == null ? List.of() : input.modelCalls().stream().map(this::modelCallPayload).toList());
        detail.put("runtimeExecution", runtimeExecutionDetail(input.executionResult(), input.workflowElapsedMs()));

        String status = inferStatus(input.answer(), input.executionResult());
        int ragContextCount = input.ragResult() == null ? 0 : input.ragResult().context().size();
        String summary = buildSummary(input.answer(), tools.size(), ragContextCount, input.executionResult());
        traceRepository.save(new AgentRunTraceEntity(
                UUID.randomUUID().toString(),
                input.orgId(),
                input.userId(),
                input.sessionId(),
                emptyToDefault(input.agentId(), "cici-system"),
                channelOf(input.sessionId()),
                status,
                clip(input.question(), 80),
                summary,
                emptyToBlank(input.modelName()),
                emptyToBlank(input.activeSkillCode()),
                startedAt,
                endedAt,
                elapsedMs,
                input.modelCalls() == null || input.modelCalls().isEmpty() ? countModelCalls(input.messages()) : input.modelCalls().size(),
                tools.size(),
                ragContextCount,
                writeJson(knowledgeBaseNames),
                writeJson(activatedSkillCodes),
                writeJson(nodes),
                writeJson(detail),
                Instant.now()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String recordCustomerInsightRun(CustomerInsightTraceInput input) {
        Instant endedAt = input.endedAt() == null ? Instant.now() : input.endedAt();
        Instant startedAt = input.startedAt() == null ? endedAt : input.startedAt();
        int elapsedMs = (int) Math.max(0L, Duration.between(startedAt, endedAt).toMillis());
        String traceId = UUID.randomUUID().toString();
        String status = input.success() ? "success" : "failed";
        Map<String, Object> model = Map.of(
                "provider", emptyToBlank(input.modelProvider()),
                "modelName", emptyToBlank(input.modelName())
        );
        List<Map<String, Object>> nodes = List.of(
                Map.of(
                        "id", "customer-insight-request",
                        "type", "USER_MESSAGE",
                        "title", "客户洞察输入",
                        "status", "success",
                        "startedAt", startedAt.toString(),
                        "endedAt", startedAt.toString(),
                        "elapsedMs", 0,
                        "summary", clip(input.inputSummary(), 220),
                        "metadata", Map.of(
                                "appCode", "customer-insight",
                                "projectId", input.projectPublicId(),
                                "sectionCode", input.sectionCode()
                        )
                ),
                Map.of(
                        "id", "customer-insight-model",
                        "type", "MODEL_CALL",
                        "title", "客户洞察分析",
                        "status", status,
                        "startedAt", startedAt.toString(),
                        "endedAt", endedAt.toString(),
                        "elapsedMs", elapsedMs,
                        "summary", clip(input.outputSummary(), 260),
                        "metadata", model
                )
        );
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("app", Map.of(
                "appCode", "customer-insight",
                "projectId", input.projectPublicId(),
                "sectionCode", input.sectionCode(),
                "sectionTitle", input.sectionTitle()
        ));
        detail.put("request", Map.of(
                "customerName", clip(input.customerName(), 160),
                "inputSummary", clip(input.inputSummary(), 800),
                "sourceSnapshotCount", input.sourceSnapshotCount()
        ));
        detail.put("response", Map.of(
                "summary", clip(input.outputSummary(), 1200),
                "error", clip(input.errorMessage(), 600)
        ));
        detail.put("model", model);
        detail.put("skills", Map.of(
                "activeSkillCode", input.skillCode(),
                "activatedSkillCodes", List.of(input.skillCode()),
                "skillNames", List.of(input.skillCode()),
                "boundSkillCodes", List.of(input.skillCode())
        ));
        traceRepository.save(new AgentRunTraceEntity(
                traceId,
                input.orgId(),
                input.userId(),
                "web:customer-insight:" + input.projectPublicId(),
                "cici-system",
                "web",
                status,
                clip(input.customerName() + " · " + input.sectionTitle(), 80),
                clip(input.outputSummary(), 512),
                emptyToBlank(input.modelName()),
                input.skillCode(),
                startedAt,
                endedAt,
                elapsedMs,
                1,
                0,
                0,
                "[]",
                writeJson(List.of(input.skillCode())),
                writeJson(nodes),
                writeJson(detail),
                Instant.now()));
        return traceId;
    }

    public Map<String, Object> listRunLogs(String orgId, String userId, RunLogQuery query) {
        Instant to = query.to() == null ? Instant.now() : query.to();
        Instant from = query.from() == null ? to.minus(Duration.ofDays(7)) : query.from();
        if (from.isBefore(to.minus(Duration.ofDays(7)))) {
            from = to.minus(Duration.ofDays(7));
        }
        int limit = Math.min(Math.max(query.limit(), 1), 100);
        List<Map<String, Object>> rows = new ArrayList<>();
        LinkedHashSet<String> sessionsWithTrace = new LinkedHashSet<>();
        for (AgentRunTraceEntity item : traceRepository.findVisibleRecent(orgId, userId, from, to)) {
            sessionsWithTrace.add(item.getSessionId());
            Map<String, Object> payload = toListPayload(orgId, item);
            if (matches(payload, query)) {
                rows.add(payload);
            }
            if (rows.size() >= limit) {
                break;
            }
        }
        if (rows.size() < limit) {
            for (ChatSessionEntity session : legacyVisibleSessions(orgId, userId, from, to)) {
                if (sessionsWithTrace.contains(session.getId())) {
                    continue;
                }
                Map<String, Object> payload = legacyListPayload(orgId, session);
                if (matches(payload, query)) {
                    rows.add(payload);
                }
                if (rows.size() >= limit) {
                    break;
                }
            }
        }
        return Map.of(
                "items", rows,
                "from", from.toString(),
                "to", to.toString(),
                "nextCursor", ""
        );
    }

    public Map<String, Object> listOrgRunLogs(String orgId, RunLogQuery query) {
        Instant to = query.to() == null ? Instant.now() : query.to();
        Instant from = query.from() == null ? to.minus(Duration.ofDays(7)) : query.from();
        if (from.isBefore(to.minus(Duration.ofDays(7)))) {
            from = to.minus(Duration.ofDays(7));
        }
        int limit = Math.min(Math.max(query.limit(), 1), 100);
        List<Map<String, Object>> rows = new ArrayList<>();
        LinkedHashSet<String> sessionsWithTrace = new LinkedHashSet<>();
        for (AgentRunTraceEntity item : traceRepository.findByOrgIdAndStartedAtBetweenOrderByStartedAtDesc(orgId, from, to)) {
            sessionsWithTrace.add(item.getSessionId());
            Map<String, Object> payload = toListPayload(orgId, item);
            if (matches(payload, query)) {
                rows.add(payload);
            }
            if (rows.size() >= limit) {
                break;
            }
        }
        if (rows.size() < limit) {
            for (ChatSessionEntity session : orgLegacySessions(orgId, from, to)) {
                if (sessionsWithTrace.contains(session.getId())) {
                    continue;
                }
                Map<String, Object> payload = legacyListPayload(orgId, session);
                if (matches(payload, query)) {
                    rows.add(payload);
                }
                if (rows.size() >= limit) {
                    break;
                }
            }
        }
        return Map.of(
                "items", rows,
                "from", from.toString(),
                "to", to.toString(),
                "nextCursor", ""
        );
    }

    public Map<String, Object> listOrgRuntimeSnapshots(String orgId) {
        Instant to = Instant.now();
        Instant from = to.minus(Duration.ofDays(7));
        Map<String, Map<String, Object>> byAgent = new LinkedHashMap<>();
        for (AgentDefinitionEntity agent : agentDefinitionRepository.findByOrgIdAndEnabledTrueOrderByBuiltinDescUpdatedAtDesc(orgId)) {
            byAgent.put(agent.getAgentId(), newRuntimeSnapshot(
                    agent.getAgentId(),
                    agent.getName(),
                    agent.getAvatarBase64(),
                    agent.getSummary()));
        }
        Map<String, AgentRunTraceRepository.AgentRuntimeStatsProjection> statsByAgent = new LinkedHashMap<>();
        for (AgentRunTraceRepository.AgentRuntimeStatsProjection stats : traceRepository.summarizeOrgRuntime(orgId, from, to)) {
            statsByAgent.put(stats.getAgentId(), stats);
            byAgent.putIfAbsent(stats.getAgentId(), newRuntimeSnapshot(
                    stats.getAgentId(),
                    agentName(orgId, stats.getAgentId()),
                    "",
                    ""));
        }
        for (Map.Entry<String, AgentRunTraceRepository.AgentRuntimeStatsProjection> entry : statsByAgent.entrySet()) {
            Map<String, Object> snapshot = byAgent.get(entry.getKey());
            AgentRunTraceRepository.AgentRuntimeStatsProjection stats = entry.getValue();
            snapshot.put("activeSessionCount", stats.getActiveSessionCount());
            snapshot.put("sevenDaySessionCount", stats.getSevenDaySessionCount());
            snapshot.put("sevenDayFailureCount", stats.getSevenDayFailureCount());
            snapshot.put("avgLatencyMs", stats.getAvgLatencyMs() == null ? 0 : Math.round(stats.getAvgLatencyMs()));
            snapshot.put("lastActiveAt", stats.getLastActiveAt() == null ? "" : stats.getLastActiveAt().toString());
        }
        LinkedHashSet<String> latestSeen = new LinkedHashSet<>();
        for (AgentRunTraceEntity trace : traceRepository.findTop500ByOrgIdAndStartedAtBetweenOrderByStartedAtDesc(orgId, from, to)) {
            if (!latestSeen.add(trace.getAgentId())) {
                continue;
            }
            byAgent.putIfAbsent(trace.getAgentId(), newRuntimeSnapshot(
                    trace.getAgentId(),
                    agentName(orgId, trace.getAgentId()),
                    "",
                    ""));
            Map<String, Object> snapshot = byAgent.get(trace.getAgentId());
            snapshot.put("status", runtimeStatus(trace.getStatus()));
            snapshot.put("currentTask", emptyToDefault(trace.getSummary(), trace.getTitle()));
            snapshot.put("lastTraceId", trace.getTraceId());
            snapshot.put("lastRunStatus", trace.getStatus());
            snapshot.put("lastChannel", trace.getChannel());
            snapshot.put("lastElapsedMs", trace.getElapsedMs());
        }
        List<Map<String, Object>> items = byAgent.values().stream()
                .sorted((left, right) -> String.valueOf(right.getOrDefault("lastActiveAt", ""))
                        .compareTo(String.valueOf(left.getOrDefault("lastActiveAt", ""))))
                .toList();
        long running = items.stream().filter(item -> "RUNNING".equals(item.get("status"))).count();
        long warning = items.stream().filter(item -> "FAILED".equals(item.get("status"))
                || "WAITING_CONFIRMATION".equals(item.get("status"))).count();
        return Map.of(
                "items", items,
                "from", from.toString(),
                "to", to.toString(),
                "summary", Map.of(
                        "agentCount", items.size(),
                        "runningCount", running,
                        "warningCount", warning,
                        "sevenDaySessionCount", items.stream().mapToLong(item -> number(item.get("sevenDaySessionCount"))).sum()
                )
        );
    }

    public Map<String, Object> traceDetail(String orgId, String userId, String traceId) {
        if (traceId != null && traceId.startsWith("legacy-")) {
            return legacyDetail(orgId, userId, decodeLegacySessionId(traceId));
        }
        AgentRunTraceEntity entity = traceRepository.findByTraceIdAndOrgId(traceId, orgId)
                .filter(item -> userId.equals(item.getUserId()) || isOrgScopedConversation(item.getSessionId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trace not found"));
        return tracePayload(orgId, entity);
    }

    public Map<String, Object> orgTraceDetail(String orgId, String traceId) {
        if (traceId != null && traceId.startsWith("legacy-")) {
            return legacyOrgDetail(orgId, decodeLegacySessionId(traceId));
        }
        AgentRunTraceEntity entity = traceRepository.findByTraceIdAndOrgId(traceId, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trace not found"));
        return tracePayload(orgId, entity);
    }

    private Map<String, Object> tracePayload(String orgId, AgentRunTraceEntity entity) {
        Map<String, Object> detail = readMap(entity.getDetailJson());
        List<Object> nodes = readList(entity.getNodesJson());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("traceId", entity.getTraceId());
        payload.put("requestId", emptyToBlank(entity.getRequestId()));
        payload.put("sessionId", entity.getSessionId());
        payload.put("agentId", entity.getAgentId());
        payload.put("agentName", agentName(orgId, entity.getAgentId()));
        payload.put("channel", entity.getChannel());
        payload.put("status", entity.getStatus());
        payload.put("startedAt", entity.getStartedAt().toString());
        payload.put("endedAt", entity.getEndedAt().toString());
        payload.put("elapsedMs", entity.getElapsedMs());
        payload.put("summary", entity.getSummary());
        payload.put("source", emptyToDefault(entity.getSourceType(), "trace"));
        payload.put("credentialId", entity.getCredentialId() == null ? "" : entity.getCredentialId().toString());
        payload.put("externalUserId", emptyToBlank(entity.getExternalUserId()));
        payload.put("nodes", nodes);
        payload.put("errorReason", errorReason(detail, nodes));
        payload.put("detail", detail);
        payload.put("model", detail.getOrDefault("model", Map.of()));
        payload.put("tools", detail.getOrDefault("tools", List.of()));
        payload.put("skills", detail.getOrDefault("skills", Map.of()));
        payload.put("rag", detail.getOrDefault("rag", Map.of()));
        return payload;
    }

    private Map<String, Object> newRuntimeSnapshot(String agentId, String agentName, String avatarBase64, String summary) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agentId", agentId);
        payload.put("agentName", emptyToDefault(agentName, agentId));
        payload.put("avatarBase64", emptyToBlank(avatarBase64));
        payload.put("status", "IDLE");
        payload.put("currentTask", emptyToDefault(summary, "暂无运行记录"));
        payload.put("activeSessionCount", 0L);
        payload.put("sevenDaySessionCount", 0L);
        payload.put("sevenDayFailureCount", 0L);
        payload.put("avgLatencyMs", 0L);
        payload.put("lastActiveAt", "");
        payload.put("lastTraceId", "");
        payload.put("lastRunStatus", "");
        payload.put("lastChannel", "");
        payload.put("lastElapsedMs", 0);
        return payload;
    }

    private String runtimeStatus(String rawStatus) {
        return switch (emptyToBlank(rawStatus).toUpperCase(Locale.ROOT)) {
            case "RUNNING" -> "RUNNING";
            case "WAITING_CONFIRMATION" -> "WAITING_CONFIRMATION";
            case "FAILED", "FAILED_COMPLETION", "ERROR" -> "FAILED";
            case "COMPLETED", "SUCCESS" -> "COMPLETED";
            default -> "IDLE";
        };
    }

    private List<Map<String, Object>> buildNodes(ChatRunTraceInput input,
                                                 List<ToolTrace> tools,
                                                 List<String> activatedSkillCodes,
                                                 List<String> boundSkillCodes,
                                                 Instant startedAt,
                                                 Instant endedAt,
                                                 int elapsedMs) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        StageTraceInput userStage = findStage(input.stageTraces(), "USER_MESSAGE");
        nodes.add(userStage == null
                ? node("USER_MESSAGE", "用户输入", "SUCCESS", startedAt, null, null,
                clip(input.question(), 220), Map.of("sessionId", input.sessionId()))
                : stageNode(userStage));
        StageTraceInput skillResolveStage = findStage(input.stageTraces(), "SKILL_RESOLVE");
        if (skillResolveStage != null) {
            nodes.add(stageNode(skillResolveStage));
        }
        RagService.RetrievalResult rag = input.ragResult();
        int ragCount = rag == null ? 0 : rag.context().size();
        long ragMs = rag == null ? 0L : rag.timingsMs().getOrDefault("total", 0L);
        StageTraceInput ragStage = findStage(input.stageTraces(), "RAG");
        nodes.add(ragStage == null
                ? node("RAG", ragCount > 0 ? "知识库检索" : "知识库检索未触发",
                ragCount > 0 ? "SUCCESS" : "SKIPPED",
                startedAt, null, ragMs,
                ragCount > 0 ? "命中 " + ragCount + " 个知识片段。" : "本轮未触发知识库检索或无命中。",
                ragDetail(rag))
                : stageNode(ragStage, ragDetail(rag)));
        nodes.add(node("SKILL", "技能判定", activatedSkillCodes.isEmpty() ? "SKIPPED" : "SUCCESS",
                startedAt, null, 0L,
                activatedSkillCodes.isEmpty()
                        ? "本轮未激活业务技能；当前智能体有 " + boundSkillCodes.size() + " 个绑定/候选技能。"
                        : "本轮激活技能：" + String.join("、", activatedSkillCodes),
                Map.of(
                        "activeSkillCode", emptyToBlank(input.activeSkillCode()),
                        "activatedSkillCodes", activatedSkillCodes,
                        "boundSkillCodes", boundSkillCodes
                )));
        StageTraceInput toolSchemaStage = findStage(input.stageTraces(), "TOOL_SCHEMA");
        if (toolSchemaStage != null) {
            nodes.add(stageNode(toolSchemaStage));
        }
        List<Map<String, Object>> callNodes = new ArrayList<>();
        if (input.modelCalls() != null) {
            int index = 1;
            for (ModelCallTraceInput modelCall : input.modelCalls()) {
                callNodes.add(modelNode(modelCall, index++));
            }
        }
        for (ToolTrace tool : tools) {
            callNodes.add(node("TOOL", tool.name(), tool.success() ? "SUCCESS" : "FAILED",
                    tool.startedAt(), tool.endedAt(), tool.elapsedMs(), tool.summary(), tool.toPayload()));
        }
        callNodes.sort((left, right) -> String.valueOf(left.getOrDefault("startedAt", ""))
                .compareTo(String.valueOf(right.getOrDefault("startedAt", ""))));
        nodes.addAll(callNodes);
        StageTraceInput workflowStage = findStage(input.stageTraces(), "WORKFLOW");
        AgentWorkflowRuntimeService.RuntimeExecutionResult execution = input.executionResult();
        if (workflowStage != null) {
            nodes.add(stageNode(workflowStage, runtimeExecutionDetail(execution, input.workflowElapsedMs())));
        } else if (execution != null) {
            String status = AgentWorkflowExecutionLogService.normalizeWorkflowStatus(execution.executionStatus());
            nodes.add(node("WORKFLOW", "工作流定义检查", AgentWorkflowExecutionLogService.STATUS_SUCCESS.equals(status) ? "SUCCESS" : "FAILED",
                    startedAt, endedAt, (long) input.workflowElapsedMs(),
                    clip(execution.executionOutput(), 260),
                    runtimeExecutionDetail(execution, input.workflowElapsedMs())));
        }
        StageTraceInput persistenceStage = findStage(input.stageTraces(), "PERSISTENCE");
        nodes.add(persistenceStage == null
                ? node("PERSISTENCE", "消息落库", "SUCCESS", endedAt, endedAt, 0L,
                "用户消息与助手回复已写入会话日志。", Map.of("sessionId", input.sessionId()))
                : stageNode(persistenceStage));
        return nodes;
    }

    private Map<String, Object> node(String type, String title, String status, Instant startedAt, Instant endedAt,
                                     Long elapsedMs, String summary, Map<String, Object> metadata) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", type.toLowerCase(Locale.ROOT) + "-" + UUID.randomUUID().toString().substring(0, 8));
        node.put("type", type);
        node.put("title", title);
        node.put("status", status);
        node.put("startedAt", startedAt == null ? "" : startedAt.toString());
        node.put("endedAt", endedAt == null ? "" : endedAt.toString());
        node.put("elapsedMs", elapsedMs == null ? 0 : elapsedMs);
        node.put("summary", emptyToBlank(summary));
        node.put("metadata", metadata == null ? Map.of() : metadata);
        return node;
    }

    private Map<String, Object> stageNode(StageTraceInput stage) {
        return stageNode(stage, stage.metadata());
    }

    private Map<String, Object> stageNode(StageTraceInput stage, Map<String, Object> metadata) {
        return node(
                emptyToDefault(stage.type(), "STAGE"),
                emptyToDefault(stage.title(), "链路阶段"),
                emptyToDefault(stage.status(), "SUCCESS"),
                stage.startedAt(),
                stage.endedAt(),
                (long) Math.max(0, stage.elapsedMs()),
                stage.summary(),
                metadata == null ? Map.of() : metadata);
    }

    private Map<String, Object> modelNode(ModelCallTraceInput modelCall, int index) {
        String title = switch (emptyToBlank(modelCall.phase())) {
            case "tool_planning" -> "模型工具规划 #" + index;
            case "tool_planning_stop" -> "模型工具规划收口 #" + index;
            case "tool_planning_stop_skipped" -> "模型工具规划收口跳过 #" + index;
            case "tool_limit_summary" -> "工具上限后模型收口";
            case "final_stream" -> "模型最终生成";
            case "final_completion" -> "模型生成";
            default -> "模型调用 #" + index;
        };
        return node("MODEL", title, emptyToDefault(modelCall.status(), "SUCCESS"),
                modelCall.startedAt(), modelCall.endedAt(), (long) Math.max(0, modelCall.elapsedMs()),
                emptyToDefault(modelCall.summary(), "模型调用已完成。"),
                modelCallPayload(modelCall));
    }

    private Map<String, Object> modelCallPayload(ModelCallTraceInput modelCall) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("phase", emptyToBlank(modelCall.phase()));
        payload.put("modelName", emptyToBlank(modelCall.modelName()));
        payload.put("status", emptyToDefault(modelCall.status(), "SUCCESS"));
        payload.put("startedAt", modelCall.startedAt() == null ? "" : modelCall.startedAt().toString());
        payload.put("endedAt", modelCall.endedAt() == null ? "" : modelCall.endedAt().toString());
        payload.put("elapsedMs", Math.max(0, modelCall.elapsedMs()));
        payload.put("toolCallCount", Math.max(0, modelCall.toolCallCount()));
        payload.put("outputChars", Math.max(0, modelCall.outputChars()));
        payload.put("inputTokens", Math.max(0, modelCall.inputTokens()));
        payload.put("outputTokens", Math.max(0, modelCall.outputTokens()));
        payload.put("summary", emptyToBlank(modelCall.summary()));
        return payload;
    }

    private StageTraceInput findStage(List<StageTraceInput> stages, String type) {
        if (stages == null || type == null) {
            return null;
        }
        for (StageTraceInput stage : stages) {
            if (type.equalsIgnoreCase(stage.type())) {
                return stage;
            }
        }
        return null;
    }

    private List<ToolTrace> buildToolTraces(ChatRunTraceInput input) {
        if (input.toolCalls() != null && !input.toolCalls().isEmpty()) {
            return input.toolCalls().stream()
                    .map(item -> new ToolTrace(
                            emptyToBlank(item.id()),
                            emptyToDefault(item.name(), "tool"),
                            redact(emptyToBlank(item.arguments())),
                            redact(emptyToBlank(item.result())),
                            item.success(),
                            item.startedAt(),
                            item.endedAt(),
                            (long) Math.max(0, item.elapsedMs())))
                    .toList();
        }
        return extractToolTraces(input.messages());
    }

    private List<ToolTrace> extractToolTraces(List<Map<String, Object>> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        Map<String, ToolTrace> byCallId = new LinkedHashMap<>();
        for (Map<String, Object> message : messages) {
            Object role = message.get("role");
            if ("assistant".equals(String.valueOf(role)) && message.get("tool_calls") instanceof List<?> calls) {
                for (Object call : calls) {
                    if (!(call instanceof Map<?, ?> callMap)) {
                        continue;
                    }
                    Object idValue = callMap.get("id");
                    String id = idValue == null ? "" : String.valueOf(idValue);
                    Object fn = callMap.get("function");
                    String name = "tool";
                    String args = "";
                    if (fn instanceof Map<?, ?> fnMap) {
                        Object nameValue = fnMap.get("name");
                        Object argsValue = fnMap.get("arguments");
                        name = nameValue == null ? "tool" : String.valueOf(nameValue);
                        args = argsValue == null ? "" : String.valueOf(argsValue);
                    }
                    if (!id.isBlank()) {
                        byCallId.put(id, new ToolTrace(id, name, redact(args), "", true, null, null, 0L));
                    }
                }
            }
            if ("tool".equals(String.valueOf(role))) {
                String id = String.valueOf(message.getOrDefault("tool_call_id", ""));
                ToolTrace existing = byCallId.get(id);
                if (existing != null) {
                    String result = String.valueOf(message.getOrDefault("content", ""));
                    byCallId.put(id, existing.withResult(redact(result), !looksFailed(result)));
                }
            }
        }
        return new ArrayList<>(byCallId.values());
    }

    private boolean looksFailed(String result) {
        String s = result == null ? "" : result.toLowerCase(Locale.ROOT);
        return s.contains("\"success\":false") || s.contains("\"error\"") || s.contains("failed") || s.contains("异常") || s.contains("失败");
    }

    private int countModelCalls(List<Map<String, Object>> messages) {
        int toolRounds = 0;
        if (messages != null) {
            for (Map<String, Object> message : messages) {
                if ("assistant".equals(String.valueOf(message.get("role"))) && message.get("tool_calls") instanceof List<?> calls && !calls.isEmpty()) {
                    toolRounds++;
                }
            }
        }
        return Math.max(1, toolRounds + 1);
    }

    private List<String> buildActivatedSkillCodes(ChatRunTraceInput input) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (input.activatedSkillCodes() != null) {
            out.addAll(input.activatedSkillCodes().stream().filter(item -> item != null && !item.isBlank()).toList());
        }
        return new ArrayList<>(out);
    }

    private Map<String, Object> ragDetail(RagService.RetrievalResult rag) {
        if (rag == null) {
            return Map.of("triggered", false, "contextCount", 0, "knowledgeBases", List.of(), "timingsMs", Map.of(), "fallbackUsed", false);
        }
        return Map.of(
                "triggered", !rag.knowledgeBases().isEmpty() || !rag.context().isEmpty(),
                "contextCount", rag.context().size(),
                "sources", rag.sources().stream().map(RagService.RetrievedSource::toPayload).toList(),
                "metadataFilters", rag.metadataFilters(),
                "knowledgeBases", rag.knowledgeBases().stream().map(kb -> Map.of("id", kb.id(), "name", kb.name())).toList(),
                "timingsMs", rag.timingsMs(),
                "fallbackUsed", rag.fallbackUsed()
        );
    }

    private Map<String, Object> runtimeExecutionDetail(AgentWorkflowRuntimeService.RuntimeExecutionResult result, int workflowElapsedMs) {
        if (result == null) {
            return Map.of("status", "SKIPPED", "elapsedMs", 0);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", result.executionStatus());
        payload.put("output", clip(result.executionOutput(), 800));
        payload.put("publishedVersionId", result.publishedVersionId() == null ? "" : result.publishedVersionId());
        payload.put("resolvedSkillVersions", result.resolvedSkillVersions());
        payload.put("policyBundle", result.policyBundle());
        payload.put("trace", result.executionTrace());
        payload.put("contextSnapshot", result.contextSnapshot());
        payload.put("elapsedMs", Math.max(0, workflowElapsedMs));
        return payload;
    }

    private String inferStatus(String answer, AgentWorkflowRuntimeService.RuntimeExecutionResult executionResult) {
        if (executionResult != null
                && AgentWorkflowExecutionLogService.STATUS_FAILED.equals(AgentWorkflowExecutionLogService.normalizeWorkflowStatus(executionResult.executionStatus()))) {
            return "FAILED";
        }
        String text = emptyToBlank(answer);
        if (WAITING_PATTERN.matcher(text).find()) {
            return "WAITING_CONFIRMATION";
        }
        if (FAILED_PATTERN.matcher(text).find()) {
            return "FAILED";
        }
        return "COMPLETED";
    }

    private String buildSummary(String answer, int toolCount, int ragCount, AgentWorkflowRuntimeService.RuntimeExecutionResult executionResult) {
        StringBuilder prefix = new StringBuilder();
        if (ragCount > 0) {
            prefix.append("知识库命中 ").append(ragCount).append("；");
        }
        if (toolCount > 0) {
            prefix.append("工具调用 ").append(toolCount).append("；");
        }
        if (executionResult != null && executionResult.executionStatus() != null && !executionResult.executionStatus().isBlank()) {
            prefix.append("技能运行 ").append(executionResult.executionStatus()).append("；");
        }
        String body = clip(answer, 240);
        return clip((prefix + body).isBlank() ? "本轮对话已记录。" : prefix + body, 512);
    }

    private Map<String, Object> toListPayload(String orgId, AgentRunTraceEntity item) {
        Map<String, Object> detail = readMap(item.getDetailJson());
        List<Object> nodes = readList(item.getNodesJson());
        Map<String, Object> skills = detail.get("skills") instanceof Map<?, ?> skillMap
                ? normalizeMap(skillMap)
                : Map.of();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("traceId", item.getTraceId());
        payload.put("requestId", emptyToBlank(item.getRequestId()));
        payload.put("sessionId", item.getSessionId());
        payload.put("agentId", item.getAgentId());
        payload.put("agentName", agentName(orgId, item.getAgentId()));
        payload.put("title", item.getTitle());
        payload.put("channel", item.getChannel());
        payload.put("status", item.getStatus());
        payload.put("startedAt", item.getStartedAt().toString());
        payload.put("endedAt", item.getEndedAt().toString());
        payload.put("elapsedMs", item.getElapsedMs());
        payload.put("modelCallCount", item.getModelCallCount());
        payload.put("toolCallCount", item.getToolCallCount());
        payload.put("ragContextCount", item.getRagContextCount());
        payload.put("skillNames", readList(item.getSkillNamesJson()));
        payload.put("activatedSkillCodes", readList(item.getSkillNamesJson()));
        payload.put("boundSkillCodes", skills.getOrDefault("boundSkillCodes", List.of()));
        payload.put("knowledgeBaseNames", readList(item.getKnowledgeBaseNamesJson()));
        payload.put("summary", item.getSummary());
        payload.put("errorReason", errorReason(detail, nodes));
        payload.put("source", emptyToDefault(item.getSourceType(), "trace"));
        payload.put("credentialId", item.getCredentialId() == null ? "" : item.getCredentialId().toString());
        payload.put("externalUserId", emptyToBlank(item.getExternalUserId()));
        return payload;
    }

    private Map<String, Object> legacyListPayload(String orgId, ChatSessionEntity session) {
        ChatMessageEntity last = chatMessageRepository.findFirstByOrgIdAndSessionIdOrderByCreatedAtDesc(orgId, session.getId()).orElse(null);
        String agentId = emptyToDefault(session.getAgentId(), "cici-system");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("traceId", encodeLegacyTraceId(session.getId()));
        payload.put("sessionId", session.getId());
        payload.put("agentId", agentId);
        payload.put("agentName", agentName(orgId, agentId));
        payload.put("title", session.getTitle());
        payload.put("channel", channelOf(session.getId()));
        payload.put("status", "COMPLETED");
        payload.put("startedAt", session.getUpdatedAt().toString());
        payload.put("endedAt", session.getUpdatedAt().toString());
        payload.put("elapsedMs", 0);
        payload.put("modelCallCount", 0);
        payload.put("toolCallCount", 0);
        payload.put("ragContextCount", 0);
        payload.put("skillNames", List.of());
        payload.put("knowledgeBaseNames", List.of());
        payload.put("summary", last == null ? session.getTitle() : clip(last.getContent(), 240));
        payload.put("source", "chat_session");
        return payload;
    }

    private Map<String, Object> legacyDetail(String orgId, String userId, String sessionId) {
        ChatSessionEntity session = visibleSession(orgId, userId, sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trace not found"));
        return legacyPayload(orgId, session, sessionId);
    }

    private Map<String, Object> legacyOrgDetail(String orgId, String sessionId) {
        ChatSessionEntity session = chatSessionRepository.findByIdAndOrgId(sessionId, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trace not found"));
        return legacyPayload(orgId, session, sessionId);
    }

    private Map<String, Object> legacyPayload(String orgId, ChatSessionEntity session, String sessionId) {
        List<ChatMessageEntity> messages = chatMessageRepository.findByOrgIdAndSessionIdOrderByCreatedAtAsc(orgId, sessionId);
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (ChatMessageEntity message : messages) {
            String type = "assistant".equals(message.getRoleCode()) ? "ASSISTANT_MESSAGE" : "USER_MESSAGE";
            nodes.add(node(type, "assistant".equals(message.getRoleCode()) ? "助手回复" : "用户输入",
                    "SUCCESS", message.getCreatedAt(), message.getCreatedAt(), 0L,
                    clip(message.getContent(), 260),
                    Map.of("role", message.getRoleCode())));
        }
        String agentId = emptyToDefault(session.getAgentId(), "cici-system");
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("traceId", encodeLegacyTraceId(sessionId));
        detail.put("sessionId", sessionId);
        detail.put("agentId", agentId);
        detail.put("agentName", agentName(orgId, agentId));
        detail.put("channel", channelOf(sessionId));
        detail.put("status", "COMPLETED");
        detail.put("startedAt", messages.isEmpty() ? session.getUpdatedAt().toString() : messages.get(0).getCreatedAt().toString());
        detail.put("endedAt", session.getUpdatedAt().toString());
        detail.put("elapsedMs", 0);
        detail.put("summary", "历史会话消息记录，未包含运行时 trace 明细。");
        detail.put("errorReason", "");
        detail.put("nodes", nodes);
        detail.put("model", Map.of());
        detail.put("tools", List.of());
        detail.put("skills", Map.of("skillNames", List.of()));
        detail.put("rag", Map.of("triggered", false, "contextCount", 0));
        detail.put("detail", Map.of(
                "messages", messages.stream().map(item -> Map.of(
                        "role", item.getRoleCode(),
                        "content", clip(item.getContent(), 1000),
                        "createdAt", item.getCreatedAt().toString()
                )).toList()
        ));
        return detail;
    }

    private List<ChatSessionEntity> orgLegacySessions(String orgId, Instant from, Instant to) {
        return chatSessionRepository.findByOrgIdOrderByUpdatedAtDesc(orgId).stream()
                .filter(item -> !item.getUpdatedAt().isBefore(from) && !item.getUpdatedAt().isAfter(to))
                .filter(item -> !item.getId().startsWith("assistant-ui-"))
                .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()))
                .toList();
    }

    private List<ChatSessionEntity> legacyVisibleSessions(String orgId, String userId, Instant from, Instant to) {
        List<ChatSessionEntity> visible = new ArrayList<>();
        visible.addAll(chatSessionRepository.findByOrgIdAndUserIdOrderByUpdatedAtDesc(orgId, userId));
        for (ChatSessionEntity item : chatSessionRepository.findByOrgIdOrderByUpdatedAtDesc(orgId)) {
            if (isOrgScopedConversation(item.getId())) {
                visible.add(item);
            }
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        return visible.stream()
                .filter(item -> seen.add(item.getId()))
                .filter(item -> !item.getUpdatedAt().isBefore(from) && !item.getUpdatedAt().isAfter(to))
                .filter(item -> !item.getId().startsWith("assistant-ui-"))
                .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()))
                .toList();
    }

    private Optional<ChatSessionEntity> visibleSession(String orgId, String userId, String sessionId) {
        if (isOrgScopedConversation(sessionId)) {
            return chatSessionRepository.findByIdAndOrgId(sessionId, orgId);
        }
        return chatSessionRepository.findByIdAndOrgIdAndUserId(sessionId, orgId, userId);
    }

    private boolean matches(Map<String, Object> payload, RunLogQuery query) {
        if (query.agentId() != null && !query.agentId().isBlank()
                && !query.agentId().equals(String.valueOf(payload.get("agentId")))) {
            return false;
        }
        if (query.status() != null && !query.status().isBlank()
                && !query.status().equals(String.valueOf(payload.get("status")))) {
            return false;
        }
        if (query.type() != null && !query.type().isBlank()) {
            String type = query.type().toLowerCase(Locale.ROOT);
            int toolCount = number(payload.get("toolCallCount"));
            int ragCount = number(payload.get("ragContextCount"));
            if ("tool".equals(type) && toolCount <= 0) {
                return false;
            }
            if (("rag".equals(type) || "knowledge".equals(type)) && ragCount <= 0) {
                return false;
            }
        }
        if (query.q() != null && !query.q().isBlank()) {
            String q = query.q().toLowerCase(Locale.ROOT);
            String haystack = String.join(" ",
                    String.valueOf(payload.get("traceId")),
                    String.valueOf(payload.get("sessionId")),
                    String.valueOf(payload.get("agentName")),
                    String.valueOf(payload.get("title")),
                    String.valueOf(payload.get("summary")),
                    String.valueOf(payload.get("errorReason"))).toLowerCase(Locale.ROOT);
            return haystack.contains(q);
        }
        return true;
    }

    private String errorReason(Map<String, Object> detail, List<Object> nodes) {
        Object tools = detail.get("tools");
        if (tools instanceof List<?> toolList) {
            for (Object tool : toolList) {
                if (tool instanceof Map<?, ?> raw) {
                    Map<String, Object> item = normalizeMap(raw);
                    Object success = item.get("success");
                    if (Boolean.FALSE.equals(success) || "false".equalsIgnoreCase(String.valueOf(success))) {
                        String name = emptyToDefault(asText(item.get("name")), "tool");
                        String reason = emptyToDefault(asText(item.get("errorMessage")), asText(item.get("result")));
                        return clip(name + " 调用失败：" + reason, 300);
                    }
                }
            }
        }
        Object runtime = detail.get("runtimeExecution");
        if (runtime instanceof Map<?, ?> rawRuntime) {
            Map<String, Object> item = normalizeMap(rawRuntime);
            String status = String.valueOf(item.getOrDefault("status", ""));
            if (AgentWorkflowExecutionLogService.STATUS_FAILED.equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status)) {
                return clip("技能运行失败：" + asText(item.get("output")), 300);
            }
        }
        for (Object node : nodes == null ? List.of() : nodes) {
            if (node instanceof Map<?, ?> rawNode) {
                Map<String, Object> item = normalizeMap(rawNode);
                if ("FAILED".equalsIgnoreCase(String.valueOf(item.getOrDefault("status", "")))) {
                    return clip(emptyToDefault(asText(item.get("title")), "链路节点")
                            + "：" + asText(item.get("summary")), 300);
                }
            }
        }
        return "";
    }

    private String agentName(String orgId, String agentId) {
        return agentDefinitionRepository.findByOrgIdAndAgentId(orgId, agentId)
                .map(item -> item.getName() == null || item.getName().isBlank() ? agentId : item.getName())
                .orElse(agentId);
    }

    private String channelOf(String sessionId) {
        if (sessionId == null) {
            return "web";
        }
        if (sessionId.startsWith("feishu:")) return "feishu";
        if (sessionId.startsWith("wechat:")) return "wecom";
        if (sessionId.startsWith("wecom-kf:")) return "wechat_kf";
        if (sessionId.startsWith("dingtalk:")) return "dingtalk";
        if (sessionId.startsWith("api:")) return "api";
        if (sessionId.startsWith("web:") || sessionId.startsWith("webchat:")) return "web";
        return "web";
    }

    private boolean isOrgScopedConversation(String sessionId) {
        return sessionId != null
                && (sessionId.startsWith("feishu:")
                || sessionId.startsWith("wechat:")
                || sessionId.startsWith("wecom-kf:")
                || sessionId.startsWith("dingtalk:")
                || sessionId.startsWith("api:")
                || sessionId.startsWith("web:")
                || sessionId.startsWith("webchat:"));
    }

    private String encodeLegacyTraceId(String sessionId) {
        return "legacy-" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sessionId.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeLegacySessionId(String traceId) {
        try {
            return new String(Base64.getUrlDecoder().decode(traceId.substring("legacy-".length())), StandardCharsets.UTF_8);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trace not found");
        }
    }

    private String writeJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private List<Object> readList(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, LIST_OBJECT_REF);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private Map<String, Object> readMap(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, MAP_OBJECT_REF);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private Map<String, Object> normalizeMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static String redact(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return raw
                .replaceAll("(?i)(authorization|accessToken|refreshToken|api[_-]?key|token|password|secret)(\"?\\s*[:=]\\s*\"?)[^\",}\\s]+", "$1$2[redacted]")
                .replaceAll("(1[3-9]\\d)\\d{4}(\\d{4})", "$1****$2");
    }

    private static Map<String, Object> redactedDetailText(String raw) {
        String text = redact(raw).trim();
        boolean truncated = text.length() > ADMIN_DETAIL_TEXT_MAX_LENGTH;
        String retained = truncated ? text.substring(0, ADMIN_DETAIL_TEXT_MAX_LENGTH) : text;
        return Map.of(
                "text", retained,
                "truncated", truncated,
                "retainedChars", retained.length()
        );
    }

    private static String clip(String value, int max) {
        String text = value == null ? "" : value.trim();
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, Math.max(0, max - 1)) + "…";
    }

    private static String emptyToBlank(String value) {
        return value == null ? "" : value;
    }

    private static String asText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String emptyToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int number(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ex) {
            return 0;
        }
    }

    public record RunLogQuery(Instant from, Instant to, String agentId, String status, String type, String q, int limit) {
    }

    public record ChatRunTraceInput(
            String orgId,
            String userId,
            String sessionId,
            String agentId,
            String question,
            String answer,
            String modelName,
            String activeSkillCode,
            List<String> requestedKnowledgeBaseIds,
            List<String> effectiveKnowledgeBaseIds,
            RagService.RetrievalResult ragResult,
            List<Map<String, Object>> messages,
            List<String> skillCodes,
            List<String> activatedSkillCodes,
            List<?> resolvedSkillVersions,
            List<StageTraceInput> stageTraces,
            List<ModelCallTraceInput> modelCalls,
            List<ToolCallTraceInput> toolCalls,
            AgentWorkflowRuntimeService.RuntimeExecutionResult executionResult,
            int workflowElapsedMs,
            Instant startedAt,
            Instant endedAt) {
    }

    public record CustomerInsightTraceInput(
            String orgId,
            String userId,
            String projectPublicId,
            String customerName,
            String sectionCode,
            String sectionTitle,
            String skillCode,
            String modelProvider,
            String modelName,
            String inputSummary,
            String outputSummary,
            String errorMessage,
            int sourceSnapshotCount,
            boolean success,
            Instant startedAt,
            Instant endedAt) {
    }

    public record StageTraceInput(
            String type,
            String title,
            String status,
            Instant startedAt,
            Instant endedAt,
            int elapsedMs,
            String summary,
            Map<String, Object> metadata) {
    }

    public record ModelCallTraceInput(
            String phase,
            String modelName,
            String status,
            Instant startedAt,
            Instant endedAt,
            int elapsedMs,
            int toolCallCount,
            int outputChars,
            int inputTokens,
            int outputTokens,
            String summary) {
        public ModelCallTraceInput(String phase,
                                   String modelName,
                                   String status,
                                   Instant startedAt,
                                   Instant endedAt,
                                   int elapsedMs,
                                   int toolCallCount,
                                   int outputChars,
                                   String summary) {
            this(phase, modelName, status, startedAt, endedAt, elapsedMs, toolCallCount, outputChars, 0, 0, summary);
        }
    }

    public record ToolCallTraceInput(
            String id,
            String name,
            String arguments,
            String result,
            boolean success,
            Instant startedAt,
            Instant endedAt,
            int elapsedMs) {
    }

    private record ToolTrace(String id, String name, String arguments, String result, boolean success,
                             Instant startedAt, Instant endedAt, Long elapsedMs) {
        ToolTrace withResult(String nextResult, boolean nextSuccess) {
            return new ToolTrace(id, name, arguments, nextResult, nextSuccess, startedAt, endedAt, elapsedMs);
        }

        String summary() {
            if (!success) {
                return "工具失败：" + errorMessage();
            }
            return result == null || result.isBlank() ? "工具已调用。" : clip(result, 220);
        }

        String errorMessage() {
            if (result == null || result.isBlank()) {
                return "工具返回失败状态，但未提供错误详情。";
            }
            return clip(result, 260);
        }

        Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", id);
            payload.put("name", name);
            payload.put("arguments", clip(arguments, 800));
            payload.put("result", clip(result, 1200));
            payload.put("success", success);
            payload.put("status", success ? "SUCCESS" : "FAILED");
            payload.put("errorMessage", success ? "" : errorMessage());
            payload.put("startedAt", startedAt == null ? "" : startedAt.toString());
            payload.put("endedAt", endedAt == null ? "" : endedAt.toString());
            payload.put("elapsedMs", elapsedMs == null ? 0L : Math.max(0L, elapsedMs));
            return payload;
        }
    }
}
