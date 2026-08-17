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
import java.util.Set;
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
 * Controlled update tool for DEV Autopilot delivery objects.
 *
 * <p>Allows the product manager to modify business fields (owner, status, priority,
 * estimate, description, etc.) on existing project/requirement/task records via
 * Semattice runtime.record.update. Only whitelisted fields are accepted; structural
 * fields (code, record_id, project_id, requirement_id, created_by, etc.) are rejected.
 * User confirmation is required before execution.</p>
 */
@Service
public class SematticeProjectDeliveryUpdateToolService {

    public static final String TOOL_NAME = "semattice_project_delivery_update";
    private static final String UPDATE_CAPABILITY_ID = "runtime.record.update";
    private static final String QUERY_CAPABILITY_ID = "runtime.record.query";
    private static final Pattern CONFIRM_UPDATE = Pattern.compile(
            "^\\s*(?:请)?(?:确认|确定)修改(?:项目|需求|任务)[：:]\\s*(.+?)\\s*$", Pattern.CASE_INSENSITIVE);

    private static final Map<String, String> ENTITY_OBJECTS = Map.of(
            "project", "dev_project",
            "requirement", "dev_requirement",
            "task", "dev_task",
            "worklog", "dev_worklog",
            "change", "dev_change",
            "delivery_event", "dev_delivery_event");

    /**
     * Field whitelist per object type. Only these fields may appear in the update patch.
     * Any other field is silently dropped to prevent structural corruption.
     */
    private static final Map<String, Set<String>> ALLOWED_FIELDS = Map.of(
            "dev_project", Set.of("name", "owner", "status", "health", "progress", "release", "description"),
            "dev_requirement", Set.of("title", "status", "priority", "owner", "summary", "acceptance"),
            "dev_task", Set.of("title", "status", "owner", "estimate", "actual_hours", "sequence", "description"),
            "dev_worklog", Set.of("hours", "description", "logged_at"),
            "dev_change", Set.of("title", "status", "impact_analysis", "description"),
            "dev_delivery_event", Set.of("status", "description"));

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AgentServicePrincipalExecutionService executionPrincipalService;
    private final String baseUrl;

    public SematticeProjectDeliveryUpdateToolService(RestClient.Builder restClientBuilder,
                                                      ObjectMapper objectMapper,
                                                      AgentServicePrincipalExecutionService executionPrincipalService,
                                                      @Value("${app.semattice.base-url:}") String baseUrl) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.executionPrincipalService = executionPrincipalService;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }

    public static Optional<UpdateIntent> confirmedIntent(String question) {
        String value = question == null ? "" : question.trim();
        Matcher matcher = CONFIRM_UPDATE.matcher(value);
        if (matcher.matches()) {
            return Optional.of(new UpdateIntent(matcher.group(1)));
        }
        return Optional.empty();
    }

    public String dispatch(String companyId, String userId, String agentId, String argumentsJson) {
        if (baseUrl.isBlank()) {
            return failureJson("SEMATTICE_UNAVAILABLE", "Semattice 服务未配置，无法修改研发交付记录。");
        }
        Optional<UpdateArguments> parsed = parseArguments(argumentsJson);
        if (parsed.isEmpty()) {
            return failureJson("INVALID_ARGUMENTS", "修改操作需要提供对象类型、记录引用和修改字段。");
        }
        UpdateArguments args = parsed.get();
        AgentServicePrincipalExecutionService.ExecutionAuthorization authorization =
                executionPrincipalService.authorizeSemattice(
                        companyId,
                        userId,
                        agentId,
                        List.of("runtime.record.read", "runtime.record.update"),
                        "semattice_project_delivery_update");
        OfficialAccessTokenService.IssuedToken token = authorization.token();
        try {
            return objectMapper.writeValueAsString(update(args, token));
        } catch (RestClientException exception) {
            return failureJson("SEMATTICE_UNAVAILABLE", "Semattice 修改请求失败，请稍后重试。");
        } catch (ResponseStatusException exception) {
            return failureJson("RECORD_NOT_FOUND", exception.getReason() == null ? "未找到对应记录，未修改。" : exception.getReason());
        } catch (Exception exception) {
            return failureJson("SEMATTICE_RESPONSE_INVALID", "Semattice 返回的数据无法安全解析，未确认修改成功。");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> update(UpdateArguments args, OfficialAccessTokenService.IssuedToken token) {
        String objectApiName = ENTITY_OBJECTS.get(args.entityType());
        if (objectApiName == null) {
            return failure("INVALID_ENTITY", "不支持的对象类型：" + args.entityType());
        }

        // Build filtered patch - only whitelisted fields pass through
        Set<String> allowed = ALLOWED_FIELDS.getOrDefault(objectApiName, Set.of());
        Map<String, Object> patch = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : args.updates().entrySet()) {
            if (allowed.contains(entry.getKey())) {
                patch.put(entry.getKey(), entry.getValue());
            }
        }
        if (patch.isEmpty()) {
            return failure("NO_ALLOWED_FIELDS", "提交的字段均不在允许修改范围内，未修改。");
        }

        // Find the target record
        List<Map<String, Object>> matches = queryRecords(objectApiName, token).stream()
                .filter(record -> matchesReference(record, args.reference()))
                .toList();
        if (matches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未找到对应记录，未修改。");
        }
        if (matches.size() > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "找到多个同名记录，请使用编号确认，未修改。");
        }
        Map<String, Object> record = matches.get(0);
        String recordId = (String) record.get("record_id");
        long revision = extractRevision(record);

        // Invoke runtime.record.update with optimistic revision control
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("capability_id", UPDATE_CAPABILITY_ID);
        request.put("request_id", "cici-delivery-update-" + UUID.randomUUID());
        request.put("idempotency_key", "cici-delivery-update-" + UUID.randomUUID());
        request.put("input", Map.of(
                "object_api_name", objectApiName,
                "record_id", recordId,
                "expected_revision", revision,
                "patch", patch));

        JsonNode response = restClient.post()
                .uri(baseUrl + "/v1/capabilities/" + UPDATE_CAPABILITY_ID + "/invoke")
                .header("Authorization", "Bearer " + token.token())
                .header("Content-Type", "application/json")
                .body(request)
                .retrieve()
                .body(JsonNode.class);

        if (response == null || !"succeeded".equals(response.path("status").asText())) {
            String error = response == null ? "未知错误" : response.path("error").path("message").asText("修改失败");
            return failure("UPDATE_FAILED", error);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "succeeded");
        result.put("message", "记录修改成功。");
        result.put("record_id", recordId);
        result.put("object_api_name", objectApiName);
        result.put("updated_fields", patch.keySet());
        result.put("new_revision", response.path("result").path("revision").asLong(revision + 1));
        return result;
    }

    private List<Map<String, Object>> queryRecords(String objectApiName, OfficialAccessTokenService.IssuedToken token) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("capability_id", QUERY_CAPABILITY_ID);
        request.put("request_id", "cici-delivery-update-query-" + UUID.randomUUID());
        request.put("input", Map.of("object_api_name", objectApiName, "limit", 100));
        JsonNode response = restClient.post()
                .uri(baseUrl + "/v1/capabilities/" + QUERY_CAPABILITY_ID + "/invoke")
                .header("Authorization", "Bearer " + token.token())
                .header("Content-Type", "application/json")
                .body(request)
                .retrieve()
                .body(JsonNode.class);
        if (response == null || !"succeeded".equals(response.path("status").asText())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Semattice 记录查询失败，未修改。");
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

    @SuppressWarnings("unchecked")
    private Optional<UpdateArguments> parseArguments(String argumentsJson) {
        try {
            JsonNode root = objectMapper.readTree(argumentsJson == null ? "" : argumentsJson);
            if (!root.isObject() || root.has("tenant_id") || root.has("company_id") || root.has("user_id") || root.has("token")) {
                return Optional.empty();
            }
            String entityType = root.path("entity_type").asText();
            String reference = root.path("reference").asText();
            JsonNode updatesNode = root.path("updates");
            if (!entityType.isBlank() && !reference.isBlank() && ENTITY_OBJECTS.containsKey(entityType) && updatesNode.isObject()) {
                Map<String, Object> updates = objectMapper.convertValue(updatesNode, Map.class);
                if (!updates.isEmpty()) {
                    return Optional.of(new UpdateArguments(entityType, reference, updates));
                }
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

    public record UpdateArguments(String entityType, String reference, Map<String, Object> updates) {
    }

    public record UpdateIntent(String combined) {
    }
}
