package com.codehouse.ciciassistant.auth.api;

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

/** Relays a Keycloak HUMAN bearer token to the ecosystem controller without treating it as a CiCi JWT. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class EcosystemKeycloakBearerFilter extends OncePerRequestFilter {

    public static final String TOKEN_ATTRIBUTE = EcosystemKeycloakBearerFilter.class.getName() + ".keycloakAccessToken";
    private static final String PATH_PREFIX = "/openapi/v1/ecosystem/";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith(PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring("Bearer ".length()).trim();
            if (!token.isEmpty()) {
                request.setAttribute(TOKEN_ATTRIBUTE, token);
            }
            chain.doFilter(new HttpServletRequestWrapper(request) {
                @Override
                public String getHeader(String name) {
                    return "Authorization".equalsIgnoreCase(name) ? null : super.getHeader(name);
                }
            }, response);
            return;
        }
        chain.doFilter(request, response);
    }
}
