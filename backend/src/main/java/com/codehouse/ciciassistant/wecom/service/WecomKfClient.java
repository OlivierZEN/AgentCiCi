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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
            if ("text".equalsIgnoreCase(msgType)) {
                content = item.path("text").path("content").asText("");
            }
            messages.add(new SyncedMessage(
                    text(item, "msgid"),
                    text(item, "open_kfid"),
                    text(item, "external_userid"),
                    msgType,
                    content,
                    item.path("send_time").asLong(0L)));
        }
        return new SyncResult(messages, body.path("next_cursor").asText(""), body.path("has_more").asInt(0) == 1);
    }

    public void sendText(WecomKfConfigService.ResolvedAccount resolved, String externalUserId, String content) {
        String accessToken = accessToken(resolved);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("touser", externalUserId);
        payload.put("open_kfid", resolved.account().getOpenKfId());
        payload.put("msgtype", "text");
        payload.put("text", Map.of("content", content == null ? "" : content));
        postJson("/cgi-bin/kf/send_msg?access_token=" + accessToken, payload);
    }

    public ConnectionTestResult testConnection(WecomKfConfigService.ResolvedAccount resolved) {
        Instant checkedAt = Instant.now();
        fetchAndCacheAccessToken(resolved);
        return new ConnectionTestResult(
                "connected",
                checkedAt,
                resolved.account().getAccessTokenExpiresAt(),
                root());
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

    private String root() {
        return properties.getApiBaseUrl().replaceAll("/+$", "");
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    public record SyncResult(List<SyncedMessage> messages, String nextCursor, boolean hasMore) {
    }

    public record ConnectionTestResult(String status,
                                       Instant checkedAt,
                                       Instant accessTokenExpiresAt,
                                       String apiBaseUrl) {
    }

    public record SyncedMessage(String msgId,
                                String openKfId,
                                String externalUserId,
                                String msgType,
                                String content,
                                long sendTime) {
    }

    interface JsonTransport {
        JsonNode request(String method, String path, Map<String, Object> payload) throws Exception;
    }
}
