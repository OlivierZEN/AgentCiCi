package com.codehouse.ciciassistant.openapi.service;

import com.codehouse.ciciassistant.openapi.domain.AgentApiCredentialEntity;
import com.codehouse.ciciassistant.openapi.domain.AgentApiCredentialRepository;
import com.codehouse.ciciassistant.openapi.domain.AgentApiMemoryBindingEntity;
import com.codehouse.ciciassistant.openapi.domain.AgentApiMemoryBindingRepository;
import com.codehouse.ciciassistant.ops.service.AuditService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manages the server-side trust binding for a generic external memory integration. */
@Service
public class AgentApiMemoryBindingService {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final List<String> SUBJECT_TYPES = List.of("EXTERNAL_USER", "EXTERNAL_PRINCIPAL");
    private static final List<String> IDENTITY_LEVELS = List.of("ANONYMOUS", "VERIFIED");

    private final AgentApiCredentialRepository credentials;
    private final AgentApiMemoryBindingRepository bindings;
    private final ObjectMapper json;
    private final AuditService audit;

    public AgentApiMemoryBindingService(AgentApiCredentialRepository credentials,
                                        AgentApiMemoryBindingRepository bindings,
                                        ObjectMapper json,
                                        AuditService audit) {
        this.credentials = credentials;
        this.bindings = bindings;
        this.json = json;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public BindingView get(String companyId, String agentId, Long credentialId) {
        requireCredential(companyId, agentId, credentialId);
        return bindings.findByCredentialId(credentialId).map(this::toView).orElse(null);
    }

    @Transactional
    public BindingView upsert(String companyId, String agentId, Long credentialId, String actorUserId, BindingCommand command) {
        requireCredential(companyId, agentId, credentialId);
        String applicationCode = normalizeApplicationCode(command.applicationCode());
        String subjectType = normalizeEnum(command.subjectType(), SUBJECT_TYPES, "subjectType");
        String identityLevel = normalizeEnum(command.identityLevel(), IDENTITY_LEVELS, "identityLevel");
        String namespaces = toJson(normalizeNamespaces(command.domainNamespaces()));
        AgentApiMemoryBindingEntity binding = bindings.findByCredentialId(credentialId)
                .map(existing -> { existing.update(applicationCode, subjectType, identityLevel, namespaces); return existing; })
                .orElseGet(() -> new AgentApiMemoryBindingEntity(credentialId, applicationCode, subjectType, identityLevel, namespaces));
        BindingView view = toView(bindings.save(binding));
        audit.log(companyId, actorUserId, "agent.api_memory_binding.upsert",
                "agent=" + agentId + ",credentialId=" + credentialId + ",application=" + applicationCode + ",enabled=true");
        return view;
    }

    @Transactional
    public BindingView disable(String companyId, String agentId, Long credentialId, String actorUserId) {
        requireCredential(companyId, agentId, credentialId);
        AgentApiMemoryBindingEntity binding = bindings.findByCredentialId(credentialId)
                .orElseThrow(() -> new IllegalArgumentException("Memory binding not found"));
        binding.disable();
        BindingView view = toView(bindings.save(binding));
        audit.log(companyId, actorUserId, "agent.api_memory_binding.disable",
                "agent=" + agentId + ",credentialId=" + credentialId + ",enabled=false");
        return view;
    }

    private AgentApiCredentialEntity requireCredential(String companyId, String agentId, Long credentialId) {
        if (credentialId == null || credentialId <= 0) throw new IllegalArgumentException("credentialId is required");
        return credentials.findByIdAndCompanyIdAndAgentId(credentialId, companyId, agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent API key not found"));
    }

    private String normalizeApplicationCode(String raw) {
        String value = requireText(raw, "applicationCode").toLowerCase(Locale.ROOT);
        if (!value.matches("^[a-z0-9][a-z0-9-]{1,95}$")) throw new IllegalArgumentException("applicationCode is invalid");
        return value;
    }

    private String normalizeEnum(String raw, List<String> allowed, String field) {
        String value = requireText(raw, field).toUpperCase(Locale.ROOT);
        if (!allowed.contains(value)) throw new IllegalArgumentException("invalid " + field);
        return value;
    }

    private List<String> normalizeNamespaces(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        List<String> normalized = new ArrayList<>();
        for (String raw : values) {
            String value = requireText(raw, "domainNamespace").toLowerCase(Locale.ROOT);
            if (!value.matches("^[a-z0-9][a-z0-9._-]{0,95}$")) throw new IllegalArgumentException("domainNamespace is invalid");
            if (!normalized.contains(value)) normalized.add(value);
            if (normalized.size() > 32) throw new IllegalArgumentException("too many domain namespaces");
        }
        return List.copyOf(normalized);
    }

    private BindingView toView(AgentApiMemoryBindingEntity binding) {
        return new BindingView(binding.getId(), binding.getCredentialId(), binding.getApplicationCode(),
                binding.getSubjectType(), binding.getIdentityLevel(), readNamespaces(binding.getDomainNamespacesJson()),
                binding.isEnabled(), binding.getCreatedAt(), binding.getUpdatedAt());
    }

    private List<String> readNamespaces(String raw) {
        try { return raw == null || raw.isBlank() ? List.of() : List.copyOf(json.readValue(raw, STRING_LIST)); }
        catch (JsonProcessingException ex) { return List.of(); }
    }

    private String toJson(List<String> value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("Failed to serialize namespaces", ex); }
    }

    private String requireText(String raw, String field) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    public record BindingCommand(String applicationCode, String subjectType, String identityLevel,
                                 List<String> domainNamespaces) {}
    public record BindingView(Long id, Long credentialId, String applicationCode, String subjectType,
                              String identityLevel, List<String> domainNamespaces, boolean enabled,
                              Instant createdAt, Instant updatedAt) {}
}
