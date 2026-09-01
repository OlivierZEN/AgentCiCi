package com.codehouse.ciciassistant.ai.ws;

import com.codehouse.ciciassistant.auth.service.JwtService;
import com.codehouse.ciciassistant.ai.service.ModelInvocationResolver;
import com.codehouse.ciciassistant.integration.service.IntegrationAppService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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
    private final IntegrationAppService integrationAppService;
    private final ModelInvocationResolver modelInvocationResolver;
    private final String aliyunApiKey;
    private final String aliyunModel;
    private final String aliyunUrl;
    private final boolean iflytekEnabled;
    private final String iflytekAppId;
    private final String iflytekAccessKeyId;
    private final String iflytekAccessKeySecret;
    private final String iflytekUrl;
    private final String iflytekLang;
    private final String iflytekDomain;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ConcurrentHashMap<String, SessionCtx> sessions = new ConcurrentHashMap<>();

    public AliyunRealtimeAsrWebSocketHandler(JwtService jwtService,
                                             ObjectMapper objectMapper,
                                             IntegrationAppService integrationAppService,
                                             ModelInvocationResolver modelInvocationResolver,
                                             @Value("${app.model.aliyun.api-key:}") String aliyunApiKey,
                                             @Value("${app.voice.aliyun.realtime-model:paraformer-realtime-v2}") String aliyunModel,
                                             @Value("${app.voice.aliyun.realtime-url:wss://dashscope.aliyuncs.com/api-ws/v1/inference}") String aliyunUrl,
                                             @Value("${app.voice.iflytek.enabled:false}") boolean iflytekEnabled,
                                             @Value("${app.voice.iflytek.app-id:}") String iflytekAppId,
                                             @Value("${app.voice.iflytek.access-key-id:}") String iflytekAccessKeyId,
                                             @Value("${app.voice.iflytek.access-key-secret:}") String iflytekAccessKeySecret,
                                             @Value("${app.voice.iflytek.realtime-url:wss://office-api-ast-dx.iflyaisol.com/ast/communicate/v1}") String iflytekUrl,
                                             @Value("${app.voice.iflytek.lang:autodialect}") String iflytekLang,
                                             @Value("${app.voice.iflytek.domain:com}") String iflytekDomain) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
        this.integrationAppService = integrationAppService;
        this.modelInvocationResolver = modelInvocationResolver;
        this.aliyunApiKey = aliyunApiKey;
        this.aliyunModel = aliyunModel;
        this.aliyunUrl = aliyunUrl;
        this.iflytekEnabled = iflytekEnabled;
        this.iflytekAppId = iflytekAppId;
        this.iflytekAccessKeyId = iflytekAccessKeyId;
        this.iflytekAccessKeySecret = iflytekAccessKeySecret;
        this.iflytekUrl = iflytekUrl;
        this.iflytekLang = iflytekLang;
        this.iflytekDomain = iflytekDomain;
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
        if ("embed_app".equals(claims.get("typ", String.class))) {
            List<String> permissions = claimStrings(claims.get("permissions"));
            if (!permissions.contains("voice:input") && !permissions.contains("meeting:start")) {
                session.close(CloseStatus.POLICY_VIOLATION.withReason("voice permission denied"));
                return;
            }
        }
        String companyId = String.valueOf(claims.get("company_id"));
        String memberId = claims.get("member_id", String.class);
        String userId = memberId == null || memberId.isBlank() ? claims.getSubject() : memberId;
        SessionCtx ctx = new SessionCtx(session, companyId, userId);
        sessions.put(session.getId(), ctx);
        sendClientEvent(session, Map.of("type", "status", "message", "connected"));
    }

    private static List<String> claimStrings(Object raw) {
        if (!(raw instanceof List<?> values)) {
            return List.of();
        }
        return values.stream().filter(java.util.Objects::nonNull).map(String::valueOf).toList();
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
                String requestedProvider = firstNonBlank(j.path("provider").asText(""), queryParam(session, "provider"));
                boolean speakerDiarization = j.path("speakerDiarization").asBoolean(
                        "true".equalsIgnoreCase(queryParam(session, "speakerDiarization")));
                if ("iflytek".equals(selectRealtimeProvider(requestedProvider))) {
                    startIflytekRoutedTask(ctx, sampleRate, speakerDiarization);
                } else {
                    startAliyunTask(ctx, sampleRate);
                }
            } else if ("stop".equalsIgnoreCase(type)) {
                ctx.started = false;
                if (ctx.aliyunClient != null) {
                    ctx.aliyunClient.finishTask();
                }
                if (ctx.iflytekClient != null) {
                    ctx.iflytekClient.finishTask();
                }
            } else if ("ping".equalsIgnoreCase(type)) {
                sendClientEvent(session, Map.of("type", "pong"));
            }
        } catch (Exception e) {
            ctx.started = false;
            log.warn("Realtime ASR command failed at upstream startup: {}", e.getClass().getSimpleName());
            sendClientEvent(session, Map.of("type", "error", "message", "实时语音服务启动失败，请检查厂商地址和凭据"));
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        SessionCtx ctx = sessions.get(session.getId());
        if (ctx == null || !ctx.started) return;
        if (ctx.aliyunClient != null) {
            ctx.aliyunClient.sendAudio(message.getPayload().asReadOnlyBuffer());
        }
        if (ctx.iflytekClient != null) {
            ctx.iflytekClient.sendAudio(message.getPayload().asReadOnlyBuffer());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        SessionCtx ctx = sessions.remove(session.getId());
        if (ctx != null && ctx.aliyunClient != null) {
            ctx.aliyunClient.close();
        }
        if (ctx != null && ctx.iflytekClient != null) {
            ctx.iflytekClient.close();
        }
    }

    private void startIflytekRoutedTask(SessionCtx ctx, int sampleRate, boolean speakerDiarization) throws Exception {
        ModelInvocationResolver.ResolvedModelRoute route;
        try {
            route = modelInvocationResolver.resolveRoute(ctx.companyId, "meeting-realtime-asr");
        } catch (RuntimeException ex) {
            sendClientEvent(ctx.clientSession, Map.of("type", "error", "message", ex.getMessage()));
            return;
        }
        if (!isIflytekRoute(route.providerCode())) {
            sendClientEvent(ctx.clientSession, Map.of("type", "error", "message", "AI 听记实时转写必须使用讯飞 ASR 路由"));
            return;
        }
        startIflytekTask(ctx, sampleRate, speakerDiarization, resolveIflytekConfig(ctx.companyId));
    }

    private void startAliyunTask(SessionCtx ctx, int sampleRate) throws Exception {
        if (aliyunApiKey == null || aliyunApiKey.isBlank()) {
            sendClientEvent(ctx.clientSession, Map.of("type", "error", "message", "Aliyun API key is missing"));
            return;
        }
        if (ctx.aliyunClient != null) {
            ctx.aliyunClient.close();
        }
        if (ctx.iflytekClient != null) {
            ctx.iflytekClient.close();
            ctx.iflytekClient = null;
        }
        String taskId = UUID.randomUUID().toString();
        AliyunWsClient client = new AliyunWsClient(ctx, taskId, Math.max(8000, sampleRate),
                new AliyunWsRuntime(aliyunApiKey, aliyunModel, aliyunUrl));
        ctx.aliyunClient = client;
        client.connect();
    }

    private void startIflytekTask(SessionCtx ctx,
                                  int sampleRate,
                                  boolean speakerDiarization,
                                  IflytekRuntimeConfig config) throws Exception {
        if (!config.enabled()) {
            sendClientEvent(ctx.clientSession, Map.of("type", "error", "message", "Iflytek realtime ASR is disabled"));
            return;
        }
        if (config.appId().isBlank() || config.accessKeyId().isBlank() || config.accessKeySecret().isBlank()) {
            sendClientEvent(ctx.clientSession, Map.of("type", "error", "message", "Iflytek realtime ASR credentials are missing"));
            return;
        }
        if (ctx.iflytekClient != null) {
            ctx.iflytekClient.close();
        }
        if (ctx.aliyunClient != null) {
            ctx.aliyunClient.close();
            ctx.aliyunClient = null;
        }
        String sessionId = UUID.randomUUID().toString();
        IflytekWsClient client = new IflytekWsClient(ctx, sessionId, Math.max(8000, sampleRate), speakerDiarization, config);
        ctx.iflytekClient = client;
        client.connect();
    }

    static String selectRealtimeProvider(String requestedProvider) {
        String normalized = requestedProvider == null ? "" : requestedProvider.trim().toLowerCase();
        if ("iflytek".equals(normalized) || "xunfei".equals(normalized)) {
            return "iflytek";
        }
        return "aliyun";
    }

    static boolean isIflytekRoute(String providerCode) {
        return IntegrationAppService.APP_CODE_IFLYTEK_ASR.equals(providerCode);
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

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b == null ? "" : b);
    }

    private static String encodeQueryValue(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private IflytekRuntimeConfig resolveIflytekConfig(String companyId) {
        var integrationEnabled = integrationAppService.isEnabled(companyId, IntegrationAppService.APP_CODE_IFLYTEK_ASR);
        if (integrationEnabled.isPresent() && !integrationEnabled.get()) {
            return new IflytekRuntimeConfig(false, "", "", "", iflytekUrl, iflytekLang, iflytekDomain);
        }
        Map<String, Object> rawConfig = integrationAppService
                .findRawConfig(companyId, IntegrationAppService.APP_CODE_IFLYTEK_ASR)
                .orElse(Map.of());
        boolean enabled = integrationEnabled.orElse(iflytekEnabled);
        String appId = firstNonBlank(configString(rawConfig, "appId"), iflytekAppId);
        String accessKeyId = firstNonBlank(configString(rawConfig, "accessKeyId"), iflytekAccessKeyId);
        String accessKeySecret = firstNonBlank(
                integrationAppService.decryptIflytekAccessKeySecret(rawConfig).orElse(""),
                iflytekAccessKeySecret);
        String realtimeUrl = IntegrationAppService.normalizeIflytekRealtimeUrl(
                firstNonBlank(configString(rawConfig, "realtimeUrl"), iflytekUrl));
        String lang = firstNonBlank(configString(rawConfig, "lang"), iflytekLang);
        String domain = firstNonBlank(configString(rawConfig, "domain"), iflytekDomain);
        return new IflytekRuntimeConfig(enabled, appId, accessKeyId, accessKeySecret, realtimeUrl, lang, domain);
    }

    private static String configString(Map<String, Object> config, String key) {
        Object value = config == null ? null : config.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String buildIflytekUrl(String sessionId, int sampleRate, boolean speakerDiarization, IflytekRuntimeConfig config) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("accessKeyId", config.accessKeyId());
        params.put("appId", config.appId());
        params.put("uuid", sessionId);
        params.put("utc", OffsetDateTime.now(ZoneOffset.ofHours(8)).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")));
        params.put("audio_encode", "pcm_s16le");
        params.put("lang", config.lang().isBlank() ? "autodialect" : config.lang());
        params.put("samplerate", String.valueOf(sampleRate));
        if (speakerDiarization) {
            params.put("role_type", "2");
        }
        if (!config.domain().isBlank()) {
            params.put("pd", config.domain());
        }
        String baseString = params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> encodeQueryValue(entry.getKey()) + "=" + encodeQueryValue(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(config.accessKeySecret().getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        String signature = Base64.getEncoder().encodeToString(mac.doFinal(baseString.getBytes(StandardCharsets.UTF_8)));
        String query = baseString + "&signature=" + encodeQueryValue(signature);
        String realtimeUrl = config.realtimeUrl().isBlank() ? iflytekUrl : config.realtimeUrl();
        return realtimeUrl + (realtimeUrl.contains("?") ? "&" : "?") + query;
    }

    private record IflytekRuntimeConfig(boolean enabled,
                                        String appId,
                                        String accessKeyId,
                                        String accessKeySecret,
                                        String realtimeUrl,
                                        String lang,
                                        String domain) {
    }

    private record AliyunWsRuntime(String apiKey, String modelName, String url) {
    }

    private final class IflytekWsClient implements WebSocket.Listener {
        private final SessionCtx ctx;
        private final String sessionId;
        private final int sampleRate;
        @SuppressWarnings("unused")
        private final boolean speakerDiarization;
        private final IflytekRuntimeConfig config;
        private final StringBuilder textBuffer = new StringBuilder();
        private CompletableFuture<WebSocket> sendChain = CompletableFuture.completedFuture(null);
        private String activeSpeakerId = "";
        private volatile boolean finishing;
        private volatile boolean finishedSignalled;
        private WebSocket ws;

        private IflytekWsClient(SessionCtx ctx,
                                String sessionId,
                                int sampleRate,
                                boolean speakerDiarization,
                                IflytekRuntimeConfig config) {
            this.ctx = ctx;
            this.sessionId = sessionId;
            this.sampleRate = sampleRate;
            this.speakerDiarization = speakerDiarization;
            this.config = config;
        }

        void connect() throws Exception {
            ws = httpClient.newWebSocketBuilder()
                    .header("user-agent", "cc-cici-assistant")
                    .buildAsync(URI.create(buildIflytekUrl(sessionId, sampleRate, speakerDiarization, config)), this)
                    .join();
        }

        void sendAudio(ByteBuffer audioPcm16le) {
            if (ws == null || finishing) return;
            ByteBuffer copy = ByteBuffer.allocate(audioPcm16le.remaining());
            copy.put(audioPcm16le);
            copy.flip();
            synchronized (this) {
                sendChain = sendChain
                        .exceptionally(error -> ws)
                        .thenCompose(ignored -> ws.sendBinary(copy, true));
            }
        }

        void finishTask() {
            if (ws == null || finishing) return;
            finishing = true;
            try {
                String endPayload = objectMapper.writeValueAsString(Map.of("end", true, "sessionId", sessionId));
                synchronized (this) {
                    sendChain = sendChain
                            .exceptionally(error -> ws)
                            .thenCompose(ignored -> ws.sendText(endPayload, true));
                }
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
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            if (!last) {
                textBuffer.append(data);
                webSocket.request(1);
                return null;
            }
            if (textBuffer.length() > 0) {
                textBuffer.append(data);
                data = textBuffer.toString();
                textBuffer.setLength(0);
            }
            handleIflytekEvent(String.valueOf(data));
            webSocket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            sendClientEvent(ctx.clientSession, Map.of(
                    "type", "error",
                    "message", error.getMessage() == null ? "Iflytek ASR websocket error" : error.getMessage()
            ));
            completeClientSession();
            WebSocket.Listener.super.onError(webSocket, error);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (!finishing && !finishedSignalled) {
                sendClientEvent(ctx.clientSession, Map.of(
                        "type", "error",
                        "message", "讯飞实时语音连接已提前关闭"
                ));
            }
            completeClientSession();
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }

        private void handleIflytekEvent(String raw) {
            try {
                JsonNode root = objectMapper.readTree(raw);
                String action = firstNonBlank(root.path("action").asText(""), root.path("data").path("action").asText(""));
                int code = root.path("code").asInt(0);
                if ("started".equalsIgnoreCase(action)) {
                    ctx.started = true;
                    sendClientEvent(ctx.clientSession, Map.of("type", "status", "message", "started", "provider", "iflytek"));
                    return;
                }
                if ("error".equalsIgnoreCase(action) || code != 0) {
                    ctx.started = false;
                    log.warn("Iflytek realtime ASR rejected the session: code={}, sid={}", code,
                            root.path("sid").asText("unknown"));
                    sendClientEvent(ctx.clientSession, Map.of(
                            "type", "error",
                            "message", firstNonBlank(root.path("desc").asText(""), root.path("message").asText("Iflytek ASR error"))
                    ));
                    completeClientSession();
                    return;
                }
                if (!"result".equalsIgnoreCase(action) && root.has("action")) {
                    return;
                }
                if ("action".equalsIgnoreCase(root.path("msg_type").asText(""))) {
                    return;
                }
                JsonNode payload = IflytekAsrResultParser.parsePayload(objectMapper, root);
                if ("frc".equalsIgnoreCase(root.path("res_type").asText(""))
                        && !payload.path("normal").asBoolean(true)) {
                    sendClientEvent(ctx.clientSession, Map.of(
                            "type", "error",
                            "message", payload.path("desc").asText("讯飞实时语音服务返回异常")
                    ));
                    completeClientSession();
                    return;
                }
                IflytekAsrResultParser.ExtractionResult extraction = IflytekAsrResultParser.extractPieces(payload, activeSpeakerId);
                activeSpeakerId = extraction.activeSpeakerId();
                List<IflytekAsrResultParser.TranscriptPiece> pieces = extraction.pieces();
                String eventType = IflytekAsrResultParser.isFinal(payload) ? "final" : "partial";
                for (IflytekAsrResultParser.TranscriptPiece piece : pieces) {
                    Map<String, Object> event = new HashMap<>();
                    event.put("type", eventType);
                    event.put("text", piece.text());
                    if (!piece.speakerId().isBlank()) {
                        event.put("speakerId", piece.speakerId());
                        event.put("speakerName", IflytekAsrResultParser.speakerDisplayName(piece.speakerId()));
                    }
                    sendClientEvent(ctx.clientSession, event);
                }
                if (IflytekAsrResultParser.isTerminal(payload)) {
                    completeClientSession();
                }
            } catch (Exception e) {
                sendClientEvent(ctx.clientSession, Map.of("type", "error", "message", "Iflytek ASR event parse failed"));
                completeClientSession();
            }
        }

        private void completeClientSession() {
            if (finishedSignalled) {
                return;
            }
            finishedSignalled = true;
            ctx.started = false;
            sendClientEvent(ctx.clientSession, Map.of("type", "finished"));
        }

    }

    private final class AliyunWsClient implements WebSocket.Listener {
        private final SessionCtx ctx;
        private final String taskId;
        private final int sampleRate;
        private final AliyunWsRuntime runtime;
        private final StringBuilder textBuffer = new StringBuilder();
        private CompletableFuture<WebSocket> sendChain = CompletableFuture.completedFuture(null);
        private volatile boolean finishing;
        private WebSocket ws;

        private AliyunWsClient(SessionCtx ctx, String taskId, int sampleRate, AliyunWsRuntime runtime) {
            this.ctx = ctx;
            this.taskId = taskId;
            this.sampleRate = sampleRate;
            this.runtime = runtime;
        }

        void connect() {
            ws = httpClient.newWebSocketBuilder()
                    .header("Authorization", "Bearer " + runtime.apiKey())
                    .header("user-agent", "cc-cici-assistant")
                    .buildAsync(URI.create(runtime.url()), this)
                    .join();
        }

        void sendAudio(ByteBuffer audioPcm16le) {
            if (ws == null || finishing) return;
            ByteBuffer copy = ByteBuffer.allocate(audioPcm16le.remaining());
            copy.put(audioPcm16le);
            copy.flip();
            synchronized (this) {
                if (finishing) return;
                sendChain = sendChain
                        .exceptionally(error -> ws)
                        .thenCompose(ignored -> ws.sendBinary(copy, true));
            }
        }

        void finishTask() {
            if (ws == null || finishing) return;
            finishing = true;
            try {
                Map<String, Object> finish = Map.of(
                        "header", Map.of(
                                "action", "finish-task",
                                "task_id", taskId,
                                "streaming", "duplex"
                        ),
                        "payload", Map.of("input", Map.of())
                );
                String payload = objectMapper.writeValueAsString(finish);
                synchronized (this) {
                    sendChain = sendChain
                            .exceptionally(error -> ws)
                            .thenCompose(ignored -> ws.sendText(payload, true));
                }
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
                        "model", runtime.modelName(),
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
            webSocket.request(1);
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
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            webSocket.request(1);
            return null;
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
                    ctx.started = false;
                    sendClientEvent(ctx.clientSession, Map.of("type", "finished"));
                    return;
                }
                if ("task-failed".equals(event)) {
                    ctx.started = false;
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
        private final String companyId;
        @SuppressWarnings("unused")
        private final String userId;
        private volatile boolean started;
        private volatile AliyunWsClient aliyunClient;
        private volatile IflytekWsClient iflytekClient;

        private SessionCtx(WebSocketSession clientSession, String companyId, String userId) {
            this.clientSession = clientSession;
            this.companyId = companyId;
            this.userId = userId;
        }
    }
}
