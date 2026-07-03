package com.codehouse.ciciassistant.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.codehouse.ciciassistant.auth.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TenantContextFilterTest {

    private final TenantContextFilter filter = new TenantContextFilter(
            mock(JwtService.class),
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
}
