package com.codehouse.ciciassistant.embed.service;

import com.codehouse.ciciassistant.agent.domain.AgentChannelBindingRepository;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionRepository;
import com.codehouse.ciciassistant.agent.domain.AgentPermission;
import com.codehouse.ciciassistant.agent.domain.AgentPublishConfigEntity;
import com.codehouse.ciciassistant.agent.domain.AgentPublishConfigRepository;
import com.codehouse.ciciassistant.agent.service.AgentAccessControlService;
import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.auth.service.JwtService;
import com.codehouse.ciciassistant.embed.domain.EmbedAppDefinitionEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PublicWebWidgetService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final List<String> CHAT_PERMISSIONS = List.of("chat:read", "chat:write");
    private static final String CHANNEL_WEB = "web";

    private final AgentPublishConfigRepository publishConfigs;
    private final AgentDefinitionRepository agents;
    private final AgentChannelBindingRepository channelBindings;
    private final UserRepository users;
    private final AgentAccessControlService accessControl;
    private final JwtService jwtService;
    private final EmbedAppService embedApps;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redis;

    public PublicWebWidgetService(AgentPublishConfigRepository publishConfigs,
                                  AgentDefinitionRepository agents,
                                  AgentChannelBindingRepository channelBindings,
                                  UserRepository users,
                                  AgentAccessControlService accessControl,
                                  JwtService jwtService,
                                  EmbedAppService embedApps,
                                  ObjectMapper objectMapper,
                                  ObjectProvider<StringRedisTemplate> redisProvider) {
        this.publishConfigs = publishConfigs;
        this.agents = agents;
        this.channelBindings = channelBindings;
        this.users = users;
        this.accessControl = accessControl;
        this.jwtService = jwtService;
        this.embedApps = embedApps;
        this.objectMapper = objectMapper;
        this.redis = redisProvider.getIfAvailable();
    }

    public Map<String, Object> publicConfig(String widgetKey) {
        ResolvedWidget widget = resolve(widgetKey);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("widgetKey", widget.config().widgetKey());
        data.put("assistantName", widget.config().assistantName());
        data.put("launcherLabel", widget.config().launcherLabel());
        data.put("welcomeMessage", widget.config().welcomeMessage());
        data.put("defaultOpen", widget.config().defaultOpen());
        data.put("tokenTtlSeconds", widget.config().tokenTtlSeconds());
        data.put("sdkUrl", "/sdk/sisi@1.1.0.js");
        data.put("embedUrl", "/embed/sisi");
        return data;
    }

    public boolean originAllowedForRequest(String widgetKey, String origin) {
        try {
            ResolvedWidget widget = resolve(widgetKey);
            return embedApps.originAllowed(widget.config().allowedOrigins(), embedApps.normalizeOrigin(origin));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public TokenView issueToken(String widgetKey, TokenCommand command, HttpServletRequest request) {
        ResolvedWidget widget = resolve(widgetKey);
        String requestOrigin = embedApps.normalizeOrigin(header(request, "Origin"));
        String parentOrigin = embedApps.normalizeOrigin(command == null ? null : command.parentOrigin());
        if (!requestOrigin.equals(parentOrigin)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Widget request origin does not match parentOrigin");
        }
        if (!embedApps.originAllowed(widget.config().allowedOrigins(), parentOrigin)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Widget origin is not allowed");
        }

        String visitorId = requireVisitorId(command == null ? null : command.visitorId());
        reserve(widget, request);
        String pagePath = normalizePagePath(command == null ? null : command.pagePath());
        String locale = clip(command == null ? null : command.locale(), 16);
        String nonce = "web_" + UUID.randomUUID().toString().replace("-", "");
        Instant expiresAt = Instant.now().plusSeconds(widget.config().tokenTtlSeconds());
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("widgetKey", widget.config().widgetKey());
        context.put("pagePath", pagePath);
        context.put("locale", locale);
        context.put("assistantName", widget.config().assistantName());
        context.put("welcomeMessage", widget.config().welcomeMessage());
        context.put("launcherLabel", widget.config().launcherLabel());

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("typ", "embed_app");
        claims.put("aud", "agentcici-embed");
        claims.put("appCode", "sisi");
        claims.put("company_id", widget.agent().getCompanyId());
        claims.put("companyId", widget.agent().getCompanyId());
        claims.put("member_id", widget.runAs().getId());
        claims.put("userId", widget.runAs().getId());
        claims.put("issuerRunAsUserId", widget.runAs().getId());
        claims.put("roles", List.of(widget.runAs().getRoleCode()));
        claims.put("agentId", widget.agent().getAgentId());
        claims.put("externalTenantId", "website:" + widget.config().widgetKey());
        claims.put("source", "website");
        claims.put("objectType", "WebsitePage");
        claims.put("objectId", pagePath);
        claims.put("recordName", widget.config().launcherLabel());
        claims.put("customerName", "");
        claims.put("parentOrigin", parentOrigin);
        claims.put("permissions", CHAT_PERMISSIONS);
        claims.put("externalUserId", visitorId);
        claims.put("externalUserDisplayName", "Website visitor");
        claims.put("nonce", nonce);
        claims.put("context", context);
        String token = jwtService.issueToken("embed:sisi:" + nonce, claims, widget.config().tokenTtlSeconds());
        return new TokenView(token, expiresAt, "/embed/sisi", CHAT_PERMISSIONS, widget.config().tokenTtlSeconds());
    }

    private ResolvedWidget resolve(String rawWidgetKey) {
        EmbedAppDefinitionEntity definition;
        try {
            definition = embedApps.requireDefinition("sisi");
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Web widget is not available");
        }
        if (!EmbedAppDefinitionEntity.STATUS_ENABLED.equals(definition.getStatus())
                || !embedApps.supportedSources(definition).contains("website")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Web widget is not available");
        }
        String widgetKey = normalizeWidgetKey(rawWidgetKey);
        List<ResolvedWidget> matches = publishConfigs.findByChannelIdOrderByUpdatedAtDesc(CHANNEL_WEB).stream()
                .map(entity -> resolveCandidate(entity, widgetKey))
                .filter(candidate -> candidate != null)
                .toList();
        if (matches.size() != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Web widget is not available");
        }
        return matches.get(0);
    }

    private ResolvedWidget resolveCandidate(AgentPublishConfigEntity entity, String widgetKey) {
        WidgetConfig config = parse(entity.getConfigJson());
        if (!config.enabled() || !widgetKey.equals(config.widgetKey())) {
            return null;
        }
        AgentDefinitionEntity agent = agents.findByCompanyIdAndAgentIdAndEnabledTrue(entity.getCompanyId(), entity.getAgentId())
                .orElse(null);
        if (agent == null || agent.getPublishedVersionId() == null
                || !channelBindings.existsByCompanyIdAndAgentIdAndChannelIdAndEnabledTrue(
                        entity.getCompanyId(), entity.getAgentId(), CHANNEL_WEB)) {
            return null;
        }
        UserEntity runAs = users.findByIdAndCompany_Id(config.runAsUserId(), entity.getCompanyId())
                .filter(user -> UserEntity.STATUS_ACTIVE.equals(user.getMemberStatus()))
                .orElse(null);
        if (runAs == null || !accessControl.can(entity.getCompanyId(), runAs.getId(), List.of(runAs.getRoleCode()),
                entity.getAgentId(), AgentPermission.RUN)) {
            return null;
        }
        return new ResolvedWidget(agent, runAs, config);
    }

    private WidgetConfig parse(String rawJson) {
        try {
            Map<String, Object> raw = objectMapper.readValue(rawJson == null ? "{}" : rawJson, MAP_TYPE);
            String key = normalizeWidgetKey(text(raw.get("widgetKey")));
            List<String> origins = stringList(raw.get("allowedOrigins")).stream()
                    .map(embedApps::normalizeOrigin)
                    .distinct()
                    .toList();
            if (origins.isEmpty()) {
                return WidgetConfig.disabled();
            }
            return new WidgetConfig(
                    bool(raw.get("enabled"), true),
                    key,
                    origins,
                    requireText(text(raw.get("runAsUserId")), "runAsUserId"),
                    fallback(text(raw.get("assistantName")), "AgentCiCi"),
                    fallback(text(raw.get("launcherLabel")), "咨询智能体"),
                    fallback(text(raw.get("welcomeMessage")), "你好，请告诉我你想了解的业务场景。"),
                    bool(raw.get("defaultOpen"), false),
                    clamp(integer(raw.get("tokenTtlSeconds"), 600), 60, 900),
                    clamp(integer(raw.get("rateLimitPerMinute"), 20), 1, 120));
        } catch (RuntimeException exception) {
            return WidgetConfig.disabled();
        } catch (Exception exception) {
            return WidgetConfig.disabled();
        }
    }

    private void reserve(ResolvedWidget widget, HttpServletRequest request) {
        if (redis == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Web widget rate limiter is unavailable");
        }
        long minute = Instant.now().getEpochSecond() / 60;
        String key = "cici:web-widget:" + digest(widget.config().widgetKey()) + ":" + digest(clientIp(request)) + ":" + minute;
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, Duration.ofSeconds(90));
            }
            if (count == null || count > widget.config().rateLimitPerMinute()) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Web widget request limit exceeded");
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Web widget rate limiter is unavailable");
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = header(request, "X-Forwarded-For");
        if (!forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return clip(comma >= 0 ? forwarded.substring(0, comma) : forwarded, 128);
        }
        String real = header(request, "X-Real-IP");
        return real.isBlank() ? clip(request.getRemoteAddr(), 128) : clip(real, 128);
    }

    private String normalizeWidgetKey(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (!value.matches("^ww_[a-z0-9]{24,64}$")) {
            throw new IllegalArgumentException("widgetKey is invalid");
        }
        return value;
    }

    private String requireVisitorId(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (!value.matches("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "visitorId must be a UUID v4");
        }
        return value;
    }

    private String normalizePagePath(String raw) {
        String value = raw == null ? "/" : raw.trim();
        if (!value.startsWith("/") || value.startsWith("//")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pagePath must be a same-site path");
        }
        return clip(value, 160);
    }

    private List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        return list.stream().map(this::text).filter(value -> !value.isBlank()).toList();
    }

    private String header(HttpServletRequest request, String name) {
        String value = request == null ? null : request.getHeader(name);
        return value == null ? "" : value.trim();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : clip(value, 160);
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    private boolean bool(Object value, boolean fallback) {
        return value instanceof Boolean bool ? bool : fallback;
    }

    private int integer(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(text(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String clip(String value, int max) {
        String text = value == null ? "" : value.trim();
        return text.length() <= max ? text : text.substring(0, max);
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8))).substring(0, 24);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash web widget rate key", exception);
        }
    }

    public record TokenCommand(String visitorId, String parentOrigin, String pagePath, String locale) { }

    public record TokenView(String embedToken,
                            Instant expiresAt,
                            String embedUrl,
                            List<String> permissions,
                            int ttlSeconds) { }

    private record ResolvedWidget(AgentDefinitionEntity agent, UserEntity runAs, WidgetConfig config) { }

    private record WidgetConfig(boolean enabled,
                                String widgetKey,
                                List<String> allowedOrigins,
                                String runAsUserId,
                                String assistantName,
                                String launcherLabel,
                                String welcomeMessage,
                                boolean defaultOpen,
                                int tokenTtlSeconds,
                                int rateLimitPerMinute) {
        private static WidgetConfig disabled() {
            return new WidgetConfig(false, "", List.of(), "", "", "", "", false, 600, 20);
        }
    }
}
