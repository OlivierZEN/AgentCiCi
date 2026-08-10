package com.codehouse.ciciassistant.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.agent.service.AgentDefinitionService;
import com.codehouse.ciciassistant.agent.service.AgentServicePrincipalExecutionService;
import com.codehouse.ciciassistant.auth.domain.CompanyRepository;
import com.codehouse.ciciassistant.auth.service.ServicePrincipalService;
import com.codehouse.ciciassistant.semattice.SematticeDevAutopilotTemplateClient;
import com.codehouse.ciciassistant.semattice.SematticeProvisioningService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

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
}
