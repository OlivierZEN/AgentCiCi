package com.codehouse.ciciassistant.openapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.openapi.domain.AgentApiCredentialEntity;
import com.codehouse.ciciassistant.openapi.domain.AgentApiCredentialRepository;
import com.codehouse.ciciassistant.openapi.domain.AgentApiMemoryBindingEntity;
import com.codehouse.ciciassistant.openapi.domain.AgentApiMemoryBindingRepository;
import com.codehouse.ciciassistant.ops.service.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AgentApiMemoryBindingServiceTest {
    @Test void createsValidatedBindingForTheCredentialInsideTheCurrentAgent() {
        AgentApiCredentialRepository credentials=mock(AgentApiCredentialRepository.class);
        AgentApiMemoryBindingRepository bindings=mock(AgentApiMemoryBindingRepository.class);
        AuditService audit=mock(AuditService.class);
        AgentApiCredentialEntity credential=mock(AgentApiCredentialEntity.class);
        when(credentials.findByIdAndCompanyIdAndAgentId(5L, "org-a", "agent-a")).thenReturn(Optional.of(credential));
        when(bindings.findByCredentialId(5L)).thenReturn(Optional.empty());
        when(bindings.save(any(AgentApiMemoryBindingEntity.class))).thenAnswer(call -> call.getArgument(0));
        AgentApiMemoryBindingService service=new AgentApiMemoryBindingService(credentials, bindings, new ObjectMapper(), audit);

        var result=service.upsert("org-a", "agent-a", 5L, "actor-a",
                new AgentApiMemoryBindingService.BindingCommand("Gateway-Alpha", "external_user", "verified", List.of("sales", "sales")));

        assertThat(result.applicationCode()).isEqualTo("gateway-alpha");
        assertThat(result.subjectType()).isEqualTo("EXTERNAL_USER");
        assertThat(result.identityLevel()).isEqualTo("VERIFIED");
        assertThat(result.domainNamespaces()).containsExactly("sales");
        assertThat(result.enabled()).isTrue();
        verify(audit).log("org-a", "actor-a", "agent.api_memory_binding.upsert", "agent=agent-a,credentialId=5,application=gateway-alpha,enabled=true");
    }

    @Test void rejectsInvalidValuesAndCrossAgentCredentials() {
        AgentApiCredentialRepository credentials=mock(AgentApiCredentialRepository.class);
        AgentApiMemoryBindingRepository bindings=mock(AgentApiMemoryBindingRepository.class);
        AgentApiMemoryBindingService service=new AgentApiMemoryBindingService(credentials, bindings, new ObjectMapper(), mock(AuditService.class));

        assertThatThrownBy(() -> service.upsert("org-a", "agent-a", 5L, "actor-a",
                new AgentApiMemoryBindingService.BindingCommand("bad/app", "EXTERNAL_USER", "VERIFIED", List.of())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Agent API key not found");
        when(credentials.findByIdAndCompanyIdAndAgentId(5L, "org-a", "agent-a")).thenReturn(Optional.of(mock(AgentApiCredentialEntity.class)));
        assertThatThrownBy(() -> service.upsert("org-a", "agent-a", 5L, "actor-a",
                new AgentApiMemoryBindingService.BindingCommand("bad/app", "EXTERNAL_USER", "VERIFIED", List.of())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("applicationCode is invalid");
    }

    @Test void disablesAnExistingBindingWithoutDeletingItsAuditability() {
        AgentApiCredentialRepository credentials=mock(AgentApiCredentialRepository.class);
        AgentApiMemoryBindingRepository bindings=mock(AgentApiMemoryBindingRepository.class);
        AuditService audit=mock(AuditService.class);
        AgentApiMemoryBindingEntity binding=new AgentApiMemoryBindingEntity(5L, "gateway-alpha", "EXTERNAL_USER", "VERIFIED", "[]");
        when(credentials.findByIdAndCompanyIdAndAgentId(5L, "org-a", "agent-a")).thenReturn(Optional.of(mock(AgentApiCredentialEntity.class)));
        when(bindings.findByCredentialId(5L)).thenReturn(Optional.of(binding));
        when(bindings.save(binding)).thenReturn(binding);
        AgentApiMemoryBindingService service=new AgentApiMemoryBindingService(credentials, bindings, new ObjectMapper(), audit);

        var result=service.disable("org-a", "agent-a", 5L, "actor-a");

        assertThat(result.enabled()).isFalse();
        verify(audit).log("org-a", "actor-a", "agent.api_memory_binding.disable", "agent=agent-a,credentialId=5,enabled=false");
    }
}
