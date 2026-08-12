package com.codehouse.ciciassistant.tool.codeinterpreter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SandboxCodeInterpreterClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void sendsOnlyResponsesCodeInterpreterAndProjectsSafeResult() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/compatible-mode/v1/responses", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, """
                    {
                      "output_text": "1728",
                      "output": [
                        {"type":"reasoning","summary":"private reasoning"},
                        {"type":"code_interpreter_call","status":"completed","code":"12**3","output":"1728"},
                        {"type":"message","content":[{"type":"output_text","text":"1728"}]}
                      ],
                      "usage": {
                        "input_tokens": 10,
                        "output_tokens": 4,
                        "total_tokens": 14,
                        "x_tools": {"code_interpreter":{"count":1}}
                      }
                    }
                    """);
        });
        server.start();

        SandboxCodeInterpreterClient client = new SandboxCodeInterpreterClient(new ObjectMapper());
        SandboxCodeInterpreterClient.CallResult result = client.execute(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/compatible-mode/v1",
                "test-api-key", "qwen3.5-plus", "12 的 3 次方", 10_000);

        assertThat(result.ok()).isTrue();
        assertThat(result.answer()).isEqualTo("1728");
        assertThat(result.callCount()).isEqualTo(1);
        assertThat(result.totalTokens()).isEqualTo(14);
        assertThat(result.executions()).singleElement().satisfies(execution -> {
            assertThat(execution.get("code")).isEqualTo("12**3");
            assertThat(execution.get("output")).isEqualTo("1728");
        });
        assertThat(authorization.get()).isEqualTo("Bearer test-api-key");

        String body = requestBody.get();
        assertThat(body).contains("\"type\":\"code_interpreter\"")
                .contains("\"enable_thinking\":true")
                .contains("\"store\":false")
                .doesNotContain("function");
        assertThat(result.toString()).doesNotContain("private reasoning");
    }

    @Test
    void mapsAuthenticationFailureWithoutEchoingArbitraryHtml() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/responses", exchange -> respond(exchange, 401, "<html>secret debug page</html>"));
        server.start();

        SandboxCodeInterpreterClient.CallResult result = new SandboxCodeInterpreterClient(new ObjectMapper()).execute(
                "http://127.0.0.1:" + server.getAddress().getPort(), "bad", "qwen", "test", 10_000);

        assertThat(result.ok()).isFalse();
        assertThat(result.code()).isEqualTo("CODE_INTERPRETER_AUTH_FAILED");
        assertThat(result.message()).isEqualTo("代码解释器上游返回 HTTP 401");
        assertThat(result.message()).doesNotContain("secret debug page");
    }

    private void respond(HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
