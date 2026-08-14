package com.codehouse.ciciassistant.agent.service;

import com.codehouse.ciciassistant.agent.domain.AgentPermission;
import com.codehouse.ciciassistant.auth.RoleCodes;
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
    public static final String DELEGATION_TENANT_APP_ROLE = "TENANT_APP_ROLE";
    public static final String APP_ROLE_VIEWER = "VIEWER";
    public static final String APP_ROLE_CONTRIBUTOR = "CONTRIBUTOR";
    public static final String APP_ROLE_REVIEWER = "REVIEWER";
    public static final String APP_ROLE_ADMIN = "APP_ADMIN";

    private final JdbcTemplate jdbcTemplate;
    private final OfficialAccessTokenService officialAccessTokens;
    private final PlatformAuditService audit;
    private final AgentAccessControlService accessControl;

    public AgentServicePrincipalExecutionService(JdbcTemplate jdbcTemplate,
                                                  OfficialAccessTokenService officialAccessTokens,
                                                  PlatformAuditService audit,
                                                  AgentAccessControlService accessControl) {
        this.jdbcTemplate = jdbcTemplate;
        this.officialAccessTokens = officialAccessTokens;
        this.audit = audit;
        this.accessControl = accessControl;
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
        String delegationPolicy = resolveDelegationPolicy(normalizedCompanyId, normalizedAgentId);
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
                delegationPolicy, enabled, actorPrincipalId);
        audit.log(normalizedCompanyId, actorPrincipalId, "ORG_ADMIN",
                "agent.execution_principal.configured", "agent", normalizedAgentId,
                "SERVICE execution principal configured; enabled=" + enabled + "; delegationPolicy=" + delegationPolicy);
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
        List<ServiceContext> contexts = healthyServiceContexts(normalizedCompanyId, normalizedAgentId);
        if (contexts.size() != 1) {
            throw new ForbiddenException("智能体机器执行身份不可用，请联系租户管理员检查主体状态与 Semattice 连接");
        }
        ServiceContext context = contexts.getFirst();
        ActorContext actor = requireActor(normalizedCompanyId, normalizedMemberId);
        AppAccess access = resolveAppAccess(normalizedCompanyId, normalizedAgentId, context, actor);
        if (!access.allowed()) {
            throw new ForbiddenException(access.message());
        }
        String requiredRole = requiredAppRole(requiredScopes, purpose);
        if (!roleAllows(access.effectiveRole(), requiredRole)) {
            throw new ForbiddenException("本次操作需要 DevAutopilot " + requiredRole + " 权限，当前角色为 " + access.effectiveRole());
        }
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
                actor.accountPrincipalId(), context.delegationPolicy());
        audit.log(normalizedCompanyId, actor.accountPrincipalId(), actor.roleCode(),
                "agent.service_principal.delegated", "service_principal", context.servicePrincipalId(),
                "agent=" + normalizedAgentId + "; purpose=" + safePurpose(purpose)
                        + "; delegationPolicy=" + context.delegationPolicy()
                        + "; appRole=" + access.effectiveRole()
                        + "; ownerPrincipalId=" + context.ownerPrincipalId());
        return new ExecutionAuthorization(
                context.servicePrincipalId(),
                context.displayName(),
                context.ownerPrincipalId(),
                actor.accountPrincipalId(),
                context.delegationPolicy(),
                access.effectiveRole(),
                token);
    }

    /** Lightweight projection used by the Agent list before the user starts a conversation. */
    public ExecutionAccessView executionAccess(String companyId, String actorMemberId, String agentId) {
        String normalizedCompanyId = required(companyId, "companyId");
        String normalizedAgentId = normalizeAgentId(agentId);
        Optional<BindingView> binding = findBinding(normalizedCompanyId, normalizedAgentId);
        if (binding.isEmpty()) {
            return new ExecutionAccessView(false, true, "NOT_REQUIRED", "NONE", "此智能体不需要机器执行身份");
        }
        List<ServiceContext> contexts = healthyServiceContexts(normalizedCompanyId, normalizedAgentId);
        if (contexts.size() != 1) {
            return new ExecutionAccessView(true, false, "EXECUTION_IDENTITY_UNAVAILABLE", "NONE",
                    "机器执行身份不可用，请联系租户管理员检查主体状态与 Semattice 连接");
        }
        List<ActorContext> actors = actorContexts(normalizedCompanyId, actorMemberId);
        if (actors.size() != 1) {
            return new ExecutionAccessView(true, false, "MEMBER_INACTIVE", "NONE", "当前成员已停用或不属于此租户");
        }
        AppAccess access = resolveAppAccess(normalizedCompanyId, normalizedAgentId, contexts.getFirst(), actors.getFirst());
        return new ExecutionAccessView(true, access.allowed(), access.reasonCode(), access.effectiveRole(), access.message());
    }

    private List<ServiceContext> healthyServiceContexts(String companyId, String agentId) {
        return jdbcTemplate.query("""
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
                JOIN company_member owner_member
                  ON owner_member.id = owner.company_member_id
                 AND owner_member.account_id = owner.owner_principal_id
                 AND owner_member.company_id = binding.company_id
                 AND owner_member.member_status = 'ACTIVE'
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
                  AND binding.delegation_policy IN ('PRIMARY_OWNER', 'TENANT_APP_ROLE')
                """, (rs, rowNum) -> new ServiceContext(
                        rs.getString("service_principal_id"),
                        rs.getString("delegation_policy"),
                        rs.getString("client_id"),
                        rs.getString("semattice_tenant_id"),
                        rs.getString("owner_principal_id"),
                        rs.getString("display_name")),
                OfficialAccessTokenService.SEMATTICE_AUDIENCE,
                companyId,
                agentId);
    }

    private ActorContext requireActor(String companyId, String memberId) {
        List<ActorContext> actors = actorContexts(companyId, memberId);
        if (actors.size() != 1) {
            throw new ForbiddenException("当前成员已停用或不属于此租户，不能委托机器主体执行");
        }
        return actors.getFirst();
    }

    private List<ActorContext> actorContexts(String companyId, String memberId) {
        return jdbcTemplate.query("""
                SELECT member.id, member.account_id, member.role_code
                FROM company_member member
                JOIN principal principal ON principal.id=member.account_id
                  AND principal.principal_type='HUMAN' AND principal.lifecycle_status='ACTIVE'
                WHERE member.id=? AND member.company_id=? AND member.member_status='ACTIVE'
                """, (rs, rowNum) -> new ActorContext(
                rs.getString("id"), rs.getString("account_id"), rs.getString("role_code")), memberId, companyId);
    }

    private AppAccess resolveAppAccess(String companyId, String agentId, ServiceContext service, ActorContext actor) {
        if (!accessControl.can(companyId, actor.memberId(), List.of(actor.roleCode()), agentId, AgentPermission.RUN)) {
            return AppAccess.denied("AGENT_RUN_DENIED", "当前成员没有运行此智能体的权限");
        }
        if (actor.accountPrincipalId().equals(service.ownerPrincipalId())) {
            return AppAccess.allowed(APP_ROLE_ADMIN, "PRIMARY_OWNER", "你是机器主体治理负责人，可执行 DevAutopilot 操作");
        }
        if (DELEGATION_PRIMARY_OWNER.equals(service.delegationPolicy())) {
            return AppAccess.denied("OWNER_ONLY_POLICY", "此智能体仍使用负责人专属委托策略，请联系租户管理员完成初始化升级");
        }
        if (RoleCodes.isOrgAdminRole(actor.roleCode())) {
            return AppAccess.allowed(APP_ROLE_ADMIN, "ORG_ADMIN", "你具有租户管理权限，可委托产品经理执行");
        }
        List<String> roles = jdbcTemplate.queryForList("""
                SELECT access.role_code
                FROM tenant_application_member_role access
                JOIN tenant_application_activation activation ON activation.id=access.activation_id
                WHERE activation.company_id=? AND activation.app_code='devautopilot'
                  AND activation.actual_state='ACTIVE'
                  AND access.company_member_id=? AND access.status='ACTIVE'
                """, String.class, companyId, actor.memberId());
        if (roles.size() != 1) {
            return AppAccess.denied("APP_ROLE_REQUIRED", "当前账号没有 DevAutopilot 应用角色，请联系租户管理员授权");
        }
        return AppAccess.allowed(roles.getFirst(), "EXPLICIT_APP_ROLE", "DevAutopilot 应用角色已授权");
    }

    private String resolveDelegationPolicy(String companyId, String agentId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM tenant_application_resource resource
                JOIN tenant_application_activation activation ON activation.id=resource.activation_id
                WHERE activation.company_id=? AND activation.app_code='devautopilot'
                  AND resource.resource_type='AGENT' AND resource.external_id=?
                """, Integer.class, companyId, agentId);
        return count != null && count == 1 ? DELEGATION_TENANT_APP_ROLE : DELEGATION_PRIMARY_OWNER;
    }

    private static String requiredAppRole(List<String> scopes, String purpose) {
        List<String> normalized = normalizeScopes(scopes);
        String safe = safePurpose(purpose);
        if (normalized.contains("runtime.record.delete") || normalized.contains("runtime.record.transfer")) return APP_ROLE_ADMIN;
        if (safe.endsWith("_review")) return APP_ROLE_REVIEWER;
        if (normalized.contains("runtime.record.create") || normalized.contains("runtime.record.update")) return APP_ROLE_CONTRIBUTOR;
        return APP_ROLE_VIEWER;
    }

    private static boolean roleAllows(String actual, String requiredRole) {
        return roleRank(actual) >= roleRank(requiredRole);
    }

    private static int roleRank(String role) {
        return switch (role == null ? "" : role) {
            case APP_ROLE_VIEWER -> 1;
            case APP_ROLE_CONTRIBUTOR -> 2;
            case APP_ROLE_REVIEWER -> 3;
            case APP_ROLE_ADMIN -> 4;
            default -> 0;
        };
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

    private record ActorContext(String memberId, String accountPrincipalId, String roleCode) { }

    private record AppAccess(boolean allowed, String effectiveRole, String reasonCode, String message) {
        private static AppAccess allowed(String role, String reason, String message) {
            return new AppAccess(true, role, reason, message);
        }

        private static AppAccess denied(String reason, String message) {
            return new AppAccess(false, "NONE", reason, message);
        }
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
                                         String delegatedByPrincipalId,
                                         String delegationPolicy,
                                         String effectiveAppRole,
                                         OfficialAccessTokenService.IssuedToken token) {
        public ExecutionAuthorization(String servicePrincipalId,
                                      String servicePrincipalDisplayName,
                                      String ownerPrincipalId,
                                      String delegationPolicy,
                                      OfficialAccessTokenService.IssuedToken token) {
            this(servicePrincipalId, servicePrincipalDisplayName, ownerPrincipalId, ownerPrincipalId,
                    delegationPolicy, APP_ROLE_ADMIN, token);
        }
    }

    public record ExecutionAccessView(boolean bound,
                                      boolean canInvoke,
                                      String reasonCode,
                                      String maxRole,
                                      String message) { }
}
