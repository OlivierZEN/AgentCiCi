package com.codehouse.ciciassistant.integration.service;

import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.integration.domain.IntegrationAppEntity;
import com.codehouse.ciciassistant.integration.domain.IntegrationAppRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CloudccAccessTokenService {

    private static final Logger log = LoggerFactory.getLogger(CloudccAccessTokenService.class);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration DEFAULT_TOKEN_TTL = Duration.ofMinutes(50);
    private static final String APP_CODE = IntegrationAppService.APP_CODE_CLOUDCC_CRM;

    private final IntegrationAppRepository integrationAppRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ConcurrentHashMap<String, CachedToken> tokenCache = new ConcurrentHashMap<>();

    public CloudccAccessTokenService(
            IntegrationAppRepository integrationAppRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper) {
        this.integrationAppRepository = integrationAppRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    public Optional<String> getAccessToken(String orgId, String userId) {
        return getSessionContext(orgId, userId).map(CloudccSessionContext::accessToken);
    }

    public void invalidateSessionContext(String orgId, String userId) {
        if (orgId == null || orgId.isBlank() || userId == null || userId.isBlank()) {
            return;
        }
        tokenCache.remove(orgId + "::" + userId);
    }

    public Optional<CloudccSessionContext> getSessionContext(String orgId, String userId) {
        if (orgId == null || orgId.isBlank() || userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        String cacheKey = orgId + "::" + userId;
        CachedToken cached = tokenCache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(Instant.now().plusSeconds(30))) {
            return Optional.of(new CloudccSessionContext(cached.token(), cached.baseUrl(), cached.setupSvc()));
        }
        try {
            Optional<CloudccSessionContext> fresh = fetchAndCacheToken(orgId, userId, cacheKey);
            fresh.ifPresent(t -> log.debug("CloudCC token refreshed for org={}, user={}", orgId, userId));
            return fresh;
        } catch (Exception e) {
            log.warn("Failed to obtain CloudCC token for org={}, user={}: {}", orgId, userId, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<CloudccSessionContext> fetchAndCacheToken(String orgId, String userId, String cacheKey) throws Exception {
        IntegrationAppEntity app = integrationAppRepository.findByOrgIdAndAppCode(orgId, APP_CODE).orElse(null);
        if (app == null || !app.isEnabled()) {
            return Optional.empty();
        }
        Map<String, Object> config = readConfig(app.getConfigJson());
        String cloudccOrgId = stringVal(config.get("orgId"));
        String clientId = stringVal(config.get("clientId"));
        String secretKey = stringVal(config.get("secretKey"));
        // 优先读取 orgapi_switch_address（新的配置字段），兼容历史 baseUrl。
        String orgapiSwitchAddress = stringVal(config.get("orgapi_switch_address"));
        if (orgapiSwitchAddress.isBlank()) {
            orgapiSwitchAddress = stringVal(config.get("baseUrl"));
        }
        if (cloudccOrgId.isBlank() || clientId.isBlank() || secretKey.isBlank()) {
            return Optional.empty();
        }
        UserEntity user = userRepository.findByIdAndOrg_Id(userId, orgId).orElse(null);
        if (user == null) {
            return Optional.empty();
        }
        String username = blankToEmpty(user.getCcUsername());
        String safetyMark = blankToEmpty(user.getCcSafetymark());
        if (username.isBlank() || safetyMark.isBlank()) {
            return Optional.empty();
        }

        String gateway = resolveGateway(cloudccOrgId, orgapiSwitchAddress);
        String setupSvc = deriveSetupSvc(gateway);
        String token = requestToken(gateway, cloudccOrgId, username, safetyMark, clientId, secretKey);
        Instant exp = parseJwtExp(token).orElse(Instant.now().plus(DEFAULT_TOKEN_TTL));
        tokenCache.put(cacheKey, new CachedToken(token, gateway, setupSvc, exp));
        return Optional.of(new CloudccSessionContext(token, gateway, setupSvc));
    }

    /**
     * 解析 CloudCC 组织网关地址：
     * - 若配置了 orgapi_switch_address，则直接请求该地址（通常包含 scope 和 orgId 查询参数）；
     * - 否则回退到官方默认的 apidomain 接口。
     * 响应中的 orgapi_address 将作为实际网关（baseUrl）返回，例如 https://szyd.apis.cloudcc.cn/lightningapi。
     */
    private String resolveGateway(String cloudccOrgId, String orgapiSwitchAddress) throws Exception {
        if (looksLikeDirectApiGateway(orgapiSwitchAddress)) {
            return normalizeDirectApiGateway(orgapiSwitchAddress);
        }
        String url = !orgapiSwitchAddress.isBlank()
                ? orgapiSwitchAddress
                : "https://developer.apis.cloudcc.cn/oauth/apidomain?scope=cloudccCRM&orgId="
                        + URLEncoder.encode(cloudccOrgId, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(HTTP_TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
            JsonNode root = objectMapper.readTree(resp.body());
            if (root.path("result").asBoolean(false)) {
                String gateway = root.path("orgapi_address").asText("");
                if (!gateway.isBlank()) {
                    return trimTrailingSlash(gateway);
                }
            }
        }
        throw new IllegalArgumentException("无法获取 CloudCC 组织网关地址");
    }

    public static String deriveSetupSvc(String rawGateway) {
        String gateway = ensureHttpUrl(trimTrailingSlashStatic(rawGateway));
        if (gateway.isBlank()) {
            return "";
        }
        URI uri = URI.create(gateway);
        String path = uri.getPath() == null ? "" : trimTrailingSlashStatic(uri.getPath());
        String setupPath;
        if (path.isBlank()) {
            setupPath = "/setup";
        } else {
            String[] parts = path.split("/");
            StringBuilder next = new StringBuilder();
            boolean replaced = false;
            for (String part : parts) {
                if (part == null || part.isBlank()) {
                    continue;
                }
                next.append("/");
                if ("lightningapi".equalsIgnoreCase(part)) {
                    next.append("setup");
                    replaced = true;
                } else {
                    next.append(part);
                }
            }
            setupPath = replaced ? next.toString() : path + "/setup";
        }
        try {
            return new URI(uri.getScheme(), uri.getAuthority(), setupPath, null, null).toString();
        } catch (Exception ex) {
            return trimTrailingSlashStatic(gateway) + (path.endsWith("/setup") ? "" : "/setup");
        }
    }

    private String requestToken(
            String gateway,
            String orgId,
            String username,
            String safetyMark,
            String clientId,
            String secretKey) throws Exception {
        String url = trimTrailingSlash(gateway) + "/api/cauth/token";
        String payload = objectMapper.writeValueAsString(Map.of(
                "username", username,
                "safetyMark", safetyMark,
                "clientId", clientId,
                "secretKey", secretKey,
                "orgId", orgId,
                "grant_type", "password"
        ));
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(HTTP_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new IllegalArgumentException("CloudCC 鉴权失败: HTTP " + resp.statusCode());
        }
        JsonNode root = objectMapper.readTree(resp.body());
        if (!root.path("result").asBoolean(false)) {
            throw new IllegalArgumentException("CloudCC 鉴权失败: " + root.path("returnInfo").asText("unknown"));
        }
        String token = root.path("data").path("accessToken").asText("");
        if (token.isBlank()) {
            throw new IllegalArgumentException("CloudCC 鉴权失败: 未返回 accessToken");
        }
        return token;
    }

    private Optional<Instant> parseJwtExp(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return Optional.empty();
            }
            byte[] bytes = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode payload = objectMapper.readTree(bytes);
            if (!payload.has("exp")) {
                return Optional.empty();
            }
            long expSeconds = payload.path("exp").asLong(0L);
            if (expSeconds <= 0L) {
                return Optional.empty();
            }
            return Optional.of(Instant.ofEpochSecond(expSeconds));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Map<String, Object> readConfig(String json) {
        try {
            if (json == null || json.isBlank()) {
                return Map.of();
            }
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String stringVal(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimTrailingSlash(String value) {
        return trimTrailingSlashStatic(value);
    }

    private static String trimTrailingSlashStatic(String value) {
        if (value == null) return "";
        String v = value.trim();
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }

    private boolean looksLikeDirectApiGateway(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = ensureHttpUrl(value).toLowerCase();
        if (normalized.contains("/lightningapi") || normalized.contains("/setup")) {
            return true;
        }
        try {
            URI uri = URI.create(normalized);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            String path = uri.getPath() == null ? "" : trimTrailingSlashStatic(uri.getPath());
            return path.isBlank() && host.endsWith(".apis.cloudcc.cn") && !"developer.apis.cloudcc.cn".equals(host);
        } catch (Exception ex) {
            return false;
        }
    }

    private String normalizeDirectApiGateway(String value) {
        String normalized = ensureHttpUrl(trimTrailingSlash(value));
        try {
            URI uri = URI.create(normalized);
            String path = uri.getPath() == null ? "" : trimTrailingSlashStatic(uri.getPath());
            if (path.isBlank()) {
                return trimTrailingSlash(normalized) + "/lightningapi";
            }
        } catch (Exception ignored) {
            // Fall through to the conservative setup-to-lightningapi replacement below.
        }
        return normalized.replaceFirst("(?i)/setup$", "/lightningapi");
    }

    private static String ensureHttpUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String value = raw.trim();
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "https://" + value.replaceFirst("^/+", "");
        }
        return trimTrailingSlashStatic(value);
    }

    /** {@code baseUrl} is the CloudCC org API gateway (with or without scheme); {@code setupSvc} is the setup API service root. */
    public record CloudccSessionContext(String accessToken, String baseUrl, String setupSvc) {
        public CloudccSessionContext(String accessToken, String baseUrl) {
            this(accessToken, baseUrl, deriveSetupSvc(baseUrl));
        }
    }

    private record CachedToken(String token, String baseUrl, String setupSvc, Instant expiresAt) {}
}
