package com.codehouse.ciciassistant.embed.service;

import com.codehouse.ciciassistant.embed.domain.MeetingSessionEntity;
import com.codehouse.ciciassistant.integration.service.CloudccAccessTokenService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CloudccMeetingWritebackConnector {

    private static final TypeReference<List<Map<String, Object>>> LIST_MAP_REF = new TypeReference<>() {};

    private final CloudccAccessTokenService tokenService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public CloudccMeetingWritebackConnector(CloudccAccessTokenService tokenService, ObjectMapper objectMapper) {
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public Map<String, Object> writeback(MeetingSessionEntity session, List<Map<String, Object>> selectedItems) {
        if (!"cloudcc".equalsIgnoreCase(session.getSource())) {
            throw new IllegalArgumentException("Only CloudCC writeback is supported in this connector");
        }
        if (selectedItems == null || selectedItems.isEmpty()) {
            throw new IllegalArgumentException("At least one writeback item must be selected");
        }
        CloudccAccessTokenService.CloudccSessionContext ctx = tokenService
                .getSessionContext(session.getCompanyId(), session.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("无法获取 CloudCC 访问令牌，请确认集成应用和 run-as 用户绑定已配置。"));

        List<Map<String, Object>> completed = new ArrayList<>();
        List<RollbackTarget> rollbackTargets = new ArrayList<>();
        try {
            for (Map<String, Object> item : selectedItems) {
                Map<String, Object> operation = asMap(item.get("writeback"));
                String serviceName = stringValue(operation.get("serviceName"));
                String objectApiName = stringValue(operation.get("objectApiName"));
                List<Map<String, Object>> data = dataList(operation.get("data"));
                if (serviceName.isBlank() || objectApiName.isBlank() || data.isEmpty()) {
                    throw new IllegalArgumentException("Writeback item is missing connector operation: " + stringValue(item.get("id")));
                }
                JsonNode root = callCommon(session, ctx, serviceName, objectApiName, data);
                List<String> remoteIds = extractRemoteIds(root);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("itemId", stringValue(item.get("id")));
                row.put("type", stringValue(item.get("type")));
                row.put("serviceName", serviceName);
                row.put("objectApiName", objectApiName);
                row.put("remoteIds", remoteIds);
                row.put("returnCode", root.path("returnCode").asText(""));
                row.put("returnInfo", root.path("returnInfo").asText(""));
                completed.add(row);
                if (isInsert(serviceName) && !remoteIds.isEmpty()) {
                    rollbackTargets.add(new RollbackTarget(objectApiName, remoteIds));
                }
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "SUCCESS");
            result.put("message", "已写回 " + completed.size() + " 个候选。");
            result.put("items", completed);
            result.put("recordedAt", Instant.now().toString());
            return result;
        } catch (RuntimeException ex) {
            List<Map<String, Object>> rollback = rollbackInsertedRecords(session, ctx, rollbackTargets);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "FAILED");
            result.put("message", ex.getMessage());
            result.put("items", completed);
            result.put("rollback", rollback);
            result.put("recordedAt", Instant.now().toString());
            throw new WritebackFailedException(result, ex);
        }
    }

    private JsonNode callCommon(MeetingSessionEntity session,
                                CloudccAccessTokenService.CloudccSessionContext ctx,
                                String serviceName,
                                String objectApiName,
                                List<Map<String, Object>> data) {
        try {
            JsonNode first = sendCommon(ctx.baseUrl(), ctx.accessToken(), serviceName, objectApiName, data);
            if (first.path("_httpStatus").asInt(200) == 401) {
                tokenService.invalidateSessionContext(session.getCompanyId(), session.getUserId());
                CloudccAccessTokenService.CloudccSessionContext fresh = tokenService
                        .getSessionContext(session.getCompanyId(), session.getUserId())
                        .orElseThrow(() -> new IllegalArgumentException("CloudCC 令牌刷新失败，请重新绑定账号。"));
                return checked(sendCommon(fresh.baseUrl(), fresh.accessToken(), serviceName, objectApiName, data), serviceName, objectApiName);
            }
            return checked(first, serviceName, objectApiName);
        } catch (WritebackFailedException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("调用 CloudCC API 失败：" + ex.getMessage(), ex);
        }
    }

    private JsonNode sendCommon(String baseUrl,
                                String accessToken,
                                String serviceName,
                                String objectApiName,
                                List<Map<String, Object>> data) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("serviceName", serviceName);
        body.put("objectApiName", objectApiName);
        body.put("data", objectMapper.writeValueAsString(data));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ensureBaseUrl(baseUrl) + "/openApi/common"))
                .header("Content-Type", "application/json")
                .header("accessToken", accessToken)
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = response.body() == null || response.body().isBlank()
                ? objectMapper.createObjectNode()
                : objectMapper.readTree(response.body());
        if (root instanceof ObjectNode objectNode) {
            objectNode.put("_httpStatus", response.statusCode());
        }
        return root;
    }

    private JsonNode checked(JsonNode root, String serviceName, String objectApiName) {
        if (root.path("_httpStatus").asInt(200) >= 400) {
            throw new IllegalArgumentException("CloudCC " + serviceName + " " + objectApiName
                    + " HTTP " + root.path("_httpStatus").asInt());
        }
        if (!root.path("result").asBoolean(false)) {
            String message = root.path("returnInfo").asText("unknown");
            throw new IllegalArgumentException("CloudCC " + serviceName + " " + objectApiName + " failed: " + message);
        }
        return root;
    }

    private List<Map<String, Object>> rollbackInsertedRecords(MeetingSessionEntity session,
                                                              CloudccAccessTokenService.CloudccSessionContext ctx,
                                                              List<RollbackTarget> targets) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = targets.size() - 1; i >= 0; i--) {
            RollbackTarget target = targets.get(i);
            List<Map<String, Object>> data = target.ids().stream()
                    .map(id -> Map.<String, Object>of("id", id))
                    .toList();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("objectApiName", target.objectApiName());
            row.put("ids", target.ids());
            try {
                JsonNode root = callCommon(session, ctx, "delete", target.objectApiName(), data);
                row.put("status", "SUCCESS");
                row.put("returnCode", root.path("returnCode").asText(""));
                row.put("returnInfo", root.path("returnInfo").asText(""));
            } catch (RuntimeException ex) {
                row.put("status", "FAILED");
                row.put("message", ex.getMessage());
            }
            out.add(row);
        }
        return out;
    }

    private List<String> extractRemoteIds(JsonNode root) {
        JsonNode data = root.path("data");
        if (data.isTextual()) {
            try {
                data = objectMapper.readTree(data.asText());
            } catch (JsonProcessingException ignored) {
                return List.of();
            }
        }
        List<String> ids = new ArrayList<>();
        if (data.isArray()) {
            for (JsonNode item : data) {
                addId(ids, item);
            }
        } else {
            addId(ids, data);
        }
        return ids.stream().distinct().toList();
    }

    private void addId(List<String> ids, JsonNode item) {
        if (item == null || item.isMissingNode() || item.isNull()) {
            return;
        }
        if (item.isTextual() && !item.asText().isBlank()) {
            ids.add(item.asText());
            return;
        }
        String id = item.path("id").asText(item.path("Id").asText(""));
        if (!id.isBlank()) {
            ids.add(id);
        }
    }

    private List<Map<String, Object>> dataList(Object raw) {
        if (raw instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : list) {
                Map<String, Object> map = asMap(item);
                if (!map.isEmpty()) {
                    out.add(map);
                }
            }
            return out;
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return objectMapper.readValue(text, LIST_MAP_REF);
            } catch (JsonProcessingException ignored) {
                return List.of();
            }
        }
        Map<String, Object> single = asMap(raw);
        return single.isEmpty() ? List.of() : List.of(single);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object raw) {
        return raw instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private boolean isInsert(String serviceName) {
        return "insert".equalsIgnoreCase(serviceName) || "insertWithRoleRight".equalsIgnoreCase(serviceName);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String ensureBaseUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("CloudCC baseUrl is blank");
        }
        String value = raw.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "https://" + value.replaceFirst("^/+", "");
        }
        return value;
    }

    public static class WritebackFailedException extends RuntimeException {
        private final Map<String, Object> result;

        public WritebackFailedException(Map<String, Object> result, Throwable cause) {
            super(String.valueOf(result.getOrDefault("message", "CloudCC writeback failed")), cause);
            this.result = result;
        }

        public Map<String, Object> result() {
            return result;
        }
    }

    private record RollbackTarget(String objectApiName, List<String> ids) {
    }
}
