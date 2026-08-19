package com.codehouse.ciciassistant.semattice;

import com.codehouse.ciciassistant.agent.service.AgentServicePrincipalExecutionService;
import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
            "^(?:请)?(?:确认|确定)将(项目|需求|任务)\\s+(.+?)\\s+的(.+?)修改为\\s*(.+)$",
            Pattern.CASE_INSENSITIVE);

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

    private static final Map<String, Map<String, FieldDefinition>> CONFIRMABLE_FIELDS = Map.of(
            "project", Map.of(
                    "名称", new FieldDefinition("name", ValueType.TEXT),
                    "负责人", new FieldDefinition("owner", ValueType.TEXT),
                    "状态", new FieldDefinition("status", ValueType.TEXT),
                    "健康度", new FieldDefinition("health", ValueType.TEXT),
                    "进度", new FieldDefinition("progress", ValueType.DECIMAL),
                    "发布版本", new FieldDefinition("release", ValueType.TEXT),
                    "描述", new FieldDefinition("description", ValueType.TEXT)),
            "requirement", Map.of(
                    "标题", new FieldDefinition("title", ValueType.TEXT),
                    "状态", new FieldDefinition("status", ValueType.TEXT),
                    "优先级", new FieldDefinition("priority", ValueType.TEXT),
                    "负责人", new FieldDefinition("owner", ValueType.TEXT),
                    "摘要", new FieldDefinition("summary", ValueType.TEXT),
                    "验收标准", new FieldDefinition("acceptance", ValueType.TEXT)),
            "task", Map.of(
                    "标题", new FieldDefinition("title", ValueType.TEXT),
                    "状态", new FieldDefinition("status", ValueType.TEXT),
                    "负责人", new FieldDefinition("owner", ValueType.TEXT),
                    "预估工时", new FieldDefinition("estimate", ValueType.DECIMAL),
                    "实际工时", new FieldDefinition("actual_hours", ValueType.DECIMAL),
                    "顺序", new FieldDefinition("sequence", ValueType.INTEGER),
                    "描述", new FieldDefinition("description", ValueType.TEXT)));

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
        String value = normalizeInstruction(question);
        Matcher matcher = CONFIRM_UPDATE.matcher(value);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String entityType = entityTypeForLabel(matcher.group(1));
        String reference = matcher.group(2).trim();
        String fieldLabel = matcher.group(3).trim();
        String requestedValue = matcher.group(4).trim();
        FieldDefinition field = CONFIRMABLE_FIELDS.getOrDefault(entityType, Map.of()).get(fieldLabel);
        if (field == null || reference.isBlank() || requestedValue.isBlank()
                || reference.length() > 256 || requestedValue.length() > 2000) {
            return Optional.empty();
        }
        try {
            return Optional.of(new UpdateIntent(
                    entityType, reference, fieldLabel, field.apiName(), field.convert(requestedValue)));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static String normalizeInstruction(String question) {
        String normalized = question == null ? "" : question.trim();
        normalized = normalized.replaceAll("^[`\\\"“”‘’']+", "");
        normalized = normalized.replaceAll("[`\\\"“”‘’。！？!?]+$", "");
        return normalized.trim();
    }

    private static String entityTypeForLabel(String label) {
        return switch (label) {
            case "项目" -> "project";
            case "需求" -> "requirement";
            case "任务" -> "task";
            default -> "";
        };
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

        // Find exactly one target by record ID, business code or display name/title.
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

        String correlationId = correlationId(args);
        if (matchesPatch(record, patch)) {
            return successReceipt(args, objectApiName, recordId, revision, correlationId, patch, false);
        }

        // Invoke runtime.record.update with optimistic revision control
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("capability_id", UPDATE_CAPABILITY_ID);
        request.put("request_id", correlationId);
        request.put("idempotency_key", correlationId);
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

        JsonNode updated = response.path("result");
        long updatedRevision = updated.path("revision").asLong();
        if (!recordId.equals(updated.path("record_id").asText())
                || updatedRevision <= revision
                || !matchesPatch(objectMapper.convertValue(updated.path("data"), Map.class), patch)) {
            return failure("UPDATE_READBACK_INVALID", "Semattice 更新回执不完整，未确认修改成功。");
        }

        Map<String, Object> verified = queryRecords(objectApiName, token).stream()
                .filter(candidate -> recordId.equals(candidate.get("record_id")))
                .findFirst()
                .orElse(Map.of());
        if (extractRevision(verified) < updatedRevision || !matchesPatch(verified, patch)) {
            return failure("UPDATE_READBACK_INVALID", "Semattice 写后查询与修改内容不一致，未确认修改成功。");
        }
        return successReceipt(args, objectApiName, recordId, extractRevision(verified),
                response.path("correlationId").asText(correlationId), patch, true);
    }

    private String correlationId(UpdateArguments args) {
        try {
            byte[] stable = objectMapper.writeValueAsBytes(Map.of(
                    "entity_type", args.entityType(),
                    "reference", args.reference(),
                    "updates", args.updates()));
            return "cici-delivery-update-" + UUID.nameUUIDFromBytes(stable);
        } catch (Exception exception) {
            byte[] fallback = (args.entityType() + "\n" + args.reference() + "\n" + args.updates())
                    .getBytes(StandardCharsets.UTF_8);
            return "cici-delivery-update-" + UUID.nameUUIDFromBytes(fallback);
        }
    }

    private Map<String, Object> successReceipt(UpdateArguments args, String objectApiName,
                                                String recordId, long revision, String correlationId,
                                                Map<String, Object> patch, boolean changed) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "SUCCESS");
        result.put("source", "SEMATTICE_LIVE");
        result.put("message", changed ? "记录修改成功。" : "记录已经是目标值，无需重复修改。");
        result.put("entity_type", args.entityType());
        result.put("reference", args.reference());
        result.put("record_id", recordId);
        result.put("object_api_name", objectApiName);
        result.put("revision", revision);
        result.put("correlation_id", correlationId);
        result.put("updated_fields", patch.keySet());
        result.put("verified_values", patch);
        result.put("changed", changed);
        result.put("readback_verified", true);
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
        return Set.of("record_id", "code", "name", "title").stream()
                .map(record::get)
                .filter(java.util.Objects::nonNull)
                .map(Object::toString)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .anyMatch(ref::equals);
    }

    private boolean matchesPatch(Map<String, Object> record, Map<String, Object> patch) {
        for (Map.Entry<String, Object> entry : patch.entrySet()) {
            JsonNode expected = objectMapper.valueToTree(entry.getValue());
            JsonNode actual = objectMapper.valueToTree(record.get(entry.getKey()));
            boolean equal = expected.isNumber() && actual.isNumber()
                    ? expected.decimalValue().compareTo(actual.decimalValue()) == 0
                    : expected.equals(actual);
            if (!equal) {
                return false;
            }
        }
        return true;
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

    public record UpdateIntent(String entityType, String reference, String fieldLabel,
                               String field, Object value) {
        public String toArguments(ObjectMapper objectMapper) {
            try {
                return objectMapper.writeValueAsString(Map.of(
                        "entity_type", entityType,
                        "reference", reference,
                        "updates", Map.of(field, value)));
            } catch (Exception exception) {
                throw new IllegalArgumentException("无法生成修改工具参数", exception);
            }
        }
    }

    private record FieldDefinition(String apiName, ValueType valueType) {
        Object convert(String value) {
            return switch (valueType) {
                case TEXT -> value;
                case DECIMAL -> new BigDecimal(value);
                case INTEGER -> Long.valueOf(value);
            };
        }
    }

    private enum ValueType { TEXT, DECIMAL, INTEGER }
}
