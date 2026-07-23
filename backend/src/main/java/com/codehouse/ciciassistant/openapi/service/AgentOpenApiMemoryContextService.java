package com.codehouse.ciciassistant.openapi.service;

import com.codehouse.ciciassistant.memory.service.ExternalMemoryContextService;
import com.codehouse.ciciassistant.memory.service.TrustedMemoryRuntimeContextService;
import com.codehouse.ciciassistant.openapi.domain.AgentApiMemoryBindingRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class AgentOpenApiMemoryContextService {
    private final AgentApiMemoryBindingRepository bindings; private final TrustedMemoryRuntimeContextService runtime; private final ObjectMapper json;
    public AgentOpenApiMemoryContextService(AgentApiMemoryBindingRepository bindings, TrustedMemoryRuntimeContextService runtime, ObjectMapper json) { this.bindings=bindings; this.runtime=runtime; this.json=json; }
    public <T> T withTrustedContext(AgentOpenApiAuthService.AuthenticatedCredential auth, String externalSubjectRef, Supplier<T> action) {
        if (externalSubjectRef == null || externalSubjectRef.isBlank()) return action.get();
        return bindings.findByCredentialIdAndEnabledTrue(auth.credential().getId()).map(binding -> {
            Set<String> namespaces=parseNamespaces(binding.getDomainNamespacesJson());
            var context=new ExternalMemoryContextService.ExternalMemoryContext(auth.credential().getOrgId(), binding.getApplicationCode(), "openapi-" + externalSubjectRef.trim(), externalSubjectRef.trim(), binding.getSubjectType(), binding.getIdentityLevel());
            try (var ignored=runtime.enter(new TrustedMemoryRuntimeContextService.TrustedMemoryRequest(context, auth.credential().getAgentId(), namespaces))) { return action.get(); }
        }).orElseGet(action);
    }
    private Set<String> parseNamespaces(String raw) { try { return Set.copyOf(json.readValue(raw, new TypeReference<List<String>>(){})); } catch (Exception ignored) { return Set.of(); } }
}
