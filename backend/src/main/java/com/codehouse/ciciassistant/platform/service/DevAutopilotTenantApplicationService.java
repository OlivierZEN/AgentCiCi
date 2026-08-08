package com.codehouse.ciciassistant.platform.service;

import com.codehouse.ciciassistant.agent.service.AgentDefinitionService;
import com.codehouse.ciciassistant.agent.service.AgentServicePrincipalExecutionService;
import com.codehouse.ciciassistant.auth.domain.CompanyEntity;
import com.codehouse.ciciassistant.auth.domain.CompanyRepository;
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
    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,95}$");
    private static final Pattern ALIAS = Pattern.compile("^[a-z][a-z0-9-]{1,47}$");
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
        if (!KEY.matcher(command.idempotencyKey()).matches() || !ALIAS.matcher(command.productManagerAlias()).matches()) throw new IllegalArgumentException("invalid activation key or resource alias");
        if (pmScopes.isEmpty()) throw new IllegalStateException("DevAutopilot product-manager scopes are not configured");
        var existing = find(companyId);
        if (existing != null) {
            if (!existing.idempotencyKey().equals(command.idempotencyKey()) || !"ACTIVE".equals(existing.actualState())) throw new ConflictException("DevAutopilot activation already exists for this tenant");
            return view(existing);
        }
        var binding = provisioning.getProvisioningStatus(companyId);
        if (!"PROVISIONED".equals(binding.state()) || binding.sematticeTenantId() == null) throw new ForbiddenException("Semattice must be provisioned before enabling DevAutopilot");
        String activationId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO tenant_application_activation(id,company_id,app_code,template_version,idempotency_key,desired_state,actual_state,semattice_tenant_id,created_by_member_id)
                VALUES (?,?,?,?,?,'ACTIVE','PROVISIONING',?,?)
                """, activationId, companyId, APP, SematticeDevAutopilotTemplateClient.TEMPLATE_VERSION,
                command.idempotencyKey(), binding.sematticeTenantId(), command.ownerMemberId());
        try {
            var metadata = template.apply(companyId, command.idempotencyKey());
            Map<String, Object> principal = principals.create(companyId, command.ownerMemberId(), command.productManagerName(),
                    "OFFICIAL_APP", OfficialAccessTokenService.SEMATTICE_AUDIENCE, null, pmScopes);
            String principalId = (String) principal.get("principalId");
            String agentId = "devautopilot-pm-" + UUID.randomUUID().toString().substring(0, 8);
            agents.create(companyId, new AgentDefinitionService.CreateCommand(agentId, command.productManagerName(), "DevAutopilot 租户产品经理", "", "gpt-4.1",
                    "你是本租户的研发产品经理，只能通过已绑定的受控工具处理研发交付。", "高风险操作必须确认", "standard", "copilot", "devautopilot.standard.v1", null,
                    null, false, true, "", List.of(), List.of("semattice_project_delivery_query", "semattice_project_delivery_create", "semattice_project_delivery_review"), List.of(), Map.of()));
            execution.configure(companyId, agentId, principalId, true, command.ownerMemberId());
            resource(activationId, "product_manager", "SERVICE_PRINCIPAL", command.productManagerAlias(), command.productManagerName(), principalId, true);
            resource(activationId, "product_manager", "AGENT", command.productManagerAlias() + "-agent", command.productManagerName(), agentId, true);
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

    @Transactional
    public View suspend(String companyId, String platformActor) { return transition(companyId, "SUSPENDED", platformActor); }
    @Transactional
    public View resume(String companyId, String platformActor) { return transition(companyId, "ACTIVE", platformActor); }

    @Transactional
    public ResourceView addDeveloper(String companyId, DeveloperCommand command, String platformActor) {
        Row activation = requireExisting(companyId);
        if (!"ACTIVE".equals(activation.actualState()) || developerScopes.isEmpty()) throw new ForbiddenException("DevAutopilot is not ready to create a developer identity");
        require(command.displayName(), "displayName");
        if (!ALIAS.matcher(command.resourceAlias()).matches()) throw new IllegalArgumentException("invalid developer resource alias");
        Integer duplicates = jdbc.queryForObject("SELECT count(*) FROM tenant_application_resource WHERE activation_id=? AND resource_alias=?", Integer.class, activation.id(), command.resourceAlias());
        if (duplicates != null && duplicates > 0) throw new ConflictException("developer resource alias already exists in this tenant");
        Map<String, Object> principal = principals.create(companyId, command.ownerMemberId(), command.displayName(), "THIRD_PARTY",
                OfficialAccessTokenService.SEMATTICE_AUDIENCE, null, developerScopes);
        String principalId = (String) principal.get("principalId");
        resource(activation.id(), "developer", "SERVICE_PRINCIPAL", command.resourceAlias(), command.displayName(), principalId, false);
        audit.log(companyId, platformActor, "PLATFORM", "tenant_application.developer_added", "tenant_application", activation.id(), "DevAutopilot developer resource added");
        return jdbc.queryForObject("SELECT logical_role,resource_type,resource_alias,display_name,external_id,lifecycle_state,is_primary FROM tenant_application_resource WHERE activation_id=? AND external_id=?", (rs,n)->new ResourceView(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getBoolean(7)), activation.id(), principalId);
    }

    private View transition(String companyId, String target, String platformActor) {
        Row row = requireExisting(companyId);
        jdbc.update("UPDATE tenant_application_activation SET desired_state=?,actual_state=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", target, target, row.id());
        audit.log(companyId, platformActor, "PLATFORM", "tenant_application." + target.toLowerCase(), "tenant_application", row.id(), "DevAutopilot application state changed");
        return requireView(companyId);
    }
    private void resource(String activationId, String role, String type, String alias, String name, String externalId, boolean primary) {
        jdbc.update("""
                INSERT INTO tenant_application_resource(id,activation_id,logical_role,resource_type,resource_alias,display_name,external_id,lifecycle_state,is_primary)
                VALUES (?,?,?,?,?,?,?,'ACTIVE',?)
                """, UUID.randomUUID().toString(), activationId, role, type, alias, name, externalId, primary);
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
        List<ResourceView> resources = jdbc.query("SELECT logical_role,resource_type,resource_alias,display_name,external_id,lifecycle_state,is_primary FROM tenant_application_resource WHERE activation_id=? ORDER BY logical_role,resource_type", (rs,n)->new ResourceView(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getBoolean(7)), row.id());
        return new View(row.companyId(), true, row.templateVersion(), row.desiredState(), row.actualState(), row.sematticeTenantId(), row.metadataVersionId(), row.metadataDigest(), row.lastError(), resources);
    }
    private static void require(String v,String name) { if (v==null||v.isBlank()) throw new IllegalArgumentException(name+" is required"); }
    private record Row(String id,String companyId,String templateVersion,String idempotencyKey,String desiredState,String actualState,String sematticeTenantId,String metadataVersionId,String metadataDigest,String lastError) { }
    public record ActivationCommand(String idempotencyKey, String productManagerName, String productManagerAlias, String ownerMemberId) { }
    public record DeveloperCommand(String displayName, String resourceAlias, String ownerMemberId) { }
    public record ResourceView(String logicalRole,String resourceType,String resourceAlias,String displayName,String externalId,String lifecycleState,boolean primary) { }
    public record View(String companyId,boolean enabled,String templateVersion,String desiredState,String actualState,String sematticeTenantId,String metadataVersionId,String metadataDigest,String lastErrorCode,List<ResourceView> resources) {
        static View notEnabled(String companyId) { return new View(companyId,false,null,"NOT_ENABLED","NOT_ENABLED",null,null,null,null,List.of()); }
    }
}
