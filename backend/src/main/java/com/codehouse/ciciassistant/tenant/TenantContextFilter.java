package com.codehouse.ciciassistant.tenant;

import com.codehouse.ciciassistant.auth.service.JwtService;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.jsonwebtoken.Claims;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TenantContextFilter extends OncePerRequestFilter {

    public static final String ORG_HEADER = "X-Org-Id";
    public static final String USER_HEADER = "X-User-Id";
    private static final String AUTH_HEADER = "Authorization";

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public TenantContextFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String orgId = request.getHeader(ORG_HEADER);
            String userId = request.getHeader(USER_HEADER);
            String authorization = request.getHeader(AUTH_HEADER);

            if (authorization != null && authorization.startsWith("Bearer ")) {
                String bearer = authorization.substring("Bearer ".length());
                if (isAgentOpenApiKeyRequest(request, bearer)) {
                    filterChain.doFilter(request, response);
                    return;
                }
                try {
                    Claims claims = jwtService.parse(bearer);
                    orgId = claims.get("org_id", String.class);
                    String memberId = claims.get("member_id", String.class);
                    userId = memberId == null || memberId.isBlank() ? claims.getSubject() : memberId;
                    TenantContext.setRoles(extractRoles(claims));
                } catch (Exception ex) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail("Invalid or expired token")));
                    return;
                }
            }

            if (orgId != null && !orgId.isBlank()) {
                TenantContext.setOrgId(orgId.trim());
            }
            if (userId != null && !userId.isBlank()) {
                TenantContext.setUserId(userId.trim());
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private boolean isAgentOpenApiKeyRequest(HttpServletRequest request, String bearer) {
        String path = request.getRequestURI();
        return path != null
                && path.startsWith("/openapi/v1/")
                && bearer != null
                && bearer.startsWith("cici_ak_");
    }

    @SuppressWarnings("unchecked")
    private static List<String> extractRoles(Claims claims) {
        Object raw = claims.get("roles");
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                if (o != null) {
                    out.add(o.toString());
                }
            }
            return out;
        }
        return List.of(raw.toString());
    }
}
