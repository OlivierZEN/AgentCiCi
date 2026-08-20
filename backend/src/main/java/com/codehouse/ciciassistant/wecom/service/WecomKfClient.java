package com.codehouse.ciciassistant.wecom.service;

import com.codehouse.ciciassistant.common.crypto.SecretCipherService;
import com.codehouse.ciciassistant.wecom.domain.WecomKfAccountEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WecomKfClient {

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final ObjectMapper objectMapper;
    private final WecomKfConfigService configService;
    private final SecretCipherService secretCipherService;
    private final WecomKfProperties properties;
    private final JsonTransport transport;
    private final Map<String, CachedTicket> ticketCache = new ConcurrentHashMap<>();
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    public WecomKfClient(ObjectMapper objectMapper,
                         WecomKfConfigService configService,
                         SecretCipherService secretCipherService,
                         WecomKfProperties properties) {
        this(objectMapper, configService, secretCipherService, properties, null);
    }

    WecomKfClient(ObjectMapper objectMapper,
                  WecomKfConfigService configService,
                  SecretCipherService secretCipherService,
                  WecomKfProperties properties,
                  JsonTransport transport) {
        this.objectMapper = objectMapper;
        this.configService = configService;
        this.secretCipherService = secretCipherService;
        this.properties = properties;
        this.transport = transport == null ? this::requestJson : transport;
    }

    public SyncResult syncMessages(WecomKfConfigService.ResolvedAccount resolved, String token, String cursor) {
        String accessToken = accessToken(resolved);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("token", token);
        payload.put("open_kfid", resolved.account().getOpenKfId());
        if (cursor != null && !cursor.isBlank()) {
            payload.put("cursor", cursor);
        }
        JsonNode body = postJson("/cgi-bin/kf/sync_msg?access_token=" + accessToken, payload);
        List<SyncedMessage> messages = new ArrayList<>();
        for (JsonNode item : body.path("msg_list")) {
            String msgType = text(item, "msgtype");
            String content = "";
            JsonNode event = item.path("event");
            if ("text".equalsIgnoreCase(msgType)) {
                content = item.path("text").path("content").asText("");
            }
            messages.add(new SyncedMessage(
                    text(item, "msgid"),
                    firstText(item, event, "open_kfid"),
                    firstText(item, event, "external_userid"),
                    msgType,
                    content,
                    item.path("send_time").asLong(0L),
                    item.path("origin").asInt(0),
                    text(item, "servicer_userid"),
                    text(event, "event_type"),
                    event.path("change_type").asInt(0),
                    event.path("service_state").asInt(-1),
                    text(event, "old_servicer_userid"),
                    text(event, "new_servicer_userid")));
        }
        return new SyncResult(messages, body.path("next_cursor").asText(""), body.path("has_more").asInt(0) == 1);
    }

    public SendResult sendText(WecomKfConfigService.ResolvedAccount resolved, String externalUserId, String content) {
        String accessToken = accessToken(resolved);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("touser", externalUserId);
        payload.put("open_kfid", resolved.account().getOpenKfId());
        payload.put("msgtype", "text");
        payload.put("text", Map.of("content", content == null ? "" : content));
        JsonNode body = postJson("/cgi-bin/kf/send_msg?access_token=" + accessToken, payload);
        return new SendResult(text(body, "msgid"));
    }

    public ServiceState getServiceState(WecomKfConfigService.ResolvedAccount resolved, String externalUserId) {
        String accessToken = accessToken(resolved);
        JsonNode body = postJson("/cgi-bin/kf/service_state/get?access_token=" + accessToken, Map.of(
                "open_kfid", resolved.account().getOpenKfId(),
                "external_userid", externalUserId));
        return new ServiceState(body.path("service_state").asInt(-1), text(body, "servicer_userid"));
    }

    public TransferResult transferServiceState(WecomKfConfigService.ResolvedAccount resolved,
                                               String externalUserId,
                                               int targetState,
                                               String servicerUserId) {
        if (targetState != 2 && targetState != 3) {
            throw new IllegalArgumentException("targetState must be 2 or 3");
        }
        String accessToken = accessToken(resolved);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("open_kfid", resolved.account().getOpenKfId());
        payload.put("external_userid", externalUserId);
        payload.put("service_state", targetState);
        if (targetState == 3) {
            String userId = servicerUserId == null ? "" : servicerUserId.trim();
            if (userId.isBlank()) {
                throw new IllegalArgumentException("servicerUserId is required for state 3");
            }
            payload.put("servicer_userid", userId);
        }
        JsonNode body = postJson("/cgi-bin/kf/service_state/trans?access_token=" + accessToken, payload);
        return new TransferResult(body.path("msg_code").asText(""));
    }

    public List<Servicer> listServicers(WecomKfConfigService.ResolvedAccount resolved) {
        String accessToken = accessToken(resolved);
        JsonNode body = getJson("/cgi-bin/kf/servicer/list?access_token=" + accessToken
                + "&open_kfid=" + encode(resolved.account().getOpenKfId()));
        List<Servicer> result = new ArrayList<>();
        for (JsonNode item : body.path("servicer_list")) {
            String userId = text(item, "userid");
            if (!userId.isBlank()) {
                result.add(new Servicer(userId, item.path("status").asInt(-1)));
            }
        }
        return List.copyOf(result);
    }

    public Optional<String> resolveCurrentMember(WecomKfConfigService.ResolvedAccount resolved, String code) {
        String normalizedCode = code == null ? "" : code.trim();
        if (normalizedCode.isBlank()) {
            return Optional.empty();
        }
        String accessToken = appAccessToken(resolved);
        JsonNode body = getJson("/cgi-bin/auth/getuserinfo?access_token=" + accessToken + "&code=" + encode(normalizedCode));
        String userId = text(body, "UserId");
        return userId.isBlank() ? Optional.empty() : Optional.of(userId);
    }

    public JsSdkBundle jsSdkBundle(WecomKfConfigService.ResolvedAccount resolved, String pageUrl) {
        String normalizedUrl = requireTrustedPageUrl(pageUrl);
        long timestamp = Instant.now().getEpochSecond();
        String nonce = randomHex(16);
        String corpTicket = jsApiTicket(resolved, false);
        String agentTicket = jsApiTicket(resolved, true);
        return new JsSdkBundle(
                resolved.account().getCorpId(),
                resolved.account().getWecomAppAgentId(),
                timestamp,
                nonce,
                ticketSignature(corpTicket, nonce, timestamp, normalizedUrl),
                ticketSignature(agentTicket, nonce, timestamp, normalizedUrl));
    }

    public ConnectionTestResult testConnection(WecomKfConfigService.ResolvedAccount resolved) {
        Instant checkedAt = Instant.now();
        fetchAndCacheAccessToken(resolved);
        String mobileStatus = "disabled";
        int servicerCount = 0;
        if (resolved.account().isMobileHandoffEnabled()) {
            appAccessToken(resolved);
            List<Servicer> servicers = listServicers(resolved);
            servicerCount = servicers.size();
            jsSdkBundle(resolved, properties.getPublicBaseUrl() + "/mobile/wechat-kf");
            mobileStatus = "connected";
        }
        return new ConnectionTestResult(
                "connected",
                checkedAt,
                resolved.account().getAccessTokenExpiresAt(),
                root(),
                mobileStatus,
                servicerCount);
    }

    @Transactional
    protected String accessToken(WecomKfConfigService.ResolvedAccount resolved) {
        WecomKfAccountEntity account = resolved.account();
        if (account.getAccessTokenCipher() != null
                && account.getAccessTokenIv() != null
                && account.getAccessTokenExpiresAt() != null
                && account.getAccessTokenExpiresAt().isAfter(Instant.now().plusSeconds(60))) {
            return secretCipherService.decryptUtf8(account.getAccessTokenCipher(), account.getAccessTokenIv());
        }
        return fetchAndCacheAccessToken(resolved);
    }

    @Transactional
    protected String appAccessToken(WecomKfConfigService.ResolvedAccount resolved) {
        WecomKfAccountEntity account = resolved.account();
        if (account.getWecomAppAccessTokenCipher() != null
                && account.getWecomAppAccessTokenIv() != null
                && account.getWecomAppAccessTokenExpiresAt() != null
                && account.getWecomAppAccessTokenExpiresAt().isAfter(Instant.now().plusSeconds(60))) {
            return secretCipherService.decryptUtf8(account.getWecomAppAccessTokenCipher(), account.getWecomAppAccessTokenIv());
        }
        String appSecret = resolved.wecomAppSecret() == null ? "" : resolved.wecomAppSecret().trim();
        if (appSecret.isBlank()) {
            throw new IllegalStateException("WeCom self-built application Secret is not configured");
        }
        String path = "/cgi-bin/gettoken?corpid=" + encode(account.getCorpId()) + "&corpsecret=" + encode(appSecret);
        JsonNode body = getJson(path);
        String token = body.path("access_token").asText("");
        if (token.isBlank()) {
            throw new IllegalStateException("WeCom application access_token is missing");
        }
        long expiresIn = Math.max(300L, body.path("expires_in").asLong(7200L));
        SecretCipherService.EncryptedSecret encrypted = secretCipherService.encryptUtf8(token);
        account.updateWecomAppAccessToken(encrypted.cipherBase64(), encrypted.ivBase64(), Instant.now().plusSeconds(expiresIn - 120));
        configService.save(account);
        return token;
    }

    private String fetchAndCacheAccessToken(WecomKfConfigService.ResolvedAccount resolved) {
        WecomKfAccountEntity account = resolved.account();
        String path = "/cgi-bin/gettoken?corpid=" + encode(account.getCorpId()) + "&corpsecret=" + encode(resolved.secret());
        JsonNode body = getJson(path);
        String token = body.path("access_token").asText("");
        if (token.isBlank()) {
            throw new IllegalStateException("WeCom access_token is missing");
        }
        long expiresIn = Math.max(300L, body.path("expires_in").asLong(7200L));
        SecretCipherService.EncryptedSecret encrypted = secretCipherService.encryptUtf8(token);
        account.updateAccessToken(encrypted.cipherBase64(), encrypted.ivBase64(), Instant.now().plusSeconds(expiresIn - 120));
        configService.save(account);
        return token;
    }

    private JsonNode getJson(String path) {
        try {
            return transport.request("GET", path, Map.of());
        } catch (Exception ex) {
            throw new IllegalStateException("WeCom GET failed", ex);
        }
    }

    private JsonNode postJson(String path, Map<String, Object> payload) {
        try {
            return transport.request("POST", path, payload);
        } catch (Exception ex) {
            throw new IllegalStateException("WeCom POST failed", ex);
        }
    }

    private JsonNode requestJson(String method, String path, Map<String, Object> payload) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(root() + path))
                .timeout("GET".equals(method) ? Duration.ofSeconds(12) : Duration.ofSeconds(20));
        HttpRequest request;
        if ("GET".equals(method)) {
            request = builder.GET().build();
        } else {
            request = builder
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();
        }
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return parseResponse(response);
    }

    private JsonNode parseResponse(HttpResponse<String> response) throws Exception {
        JsonNode body = objectMapper.readTree(response.body() == null ? "{}" : response.body());
        int errcode = body.path("errcode").asInt(0);
        if (response.statusCode() / 100 != 2 || errcode != 0) {
            throw new IllegalStateException("WeCom API error: http=" + response.statusCode()
                    + ", errcode=" + errcode + ", errmsg=" + body.path("errmsg").asText(""));
        }
        return body;
    }

    private String text(JsonNode node, String field) {
        return node == null ? "" : node.path(field).asText("").trim();
    }

    private String firstText(JsonNode primary, JsonNode secondary, String field) {
        String value = text(primary, field);
        return value.isBlank() ? text(secondary, field) : value;
    }

    private String root() {
        return properties.getApiBaseUrl().replaceAll("/+$", "");
    }

    private String jsApiTicket(WecomKfConfigService.ResolvedAccount resolved, boolean agentConfig) {
        String cacheKey = resolved.account().getCorpId() + ":" + (agentConfig ? "agent" : "corp");
        CachedTicket cached = ticketCache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(Instant.now().plusSeconds(60))) {
            return cached.ticket();
        }
        String accessToken = appAccessToken(resolved);
        String path = agentConfig
                ? "/cgi-bin/ticket/get?access_token=" + accessToken + "&type=agent_config"
                : "/cgi-bin/get_jsapi_ticket?access_token=" + accessToken;
        JsonNode body = getJson(path);
        String ticket = text(body, "ticket");
        if (ticket.isBlank()) {
            throw new IllegalStateException("WeCom JS-SDK ticket is missing");
        }
        long expires = Math.max(300L, body.path("expires_in").asLong(7200L));
        ticketCache.put(cacheKey, new CachedTicket(ticket, Instant.now().plusSeconds(expires - 120)));
        return ticket;
    }

    private String requireTrustedPageUrl(String pageUrl) {
        String configured = properties.getPublicBaseUrl();
        if (configured.isBlank()) {
            throw new IllegalStateException("app.wecom-kf.public-base-url is required for mobile handoff");
        }
        try {
            URI configuredUri = URI.create(configured);
            URI candidate = URI.create(pageUrl == null ? "" : pageUrl.trim());
            boolean trusted = "https".equalsIgnoreCase(candidate.getScheme())
                    && configuredUri.getHost() != null
                    && configuredUri.getHost().equalsIgnoreCase(candidate.getHost())
                    && effectivePort(configuredUri) == effectivePort(candidate)
                    && candidate.getUserInfo() == null
                    && candidate.getFragment() == null;
            if (!trusted) {
                throw new IllegalArgumentException("pageUrl is outside the configured public origin");
            }
            return candidate.toString();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("pageUrl is invalid or untrusted", ex);
        }
    }

    private int effectivePort(URI uri) {
        return uri.getPort() < 0 ? 443 : uri.getPort();
    }

    private String ticketSignature(String ticket, String nonce, long timestamp, String url) {
        String canonical = "jsapi_ticket=" + ticket + "&noncestr=" + nonce + "&timestamp=" + timestamp + "&url=" + url;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign WeCom JS-SDK configuration", ex);
        }
    }

    private String randomHex(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return HexFormat.of().formatHex(value);
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    public record SyncResult(List<SyncedMessage> messages, String nextCursor, boolean hasMore) {
    }

    public record ConnectionTestResult(String status,
                                       Instant checkedAt,
                                       Instant accessTokenExpiresAt,
                                       String apiBaseUrl,
                                       String mobileHandoffStatus,
                                       int servicerCount) {
    }

    public record ServiceState(int state, String servicerUserId) {
        public ServiceState {
            if (state < 0 || state > 4) {
                throw new IllegalStateException("WeCom service_state is missing or invalid: " + state);
            }
        }
    }

    public record TransferResult(String messageCode) {
    }

    public record SendResult(String messageId) {
    }

    public record Servicer(String userId, int status) {
        public boolean accepting() {
            return status == 0;
        }
    }

    public record JsSdkBundle(String corpId,
                              String agentId,
                              long timestamp,
                              String nonce,
                              String corpSignature,
                              String agentSignature) {
    }

    private record CachedTicket(String ticket, Instant expiresAt) {
    }

    public record SyncedMessage(String msgId,
                                String openKfId,
                                String externalUserId,
                                String msgType,
                                String content,
                                long sendTime,
                                int origin,
                                String servicerUserId,
                                String eventType,
                                int changeType,
                                int eventServiceState,
                                String oldServicerUserId,
                                String newServicerUserId) {
        public SyncedMessage(String msgId,
                             String openKfId,
                             String externalUserId,
                             String msgType,
                             String content,
                             long sendTime) {
            this(msgId, openKfId, externalUserId, msgType, content, sendTime,
                    3, "", "", 0, -1, "", "");
        }
    }

    interface JsonTransport {
        JsonNode request(String method, String path, Map<String, Object> payload) throws Exception;
    }
}
