package com.codehouse.ciciassistant.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class AliyunBailianClientTest {

    @Test
    void requiredToolCompletionUsesReasoningCompatibleAutoChoiceForTheSingleDecisionTool() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {"choices":[{"message":{"role":"assistant","content":"","tool_calls":[{"id":"decision-1","type":"function","function":{"name":"resolve_devautopilot_dialogue","arguments":"{\\\"action\\\":\\\"OTHER\\\"}"}}]},"finish_reason":"tool_calls"}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) {
                body.write(response);
            }
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            AliyunBailianClient client = new AliyunBailianClient(RestClient.builder(), objectMapper);
            Map<String, Object> tool = Map.of("type", "function", "function", Map.of(
                    "name", "resolve_devautopilot_dialogue",
                    "parameters", Map.of("type", "object")));

            AliyunBailianClient.ChatCompletionResult result =
                    client.requiredToolCompletionWithCredentials(
                            "semantic-model", List.of(Map.of("role", "user", "content", "任意自然语言")),
                            tool, "resolve_devautopilot_dialogue", baseUrl, "test-key", 128, 8_192);

            JsonNode sent = objectMapper.readTree(requestBody.get());
            assertThat(sent.path("temperature").asInt()).isZero();
            assertThat(sent.path("parallel_tool_calls").asBoolean()).isFalse();
            assertThat(sent.path("tool_choice").asText()).isEqualTo("auto");
            assertThat(sent.has("enable_thinking")).isFalse();
            assertThat(result.toolCalls()).singleElement()
                    .satisfies(call -> assertThat(call.name()).isEqualTo("resolve_devautopilot_dialogue"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void requiredToolCompletionRetriesWithoutThinkingWhenReasoningPassOmitsTheProtocol() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        java.util.concurrent.atomic.AtomicInteger requests = new java.util.concurrent.atomic.AtomicInteger();
        AtomicReference<String> retryBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            int attempt = requests.incrementAndGet();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] response;
            if (attempt == 1) {
                response = """
                        {"choices":[{"message":{"role":"assistant","content":"plain text"},"finish_reason":"stop"}]}
                        """.getBytes(StandardCharsets.UTF_8);
            } else {
                retryBody.set(body);
                response = """
                        {"choices":[{"message":{"role":"assistant","content":"","tool_calls":[{"id":"decision-2","type":"function","function":{"name":"resolve_devautopilot_dialogue","arguments":"{\\"action\\":\\"OTHER\\"}"}}]},"finish_reason":"tool_calls"}]}
                        """.getBytes(StandardCharsets.UTF_8);
            }
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            AliyunBailianClient client = new AliyunBailianClient(RestClient.builder(), objectMapper);
            Map<String, Object> tool = Map.of("type", "function", "function", Map.of(
                    "name", "resolve_devautopilot_dialogue",
                    "parameters", Map.of("type", "object")));

            AliyunBailianClient.ChatCompletionResult result =
                    client.requiredToolCompletionWithCredentials(
                            "semantic-model", List.of(Map.of("role", "user", "content", "任意自然语言")),
                            tool, "resolve_devautopilot_dialogue", baseUrl, "test-key", 128, 8_192);

            JsonNode retry = objectMapper.readTree(retryBody.get());
            assertThat(requests).hasValue(2);
            assertThat(retry.path("enable_thinking").asBoolean()).isFalse();
            assertThat(retry.path("tool_choice").path("function").path("name").asText())
                    .isEqualTo("resolve_devautopilot_dialogue");
            assertThat(result.toolCalls()).singleElement()
                    .satisfies(call -> assertThat(call.id()).isEqualTo("decision-2"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void boundedCompletionSendsOutputBudgetAndRejectsResponseBeforeJsonParsing() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicReference<String> requestBody = new AtomicReference<>();
        byte[] oversizedResponse = ("{\"choices\":[{\"message\":{\"content\":\""
                + "x".repeat(4_096)
                + "\"},\"finish_reason\":\"stop\"}]}")
                .getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, 0);
            try (var body = exchange.getResponseBody()) {
                body.write(oversizedResponse);
            }
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            AliyunBailianClient client = new AliyunBailianClient(
                    RestClient.builder(), objectMapper);

            AliyunBailianClient.ChatCompletionResult result =
                    client.chatCompletionWithCredentials(
                            "bounded-model",
                            List.<Map<String, Object>>of(
                                    Map.of("role", "user", "content", "bounded request")),
                            null,
                            true,
                            baseUrl,
                            "test-key",
                            64,
                            128);

            JsonNode sent = objectMapper.readTree(requestBody.get());
            assertThat(sent.path("max_tokens").asInt()).isEqualTo(64);
            assertThat(result.content()).isEqualTo("Model call failed: response exceeds byte limit.");
        } finally {
            server.stop(0);
        }
    }
}
