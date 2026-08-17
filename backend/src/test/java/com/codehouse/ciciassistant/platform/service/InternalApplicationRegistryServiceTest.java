package com.codehouse.ciciassistant.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class InternalApplicationRegistryServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final InternalApplicationRegistryService service = new InternalApplicationRegistryService(
            jdbc, new ObjectMapper(), mock(PlatformAuditService.class),
            mock(InternalApplicationProviderConnectionService.class));

    @Test
    void matchesOnlySupportedSemanticVersionConstraints() {
        assertThat(InternalApplicationRegistryService.matchesConstraint("1.2.3", "*")).isTrue();
        assertThat(InternalApplicationRegistryService.matchesConstraint("1.2.3", ">=1.2.0")).isTrue();
        assertThat(InternalApplicationRegistryService.matchesConstraint("1.2.3", ">=2.0.0")).isFalse();
        assertThat(InternalApplicationRegistryService.matchesConstraint("1.2.3", "=1.2.3")).isTrue();
        assertThat(InternalApplicationRegistryService.matchesConstraint("1.2.3", "1.2.4")).isFalse();
    }

    @Test
    void rejectsDeploymentAddressesFromCatalogGovernanceFieldsBeforePersistence() {
        var command = new InternalApplicationRegistryService.ApplicationCommand(
                "sales-workbench",
                "销售工作台",
                "连接 https://runtime.example.com 的内部应用",
                "workflow",
                "Sales Platform",
                "SHARED_RUNTIME_TENANT_ISOLATED",
                null,
                "SERVER_HANDOFF",
                "sales-workbench.web");

        assertThatThrownBy(() -> service.create(command, "platform-admin", "PLATFORM_ADMIN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deployment-managed logical key");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void rejectsProviderCallbackWithoutADeploymentManagedBindingKey() {
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of(application()));

        var version = new InternalApplicationRegistryService.VersionCommand(
                "1.0.0",
                null,
                "SAGA_V1",
                List.of(new InternalApplicationRegistryService.StepCommand(
                        "activate", "PROVIDER_CALLBACK", "tenant.activate", "v1")),
                List.of());

        assertThatThrownBy(() -> service.createVersion(
                "sales-workbench", version, "platform-admin", "PLATFORM_ADMIN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("providerBindingKey");
    }

    private InternalApplicationRegistryService.ApplicationSummaryView application() {
        return new InternalApplicationRegistryService.ApplicationSummaryView(
                "sales-workbench", "销售工作台", "销售运营", "workflow", "Sales Platform",
                "SHARED_RUNTIME_TENANT_ISOLATED", "DRAFT", null,
                "SERVER_HANDOFF", "sales-workbench.web", null, 0, Instant.now(), Instant.now());
    }
}
