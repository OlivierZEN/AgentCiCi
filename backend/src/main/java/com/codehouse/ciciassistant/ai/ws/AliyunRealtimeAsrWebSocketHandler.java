package com.codehouse.ciciassistant.ai.ws;

import com.codehouse.ciciassistant.auth.service.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

@Component
public class AliyunRealtimeAsrWebSocketHandler extends BinaryWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AliyunRealtimeAsrWebSocketHandler.class);

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String url;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ConcurrentHashMap<String, SessionCtx> sessions = new ConcurrentHashMap<>();

    public AliyunRealtimeAsrWebSocketHandler(JwtService jwtService,
                                             ObjectMapper objectMapper,
                                             @Value("${app.model.aliyun.api-key:}") String apiKey,
                                             @Value("${app.voice.aliyun.realtime-model:paraformer-realtime-v2}") String model,
                                             @Value("${app.voice.aliyun.realtime-url:wss://dashscope.aliyuncs.com/api-ws/v1/inference}") String url) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.url = url;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = queryParam(session, "token");
        if (token == null || token.isBlank()) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("missing token"));
            return;
        }
        Claims claims;
        try {
            claims = jwtService.parse(token);
        } catch (Exception e) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("invalid token"));
            return;
        }
        String orgId = String.valueOf(claims.get("org_id"));
        String userId = claims.getSubject();
        SessionCtx ctx = new SessionCtx(session, orgId, userId);
        sessions.put(session.getId(), ctx);
        sendClientEvent(session, Map.of("type", "status", "message", "connected"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        SessionCtx ctx = sessions.get(session.getId());
        if (ctx == null) return;
        try {
            JsonNode j = objectMapper.readTree(message.getPayload());
            String type = j.path("type").asText("");
            if ("start".equalsIgnoreCase(type)) {
                int sampleRate = j.path("sampleRate").asInt(16000);
                startAliyunTask(ctx, sampleRate);
            } else if ("stop".equalsIgnoreCase(type)) {
                if (ctx.aliyunClient != null) {
                    ctx.aliyunClient.finishTask();
                }
            } else if ("ping".equalsIgnoreCase(type)) {
                sendClientEvent(session, Map.of("type", "pong"));
            }
        } catch (Exception e) {
            sendClientEvent(session, Map.of("type", "error", "message", "invalid ws text message"));
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        SessionCtx ctx = sessions.get(session.getId());
        if (ctx == null || ctx.aliyunClient == null || !ctx.started) return;
        ctx.aliyunClient.sendAudio(message.getPayload().asReadOnlyBuffer());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        SessionCtx ctx = sessions.remove(session.getId());
        if (ctx != null && ctx.aliyunClient != null) {
            ctx.aliyunClient.close();
        }
    }

    private void startAliyunTask(SessionCtx ctx, int sampleRate) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            sendClientEvent(ctx.clientSession, Map.of("type", "error", "message", "Aliyun API key is missing"));
            return;
        }
        if (ctx.aliyunClient != null) {
            ctx.aliyunClient.close();
        }
        String taskId = UUID.randomUUID().toString();
        AliyunWsClient client = new AliyunWsClient(ctx, taskId, Math.max(8000, sampleRate));
        ctx.aliyunClient = client;
        client.connect();
    }

    private void sendClientEvent(WebSocketSession session, Map<String, Object> payload) {
        if (session == null || !session.isOpen()) return;
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            }
        } catch (Exception e) {
            log.warn("Failed to send client ASR event: {}", e.getMessage());
        }
    }

    private String queryParam(WebSocketSession session, String key) {
        URI uri = session.getUri();
        if (uri == null || uri.getRawQuery() == null) return null;
        for (String pair : uri.getRawQuery().split("&")) {
            int idx = pair.indexOf('=');
            String k = idx >= 0 ? pair.substring(0, idx) : pair;
            if (!key.equals(k)) continue;
            String v = idx >= 0 ? pair.substring(idx + 1) : "";
            return URLDecoder.decode(v, StandardCharsets.UTF_8);
        }
        return null;
    }

    private final class AliyunWsClient implements WebSocket.Listener {
        private final SessionCtx ctx;
        private final String taskId;
        private final int sampleRate;
        private final StringBuilder textBuffer = new StringBuilder();
        private WebSocket ws;

        private AliyunWsClient(SessionCtx ctx, String taskId, int sampleRate) {
            this.ctx = ctx;
            this.taskId = taskId;
            this.sampleRate = sampleRate;
        }

        void connect() {
            ws = httpClient.newWebSocketBuilder()
                    .header("Authorization", "Bearer " + apiKey)
                    .header("user-agent", "cc-cici-assistant")
                    .buildAsync(URI.create(url), this)
                    .join();
        }

        void sendAudio(ByteBuffer audioPcm16le) {
            if (ws == null) return;
            ws.sendBinary(audioPcm16le, true);
        }

        void finishTask() {
            if (ws == null) return;
            try {
                Map<String, Object> finish = Map.of(
                        "header", Map.of(
                                "action", "finish-task",
                                "task_id", taskId,
                                "streaming", "duplex"
                        ),
                        "payload", Map.of("input", Map.of())
                );
                ws.sendText(objectMapper.writeValueAsString(finish), true);
            } catch (Exception e) {
                sendClientEvent(ctx.clientSession, Map.of("type", "error", "message", e.getMessage()));
            }
        }

        void close() {
            if (ws != null) {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
            }
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            try {
                Map<String, Object> runTask = new HashMap<>();
                runTask.put("header", Map.of(
                        "action", "run-task",
                        "task_id", taskId,
                        "streaming", "duplex"
                ));
                runTask.put("payload", Map.of(
                        "task_group", "audio",
                        "task", "asr",
                        "function", "recognition",
                        "model", model,
                        "parameters", Map.of(
                                "format", "pcm",
                                "sample_rate", sampleRate,
                                "disfluency_removal_enabled", false
                        ),
                        "input", Map.of()
                ));
                webSocket.sendText(objectMapper.writeValueAsString(runTask), true);
            } catch (Exception e) {
                sendClientEvent(ctx.clientSession, Map.of("type", "error", "message", e.getMessage()));
            }
            WebSocket.Listener.super.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            if (!last) {
                textBuffer.append(data);
                return WebSocket.Listener.super.onText(webSocket, data, false);
            }
            if (textBuffer.length() > 0) {
                textBuffer.append(data);
                data = textBuffer.toString();
                textBuffer.setLength(0);
            }
            handleAliyunEvent(String.valueOf(data));
            return WebSocket.Listener.super.onText(webSocket, data, true);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            return WebSocket.Listener.super.onBinary(webSocket, data, last);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            sendClientEvent(ctx.clientSession, Map.of(
                    "type", "error",
                    "message", error.getMessage() == null ? "ASR websocket error" : error.getMessage()
            ));
            WebSocket.Listener.super.onError(webSocket, error);
        }

        private void handleAliyunEvent(String raw) {
            try {
                JsonNode root = objectMapper.readTree(raw);
                JsonNode header = root.path("header");
                String event = header.path("event").asText("");
                if ("task-started".equals(event)) {
                    ctx.started = true;
                    sendClientEvent(ctx.clientSession, Map.of("type", "status", "message", "started"));
                    return;
                }
                if ("result-generated".equals(event)) {
                    JsonNode sentence = root.path("payload").path("output").path("sentence");
                    String text = sentence.path("text").asText("");
                    boolean sentenceEnd = sentence.path("sentence_end").asBoolean(false);
                    if (!text.isBlank()) {
                        sendClientEvent(ctx.clientSession, Map.of(
                                "type", sentenceEnd ? "final" : "partial",
                                "text", text
                        ));
                    }
                    return;
                }
                if ("task-finished".equals(event)) {
                    sendClientEvent(ctx.clientSession, Map.of("type", "finished"));
                    return;
                }
                if ("task-failed".equals(event)) {
                    String err = header.path("error_message").asText("task failed");
                    sendClientEvent(ctx.clientSession, Map.of("type", "error", "message", err));
                }
            } catch (Exception e) {
                sendClientEvent(ctx.clientSession, Map.of("type", "error", "message", "ASR event parse failed"));
            }
        }
    }

    private static final class SessionCtx {
        private final WebSocketSession clientSession;
        @SuppressWarnings("unused")
        private final String orgId;
        @SuppressWarnings("unused")
        private final String userId;
        private volatile boolean started;
        private volatile AliyunWsClient aliyunClient;

        private SessionCtx(WebSocketSession clientSession, String orgId, String userId) {
            this.clientSession = clientSession;
            this.orgId = orgId;
            this.userId = userId;
        }
    }
}

