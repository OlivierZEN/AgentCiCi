package com.codehouse.ciciassistant.embed.service;

import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.auth.service.JwtService;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.common.error.UnauthorizedException;
import com.codehouse.ciciassistant.embed.domain.EmbedAppDefinitionEntity;
import com.codehouse.ciciassistant.embed.domain.OrgEmbedAppConfigEntity;
import com.codehouse.ciciassistant.openapi.domain.AgentApiCredentialEntity;
import com.codehouse.ciciassistant.openapi.domain.AgentApiCredentialRepository;
import com.codehouse.ciciassistant.openapi.service.AgentApiKeyGenerator;
import com.codehouse.ciciassistant.openapi.service.AgentOpenApiCredentialService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmbedTokenService {

    private static final String API_KEY_HEADER = "X-Cici-Api-Key";

    private final AgentApiCredentialRepository credentialRepository;
    private final AgentApiKeyGenerator keyGenerator;
    private final AgentOpenApiCredentialService credentialService;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final EmbedAppService embedAppService;

    public EmbedTokenService(AgentApiCredentialRepository credentialRepository,
                             AgentApiKeyGenerator keyGenerator,
                             AgentOpenApiCredentialService credentialService,
                             UserRepository userRepository,
                             JwtService jwtService,
                             EmbedAppService embedAppService) {
        this.credentialRepository = credentialRepository;
        this.keyGenerator = keyGenerator;
        this.credentialService = credentialService;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.embedAppService = embedAppService;
    }

    @Transactional
    public TokenIssue issueToken(String appCode, TokenCommand command, HttpServletRequest request) {
        AgentApiCredentialEntity credential = authenticateCredential(request);
        EmbedAppDefinitionEntity definition = embedAppService.requireDefinition(appCode);
        if (!EmbedAppDefinitionEntity.STATUS_ENABLED.equals(definition.getStatus())) {
            throw new ForbiddenException("嵌入式智能应用未启用");
        }
        OrgEmbedAppConfigEntity config = embedAppService.ensureConfig(credential.getOrgId(), definition);
        if (!config.isEnabled()) {
            throw new ForbiddenException("当前组织未启用该嵌入式智能应用");
        }
        String parentOrigin = embedAppService.normalizeOrigin(command.parentOrigin());
        if (!embedAppService.originAllowed(embedAppService.allowedOrigins(config), parentOrigin)) {
            throw new ForbiddenException("parentOrigin is not allowed for this embedded app");
        }
        String source = requireText(command.source(), "source").toLowerCase();
        if (!embedAppService.supportedSources(definition).contains(source)) {
            throw new IllegalArgumentException("Unsupported source: " + source);
        }
        Map<String, Object> context = command.context() == null ? Map.of() : command.context();
        String objectType = requireText(stringValue(context.get("objectType")), "context.objectType");
        String objectId = requireText(stringValue(context.get("objectId")), "context.objectId");
        String nonce = "emb_" + UUID.randomUUID().toString().replace("-", "");
        List<String> permissions = permissions(command.permissions(), config, definition);
        int ttl = ttl(command.ttlSeconds(), config.getTokenTtlSeconds());
        Instant expiresAt = Instant.now().plusSeconds(ttl);
        String externalUserId = externalUserId(command.user());
        String displayName = stringValue(command.user() == null ? null : command.user().get("displayName"));
        String subject = "embed:" + definition.getAppCode() + ":" + nonce;
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("typ", "embed_app");
        claims.put("aud", "agentcici-embed");
        claims.put("appCode", definition.getAppCode());
        claims.put("org_id", credential.getOrgId());
        claims.put("orgId", credential.getOrgId());
        claims.put("member_id", credential.getRunAsUserId());
        claims.put("userId", credential.getRunAsUserId());
        claims.put("source", source);
        claims.put("objectType", objectType);
        claims.put("objectId", objectId);
        claims.put("recordName", clip(stringValue(context.get("recordName")), 256));
        claims.put("customerName", clip(stringValue(context.get("customerName")), 256));
        claims.put("parentOrigin", parentOrigin);
        claims.put("permissions", permissions);
        claims.put("externalUserId", externalUserId);
        claims.put("externalUserDisplayName", displayName);
        claims.put("nonce", nonce);
        claims.put("context", context);
        String token = jwtService.issueToken(subject, claims, ttl);
        credential.markUsed();
        return new TokenIssue(token, expiresAt, definition.getEmbedUrl(), permissions, ttl);
    }

    @Transactional
    public TokenIssue issueAdminDebugToken(String orgId, String currentUserId, String appCode, TokenCommand command) {
        EmbedAppDefinitionEntity definition = embedAppService.requireDefinition(appCode);
        if (!EmbedAppDefinitionEntity.STATUS_ENABLED.equals(definition.getStatus())) {
            throw new ForbiddenException("嵌入式智能应用未启用");
        }
        OrgEmbedAppConfigEntity config = embedAppService.ensureConfig(orgId, definition);
        if (!config.isEnabled()) {
            throw new ForbiddenException("当前组织未启用该嵌入式智能应用");
        }
        String runAsUserId = config.getRunAsUserId() == null || config.getRunAsUserId().isBlank()
                ? requireText(currentUserId, "currentUserId")
                : config.getRunAsUserId();
        userRepository.findByIdAndOrg_Id(runAsUserId, orgId)
                .orElseThrow(() -> new ForbiddenException("runAsUserId no longer belongs to the current org"));
        String parentOrigin = embedAppService.normalizeOrigin(command.parentOrigin());
        if (!embedAppService.originAllowed(embedAppService.allowedOrigins(config), parentOrigin)) {
            throw new ForbiddenException("parentOrigin is not allowed for this embedded app");
        }
        String source = requireText(command.source(), "source").toLowerCase();
        if (!embedAppService.supportedSources(definition).contains(source)) {
            throw new IllegalArgumentException("Unsupported source: " + source);
        }
        Map<String, Object> context = command.context() == null ? Map.of() : command.context();
        String objectType = requireText(stringValue(context.get("objectType")), "context.objectType");
        String objectId = requireText(stringValue(context.get("objectId")), "context.objectId");
        String nonce = "emb_dbg_" + UUID.randomUUID().toString().replace("-", "");
        List<String> permissions = permissions(command.permissions(), config, definition);
        int ttl = ttl(command.ttlSeconds(), config.getTokenTtlSeconds());
        Instant expiresAt = Instant.now().plusSeconds(ttl);
        String externalUserId = externalUserId(command.user());
        String displayName = stringValue(command.user() == null ? null : command.user().get("displayName"));
        String subject = "embed:" + definition.getAppCode() + ":" + nonce;
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("typ", "embed_app");
        claims.put("aud", "agentcici-embed");
        claims.put("debug", true);
        claims.put("appCode", definition.getAppCode());
        claims.put("org_id", orgId);
        claims.put("orgId", orgId);
        claims.put("member_id", runAsUserId);
        claims.put("userId", runAsUserId);
        claims.put("source", source);
        claims.put("objectType", objectType);
        claims.put("objectId", objectId);
        claims.put("recordName", clip(stringValue(context.get("recordName")), 256));
        claims.put("customerName", clip(stringValue(context.get("customerName")), 256));
        claims.put("parentOrigin", parentOrigin);
        claims.put("permissions", permissions);
        claims.put("externalUserId", externalUserId);
        claims.put("externalUserDisplayName", displayName);
        claims.put("nonce", nonce);
        claims.put("context", context);
        String token = jwtService.issueToken(subject, claims, ttl);
        return new TokenIssue(token, expiresAt, definition.getEmbedUrl(), permissions, ttl);
    }

    public AuthenticatedEmbedToken authenticateEmbedToken(String appCode, HttpServletRequest request) {
        String token = extractBearer(request);
        if (token.isBlank()) {
            throw new UnauthorizedException("Embed token is required");
        }
        try {
            Claims claims = jwtService.parse(token);
            if (!"embed_app".equals(claims.get("typ", String.class))) {
                throw new UnauthorizedException("Invalid embed token");
            }
            String tokenAppCode = claims.get("appCode", String.class);
            if (!embedAppService.requireDefinition(appCode).getAppCode().equals(tokenAppCode)) {
                throw new ForbiddenException("Embed token appCode mismatch");
            }
            List<String> permissions = listClaim(claims.get("permissions"));
            @SuppressWarnings("unchecked")
            Map<String, Object> context = claims.get("context", Map.class);
            return new AuthenticatedEmbedToken(
                    tokenAppCode,
                    claims.get("orgId", String.class),
                    claims.get("userId", String.class),
                    claims.get("externalUserId", String.class),
                    claims.get("source", String.class),
                    claims.get("objectType", String.class),
                    claims.get("objectId", String.class),
                    claims.get("recordName", String.class),
                    claims.get("customerName", String.class),
                    claims.get("parentOrigin", String.class),
                    permissions,
                    claims.get("nonce", String.class),
                    context == null ? Map.of() : context);
        } catch (UnauthorizedException | ForbiddenException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new UnauthorizedException("Invalid or expired embed token");
        }
    }

    private AgentApiCredentialEntity authenticateCredential(HttpServletRequest request) {
        String plainKey = extractPlainKey(request);
        if (plainKey.isBlank()) {
            throw new UnauthorizedException("API key is required");
        }
        String publicId = keyGenerator.publicIdFromPlainKey(plainKey);
        if (publicId.isBlank()) {
            throw new UnauthorizedException("API key is invalid or revoked");
        }
        AgentApiCredentialEntity credential = credentialRepository.findByPublicId(publicId)
                .filter(item -> keyGenerator.matches(plainKey, item.getKeyHash()))
                .orElseThrow(() -> new UnauthorizedException("API key is invalid or revoked"));
        if (!AgentApiCredentialEntity.STATUS_ACTIVE.equals(credential.getStatus())) {
            throw new UnauthorizedException("API key is invalid or revoked");
        }
        if (credential.getExpiresAt() != null && credential.getExpiresAt().isBefore(Instant.now())) {
            throw new ForbiddenException("API key is expired");
        }
        if (!clientIpAllowed(credentialService.toView(credential).allowedIps(), clientIp(request))) {
            throw new ForbiddenException("Client IP is not allowed");
        }
        userRepository.findByIdAndOrg_Id(credential.getRunAsUserId(), credential.getOrgId())
                .orElseThrow(() -> new ForbiddenException("runAsUserId no longer belongs to the credential org"));
        return credential;
    }

    private String extractPlainKey(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring("Bearer ".length()).trim();
        }
        String apiKey = request.getHeader(API_KEY_HEADER);
        return apiKey == null ? "" : apiKey.trim();
    }

    private String extractBearer(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return "";
        }
        return authorization.substring("Bearer ".length()).trim();
    }

    private boolean clientIpAllowed(List<String> allowedIps, String clientIp) {
        if (allowedIps == null || allowedIps.isEmpty()) {
            return true;
        }
        for (String entry : allowedIps) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String text = entry.trim();
            if (text.equals(clientIp)) {
                return true;
            }
            if (text.endsWith("/32") && text.substring(0, text.length() - 3).equals(clientIp)) {
                return true;
            }
        }
        return false;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma >= 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr() == null ? "" : request.getRemoteAddr();
    }

    private List<String> permissions(List<String> requested, OrgEmbedAppConfigEntity config, EmbedAppDefinitionEntity definition) {
        List<String> allowed = embedAppService.scopeOverrides(config).isEmpty()
                ? embedAppService.requiredScopes(definition)
                : embedAppService.scopeOverrides(config);
        if (requested == null || requested.isEmpty()) {
            return allowed;
        }
        List<String> out = new ArrayList<>();
        for (String value : requested) {
            String text = value == null ? "" : value.trim();
            if (text.isBlank() || out.contains(text)) {
                continue;
            }
            if (!allowed.contains(text)) {
                throw new ForbiddenException("Requested embed scope is not allowed: " + text);
            }
            out.add(text);
        }
        return List.copyOf(out);
    }

    private int ttl(Integer requested, int configured) {
        int ttl = requested == null ? configured : requested;
        if (ttl < 60 || ttl > 1800) {
            throw new IllegalArgumentException("ttlSeconds must be between 60 and 1800");
        }
        return ttl;
    }

    private String externalUserId(Map<String, Object> user) {
        if (user == null || user.isEmpty()) {
            return "";
        }
        return clip(stringValue(user.get("externalUserId")).isBlank()
                ? stringValue(user.get("id"))
                : stringValue(user.get("externalUserId")), 128);
    }

    private List<String> listClaim(Object raw) {
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    out.add(item.toString());
                }
            }
            return List.copyOf(out);
        }
        return List.of();
    }

    private String requireText(String value, String fieldName) {
        String text = value == null ? "" : value.trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return text;
    }

    private String stringValue(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    private String clip(String value, int max) {
        String text = value == null ? "" : value.trim();
        return text.length() <= max ? text : text.substring(0, max);
    }

    public record TokenCommand(
            String source,
            String parentOrigin,
            Map<String, Object> user,
            Map<String, Object> context,
            List<String> permissions,
            Integer ttlSeconds
    ) {
    }

    public record TokenIssue(
            String embedToken,
            Instant expiresAt,
            String embedUrl,
            List<String> permissions,
            int ttlSeconds
    ) {
    }

    public record AuthenticatedEmbedToken(
            String appCode,
            String orgId,
            String userId,
            String externalUserId,
            String source,
            String objectType,
            String objectId,
            String recordName,
            String customerName,
            String parentOrigin,
            List<String> permissions,
            String nonce,
            Map<String, Object> context
    ) {
        public boolean can(String permission) {
            return permissions != null && permissions.contains(permission);
        }
    }
}
