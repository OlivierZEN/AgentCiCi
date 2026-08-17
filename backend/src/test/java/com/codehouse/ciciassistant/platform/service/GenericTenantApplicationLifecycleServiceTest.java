package com.codehouse.ciciassistant.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.codehouse.ciciassistant.semattice.SematticeProvisioningService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

class GenericTenantApplicationLifecycleServiceTest {

    @Test
    void invokesTheProviderInitializationInterfaceThroughTheBackend() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/tenant-lifecycle/v1/activations", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"status\":\"SUCCEEDED\",\"operationId\":\"provider-op\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            JdbcTemplate jdbc = mock(JdbcTemplate.class);
            InternalApplicationProviderConnectionService providerConnections =
                    new InternalApplicationProviderConnectionService(
                            jdbc, mock(PlatformAuditService.class), mock(Environment.class));
            GenericTenantApplicationLifecycleService service = new GenericTenantApplicationLifecycleService(
                    jdbc, new ObjectMapper(), mock(InternalApplicationRegistryService.class), providerConnections,
                    mock(SematticeProvisioningService.class), mock(PlatformAuditService.class));
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            var revision = new InternalApplicationProviderConnectionService.RevisionView(
                    "revision-1", "sales.lifecycle", 1, baseUrl, "v1", "NONE", null,
                    "/health", "/internal/tenant-lifecycle/v1/activations", null, null, null, null,
                    5000, 2, "PASSED", Instant.now(), 200, 1L, null, Instant.now());
            var connection = new InternalApplicationProviderConnectionService.ConnectionView(
                    "sales.lifecycle", "sales-workbench", "Sales lifecycle", "local",
                    "PLATFORM_INTERNAL", "ACTIVE", revision.id(), List.of(revision), Instant.now(), Instant.now());

            String response = service.invoke(
                    "operation-1", "sales-workbench",
                    new InternalApplicationProviderConnectionService.ActiveConnection(connection, revision),
                    "ACTIVATE", revision.activatePath(),
                    new GenericTenantApplicationLifecycleService.StepDefinition(
                            "activation", "PROVIDER_CALLBACK", "tenant.activate"),
                    "{\"companyId\":\"org00000000000000001\",\"appCode\":\"sales-workbench\"}");

            assertThat(response).contains("SUCCEEDED");
            assertThat(requestBody.get()).contains("org00000000000000001", "sales-workbench");
        } finally {
            server.stop(0);
        }
    }
}
