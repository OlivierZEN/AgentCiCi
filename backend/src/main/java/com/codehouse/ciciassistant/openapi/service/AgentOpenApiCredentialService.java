package com.codehouse.ciciassistant.openapi.service;

import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionRepository;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.openapi.config.AgentOpenApiProperties;
import com.codehouse.ciciassistant.openapi.domain.AgentApiCredentialEntity;
import com.codehouse.ciciassistant.openapi.domain.AgentApiCredentialRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentOpenApiCredentialService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final AgentApiCredentialRepository credentialRepository;
    private final AgentDefinitionRepository agentDefinitionRepository;
    private final UserRepository userRepository;
    private final AgentOpenApiProperties properties;
    private final AgentApiKeyGenerator keyGenerator;
    private final ObjectMapper objectMapper;

    public AgentOpenApiCredentialService(AgentApiCredentialRepository credentialRepository,
                                         AgentDefinitionRepository agentDefinitionRepository,
                                         UserRepository userRepository,
                                         AgentOpenApiProperties properties,
                                         AgentApiKeyGenerator keyGenerator,
                                         ObjectMapper objectMapper) {
        this.credentialRepository = credentialRepository;
        this.agentDefinitionRepository = agentDefinitionRepository;
        this.userRepository = userRepository;
        this.properties = properties;
        this.keyGenerator = keyGenerator;
        this.objectMapper = objectMapper;
    }

    public List<CredentialView> list(String orgId, String agentId) {
        requireAgent(orgId, agentId);
        return credentialRepository.findByOrgIdAndAgentIdOrderByCreatedAtDesc(orgId, agentId)
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public CredentialCreation create(String orgId, String agentId, String actorUserId, CreateCredentialCommand command) {
        requireAgent(orgId, agentId);
        String runAsUserId = requireRunAsUser(orgId, command.runAsUserId());
        String keyType = normalizeKeyType(command.keyType());
        AgentApiKeyGenerator.GeneratedKey generated = generateUniqueKey();
        AgentApiCredentialEntity entity = credentialRepository.save(new AgentApiCredentialEntity(
                generated.publicId(),
                orgId,
                agentId,
                requireText(command.name(), "name"),
                generated.keyPrefix(),
                generated.keyHash(),
                keyType,
                runAsUserId,
                toJson(normalizeList(command.allowedIps())),
                toJson(normalizeScopes(command.scopes())),
                positiveOrDefault(command.rateLimitPerMinute(), properties.getDefaultRateLimitPerMinute(), 1, 10000),
                positiveOrDefault(command.dailyQuota(), properties.getDefaultDailyQuota(), 1, 10000000),
                positiveOrDefault(command.maxPromptChars(), properties.getDefaultMaxPromptChars(), 1, 64000),
                positiveOrDefault(command.maxResponseChars(), properties.getDefaultMaxResponseChars(), 1, 128000),
                command.allowStream() == null || command.allowStream(),
                command.allowTraceRead() != null && command.allowTraceRead(),
                command.expiresAt(),
                actorUserId
        ));
        return new CredentialCreation(toView(entity), generated.plainKey());
    }

    @Transactional
    public CredentialView update(String orgId, String agentId, Long credentialId, UpdateCredentialCommand command) {
        AgentApiCredentialEntity entity = requireCredential(orgId, agentId, credentialId);
        String runAsUserId = command.runAsUserId() == null || command.runAsUserId().isBlank()
                ? entity.getRunAsUserId()
                : requireRunAsUser(orgId, command.runAsUserId());
        String status = normalizeStatus(command.status(), entity.getStatus());
        if (AgentApiCredentialEntity.STATUS_REVOKED.equals(entity.getStatus())
                && !AgentApiCredentialEntity.STATUS_REVOKED.equals(status)) {
            throw new IllegalArgumentException("Revoked API keys cannot be reactivated; rotate or create a new key.");
        }
        entity.updateMutableFields(
                command.name() == null || command.name().isBlank() ? entity.getName() : requireText(command.name(), "name"),
                runAsUserId,
                command.allowedIps() == null ? entity.getAllowedIpsJson() : toJson(normalizeList(command.allowedIps())),
                command.scopes() == null ? entity.getScopesJson() : toJson(normalizeScopes(command.scopes())),
                positiveOrDefault(command.rateLimitPerMinute(), entity.getRateLimitPerMinute(), 1, 10000),
                positiveOrDefault(command.dailyQuota(), entity.getDailyQuota(), 1, 10000000),
                positiveOrDefault(command.maxPromptChars(), entity.getMaxPromptChars(), 1, 64000),
                positiveOrDefault(command.maxResponseChars(), entity.getMaxResponseChars(), 1, 128000),
                command.allowStream() == null ? entity.isAllowStream() : command.allowStream(),
                command.allowTraceRead() == null ? entity.isAllowTraceRead() : command.allowTraceRead(),
                command.expiresAt() == null ? entity.getExpiresAt() : command.expiresAt(),
                status);
        return toView(entity);
    }

    @Transactional
    public CredentialCreation rotate(String orgId, String agentId, Long credentialId) {
        AgentApiCredentialEntity entity = requireCredential(orgId, agentId, credentialId);
        AgentApiKeyGenerator.GeneratedKey generated = generateUniqueKey();
        entity.rotate(generated.publicId(), generated.keyPrefix(), generated.keyHash());
        return new CredentialCreation(toView(entity), generated.plainKey());
    }

    @Transactional
    public CredentialView revoke(String orgId, String agentId, Long credentialId, String actorUserId) {
        AgentApiCredentialEntity entity = requireCredential(orgId, agentId, credentialId);
        entity.revoke(actorUserId);
        return toView(entity);
    }

    public CredentialView toView(AgentApiCredentialEntity entity) {
        return new CredentialView(
                entity.getId(),
                entity.getPublicId(),
                entity.getName(),
                entity.getKeyPrefix(),
                entity.getKeyType(),
                entity.getStatus(),
                entity.getRunAsUserId(),
                readStringList(entity.getAllowedIpsJson()),
                readStringList(entity.getScopesJson()),
                entity.getRateLimitPerMinute(),
                entity.getDailyQuota(),
                entity.getMaxPromptChars(),
                entity.getMaxResponseChars(),
                entity.isAllowStream(),
                entity.isAllowTraceRead(),
                entity.getExpiresAt(),
                entity.getLastUsedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getRevokedAt()
        );
    }

    private AgentApiCredentialEntity requireCredential(String orgId, String agentId, Long credentialId) {
        if (credentialId == null || credentialId <= 0) {
            throw new IllegalArgumentException("credentialId is required");
        }
        requireAgent(orgId, agentId);
        return credentialRepository.findByIdAndOrgIdAndAgentId(credentialId, orgId, agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent API key not found"));
    }

    private AgentDefinitionEntity requireAgent(String orgId, String agentId) {
        String normalized = normalizeAgentId(agentId);
        return agentDefinitionRepository.findByOrgIdAndAgentId(orgId, normalized)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + normalized));
    }

    private String requireRunAsUser(String orgId, String runAsUserId) {
        String normalized = requireText(runAsUserId, "runAsUserId");
        userRepository.findByIdAndOrg_Id(normalized, orgId)
                .orElseThrow(() -> new IllegalArgumentException("runAsUserId must belong to the current org"));
        return normalized;
    }

    private AgentApiKeyGenerator.GeneratedKey generateUniqueKey() {
        for (int i = 0; i < 8; i++) {
            String publicId = keyGenerator.newPublicId();
            if (!credentialRepository.existsByPublicId(publicId)) {
                return keyGenerator.generate(publicId);
            }
        }
        throw new IllegalStateException("Failed to generate unique Agent Open API key public id");
    }

    private String normalizeAgentId(String raw) {
        String text = requireText(raw, "agentId").toLowerCase(Locale.ROOT);
        if (!text.matches("^[a-z0-9][a-z0-9-]{1,63}$")) {
            throw new IllegalArgumentException("agentId must match ^[a-z0-9][a-z0-9-]{1,63}$");
        }
        return text;
    }

    private String normalizeStatus(String status, String fallback) {
        if (status == null || status.isBlank()) {
            return fallback;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (AgentApiCredentialEntity.STATUS_ACTIVE.equals(normalized)
                || AgentApiCredentialEntity.STATUS_PAUSED.equals(normalized)
                || AgentApiCredentialEntity.STATUS_REVOKED.equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("Unsupported API key status: " + status);
    }

    private String normalizeKeyType(String keyType) {
        if (keyType == null || keyType.isBlank()) {
            return AgentApiCredentialEntity.KEY_TYPE_STANDARD;
        }
        String normalized = keyType.trim().toLowerCase(Locale.ROOT);
        if (AgentApiCredentialEntity.KEY_TYPE_STANDARD.equals(normalized)
                || AgentApiCredentialEntity.KEY_TYPE_CLOUDCC.equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("Unsupported API key type: " + keyType);
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String text = value == null ? "" : value.trim();
            if (text.isBlank() || result.contains(text)) {
                continue;
            }
            result.add(text);
        }
        return List.copyOf(result);
    }

    private List<String> normalizeScopes(List<String> values) {
        List<String> normalized = normalizeList(values);
        if (normalized.isEmpty()) {
            return List.of("chat", "files", "feedback", "history");
        }
        List<String> allowed = List.of("chat", "files", "feedback", "history", "audio", "*");
        for (String scope : normalized) {
            if (!allowed.contains(scope)) {
                throw new IllegalArgumentException("Unsupported API key scope: " + scope);
            }
        }
        return normalized;
    }

    private int positiveOrDefault(Integer value, int fallback, int min, int max) {
        int resolved = value == null ? fallback : value;
        if (resolved < min || resolved > max) {
            throw new IllegalArgumentException("numeric limit out of range");
        }
        return resolved;
    }

    private String requireText(String value, String fieldName) {
        String text = value == null ? "" : value.trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return text;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize Agent API key settings", ex);
        }
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

    public Map<String, Object> toCreationPayload(CredentialCreation created) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("credential", created.credential());
        payload.put("plainKey", created.plainKey());
        return payload;
    }

    public record CreateCredentialCommand(
            String name,
            String runAsUserId,
            Instant expiresAt,
            List<String> allowedIps,
            Integer rateLimitPerMinute,
            Integer dailyQuota,
            Integer maxPromptChars,
            Integer maxResponseChars,
            Boolean allowStream,
            Boolean allowTraceRead,
            List<String> scopes,
            String keyType
    ) {
    }

    public record UpdateCredentialCommand(
            String name,
            String runAsUserId,
            Instant expiresAt,
            List<String> allowedIps,
            Integer rateLimitPerMinute,
            Integer dailyQuota,
            Integer maxPromptChars,
            Integer maxResponseChars,
            Boolean allowStream,
            Boolean allowTraceRead,
            String status,
            List<String> scopes
    ) {
    }

    public record CredentialCreation(CredentialView credential, String plainKey) {
    }

    public record CredentialView(
            Long id,
            String publicId,
            String name,
            String keyPrefix,
            String keyType,
            String status,
            String runAsUserId,
            List<String> allowedIps,
            List<String> scopes,
            int rateLimitPerMinute,
            int dailyQuota,
            int maxPromptChars,
            int maxResponseChars,
            boolean allowStream,
            boolean allowTraceRead,
            Instant expiresAt,
            Instant lastUsedAt,
            Instant createdAt,
            Instant updatedAt,
            Instant revokedAt
    ) {
    }
}
