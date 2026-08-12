package com.codehouse.ciciassistant.tool.managedweb;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ManagedWebToolClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void searchRequestContainsOnlyWebSearchAndDoesNotProjectReasoning() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        start(exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, response("搜索完成", 2, 0));
        });

        ManagedWebToolClient.CallResult result = client().execute(baseUrl(), "secret", "qwen3.5-plus",
                "query", ManagedWebToolClient.ToolMode.SEARCH, 10_000);

        assertThat(result.ok()).isTrue();
        assertThat(result.answer()).isEqualTo("搜索完成");
        assertThat(result.searchCalls()).isEqualTo(2);
        assertThat(result.extractorCalls()).isZero();
        assertThat(requestBody.get()).contains("\"type\":\"web_search\"")
                .doesNotContain("web_extractor")
                .doesNotContain("function");
        assertThat(result.toString()).doesNotContain("private reasoning");
    }

    @Test
    void extractorRequestAlwaysCombinesWebSearchAndWebExtractor() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        start(exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, response("页面标题", 1, 1));
        });

        ManagedWebToolClient.CallResult result = client().execute(baseUrl(), "secret", "qwen3.5-plus",
                "extract", ManagedWebToolClient.ToolMode.EXTRACT, 10_000);

        assertThat(result.ok()).isTrue();
        assertThat(result.searchCalls()).isEqualTo(1);
        assertThat(result.extractorCalls()).isEqualTo(1);
        assertThat(requestBody.get()).contains("\"type\":\"web_search\"")
                .contains("\"type\":\"web_extractor\"")
                .contains("\"enable_thinking\":true")
                .contains("\"store\":false");
    }

    private void start(ExchangeHandler handler) throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/compatible-mode/v1/responses", exchange -> handler.handle(exchange));
        server.start();
    }

    private ManagedWebToolClient client() {
        return new ManagedWebToolClient(new ObjectMapper());
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/compatible-mode/v1";
    }

    private String response(String answer, int searchCalls, int extractorCalls) {
        return """
                {
                  "output_text": "%s",
                  "output": [
                    {"type":"reasoning","summary":"private reasoning"},
                    {"type":"message","content":[{"type":"output_text","text":"%s"}]}
                  ],
                  "usage": {
                    "input_tokens": 12,
                    "output_tokens": 6,
                    "total_tokens": 18,
                    "x_tools": {
                      "web_search":{"count":%d},
                      "web_extractor":{"count":%d}
                    }
                  }
                }
                """.formatted(answer, answer, searchCalls, extractorCalls);
    }

    private void respond(HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws java.io.IOException;
    }
}
