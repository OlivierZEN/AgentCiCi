package com.codehouse.ciciassistant.platform.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class OfficialDevAutopilotActivationFilterTest {

    @Test
    void verifiesOactAndExposesOnlyTrustedCompanyContextDownstream() throws Exception {
        OfficialAccessTokenService tokens = mock(OfficialAccessTokenService.class);
        when(tokens.verifyDevAutopilotContext("valid-oact")).thenReturn(
                new OfficialAccessTokenService.VerifiedContext(
                        "org00000000000000001", "tenant-a", "principal-a", "HUMAN"));
        OfficialDevAutopilotActivationFilter filter =
                new OfficialDevAutopilotActivationFilter(tokens, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", OfficialDevAutopilotActivationFilter.ACTIVATION_PATH);
        request.addHeader("Authorization", "Bearer valid-oact");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (downstreamRequest, downstreamResponse) -> {
            assertThat(((HttpServletRequest) downstreamRequest).getHeader("Authorization")).isNull();
            assertThat(TenantContext.requireCompanyId()).isEqualTo("org00000000000000001");
            assertThat(TenantContext.getUserId()).contains("principal-a");
        });

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(TenantContext.getCompanyId()).isEmpty();
    }

    @Test
    void rejectsInvalidOactBeforeActivationLookup() throws ServletException, IOException {
        OfficialAccessTokenService tokens = mock(OfficialAccessTokenService.class);
        when(tokens.verifyDevAutopilotContext("invalid-oact"))
                .thenThrow(new ForbiddenException("invalid"));
        OfficialDevAutopilotActivationFilter filter =
                new OfficialDevAutopilotActivationFilter(tokens, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", OfficialDevAutopilotActivationFilter.ACTIVATION_PATH);
        request.addHeader("Authorization", "Bearer invalid-oact");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (downstreamRequest, downstreamResponse) -> {
            throw new AssertionError("invalid OACT must not reach the activation controller");
        });

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Invalid or expired official access token");
    }
}
