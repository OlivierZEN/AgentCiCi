package com.codehouse.ciciassistant.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.auth.service.JwtService;
import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TenantContextFilterTest {

    private final TenantContextFilter filter = new TenantContextFilter(
            mock(JwtService.class),
            mock(OfficialAccessTokenService.class),
            new ObjectMapper(),
            false);

    @Test
    void shouldLetAsrWebSocketHandshakeReachItsOwnTokenValidator() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/asr");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isNotEqualTo(401);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void shouldStillRequireAuthenticationForProtectedBusinessApis() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Authentication required");
    }

    @Test
    void shouldLetOidcPasswordActionStartWithoutAnExistingApplicationSession() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/oidc/password");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isNotEqualTo(401);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void shouldLetSematticeInternalProvisioningReachItsHmacValidator() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/semattice/provisioning/reservations");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isNotEqualTo(401);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void shouldLetSematticeConsoleHandoffReachItsHmacValidator() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/semattice/console-handoffs/redeem");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isNotEqualTo(401);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void shouldRejectLegacyTokenWithoutCompanyIdClaim() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        Claims legacyClaims = mock(Claims.class);
        when(jwtService.parse("legacy-token")).thenReturn(legacyClaims);
        when(legacyClaims.get("typ", String.class)).thenReturn(null);
        when(legacyClaims.get("company_id", String.class)).thenReturn(null);
        when(legacyClaims.get("roles")).thenReturn(java.util.List.of("ORG_ADMIN"));

        TenantContextFilter strictFilter = new TenantContextFilter(
                jwtService, mock(OfficialAccessTokenService.class), new ObjectMapper(), false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/me");
        request.addHeader("Authorization", "Bearer legacy-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        strictFilter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Legacy company token is no longer accepted");
    }

    @Test
    void shouldAcceptEcosystemUserTokenForAgentCiCiAudience() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        OfficialAccessTokenService official = mock(OfficialAccessTokenService.class);
        when(official.verifyEcosystemUserContext("ecosystem-token", OfficialAccessTokenService.AGENTCICI_AUDIENCE))
                .thenReturn(new OfficialAccessTokenService.EcosystemUserContext(
                        "org00000000000000001", "member-1", "account-1", java.util.List.of("ORG_ADMIN")));
        TenantContextFilter ecosystemFilter = new TenantContextFilter(jwtService, official, new ObjectMapper(), false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/me");
        request.addHeader("Authorization", "Bearer ecosystem-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        ecosystemFilter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isNotEqualTo(401);
        assertThat(chain.getRequest()).isSameAs(request);
    }
}
