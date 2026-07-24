package com.codehouse.ciciassistant.feishu.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FeishuUserProfileService {

    private static final Logger log = LoggerFactory.getLogger(FeishuUserProfileService.class);
    private static final String FEISHU_HOST = "https://open.feishu.cn";

    private final FeishuBotConfigService feishuBotConfigService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Map<String, CachedToken> tokenCache = new ConcurrentHashMap<>();

    public FeishuUserProfileService(FeishuBotConfigService feishuBotConfigService, ObjectMapper objectMapper) {
        this.feishuBotConfigService = feishuBotConfigService;
        this.objectMapper = objectMapper;
    }

    public Optional<Profile> fetchProfile(String companyId, String openId) {
        if (openId == null || openId.isBlank()) {
            return Optional.empty();
        }
        FeishuBotConfigService.FeishuBotConfig config = feishuBotConfigService.getEnabledConfig(companyId).orElse(null);
        if (config == null) {
            return Optional.empty();
        }
        String token = resolveTenantToken(config).orElse("");
        if (token.isBlank()) {
            return Optional.empty();
        }
        try {
            String encodedOpenId = URLEncoder.encode(openId, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(FEISHU_HOST + "/open-apis/contact/v3/users/" + encodedOpenId + "?user_id_type=open_id"))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(resp.body());
            if (resp.statusCode() != 200 || root.path("code").asInt(-1) != 0) {
                log.warn("Feishu profile query failed, companyId={}, openId={}, status={}, code={}, msg={}",
                        companyId, openId, resp.statusCode(), root.path("code").asText(""), root.path("msg").asText(""));
                return Optional.empty();
            }
            JsonNode user = root.path("data").path("user");
            String name = user.path("name").asText("").trim();
            String avatar = user.path("avatar_url").asText("").trim();
            if (name.isBlank() && avatar.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new Profile(name, avatar));
        } catch (Exception ex) {
            log.warn("Failed to fetch Feishu profile, companyId={}, openId={}", companyId, openId, ex);
            return Optional.empty();
        }
    }

    private Optional<String> resolveTenantToken(FeishuBotConfigService.FeishuBotConfig config) {
        CachedToken cached = tokenCache.get(config.companyId());
        if (cached != null && cached.expiresAt().isAfter(Instant.now().plusSeconds(60))) {
            return Optional.of(cached.token());
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(FEISHU_HOST + "/open-apis/auth/v3/tenant_access_token/internal"))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(Map.of(
                            "app_id", config.appId(),
                            "app_secret", config.appSecret()
                    ))))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(resp.body());
            if (resp.statusCode() != 200 || root.path("code").asInt(-1) != 0) {
                log.warn("Feishu tenant token query failed, companyId={}, status={}, code={}, msg={}",
                        config.companyId(), resp.statusCode(), root.path("code").asText(""), root.path("msg").asText(""));
                return Optional.empty();
            }
            String token = root.path("tenant_access_token").asText("").trim();
            long expireSeconds = root.path("expire").asLong(7200L);
            if (token.isBlank()) {
                return Optional.empty();
            }
            tokenCache.put(config.companyId(), new CachedToken(token, Instant.now().plusSeconds(Math.max(60L, expireSeconds))));
            return Optional.of(token);
        } catch (Exception ex) {
            log.warn("Failed to fetch Feishu tenant token, companyId={}", config.companyId(), ex);
            return Optional.empty();
        }
    }

    public record Profile(String displayName, String avatarUrl) {
    }

    private record CachedToken(String token, Instant expiresAt) {
    }
}
