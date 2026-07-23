package com.codehouse.ciciassistant.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionRepository;
import com.codehouse.ciciassistant.agent.config.AgentRuntimeModeRouterProperties;
import com.codehouse.ciciassistant.agent.service.AgentAccessControlService;
import com.codehouse.ciciassistant.agent.service.AgentRuntimeConcurrencyService;
import com.codehouse.ciciassistant.agent.service.AgentRuntimeModeRouter;
import com.codehouse.ciciassistant.agent.service.AgentPlanExecCanaryService;
import com.codehouse.ciciassistant.agent.service.AgentWorkflowExecutionLogService;
import com.codehouse.ciciassistant.agent.service.AgentWorkflowRuntimeService;
import com.codehouse.ciciassistant.ai.domain.ChatMessageEntity;
import com.codehouse.ciciassistant.ai.domain.ChatMessageRepository;
import com.codehouse.ciciassistant.ai.domain.ChatSessionRepository;
import com.codehouse.ciciassistant.ai.domain.ChatSessionStateRepository;
import com.codehouse.ciciassistant.ai.service.AliyunBailianClient.ToolCallInfo;
import com.codehouse.ciciassistant.billing.service.BillingUsageMeteringService;
import com.codehouse.ciciassistant.crmanalysis.service.CrmProductSalesAnalysisToolService;
import com.codehouse.ciciassistant.crmanalysis.service.CrmProductSalesAnswerFormatter;
import com.codehouse.ciciassistant.feishu.domain.FeishuBotBindingRepository;
import com.codehouse.ciciassistant.memory.service.UserMemoryService;
import com.codehouse.ciciassistant.memory.service.TrustedMemoryRuntimeContextService;
import com.codehouse.ciciassistant.model.service.ModelProviderService;
import com.codehouse.ciciassistant.ops.service.AuditService;
import com.codehouse.ciciassistant.security.service.SafetyGatewayService;
import com.codehouse.ciciassistant.skill.service.BuiltinSkillDocumentService;
import com.codehouse.ciciassistant.skill.service.BuiltinSkillRuntimeConfigService;
import com.codehouse.ciciassistant.skill.service.SkillPromptAssembler;
import com.codehouse.ciciassistant.skill.service.SkillResolverService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
    void shouldRequireCadenceBeforePlanningScheduleCreation() {
        assertThat(ChatOrchestratorService.scheduleCadenceClarification("我要创建一个定时任务：寻找美国 K12 教育机构"))
                .hasValueSatisfying(value -> assertThat(value).contains("请补充执行周期"));
        assertThat(ChatOrchestratorService.scheduleCadenceClarification("创建定时任务：每天 09:00 搜索美国 K12 教育机构"))
                .isEmpty();
        assertThat(ChatOrchestratorService.scheduleCadenceClarification("搜索美国 K12 教育机构"))
                .isEmpty();
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
    void shouldRecognizeEmailBodyContinuationConfirmation() {
        assertThat(ChatOrchestratorService.isEmailBodyContinuationConfirmation("是的")).isTrue();
        assertThat(ChatOrchestratorService.isEmailBodyContinuationConfirmation("好的，展开正文")).isTrue();
        assertThat(ChatOrchestratorService.isEmailBodyContinuationConfirmation("看下这封邮件内容")).isTrue();
        assertThat(ChatOrchestratorService.isEmailBodyContinuationConfirmation("不用了")).isFalse();
    }

    @Test
    void shouldReadPendingEmailStateFromSessionStateJson() {
        assertThat(ChatOrchestratorService.pendingEmailFromStateJson("""
                {"pending_email_message_id":"202606261421@example.com","pending_email_action":"read_body",
                 "pending_email_subject":"Cloud CC 产品分享 - 标签管理","pending_email_from":"beibei.sun@mercedes-benz.com"}
                """))
                .contains(new ChatOrchestratorService.PendingEmailState(
                        "202606261421@example.com",
                        "Cloud CC 产品分享 - 标签管理",
                        "beibei.sun@mercedes-benz.com"));

        assertThat(ChatOrchestratorService.pendingEmailFromStateJson("""
                {"pending_email_message_id":"202606261421@example.com","pending_email_action":"send_reply"}
                """))
                .isEmpty();

        assertThat(ChatOrchestratorService.pendingEmailFromStateJson("not-json")).isEmpty();
    }

    @Test
    void shouldBuildRefreshSearchForExpiredPop3EmailId() {
        ChatOrchestratorService.PendingEmailState pending = new ChatOrchestratorService.PendingEmailState(
                "202606261421@example.com",
                "Cloud CC 产品分享 - 标签管理",
                "beibei.sun@mercedes-benz.com");

        assertThat(ChatOrchestratorService.isEmailMessageIdNotFoundResult("""
                ❌ 在最近 50 封邮件中没有找到 messageId=202606261421@example.com。
                POP3 下 messageId 仅在服务器可见范围内有效，建议先用 email_list_inbox 获取最新 id。
                """)).isTrue();
        assertThat(ChatOrchestratorService.buildEmailRefreshSearchArguments(pending))
                .isEqualTo("{\"keyword\":\"Cloud CC 产品分享 - 标签管理\",\"limit\":5,\"scanLimit\":50,\"from\":\"beibei.sun@mercedes-benz.com\"}");
    }

    @Test
    void shouldTreatPromisedEmailBodyReadAsDeferredToolResult() {
        assertThat(ChatOrchestratorService.finalAnswerDefersToolResult("已找到邮件，让我读取正文内容。")).isTrue();
        assertThat(ChatOrchestratorService.finalAnswerDefersToolResult("已找到邮件，正文如下：测试内容。")).isFalse();
    }

    @Test
    void shouldNotTreatOptionalCrmDrilldownAfterConcreteConclusionAsDeferred() {
        String answer = """
                ### 直接结论

                近 30 天销量冠军是智能巡检终端 X1（130 台），订单销售额冠军是边缘采集网关 G5（¥180000）。

                ### 口径与覆盖

                扫描 48 张订单、144 条明细，计入 40 张订单、120 条明细。

                如需我可以继续查看重点客户、开放商机和临期合同明细。
                """;

        assertThat(ChatOrchestratorService.finalAnswerDefersToolResult(answer)).isFalse();
        assertThat(ChatOrchestratorService.appendToolResultFallbackIfDeferred(
                answer,
                List.of(Map.of("role", "tool", "content", "{\"status\":\"SUCCESS\"}"))))
                .isEqualTo(answer);
    }

    @Test
    void shouldStillTreatBareOptionalOfferWithoutAConcreteConclusionAsDeferred() {
        assertThat(ChatOrchestratorService.finalAnswerDefersToolResult(
                "如需我可以继续查看重点客户、开放商机和临期合同明细。"))
                .isTrue();
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

        assertThat(ChatOrchestratorService.shouldSkipToolPlanningStop(
                "查一下最近的潜在客户。",
                List.of(new ToolCallInfo("call_1", "get_object_list", "{\"object_type\":\"standard\"}")),
                List.of(Map.of("role", "tool", "content", """
                        标准对象列表（共 152 条）：
                        [{"id":"lead","label":"潜在客户","objapi":"cloudcclead","objprefix":"004"}]
                        """))))
                .isFalse();
    }

    @Test
    void shouldAllowMetadataLookupSkipOnlyWhenUserAskedForMetadata() {
        assertThat(ChatOrchestratorService.shouldSkipToolPlanningStop(
                "列出线索对象字段",
                List.of(new ToolCallInfo("call_1", "get_object_fields", "{\"objprefix\":\"00Q\"}")),
                List.of(Map.of("role", "tool", "content", "对象字段列表（标准字段 10 条，自定义字段 41 条）：{\"result\":true}"))))
                .isTrue();

        assertThat(ChatOrchestratorService.shouldSkipToolPlanningStop(
                "列出标准对象列表",
                List.of(new ToolCallInfo("call_1", "get_object_list", "{\"object_type\":\"standard\"}")),
                List.of(Map.of("role", "tool", "content", """
                        标准对象列表（共 152 条）：
                        [{"id":"lead","label":"潜在客户","objapi":"cloudcclead","objprefix":"004"}]
                        """))))
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
    void shouldRetrieveKnowledgeForDeploymentQuestionWithDefaultKb() {
        assertThat(ChatOrchestratorService.shouldUseKnowledgeRetrieval(
                "CloudCC私有云部署注意事项有哪些",
                List.of("12"),
                List.of()))
                .isTrue();
    }

    @Test
    void shouldRetrieveKnowledgeForProductFeatureAndCompanyQuestionsWithDefaultKb() {
        assertThat(ChatOrchestratorService.shouldUseKnowledgeRetrieval(
                "CloudCC 产品都有什么功能",
                List.of("12"),
                List.of()))
                .isTrue();

        assertThat(ChatOrchestratorService.shouldUseKnowledgeRetrieval(
                "介绍一下CloudCC这家公司",
                List.of("12"),
                List.of()))
                .isTrue();
    }

    @Test
    void shouldExposeKnowledgeRetrievalDecisionMetadataForTraces() {
        Map<String, Object> metadata = ChatOrchestratorService.knowledgeRetrievalDecisionMetadata(
                KnowledgeRetrievalRouter.decide(
                        "CloudCC 产品都有什么功能",
                        List.of("12"),
                        List.of(),
                        "web:after-sales"));

        assertThat(metadata)
                .containsEntry("ragTriggerReason", "KNOWLEDGE_INTENT_MATCH")
                .containsEntry("ragMatchedCategory", "product_knowledge")
                .containsEntry("ragMatchedTerm", "产品")
                .containsEntry("ragPolicyVersion", "rag-router-v1");
    }

    @Test
    void shouldGuardAgainstPseudoKnowledgeSearchXmlInToolBoundaryPrompt() {
        String prompt = ChatOrchestratorService.buildToolUseBoundaryPromptBlock("web:after-sales");

        assertThat(prompt)
                .contains("search_knowledge")
                .contains("XML")
                .contains("不要输出");
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
                .contains("详细字段已隐藏")
                .doesNotContain("Top 10 Semiconductor Companies", "https://example.com/asia", "Taiwan Semiconductor")
                .doesNotContain("模型本轮未能生成最终自然语言总结")
                .doesNotContain("\"success\"")
                .doesNotContain("\"results\"");
    }

    @Test
    void shouldNeverExposeUnknownJsonFromGenericToolFallback() {
        String unknownJson = """
                {"status":"mystery","productId":"p-secret","ownerId":"005-secret",
                 "arguments":{"accessToken":"token-secret"},"rows":[{"internal":"payload-secret"}]}
                """;

        String fallback = ChatOrchestratorService.buildToolResultFallbackMessage(List.of(Map.of(
                "role", "tool",
                "content", unknownJson
        )));

        assertThat(fallback)
                .contains("未展示原始结果")
                .doesNotContain("{", "productId", "p-secret", "ownerId", "005-secret",
                        "accessToken", "token-secret", "payload-secret", "tool_result");
    }

    @Test
    void shouldNeverExposeUnknownJsonFromToolLimitFallback() {
        List<Map<String, Object>> messages = List.of(
                Map.of(
                        "role", "assistant",
                        "content", "",
                        "tool_calls", List.of(Map.of(
                                "id", "unknown-call",
                                "type", "function",
                                "function", Map.of("name", "unknown_lookup", "arguments", "{}")))),
                Map.of(
                        "role", "tool",
                        "tool_call_id", "unknown-call",
                        "content", "{\"mystery\":\"payload-secret\",\"ownerId\":\"005-secret\"}"));

        String fallback = ChatOrchestratorService.buildToolLimitReachedFallbackMessage(messages, 2);

        assertThat(fallback)
                .contains("原始字段已隐藏")
                .doesNotContain("{\"mystery\"", "payload-secret", "ownerId", "005-secret",
                        "unknown_lookup", "unknown-call", "查询参数");
    }

    @Test
    void shouldNeverUseInternalIdAsBusinessDataFallbackTitle() {
        String toolJson = """
                {"success":true,"data":[
                  {"id":"a49-secret-object-id","ownerId":"005-secret","internal":"payload-secret"}
                ]}
                """;

        String fallback = ChatOrchestratorService.buildToolResultFallbackMessage(List.of(Map.of(
                "role", "tool",
                "content", toolJson
        )));

        assertThat(fallback)
                .contains("1 条业务记录", "详细字段已隐藏")
                .doesNotContain("a49-secret-object-id", "ownerId", "005-secret", "payload-secret");
    }

    @Test
    void shouldProjectSensitiveAnswerMessageAndErrorFieldsBeforeGenericFallback() {
        List<String> sensitivePayloads = List.of(
                "{\"success\":true,\"answer\":\"productId=p-secret ownerId=005-secret\"}",
                "{\"success\":true,\"message\":\"toolName=private_lookup arguments={\\\"recordId\\\":\\\"a49-secret\\\"}\"}",
                "{\"success\":false,\"error\":\"accessToken=token-secret credentials=private\"}",
                "{\"success\":true,\"summary\":\"内部记录 00520264AE58B11bw6gE\"}");

        for (String payload : sensitivePayloads) {
            String fallback = ChatOrchestratorService.buildToolResultFallbackMessage(List.of(Map.of(
                    "role", "tool",
                    "content", payload
            )));

            assertThat(fallback)
                    .containsAnyOf("已隐藏", "请检查参数后重试")
                    .doesNotContain("productId", "p-secret", "ownerId", "005-secret",
                            "toolName", "private_lookup", "arguments", "recordId", "a49-secret",
                            "accessToken", "token-secret", "credentials", "00520264AE58B11bw6gE", "{\\\"");
        }
    }

    @Test
    void shouldProjectSensitiveTrustedFieldsInsideToolLimitSummary() {
        List<Map<String, Object>> messages = List.of(
                Map.of(
                        "role", "assistant",
                        "content", "",
                        "tool_calls", List.of(Map.of(
                                "id", "call-sensitive",
                                "type", "function",
                                "function", Map.of("name", "private_lookup", "arguments", "{\"ownerId\":\"005-secret\"}")))),
                Map.of(
                        "role", "tool",
                        "tool_call_id", "call-sensitive",
                        "content", "{\"success\":true,\"answer\":\"recordId=a49-secret payload={\\\"token\\\":\\\"secret\\\"}\"}"));

        String fallback = ChatOrchestratorService.buildToolLimitReachedFallbackMessage(messages, 2);

        assertThat(fallback)
                .contains("已隐藏")
                .doesNotContain("private_lookup", "ownerId", "005-secret", "recordId", "a49-secret",
                        "payload", "token", "secret", "arguments", "call-sensitive", "{\\\"");
    }

    @Test
    void shouldFailClosedForUntrustedGenericDisplayFieldsThatDoNotMatchKnownIdPrefixes() {
        List<String> sensitivePayloads = List.of(
                "{\"success\":true,\"answer\":\"crm_product_sales_rank\"}",
                "{\"success\":true,\"answer\":\"结果如下 {\\\"foo\\\":\\\"bar\\\"}\"}",
                "{\"success\":true,\"results\":[{\"title\":\"private_lookup\","
                        + "\"url\":\"https://example.com/?access_token=secret\","
                        + "\"snippet\":\"customerId=CUSTOMER-SECRET\"}]}",
                "{\"success\":true,\"data\":[{\"name\":\"PRODUCT-UUID-12345678\","
                        + "\"bkhrccname\":\"private_lookup\",\"khy\":\"2026-07 {\\\"foo\\\":1}\"}]}"
        );

        for (String payload : sensitivePayloads) {
            String fallback = ChatOrchestratorService.buildToolResultFallbackMessage(List.of(Map.of(
                    "role", "tool",
                    "content", payload
            )));

            assertThat(fallback)
                    .contains("已隐藏")
                    .doesNotContain("crm_product_sales_rank", "private_lookup", "access_token", "secret",
                            "customerId", "CUSTOMER-SECRET", "PRODUCT-UUID-12345678", "foo", "bar", "{");
        }
    }

    @Test
    void shouldFailClosedForUnstructuredToolLimitContent() {
        List<Map<String, Object>> messages = List.of(
                Map.of(
                        "role", "assistant",
                        "content", "",
                        "tool_calls", List.of(Map.of(
                                "id", "call-untrusted",
                                "type", "function",
                                "function", Map.of("name", "private_lookup", "arguments", "{}")))),
                Map.of(
                        "role", "tool",
                        "tool_call_id", "call-untrusted",
                        "content", "customerId=CUSTOMER-SECRET private_lookup"));

        String fallback = ChatOrchestratorService.buildToolLimitReachedFallbackMessage(messages, 2);

        assertThat(fallback)
                .contains("已隐藏")
                .doesNotContain("customerId", "CUSTOMER-SECRET", "private_lookup", "call-untrusted");
    }

    @Test
    void shouldUseCrmFormatterForCrmToolFallbackInsteadOfRawPayload() {
        String crmJson = """
                {"status":"EMPTY","metric":"SALES_QUANTITY","startDate":"2026-06-15",
                 "endDate":"2026-07-14","dataAsOf":"2026-07-14T12:00:00+08:00",
                 "sourceObjects":["product","cloudccorder","cloudccorderitem"],"rows":[],
                 "coverage":{"scannedOrders":0,"includedOrders":0,"excludedOrders":0,
                 "scannedItems":0,"includedItems":0,"excludedItems":0},"warnings":[]}
                """;
        List<Map<String, Object>> messages = List.of(
                Map.of(
                        "role", "assistant",
                        "content", "",
                        "tool_calls", List.of(Map.of(
                                "id", "crm-call",
                                "type", "function",
                                "function", Map.of(
                                        "name", "crm_product_sales_rank",
                                        "arguments", "{\"range\":\"LAST_30_DAYS\"}")))),
                Map.of("role", "tool", "tool_call_id", "crm-call", "content", crmJson));

        String fallback = ChatOrchestratorService.buildToolResultFallbackMessage(messages);

        assertThat(fallback)
                .contains("2026-06-15 至 2026-07-14")
                .contains("没有可计入的有效销售事实")
                .doesNotContain("{\"status\"", "crm_product_sales_rank", "tool_result");
    }

    @Test
    void shouldAppendSafeResultSummaryWhenFinalAnswerOnlyPromisesFollowup() {
        String toolJson = """
                {"success":true,"results":[
                  {"title":"白糖现货价格日报","url":"https://example.com/sugar","snippet":"广西、云南主产区报价小幅上行。"}
                ]}
                """;

        String guarded = ChatOrchestratorService.appendToolResultFallbackIfDeferred(
                "数据比较丰富，接下来我再抽取 CRM 中的客户反馈数据，补充客户感知维度。",
                List.of(Map.of("role", "tool", "content", toolJson)));

        assertThat(guarded)
                .contains("已返回结果摘要")
                .contains("1 条结果", "详细字段已隐藏")
                .doesNotContain("白糖现货价格日报", "广西、云南主产区报价小幅上行", "https://example.com/sugar")
                .doesNotContain("本轮不会在完成状态后自动追加回复", "模型本轮未能生成最终自然语言总结");
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
                .contains("1. 查询完成，但没有返回匹配记录")
                .contains("2. 读取到对象字段结构：标准字段 10 条、自定义字段 41 条")
                .contains("下一步可以确认对象名称、人员姓名、月份/季度字段或筛选条件")
                .doesNotContain("get_object_data", "get_object_fields", "monthkpi", "a49",
                        "objapi", "objprefix", "expressions", "查询参数")
                .doesNotContain("系统保护上限")
                .doesNotContain("暂时无法继续处理");
    }

    @Test
    void shouldUseSameDeterministicCrmBodyForBlockingStreamingAndPersistenceWithoutFinalLlm() {
        String rawCrmResult = """
                {"status":"SUCCESS","metric":"SALES_QUANTITY","startDate":"2026-06-15",
                 "endDate":"2026-07-14","dataAsOf":"2026-07-14T12:00:00+08:00",
                 "sourceObjects":["product","cloudccorder","cloudccorderitem"],
                 "rows":[{"rank":1,"productId":"internal-product-id","productName":"智能巡检终端 X1",
                 "productCode":"DEMO-X1","unit":"台","salesQuantity":130,"salesAmount":130000,
                 "orderCount":12,"customerCount":8,"previousValue":100,"changeRate":0.3,
                 "quantityContributionRate":1,"amountContributionRate":1,"realizedAveragePrice":1000,
                 "top1CustomerConcentration":0.7,"top3CustomerConcentration":0.9,
                 "quantityRank":1,"amountRank":1,"pipeline":null,"contracts":null}],
                 "coverage":{"scannedOrders":48,"includedOrders":40,"excludedOrders":8,
                 "scannedItems":144,"includedItems":120,"excludedItems":24},"warnings":[],
                 "summary":{"totalSalesQuantity":130,"totalSalesAmount":130000,"orderCount":12,
                 "customerCount":8,"currency":"CNY","amountComparable":true,
                 "quantityLeader":{"productName":"智能巡检终端 X1","productCode":"DEMO-X1","unit":"台","value":130},
                 "amountLeader":{"productName":"智能巡检终端 X1","productCode":"DEMO-X1","unit":"台","value":130000}},
                 "insights":[]}
                """;
        CrmRouteFixture fixture = new CrmRouteFixture(rawCrmResult);
        String question = "嗯？看一下销量最好的产品有哪些？";
        String expected = fixture.formatter.formatJson(rawCrmResult);

        Map<String, Object> blocking = fixture.service.chat(
                "demo-org", "sales-a", "blocking-session", question,
                List.of(), "agent-cici", "crm-business-analysis");
        CapturingEmitter emitter = new CapturingEmitter();
        fixture.service.chatStreamBlocking(
                "demo-org", "sales-a", "stream-session", question,
                List.of(), "agent-cici", "crm-business-analysis", emitter);

        List<String> deltaChunks = emitter.deltaChunks();

        assertThat(deltaChunks).hasSizeGreaterThan(1);
        assertThat(deltaChunks).allSatisfy(chunk -> {
            assertThat(chunk).isNotBlank();
            assertThat(chunk.length()).isLessThanOrEqualTo(18);
        });
        assertThat(deltaChunks.getFirst()).isNotEqualTo(expected);
        assertThat(String.join("", deltaChunks)).isEqualTo(expected);
        assertThat(emitter.eventNames().stream().filter("done"::equals).count()).isEqualTo(1L);
        assertThat(emitter.lastIndexOf("delta")).isLessThan(emitter.firstIndexOf("done"));
        assertThat(blocking.get("answer")).isEqualTo(expected);
        assertThat(emitter.deltaText()).isEqualTo(expected);
        assertThat(emitter.eventNames()).doesNotContain("tool_call", "tool_result");
        assertThat(emitter.allDataText())
                .doesNotContain("{\"status\"", "internal-product-id", "crm_product_sales_rank", "tool_result");
        assertThat(expected)
                .contains("销量冠军：智能巡检终端 X1")
                .contains("产品 Top 5")
                .doesNotContain("internal-product-id", "{\"status\"");
        verifyNoInteractions(fixture.aliyunBailianClient);

        ArgumentCaptor<ChatMessageEntity> persisted = ArgumentCaptor.forClass(ChatMessageEntity.class);
        verify(fixture.chatMessageRepository, times(4)).save(persisted.capture());
        assertThat(persisted.getAllValues().stream()
                .filter(message -> "assistant".equals(message.getRoleCode()))
                .map(ChatMessageEntity::getContent)
                .toList())
                .containsExactly(expected, expected);
    }

    private static final class CrmRouteFixture {
        private final ChatSessionRepository chatSessionRepository = mock(ChatSessionRepository.class);
        private final ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
        private final ModelRouterService modelRouterService = mock(ModelRouterService.class);
        private final ModelProviderService modelProviderService = mock(ModelProviderService.class);
        private final ToolOrchestratorService toolOrchestratorService = mock(ToolOrchestratorService.class);
        private final RagService ragService = mock(RagService.class);
        private final ChatThinkingConfigService chatThinkingConfigService = mock(ChatThinkingConfigService.class);
        private final AuditService auditService = mock(AuditService.class);
        private final AliyunBailianClient aliyunBailianClient = mock(AliyunBailianClient.class);
        private final SessionRealtimeEventService sessionRealtimeEventService = mock(SessionRealtimeEventService.class);
        private final FeishuBotBindingRepository feishuBotBindingRepository = mock(FeishuBotBindingRepository.class);
        private final SkillResolverService skillResolverService = mock(SkillResolverService.class);
        private final SkillPromptAssembler skillPromptAssembler = mock(SkillPromptAssembler.class);
        private final BuiltinSkillDocumentService builtinSkillDocumentService = mock(BuiltinSkillDocumentService.class);
        private final BuiltinSkillRuntimeConfigService builtinSkillRuntimeConfigService =
                mock(BuiltinSkillRuntimeConfigService.class);
        private final UserMemoryService userMemoryService = mock(UserMemoryService.class);
        private final ChatSessionStateService chatSessionStateService = mock(ChatSessionStateService.class);
        private final ChatSessionStateRepository chatSessionStateRepository = mock(ChatSessionStateRepository.class);
        private final RuntimeContextPromptService runtimeContextPromptService = mock(RuntimeContextPromptService.class);
        private final TrustedMemoryRuntimeContextService trustedMemoryRuntimeContextService = mock(TrustedMemoryRuntimeContextService.class);
        private final AgentWorkflowRuntimeService agentWorkflowRuntimeService = mock(AgentWorkflowRuntimeService.class);
        private final AgentWorkflowVersionRepository agentWorkflowVersionRepository =
                mock(AgentWorkflowVersionRepository.class);
        private final AgentWorkflowExecutionLogService agentWorkflowExecutionLogService =
                mock(AgentWorkflowExecutionLogService.class);
        private final AgentRunTraceService agentRunTraceService = mock(AgentRunTraceService.class);
        private final AgentAccessControlService agentAccessControlService = mock(AgentAccessControlService.class);
        private final AgentRuntimeModeRouter agentRuntimeModeRouter =
                new AgentRuntimeModeRouter(new AgentRuntimeModeRouterProperties());
        private final AgentPlanExecCanaryService agentPlanExecCanaryService = mock(AgentPlanExecCanaryService.class);
        private final BillingUsageMeteringService billingUsageMeteringService = mock(BillingUsageMeteringService.class);
        private final SafetyGatewayService safetyGatewayService = mock(SafetyGatewayService.class);
        private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        private final CrmProductSalesAnswerFormatter formatter =
                new CrmProductSalesAnswerFormatter(new ObjectMapper().findAndRegisterModules());
        private final Executor directExecutor = Runnable::run;
        private final ChatOrchestratorService service;

        private CrmRouteFixture(String rawCrmResult) {
            SkillResolverService.ResolvedSkill crmSkill = new SkillResolverService.ResolvedSkill(
                    "crm-business-analysis", "CRM 经营分析", "", List.of(CrmProductSalesAnalysisToolService.TOOL_NAME),
                    List.of(), "", "", "LOW", "always-on");
            SkillResolverService.ResolvedSkillContext skillContext = new SkillResolverService.ResolvedSkillContext(
                    "agent-cici",
                    List.of(crmSkill),
                    List.of("crm-business-analysis"),
                    List.of(CrmProductSalesAnalysisToolService.TOOL_NAME),
                    List.of(),
                    List.of(CrmProductSalesAnalysisToolService.TOOL_NAME),
                    List.of(CrmProductSalesAnalysisToolService.TOOL_NAME),
                    List.of(),
                    List.of(),
                    "",
                    "",
                    "mock-model",
                    "crm-business-analysis",
                    4,
                    null,
                    List.of(),
                    List.of(),
                    SkillResolverService.ResolvedPolicyBundle.EMPTY);
            BuiltinSkillDocumentService.ResolvedBuiltinSkillDocs builtinDocs =
                    new BuiltinSkillDocumentService.ResolvedBuiltinSkillDocs(List.of(), List.of());
            RuntimeContextPromptService.RuntimeContext runtimeContext = new RuntimeContextPromptService.RuntimeContext(
                    "Asia/Shanghai", "2026-07-14", "20:00:00", "2026年7月14日", "星期二");
            AgentWorkflowRuntimeService.RuntimeExecutionResult executionResult =
                    new AgentWorkflowRuntimeService.RuntimeExecutionResult(
                            AgentWorkflowExecutionLogService.STATUS_SUCCESS,
                            "completed",
                            null,
                            List.of(),
                            new AgentWorkflowRuntimeService.RuntimePolicyBundleView("", 0, 0, 0),
                            List.of(),
                            new java.util.LinkedHashMap<>());

            when(skillResolverService.resolve(anyString(), anyString(), anyString(), any()))
                    .thenReturn(skillContext);
            when(skillResolverService.resolveKnowledgeBaseIds(any(), anyList())).thenReturn(List.of());
            when(builtinSkillDocumentService.resolveDocs(
                    any(SkillResolverService.ResolvedSkillContext.class), anyString())).thenReturn(builtinDocs);
            when(modelRouterService.route(eq("demo-org"), eq("chat"), eq("mock-model")))
                    .thenReturn(Map.of("provider", "mock", "modelName", "mock-model"));
            when(chatThinkingConfigService.isEnabled("demo-org")).thenReturn(false);
            when(toolOrchestratorService.getToolDefinitions(anyString(), anyList(), anyList()))
                    .thenReturn(List.of());
            when(toolOrchestratorService.executeTool(
                    eq("demo-org"), eq("sales-a"), eq(CrmProductSalesAnalysisToolService.TOOL_NAME),
                    anyString(), anyList(), anyList())).thenReturn(rawCrmResult);
            when(runtimeContextPromptService.current()).thenReturn(runtimeContext);
            when(runtimeContextPromptService.buildPromptBlock(runtimeContext)).thenReturn("[runtime]");
            when(runtimeContextPromptService.toPayload(runtimeContext)).thenReturn(Map.of());
            when(builtinSkillRuntimeConfigService.resolve(any(), any(), anyString(), anyString()))
                    .thenReturn(BuiltinSkillRuntimeConfigService.ResolvedBuiltinSkillRuntimeConfig.empty());
            when(skillPromptAssembler.assemble(anyString(), any(), any(), any())).thenReturn("system");
            when(userMemoryService.listForInjection(anyString(), anyString(), anyString())).thenReturn(List.of());
            when(chatSessionStateService.get(anyString(), anyString())).thenReturn(Optional.empty());
            when(chatMessageRepository.findByOrgIdAndSessionIdOrderByCreatedAtDesc(anyString(), anyString(), any()))
                    .thenReturn(List.of());
            when(chatSessionRepository.findById(anyString())).thenReturn(Optional.empty());
            when(agentWorkflowRuntimeService.evaluateForChat(anyString(), anyString(), anyString(), anyList()))
                    .thenReturn(executionResult);
            when(agentPlanExecCanaryService.start(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(AgentPlanExecCanaryService.CanaryExecution.notSelected());
            when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
            when(safetyGatewayService.checkInput(anyString(), anyString(), anyString(), anyString()))
                    .thenAnswer(invocation -> new SafetyGatewayService.SafetyDecision(
                            "ALLOW", invocation.getArgument(3), List.of(), false, "test"));
            when(safetyGatewayService.checkOutput(anyString(), anyString(), anyString(), anyString()))
                    .thenAnswer(invocation -> new SafetyGatewayService.SafetyDecision(
                            "ALLOW", invocation.getArgument(3), List.of(), false, "test"));

            service = new ChatOrchestratorService(
                    chatSessionRepository,
                    chatMessageRepository,
                    modelRouterService,
                    modelProviderService,
                    toolOrchestratorService,
                    ragService,
                    chatThinkingConfigService,
                    auditService,
                    aliyunBailianClient,
                    sessionRealtimeEventService,
                    feishuBotBindingRepository,
                    skillResolverService,
                    skillPromptAssembler,
                    builtinSkillDocumentService,
                    builtinSkillRuntimeConfigService,
                    userMemoryService,
                    chatSessionStateService,
                    chatSessionStateRepository,
                    runtimeContextPromptService,
                    trustedMemoryRuntimeContextService,
                    agentWorkflowRuntimeService,
                    agentWorkflowVersionRepository,
                    agentWorkflowExecutionLogService,
                    agentRunTraceService,
                    agentAccessControlService,
                    billingUsageMeteringService,
                    formatter,
                    safetyGatewayService,
                    new AgentRuntimeConcurrencyService(),
                    agentRuntimeModeRouter,
                    agentPlanExecCanaryService,
                    directExecutor,
                    transactionManager);
        }
    }

    private static final class CapturingEmitter extends SseEmitter {
        private final List<String> eventNames = new ArrayList<>();
        private final List<Object> eventData = new ArrayList<>();

        private CapturingEmitter() {
            super(60_000L);
        }

        @Override
        public synchronized void send(SseEventBuilder builder) throws IOException {
            Set<ResponseBodyEmitter.DataWithMediaType> items = builder.build();
            String eventName = "";
            Object data = null;
            for (ResponseBodyEmitter.DataWithMediaType item : items) {
                Object value = item.getData();
                if (value instanceof String text && text.startsWith("event:")) {
                    String framed = text.substring("event:".length());
                    int lineEnd = framed.indexOf('\n');
                    eventName = (lineEnd >= 0 ? framed.substring(0, lineEnd) : framed).trim();
                } else if (!(value instanceof String)) {
                    data = value;
                }
            }
            eventNames.add(eventName);
            eventData.add(data);
        }

        private List<String> eventNames() {
            return List.copyOf(eventNames);
        }

        private List<String> deltaChunks() {
            List<String> chunks = new ArrayList<>();
            for (int index = 0; index < eventNames.size(); index++) {
                if (!"delta".equals(eventNames.get(index))) {
                    continue;
                }
                Object data = eventData.get(index);
                if (data instanceof Map<?, ?> map && map.get("text") != null) {
                    chunks.add(String.valueOf(map.get("text")));
                }
            }
            return List.copyOf(chunks);
        }

        private String deltaText() {
            return String.join("", deltaChunks());
        }

        private int firstIndexOf(String eventName) {
            return eventNames.indexOf(eventName);
        }

        private int lastIndexOf(String eventName) {
            return eventNames.lastIndexOf(eventName);
        }

        private String allDataText() {
            return eventData.toString();
        }
    }
}
