package com.codehouse.ciciassistant.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.platform.service.SystemApiCatalogService;
import com.codehouse.ciciassistant.semattice.SematticeSystemApiCatalogClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SystemApiCatalogServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void returnsAgentCiCiCatalogAndMakesRemoteFailureExplicit() {
        SematticeSystemApiCatalogClient client = mock(SematticeSystemApiCatalogClient.class);
        when(client.fetch()).thenReturn(Optional.empty());

        var catalog = new SystemApiCatalogService(client, objectMapper).catalog();

        assertThat(catalog.contractVersion()).isEqualTo("v1");
        assertThat(catalog.notice()).contains("不代表已获得调用权限");
        assertThat(catalog.providers()).hasSize(2);
        var agentCiCi = catalog.providers().getFirst();
        assertThat(agentCiCi.code()).isEqualTo("agentcici");
        assertThat(agentCiCi.apis()).extracting(SystemApiCatalogService.ApiView::id)
                .containsExactly(
                        "agentcici.company.list",
                        "agentcici.company.switch",
                        "agentcici.service-token.exchange",
                        "agentcici.devautopilot.activation.resolve",
                        "agentcici.devautopilot.handoff.exchange",
                        "agentcici.semattice.provisioning.reserve",
                        "agentcici.semattice.console-handoff.redeem",
                        "agentcici.oact.jwks");
        assertThat(agentCiCi.apis()).allSatisfy(api -> {
            assertThat(api.path()).startsWith("/");
            assertThat(api.path()).doesNotContain("localhost", "uat.", "http://", "https://");
            assertThat(api.requestExample().toString()).doesNotContain("secret", "eyJ");
        });
        assertThat(agentCiCi.apis().getFirst()).satisfies(api -> {
            assertThat(api.path()).isEqualTo("/auth/companies");
            assertThat(api.authType()).isEqualTo("Bearer AgentCiCi HUMAN token");
            assertThat(api.outputSchema().path("properties").path("data").path("properties")
                    .path("companies").path("type").asText()).isEqualTo("array");
        });
        assertThat(agentCiCi.apis().get(1)).satisfies(api -> {
            assertThat(api.path()).isEqualTo("/auth/switch-company");
            assertThat(api.requestExample().path("companyId").asText()).isEqualTo("${TARGET_COMPANY_ID}");
            assertThat(api.responseExample().path("data").path("token").asText())
                    .isEqualTo("${NEW_AGENTCICI_USER_TOKEN}");
            assertThat(api.callNotes()).anyMatch(note -> note.contains("返回 403"));
        });
        assertThat(catalog.providers().get(1).status()).isEqualTo("unavailable");
    }

    @Test
    void preservesSematticeProviderOwnedSchemaAndInvocationMetadata() throws Exception {
        SematticeSystemApiCatalogClient client = mock(SematticeSystemApiCatalogClient.class);
        when(client.fetch()).thenReturn(Optional.of(objectMapper.readTree("""
                {
                  "code":"semattice","name":"Semattice","description":"Provider contract",
                  "contract_version":"v1","status":"available","apis":[{
                    "id":"runtime.record.query","title":"业务记录查询","summary":"受限查询",
                    "description":"Registry description","category":"业务数据","method":"POST",
                    "path":"/v1/capabilities/runtime.record.query/invoke","protocols":["HTTP","MCP","CLI"],
                    "auth_type":"Bearer OACT","audience":"Semattice","required_scope":"runtime.record.read",
                    "risk_level":"low","version":"v1","state":"published","idempotency_required":true,
                    "execution_mode":"synchronous","approval_required":false,"consumers":["DevAutopilot"],
                    "input_schema":{"type":"object","required":["object_api_name"]},
                    "output_schema":{"type":"object"},"request_example":{"input":{}},
                    "response_example":{"status":"succeeded"},"error_codes":["UNAUTHORIZED"],
                    "compatibility":"compatible","source_contract":"Runtime Record Capability Contract"
                  }]
                }
                """)));

        var provider = new SystemApiCatalogService(client, objectMapper).catalog().providers().get(1);

        assertThat(provider.status()).isEqualTo("available");
        assertThat(provider.apis()).singleElement().satisfies(api -> {
            assertThat(api.id()).isEqualTo("runtime.record.query");
            assertThat(api.protocols()).containsExactly("HTTP", "MCP", "CLI");
            assertThat(api.requiredScope()).isEqualTo("runtime.record.read");
            assertThat(api.inputSchema().path("required").get(0).asText()).isEqualTo("object_api_name");
        });
    }
}
