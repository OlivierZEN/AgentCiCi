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

class SematticeProjectDeliveryReviewToolServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void reviewsDesignThroughDevAutopilotUsingProductManagerServiceToken() throws Exception {
        String taskId = "019fb381-622b-73b9-b8c8-b97181509008";
        String submissionId = "019fb381-622b-73b9-b8c8-b97181509009";
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AgentServicePrincipalExecutionService execution = mock(AgentServicePrincipalExecutionService.class);
        OfficialAccessTokenService.IssuedToken token = new OfficialAccessTokenService.IssuedToken(
                "pm-service-oact", Instant.now().plusSeconds(300), "tenant-1", "org-1",
                List.of("runtime.record.read", "runtime.record.create", "runtime.record.update"));
        when(execution.authorizeSemattice("org-1", "member-1", "dev-autopilot-pm",
                List.of("runtime.record.read", "runtime.record.create", "runtime.record.update"), SematticeProjectDeliveryReviewToolService.TOOL_NAME))
                .thenReturn(new AgentServicePrincipalExecutionService.ExecutionAuthorization(
                        "service-1", "大乔", "owner-1", "PRIMARY_OWNER", token));
        server.expect(requestTo("https://x.example.test/devautopilot/api/pm/v1/tasks/" + taskId + "/reviews"))
                .andExpect(header("Authorization", "Bearer pm-service-oact"))
                .andExpect(header("Idempotency-Key", "cici-review-" + submissionId + "-approve"))
                .andExpect(jsonPath("$.reviewType").value("design"))
                .andExpect(jsonPath("$.decision").value("approved"))
                .andExpect(jsonPath("$.submissionEventId").value(submissionId))
                .andRespond(withSuccess("""
                        {"data":{"event":{"id":"019fb381-622b-73b9-b8c8-b97181509010","event_type":"design_approved"},
                        "task":{"id":"019fb381-622b-73b9-b8c8-b97181509008","status":"进行中"}},"correlationId":"corr-1"}
                        """, MediaType.APPLICATION_JSON));
        SematticeProjectDeliveryReviewToolService service = new SematticeProjectDeliveryReviewToolService(
                builder, objectMapper, execution, "https://x.example.test/devautopilot/");

        JsonNode result = objectMapper.readTree(service.dispatch("org-1", "member-1", "dev-autopilot-pm", """
                {"task_id":"%s","submission_event_id":"%s","gate":"design","decision":"approve",
                 "summary":"设计边界与验收标准完整，同意执行。","checklist":["状态机边界明确","测试计划完整"]}
                """.formatted(taskId, submissionId)));

        assertThat(result.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(result.path("source").asText()).isEqualTo("DEV_AUTOPILOT_LIVE");
        assertThat(result.path("execution_principal_type").asText()).isEqualTo("SERVICE");
        assertThat(result.path("execution_principal").asText()).isEqualTo("大乔");
        assertThat(result.path("task").path("status").asText()).isEqualTo("进行中");
        server.verify();
    }

    @Test
    void rejectsCallerSuppliedIdentityAndTargetBeforeAuthorization() throws Exception {
        SematticeProjectDeliveryReviewToolService service = new SematticeProjectDeliveryReviewToolService(
                RestClient.builder(), objectMapper, mock(AgentServicePrincipalExecutionService.class),
                "https://x.example.test/devautopilot");

        JsonNode result = objectMapper.readTree(service.dispatch("org-1", "member-1", "dev-autopilot-pm", """
                {"task_id":"019fb381-622b-73b9-b8c8-b97181509008",
                 "submission_event_id":"019fb381-622b-73b9-b8c8-b97181509009",
                 "gate":"completion","decision":"approve","summary":"通过","token":"stolen"}
                """));

        assertThat(result.path("status").asText()).isEqualTo("FAILED");
        assertThat(result.path("code").asText()).isEqualTo("INVALID_ARGUMENTS");
    }
}
