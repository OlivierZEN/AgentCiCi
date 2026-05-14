package com.codehouse.ciciassistant.embed.service;

import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.embed.domain.EmbedAppDefinitionEntity;
import com.codehouse.ciciassistant.embed.domain.EmbedAppDefinitionRepository;
import com.codehouse.ciciassistant.embed.domain.MeetingSessionEntity;
import com.codehouse.ciciassistant.embed.domain.MeetingSessionRepository;
import com.codehouse.ciciassistant.embed.domain.OrgEmbedAppConfigEntity;
import com.codehouse.ciciassistant.embed.domain.OrgEmbedAppConfigRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmbedAppService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> MAP_REF = new TypeReference<>() {};

    private final EmbedAppDefinitionRepository definitionRepository;
    private final OrgEmbedAppConfigRepository configRepository;
    private final MeetingSessionRepository meetingSessionRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public EmbedAppService(EmbedAppDefinitionRepository definitionRepository,
                           OrgEmbedAppConfigRepository configRepository,
                           MeetingSessionRepository meetingSessionRepository,
                           UserRepository userRepository,
                           ObjectMapper objectMapper) {
        this.definitionRepository = definitionRepository;
        this.configRepository = configRepository;
        this.meetingSessionRepository = meetingSessionRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> listAdminApps(String orgId) {
        Map<String, OrgEmbedAppConfigEntity> configs = new LinkedHashMap<>();
        for (OrgEmbedAppConfigEntity config : configRepository.findByOrgId(orgId)) {
            configs.put(config.getAppCode(), config);
        }
        return definitionRepository.findAllByOrderByAppCodeAsc().stream()
                .map(definition -> view(definition, configs.get(definition.getAppCode())))
                .toList();
    }

    public Map<String, Object> adminDetail(String orgId, String appCode) {
        EmbedAppDefinitionEntity definition = requireDefinition(appCode);
        OrgEmbedAppConfigEntity config = configRepository.findByOrgIdAndAppCode(orgId, definition.getAppCode())
                .orElse(null);
        return view(definition, config);
    }

    public List<Map<String, Object>> recentSessions(String orgId, String appCode, int limit) {
        EmbedAppDefinitionEntity definition = requireDefinition(appCode);
        int size = Math.max(1, Math.min(limit, 50));
        return meetingSessionRepository.findByOrgIdAndAppCodeOrderByUpdatedAtDesc(orgId, definition.getAppCode(), PageRequest.of(0, size))
                .stream()
                .map(this::sessionView)
                .toList();
    }

    @Transactional
    public Map<String, Object> updateConfig(String orgId, String appCode, ConfigCommand command) {
        EmbedAppDefinitionEntity definition = requireDefinition(appCode);
        OrgEmbedAppConfigEntity existing = configRepository.findByOrgIdAndAppCode(orgId, definition.getAppCode())
                .orElse(null);
        int ttl = clampTtl(command.tokenTtlSeconds(), definition.getDefaultTokenTtlSeconds());
        String runAs = normalizeRunAsUser(orgId, command.runAsUserId());
        String allowedOriginsJson = toJson(normalizeOrigins(command.allowedOrigins()));
        String sourceBindingsJson = toJson(command.sourceBindings() == null ? Map.of() : command.sourceBindings());
        String scopeOverridesJson = toJson(normalizeScopes(command.scopeOverrides(), requiredScopes(definition)));
        if (existing == null) {
            existing = configRepository.save(new OrgEmbedAppConfigEntity(
                    orgId,
                    definition.getAppCode(),
                    command.enabled() == null || command.enabled(),
                    allowedOriginsJson,
                    runAs,
                    sourceBindingsJson,
                    scopeOverridesJson,
                    ttl));
        } else {
            existing.update(
                    command.enabled() == null || command.enabled(),
                    allowedOriginsJson,
                    runAs,
                    sourceBindingsJson,
                    scopeOverridesJson,
                    ttl);
        }
        return view(definition, existing);
    }

    @Transactional
    public OrgEmbedAppConfigEntity ensureConfig(String orgId, EmbedAppDefinitionEntity definition) {
        return configRepository.findByOrgIdAndAppCode(orgId, definition.getAppCode())
                .orElseGet(() -> configRepository.save(new OrgEmbedAppConfigEntity(
                        orgId,
                        definition.getAppCode(),
                        true,
                        "[]",
                        null,
                        "{}",
                        definition.getRequiredScopesJson(),
                        definition.getDefaultTokenTtlSeconds())));
    }

    public EmbedAppDefinitionEntity requireDefinition(String appCode) {
        String normalized = normalizeAppCode(appCode);
        return definitionRepository.findById(normalized)
                .orElseThrow(() -> new IllegalArgumentException("嵌入式智能应用不存在: " + normalized));
    }

    public List<String> requiredScopes(EmbedAppDefinitionEntity definition) {
        return readStringList(definition.getRequiredScopesJson());
    }

    public List<String> supportedSources(EmbedAppDefinitionEntity definition) {
        return readStringList(definition.getSupportedSourcesJson());
    }

    public List<String> allowedOrigins(OrgEmbedAppConfigEntity config) {
        return readStringList(config.getAllowedOriginsJson());
    }

    public List<String> scopeOverrides(OrgEmbedAppConfigEntity config) {
        return readStringList(config.getScopeOverridesJson());
    }

    public Map<String, Object> readDoc(EmbedAppDefinitionEntity definition) {
        return readMap(definition.getDocJson());
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("无法序列化嵌入式智能应用配置", ex);
        }
    }

    public boolean originAllowed(List<String> allowedOrigins, String parentOrigin) {
        String normalizedOrigin = normalizeOrigin(parentOrigin);
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            return isLocalOrigin(normalizedOrigin);
        }
        for (String allowed : allowedOrigins) {
            if (matchesOrigin(allowed, normalizedOrigin)) {
                return true;
            }
        }
        return false;
    }

    public String normalizeOrigin(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException("parentOrigin is required");
        }
        try {
            URI uri = URI.create(text);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if ((!scheme.equals("https") && !scheme.equals("http")) || host.isBlank()) {
                throw new IllegalArgumentException("parentOrigin must be an http(s) origin");
            }
            String port = uri.getPort() < 0 ? "" : ":" + uri.getPort();
            return scheme + "://" + host + port;
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("parentOrigin must be an http(s) origin");
        }
    }

    private boolean matchesOrigin(String allowed, String normalizedOrigin) {
        String normalizedAllowed = allowed == null ? "" : allowed.trim().toLowerCase(Locale.ROOT);
        if (normalizedAllowed.isBlank()) {
            return false;
        }
        if (normalizedAllowed.contains("*.")) {
            URI origin = URI.create(normalizedOrigin);
            String suffix = normalizedAllowed.substring(normalizedAllowed.indexOf("*.") + 1);
            String allowedScheme = normalizedAllowed.substring(0, normalizedAllowed.indexOf("://"));
            return origin.getScheme().equals(allowedScheme)
                    && origin.getHost() != null
                    && origin.getHost().endsWith(suffix);
        }
        return normalizeOrigin(normalizedAllowed).equals(normalizedOrigin);
    }

    private boolean isLocalOrigin(String origin) {
        return origin.startsWith("http://localhost:")
                || origin.startsWith("http://127.0.0.1:")
                || origin.equals("http://localhost")
                || origin.equals("http://127.0.0.1");
    }

    private Map<String, Object> view(EmbedAppDefinitionEntity definition, OrgEmbedAppConfigEntity config) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("appCode", definition.getAppCode());
        data.put("name", definition.getName());
        data.put("description", definition.getDescription());
        data.put("platformStatus", definition.getStatus());
        data.put("status", config == null ? "UNCONFIGURED" : (config.isEnabled() ? "ENABLED" : "DISABLED"));
        data.put("embedMode", definition.getEmbedMode());
        data.put("stableSdkUrl", definition.getStableSdkUrl());
        data.put("versionedSdkUrl", definition.getVersionedSdkUrl());
        data.put("embedUrl", definition.getEmbedUrl());
        data.put("requiredScopes", requiredScopes(definition));
        data.put("supportedSources", supportedSources(definition));
        data.put("version", definition.getVersion());
        data.put("defaultTokenTtlSeconds", definition.getDefaultTokenTtlSeconds());
        data.put("doc", readDoc(definition));
        data.put("config", configView(config));
        return data;
    }

    private Map<String, Object> configView(OrgEmbedAppConfigEntity config) {
        if (config == null) {
            return Map.of(
                    "enabled", true,
                    "allowedOrigins", List.of(),
                    "runAsUserId", "",
                    "sourceBindings", Map.of(),
                    "scopeOverrides", List.of(),
                    "tokenTtlSeconds", 900
            );
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", config.isEnabled());
        data.put("allowedOrigins", allowedOrigins(config));
        data.put("runAsUserId", config.getRunAsUserId() == null ? "" : config.getRunAsUserId());
        data.put("sourceBindings", readMap(config.getSourceBindingsJson()));
        data.put("scopeOverrides", scopeOverrides(config));
        data.put("tokenTtlSeconds", config.getTokenTtlSeconds());
        data.put("updatedAt", config.getUpdatedAt() == null ? "" : config.getUpdatedAt().toString());
        return data;
    }

    private Map<String, Object> sessionView(MeetingSessionEntity session) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", session.getId());
        data.put("status", session.getStatus());
        data.put("source", session.getSource());
        data.put("objectType", session.getObjectType());
        data.put("objectId", session.getObjectId());
        data.put("recordName", session.getRecordName() == null ? "" : session.getRecordName());
        data.put("customerName", session.getCustomerName() == null ? "" : session.getCustomerName());
        data.put("parentOrigin", session.getParentOrigin());
        data.put("traceId", session.getTraceId() == null ? "" : session.getTraceId());
        data.put("externalUserId", session.getExternalUserId() == null ? "" : session.getExternalUserId());
        data.put("createdAt", session.getCreatedAt() == null ? "" : session.getCreatedAt().toString());
        data.put("updatedAt", session.getUpdatedAt() == null ? "" : session.getUpdatedAt().toString());
        return data;
    }

    private String normalizeAppCode(String raw) {
        String text = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (!text.matches("^[a-z0-9][a-z0-9-]{1,63}$")) {
            throw new IllegalArgumentException("appCode must match ^[a-z0-9][a-z0-9-]{1,63}$");
        }
        return text;
    }

    private String normalizeRunAsUser(String orgId, String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isBlank()) {
            return null;
        }
        userRepository.findByIdAndOrg_Id(text, orgId)
                .orElseThrow(() -> new IllegalArgumentException("runAsUserId must belong to the current org"));
        return text;
    }

    private int clampTtl(Integer requested, int fallback) {
        int ttl = requested == null ? fallback : requested;
        if (ttl < 60 || ttl > 1800) {
            throw new IllegalArgumentException("tokenTtlSeconds must be between 60 and 1800");
        }
        return ttl;
    }

    private List<String> normalizeScopes(List<String> raw, List<String> supported) {
        if (raw == null || raw.isEmpty()) {
            return supported;
        }
        List<String> out = new ArrayList<>();
        for (String value : raw) {
            String text = value == null ? "" : value.trim();
            if (text.isBlank() || out.contains(text)) {
                continue;
            }
            if (!supported.contains(text)) {
                throw new IllegalArgumentException("Unsupported embed scope: " + text);
            }
            out.add(text);
        }
        return out;
    }

    private List<String> normalizeOrigins(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String value : raw) {
            String text = value == null ? "" : value.trim();
            if (text.isBlank()) {
                continue;
            }
            String normalized = text.contains("*.") ? text.toLowerCase(Locale.ROOT) : normalizeOrigin(text);
            if (!out.contains(normalized)) {
                out.add(normalized);
            }
        }
        return List.copyOf(out);
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_REF);
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    public record ConfigCommand(
            Boolean enabled,
            List<String> allowedOrigins,
            String runAsUserId,
            Map<String, Object> sourceBindings,
            List<String> scopeOverrides,
            Integer tokenTtlSeconds
    ) {
    }
}
