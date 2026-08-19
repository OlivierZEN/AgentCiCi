package com.codehouse.ciciassistant.platform.service;

import com.codehouse.ciciassistant.agent.service.AgentDefinitionService;
import com.codehouse.ciciassistant.agent.service.AgentServicePrincipalExecutionService;
import com.codehouse.ciciassistant.auth.domain.CompanyEntity;
import com.codehouse.ciciassistant.auth.domain.CompanyRepository;
import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import com.codehouse.ciciassistant.auth.service.ServicePrincipalService;
import com.codehouse.ciciassistant.common.error.ConflictException;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.common.error.ResourceNotFoundException;
import com.codehouse.ciciassistant.semattice.SematticeDevAutopilotAuthorizationClient;
import com.codehouse.ciciassistant.semattice.SematticeDevAutopilotTemplateClient;
import com.codehouse.ciciassistant.semattice.SematticeProvisioningService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Control-plane authority for the tenant-local DevAutopilot application template. */
@Service
public class DevAutopilotTenantApplicationService {
    static final List<String> STANDARD_EXECUTION_SCOPES = List.of(
            "runtime.record.read", "runtime.record.create", "runtime.record.update");
    private static final String APP = "devautopilot";
    private static final String PRODUCT_MANAGER_AGENT_ID = "devautopilot-pm";
    private static final String PRODUCT_MANAGER_DEFAULT_NAME = "研发产品经理";
    private static final String PRODUCT_MANAGER_TEMPLATE_VERSION = "devautopilot.standard.v1";
    private static final List<String> PRODUCT_MANAGER_TOOL_IDS = List.of(
            "semattice_project_delivery_query",
            "semattice_project_delivery_create",
            "semattice_project_delivery_review");
    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,95}$");
    private final JdbcTemplate jdbc;
    private final CompanyRepository companies;
    private final SematticeProvisioningService provisioning;
    private final SematticeDevAutopilotTemplateClient template;
    private final SematticeDevAutopilotAuthorizationClient authorizationTemplate;
    private final ServicePrincipalService principals;
    private final AgentDefinitionService agents;
    private final DevAutopilotProductManagerAgentPublisher productManagerAgentPublisher;
    private final AgentServicePrincipalExecutionService execution;
    private final PlatformAuditService audit;
    private final List<String> pmScopes;
    private final List<String> developerScopes;

    public DevAutopilotTenantApplicationService(JdbcTemplate jdbc, CompanyRepository companies,
                                                 SematticeProvisioningService provisioning,
                                                 SematticeDevAutopilotTemplateClient template,
                                                 SematticeDevAutopilotAuthorizationClient authorizationTemplate,
                                                 ServicePrincipalService principals,
                                                 AgentDefinitionService agents,
                                                 DevAutopilotProductManagerAgentPublisher productManagerAgentPublisher,
                                                 AgentServicePrincipalExecutionService execution,
                                                 PlatformAuditService audit,
                                                 @Value("${app.devautopilot.template.pm-scopes:}") List<String> pmScopes,
                                                 @Value("${app.devautopilot.template.developer-scopes:}") List<String> developerScopes) {
        this.jdbc = jdbc; this.companies = companies; this.provisioning = provisioning; this.template = template;
        this.authorizationTemplate = authorizationTemplate;
        this.principals = principals; this.agents = agents; this.productManagerAgentPublisher = productManagerAgentPublisher;
        this.execution = execution; this.audit = audit;
        this.pmScopes = templateScopes(pmScopes);
        this.developerScopes = templateScopes(developerScopes);
    }

    private static List<String> templateScopes(List<String> configured) {
        List<String> normalized = configured == null ? List.of() : configured.stream()
                .filter(v -> v != null && !v.isBlank()).map(String::trim).distinct().sorted().toList();
        return normalized.isEmpty() ? STANDARD_EXECUTION_SCOPES : normalized;
    }

    public View activate(String companyId, ActivationCommand command, String platformActor) {
        requireCompany(companyId);
        require(command.idempotencyKey(), "idempotencyKey");
        if (!KEY.matcher(command.idempotencyKey()).matches()) throw new IllegalArgumentException("invalid activation key");
        String attemptToken = UUID.randomUUID().toString();
        var existing = find(companyId);
        if (existing != null) {
            if (!existing.idempotencyKey().equals(command.idempotencyKey())) {
                throw new ConflictException("DevAutopilot activation already exists for this tenant with another idempotency key");
            }
            if ("ACTIVE".equals(existing.actualState())) return view(existing);
        }
        var binding = provisioning.getProvisioningStatus(companyId);
        if (!"PROVISIONED".equals(binding.state()) || binding.sematticeTenantId() == null) throw new ForbiddenException("Semattice must be provisioned before enabling DevAutopilot");
        if (existing == null) {
            String ownerMemberId = initialOwnerMemberId(companyId);
            String activationId = UUID.randomUUID().toString();
            int inserted = jdbc.update("""
                    INSERT INTO tenant_application_activation(
                        id,company_id,app_code,template_version,idempotency_key,desired_state,actual_state,
                        semattice_tenant_id,created_by_member_id,activation_stage,attempt_count,last_attempt_at,
                        lease_token,lease_expires_at)
                    VALUES (?,?,?,?,?,'ACTIVE','PROVISIONING',?,?,'PROVISIONING',1,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP + INTERVAL '5 minutes')
                    ON CONFLICT (company_id,app_code) DO NOTHING
                    """, activationId, companyId, APP, SematticeDevAutopilotTemplateClient.TEMPLATE_VERSION,
                    command.idempotencyKey(), binding.sematticeTenantId(), ownerMemberId, attemptToken);
            if (inserted != 1) {
                Row concurrent = requireExisting(companyId);
                if (!concurrent.idempotencyKey().equals(command.idempotencyKey())) {
                    throw new ConflictException("DevAutopilot activation already exists for this tenant with another idempotency key");
                }
                throw new ConflictException("DevAutopilot activation is already in progress");
            }
            existing = requireExisting(companyId);
        } else {
            int acquired = jdbc.update("""
                    UPDATE tenant_application_activation
                    SET actual_state='PROVISIONING',failed_stage=NULL,last_error_code=NULL,
                        attempt_count=attempt_count+1,last_attempt_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,
                        lease_token=?,lease_expires_at=CURRENT_TIMESTAMP + INTERVAL '5 minutes'
                    WHERE id=? AND (lease_token IS NULL OR lease_expires_at < CURRENT_TIMESTAMP)
                    """, attemptToken, existing.id());
            if (acquired != 1) throw new ConflictException("DevAutopilot activation is already in progress");
            existing = requireExisting(companyId);
        }
        String activationId = existing.id();
        String nextStage = nextActivationStage(existing.activationStage());
        try {
            String ownerMemberId = initialOwnerMemberId(companyId);
            if (stageBefore(existing.activationStage(), "METADATA_READY")) {
                nextStage = "METADATA_READY";
                var metadata = template.apply(companyId, command.idempotencyKey());
                validateMetadataBaseline(companyId, metadata);
                checkpointMetadata(activationId, attemptToken, metadata);
            }
            Row progress = requireExisting(companyId);
            if (stageBefore(progress.activationStage(), "PRODUCT_MANAGER_READY")) {
                nextStage = "PRODUCT_MANAGER_READY";
                initializeProductManager(companyId, activationId, ownerMemberId);
                checkpointStage(activationId, attemptToken, "PRODUCT_MANAGER_READY");
            }
            progress = requireExisting(companyId);
            if (stageBefore(progress.activationStage(), "PRINCIPALS_READY")) {
                nextStage = "PRINCIPALS_READY";
                reconcilePrincipalProjections(companyId, activationId, platformActor);
                checkpointStage(activationId, attemptToken, "PRINCIPALS_READY");
            }
            progress = requireExisting(companyId);
            if (stageBefore(progress.activationStage(), "AUTHORIZATION_READY")) {
                nextStage = "AUTHORIZATION_READY";
                reconcileAuthorizationTemplate(companyId, activationId);
                checkpointStage(activationId, attemptToken, "AUTHORIZATION_READY");
            }
            audit.log(companyId, platformActor, "PLATFORM", "tenant_application.activated", "tenant_application", activationId,
                    "DevAutopilot standard application activated");
            int completed = jdbc.update("""
                    UPDATE tenant_application_activation
                    SET actual_state='ACTIVE',activation_stage='ACTIVE',failed_stage=NULL,last_error_code=NULL,
                        lease_token=NULL,lease_expires_at=NULL,updated_at=CURRENT_TIMESTAMP
                    WHERE id=? AND lease_token=?
                    """, activationId, attemptToken);
            if (completed != 1) throw new ConflictException("DevAutopilot activation lease was lost");
        } catch (RuntimeException exception) {
            jdbc.update("""
                    UPDATE tenant_application_activation
                    SET actual_state='FAILED',failed_stage=?,last_error_code=?,lease_token=NULL,
                        lease_expires_at=NULL,updated_at=CURRENT_TIMESTAMP
                    WHERE id=? AND lease_token=?
                    """, nextStage, activationFailureCode(nextStage, exception), activationId, attemptToken);
            throw exception;
        }
        return requireView(companyId);
    }

    private void validateMetadataBaseline(String companyId, SematticeDevAutopilotTemplateClient.TemplateView metadata) {
        if (!companyId.equals(metadata.companyId())
                || metadata.objectCount() != 7
                || metadata.fieldCount() != 86
                || !("applied".equals(metadata.state()) || "already_applied".equals(metadata.state()))) {
            throw new IllegalStateException("Semattice DevAutopilot metadata baseline is incomplete");
        }
    }

    private void checkpointMetadata(String activationId, String attemptToken,
                                    SematticeDevAutopilotTemplateClient.TemplateView metadata) {
        int updated = jdbc.update("""
                UPDATE tenant_application_activation
                SET activation_stage='METADATA_READY',metadata_version_id=?,metadata_digest=?,
                    failed_stage=NULL,last_error_code=NULL,lease_expires_at=CURRENT_TIMESTAMP + INTERVAL '5 minutes',
                    updated_at=CURRENT_TIMESTAMP
                WHERE id=? AND lease_token=?
                """, metadata.metadataVersionId(), metadata.snapshotDigest(), activationId, attemptToken);
        requireLeaseUpdate(updated);
    }

    private void checkpointStage(String activationId, String attemptToken, String stage) {
        int updated = jdbc.update("""
                UPDATE tenant_application_activation
                SET activation_stage=?,failed_stage=NULL,last_error_code=NULL,
                    lease_expires_at=CURRENT_TIMESTAMP + INTERVAL '5 minutes',updated_at=CURRENT_TIMESTAMP
                WHERE id=? AND lease_token=?
                """, stage, activationId, attemptToken);
        requireLeaseUpdate(updated);
    }

    private static void requireLeaseUpdate(int updated) {
        if (updated != 1) throw new ConflictException("DevAutopilot activation lease was lost");
    }

    static boolean stageBefore(String current, String target) {
        return activationStageRank(current) < activationStageRank(target);
    }

    private static int activationStageRank(String stage) {
        return switch (stage == null ? "PROVISIONING" : stage) {
            case "PROVISIONING" -> 0;
            case "METADATA_READY" -> 1;
            case "PRODUCT_MANAGER_READY" -> 2;
            case "PRINCIPALS_READY" -> 3;
            case "AUTHORIZATION_READY" -> 4;
            case "ACTIVE" -> 5;
            default -> throw new IllegalStateException("Unknown DevAutopilot activation stage");
        };
    }

    private static String nextActivationStage(String current) {
        return switch (current == null ? "PROVISIONING" : current) {
            case "PROVISIONING" -> "METADATA_READY";
            case "METADATA_READY" -> "PRODUCT_MANAGER_READY";
            case "PRODUCT_MANAGER_READY" -> "PRINCIPALS_READY";
            case "PRINCIPALS_READY" -> "AUTHORIZATION_READY";
            case "AUTHORIZATION_READY", "ACTIVE" -> "ACTIVE";
            default -> "PROVISIONING";
        };
    }

    static String activationFailureCode(String stage, RuntimeException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        if (message.contains("SCHEMA_MIGRATION_REQUIRED")) return "SCHEMA_MIGRATION_REQUIRED";
        if (message.contains("SCHEMA_MIGRATION_DRIFT")) return "SCHEMA_MIGRATION_DRIFT";
        return "ACTIVATION_" + stage + "_FAILED";
    }

    @Transactional(readOnly = true)
    public View get(String companyId) { requireCompany(companyId); var row = find(companyId); return row == null ? View.notEnabled(companyId) : view(row); }

    @Transactional(readOnly = true)
    public List<ApplicationMemberAccessView> listAccessMembers(String companyId) {
        Row activation = requireExisting(companyId);
        return jdbc.query("""
                SELECT member.id,
                       account.public_id,
                       COALESCE(account.display_name, account.primary_mobile, account.email, account.public_id),
                       member.role_code,
                       explicit.role_code,
                       EXISTS (
                           SELECT 1 FROM tenant_application_resource resource
                           JOIN service_principal_owner owner ON owner.service_principal_id=resource.external_id
                             AND owner.owner_role='PRIMARY' AND owner.owner_status='ACTIVE'
                           WHERE resource.activation_id=? AND resource.logical_role='product_manager'
                             AND resource.resource_type='SERVICE_PRINCIPAL' AND resource.is_primary=TRUE
                             AND owner.company_member_id=member.id
                       ) AS governance_owner
                FROM company_member member
                JOIN user_account account ON account.id=member.account_id
                LEFT JOIN tenant_application_member_role explicit
                  ON explicit.activation_id=? AND explicit.company_member_id=member.id AND explicit.status='ACTIVE'
                WHERE member.company_id=? AND member.member_status='ACTIVE'
                ORDER BY CASE member.role_code WHEN 'OWNER' THEN 0 WHEN 'ORG_ADMIN' THEN 1 ELSE 2 END,
                         account.display_name NULLS LAST, account.public_id
                """, (rs, rowNum) -> {
            String memberRole = rs.getString(4);
            String explicitRole = rs.getString(5);
            boolean governanceOwner = rs.getBoolean(6);
            String effectiveRole = RoleCodes.isOrgAdminRole(memberRole) || governanceOwner
                    ? AgentServicePrincipalExecutionService.APP_ROLE_ADMIN
                    : (explicitRole == null ? "NONE" : explicitRole);
            String source = RoleCodes.isOrgAdminRole(memberRole) ? "TENANT_ADMIN"
                    : governanceOwner ? "GOVERNANCE_OWNER"
                    : explicitRole == null ? "NONE" : "EXPLICIT";
            return new ApplicationMemberAccessView(
                    rs.getString(1), rs.getString(2), rs.getString(3), memberRole,
                    explicitRole, effectiveRole, source, governanceOwner);
        }, activation.id(), activation.id(), companyId);
    }

    @Transactional
    public List<ApplicationMemberAccessView> replaceAccessMembers(String companyId,
                                                                  String actorMemberId,
                                                                  List<ApplicationMemberRoleInput> requested) {
        Row activation = requireExisting(companyId);
        if (!"ACTIVE".equals(activation.actualState())) {
            throw new ForbiddenException("DevAutopilot 应用未运行，不能调整成员权限");
        }
        List<ApplicationMemberAccessView> before = listAccessMembers(companyId);
        List<ApplicationMemberRoleInput> inputs = requested == null ? List.of() : requested;
        Map<String, String> normalized = new LinkedHashMap<>();
        Set<String> validRoles = Set.of(
                AgentServicePrincipalExecutionService.APP_ROLE_VIEWER,
                AgentServicePrincipalExecutionService.APP_ROLE_CONTRIBUTOR,
                AgentServicePrincipalExecutionService.APP_ROLE_REVIEWER,
                AgentServicePrincipalExecutionService.APP_ROLE_ADMIN);
        for (ApplicationMemberRoleInput input : inputs) {
            String memberId = input == null ? "" : value(input.memberId());
            String role = input == null ? "" : value(input.roleCode()).toUpperCase(Locale.ROOT);
            if (memberId.isBlank() || !validRoles.contains(role)) {
                throw new IllegalArgumentException("DevAutopilot 应用成员和角色无效");
            }
            if (normalized.putIfAbsent(memberId, role) != null) {
                throw new IllegalArgumentException("DevAutopilot 应用成员不能重复授权");
            }
        }
        for (String memberId : normalized.keySet()) {
            Integer activeCount = jdbc.queryForObject("""
                    SELECT count(*) FROM company_member
                    WHERE company_id=? AND member_status='ACTIVE' AND id=?
                    """, Integer.class, companyId, memberId);
            if (activeCount == null || activeCount != 1) {
                throw new ForbiddenException("只能授权当前租户的激活成员");
            }
        }
        jdbc.update("""
                UPDATE tenant_application_member_role
                SET status='REVOKED',updated_at=CURRENT_TIMESTAMP
                WHERE activation_id=? AND status='ACTIVE'
                """, activation.id());
        for (Map.Entry<String, String> entry : normalized.entrySet()) {
            jdbc.update("""
                    INSERT INTO tenant_application_member_role(
                        id,activation_id,company_member_id,role_code,status,granted_by_member_id,created_at,updated_at)
                    VALUES (?,?,?,?, 'ACTIVE',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    ON CONFLICT (activation_id,company_member_id) DO UPDATE
                    SET role_code=EXCLUDED.role_code,status='ACTIVE',granted_by_member_id=EXCLUDED.granted_by_member_id,
                        updated_at=CURRENT_TIMESTAMP
                    """, UUID.randomUUID().toString(), activation.id(), entry.getKey(), entry.getValue(), actorMemberId);
        }
        List<ApplicationMemberAccessView> after = listAccessMembers(companyId);
        String actorPrincipalId = jdbc.queryForObject("""
                SELECT account_id FROM company_member WHERE id=? AND company_id=? AND member_status='ACTIVE'
                """, String.class, actorMemberId, companyId);
        audit.log(companyId, actorPrincipalId, "ORG_ADMIN", "tenant_application.access_roles.replaced",
                "tenant_application", activation.id(),
                "before=" + roleAuditSummary(before) + "; after=" + roleAuditSummary(after));
        return after;
    }

    /** Repairs an earlier activation that only provisioned Semattice metadata. GET remains read-only. */
    @Transactional
    public View reconcileInitialization(String companyId, String platformActor) {
        Row activation = requireExisting(companyId);
        if (!"ACTIVE".equals(activation.actualState())) {
            throw new ForbiddenException("Only an active DevAutopilot application can be initialized");
        }
        var metadata = reconcileMetadataBaseline(companyId, activation.id());
        initializeProductManager(companyId, activation.id(), initialOwnerMemberId(companyId));
        reconcilePrincipalProjections(companyId, activation.id(), platformActor);
        var authorization = reconcileAuthorizationTemplate(companyId, activation.id());
        audit.log(companyId, platformActor, "PLATFORM", "tenant_application.initialization_reconciled", "tenant_application", activation.id(),
                "DevAutopilot standard tenant resources reconciled; metadata=" + metadata.metadataVersionId()
                        + "; authorization=" + authorization.authorizationDigest());
        return requireView(companyId);
    }

    /**
     * Re-apply only the immutable authorization template for the current tenant team.
     * Unlike initialization, this leaves metadata, principals, credentials and business records untouched.
     */
    @Transactional
    public View reconcileAuthorization(String companyId, String actorMemberId) {
        Row activation = requireExisting(companyId);
        if (!"ACTIVE".equals(activation.actualState())) {
            throw new ForbiddenException("Only an active DevAutopilot application can synchronize authorization");
        }
        var authorization = reconcileAuthorizationTemplate(companyId, activation.id());
        String actorPrincipalId = jdbc.queryForObject("""
                SELECT account_id FROM company_member WHERE id=? AND company_id=? AND member_status='ACTIVE'
                """, String.class, actorMemberId, companyId);
        audit.log(companyId, actorPrincipalId, "ORG_ADMIN", "tenant_application.authorization_reconciled",
                "tenant_application", activation.id(),
                "DevAutopilot authorization template reconciled; version=" + authorization.templateVersion()
                        + "; digest=" + authorization.authorizationDigest());
        return requireView(companyId);
    }

    SematticeDevAutopilotTemplateClient.TemplateView reconcileMetadataBaseline(String companyId, String activationId) {
        var metadata = template.apply(companyId, metadataReconciliationKey(activationId));
        validateMetadataBaseline(companyId, metadata);
        jdbc.update("""
                UPDATE tenant_application_activation
                SET metadata_version_id=?,metadata_digest=?,last_error_code=NULL,updated_at=CURRENT_TIMESTAMP
                WHERE id=?
                """, metadata.metadataVersionId(), metadata.snapshotDigest(), activationId);
        return metadata;
    }

    static String metadataReconciliationKey(String activationId) {
        require(activationId, "activationId");
        return "devautopilot.standard.v1:shape-7x86:" + activationId;
    }

    @Transactional
    public View suspend(String companyId, String platformActor) { return transition(companyId, "SUSPENDED", platformActor); }
    @Transactional
    public View resume(String companyId, String platformActor) { return transition(companyId, "ACTIVE", platformActor); }

    @Transactional
    public TeamResourceView createProductManager(String companyId, String displayName, String actorMemberId, String ownerMemberId) {
        Row activation = requireExisting(companyId);
        if (!"ACTIVE".equals(activation.actualState()) || pmScopes.isEmpty()) throw new ForbiddenException("DevAutopilot is not ready to create a product-manager identity");
        require(displayName, "displayName");
        Integer count = jdbc.queryForObject("SELECT count(*) FROM tenant_application_resource WHERE activation_id=? AND logical_role='product_manager' AND resource_type='SERVICE_PRINCIPAL'", Integer.class, activation.id());
        if (count != null && count > 0) throw new ConflictException("DevAutopilot product manager already exists for this tenant");
        String agentId = "devautopilot-pm-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> principal = createProductManagerResources(companyId, activation.id(), displayName, actorMemberId, ownerMemberId, agentId);
        principals.synchronizeProjection(companyId, (String) principal.get("principalId"), actorMemberId);
        reconcileAuthorizationTemplate(companyId, activation.id());
        audit.log(companyId, actorMemberId, "ORG_ADMIN", "tenant_application.product_manager_added", "tenant_application", activation.id(), "DevAutopilot product-manager resource added");
        return teamResource(activation.id(), principal);
    }

    @Transactional
    public TeamResourceView addDeveloper(String companyId, String displayName, String actorMemberId,
                                         String ownerMemberId, int maxInstances) {
        Row activation = requireExisting(companyId);
        if (!"ACTIVE".equals(activation.actualState()) || developerScopes.isEmpty()) throw new ForbiddenException("DevAutopilot is not ready to create a developer identity");
        require(displayName, "displayName");
        requireMaxInstances(maxInstances);
        Map<String, Object> principal = principals.create(companyId, actorMemberId, ownerMemberId, displayName, "THIRD_PARTY",
                OfficialAccessTokenService.SEMATTICE_AUDIENCE, null, developerScopes);
        String principalId = (String) principal.get("principalId");
        resource(activation.id(), "developer", "SERVICE_PRINCIPAL", generatedAlias("developer"), displayName,
                principalId, false, maxInstances);
        principals.synchronizeProjection(companyId, principalId, actorMemberId);
        reconcileAuthorizationTemplate(companyId, activation.id());
        audit.log(companyId, actorMemberId, "ORG_ADMIN", "tenant_application.developer_added", "tenant_application", activation.id(), "DevAutopilot developer resource added");
        return teamResource(activation.id(), principal);
    }

    @Transactional
    public ResourceView updateDeveloperRuntimePolicy(String companyId, String principalId, int maxInstances,
                                                     long expectedRevision, String actorMemberId) {
        Row activation = requireExisting(companyId);
        if (!"ACTIVE".equals(activation.actualState())) {
            throw new ForbiddenException("DevAutopilot 应用未运行，不能调整机器开发者实例上限");
        }
        require(principalId, "principalId");
        requireMaxInstances(maxInstances);
        if (expectedRevision < 1) throw new IllegalArgumentException("expectedRevision must be at least 1");
        ResourceView before = developerResource(activation.id(), principalId);
        int updated = jdbc.update("""
                UPDATE tenant_application_resource
                SET max_instances=?,runtime_policy_revision=runtime_policy_revision+1,updated_at=CURRENT_TIMESTAMP
                WHERE activation_id=? AND external_id=? AND logical_role='developer'
                  AND resource_type='SERVICE_PRINCIPAL' AND runtime_policy_revision=?
                """, maxInstances, activation.id(), principalId, expectedRevision);
        if (updated != 1) {
            throw new ConflictException("机器开发者运行策略已变化，请刷新后重试");
        }
        ResourceView after = developerResource(activation.id(), principalId);
        audit.log(companyId, actorMemberId, "ORG_ADMIN", "tenant_application.developer_runtime_policy_updated",
                "service_principal", principalId,
                "max_instances=" + before.maxInstances() + "->" + after.maxInstances()
                        + "; revision=" + before.runtimePolicyRevision() + "->" + after.runtimePolicyRevision());
        return after;
    }

    private View transition(String companyId, String target, String platformActor) {
        Row row = requireExisting(companyId);
        if (target.equals(row.actualState())) return view(row);
        if ("SUSPENDED".equals(target)) return suspendResources(companyId, row, platformActor);
        return resumeResources(companyId, row, platformActor);
    }

    private void initializeProductManager(String companyId, String activationId, String ownerMemberId) {
        List<ResourceView> resources = jdbc.query("SELECT logical_role,resource_type,resource_alias,display_name,external_id,lifecycle_state,is_primary,max_instances,runtime_policy_revision FROM tenant_application_resource WHERE activation_id=? ORDER BY logical_role,resource_type", (rs,n) -> resourceView(rs), activationId);
        boolean hasPrimaryAgent = resources.stream().anyMatch(resource -> "product_manager".equals(resource.logicalRole()) && "AGENT".equals(resource.resourceType()) && resource.primary());
        boolean hasPrimaryPrincipal = resources.stream().anyMatch(resource -> "product_manager".equals(resource.logicalRole()) && "SERVICE_PRINCIPAL".equals(resource.resourceType()) && resource.primary());
        if (hasPrimaryAgent && hasPrimaryPrincipal) {
            String agentId = resources.stream()
                    .filter(resource -> "product_manager".equals(resource.logicalRole())
                            && "AGENT".equals(resource.resourceType()) && resource.primary())
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("DevAutopilot primary product-manager Agent is missing"))
                    .externalId();
            productManagerAgentPublisher.ensurePublished(companyId, agentId);
            // Reconciliation must also repair a missing, disabled or legacy execution binding;
            // merely republishing the Agent leaves the tenant unable to call Semattice.
            setProductManagerBinding(companyId, activationId, true);
            return;
        }
        if (hasPrimaryAgent || hasPrimaryPrincipal) throw new IllegalStateException("DevAutopilot product-manager resources are incomplete");
        createProductManagerResources(companyId, activationId, PRODUCT_MANAGER_DEFAULT_NAME, ownerMemberId, ownerMemberId, PRODUCT_MANAGER_AGENT_ID);
    }

    private void reconcilePrincipalProjections(String companyId, String activationId, String actorPrincipalId) {
        for (ServiceResource resource : serviceResources(activationId)) {
            principals.synchronizeProjection(companyId, resource.externalId(), actorPrincipalId);
        }
    }

    private Map<String, Object> createProductManagerResources(String companyId, String activationId, String displayName,
                                                                String actorMemberId, String ownerMemberId, String agentId) {
        ensureProductManagerAgent(companyId, displayName, agentId);
        productManagerAgentPublisher.ensurePublished(companyId, agentId);
        Map<String, Object> principal = principals.create(companyId, actorMemberId, ownerMemberId, displayName, "OFFICIAL_APP",
                OfficialAccessTokenService.SEMATTICE_AUDIENCE, null, pmScopes);
        String principalId = (String) principal.get("principalId");
        // Register the application-owned resources before configuring execution.  The binding
        // service derives TENANT_APP_ROLE from this ownership record; reversing this order would
        // silently leave newly activated tenants on the legacy PRIMARY_OWNER-only policy.
        resource(activationId, "product_manager", "SERVICE_PRINCIPAL", generatedAlias("product-manager"), displayName, principalId, true, 1);
        resource(activationId, "product_manager", "AGENT", generatedAlias("product-manager-agent"), displayName, agentId, true, 1);
        execution.configure(companyId, agentId, principalId, true, ownerMemberId);
        return principal;
    }

    /**
     * Agent creation commits independently from the activation checkpoint.  A process interruption
     * can therefore leave the governed Agent in place before the application resource rows exist.
     * Reuse only the exact managed template identity; a same-id custom Agent remains a hard conflict.
     */
    private void ensureProductManagerAgent(String companyId, String displayName, String agentId) {
        try {
            AgentDefinitionService.AgentDetail existing = agents.get(companyId, agentId);
            if (!PRODUCT_MANAGER_TEMPLATE_VERSION.equals(existing.definition().getVersionLabel())
                    || !existing.definition().isEnabled()) {
                throw new ConflictException("Existing Agent is not the managed DevAutopilot product-manager template: " + agentId);
            }
            return;
        } catch (ResourceNotFoundException notFound) {
            // The normal first-attempt path creates the managed Agent below.
        }
        agents.create(companyId, new AgentDefinitionService.CreateCommand(agentId, displayName, "DevAutopilot 租户产品经理", "", "gpt-4.1",
                DevAutopilotProductManagerAgentPublisher.STANDARD_SYSTEM_PROMPT, "高风险操作必须确认", "standard", "copilot", PRODUCT_MANAGER_TEMPLATE_VERSION, null,
                null, false, true, DevAutopilotProductManagerAgentPublisher.STANDARD_SPEC, List.of(),
                PRODUCT_MANAGER_TOOL_IDS, List.of("web"), Map.of()));
    }

    /**
     * The activation row is the runtime fail-closed gate.  Resource lifecycle changes are then
     * applied underneath it; a Keycloak failure deliberately leaves SUSPENDING in place rather
     * than accidentally reopening the DevAutopilot API.
     */
    private View suspendResources(String companyId, Row row, String platformActor) {
        jdbc.update("UPDATE tenant_application_activation SET desired_state='SUSPENDED',actual_state='SUSPENDING',last_error_code=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=?", row.id());
        try {
            setProductManagerBinding(companyId, row.id(), false);
            for (ServiceResource resource : serviceResources(row.id())) {
                if (!"SUSPENDED".equals(resource.lifecycleState())) {
                    principals.suspend(companyId, ownerMemberId(companyId, resource.externalId()), resource.externalId());
                    jdbc.update("UPDATE tenant_application_resource SET lifecycle_state='SUSPENDED' WHERE id=?", resource.id());
                }
            }
            jdbc.update("UPDATE tenant_application_activation SET actual_state='SUSPENDED',last_error_code=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=?", row.id());
            audit.log(companyId, platformActor, "PLATFORM", "tenant_application.suspended", "tenant_application", row.id(), "DevAutopilot application and tenant resources suspended");
            return requireView(companyId);
        } catch (RuntimeException exception) {
            jdbc.update("UPDATE tenant_application_activation SET actual_state='SUSPENDING',last_error_code='SUSPEND_FAILED',updated_at=CURRENT_TIMESTAMP WHERE id=?", row.id());
            throw exception;
        }
    }

    /** Restore resource credentials before restoring the agent execution binding and application gate. */
    private View resumeResources(String companyId, Row row, String platformActor) {
        if (!"SUSPENDED".equals(row.actualState())) throw new ConflictException("only a suspended DevAutopilot application can be resumed");
        jdbc.update("UPDATE tenant_application_activation SET desired_state='ACTIVE',actual_state='RESUMING',last_error_code=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=?", row.id());
        try {
            for (ServiceResource resource : serviceResources(row.id())) {
                if ("SUSPENDED".equals(resource.lifecycleState())) {
                    principals.activate(companyId, ownerMemberId(companyId, resource.externalId()), resource.externalId());
                    jdbc.update("UPDATE tenant_application_resource SET lifecycle_state='ACTIVE' WHERE id=?", resource.id());
                }
            }
            reconcilePrincipalProjections(companyId, row.id(), platformActor);
            reconcileAuthorizationTemplate(companyId, row.id());
            setProductManagerBinding(companyId, row.id(), true);
            jdbc.update("UPDATE tenant_application_activation SET actual_state='ACTIVE',last_error_code=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=?", row.id());
            audit.log(companyId, platformActor, "PLATFORM", "tenant_application.resumed", "tenant_application", row.id(), "DevAutopilot application and tenant resources resumed");
            return requireView(companyId);
        } catch (RuntimeException exception) {
            jdbc.update("UPDATE tenant_application_activation SET actual_state='RESUMING',last_error_code='RESUME_FAILED',updated_at=CURRENT_TIMESTAMP WHERE id=?", row.id());
            throw exception;
        }
    }

    private List<ServiceResource> serviceResources(String activationId) {
        return jdbc.query("""
                SELECT resource.id,resource.external_id,COALESCE(principal.lifecycle_status,'DISABLED')
                FROM tenant_application_resource resource
                LEFT JOIN principal ON principal.id=resource.external_id
                WHERE resource.activation_id=? AND resource.resource_type='SERVICE_PRINCIPAL'
                ORDER BY resource.id
                """,
                (rs, n) -> new ServiceResource(rs.getString(1), rs.getString(2), rs.getString(3)), activationId);
    }

    private String ownerMemberId(String companyId, String servicePrincipalId) {
        List<String> ownerIds = jdbc.queryForList("""
                SELECT owner.company_member_id
                FROM service_principal_owner owner
                JOIN company_member member ON member.id=owner.company_member_id
                WHERE owner.service_principal_id=? AND owner.owner_role='PRIMARY' AND owner.owner_status='ACTIVE'
                  AND member.company_id=? AND member.member_status='ACTIVE'
                """, String.class, servicePrincipalId, companyId);
        if (ownerIds.size() != 1) throw new IllegalStateException("DevAutopilot service principal has no active tenant-local owner");
        return ownerIds.getFirst();
    }

    private void setProductManagerBinding(String companyId, String activationId, boolean enabled) {
        List<BindingTarget> targets = jdbc.query("""
                SELECT agent.external_id, principal.external_id
                FROM tenant_application_resource agent
                JOIN tenant_application_resource principal
                  ON principal.activation_id=agent.activation_id
                 AND principal.logical_role='product_manager'
                 AND principal.resource_type='SERVICE_PRINCIPAL'
                 AND principal.is_primary=TRUE
                WHERE agent.activation_id=? AND agent.logical_role='product_manager'
                  AND agent.resource_type='AGENT' AND agent.is_primary=TRUE
                """, (rs, n) -> new BindingTarget(rs.getString(1), rs.getString(2)), activationId);
        if (targets.isEmpty()) return;
        if (targets.size() != 1) throw new IllegalStateException("DevAutopilot product-manager resources are incomplete");
        BindingTarget target = targets.getFirst();
        execution.configure(companyId, target.agentId(), target.servicePrincipalId(), enabled,
                ownerMemberId(companyId, target.servicePrincipalId()));
    }
    private void resource(String activationId, String role, String type, String alias, String name, String externalId,
                          boolean primary, int maxInstances) {
        jdbc.update("""
                INSERT INTO tenant_application_resource(id,activation_id,logical_role,resource_type,resource_alias,display_name,external_id,lifecycle_state,is_primary,max_instances)
                VALUES (?,?,?,?,?,?,?,'ACTIVE',?,?)
                """, UUID.randomUUID().toString(), activationId, role, type, alias, name, externalId, primary, maxInstances);
    }

    SematticeDevAutopilotAuthorizationClient.AuthorizationView reconcileAuthorizationTemplate(String companyId,
                                                                                                String activationId) {
        List<SematticeDevAutopilotAuthorizationClient.Assignment> assignments = authorizationAssignments(companyId, activationId);
        assignments.stream()
                .filter(item -> "application_admin".equals(item.logicalRole()))
                .forEach(item -> principals.synchronizeHumanProjection(companyId, item.principalId()));
        var result = authorizationTemplate.apply(companyId, activationId,
                authorizationReconciliationKey(activationId, assignments), assignments);
        if (!companyId.equals(result.companyId())
                || !SematticeDevAutopilotAuthorizationClient.TEMPLATE_VERSION.equals(result.templateVersion())
                || result.authorizationDigest() == null || result.authorizationDigest().length() != 64
                || result.roleCount() != 4 || result.permissionSetCount() != 4 || result.objectCount() != 7
                || result.assignmentCount() != assignments.size() || !result.verified()
                || !("applied".equals(result.state()) || "already_applied".equals(result.state()))) {
            throw new IllegalStateException("Semattice DevAutopilot authorization baseline is incomplete");
        }
        jdbc.update("""
                UPDATE tenant_application_activation
                SET authorization_template_version=?,authorization_digest=?,authorization_role_count=?,
                    authorization_permission_set_count=?,authorization_assignment_count=?,
                    authorization_verified_at=CURRENT_TIMESTAMP,last_error_code=NULL,updated_at=CURRENT_TIMESTAMP
                WHERE id=?
                """, result.templateVersion(), result.authorizationDigest(), result.roleCount(),
                result.permissionSetCount(), result.assignmentCount(), activationId);
        return result;
    }

    List<SematticeDevAutopilotAuthorizationClient.Assignment> authorizationAssignments(String companyId,
                                                                                        String activationId) {
        List<SematticeDevAutopilotAuthorizationClient.Assignment> result = new java.util.ArrayList<>();
        result.addAll(jdbc.query("""
                SELECT DISTINCT member.account_id
                FROM company_member member
                LEFT JOIN tenant_application_member_role app_access
                  ON app_access.activation_id=? AND app_access.company_member_id=member.id
                 AND app_access.status='ACTIVE'
                WHERE member.company_id=? AND member.member_status='ACTIVE'
                  AND (member.role_code IN ('OWNER','ORG_ADMIN') OR app_access.role_code='APP_ADMIN')
                ORDER BY member.account_id
                """, (rs, rowNum) -> new SematticeDevAutopilotAuthorizationClient.Assignment(
                OfficialAccessTokenService.sematticePrincipalId(rs.getString(1)), "application_admin"),
                activationId, companyId));
        if (result.isEmpty()) {
            throw new IllegalStateException("DevAutopilot application administrator is unavailable");
        }
        result.addAll(jdbc.query("""
                SELECT external_id,logical_role
                FROM tenant_application_resource
                WHERE activation_id=? AND resource_type='SERVICE_PRINCIPAL'
                  AND logical_role IN ('product_manager','developer','observer')
                ORDER BY logical_role,external_id
                """, (rs, rowNum) -> new SematticeDevAutopilotAuthorizationClient.Assignment(
                rs.getString(1), rs.getString(2)), activationId));
        long productManagers = result.stream().filter(item -> "product_manager".equals(item.logicalRole())).count();
        if (productManagers != 1) {
            throw new IllegalStateException("DevAutopilot requires exactly one product-manager identity");
        }
        return result.stream()
                .sorted(Comparator.comparing(SematticeDevAutopilotAuthorizationClient.Assignment::logicalRole)
                        .thenComparing(SematticeDevAutopilotAuthorizationClient.Assignment::principalId))
                .toList();
    }

    static String authorizationReconciliationKey(String activationId,
                                                  List<SematticeDevAutopilotAuthorizationClient.Assignment> assignments) {
        require(activationId, "activationId");
        try {
            String canonical = assignments.stream()
                    .sorted(Comparator.comparing(SematticeDevAutopilotAuthorizationClient.Assignment::logicalRole)
                            .thenComparing(SematticeDevAutopilotAuthorizationClient.Assignment::principalId))
                    .map(item -> item.logicalRole() + ":" + item.principalId())
                    .reduce((left, right) -> left + "\n" + right).orElse("");
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
            // The managed manifest is immutable. Its version is part of the
            // idempotency key so a template upgrade replays reconciliation
            // while repeated requests for one version stay idempotent.
            return SematticeDevAutopilotAuthorizationClient.TEMPLATE_VERSION + ":" + activationId + ":" + digest.substring(0, 16);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot derive DevAutopilot authorization reconciliation key", exception);
        }
    }

    private TeamResourceView teamResource(String activationId, Map<String, Object> principal) {
        String principalId = (String) principal.get("principalId");
        ResourceView resource = jdbc.queryForObject("""
                SELECT resource.logical_role,resource.resource_type,resource.resource_alias,
                       COALESCE(authority.display_name,resource.display_name),resource.external_id,
                       COALESCE(authority.lifecycle_status,'DISABLED'),resource.is_primary,
                       resource.max_instances,resource.runtime_policy_revision
                FROM tenant_application_resource resource
                LEFT JOIN principal authority ON authority.id=resource.external_id
                WHERE resource.activation_id=? AND resource.external_id=?
                """,
                (rs, n) -> resourceView(rs), activationId, principalId);
        return new TeamResourceView(resource, principalId, (String) principal.get("clientId"), (String) principal.get("clientSecret"),
                (String) principal.get("credentialNotice"));
    }

    private static String generatedAlias(String role) {
        return role + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
    private static String value(String input) { return input == null ? "" : input.trim(); }
    private static String roleAuditSummary(List<ApplicationMemberAccessView> members) {
        return members.stream()
                .filter(item -> item.explicitRole() != null && !item.explicitRole().isBlank())
                .map(item -> item.memberId() + ":" + item.explicitRole())
                .sorted()
                .toList().toString();
    }
    private String initialOwnerMemberId(String companyId) {
        List<String> candidates = jdbc.queryForList("""
                SELECT id FROM company_member
                WHERE company_id=? AND member_status='ACTIVE' AND role_code IN (?, ?)
                ORDER BY CASE role_code WHEN ? THEN 0 ELSE 1 END, created_at, id
                """, String.class, companyId, RoleCodes.OWNER, RoleCodes.ORG_ADMIN, RoleCodes.OWNER);
        if (candidates.isEmpty()) throw new IllegalStateException("DevAutopilot activation requires an active tenant ORG_ADMIN");
        return candidates.getFirst();
    }
    private CompanyEntity requireCompany(String companyId) {
        CompanyEntity company = companies.findById(companyId).orElseThrow(() -> new ResourceNotFoundException("tenant not found"));
        if (!"ACTIVE".equals(company.getStatus())) throw new ForbiddenException("tenant is not active"); return company;
    }
    private Row requireExisting(String companyId) { Row row = find(companyId); if (row == null) throw new ResourceNotFoundException("DevAutopilot is not enabled"); return row; }
    private View requireView(String companyId) { return view(requireExisting(companyId)); }
    private Row find(String companyId) {
        List<Row> rows = jdbc.query("""
                SELECT id,company_id,template_version,idempotency_key,desired_state,actual_state,semattice_tenant_id,
                       metadata_version_id,metadata_digest,last_error_code,activation_stage,failed_stage,attempt_count
                FROM tenant_application_activation WHERE company_id=? AND app_code='devautopilot'
                """, (rs, n) -> new Row(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getString(9),rs.getString(10),rs.getString(11),rs.getString(12),rs.getInt(13)), companyId);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    private View view(Row row) {
        List<ResourceView> resources = jdbc.query("""
                SELECT resource.logical_role,resource.resource_type,resource.resource_alias,
                       CASE WHEN resource.resource_type='SERVICE_PRINCIPAL'
                            THEN COALESCE(authority.display_name,resource.display_name)
                            ELSE resource.display_name END,
                       resource.external_id,
                       CASE WHEN resource.resource_type='SERVICE_PRINCIPAL'
                            THEN COALESCE(authority.lifecycle_status,'DISABLED')
                            ELSE resource.lifecycle_state END,
                       resource.is_primary,resource.max_instances,resource.runtime_policy_revision
                FROM tenant_application_resource resource
                LEFT JOIN principal authority ON authority.id=resource.external_id
                WHERE resource.activation_id=?
                ORDER BY resource.logical_role,resource.resource_type
                """, (rs,n)->resourceView(rs), row.id());
        return new View(row.companyId(), true, row.templateVersion(), row.desiredState(), row.actualState(), row.sematticeTenantId(), row.metadataVersionId(), row.metadataDigest(),
                initializationReady(row.companyId(), row.id()), row.lastError(), resources,
                row.activationStage(), row.failedStage(), row.attemptCount());
    }
    boolean initializationReady(String companyId, String activationId) {
        Boolean ready = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM tenant_application_resource resource
                    JOIN tenant_application_activation app_activation
                      ON app_activation.id=resource.activation_id
                    JOIN agent_definition agent
                      ON agent.company_id=? AND agent.agent_id=resource.external_id
                    JOIN agent_channel_binding channel
                      ON channel.company_id=agent.company_id AND channel.agent_id=agent.agent_id
                     AND channel.channel_id='web' AND channel.enabled=TRUE
                    JOIN agent_service_principal_binding execution
                      ON execution.company_id=agent.company_id AND execution.agent_id=agent.agent_id
                     AND execution.delegation_policy='TENANT_APP_ROLE'
                    JOIN agent_skill_binding skill_binding
                      ON skill_binding.company_id=agent.company_id AND skill_binding.agent_id=agent.agent_id
                     AND skill_binding.enabled=TRUE AND skill_binding.activation_mode='always-on'
                    JOIN skill_definition skill
                      ON skill.id=skill_binding.skill_id AND skill.company_id=skill_binding.company_id
                     AND skill.skill_code='semattice-project-delivery-management' AND skill.enabled=TRUE
                    JOIN agent_workflow_skill_ref workflow_skill
                      ON workflow_skill.company_id=agent.company_id
                     AND workflow_skill.workflow_version_id=agent.published_version_id
                     AND workflow_skill.skill_id=skill.id
                    JOIN skill_version skill_version
                      ON skill_version.id=workflow_skill.skill_version_id
                     AND skill_version.company_id=workflow_skill.company_id
                     AND skill_version.publish_status='PUBLISHED'
                    WHERE resource.activation_id=? AND resource.logical_role='product_manager'
                      AND resource.resource_type='AGENT' AND resource.is_primary=TRUE
                      AND agent.enabled=TRUE AND agent.published_version_id IS NOT NULL
                      AND ((app_activation.actual_state='ACTIVE' AND execution.enabled=TRUE)
                        OR (app_activation.actual_state='SUSPENDED' AND execution.enabled=FALSE))
                ) AND EXISTS (
                    SELECT 1
                    FROM tenant_application_resource resource
                    JOIN tenant_application_activation app_activation
                      ON app_activation.id=resource.activation_id
                    JOIN principal authority ON authority.id=resource.external_id
                    WHERE resource.activation_id=? AND resource.logical_role='product_manager'
                      AND resource.resource_type='SERVICE_PRINCIPAL' AND resource.is_primary=TRUE
                      AND authority.principal_type='SERVICE'
                      AND ((app_activation.actual_state='ACTIVE' AND authority.lifecycle_status='ACTIVE')
                        OR (app_activation.actual_state='SUSPENDED' AND authority.lifecycle_status='SUSPENDED'))
                ) AND EXISTS (
                    SELECT 1
                    FROM tenant_application_activation activation
                    WHERE activation.id=? AND activation.company_id=?
                      AND activation.authorization_template_version=?
                      AND length(activation.authorization_digest)=64
                      AND activation.authorization_role_count=4
                      AND activation.authorization_permission_set_count=4
                      AND activation.authorization_verified_at IS NOT NULL
                      AND activation.authorization_assignment_count=(
                          SELECT 1 + count(*)
                          FROM tenant_application_resource resource
                          WHERE resource.activation_id=activation.id
                            AND resource.resource_type='SERVICE_PRINCIPAL'
                            AND resource.logical_role IN ('product_manager','developer','observer')
                      )
                )
                """, Boolean.class, companyId, activationId, activationId, activationId, companyId,
                SematticeDevAutopilotAuthorizationClient.TEMPLATE_VERSION);
        return Boolean.TRUE.equals(ready);
    }
    private static void require(String v,String name) { if (v==null||v.isBlank()) throw new IllegalArgumentException(name+" is required"); }
    private static void requireMaxInstances(int value) {
        if (value < 1 || value > 64) throw new IllegalArgumentException("maxInstances must be between 1 and 64");
    }
    private ResourceView developerResource(String activationId, String principalId) {
        List<ResourceView> resources = jdbc.query("""
                SELECT resource.logical_role,resource.resource_type,resource.resource_alias,
                       COALESCE(authority.display_name,resource.display_name),resource.external_id,
                       COALESCE(authority.lifecycle_status,'DISABLED'),resource.is_primary,
                       resource.max_instances,resource.runtime_policy_revision
                FROM tenant_application_resource resource
                LEFT JOIN principal authority ON authority.id=resource.external_id
                WHERE resource.activation_id=? AND resource.external_id=?
                  AND resource.logical_role='developer' AND resource.resource_type='SERVICE_PRINCIPAL'
                """, (rs, n) -> resourceView(rs), activationId, principalId);
        if (resources.size() != 1) throw new ResourceNotFoundException("DevAutopilot machine developer not found");
        return resources.getFirst();
    }
    private static ResourceView resourceView(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ResourceView(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getBoolean(7), rs.getInt(8), rs.getLong(9));
    }
    private record Row(String id,String companyId,String templateVersion,String idempotencyKey,String desiredState,String actualState,String sematticeTenantId,String metadataVersionId,String metadataDigest,String lastError,String activationStage,String failedStage,int attemptCount) { }
    private record ServiceResource(String id, String externalId, String lifecycleState) { }
    private record BindingTarget(String agentId, String servicePrincipalId) { }
    public record ActivationCommand(String idempotencyKey) { }
    public record ResourceView(String logicalRole,String resourceType,String resourceAlias,String displayName,String externalId,
                               String lifecycleState,boolean primary,int maxInstances,long runtimePolicyRevision) { }
    public record TeamResourceView(ResourceView resource, String principalId, String clientId, String clientSecret, String credentialNotice) { }
    public record ApplicationMemberRoleInput(String memberId, String roleCode) { }
    public record ApplicationMemberAccessView(String memberId,
                                              String publicId,
                                              String displayName,
                                              String memberRole,
                                              String explicitRole,
                                              String effectiveRole,
                                              String source,
                                              boolean governanceOwner) { }
    public record View(String companyId,boolean enabled,String templateVersion,String desiredState,String actualState,String sematticeTenantId,String metadataVersionId,String metadataDigest,boolean initializationReady,String lastErrorCode,List<ResourceView> resources,String activationStage,String failedStage,int attemptCount) {
        static View notEnabled(String companyId) { return new View(companyId,false,null,"NOT_ENABLED","NOT_ENABLED",null,null,null,false,null,List.of(),"NOT_ENABLED",null,0); }
    }
}
