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
