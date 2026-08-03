package com.codehouse.ciciassistant.agent.service;

import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.platform.service.PlatformAuditService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the governed SERVICE principal that executes an Agent's resource calls.
 * The logged-in HUMAN is a delegator only and can never be used as a fallback actor.
 */
@Service
public class AgentServicePrincipalExecutionService {

    public static final String DELEGATION_PRIMARY_OWNER = "PRIMARY_OWNER";

    private final JdbcTemplate jdbcTemplate;
    private final OfficialAccessTokenService officialAccessTokens;
    private final PlatformAuditService audit;

    public AgentServicePrincipalExecutionService(JdbcTemplate jdbcTemplate,
                                                  OfficialAccessTokenService officialAccessTokens,
                                                  PlatformAuditService audit) {
        this.jdbcTemplate = jdbcTemplate;
        this.officialAccessTokens = officialAccessTokens;
        this.audit = audit;
    }

    public Optional<BindingView> findBinding(String companyId, String agentId) {
        List<BindingView> rows = jdbcTemplate.query("""
                SELECT binding.company_id,
                       binding.agent_id,
                       binding.service_principal_id,
                       binding.delegation_policy,
                       binding.enabled,
                       binding.configured_by_principal_id,
                       binding.created_at,
                       binding.updated_at,
                       principal.display_name,
                       principal.lifecycle_status,
                       service.public_id,
                       service.client_id,
                       service.token_audience,
                       owner.owner_principal_id
                FROM agent_service_principal_binding binding
                JOIN service_principal service
                  ON service.principal_id = binding.service_principal_id
                JOIN principal principal
                  ON principal.id = service.principal_id
                LEFT JOIN service_principal_owner owner
                  ON owner.service_principal_id = service.principal_id
                 AND owner.owner_role = 'PRIMARY'
                 AND owner.owner_status = 'ACTIVE'
                WHERE binding.company_id = ?
                  AND binding.agent_id = ?
                """, AgentServicePrincipalExecutionService::mapBinding, required(companyId, "companyId"), normalizeAgentId(agentId));
        if (rows.size() > 1) {
            throw new IncorrectResultSizeDataAccessException(1, rows.size());
        }
        return rows.stream().findFirst();
    }

    @Transactional
    public BindingView configure(String companyId,
                                 String agentId,
                                 String servicePrincipalId,
                                 boolean enabled,
                                 String actorMemberId) {
        String normalizedCompanyId = required(companyId, "companyId");
        String normalizedAgentId = normalizeAgentId(agentId);
        String normalizedPrincipalId = required(servicePrincipalId, "servicePrincipalId");
        String actorPrincipalId = resolveConfigurableOwner(
                normalizedCompanyId, normalizedAgentId, normalizedPrincipalId, required(actorMemberId, "actorMemberId"));
        jdbcTemplate.update("""
                INSERT INTO agent_service_principal_binding (
                    company_id, agent_id, service_principal_id, delegation_policy, enabled,
                    configured_by_principal_id, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (company_id, agent_id) DO UPDATE
                SET service_principal_id = EXCLUDED.service_principal_id,
                    delegation_policy = EXCLUDED.delegation_policy,
                    enabled = EXCLUDED.enabled,
                    configured_by_principal_id = EXCLUDED.configured_by_principal_id,
                    updated_at = EXCLUDED.updated_at
                """, normalizedCompanyId, normalizedAgentId, normalizedPrincipalId,
                DELEGATION_PRIMARY_OWNER, enabled, actorPrincipalId);
        audit.log(normalizedCompanyId, actorPrincipalId, "ORG_ADMIN",
                "agent.execution_principal.configured", "agent", normalizedAgentId,
                "SERVICE execution principal configured; enabled=" + enabled);
        return findBinding(normalizedCompanyId, normalizedAgentId)
                .orElseThrow(() -> new IllegalStateException("Agent execution principal binding was not persisted"));
    }

    /**
     * Issues a least-privilege SERVICE OACT for one Agent tool call after validating the human delegation chain.
     */
    public ExecutionAuthorization authorizeSemattice(String companyId,
                                                      String actorMemberId,
                                                      String agentId,
                                                      List<String> requiredScopes,
                                                      String purpose) {
        String normalizedCompanyId = required(companyId, "companyId");
        String normalizedAgentId = normalizeAgentId(agentId);
        String normalizedMemberId = required(actorMemberId, "actorMemberId");
        List<ServiceContext> contexts = jdbcTemplate.query("""
                SELECT binding.service_principal_id,
                       binding.delegation_policy,
                       service.client_id,
                       service_binding.semattice_tenant_id,
                       owner.owner_principal_id,
                       principal.display_name
                FROM agent_service_principal_binding binding
                JOIN agent_definition agent
                  ON agent.company_id = binding.company_id
                 AND agent.agent_id = binding.agent_id
                 AND agent.enabled = TRUE
                JOIN service_principal service
                  ON service.principal_id = binding.service_principal_id
                 AND service.token_audience = ?
                JOIN principal principal
                  ON principal.id = service.principal_id
                 AND principal.principal_type = 'SERVICE'
                 AND principal.lifecycle_status = 'ACTIVE'
                JOIN principal_identity identity_record
                  ON identity_record.principal_id = service.principal_id
                 AND identity_record.provider = 'KEYCLOAK'
                 AND identity_record.identity_type = 'SERVICE_ACCOUNT'
                 AND identity_record.binding_status = 'ACTIVE'
                 AND identity_record.keycloak_client_id = service.client_id
                JOIN service_principal_owner owner
                  ON owner.service_principal_id = service.principal_id
                 AND owner.owner_role = 'PRIMARY'
                 AND owner.owner_status = 'ACTIVE'
                JOIN company_member member
                  ON member.id = ?
                 AND member.id = owner.company_member_id
                 AND member.account_id = owner.owner_principal_id
                 AND member.company_id = binding.company_id
                 AND member.member_status = 'ACTIVE'
                JOIN company company
                  ON company.id = binding.company_id
                 AND company.status = 'ACTIVE'
                JOIN semattice_provisioning_binding service_binding
                  ON service_binding.company_id = binding.company_id
                 AND service_binding.state = 'PROVISIONED'
                 AND service_binding.semattice_tenant_id IS NOT NULL
                WHERE binding.company_id = ?
                  AND binding.agent_id = ?
                  AND binding.enabled = TRUE
                  AND binding.delegation_policy = 'PRIMARY_OWNER'
                """, (rs, rowNum) -> new ServiceContext(
                        rs.getString("service_principal_id"),
                        rs.getString("delegation_policy"),
                        rs.getString("client_id"),
                        rs.getString("semattice_tenant_id"),
                        rs.getString("owner_principal_id"),
                        rs.getString("display_name")),
                OfficialAccessTokenService.SEMATTICE_AUDIENCE,
                normalizedMemberId,
                normalizedCompanyId,
                normalizedAgentId);
        if (contexts.size() != 1) {
            throw new ForbiddenException("智能体未绑定可用的机器执行身份，或当前用户无委托权限");
        }
        ServiceContext context = contexts.getFirst();
        List<String> normalizedScopes = normalizeScopes(requiredScopes);
        List<String> grantedScopes = jdbcTemplate.queryForList("""
                SELECT scope_code
                FROM service_principal_scope
                WHERE service_principal_id = ?
                ORDER BY scope_code
                """, String.class, context.servicePrincipalId());
        if (normalizedScopes.isEmpty() || !grantedScopes.containsAll(normalizedScopes)) {
            throw new ForbiddenException("机器执行身份缺少本次操作所需的 Semattice scope");
        }
        OfficialAccessTokenService.IssuedToken token = officialAccessTokens.issueForSematticeService(
                context.servicePrincipalId(), context.ownerPrincipalId(), context.clientId(),
                context.tenantId(), normalizedCompanyId, normalizedScopes,
                context.ownerPrincipalId(), context.delegationPolicy());
        audit.log(normalizedCompanyId, context.ownerPrincipalId(), "SERVICE_DELEGATOR",
                "agent.service_principal.delegated", "service_principal", context.servicePrincipalId(),
                "agent=" + normalizedAgentId + "; purpose=" + safePurpose(purpose)
                        + "; delegationPolicy=" + context.delegationPolicy());
        return new ExecutionAuthorization(
                context.servicePrincipalId(),
                context.displayName(),
                context.ownerPrincipalId(),
                context.delegationPolicy(),
                token);
    }

    private String resolveConfigurableOwner(String companyId,
                                            String agentId,
                                            String servicePrincipalId,
                                            String actorMemberId) {
        List<String> owners = jdbcTemplate.queryForList("""
                SELECT owner.owner_principal_id
                FROM agent_definition agent
                JOIN service_principal service
                  ON service.principal_id = ?
                 AND service.token_audience = ?
                JOIN principal principal
                  ON principal.id = service.principal_id
                 AND principal.principal_type = 'SERVICE'
                 AND principal.lifecycle_status = 'ACTIVE'
                JOIN service_principal_owner owner
                  ON owner.service_principal_id = service.principal_id
                 AND owner.owner_role = 'PRIMARY'
                 AND owner.owner_status = 'ACTIVE'
                JOIN company_member member
                  ON member.id = ?
                 AND member.id = owner.company_member_id
                 AND member.account_id = owner.owner_principal_id
                 AND member.company_id = agent.company_id
                 AND member.member_status = 'ACTIVE'
                WHERE agent.company_id = ?
                  AND agent.agent_id = ?
                  AND agent.enabled = TRUE
                """, String.class,
                servicePrincipalId,
                OfficialAccessTokenService.SEMATTICE_AUDIENCE,
                actorMemberId,
                companyId,
                agentId);
        if (owners.size() != 1) {
            throw new ForbiddenException("只能绑定由当前用户负责的有效机器身份");
        }
        return owners.getFirst();
    }

    private static BindingView mapBinding(ResultSet rs, int rowNum) throws SQLException {
        return new BindingView(
                rs.getString("company_id"),
                rs.getString("agent_id"),
                rs.getString("service_principal_id"),
                rs.getString("public_id"),
                rs.getString("display_name"),
                rs.getString("client_id"),
                rs.getString("token_audience"),
                rs.getString("lifecycle_status"),
                rs.getString("owner_principal_id"),
                rs.getString("delegation_policy"),
                rs.getBoolean("enabled"),
                rs.getString("configured_by_principal_id"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")));
    }

    private static List<String> normalizeScopes(List<String> scopes) {
        return scopes == null ? List.of() : scopes.stream()
                .filter(scope -> scope != null && !scope.isBlank())
                .map(scope -> scope.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private static String normalizeAgentId(String value) {
        return required(value, "agentId").toLowerCase(Locale.ROOT);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static String safePurpose(String value) {
        String normalized = value == null ? "unspecified" : value.trim();
        return normalized.matches("^[a-z0-9_.-]{1,128}$") ? normalized : "unspecified";
    }

    private static Instant toInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private record ServiceContext(String servicePrincipalId,
                                  String delegationPolicy,
                                  String clientId,
                                  String tenantId,
                                  String ownerPrincipalId,
                                  String displayName) {
    }

    public record BindingView(String companyId,
                              String agentId,
                              String servicePrincipalId,
                              String servicePrincipalPublicId,
                              String displayName,
                              String clientId,
                              String tokenAudience,
                              String lifecycleStatus,
                              String ownerPrincipalId,
                              String delegationPolicy,
                              boolean enabled,
                              String configuredByPrincipalId,
                              Instant createdAt,
                              Instant updatedAt) {
    }

    public record ExecutionAuthorization(String servicePrincipalId,
                                         String servicePrincipalDisplayName,
                                         String ownerPrincipalId,
                                         String delegationPolicy,
                                         OfficialAccessTokenService.IssuedToken token) {
    }
}
