package com.codehouse.ciciassistant.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class AliyunBailianClient {

    private static final Logger log = LoggerFactory.getLogger(AliyunBailianClient.class);

    static final String SYSTEM_PROMPT = """
            You are CiCi, an enterprise digital employee assistant. Answer in the same language as the user's question.
            Always format answers in readable Markdown: use ## or ### for section headings, bullet or numbered lists for enumerations,
            **bold** for important terms, short paragraphs, and blank lines between sections. Never dump everything into one dense paragraph.
            If a suitable tool is available and it can provide the facts or records needed to answer, call the tool proactively instead of guessing.
            Never show your chain-of-thought, internal planning, or step-by-step reasoning to the user. Do not output sections titled
            "Thinking Process", "思考过程", "Analysis", or similar. Reply with only the final user-facing answer.
            """;

    /** Used when the organization enables "show thinking" in model settings. */
    static final String SYSTEM_PROMPT_WITH_THINKING = """
            You are CiCi, an enterprise digital employee assistant. Answer in the same language as the user's question.
            Use readable Markdown (##/### headings, lists, **bold**, short paragraphs, blank lines between sections).
            If a suitable tool is available and it can provide the facts or records needed to answer, call the tool proactively instead of guessing.
            The user is allowed to see your reasoning. Structure your reply as follows:
            1) Start with a section whose heading is exactly: ## 思考过程
            Under it, give a concise outline of how you approach the question (short bullets or a brief paragraph).
            2) Then add a separate section with heading: ## 回答
            Under it, give the complete, polished answer for the user.
            If the platform exposes a separate reasoning stream, reasoning may appear there as well; still use the two Markdown sections for the main answer text when possible.
            """;

    /** Timeout for non-streaming LLM calls (tool-resolution rounds). */
    private static final Duration NON_STREAM_TIMEOUT = Duration.ofSeconds(120);
    /** Timeout for the streaming final-answer call. */
    private static final Duration STREAM_CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration STREAM_REQUEST_TIMEOUT = Duration.ofSeconds(120);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private final String defaultModel;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(STREAM_CONNECT_TIMEOUT)
            .build();

    public AliyunBailianClient(RestClient.Builder restClientBuilder,
                               ObjectMapper objectMapper,
                               @Value("${app.model.aliyun.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}") String baseUrl,
                               @Value("${app.model.aliyun.api-key:}") String apiKey,
                               @Value("${app.model.aliyun.default-model:qwen3.5-plus}") String defaultModel) {
        var factorySettings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(30))
                .withReadTimeout(NON_STREAM_TIMEOUT);
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(ClientHttpRequestFactories.get(factorySettings))
                .build();
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.defaultModel = defaultModel;
    }

    // ── Legacy simple chat (backward compat) ──

    public String chat(String modelName, String question, List<String> ragContext, Map<String, Object> toolResult) {
        String prompt = buildPrompt(question, ragContext, toolResult);
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", prompt)
        );
        ChatCompletionResult result = chatCompletion(modelName, messages, null, true);
        return result.content() != null ? result.content() : "Model returned empty response.";
    }

    public void chatStream(String modelName, String question, List<String> ragContext,
                           Map<String, Object> toolResult, Consumer<String> onDelta) throws Exception {
        String prompt = buildPrompt(question, ragContext, toolResult);
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", prompt)
        );
        chatStreamWithMessages(modelName, messages, null, false, onDelta);
    }

    // ── Function-calling-aware API ──

    /**
     * Non-streaming chat completion with optional tools (function calling).
     * Returns structured result including any tool_calls the model wants to make.
     */
    public ChatCompletionResult chatCompletion(String modelName, List<Map<String, Object>> messages,
                                               List<Map<String, Object>> tools) {
        return chatCompletion(modelName, messages, tools, true);
    }

    /**
     * @param stripThinkingFromAssistantContent when true, remove visible CoT blocks from assistant message text (tool rounds + final).
     */
    public ChatCompletionResult chatCompletion(String modelName, List<Map<String, Object>> messages,
                                               List<Map<String, Object>> tools,
                                               boolean stripThinkingFromAssistantContent) {
        return chatCompletionWithCredentials(
                modelName,
                messages,
                tools,
                stripThinkingFromAssistantContent,
                baseUrl,
                apiKey);
    }

    public ChatCompletionResult chatCompletionWithCredentials(String modelName,
                                                              List<Map<String, Object>> messages,
                                                              List<Map<String, Object>> tools,
                                                              boolean stripThinkingFromAssistantContent,
                                                              String apiBaseUrl,
                                                              String apiKey) {
        String normalizedBaseUrl = normalizeBaseUrl(apiBaseUrl);
        if ((apiKey == null || apiKey.isBlank()) && normalizedBaseUrl.equals(baseUrl)) {
            return new ChatCompletionResult("assistant", "Aliyun API key is not configured.", null, "stop", 0, 0);
        }
        String targetModel = modelName == null || modelName.isBlank() ? defaultModel : modelName;
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", targetModel);
        payload.put("messages", messages);
        if (tools != null && !tools.isEmpty()) {
            payload.put("tools", tools);
        }
        if (!stripThinkingFromAssistantContent) {
            payload.put("enable_thinking", true);
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = RestClient.builder()
                    .baseUrl(normalizeBaseUrl(apiBaseUrl))
                    .requestFactory(ClientHttpRequestFactories.get(ClientHttpRequestFactorySettings.DEFAULTS
                            .withConnectTimeout(Duration.ofSeconds(30))
                            .withReadTimeout(NON_STREAM_TIMEOUT)))
                    .build()
                    .post()
                    .uri("/chat/completions")
                    .headers(headers -> addOptionalBearer(headers, apiKey))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(Map.class);

            if (response == null) return new ChatCompletionResult("assistant", "Empty response.", null, "stop", 0, 0);
            return parseCompletionResponse(response, stripThinkingFromAssistantContent);
        } catch (Exception e) {
            log.error("Aliyun chat completion failed: {}", e.getMessage());
            return new ChatCompletionResult("assistant", "Model call failed: " + e.getMessage(), null, "stop", 0, 0);
        }
    }

    /**
     * Bounded non-streaming completion for callers that require a hard output budget.
     * The legacy overload intentionally keeps its existing RestClient behavior.
     */
    public ChatCompletionResult chatCompletionWithCredentials(String modelName,
                                                              List<Map<String, Object>> messages,
                                                              List<Map<String, Object>> tools,
                                                              boolean stripThinkingFromAssistantContent,
                                                              String apiBaseUrl,
                                                              String apiKey,
                                                              int maxOutputTokens,
                                                              int maxResponseBytes) {
        if (maxOutputTokens <= 0 || maxResponseBytes <= 0) {
            throw new IllegalArgumentException("Completion budgets must be positive");
        }
        String normalizedBaseUrl = normalizeBaseUrl(apiBaseUrl);
        if ((apiKey == null || apiKey.isBlank()) && normalizedBaseUrl.equals(baseUrl)) {
            return new ChatCompletionResult(
                    "assistant", "Aliyun API key is not configured.", null, "stop", 0, 0);
        }
        String targetModel = modelName == null || modelName.isBlank() ? defaultModel : modelName;
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", targetModel);
        payload.put("messages", messages);
        payload.put("max_tokens", maxOutputTokens);
        if (tools != null && !tools.isEmpty()) {
            payload.put("tools", tools);
        }
        if (!stripThinkingFromAssistantContent) {
            payload.put("enable_thinking", true);
        }

        try {
            String jsonBody = objectMapper.writeValueAsString(payload);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(normalizedBaseUrl + "/chat/completions"))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .timeout(NON_STREAM_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
            if (apiKey != null && !apiKey.isBlank()) {
                requestBuilder.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
            }
            HttpResponse<InputStream> httpResponse = httpClient.send(
                    requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream responseBody = httpResponse.body()) {
                if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
                    return new ChatCompletionResult(
                            "assistant", "Model call failed: HTTP " + httpResponse.statusCode() + ".",
                            null, "stop", 0, 0);
                }
                long contentLength = httpResponse.headers()
                        .firstValueAsLong(HttpHeaders.CONTENT_LENGTH)
                        .orElse(-1L);
                if (contentLength > maxResponseBytes) {
                    return responseTooLarge();
                }
                byte[] responseBytes = responseBody.readNBytes(maxResponseBytes + 1);
                if (responseBytes.length > maxResponseBytes) {
                    return responseTooLarge();
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> response = objectMapper.readValue(responseBytes, Map.class);
                return parseCompletionResponse(response, stripThinkingFromAssistantContent);
            }
        } catch (Exception exception) {
            log.error("Bounded Aliyun chat completion failed: {}", exception.getClass().getSimpleName());
            return new ChatCompletionResult(
                    "assistant", "Model call failed: bounded completion request failed.",
                    null, "stop", 0, 0);
        }
    }

    private ChatCompletionResult responseTooLarge() {
        return new ChatCompletionResult(
                "assistant", "Model call failed: response exceeds byte limit.",
                null, "length", 0, 0);
    }

    private String normalizeBaseUrl(String value) {
        String safe = value == null || value.isBlank() ? baseUrl : value.trim();
        return safe.endsWith("/") ? safe.substring(0, safe.length() - 1) : safe;
    }

    private void addOptionalBearer(HttpHeaders headers, String apiKey) {
        if (apiKey != null && !apiKey.isBlank()) {
            headers.setBearerAuth(apiKey);
        }
    }

    /**
     * Streaming chat with full messages and optional tools.
     * Tool calls in stream deltas are NOT supported here — use non-streaming for tool resolution.
     */
    public ChatStreamResult chatStreamWithMessages(String modelName, List<Map<String, Object>> messages,
                                                   List<Map<String, Object>> tools,
                                                   boolean showThinking,
                                                   Consumer<String> onDelta) throws Exception {
        return chatStreamWithCredentials(modelName, messages, tools, showThinking, onDelta, baseUrl, apiKey);
    }

    public ChatStreamResult chatStreamWithCredentials(String modelName,
                                                      List<Map<String, Object>> messages,
                                                      List<Map<String, Object>> tools,
                                                      boolean showThinking,
                                                      Consumer<String> onDelta,
                                                      String apiBaseUrl,
                                                      String apiKey) throws Exception {
        String normalizedBaseUrl = normalizeBaseUrl(apiBaseUrl);
        if ((apiKey == null || apiKey.isBlank()) && normalizedBaseUrl.equals(baseUrl)) {
            onDelta.accept("Aliyun API key is not configured.");
            return new ChatStreamResult(0, 0);
        }
        String targetModel = modelName == null || modelName.isBlank() ? defaultModel : modelName;
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", targetModel);
        payload.put("stream", true);
        payload.put("stream_options", Map.of("include_usage", true));
        payload.put("messages", messages);
        if (tools != null && !tools.isEmpty()) {
            payload.put("tools", tools);
        }
        if (showThinking) {
            payload.put("enable_thinking", true);
        }

        String jsonBody = objectMapper.writeValueAsString(payload);
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(normalizedBaseUrl + "/chat/completions"))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.ACCEPT, "text/event-stream")
                .timeout(STREAM_REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
        if (apiKey != null && !apiKey.isBlank()) {
            requestBuilder.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }
        HttpRequest request = requestBuilder.build();

        HttpResponse<java.io.InputStream> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String err = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            onDelta.accept("Model HTTP " + response.statusCode() + ": " + err);
            return new ChatStreamResult(0, 0);
        }

        int promptTokens = 0;
        int completionTokens = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || !line.startsWith("data:")) continue;
                String payloadLine = line.substring(5).trim();
                if ("[DONE]".equals(payloadLine)) break;
                JsonNode root = objectMapper.readTree(payloadLine);
                JsonNode usage = root.path("usage");
                if (usage.isObject()) {
                    promptTokens = usage.path("prompt_tokens").asInt(promptTokens);
                    completionTokens = usage.path("completion_tokens").asInt(completionTokens);
                }
                JsonNode choices = root.path("choices");
                if (!choices.isArray() || choices.isEmpty()) continue;
                JsonNode delta = choices.get(0).path("delta");
                if (delta.isMissingNode()) continue;
                String piece = textFromDelta(delta, showThinking);
                if (piece != null && !piece.isEmpty()) {
                    onDelta.accept(piece);
                }
            }
        }
        return new ChatStreamResult(promptTokens, completionTokens);
    }

    // ── Response parsing ──

    @SuppressWarnings("unchecked")
    private ChatCompletionResult parseCompletionResponse(Map<String, Object> response,
                                                         boolean stripThinkingFromAssistantContent) {
        Object choicesObj = response.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
            return new ChatCompletionResult("assistant", "No choices in response.", null, "stop", 0, 0);
        }
        Map<String, Object> first = (Map<String, Object>) choices.get(0);
        String finishReason = String.valueOf(first.getOrDefault("finish_reason", "stop"));
        Map<String, Object> message = (Map<String, Object>) first.get("message");
        if (message == null) {
            return new ChatCompletionResult("assistant", "", null, finishReason, 0, 0);
        }

        String content = message.get("content") != null ? String.valueOf(message.get("content")) : null;
        if (stripThinkingFromAssistantContent && content != null) {
            content = AssistantContentSanitizer.stripThinkingSections(content);
        }

        List<ToolCallInfo> toolCalls = null;
        Object tcObj = message.get("tool_calls");
        if (tcObj instanceof List<?> tcList && !tcList.isEmpty()) {
            toolCalls = new ArrayList<>();
            for (Object tc : tcList) {
                Map<String, Object> tcMap = (Map<String, Object>) tc;
                String id = String.valueOf(tcMap.getOrDefault("id", ""));
                Map<String, Object> fn = (Map<String, Object>) tcMap.get("function");
                if (fn != null) {
                    toolCalls.add(new ToolCallInfo(
                            id,
                            String.valueOf(fn.get("name")),
                            String.valueOf(fn.getOrDefault("arguments", "{}"))
                    ));
                }
            }
        }

        int promptTokens = 0;
        int completionTokens = 0;
        Object usageObj = response.get("usage");
        if (usageObj instanceof Map<?, ?> usage) {
            promptTokens = intValue(usage.get("prompt_tokens"));
            completionTokens = intValue(usage.get("completion_tokens"));
        }

        return new ChatCompletionResult("assistant", content, toolCalls, finishReason, promptTokens, completionTokens);
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String textFromDelta(JsonNode delta, boolean includeReasoningFields) {
        StringBuilder sb = new StringBuilder();
        if (includeReasoningFields) {
            appendDeltaText(sb, delta, "reasoning_content");
            appendDeltaText(sb, delta, "reasoning");
        }
        appendDeltaText(sb, delta, "content");
        return sb.toString();
    }

    private static void appendDeltaText(StringBuilder sb, JsonNode delta, String field) {
        JsonNode node = delta.get(field);
        if (node == null || !node.isTextual()) {
            return;
        }
        String t = node.asText();
        if (!t.isEmpty()) {
            sb.append(t);
        }
    }

    private String buildPrompt(String question, List<String> ragContext, Map<String, Object> toolResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("Question:\n").append(question).append("\n\n");
        if (!ragContext.isEmpty()) {
            sb.append("RAG Context:\n");
            for (int i = 0; i < ragContext.size(); i++) {
                sb.append(i + 1).append(". ").append(ragContext.get(i)).append("\n");
            }
            sb.append("\n");
        }
        List<?> toolCalls = (List<?>) toolResult.getOrDefault("toolCalls", List.of());
        if (!toolCalls.isEmpty()) {
            sb.append("Tool Result:\n").append(toolResult).append("\n");
        }
        return sb.toString();
    }

    // ── Value types ──

    public record ChatCompletionResult(
            String role,
            String content,
            List<ToolCallInfo> toolCalls,
            String finishReason,
            int promptTokens,
            int completionTokens
    ) {
        public boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
    }

    public record ChatStreamResult(int promptTokens, int completionTokens) {}

    public record ToolCallInfo(String id, String name, String arguments) {}
}
