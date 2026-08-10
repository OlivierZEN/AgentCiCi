package com.codehouse.ciciassistant.auth.service;

import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.auth.domain.AccountExternalIdentityEntity;
import com.codehouse.ciciassistant.auth.domain.AccountExternalIdentityRepository;
import com.codehouse.ciciassistant.auth.domain.SematticeProvisioningBindingEntity;
import com.codehouse.ciciassistant.auth.domain.SematticeProvisioningBindingRepository;
import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.semattice.SematticeMetadataApprovalService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtBuilder;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Signs short-lived official application context tokens. This service never exchanges a token per
 * Semattice request: callers reuse the token until its bounded expiration.
 */
@Service
public class OfficialAccessTokenService {

    public static final String SEMATTICE_AUDIENCE = "semattice-api";
    public static final String IDENTITY_PRINCIPAL_READ_SCOPE = "identity.principal.read";

    private final AccountExternalIdentityRepository identityRepository;
    private final SematticeProvisioningBindingRepository bindingRepository;
    private final SematticeMetadataApprovalService metadataApprovalService;
    private final boolean enabled;
    private final String issuer;
    private final String keyId;
    private final PrivateKey privateKey;
    private final List<String> sematticeScopes;
    private final List<String> sematticeServiceScopes;
    private final long ttlSeconds;

    public OfficialAccessTokenService(AccountExternalIdentityRepository identityRepository,
                                      SematticeProvisioningBindingRepository bindingRepository,
                                      SematticeMetadataApprovalService metadataApprovalService,
                                      @Value("${app.auth.official-access.enabled:false}") boolean enabled,
                                      @Value("${app.auth.official-access.issuer:}") String issuer,
                                      @Value("${app.auth.official-access.key-id:}") String keyId,
                                      @Value("${app.auth.official-access.private-key-pkcs8-base64:}") String privateKeyBase64,
                                      @Value("${app.auth.official-access.semattice-scopes:}") List<String> sematticeScopes,
                                      @Value("${app.auth.official-access.semattice-service-scopes:}") List<String> sematticeServiceScopes,
                                      @Value("${app.auth.official-access.ttl-seconds:600}") long ttlSeconds) {
        this.identityRepository = identityRepository;
        this.bindingRepository = bindingRepository;
        this.metadataApprovalService = metadataApprovalService;
        this.enabled = enabled;
        this.issuer = trim(issuer);
        this.keyId = trim(keyId);
        this.privateKey = enabled ? parseRsaPrivateKey(privateKeyBase64) : null;
        this.sematticeScopes = normalizeScopes(sematticeScopes);
        List<String> configuredServiceScopes = normalizeScopes(sematticeServiceScopes);
        this.sematticeServiceScopes = configuredServiceScopes.isEmpty() ? this.sematticeScopes : configuredServiceScopes;
        if (ttlSeconds < 60 || ttlSeconds > 600) {
            throw new IllegalArgumentException("Official access token TTL must be between 60 and 600 seconds");
        }
        this.ttlSeconds = ttlSeconds;
        if (enabled && (this.issuer.isBlank() || this.keyId.isBlank() || this.sematticeScopes.isEmpty())) {
            throw new IllegalArgumentException("Official access token issuer, key ID and Semattice scopes are required when enabled");
        }
    }

    public IssuedToken issueForSemattice(UserEntity member) {
        return issueForSemattice(member, sematticeScopes);
    }

    /** Tenant-scoped HUMAN token for the activated DevAutopilot application. */
    public IssuedToken issueForDevAutopilot(UserEntity member) {
        List<String> scopes = new ArrayList<>(sematticeScopes);
        if (!scopes.contains(IDENTITY_PRINCIPAL_READ_SCOPE)) {
            scopes.add(IDENTITY_PRINCIPAL_READ_SCOPE);
        }
        return issueForSemattice(member, List.copyOf(scopes));
    }

    public IssuedToken issueForSematticeConsole(UserEntity member) {
        return issueForSemattice(member, sematticeConsoleScopesFor(member),
                metadataApprovalService.approvedIdsForRequester(member.getCompany().getId(), member.getId()));
    }

    /** Server-only token used to establish the member projection before dependent SERVICE projections. */
    public IssuedToken issueForSematticePrincipalSync(UserEntity member) {
        return issueForSemattice(member, List.of("identity.principal.sync"), List.of(), false);
    }

    /**
     * Signs the resource-facing token after the caller has resolved an active service principal,
     * its accountable human owner, tenant binding and persisted scope grants.
     */
    public IssuedToken issueForSematticeService(String principalId,
                                                String ownerPrincipalId,
                                                String clientId,
                                                String tenantId,
                                                String companyId,
                                                List<String> requestedScopes) {
        return issueForSematticeService(principalId, ownerPrincipalId, clientId, tenantId, companyId,
                requestedScopes, null, null);
    }

    public IssuedToken issueForSematticeService(String principalId,
                                                String ownerPrincipalId,
                                                String clientId,
                                                String tenantId,
                                                String companyId,
                                                List<String> requestedScopes,
                                                String delegatedByPrincipalId,
                                                String delegationPolicy) {
        return issueForSematticeServiceInternal(principalId, ownerPrincipalId, clientId, tenantId, companyId,
                requestedScopes, delegatedByPrincipalId, delegationPolicy, "ACTIVE");
    }

    public IssuedToken issueForSematticeServiceProjection(String principalId,
                                                          String ownerPrincipalId,
                                                          String clientId,
                                                          String tenantId,
                                                          String companyId,
                                                          String lifecycleStatus) {
        return issueForSematticeServiceInternal(principalId, ownerPrincipalId, clientId, tenantId, companyId,
                List.of("identity.principal.sync"), null, null, lifecycleStatus);
    }

    private IssuedToken issueForSematticeServiceInternal(String principalId,
                                                          String ownerPrincipalId,
                                                          String clientId,
                                                          String tenantId,
                                                          String companyId,
                                                          List<String> requestedScopes,
                                                          String delegatedByPrincipalId,
                                                          String delegationPolicy,
                                                          String lifecycleStatus) {
        requireEnabled();
        requireUuid(principalId, "service principal");
        requireUuid(ownerPrincipalId, "service owner");
        if (!hasText(clientId) || !clientId.matches("^[a-z0-9][a-z0-9-]{2,127}$")) {
            throw new ForbiddenException("机器账户客户端标识无效");
        }
        if (!hasText(tenantId) || !hasText(companyId)) {
            throw new ForbiddenException("机器账户缺少数据平台租户绑定");
        }
        List<String> issuedScopes = normalizeScopes(requestedScopes);
        if (issuedScopes.isEmpty() || issuedScopes.stream().anyMatch(scope -> !sematticeServiceScopes.contains(scope))) {
            throw new ForbiddenException("机器账户 scope 未获官方应用授权");
        }
        String lifecycle = hasText(lifecycleStatus) ? lifecycleStatus.trim().toUpperCase() : "ACTIVE";
        if ("REVOKED".equals(lifecycle)) lifecycle = "DISABLED";
        if (!List.of("ACTIVE", "SUSPENDED", "DISABLED").contains(lifecycle)) {
            throw new ForbiddenException("机器账户生命周期状态无效");
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(ttlSeconds);
        JwtBuilder builder = Jwts.builder()
                .header().keyId(keyId).and()
                .issuer(issuer)
                .subject(principalId)
                .audience().add(SEMATTICE_AUDIENCE).and()
                .claim("tenant_id", tenantId)
                .claim("company_id", companyId)
                .claim("principal_id", principalId)
                .claim("principal_type", "SERVICE")
                .claim("owner_principal_id", ownerPrincipalId)
                .claim("client_id", clientId)
                .claim("lifecycle_status", lifecycle)
                .claim("scope", String.join(" ", issuedScopes))
                .claim("actor_type", "service")
                .claim("authorized_party", "agentcici");
        if (hasText(delegatedByPrincipalId)) {
            requireUuid(delegatedByPrincipalId, "delegating principal");
            builder.claim("delegated_by_principal_id", delegatedByPrincipalId);
        }
        if (hasText(delegationPolicy)) {
            builder.claim("delegation_policy", delegationPolicy.trim());
        }
        String token = builder
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
        return new IssuedToken(token, expiresAt, tenantId, companyId, issuedScopes);
    }

    private IssuedToken issueForSemattice(UserEntity member, List<String> issuedScopes) {
        return issueForSemattice(member, issuedScopes, List.of(), true);
    }

    private IssuedToken issueForSemattice(UserEntity member, List<String> issuedScopes, List<String> approvals) {
        return issueForSemattice(member, issuedScopes, approvals, true);
    }

    private IssuedToken issueForSemattice(UserEntity member, List<String> issuedScopes, List<String> approvals,
                                          boolean requireUnifiedIdentity) {
        requireEnabled();
        if (!UserEntity.STATUS_ACTIVE.equals(member.getMemberStatus())
                || !"ACTIVE".equalsIgnoreCase(member.getCompany().getStatus())) {
            throw new ForbiddenException("当前成员或公司不可访问数据平台");
        }
        AccountExternalIdentityEntity identity = identityRepository.findByAccount_Id(member.getAccountId()).orElse(null);
        if (requireUnifiedIdentity && identity == null) {
            throw new ForbiddenException("当前账号尚未绑定统一身份");
        }
        SematticeProvisioningBindingEntity binding = bindingRepository.findByCompanyId(member.getCompany().getId())
                .filter(value -> SematticeProvisioningBindingEntity.PROVISIONED.equals(value.getState()))
                .filter(value -> hasText(value.getSematticeTenantId()))
                .orElseThrow(() -> new ForbiddenException("当前公司尚未开通数据平台"));

        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(ttlSeconds);
        String membershipVersion = membershipVersion(member);
        JwtBuilder builder = Jwts.builder()
                .header().keyId(keyId).and()
                .issuer(issuer)
                // The official token subject is the immutable AgentCiCi Principal. The
                // Keycloak subject remains an identity attribute, never a resource actor ID.
                .subject(member.getAccountId())
                .audience().add(SEMATTICE_AUDIENCE).and()
                .claim("tenant_id", binding.getSematticeTenantId())
                .claim("company_id", member.getCompany().getId())
                .claim("principal_id", member.getAccountId())
                .claim("principal_type", "HUMAN")
                .claim("member_id", member.getId())
                .claim("account_id", member.getAccountId())
                .claim("roles", List.of(member.getRoleCode()))
                .claim("scope", String.join(" ", issuedScopes))
                .claim("actor_type", "human")
                .claim("authorized_party", "agentcici")
                .claim("membership_version", membershipVersion)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt));
        if (identity != null) {
            builder.claim("keycloak_subject", identity.getSubject());
        }
        List<String> verifiedApprovals = approvals == null ? List.of() : approvals.stream().filter(OfficialAccessTokenService::hasText).distinct().toList();
        if (!verifiedApprovals.isEmpty()) {
            builder.claim("approvals", verifiedApprovals);
        }
        String token = builder.signWith(privateKey, Jwts.SIG.RS256).compact();
        return new IssuedToken(token, expiresAt, binding.getSematticeTenantId(), member.getCompany().getId(), issuedScopes);
    }

    public Map<String, Object> jwks() {
        requireEnabled();
        if (!(privateKey instanceof RSAPrivateCrtKey rsaPrivateKey)) {
            throw new IllegalStateException("Official access signing key is not RSA CRT key");
        }
        Map<String, Object> key = new LinkedHashMap<>();
        key.put("kty", "RSA");
        key.put("kid", keyId);
        key.put("use", "sig");
        key.put("alg", "RS256");
        key.put("n", base64UrlUnsigned(rsaPrivateKey.getModulus()));
        key.put("e", base64UrlUnsigned(rsaPrivateKey.getPublicExponent()));
        return Map.of("keys", List.of(key));
    }

    /**
     * Verifies the short-lived OACT presented back to AgentCiCi by DevAutopilot.
     * This is deliberately separate from the application-session JWT parser: OACT is RS256,
     * audience-bound and only accepted at the dedicated activation resolve boundary.
     */
    public VerifiedContext verifyDevAutopilotContext(String token) {
        requireEnabled();
        if (!hasText(token)) {
            throw new ForbiddenException("DevAutopilot 官方访问令牌缺失");
        }
        try {
            var signed = Jwts.parser().verifyWith(verificationKey()).build().parseSignedClaims(token.trim());
            Claims claims = signed.getPayload();
            if (!keyId.equals(signed.getHeader().getKeyId())
                    || !issuer.equals(claims.getIssuer())
                    || claims.getAudience() == null
                    || !claims.getAudience().contains(SEMATTICE_AUDIENCE)
                    || !"agentcici".equals(claims.get("authorized_party", String.class))) {
                throw new ForbiddenException("DevAutopilot 官方访问令牌不受信");
            }
            String companyId = trim(claims.get("company_id", String.class));
            String tenantId = trim(claims.get("tenant_id", String.class));
            String principalId = trim(claims.get("principal_id", String.class));
            String principalType = trim(claims.get("principal_type", String.class));
            if (!hasText(companyId) || !hasText(tenantId) || !hasText(principalId)
                    || !("HUMAN".equals(principalType) || "SERVICE".equals(principalType))) {
                throw new ForbiddenException("DevAutopilot 官方访问令牌上下文不完整");
            }
            return new VerifiedContext(companyId, tenantId, principalId, principalType);
        } catch (ForbiddenException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ForbiddenException("DevAutopilot 官方访问令牌无效或已过期");
        }
    }

    private PublicKey verificationKey() {
        try {
            if (!(privateKey instanceof RSAPrivateCrtKey rsaPrivateKey)) {
                throw new IllegalStateException("Official access signing key is not RSA CRT key");
            }
            return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
                    rsaPrivateKey.getModulus(), rsaPrivateKey.getPublicExponent()));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to derive official access verification key", ex);
        }
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new ForbiddenException("官方应用访问令牌尚未启用");
        }
    }

    private static PrivateKey parseRsaPrivateKey(String encoded) {
        try {
            String value = trim(encoded);
            if (value.isBlank()) {
                throw new IllegalArgumentException("Official access private key is required when enabled");
            }
            byte[] bytes = Base64.getDecoder().decode(value);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(bytes));
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Official access private key is invalid", ex);
        }
    }

    private static List<String> normalizeScopes(List<String> configured) {
        if (configured == null) {
            return List.of();
        }
        return configured.stream().map(OfficialAccessTokenService::trim).filter(value -> !value.isBlank()).distinct().toList();
    }

    private List<String> sematticeConsoleScopesFor(UserEntity member) {
        if (!RoleCodes.isOrgAdminRole(member.getRoleCode()) || sematticeScopes.contains("audit.read")) {
            return sematticeScopes;
        }
        return java.util.stream.Stream.concat(sematticeScopes.stream(), java.util.stream.Stream.of("audit.read"))
                .distinct()
                .toList();
    }

    private static String membershipVersion(UserEntity member) {
        try {
            String source = member.getId() + "|" + member.getCompany().getId() + "|" + member.getRoleCode() + "|" + member.getMemberStatus();
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to calculate membership version", ex);
        }
    }

    private static String base64UrlUnsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        int first = bytes.length > 1 && bytes[0] == 0 ? 1 : 0;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(java.util.Arrays.copyOfRange(bytes, first, bytes.length));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static void requireUuid(String value, String label) {
        try {
            UUID.fromString(value);
        } catch (Exception ex) {
            throw new ForbiddenException(label + " 标识无效");
        }
    }

    public record IssuedToken(String token,
                              Instant expiresAt,
                              String tenantId,
                              String companyId,
                              List<String> scopes) {
    }

    public record VerifiedContext(String companyId, String tenantId, String principalId, String principalType) {
    }

}
