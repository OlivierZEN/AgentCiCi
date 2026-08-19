package com.codehouse.ciciassistant.semattice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.codehouse.ciciassistant.agent.service.AgentServicePrincipalExecutionService;
import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SematticeProjectDeliveryUpdateToolServiceTest {

    private static final String RECORD_ID = "019ffde1-7b82-7f33-93c3-985921aca699";
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void exactProjectRenameConfirmationProducesTrustedToolArguments() throws Exception {
        var intent = SematticeProjectDeliveryUpdateToolService.confirmedIntent(
                "确认将项目 DAS-4F5ED86B 的名称修改为 AgentCiCi企业级智能体平台").orElseThrow();

        assertThat(intent.entityType()).isEqualTo("project");
        assertThat(intent.reference()).isEqualTo("DAS-4F5ED86B");
        assertThat(intent.fieldLabel()).isEqualTo("名称");
        assertThat(intent.field()).isEqualTo("name");
        assertThat(intent.value()).isEqualTo("AgentCiCi企业级智能体平台");
        JsonNode arguments = objectMapper.readTree(intent.toArguments(objectMapper));
        assertThat(arguments.path("updates").path("name").asText())
                .isEqualTo("AgentCiCi企业级智能体平台");
    }

    @Test
    void naturalLanguageOrUnsupportedFieldCannotBypassExactConfirmationProtocol() {
        assertThat(SematticeProjectDeliveryUpdateToolService.confirmedIntent(
                "把 DAS-4F5ED86B 改名为 AgentCiCi企业级智能体平台")).isEmpty();
        assertThat(SematticeProjectDeliveryUpdateToolService.confirmedIntent(
                "确认将项目 DAS-4F5ED86B 的编号修改为 DAS-OTHER")).isEmpty();
        assertThat(SematticeProjectDeliveryUpdateToolService.confirmedIntent(
                "确认将项目 DAS-4F5ED86B 的进度修改为快完成了")).isEmpty();
    }

    @Test
    void returnsSuccessOnlyAfterRevisionAndWrittenValueAreReadBack() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AgentServicePrincipalExecutionService execution = authorizedExecution();
        String queryUrl = "https://semattice.example.test/v1/capabilities/runtime.record.query/invoke";
        String updateUrl = "https://semattice.example.test/v1/capabilities/runtime.record.update/invoke";

        server.expect(requestTo(queryUrl))
                .andExpect(header("Authorization", "Bearer pm-service-oact"))
                .andExpect(jsonPath("$.input.object_api_name").value("dev_project"))
                .andRespond(withSuccess(queryResponse("企业级智能体平台项目AgentCiCi", 1), MediaType.APPLICATION_JSON));
        server.expect(requestTo(updateUrl))
                .andExpect(header("Authorization", "Bearer pm-service-oact"))
                .andExpect(jsonPath("$.input.record_id").value(RECORD_ID))
                .andExpect(jsonPath("$.input.expected_revision").value(1))
                .andExpect(jsonPath("$.input.patch.name").value("AgentCiCi企业级智能体平台"))
                .andExpect(jsonPath("$.request_id").value(org.hamcrest.Matchers.startsWith("cici-delivery-update-")))
                .andExpect(jsonPath("$.idempotency_key").value(org.hamcrest.Matchers.startsWith("cici-delivery-update-")))
                .andRespond(withSuccess(updateResponse("AgentCiCi企业级智能体平台", 2), MediaType.APPLICATION_JSON));
        server.expect(requestTo(queryUrl))
                .andExpect(header("Authorization", "Bearer pm-service-oact"))
                .andRespond(withSuccess(queryResponse("AgentCiCi企业级智能体平台", 2), MediaType.APPLICATION_JSON));

        var service = new SematticeProjectDeliveryUpdateToolService(
                builder, objectMapper, execution, "https://semattice.example.test/");
        JsonNode result = objectMapper.readTree(service.dispatch(
                "org-1", "member-1", "dev-autopilot-pm", """
                {"entity_type":"project","reference":"DAS-4F5ED86B",
                 "updates":{"name":"AgentCiCi企业级智能体平台"}}
                """));

        assertThat(result.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(result.path("source").asText()).isEqualTo("SEMATTICE_LIVE");
        assertThat(result.path("record_id").asText()).isEqualTo(RECORD_ID);
        assertThat(result.path("revision").asLong()).isEqualTo(2);
        assertThat(result.path("readback_verified").asBoolean()).isTrue();
        assertThat(result.path("verified_values").path("name").asText())
                .isEqualTo("AgentCiCi企业级智能体平台");
        assertThat(result.path("changed").asBoolean()).isTrue();
        server.verify();
    }

    @Test
    void repeatedConfirmationReturnsVerifiedNoopWithoutAnotherWrite() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://semattice.example.test/v1/capabilities/runtime.record.query/invoke"))
                .andRespond(withSuccess(queryResponse("AgentCiCi企业级智能体平台", 2), MediaType.APPLICATION_JSON));
        var service = new SematticeProjectDeliveryUpdateToolService(
                builder, objectMapper, authorizedExecution(), "https://semattice.example.test");

        JsonNode result = objectMapper.readTree(service.dispatch(
                "org-1", "member-1", "dev-autopilot-pm", """
                {"entity_type":"project","reference":"DAS-4F5ED86B",
                 "updates":{"name":"AgentCiCi企业级智能体平台"}}
                """));

        assertThat(result.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(result.path("changed").asBoolean()).isFalse();
        assertThat(result.path("readback_verified").asBoolean()).isTrue();
        assertThat(result.path("revision").asLong()).isEqualTo(2);
        server.verify();
    }

    @Test
    void failsClosedWhenPostWriteQueryDoesNotContainTheRequestedValue() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String queryUrl = "https://semattice.example.test/v1/capabilities/runtime.record.query/invoke";
        server.expect(requestTo(queryUrl))
                .andRespond(withSuccess(queryResponse("企业级智能体平台项目AgentCiCi", 1), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://semattice.example.test/v1/capabilities/runtime.record.update/invoke"))
                .andRespond(withSuccess(updateResponse("AgentCiCi企业级智能体平台", 2), MediaType.APPLICATION_JSON));
        server.expect(requestTo(queryUrl))
                .andRespond(withSuccess(queryResponse("企业级智能体平台项目AgentCiCi", 1), MediaType.APPLICATION_JSON));
        var service = new SematticeProjectDeliveryUpdateToolService(
                builder, objectMapper, authorizedExecution(), "https://semattice.example.test");

        JsonNode result = objectMapper.readTree(service.dispatch(
                "org-1", "member-1", "dev-autopilot-pm", """
                {"entity_type":"project","reference":"DAS-4F5ED86B",
                 "updates":{"name":"AgentCiCi企业级智能体平台"}}
                """));

        assertThat(result.path("status").asText()).isEqualTo("failed");
        assertThat(result.path("error").path("code").asText()).isEqualTo("UPDATE_READBACK_INVALID");
        assertThat(result.path("readback_verified").asBoolean(false)).isFalse();
        server.verify();
    }

    private AgentServicePrincipalExecutionService authorizedExecution() {
        AgentServicePrincipalExecutionService execution = mock(AgentServicePrincipalExecutionService.class);
        var token = new OfficialAccessTokenService.IssuedToken(
                "pm-service-oact", Instant.now().plusSeconds(300), "tenant-1", "org-1",
                List.of("runtime.record.read", "runtime.record.update"));
        when(execution.authorizeSemattice(
                "org-1", "member-1", "dev-autopilot-pm",
                List.of("runtime.record.read", "runtime.record.update"),
                SematticeProjectDeliveryUpdateToolService.TOOL_NAME))
                .thenReturn(new AgentServicePrincipalExecutionService.ExecutionAuthorization(
                        "service-1", "大乔", "owner-1", "PRIMARY_OWNER", token));
        return execution;
    }

    private String queryResponse(String name, long revision) {
        return """
                {"status":"succeeded","result":{"records":[{
                  "record_id":"%s","revision":%d,
                  "data":{"code":"DAS-4F5ED86B","name":"%s","status":"进行中"}
                }]}}
                """.formatted(RECORD_ID, revision, name);
    }

    private String updateResponse(String name, long revision) {
        return """
                {"status":"succeeded","correlationId":"corr-update-1","result":{
                  "record_id":"%s","revision":%d,
                  "data":{"code":"DAS-4F5ED86B","name":"%s","status":"进行中"}
                }}
                """.formatted(RECORD_ID, revision, name);
    }
}
