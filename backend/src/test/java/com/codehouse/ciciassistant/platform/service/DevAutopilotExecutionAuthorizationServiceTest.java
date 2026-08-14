package com.codehouse.ciciassistant.platform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.agent.service.AgentServicePrincipalExecutionService;
import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DevAutopilotExecutionAuthorizationServiceTest {
    private static final String COMPANY_ID = "company-a";
    private static final String TENANT_ID = "tenant-a";
    private static final String MEMBER_ID = "member-a";
    private static final String AGENT_ID = "pm-agent-a";
    private static final String SERVICE_ID = "00000000-0000-4000-8000-000000000101";

    @Test
    void requirementConfirmationUsesThePrimaryProductManagerWithFixedScopes() {
        DevAutopilotTenantApplicationService applications = mock(DevAutopilotTenantApplicationService.class);
        AgentServicePrincipalExecutionService executions = mock(AgentServicePrincipalExecutionService.class);
        when(applications.get(COMPANY_ID)).thenReturn(activeApplication());
        List<String> scopes = List.of("runtime.record.read", "runtime.record.update");
        var token = new OfficialAccessTokenService.IssuedToken(
                "test-service-oact", Instant.parse("2030-01-01T00:00:00Z"), TENANT_ID, COMPANY_ID, scopes);
        when(executions.authorizeSemattice(eq(COMPANY_ID), eq(MEMBER_ID), eq(AGENT_ID), eq(scopes),
                eq("devautopilot_requirement_confirm")))
                .thenReturn(new AgentServicePrincipalExecutionService.ExecutionAuthorization(
                        SERVICE_ID, "研发产品经理", "owner-a", "human-a", "TENANT_APP_ROLE", "CONTRIBUTOR", token));

        var result = new DevAutopilotExecutionAuthorizationService(applications, executions)
                .authorize(COMPANY_ID, MEMBER_ID,
                        DevAutopilotExecutionAuthorizationService.Operation.REQUIREMENT_CONFIRM);

        assertEquals(SERVICE_ID, result.servicePrincipalId());
        assertEquals("human-a", result.delegatedByPrincipalId());
        assertEquals(scopes, result.scopes());
        verify(executions).authorizeSemattice(COMPANY_ID, MEMBER_ID, AGENT_ID, scopes,
                "devautopilot_requirement_confirm");
    }

    @Test
    void unknownOperationCannotChooseScopes() {
        DevAutopilotTenantApplicationService applications = mock(DevAutopilotTenantApplicationService.class);
        when(applications.get(COMPANY_ID)).thenReturn(activeApplication());
        var service = new DevAutopilotExecutionAuthorizationService(
                applications, mock(AgentServicePrincipalExecutionService.class));

        assertThrows(IllegalArgumentException.class, () -> service.authorize(
                COMPANY_ID, MEMBER_ID, DevAutopilotExecutionAuthorizationService.Operation.UNKNOWN));
    }

    private static DevAutopilotTenantApplicationService.View activeApplication() {
        return new DevAutopilotTenantApplicationService.View(
                COMPANY_ID, true, "1", "ACTIVE", "ACTIVE", TENANT_ID,
                "metadata-a", "digest-a", true, null,
                List.of(
                        new DevAutopilotTenantApplicationService.ResourceView(
                                "product_manager", "AGENT", "pm-agent", "研发产品经理", AGENT_ID,
                                "ACTIVE", true, 1, 1),
                        new DevAutopilotTenantApplicationService.ResourceView(
                                "product_manager", "SERVICE_PRINCIPAL", "pm-principal", "研发产品经理", SERVICE_ID,
                                "ACTIVE", true, 1, 1)));
    }
}
