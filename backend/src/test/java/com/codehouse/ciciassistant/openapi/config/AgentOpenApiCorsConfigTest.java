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

    @Test
    void shouldAllowConversationDeletePreflight() throws Exception {
        CorsFilter filter = corsFilter("*");
        MockHttpServletRequest request = preflight(
                "https://another.example",
                "DELETE",
                "/openapi/v1/conversations/conversation-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS))
                .contains("DELETE");
    }

    @Test
    void shouldRegisterOnlyForOpenApiOwnedPaths() {
        AgentOpenApiProperties properties = new AgentOpenApiProperties();
        properties.setCorsAllowedOrigins(List.of("*"));
        FilterRegistrationBean<CorsFilter> registration =
                new AgentOpenApiCorsConfig().agentOpenApiCorsFilter(properties);

        assertThat(registration.getUrlPatterns())
                .containsExactlyInAnyOrder("/openapi/v1/*", "/auth/cloudcc-sso/*");
    }

    private CorsFilter corsFilter(String allowedOrigin) {
        AgentOpenApiProperties properties = new AgentOpenApiProperties();
        properties.setCorsAllowedOrigins(List.of(allowedOrigin));
        FilterRegistrationBean<CorsFilter> registration =
                new AgentOpenApiCorsConfig().agentOpenApiCorsFilter(properties);
        return registration.getFilter();
    }

    private MockHttpServletRequest preflight(String origin) {
        return preflight(origin, "POST", "/openapi/v1/chat-messages");
    }

    private MockHttpServletRequest preflight(String origin, String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", path);
        request.addHeader(HttpHeaders.ORIGIN, origin);
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method);
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type,idempotency-key");
        return request;
    }
}
