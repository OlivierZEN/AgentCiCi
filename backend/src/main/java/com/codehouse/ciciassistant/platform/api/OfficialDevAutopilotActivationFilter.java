package com.codehouse.ciciassistant.platform.api;

import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Accepts AgentCiCi-issued OACT only at the DevAutopilot activation resolve boundary. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class OfficialDevAutopilotActivationFilter extends OncePerRequestFilter {
    static final String ACTIVATION_PATH = "/openapi/v1/official/devautopilot/activation";

    private final OfficialAccessTokenService tokens;
    private final ObjectMapper objectMapper;

    public OfficialDevAutopilotActivationFilter(OfficialAccessTokenService tokens, ObjectMapper objectMapper) {
        this.tokens = tokens;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !ACTIVATION_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            unauthorized(response);
            return;
        }
        OfficialAccessTokenService.VerifiedContext context;
        try {
            context = tokens.verifyDevAutopilotContext(authorization.substring("Bearer ".length()).trim());
        } catch (Exception ex) {
            unauthorized(response);
            return;
        }
        try {
            TenantContext.setCompanyId(context.companyId());
            TenantContext.setUserId(context.principalId());
            TenantContext.setTokenType("official_access");
            chain.doFilter(withoutAuthorization(request), response);
        } finally {
            TenantContext.clear();
        }
    }

    private HttpServletRequest withoutAuthorization(HttpServletRequest request) {
        return new HttpServletRequestWrapper(request) {
            @Override
            public String getHeader(String name) {
                return "Authorization".equalsIgnoreCase(name) ? null : super.getHeader(name);
            }
        };
    }

    private void unauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail("Invalid or expired official access token")));
    }
}
