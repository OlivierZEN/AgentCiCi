package com.codehouse.ciciassistant.semattice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.codehouse.ciciassistant.agent.service.AgentServicePrincipalExecutionService;
import com.codehouse.ciciassistant.ai.domain.ChatMessageEntity;
import com.codehouse.ciciassistant.ai.domain.ChatMessageRepository;
import com.codehouse.ciciassistant.ai.domain.ChatSessionEntity;
import com.codehouse.ciciassistant.ai.domain.ChatSessionRepository;
import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import com.codehouse.ciciassistant.platform.service.PlatformAuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DevAutopilotIntakeReconciliationServiceTest {

    private static final String SESSION_ID = "workbench:devautopilot-pm";
    private static final String RECORD_ID = "019ff668-6874-7348-ab3c-6d1c2635ad0a";
    private static final String ORIGINAL =
            "提个需求，我希望在当前用户与智能体的对话框中，可以通过通过复制粘贴的方式上传截图，可以连续粘贴多张";
    private static final Instant RECONCILED_AT = Instant.parse("2026-08-13T01:00:00Z");

    @Test
    void reconcilesHistoricalRequirementFromPersistedConfirmedDraftAndReadsBackEveryField() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        ChatSessionRepository sessions = mock(ChatSessionRepository.class);
        ChatMessageRepository messages = mock(ChatMessageRepository.class);
        AgentServicePrincipalExecutionService execution = mock(AgentServicePrincipalExecutionService.class);
        PlatformAuditService audit = mock(PlatformAuditService.class);
        ChatSessionEntity session = new ChatSessionEntity(
                SESSION_ID, "org-1", "member-1", "devautopilot-pm", "需求受理");
        when(sessions.findByIdAndCompanyId(SESSION_ID, "org-1")).thenReturn(Optional.of(session));
        List<ChatMessageEntity> history = history();
        when(messages.findByCompanyIdAndSessionIdOrderByCreatedAtAsc("org-1", SESSION_ID)).thenReturn(history);

        OfficialAccessTokenService.IssuedToken token = new OfficialAccessTokenService.IssuedToken(
                "service-oact", Instant.now().plusSeconds(300), "tenant-1", "org-1",
                List.of("runtime.record.read", "runtime.record.update"));
        when(execution.authorizeSemattice("org-1", "member-1", "devautopilot-pm",
                List.of("runtime.record.read", "runtime.record.update"), "devautopilot_intake_reconciliation"))
                .thenReturn(new AgentServicePrincipalExecutionService.ExecutionAuthorization(
                        "service-1", "研发产品经理", "owner-1", "human-actor-1",
                        "TENANT_APP_ROLE", "APP_ADMIN", token));

        SematticeProjectDeliveryWriteToolService.CreateIntent intent =
                SematticeProjectDeliveryWriteToolService.confirmedIntent(
                                "确认提交需求", messageMaps(history.subList(0, 2)), SESSION_ID, objectMapper)
                        .orElseThrow();
        Map<String, Object> currentData = genericRecordData();
        Map<String, Object> expectedData = new LinkedHashMap<>(currentData);
        expectedData.put("summary", intent.description());
        expectedData.put("priority", intent.priority());
        expectedData.put("acceptance", intent.acceptanceCriteria());
        @SuppressWarnings("unchecked")
        Map<String, Object> repairedIntake = new LinkedHashMap<>((Map<String, Object>) currentData.get("intake"));
        repairedIntake.putAll(intent.intake());
        repairedIntake.put("reconciled_at", RECONCILED_AT.toString());
        repairedIntake.put("reconciled_by_principal_id", "human-actor-1");
        repairedIntake.put("reconciliation_source", "confirmed_conversation_draft");
        repairedIntake.put("conversation_id", SESSION_ID);
        expectedData.put("intake", repairedIntake);

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://semattice.example.test/v1/capabilities/runtime.record.get/invoke"))
                .andRespond(withSuccess(recordResponse(objectMapper, 1, currentData), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://semattice.example.test/v1/capabilities/runtime.record.update/invoke"))
                .andExpect(jsonPath("$.input.expected_revision").value(1))
                .andExpect(jsonPath("$.input.patch.acceptance.length()").value(5))
                .andExpect(jsonPath("$.input.patch.intake.assumptions.length()").value(4))
                .andExpect(jsonPath("$.input.patch.intake.pm_assessment").value(org.hamcrest.Matchers.containsString(
                        "剪贴板图片捕获事件监听")))
                .andRespond(withSuccess("{\"status\":\"succeeded\",\"result\":{\"revision\":2}}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://semattice.example.test/v1/capabilities/runtime.record.get/invoke"))
                .andRespond(withSuccess(recordResponse(objectMapper, 2, expectedData), MediaType.APPLICATION_JSON));

        DevAutopilotIntakeReconciliationService service = new DevAutopilotIntakeReconciliationService(
                builder, objectMapper, sessions, messages, execution, audit,
                "https://semattice.example.test", Clock.fixed(RECONCILED_AT, ZoneOffset.UTC));

        var result = service.reconcile("org-1", "member-1", SESSION_ID, RECORD_ID);

        assertThat(result.status()).isEqualTo("UPDATED");
        assertThat(result.revision()).isEqualTo(2);
        assertThat(result.readbackVerified()).isTrue();
        assertThat(result.contentDigest()).hasSize(64);
        assertThat(result.updatedFields()).contains("summary", "acceptance", "intake");
        verify(audit).log("org-1", "member-1", "ORG_ADMIN", "devautopilot.intake.reconciled",
                "dev_requirement", RECORD_ID,
                "session=" + SESSION_ID + "; original_confirmer=member-1; revision=2; digest="
                        + result.contentDigest());
        server.verify();
    }

    @Test
    void rejectsRecordWithoutTrustedReceiptBeforeRequestingServiceToken() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        ChatSessionRepository sessions = mock(ChatSessionRepository.class);
        ChatMessageRepository messages = mock(ChatMessageRepository.class);
        AgentServicePrincipalExecutionService execution = mock(AgentServicePrincipalExecutionService.class);
        when(sessions.findByIdAndCompanyId(SESSION_ID, "org-1")).thenReturn(Optional.of(
                new ChatSessionEntity(SESSION_ID, "org-1", "member-1", "devautopilot-pm", "需求受理")));
        when(messages.findByCompanyIdAndSessionIdOrderByCreatedAtAsc("org-1", SESSION_ID))
                .thenReturn(history().subList(0, 3));
        DevAutopilotIntakeReconciliationService service = new DevAutopilotIntakeReconciliationService(
                RestClient.builder(), objectMapper, sessions, messages, execution, mock(PlatformAuditService.class),
                "https://semattice.example.test", Clock.fixed(RECONCILED_AT, ZoneOffset.UTC));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        service.reconcile("org-1", "member-1", SESSION_ID, RECORD_ID))
                .hasMessageContaining("成功回执");
        org.mockito.Mockito.verifyNoInteractions(execution);
    }

    private List<ChatMessageEntity> history() {
        return List.of(
                new ChatMessageEntity(SESSION_ID, "org-1", "user", ORIGINAL),
                new ChatMessageEntity(SESSION_ID, "org-1", "assistant", draft()),
                new ChatMessageEntity(SESSION_ID, "org-1", "user", "确认提交需求"),
                new ChatMessageEntity(SESSION_ID, "org-1", "assistant",
                        "已在 Semattice 创建需求：对话框支持截图粘贴上传（多张连续）（REQ-6F34ECF3）。记录 ID："
                                + RECORD_ID + "；revision：1。"));
    }

    private static List<Map<String, Object>> messageMaps(List<ChatMessageEntity> entities) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ChatMessageEntity entity : entities) {
            result.add(Map.of("role", entity.getRoleCode(), "content", entity.getContent()));
        }
        return result;
    }

    private static String recordResponse(ObjectMapper objectMapper, long revision, Map<String, Object> data)
            throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "status", "succeeded",
                "result", Map.of("record_id", RECORD_ID, "revision", revision, "data", data)));
    }

    private static Map<String, Object> genericRecordData() {
        Map<String, Object> intake = new LinkedHashMap<>();
        intake.put("version", "DEV_AUTOPILOT_INTAKE_V1");
        intake.put("classification", "requirement");
        intake.put("project", "DAS-A2AFD106");
        intake.put("requirement", "");
        intake.put("title", "对话框支持截图粘贴上传（多张连续）");
        intake.put("original_report", ORIGINAL);
        intake.put("pm_assessment", "产品经理根据用户原始描述识别为新增能力或业务结果，分类为需求");
        intake.put("priority", "P2");
        intake.put("severity", "");
        intake.put("environment", "待技术团队评估");
        intake.put("reproduction_steps", List.of());
        intake.put("expected_result", "");
        intake.put("actual_result", "");
        intake.put("acceptance_criteria", List.of("由产品经理与全栈开发者基于用户原始描述细化并验证验收标准"));
        intake.put("impact_analysis", List.of());
        intake.put("user_supplements", List.of());
        intake.put("assumptions", List.of("工程细节由全栈开发者在源代码、开发环境和测试环境中验证"));
        intake.put("developer_verification_pending", true);
        intake.put("confirmed_by_principal_id", "original-human");
        intake.put("confirmed_at", "2026-08-12T14:37:45Z");
        intake.put("correlation_id", "cici-delivery-intake-existing");
        intake.put("conversation_id", SESSION_ID);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("code", "REQ-6F34ECF3");
        data.put("project_id", "019ff462-2dcc-73f5-aefb-32297e578328");
        data.put("title", "对话框支持截图粘贴上传（多张连续）");
        data.put("status", "待确认");
        data.put("priority", "P2");
        data.put("owner", "研发产品经理");
        data.put("summary", "产品经理根据用户原始描述识别为新增能力或业务结果，分类为需求");
        data.put("acceptance", List.of("由产品经理与全栈开发者基于用户原始描述细化并验证验收标准"));
        data.put("intake", intake);
        return data;
    }

    private static String draft() {
        return """
                ## 需求受理草稿 (Dev Autopilot)

                ### 事项分类依据
                | 判断维度 | 内容 |
                |----------|------|
                | **事项类型** | 需求（Requirement） |
                | **分类理由** | 新增当前系统不支持的能力，属于功能增强 |
                | **关联项目** | 企业级智能体平台 CCAgent（项目编号：DAS-A2AFD106） |

                #### 基本信息
                | 字段 | 内容 |
                |------|------|
                | **需求标题** | 对话框支持截图粘贴上传（多张连续） |
                | **优先级** | P2（提升用户体验，非核心阻塞） |
                | **环境** | 待技术团队评估 |

                #### 用户原始报告（逐字）
                > """ + ORIGINAL + """

                #### 产品经理分析与验收标准
                **分析要点：**
                - 需要前端实现剪贴板图片捕获事件监听
                - 需要后端支持图片接收与存储
                - 需要考虑连续粘贴的队列管理与上传状态提示
                - 需处理不同格式（PNG/JPG/BMP）的图片兼容

                **验收标准：**
                - 用户在对话框内 Ctrl+V 或 Command+V 可插入本地截图
                - 支持连续多次粘贴操作（无次数限制）
                - 粘贴后即时显示缩略图预览
                - 提供已上传图片的管理（删除/替换）
                - 后台完成实际上传并关联到对话记录

                #### 待开发者验证项
                | 问题 | 说明 |
                |------|------|
                | 技术可行性 | 浏览器剪贴板 API 对图片访问权限范围 |
                | 性能影响 | 大尺寸图片或高频连续粘贴的资源占用 |
                | 兼容性 | 主流浏览器版本的 API 支持情况 |
                | 安全合规 | 图片上传是否涉及敏感内容过滤 |

                如需将此需求提交至研发管理系统进行跟踪，请回复：`确认提交需求`
                """;
    }
}
