package com.codehouse.ciciassistant.semattice;

import com.codehouse.ciciassistant.agent.service.AgentServicePrincipalExecutionService;
import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Product-manager review bridge for DEV Autopilot delivery gates.
 * The HUMAN supplies delegation context; the bound product-manager SERVICE is the actor.
 */
@Service
public class SematticeProjectDeliveryReviewToolService {

    public static final String TOOL_NAME = "semattice_project_delivery_review";
    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "task_id", "submission_event_id", "gate", "decision", "summary", "detail", "checklist");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AgentServicePrincipalExecutionService executionPrincipalService;
    private final String baseUrl;

    public SematticeProjectDeliveryReviewToolService(RestClient.Builder restClientBuilder,
                                                     ObjectMapper objectMapper,
                                                     AgentServicePrincipalExecutionService executionPrincipalService,
                                                     @Value("${app.dev-autopilot.base-url:}") String baseUrl) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.executionPrincipalService = executionPrincipalService;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }

    public static String toolDescription() {
        return "使用当前 Agent 显式绑定的产品经理 SERVICE Principal，评审 DEV Autopilot 中待确认的技术设计或完成申请。"
                + "调用前必须先查询实时交付事件并使用准确的 task_id 与 submission_event_id；"
                + "仅支持通过或要求修改，不接受租户、用户、Principal、令牌或目标地址参数。";
    }

    public static JsonNode toolSchema(ObjectMapper objectMapper) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("task_id", Map.of("type", "string", "description", "待评审任务的 Semattice record UUID。"));
        properties.put("submission_event_id", Map.of("type", "string", "description", "待评审 design_submitted 或 completion_requested 事件 UUID。"));
        properties.put("gate", Map.of("type", "string", "enum", List.of("design", "completion"), "description", "评审门禁。"));
        properties.put("decision", Map.of("type", "string", "enum", List.of("approve", "request_changes"), "description", "通过或要求修改。"));
        properties.put("summary", Map.of("type", "string", "minLength", 1, "maxLength", 500, "description", "明确、可审计的评审结论。"));
        properties.put("detail", Map.of("type", "string", "maxLength", 4000, "description", "可选的修改意见或验收说明。"));
        properties.put("checklist", Map.of(
                "type", "array", "maxItems", 20,
                "items", Map.of("type", "string", "maxLength", 300),
                "description", "可选的已核验检查项。"));
        return objectMapper.valueToTree(Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", properties,
                "required", List.of("task_id", "submission_event_id", "gate", "decision", "summary")));
    }

    public String dispatch(String companyId, String userId, String agentId, String argumentsJson) {
        if (baseUrl.isBlank()) {
            return failure("DEV_AUTOPILOT_UNAVAILABLE", "DEV Autopilot 服务未配置，无法执行评审。");
        }
        JsonNode args;
        try {
            args = objectMapper.readTree(argumentsJson == null ? "{}" : argumentsJson);
        } catch (Exception exception) {
            return failure("INVALID_ARGUMENTS", "评审参数不是有效 JSON。");
        }
        String validationFailure = validate(args);
        if (validationFailure != null) {
            return failure("INVALID_ARGUMENTS", validationFailure);
        }

        AgentServicePrincipalExecutionService.ExecutionAuthorization authorization =
                executionPrincipalService.authorizeSemattice(
                        companyId,
                        userId,
                        agentId,
                        List.of("runtime.record.read", "runtime.record.create", "runtime.record.update"),
                        TOOL_NAME);
        OfficialAccessTokenService.IssuedToken token = authorization.token();
        String requestId = "cici-delivery-review-" + UUID.randomUUID();
        String idempotencyKey = "cici-review-" + args.path("submission_event_id").asText()
                + "-" + args.path("decision").asText();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("reviewType", args.path("gate").asText());
        request.put("decision", "approve".equals(args.path("decision").asText()) ? "approved" : "changes_requested");
        request.put("submissionEventId", args.path("submission_event_id").asText());
        request.put("summary", args.path("summary").asText());
        if (args.has("detail")) {
            request.put("detail", args.path("detail").asText());
        }
        if (args.has("checklist")) {
            request.put("checklist", objectMapper.convertValue(args.path("checklist"), List.class));
        }
        try {
            JsonNode response = restClient.post()
                    .uri(baseUrl + "/api/pm/v1/tasks/" + args.path("task_id").asText() + "/reviews")
                    .header("Authorization", "Bearer " + token.token())
                    .header("Content-Type", "application/json")
                    .header("X-Correlation-ID", requestId)
                    .header("Idempotency-Key", idempotencyKey)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !response.path("data").isObject()) {
                return failure("DEV_AUTOPILOT_RESPONSE_INVALID", "DEV Autopilot 返回了无效评审结果。");
            }
            Map<String, Object> result = objectMapper.convertValue(response.path("data"), Map.class);
            result.put("status", "SUCCESS");
            result.put("source", "DEV_AUTOPILOT_LIVE");
            result.put("execution_principal_type", "SERVICE");
            result.put("execution_principal", authorization.servicePrincipalDisplayName());
            result.put("delegation_policy", authorization.delegationPolicy());
            result.put("correlation_id", response.path("correlationId").asText(requestId));
            return objectMapper.writeValueAsString(result);
        } catch (RestClientException exception) {
            return failure("DEV_AUTOPILOT_REVIEW_FAILED", "DEV Autopilot 拒绝或未能完成本次评审。");
        } catch (Exception exception) {
            return failure("DEV_AUTOPILOT_RESPONSE_INVALID", "DEV Autopilot 返回的数据无法安全解析。");
        }
    }

    private static String validate(JsonNode args) {
        if (!args.isObject()) {
            return "评审参数必须是 JSON 对象。";
        }
        var fields = args.fieldNames();
        while (fields.hasNext()) {
            if (!ALLOWED_FIELDS.contains(fields.next())) {
                return "评审参数包含未允许字段。";
            }
        }
        if (!validUuid(args.path("task_id").asText()) || !validUuid(args.path("submission_event_id").asText())) {
            return "task_id 与 submission_event_id 必须是有效 UUID。";
        }
        if (!Set.of("design", "completion").contains(args.path("gate").asText())) {
            return "gate 只允许 design 或 completion。";
        }
        if (!Set.of("approve", "request_changes").contains(args.path("decision").asText())) {
            return "decision 只允许 approve 或 request_changes。";
        }
        String summary = args.path("summary").asText();
        if (summary.isBlank() || summary.length() > 500) {
            return "summary 必填且不能超过 500 字符。";
        }
        if (args.has("detail") && (!args.path("detail").isTextual() || args.path("detail").asText().length() > 4000)) {
            return "detail 必须是最多 4000 字符的文本。";
        }
        if (args.has("checklist")) {
            if (!args.path("checklist").isArray() || args.path("checklist").size() > 20) {
                return "checklist 必须是最多 20 项的数组。";
            }
            for (JsonNode item : args.path("checklist")) {
                if (!item.isTextual() || item.asText().isBlank() || item.asText().length() > 300) {
                    return "checklist 每项必须是最多 300 字符的非空文本。";
                }
            }
        }
        return null;
    }

    private static boolean validUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String failure(String code, String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("status", "FAILED", "code", code, "message", message));
        } catch (Exception ignored) {
            return "{\"status\":\"FAILED\",\"code\":\"" + code + "\"}";
        }
    }
}
