package com.codehouse.ciciassistant.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ToolPlanningIntentRouterTest {

    private static final List<String> SALES_TOOLS = List.of(
            "rag-search", "cloudcc_pageQuery", "quote-generator", "cloudcc_getObjectFields");

    @Test
    void shouldSkipPlanningForAdvisorySalesQuestionsEvenWhenAgentHasTools() {
        assertThat(ToolPlanningIntentRouter.decide(
                "我们是卖办公用品的", SALES_TOOLS, false))
                .isEqualTo(new ToolPlanningIntentRouter.Decision(false, "DIRECT_ADVISORY"));
        assertThat(ToolPlanningIntentRouter.decide(
                "我们有 50 人销售团队，想统一管理客户跟进并自动生成售前建议，请给出一个简要实施方案。",
                SALES_TOOLS, false))
                .isEqualTo(new ToolPlanningIntentRouter.Decision(false, "DIRECT_ADVISORY"));
    }

    @Test
    void shouldPlanForExplicitLiveLookupAndBusinessActions() {
        assertThat(ToolPlanningIntentRouter.decide(
                "查询今天的订单", SALES_TOOLS, false).shouldPlan()).isTrue();
        assertThat(ToolPlanningIntentRouter.decide(
                "从 CRM 获取最近的客户跟进记录", SALES_TOOLS, false).shouldPlan()).isTrue();
        assertThat(ToolPlanningIntentRouter.decide(
                "为这个客户生成一份报价", SALES_TOOLS, false).shouldPlan()).isTrue();
        assertThat(ToolPlanningIntentRouter.decide(
                "本月销量最高的产品有哪些", SALES_TOOLS, false).shouldPlan()).isTrue();
    }

    @Test
    void shouldPlanWhenKnowledgeRetrievalIsRequiredAndSkipWhenNoToolsExist() {
        assertThat(ToolPlanningIntentRouter.decide(
                "解释退款规则", SALES_TOOLS, true))
                .isEqualTo(new ToolPlanningIntentRouter.Decision(true, "KNOWLEDGE_REQUIRED"));
        assertThat(ToolPlanningIntentRouter.decide(
                "查询今天的订单", List.of(), false))
                .isEqualTo(new ToolPlanningIntentRouter.Decision(false, "NO_TOOLS"));
    }

    @Test
    void shouldPreservePlanningForAmbiguousRequestsInsteadOfGuessingADataFreeAnswer() {
        assertThat(ToolPlanningIntentRouter.decide(
                "处理一下待办", SALES_TOOLS, false))
                .isEqualTo(new ToolPlanningIntentRouter.Decision(true, "AMBIGUOUS_TOOL_INTENT"));
    }
}
