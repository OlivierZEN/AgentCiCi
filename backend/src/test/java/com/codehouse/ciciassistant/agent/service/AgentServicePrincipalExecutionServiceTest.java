package com.codehouse.ciciassistant.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.agent.domain.AgentPermission;
import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.platform.service.PlatformAuditService;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class AgentServicePrincipalExecutionServiceTest {

    private static final String SERVICE_ID = "11111111-1111-4111-8111-111111111111";
    private static final String TENANT_ID = "22222222-2222-4222-8222-222222222222";
    private static final String OWNER_ID = "33333333-3333-4333-8333-333333333333";
    private static final String ACTOR_ID = "44444444-4444-4444-8444-444444444444";

    private JdbcTemplate jdbc;
    private OfficialAccessTokenService tokens;
    private PlatformAuditService audit;
    private AgentAccessControlService access;
    private AgentServicePrincipalExecutionService service;
    private String actorRole;
    private String appRole;
    private boolean healthyBinding;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() throws Exception {
        jdbc = mock(JdbcTemplate.class);
        tokens = mock(OfficialAccessTokenService.class);
        audit = mock(PlatformAuditService.class);
        access = mock(AgentAccessControlService.class);
        service = new AgentServicePrincipalExecutionService(jdbc, tokens, audit, access);
        actorRole = RoleCodes.ORG_ADMIN;
        appRole = null;
        healthyBinding = true;

        when(access.can(eq("org-1"), eq("member-1"), any(), eq("devautopilot-pm"), eq(AgentPermission.RUN)))
                .thenReturn(true);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            RowMapper mapper = invocation.getArgument(1, RowMapper.class);
            if (sql.contains("FROM agent_service_principal_binding binding") && sql.contains("service_binding")) {
                if (!healthyBinding) return List.of();
                ResultSet row = mock(ResultSet.class);
                when(row.getString("service_principal_id")).thenReturn(SERVICE_ID);
                when(row.getString("delegation_policy")).thenReturn("TENANT_APP_ROLE");
                when(row.getString("client_id")).thenReturn("dev-autopilot-product-manager");
                when(row.getString("semattice_tenant_id")).thenReturn(TENANT_ID);
                when(row.getString("owner_principal_id")).thenReturn(OWNER_ID);
                when(row.getString("display_name")).thenReturn("DEV Autopilot 产品经理");
                return List.of(mapper.mapRow(row, 0));
            }
            if (sql.contains("FROM company_member member") && sql.contains("principal principal")) {
                ResultSet row = mock(ResultSet.class);
                when(row.getString("id")).thenReturn("member-1");
                when(row.getString("account_id")).thenReturn(ACTOR_ID);
                when(row.getString("role_code")).thenAnswer(ignored -> actorRole);
                return List.of(mapper.mapRow(row, 0));
            }
            return List.of();
        });
        when(jdbc.queryForList(anyString(), eq(String.class), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            if (sql.contains("service_principal_scope")) {
                return List.of("runtime.record.create", "runtime.record.read", "runtime.record.update", "runtime.record.delete");
            }
            if (sql.contains("tenant_application_member_role")) {
                return appRole == null ? List.of() : List.of(appRole);
            }
            return List.of();
        });
    }

    @Test
    void orgAdminCanDelegateWithoutBeingTheGovernanceOwnerAndBothPrincipalsAreAudited() {
        OfficialAccessTokenService.IssuedToken issued = issuedToken(List.of("runtime.record.read", "runtime.record.create"));
        when(tokens.issueForSematticeService(
                SERVICE_ID, OWNER_ID, "dev-autopilot-product-manager", TENANT_ID, "org-1",
                List.of("runtime.record.read", "runtime.record.create"), ACTOR_ID, "TENANT_APP_ROLE"))
                .thenReturn(issued);

        AgentServicePrincipalExecutionService.ExecutionAuthorization authorization = service.authorizeSemattice(
                "org-1", "member-1", "devautopilot-pm",
                List.of("runtime.record.read", "runtime.record.create"), "semattice_project_delivery_create");

        assertThat(authorization.token().token()).isEqualTo("service-oact");
        assertThat(authorization.ownerPrincipalId()).isEqualTo(OWNER_ID);
        assertThat(authorization.delegatedByPrincipalId()).isEqualTo(ACTOR_ID);
        assertThat(authorization.effectiveAppRole()).isEqualTo("APP_ADMIN");
        verify(audit).log(eq("org-1"), eq(ACTOR_ID), eq(RoleCodes.ORG_ADMIN),
                eq("agent.service_principal.delegated"), eq("service_principal"), eq(SERVICE_ID),
                org.mockito.ArgumentMatchers.contains("ownerPrincipalId=" + OWNER_ID));
    }

    @Test
    void explicitContributorCanCreateButCannotReview() {
        actorRole = RoleCodes.ORG_USER;
        appRole = "CONTRIBUTOR";
        when(tokens.issueForSematticeService(anyString(), anyString(), anyString(), anyString(), anyString(),
                any(), anyString(), anyString())).thenReturn(issuedToken(List.of("runtime.record.read", "runtime.record.create")));

        assertThat(service.authorizeSemattice(
                "org-1", "member-1", "devautopilot-pm",
                List.of("runtime.record.read", "runtime.record.create"), "semattice_project_delivery_create")
                .effectiveAppRole()).isEqualTo("CONTRIBUTOR");

        assertThatThrownBy(() -> service.authorizeSemattice(
                "org-1", "member-1", "devautopilot-pm",
                List.of("runtime.record.read", "runtime.record.create", "runtime.record.update"),
                "semattice_project_delivery_review"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("REVIEWER");
    }

    @Test
    void viewerCannotCreateAndMemberWithoutApplicationRoleGetsActionableMessage() {
        actorRole = RoleCodes.ORG_USER;
        appRole = "VIEWER";
        assertThatThrownBy(() -> service.authorizeSemattice(
                "org-1", "member-1", "devautopilot-pm",
                List.of("runtime.record.read", "runtime.record.create"), "semattice_project_delivery_create"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("CONTRIBUTOR");

        appRole = null;
        assertThatThrownBy(() -> service.authorizeSemattice(
                "org-1", "member-1", "devautopilot-pm",
                List.of("runtime.record.read"), "semattice_project_delivery_query"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("DevAutopilot 应用角色");
    }

    @Test
    void failsClosedWithSpecificIdentityMessageWhenBindingIsUnhealthy() {
        healthyBinding = false;
        assertThatThrownBy(() -> service.authorizeSemattice(
                "org-1", "member-1", "devautopilot-pm", List.of("runtime.record.read"),
                "semattice_project_delivery_query"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("机器执行身份不可用");
    }

    private OfficialAccessTokenService.IssuedToken issuedToken(List<String> scopes) {
        return new OfficialAccessTokenService.IssuedToken(
                "service-oact", Instant.now().plusSeconds(300), TENANT_ID, "org-1", scopes);
    }
}
