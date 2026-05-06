package com.codehouse.ciciassistant.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

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
    void shouldTellModelNotToPromiseFutureToolRetriesInFinalAnswer() {
        String promptBlock = ChatOrchestratorService.buildToolFinalAnswerGuardPrompt();

        assertThat(promptBlock)
                .contains("不要承诺“稍后/继续/让我重新查询”")
                .contains("缺少必需参数或参数问题")
                .contains("不要让用户误以为系统仍会自动继续回复");
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
}
