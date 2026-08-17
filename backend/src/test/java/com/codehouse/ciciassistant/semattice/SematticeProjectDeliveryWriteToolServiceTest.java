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
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SematticeProjectDeliveryWriteToolServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void returnsDraftUntilExactProjectConfirmationThenCreatesWithDelegatedToken() throws Exception {
        assertThat(SematticeProjectDeliveryWriteToolService.confirmedIntent("确认创建项目：棕榈地"))
                .hasValueSatisfying(intent -> assertThat(intent.operation()).isEqualTo("create_project"));

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
                builder, objectMapper, execution, mock(DevAutopilotDeveloperAssignmentService.class),
                "https://semattice.example.test");

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
    void confirmedRequirementIsCreatedAsConfirmedAndReadBackBeforeSuccess() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AgentServicePrincipalExecutionService execution = mock(AgentServicePrincipalExecutionService.class);
        OfficialAccessTokenService.IssuedToken token = new OfficialAccessTokenService.IssuedToken(
                "service-oact", Instant.now().plusSeconds(300), "tenant-1", "org-1",
                List.of("runtime.record.read", "runtime.record.create"));
        when(execution.authorizeSemattice("org-1", "member-1", "dev-autopilot-pm",
                List.of("runtime.record.read", "runtime.record.create"), "semattice_project_delivery_create"))
                .thenReturn(new AgentServicePrincipalExecutionService.ExecutionAuthorization(
                        "service-1", "研发产品经理", "owner-1", "PRIMARY_OWNER", token));
        server.expect(requestTo("https://semattice.example.test/v1/capabilities/runtime.record.query/invoke"))
                .andRespond(withSuccess("{\"status\":\"succeeded\",\"result\":{\"records\":[{\"record_id\":\"019fb381-622b-73b9-b8c8-b97181509001\",\"data\":{\"code\":\"DAS-001\"}}]}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://semattice.example.test/v1/capabilities/runtime.record.create/invoke"))
                .andExpect(jsonPath("$.input.object_api_name").value("dev_requirement"))
                .andExpect(jsonPath("$.input.data.status").value("已确认"))
                .andRespond(withSuccess("{\"status\":\"succeeded\",\"result\":{\"record_id\":\"019fb381-622b-73b9-b8c8-b97181509011\"}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://semattice.example.test/v1/capabilities/runtime.record.get/invoke"))
                .andRespond(withSuccess("{\"status\":\"succeeded\",\"result\":{\"record_id\":\"019fb381-622b-73b9-b8c8-b97181509011\",\"revision\":1,\"data\":{\"code\":\"REQ-PLACEHOLDER\",\"project_id\":\"019fb381-622b-73b9-b8c8-b97181509001\",\"title\":\"需求确认状态闭环\",\"status\":\"已确认\",\"priority\":\"P1\",\"owner\":\"研发产品经理\",\"summary\":\"由研发交付产品经理创建\",\"acceptance\":[]}}}", MediaType.APPLICATION_JSON));
        SematticeProjectDeliveryWriteToolService service = new SematticeProjectDeliveryWriteToolService(
                builder, objectMapper, execution, mock(DevAutopilotDeveloperAssignmentService.class),
                "https://semattice.example.test");

        JsonNode result = objectMapper.readTree(service.dispatch("org-1", "member-1", "dev-autopilot-pm",
                "{\"operation\":\"create_requirement\",\"project\":\"DAS-001\",\"title\":\"需求确认状态闭环\"}"));

        assertThat(result.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(result.path("object_api_name").asText()).isEqualTo("dev_requirement");
        assertThat(result.path("readback_verified").asBoolean()).isTrue();
        server.verify();
    }

    @Test
    void restoresAClassifiedDefectFromTheInvisibleDraftOnShortConfirmation() throws Exception {
        String original = "退出后又自动进入系统，没有真正注销";
        String marker = "<!-- DEV_AUTOPILOT_INTAKE_V1 " + objectMapper.writeValueAsString(Map.ofEntries(
                Map.entry("classification", "defect"), Map.entry("project", "DAS-001"),
                Map.entry("requirement", ""), Map.entry("title", "退出登录后会话仍然有效"),
                Map.entry("original_report", "退出后又自动进入系统， 没有真正注销"), Map.entry("pm_assessment", "已有退出能力偏离预期，判定为缺陷"),
                Map.entry("priority", "P1"), Map.entry("severity", "high"),
                Map.entry("environment", "待开发者验证"), Map.entry("reproduction_steps", List.of("待开发者验证")),
                Map.entry("expected_result", "退出后保持未登录"), Map.entry("actual_result", "退出后自动重新进入系统"),
                Map.entry("acceptance_criteria", List.of()), Map.entry("impact_analysis", List.of()),
                Map.entry("user_supplements", List.of()), Map.entry("assumptions", List.of("待开发者验证会话清理链路")),
                Map.entry("clarification_question", ""), Map.entry("ready_for_confirmation", true),
                Map.entry("cancelled", false))) + " -->";
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "user", "content", original),
                Map.of("role", "assistant", "content", "我已完成专业整理，请确认提交。\n" + marker));

        var confirmed = SematticeProjectDeliveryWriteToolService.confirmedIntent(
                "确认提交", messages, "conversation-1", objectMapper);

        assertThat(confirmed).hasValueSatisfying(intent -> {
            assertThat(intent.operation()).isEqualTo("create_defect");
            assertThat(intent.description()).isEqualTo(original);
            assertThat(intent.reproductionSteps()).containsExactly("待开发者验证");
            assertThat(intent.intake()).containsEntry("classification", "defect")
                    .containsEntry("conversation_id", "conversation-1")
                    .containsEntry("developer_verification_pending", true);
        });
        SematticeProjectDeliveryWriteToolService writer = new SematticeProjectDeliveryWriteToolService(
                RestClient.builder(), objectMapper, mock(AgentServicePrincipalExecutionService.class),
                mock(DevAutopilotDeveloperAssignmentService.class), "https://semattice.example.test");
        String firstKey = ReflectionTestUtils.invokeMethod(writer, "correlationId", confirmed.orElseThrow());
        String replayKey = ReflectionTestUtils.invokeMethod(writer, "correlationId", confirmed.orElseThrow());
        assertThat(firstKey).startsWith("cici-delivery-intake-").isEqualTo(replayKey);
        assertThat(SematticeProjectDeliveryWriteToolService.hasPendingIntake(messages)).isTrue();
        List<Map<String, Object>> completedMessages = List.of(
                messages.get(0), messages.get(1), Map.of("role", "user", "content", "确认提交"),
                Map.of("role", "assistant", "content", "已在 Semattice 创建缺陷：退出登录后会话仍然有效。"));
        assertThat(SematticeProjectDeliveryWriteToolService.hasPendingIntake(completedMessages)).isFalse();
        assertThat(SematticeProjectDeliveryWriteToolService.confirmedIntent(
                "确认提交", completedMessages, "conversation-1", objectMapper)).isEmpty();
    }

    @Test
    void restoresNewDefectAfterAProjectWasCompletedInTheSameConversation() throws Exception {
        String original = "提交一个Bug。退出后又自动进入系统，并没有真正注销用户的登录状态。";
        String marker = "<!-- DEV_AUTOPILOT_INTAKE_V1 " + objectMapper.writeValueAsString(Map.ofEntries(
                Map.entry("classification", "defect"), Map.entry("project", "企业级智能体平台"),
                Map.entry("requirement", ""), Map.entry("title", "退出登录后会话仍然有效"),
                Map.entry("original_report", original), Map.entry("pm_assessment", "已有退出能力偏离预期，判定为缺陷"),
                Map.entry("priority", "P1"), Map.entry("severity", "high"),
                Map.entry("environment", "Web"), Map.entry("reproduction_steps", List.of("点击退出")),
                Map.entry("expected_result", "保持未登录"), Map.entry("actual_result", "自动重新进入系统"),
                Map.entry("acceptance_criteria", List.of()), Map.entry("impact_analysis", List.of()),
                Map.entry("user_supplements", List.of()), Map.entry("assumptions", List.of("待开发者验证")),
                Map.entry("clarification_question", ""), Map.entry("ready_for_confirmation", true),
                Map.entry("cancelled", false))) + " -->";
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "user", "content", "创建一个研发项目：企业级智能体平台"),
                Map.of("role", "assistant", "content", "请回复确认创建项目：企业级智能体平台"),
                Map.of("role", "user", "content", "确认创建项目：企业级智能体平台"),
                Map.of("role", "assistant", "content", "已在 Semattice 创建项目：企业级智能体平台（DAS-001）。"),
                Map.of("role", "user", "content", "查看当前所有的研发项目"),
                Map.of("role", "assistant", "content", "当前有 1 个研发项目。"),
                Map.of("role", "user", "content", original),
                Map.of("role", "assistant", "content", "缺陷受理草案，请回复确认提交缺陷。\n" + marker));

        var confirmed = SematticeProjectDeliveryWriteToolService.confirmedIntent(
                "确认提交缺陷", messages, "conversation-sequential", objectMapper);

        assertThat(confirmed).hasValueSatisfying(intent -> {
            assertThat(intent.operation()).isEqualTo("create_defect");
            assertThat(intent.parentReference()).isEqualTo("企业级智能体平台");
            assertThat(intent.description()).isEqualTo(original);
            assertThat(intent.intake()).containsEntry("conversation_id", "conversation-sequential");
        });
        assertThat(SematticeProjectDeliveryWriteToolService.hasPendingIntake(messages)).isTrue();
    }

    @Test
    void rejectsVisibleDefectDraftWhenStructuredMarkerIsMissing() {
        String original = "UAT验收：我在研发交付页面点击项目筛选后，筛选面板偶尔不会展开，需要刷新页面才能恢复。";
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "user", "content", original),
                Map.of("role", "assistant", "content", "## UAT缺陷受理处理中\n缺陷标题：研发交付页面项目筛选面板偶发性无法展开\n请回复：确认提交缺陷"),
                Map.of("role", "user", "content", "确认提交缺陷"),
                Map.of("role", "assistant", "content", "本轮没有获得 Semattice 的真实写入成功回执。"),
                Map.of("role", "user", "content", "项目：智能体平台"),
                Map.of("role", "assistant", "content", """
                        ## UAT 缺陷创建草案
                        **标题**：研发交付页面项目筛选面板偶发性无法展开
                        **优先级**：P2
                        **严重程度**：medium
                        **环境**：UAT
                        **关联项目**：智能体平台 (DAS-751707A5)
                        如你确认无误，回复 `确认创建`。
                        """));

        var confirmed = SematticeProjectDeliveryWriteToolService.confirmedIntent(
                "确认创建", messages, "conversation-uat", objectMapper);

        assertThat(confirmed).isEmpty();
        assertThat(SematticeProjectDeliveryWriteToolService.hasPendingIntake(messages)).isFalse();
    }

    @Test
    void rejectsMarkdownTableDraftWhenStructuredMarkerIsMissing() {
        String original = "反馈这个项目的一个问题： 当我在。登录进入系统之后，然后再点击。左下角的退出图标。系统退出到了登录过渡页面，然后通过统一登录又自动跳转进入了系统。实际上退出动作并没有注销当前用户的登录状态。";
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "assistant", "content", "已在 Semattice 创建项目：企业级智能体平台CCAgent（DAS-A2AFD106）。"),
                Map.of("role", "user", "content", original),
                Map.of("role", "assistant", "content", """
                        ## 缺陷受理草稿（Dev Autopilot）

                        ### 事项分类依据
                        | 判断维度 | 内容 |
                        |----------|------|
                        | **事项类型** | 缺陷（Defect） |
                        | **分类理由** | 已有退出能力偏离正常预期，属于功能故障 |
                        | **关联项目** | 企业级智能体平台 CCAgent（项目编号：DAS-A2AFD106） |

                        ### 专业整理详情
                        | 字段 | 内容 |
                        |------|------|
                        | **缺陷标题** | 退出登录未有效清除会话导致自动重登 |
                        | **优先级** | P2（影响用户使用体验，但不阻塞核心流程） |
                        | **严重度** | medium |
                        | **测试环境** | 待开发者验证 |

                        如需将此缺陷提交至研发管理系统进行跟踪，请回复：`确认提交缺陷`
                        """));

        var confirmed = SematticeProjectDeliveryWriteToolService.confirmedIntent(
                "确认提交缺陷", messages, "workbench:devautopilot-pm", objectMapper);

        assertThat(confirmed).isEmpty();
        assertThat(SematticeProjectDeliveryWriteToolService.hasPendingIntake(messages)).isFalse();
    }

    @Test
    void rejectsProfessionalVisibleRequirementDraftWithoutStructuredMarker() {
        String original = "提个需求，我希望在当前用户与智能体的对话框中，可以通过复制粘贴的方式上传截图，可以连续粘贴多张";
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "user", "content", original),
                Map.of("role", "assistant", "content", """
                        ## 需求受理草稿（Dev Autopilot）

                        ### 事项分类依据
                        | 判断维度 | 内容 |
                        |----------|------|
                        | 事项类型 | 需求（Requirement） |
                        | 分类理由 | 新增当前系统不支持的能力，属于功能增强 |
                        | 关联项目 | 企业级智能体平台 CCAgent（项目编号：DAS-A2AFD106） |

                        ### 专业整理详情
                        | 字段 | 内容 |
                        |------|------|
                        | 需求标题 | 对话框支持截图粘贴上传（多张连续） |
                        | 优先级 | P2（提升用户体验，非核心阻塞） |
                        | 环境 | 待技术团队评估 |

                        ### 用户原始报告（逐字）
                        > 提个需求，我希望在当前用户与智能体的对话框中，可以通过复制粘贴的方式上传截图，可以连续粘贴多张

                        ### 产品经理分析与验收标准
                        **分析要点**:
                        - 需要前端实现剪贴板图片捕获事件监听
                        - 需要后端支持图片接收与存储
                        - 需要考虑连续粘贴的队列管理与上传状态提示
                        - 需处理不同格式图片的兼容

                        **验收标准**:
                        - 用户在对话框内 Ctrl+V 或 Command+V 可插入本地截图
                        - 支持连续多次粘贴操作（无次数限制）
                        - 粘贴后即时显示缩略图预览
                        - 提供已上传图片的管理（删除/替换）
                        - 后台完成实际上传并关联到对话记录

                        ---

                        ### 待开发者验证项
                        | 问题 | 说明 |
                        |------|------|
                        | 技术可行性 | 浏览器剪贴板 API 对图片访问权限范围 |
                        | 性能影响 | 大尺寸图片或高频连续粘贴的资源占用 |
                        | 兼容性 | 主流浏览器版本的 API 支持情况 |
                        | 安全合规 | 图片上传是否涉及敏感内容过滤 |

                        如需将此需求提交至研发管理系统进行跟踪，请回复：`确认提交需求`
                        """));

        var confirmed = SematticeProjectDeliveryWriteToolService.confirmedIntent(
                "确认提交需求", messages, "workbench:devautopilot-pm", objectMapper);

        assertThat(confirmed).isEmpty();
        assertThat(SematticeProjectDeliveryWriteToolService.hasPendingIntake(messages)).isFalse();
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
                builder, objectMapper, execution, mock(DevAutopilotDeveloperAssignmentService.class),
                "https://semattice.example.test");

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
                builder, objectMapper, execution, mock(DevAutopilotDeveloperAssignmentService.class),
                "https://semattice.example.test");

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
                mock(DevAutopilotDeveloperAssignmentService.class),
                "https://semattice.example.test");

        JsonNode result = objectMapper.readTree(service.dispatch("org-1", "member-1", "dev-autopilot-pm",
                "{\"operation\":\"create_project\",\"name\":\"棕榈地\",\"tenant_id\":\"other\"}"));

        assertThat(result.path("status").asText()).isEqualTo("FAILED");
        assertThat(result.path("code").asText()).isEqualTo("INVALID_ARGUMENTS");
    }
}
