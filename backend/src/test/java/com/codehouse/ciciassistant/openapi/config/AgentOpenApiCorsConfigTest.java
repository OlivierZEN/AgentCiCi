package com.codehouse.ciciassistant.openapi.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CorsFilter;

class AgentOpenApiCorsConfigTest {

    @Test
    void shouldAllowWildcardOpenApiPreflightOrigin() throws Exception {
        CorsFilter filter = corsFilter("*");
        MockHttpServletRequest request = preflight("https://cnbh01.cloudcc.cn");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .isEqualTo("*");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS))
                .contains("POST");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS))
                .contains("authorization");
    }

    @Test
    void shouldAllowAnyOpenApiPreflightOrigin() throws Exception {
        CorsFilter filter = corsFilter("*");
        MockHttpServletRequest request = preflight("https://another.example");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo("*");
    }

    private CorsFilter corsFilter(String allowedOrigin) {
        AgentOpenApiProperties properties = new AgentOpenApiProperties();
        properties.setCorsAllowedOrigins(List.of(allowedOrigin));
        FilterRegistrationBean<CorsFilter> registration =
                new AgentOpenApiCorsConfig().agentOpenApiCorsFilter(properties);
        return registration.getFilter();
    }

    private MockHttpServletRequest preflight(String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/openapi/v1/agents/agent-1/chat/stream");
        request.addHeader(HttpHeaders.ORIGIN, origin);
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST");
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type,idempotency-key");
        return request;
    }
}
