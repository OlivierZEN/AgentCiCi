package com.codehouse.ciciassistant.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.agent.service.AgentDefinitionService;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.agent.service.AgentServicePrincipalExecutionService;
import com.codehouse.ciciassistant.auth.domain.CompanyRepository;
import com.codehouse.ciciassistant.auth.service.ServicePrincipalService;
import com.codehouse.ciciassistant.semattice.SematticeDevAutopilotAuthorizationClient;
import com.codehouse.ciciassistant.semattice.SematticeDevAutopilotTemplateClient;
import com.codehouse.ciciassistant.semattice.SematticeProvisioningService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

class DevAutopilotTenantApplicationReadinessTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final DevAutopilotTenantApplicationService service = new DevAutopilotTenantApplicationService(
            jdbc,
            mock(CompanyRepository.class),
            mock(SematticeProvisioningService.class),
            mock(SematticeDevAutopilotTemplateClient.class),
            mock(SematticeDevAutopilotAuthorizationClient.class),
            mock(ServicePrincipalService.class),
            mock(AgentDefinitionService.class),
            mock(DevAutopilotProductManagerAgentPublisher.class),
            mock(AgentServicePrincipalExecutionService.class),
            mock(PlatformAuditService.class),
            List.of("runtime.record.read"),
            List.of("runtime.record.read"));

    @Test
    void reportsIncompleteWhenProductManagerAgentIsStillAnUnpublishedDraft() {
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(Boolean.class),
                org.mockito.ArgumentMatchers.eq("company-a"),
                org.mockito.ArgumentMatchers.eq("activation-a"),
                org.mockito.ArgumentMatchers.eq("activation-a"),
                org.mockito.ArgumentMatchers.eq("activation-a"),
                org.mockito.ArgumentMatchers.eq("company-a"),
                org.mockito.ArgumentMatchers.eq(SematticeDevAutopilotAuthorizationClient.TEMPLATE_VERSION)))
                .thenReturn(false);

        assertThat(service.initializationReady("company-a", "activation-a")).isFalse();
    }

    @Test
    void reportsReadyOnlyAfterPublishedWebAgentAndServiceIdentityExist() {
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(Boolean.class),
                org.mockito.ArgumentMatchers.eq("company-a"),
                org.mockito.ArgumentMatchers.eq("activation-a"),
                org.mockito.ArgumentMatchers.eq("activation-a"),
                org.mockito.ArgumentMatchers.eq("activation-a"),
                org.mockito.ArgumentMatchers.eq("company-a"),
                org.mockito.ArgumentMatchers.eq(SematticeDevAutopilotAuthorizationClient.TEMPLATE_VERSION)))
                .thenReturn(true);

        assertThat(service.initializationReady("company-a", "activation-a")).isTrue();

        ArgumentCaptor<String> readinessSql = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(jdbc).queryForObject(readinessSql.capture(),
                org.mockito.ArgumentMatchers.eq(Boolean.class),
                org.mockito.ArgumentMatchers.eq("company-a"),
                org.mockito.ArgumentMatchers.eq("activation-a"),
                org.mockito.ArgumentMatchers.eq("activation-a"),
                org.mockito.ArgumentMatchers.eq("activation-a"),
                org.mockito.ArgumentMatchers.eq("company-a"),
                org.mockito.ArgumentMatchers.eq(SematticeDevAutopilotAuthorizationClient.TEMPLATE_VERSION));
        assertThat(readinessSql.getValue())
                .contains("agent_skill_binding", "semattice-project-delivery-management",
                        "agent_workflow_skill_ref", "skill_version.publish_status='PUBLISHED'",
                        "authorization_template_version", "authorization_verified_at");
    }

    @Test
    void registersApplicationResourcesBeforeDerivingTheExecutionDelegationPolicy() {
        ServicePrincipalService principals = mock(ServicePrincipalService.class);
        AgentServicePrincipalExecutionService execution = mock(AgentServicePrincipalExecutionService.class);
        AgentDefinitionService agents = mock(AgentDefinitionService.class);
        DevAutopilotTenantApplicationService orderedService = new DevAutopilotTenantApplicationService(
                jdbc,
                mock(CompanyRepository.class),
                mock(SematticeProvisioningService.class),
                mock(SematticeDevAutopilotTemplateClient.class),
                mock(SematticeDevAutopilotAuthorizationClient.class),
                principals,
                agents,
                mock(DevAutopilotProductManagerAgentPublisher.class),
                execution,
                mock(PlatformAuditService.class),
                List.of("runtime.record.read"),
                List.of("runtime.record.read"));
        when(principals.create(eq("company-a"), eq("owner-member"), eq("owner-member"),
                eq("研发产品经理"), eq("OFFICIAL_APP"), anyString(), any(), any()))
                .thenReturn(Map.of("principalId", "service-principal-a"));
        when(agents.get("company-a", "agent-a"))
                .thenThrow(new com.codehouse.ciciassistant.common.error.ResourceNotFoundException("missing"));

        ReflectionTestUtils.invokeMethod(orderedService, "createProductManagerResources",
                "company-a", "activation-a", "研发产品经理", "owner-member", "owner-member", "agent-a");

        ArgumentCaptor<AgentDefinitionService.CreateCommand> create =
                ArgumentCaptor.forClass(AgentDefinitionService.CreateCommand.class);
        verify(agents).create(eq("company-a"), create.capture());
        assertThat(create.getValue().systemPrompt())
                .isEqualTo(DevAutopilotProductManagerAgentPublisher.STANDARD_SYSTEM_PROMPT);
        assertThat(create.getValue().specText())
                .isEqualTo(DevAutopilotProductManagerAgentPublisher.STANDARD_SPEC);
        InOrder order = inOrder(jdbc, execution);
        order.verify(jdbc, times(2)).update(contains("INSERT INTO tenant_application_resource"), any(Object[].class));
        order.verify(execution).configure("company-a", "agent-a", "service-principal-a", true, "owner-member");
        verify(execution).configure("company-a", "agent-a", "service-principal-a", true, "owner-member");
    }

    @Test
    void reusesManagedAgentLeftByAnInterruptedActivationAttempt() {
        ServicePrincipalService principals = mock(ServicePrincipalService.class);
        AgentServicePrincipalExecutionService execution = mock(AgentServicePrincipalExecutionService.class);
        AgentDefinitionService agents = mock(AgentDefinitionService.class);
        DevAutopilotProductManagerAgentPublisher publisher = mock(DevAutopilotProductManagerAgentPublisher.class);
        DevAutopilotTenantApplicationService retryableService = new DevAutopilotTenantApplicationService(
                jdbc, mock(CompanyRepository.class), mock(SematticeProvisioningService.class),
                mock(SematticeDevAutopilotTemplateClient.class), mock(SematticeDevAutopilotAuthorizationClient.class),
                principals, agents, publisher, execution, mock(PlatformAuditService.class),
                List.of("runtime.record.read"), List.of("runtime.record.read"));
        AgentDefinitionEntity existing = new AgentDefinitionEntity(
                "company-a", "agent-a", "研发产品经理", "DevAutopilot 租户产品经理", "", "gpt-4.1",
                DevAutopilotProductManagerAgentPublisher.STANDARD_SYSTEM_PROMPT, "高风险操作必须确认",
                "standard", "copilot", "devautopilot.standard.v1", null, false, true);
        when(agents.get("company-a", "agent-a")).thenReturn(new AgentDefinitionService.AgentDetail(
                existing, DevAutopilotProductManagerAgentPublisher.STANDARD_SPEC, List.of(), List.of(),
                List.of("web"), Map.of()));
        when(principals.create(eq("company-a"), eq("owner-member"), eq("owner-member"),
                eq("研发产品经理"), eq("OFFICIAL_APP"), anyString(), any(), any()))
                .thenReturn(Map.of("principalId", "service-principal-a"));

        ReflectionTestUtils.invokeMethod(retryableService, "createProductManagerResources",
                "company-a", "activation-a", "研发产品经理", "owner-member", "owner-member", "agent-a");

        verify(agents, org.mockito.Mockito.never()).create(anyString(), any());
        verify(publisher).ensurePublished("company-a", "agent-a");
        verify(execution).configure("company-a", "agent-a", "service-principal-a", true, "owner-member");
    }

    @Test
    void usesTheGovernedExecutionScopesWhenDeploymentOverridesAreAbsent() {
        ServicePrincipalService principals = mock(ServicePrincipalService.class);
        AgentServicePrincipalExecutionService execution = mock(AgentServicePrincipalExecutionService.class);
        AgentDefinitionService agents = mock(AgentDefinitionService.class);
        DevAutopilotTenantApplicationService defaultedService = new DevAutopilotTenantApplicationService(
                jdbc, mock(CompanyRepository.class), mock(SematticeProvisioningService.class),
                mock(SematticeDevAutopilotTemplateClient.class), mock(SematticeDevAutopilotAuthorizationClient.class),
                principals, agents, mock(DevAutopilotProductManagerAgentPublisher.class),
                execution, mock(PlatformAuditService.class), List.of(), List.of());
        when(principals.create(eq("company-a"), eq("owner-member"), eq("owner-member"),
                eq("研发产品经理"), eq("OFFICIAL_APP"), anyString(), any(), any()))
                .thenReturn(Map.of("principalId", "service-principal-a"));
        when(agents.get("company-a", "agent-a"))
                .thenThrow(new com.codehouse.ciciassistant.common.error.ResourceNotFoundException("missing"));

        ReflectionTestUtils.invokeMethod(defaultedService, "createProductManagerResources",
                "company-a", "activation-a", "研发产品经理", "owner-member", "owner-member", "agent-a");

        verify(principals).create(eq("company-a"), eq("owner-member"), eq("owner-member"),
                eq("研发产品经理"), eq("OFFICIAL_APP"), anyString(), any(),
                eq(DevAutopilotTenantApplicationService.STANDARD_EXECUTION_SCOPES));
    }

    @Test
    void reappliesAndPersistsTheCurrentSevenObjectMetadataBaselineDuringReconciliation() {
        SematticeDevAutopilotTemplateClient template = mock(SematticeDevAutopilotTemplateClient.class);
        JdbcTemplate metadataJdbc = mock(JdbcTemplate.class);
        DevAutopilotTenantApplicationService metadataService = service(metadataJdbc, template);
        String key = DevAutopilotTenantApplicationService.metadataReconciliationKey("activation-a");
        when(template.apply("company-a", key)).thenReturn(new SematticeDevAutopilotTemplateClient.TemplateView(
                "company-a", "tenant-a", "metadata-v2", "digest-v2", 7, 86, "applied"));

        var result = metadataService.reconcileMetadataBaseline("company-a", "activation-a");

        assertThat(result.metadataVersionId()).isEqualTo("metadata-v2");
        verify(metadataJdbc).update(contains("metadata_version_id=?"),
                eq("metadata-v2"), eq("digest-v2"), eq("activation-a"));
    }

    @Test
    void refusesToMarkInitializationCompleteWhenSematticeReturnsTheOldSixObjectShape() {
        SematticeDevAutopilotTemplateClient template = mock(SematticeDevAutopilotTemplateClient.class);
        JdbcTemplate metadataJdbc = mock(JdbcTemplate.class);
        DevAutopilotTenantApplicationService metadataService = service(metadataJdbc, template);
        when(template.apply(eq("company-a"), anyString())).thenReturn(new SematticeDevAutopilotTemplateClient.TemplateView(
                "company-a", "tenant-a", "metadata-v1", "digest-v1", 6, 60, "already_applied"));

        assertThatThrownBy(() -> metadataService.reconcileMetadataBaseline("company-a", "activation-a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("metadata baseline is incomplete");
        verifyNoInteractions(metadataJdbc);
    }

    @Test
    void authorizationReconciliationKeyIsStableAndChangesWithTheAuthoritativeAssignmentSet() {
        var owner = new SematticeDevAutopilotAuthorizationClient.Assignment("human-owner", "application_admin");
        var productManager = new SematticeDevAutopilotAuthorizationClient.Assignment("service-pm", "product_manager");
        String ordered = DevAutopilotTenantApplicationService.authorizationReconciliationKey(
                "11111111-1111-4111-8111-111111111111", List.of(owner, productManager));
        String reversed = DevAutopilotTenantApplicationService.authorizationReconciliationKey(
                "11111111-1111-4111-8111-111111111111", List.of(productManager, owner));
        String withDeveloper = DevAutopilotTenantApplicationService.authorizationReconciliationKey(
                "11111111-1111-4111-8111-111111111111", List.of(owner, productManager,
                        new SematticeDevAutopilotAuthorizationClient.Assignment("service-dev", "developer")));

        assertThat(ordered).isEqualTo(reversed).hasSizeLessThanOrEqualTo(96);
        assertThat(withDeveloper).isNotEqualTo(ordered).hasSizeLessThanOrEqualTo(96);
        assertThat(ordered).startsWith(SematticeDevAutopilotAuthorizationClient.TEMPLATE_VERSION + ":");
    }

    @Test
    void authorizationAssignmentsIncludeActiveTenantAndExplicitApplicationAdministrators() {
        when(jdbc.query(contains("SELECT DISTINCT member.account_id"), any(RowMapper.class),
                eq("activation-a"), eq("company-a"))).thenReturn(List.of(
                new SematticeDevAutopilotAuthorizationClient.Assignment("human-owner", "application_admin"),
                new SematticeDevAutopilotAuthorizationClient.Assignment("human-org-admin", "application_admin"),
                new SematticeDevAutopilotAuthorizationClient.Assignment("human-explicit-admin", "application_admin")));
        when(jdbc.query(contains("SELECT external_id,logical_role"), any(RowMapper.class),
                eq("activation-a"))).thenReturn(List.of(
                new SematticeDevAutopilotAuthorizationClient.Assignment("service-pm", "product_manager"),
                new SematticeDevAutopilotAuthorizationClient.Assignment("service-developer", "developer")));

        var assignments = service.authorizationAssignments("company-a", "activation-a");

        assertThat(assignments).extracting(SematticeDevAutopilotAuthorizationClient.Assignment::principalId)
                .containsExactly("human-explicit-admin", "human-org-admin", "human-owner", "service-developer", "service-pm");
        verify(jdbc).query(org.mockito.ArgumentMatchers.argThat(sql -> sql.contains("member.member_status='ACTIVE'")
                        && sql.contains("member.role_code IN ('OWNER','ORG_ADMIN')")
                        && sql.contains("app_access.role_code='APP_ADMIN'")
                        && sql.contains("app_access.status='ACTIVE'")),
                any(RowMapper.class), eq("activation-a"), eq("company-a"));
    }

    @Test
    void authorizationAssignmentsFailClosedWithoutAnActiveHumanAdministrator() {
        when(jdbc.query(contains("SELECT DISTINCT member.account_id"), any(RowMapper.class),
                eq("activation-a"), eq("company-a"))).thenReturn(List.of());

        assertThatThrownBy(() -> service.authorizationAssignments("company-a", "activation-a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DevAutopilot application administrator is unavailable");
    }

    @Test
    void authorizationReconciliationProjectsEveryHumanAdministratorBeforeApplyingTheTemplate() {
        JdbcTemplate authorizationJdbc = mock(JdbcTemplate.class);
        ServicePrincipalService principals = mock(ServicePrincipalService.class);
        SematticeDevAutopilotAuthorizationClient authorization = mock(SematticeDevAutopilotAuthorizationClient.class);
        DevAutopilotTenantApplicationService authorizationService = new DevAutopilotTenantApplicationService(
                authorizationJdbc, mock(CompanyRepository.class), mock(SematticeProvisioningService.class),
                mock(SematticeDevAutopilotTemplateClient.class), authorization, principals,
                mock(AgentDefinitionService.class), mock(DevAutopilotProductManagerAgentPublisher.class),
                mock(AgentServicePrincipalExecutionService.class), mock(PlatformAuditService.class),
                List.of("runtime.record.read"), List.of("runtime.record.read"));
        when(authorizationJdbc.query(contains("SELECT DISTINCT member.account_id"), any(RowMapper.class),
                eq("activation-a"), eq("company-a"))).thenReturn(List.of(
                new SematticeDevAutopilotAuthorizationClient.Assignment("human-owner", "application_admin"),
                new SematticeDevAutopilotAuthorizationClient.Assignment("human-org-admin", "application_admin")));
        when(authorizationJdbc.query(contains("SELECT external_id,logical_role"), any(RowMapper.class),
                eq("activation-a"))).thenReturn(List.of(
                new SematticeDevAutopilotAuthorizationClient.Assignment("service-pm", "product_manager")));
        when(authorization.apply(eq("company-a"), eq("activation-a"), anyString(), any())).thenReturn(
                new SematticeDevAutopilotAuthorizationClient.AuthorizationView(
                        "company-a", "tenant-a", SematticeDevAutopilotAuthorizationClient.TEMPLATE_VERSION,
                        "a".repeat(64), 4, 4, 7, 3, true, "applied"));

        authorizationService.reconcileAuthorizationTemplate("company-a", "activation-a");

        InOrder order = inOrder(principals, authorization);
        order.verify(principals).synchronizeHumanProjection("company-a", "human-org-admin");
        order.verify(principals).synchronizeHumanProjection("company-a", "human-owner");
        order.verify(authorization).apply(eq("company-a"), eq("activation-a"), anyString(), any());
    }

    @Test
    void activationStagesResumeStrictlyAfterTheLastDurableCheckpoint() {
        assertThat(DevAutopilotTenantApplicationService.stageBefore("PROVISIONING", "METADATA_READY")).isTrue();
        assertThat(DevAutopilotTenantApplicationService.stageBefore("METADATA_READY", "METADATA_READY")).isFalse();
        assertThat(DevAutopilotTenantApplicationService.stageBefore("PRINCIPALS_READY", "AUTHORIZATION_READY")).isTrue();
        assertThat(DevAutopilotTenantApplicationService.stageBefore("AUTHORIZATION_READY", "PRINCIPALS_READY")).isFalse();
        assertThat(DevAutopilotTenantApplicationService.stageBefore("ACTIVE", "AUTHORIZATION_READY")).isFalse();
    }

    @Test
    void preservesStableSchemaContractFailuresForRecoveryAndHumanDiagnosis() {
        RuntimeException migrationRequired = new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Semattice request failed: HTTP 503 SCHEMA_MIGRATION_REQUIRED");
        RuntimeException drift = new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Semattice request failed: HTTP 503 SCHEMA_MIGRATION_DRIFT");

        assertThat(DevAutopilotTenantApplicationService.activationFailureCode("AUTHORIZATION_READY", migrationRequired))
                .isEqualTo("SCHEMA_MIGRATION_REQUIRED");
        assertThat(DevAutopilotTenantApplicationService.activationFailureCode("AUTHORIZATION_READY", drift))
                .isEqualTo("SCHEMA_MIGRATION_DRIFT");
        assertThat(DevAutopilotTenantApplicationService.activationFailureCode(
                "AUTHORIZATION_READY", new IllegalStateException("remote unavailable")))
                .isEqualTo("ACTIVATION_AUTHORIZATION_READY_FAILED");
    }

    private DevAutopilotTenantApplicationService service(JdbcTemplate targetJdbc,
                                                          SematticeDevAutopilotTemplateClient template) {
        return new DevAutopilotTenantApplicationService(
                targetJdbc,
                mock(CompanyRepository.class),
                mock(SematticeProvisioningService.class),
                template,
                mock(SematticeDevAutopilotAuthorizationClient.class),
                mock(ServicePrincipalService.class),
                mock(AgentDefinitionService.class),
                mock(DevAutopilotProductManagerAgentPublisher.class),
                mock(AgentServicePrincipalExecutionService.class),
                mock(PlatformAuditService.class),
                List.of("runtime.record.read"),
                List.of("runtime.record.read"));
    }
}
