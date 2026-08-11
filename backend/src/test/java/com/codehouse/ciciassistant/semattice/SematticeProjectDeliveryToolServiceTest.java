package com.codehouse.ciciassistant.semattice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.agent.service.AgentServicePrincipalExecutionService;
import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SematticeProjectDeliveryToolServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void queriesAllPublishedDeliveryObjectsWithDelegatedOfficialAccessToken() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AgentServicePrincipalExecutionService execution = mock(AgentServicePrincipalExecutionService.class);
        OfficialAccessTokenService.IssuedToken token = new OfficialAccessTokenService.IssuedToken(
                "service-oact", Instant.now().plusSeconds(300), "tenant-1", "org-1", java.util.List.of("runtime.record.read"));
        when(execution.authorizeSemattice("org-1", "member-1", "dev-autopilot-pm",
                java.util.List.of("runtime.record.read"), "semattice_project_delivery_query"))
                .thenReturn(new AgentServicePrincipalExecutionService.ExecutionAuthorization(
                        "service-1", "DEV Autopilot 产品经理", "owner-1", "PRIMARY_OWNER", token));
        for (String objectName : java.util.List.of("dev_project", "dev_requirement", "dev_task", "dev_worklog", "dev_change", "dev_delivery_event", "dev_defect")) {
            String response = "dev_delivery_event".equals(objectName)
                    ? "{\"status\":\"succeeded\",\"result\":{\"records\":["
                    + "{\"record_id\":\"event-submission\",\"data\":{\"status\":\"pending\",\"event_type\":\"design_submitted\"}},"
                    + "{\"record_id\":\"event-decision\",\"data\":{\"status\":\"accepted\",\"event_type\":\"design_approved\",\"parent_event_id\":\"event-submission\"}}]}}"
                    : "{\"status\":\"succeeded\",\"result\":{\"records\":[{\"record_id\":\"r-" + objectName
                    + "\",\"revision\":1,\"data\":{\"status\":\"执行中\",\"name\":\"演示项目\",\"hours\":2.5}}]}}";
            server.expect(requestTo("https://semattice.example.test/v1/capabilities/runtime.record.query/invoke"))
                    .andExpect(header("Authorization", "Bearer service-oact"))
                    .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        }
        SematticeProjectDeliveryToolService service = new SematticeProjectDeliveryToolService(
                builder, objectMapper, execution, "https://semattice.example.test");

        JsonNode result = objectMapper.readTree(service.dispatch(
                "org-1", "member-1", "dev-autopilot-pm", "{\"focus\":\"overview\"}"));

        assertThat(result.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(result.path("source").asText()).isEqualTo("SEMATTICE_LIVE");
        assertThat(result.path("executing_project_count").asLong()).isEqualTo(1);
        assertThat(result.path("projects").get(0).path("name").asText()).isEqualTo("演示项目");
        assertThat(result.path("execution_principal_type").asText()).isEqualTo("SERVICE");
        assertThat(result.path("execution_principal").asText()).isEqualTo("DEV Autopilot 产品经理");
        assertThat(result.path("events").size()).isEqualTo(2);
        assertThat(result.path("pending_reviews").size()).isZero();
        assertThat(result.path("defects").size()).isEqualTo(1);
        assertThat(result.path("defects").get(0).path("revision").asLong()).isEqualTo(1);
        server.verify();
    }

    @Test
    void rejectsTenantAndTokenArgumentsBeforeAnyRemoteCall() throws Exception {
        SematticeProjectDeliveryToolService service = new SematticeProjectDeliveryToolService(
                RestClient.builder(), objectMapper, mock(AgentServicePrincipalExecutionService.class),
                "https://semattice.example.test");

        JsonNode result = objectMapper.readTree(service.dispatch(
                "org-1", "member-1", "dev-autopilot-pm", "{\"tenant_id\":\"other\"}"));

        assertThat(result.path("status").asText()).isEqualTo("FAILED");
        assertThat(result.path("code").asText()).isEqualTo("INVALID_ARGUMENTS");
    }
}
