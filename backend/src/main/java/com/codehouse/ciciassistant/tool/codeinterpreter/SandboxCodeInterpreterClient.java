package com.codehouse.ciciassistant.tool.codeinterpreter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** Minimal OpenAI-compatible Responses API client for the vendor-managed code sandbox. */
@Component
public class SandboxCodeInterpreterClient {

    static final int MAX_RESPONSE_BYTES = 2_000_000;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public SandboxCodeInterpreterClient(ObjectMapper objectMapper) {
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
                              int timeoutMs) {
        long started = System.nanoTime();
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("input", input);
            payload.put("tools", List.of(Map.of("type", "code_interpreter")));
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
                    return CallResult.failure("CODE_INTERPRETER_RESPONSE_TOO_LARGE",
                            "代码解释器响应超过平台限制", response.statusCode(), latencyMs);
                }
                String responseText = new String(bytes, StandardCharsets.UTF_8);
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    return CallResult.failure(upstreamCode(response.statusCode()),
                            safeUpstreamMessage(responseText, response.statusCode()), response.statusCode(), latencyMs);
                }
                JsonNode root = objectMapper.readTree(responseText);
                return parse(root, response.statusCode(), latencyMs);
            }
        } catch (java.net.http.HttpTimeoutException exception) {
            return CallResult.failure("CODE_INTERPRETER_TIMEOUT", "代码解释器请求超时", 0, elapsedMillis(started));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return CallResult.failure("CODE_INTERPRETER_INTERRUPTED", "代码解释器请求被中断", 0, elapsedMillis(started));
        } catch (Exception exception) {
            return CallResult.failure("CODE_INTERPRETER_TRANSPORT_ERROR", "代码解释器连接失败", 0, elapsedMillis(started));
        }
    }

    private CallResult parse(JsonNode root, int status, long latencyMs) {
        String answer = root.path("output_text").asText("");
        List<Map<String, Object>> executions = new ArrayList<>();
        JsonNode output = root.path("output");
        if (output.isArray()) {
            for (JsonNode item : output) {
                String type = item.path("type").asText("");
                if ("message".equals(type) && answer.isBlank()) {
                    answer = extractMessageText(item);
                } else if ("code_interpreter_call".equals(type)) {
                    Map<String, Object> execution = new LinkedHashMap<>();
                    execution.put("status", item.path("status").asText("completed"));
                    String code = item.path("code").asText("");
                    if (!code.isBlank()) {
                        execution.put("code", truncate(code, 4000));
                    }
                    String result = item.path("output").asText("");
                    if (!result.isBlank()) {
                        execution.put("output", truncate(result, 4000));
                    }
                    executions.add(execution);
                }
            }
        }
        if (answer.isBlank()) {
            return CallResult.failure("CODE_INTERPRETER_EMPTY_RESPONSE", "代码解释器未返回最终答案", status, latencyMs);
        }
        JsonNode usage = root.path("usage");
        int inputTokens = usage.path("input_tokens").asInt(0);
        int outputTokens = usage.path("output_tokens").asInt(0);
        int totalTokens = usage.path("total_tokens").asInt(inputTokens + outputTokens);
        int callCount = usage.path("x_tools").path("code_interpreter").path("count").asInt(executions.size());
        return new CallResult(true, truncate(answer, 40_000), executions, callCount,
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
            // Return a stable message below; never echo arbitrary upstream HTML.
        }
        return "代码解释器上游返回 HTTP " + status;
    }

    private String upstreamCode(int status) {
        if (status == 401 || status == 403) return "CODE_INTERPRETER_AUTH_FAILED";
        if (status == 429) return "CODE_INTERPRETER_RATE_LIMITED";
        if (status >= 500) return "CODE_INTERPRETER_UPSTREAM_UNAVAILABLE";
        return "CODE_INTERPRETER_UPSTREAM_ERROR";
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

    public record CallResult(boolean ok,
                             String answer,
                             List<Map<String, Object>> executions,
                             int callCount,
                             int inputTokens,
                             int outputTokens,
                             int totalTokens,
                             int httpStatus,
                             long latencyMs,
                             String code,
                             String message) {
        static CallResult failure(String code, String message, int status, long latencyMs) {
            return new CallResult(false, null, List.of(), 0, 0, 0, 0, status, latencyMs, code, message);
        }
    }
}
