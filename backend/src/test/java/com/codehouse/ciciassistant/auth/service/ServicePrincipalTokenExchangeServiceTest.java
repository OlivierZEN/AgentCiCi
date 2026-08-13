package com.codehouse.ciciassistant.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.common.error.ForbiddenException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class ServicePrincipalTokenExchangeServiceTest {

    @Test
    void resolvesTheCanonicalSematticeTenantColumn() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        KeycloakOidcLoginService keycloak = mock(KeycloakOidcLoginService.class);
        OfficialAccessTokenService official = mock(OfficialAccessTokenService.class);
        when(keycloak.issuer()).thenReturn("https://sso.example/realms/agentcici");
        when(keycloak.verifyServiceAccessToken("source-token"))
                .thenReturn(new KeycloakOidcLoginService.ServiceAccessToken("service-subject", "service-client"));
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        ServicePrincipalTokenExchangeService service = new ServicePrincipalTokenExchangeService(
                jdbc, keycloak, official, true);

        assertThatThrownBy(() -> service.exchangeForSemattice("source-token"))
                .isInstanceOf(ForbiddenException.class);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class),
                eq("https://sso.example/realms/agentcici"), eq("service-subject"),
                eq("service-client"), eq("service-client"), eq(OfficialAccessTokenService.SEMATTICE_AUDIENCE));
        assertThat(sql.getValue()).contains("binding.semattice_tenant_id")
                .contains("resource.max_instances", "activation.actual_state='ACTIVE'")
                .doesNotContain("sematrice_tenant_id");
    }
}
