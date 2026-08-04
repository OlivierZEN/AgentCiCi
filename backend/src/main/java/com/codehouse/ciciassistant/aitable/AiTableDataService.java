package com.codehouse.ciciassistant.aitable;

import com.codehouse.ciciassistant.auth.service.AuthService;
import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Same-origin, read-only adapter for the authenticated AI table. Tenant, actor and official
 * access token are always derived from the AgentCiCi session, never from browser input.
 */
@Service
public class AiTableDataService {

    private static final String METADATA_CAPABILITY = "metadata.version.get-current";
    private static final String RECORD_QUERY_CAPABILITY = "runtime.record.query";
    private static final int DEFAULT_LIMIT = 25;
    private static final int MAX_LIMIT = 100;
    private static final int MAX_QUERY_LENGTH = 128;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AuthService authService;
    private final OfficialAccessTokenService officialAccessTokens;
    private final String baseUrl;

    public AiTableDataService(RestClient.Builder restClientBuilder,
                              ObjectMapper objectMapper,
                              AuthService authService,
                              OfficialAccessTokenService officialAccessTokens,
                              @Value("${app.semattice.base-url:}") String baseUrl) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.authService = authService;
        this.officialAccessTokens = officialAccessTokens;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }

    public Map<String, Object> catalog(String companyId, String userId) {
        OfficialAccessTokenService.IssuedToken token = issueToken(companyId, userId);
        Catalog catalog = loadCatalog(token);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("companyName", companyName(companyId, userId));
        result.put("preferenceScope", preferenceScope(companyId, userId));
        result.put("source", "SEMATTICE_LIVE");
        result.put("retrievedAt", Instant.now().toString());
        result.put("objects", catalog.objects());
        return result;
    }

    public Map<String, Object> records(String companyId, String userId, String objectApiName,
                                       int requestedLimit, String after, String query) {
        OfficialAccessTokenService.IssuedToken token = issueToken(companyId, userId);
        Catalog catalog = loadCatalog(token);
        ObjectDefinition object = catalog.byApiName().get(normalizeObjectApiName(objectApiName));
        if (object == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "当前已发布模型中不存在该业务对象");
        }
        int limit = normalizeLimit(requestedLimit);
        String normalizedQuery = normalizeQuery(query);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("object_api_name", object.apiName());
        input.put("limit", limit);
        if (!blank(after)) {
            input.put("after", after.trim());
        }
        if (!normalizedQuery.isBlank()) {
            if (blank(object.searchFieldApiName())) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "该对象尚未配置可查询的文本索引，请联系管理员配置后再搜索");
            }
            input.put("filters", List.of(Map.of(
                    "field", object.searchFieldApiName(), "op", "prefix", "value", normalizedQuery)));
        }
        JsonNode payload = invoke(RECORD_QUERY_CAPABILITY, input, token);
        List<Map<String, Object>> records = new ArrayList<>();
        for (JsonNode record : payload.path("records")) {
            if (!record.path("data").isObject() || blank(record.path("record_id").asText())) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", record.path("record_id").asText());
            row.put("revision", record.path("revision").asLong());
            row.put("data", objectMapper.convertValue(record.path("data"), Map.class));
            records.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("objectApiName", object.apiName());
        result.put("records", records);
        result.put("nextCursor", payload.path("next_cursor").asText(""));
        result.put("retrievedAt", Instant.now().toString());
        result.put("queryFieldLabel", object.searchFieldLabel());
        result.put("searchSupported", !blank(object.searchFieldApiName()));
        return result;
    }

    private OfficialAccessTokenService.IssuedToken issueToken(String companyId, String userId) {
        if (baseUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "业务数据服务尚未配置");
        }
        return authService.issueSematticeOfficialAccess(companyId, userId, officialAccessTokens);
    }

    private Catalog loadCatalog(OfficialAccessTokenService.IssuedToken token) {
        JsonNode payload = invoke(METADATA_CAPABILITY, Map.of(), token);
        Map<String, List<FieldDefinition>> fieldsByObject = new LinkedHashMap<>();
        for (JsonNode field : payload.path("fields")) {
            String objectId = field.path("object_id").asText();
            String apiName = field.path("api_name").asText();
            if (blank(objectId) || blank(apiName)) {
                continue;
            }
            fieldsByObject.computeIfAbsent(objectId, ignored -> new ArrayList<>()).add(new FieldDefinition(
                    apiName,
                    nonBlank(field.path("label").asText(), apiName),
                    field.path("data_type").asText(),
                    field.path("indexed").asBoolean(false),
                    field.path("lifecycle_state").asText()));
        }
        List<Map<String, Object>> objects = new ArrayList<>();
        Map<String, ObjectDefinition> byApiName = new LinkedHashMap<>();
        for (JsonNode object : payload.path("objects")) {
            String objectId = object.path("object_id").asText();
            String apiName = object.path("api_name").asText();
            if (blank(objectId) || blank(apiName)) {
                continue;
            }
            List<FieldDefinition> fields = fieldsByObject.getOrDefault(objectId, List.of()).stream()
                    .filter(field -> !"retired".equalsIgnoreCase(field.lifecycleState()))
                    .limit(32)
                    .toList();
            FieldDefinition searchField = fields.stream()
                    .filter(FieldDefinition::indexed)
                    .filter(field -> "text".equalsIgnoreCase(field.dataType()))
                    .findFirst()
                    .orElse(null);
            ObjectDefinition definition = new ObjectDefinition(
                    apiName,
                    nonBlank(object.path("label").asText(), apiName),
                    object.path("description").asText(),
                    fields,
                    searchField == null ? "" : searchField.apiName(),
                    searchField == null ? "" : searchField.label());
            objects.add(definition.asResponse());
            byApiName.put(apiName, definition);
        }
        return new Catalog(objects, byApiName);
    }

    private JsonNode invoke(String capabilityId, Map<String, Object> input,
                            OfficialAccessTokenService.IssuedToken token) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("capability_id", capabilityId);
        request.put("request_id", "ai-table-" + UUID.randomUUID());
        request.put("input", input);
        try {
            JsonNode response = restClient.post()
                    .uri(baseUrl + "/v1/capabilities/" + capabilityId + "/invoke")
                    .header("Authorization", "Bearer " + token.token())
                    .header("Content-Type", "application/json")
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            if (response != null && "succeeded".equals(response.path("status").asText())
                    && response.path("result").isObject()) {
                return response.path("result");
            }
            String code = response == null ? "" : response.path("error").path("code").asText();
            if ("FORBIDDEN".equalsIgnoreCase(code) || "PERMISSION_DENIED".equalsIgnoreCase(code)) {
                throw new ForbiddenException("当前成员没有读取该业务对象的权限");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "业务数据服务返回异常，请稍后重试");
        } catch (ForbiddenException | ResponseStatusException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "业务数据服务暂时不可用，请稍后重试", exception);
        }
    }

    private String companyName(String companyId, String userId) {
        Object value = authService.currentUser(companyId, userId).get("companyName");
        return value == null ? "当前租户" : String.valueOf(value);
    }

    private static String preferenceScope(String companyId, String userId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((companyId + "\u0000" + userId).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 22);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create local preference scope", exception);
        }
    }

    private static int normalizeLimit(int value) {
        if (value <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(value, MAX_LIMIT);
    }

    private static String normalizeQuery(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > MAX_QUERY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "查询关键词不能超过 128 个字符");
        }
        return normalized;
    }

    private static String normalizeObjectApiName(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("^[a-z][a-z0-9_]{0,95}$")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "当前已发布模型中不存在该业务对象");
        }
        return normalized;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String nonBlank(String value, String fallback) {
        return blank(value) ? fallback : value;
    }

    private record Catalog(List<Map<String, Object>> objects, Map<String, ObjectDefinition> byApiName) { }

    private record ObjectDefinition(String apiName, String label, String description,
                                    List<FieldDefinition> fields, String searchFieldApiName,
                                    String searchFieldLabel) {
        Map<String, Object> asResponse() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("apiName", apiName);
            result.put("label", label);
            result.put("description", description);
            result.put("searchFieldApiName", searchFieldApiName);
            result.put("searchFieldLabel", searchFieldLabel);
            result.put("fields", fields.stream().map(FieldDefinition::asResponse).toList());
            return result;
        }
    }

    private record FieldDefinition(String apiName, String label, String dataType, boolean indexed,
                                   String lifecycleState) {
        Map<String, Object> asResponse() {
            return Map.of(
                    "apiName", apiName,
                    "label", label,
                    "dataType", dataType,
                    "indexed", indexed,
                    "defaultVisible", true);
        }
    }
}
