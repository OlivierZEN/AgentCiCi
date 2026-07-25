package com.codehouse.ciciassistant.mcp.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.codehouse.ciciassistant.mcp.domain.McpServerEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class McpClientTest {

    private static final String SESSION_ID = "session-from-initialize";
    private static final String PROTOCOL_VERSION = "2025-03-26";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<RequestSnapshot> requests = new ArrayList<>();
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcp", this::handleMcpRequest);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void shouldReuseInitializeSessionForNotificationToolListAndToolCall() throws Exception {
        McpClient client = new McpClient(objectMapper);
        McpServerEntity mcpServer = new McpServerEntity(
                "company-1",
                "test-mcp",
                "test server",
                "streamableHttp",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp",
                "Mcp-Session-Id=stale-configured-session\nMCP-Protocol-Version=stale-version",
                5
        );
        Map<String, String> bearerJwt = Map.of("Authorization", "Bearer user-jwt");

        List<McpClient.McpTool> tools = client.listTools(mcpServer, bearerJwt);
        String result = client.callTool(mcpServer, "lookup", "{\"query\":\"Ada\"}", bearerJwt);

        assertThat(tools).singleElement().satisfies(tool -> {
            assertThat(tool.name()).isEqualTo("lookup");
            assertThat(tool.description()).isEqualTo("Find a record");
        });
        assertThat(result).isEqualTo("Ada Lovelace");
        assertThat(requests).extracting(RequestSnapshot::method)
                .containsExactly("initialize", "notifications/initialized", "tools/list", "tools/call");

        RequestSnapshot initialize = requests.get(0);
        assertThat(initialize.sessionId()).isNull();
        assertThat(initialize.protocolVersion()).isEqualTo(PROTOCOL_VERSION);

        for (RequestSnapshot request : requests.subList(1, requests.size())) {
            assertThat(request.sessionId()).isEqualTo(SESSION_ID);
            assertThat(request.protocolVersion()).isEqualTo(PROTOCOL_VERSION);
        }
        assertThat(requests.get(3).authorization()).isEqualTo("Bearer user-jwt");
    }

    private void handleMcpRequest(HttpExchange exchange) throws IOException {
        JsonNode payload = objectMapper.readTree(exchange.getRequestBody().readAllBytes());
        String method = payload.path("method").asText();
        requests.add(new RequestSnapshot(
                method,
                exchange.getRequestHeaders().getFirst("Mcp-Session-Id"),
                exchange.getRequestHeaders().getFirst("MCP-Protocol-Version"),
                exchange.getRequestHeaders().getFirst("Authorization")
        ));

        switch (method) {
            case "initialize" -> respond(exchange, 200,
                    "data: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2025-03-26\"}}\n\n",
                    "text/event-stream", true);
            case "notifications/initialized" -> respond(exchange, 202, "", "application/json", false);
            case "tools/list" -> respond(exchange, 200,
                    "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"tools\":[{\"name\":\"lookup\",\"description\":\"Find a record\",\"inputSchema\":{\"type\":\"object\"}}]}}",
                    "application/json", false);
            case "tools/call" -> respond(exchange, 200,
                    "{\"jsonrpc\":\"2.0\",\"id\":3,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"Ada Lovelace\"}]}}",
                    "application/json", false);
            default -> respond(exchange, 400, "unexpected method", "text/plain", false);
        }
    }

    private void respond(HttpExchange exchange, int status, String body, String contentType, boolean initialize)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        if (initialize) {
            exchange.getResponseHeaders().set("Mcp-Session-Id", SESSION_ID);
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private record RequestSnapshot(String method, String sessionId, String protocolVersion, String authorization) {}
}
