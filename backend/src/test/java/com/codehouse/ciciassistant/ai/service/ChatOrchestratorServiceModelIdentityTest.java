package com.codehouse.ciciassistant.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.codehouse.ciciassistant.ai.service.AliyunBailianClient.ToolCallInfo;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChatOrchestratorServiceModelIdentityTest {

    @Test
    void shouldTellModelTheActualRoutedProviderAndModel() {
        String promptBlock = ChatOrchestratorService.buildModelIdentityPromptBlock(
                "aliyun-bailian",
                "deepseek-v4-pro");

        assertThat(promptBlock)
                .contains("阿里云百炼 (aliyun-bailian)")
                .contains("deepseek-v4-pro")
                .contains("只能依据以上两项回答")
                .contains("不得自称 Claude");
    }

    @Test
    void shouldKeepAnthropicLabelOnlyWhenProviderIsAnthropic() {
        String promptBlock = ChatOrchestratorService.buildModelIdentityPromptBlock(
                "anthropic",
                "claude-sonnet-4-5");

        assertThat(promptBlock)
                .contains("Anthropic")
                .contains("claude-sonnet-4-5");
    }

    @Test
    void shouldTellModelNotToUseToolsForCasualConversation() {
        String promptBlock = ChatOrchestratorService.buildToolUseBoundaryPromptBlock("assistant-ui-1");

        assertThat(promptBlock)
                .contains("寒暄、闲聊")
                .contains("必须直接用文本回答，不要调用任何工具")
                .contains("不要把每句对话都当成知识库问答")
                .doesNotContain("当前会话来自飞书渠道");
    }

    @Test
    void shouldTreatFeishuConversationAsCasualUnlessBusinessActionIsExplicit() {
        String promptBlock = ChatOrchestratorService.buildToolUseBoundaryPromptBlock("feishu:tenant:chat");

        assertThat(promptBlock)
                .contains("当前会话来自飞书渠道")
                .contains("除非用户明确提出业务数据查询或操作，不要触发工具");
    }

    @Test
    void shouldTreatWecomKfConversationAsKnowledgeOnlyCustomerService() {
        String promptBlock = ChatOrchestratorService.buildToolUseBoundaryPromptBlock("wecom-kf:abc123");

        assertThat(promptBlock)
                .contains("当前会话来自企业微信「微信客服」")
                .contains("只做知识库售后问答")
                .contains("不查询或操作 CRM、订单、客户档案、工单、物流");
    }

    @Test
    void shouldTellModelNotToPromiseFutureToolRetriesInFinalAnswer() {
        String promptBlock = ChatOrchestratorService.buildToolFinalAnswerGuardPrompt();

        assertThat(promptBlock)
                .contains("不要承诺“稍后/继续/让我重新查询”")
                .contains("缺少必需参数或参数问题")
                .contains("不要让用户误以为系统仍会自动继续回复");
    }

    @Test
    void shouldUseShortPromptForToolPlanningStopChecks() {
        String promptBlock = ChatOrchestratorService.buildToolPlanningStopPrompt();

        assertThat(promptBlock)
                .contains("只判断是否必须继续调用工具")
                .contains("READY_TO_FINALIZE")
                .contains("email_get_message")
                .contains("不要为了润色、总结、排序或格式化而继续请求工具");
    }

    @Test
    void shouldSkipPlanningStopForSingleSuccessfulReadonlyLookup() {
        boolean skip = ChatOrchestratorService.shouldSkipToolPlanningStop(
                "看下今天的潜在客户数据并汇总",
                List.of(new ToolCallInfo("call_1", "get_lead_data", "{\"limit\":20}")),
                List.of(Map.of(
                        "role", "tool",
                        "tool_call_id", "call_1",
                        "content", "潜在客户数据查询结果（返回 20 条，总计 133574 条）：{\"result\":true,\"data\":[]}"
                )));

        assertThat(skip).isTrue();
    }

    @Test
    void shouldKeepPlanningStopForSingleEmailSearchWhenUserAskedForBody() {
        boolean skip = ChatOrchestratorService.shouldSkipToolPlanningStop(
                "和利时环境大数据表排查分析 看下这封邮件内容",
                List.of(new ToolCallInfo("call_1", "email_search", "{\"keyword\":\"和利时环境大数据表排查分析\"}")),
                List.of(Map.of(
                        "role", "tool",
                        "tool_call_id", "call_1",
                        "content", """
                                🔎 在最近 50 封邮件中匹配到 1 封 (keyword='和利时环境大数据表排查分析', from='')：
                                - [2026-06-24 07:25] yilei@cloudcc.com · 回复: 回复: 和利时环境大数据表排查分析 · id=2026062415253318283815@cloudcc.com
                                """
                )));

        assertThat(skip).isFalse();
    }

    @Test
    void shouldExtractSingleEmailMessageIdFromSearchResult() {
        assertThat(ChatOrchestratorService.extractSingleEmailSearchMessageId("""
                🔎 在最近 50 封邮件中匹配到 1 封：
                - [2026-06-24 07:25] yilei@cloudcc.com · 主题 · id=2026062415253318283815@cloudcc.com。
                """))
                .contains("2026062415253318283815@cloudcc.com");

        assertThat(ChatOrchestratorService.extractSingleEmailSearchMessageId("""
                - first · id=first@cloudcc.com
                - second · id=second@cloudcc.com
                """))
                .isEmpty();
    }

    @Test
    void shouldKeepPlanningStopForWritesFailuresOrMultipleTools() {
        assertThat(ChatOrchestratorService.shouldSkipToolPlanningStop(
                "查到客户后发送邮件",
                List.of(new ToolCallInfo("call_1", "get_lead_data", "{}")),
                List.of(Map.of("role", "tool", "content", "{\"result\":true,\"data\":[]}"))))
                .isFalse();

        assertThat(ChatOrchestratorService.shouldSkipToolPlanningStop(
                "看下潜在客户",
                List.of(new ToolCallInfo("call_1", "email_send", "{}")),
                List.of(Map.of("role", "tool", "content", "✅ 已发送"))))
                .isFalse();

        assertThat(ChatOrchestratorService.shouldSkipToolPlanningStop(
                "看下潜在客户",
                List.of(new ToolCallInfo("call_1", "get_lead_data", "{}")),
                List.of(Map.of("role", "tool", "content", "❌ 缺少必需参数: objectApiName"))))
                .isFalse();

        assertThat(ChatOrchestratorService.shouldSkipToolPlanningStop(
                "看下潜在客户",
                List.of(
                        new ToolCallInfo("call_1", "get_lead_data", "{}"),
                        new ToolCallInfo("call_2", "get_object_fields", "{}")),
                List.of(Map.of("role", "tool", "content", "{\"result\":true}"))))
                .isFalse();

        assertThat(ChatOrchestratorService.shouldSkipToolPlanningStop(
                "查询潜在客户数据",
                List.of(new ToolCallInfo("call_1", "get_object_fields", "{\"objprefix\":\"00Q\"}")),
                List.of(Map.of("role", "tool", "content", "对象字段列表（标准字段 10 条，自定义字段 41 条）：{\"result\":true}"))))
                .isFalse();
    }

    @Test
    void shouldAllowMetadataLookupSkipOnlyWhenUserAskedForMetadata() {
        assertThat(ChatOrchestratorService.shouldSkipToolPlanningStop(
                "列出线索对象字段",
                List.of(new ToolCallInfo("call_1", "get_object_fields", "{\"objprefix\":\"00Q\"}")),
                List.of(Map.of("role", "tool", "content", "对象字段列表（标准字段 10 条，自定义字段 41 条）：{\"result\":true}"))))
                .isTrue();
    }

    @Test
    void shouldRetrieveKnowledgeWhenKbIsExplicitlyRequested() {
        assertThat(ChatOrchestratorService.shouldUseKnowledgeRetrieval(
                "你好，帮我看一下",
                List.of("12"),
                List.of("12")))
                .isTrue();
    }

    @Test
    void shouldRetrieveKnowledgeForKnowledgeIntentWithDefaultKb() {
        assertThat(ChatOrchestratorService.shouldUseKnowledgeRetrieval(
                "根据产品文档说明一下触发器怎么配置",
                List.of("12"),
                List.of()))
                .isTrue();
    }

    @Test
    void shouldSkipKnowledgeForCasualOrBusinessToolIntentWithDefaultKb() {
        assertThat(ChatOrchestratorService.shouldUseKnowledgeRetrieval(
                "你好，上才艺",
                List.of("12"),
                List.of()))
                .isFalse();

        assertThat(ChatOrchestratorService.shouldUseKnowledgeRetrieval(
                "看下今天的潜在客户和订阅台账明细",
                List.of("12"),
                List.of()))
                .isFalse();
    }

    @Test
    void shouldForceKnowledgeRetrievalForWecomCustomerServiceWhenDefaultKbExists() {
        assertThat(ChatOrchestratorService.shouldUseKnowledgeRetrieval(
                "我的订单 SO-001 怎么还没有发货",
                List.of("12"),
                List.of(),
                "wecom-kf:customer-session"))
                .isTrue();

        assertThat(ChatOrchestratorService.shouldUseKnowledgeRetrieval(
                "你好",
                List.of("12"),
                List.of(),
                "wecom-kf:customer-session"))
                .isFalse();
    }

    @Test
    void shouldSkipKnowledgeWhenNoEffectiveKbExists() {
        assertThat(ChatOrchestratorService.shouldUseKnowledgeRetrieval(
                "根据知识库说明报销制度",
                List.of(),
                List.of("12")))
                .isFalse();
    }

    @Test
    void shouldSummarizeStructuredToolFallbackInsteadOfDumpingRawJson() {
        String toolJson = """
                {"success":true,"answer":null,"responseTime":1.31,"results":[
                  {"title":"Top 10 Semiconductor Companies in Asia in 2026","url":"https://example.com/asia","snippet":"Taiwan Semiconductor Manufacturing Company leads advanced chip manufacturing."},
                  {"title":"Semiconductor Stocks News","url":"https://example.com/stocks","snippet":"Nvidia and Micron are highlighted in the 2026 outlook."}
                ]}
                """;

        String fallback = ChatOrchestratorService.buildToolResultFallbackMessage(List.of(Map.of(
                "role", "tool",
                "content", toolJson
        )));

        assertThat(fallback)
                .contains("工具已返回 2 条结果")
                .contains("模型本轮未能生成最终自然语言总结")
                .contains("Top 10 Semiconductor Companies")
                .contains("来源：https://example.com/asia")
                .doesNotContain("\"success\"")
                .doesNotContain("\"results\"");
    }

    @Test
    void shouldAppendToolSummaryWhenFinalAnswerOnlyPromisesFollowup() {
        String toolJson = """
                {"success":true,"results":[
                  {"title":"白糖现货价格日报","url":"https://example.com/sugar","snippet":"广西、云南主产区报价小幅上行。"}
                ]}
                """;

        String guarded = ChatOrchestratorService.appendToolResultFallbackIfDeferred(
                "数据比较丰富，接下来我再抽取 CRM 中的客户反馈数据，补充客户感知维度。",
                List.of(Map.of("role", "tool", "content", toolJson)));

        assertThat(guarded)
                .contains("本轮不会在完成状态后自动追加回复")
                .contains("白糖现货价格日报")
                .contains("广西、云南主产区报价小幅上行");
    }

    @Test
    void shouldNotAppendToolSummaryForConcreteFinalAnswer() {
        String guarded = ChatOrchestratorService.appendToolResultFallbackIfDeferred(
                "已完成白糖价格与客户反馈分析，结论是短期采购意愿受价格波动影响。",
                List.of(Map.of("role", "tool", "content", "{\"success\":true,\"results\":[{\"title\":\"结果\"}]}")));

        assertThat(guarded).doesNotContain("本轮不会在完成状态后自动追加回复");
    }

    @Test
    void shouldBuildReadableFallbackWhenToolLimitIsReached() {
        List<Map<String, Object>> messages = List.of(
                Map.of(
                        "role", "assistant",
                        "content", "",
                        "tool_calls", List.of(Map.of(
                                "id", "call_1",
                                "type", "function",
                                "function", Map.of(
                                        "name", "get_object_data",
                                        "arguments", "{\"objapi\":\"monthkpi\",\"expressions\":\"khperson='龚俊杰'\"}"
                                )
                        ))
                ),
                Map.of(
                        "role", "tool",
                        "tool_call_id", "call_1",
                        "content", "对象数据查询结果（返回 0 条，总计 0 条）：\n{\"result\":true,\"data\":[],\"totalCount\":0,\"returnCount\":0}"
                ),
                Map.of(
                        "role", "assistant",
                        "content", "",
                        "tool_calls", List.of(Map.of(
                                "id", "call_2",
                                "type", "function",
                                "function", Map.of(
                                        "name", "get_object_fields",
                                        "arguments", "{\"objprefix\":\"a49\"}"
                                )
                        ))
                ),
                Map.of(
                        "role", "tool",
                        "tool_call_id", "call_2",
                        "content", "对象字段列表（标准字段 10 条，自定义字段 41 条）：\n{\"result\":true}"
                )
        );

        String fallback = ChatOrchestratorService.buildToolLimitReachedFallbackMessage(messages, 4);

        assertThat(fallback)
                .contains("本轮已经完成 2 次工具查询")
                .contains("get_object_data：查询完成，但没有返回匹配记录")
                .contains("monthkpi")
                .contains("get_object_fields：读取到对象字段结构：标准字段 10 条、自定义字段 41 条")
                .contains("下一步可以确认对象名称、人员姓名、月份/季度字段或筛选条件")
                .doesNotContain("系统保护上限")
                .doesNotContain("暂时无法继续处理");
    }
}
