package com.codehouse.ciciassistant.platform.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

class InternalApplicationProviderConnectionServiceTest {

    private final InternalApplicationProviderConnectionService service =
            new InternalApplicationProviderConnectionService(
                    mock(JdbcTemplate.class), mock(PlatformAuditService.class), mock(Environment.class));

    @Test
    void publicConnectionsRequireHttps() {
        assertThatThrownBy(() -> service.validateCommand(command("PUBLIC_HTTPS", "http://service.example.test", "NONE", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("require HTTPS");
    }

    @Test
    void internalConnectionsMayUseHttpButRejectCloudMetadata() {
        assertThatCode(() -> service.validateCommand(command("PLATFORM_INTERNAL", "http://provider.internal:8080", "NONE", null)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> service.validateCommand(command("PLATFORM_INTERNAL", "http://169.254.169.254", "NONE", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("metadata");
    }

    @Test
    void credentialsAreReferencesAndMustMatchTheAuthMode() {
        assertThatThrownBy(() -> service.validateCommand(command(
                "PUBLIC_HTTPS", "https://service.example.test", "BEARER_SECRET_REF", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("secretRef");
        assertThatCode(() -> service.validateCommand(command(
                "PUBLIC_HTTPS", "https://service.example.test", "HMAC_SHA256_SECRET_REF", "sales.lifecycle-key")))
                .doesNotThrowAnyException();
    }

    @Test
    void activeConnectionNetworkBoundaryCannotBeChangedByADraftRevision() {
        var connection = new InternalApplicationProviderConnectionService.ConnectionView(
                "sales.lifecycle", "sales-workbench", "Sales lifecycle", "local",
                "PLATFORM_INTERNAL", "ACTIVE", "revision-1", List.of(), Instant.now(), Instant.now());

        assertThatThrownBy(() -> service.validateStableMetadata(connection, "local", "PUBLIC_HTTPS"))
                .hasMessageContaining("immutable");
        assertThatCode(() -> service.validateStableMetadata(connection, "local", "PLATFORM_INTERNAL"))
                .doesNotThrowAnyException();
    }

    private InternalApplicationProviderConnectionService.ConnectionCommand command(
            String networkScope, String baseUrl, String authType, String secretRef) {
        return new InternalApplicationProviderConnectionService.ConnectionCommand(
                "sales.lifecycle", "Sales lifecycle", "default", networkScope, baseUrl, "v1",
                authType, secretRef, "/health", "/activations", "/reconciliations",
                "/suspensions", "/resumptions", "/upgrades", 10000, 2);
    }
}
