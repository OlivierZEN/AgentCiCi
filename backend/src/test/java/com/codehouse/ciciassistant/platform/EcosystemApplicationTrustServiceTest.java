package com.codehouse.ciciassistant.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.platform.service.EcosystemApplicationTrustService;
import com.codehouse.ciciassistant.platform.service.PlatformAuditService;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class EcosystemApplicationTrustServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final EcosystemApplicationTrustService service = new EcosystemApplicationTrustService(
            jdbc, mock(PlatformAuditService.class));

    @Test
    void failsClosedForAnUnregisteredKeycloakClient() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq("unknown-client"))).thenReturn(List.of());

        assertThatThrownBy(() -> service.requireActiveClient("unknown-client", "organization.read"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("not registered");
    }

    @Test
    void requiresBothActiveStatusAndTheGovernedApplicationScope() {
        var active = application(EcosystemApplicationTrustService.STATUS_ACTIVE, Set.of("organization.read"));
        when(jdbc.query(anyString(), any(RowMapper.class), eq("internal-workbench")))
                .thenReturn(List.of(active));

        assertThat(service.requireActiveClient("internal-workbench", "organization.read")).isEqualTo(active);
        assertThatThrownBy(() -> service.requireActiveClient("internal-workbench", "organization.context"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("scope organization.context");
    }

    @Test
    void rejectsASuspendedApplicationEvenWhenItsScopeIsPresent() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq("internal-workbench")))
                .thenReturn(List.of(application(
                        EcosystemApplicationTrustService.STATUS_SUSPENDED,
                        Set.of("organization.read"))));

        assertThatThrownBy(() -> service.requireActiveClient("internal-workbench", "organization.read"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("suspended");
    }

    @Test
    void savesAndReadsBackPostgresTimestampColumnsWithoutUnsupportedInstantConversion() throws Exception {
        Instant createdAt = Instant.parse("2026-08-14T03:40:08Z");
        Instant updatedAt = Instant.parse("2026-08-14T03:41:09Z");
        when(jdbc.query(anyString(), any(RowMapper.class), eq("cosales-web"))).thenAnswer(invocation -> {
            RowMapper<EcosystemApplicationTrustService.TrustedApplicationView> mapper = invocation.getArgument(1);
            ResultSet row = mock(ResultSet.class);
            when(row.getString("app_code")).thenReturn("cosales-web");
            when(row.getString("display_name")).thenReturn("CCSales Web");
            when(row.getString("keycloak_client_id")).thenReturn("cosales-web");
            when(row.getString("allowed_scopes"))
                    .thenReturn("organization.read organization.context");
            when(row.getString("status")).thenReturn(EcosystemApplicationTrustService.STATUS_ACTIVE);
            when(row.getString("created_by")).thenReturn("platform-admin");
            when(row.getTimestamp("created_at")).thenReturn(Timestamp.from(createdAt));
            when(row.getTimestamp("updated_at")).thenReturn(Timestamp.from(updatedAt));
            return List.of(mapper.mapRow(row, 0));
        });

        var application = service.upsert(
                new EcosystemApplicationTrustService.TrustedApplicationCommand(
                        "cosales-web",
                        "CCSales Web",
                        "cosales-web",
                        List.of("organization.read", "organization.context"),
                        EcosystemApplicationTrustService.STATUS_ACTIVE),
                "platform-admin",
                "PLATFORM_ADMIN");

        assertThat(application.appCode()).isEqualTo("cosales-web");
        assertThat(application.createdAt()).isEqualTo(createdAt);
        assertThat(application.updatedAt()).isEqualTo(updatedAt);
    }

    private EcosystemApplicationTrustService.TrustedApplicationView application(String status, Set<String> scopes) {
        return new EcosystemApplicationTrustService.TrustedApplicationView(
                "internal-workbench", "内部工作台", "internal-workbench", scopes,
                status, "platform-admin", Instant.now(), Instant.now());
    }
}
