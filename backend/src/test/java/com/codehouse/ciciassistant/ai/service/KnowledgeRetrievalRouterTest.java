package com.codehouse.ciciassistant.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeRetrievalRouterTest {

    @Test
    void shouldReturnObservableDecisionForProductAndCompanyKnowledge() {
        KnowledgeRetrievalRouter.Decision product = KnowledgeRetrievalRouter.decide(
                "CloudCC 产品都有什么功能",
                List.of("12"),
                List.of(),
                "web:after-sales");
        KnowledgeRetrievalRouter.Decision company = KnowledgeRetrievalRouter.decide(
                "介绍一下CloudCC这家公司",
                List.of("12"),
                List.of(),
                "web:after-sales");

        assertThat(product.shouldRetrieve()).isTrue();
        assertThat(product.reason()).isEqualTo(KnowledgeRetrievalRouter.Reason.KNOWLEDGE_INTENT_MATCH);
        assertThat(product.matchedCategory()).isEqualTo("product_knowledge");
        assertThat(product.matchedTerm()).isEqualTo("产品");
        assertThat(product.policyVersion()).isEqualTo("rag-router-v1");

        assertThat(company.shouldRetrieve()).isTrue();
        assertThat(company.reason()).isEqualTo(KnowledgeRetrievalRouter.Reason.KNOWLEDGE_INTENT_MATCH);
        assertThat(company.matchedCategory()).isEqualTo("company_profile");
    }

    @Test
    void shouldReturnObservableDecisionForDeploymentAndDocumentKnowledge() {
        KnowledgeRetrievalRouter.Decision deployment = KnowledgeRetrievalRouter.decide(
                "CloudCC私有云部署注意事项有哪些",
                List.of("12"),
                List.of(),
                "web:after-sales");
        KnowledgeRetrievalRouter.Decision document = KnowledgeRetrievalRouter.decide(
                "根据产品文档说明一下触发器怎么配置",
                List.of("12"),
                List.of(),
                "web:after-sales");

        assertThat(deployment.shouldRetrieve()).isTrue();
        assertThat(deployment.reason()).isEqualTo(KnowledgeRetrievalRouter.Reason.KNOWLEDGE_INTENT_MATCH);
        assertThat(deployment.matchedCategory()).isEqualTo("deployment_implementation");

        assertThat(document.shouldRetrieve()).isTrue();
        assertThat(document.reason()).isEqualTo(KnowledgeRetrievalRouter.Reason.KNOWLEDGE_INTENT_MATCH);
        assertThat(document.matchedCategory()).isIn("documentation", "product_knowledge");
    }

    @Test
    void shouldSkipCasualAndBusinessToolIntentWithStableReasons() {
        KnowledgeRetrievalRouter.Decision casual = KnowledgeRetrievalRouter.decide(
                "你好，上才艺",
                List.of("12"),
                List.of(),
                "web:after-sales");
        KnowledgeRetrievalRouter.Decision business = KnowledgeRetrievalRouter.decide(
                "看下今天的潜在客户和订阅台账明细",
                List.of("12"),
                List.of(),
                "web:after-sales");

        assertThat(casual.shouldRetrieve()).isFalse();
        assertThat(casual.reason()).isEqualTo(KnowledgeRetrievalRouter.Reason.CASUAL_CONVERSATION);

        assertThat(business.shouldRetrieve()).isFalse();
        assertThat(business.reason()).isEqualTo(KnowledgeRetrievalRouter.Reason.BUSINESS_TOOL_INTENT);
    }

    @Test
    void shouldForceRetrievalForWecomCustomerServiceAfterNonCasualQuestion() {
        KnowledgeRetrievalRouter.Decision order = KnowledgeRetrievalRouter.decide(
                "我的订单 SO-001 怎么还没有发货",
                List.of("12"),
                List.of(),
                "wecom-kf:customer-session");
        KnowledgeRetrievalRouter.Decision hello = KnowledgeRetrievalRouter.decide(
                "你好",
                List.of("12"),
                List.of(),
                "wecom-kf:customer-session");

        assertThat(order.shouldRetrieve()).isTrue();
        assertThat(order.reason()).isEqualTo(KnowledgeRetrievalRouter.Reason.WECOM_KF_KNOWLEDGE_FIRST);
        assertThat(order.matchedCategory()).isEqualTo("wecom_customer_service");

        assertThat(hello.shouldRetrieve()).isFalse();
        assertThat(hello.reason()).isEqualTo(KnowledgeRetrievalRouter.Reason.CASUAL_CONVERSATION);
    }

    @Test
    void shouldSkipWhenNoEffectiveKnowledgeBaseExistsEvenWhenRequested() {
        KnowledgeRetrievalRouter.Decision decision = KnowledgeRetrievalRouter.decide(
                "根据知识库说明报销制度",
                List.of(),
                List.of("12"),
                "web:after-sales");

        assertThat(decision.shouldRetrieve()).isFalse();
        assertThat(decision.reason()).isEqualTo(KnowledgeRetrievalRouter.Reason.NO_EFFECTIVE_KNOWLEDGE_BASE);
    }
}
