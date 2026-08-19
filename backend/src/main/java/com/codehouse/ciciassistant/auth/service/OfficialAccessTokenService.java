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

    public static final String AGENTCICI_AUDIENCE = "agentcici-api";
    public static final String DEVAUTOPILOT_AUDIENCE = "devautopilot-api";
    public static final String SEMATTICE_AUDIENCE = "semattice-api";
    public static final String ECOSYSTEM_USER_TOKEN_TYPE = "ecosystem_user";
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
    private final long ecosystemTtlSeconds;

    public OfficialAccessTokenService(AccountExternalIdentityRepository identityRepository,
                                      SematticeProvisioningBindingRepository bindingRepository,
                                      SematticeMetadataApprovalService metadataApprovalService,
                                      @Value("${app.auth.official-access.enabled:false}") boolean enabled,
                                      @Value("${app.auth.official-access.issuer:}") String issuer,
                                      @Value("${app.auth.official-access.key-id:}") String keyId,
                                      @Value("${app.auth.official-access.private-key-pkcs8-base64:}") String privateKeyBase64,
                                      @Value("${app.auth.official-access.semattice-scopes:}") List<String> sematticeScopes,
                                      @Value("${app.auth.official-access.semattice-service-scopes:}") List<String> sematticeServiceScopes,
                                      @Value("${app.auth.official-access.ttl-seconds:600}") long ttlSeconds,
                                      @Value("${app.auth.ecosystem-access.ttl-seconds:7200}") long ecosystemTtlSeconds) {
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
        if (ecosystemTtlSeconds < 900 || ecosystemTtlSeconds > 43200) {
            throw new IllegalArgumentException("Ecosystem user token TTL must be between 900 and 43200 seconds");
        }
        this.ecosystemTtlSeconds = ecosystemTtlSeconds;
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

    /**
     * Signs the shared HUMAN session token used by AgentCiCi and its activated internal apps.
     * Resource applications still enforce their own audience and authorization policies.
     */
    public IssuedToken issueEcosystemUserToken(UserEntity member,
                                               List<String> roles,
                                               Map<String, Object> additionalClaims) {
        requireEnabled();
        if (!UserEntity.STATUS_ACTIVE.equals(member.getMemberStatus())
                || !"ACTIVE".equalsIgnoreCase(member.getCompany().getStatus())) {
            throw new ForbiddenException("当前成员或公司不可登录内部应用");
        }
        AccountExternalIdentityEntity identity = identityRepository.findByAccount_Id(member.getAccountId()).orElse(null);
        SematticeProvisioningBindingEntity binding = bindingRepository.findByCompanyId(member.getCompany().getId())
                .filter(value -> SematticeProvisioningBindingEntity.PROVISIONED.equals(value.getState()))
                .filter(value -> hasText(value.getSematticeTenantId()))
                .orElse(null);

        List<String> issuedScopes = new ArrayList<>();
        if (binding != null) {
            issuedScopes.addAll(sematticeScopes);
            if (!issuedScopes.contains(IDENTITY_PRINCIPAL_READ_SCOPE)) {
                issuedScopes.add(IDENTITY_PRINCIPAL_READ_SCOPE);
            }
        }
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(ecosystemTtlSeconds);
        JwtBuilder builder = Jwts.builder()
                .header().keyId(keyId).and()
                .issuer(issuer)
                .subject(member.getAccountId())
                .audience().add(AGENTCICI_AUDIENCE).add(DEVAUTOPILOT_AUDIENCE).and()
                .claim("typ", ECOSYSTEM_USER_TOKEN_TYPE)
                .claim("company_id", member.getCompany().getId())
                .claim("principal_id", member.getAccountId())
                .claim("principal_type", "HUMAN")
                .claim("member_id", member.getId())
                .claim("account_id", member.getAccountId())
                .claim("roles", roles == null || roles.isEmpty() ? List.of(member.getRoleCode()) : List.copyOf(roles))
                .claim("scope", String.join(" ", issuedScopes))
                .claim("actor_type", "human")
                .claim("authorized_party", "agentcici")
                .claim("membership_version", membershipVersion(member))
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt));
        if (binding != null) {
            builder.audience().add(SEMATTICE_AUDIENCE).and()
                    .claim("tenant_id", binding.getSematticeTenantId());
        }
        if (identity != null) {
            builder.claim("keycloak_subject", identity.getSubject());
        }
        if (additionalClaims != null) {
            additionalClaims.forEach((key, value) -> {
                if (hasText(key) && value != null && !isReservedEcosystemClaim(key)) {
                    builder.claim(key, value);
                }
            });
        }
        String token = builder.signWith(privateKey, Jwts.SIG.RS256).compact();
        return new IssuedToken(token, expiresAt,
                binding == null ? "" : binding.getSematticeTenantId(),
                member.getCompany().getId(), List.copyOf(issuedScopes));
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
                                                Integer maxInstances) {
        return issueForSematticeServiceInternal(principalId, ownerPrincipalId, clientId, tenantId, companyId,
                requestedScopes, null, null, "ACTIVE", maxInstances);
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
                requestedScopes, delegatedByPrincipalId, delegationPolicy, "ACTIVE", null);
    }

    public IssuedToken issueForSematticeServiceProjection(String principalId,
                                                          String ownerPrincipalId,
                                                          String clientId,
                                                          String tenantId,
                                                          String companyId,
                                                          String lifecycleStatus) {
        return issueForSematticeServiceInternal(principalId, ownerPrincipalId, clientId, tenantId, companyId,
                List.of("identity.principal.sync"), null, null, lifecycleStatus, null);
    }

    private IssuedToken issueForSematticeServiceInternal(String principalId,
                                                          String ownerPrincipalId,
                                                          String clientId,
                                                          String tenantId,
                                                          String companyId,
                                                          List<String> requestedScopes,
                                                          String delegatedByPrincipalId,
                                                          String delegationPolicy,
                                                          String lifecycleStatus,
                                                          Integer maxInstances) {
        requireEnabled();
        requireUuid(principalId, "service principal");
        String sematticeOwnerPrincipalId = sematticePrincipalId(ownerPrincipalId);
        requireUuid(sematticeOwnerPrincipalId, "service owner");
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
        if (maxInstances != null && (maxInstances < 1 || maxInstances > 64)) {
            throw new ForbiddenException("机器开发者实例上限无效");
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
                .claim("owner_principal_id", sematticeOwnerPrincipalId)
                .claim("client_id", clientId)
                .claim("lifecycle_status", lifecycle)
                .claim("scope", String.join(" ", issuedScopes))
                .claim("actor_type", "service")
                .claim("authorized_party", "agentcici");
        if (maxInstances != null) {
            builder.claim("max_instances", maxInstances);
        }
        if (hasText(delegatedByPrincipalId)) {
            String sematticeDelegatingPrincipalId = sematticePrincipalId(delegatedByPrincipalId);
            requireUuid(sematticeDelegatingPrincipalId, "delegating principal");
            builder.claim("delegated_by_principal_id", sematticeDelegatingPrincipalId);
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
        String sematticePrincipalId = sematticePrincipalId(member.getAccountId());
        JwtBuilder builder = Jwts.builder()
                .header().keyId(keyId).and()
                .issuer(issuer)
                // The official token subject is the immutable AgentCiCi Principal. The
                // Keycloak subject remains an identity attribute, never a resource actor ID.
                .subject(sematticePrincipalId)
                .audience().add(SEMATTICE_AUDIENCE).and()
                .claim("tenant_id", binding.getSematticeTenantId())
                .claim("company_id", member.getCompany().getId())
                .claim("principal_id", sematticePrincipalId)
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

    /**
     * Semattice's governed identity contract requires UUID principal IDs. Current AgentCiCi
     * accounts already satisfy that contract; legacy opaque account IDs receive a stable UUID
     * projection without mutating their local primary keys or tenant business data.
     */
    public static String sematticePrincipalId(String agentCiciPrincipalId) {
        String normalized = trim(agentCiciPrincipalId);
        if (normalized.isBlank()) {
            throw new ForbiddenException("账号缺少可投影的 Principal 标识");
        }
        try {
            UUID parsed = UUID.fromString(normalized);
            if (parsed.getMostSignificantBits() != 0L || parsed.getLeastSignificantBits() != 0L) {
                return parsed.toString();
            }
        } catch (IllegalArgumentException ignored) {
            // Legacy opaque IDs are deterministically projected below.
        }
        return UUID.nameUUIDFromBytes(("agentcici-principal:" + normalized)
                .getBytes(StandardCharsets.UTF_8)).toString();
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

    public EcosystemUserContext verifyEcosystemUserContext(String token, String requiredAudience) {
        requireEnabled();
        if (!hasText(token) || !List.of(AGENTCICI_AUDIENCE, DEVAUTOPILOT_AUDIENCE).contains(requiredAudience)) {
            throw new ForbiddenException("内部应用用户令牌缺失或 audience 无效");
        }
        try {
            var signed = Jwts.parser().verifyWith(verificationKey()).build().parseSignedClaims(token.trim());
            Claims claims = signed.getPayload();
            if (!keyId.equals(signed.getHeader().getKeyId())
                    || !issuer.equals(claims.getIssuer())
                    || claims.getAudience() == null
                    || !claims.getAudience().contains(requiredAudience)
                    || !ECOSYSTEM_USER_TOKEN_TYPE.equals(claims.get("typ", String.class))
                    || !"agentcici".equals(claims.get("authorized_party", String.class))
                    || !"HUMAN".equals(claims.get("principal_type", String.class))) {
                throw new ForbiddenException("内部应用用户令牌不受信");
            }
            String companyId = trim(claims.get("company_id", String.class));
            String memberId = trim(claims.get("member_id", String.class));
            String accountId = trim(claims.get("account_id", String.class));
            String principalId = trim(claims.get("principal_id", String.class));
            if (!hasText(companyId) || !hasText(memberId) || !hasText(accountId)
                    || !accountId.equals(principalId) || !accountId.equals(claims.getSubject())) {
                throw new ForbiddenException("内部应用用户令牌上下文不完整");
            }
            return new EcosystemUserContext(companyId, memberId, accountId, extractStringList(claims.get("roles")));
        } catch (ForbiddenException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ForbiddenException("内部应用用户令牌无效或已过期");
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

    private static boolean isReservedEcosystemClaim(String key) {
        return List.of("iss", "sub", "aud", "exp", "iat", "jti", "typ", "company_id", "tenant_id",
                "principal_id", "principal_type", "member_id", "account_id", "roles", "scope",
                "actor_type", "authorized_party", "membership_version").contains(key);
    }

    private static List<String> extractStringList(Object raw) {
        if (!(raw instanceof List<?> values)) {
            return raw == null ? List.of() : List.of(raw.toString());
        }
        return values.stream().filter(java.util.Objects::nonNull).map(Object::toString).toList();
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

    public record EcosystemUserContext(String companyId, String memberId, String accountId, List<String> roles) {
    }

}
