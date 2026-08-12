package com.codehouse.ciciassistant.tool.managedweb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** Minimal OpenAI-compatible Responses client for managed web search and extraction. */
@Component
public class ManagedWebToolClient {

    static final int MAX_RESPONSE_BYTES = 2_000_000;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ManagedWebToolClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    public CallResult execute(String apiBaseUrl,
                              String apiKey,
                              String model,
                              String input,
                              ToolMode mode,
                              int timeoutMs) {
        long started = System.nanoTime();
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("input", input);
            payload.put("tools", mode == ToolMode.SEARCH
                    ? List.of(Map.of("type", "web_search"))
                    : List.of(Map.of("type", "web_search"), Map.of("type", "web_extractor")));
            payload.put("enable_thinking", true);
            payload.put("store", false);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(apiBaseUrl) + "/responses"))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            long latencyMs = elapsedMillis(started);
            try (InputStream body = response.body()) {
                byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (bytes.length > MAX_RESPONSE_BYTES) {
                    return CallResult.failure("MANAGED_WEB_RESPONSE_TOO_LARGE",
                            "联网能力响应超过平台限制", response.statusCode(), latencyMs);
                }
                String responseText = new String(bytes, StandardCharsets.UTF_8);
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    return CallResult.failure(upstreamCode(response.statusCode()),
                            safeUpstreamMessage(responseText, response.statusCode()), response.statusCode(), latencyMs);
                }
                return parse(objectMapper.readTree(responseText), response.statusCode(), latencyMs);
            }
        } catch (java.net.http.HttpTimeoutException exception) {
            return CallResult.failure("MANAGED_WEB_TIMEOUT", "联网能力请求超时", 0, elapsedMillis(started));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return CallResult.failure("MANAGED_WEB_INTERRUPTED", "联网能力请求被中断", 0, elapsedMillis(started));
        } catch (Exception exception) {
            return CallResult.failure("MANAGED_WEB_TRANSPORT_ERROR", "联网能力连接失败", 0, elapsedMillis(started));
        }
    }

    private CallResult parse(JsonNode root, int status, long latencyMs) {
        String answer = root.path("output_text").asText("");
        JsonNode output = root.path("output");
        if (answer.isBlank() && output.isArray()) {
            for (JsonNode item : output) {
                if ("message".equals(item.path("type").asText(""))) {
                    String text = extractMessageText(item);
                    if (!text.isBlank()) {
                        answer = text;
                        break;
                    }
                }
            }
        }
        if (answer.isBlank()) {
            return CallResult.failure("MANAGED_WEB_EMPTY_RESPONSE", "联网能力未返回最终答案", status, latencyMs);
        }
        JsonNode usage = root.path("usage");
        int inputTokens = usage.path("input_tokens").asInt(0);
        int outputTokens = usage.path("output_tokens").asInt(0);
        int totalTokens = usage.path("total_tokens").asInt(inputTokens + outputTokens);
        int searchCalls = usage.path("x_tools").path("web_search").path("count").asInt(0);
        int extractorCalls = usage.path("x_tools").path("web_extractor").path("count").asInt(0);
        return new CallResult(true, truncate(answer, 40_000), searchCalls, extractorCalls,
                inputTokens, outputTokens, totalTokens, status, latencyMs, null, null);
    }

    private String extractMessageText(JsonNode message) {
        JsonNode content = message.path("content");
        if (!content.isArray()) return "";
        StringBuilder text = new StringBuilder();
        for (JsonNode part : content) {
            if ("output_text".equals(part.path("type").asText()) && part.path("text").isTextual()) {
                if (!text.isEmpty()) text.append('\n');
                text.append(part.path("text").asText());
            }
        }
        return text.toString();
    }

    private String safeUpstreamMessage(String body, int status) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String message = root.path("error").path("message").asText("");
            if (message.isBlank()) message = root.path("message").asText("");
            if (!message.isBlank()) return truncate(message, 500);
        } catch (Exception ignored) {
            // Never echo arbitrary upstream HTML.
        }
        return "联网能力上游返回 HTTP " + status;
    }

    private String upstreamCode(int status) {
        if (status == 401 || status == 403) return "MANAGED_WEB_AUTH_FAILED";
        if (status == 429) return "MANAGED_WEB_RATE_LIMITED";
        if (status >= 500) return "MANAGED_WEB_UPSTREAM_UNAVAILABLE";
        return "MANAGED_WEB_UPSTREAM_ERROR";
    }

    private String normalizeBaseUrl(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max) + "…";
    }

    private long elapsedMillis(long started) {
        return Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
    }

    public enum ToolMode {
        SEARCH,
        EXTRACT
    }

    public record CallResult(boolean ok,
                             String answer,
                             int searchCalls,
                             int extractorCalls,
                             int inputTokens,
                             int outputTokens,
                             int totalTokens,
                             int httpStatus,
                             long latencyMs,
                             String code,
                             String message) {
        static CallResult failure(String code, String message, int status, long latencyMs) {
            return new CallResult(false, null, 0, 0, 0, 0, 0, status, latencyMs, code, message);
        }
    }
}
