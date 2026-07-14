package com.codehouse.ciciassistant.ai.service;

import com.codehouse.ciciassistant.agent.domain.AgentPermission;
import com.codehouse.ciciassistant.agent.service.AgentAccessControlService;
import com.codehouse.ciciassistant.agent.service.AgentRuntimeConcurrencyService;
import com.codehouse.ciciassistant.agent.service.AgentWorkflowExecutionLogService;
import com.codehouse.ciciassistant.agent.service.AgentWorkflowRuntimeService;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionRepository;
import com.codehouse.ciciassistant.ai.domain.ChatMessageEntity;
import com.codehouse.ciciassistant.ai.domain.ChatMessageRepository;
import com.codehouse.ciciassistant.ai.domain.ChatSessionEntity;
import com.codehouse.ciciassistant.ai.domain.ChatSessionRepository;
import com.codehouse.ciciassistant.ai.domain.ChatSessionStateEntity;
import com.codehouse.ciciassistant.ai.domain.ChatSessionStateRepository;
import com.codehouse.ciciassistant.ai.service.AliyunBailianClient.ChatCompletionResult;
import com.codehouse.ciciassistant.ai.service.AliyunBailianClient.ChatStreamResult;
import com.codehouse.ciciassistant.ai.service.AliyunBailianClient.ToolCallInfo;
import com.codehouse.ciciassistant.ai.service.RuntimeContextPromptService.RuntimeContext;
import com.codehouse.ciciassistant.feishu.domain.FeishuBotBindingEntity;
import com.codehouse.ciciassistant.feishu.domain.FeishuBotBindingRepository;
import com.codehouse.ciciassistant.billing.service.BillingUsageMeteringService;
import com.codehouse.ciciassistant.crmanalysis.service.CrmProductSalesAnalysisToolService;
import com.codehouse.ciciassistant.crmanalysis.service.CrmProductSalesAnswerFormatter;
import com.codehouse.ciciassistant.crmanalysis.service.CrmProductSalesIntentRouter;
import com.codehouse.ciciassistant.memory.domain.UserMemoryEntity;
import com.codehouse.ciciassistant.memory.service.UserMemoryService;
import com.codehouse.ciciassistant.model.service.ModelProviderService;
import com.codehouse.ciciassistant.ops.service.AuditService;
import com.codehouse.ciciassistant.kb.service.KbAccessControlService;
import com.codehouse.ciciassistant.skill.service.SkillPromptAssembler;
import com.codehouse.ciciassistant.skill.service.SkillResolverService;
import com.codehouse.ciciassistant.skill.service.BuiltinSkillDocumentService;
import com.codehouse.ciciassistant.skill.service.BuiltinSkillRuntimeConfigService;
import com.codehouse.ciciassistant.skill.service.SkillResolverService.ResolvedSkillContext;
import com.codehouse.ciciassistant.tenant.TenantContext;
import com.codehouse.ciciassistant.tool.service.ToolNameNormalizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ChatOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(ChatOrchestratorService.class);
    private static final int MAX_TOOL_ROUNDS = 8;
    private static final int MIN_TOOL_ROUNDS = 1;
    private static final int MAX_POLICY_TOOL_ROUNDS = 12;
    private static final ObjectMapper TOOL_RESULT_OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final CrmProductSalesAnswerFormatter CRM_TOOL_RESULT_FALLBACK_FORMATTER =
            new CrmProductSalesAnswerFormatter(TOOL_RESULT_OBJECT_MAPPER);
    private static final Pattern DEFERRED_TOOL_FINAL_PATTERN = Pattern.compile(
            "(后续|接下来|随后|稍后).{0,18}(继续|重新|再|将|会|我)?.{0,18}(查询|检索|调用|获取|读取|查看|打开|处理|尝试|抽取|整理|分析|生成|补充|展示|展现|输出)"
                    + "|(让我|我来|我会|我再|将).{0,12}(继续|重新|再)?.{0,12}(查询|检索|调用|获取|读取|查看|打开|处理|尝试|抽取|整理|分析|生成|补充|展示|展现|输出)"
                    + "|(继续|重新|再).{0,8}(查询|检索|调用|获取|读取|查看|打开|处理|尝试|抽取|整理|分析|生成|补充|展示|展现|输出)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern OPTIONAL_DRILLDOWN_OFFER_PATTERN = Pattern.compile(
            "(如需|若需|如果需要|需要的话).{0,48}(可以|可)?.{0,12}(继续|进一步).{0,20}"
                    + "(查询|检索|获取|读取|查看|打开|抽取|整理|分析|展示|展现|输出).*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern TOOL_DATA_COUNT_PATTERN = Pattern.compile("返回\\s*(\\d+)\\s*条[，,]\\s*总计\\s*(\\d+)\\s*条");
    private static final Pattern TOOL_FIELD_COUNT_PATTERN = Pattern.compile("对象字段列表（标准字段\\s*(\\d+)\\s*条[，,]\\s*自定义字段\\s*(\\d+)\\s*条）");
    private static final Pattern TOOL_OBJECT_COUNT_PATTERN = Pattern.compile("所有对象列表（标准对象:\\s*(\\d+)\\s*条[，,]\\s*自定义对象:\\s*(\\d+)\\s*条[，,]\\s*总计:\\s*(\\d+)\\s*条）");
    private static final Pattern EMAIL_SEARCH_MESSAGE_ID_PATTERN = Pattern.compile("(?m)\\bid=([^\\s\\r\\n]+)");
    private static final String PROTECTED_TOOL_DISPLAY_FALLBACK =
            "工具返回的可读结果包含受保护的内部字段，已隐藏。";

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ModelRouterService modelRouterService;
    private final ModelProviderService modelProviderService;
    private final ToolOrchestratorService toolOrchestratorService;
    private final RagService ragService;
    private final ChatThinkingConfigService chatThinkingConfigService;
    private final AuditService auditService;
    private final AliyunBailianClient aliyunBailianClient;
    private final SessionRealtimeEventService sessionRealtimeEventService;
    private final FeishuBotBindingRepository feishuBotBindingRepository;
    private final SkillResolverService skillResolverService;
    private final SkillPromptAssembler skillPromptAssembler;
    private final BuiltinSkillDocumentService builtinSkillDocumentService;
    private final BuiltinSkillRuntimeConfigService builtinSkillRuntimeConfigService;
    private final UserMemoryService userMemoryService;
    private final ChatSessionStateService chatSessionStateService;
    private final ChatSessionStateRepository chatSessionStateRepository;
    private final RuntimeContextPromptService runtimeContextPromptService;
    private final AgentWorkflowRuntimeService agentWorkflowRuntimeService;
    private final AgentWorkflowVersionRepository agentWorkflowVersionRepository;
    private final AgentWorkflowExecutionLogService agentWorkflowExecutionLogService;
    private final AgentRunTraceService agentRunTraceService;
    private final AgentAccessControlService agentAccessControlService;
    private final AgentRuntimeConcurrencyService agentRuntimeConcurrencyService;
    private final BillingUsageMeteringService billingUsageMeteringService;
    private final CrmProductSalesAnswerFormatter crmProductSalesAnswerFormatter;
    private final Executor agentRuntimeExecutor;
    private final TransactionTemplate tx;

    public ChatOrchestratorService(ChatSessionRepository chatSessionRepository,
                                   ChatMessageRepository chatMessageRepository,
                                   ModelRouterService modelRouterService,
                                   ModelProviderService modelProviderService,
                                   ToolOrchestratorService toolOrchestratorService,
                                   RagService ragService,
                                   ChatThinkingConfigService chatThinkingConfigService,
                                   AuditService auditService,
                                   AliyunBailianClient aliyunBailianClient,
                                   SessionRealtimeEventService sessionRealtimeEventService,
                                   FeishuBotBindingRepository feishuBotBindingRepository,
                                   SkillResolverService skillResolverService,
                                   SkillPromptAssembler skillPromptAssembler,
                                   BuiltinSkillDocumentService builtinSkillDocumentService,
                                   BuiltinSkillRuntimeConfigService builtinSkillRuntimeConfigService,
                                   UserMemoryService userMemoryService,
                                   ChatSessionStateService chatSessionStateService,
                                   ChatSessionStateRepository chatSessionStateRepository,
                                   RuntimeContextPromptService runtimeContextPromptService,
                                   AgentWorkflowRuntimeService agentWorkflowRuntimeService,
                                   AgentWorkflowVersionRepository agentWorkflowVersionRepository,
                                   AgentWorkflowExecutionLogService agentWorkflowExecutionLogService,
                                   AgentRunTraceService agentRunTraceService,
                                   AgentAccessControlService agentAccessControlService,
                                   BillingUsageMeteringService billingUsageMeteringService,
                                   CrmProductSalesAnswerFormatter crmProductSalesAnswerFormatter,
                                   AgentRuntimeConcurrencyService agentRuntimeConcurrencyService,
                                   @Qualifier("agentRuntimeExecutor") Executor agentRuntimeExecutor,
                                   PlatformTransactionManager transactionManager) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.modelRouterService = modelRouterService;
        this.modelProviderService = modelProviderService;
        this.toolOrchestratorService = toolOrchestratorService;
        this.ragService = ragService;
        this.chatThinkingConfigService = chatThinkingConfigService;
        this.auditService = auditService;
        this.aliyunBailianClient = aliyunBailianClient;
        this.sessionRealtimeEventService = sessionRealtimeEventService;
        this.feishuBotBindingRepository = feishuBotBindingRepository;
        this.skillResolverService = skillResolverService;
        this.skillPromptAssembler = skillPromptAssembler;
        this.builtinSkillDocumentService = builtinSkillDocumentService;
        this.builtinSkillRuntimeConfigService = builtinSkillRuntimeConfigService;
        this.userMemoryService = userMemoryService;
        this.chatSessionStateService = chatSessionStateService;
        this.chatSessionStateRepository = chatSessionStateRepository;
        this.runtimeContextPromptService = runtimeContextPromptService;
        this.agentWorkflowRuntimeService = agentWorkflowRuntimeService;
        this.agentWorkflowVersionRepository = agentWorkflowVersionRepository;
        this.agentWorkflowExecutionLogService = agentWorkflowExecutionLogService;
        this.agentRunTraceService = agentRunTraceService;
        this.agentAccessControlService = agentAccessControlService;
        this.billingUsageMeteringService = billingUsageMeteringService;
        this.crmProductSalesAnswerFormatter = crmProductSalesAnswerFormatter;
        this.agentRuntimeConcurrencyService = agentRuntimeConcurrencyService;
        this.agentRuntimeExecutor = agentRuntimeExecutor;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public Map<String, Object> chat(String orgId, String userId, String sessionId,
                                     String question, List<String> kbIds, String requestedAgentId,
                                     String activeSkillCode) {
        return chat(orgId, userId, sessionId, question, kbIds, requestedAgentId, activeSkillCode, Map.of());
    }

    public Map<String, Object> chat(String orgId, String userId, String sessionId,
                                     String question, List<String> kbIds, String requestedAgentId,
                                     String activeSkillCode, Map<String, String> metadataFilters) {
        String runId = newRunId();
        return agentRuntimeConcurrencyService.run(orgId, userId, requestedAgentId, sessionId,
                () -> chatLocked(orgId, userId, sessionId, question, kbIds, requestedAgentId,
                        activeSkillCode, metadataFilters, runId));
    }

    /**
     * Executes one candidate-version model turn for evaluation without persisting chat state and without
     * executing tool calls. Tool schemas remain visible so the evaluator can verify tool selection and
     * arguments, but every requested call is captured as evidence only.
     */
    public EvaluationDryRunResult evaluateNoSideEffects(String orgId,
                                                        String actorId,
                                                        String agentId,
                                                        Integer versionNo,
                                                        String question,
                                                        String conversationHistoryJson,
                                                        String fixtureJson) {
        AgentWorkflowVersionEntity version = agentWorkflowVersionRepository
                .findByOrgIdAndAgentIdAndVersionNo(orgId, agentId, versionNo)
                .orElseThrow(() -> new IllegalArgumentException("Agent workflow version not found"));
        Map<String, Object> manifest = parseObject(version.getWorkflowManifest());
        Map<String, Object> identity = objectMap(manifest.get("identity"));
        ResolvedSkillContext resolved = skillResolverService.resolveForEvaluation(orgId, agentId, version.getId());
        ResolvedSkillContext skillContext = withEvaluationIdentity(
                resolved,
                textOrDefault(identity.get("systemPrompt"), resolved.agentSystemPrompt()),
                textOrDefault(identity.get("model"), resolved.agentModel()));
        BuiltinSkillDocumentService.ResolvedBuiltinSkillDocs builtinDocs =
                builtinSkillDocumentService.resolveDocs(skillContext, question);
        Map<String, String> routedModel = modelRouterService.route(orgId, "chat", skillContext.agentModel());
        String modelName = resolveModelName(
                skillContext.agentModel(), routedModel.get("provider"), routedModel.get("modelName"));
        ModelCallCredentials credentials = resolveModelCallCredentials(orgId, routedModel.get("provider"));

        List<String> knowledgeBaseIds = skillResolverService.resolveKnowledgeBaseIds(skillContext, List.of());
        KnowledgeRetrievalRouter.Decision decision = KnowledgeRetrievalRouter.decide(
                question, knowledgeBaseIds, List.of(), "evaluation-" + version.getId());
        RagService.RetrievalResult ragResult = decision.shouldRetrieve()
                ? ragService.retrieveDetailed(
                        orgId,
                        knowledgeBaseIds,
                        question,
                        Map.of(),
                        KbAccessControlService.AccessPrincipal.system())
                : emptyRagRetrievalResult();

        BuiltinSkillRuntimeConfigService.ResolvedBuiltinSkillRuntimeConfig runtimeConfig =
                builtinSkillRuntimeConfigService.resolve(skillContext, builtinDocs, orgId, actorId);
        String system = skillPromptAssembler.assemble(
                AliyunBailianClient.SYSTEM_PROMPT,
                skillContext,
                builtinDocs,
                runtimeConfig)
                + "\n---\n\nEvaluation policy: this is a side-effect-free candidate-version evaluation. "
                + "You may request a tool call for planning evidence, but no tool will be executed and you must not claim that a write succeeded.";
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", system));
        messages.addAll(parseEvaluationHistory(conversationHistoryJson));
        StringBuilder userContent = new StringBuilder(question == null ? "" : question);
        if (!ragResult.context().isEmpty()) {
            userContent.append("\n\n[参考知识库信息]\n");
            for (int index = 0; index < ragResult.context().size(); index++) {
                userContent.append(index + 1).append(". ").append(ragResult.context().get(index)).append('\n');
            }
        }
        if (fixtureJson != null && !fixtureJson.isBlank()) {
            userContent.append("\n\n[评测夹具，仅作只读上下文]\n").append(fixtureJson.trim());
        }
        messages.add(Map.of("role", "user", "content", userContent.toString()));

        List<Map<String, Object>> toolSchemas = toolOrchestratorService.getToolDefinitions(
                orgId, skillContext.allowedToolNames(), skillContext.skillApiTools());
        ChatCompletionResult completion = chatCompletionWithResolvedCredentials(
                modelName,
                messages,
                toolSchemas.isEmpty() ? null : toolSchemas,
                true,
                credentials);
        String rawOutput = completion.content() == null ? "" : completion.content().trim();
        if (rawOutput.isBlank()
                || rawOutput.startsWith("Aliyun API key is not configured")
                || rawOutput.startsWith("Model call failed:")
                || rawOutput.startsWith("Empty response.")) {
            throw new IllegalStateException("Evaluation model call failed: " + rawOutput);
        }
        List<Map<String, Object>> plannedToolCalls = safeToolCalls(completion).stream()
                .map(call -> Map.<String, Object>of(
                        "id", call.id() == null ? "" : call.id(),
                        "name", call.name() == null ? "" : call.name(),
                        "arguments", parseToolArguments(call.arguments())))
                .toList();
        String output = AssistantContentSanitizer.stripThinkingSections(rawOutput).trim();
        List<String> ragSources = ragResult.sources().stream()
                .map(source -> source.knowledgeBaseName() + "/" + source.documentName())
                .distinct()
                .toList();
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("runMode", "EVALUATION");
        context.put("evaluationVersionNo", versionNo);
        context.put("modelName", modelName);
        context.put("toolCalls", plannedToolCalls);
        context.put("ragSources", ragSources);
        context.put("knowledgeUsed", !ragResult.context().isEmpty());
        context.put("sideEffectPolicy", "BLOCK_WRITES");
        context.put("writeSideEffectsExecuted", false);
        context.put("resolvedSkillVersions", skillContext.resolvedSkillRefs());
        context.put("effectiveKnowledgeBaseIds", knowledgeBaseIds);
        context.put("promptTokens", completion.promptTokens());
        context.put("completionTokens", completion.completionTokens());
        return new EvaluationDryRunResult(
                output,
                plannedToolCalls,
                ragSources,
                List.of("model:evaluation", "tools:planned-only", "side-effects:blocked"),
                context);
    }

    private ResolvedSkillContext withEvaluationIdentity(ResolvedSkillContext current,
                                                        String systemPrompt,
                                                        String model) {
        return new ResolvedSkillContext(
                current.agentId(), current.skills(), current.skillCodes(), current.allowedToolNames(),
                current.agentDirectToolNames(), current.skillDeclaredToolNames(), current.skillScopedToolNames(),
                current.defaultKnowledgeBaseIds(), current.handoffRules(), current.outputContract(),
                systemPrompt, model, current.activeSkillCode(), current.maxToolCalls(), current.publishedVersionId(),
                current.resolvedSkillRefs(), current.skillApiTools(), current.policyBundle());
    }

    private List<Map<String, Object>> parseEvaluationHistory(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            JsonNode root = TOOL_RESULT_OBJECT_MAPPER.readTree(json);
            if (!root.isArray()) return List.of();
            List<Map<String, Object>> rows = new ArrayList<>();
            for (JsonNode item : root) {
                String role = item.path("role").asText("").trim().toLowerCase(Locale.ROOT);
                String content = item.path("content").asText("").trim();
                if (("user".equals(role) || "assistant".equals(role)) && !content.isBlank()) {
                    rows.add(Map.of("role", role, "content", content));
                }
            }
            return rows;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid evaluation conversation history");
        }
    }

    private Map<String, Object> parseObject(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return TOOL_RESULT_OBJECT_MAPPER.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid candidate workflow manifest");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private String textOrDefault(Object value, String fallback) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
    }

    private Map<String, Object> parseToolArguments(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return TOOL_RESULT_OBJECT_MAPPER.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception ex) {
            return Map.of("raw", json);
        }
    }

    private Map<String, Object> chatLocked(String orgId, String userId, String sessionId,
                                           String question, List<String> kbIds, String requestedAgentId,
                                           String activeSkillCode, Map<String, String> metadataFilters,
                                           String runId) {
        Instant runStartedAt = Instant.now();
        List<AgentRunTraceService.StageTraceInput> stageTraces = new ArrayList<>();
        List<AgentRunTraceService.ModelCallTraceInput> modelCallTraces = new ArrayList<>();
        List<AgentRunTraceService.ToolCallTraceInput> toolCallTraces = new ArrayList<>();
        Instant skillStartedAt = Instant.now();
        ResolvedSkillContext skillContext = skillResolverService.resolve(
                orgId, requestedAgentId, sessionId, Optional.ofNullable(activeSkillCode));
        agentAccessControlService.require(orgId, userId, TenantContext.getRoles(), skillContext.agentId(), AgentPermission.RUN);
        BuiltinSkillDocumentService.ResolvedBuiltinSkillDocs builtinDocs =
                builtinSkillDocumentService.resolveDocs(skillContext, question);
        stageTraces.add(stageTrace("SKILL_RESOLVE", "技能候选解析", "SUCCESS", skillStartedAt, Instant.now(),
                "已解析当前智能体绑定技能、工具边界与会话激活技能。",
                withRunId(skillTraceMetadata(skillContext, List.of(), builtinDocs), runId)));
        Instant userPersistStartedAt = Instant.now();
        persistUserTurnCommitted(orgId, userId, sessionId, question, skillContext.agentId());
        stageTraces.add(stageTrace("USER_MESSAGE", "用户输入", "SUCCESS", userPersistStartedAt, Instant.now(),
                clipForTrace(question, 220), Map.of("sessionId", sessionId, "runId", runId)));

        Map<String, String> routedModel = modelRouterService.route(orgId, "chat", skillContext.agentModel());
        String modelName = resolveModelName(skillContext.agentModel(), routedModel.get("provider"), routedModel.get("modelName"));
        ModelCallCredentials modelCredentials = resolveModelCallCredentials(orgId, routedModel.get("provider"));
        boolean showThinking = chatThinkingConfigService.isEnabled(orgId);
        List<String> effectiveKnowledgeBaseIds = skillResolverService.resolveKnowledgeBaseIds(skillContext, kbIds);
        List<String> requestedKnowledgeBaseIds = normalizeKnowledgeBaseIds(kbIds);
        KnowledgeRetrievalRouter.Decision knowledgeDecision = KnowledgeRetrievalRouter.decide(
                question, effectiveKnowledgeBaseIds, requestedKnowledgeBaseIds, sessionId);
        boolean useKnowledgeRetrieval = knowledgeDecision.shouldRetrieve();
        Instant ragStartedAt = Instant.now();
        RagService.RetrievalResult ragResult = useKnowledgeRetrieval
                ? ragService.retrieveDetailed(
                orgId,
                effectiveKnowledgeBaseIds,
                question,
                metadataFilters,
                KbAccessControlService.AccessPrincipal.user(userId, TenantContext.getRoles()))
                : emptyRagRetrievalResult();
        stageTraces.add(stageTrace("RAG", useKnowledgeRetrieval ? "知识库检索" : "知识库检索未触发",
                useKnowledgeRetrieval ? "SUCCESS" : "SKIPPED", ragStartedAt, Instant.now(),
                useKnowledgeRetrieval
                        ? "知识库检索完成，命中 " + ragResult.context().size() + " 个片段。"
                        : "本轮输入未满足知识库检索条件。",
                withRunId(ragDetailMetadata(ragResult, knowledgeDecision), runId)));
        List<String> ragContext = ragResult.context();
        Instant toolSchemaStartedAt = Instant.now();
        List<Map<String, Object>> tools = isWecomKfSession(sessionId)
                ? List.of()
                : toolOrchestratorService.getToolDefinitions(
                orgId, skillContext.allowedToolNames(), skillContext.skillApiTools());
        stageTraces.add(stageTrace("TOOL_SCHEMA", "工具定义加载", "SUCCESS", toolSchemaStartedAt, Instant.now(),
                "已加载本轮可用工具定义 " + tools.size() + " 个。",
                Map.of("toolDefinitionCount", tools.size(), "allowedToolNames", skillContext.allowedToolNames(), "runId", runId)));
        RuntimeContext runtimeContext = runtimeContextPromptService.current();

        chatSessionStateService.mergeUserTurn(orgId, sessionId, skillContext.agentId(), question);
        List<Map<String, Object>> messages = buildInitialMessages(
                sessionId, question, ragContext, showThinking, skillContext, orgId, userId,
                runtimeContext, routedModel.get("provider"), modelName, builtinDocs);
        appendConfirmedPendingEmailBodyToolResult(
                messages, orgId, userId, sessionId, skillContext, null, toolCallTraces, runId, question);
        Optional<String> forcedCrmProductSalesAnswer = appendForcedCrmProductSalesToolResult(
                messages, orgId, userId, sessionId, skillContext, toolCallTraces, runId, question);
        int maxToolRounds = resolveMaxToolRounds(skillContext.maxToolCalls());
        String answer = forcedCrmProductSalesAnswer.orElseGet(() -> runToolLoop(
                modelName, messages, tools, orgId, userId, sessionId,
                showThinking, skillContext, maxToolRounds, modelCredentials, modelCallTraces, toolCallTraces, runId));
        Instant wfStartedAt = Instant.now();
        AgentWorkflowRuntimeService.RuntimeExecutionResult executionResult = agentWorkflowRuntimeService.evaluateForChat(
                orgId, skillContext.agentId(), question, skillContext.allowedToolNames());
        executionResult.contextSnapshot().put("runId", runId);
        Instant wfEndedAt = Instant.now();
        int wfMs = elapsedMs(wfStartedAt, wfEndedAt);
        stageTraces.add(stageTrace("WORKFLOW", "技能运行治理",
                AgentWorkflowExecutionLogService.STATUS_SUCCESS.equals(
                        AgentWorkflowExecutionLogService.normalizeWorkflowStatus(executionResult.executionStatus()))
                        ? "SUCCESS" : "FAILED",
                wfStartedAt, wfEndedAt, clipForTrace(executionResult.executionOutput(), 260),
                workflowTraceMetadata(executionResult, wfMs)));
        try {
            agentWorkflowExecutionLogService.appendFromChat(
                    orgId,
                    skillContext.agentId(),
                    executionResult.publishedVersionId(),
                    executionResult.executionStatus(),
                    wfMs,
                    executionResult.executionOutput());
        } catch (RuntimeException ignored) {
            // chat path must not fail on execution audit persistence
        }
        Instant assistantPersistStartedAt = Instant.now();
        persistAssistantTurnCommitted(orgId, userId, sessionId, answer, "AI_CHAT", modelName);
        stageTraces.add(stageTrace("PERSISTENCE", "消息落库", "SUCCESS", assistantPersistStartedAt, Instant.now(),
                "用户消息与助手回复已写入会话日志。", Map.of("sessionId", sessionId)));
        List<String> activatedSkillCodes = resolveActivatedSkillCodes(skillContext, toolCallTraces);
        recordRunTraceSafely(
                orgId,
                userId,
                sessionId,
                question,
                answer,
                modelName,
                requestedKnowledgeBaseIds,
                effectiveKnowledgeBaseIds,
                ragResult,
                messages,
                skillContext,
                activatedSkillCodes,
                stageTraces,
                modelCallTraces,
                toolCallTraces,
                executionResult,
                wfMs,
                runStartedAt,
                Instant.now());
        recordBillingUsageSafely(orgId, userId, sessionId, modelName, skillContext.agentId(), ragResult,
                modelCallTraces, toolCallTraces, wfMs, isBillableAssistantAnswer(answer));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orgId", orgId);
        payload.put("runId", runId);
        payload.put("sessionId", sessionId);
        payload.put("agentId", skillContext.agentId());
        payload.put("answer", answer);
        payload.put("model", Map.of("modelName", modelName));
        payload.put("ragContext", ragContext);
        payload.put("ragRetrieval", ragRetrievalPayload(ragResult));
        payload.put("effectiveKnowledgeBaseIds", effectiveKnowledgeBaseIds);
        payload.put("resolvedSkills", skillContext.skillCodes());
        payload.put("resolvedSkillVersions", skillContext.resolvedSkillRefs());
        payload.put("fileBackedSkillRefs", builtinDocs.refs());
        payload.put("effectiveToolNames", skillContext.allowedToolNames());
        payload.put("agentDirectToolNames", skillContext.agentDirectToolNames());
        payload.put("skillDeclaredToolNames", skillContext.skillDeclaredToolNames());
        payload.put("skillScopedToolNames", skillContext.skillScopedToolNames());
        payload.put("skillApiToolNames", skillContext.skillApiTools().stream().map(item -> item.toolName()).toList());
        payload.put("activeSkillCode", skillContext.activeSkillCode() == null ? "" : skillContext.activeSkillCode());
        payload.put("runtimeContext", runtimeContextPromptService.toPayload(runtimeContext));
        payload.put("runtimePolicy", Map.of(
                "maxToolCalls", maxToolRounds,
                "publishedVersionId", skillContext.publishedVersionId() == null ? "" : skillContext.publishedVersionId().toString(),
                "policyBundleCode", skillContext.policyBundle() == null ? "" : skillContext.policyBundle().bundleCode(),
                "policyBundleVersionNo", skillContext.policyBundle() == null || skillContext.policyBundle().versionNo() == null
                        ? ""
                        : skillContext.policyBundle().versionNo().toString()
        ));
        payload.put("runtimeExecution", Map.of(
                "status", executionResult.executionStatus(),
                "output", executionResult.executionOutput(),
                "publishedVersionId", executionResult.publishedVersionId() == null ? "" : executionResult.publishedVersionId().toString(),
                "resolvedSkillVersions", executionResult.resolvedSkillVersions(),
                "policyBundle", executionResult.policyBundle(),
                "trace", executionResult.executionTrace(),
                "contextSnapshot", executionResult.contextSnapshot()
        ));
        payload.put("runtimeGovernance", Map.of(
                "resolvedSkillVersions", executionResult.resolvedSkillVersions(),
                "policyBundle", executionResult.policyBundle()
        ));
        payload.put("timestamp", Instant.now().toString());
        return payload;
    }

    public void chatStream(String orgId, String userId, String sessionId,
                           String question, List<String> kbIds, String requestedAgentId,
                           String activeSkillCode, SseEmitter emitter) {
        chatStream(orgId, userId, sessionId, question, kbIds, requestedAgentId, activeSkillCode, Map.of(), emitter);
    }

    public void chatStream(String orgId, String userId, String sessionId,
                           String question, List<String> kbIds, String requestedAgentId,
                           String activeSkillCode, Map<String, String> metadataFilters, SseEmitter emitter) {
        CompletableFuture.runAsync(() -> {
            chatStreamBlocking(orgId, userId, sessionId, question, kbIds, requestedAgentId, activeSkillCode, metadataFilters, emitter);
        }, agentRuntimeExecutor);
    }

    public void chatStreamBlocking(String orgId, String userId, String sessionId,
                                   String question, List<String> kbIds, String requestedAgentId,
                                   String activeSkillCode, SseEmitter emitter) {
        chatStreamBlocking(orgId, userId, sessionId, question, kbIds, requestedAgentId, activeSkillCode, Map.of(), emitter);
    }

    public void chatStreamBlocking(String orgId, String userId, String sessionId,
                                   String question, List<String> kbIds, String requestedAgentId,
                                   String activeSkillCode, Map<String, String> metadataFilters, SseEmitter emitter) {
        String runId = newRunId();
        agentRuntimeConcurrencyService.run(orgId, userId, requestedAgentId, sessionId, () -> {
            chatStreamBlockingLocked(orgId, userId, sessionId, question, kbIds, requestedAgentId,
                    activeSkillCode, metadataFilters, emitter, runId);
            return null;
        });
    }

    private void chatStreamBlockingLocked(String orgId, String userId, String sessionId,
                                          String question, List<String> kbIds, String requestedAgentId,
                                          String activeSkillCode, Map<String, String> metadataFilters,
                                          SseEmitter emitter, String runId) {
            Instant runStartedAt = Instant.now();
            List<AgentRunTraceService.StageTraceInput> stageTraces = new ArrayList<>();
            List<AgentRunTraceService.ModelCallTraceInput> modelCallTraces = new ArrayList<>();
            List<AgentRunTraceService.ToolCallTraceInput> toolCallTraces = new ArrayList<>();
            try {
                safeSendPhase(emitter, "run", null, Map.of("runId", runId));
                Instant skillStartedAt = Instant.now();
        ResolvedSkillContext skillContext = skillResolverService.resolve(
                orgId, requestedAgentId, sessionId, Optional.ofNullable(activeSkillCode));
        agentAccessControlService.require(orgId, userId, TenantContext.getRoles(), skillContext.agentId(), AgentPermission.RUN);
                BuiltinSkillDocumentService.ResolvedBuiltinSkillDocs builtinDocs =
                        builtinSkillDocumentService.resolveDocs(skillContext, question);
                stageTraces.add(stageTrace("SKILL_RESOLVE", "技能候选解析", "SUCCESS", skillStartedAt, Instant.now(),
                        "已解析当前智能体绑定技能、工具边界与会话激活技能。",
                        withRunId(skillTraceMetadata(skillContext, List.of(), builtinDocs), runId)));
                Instant userPersistStartedAt = Instant.now();
                persistUserTurnCommitted(orgId, userId, sessionId, question, skillContext.agentId());
                stageTraces.add(stageTrace("USER_MESSAGE", "用户输入", "SUCCESS", userPersistStartedAt, Instant.now(),
                        clipForTrace(question, 220), Map.of("sessionId", sessionId, "runId", runId)));

                Map<String, String> routedModel = modelRouterService.route(orgId, "chat", skillContext.agentModel());
                String modelName = resolveModelName(skillContext.agentModel(), routedModel.get("provider"), routedModel.get("modelName"));
                ModelCallCredentials modelCredentials = resolveModelCallCredentials(orgId, routedModel.get("provider"));
                boolean showThinking = chatThinkingConfigService.isEnabled(orgId);
                List<String> effectiveKnowledgeBaseIds = skillResolverService.resolveKnowledgeBaseIds(skillContext, kbIds);
                List<String> requestedKnowledgeBaseIds = normalizeKnowledgeBaseIds(kbIds);
                KnowledgeRetrievalRouter.Decision knowledgeDecision = KnowledgeRetrievalRouter.decide(
                        question, effectiveKnowledgeBaseIds, requestedKnowledgeBaseIds, sessionId);
                boolean useKnowledgeRetrieval = knowledgeDecision.shouldRetrieve();
                safeSendPhase(emitter, "model", modelName);
                if (useKnowledgeRetrieval) {
                    safeSendPhase(emitter, "retrieving", modelName, Map.of(
                            "knowledgeBaseIds", effectiveKnowledgeBaseIds,
                            "ragTriggerReason", knowledgeDecision.reason().name(),
                            "ragMatchedCategory", knowledgeDecision.matchedCategory(),
                            "ragMatchedTerm", knowledgeDecision.matchedTerm(),
                            "ragPolicyVersion", knowledgeDecision.policyVersion()
                    ));
                }
                Instant ragStartedAt = Instant.now();
                RagService.RetrievalResult ragResult = useKnowledgeRetrieval
                        ? ragService.retrieveDetailed(
                        orgId,
                        effectiveKnowledgeBaseIds,
                        question,
                        metadataFilters,
                        KbAccessControlService.AccessPrincipal.user(userId, TenantContext.getRoles()))
                        : emptyRagRetrievalResult();
                stageTraces.add(stageTrace("RAG", useKnowledgeRetrieval ? "知识库检索" : "知识库检索未触发",
                        useKnowledgeRetrieval ? "SUCCESS" : "SKIPPED", ragStartedAt, Instant.now(),
                        useKnowledgeRetrieval
                                ? "知识库检索完成，命中 " + ragResult.context().size() + " 个片段。"
                                : "本轮输入未满足知识库检索条件。",
                        withRunId(ragDetailMetadata(ragResult, knowledgeDecision), runId)));
                List<String> ragContext = ragResult.context();
                if (useKnowledgeRetrieval) {
                    safeSendPhase(emitter, "rag_done", modelName, ragPhasePayload(ragResult));
                    log.info("chatStream RAG done: session={} agent={} kbs={} contexts={} timingsMs={} fallback={}",
                            sessionId,
                            skillContext.agentId(),
                            ragResult.knowledgeBases().stream().map(RagService.RetrievedKnowledgeBase::name).toList(),
                            ragContext.size(),
                            ragResult.timingsMs(),
                            ragResult.fallbackUsed());
                }
                Instant toolSchemaStartedAt = Instant.now();
                List<Map<String, Object>> tools = isWecomKfSession(sessionId)
                        ? List.of()
                        : toolOrchestratorService.getToolDefinitions(
                        orgId, skillContext.allowedToolNames(), skillContext.skillApiTools());
                stageTraces.add(stageTrace("TOOL_SCHEMA", "工具定义加载", "SUCCESS", toolSchemaStartedAt, Instant.now(),
                        "已加载本轮可用工具定义 " + tools.size() + " 个。",
                        Map.of("toolDefinitionCount", tools.size(), "allowedToolNames", skillContext.allowedToolNames(), "runId", runId)));
                RuntimeContext runtimeContext = runtimeContextPromptService.current();
                chatSessionStateService.mergeUserTurn(orgId, sessionId, skillContext.agentId(), question);
                List<Map<String, Object>> messages = buildInitialMessages(
                        sessionId, question, ragContext, showThinking, skillContext, orgId, userId,
                        runtimeContext, routedModel.get("provider"), modelName, builtinDocs);
                appendConfirmedPendingEmailBodyToolResult(
                        messages, orgId, userId, sessionId, skillContext, emitter, toolCallTraces, runId, question);
                Optional<String> forcedCrmProductSalesAnswer = appendForcedCrmProductSalesToolResult(
                        messages, orgId, userId, sessionId, skillContext, toolCallTraces, runId, question);
                int maxToolRounds = resolveMaxToolRounds(skillContext.maxToolCalls());
                boolean pendingApprovalsUsed = forcedCrmProductSalesAnswer.isEmpty() && resolveToolCalls(
                        modelName, messages, tools, orgId, userId, sessionId,
                        showThinking, skillContext, emitter, maxToolRounds, modelCredentials, modelCallTraces,
                        toolCallTraces, runId);
                if (pendingApprovalsUsed) {
                    // Keep chat concise when a dedicated approvals page is rendered on frontend.
                    messages.add(Map.of(
                            "role", "system",
                            "content", "You have already returned approval records via tool_result event. "
                                    + "Do not repeat long approval lists in chat; give only a short summary in 1-2 sentences."
                    ));
                }
                if (hasToolMessages(messages)) {
                    messages.add(Map.of(
                            "role", "system",
                            "content", buildToolFinalAnswerGuardPrompt()
                    ));
                }

                safeSendPhase(emitter, "generating", modelName);
                String finalText;
                if (forcedCrmProductSalesAnswer.isPresent()) {
                    finalText = forcedCrmProductSalesAnswer.get();
                    safeSendDelta(emitter, finalText);
                } else {
                    StringBuilder acc = new StringBuilder();
                    log.info("chatStream start LLM stream: session={} model={} msgCount={} toolCount={}",
                            sessionId, modelName, messages.size(), 0);
                    long streamStart = System.currentTimeMillis();
                    Instant finalModelStartedAt = Instant.now();
                    String finalModelStatus = "SUCCESS";
                    ChatStreamResult streamResult = new ChatStreamResult(0, 0);
                    try {
                        if (modelCredentials.hasProviderCredentials()) {
                            streamResult = aliyunBailianClient.chatStreamWithCredentials(
                                    modelName,
                                    messages,
                                    null,
                                    showThinking,
                                    piece -> {
                                        acc.append(piece);
                                        safeSendDelta(emitter, piece);
                                    },
                                    modelCredentials.apiBaseUrl(),
                                    modelCredentials.apiKey());
                        } else {
                            streamResult = aliyunBailianClient.chatStreamWithMessages(
                                    modelName,
                                    messages,
                                    null,
                                    showThinking,
                                    piece -> {
                                        acc.append(piece);
                                        safeSendDelta(emitter, piece);
                                    });
                        }
                        log.info("chatStream LLM stream done: session={} chars={} elapsedMs={}",
                                sessionId, acc.length(), System.currentTimeMillis() - streamStart);
                    } catch (Exception ex) {
                        finalModelStatus = "FAILED";
                        log.warn("chatStream LLM stream failed: session={} elapsedMs={} err={}",
                                sessionId, System.currentTimeMillis() - streamStart, ex.getMessage());
                        String fallback = "（生成回复时发生错误，请重试。详情：" + ex.getMessage() + "）";
                        acc.append(fallback);
                        safeSendDeltaInChunks(emitter, fallback);
                    } finally {
                        Instant finalModelEndedAt = Instant.now();
                        modelCallTraces.add(new AgentRunTraceService.ModelCallTraceInput(
                                "final_stream",
                                modelName,
                                finalModelStatus,
                                finalModelStartedAt,
                                finalModelEndedAt,
                                elapsedMs(finalModelStartedAt, finalModelEndedAt),
                                0,
                                acc.length(),
                                streamResult.promptTokens(),
                                streamResult.completionTokens(),
                                "最终流式回复生成。"));
                    }

                    finalText = showThinking
                            ? acc.toString()
                            : AssistantContentSanitizer.stripThinkingSections(acc.toString());
                    if (finalText == null || finalText.trim().isEmpty()) {
                        finalText = buildToolResultFallbackMessage(messages);
                        safeSendDeltaInChunks(emitter, finalText);
                    } else {
                        String guardedFinalText = appendToolResultFallbackIfDeferred(finalText, messages);
                        if (!guardedFinalText.equals(finalText)) {
                            String appended = guardedFinalText.substring(finalText.length());
                            safeSendDeltaInChunks(emitter, appended);
                            finalText = guardedFinalText;
                        }
                    }
                }
                Instant wfStartedAt = Instant.now();
                AgentWorkflowRuntimeService.RuntimeExecutionResult executionResult = agentWorkflowRuntimeService.evaluateForChat(
                        orgId, skillContext.agentId(), question, skillContext.allowedToolNames());
                executionResult.contextSnapshot().put("runId", runId);
                Instant wfEndedAt = Instant.now();
                int wfMs = elapsedMs(wfStartedAt, wfEndedAt);
                stageTraces.add(stageTrace("WORKFLOW", "技能运行治理",
                        AgentWorkflowExecutionLogService.STATUS_SUCCESS.equals(
                                AgentWorkflowExecutionLogService.normalizeWorkflowStatus(executionResult.executionStatus()))
                                ? "SUCCESS" : "FAILED",
                        wfStartedAt, wfEndedAt, clipForTrace(executionResult.executionOutput(), 260),
                        workflowTraceMetadata(executionResult, wfMs)));
                try {
                    agentWorkflowExecutionLogService.appendFromChat(
                            orgId,
                            skillContext.agentId(),
                            executionResult.publishedVersionId(),
                            executionResult.executionStatus(),
                            wfMs,
                            executionResult.executionOutput());
                } catch (RuntimeException ignored) {
                    // stream path must not fail on execution audit persistence
                }
                Instant assistantPersistStartedAt = Instant.now();
                persistAssistantTurnCommitted(orgId, userId, sessionId, finalText, "AI_CHAT_STREAM", modelName);
                stageTraces.add(stageTrace("PERSISTENCE", "消息落库", "SUCCESS", assistantPersistStartedAt, Instant.now(),
                        "用户消息与助手回复已写入会话日志。", Map.of("sessionId", sessionId)));
                List<String> activatedSkillCodes = resolveActivatedSkillCodes(skillContext, toolCallTraces);
                recordRunTraceSafely(
                        orgId,
                        userId,
                        sessionId,
                        question,
                        finalText,
                        modelName,
                        requestedKnowledgeBaseIds,
                        effectiveKnowledgeBaseIds,
                        ragResult,
                        messages,
                        skillContext,
                        activatedSkillCodes,
                        stageTraces,
                        modelCallTraces,
                        toolCallTraces,
                        executionResult,
                        wfMs,
                        runStartedAt,
                        Instant.now());
                recordBillingUsageSafely(orgId, userId, sessionId, modelName, skillContext.agentId(), ragResult,
                        modelCallTraces, toolCallTraces, wfMs, isBillableAssistantAnswer(finalText));
                emitter.send(SseEmitter.event().name("done").data(Map.of("ok", true, "runId", runId)));
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data(Map.of("message", e.getMessage() == null ? "stream failed" : e.getMessage(), "runId", runId)));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
    }

    // ── Function calling loop ──

    /**
     * Run the full tool-calling loop: model → tool calls → execute → feed back → repeat.
     * Returns the final text answer.
     */
    private String runToolLoop(String modelName, List<Map<String, Object>> messages,
                               List<Map<String, Object>> tools, String orgId, String userId, String sessionId,
                               boolean showThinking, ResolvedSkillContext skillContext, int maxToolRounds,
                               ModelCallCredentials modelCredentials,
                               List<AgentRunTraceService.ModelCallTraceInput> modelCallTraces,
                               List<AgentRunTraceService.ToolCallTraceInput> toolCallTraces,
                               String runId) {
        for (int round = 0; round < maxToolRounds; round++) {
            Instant modelStartedAt = Instant.now();
            ChatCompletionResult result;
            try {
                result = chatCompletionWithResolvedCredentials(
                        modelName, messages, tools.isEmpty() ? null : tools, !showThinking, modelCredentials);
            } catch (RuntimeException ex) {
                Instant modelEndedAt = Instant.now();
                modelCallTraces.add(new AgentRunTraceService.ModelCallTraceInput(
                        "tool_planning",
                        modelName,
                        "FAILED",
                        modelStartedAt,
                        modelEndedAt,
                        elapsedMs(modelStartedAt, modelEndedAt),
                        0,
                        0,
                        "模型工具规划失败：" + ex.getMessage()));
                throw ex;
            }
            Instant modelEndedAt = Instant.now();
            List<ToolCallInfo> resultToolCalls = safeToolCalls(result);
            modelCallTraces.add(new AgentRunTraceService.ModelCallTraceInput(
                    result.hasToolCalls() ? "tool_planning" : "final_completion",
                    modelName,
                    "SUCCESS",
                    modelStartedAt,
                    modelEndedAt,
                    elapsedMs(modelStartedAt, modelEndedAt),
                    resultToolCalls.size(),
                    result.content() == null ? 0 : result.content().length(),
                    result.promptTokens(),
                    result.completionTokens(),
                    result.hasToolCalls() ? "模型规划了 " + resultToolCalls.size() + " 个工具调用。" : "模型返回最终文本。"));

            if (!result.hasToolCalls()) {
                if (result.content() == null || result.content().isBlank()) {
                    if (hasToolMessages(messages)) {
                        return buildToolResultFallbackMessage(messages);
                    }
                    return "模型本轮未能生成回复，请稍后重试。";
                }
                String answer = showThinking ? result.content() : AssistantContentSanitizer.stripThinkingSections(result.content());
                return appendToolResultFallbackIfDeferred(answer, messages);
            }

            appendToolCallsAndResults(messages, result, orgId, userId, sessionId, skillContext, null, toolCallTraces, runId);
        }
        return completeFromToolResultsAfterLimit(modelName, messages, showThinking, maxToolRounds, modelCredentials, modelCallTraces);
    }

    private String completeFromToolResultsAfterLimit(String modelName,
                                                     List<Map<String, Object>> messages,
                                                     boolean showThinking,
                                                     int maxToolRounds,
                                                     ModelCallCredentials modelCredentials,
                                                     List<AgentRunTraceService.ModelCallTraceInput> modelCallTraces) {
        String deterministicFallback = buildToolLimitReachedFallbackMessage(messages, maxToolRounds);
        if (!hasToolMessages(messages)) {
            return deterministicFallback;
        }

        List<Map<String, Object>> finalMessages = new ArrayList<>(messages);
        finalMessages.add(Map.of(
                "role", "system",
                "content", buildToolLimitFinalAnswerPrompt(maxToolRounds)
        ));
        Instant modelStartedAt = Instant.now();
        ChatCompletionResult result;
        try {
            result = chatCompletionWithResolvedCredentials(modelName, finalMessages, null, !showThinking, modelCredentials);
        } catch (RuntimeException ex) {
            Instant modelEndedAt = Instant.now();
            if (modelCallTraces != null) {
                modelCallTraces.add(new AgentRunTraceService.ModelCallTraceInput(
                        "tool_limit_summary",
                        modelName,
                        "FAILED",
                        modelStartedAt,
                        modelEndedAt,
                        elapsedMs(modelStartedAt, modelEndedAt),
                        0,
                        0,
                        "工具轮次耗尽后的模型收口失败：" + ex.getMessage()));
            }
            return deterministicFallback;
        }
        Instant modelEndedAt = Instant.now();
        String content = result == null || result.content() == null ? "" : result.content().trim();
        if (modelCallTraces != null) {
            modelCallTraces.add(new AgentRunTraceService.ModelCallTraceInput(
                    "tool_limit_summary",
                    modelName,
                    isUsableToolLimitAnswer(content) ? "SUCCESS" : "FAILED",
                    modelStartedAt,
                    modelEndedAt,
                    elapsedMs(modelStartedAt, modelEndedAt),
                    safeToolCalls(result).size(),
                    content.length(),
                    result == null ? 0 : result.promptTokens(),
                    result == null ? 0 : result.completionTokens(),
                    isUsableToolLimitAnswer(content)
                            ? "工具轮次耗尽后，模型基于已有工具结果完成收口。"
                            : "工具轮次耗尽后，模型未能生成可用收口，已使用确定性摘要兜底。"));
        }
        if (!isUsableToolLimitAnswer(content)) {
            return deterministicFallback;
        }
        if (finalAnswerDefersToolResult(content)) {
            return content
                    + "\n\n---\n本轮不会继续发起工具调用。以下是已经返回的工具结果摘要：\n\n"
                    + deterministicFallback;
        }
        return content;
    }

    /**
     * Resolve all tool calls without returning final answer — used before streaming.
     * After this, messages contains the full tool conversation history,
     * and the next model call (streaming) will produce the final answer.
     */
    private boolean resolveToolCalls(String modelName, List<Map<String, Object>> messages,
                                  List<Map<String, Object>> tools, String orgId, String userId, String sessionId,
                                  boolean showThinking,
                                  ResolvedSkillContext skillContext,
                                  SseEmitter emitter,
                                  int maxToolRounds,
                                  ModelCallCredentials modelCredentials,
                                  List<AgentRunTraceService.ModelCallTraceInput> modelCallTraces,
                                  List<AgentRunTraceService.ToolCallTraceInput> toolCallTraces,
                                  String runId) {
        if (tools.isEmpty()) return false;
        boolean pendingApprovalsUsed = false;

        for (int round = 0; round < maxToolRounds; round++) {
            Instant modelStartedAt = Instant.now();
            ChatCompletionResult result;
            try {
                List<Map<String, Object>> planningMessages = hasToolMessages(messages)
                        ? withToolPlanningStopPrompt(messages)
                        : messages;
                result = chatCompletionWithResolvedCredentials(
                        modelName, planningMessages, tools, !showThinking, modelCredentials);
            } catch (RuntimeException ex) {
                Instant modelEndedAt = Instant.now();
                modelCallTraces.add(new AgentRunTraceService.ModelCallTraceInput(
                        "tool_planning",
                        modelName,
                        "FAILED",
                        modelStartedAt,
                        modelEndedAt,
                        elapsedMs(modelStartedAt, modelEndedAt),
                        0,
                        0,
                        "模型工具规划失败：" + ex.getMessage()));
                throw ex;
            }
            Instant modelEndedAt = Instant.now();
            List<ToolCallInfo> resultToolCalls = safeToolCalls(result);
            modelCallTraces.add(new AgentRunTraceService.ModelCallTraceInput(
                    result.hasToolCalls() ? "tool_planning" : "tool_planning_stop",
                    modelName,
                    "SUCCESS",
                    modelStartedAt,
                    modelEndedAt,
                    elapsedMs(modelStartedAt, modelEndedAt),
                    resultToolCalls.size(),
                    result.content() == null ? 0 : result.content().length(),
                    result.promptTokens(),
                    result.completionTokens(),
                    result.hasToolCalls() ? "模型规划了 " + resultToolCalls.size() + " 个工具调用。" : "模型未继续请求工具。"));

            if (!result.hasToolCalls()) {
                break;
            }
            pendingApprovalsUsed = appendToolCallsAndResults(
                    messages, result, orgId, userId, sessionId, skillContext, emitter, toolCallTraces, runId)
                    || pendingApprovalsUsed;
            if (shouldSkipToolPlanningStop(questionFromMessages(messages), resultToolCalls, messages)) {
                modelCallTraces.add(new AgentRunTraceService.ModelCallTraceInput(
                        "tool_planning_stop_skipped",
                        modelName,
                        "SKIPPED",
                        Instant.now(),
                        Instant.now(),
                        0,
                        0,
                        0,
                        0,
                        0,
                        "单个只读查询工具已成功返回结果，直接进入最终生成。"));
                break;
            }
        }
        return pendingApprovalsUsed;
    }

    private boolean appendToolCallsAndResults(List<Map<String, Object>> messages,
                                            ChatCompletionResult result, String orgId, String userId,
                                            String sessionId,
                                            ResolvedSkillContext skillContext,
                                            SseEmitter emitter,
                                            List<AgentRunTraceService.ToolCallTraceInput> toolCallTraces,
                                            String runId) {
        List<Map<String, Object>> toolCallMaps = new ArrayList<>();
        List<ToolCallInfo> toolCalls = safeToolCalls(result);
        for (ToolCallInfo tc : toolCalls) {
            toolCallMaps.add(Map.of(
                    "id", tc.id(),
                    "type", "function",
                    "function", Map.of("name", tc.name(), "arguments", tc.arguments())
            ));
        }
        Map<String, Object> assistantMsg = new HashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", result.content() != null ? result.content() : "");
        assistantMsg.put("tool_calls", toolCallMaps);
        messages.add(assistantMsg);

        boolean pendingApprovalsUsed = false;
        for (ToolCallInfo tc : toolCalls) {
            if (emitter != null) {
                safeSendToolCall(emitter, tc.name());
            }
            log.info("Calling MCP tool: {} with args: {}", tc.name(), tc.arguments());
            String canonicalTool = ToolNameNormalizer.canonicalize(tc.name());
            String idempotencyKey = toolIdempotencyKey(runId, tc.id(), canonicalTool != null ? canonicalTool : tc.name());
            Instant toolStartedAt = Instant.now();
            String toolResult = "";
            boolean toolSuccess = true;
            try {
                toolResult = toolOrchestratorService.executeTool(
                        orgId,
                        userId,
                        tc.name(),
                        tc.arguments(),
                        skillContext.allowedToolNames(),
                        skillContext.agentDirectToolNames());
            } catch (RuntimeException ex) {
                toolSuccess = false;
                toolResult = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                throw ex;
            } finally {
                Instant toolEndedAt = Instant.now();
                if (toolCallTraces != null) {
                    toolCallTraces.add(new AgentRunTraceService.ToolCallTraceInput(
                            tc.id(),
                            canonicalTool != null ? canonicalTool : tc.name(),
                            traceToolArguments(tc.arguments(), idempotencyKey),
                            toolResult,
                            toolSuccess && !looksFailedToolResult(toolResult),
                            toolStartedAt,
                            toolEndedAt,
                            elapsedMs(toolStartedAt, toolEndedAt)));
                }
            }
            logToolInvocationAudit(orgId, userId, sessionId, skillContext, canonicalTool != null ? canonicalTool : tc.name());
            chatSessionStateService.mergeToolResult(orgId, sessionId, skillContext.agentId(), tc.name(), toolResult);
            if (emitter != null && isPendingApprovalsTool(tc.name())) {
                safeSendToolResult(emitter, tc.name(), toolResult);
                pendingApprovalsUsed = true;
            }
            messages.add(Map.of(
                    "role", "tool",
                    "tool_call_id", tc.id(),
                    "content", toolResult
            ));
        }
        return pendingApprovalsUsed;
    }

    private boolean appendConfirmedPendingEmailBodyToolResult(List<Map<String, Object>> messages,
                                                              String orgId,
                                                              String userId,
                                                              String sessionId,
                                                              ResolvedSkillContext skillContext,
                                                              SseEmitter emitter,
                                                              List<AgentRunTraceService.ToolCallTraceInput> toolCallTraces,
                                                              String runId,
                                                              String question) {
        if (!isEmailBodyContinuationConfirmation(question)) {
            return false;
        }
        Optional<PendingEmailState> pendingEmail = pendingEmailFromState(orgId, sessionId);
        if (pendingEmail.isEmpty()) {
            return false;
        }
        PendingEmailState pending = pendingEmail.get();
        String toolResult = executeAndAppendSyntheticToolCall(
                messages,
                orgId,
                userId,
                sessionId,
                skillContext,
                emitter,
                toolCallTraces,
                runId,
                "email_get_message",
                "{\"messageId\":\"" + escapeJson(pending.messageId()) + "\"}",
                "auto_email_body_");
        if (isEmailMessageIdNotFoundResult(toolResult) && pending.hasRefreshHints()) {
            String searchResult = executeAndAppendSyntheticToolCall(
                    messages,
                    orgId,
                    userId,
                    sessionId,
                    skillContext,
                    emitter,
                    toolCallTraces,
                    runId,
                    "email_search",
                    buildEmailRefreshSearchArguments(pending),
                    "auto_email_refresh_");
            Optional<String> refreshedMessageId = extractSingleEmailSearchMessageId(searchResult);
            if (refreshedMessageId.isPresent() && !refreshedMessageId.get().equals(pending.messageId())) {
                executeAndAppendSyntheticToolCall(
                        messages,
                        orgId,
                        userId,
                        sessionId,
                        skillContext,
                        emitter,
                        toolCallTraces,
                        runId,
                        "email_get_message",
                        "{\"messageId\":\"" + escapeJson(refreshedMessageId.get()) + "\"}",
                        "auto_email_body_");
            }
        }
        return true;
    }

    private String executeAndAppendSyntheticToolCall(List<Map<String, Object>> messages,
                                                     String orgId,
                                                     String userId,
                                                     String sessionId,
                                                     ResolvedSkillContext skillContext,
                                                     SseEmitter emitter,
                                                     List<AgentRunTraceService.ToolCallTraceInput> toolCallTraces,
                                                     String runId,
                                                     String toolName,
                                                     String arguments,
                                                     String callIdPrefix) {
        String callId = callIdPrefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        messages.add(Map.of(
                "role", "assistant",
                "content", "",
                "tool_calls", List.of(Map.of(
                        "id", callId,
                        "type", "function",
                        "function", Map.of("name", toolName, "arguments", arguments)
                ))
        ));
        if (emitter != null) {
            safeSendToolCall(emitter, toolName);
        }
        String idempotencyKey = toolIdempotencyKey(runId, callId, toolName);
        Instant toolStartedAt = Instant.now();
        String toolResult = "";
        boolean toolSuccess = true;
        try {
            toolResult = toolOrchestratorService.executeTool(
                    orgId,
                    userId,
                    toolName,
                    arguments,
                    skillContext.allowedToolNames(),
                    skillContext.agentDirectToolNames());
        } catch (RuntimeException ex) {
            toolSuccess = false;
            toolResult = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            throw ex;
        } finally {
            Instant toolEndedAt = Instant.now();
            if (toolCallTraces != null) {
                toolCallTraces.add(new AgentRunTraceService.ToolCallTraceInput(
                        callId,
                        toolName,
                        traceToolArguments(arguments, idempotencyKey),
                        toolResult,
                        toolSuccess && !looksFailedToolResult(toolResult),
                        toolStartedAt,
                        toolEndedAt,
                        elapsedMs(toolStartedAt, toolEndedAt)));
            }
        }
        logToolInvocationAudit(orgId, userId, sessionId, skillContext, toolName);
        chatSessionStateService.mergeToolResult(orgId, sessionId, skillContext.agentId(), toolName, toolResult);
        messages.add(Map.of(
                "role", "tool",
                "tool_call_id", callId,
                "content", toolResult
        ));
        return toolResult;
    }

    private Optional<String> appendForcedCrmProductSalesToolResult(
            List<Map<String, Object>> messages,
            String orgId,
            String userId,
            String sessionId,
            ResolvedSkillContext skillContext,
            List<AgentRunTraceService.ToolCallTraceInput> toolCallTraces,
            String runId,
            String question) {
        if (!skillContext.skillCodes().contains("crm-business-analysis")) {
            return Optional.empty();
        }
        Optional<String> arguments = CrmProductSalesIntentRouter.route(question);
        if (arguments.isEmpty()) {
            return Optional.empty();
        }
        String toolResult = executeAndAppendSyntheticToolCall(
                messages,
                orgId,
                userId,
                sessionId,
                skillContext,
                null,
                toolCallTraces,
                runId,
                CrmProductSalesAnalysisToolService.TOOL_NAME,
                arguments.get(),
                "auto_crm_sales_");
        return Optional.of(crmProductSalesAnswerFormatter.formatJson(toolResult));
    }

    private Optional<PendingEmailState> pendingEmailFromState(String orgId, String sessionId) {
        return chatSessionStateService.get(orgId, sessionId)
                .flatMap(state -> pendingEmailFromStateJson(state.getStateJson()));
    }

    record PendingEmailState(String messageId, String subject, String from) {
        boolean hasRefreshHints() {
            return (subject != null && !subject.isBlank()) || (from != null && !from.isBlank());
        }
    }

    static Optional<PendingEmailState> pendingEmailFromStateJson(String stateJson) {
        if (stateJson == null || stateJson.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = TOOL_RESULT_OBJECT_MAPPER.readTree(stateJson);
            String messageId = root.path("pending_email_message_id").asText("").trim();
            String action = root.path("pending_email_action").asText("").trim();
            if (messageId.isBlank()) {
                return Optional.empty();
            }
            if (!action.isBlank() && !"read_body".equalsIgnoreCase(action)) {
                return Optional.empty();
            }
            String subject = root.path("pending_email_subject").asText("").trim();
            String from = root.path("pending_email_from").asText("").trim();
            return Optional.of(new PendingEmailState(messageId, subject, from));
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    static boolean isEmailMessageIdNotFoundResult(String result) {
        String text = result == null ? "" : result.toLowerCase(Locale.ROOT);
        return text.contains("没有找到 messageid")
                || (text.contains("messageid") && text.contains("pop3") && text.contains("获取最新 id"));
    }

    static String buildEmailRefreshSearchArguments(PendingEmailState pending) {
        StringBuilder args = new StringBuilder("{\"keyword\":\"")
                .append(escapeJson(pending.subject()))
                .append("\",\"limit\":5,\"scanLimit\":50");
        if (pending.from() != null && !pending.from().isBlank()) {
            args.append(",\"from\":\"").append(escapeJson(pending.from())).append("\"");
        }
        args.append("}");
        return args.toString();
    }

    private void logToolInvocationAudit(String orgId, String userId, String sessionId,
                                       ResolvedSkillContext skillContext, String canonicalToolName) {
        try {
            String tool = canonicalToolName == null ? "" : canonicalToolName;
            String invocationType = classifyInvocationForAudit(tool, skillContext);
            String skillJson = skillContext.activeSkillCode() == null
                    ? "null"
                    : ("\"" + escapeJson(skillContext.activeSkillCode()) + "\"");
            String detail = String.format(Locale.ROOT,
                    "{\"timestamp\":\"%s\",\"agent_id\":\"%s\",\"skill_id\":%s,\"session_id\":\"%s\",\"user_id\":\"%s\","
                            + "\"tool\":\"%s\",\"invocation_type\":\"%s\",\"policy\":\"runtime_tool_allowlist_v2\",\"decision\":\"allowed\"}",
                    Instant.now(),
                    escapeJson(skillContext.agentId()),
                    skillJson,
                    escapeJson(sessionId),
                    escapeJson(userId),
                    escapeJson(tool),
                    invocationType);
            String clipped = detail.length() > 1900 ? detail.substring(0, 1900) + "…" : detail;
            auditService.log(orgId, userId, "TOOL_INVOCATION", clipped);
        } catch (RuntimeException ignored) {
            // chat path must not fail on audit persistence
        }
    }

    private static String escapeJson(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String classifyInvocationForAudit(String canonicalTool, ResolvedSkillContext ctx) {
        if ("memory_remember".equals(canonicalTool) || "memory_forget".equals(canonicalTool)) {
            return "memory_builtin";
        }
        LinkedHashSet<String> direct = new LinkedHashSet<>(ToolNameNormalizer.canonicalizeAll(ctx.agentDirectToolNames()));
        if (direct.contains(canonicalTool)) {
            return "agent_direct";
        }
        return "skill_scoped";
    }

    private void recordRunTraceSafely(String orgId,
                                      String userId,
                                      String sessionId,
                                      String question,
                                      String answer,
                                      String modelName,
                                      List<String> requestedKnowledgeBaseIds,
                                      List<String> effectiveKnowledgeBaseIds,
                                      RagService.RetrievalResult ragResult,
                                      List<Map<String, Object>> messages,
                                      ResolvedSkillContext skillContext,
                                      List<String> activatedSkillCodes,
                                      List<AgentRunTraceService.StageTraceInput> stageTraces,
                                      List<AgentRunTraceService.ModelCallTraceInput> modelCallTraces,
                                      List<AgentRunTraceService.ToolCallTraceInput> toolCallTraces,
                                      AgentWorkflowRuntimeService.RuntimeExecutionResult executionResult,
                                      int workflowElapsedMs,
                                      Instant startedAt,
                                      Instant endedAt) {
        try {
            agentRunTraceService.recordChatRun(new AgentRunTraceService.ChatRunTraceInput(
                    orgId,
                    userId,
                    sessionId,
                    skillContext.agentId(),
                    question,
                    answer,
                    modelName,
                    skillContext.activeSkillCode(),
                    requestedKnowledgeBaseIds,
                    effectiveKnowledgeBaseIds,
                    ragResult,
                    messages,
                    skillContext.skillCodes(),
                    activatedSkillCodes,
                    skillContext.resolvedSkillRefs(),
                    stageTraces,
                    modelCallTraces,
                    toolCallTraces,
                    executionResult,
                    workflowElapsedMs,
                    startedAt,
                    endedAt));
        } catch (RuntimeException ex) {
            log.warn("agent run trace persistence failed: session={} err={}", sessionId, ex.getMessage());
        }
    }

    private void recordBillingUsageSafely(String orgId,
                                          String userId,
                                          String sessionId,
                                          String modelName,
                                          String agentId,
                                          RagService.RetrievalResult ragResult,
                                          List<AgentRunTraceService.ModelCallTraceInput> modelCallTraces,
                                          List<AgentRunTraceService.ToolCallTraceInput> toolCallTraces,
                                          int workflowElapsedMs,
                                          boolean billable) {
        billingUsageMeteringService.recordChatRunSafely(new BillingUsageMeteringService.ChatRunMeteringInput(
                orgId,
                userId,
                agentId,
                sessionId,
                modelName,
                modelCallTraces,
                toolCallTraces,
                ragResult,
                workflowElapsedMs,
                billable,
                Instant.now()));
    }

    private static boolean isBillableAssistantAnswer(String content) {
        return isUsableToolLimitAnswer(content);
    }

    private static AgentRunTraceService.StageTraceInput stageTrace(String type,
                                                                   String title,
                                                                   String status,
                                                                   Instant startedAt,
                                                                   Instant endedAt,
                                                                   String summary,
                                                                   Map<String, Object> metadata) {
        return new AgentRunTraceService.StageTraceInput(
                type,
                title,
                status,
                startedAt,
                endedAt,
                elapsedMs(startedAt, endedAt),
                summary,
                metadata == null ? Map.of() : metadata);
    }

    private static String newRunId() {
        return "run-" + UUID.randomUUID();
    }

    private static Map<String, Object> withRunId(Map<String, Object> metadata, String runId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (metadata != null) {
            payload.putAll(metadata);
        }
        payload.put("runId", runId);
        return payload;
    }

    private static String toolIdempotencyKey(String runId, String toolCallId, String toolName) {
        return String.join(":",
                runId == null ? "run-unknown" : runId,
                toolName == null || toolName.isBlank() ? "tool" : toolName,
                toolCallId == null || toolCallId.isBlank() ? "call" : toolCallId);
    }

    private static String traceToolArguments(String rawArguments, String idempotencyKey) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("_idempotencyKey", idempotencyKey);
        String args = rawArguments == null ? "" : rawArguments.trim();
        if (!args.isBlank()) {
            try {
                Map<String, Object> parsed = TOOL_RESULT_OBJECT_MAPPER.readValue(args, new com.fasterxml.jackson.core.type.TypeReference<>() {});
                payload.putAll(parsed);
                return TOOL_RESULT_OBJECT_MAPPER.writeValueAsString(payload);
            } catch (Exception ignored) {
                payload.put("arguments", args);
            }
        }
        try {
            return TOOL_RESULT_OBJECT_MAPPER.writeValueAsString(payload);
        } catch (Exception ignored) {
            return args;
        }
    }

    private static List<ToolCallInfo> safeToolCalls(ChatCompletionResult result) {
        if (result == null || result.toolCalls() == null) {
            return List.of();
        }
        return result.toolCalls();
    }

    private static int elapsedMs(Instant startedAt, Instant endedAt) {
        if (startedAt == null || endedAt == null) {
            return 0;
        }
        return (int) Math.max(0L, java.time.Duration.between(startedAt, endedAt).toMillis());
    }

    private static String clipForTrace(String text, int max) {
        String safe = text == null ? "" : text;
        if (safe.length() <= max) {
            return safe;
        }
        return safe.substring(0, Math.max(0, max - 1)) + "…";
    }

    private static Map<String, Object> skillTraceMetadata(ResolvedSkillContext skillContext,
                                                          List<String> activatedSkillCodes,
                                                          BuiltinSkillDocumentService.ResolvedBuiltinSkillDocs builtinDocs) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("boundSkillCodes", skillContext.skillCodes());
        metadata.put("activatedSkillCodes", activatedSkillCodes == null ? List.of() : activatedSkillCodes);
        metadata.put("activeSkillCode", skillContext.activeSkillCode() == null ? "" : skillContext.activeSkillCode());
        metadata.put("allowedToolNames", skillContext.allowedToolNames());
        metadata.put("skillScopedToolNames", skillContext.skillScopedToolNames());
        metadata.put("fileBackedSkillRefs", builtinDocs == null ? List.of() : builtinDocs.refs());
        return metadata;
    }

    private static Map<String, Object> ragDetailMetadata(RagService.RetrievalResult ragResult) {
        return ragDetailMetadata(ragResult, null);
    }

    private static Map<String, Object> ragDetailMetadata(RagService.RetrievalResult ragResult,
                                                         KnowledgeRetrievalRouter.Decision decision) {
        if (ragResult == null) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("triggered", false);
            metadata.put("contextCount", 0);
            metadata.put("timingsMs", Map.of());
            metadata.putAll(knowledgeRetrievalDecisionMetadata(decision));
            return metadata;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("triggered", !ragResult.context().isEmpty() || !ragResult.knowledgeBases().isEmpty());
        metadata.put("contextCount", ragResult.context().size());
        metadata.put("knowledgeBases", ragResult.knowledgeBases().stream().map(RagService.RetrievedKnowledgeBase::name).toList());
        metadata.put("sources", ragResult.sources().stream().map(RagService.RetrievedSource::toPayload).toList());
        metadata.put("metadataFilters", ragResult.metadataFilters());
        metadata.put("permissionFilteredCount", ragResult.permissionFilteredCount());
        metadata.put("timingsMs", ragResult.timingsMs());
        metadata.put("fallbackUsed", ragResult.fallbackUsed());
        metadata.putAll(knowledgeRetrievalDecisionMetadata(decision));
        return metadata;
    }

    static Map<String, Object> knowledgeRetrievalDecisionMetadata(KnowledgeRetrievalRouter.Decision decision) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (decision == null) {
            metadata.put("ragTriggerReason", "");
            metadata.put("ragMatchedCategory", "");
            metadata.put("ragMatchedTerm", "");
            metadata.put("ragPolicyVersion", KnowledgeRetrievalRouter.POLICY_VERSION);
            return metadata;
        }
        metadata.put("ragTriggerReason", decision.reason().name());
        metadata.put("ragMatchedCategory", decision.matchedCategory());
        metadata.put("ragMatchedTerm", decision.matchedTerm());
        metadata.put("ragPolicyVersion", decision.policyVersion());
        return metadata;
    }

    private static Map<String, Object> workflowTraceMetadata(
            AgentWorkflowRuntimeService.RuntimeExecutionResult executionResult,
            int elapsedMs) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("status", executionResult == null ? "" : emptyString(executionResult.executionStatus()));
        metadata.put("elapsedMs", Math.max(0, elapsedMs));
        return metadata;
    }

    private static String emptyString(String value) {
        return value == null ? "" : value;
    }

    private static boolean looksFailedToolResult(String result) {
        String s = result == null ? "" : result.toLowerCase(Locale.ROOT);
        return s.contains("\"success\":false") || s.contains("\"error\"") || s.contains("failed")
                || s.contains("异常") || s.contains("失败") || s.contains("错误");
    }

    private static List<String> resolveActivatedSkillCodes(
            ResolvedSkillContext skillContext,
            List<AgentRunTraceService.ToolCallTraceInput> toolCallTraces) {
        LinkedHashSet<String> activated = new LinkedHashSet<>();
        if (skillContext.activeSkillCode() != null && !skillContext.activeSkillCode().isBlank()) {
            activated.add(skillContext.activeSkillCode());
        }
        LinkedHashSet<String> calledTools = new LinkedHashSet<>();
        for (AgentRunTraceService.ToolCallTraceInput tool : toolCallTraces == null ? List.<AgentRunTraceService.ToolCallTraceInput>of() : toolCallTraces) {
            String canonical = ToolNameNormalizer.canonicalize(tool.name());
            if (canonical != null && !canonical.isBlank()) {
                calledTools.add(canonical);
            }
        }
        if (!calledTools.isEmpty()) {
            for (SkillResolverService.ResolvedSkill skill : skillContext.skills()) {
                LinkedHashSet<String> declaredTools = new LinkedHashSet<>(ToolNameNormalizer.canonicalizeAll(skill.toolWhitelist()));
                declaredTools.retainAll(calledTools);
                if (!declaredTools.isEmpty()) {
                    activated.add(skill.skillCode());
                }
            }
        }
        return List.copyOf(activated);
    }

    private ChatCompletionResult chatCompletionWithResolvedCredentials(String modelName,
                                                                       List<Map<String, Object>> messages,
                                                                       List<Map<String, Object>> tools,
                                                                       boolean stripThinkingFromAssistantContent,
                                                                       ModelCallCredentials credentials) {
        if (credentials != null && credentials.hasProviderCredentials()) {
            return aliyunBailianClient.chatCompletionWithCredentials(
                    modelName,
                    messages,
                    tools,
                    stripThinkingFromAssistantContent,
                    credentials.apiBaseUrl(),
                    credentials.apiKey());
        }
        return aliyunBailianClient.chatCompletion(modelName, messages, tools, stripThinkingFromAssistantContent);
    }

    private ModelCallCredentials resolveModelCallCredentials(String orgId, String providerCode) {
        if (providerCode == null || providerCode.isBlank() || "mock".equalsIgnoreCase(providerCode.trim())) {
            return ModelCallCredentials.empty(providerCode);
        }
        try {
            Map<String, String> credentials = modelProviderService.credentialsForProvider(orgId, providerCode.trim());
            if (!Boolean.parseBoolean(credentials.getOrDefault("enabled", "false"))) {
                throw new IllegalArgumentException("当前模型厂商已停用，请联系平台运营启用模型厂商。");
            }
            return new ModelCallCredentials(
                    providerCode.trim(),
                    credentials.get("apiBaseUrl"),
                    credentials.get("apiKey"),
                    Boolean.parseBoolean(credentials.getOrDefault("apiKeyRequired", "true")));
        } catch (IllegalArgumentException ex) {
            log.warn("model provider credentials unavailable: org={} provider={} err={}", orgId, providerCode, ex.getMessage());
            return ModelCallCredentials.empty(providerCode);
        }
    }

    // ── Helpers ──

    private List<Map<String, Object>> buildInitialMessages(String sessionId, String question, List<String> ragContext,
                                                           boolean showThinking,
                                                           ResolvedSkillContext skillContext,
                                                           String orgId, String userId,
                                                           RuntimeContext runtimeContext,
                                                           String routedProvider,
                                                           String modelName,
                                                           BuiltinSkillDocumentService.ResolvedBuiltinSkillDocs builtinDocs) {
        List<Map<String, Object>> messages = new ArrayList<>();
        String baseSystem = showThinking ? AliyunBailianClient.SYSTEM_PROMPT_WITH_THINKING : AliyunBailianClient.SYSTEM_PROMPT;
        BuiltinSkillRuntimeConfigService.ResolvedBuiltinSkillRuntimeConfig runtimeConfig =
                builtinSkillRuntimeConfigService.resolve(skillContext, builtinDocs, orgId, userId);
        String system = skillPromptAssembler.assemble(baseSystem, skillContext, builtinDocs, runtimeConfig);
        system = buildModelIdentityPromptBlock(routedProvider, modelName)
                + "\n---\n\n" + system
                + "\n---\n\n" + buildToolUseBoundaryPromptBlock(sessionId);
        // Prepend user memories if available
        List<UserMemoryEntity> memories = userMemoryService.listForInjection(orgId, userId, skillContext.agentId());
        if (!memories.isEmpty()) {
            system = userMemoryService.buildPromptBlock(memories) + "\n---\n\n" + system;
        }
        Optional<ChatSessionStateEntity> sessionState = chatSessionStateService.get(orgId, sessionId);
        if (sessionState.isPresent()) {
            system = chatSessionStateService.buildPromptBlock(sessionState.get()) + "\n---\n\n" + system;
        }
        system = runtimeContextPromptService.buildPromptBlock(runtimeContext) + "\n---\n\n" + system;
        messages.add(Map.of("role", "system", "content", system));
        messages.addAll(buildRecentHistoryMessages(orgId, sessionId, question));

        StringBuilder userContent = new StringBuilder(question);
        if (!ragContext.isEmpty()) {
            userContent.append("\n\n[参考知识库信息]\n");
            for (int i = 0; i < ragContext.size(); i++) {
                userContent.append(i + 1).append(". ").append(ragContext.get(i)).append("\n");
            }
        }
        messages.add(Map.of("role", "user", "content", userContent.toString()));
        return messages;
    }

    private List<Map<String, Object>> buildRecentHistoryMessages(String orgId, String sessionId, String currentQuestion) {
        List<ChatMessageEntity> latest = chatMessageRepository.findByOrgIdAndSessionIdOrderByCreatedAtDesc(
                orgId, sessionId, PageRequest.of(0, 20));
        if (latest.isEmpty()) {
            return List.of();
        }
        List<ChatMessageEntity> ascending = new ArrayList<>(latest);
        Collections.reverse(ascending);
        List<Map<String, Object>> history = new ArrayList<>();
        boolean skippedCurrentUserTurn = false;
        for (ChatMessageEntity item : ascending) {
            if (!skippedCurrentUserTurn
                    && "user".equals(item.getRoleCode())
                    && currentQuestion.equals(item.getContent())) {
                skippedCurrentUserTurn = true;
                continue;
            }
            if (!"user".equals(item.getRoleCode()) && !"assistant".equals(item.getRoleCode())) {
                continue;
            }
            history.add(Map.of(
                    "role", item.getRoleCode(),
                    "content", item.getContent()
            ));
        }
        return history;
    }

    private void persistUserTurn(String orgId, String userId, String sessionId, String question, String agentId) {
        Optional<ChatSessionEntity> existing = chatSessionRepository.findById(sessionId);
        ChatSessionEntity session = existing.orElseGet(() ->
                new ChatSessionEntity(sessionId, orgId, userId, agentId, clip(question, 48)));
        session.touch(clip(question, 48), agentId);
        chatSessionRepository.save(session);
        chatMessageRepository.save(new ChatMessageEntity(sessionId, orgId, "user", question));
    }

    private void persistUserTurnCommitted(String orgId, String userId, String sessionId, String question, String agentId) {
        tx.executeWithoutResult(s -> persistUserTurn(orgId, userId, sessionId, question, agentId));
        publishSessionUpdated(orgId, userId, sessionId, "user_message");
    }

    private void persistAssistantTurnCommitted(String orgId, String userId, String sessionId,
                                               String answer, String auditAction, String modelName) {
        tx.executeWithoutResult(s -> {
            touchSessionForAssistantReply(sessionId);
            chatMessageRepository.save(new ChatMessageEntity(sessionId, orgId, "assistant", answer));
            auditService.log(orgId, userId, auditAction,
                    "session=" + sessionId + ", model=" + modelName);
        });
        publishSessionUpdated(orgId, userId, sessionId, "session_deleted");
    }

    private void touchSessionForAssistantReply(String sessionId) {
        chatSessionRepository.findById(sessionId).ifPresent(session -> {
            session.touch(session.getTitle(), session.getAgentId());
            chatSessionRepository.save(session);
        });
    }

    public List<Map<String, Object>> sessions(String orgId, String userId) {
        return queryVisibleSessions(orgId, userId).stream()
                .filter(item -> !isInternalWorkbenchSession(item.getId()))
                .map(item -> toSessionSummary(orgId, item))
                .toList();
    }

    public SseEmitter sessionStream(String orgId, String userId) {
        return sessionRealtimeEventService.subscribe(orgId, userId);
    }

    public List<Map<String, String>> sessionMessages(String orgId, String userId, String sessionId) {
        queryVisibleSession(orgId, userId, sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        return chatMessageRepository.findByOrgIdAndSessionIdOrderByCreatedAtAsc(orgId, sessionId).stream()
                .map(item -> Map.of(
                        "role", item.getRoleCode(),
                        "content", item.getContent(),
                        "createdAt", item.getCreatedAt().toString()
                ))
                .toList();
    }

    public Map<String, Object> sessionState(String orgId, String userId, String sessionId) {
        queryVisibleSession(orgId, userId, sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        Optional<ChatSessionStateEntity> state = chatSessionStateService.get(orgId, sessionId);
        if (state.isEmpty()) {
            return Map.of(
                    "sessionId", sessionId,
                    "exists", false
            );
        }
        ChatSessionStateEntity entity = state.get();
        return Map.of(
                "sessionId", sessionId,
                "exists", true,
                "agentId", entity.getAgentId() == null ? "" : entity.getAgentId(),
                "summary", entity.getSummary(),
                "stateJson", entity.getStateJson(),
                "updatedAt", entity.getUpdatedAt().toString()
        );
    }

    public Map<String, Object> deleteSession(String orgId, String userId, String sessionId) {
        ChatSessionEntity session = queryVisibleSession(orgId, userId, sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        tx.executeWithoutResult(s -> {
            chatMessageRepository.deleteByOrgIdAndSessionId(orgId, sessionId);
            chatSessionStateRepository.deleteBySessionIdAndOrgId(sessionId, orgId);
            chatSessionRepository.delete(session);
        });
        publishSessionUpdated(orgId, userId, sessionId, "assistant_message");
        return Map.of(
                "sessionId", sessionId,
                "deleted", true
        );
    }

    private Map<String, Object> toSessionSummary(String orgId, ChatSessionEntity session) {
        ChatMessageEntity lastMessage = chatMessageRepository
                .findFirstByOrgIdAndSessionIdOrderByCreatedAtDesc(orgId, session.getId())
                .orElse(null);
        SessionDescriptor descriptor = describeSession(session);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", session.getId());
        payload.put("agentId", descriptor.agentId());
        payload.put("title", descriptor.title());
        payload.put("participantName", descriptor.participantName());
        payload.put("participantType", descriptor.participantType());
        payload.put("channel", descriptor.channel());
        payload.put("lastMessage", clip(lastMessage == null ? session.getTitle() : lastMessage.getContent(), 120));
        payload.put("updatedAt", session.getUpdatedAt().toString());
        payload.put("unread", 0);
        payload.put("owner", descriptor.owner());
        payload.put("summary", descriptor.summary());
        payload.put("avatarUrl", descriptor.avatarUrl());
        return payload;
    }

    private List<ChatSessionEntity> queryVisibleSessions(String orgId, String userId) {
        List<ChatSessionEntity> visible = new ArrayList<>();
        visible.addAll(chatSessionRepository.findByOrgIdAndUserIdOrderByUpdatedAtDesc(orgId, userId));
        for (ChatSessionEntity item : chatSessionRepository.findByOrgIdOrderByUpdatedAtDesc(orgId)) {
            if (isOrgScopedConversation(item.getId())) {
                visible.add(item);
            }
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<ChatSessionEntity> deduped = new ArrayList<>();
        for (ChatSessionEntity item : visible) {
            if (seen.add(item.getId())) {
                deduped.add(item);
            }
        }
        deduped.sort((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()));
        return deduped;
    }

    private Optional<ChatSessionEntity> queryVisibleSession(String orgId, String userId, String sessionId) {
        if (isOrgScopedConversation(sessionId)) {
            return chatSessionRepository.findByIdAndOrgId(sessionId, orgId);
        }
        return chatSessionRepository.findByIdAndOrgIdAndUserId(sessionId, orgId, userId);
    }

    private SessionDescriptor describeSession(ChatSessionEntity session) {
        String sessionId = session.getId();
        String agentId = session.getAgentId() == null || session.getAgentId().isBlank()
                ? "cici-system"
                : session.getAgentId();
        if (sessionId.startsWith("feishu:")) {
            String[] parts = sessionId.split(":", 3);
            String chatId = parts.length >= 3 ? parts[2] : sessionId;
            FeishuBotBindingEntity binding = feishuBotBindingRepository
                    .findFirstByOrgIdAndChatIdAndStatusOrderByUpdatedAtDesc(
                            session.getOrgId(), chatId, FeishuBotBindingEntity.STATUS_ACTIVE)
                    .orElse(null);
            String participantName = binding == null || binding.getDisplayName() == null || binding.getDisplayName().isBlank()
                    ? "飞书会话 " + abbreviateId(chatId)
                    : binding.getDisplayName();
            String avatarUrl = binding == null ? "" : Optional.ofNullable(binding.getAvatarUrl()).orElse("");
            return new SessionDescriptor(
                    agentId,
                    "飞书 / " + participantName,
                    participantName,
                    "external",
                    "feishu",
                    "CiCi",
                    "来自飞书单聊的真实会话，已接入系统会话列表。",
                    avatarUrl
            );
        }
        if (sessionId.startsWith("wechat:")) {
            String participantName = "企微会话 " + abbreviateId(sessionId);
            return new SessionDescriptor(
                    agentId,
                    "企微 / " + participantName,
                    participantName,
                    "external",
                    "wechat",
                    "CiCi",
                    "来自企微渠道的真实会话。",
                    ""
            );
        }
        if (sessionId.startsWith("wecom-kf:")) {
            String participantName = "微信客服 " + abbreviateId(sessionId);
            return new SessionDescriptor(
                    agentId,
                    "微信客服 / " + participantName,
                    participantName,
                    "external",
                    "wechat_kf",
                    "CiCi",
                    "来自企业微信「微信客服」的客户会话。",
                    ""
            );
        }
        if (sessionId.startsWith("dingtalk:")) {
            String participantName = "钉钉会话 " + abbreviateId(sessionId);
            return new SessionDescriptor(
                    agentId,
                    "钉钉 / " + participantName,
                    participantName,
                    "employee",
                    "dingtalk",
                    "CiCi",
                    "来自钉钉渠道的真实会话。",
                    ""
            );
        }
        if (sessionId.startsWith("web:") || sessionId.startsWith("webchat:")) {
            String participantName = "WebChat " + abbreviateId(sessionId);
            return new SessionDescriptor(
                    agentId,
                    "Web / " + participantName,
                    participantName,
                    "external",
                    "web",
                    "CiCi",
                    "来自 WebChat 渠道的真实会话。",
                    ""
            );
        }
        if (sessionId.startsWith("api:")) {
            String participantName = "API 会话 " + abbreviateId(sessionId);
            return new SessionDescriptor(
                    agentId,
                    "Open API / " + participantName,
                    participantName,
                    "external",
                    "api",
                    "CiCi",
                    "来自 Agent Open API 的外部系统会话。",
                    ""
            );
        }
        String participantName = "会话 " + abbreviateId(sessionId);
        return new SessionDescriptor(
                agentId,
                participantName,
                participantName,
                "external",
                "web",
                "CiCi",
                "来自统一聊天持久层的真实会话。",
                ""
        );
    }

    private boolean isInternalWorkbenchSession(String sessionId) {
        return sessionId.startsWith("assistant-ui-");
    }

    private boolean isOrgScopedConversation(String sessionId) {
        return sessionId.startsWith("feishu:")
                || sessionId.startsWith("wechat:")
                || sessionId.startsWith("wecom-kf:")
                || sessionId.startsWith("dingtalk:")
                || sessionId.startsWith("api:")
                || sessionId.startsWith("web:")
                || sessionId.startsWith("webchat:");
    }

    private void publishSessionUpdated(String orgId, String userId, String sessionId, String trigger) {
        sessionRealtimeEventService.publishSessionUpdated(
                orgId,
                userId,
                sessionId,
                isOrgScopedConversation(sessionId),
                trigger
        );
    }

    private String abbreviateId(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unknown";
        }
        String cleaned = raw.trim();
        if (cleaned.length() <= 10) {
            return cleaned;
        }
        return cleaned.substring(0, 4) + "…" + cleaned.substring(cleaned.length() - 4);
    }

    private record SessionDescriptor(
            String agentId,
            String title,
            String participantName,
            String participantType,
            String channel,
            String owner,
            String summary,
            String avatarUrl
    ) {
    }

    private static void safeSendDelta(SseEmitter emitter, String text) {
        try {
            emitter.send(SseEmitter.event().name("delta").data(Map.of("text", text)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void safeSendDeltaInChunks(SseEmitter emitter, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        final int chunkSize = 18;
        final long pauseMs = 18L;
        for (int i = 0; i < text.length(); i += chunkSize) {
            String chunk = text.substring(i, Math.min(i + chunkSize, text.length()));
            safeSendDelta(emitter, chunk);
            if (i + chunkSize < text.length()) {
                try {
                    Thread.sleep(pauseMs);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private static void safeSendToolResult(SseEmitter emitter, String toolName, String payload) {
        try {
            emitter.send(SseEmitter.event().name("tool_result").data(Map.of(
                    "toolName", toolName,
                    "payload", payload == null ? "" : payload
            )));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void safeSendToolCall(SseEmitter emitter, String toolName) {
        try {
            emitter.send(SseEmitter.event().name("tool_call").data(Map.of("toolName", toolName)));
        } catch (IOException ignored) {}
    }

    private static void safeSendPhase(SseEmitter emitter, String phase) {
        safeSendPhase(emitter, phase, null);
    }

    private static void safeSendPhase(SseEmitter emitter, String phase, String modelName) {
        safeSendPhase(emitter, phase, modelName, Map.of());
    }

    private static void safeSendPhase(SseEmitter emitter, String phase, String modelName, Map<String, Object> extra) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("phase", phase);
            if (modelName != null && !modelName.isBlank()) {
                payload.put("modelName", modelName);
            }
            if (extra != null && !extra.isEmpty()) {
                payload.putAll(extra);
            }
            emitter.send(SseEmitter.event().name("phase").data(payload));
        } catch (IOException ignored) {}
    }

    private static Map<String, Object> ragPhasePayload(RagService.RetrievalResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("knowledgeBaseIds", result.knowledgeBases().stream().map(RagService.RetrievedKnowledgeBase::id).toList());
        payload.put("knowledgeBaseNames", result.knowledgeBases().stream().map(RagService.RetrievedKnowledgeBase::name).toList());
        payload.put("contextCount", result.context().size());
        payload.put("sources", result.sources().stream().map(RagService.RetrievedSource::toPayload).toList());
        payload.put("metadataFilters", result.metadataFilters());
        payload.put("permissionFilteredCount", result.permissionFilteredCount());
        payload.put("elapsedMs", result.timingsMs().getOrDefault("total", 0L));
        payload.put("timingsMs", result.timingsMs());
        payload.put("fallbackUsed", result.fallbackUsed());
        return payload;
    }

    private static Map<String, Object> ragRetrievalPayload(RagService.RetrievalResult result) {
        return ragPhasePayload(result);
    }

    private static boolean isPendingApprovalsTool(String toolName) {
        return "get_pending_approvals".equalsIgnoreCase(toolName);
    }

    static List<Map<String, Object>> withToolPlanningStopPrompt(List<Map<String, Object>> messages) {
        List<Map<String, Object>> out = new ArrayList<>(messages == null ? List.of() : messages);
        out.add(Map.of(
                "role", "system",
                "content", buildToolPlanningStopPrompt()
        ));
        return out;
    }

    static String buildToolPlanningStopPrompt() {
        return """
                [工具规划收口判断]
                - 你现在只判断是否必须继续调用工具，不生成给用户看的最终回答。
                - 如果已有 tool messages 足以进入最终回答，不要输出解释，只回复 READY_TO_FINALIZE。
                - 只有确实缺少必要事实、字段结构、下一页游标或必须执行的后续动作时，才继续发起一个最小必要工具调用。
                - 如果用户要求查看邮件正文/内容/详情，而现有 email_search 结果只有 messageId，没有正文，必须继续调用 email_get_message。
                - 不要为了润色、总结、排序或格式化而继续请求工具。
                """.trim();
    }

    static boolean shouldSkipToolPlanningStop(String question,
                                              List<ToolCallInfo> plannedToolCalls,
                                              List<Map<String, Object>> messages) {
        if (plannedToolCalls == null || plannedToolCalls.size() != 1) {
            return false;
        }
        String toolName = ToolNameNormalizer.canonicalize(plannedToolCalls.get(0).name());
        if (!isReadOnlyLookupTool(toolName) || !isLookupOnlyUserIntent(question)) {
            return false;
        }
        if (isMetadataLookupTool(toolName) && !isMetadataLookupIntent(question)) {
            return false;
        }
        List<String> toolResults = toolResultContents(messages);
        if (toolResults.size() != 1) {
            return false;
        }
        String result = toolResults.get(0);
        if ("email_search".equals(toolName)
                && isEmailBodyReadIntent(question)
                && extractSingleEmailSearchMessageId(result).isPresent()) {
            return false;
        }
        return !looksFailedToolResult(result) && !toolResultRequiresMoreToolWork(result);
    }

    static boolean isEmailBodyReadIntent(String question) {
        String text = question == null ? "" : question.trim().toLowerCase(Locale.ROOT);
        if (text.isBlank()) {
            return false;
        }
        boolean mentionsMail = containsAny(text, List.of("邮件", "这封", "email", "mail", "message"));
        boolean asksBody = containsAny(text, List.of(
                "正文", "内容", "详情", "明细", "原文", "全文", "展开", "打开", "读", "读取",
                "查看", "看下", "看一下", "看看",
                "body", "content", "detail", "details", "read", "open", "show"));
        return mentionsMail && asksBody;
    }

    static boolean isEmailBodyContinuationConfirmation(String question) {
        String text = question == null ? "" : question.trim().toLowerCase(Locale.ROOT);
        if (text.isBlank()) {
            return false;
        }
        String compact = text.replaceAll("\\s+", "");
        if (isEmailBodyReadIntent(text)) {
            return true;
        }
        return containsAny(compact, List.of(
                "是", "是的", "对", "对的", "可以", "好的", "好", "嗯", "嗯嗯", "继续",
                "展开", "打开", "读取", "读一下", "看正文", "看内容", "查看正文",
                "yes", "y", "ok", "okay", "continue", "read", "open", "show"));
    }

    static Optional<String> extractSingleEmailSearchMessageId(String result) {
        String text = result == null ? "" : result;
        if (text.isBlank()) {
            return Optional.empty();
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        Matcher matcher = EMAIL_SEARCH_MESSAGE_ID_PATTERN.matcher(text);
        while (matcher.find()) {
            String id = stripTrailingEmailIdPunctuation(matcher.group(1));
            if (!id.isBlank()) {
                ids.add(id);
            }
        }
        if (ids.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(ids.iterator().next());
    }

    private static String stripTrailingEmailIdPunctuation(String raw) {
        String value = raw == null ? "" : raw.trim();
        while (!value.isBlank() && "，,；;。)）]】".indexOf(value.charAt(value.length() - 1)) >= 0) {
            value = value.substring(0, value.length() - 1).trim();
        }
        return value;
    }

    private static boolean isReadOnlyLookupTool(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return false;
        }
        String normalized = toolName.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (containsAny(lower, List.of(
                "send", "reply", "create", "update", "delete", "remove", "upsert", "save", "write",
                "approve", "reject", "submit", "publish", "revoke", "rotate", "remember", "forget"))) {
            return false;
        }
        return lower.startsWith("get_")
                || lower.startsWith("list_")
                || lower.startsWith("search_")
                || lower.startsWith("query_")
                || lower.startsWith("fetch_")
                || lower.startsWith("retrieve_")
                || lower.startsWith("find_")
                || lower.startsWith("lookup_")
                || lower.startsWith("cloudcc_get")
                || "cloudcc_pagequery".equals(lower)
                || lower.startsWith("tavily_")
                || "email_list_inbox".equals(lower)
                || "email_search".equals(lower)
                || "email_get_message".equals(lower);
    }

    private static boolean isMetadataLookupTool(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return false;
        }
        String lower = toolName.trim().toLowerCase(Locale.ROOT);
        return lower.contains("objectfields")
                || lower.contains("object_fields")
                || lower.contains("standardobjects")
                || lower.contains("standard_objects")
                || lower.contains("customobjects")
                || lower.contains("custom_objects")
                || lower.contains("object_list")
                || "get_objects".equals(lower)
                || "list_objects".equals(lower);
    }

    private static boolean isMetadataLookupIntent(String question) {
        String text = question == null ? "" : question.trim().toLowerCase(Locale.ROOT);
        return containsAny(text, List.of(
                "字段", "字段列表", "对象字段", "对象列表", "标准对象", "自定义对象", "对象 api", "对象api",
                "schema", "fields", "object list", "objects"));
    }

    private static boolean isLookupOnlyUserIntent(String question) {
        String text = question == null ? "" : question.trim().toLowerCase(Locale.ROOT);
        if (text.isBlank()) {
            return false;
        }
        if (containsAny(text, List.of(
                "发送", "发给", "回复", "新建", "创建", "新增", "更新", "修改", "删除", "移除",
                "审批", "同意", "拒绝", "提交", "发布", "撤销", "绑定", "解绑",
                "send", "reply", "create", "update", "delete", "approve", "reject", "submit", "publish"))) {
            return false;
        }
        return containsAny(text, List.of(
                "查询", "查一下", "查下", "看下", "看一下", "获取", "拉取", "列出", "列表",
                "找", "搜索", "检索", "统计", "汇总", "总结", "分析", "明细", "台账",
                "客户", "线索", "商机", "联系人", "订单", "邮件", "日程", "待办",
                "search", "query", "lookup", "find", "list", "summarize", "analyze"));
    }

    private static boolean toolResultRequiresMoreToolWork(String result) {
        String text = result == null ? "" : result.trim().toLowerCase(Locale.ROOT);
        if (text.isBlank()) {
            return true;
        }
        return containsAny(text, List.of(
                "\"hasmore\":true",
                "\"has_more\":true",
                "\"needmoreparams\":true",
                "\"need_more_params\":true",
                "\"requiresfollowup\":true",
                "\"requires_followup\":true",
                "\"nextpagetoken\"",
                "\"next_page_token\"",
                "缺少必需参数",
                "需要补充参数",
                "参数问题",
                "请先调用",
                "必须先调用"));
    }

    private static List<String> toolResultContents(List<Map<String, Object>> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<String> results = new ArrayList<>();
        for (Map<String, Object> msg : messages) {
            if (!"tool".equals(String.valueOf(msg.get("role")))) {
                continue;
            }
            Object content = msg.get("content");
            if (content instanceof String text && !text.isBlank()) {
                results.add(text);
            }
        }
        return results;
    }

    private static String questionFromMessages(List<Map<String, Object>> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, Object> msg = messages.get(i);
            if (!"user".equals(String.valueOf(msg.get("role")))) {
                continue;
            }
            Object content = msg.get("content");
            return content == null ? "" : String.valueOf(content);
        }
        return "";
    }

    static String appendToolResultFallbackIfDeferred(String finalText, List<Map<String, Object>> messages) {
        if (!hasToolMessages(messages) || !finalAnswerDefersToolResult(finalText)) {
            return finalText;
        }
        String fallback = buildToolResultFallbackMessage(messages);
        if (fallback == null || fallback.isBlank()) {
            return finalText;
        }
        String current = finalText == null ? "" : finalText;
        String normalizedCurrent = current.replaceAll("\\s+", "");
        String normalizedFallback = fallback.replaceAll("\\s+", "");
        if (!normalizedCurrent.isBlank() && normalizedCurrent.contains(normalizedFallback)) {
            return current;
        }
        return current
                + "\n\n---\n已返回结果摘要：\n\n"
                + fallback;
    }

    static boolean finalAnswerDefersToolResult(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String normalized = content.replaceAll("\\s+", "");
        Matcher optionalOffer = OPTIONAL_DRILLDOWN_OFFER_PATTERN.matcher(normalized);
        if (optionalOffer.find()) {
            String concretePrefix = normalized.substring(0, optionalOffer.start());
            if (concretePrefix.length() >= 16 && containsAny(concretePrefix, List.of(
                    "结论", "冠军", "排行", "Top", "TOP", "已完成", "结果如下",
                    "正文如下", "扫描", "计入", "订单", "客户", "销售额", "销量"))) {
                normalized = concretePrefix;
            }
        }
        return DEFERRED_TOOL_FINAL_PATTERN.matcher(normalized).find();
    }

    static String buildToolLimitFinalAnswerPrompt(int maxToolRounds) {
        return """
                [工具轮次收口]
                - 本轮已经完成 %d 轮工具调用，不允许再请求任何工具。
                - 你现在只能基于上方已有 tool messages 给用户一段最终可读回复。
                - 如果工具结果没有查到匹配数据，要明确说明已尝试的查询条件和未命中的事实，不要继续承诺“稍后再查”。
                - 如果工具结果只支持部分回答，要分成“已确认”和“仍需确认”。
                - 回复保持简洁，适合外部聊天工具直接发送。
                """.formatted(Math.max(1, maxToolRounds)).trim();
    }

    static String buildToolLimitReachedFallbackMessage(List<Map<String, Object>> messages, int maxToolRounds) {
        List<ToolResultSummary> summaries = collectToolResultSummaries(messages);
        if (summaries.isEmpty()) {
            return "本轮已经达到 " + Math.max(1, maxToolRounds)
                    + " 次工具查询上限，但没有拿到可展示的工具结果。请确认查询对象、人员名称或时间范围后再试。";
        }
        StringBuilder text = new StringBuilder();
        text.append("本轮已经完成 ").append(summaries.size())
                .append(" 次工具查询，但还没有形成可靠的最终结论。为了避免继续无效调用，我先把已返回结果整理如下：");
        int limit = Math.min(6, summaries.size());
        for (int i = 0; i < limit; i++) {
            ToolResultSummary item = summaries.get(i);
            text.append("\n").append(i + 1).append(". ")
                    .append(item.summary());
        }
        if (summaries.size() > limit) {
            text.append("\n其余 ").append(summaries.size() - limit).append(" 次工具结果已省略。");
        }
        text.append("\n\n下一步可以确认对象名称、人员姓名、月份/季度字段或筛选条件后重新查询。");
        return text.toString();
    }

    private static boolean isUsableToolLimitAnswer(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String normalized = content.trim();
        return !normalized.startsWith("Model call failed:")
                && !normalized.startsWith("Aliyun API key is not configured")
                && !normalized.startsWith("Empty response.")
                && !normalized.startsWith("No choices in response.")
                && !normalized.contains("系统保护上限")
                && !normalized.contains("暂时无法继续处理");
    }

    static String buildToolResultFallbackMessage(List<Map<String, Object>> messages) {
        Optional<String> crmToolResult = toolResultForName(
                messages, CrmProductSalesAnalysisToolService.TOOL_NAME);
        if (crmToolResult.isPresent()) {
            return CRM_TOOL_RESULT_FALLBACK_FORMATTER.formatJson(crmToolResult.get());
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, Object> msg = messages.get(i);
            if (!"tool".equals(String.valueOf(msg.get("role")))) {
                continue;
            }
            Object raw = msg.get("content");
            if (!(raw instanceof String toolContent)) {
                continue;
            }
            String normalized = toolContent.trim();
            if (normalized.isEmpty()) {
                continue;
            }
            String structured = buildStructuredToolResultFallbackMessage(normalized);
            if (!structured.isBlank()) {
                return structured;
            }
            return "工具已返回结果，但当前结构暂不支持安全展示。为保护业务数据，未展示原始结果；"
                    + "请调整筛选条件后重试。";
        }
        return "本次工具调用已完成，但没有可安全展示的数据摘要。请调整筛选条件后重试。";
    }

    private static Optional<String> toolResultForName(List<Map<String, Object>> messages, String expectedToolName) {
        if (messages == null || messages.isEmpty() || expectedToolName == null || expectedToolName.isBlank()) {
            return Optional.empty();
        }
        Map<String, String> toolNamesByCallId = new LinkedHashMap<>();
        for (Map<String, Object> message : messages) {
            Object rawToolCalls = message.get("tool_calls");
            if (!(rawToolCalls instanceof List<?> toolCalls)) {
                continue;
            }
            for (Object rawToolCall : toolCalls) {
                if (!(rawToolCall instanceof Map<?, ?> toolCall)) {
                    continue;
                }
                Object functionValue = toolCall.get("function");
                if (!(functionValue instanceof Map<?, ?> function)) {
                    continue;
                }
                Object callIdValue = toolCall.get("id");
                Object toolNameValue = function.get("name");
                String callId = callIdValue == null ? "" : String.valueOf(callIdValue);
                String toolName = toolNameValue == null ? "" : String.valueOf(toolNameValue);
                if (!callId.isBlank() && !toolName.isBlank()) {
                    toolNamesByCallId.put(callId, toolName);
                }
            }
        }
        for (int index = messages.size() - 1; index >= 0; index--) {
            Map<String, Object> message = messages.get(index);
            if (!"tool".equals(String.valueOf(message.get("role")))) {
                continue;
            }
            String callId = String.valueOf(message.getOrDefault("tool_call_id", ""));
            if (!expectedToolName.equalsIgnoreCase(toolNamesByCallId.getOrDefault(callId, ""))) {
                continue;
            }
            Object content = message.get("content");
            if (content instanceof String text && !text.isBlank()) {
                return Optional.of(text);
            }
        }
        return Optional.empty();
    }

    private static List<ToolResultSummary> collectToolResultSummaries(List<Map<String, Object>> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        Map<String, ToolCallSummary> toolCallsById = new LinkedHashMap<>();
        for (Map<String, Object> msg : messages) {
            Object rawToolCalls = msg.get("tool_calls");
            if (!(rawToolCalls instanceof List<?> toolCalls)) {
                continue;
            }
            for (Object rawCall : toolCalls) {
                if (!(rawCall instanceof Map<?, ?> call)) {
                    continue;
                }
                Object rawId = call.get("id");
                String id = rawId == null ? "" : String.valueOf(rawId);
                Object functionObj = call.get("function");
                if (id.isBlank() || !(functionObj instanceof Map<?, ?> function)) {
                    continue;
                }
                Object rawName = function.get("name");
                Object rawArguments = function.get("arguments");
                String name = rawName == null ? "工具" : String.valueOf(rawName);
                String arguments = rawArguments == null ? "" : String.valueOf(rawArguments);
                toolCallsById.put(id, new ToolCallSummary(name, arguments));
            }
        }
        List<ToolResultSummary> summaries = new ArrayList<>();
        for (Map<String, Object> msg : messages) {
            if (!"tool".equals(String.valueOf(msg.get("role")))) {
                continue;
            }
            Object raw = msg.get("content");
            if (!(raw instanceof String content) || content.isBlank()) {
                continue;
            }
            String toolCallId = String.valueOf(msg.getOrDefault("tool_call_id", ""));
            ToolCallSummary call = toolCallsById.get(toolCallId);
            summaries.add(new ToolResultSummary(
                    call == null || call.name().isBlank() ? "工具" : call.name(),
                    call == null ? "" : call.arguments(),
                    summarizeToolContentForLimit(content)));
        }
        return summaries;
    }

    private static String summarizeToolContentForLimit(String toolContent) {
        String normalized = toolContent == null ? "" : toolContent.trim();
        if (normalized.isBlank()) {
            return "工具没有返回可展示内容。";
        }
        Matcher dataCount = TOOL_DATA_COUNT_PATTERN.matcher(normalized);
        if (dataCount.find()) {
            int returned = parsePositiveInt(dataCount.group(1));
            int total = parsePositiveInt(dataCount.group(2));
            if (returned == 0) {
                return "查询完成，但没有返回匹配记录（返回 0 条，总计 " + total + " 条）。";
            }
            return "查询返回 " + returned + " 条记录，总计 " + total + " 条。";
        }
        Matcher fieldCount = TOOL_FIELD_COUNT_PATTERN.matcher(normalized);
        if (fieldCount.find()) {
            return "读取到对象字段结构：标准字段 " + fieldCount.group(1)
                    + " 条、自定义字段 " + fieldCount.group(2) + " 条。";
        }
        Matcher objectCount = TOOL_OBJECT_COUNT_PATTERN.matcher(normalized);
        if (objectCount.find()) {
            return "读取到对象列表：标准对象 " + objectCount.group(1)
                    + " 条、自定义对象 " + objectCount.group(2)
                    + " 条，总计 " + objectCount.group(3) + " 条。";
        }
        String structured = buildStructuredToolResultFallbackMessage(normalized)
                .replaceAll("\\s+", " ")
                .trim();
        if (!structured.isBlank()) {
            return clipStatic(structured, 360);
        }
        if (normalized.startsWith("{") || normalized.startsWith("[")) {
            return "工具返回了暂不支持安全展示的结构化结果，原始字段已隐藏。";
        }
        return PROTECTED_TOOL_DISPLAY_FALLBACK;
    }

    private static String buildStructuredToolResultFallbackMessage(String toolContent) {
        try {
            JsonNode root = parseToolContentObject(toolContent);
            if (!root.isObject()) {
                return "";
            }
            String answer = nodeText(root, "answer");
            if (!answer.isBlank()) {
                return PROTECTED_TOOL_DISPLAY_FALLBACK;
            }
            boolean failed = booleanFieldIsFalse(root, "success")
                    || booleanFieldIsFalse(root, "ok")
                    || booleanFieldIsFalse(root, "result");
            if (failed) {
                return "工具调用未完成。请检查参数后重试。";
            }
            JsonNode results = root.path("results");
            if (results.isArray()) {
                return summarizeResultArray(results);
            }
            JsonNode data = root.path("data");
            if (data.isArray()) {
                return summarizeBusinessDataArray(data);
            }
            String message = firstNonBlank(nodeText(root, "message"), nodeText(root, "summary"));
            if (!message.isBlank()) {
                return PROTECTED_TOOL_DISPLAY_FALLBACK;
            }
            return "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private static JsonNode parseToolContentObject(String toolContent) throws IOException {
        try {
            return TOOL_RESULT_OBJECT_MAPPER.readTree(toolContent);
        } catch (Exception ignored) {
            int start = toolContent == null ? -1 : toolContent.indexOf('{');
            int end = toolContent == null ? -1 : toolContent.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return TOOL_RESULT_OBJECT_MAPPER.readTree(toolContent.substring(start, end + 1));
            }
            throw new IOException(ignored);
        }
    }

    private static String summarizeResultArray(JsonNode results) {
        int count = results.size();
        if (count == 0) {
            return "工具查询已完成，但没有返回匹配结果。你可以换一个行业、地区或筛选条件再试。";
        }
        return "工具已返回 " + count + " 条结果。为保护业务数据，未验证的详细字段已隐藏；"
                + "请调整筛选条件后重试。";
    }

    private static String summarizeBusinessDataArray(JsonNode data) {
        int count = data.size();
        if (count == 0) {
            return "工具查询已完成，但没有返回匹配业务记录。你可以确认姓名、月份、对象或筛选字段后再试。";
        }
        return "工具已返回 " + count + " 条业务记录。为保护业务数据，未验证的详细字段已隐藏；"
                + "请调整筛选条件后重试。";
    }

    private static boolean booleanFieldIsFalse(JsonNode root, String field) {
        JsonNode node = root.path(field);
        return node.isBoolean() && !node.asBoolean();
    }

    private static String nodeText(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return "";
        }
        String value = node.isTextual() ? node.asText() : node.toString();
        return value == null ? "" : value.trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static int parsePositiveInt(String value) {
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    public record EvaluationDryRunResult(
            String output,
            List<Map<String, Object>> toolCalls,
            List<String> ragSources,
            List<String> trace,
            Map<String, Object> context
    ) {
    }

    private record ToolCallSummary(String name, String arguments) {
    }

    private record ToolResultSummary(String toolName, String arguments, String summary) {
    }

    private static String clipStatic(String value, int max) {
        if (value == null || value.length() <= max) {
            return value == null ? "" : value;
        }
        return value.substring(0, max) + "...";
    }

    private static boolean hasToolMessages(List<Map<String, Object>> messages) {
        return messages.stream().anyMatch(msg -> "tool".equals(String.valueOf(msg.get("role"))));
    }

    static String buildModelIdentityPromptBlock(String routedProvider, String modelName) {
        String provider = routedProvider == null || routedProvider.isBlank() ? "unknown" : routedProvider.trim();
        String model = modelName == null || modelName.isBlank() ? "unknown" : modelName.trim();
        String providerLabel = switch (provider) {
            case "aliyun-bailian" -> "阿里云百炼 (aliyun-bailian)";
            case "anthropic" -> "Anthropic";
            case "openai" -> "OpenAI";
            case "deepseek" -> "深度求索 (deepseek)";
            case "ollama-local" -> "本地 Ollama (ollama-local)";
            case "lmstudio-local" -> "本地 LM Studio (lmstudio-local)";
            default -> provider;
        };
        return """
                [运行模型上下文]
                - 当前服务端模型供应商：%s
                - 当前服务端模型名称：%s
                - 当用户询问“你现在调用的是什么模型/供应商/是不是 Claude/OpenAI”等问题时，只能依据以上两项回答。
                - 不得自称 Claude、Anthropic、OpenAI、GPT、Gemini 等第三方模型，除非当前供应商或模型名称明确如此。
                - 不要透露 API key、密钥、内部路由实现细节或不可见配置。
                """.formatted(providerLabel, model).trim();
    }

    static String buildToolUseBoundaryPromptBlock(String sessionId) {
        boolean externalFeishu = sessionId != null && sessionId.startsWith("feishu:");
        boolean wecomKf = isWecomKfSession(sessionId);
        String channelRule = externalFeishu
                ? "\n- 当前会话来自飞书渠道，默认按日常对话处理；除非用户明确提出业务数据查询或操作，不要触发工具。"
                : "";
        if (wecomKf) {
            channelRule = "\n- 当前会话来自企业微信「微信客服」，客户是外部客户；当前阶段只做知识库售后问答，不查询或操作 CRM、订单、客户档案、工单、物流等业务系统。"
                    + "\n- 如果客户问题需要实时订单、物流、保修状态或客户档案，只能基于知识库说明处理原则，并建议转人工核实，不得声称已查询实时业务数据。";
        }
        return """
                [工具调用边界 - 运行时优先策略]
                - 对寒暄、闲聊、祝福、角色扮演、才艺表演、轻量创作、常识性解释等不需要企业实时数据或外部动作的问题，必须直接用文本回答，不要调用任何工具。
                - 只有当用户明确要求查询、创建、更新企业业务记录，读取邮件、审批、客户、日程等实时数据，搜索外部实时信息，或问题依赖已绑定业务系统事实时，才调用相应工具。
                - 知识库只在用户显式选择知识库，或问题明确指向企业文档、制度、流程、规则、手册、产品功能、产品能力、产品配置、公司介绍、操作指南、口径、依据时使用；不要把每句对话都当成知识库问答。
                - 知识库检索由服务端预检索或正式 function calling 完成；不要输出 `<search_knowledge ... />`、`<rag-search ... />` 等 XML/伪工具标签作为最终回答。
                - 如果不确定是否需要工具，先用一句话澄清或直接给出通用答复，不要为了试探而调用工具。%s
                """.formatted(channelRule).trim();
    }

    static String buildToolFinalAnswerGuardPrompt() {
        return """
                [工具结果后的最终回答约束]
                - 你现在只能输出本轮最终回复；不要承诺“稍后/继续/让我重新查询”，除非在最终回复前已经实际发起了新的工具调用并拿到结果。
                - 如果工具结果包含“❌”、success=false、ok=false、查询失败、调用失败、缺少必需参数或参数问题，必须明确说明哪一步没有完成，以及需要用户补充或确认什么。
                - 如果已有工具结果足以回答，就给出可验证的结果摘要；如果只能部分回答，明确标注“已完成部分”和“未完成部分”。
                - 不要把工具参数错误描述为“查询成功”，也不要让用户误以为系统仍会自动继续回复。
                """.trim();
    }

    private static String resolveModelName(String agentModel, String routedProvider, String routedModelName) {
        return routedModelName;
    }

    private String clip(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static int resolveMaxToolRounds(Integer configuredMaxToolCalls) {
        if (configuredMaxToolCalls == null) {
            return MAX_TOOL_ROUNDS;
        }
        return Math.max(MIN_TOOL_ROUNDS, Math.min(MAX_POLICY_TOOL_ROUNDS, configuredMaxToolCalls));
    }

    static boolean shouldUseKnowledgeRetrieval(String question,
                                               List<String> effectiveKnowledgeBaseIds,
                                               List<String> requestedKnowledgeBaseIds) {
        return shouldUseKnowledgeRetrieval(question, effectiveKnowledgeBaseIds, requestedKnowledgeBaseIds, null);
    }

    static boolean shouldUseKnowledgeRetrieval(String question,
                                               List<String> effectiveKnowledgeBaseIds,
                                               List<String> requestedKnowledgeBaseIds,
                                               String sessionId) {
        return KnowledgeRetrievalRouter.decide(
                question, effectiveKnowledgeBaseIds, requestedKnowledgeBaseIds, sessionId).shouldRetrieve();
    }

    private static boolean isWecomKfSession(String sessionId) {
        return sessionId != null && sessionId.startsWith("wecom-kf:");
    }

    private static List<String> normalizeKnowledgeBaseIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static boolean containsAny(String text, List<String> needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static RagService.RetrievalResult emptyRagRetrievalResult() {
        return new RagService.RetrievalResult(List.of(), List.of(), List.of(), Map.of("total", 0L), false, Map.of(), 0);
    }

    private record ModelCallCredentials(String providerCode, String apiBaseUrl, String apiKey, boolean apiKeyRequired) {
        static ModelCallCredentials empty(String providerCode) {
            return new ModelCallCredentials(providerCode == null ? "" : providerCode, "", "", true);
        }

        boolean hasProviderCredentials() {
            if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
                return false;
            }
            return !apiKeyRequired || (apiKey != null && !apiKey.isBlank());
        }
    }
}
