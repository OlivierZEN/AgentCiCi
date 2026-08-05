package com.codehouse.ciciassistant.ontology.semattice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.codehouse.ciciassistant.auth.service.AuthService;
import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SematticeOntologyHttpGatewayTest {

    @Test
    void derivesBearerTokenServerSideAndSendsStableIdempotencyEnvelope() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AuthService authService = mock(AuthService.class);
        OfficialAccessTokenService tokenService = mock(OfficialAccessTokenService.class);
        when(authService.issueSematticeOfficialAccess("company-a", "user-a", tokenService))
                .thenReturn(new OfficialAccessTokenService.IssuedToken(
                        "short-oact", Instant.now().plusSeconds(300), "tenant-a", "company-a",
                        List.of("metadata.read")));
        SematticeOntologyHttpGateway gateway = new SematticeOntologyHttpGateway(
                builder, new ObjectMapper(), authService, tokenService, "https://semattice.test/");
        server.expect(requestTo(
                        "https://semattice.test/v1/capabilities/metadata.version.get-current/invoke"))
                .andExpect(header("Authorization", "Bearer short-oact"))
                .andExpect(jsonPath("$.capability_id").value("metadata.version.get-current"))
                .andExpect(jsonPath("$.idempotency_key").value("operation-1"))
                .andExpect(jsonPath("$.tenant_id").doesNotExist())
                .andRespond(withSuccess("""
                        {"capability_id":"metadata.version.get-current","request_id":"r1","audit_id":"a1","status":"succeeded","result":{"objects":[]}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(gateway.invoke(
                "company-a", "user-a", "metadata.version.get-current", Map.of(), "operation-1"))
                .isEqualTo(new ObjectMapper().createObjectNode().set("objects", new ObjectMapper().createArrayNode()));
        server.verify();
    }

    @Test
    void rejectsUnconfiguredAndUntrustedCapabilityIdentifiersBeforeNetworkAccess() {
        SematticeOntologyHttpGateway unconfigured = new SematticeOntologyHttpGateway(
                RestClient.builder(), new ObjectMapper(), mock(AuthService.class),
                mock(OfficialAccessTokenService.class), "");
        assertThatThrownBy(() -> unconfigured.invokeRead(
                "company-a", "user-a", "metadata.version.get-current", Map.of()))
                .isInstanceOf(SematticeCapabilityException.class)
                .extracting(exception -> ((SematticeCapabilityException) exception).code())
                .isEqualTo("DATA_SOURCE_UNAVAILABLE");
        assertThatThrownBy(() -> unconfigured.invokeRead(
                "company-a", "user-a", "../../admin", Map.of()))
                .isInstanceOf(SematticeCapabilityException.class);
    }
}
