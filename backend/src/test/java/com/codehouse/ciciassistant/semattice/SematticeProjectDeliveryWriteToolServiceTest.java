package com.codehouse.ciciassistant.semattice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
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

class SematticeProjectDeliveryWriteToolServiceTest {

    @Test
    void recognizesFieldOnlyRepliesAsPendingDraftContinuations() {
        assertThat(SematticeProjectDeliveryWriteToolService.isDraftContinuation("父项目：智能体平台")).isTrue();
        assertThat(SematticeProjectDeliveryWriteToolService.isDraftContinuation("环境=UAT")).isTrue();
        assertThat(SematticeProjectDeliveryWriteToolService.isDraftContinuation("查询当前所有项目")).isFalse();
        assertThat(SematticeProjectDeliveryWriteToolService.isDraftContinuation("确认提交此缺陷")).isFalse();
    }

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void returnsDraftUntilExactProjectConfirmationThenCreatesWithDelegatedToken() throws Exception {
        assertThat(SematticeProjectDeliveryWriteToolService.isDraftRequest("现在创建一个棕榈地的研发项目"))
                .isTrue();
        assertThat(SematticeProjectDeliveryWriteToolService.confirmedIntent("确认创建项目：棕榈地"))
                .hasValueSatisfying(intent -> assertThat(intent.operation()).isEqualTo("create_project"));
        assertThat(SematticeProjectDeliveryWriteToolService.isDraftRequest("确认创建项目：棕榈地"))
                .isFalse();

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AgentServicePrincipalExecutionService execution = mock(AgentServicePrincipalExecutionService.class);
        OfficialAccessTokenService.IssuedToken token = new OfficialAccessTokenService.IssuedToken(
                "service-oact", Instant.now().plusSeconds(300), "tenant-1", "org-1",
                List.of("runtime.record.read", "runtime.record.create"));
        when(execution.authorizeSemattice("org-1", "member-1", "dev-autopilot-pm",
                List.of("runtime.record.read", "runtime.record.create"), "semattice_project_delivery_create"))
                .thenReturn(new AgentServicePrincipalExecutionService.ExecutionAuthorization(
                        "service-1", "DEV Autopilot 产品经理", "owner-1", "PRIMARY_OWNER", token));
        server.expect(requestTo("https://semattice.example.test/v1/capabilities/runtime.record.create/invoke"))
                .andExpect(header("Authorization", "Bearer service-oact"))
                .andRespond(withSuccess("{\"status\":\"succeeded\",\"result\":{\"record_id\":\"019fb381-622b-73b9-b8c8-b97181509008\"}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://semattice.example.test/v1/capabilities/runtime.record.get/invoke"))
                .andExpect(header("Authorization", "Bearer service-oact"))
                .andRespond(withSuccess("{\"status\":\"succeeded\",\"result\":{\"record_id\":\"019fb381-622b-73b9-b8c8-b97181509008\",\"revision\":1,\"data\":{\"code\":\"DAS-PLACEHOLDER\",\"name\":\"棕榈地\",\"owner\":\"DEV Autopilot 产品经理\",\"status\":\"规划中\",\"health\":\"待评估\",\"progress\":0,\"release\":\"v0.1.0\",\"description\":\"由研发交付产品经理创建\"}}}", MediaType.APPLICATION_JSON));
        SematticeProjectDeliveryWriteToolService service = new SematticeProjectDeliveryWriteToolService(
                builder, objectMapper, execution, "https://semattice.example.test");

        JsonNode result = objectMapper.readTree(service.dispatch("org-1", "member-1", "dev-autopilot-pm",
                "{\"operation\":\"create_project\",\"name\":\"棕榈地\"}"));

        assertThat(result.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(result.path("source").asText()).isEqualTo("SEMATTICE_LIVE");
        assertThat(result.path("code").asText()).startsWith("DAS-");
        assertThat(result.path("name").asText()).isEqualTo("棕榈地");
        assertThat(result.path("execution_principal_type").asText()).isEqualTo("SERVICE");
        assertThat(result.path("execution_principal").asText()).isEqualTo("DEV Autopilot 产品经理");
        assertThat(result.path("revision").asLong()).isEqualTo(1);
        assertThat(result.path("readback_verified").asBoolean()).isTrue();
        assertThat(result.path("correlation_id").asText()).isNotBlank();
        server.verify();
    }

    @Test
    void routesNaturalCreateLanguageWithoutExtractingBusinessNames() {
        assertThat(SematticeProjectDeliveryWriteToolService.isDraftRequest(
                "现在创建一个研发项目名称叫：AgentCiCi企业级智能体平台")).isTrue();
        assertThat(SematticeProjectDeliveryWriteToolService.isDraftRequest(
                "帮我创建一个新项目：AgentCiCi企业级智能体平台")).isTrue();
        assertThat(SematticeProjectDeliveryWriteToolService.isDraftRequest(
                "新增需求：为 AgentCiCi 企业级智能体平台提供组织级智能体治理")).isTrue();
        assertThat(SematticeProjectDeliveryWriteToolService.isDraftRequest(
                "帮我记录一个 Bug：确认按钮点击没有反应")).isTrue();
        assertThat(SematticeProjectDeliveryWriteToolService.isDraftRequest("现在有哪些项目在执行"))
                .isFalse();
    }

    @Test
    void modelDraftPromptRequiresFullSemanticUnderstandingAndNoWrite() {
        String prompt = SematticeProjectDeliveryWriteToolService.modelDraftPrompt();

        assertThat(prompt).contains("基于完整用户消息和会话上下文进行语义理解");
        assertThat(prompt).contains("服务端没有、也不会用正则替你抽取项目名");
        assertThat(prompt).contains("完整项目名称是“AgentCiCi企业级智能体平台”，不是“新”");
        assertThat(prompt).contains("不调用任何工具，不写入 Semattice");
        assertThat(prompt).contains("确认创建项目：<完整项目名称>");
        assertThat(prompt).contains("确认提交缺陷：项目=<父项目编号或名称>");
    }

    @Test
    void exactDefectConfirmationCreatesAndReadsBackGovernedRecord() throws Exception {
        String confirmation = "确认提交缺陷：项目=DAS-001；标题=确认按钮无响应；描述=点击后页面没有变化；"
                + "严重度=high；优先级=P1；环境=UAT Chrome；复现步骤=打开详情后点击确认；"
                + "预期结果=保存成功；实际结果=没有请求发出";
        assertThat(SematticeProjectDeliveryWriteToolService.confirmedIntent(confirmation))
                .hasValueSatisfying(intent -> {
                    assertThat(intent.operation()).isEqualTo("create_defect");
                    assertThat(intent.severity()).isEqualTo("high");
                    assertThat(intent.priority()).isEqualTo("P1");
                });

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AgentServicePrincipalExecutionService execution = mock(AgentServicePrincipalExecutionService.class);
        OfficialAccessTokenService.IssuedToken token = new OfficialAccessTokenService.IssuedToken(
                "service-oact", Instant.now().plusSeconds(300), "tenant-1", "org-1",
                List.of("runtime.record.read", "runtime.record.create"));
        when(execution.authorizeSemattice("org-1", "member-1", "dev-autopilot-pm",
                List.of("runtime.record.read", "runtime.record.create"), "semattice_project_delivery_create"))
                .thenReturn(new AgentServicePrincipalExecutionService.ExecutionAuthorization(
                        "service-1", "研发产品经理", "owner-1", "human-actor-1", "TENANT_APP_ROLE", "CONTRIBUTOR", token));
        server.expect(requestTo("https://semattice.example.test/v1/capabilities/runtime.record.query/invoke"))
                .andRespond(withSuccess("{\"status\":\"succeeded\",\"result\":{\"records\":[{\"record_id\":\"019fb381-622b-73b9-b8c8-b97181509001\",\"data\":{\"code\":\"DAS-001\"}}]}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://semattice.example.test/v1/capabilities/runtime.record.create/invoke"))
                .andRespond(withSuccess("{\"status\":\"succeeded\",\"result\":{\"record_id\":\"019fb381-622b-73b9-b8c8-b97181509009\"}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://semattice.example.test/v1/capabilities/runtime.record.get/invoke"))
                .andRespond(withSuccess("{\"status\":\"succeeded\",\"result\":{\"record_id\":\"019fb381-622b-73b9-b8c8-b97181509009\",\"revision\":1,\"data\":{\"project_id\":\"019fb381-622b-73b9-b8c8-b97181509001\",\"title\":\"确认按钮无响应\",\"description\":\"点击后页面没有变化\",\"severity\":\"high\",\"priority\":\"P1\",\"status\":\"new\",\"reporter_principal_id\":\"human-actor-1\",\"environment\":\"UAT Chrome\",\"reproduction_steps\":[\"打开详情后点击确认\"],\"expected_result\":\"保存成功\",\"actual_result\":\"没有请求发出\",\"source\":\"chat\"}}}", MediaType.APPLICATION_JSON));
        SematticeProjectDeliveryWriteToolService service = new SematticeProjectDeliveryWriteToolService(
                builder, objectMapper, execution, "https://semattice.example.test");

        JsonNode result = objectMapper.readTree(service.dispatch("org-1", "member-1", "dev-autopilot-pm",
                SematticeProjectDeliveryWriteToolService.confirmedIntent(confirmation).orElseThrow().toArguments(objectMapper)));

        assertThat(result.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(result.path("object_api_name").asText()).isEqualTo("dev_defect");
        assertThat(result.path("code").asText()).startsWith("BUG-");
        assertThat(result.path("revision").asLong()).isEqualTo(1);
        assertThat(result.path("readback_verified").asBoolean()).isTrue();
        assertThat(result.path("delegation_policy").asText()).isEqualTo("TENANT_APP_ROLE");
        server.verify();
    }

    @Test
    void refusesSuccessWhenReadbackDoesNotMatchWrittenFields() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AgentServicePrincipalExecutionService execution = mock(AgentServicePrincipalExecutionService.class);
        OfficialAccessTokenService.IssuedToken token = new OfficialAccessTokenService.IssuedToken(
                "service-oact", Instant.now().plusSeconds(300), "tenant-1", "org-1",
                List.of("runtime.record.read", "runtime.record.create"));
        when(execution.authorizeSemattice("org-1", "member-1", "dev-autopilot-pm",
                List.of("runtime.record.read", "runtime.record.create"), "semattice_project_delivery_create"))
                .thenReturn(new AgentServicePrincipalExecutionService.ExecutionAuthorization(
                        "service-1", "DEV Autopilot 产品经理", "owner-1", "PRIMARY_OWNER", token));
        server.expect(requestTo("https://semattice.example.test/v1/capabilities/runtime.record.create/invoke"))
                .andRespond(withSuccess("{\"status\":\"succeeded\",\"result\":{\"record_id\":\"019fb381-622b-73b9-b8c8-b97181509010\"}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://semattice.example.test/v1/capabilities/runtime.record.get/invoke"))
                .andRespond(withSuccess("{\"status\":\"succeeded\",\"result\":{\"record_id\":\"019fb381-622b-73b9-b8c8-b97181509010\",\"revision\":1,\"data\":{\"name\":\"其他项目\"}}}", MediaType.APPLICATION_JSON));
        SematticeProjectDeliveryWriteToolService service = new SematticeProjectDeliveryWriteToolService(
                builder, objectMapper, execution, "https://semattice.example.test");

        JsonNode result = objectMapper.readTree(service.dispatch("org-1", "member-1", "dev-autopilot-pm",
                "{\"operation\":\"create_project\",\"name\":\"棕榈地\"}"));

        assertThat(result.path("status").asText()).isEqualTo("FAILED");
        assertThat(result.path("code").asText()).isEqualTo("SEMATTICE_WRITE_UNVERIFIED");
        assertThat(result.path("message").asText()).contains("不能确认创建成功");
        server.verify();
    }

    @Test
    void rejectsCallerSuppliedTenantBeforeAnyRemoteCall() throws Exception {
        SematticeProjectDeliveryWriteToolService service = new SematticeProjectDeliveryWriteToolService(
                RestClient.builder(), objectMapper, mock(AgentServicePrincipalExecutionService.class),
                "https://semattice.example.test");

        JsonNode result = objectMapper.readTree(service.dispatch("org-1", "member-1", "dev-autopilot-pm",
                "{\"operation\":\"create_project\",\"name\":\"棕榈地\",\"tenant_id\":\"other\"}"));

        assertThat(result.path("status").asText()).isEqualTo("FAILED");
        assertThat(result.path("code").asText()).isEqualTo("INVALID_ARGUMENTS");
    }
}
