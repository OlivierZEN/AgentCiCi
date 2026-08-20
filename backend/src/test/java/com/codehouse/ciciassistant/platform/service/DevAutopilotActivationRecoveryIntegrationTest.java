package com.codehouse.ciciassistant.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.agent.service.AgentDefinitionService;
import com.codehouse.ciciassistant.agent.service.AgentServicePrincipalExecutionService;
import com.codehouse.ciciassistant.auth.domain.CompanyEntity;
import com.codehouse.ciciassistant.auth.domain.CompanyRepository;
import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import com.codehouse.ciciassistant.auth.service.ServicePrincipalService;
import com.codehouse.ciciassistant.semattice.SematticeDevAutopilotAuthorizationClient;
import com.codehouse.ciciassistant.semattice.SematticeDevAutopilotTemplateClient;
import com.codehouse.ciciassistant.semattice.SematticeProvisioningService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.server.ResponseStatusException;

class DevAutopilotActivationRecoveryIntegrationTest {
    private static final String COMPANY_ID = "org00000000000000001";
    private static final String ACTIVATION_KEY = "devautopilot-standard-v1-" + COMPANY_ID;
    private JdbcTemplate jdbc;

    @BeforeEach
    void prepareFreshDatabase() {
        String jdbcUrl = System.getenv("DEVAUTOPILOT_RECOVERY_TEST_URL");
        String username = System.getenv("DEVAUTOPILOT_RECOVERY_TEST_USERNAME");
        String password = System.getenv("DEVAUTOPILOT_RECOVERY_TEST_PASSWORD");
        Assumptions.assumeTrue(jdbcUrl != null && !jdbcUrl.isBlank()
                        && username != null && !username.isBlank()
                        && password != null && !password.isBlank(),
                "Fresh recovery database is supplied only by the verification command");
        Flyway.configure()
                .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(jdbcUrl, username, password));
        jdbc.update("DELETE FROM tenant_application_resource");
        jdbc.update("DELETE FROM tenant_application_activation");
        jdbc.update("DELETE FROM company_member");
        jdbc.update("DELETE FROM user_account");
        jdbc.update("DELETE FROM company");
        jdbc.update("INSERT INTO company(id,name,status) VALUES (?,?, 'ACTIVE')", COMPANY_ID, "Recovery Tenant");
        jdbc.update("""
                INSERT INTO user_account(id,primary_mobile,display_name,status,created_at,updated_at)
                VALUES ('owner-account','13900000001','Recovery Owner','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO company_member(id,company_id,account_id,role_code,member_status,created_at)
                VALUES ('owner-member',?,'owner-account','OWNER','ACTIVE',CURRENT_TIMESTAMP)
                """, COMPANY_ID);
    }

    @Test
    void persistsTheFailedAuthorizationStageAndResumesWithoutRepeatingCompletedWork() {
        CompanyRepository companies = mock(CompanyRepository.class);
        when(companies.findById(COMPANY_ID)).thenReturn(Optional.of(new CompanyEntity(COMPANY_ID, "Recovery Tenant", "ACTIVE")));
        SematticeProvisioningService provisioning = mock(SematticeProvisioningService.class);
        when(provisioning.getProvisioningStatus(COMPANY_ID)).thenReturn(
                new SematticeProvisioningService.BindingView("reservation-a", COMPANY_ID, "PROVISIONED", "tenant-a", "operation-a", null));
        SematticeDevAutopilotTemplateClient template = mock(SematticeDevAutopilotTemplateClient.class);
        when(template.apply(COMPANY_ID, ACTIVATION_KEY)).thenReturn(
                new SematticeDevAutopilotTemplateClient.TemplateView(
                        COMPANY_ID, "tenant-a", "metadata-a", "digest-a", 7, 87, "applied"));
        ServicePrincipalService principals = mock(ServicePrincipalService.class);
        when(principals.create(eq(COMPANY_ID), eq("owner-member"), eq("owner-member"), anyString(),
                eq("OFFICIAL_APP"), eq(OfficialAccessTokenService.SEMATTICE_AUDIENCE), any(), any()))
                .thenReturn(Map.of("principalId", "service-pm"));
        SematticeDevAutopilotAuthorizationClient authorization = mock(SematticeDevAutopilotAuthorizationClient.class);
        AtomicInteger attempts = new AtomicInteger();
        when(authorization.apply(eq(COMPANY_ID), anyString(), anyString(), any())).thenAnswer(invocation -> {
            if (attempts.getAndIncrement() == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Semattice request failed: HTTP 503 SCHEMA_MIGRATION_REQUIRED");
            }
            return new SematticeDevAutopilotAuthorizationClient.AuthorizationView(
                    COMPANY_ID, "tenant-a", SematticeDevAutopilotAuthorizationClient.TEMPLATE_VERSION,
                    "a".repeat(64), 4, 4, 7, 2, true, "applied");
        });
        DevAutopilotTenantApplicationService service = new DevAutopilotTenantApplicationService(
                jdbc, companies, provisioning, template, authorization, principals,
                mock(AgentDefinitionService.class), mock(DevAutopilotProductManagerAgentPublisher.class),
                mock(AgentServicePrincipalExecutionService.class), mock(PlatformAuditService.class),
                List.of("runtime.record.read"), List.of("runtime.record.read"));

        assertThatThrownBy(() -> service.activate(COMPANY_ID,
                new DevAutopilotTenantApplicationService.ActivationCommand(ACTIVATION_KEY), "platform-operator"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("SCHEMA_MIGRATION_REQUIRED");
        assertThat(jdbc.queryForMap("""
                SELECT actual_state,activation_stage,failed_stage,last_error_code,attempt_count,lease_token
                FROM tenant_application_activation WHERE company_id=?
                """, COMPANY_ID))
                .containsEntry("actual_state", "FAILED")
                .containsEntry("activation_stage", "PRINCIPALS_READY")
                .containsEntry("failed_stage", "AUTHORIZATION_READY")
                .containsEntry("last_error_code", "SCHEMA_MIGRATION_REQUIRED")
                .containsEntry("attempt_count", 1)
                .containsEntry("lease_token", null);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tenant_application_resource", Integer.class)).isEqualTo(2);

        var recovered = service.activate(COMPANY_ID,
                new DevAutopilotTenantApplicationService.ActivationCommand(ACTIVATION_KEY), "platform-operator");

        assertThat(recovered.actualState()).isEqualTo("ACTIVE");
        assertThat(recovered.activationStage()).isEqualTo("ACTIVE");
        assertThat(recovered.failedStage()).isNull();
        assertThat(recovered.attemptCount()).isEqualTo(2);
        verify(template, times(1)).apply(COMPANY_ID, ACTIVATION_KEY);
        verify(principals, times(1)).create(eq(COMPANY_ID), eq("owner-member"), eq("owner-member"), anyString(),
                eq("OFFICIAL_APP"), eq(OfficialAccessTokenService.SEMATTICE_AUDIENCE), any(), any());
        verify(authorization, times(2)).apply(eq(COMPANY_ID), anyString(), anyString(), any());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tenant_application_resource", Integer.class)).isEqualTo(2);
    }
}
