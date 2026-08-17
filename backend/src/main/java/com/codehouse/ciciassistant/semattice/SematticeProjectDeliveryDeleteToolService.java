package com.codehouse.ciciassistant.semattice;

import com.codehouse.ciciassistant.agent.service.AgentServicePrincipalExecutionService;
import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Controlled delete (trash) tool for DEV Autopilot delivery objects.
 *
 * <p>Moves a record to the recycle bin via Semattice runtime.record.delete.
 * The record can be restored within 30 days. No backup/approval/dual-phase
 * prerequisites are required — just user confirmation and RBAC.</p>
 */
@Service
public class SematticeProjectDeliveryDeleteToolService {

    public static final String TOOL_NAME = "semattice_project_delivery_delete";
    private static final String DELETE_CAPABILITY_ID = "runtime.record.delete";
    private static final String QUERY_CAPABILITY_ID = "runtime.record.query";
    private static final Pattern CONFIRM_DELETE = Pattern.compile(
            "^\\s*(?:请)?(?:确认|确定)删除(项目|需求|任务|工时|变更|交付事件)[：:]\\s*(.+?)\\s*$",
            Pattern.CASE_INSENSITIVE);

    private static final Map<String, String> ENTITY_OBJECTS = Map.of(
            "project", "dev_project",
            "requirement", "dev_requirement",
            "task", "dev_task",
            "worklog", "dev_worklog",
            "change", "dev_change",
            "delivery_event", "dev_delivery_event");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AgentServicePrincipalExecutionService executionPrincipalService;
    private final String baseUrl;

    public SematticeProjectDeliveryDeleteToolService(RestClient.Builder restClientBuilder,
                                                      ObjectMapper objectMapper,
                                                      AgentServicePrincipalExecutionService executionPrincipalService,
                                                      @Value("${app.semattice.base-url:}") String baseUrl) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.executionPrincipalService = executionPrincipalService;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }

    public static Optional<DeleteIntent> confirmedIntent(String question) {
        String value = question == null ? "" : question.trim();
        Matcher matcher = CONFIRM_DELETE.matcher(value);
        if (matcher.matches()) {
            return Optional.of(new DeleteIntent(entityTypeForLabel(matcher.group(1)), matcher.group(2)));
        }
        return Optional.empty();
    }

    private static String entityTypeForLabel(String label) {
        return switch (label) {
            case "项目" -> "project";
            case "需求" -> "requirement";
            case "任务" -> "task";
            case "工时" -> "worklog";
            case "变更" -> "change";
            case "交付事件" -> "delivery_event";
            default -> "";
        };
    }

    public String dispatch(String companyId, String userId, String agentId, String argumentsJson) {
        if (baseUrl.isBlank()) {
            return failureJson("SEMATTICE_UNAVAILABLE", "Semattice 服务未配置，无法删除研发交付记录。");
        }
        Optional<DeleteIntent> intent = parseArguments(argumentsJson);
        if (intent.isEmpty()) {
            return failureJson("INVALID_ARGUMENTS", "删除操作需要提供对象类型和记录引用。");
        }
        AgentServicePrincipalExecutionService.ExecutionAuthorization authorization =
                executionPrincipalService.authorizeSemattice(
                        companyId,
                        userId,
                        agentId,
                        List.of("runtime.record.read", "runtime.record.delete"),
                        "semattice_project_delivery_delete");
        OfficialAccessTokenService.IssuedToken token = authorization.token();
        try {
            return objectMapper.writeValueAsString(delete(intent.get(), token));
        } catch (RestClientException exception) {
            return failureJson("SEMATTICE_UNAVAILABLE", "Semattice 删除请求失败，请稍后重试。");
        } catch (ResponseStatusException exception) {
            return failureJson("RECORD_NOT_FOUND", exception.getReason() == null ? "未找到对应记录，未删除。" : exception.getReason());
        } catch (Exception exception) {
            return failureJson("SEMATTICE_RESPONSE_INVALID", "Semattice 返回的数据无法安全解析，未确认删除成功。");
        }
    }

    private Map<String, Object> delete(DeleteIntent intent, OfficialAccessTokenService.IssuedToken token) {
        String objectApiName = ENTITY_OBJECTS.get(intent.entityType());
        if (objectApiName == null) {
            return failure("INVALID_ENTITY", "不支持的对象类型：" + intent.entityType());
        }
        List<Map<String, Object>> matches = queryRecords(objectApiName, token).stream()
                .filter(record -> matchesReference(record, intent.reference()))
                .toList();
        if (matches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未找到对应记录，未删除。");
        }
        if (matches.size() > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "找到多个同名记录，请使用编号确认，未删除。");
        }
        Map<String, Object> record = matches.get(0);
        String recordId = (String) record.get("record_id");
        long revision = extractRevision(record);

        String requestId = "cici-delivery-delete-" + UUID.randomUUID();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("capability_id", DELETE_CAPABILITY_ID);
        request.put("request_id", requestId);
        request.put("idempotency_key", "cici-delivery-delete-" + UUID.randomUUID());
        request.put("input", Map.of(
                "object_api_name", objectApiName,
                "record_id", recordId,
                "expected_revision", revision));

        JsonNode response = restClient.post()
                .uri(baseUrl + "/v1/capabilities/" + DELETE_CAPABILITY_ID + "/invoke")
                .header("Authorization", "Bearer " + token.token())
                .header("Content-Type", "application/json")
                .body(request)
                .retrieve()
                .body(JsonNode.class);

        if (response == null || !"succeeded".equals(response.path("status").asText())) {
            String error = response == null ? "未知错误" : response.path("error").path("message").asText("删除失败");
            return failure("DELETE_FAILED", error);
        }

        JsonNode deleted = response.path("result");
        String deletedRecordId = deleted.path("record_id").asText();
        String lifecycleState = deleted.path("lifecycle_state").asText();
        long deletedRevision = deleted.path("revision").asLong();
        if (!recordId.equals(deletedRecordId) || !"trashed".equals(lifecycleState) || deletedRevision <= revision) {
            return failure("DELETE_READBACK_INVALID", "Semattice 删除回读不完整，未确认删除成功。");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "SUCCESS");
        result.put("source", "SEMATTICE_LIVE");
        result.put("message", "记录已移入回收站，30 天内可恢复。");
        result.put("record_id", recordId);
        result.put("object_api_name", objectApiName);
        result.put("revision", deletedRevision);
        result.put("correlation_id", response.path("correlationId").asText(requestId));
        result.put("lifecycle_state", lifecycleState);
        result.put("readback_verified", true);
        return result;
    }

    private List<Map<String, Object>> queryRecords(String objectApiName, OfficialAccessTokenService.IssuedToken token) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("capability_id", QUERY_CAPABILITY_ID);
        request.put("request_id", "cici-delivery-delete-query-" + UUID.randomUUID());
        request.put("input", Map.of("object_api_name", objectApiName, "limit", 100));
        JsonNode response = restClient.post()
                .uri(baseUrl + "/v1/capabilities/" + QUERY_CAPABILITY_ID + "/invoke")
                .header("Authorization", "Bearer " + token.token())
                .header("Content-Type", "application/json")
                .body(request)
                .retrieve()
                .body(JsonNode.class);
        if (response == null || !"succeeded".equals(response.path("status").asText())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Semattice 记录查询失败，未删除。");
        }
        return java.util.stream.StreamSupport.stream(response.path("result").path("records").spliterator(), false)
                .filter(item -> item.path("data").isObject())
                .map(item -> {
                    Map<String, Object> record = objectMapper.convertValue(item.path("data"), Map.class);
                    record.put("record_id", item.path("record_id").asText());
                    record.put("revision", item.path("revision").asLong());
                    return record;
                })
                .toList();
    }

    private boolean matchesReference(Map<String, Object> record, String reference) {
        String ref = reference.trim().toLowerCase(Locale.ROOT);
        for (Object value : record.values()) {
            if (value != null && value.toString().toLowerCase(Locale.ROOT).contains(ref)) {
                return true;
            }
        }
        return false;
    }

    private long extractRevision(Map<String, Object> record) {
        Object revision = record.get("revision");
        if (revision instanceof Number number) {
            return number.longValue();
        }
        return 1L;
    }

    private Optional<DeleteIntent> parseArguments(String argumentsJson) {
        try {
            JsonNode root = objectMapper.readTree(argumentsJson == null ? "" : argumentsJson);
            if (!root.isObject() || root.has("tenant_id") || root.has("company_id") || root.has("user_id") || root.has("token")) {
                return Optional.empty();
            }
            String entityType = root.path("entity_type").asText();
            String reference = root.path("reference").asText();
            if (!entityType.isBlank() && !reference.isBlank() && ENTITY_OBJECTS.containsKey(entityType)) {
                return Optional.of(new DeleteIntent(entityType, reference));
            }
        } catch (Exception ignored) {
            // Return the stable invalid-arguments result below.
        }
        return Optional.empty();
    }

    private static Map<String, Object> failure(String code, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "failed");
        result.put("error", Map.of("code", code, "message", message));
        return result;
    }

    private String failureJson(String code, String message) {
        try {
            return objectMapper.writeValueAsString(failure(code, message));
        } catch (Exception ignored) {
            return "{\"status\":\"failed\",\"error\":{\"code\":\"" + code + "\"}}";
        }
    }

    public record DeleteIntent(String entityType, String reference) {
        public DeleteIntent(String combined) {
            this(parseEntityType(combined), combined);
        }
        private static String parseEntityType(String combined) {
            String lower = combined.toLowerCase(Locale.ROOT);
            if (lower.contains("项目") || lower.contains("project")) return "project";
            if (lower.contains("需求") || lower.contains("requirement")) return "requirement";
            if (lower.contains("任务") || lower.contains("task")) return "task";
            if (lower.contains("工时") || lower.contains("worklog")) return "worklog";
            if (lower.contains("变更") || lower.contains("change")) return "change";
            if (lower.contains("交付事件") || lower.contains("delivery")) return "delivery_event";
            return "project";
        }

        public String toArguments(ObjectMapper objectMapper) {
            try {
                return objectMapper.writeValueAsString(Map.of(
                        "entity_type", entityType,
                        "reference", reference));
            } catch (Exception exception) {
                throw new IllegalArgumentException("无法生成删除工具参数", exception);
            }
        }
    }
}
