package com.codehouse.ciciassistant.auth.service;

import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lifecycle authority for non-human principals.  Credentials are returned once and never stored. */
@Service
public class ServicePrincipalService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final KeycloakIdentityProvisioningService keycloak;
    private final List<String> sematticeAllowedScopes;

    public ServicePrincipalService(JdbcTemplate jdbcTemplate,
                                   UserRepository userRepository,
                                   KeycloakIdentityProvisioningService keycloak,
                                   @Value("${app.auth.official-access.semattice-scopes:}") List<String> sematticeAllowedScopes) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
        this.keycloak = keycloak;
        this.sematticeAllowedScopes = sematticeAllowedScopes == null ? List.of() : sematticeAllowedScopes.stream()
                .filter(scope -> scope != null && !scope.isBlank()).map(String::trim).distinct().toList();
    }

    @Transactional
    public Map<String, Object> create(String companyId, String actorMemberId, String displayName,
                                      String serviceKind, String audience, String requestedClientId, List<String> requestedScopes) {
        UserEntity owner = userRepository.findByIdAndCompany_Id(actorMemberId, companyId)
                .orElseThrow(() -> new ForbiddenException("机器账户必须由当前组织的人类成员负责"));
        if (!UserEntity.STATUS_ACTIVE.equals(owner.getMemberStatus())) {
            throw new ForbiddenException("机器账户责任人必须是有效的人类成员");
        }
        String name = required(displayName, "displayName");
        String kind = enumValue(serviceKind, "serviceKind", "OFFICIAL_APP", "THIRD_PARTY", "AUTOMATION", "SYSTEM");
        String targetAudience = required(audience, "audience");
        List<String> scopes = requireAllowedScopes(requestedScopes);
        String clientId = requestedClientId == null || requestedClientId.isBlank()
                ? "agentcici-" + randomSuffix(12).toLowerCase() : required(requestedClientId, "clientId");
        if (!clientId.matches("^[a-z0-9][a-z0-9-]{2,127}$")) {
            throw new IllegalArgumentException("clientId 只能由小写字母、数字和连字符组成");
        }
        KeycloakIdentityProvisioningService.ServiceClientCredentials credentials = keycloak
                .createServiceClient(clientId, targetAudience);
        String principalId = UUID.randomUUID().toString();
        String publicId = "S" + java.time.Year.now().getValue() + randomSuffix(8);
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO principal (id, principal_type, lifecycle_status, display_name, created_by_principal_id, created_at, updated_at)
                VALUES (?, 'SERVICE', 'ACTIVE', ?, ?, ?, ?)
                """, principalId, name, owner.getAccountId(), now, now);
        jdbcTemplate.update("""
                INSERT INTO service_principal (principal_id, public_id, service_kind, client_id, credential_mode, token_audience, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'CLIENT_SECRET', ?, ?, ?)
                """, principalId, publicId, kind, clientId, targetAudience, now, now);
        jdbcTemplate.update("""
                INSERT INTO principal_identity (id, principal_id, provider, identity_type, issuer, subject, keycloak_client_id, binding_status, created_at, updated_at, last_verified_at)
                VALUES (?, ?, 'KEYCLOAK', 'SERVICE_ACCOUNT', ?, ?, ?, 'ACTIVE', ?, ?, ?)
                """, UUID.randomUUID().toString(), principalId, keycloak.issuer(), credentials.subject(), clientId, now, now, now);
        jdbcTemplate.update("""
                INSERT INTO service_principal_owner (service_principal_id, owner_principal_id, company_member_id, owner_role, owner_status, assigned_at)
                VALUES (?, ?, ?, 'PRIMARY', 'ACTIVE', ?)
                """, principalId, owner.getAccountId(), owner.getId(), now);
        scopes.forEach(scope -> jdbcTemplate.update("""
                INSERT INTO service_principal_scope (service_principal_id, scope_code, created_at)
                VALUES (?, ?, ?)
                """, principalId, scope, now));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("principalId", principalId);
        result.put("publicId", publicId);
        result.put("clientId", clientId);
        result.put("clientSecret", credentials.clientSecret());
        result.put("tokenAudience", targetAudience);
        result.put("scopes", scopes);
        result.put("ownerMemberId", owner.getId());
        result.put("credentialNotice", "clientSecret 仅本次返回；请立即写入受管密钥库，系统不会保存或再次显示。");
        return result;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private List<String> requireAllowedScopes(List<String> requestedScopes) {
        List<String> scopes = requestedScopes == null ? List.of() : requestedScopes.stream()
                .filter(scope -> scope != null && !scope.isBlank()).map(String::trim).distinct().toList();
        if (scopes.isEmpty()) {
            throw new IllegalArgumentException("机器账户至少需要一个 scope");
        }
        if (sematticeAllowedScopes.isEmpty() || scopes.stream().anyMatch(scope -> !sematticeAllowedScopes.contains(scope))) {
            throw new ForbiddenException("机器账户申请了未授权的 Semattice scope");
        }
        return scopes;
    }

    private static String enumValue(String value, String field, String... allowed) {
        String normalized = required(value, field).toUpperCase();
        for (String candidate : allowed) {
            if (candidate.equals(normalized)) return normalized;
        }
        throw new IllegalArgumentException(field + " is invalid");
    }

    private static String randomSuffix(int length) {
        StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < length; i++) result.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        return result.toString();
    }
}
