package com.codehouse.ciciassistant.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.codehouse.ciciassistant.auth.domain.AccountExternalIdentityRepository;
import com.codehouse.ciciassistant.auth.service.AuthService;
import com.codehouse.ciciassistant.auth.service.KeycloakOidcLoginService;
import com.codehouse.ciciassistant.auth.service.OidcLoginStateStore;
import com.codehouse.ciciassistant.common.error.UnauthorizedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class KeycloakOidcLoginServiceTest {

    @Test
    void canonicalizesNonCallbackHostsBeforeCreatingOidcState() {
        KeycloakOidcLoginService service = service();

        assertThat(service.isCanonicalStartHost("agentcici.com")).isFalse();
        assertThat(service.isCanonicalStartHost("x.agentcici.com")).isTrue();
        assertThat(service.canonicalStartUri("/admin/ops?tab=access").toString())
                .isEqualTo("https://x.agentcici.com/auth/oidc/login?return_to=%2Fadmin%2Fops%3Ftab%3Daccess");
    }

    @Test
    void rejectsLookalikeOrMalformedStartHosts() {
        KeycloakOidcLoginService service = service();

        assertThat(service.isCanonicalStartHost("x.agentcici.com.evil.example")).isFalse();
        assertThat(service.isCanonicalStartHost("x.agentcici.com:444")).isFalse();
        assertThat(service.isCanonicalStartHost("not a host")).isFalse();
    }

    @Test
    void retainsFailClosedStateComparisonAtCallback() {
        KeycloakOidcLoginService service = service(true);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.complete("authorization-code", "expected-state", "other-state"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid OIDC login state");
    }

    private KeycloakOidcLoginService service() {
        return service(false);
    }

    private KeycloakOidcLoginService service(boolean enabled) {
        return new KeycloakOidcLoginService(
                org.mockito.Mockito.mock(AccountExternalIdentityRepository.class),
                org.mockito.Mockito.mock(AuthService.class),
                org.mockito.Mockito.mock(OidcLoginStateStore.class),
                new ObjectMapper(),
                enabled,
                "https://sso.agentcici.com/realms/agentcici",
                "agentcici-bff",
                "test-client-secret",
                "https://x.agentcici.com/auth/oidc/callback");
    }
}
