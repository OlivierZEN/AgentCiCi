package com.codehouse.ciciassistant.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.platform.service.PlatformAuditService;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class AgentServicePrincipalExecutionServiceTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void issuesLeastPrivilegeServiceTokenForThePrimaryOwnerDelegation() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        OfficialAccessTokenService tokens = mock(OfficialAccessTokenService.class);
        PlatformAuditService audit = mock(PlatformAuditService.class);
        ResultSet row = mock(ResultSet.class);
        when(row.getString("service_principal_id")).thenReturn("11111111-1111-4111-8111-111111111111");
        when(row.getString("delegation_policy")).thenReturn("PRIMARY_OWNER");
        when(row.getString("client_id")).thenReturn("dev-autopilot-product-manager");
        when(row.getString("semattice_tenant_id")).thenReturn("22222222-2222-4222-8222-222222222222");
        when(row.getString("owner_principal_id")).thenReturn("33333333-3333-4333-8333-333333333333");
        when(row.getString("display_name")).thenReturn("DEV Autopilot 产品经理");
        doAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(row, 0));
        }).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));
        when(jdbc.queryForList(anyString(), eq(String.class), any(Object[].class)))
                .thenReturn(List.of("runtime.record.create", "runtime.record.read"));
        OfficialAccessTokenService.IssuedToken issued = new OfficialAccessTokenService.IssuedToken(
                "service-oact", Instant.now().plusSeconds(300),
                "22222222-2222-4222-8222-222222222222", "org-1", List.of("runtime.record.read"));
        when(tokens.issueForSematticeService(
                "11111111-1111-4111-8111-111111111111",
                "33333333-3333-4333-8333-333333333333",
                "dev-autopilot-product-manager",
                "22222222-2222-4222-8222-222222222222",
                "org-1",
                List.of("runtime.record.read"),
                "33333333-3333-4333-8333-333333333333",
                "PRIMARY_OWNER"))
                .thenReturn(issued);

        AgentServicePrincipalExecutionService service =
                new AgentServicePrincipalExecutionService(jdbc, tokens, audit);
        AgentServicePrincipalExecutionService.ExecutionAuthorization authorization = service.authorizeSemattice(
                "org-1", "member-1", "dev-autopilot-pm", List.of("runtime.record.read"),
                "semattice_project_delivery_query");

        assertThat(authorization.token().token()).isEqualTo("service-oact");
        assertThat(authorization.servicePrincipalDisplayName()).isEqualTo("DEV Autopilot 产品经理");
        assertThat(authorization.delegationPolicy()).isEqualTo("PRIMARY_OWNER");
        verify(tokens).issueForSematticeService(
                "11111111-1111-4111-8111-111111111111",
                "33333333-3333-4333-8333-333333333333",
                "dev-autopilot-product-manager",
                "22222222-2222-4222-8222-222222222222",
                "org-1",
                List.of("runtime.record.read"),
                "33333333-3333-4333-8333-333333333333",
                "PRIMARY_OWNER");
        verify(audit).log(eq("org-1"), eq("33333333-3333-4333-8333-333333333333"),
                eq("SERVICE_DELEGATOR"), eq("agent.service_principal.delegated"),
                eq("service_principal"), eq("11111111-1111-4111-8111-111111111111"), anyString());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void failsClosedWhenNoExplicitMachineExecutionBindingCanBeResolved() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        doAnswer(invocation -> List.of())
                .when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));
        AgentServicePrincipalExecutionService service = new AgentServicePrincipalExecutionService(
                jdbc, mock(OfficialAccessTokenService.class), mock(PlatformAuditService.class));

        assertThatThrownBy(() -> service.authorizeSemattice(
                "org-1", "member-1", "dev-autopilot-pm", List.of("runtime.record.read"),
                "semattice_project_delivery_query"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("机器执行身份");
    }
}
