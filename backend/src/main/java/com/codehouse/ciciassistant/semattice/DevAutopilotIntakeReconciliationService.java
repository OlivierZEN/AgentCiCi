package com.codehouse.ciciassistant.semattice;

import com.codehouse.ciciassistant.agent.service.AgentServicePrincipalExecutionService;
import com.codehouse.ciciassistant.ai.domain.ChatMessageEntity;
import com.codehouse.ciciassistant.ai.domain.ChatMessageRepository;
import com.codehouse.ciciassistant.ai.domain.ChatSessionEntity;
import com.codehouse.ciciassistant.ai.domain.ChatSessionRepository;
import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import com.codehouse.ciciassistant.platform.service.PlatformAuditService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reconciles a historical delivery record from the confirmed draft persisted in AgentCiCi.
 *
 * <p>The caller supplies only the conversation and record identifiers. Business fields are always
 * recovered from the trusted conversation, then written by the governed product-manager SERVICE
 * through Semattice runtime capabilities. This prevents an admin repair endpoint from becoming an
 * unrestricted record mutation API.</p>
 */
@Service
public class DevAutopilotIntakeReconciliationService {

    private static final String PRODUCT_MANAGER_AGENT_ID = "devautopilot-pm";
    private static final String READ_CAPABILITY_ID = "runtime.record.get";
    private static final String UPDATE_CAPABILITY_ID = "runtime.record.update";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ChatSessionRepository sessions;
    private final ChatMessageRepository messages;
    private final AgentServicePrincipalExecutionService executionPrincipals;
    private final PlatformAuditService audit;
    private final String baseUrl;
    private final Clock clock;

    @Autowired
    public DevAutopilotIntakeReconciliationService(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            ChatSessionRepository sessions,
            ChatMessageRepository messages,
            AgentServicePrincipalExecutionService executionPrincipals,
            PlatformAuditService audit,
            @Value("${app.semattice.base-url:}") String baseUrl) {
        this(restClientBuilder, objectMapper, sessions, messages, executionPrincipals, audit, baseUrl, Clock.systemUTC());
    }

    DevAutopilotIntakeReconciliationService(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            ChatSessionRepository sessions,
            ChatMessageRepository messages,
            AgentServicePrincipalExecutionService executionPrincipals,
            PlatformAuditService audit,
            String baseUrl,
            Clock clock) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.sessions = sessions;
        this.messages = messages;
        this.executionPrincipals = executionPrincipals;
        this.audit = audit;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.clock = clock;
    }

    public ReconciliationView reconcile(String companyId, String actorMemberId, String sessionId, String recordId) {
        if (baseUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Semattice 服务未配置");
        }
        ChatSessionEntity session = sessions.findByIdAndCompanyId(sessionId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到当前租户的受理会话"));
        if (!PRODUCT_MANAGER_AGENT_ID.equals(session.getAgentId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该会话不是 DevAutopilot 产品经理受理会话");
        }
        RecoveredConfirmation recovered = recoverConfirmation(companyId, sessionId, recordId);
        SematticeProjectDeliveryWriteToolService.CreateIntent intent =
                SematticeProjectDeliveryWriteToolService.confirmedIntent(
                                recovered.confirmation(), recovered.messagesBeforeConfirmation(), sessionId, objectMapper)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.UNPROCESSABLE_ENTITY, "无法从已确认草稿恢复结构化受理内容"));
        String objectApiName = objectApiName(intent.operation());
        AgentServicePrincipalExecutionService.ExecutionAuthorization authorization =
                executionPrincipals.authorizeSemattice(
                        companyId,
                        actorMemberId,
                        PRODUCT_MANAGER_AGENT_ID,
                        List.of("runtime.record.read", "runtime.record.update"),
                        "devautopilot_intake_reconciliation");
        OfficialAccessTokenService.IssuedToken token = authorization.token();
        JsonNode current = readRecord(objectApiName, recordId, token, "before");
        JsonNode currentData = current.path("data");
        validateTarget(currentData, intent, sessionId, recovered.receipt());

        Map<String, Object> semanticPatch = semanticPatch(intent);
        String contentDigest = digest(semanticPatch);
        if (matchesSemanticPatch(currentData, semanticPatch)) {
            return new ReconciliationView("UNCHANGED", objectApiName, recordId,
                    current.path("revision").asLong(), contentDigest, true, List.copyOf(semanticPatch.keySet()));
        }

        Map<String, Object> writePatch = materializePatch(
                currentData, semanticPatch, authorization.delegatedByPrincipalId(), sessionId);
        long expectedRevision = current.path("revision").asLong();
        long updatedRevision = updateRecord(
                objectApiName, recordId, expectedRevision, writePatch, token, contentDigest);
        JsonNode verified = readRecord(objectApiName, recordId, token, "after");
        if (verified.path("revision").asLong() != updatedRevision
                || !matchesExactPatch(verified.path("data"), writePatch)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Semattice 纠正后字段回读不一致，不能确认修复成功");
        }
        audit.log(companyId, actorMemberId, "ORG_ADMIN", "devautopilot.intake.reconciled",
                objectApiName, recordId,
                "session=" + sessionId + "; revision=" + updatedRevision + "; digest=" + contentDigest);
        return new ReconciliationView("UPDATED", objectApiName, recordId,
                updatedRevision, contentDigest, true, List.copyOf(writePatch.keySet()));
    }

    private RecoveredConfirmation recoverConfirmation(String companyId, String sessionId, String recordId) {
        List<ChatMessageEntity> history = messages.findByCompanyIdAndSessionIdOrderByCreatedAtAsc(companyId, sessionId);
        int receiptIndex = -1;
        for (int index = history.size() - 1; index >= 0; index--) {
            ChatMessageEntity message = history.get(index);
            if ("assistant".equals(message.getRoleCode()) && message.getContent().contains(recordId)) {
                receiptIndex = index;
                break;
            }
        }
        if (receiptIndex < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "会话中不存在该记录的成功回执");
        }
        int confirmationIndex = -1;
        for (int index = receiptIndex - 1; index >= 0; index--) {
            ChatMessageEntity message = history.get(index);
            if ("user".equals(message.getRoleCode()) && isConfirmation(message.getContent())) {
                confirmationIndex = index;
                break;
            }
        }
        if (confirmationIndex < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "成功回执之前不存在用户确认指令");
        }
        List<Map<String, Object>> beforeConfirmation = new ArrayList<>();
        for (int index = 0; index < confirmationIndex; index++) {
            ChatMessageEntity item = history.get(index);
            if (Set.of("user", "assistant").contains(item.getRoleCode())) {
                beforeConfirmation.add(Map.of("role", item.getRoleCode(), "content", item.getContent()));
            }
        }
        return new RecoveredConfirmation(
                history.get(confirmationIndex).getContent(),
                List.copyOf(beforeConfirmation),
                history.get(receiptIndex).getContent());
    }

    private boolean isConfirmation(String content) {
        String normalized = content == null ? "" : content.replaceAll("\\s+", "");
        return normalized.matches("^(?:请)?(?:确认|确定)(?:提交|创建)(?:此|该|本)?(?:需求|缺陷|变更).*$");
    }

    private String objectApiName(String operation) {
        return switch (operation) {
            case "create_requirement" -> "dev_requirement";
            case "create_change" -> "dev_change";
            case "create_defect" -> "dev_defect";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持纠正需求、变更或缺陷受理记录");
        };
    }

    private void validateTarget(JsonNode currentData,
                                SematticeProjectDeliveryWriteToolService.CreateIntent intent,
                                String sessionId,
                                String receipt) {
        if (!receipt.contains(currentData.path("code").asText("")) && !receipt.contains(intent.title())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "成功回执与目标记录内容不一致");
        }
        JsonNode currentIntake = currentData.path("intake");
        if (!currentIntake.isObject() || !sessionId.equals(currentIntake.path("conversation_id").asText())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "目标记录不属于该受理会话");
        }
        String expectedClassification = switch (intent.operation()) {
            case "create_requirement" -> "requirement";
            case "create_change" -> "change";
            case "create_defect" -> "defect";
            default -> "";
        };
        if (!expectedClassification.equals(currentIntake.path("classification").asText())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "目标记录类型与受理草稿不一致");
        }
        String currentTitle = "create_change".equals(intent.operation())
                ? currentData.path("summary").asText() : currentData.path("title").asText();
        if (!intent.title().equals(currentTitle)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "目标记录标题与已确认草稿不一致");
        }
    }

    private Map<String, Object> semanticPatch(SematticeProjectDeliveryWriteToolService.CreateIntent intent) {
        Map<String, Object> patch = new LinkedHashMap<>();
        switch (intent.operation()) {
            case "create_requirement" -> {
                patch.put("summary", intent.description());
                patch.put("priority", intent.priority());
                patch.put("acceptance", intent.acceptanceCriteria());
            }
            case "create_change" -> {
                patch.put("summary", intent.title());
                patch.put("impact", intent.impactAnalysis());
            }
            case "create_defect" -> {
                patch.put("description", intent.description());
                patch.put("severity", intent.severity());
                patch.put("priority", intent.priority());
                patch.put("environment", intent.environment());
                patch.put("reproduction_steps", intent.reproductionSteps());
                patch.put("expected_result", intent.expectedResult());
                patch.put("actual_result", intent.actualResult());
            }
            default -> throw new IllegalStateException("unsupported intake operation");
        }
        patch.put("intake", intent.intake());
        return Map.copyOf(patch);
    }

    private boolean matchesSemanticPatch(JsonNode currentData, Map<String, Object> semanticPatch) {
        for (Map.Entry<String, Object> field : semanticPatch.entrySet()) {
            if ("intake".equals(field.getKey())) {
                JsonNode expectedIntake = objectMapper.valueToTree(field.getValue());
                for (var fields = expectedIntake.fields(); fields.hasNext(); ) {
                    var expectedField = fields.next();
                    if (!expectedField.getValue().equals(currentData.path("intake").path(expectedField.getKey()))) {
                        return false;
                    }
                }
            } else if (!objectMapper.valueToTree(field.getValue()).equals(currentData.path(field.getKey()))) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> materializePatch(JsonNode currentData,
                                                  Map<String, Object> semanticPatch,
                                                  String actorPrincipalId,
                                                  String sessionId) {
        Map<String, Object> patch = new LinkedHashMap<>(semanticPatch);
        Map<String, Object> mergedIntake = currentData.path("intake").isObject()
                ? objectMapper.convertValue(currentData.path("intake"), Map.class)
                : new LinkedHashMap<>();
        mergedIntake.putAll((Map<String, Object>) semanticPatch.get("intake"));
        mergedIntake.put("reconciled_at", Instant.now(clock).toString());
        mergedIntake.put("reconciled_by_principal_id", actorPrincipalId);
        mergedIntake.put("reconciliation_source", "confirmed_conversation_draft");
        mergedIntake.put("conversation_id", sessionId);
        patch.put("intake", mergedIntake);
        return patch;
    }

    private JsonNode readRecord(String objectApiName,
                                String recordId,
                                OfficialAccessTokenService.IssuedToken token,
                                String phase) {
        Map<String, Object> request = Map.of(
                "capability_id", READ_CAPABILITY_ID,
                "request_id", "cici-intake-reconcile-" + phase + "-" + UUID.randomUUID(),
                "input", Map.of("object_api_name", objectApiName, "record_id", recordId));
        JsonNode response = restClient.post()
                .uri(baseUrl + "/v1/capabilities/" + READ_CAPABILITY_ID + "/invoke")
                .header("Authorization", "Bearer " + token.token())
                .header("Content-Type", "application/json")
                .body(request)
                .retrieve()
                .body(JsonNode.class);
        JsonNode record = response == null ? null : response.path("result");
        if (response == null || !"succeeded".equals(response.path("status").asText())
                || record == null || !recordId.equals(record.path("record_id").asText())
                || record.path("revision").asLong() < 1 || !record.path("data").isObject()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Semattice 记录回读失败");
        }
        return record;
    }

    private long updateRecord(String objectApiName,
                              String recordId,
                              long expectedRevision,
                              Map<String, Object> patch,
                              OfficialAccessTokenService.IssuedToken token,
                              String contentDigest) {
        String operationId = "cici-intake-reconcile-" + recordId + "-" + expectedRevision + "-" + contentDigest;
        Map<String, Object> request = Map.of(
                "capability_id", UPDATE_CAPABILITY_ID,
                "request_id", operationId,
                "idempotency_key", operationId,
                "input", Map.of(
                        "object_api_name", objectApiName,
                        "record_id", recordId,
                        "expected_revision", expectedRevision,
                        "patch", patch));
        JsonNode response = restClient.post()
                .uri(baseUrl + "/v1/capabilities/" + UPDATE_CAPABILITY_ID + "/invoke")
                .header("Authorization", "Bearer " + token.token())
                .header("Content-Type", "application/json")
                .body(request)
                .retrieve()
                .body(JsonNode.class);
        long revision = response == null ? 0 : response.path("result").path("revision").asLong();
        if (response == null || !"succeeded".equals(response.path("status").asText())
                || revision <= expectedRevision) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Semattice 未返回有效纠正回执");
        }
        return revision;
    }

    private boolean matchesExactPatch(JsonNode verifiedData, Map<String, Object> patch) {
        for (Map.Entry<String, Object> field : patch.entrySet()) {
            if (!objectMapper.valueToTree(field.getValue()).equals(verifiedData.path(field.getKey()))) {
                return false;
            }
        }
        return true;
    }

    private String digest(Map<String, Object> semanticPatch) {
        try {
            byte[] canonical = objectMapper.writeValueAsBytes(semanticPatch);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot digest delivery intake", exception);
        }
    }

    private record RecoveredConfirmation(
            String confirmation,
            List<Map<String, Object>> messagesBeforeConfirmation,
            String receipt) {
    }

    public record ReconciliationView(
            String status,
            String objectApiName,
            String recordId,
            long revision,
            String contentDigest,
            boolean readbackVerified,
            List<String> updatedFields) {
    }
}
