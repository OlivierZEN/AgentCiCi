package com.codehouse.ciciassistant.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.codehouse.ciciassistant.ai.service.AliyunBailianClient.ChatCompletionResult;
import com.codehouse.ciciassistant.ai.service.AliyunBailianClient.ToolCallInfo;
import com.codehouse.ciciassistant.ai.service.DevAutopilotDialogueDecisionService.DialogueDecision;
import com.codehouse.ciciassistant.semattice.SematticeProjectDeliveryWriteToolService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DevAutopilotDialogueDecisionServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final DevAutopilotDialogueDecisionService service =
            new DevAutopilotDialogueDecisionService(mock(AliyunBailianClient.class), objectMapper);

    @Test
    void acceptsModelSemanticDecisionForImplicitCreateWithoutInspectingUserWording() throws Exception {
        String userMessage = "我们还缺一块统一承接销售智能应用交付的工作空间，叫 CCSales 智能应用";
        Map<String, Object> arguments = completeArguments();
        arguments.put("action", "CREATE_DRAFT");
        arguments.put("object_type", "PROJECT");
        arguments.put("confidence", 0.94);
        arguments.put("name", "CCSales 智能应用");
        arguments.put("original_report", userMessage);

        var parsed = service.parse(completion(arguments),
                List.of(Map.of("role", "user", "content", userMessage)), userMessage);

        assertThat(parsed).hasValueSatisfying(decision -> {
            assertThat(decision.action()).isEqualTo("CREATE_DRAFT");
            assertThat(decision.name()).isEqualTo("CCSales 智能应用");
            assertThat(service.fixedAnswer(decision)).hasValueSatisfying(answer -> {
                assertThat(answer).startsWith("## 研发项目创建草案");
                assertThat(answer).contains("| 项目名称 | CCSales 智能应用 |");
                assertThat(answer).contains("`确认创建项目：CCSales 智能应用`");
                assertThat(answer).contains("数据写入：未执行");
            });
        });
    }

    @Test
    void respectsWholeSentenceNegationEvenWhenItContainsCreateProjectWords() throws Exception {
        String userMessage = "不要创建项目，我只是想了解为什么上次创建失败";
        Map<String, Object> arguments = completeArguments();
        arguments.put("action", "OTHER");
        arguments.put("object_type", "UNKNOWN");
        arguments.put("confidence", 0.97);
        arguments.put("reason", "用户否定执行创建，并询问历史失败原因");
        arguments.put("original_report", userMessage);

        DialogueDecision decision = service.parse(completion(arguments),
                List.of(Map.of("role", "user", "content", userMessage)), userMessage).orElseThrow();

        assertThat(decision.action()).isEqualTo("OTHER");
        assertThat(service.fixedAnswer(decision)).isEmpty();
    }

    @Test
    void rendersIdenticalStandardDraftForTheSameStructuredDecision() {
        DialogueDecision decision = projectDecision("autopilot测试项目2");

        String first = service.fixedAnswer(decision).orElseThrow();
        String replay = service.fixedAnswer(decision).orElseThrow();

        assertThat(replay).isEqualTo(first);
        assertThat(first).contains("| 初始状态 | 规划中 |", "| 健康度 | 待评估 |", "| 版本 | v0.1.0 |");
    }

    @Test
    void acceptsActionRelevantFieldsWithoutRequiringEveryIrrelevantDraftField() throws Exception {
        String userMessage = "创建一个项目：autopilot测试项目3";
        Map<String, Object> arguments = new java.util.LinkedHashMap<>();
        arguments.put("action", "CREATE_DRAFT");
        arguments.put("object_type", "PROJECT");
        arguments.put("confidence", 0.98);
        arguments.put("reason", "用户希望新增一个研发项目");
        arguments.put("name", "autopilot测试项目3");

        DialogueDecision decision = service.parse(completion(arguments),
                List.of(Map.of("role", "user", "content", userMessage)), userMessage).orElseThrow();

        assertThat(service.fixedAnswer(decision)).hasValueSatisfying(answer -> {
            assertThat(answer).startsWith("## 研发项目创建草案");
            assertThat(answer).contains("| 项目名称 | autopilot测试项目3 |");
            assertThat(answer).contains("`确认创建项目：autopilot测试项目3`");
        });
    }

    @Test
    void lowConfidenceDecisionUsesOneFixedClarificationAndNeverQueriesOrWrites() {
        DialogueDecision decision = new DialogueDecision(
                "QUERY", "PROJECT", 0.51, "语义不足", "", "", "", "", "", "",
                "P2", "medium", "", List.of(), "", "", List.of(), List.of(),
                "这事怎么处理", List.of(), List.of(), "", "", "", "");

        assertThat(service.fixedAnswer(decision)).hasValue(
                "## 需要补充信息\n\n- 判定状态：需要澄清\n- 数据写入：未执行\n\n"
                        + "请再说明你是要查询现有研发数据，还是新增、调整或删除一项研发事项。");
    }

    @Test
    void standardIntakeMarkerRoundTripsLiteralBracesAndCommentClosers() {
        String original = "希望展示 JSON 示例 }，但不要让 --> 截断受理数据";
        DialogueDecision decision = new DialogueDecision(
                "CREATE_DRAFT", "REQUIREMENT", 0.98, "新增业务能力", "", "DAS-ONE", "",
                "展示结构化示例", "用户期望新增展示能力", "页面应安全展示用户输入", "P2", "medium", "",
                List.of(), "", "", List.of("页面完整展示原始内容"), List.of(), original,
                List.of(), List.of(), "", "", "", "");

        String draft = service.fixedAnswer(decision).orElseThrow();
        var intent = SematticeProjectDeliveryWriteToolService.confirmedIntent(
                        "确认提交",
                        List.of(Map.of("role", "user", "content", original),
                                Map.of("role", "assistant", "content", draft)),
                        "conversation-1",
                        objectMapper)
                .orElseThrow();

        assertThat(draft).contains("\\u002d\\u002d>");
        assertThat(intent.intake().get("original_report")).isEqualTo(original);
    }

    private ChatCompletionResult completion(Map<String, Object> arguments) throws Exception {
        return new ChatCompletionResult("assistant", "", List.of(new ToolCallInfo(
                "decision-1", DevAutopilotDialogueDecisionService.TOOL_NAME,
                objectMapper.writeValueAsString(arguments))), "tool_calls", 20, 30);
    }

    private static Map<String, Object> completeArguments() {
        Map<String, Object> values = new java.util.LinkedHashMap<>();
        values.put("action", "OTHER");
        values.put("object_type", "UNKNOWN");
        values.put("confidence", 1.0);
        for (String field : List.of("reason", "name", "project", "requirement", "title",
                "classification_reason", "pm_assessment", "priority", "severity", "environment",
                "expected_result", "actual_result", "original_report", "clarification_question",
                "record_reference", "source_developer", "target_developer")) {
            values.put(field, "");
        }
        for (String field : List.of("reproduction_steps", "acceptance_criteria", "impact_analysis",
                "user_supplements", "assumptions")) {
            values.put(field, List.of());
        }
        return values;
    }

    private static DialogueDecision projectDecision(String name) {
        return new DialogueDecision(
                "CREATE_DRAFT", "PROJECT", 0.99, "用户希望新增研发项目", name,
                "", "", "", "", "", "P2", "medium", "", List.of(), "", "",
                List.of(), List.of(), "创建一个项目：" + name, List.of(), List.of(), "", "", "", "");
    }
}
