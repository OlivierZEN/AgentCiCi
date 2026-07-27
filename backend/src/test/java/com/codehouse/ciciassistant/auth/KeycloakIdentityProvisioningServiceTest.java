package com.codehouse.ciciassistant.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codehouse.ciciassistant.auth.domain.AccountExternalIdentityRepository;
import com.codehouse.ciciassistant.auth.domain.UserAccountRepository;
import com.codehouse.ciciassistant.auth.service.KeycloakIdentityProvisioningService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class KeycloakIdentityProvisioningServiceTest {

    @Test
    void allowsMachineProvisioningWithoutHumanInvitationRedirectUri() {
        KeycloakIdentityProvisioningService service = service(false, true, "");

        assertThat(service.isEnabled()).isFalse();
        assertThat(service.isMachineProvisioningEnabled()).isTrue();
    }

    @Test
    void requiresInvitationRedirectOnlyWhenHumanProvisioningIsEnabled() {
        assertThatThrownBy(() -> service(true, false, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("redirect URI");
    }

    @Test
    void failsClosedWhenMachineProvisioningIsDisabled() {
        KeycloakIdentityProvisioningService service = service(false, false, "");

        assertThatThrownBy(() -> service.createServiceClient("agentcici-test", "semattice-api"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("尚未启用");
    }

    private KeycloakIdentityProvisioningService service(boolean humanEnabled, boolean machineEnabled, String redirectUri) {
        return new KeycloakIdentityProvisioningService(
                Mockito.mock(AccountExternalIdentityRepository.class),
                Mockito.mock(UserAccountRepository.class),
                new ObjectMapper(), humanEnabled, machineEnabled,
                "https://sso.agentcici.com/realms/agentcici", "agentcici-provisioner", "test-secret", redirectUri);
    }
}
