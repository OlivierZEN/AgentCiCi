package com.codehouse.ciciassistant.openapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.memory.service.TrustedMemoryRuntimeContextService;
import com.codehouse.ciciassistant.openapi.domain.AgentApiCredentialEntity;
import com.codehouse.ciciassistant.openapi.domain.AgentApiMemoryBindingEntity;
import com.codehouse.ciciassistant.openapi.domain.AgentApiMemoryBindingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentOpenApiMemoryContextServiceTest {
    @Test void derivesEveryTrustedFieldFromCredentialBinding() {
        AgentApiMemoryBindingRepository bindings=mock(AgentApiMemoryBindingRepository.class);
        TrustedMemoryRuntimeContextService runtime=mock(TrustedMemoryRuntimeContextService.class);
        AgentApiCredentialEntity credential=mock(AgentApiCredentialEntity.class);
        when(credential.getId()).thenReturn(9L); when(credential.getCompanyId()).thenReturn("org-a"); when(credential.getAgentId()).thenReturn("agent-a");
        AgentOpenApiAuthService.AuthenticatedCredential auth=new AgentOpenApiAuthService.AuthenticatedCredential(credential, mock(AgentDefinitionEntity.class), "127.0.0.1", null);
        AgentApiMemoryBindingEntity binding=new AgentApiMemoryBindingEntity(9L, "app-a", "EXTERNAL_USER", "VERIFIED", "[\"ns-a\"]");
        when(bindings.findByCredentialIdAndEnabledTrue(9L)).thenReturn(Optional.of(binding));
        when(runtime.enter(any())).thenReturn(() -> {});
        AgentOpenApiMemoryContextService service=new AgentOpenApiMemoryContextService(bindings, runtime, new ObjectMapper());

        assertThat(service.withTrustedContext(auth, "subject-a", "session-internal-a", () -> "ok")).isEqualTo("ok");
        ArgumentCaptor<TrustedMemoryRuntimeContextService.TrustedMemoryRequest> captor=ArgumentCaptor.forClass(TrustedMemoryRuntimeContextService.TrustedMemoryRequest.class);
        verify(runtime).enter(captor.capture());
        assertThat(captor.getValue().context().applicationCode()).isEqualTo("app-a");
        assertThat(captor.getValue().context().externalSubjectRef()).isEqualTo("subject-a");
        assertThat(captor.getValue().context().conversationRef()).isEqualTo("session-internal-a");
        assertThat(captor.getValue().domainNamespaces()).containsExactly("ns-a");
    }

    @Test void bypassesMemoryWhenTheTrustedConversationIsMissing() {
        AgentApiMemoryBindingRepository bindings=mock(AgentApiMemoryBindingRepository.class);
        TrustedMemoryRuntimeContextService runtime=mock(TrustedMemoryRuntimeContextService.class);
        AgentApiCredentialEntity credential=mock(AgentApiCredentialEntity.class);
        when(credential.getId()).thenReturn(9L); when(credential.getCompanyId()).thenReturn("org-a"); when(credential.getAgentId()).thenReturn("agent-a");
        AgentOpenApiAuthService.AuthenticatedCredential auth=new AgentOpenApiAuthService.AuthenticatedCredential(credential, mock(AgentDefinitionEntity.class), "127.0.0.1", null);
        when(bindings.findByCredentialIdAndEnabledTrue(9L)).thenReturn(Optional.empty());
        AgentOpenApiMemoryContextService service=new AgentOpenApiMemoryContextService(bindings, runtime, new ObjectMapper());

        assertThat(service.withTrustedContext(auth, "subject-a", "", () -> "ok")).isEqualTo("ok");
        verifyNoInteractions(bindings, runtime);
    }

    @Test void bypassesMemoryWhenTheCredentialHasNoEnabledBinding() {
        AgentApiMemoryBindingRepository bindings=mock(AgentApiMemoryBindingRepository.class);
        TrustedMemoryRuntimeContextService runtime=mock(TrustedMemoryRuntimeContextService.class);
        AgentApiCredentialEntity credential=mock(AgentApiCredentialEntity.class);
        when(credential.getId()).thenReturn(9L); when(credential.getCompanyId()).thenReturn("org-a"); when(credential.getAgentId()).thenReturn("agent-a");
        AgentOpenApiAuthService.AuthenticatedCredential auth=new AgentOpenApiAuthService.AuthenticatedCredential(credential, mock(AgentDefinitionEntity.class), "127.0.0.1", null);
        when(bindings.findByCredentialIdAndEnabledTrue(9L)).thenReturn(Optional.empty());
        AgentOpenApiMemoryContextService service=new AgentOpenApiMemoryContextService(bindings, runtime, new ObjectMapper());

        assertThat(service.withTrustedContext(auth, "subject-a", "session-internal-a", () -> "ok")).isEqualTo("ok");
        verify(bindings).findByCredentialIdAndEnabledTrue(9L);
        verifyNoInteractions(runtime);
    }
}
