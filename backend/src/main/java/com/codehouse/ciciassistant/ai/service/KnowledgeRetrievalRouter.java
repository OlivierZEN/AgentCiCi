package com.codehouse.ciciassistant.ai.service;

import java.util.List;
import java.util.Locale;

final class KnowledgeRetrievalRouter {

    static final String POLICY_VERSION = "rag-router-v1";

    private static final List<IntentCategory> KNOWLEDGE_CATEGORIES = List.of(
            new IntentCategory("company_profile", List.of(
                    "公司", "简介", "介绍", "企业介绍", "公司介绍", "关于我们")),
            new IntentCategory("deployment_implementation", List.of(
                    "部署", "私有云", "公有云", "注意事项", "最佳实践", "解决方案", "实施指南", "上线", "交付")),
            new IntentCategory("policy_process", List.of(
                    "制度", "政策", "流程", "规则", "规范", "条款", "口径", "依据", "报销制度", "价格政策")),
            new IntentCategory("documentation", List.of(
                    "知识库", "知识", "文档", "资料", "手册", "faq", "常见问题", "说明书", "操作指南", "配置", "产品说明")),
            new IntentCategory("product_knowledge", List.of(
                    "产品", "功能", "能力", "模块", "特性", "特色", "都有什么", "有哪些", "能做什么", "支持哪些", "包括哪些"))
    );

    private static final List<String> CASUAL_TERMS = List.of(
            "你好", "您好", "早上好", "晚上好", "谢谢", "感谢", "辛苦了",
            "讲个笑话", "上才艺", "唱首歌", "写首诗", "角色扮演", "随便聊聊");

    private static final List<String> BUSINESS_TOOL_TERMS = List.of(
            "查询", "查一下", "看下", "看一下", "拉取", "获取", "列出", "列表", "明细", "台账",
            "客户", "线索", "商机", "报价", "订单", "审批", "待办", "日程", "邮件", "发送", "创建", "更新");

    private KnowledgeRetrievalRouter() {
    }

    static Decision decide(String question,
                           List<String> effectiveKnowledgeBaseIds,
                           List<String> requestedKnowledgeBaseIds,
                           String sessionId) {
        if (effectiveKnowledgeBaseIds == null || effectiveKnowledgeBaseIds.isEmpty()) {
            return Decision.skip(Reason.NO_EFFECTIVE_KNOWLEDGE_BASE);
        }
        if (requestedKnowledgeBaseIds != null && !requestedKnowledgeBaseIds.isEmpty()) {
            return Decision.retrieve(Reason.EXPLICIT_KNOWLEDGE_BASE_REQUEST, "explicit_request", "");
        }
        String text = question == null ? "" : question.trim().toLowerCase(Locale.ROOT);
        if (text.isBlank()) {
            return Decision.skip(Reason.BLANK_INPUT);
        }
        String casualTerm = firstMatch(text, CASUAL_TERMS);
        if (!casualTerm.isBlank()) {
            return Decision.skip(Reason.CASUAL_CONVERSATION, "casual", casualTerm);
        }
        for (IntentCategory category : KNOWLEDGE_CATEGORIES) {
            String matchedTerm = firstMatch(text, category.terms());
            if (!matchedTerm.isBlank()) {
                return Decision.retrieve(Reason.KNOWLEDGE_INTENT_MATCH, category.name(), matchedTerm);
            }
        }
        String businessTerm = firstMatch(text, BUSINESS_TOOL_TERMS);
        if (!businessTerm.isBlank()) {
            if (isWecomKfSession(sessionId)) {
                return Decision.retrieve(Reason.WECOM_KF_KNOWLEDGE_FIRST, "wecom_customer_service", businessTerm);
            }
            return Decision.skip(Reason.BUSINESS_TOOL_INTENT, "business_tool", businessTerm);
        }
        if (isWecomKfSession(sessionId)) {
            return Decision.retrieve(Reason.WECOM_KF_KNOWLEDGE_FIRST, "wecom_customer_service", "");
        }
        return Decision.skip(Reason.NO_KNOWLEDGE_INTENT_MATCH);
    }

    private static String firstMatch(String text, List<String> terms) {
        for (String term : terms) {
            if (text.contains(term.toLowerCase(Locale.ROOT))) {
                return term;
            }
        }
        return "";
    }

    private static boolean isWecomKfSession(String sessionId) {
        return sessionId != null && sessionId.startsWith("wecom-kf:");
    }

    enum Reason {
        EXPLICIT_KNOWLEDGE_BASE_REQUEST,
        KNOWLEDGE_INTENT_MATCH,
        WECOM_KF_KNOWLEDGE_FIRST,
        NO_EFFECTIVE_KNOWLEDGE_BASE,
        BLANK_INPUT,
        CASUAL_CONVERSATION,
        BUSINESS_TOOL_INTENT,
        NO_KNOWLEDGE_INTENT_MATCH
    }

    record Decision(boolean shouldRetrieve,
                    Reason reason,
                    String matchedCategory,
                    String matchedTerm,
                    String policyVersion) {
        static Decision retrieve(Reason reason, String matchedCategory, String matchedTerm) {
            return new Decision(true, reason, safe(matchedCategory), safe(matchedTerm), POLICY_VERSION);
        }

        static Decision skip(Reason reason) {
            return skip(reason, "", "");
        }

        static Decision skip(Reason reason, String matchedCategory, String matchedTerm) {
            return new Decision(false, reason, safe(matchedCategory), safe(matchedTerm), POLICY_VERSION);
        }

        private static String safe(String value) {
            return value == null ? "" : value;
        }
    }

    private record IntentCategory(String name, List<String> terms) {
    }
}
