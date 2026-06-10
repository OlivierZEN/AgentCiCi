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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TenantContextFilter extends OncePerRequestFilter {

    public static final String ORG_HEADER = "X-Org-Id";
    public static final String USER_HEADER = "X-User-Id";
    private static final String AUTH_HEADER = "Authorization";

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final boolean allowHeaderContext;

    public TenantContextFilter(JwtService jwtService,
                               ObjectMapper objectMapper,
                               @Value("${app.auth.allow-header-context:false}") boolean allowHeaderContext) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
        this.allowHeaderContext = allowHeaderContext;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String orgId = allowHeaderContext ? request.getHeader(ORG_HEADER) : null;
            String userId = allowHeaderContext ? request.getHeader(USER_HEADER) : null;
            String authorization = request.getHeader(AUTH_HEADER);
            boolean authenticated = false;

            if (authorization != null && authorization.startsWith("Bearer ")) {
                String bearer = authorization.substring("Bearer ".length());
                if (isExternalApiKeyRequest(request, bearer)) {
                    filterChain.doFilter(request, response);
                    return;
                }
                try {
                    Claims claims = jwtService.parse(bearer);
                    String tokenType = claims.get("typ", String.class);
                    if ("embed_app".equals(tokenType)) {
                        if (isEmbedRuntimeRequest(request)) {
                            filterChain.doFilter(request, response);
                            return;
                        }
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail("Embed token is not valid for this endpoint")));
                        return;
                    }
                    orgId = claims.get("org_id", String.class);
                    TenantContext.setTokenType(tokenType == null || tokenType.isBlank() ? "organization" : tokenType);
                    TenantContext.setRoles(extractRoles(claims));
                    authenticated = true;
                    if ("platform".equals(tokenType)) {
                        userId = claims.get("platform_account_id", String.class);
                        if (userId == null || userId.isBlank()) {
                            userId = claims.getSubject();
                        }
                        orgId = null;
                    } else {
                        String memberId = claims.get("member_id", String.class);
                        userId = memberId == null || memberId.isBlank() ? claims.getSubject() : memberId;
                    }
                } catch (Exception ex) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail("Invalid or expired token")));
                    return;
                }
            }

            if (!authenticated && allowHeaderContext) {
                authenticated = hasText(orgId) && hasText(userId);
            }

            if (!authenticated && requiresAuthenticatedContext(request)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail("Authentication required")));
                return;
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

    private boolean isExternalApiKeyRequest(HttpServletRequest request, String bearer) {
        String path = request.getRequestURI();
        return path != null
                && (path.startsWith("/openapi/v1/") || isEmbedTokenIssueRequest(path))
                && bearer != null
                && bearer.startsWith("cici_ak_");
    }

    private boolean requiresAuthenticatedContext(HttpServletRequest request) {
        return !isPublicRequest(request);
    }

    private boolean isPublicRequest(HttpServletRequest request) {
        String method = request.getMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }
        String path = request.getRequestURI();
        if (path == null || path.isBlank()) {
            return false;
        }
        if (path.startsWith("/openapi/v1/")) {
            return true;
        }
        if (path.startsWith("/embed/v1/apps/")) {
            return true;
        }
        if (path.startsWith("/wecom/kf/callback")) {
            return true;
        }
        if (path.startsWith("/public/")) {
            return true;
        }
        return isExactPublicPath(path);
    }

    private boolean isExactPublicPath(String path) {
        return "/auth/sms/send".equals(path)
                || "/auth/sms/login".equals(path)
                || "/auth/password/login".equals(path)
                || "/auth/register".equals(path)
                || "/auth/platform/password/login".equals(path)
                || "/actuator/health".equals(path)
                || "/system/health".equals(path)
                || "/system/version".equals(path)
                || "/billing/mode".equals(path)
                || "/api/autoservice/demo-requests".equals(path);
    }

    private boolean isEmbedTokenIssueRequest(String path) {
        return path != null
                && path.startsWith("/embed/v1/apps/")
                && path.endsWith("/tokens");
    }

    private boolean isEmbedRuntimeRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.startsWith("/embed/v1/apps/");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
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
