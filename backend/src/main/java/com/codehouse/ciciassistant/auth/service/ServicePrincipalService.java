package com.codehouse.ciciassistant.auth.service;

import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.platform.service.PlatformAuditService;
import com.codehouse.ciciassistant.semattice.SematticePrincipalProjectionClient;
import java.security.SecureRandom;
import java.sql.Timestamp;
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
    private static final String RECORD_DELETE_SCOPE = "runtime.record.delete";
    private static final String RECORD_TRANSFER_SCOPE = "runtime.record.transfer";

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final KeycloakIdentityProvisioningService keycloak;
    private final PlatformAuditService audit;
    private final SematticePrincipalProjectionClient projections;
    private final List<String> sematticeAllowedScopes;

    public ServicePrincipalService(JdbcTemplate jdbcTemplate,
                                   UserRepository userRepository,
                                   KeycloakIdentityProvisioningService keycloak,
                                   PlatformAuditService audit,
                                   SematticePrincipalProjectionClient projections,
                                   @Value("${app.auth.official-access.semattice-scopes:}") List<String> sematticeHumanScopes,
                                   @Value("${app.auth.official-access.semattice-service-scopes:}") List<String> sematticeServiceScopes) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
        this.keycloak = keycloak;
        this.audit = audit;
        this.projections = projections;
        List<String> humanScopes = sematticeHumanScopes == null ? List.of() : sematticeHumanScopes.stream()
                .filter(scope -> scope != null && !scope.isBlank()).map(String::trim).distinct().toList();
        List<String> serviceScopes = sematticeServiceScopes == null ? List.of() : sematticeServiceScopes.stream()
                .filter(scope -> scope != null && !scope.isBlank()).map(String::trim).distinct().toList();
        this.sematticeAllowedScopes = serviceScopes.isEmpty() ? humanScopes : serviceScopes;
    }

    @Transactional
    public Map<String, Object> create(String companyId, String actorMemberId, String displayName,
                                      String serviceKind, String audience, String requestedClientId, List<String> requestedScopes) {
        return create(companyId, actorMemberId, actorMemberId, displayName, serviceKind, audience, requestedClientId, requestedScopes);
    }

    @Transactional
    public Map<String, Object> create(String companyId, String actorMemberId, String ownerMemberId, String displayName,
                                      String serviceKind, String audience, String requestedClientId, List<String> requestedScopes) {
        UserEntity actor = requireActiveMember(companyId, actorMemberId, "只有有效的人类成员可以创建机器账户");
        UserEntity owner = requireActiveMember(companyId, ownerMemberId, "机器账户责任人必须是同组织有效人类成员");
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
        Timestamp now = Timestamp.from(Instant.now());
        try {
            jdbcTemplate.update("""
                    INSERT INTO principal (id, principal_type, lifecycle_status, display_name, created_by_principal_id, created_at, updated_at)
                    VALUES (?, 'SERVICE', 'ACTIVE', ?, ?, ?, ?)
                    """, principalId, name, actor.getAccountId(), now, now);
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
            audit(companyId, actor.getAccountId(), "created", principalId, "机器账户已创建，密钥仅返回一次");
            projections.syncService(owner, principalId, name, publicId, clientId, "ACTIVE");
        } catch (RuntimeException persistenceFailure) {
            try {
                keycloak.deleteServiceClient(clientId);
            } catch (RuntimeException compensationFailure) {
                persistenceFailure.addSuppressed(compensationFailure);
            }
            throw persistenceFailure;
        }
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

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String companyId) {
        return jdbcTemplate.query("""
                SELECT p.id, p.display_name, p.lifecycle_status, sp.public_id, sp.service_kind,
                       sp.client_id, sp.token_audience, sp.last_rotated_at, sp.created_at,
                       owner.owner_principal_id, owner.company_member_id,
                       account.public_id AS owner_public_id, account.display_name AS owner_display_name,
                       EXISTS (
                           SELECT 1
                           FROM tenant_application_resource resource
                           JOIN tenant_application_activation activation ON activation.id=resource.activation_id
                           WHERE resource.external_id=sp.principal_id
                             AND resource.resource_type='SERVICE_PRINCIPAL'
                             AND resource.logical_role='product_manager'
                             AND activation.app_code='devautopilot'
                             AND activation.company_id=member.company_id
                       ) AS devautopilot_product_manager
                FROM service_principal sp
                JOIN principal p ON p.id = sp.principal_id
                JOIN LATERAL (
                    SELECT candidate.*
                    FROM service_principal_owner candidate
                    WHERE candidate.service_principal_id = sp.principal_id
                      AND candidate.owner_role = 'PRIMARY'
                    ORDER BY (candidate.owner_status = 'ACTIVE') DESC, candidate.assigned_at DESC
                    LIMIT 1
                ) owner ON TRUE
                JOIN company_member member ON member.id = owner.company_member_id
                JOIN user_account account ON account.id = owner.owner_principal_id
                WHERE member.company_id = ?
                ORDER BY sp.created_at, p.id
                """, (rs, rowNum) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            String principalId = rs.getString("id");
            item.put("principalId", principalId);
            item.put("publicId", rs.getString("public_id"));
            item.put("displayName", rs.getString("display_name"));
            item.put("principalType", "SERVICE");
            item.put("lifecycleStatus", rs.getString("lifecycle_status"));
            item.put("serviceKind", rs.getString("service_kind"));
            item.put("clientId", rs.getString("client_id"));
            item.put("tokenAudience", rs.getString("token_audience"));
            item.put("scopes", scopes(principalId));
            item.put("availableScopes", allowedScopesFor(rs.getBoolean("devautopilot_product_manager")));
            item.put("lastRotatedAt", toInstant(rs.getTimestamp("last_rotated_at")));
            item.put("createdAt", toInstant(rs.getTimestamp("created_at")));
            item.put("ownerPrincipalId", rs.getString("owner_principal_id"));
            item.put("ownerMemberId", rs.getString("company_member_id"));
            item.put("ownerPublicId", rs.getString("owner_public_id"));
            item.put("ownerDisplayName", rs.getString("owner_display_name"));
            return item;
        }, companyId);
    }

    @Transactional
    public Map<String, Object> rotateSecret(String companyId, String actorMemberId, String principalId) {
        UserEntity actor = requireActiveMember(companyId, actorMemberId, "只有有效的人类成员可以轮换机器账户密钥");
        GovernedService service = requireGoverned(companyId, principalId);
        if (!"ACTIVE".equals(service.lifecycleStatus())) {
            throw new ForbiddenException("只有有效机器账户可以轮换密钥");
        }
        String secret = keycloak.rotateServiceClientSecret(service.clientId());
        Instant occurredAt = Instant.now();
        Timestamp now = Timestamp.from(occurredAt);
        jdbcTemplate.update("UPDATE service_principal SET last_rotated_at=?, updated_at=? WHERE principal_id=?", now, now, principalId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("principalId", principalId);
        result.put("clientId", service.clientId());
        result.put("clientSecret", secret);
        result.put("lastRotatedAt", occurredAt);
        result.put("credentialNotice", "clientSecret 仅本次返回；旧密钥已失效，请立即更新受管密钥库。");
        audit(companyId, actor.getAccountId(), "credential_rotated", principalId, "机器账户密钥已轮换");
        return result;
    }

    @Transactional
    public Map<String, Object> renameClientId(String companyId, String actorMemberId, String principalId,
                                              String replacementClientId) {
        UserEntity actor = requireActiveMember(companyId, actorMemberId, "只有有效的人类成员可以更新机器账户 Client ID");
        GovernedService service = requireGoverned(companyId, principalId);
        if ("REVOKED".equals(service.lifecycleStatus())) {
            throw new ForbiddenException("已撤销机器账户不能更新 Client ID");
        }
        String replacement = required(replacementClientId, "clientId");
        if (!replacement.matches("^[a-z0-9][a-z0-9-]{2,127}$")) {
            throw new IllegalArgumentException("clientId 只能由小写字母、数字和连字符组成");
        }
        if (service.clientId().equals(replacement)) {
            return Map.of("principalId", principalId, "clientId", replacement, "changed", false);
        }
        Integer conflicts = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM service_principal WHERE client_id=? AND principal_id<>?",
                Integer.class, replacement, principalId);
        if (conflicts != null && conflicts > 0) {
            throw new IllegalArgumentException("Client ID 已被当前平台中的其他机器账户使用");
        }
        keycloak.renameServiceClient(service.clientId(), replacement);
        try {
            Timestamp now = Timestamp.from(Instant.now());
            jdbcTemplate.update("UPDATE service_principal SET client_id=?, updated_at=? WHERE principal_id=?",
                    replacement, now, principalId);
            jdbcTemplate.update("UPDATE principal_identity SET keycloak_client_id=?, updated_at=?, last_verified_at=? "
                            + "WHERE principal_id=? AND provider='KEYCLOAK' AND identity_type='SERVICE_ACCOUNT'",
                    replacement, now, now, principalId);
            audit(companyId, actor.getAccountId(), "client_id_renamed", principalId,
                    "机器账户 Client ID 已更新为 " + replacement);
            synchronizeProjectionSafely(companyId, principalId, actor.getAccountId());
        } catch (RuntimeException persistenceFailure) {
            try {
                keycloak.renameServiceClient(replacement, service.clientId());
            } catch (RuntimeException compensationFailure) {
                persistenceFailure.addSuppressed(compensationFailure);
            }
            throw persistenceFailure;
        }
        return Map.of("principalId", principalId, "clientId", replacement, "changed", true);
    }

    @Transactional
    public Map<String, Object> updateScopes(String companyId, String actorMemberId, String principalId,
                                            List<String> requestedScopes) {
        UserEntity actor = requireActiveMember(companyId, actorMemberId, "只有有效的人类成员可以调整机器账户授权范围");
        GovernedService service = requireGoverned(companyId, principalId);
        if ("REVOKED".equals(service.lifecycleStatus())) {
            throw new ForbiddenException("已撤销机器账户不能调整授权范围");
        }
        List<String> replacement = requireAllowedScopes(requestedScopes,
                allowedScopesFor(isDevAutopilotProductManager(companyId, principalId)));
        List<String> previous = scopes(principalId).stream().distinct().sorted().toList();
        if (previous.equals(replacement)) {
            return Map.of("principalId", principalId, "scopes", replacement, "changed", false);
        }
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("DELETE FROM service_principal_scope WHERE service_principal_id=?", principalId);
        replacement.forEach(scope -> jdbcTemplate.update("""
                INSERT INTO service_principal_scope (service_principal_id, scope_code, created_at)
                VALUES (?, ?, ?)
                """, principalId, scope, now));
        audit(companyId, actor.getAccountId(), "scopes_updated", principalId,
                "机器账户授权范围已从 [" + String.join(",", previous) + "] 更新为 [" + String.join(",", replacement) + "]");
        return Map.of("principalId", principalId, "scopes", replacement, "changed", true);
    }

    @Transactional
    public Map<String, Object> suspend(String companyId, String actorMemberId, String principalId) {
        UserEntity actor = requireActiveMember(companyId, actorMemberId, "只有有效的人类成员可以暂停机器账户");
        GovernedService service = requireGoverned(companyId, principalId);
        if ("REVOKED".equals(service.lifecycleStatus())) {
            throw new ForbiddenException("已撤销机器账户不能暂停");
        }
        if (!"SUSPENDED".equals(service.lifecycleStatus())) {
            keycloak.setServiceClientEnabled(service.clientId(), false);
            Timestamp now = Timestamp.from(Instant.now());
            jdbcTemplate.update("UPDATE principal SET lifecycle_status='SUSPENDED', suspended_at=?, updated_at=? WHERE id=?", now, now, principalId);
            updateApplicationResource(principalId, "SUSPENDED", null);
            audit(companyId, actor.getAccountId(), "suspended", principalId, "机器账户已暂停");
            synchronizeProjectionSafely(companyId, principalId, actor.getAccountId());
        }
        return requireView(companyId, principalId);
    }

    @Transactional
    public Map<String, Object> activate(String companyId, String actorMemberId, String principalId) {
        UserEntity actor = requireActiveMember(companyId, actorMemberId, "只有有效的人类成员可以恢复机器账户");
        GovernedService service = requireGoverned(companyId, principalId);
        if (!"SUSPENDED".equals(service.lifecycleStatus())) {
            throw new ForbiddenException("只有已暂停机器账户可以恢复");
        }
        keycloak.setServiceClientEnabled(service.clientId(), true);
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("UPDATE principal SET lifecycle_status='ACTIVE', suspended_at=NULL, updated_at=? WHERE id=?", now, principalId);
        updateApplicationResource(principalId, "ACTIVE", null);
        audit(companyId, actor.getAccountId(), "activated", principalId, "机器账户已恢复");
        synchronizeProjectionSafely(companyId, principalId, actor.getAccountId());
        return requireView(companyId, principalId);
    }

    @Transactional
    public Map<String, Object> revoke(String companyId, String actorMemberId, String principalId) {
        UserEntity actor = requireActiveMember(companyId, actorMemberId, "只有有效的人类成员可以撤销机器账户");
        GovernedService service = requireGoverned(companyId, principalId);
        if (!"REVOKED".equals(service.lifecycleStatus())) {
            keycloak.setServiceClientEnabled(service.clientId(), false);
            Timestamp now = Timestamp.from(Instant.now());
            jdbcTemplate.update("UPDATE principal SET lifecycle_status='REVOKED', suspended_at=NULL, revoked_at=?, updated_at=? WHERE id=?", now, now, principalId);
            jdbcTemplate.update("UPDATE principal_identity SET binding_status='REVOKED', updated_at=? WHERE principal_id=?", now, principalId);
            jdbcTemplate.update("UPDATE service_principal_owner SET owner_status='REVOKED', revoked_at=? WHERE service_principal_id=? AND owner_status<>'REVOKED'", now, principalId);
            updateApplicationResource(principalId, "REVOKED", null);
            audit(companyId, actor.getAccountId(), "revoked", principalId, "机器账户已永久撤销");
            synchronizeProjectionSafely(companyId, principalId, actor.getAccountId());
        }
        return requireView(companyId, principalId);
    }

    @Transactional
    public Map<String, Object> transferOwner(String companyId, String actorMemberId, String principalId, String newOwnerMemberId) {
        UserEntity actor = requireActiveMember(companyId, actorMemberId, "只有有效的人类成员可以移交机器账户负责人");
        UserEntity newOwner = requireActiveMember(companyId, newOwnerMemberId, "新负责人必须是同组织有效人类成员");
        GovernedService service = requireGoverned(companyId, principalId);
        if ("REVOKED".equals(service.lifecycleStatus())) {
            throw new ForbiddenException("已撤销机器账户不能移交负责人");
        }
        if (replacePrimaryOwner(principalId, newOwner)) {
            audit(companyId, actor.getAccountId(), "owner_transferred", principalId,
                    "机器账户负责人已移交给 " + newOwner.getAccountId());
        }
        synchronizeProjectionSafely(companyId, principalId, actor.getAccountId());
        return requireView(companyId, principalId);
    }

    @Transactional
    public Map<String, Object> updateProfile(String companyId, String actorMemberId, String principalId,
                                             String displayName, String ownerMemberId) {
        UserEntity actor = requireActiveMember(companyId, actorMemberId, "只有有效的人类成员可以编辑机器账户");
        UserEntity owner = requireActiveMember(companyId, ownerMemberId, "负责人必须是同组织有效人类成员");
        GovernedService service = requireGoverned(companyId, principalId);
        if ("REVOKED".equals(service.lifecycleStatus())) {
            throw new ForbiddenException("已撤销机器账户不能编辑");
        }
        String name = required(displayName, "displayName");
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("UPDATE principal SET display_name=?, updated_at=? WHERE id=?", name, now, principalId);
        updateApplicationResource(principalId, null, name);
        boolean ownerChanged = replacePrimaryOwner(principalId, owner);
        audit(companyId, actor.getAccountId(), "profile_updated", principalId,
                "机器账户显示名称已更新" + (ownerChanged ? "，负责人已更新为 " + owner.getAccountId() : ""));
        synchronizeProjectionSafely(companyId, principalId, actor.getAccountId());
        return requireView(companyId, principalId);
    }

    /** Strict synchronization is used by provisioning/reconciliation; lifecycle writes use the safe wrapper below. */
    public void synchronizeProjection(String companyId, String principalId, String actorPrincipalId) {
        Map<String, Object> principal = requireView(companyId, principalId);
        String ownerMemberId = String.valueOf(principal.get("ownerMemberId"));
        UserEntity owner = requireActiveMember(companyId, ownerMemberId, "机器账户责任人必须是同组织有效人类成员");
        projections.syncService(owner, principalId, String.valueOf(principal.get("displayName")),
                String.valueOf(principal.get("publicId")), String.valueOf(principal.get("clientId")),
                String.valueOf(principal.get("lifecycleStatus")));
    }

    private void synchronizeProjectionSafely(String companyId, String principalId, String actorPrincipalId) {
        try {
            synchronizeProjection(companyId, principalId, actorPrincipalId);
        } catch (RuntimeException exception) {
            audit(companyId, actorPrincipalId, "projection_sync_deferred", principalId,
                    "Semattice 主体投影同步已延后，将由租户应用初始化补偿");
        }
    }

    private void updateApplicationResource(String principalId, String lifecycleStatus, String displayName) {
        if (lifecycleStatus != null) {
            jdbcTemplate.update("UPDATE tenant_application_resource SET lifecycle_state=? WHERE external_id=? AND resource_type='SERVICE_PRINCIPAL'",
                    lifecycleStatus, principalId);
        }
        if (displayName != null) {
            jdbcTemplate.update("UPDATE tenant_application_resource SET display_name=? WHERE external_id=? AND resource_type='SERVICE_PRINCIPAL'",
                    displayName, principalId);
        }
    }

    private boolean replacePrimaryOwner(String principalId, UserEntity newOwner) {
        List<String> currentOwners = jdbcTemplate.queryForList("""
                SELECT company_member_id
                FROM service_principal_owner
                WHERE service_principal_id=? AND owner_role='PRIMARY' AND owner_status='ACTIVE'
                """, String.class, principalId);
        if (currentOwners.size() == 1 && newOwner.getId().equals(currentOwners.getFirst())) {
            return false;
        }
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                UPDATE service_principal_owner
                SET owner_status='REVOKED', revoked_at=?
                WHERE service_principal_id=? AND owner_role='PRIMARY' AND owner_status='ACTIVE'
                """, now, principalId);
        jdbcTemplate.update("""
                INSERT INTO service_principal_owner(service_principal_id,owner_principal_id,company_member_id,owner_role,owner_status,assigned_at)
                VALUES (?, ?, ?, 'PRIMARY', 'ACTIVE', ?)
                ON CONFLICT(service_principal_id,owner_principal_id,company_member_id) DO UPDATE
                SET owner_role='PRIMARY',owner_status='ACTIVE',assigned_at=excluded.assigned_at,revoked_at=NULL
                """, principalId, newOwner.getAccountId(), newOwner.getId(), now);
        return true;
    }

    private UserEntity requireActiveMember(String companyId, String memberId, String message) {
        UserEntity member = userRepository.findByIdAndCompany_Id(required(memberId, "memberId"), companyId)
                .orElseThrow(() -> new ForbiddenException(message));
        if (!UserEntity.STATUS_ACTIVE.equals(member.getMemberStatus())) {
            throw new ForbiddenException(message);
        }
        return member;
    }

    private GovernedService requireGoverned(String companyId, String principalId) {
        List<GovernedService> matches = jdbcTemplate.query("""
                SELECT sp.client_id,p.lifecycle_status
                FROM service_principal sp
                JOIN principal p ON p.id=sp.principal_id
                WHERE sp.principal_id=?
                  AND EXISTS (
                    SELECT 1 FROM service_principal_owner owner
                    JOIN company_member member ON member.id=owner.company_member_id
                    WHERE owner.service_principal_id=sp.principal_id AND member.company_id=?
                  )
                """, (rs, rowNum) -> new GovernedService(rs.getString("client_id"), rs.getString("lifecycle_status")),
                required(principalId, "principalId"), companyId);
        if (matches.size() != 1) {
            throw new ForbiddenException("机器账户不存在或不属于当前组织");
        }
        return matches.get(0);
    }

    private Map<String, Object> requireView(String companyId, String principalId) {
        return list(companyId).stream().filter(item -> principalId.equals(item.get("principalId"))).findFirst()
                .orElseThrow(() -> new ForbiddenException("机器账户不存在或不属于当前组织"));
    }

    private List<String> scopes(String principalId) {
        return jdbcTemplate.queryForList("""
                SELECT scope_code FROM service_principal_scope
                WHERE service_principal_id=? ORDER BY scope_code
                """, String.class, principalId);
    }

    private boolean isDevAutopilotProductManager(String companyId, String principalId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM tenant_application_resource resource
                JOIN tenant_application_activation activation ON activation.id=resource.activation_id
                WHERE resource.external_id=?
                  AND resource.resource_type='SERVICE_PRINCIPAL'
                  AND resource.logical_role='product_manager'
                  AND activation.app_code='devautopilot'
                  AND activation.company_id=?
                """, Integer.class, principalId, companyId);
        return count != null && count > 0;
    }

    private List<String> allowedScopesFor(boolean devAutopilotProductManager) {
        if (devAutopilotProductManager) return sematticeAllowedScopes;
        return sematticeAllowedScopes.stream().filter(scope -> !RECORD_DELETE_SCOPE.equals(scope) && !RECORD_TRANSFER_SCOPE.equals(scope)).toList();
    }

    private void audit(String companyId, String actorPrincipalId, String action, String principalId, String detail) {
        audit.log(companyId, actorPrincipalId, "ORG_ADMIN", "service_principal." + action,
                "service_principal", principalId, detail);
    }

    private static Instant toInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private record GovernedService(String clientId, String lifecycleStatus) {
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private List<String> requireAllowedScopes(List<String> requestedScopes) {
        return requireAllowedScopes(requestedScopes, sematticeAllowedScopes);
    }

    private List<String> requireAllowedScopes(List<String> requestedScopes, List<String> allowedScopes) {
        List<String> scopes = requestedScopes == null ? List.of() : requestedScopes.stream()
                .filter(scope -> scope != null && !scope.isBlank()).map(String::trim).distinct().sorted().toList();
        if (scopes.isEmpty()) {
            throw new IllegalArgumentException("机器账户至少需要一个 scope");
        }
        if (allowedScopes.isEmpty() || scopes.stream().anyMatch(scope -> !allowedScopes.contains(scope))) {
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
