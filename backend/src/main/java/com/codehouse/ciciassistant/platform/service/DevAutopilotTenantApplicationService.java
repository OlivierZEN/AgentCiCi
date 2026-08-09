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
import com.codehouse.ciciassistant.semattice.SematticeDevAutopilotTemplateClient;
import com.codehouse.ciciassistant.semattice.SematticeProvisioningService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Control-plane authority for the tenant-local DevAutopilot application template. */
@Service
public class DevAutopilotTenantApplicationService {
    private static final String APP = "devautopilot";
    private static final String PRODUCT_MANAGER_AGENT_ID = "devautopilot-pm";
    private static final String PRODUCT_MANAGER_DEFAULT_NAME = "研发产品经理";
    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,95}$");
    private final JdbcTemplate jdbc;
    private final CompanyRepository companies;
    private final SematticeProvisioningService provisioning;
    private final SematticeDevAutopilotTemplateClient template;
    private final ServicePrincipalService principals;
    private final AgentDefinitionService agents;
    private final AgentServicePrincipalExecutionService execution;
    private final PlatformAuditService audit;
    private final List<String> pmScopes;
    private final List<String> developerScopes;

    public DevAutopilotTenantApplicationService(JdbcTemplate jdbc, CompanyRepository companies,
                                                 SematticeProvisioningService provisioning,
                                                 SematticeDevAutopilotTemplateClient template,
                                                 ServicePrincipalService principals,
                                                 AgentDefinitionService agents,
                                                 AgentServicePrincipalExecutionService execution,
                                                 PlatformAuditService audit,
                                                 @Value("${app.devautopilot.template.pm-scopes:}") List<String> pmScopes,
                                                 @Value("${app.devautopilot.template.developer-scopes:}") List<String> developerScopes) {
        this.jdbc = jdbc; this.companies = companies; this.provisioning = provisioning; this.template = template;
        this.principals = principals; this.agents = agents; this.execution = execution; this.audit = audit;
        this.pmScopes = pmScopes == null ? List.of() : pmScopes.stream().filter(v -> v != null && !v.isBlank()).map(String::trim).distinct().toList();
        this.developerScopes = developerScopes == null ? List.of() : developerScopes.stream().filter(v -> v != null && !v.isBlank()).map(String::trim).distinct().toList();
    }

    @Transactional
    public View activate(String companyId, ActivationCommand command, String platformActor) {
        requireCompany(companyId);
        require(command.idempotencyKey(), "idempotencyKey");
        if (!KEY.matcher(command.idempotencyKey()).matches()) throw new IllegalArgumentException("invalid activation key");
        var existing = find(companyId);
        if (existing != null) {
            if (!existing.idempotencyKey().equals(command.idempotencyKey()) || !"ACTIVE".equals(existing.actualState())) throw new ConflictException("DevAutopilot activation already exists for this tenant");
            return view(existing);
        }
        var binding = provisioning.getProvisioningStatus(companyId);
        if (!"PROVISIONED".equals(binding.state()) || binding.sematticeTenantId() == null) throw new ForbiddenException("Semattice must be provisioned before enabling DevAutopilot");
        String ownerMemberId = initialOwnerMemberId(companyId);
        String activationId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO tenant_application_activation(id,company_id,app_code,template_version,idempotency_key,desired_state,actual_state,semattice_tenant_id,created_by_member_id)
                VALUES (?,?,?,?,?,'ACTIVE','PROVISIONING',?,?)
                """, activationId, companyId, APP, SematticeDevAutopilotTemplateClient.TEMPLATE_VERSION,
                command.idempotencyKey(), binding.sematticeTenantId(), ownerMemberId);
        try {
            var metadata = template.apply(companyId, command.idempotencyKey());
            initializeProductManager(companyId, activationId, ownerMemberId);
            reconcilePrincipalProjections(companyId, activationId, platformActor);
            jdbc.update("""
                    UPDATE tenant_application_activation SET actual_state='ACTIVE',metadata_version_id=?,metadata_digest=?,updated_at=CURRENT_TIMESTAMP WHERE id=?
                    """,
                    metadata.metadataVersionId(), metadata.snapshotDigest(), activationId);
            audit.log(companyId, platformActor, "PLATFORM", "tenant_application.activated", "tenant_application", activationId,
                    "DevAutopilot standard application activated");
            return requireView(companyId);
        } catch (RuntimeException exception) {
            jdbc.update("UPDATE tenant_application_activation SET actual_state='FAILED',last_error_code=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", "ACTIVATION_FAILED", activationId);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public View get(String companyId) { requireCompany(companyId); var row = find(companyId); return row == null ? View.notEnabled(companyId) : view(row); }

    /** Repairs an earlier activation that only provisioned Semattice metadata. GET remains read-only. */
    @Transactional
    public View reconcileInitialization(String companyId, String platformActor) {
        Row activation = requireExisting(companyId);
        if (!"ACTIVE".equals(activation.actualState())) {
            throw new ForbiddenException("Only an active DevAutopilot application can be initialized");
        }
        initializeProductManager(companyId, activation.id(), initialOwnerMemberId(companyId));
        reconcilePrincipalProjections(companyId, activation.id(), platformActor);
        audit.log(companyId, platformActor, "PLATFORM", "tenant_application.initialization_reconciled", "tenant_application", activation.id(),
                "DevAutopilot standard tenant resources reconciled");
        return requireView(companyId);
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
        audit.log(companyId, actorMemberId, "ORG_ADMIN", "tenant_application.product_manager_added", "tenant_application", activation.id(), "DevAutopilot product-manager resource added");
        return teamResource(activation.id(), principal);
    }

    @Transactional
    public TeamResourceView addDeveloper(String companyId, String displayName, String actorMemberId, String ownerMemberId) {
        Row activation = requireExisting(companyId);
        if (!"ACTIVE".equals(activation.actualState()) || developerScopes.isEmpty()) throw new ForbiddenException("DevAutopilot is not ready to create a developer identity");
        require(displayName, "displayName");
        Map<String, Object> principal = principals.create(companyId, actorMemberId, ownerMemberId, displayName, "THIRD_PARTY",
                OfficialAccessTokenService.SEMATTICE_AUDIENCE, null, developerScopes);
        String principalId = (String) principal.get("principalId");
        resource(activation.id(), "developer", "SERVICE_PRINCIPAL", generatedAlias("developer"), displayName, principalId, false);
        audit.log(companyId, actorMemberId, "ORG_ADMIN", "tenant_application.developer_added", "tenant_application", activation.id(), "DevAutopilot developer resource added");
        return teamResource(activation.id(), principal);
    }

    private View transition(String companyId, String target, String platformActor) {
        Row row = requireExisting(companyId);
        if (target.equals(row.actualState())) return view(row);
        if ("SUSPENDED".equals(target)) return suspendResources(companyId, row, platformActor);
        return resumeResources(companyId, row, platformActor);
    }

    private void initializeProductManager(String companyId, String activationId, String ownerMemberId) {
        List<ResourceView> resources = jdbc.query("SELECT logical_role,resource_type,resource_alias,display_name,external_id,lifecycle_state,is_primary FROM tenant_application_resource WHERE activation_id=? ORDER BY logical_role,resource_type", (rs,n) -> new ResourceView(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getBoolean(7)), activationId);
        boolean hasPrimaryAgent = resources.stream().anyMatch(resource -> "product_manager".equals(resource.logicalRole()) && "AGENT".equals(resource.resourceType()) && resource.primary());
        boolean hasPrimaryPrincipal = resources.stream().anyMatch(resource -> "product_manager".equals(resource.logicalRole()) && "SERVICE_PRINCIPAL".equals(resource.resourceType()) && resource.primary());
        if (hasPrimaryAgent && hasPrimaryPrincipal) return;
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
        agents.create(companyId, new AgentDefinitionService.CreateCommand(agentId, displayName, "DevAutopilot 租户产品经理", "", "gpt-4.1",
                "你是本租户的研发产品经理，只能通过已绑定的受控工具处理研发交付。", "高风险操作必须确认", "standard", "copilot", "devautopilot.standard.v1", null,
                null, false, true, "", List.of(), List.of("semattice_project_delivery_query", "semattice_project_delivery_create", "semattice_project_delivery_review"), List.of(), Map.of()));
        Map<String, Object> principal = principals.create(companyId, actorMemberId, ownerMemberId, displayName, "OFFICIAL_APP",
                OfficialAccessTokenService.SEMATTICE_AUDIENCE, null, pmScopes);
        String principalId = (String) principal.get("principalId");
        execution.configure(companyId, agentId, principalId, true, ownerMemberId);
        resource(activationId, "product_manager", "SERVICE_PRINCIPAL", generatedAlias("product-manager"), displayName, principalId, true);
        resource(activationId, "product_manager", "AGENT", generatedAlias("product-manager-agent"), displayName, agentId, true);
        return principal;
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
    private void resource(String activationId, String role, String type, String alias, String name, String externalId, boolean primary) {
        jdbc.update("""
                INSERT INTO tenant_application_resource(id,activation_id,logical_role,resource_type,resource_alias,display_name,external_id,lifecycle_state,is_primary)
                VALUES (?,?,?,?,?,?,?,'ACTIVE',?)
                """, UUID.randomUUID().toString(), activationId, role, type, alias, name, externalId, primary);
    }

    private TeamResourceView teamResource(String activationId, Map<String, Object> principal) {
        String principalId = (String) principal.get("principalId");
        ResourceView resource = jdbc.queryForObject("""
                SELECT resource.logical_role,resource.resource_type,resource.resource_alias,
                       COALESCE(authority.display_name,resource.display_name),resource.external_id,
                       COALESCE(authority.lifecycle_status,'DISABLED'),resource.is_primary
                FROM tenant_application_resource resource
                LEFT JOIN principal authority ON authority.id=resource.external_id
                WHERE resource.activation_id=? AND resource.external_id=?
                """,
                (rs, n) -> new ResourceView(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getBoolean(7)), activationId, principalId);
        return new TeamResourceView(resource, principalId, (String) principal.get("clientId"), (String) principal.get("clientSecret"),
                (String) principal.get("credentialNotice"));
    }

    private static String generatedAlias(String role) {
        return role + "-" + UUID.randomUUID().toString().substring(0, 8);
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
                SELECT id,company_id,template_version,idempotency_key,desired_state,actual_state,semattice_tenant_id,metadata_version_id,metadata_digest,last_error_code
                FROM tenant_application_activation WHERE company_id=? AND app_code='devautopilot'
                """, (rs, n) -> new Row(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getString(9),rs.getString(10)), companyId);
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
                       resource.is_primary
                FROM tenant_application_resource resource
                LEFT JOIN principal authority ON authority.id=resource.external_id
                WHERE resource.activation_id=?
                ORDER BY resource.logical_role,resource.resource_type
                """, (rs,n)->new ResourceView(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getBoolean(7)), row.id());
        return new View(row.companyId(), true, row.templateVersion(), row.desiredState(), row.actualState(), row.sematticeTenantId(), row.metadataVersionId(), row.metadataDigest(), row.lastError(), resources);
    }
    private static void require(String v,String name) { if (v==null||v.isBlank()) throw new IllegalArgumentException(name+" is required"); }
    private record Row(String id,String companyId,String templateVersion,String idempotencyKey,String desiredState,String actualState,String sematticeTenantId,String metadataVersionId,String metadataDigest,String lastError) { }
    private record ServiceResource(String id, String externalId, String lifecycleState) { }
    private record BindingTarget(String agentId, String servicePrincipalId) { }
    public record ActivationCommand(String idempotencyKey) { }
    public record ResourceView(String logicalRole,String resourceType,String resourceAlias,String displayName,String externalId,String lifecycleState,boolean primary) { }
    public record TeamResourceView(ResourceView resource, String principalId, String clientId, String clientSecret, String credentialNotice) { }
    public record View(String companyId,boolean enabled,String templateVersion,String desiredState,String actualState,String sematticeTenantId,String metadataVersionId,String metadataDigest,String lastErrorCode,List<ResourceView> resources) {
        static View notEnabled(String companyId) { return new View(companyId,false,null,"NOT_ENABLED","NOT_ENABLED",null,null,null,null,List.of()); }
    }
}
