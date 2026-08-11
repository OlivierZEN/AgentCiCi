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
import com.codehouse.ciciassistant.agent.service.AgentServicePrincipalExecutionService;
import com.codehouse.ciciassistant.auth.domain.CompanyRepository;
import com.codehouse.ciciassistant.auth.service.ServicePrincipalService;
import com.codehouse.ciciassistant.semattice.SematticeDevAutopilotTemplateClient;
import com.codehouse.ciciassistant.semattice.SematticeProvisioningService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class DevAutopilotTenantApplicationReadinessTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final DevAutopilotTenantApplicationService service = new DevAutopilotTenantApplicationService(
            jdbc,
            mock(CompanyRepository.class),
            mock(SematticeProvisioningService.class),
            mock(SematticeDevAutopilotTemplateClient.class),
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
                org.mockito.ArgumentMatchers.eq("activation-a")))
                .thenReturn(false);

        assertThat(service.initializationReady("company-a", "activation-a")).isFalse();
    }

    @Test
    void reportsReadyOnlyAfterPublishedWebAgentAndServiceIdentityExist() {
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(Boolean.class),
                org.mockito.ArgumentMatchers.eq("company-a"),
                org.mockito.ArgumentMatchers.eq("activation-a"),
                org.mockito.ArgumentMatchers.eq("activation-a")))
                .thenReturn(true);

        assertThat(service.initializationReady("company-a", "activation-a")).isTrue();

        ArgumentCaptor<String> readinessSql = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(jdbc).queryForObject(readinessSql.capture(),
                org.mockito.ArgumentMatchers.eq(Boolean.class),
                org.mockito.ArgumentMatchers.eq("company-a"),
                org.mockito.ArgumentMatchers.eq("activation-a"),
                org.mockito.ArgumentMatchers.eq("activation-a"));
        assertThat(readinessSql.getValue())
                .contains("agent_skill_binding", "semattice-project-delivery-management",
                        "agent_workflow_skill_ref", "skill_version.publish_status='PUBLISHED'");
    }

    @Test
    void registersApplicationResourcesBeforeDerivingTheExecutionDelegationPolicy() {
        ServicePrincipalService principals = mock(ServicePrincipalService.class);
        AgentServicePrincipalExecutionService execution = mock(AgentServicePrincipalExecutionService.class);
        DevAutopilotTenantApplicationService orderedService = new DevAutopilotTenantApplicationService(
                jdbc,
                mock(CompanyRepository.class),
                mock(SematticeProvisioningService.class),
                mock(SematticeDevAutopilotTemplateClient.class),
                principals,
                mock(AgentDefinitionService.class),
                mock(DevAutopilotProductManagerAgentPublisher.class),
                execution,
                mock(PlatformAuditService.class),
                List.of("runtime.record.read"),
                List.of("runtime.record.read"));
        when(principals.create(eq("company-a"), eq("owner-member"), eq("owner-member"),
                eq("研发产品经理"), eq("OFFICIAL_APP"), anyString(), any(), any()))
                .thenReturn(Map.of("principalId", "service-principal-a"));

        ReflectionTestUtils.invokeMethod(orderedService, "createProductManagerResources",
                "company-a", "activation-a", "研发产品经理", "owner-member", "owner-member", "agent-a");

        InOrder order = inOrder(jdbc, execution);
        order.verify(jdbc, times(2)).update(contains("INSERT INTO tenant_application_resource"), any(Object[].class));
        order.verify(execution).configure("company-a", "agent-a", "service-principal-a", true, "owner-member");
        verify(execution).configure("company-a", "agent-a", "service-principal-a", true, "owner-member");
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

    private DevAutopilotTenantApplicationService service(JdbcTemplate targetJdbc,
                                                          SematticeDevAutopilotTemplateClient template) {
        return new DevAutopilotTenantApplicationService(
                targetJdbc,
                mock(CompanyRepository.class),
                mock(SematticeProvisioningService.class),
                template,
                mock(ServicePrincipalService.class),
                mock(AgentDefinitionService.class),
                mock(DevAutopilotProductManagerAgentPublisher.class),
                mock(AgentServicePrincipalExecutionService.class),
                mock(PlatformAuditService.class),
                List.of("runtime.record.read"),
                List.of("runtime.record.read"));
    }
}
