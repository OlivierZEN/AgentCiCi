package com.codehouse.ciciassistant.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.memory.service.TrustedMemoryRuntimeContextService;
import com.codehouse.ciciassistant.openapi.domain.AgentApiCredentialEntity;
import com.codehouse.ciciassistant.openapi.domain.AgentApiMemoryBindingEntity;
import com.codehouse.ciciassistant.openapi.domain.AgentApiMemoryBindingRepository;
import com.codehouse.ciciassistant.openapi.service.AgentOpenApiAuthService;
import com.codehouse.ciciassistant.openapi.service.AgentOpenApiMemoryContextService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GenericExternalMemoryAdapterContractTest {
    @Test void keepsTwoIndependentAdapterBindingsAndSessionsIsolated() {
        AgentApiMemoryBindingRepository bindings=mock(AgentApiMemoryBindingRepository.class);
        TrustedMemoryRuntimeContextService runtime=mock(TrustedMemoryRuntimeContextService.class);
        when(runtime.enter(any())).thenReturn(() -> {});
        when(bindings.findByCredentialIdAndEnabledTrue(11L)).thenReturn(Optional.of(
                new AgentApiMemoryBindingEntity(11L, "adapter-alpha", "EXTERNAL_USER", "VERIFIED", "[\"sales\"]")));
        when(bindings.findByCredentialIdAndEnabledTrue(22L)).thenReturn(Optional.of(
                new AgentApiMemoryBindingEntity(22L, "adapter-beta", "EXTERNAL_PRINCIPAL", "ANONYMOUS", "[\"support\"]")));
        AgentOpenApiMemoryContextService service=new AgentOpenApiMemoryContextService(bindings, runtime, new ObjectMapper());

        assertThat(service.withTrustedContext(auth(11L, "agent-a"), "same-subject", "session-a", () -> "a")).isEqualTo("a");
        assertThat(service.withTrustedContext(auth(22L, "agent-b"), "same-subject", "session-b", () -> "b")).isEqualTo("b");

        ArgumentCaptor<TrustedMemoryRuntimeContextService.TrustedMemoryRequest> requests=ArgumentCaptor.forClass(TrustedMemoryRuntimeContextService.TrustedMemoryRequest.class);
        verify(runtime, org.mockito.Mockito.times(2)).enter(requests.capture());
        var first=requests.getAllValues().get(0).context(); var second=requests.getAllValues().get(1).context();
        assertThat(first.applicationCode()).isEqualTo("adapter-alpha");
        assertThat(second.applicationCode()).isEqualTo("adapter-beta");
        assertThat(first.subjectType()).isEqualTo("EXTERNAL_USER");
        assertThat(second.subjectType()).isEqualTo("EXTERNAL_PRINCIPAL");
        assertThat(first.conversationRef()).isEqualTo("session-a");
        assertThat(second.conversationRef()).isEqualTo("session-b");
    }

    @Test void degradesSafelyWhenTheSecondAdapterBindingIsDisabled() {
        AgentApiMemoryBindingRepository bindings=mock(AgentApiMemoryBindingRepository.class);
        TrustedMemoryRuntimeContextService runtime=mock(TrustedMemoryRuntimeContextService.class);
        when(bindings.findByCredentialIdAndEnabledTrue(22L)).thenReturn(Optional.empty());
        AgentOpenApiMemoryContextService service=new AgentOpenApiMemoryContextService(bindings, runtime, new ObjectMapper());

        assertThat(service.withTrustedContext(auth(22L, "agent-b"), "subject-b", "session-b", () -> "ok")).isEqualTo("ok");

        verify(bindings).findByCredentialIdAndEnabledTrue(22L);
        verifyNoMoreInteractions(runtime);
    }

    private static AgentOpenApiAuthService.AuthenticatedCredential auth(Long credentialId, String agentId) {
        AgentApiCredentialEntity credential=mock(AgentApiCredentialEntity.class);
        when(credential.getId()).thenReturn(credentialId); when(credential.getOrgId()).thenReturn("org-a"); when(credential.getAgentId()).thenReturn(agentId);
        return new AgentOpenApiAuthService.AuthenticatedCredential(credential, mock(AgentDefinitionEntity.class), "127.0.0.1", null);
    }
}
