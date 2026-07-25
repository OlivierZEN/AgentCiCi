package com.codehouse.ciciassistant.mcp.service;

import com.codehouse.ciciassistant.mcp.domain.McpServerEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Component;

/**
 * MCP (Model Context Protocol) client that communicates with remote MCP servers
 * via the Streamable HTTP transport (JSON-RPC 2.0 over HTTP POST).
 */
@Component
public class McpClient {

    private static final String PROTOCOL_VERSION = "2025-03-26";
    private static final String MCP_PROTOCOL_VERSION_HEADER = "MCP-Protocol-Version";
    private static final String MCP_SESSION_ID_HEADER = "Mcp-Session-Id";

    private final ObjectMapper objectMapper;
    // Do not pin to HTTP/1.1: mcp.cloudcc.cn (and many other MCP servers) negotiate HTTP/2
    // via ALPN. Forcing HTTP_1_1 causes Java's HTTP/1.1 parser to receive HTTP/2 frames,
    // which produces "HTTP/1.1 header parser received no bytes" errors.
    // Leaving the version unset lets Java auto-negotiate the best available protocol.
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final AtomicInteger idSeq = new AtomicInteger(1);

    /** server key → session-id returned by the MCP server after initialize */
    private final ConcurrentHashMap<String, String> sessionIds = new ConcurrentHashMap<>();

    /** Server keys that have completed initialize and notifications/initialized in this process. */
    private final ConcurrentHashMap.KeySetView<String, Boolean> initializedServerKeys = ConcurrentHashMap.newKeySet();

    /** Serializes initialization so one server cannot receive interleaved MCP sessions. */
    private final ConcurrentHashMap<String, ReentrantLock> initializationLocks = new ConcurrentHashMap<>();

    public McpClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ── public API ──────────────────────────────────────────

    /**
     * Initialize connection with the MCP server.
     * Returns the server's capability info or throws on failure.
     */
    public JsonNode initialize(McpServerEntity server) throws Exception {
        return initialize(server, Map.of());
    }

    public JsonNode initialize(McpServerEntity server, Map<String, String> extraHeaders) throws Exception {
        String serverKey = serverKey(server);
        ReentrantLock lock = initializationLocks.computeIfAbsent(serverKey, ignored -> new ReentrantLock());
        lock.lock();
        try {
            // A caller explicitly asking to initialize starts a fresh MCP session.
            clearSession(serverKey);
            return initializeInternal(server, serverKey, extraHeaders);
        } finally {
            lock.unlock();
        }
    }

    private JsonNode initializeInternal(McpServerEntity server, String serverKey, Map<String, String> extraHeaders)
            throws Exception {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("protocolVersion", PROTOCOL_VERSION);
        params.putObject("capabilities");
        ObjectNode clientInfo = params.putObject("clientInfo");
        clientInfo.put("name", "cc-cici-assistant");
        clientInfo.put("version", "1.0.0");

        RpcResponse initializeResponse = rpcWithResponse(server, "initialize", params, extraHeaders);
        initializeResponse.httpResponse().headers().firstValue(MCP_SESSION_ID_HEADER)
                .filter(sessionId -> !sessionId.isBlank())
                .ifPresent(sessionId -> sessionIds.put(serverKey, sessionId));

        // The server must see this notification on the same session before later MCP methods.
        sendNotification(server, "notifications/initialized", objectMapper.createObjectNode(), extraHeaders);
        initializedServerKeys.add(serverKey);
        return initializeResponse.result();
    }

    /**
     * List all tools exposed by the MCP server.
     */
    public List<McpTool> listTools(McpServerEntity server) throws Exception {
        return listTools(server, Map.of());
    }

    public List<McpTool> listTools(McpServerEntity server, Map<String, String> extraHeaders) throws Exception {
        ensureInitialized(server, extraHeaders);
        JsonNode result = rpc(server, "tools/list", objectMapper.createObjectNode(), extraHeaders);
        if (result == null || !result.has("tools")) {
            return Collections.emptyList();
        }
        List<McpTool> tools = new ArrayList<>();
        for (JsonNode t : result.get("tools")) {
            tools.add(new McpTool(
                    t.path("name").asText(),
                    t.path("description").asText(""),
                    t.has("inputSchema") ? t.get("inputSchema") : objectMapper.createObjectNode()
            ));
        }
        return tools;
    }

    /**
     * Call a tool on the MCP server with the given arguments.
     */
    public String callTool(McpServerEntity server, String toolName, String argumentsJson) throws Exception {
        return callTool(server, toolName, argumentsJson, Map.of());
    }

    public String callTool(McpServerEntity server, String toolName, String argumentsJson, Map<String, String> extraHeaders)
            throws Exception {
        ensureInitialized(server, extraHeaders);
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", toolName);
        if (argumentsJson != null && !argumentsJson.isBlank()) {
            params.set("arguments", objectMapper.readTree(argumentsJson));
        } else {
            params.putObject("arguments");
        }

        JsonNode result = rpc(server, "tools/call", params, extraHeaders);
        if (result == null) return "";

        if (result.has("content") && result.get("content").isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode c : result.get("content")) {
                if ("text".equals(c.path("type").asText())) {
                    sb.append(c.path("text").asText());
                } else {
                    sb.append(c.toString());
                }
            }
            return sb.toString();
        }
        return result.toString();
    }

    /**
     * Quick health/connectivity check: initialize → tools/list.
     */
    public Map<String, Object> healthCheck(McpServerEntity server) throws Exception {
        return healthCheck(server, Map.of());
    }

    public Map<String, Object> healthCheck(McpServerEntity server, Map<String, String> extraHeaders) throws Exception {
        initialize(server, extraHeaders);
        List<McpTool> tools = listTools(server, extraHeaders);
        return Map.of(
                "status", "connected",
                "protocolVersion", PROTOCOL_VERSION,
                "toolCount", tools.size()
        );
    }

    public void clearSession(Long serverId) {
        if (serverId != null) {
            clearSession(serverKey(serverId));
        }
    }

    // ── internals ───────────────────────────────────────────

    private JsonNode rpc(McpServerEntity server, String method, JsonNode params, Map<String, String> extraHeaders) throws Exception {
        return rpcWithResponse(server, method, params, extraHeaders).result();
    }

    private RpcResponse rpcWithResponse(McpServerEntity server, String method, JsonNode params,
                                        Map<String, String> extraHeaders) throws Exception {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", idSeq.getAndIncrement());
        request.put("method", method);
        request.set("params", params);

        HttpResponse<String> httpResp = doPost(server, request, extraHeaders);
        String body = httpResp.body();

        if (body.startsWith("event:") || body.startsWith("data:")) {
            return new RpcResponse(httpResp, extractJsonRpcFromSse(body));
        }

        JsonNode root = objectMapper.readTree(body);
        if (root.has("error")) {
            JsonNode err = root.get("error");
            throw new McpException(
                    err.path("code").asInt(-1),
                    err.path("message").asText("MCP error")
            );
        }

        return new RpcResponse(httpResp, root.get("result"));
    }

    private void sendNotification(McpServerEntity server, String method, JsonNode params,
                                  Map<String, String> extraHeaders) throws Exception {
        ObjectNode notification = objectMapper.createObjectNode();
        notification.put("jsonrpc", "2.0");
        notification.put("method", method);
        notification.set("params", params);
        doPost(server, notification, extraHeaders);
    }

    private HttpResponse<String> doPost(McpServerEntity server, JsonNode body, Map<String, String> extraHeaders)
            throws Exception {
        String jsonBody = objectMapper.writeValueAsString(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(server.getUrl()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .timeout(Duration.ofSeconds(server.getTimeoutSeconds()))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));

        parseAndApplyHeaders(server.getHeaders(), builder);
        if (extraHeaders != null) {
            for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null || key.isBlank() || val == null || val.isBlank()) {
                    continue;
                }
                if (isConfigurableRequestHeader(key)) {
                    // Dynamic headers should override any static configured headers
                    // (e.g. stale accessToken/base_url from MCP server settings).
                    builder.setHeader(key, val);
                }
            }
        }

        // Protocol/session headers are not configurable credentials. Set them last so a stale
        // MCP Server configuration cannot replace the session negotiated by this process.
        builder.setHeader(MCP_PROTOCOL_VERSION_HEADER, PROTOCOL_VERSION);
        String sessionId = sessionIds.get(serverKey(server));
        if (sessionId != null && !sessionId.isBlank()) {
            builder.setHeader(MCP_SESSION_ID_HEADER, sessionId);
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new McpException(response.statusCode(),
                    "HTTP " + response.statusCode() + ": " + response.body());
        }
        return response;
    }

    private void ensureInitialized(McpServerEntity server, Map<String, String> extraHeaders) throws Exception {
        String serverKey = serverKey(server);
        if (initializedServerKeys.contains(serverKey)) {
            return;
        }
        ReentrantLock lock = initializationLocks.computeIfAbsent(serverKey, ignored -> new ReentrantLock());
        lock.lock();
        try {
            if (!initializedServerKeys.contains(serverKey)) {
                clearSession(serverKey);
                initializeInternal(server, serverKey, extraHeaders);
            }
        } finally {
            lock.unlock();
        }
    }

    private void clearSession(String serverKey) {
        sessionIds.remove(serverKey);
        initializedServerKeys.remove(serverKey);
    }

    private String serverKey(McpServerEntity server) {
        return server.getId() == null ? "url:" + server.getUrl() : serverKey(server.getId());
    }

    private String serverKey(Long serverId) {
        return "id:" + serverId;
    }

    private void parseAndApplyHeaders(String headersText, HttpRequest.Builder builder) {
        if (headersText == null || headersText.isBlank()) return;
        for (String line : headersText.split("\\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            int sep = line.indexOf('=');
            if (sep <= 0) {
                // Also support common HTTP header format: Key: Value
                sep = line.indexOf(':');
            }
            if (sep <= 0) continue;
            String key = line.substring(0, sep).trim();
            String val = line.substring(sep + 1).trim();
            if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
                val = val.substring(1, val.length() - 1);
            }
            if (isConfigurableRequestHeader(key)) {
                builder.header(key, val);
            }
        }
    }

    private boolean isConfigurableRequestHeader(String headerName) {
        return !headerName.equalsIgnoreCase("Content-Type")
                && !headerName.equalsIgnoreCase("Accept")
                && !headerName.equalsIgnoreCase(MCP_PROTOCOL_VERSION_HEADER)
                && !headerName.equalsIgnoreCase(MCP_SESSION_ID_HEADER);
    }

    /**
     * Some MCP servers wrap JSON-RPC responses in SSE events.
     */
    private JsonNode extractJsonRpcFromSse(String sseBody) throws Exception {
        for (String line : sseBody.split("\\n")) {
            line = line.trim();
            if (line.startsWith("data:")) {
                String data = line.substring(5).trim();
                if (data.isEmpty() || "[DONE]".equals(data)) continue;
                JsonNode node = objectMapper.readTree(data);
                if (node.has("result")) return node.get("result");
                if (node.has("error")) {
                    JsonNode err = node.get("error");
                    throw new McpException(err.path("code").asInt(-1), err.path("message").asText("MCP SSE error"));
                }
            }
        }
        return objectMapper.createObjectNode();
    }

    // ── value types ─────────────────────────────────────────

    private record RpcResponse(HttpResponse<String> httpResponse, JsonNode result) {}

    public record McpTool(String name, String description, JsonNode inputSchema) {}

    public static class McpException extends RuntimeException {
        private final int code;
        public McpException(int code, String message) {
            super(message);
            this.code = code;
        }
        public int getCode() { return code; }
    }
}
