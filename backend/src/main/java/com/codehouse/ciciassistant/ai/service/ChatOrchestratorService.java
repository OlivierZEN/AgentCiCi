package com.codehouse.ciciassistant.ai.service;

import com.codehouse.ciciassistant.agent.domain.AgentPermission;
import com.codehouse.ciciassistant.agent.service.AgentAccessControlService;
import com.codehouse.ciciassistant.agent.service.AgentWorkflowExecutionLogService;
import com.codehouse.ciciassistant.agent.service.AgentWorkflowRuntimeService;
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
import com.codehouse.ciciassistant.memory.domain.UserMemoryEntity;
import com.codehouse.ciciassistant.memory.service.UserMemoryService;
import com.codehouse.ciciassistant.model.service.ModelProviderService;
import com.codehouse.ciciassistant.ops.service.AuditService;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private static final ObjectMapper TOOL_RESULT_OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern DEFERRED_TOOL_FINAL_PATTERN = Pattern.compile(
            "(后续|接下来|随后|稍后).{0,18}(继续|重新|再|将|会|我)?.{0,18}(查询|检索|调用|获取|处理|尝试|抽取|整理|分析|生成|补充|展示|展现|输出)"
                    + "|(让我|我来|我会|我再|将).{0,12}(继续|重新|再)?.{0,12}(查询|检索|调用|获取|处理|尝试|抽取|整理|分析|生成|补充|展示|展现|输出)"
                    + "|(继续|重新|再).{0,8}(查询|检索|调用|获取|处理|尝试|抽取|整理|分析|生成|补充|展示|展现|输出)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern TOOL_DATA_COUNT_PATTERN = Pattern.compile("返回\\s*(\\d+)\\s*条[，,]\\s*总计\\s*(\\d+)\\s*条");
    private static final Pattern TOOL_FIELD_COUNT_PATTERN = Pattern.compile("对象字段列表（标准字段\\s*(\\d+)\\s*条[，,]\\s*自定义字段\\s*(\\d+)\\s*条）");
    private static final Pattern TOOL_OBJECT_COUNT_PATTERN = Pattern.compile("所有对象列表（标准对象:\\s*(\\d+)\\s*条[，,]\\s*自定义对象:\\s*(\\d+)\\s*条[，,]\\s*总计:\\s*(\\d+)\\s*条）");

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
    private final AgentWorkflowExecutionLogService agentWorkflowExecutionLogService;
    private final AgentRunTraceService agentRunTraceService;
    private final AgentAccessControlService agentAccessControlService;
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
                                   AgentWorkflowExecutionLogService agentWorkflowExecutionLogService,
                                   AgentRunTraceService agentRunTraceService,
                                   AgentAccessControlService agentAccessControlService,
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
        this.agentWorkflowExecutionLogService = agentWorkflowExecutionLogService;
        this.agentRunTraceService = agentRunTraceService;
        this.agentAccessControlService = agentAccessControlService;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public Map<String, Object> chat(String orgId, String userId, String sessionId,
                                     String question, List<String> kbIds, String requestedAgentId,
                                     String activeSkillCode) {
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
                skillTraceMetadata(skillContext, List.of(), builtinDocs)));
        Instant userPersistStartedAt = Instant.now();
        persistUserTurnCommitted(orgId, userId, sessionId, question, skillContext.agentId());
        stageTraces.add(stageTrace("USER_MESSAGE", "用户输入", "SUCCESS", userPersistStartedAt, Instant.now(),
                clipForTrace(question, 220), Map.of("sessionId", sessionId)));

        Map<String, String> routedModel = modelRouterService.route(orgId, "chat");
        String modelName = resolveModelName(skillContext.agentModel(), routedModel.get("provider"), routedModel.get("modelName"));
        ModelCallCredentials modelCredentials = resolveModelCallCredentials(orgId, routedModel.get("provider"));
        boolean showThinking = chatThinkingConfigService.isEnabled(orgId);
        List<String> effectiveKnowledgeBaseIds = skillResolverService.resolveKnowledgeBaseIds(skillContext, kbIds);
        List<String> requestedKnowledgeBaseIds = normalizeKnowledgeBaseIds(kbIds);
        boolean useKnowledgeRetrieval = shouldUseKnowledgeRetrieval(
                question, effectiveKnowledgeBaseIds, requestedKnowledgeBaseIds, sessionId);
        Instant ragStartedAt = Instant.now();
        RagService.RetrievalResult ragResult = useKnowledgeRetrieval
                ? ragService.retrieveDetailed(orgId, effectiveKnowledgeBaseIds, question)
                : emptyRagRetrievalResult();
        stageTraces.add(stageTrace("RAG", useKnowledgeRetrieval ? "知识库检索" : "知识库检索未触发",
                useKnowledgeRetrieval ? "SUCCESS" : "SKIPPED", ragStartedAt, Instant.now(),
                useKnowledgeRetrieval
                        ? "知识库检索完成，命中 " + ragResult.context().size() + " 个片段。"
                        : "本轮输入未满足知识库检索条件。",
                ragDetailMetadata(ragResult)));
        List<String> ragContext = ragResult.context();
        Instant toolSchemaStartedAt = Instant.now();
        List<Map<String, Object>> tools = isWecomKfSession(sessionId)
                ? List.of()
                : toolOrchestratorService.getToolDefinitions(
                orgId, skillContext.allowedToolNames(), skillContext.skillApiTools());
        stageTraces.add(stageTrace("TOOL_SCHEMA", "工具定义加载", "SUCCESS", toolSchemaStartedAt, Instant.now(),
                "已加载本轮可用工具定义 " + tools.size() + " 个。",
                Map.of("toolDefinitionCount", tools.size(), "allowedToolNames", skillContext.allowedToolNames())));
        RuntimeContext runtimeContext = runtimeContextPromptService.current();

        chatSessionStateService.mergeUserTurn(orgId, sessionId, skillContext.agentId(), question);
        List<Map<String, Object>> messages = buildInitialMessages(
                sessionId, question, ragContext, showThinking, skillContext, orgId, userId,
                runtimeContext, routedModel.get("provider"), modelName, builtinDocs);
        int maxToolRounds = resolveMaxToolRounds(skillContext.maxToolCalls());
        String answer = runToolLoop(modelName, messages, tools, orgId, userId, sessionId,
                showThinking, skillContext, maxToolRounds, modelCredentials, modelCallTraces, toolCallTraces);
        Instant wfStartedAt = Instant.now();
        AgentWorkflowRuntimeService.RuntimeExecutionResult executionResult = agentWorkflowRuntimeService.evaluateForChat(
                orgId, skillContext.agentId(), question, skillContext.allowedToolNames());
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
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orgId", orgId);
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
        CompletableFuture.runAsync(() -> {
            Instant runStartedAt = Instant.now();
            List<AgentRunTraceService.StageTraceInput> stageTraces = new ArrayList<>();
            List<AgentRunTraceService.ModelCallTraceInput> modelCallTraces = new ArrayList<>();
            List<AgentRunTraceService.ToolCallTraceInput> toolCallTraces = new ArrayList<>();
            try {
                Instant skillStartedAt = Instant.now();
        ResolvedSkillContext skillContext = skillResolverService.resolve(
                orgId, requestedAgentId, sessionId, Optional.ofNullable(activeSkillCode));
        agentAccessControlService.require(orgId, userId, TenantContext.getRoles(), skillContext.agentId(), AgentPermission.RUN);
                BuiltinSkillDocumentService.ResolvedBuiltinSkillDocs builtinDocs =
                        builtinSkillDocumentService.resolveDocs(skillContext, question);
                stageTraces.add(stageTrace("SKILL_RESOLVE", "技能候选解析", "SUCCESS", skillStartedAt, Instant.now(),
                        "已解析当前智能体绑定技能、工具边界与会话激活技能。",
                        skillTraceMetadata(skillContext, List.of(), builtinDocs)));
                Instant userPersistStartedAt = Instant.now();
                persistUserTurnCommitted(orgId, userId, sessionId, question, skillContext.agentId());
                stageTraces.add(stageTrace("USER_MESSAGE", "用户输入", "SUCCESS", userPersistStartedAt, Instant.now(),
                        clipForTrace(question, 220), Map.of("sessionId", sessionId)));

                Map<String, String> routedModel = modelRouterService.route(orgId, "chat");
                String modelName = resolveModelName(skillContext.agentModel(), routedModel.get("provider"), routedModel.get("modelName"));
                ModelCallCredentials modelCredentials = resolveModelCallCredentials(orgId, routedModel.get("provider"));
                boolean showThinking = chatThinkingConfigService.isEnabled(orgId);
                List<String> effectiveKnowledgeBaseIds = skillResolverService.resolveKnowledgeBaseIds(skillContext, kbIds);
                List<String> requestedKnowledgeBaseIds = normalizeKnowledgeBaseIds(kbIds);
                boolean useKnowledgeRetrieval = shouldUseKnowledgeRetrieval(
                        question, effectiveKnowledgeBaseIds, requestedKnowledgeBaseIds, sessionId);
                safeSendPhase(emitter, "model", modelName);
                if (useKnowledgeRetrieval) {
                    safeSendPhase(emitter, "retrieving", modelName, Map.of(
                            "knowledgeBaseIds", effectiveKnowledgeBaseIds
                    ));
                }
                Instant ragStartedAt = Instant.now();
                RagService.RetrievalResult ragResult = useKnowledgeRetrieval
                        ? ragService.retrieveDetailed(orgId, effectiveKnowledgeBaseIds, question)
                        : emptyRagRetrievalResult();
                stageTraces.add(stageTrace("RAG", useKnowledgeRetrieval ? "知识库检索" : "知识库检索未触发",
                        useKnowledgeRetrieval ? "SUCCESS" : "SKIPPED", ragStartedAt, Instant.now(),
                        useKnowledgeRetrieval
                                ? "知识库检索完成，命中 " + ragResult.context().size() + " 个片段。"
                                : "本轮输入未满足知识库检索条件。",
                        ragDetailMetadata(ragResult)));
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
                        Map.of("toolDefinitionCount", tools.size(), "allowedToolNames", skillContext.allowedToolNames())));
                RuntimeContext runtimeContext = runtimeContextPromptService.current();
                chatSessionStateService.mergeUserTurn(orgId, sessionId, skillContext.agentId(), question);
                List<Map<String, Object>> messages = buildInitialMessages(
                        sessionId, question, ragContext, showThinking, skillContext, orgId, userId,
                        runtimeContext, routedModel.get("provider"), modelName, builtinDocs);
                int maxToolRounds = resolveMaxToolRounds(skillContext.maxToolCalls());
                boolean pendingApprovalsUsed = resolveToolCalls(
                        modelName, messages, tools, orgId, userId, sessionId,
                        showThinking, skillContext, emitter, maxToolRounds, modelCredentials, modelCallTraces, toolCallTraces);
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

                String finalText = showThinking ? acc.toString() : AssistantContentSanitizer.stripThinkingSections(acc.toString());
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
                Instant wfStartedAt = Instant.now();
                AgentWorkflowRuntimeService.RuntimeExecutionResult executionResult = agentWorkflowRuntimeService.evaluateForChat(
                        orgId, skillContext.agentId(), question, skillContext.allowedToolNames());
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
                emitter.send(SseEmitter.event().name("done").data(Map.of("ok", true)));
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data(Map.of("message", e.getMessage() == null ? "stream failed" : e.getMessage())));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        });
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
                               List<AgentRunTraceService.ToolCallTraceInput> toolCallTraces) {
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

            appendToolCallsAndResults(messages, result, orgId, userId, sessionId, skillContext, null, toolCallTraces);
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
                                  List<AgentRunTraceService.ToolCallTraceInput> toolCallTraces) {
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
                    messages, result, orgId, userId, sessionId, skillContext, emitter, toolCallTraces)
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
                                            List<AgentRunTraceService.ToolCallTraceInput> toolCallTraces) {
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
                            tc.arguments(),
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
        if (ragResult == null) {
            return Map.of("triggered", false, "contextCount", 0, "timingsMs", Map.of());
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("triggered", !ragResult.context().isEmpty() || !ragResult.knowledgeBases().isEmpty());
        metadata.put("contextCount", ragResult.context().size());
        metadata.put("knowledgeBases", ragResult.knowledgeBases().stream().map(RagService.RetrievedKnowledgeBase::name).toList());
        metadata.put("timingsMs", ragResult.timingsMs());
        metadata.put("fallbackUsed", ragResult.fallbackUsed());
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
                throw new IllegalArgumentException("当前模型厂商已停用，请先在管理后台启用模型配置。");
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
        return !looksFailedToolResult(result) && !toolResultRequiresMoreToolWork(result);
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
                + "\n\n---\n本轮不会在完成状态后自动追加回复。以下是已经返回的工具结果摘要：\n\n"
                + fallback;
    }

    static boolean finalAnswerDefersToolResult(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String normalized = content.replaceAll("\\s+", "");
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
                    .append(item.toolName()).append("：")
                    .append(item.summary());
            if (!item.arguments().isBlank()) {
                text.append("\n   查询参数：").append(clipStatic(item.arguments(), 220));
            }
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
            String clipped = normalized.length() > 4000 ? normalized.substring(0, 4000) + "..." : normalized;
            return "工具已返回结果，但模型本轮未能生成最终自然语言总结。"
                    + "该工具返回的是非结构化内容，先展示原始结果前段，必要时请让我继续整理：\n\n"
                    + clipped;
        }
        return "本次工具调用已完成，但模型本轮未能生成可展示的数据摘要。请调整筛选条件后重试。";
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
        return clipStatic(firstLine(normalized), 360);
    }

    private static String buildStructuredToolResultFallbackMessage(String toolContent) {
        try {
            JsonNode root = parseToolContentObject(toolContent);
            if (!root.isObject()) {
                return "";
            }
            String answer = nodeText(root, "answer");
            if (!answer.isBlank()) {
                return "工具已返回 answer，但模型本轮未能生成最终自然语言总结。先展示可读结果：\n\n"
                        + clipStatic(answer, 1200);
            }
            boolean failed = booleanFieldIsFalse(root, "success")
                    || booleanFieldIsFalse(root, "ok")
                    || booleanFieldIsFalse(root, "result");
            if (failed) {
                String message = firstNonBlank(
                        nodeText(root, "message"),
                        nodeText(root, "error"),
                        nodeText(root, "reason"));
                return "工具调用未完成，模型本轮未能生成最终自然语言总结。"
                        + (message.isBlank() ? "请检查参数后重试。" : "原因：" + clipStatic(message, 500));
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
                return "工具已返回结果，但模型本轮未能生成最终自然语言总结。先展示工具摘要：\n\n"
                        + clipStatic(message, 1000);
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
        StringBuilder summary = new StringBuilder();
        summary.append("工具已返回 ").append(count)
                .append(" 条结果，但模型本轮未能生成最终自然语言总结。先给你可读摘要，必要时请让我继续整理成结论：");
        int limit = Math.min(5, count);
        for (int i = 0; i < limit; i++) {
            JsonNode item = results.get(i);
            String title = firstNonBlank(nodeText(item, "title"), nodeText(item, "name"), "结果 " + (i + 1));
            String url = nodeText(item, "url");
            String snippet = firstNonBlank(nodeText(item, "snippet"), nodeText(item, "content"), nodeText(item, "description"));
            summary.append("\n").append(i + 1).append(". ").append(clipStatic(title, 180));
            if (!url.isBlank()) {
                summary.append("\n   来源：").append(clipStatic(url, 260));
            }
            if (!snippet.isBlank()) {
                summary.append("\n   摘要：").append(clipStatic(snippet.replace("\\n", "\n"), 220));
            }
        }
        if (count > limit) {
            summary.append("\n其余 ").append(count - limit).append(" 条结果已省略，可继续让我按公司、地区或营收规模整理。");
        }
        return summary.toString();
    }

    private static String summarizeBusinessDataArray(JsonNode data) {
        int count = data.size();
        if (count == 0) {
            return "工具查询已完成，但没有返回匹配业务记录。你可以确认姓名、月份、对象或筛选字段后再试。";
        }
        StringBuilder summary = new StringBuilder();
        summary.append("工具已返回 ").append(count).append(" 条业务记录。先展示前几条可读摘要：");
        int limit = Math.min(5, count);
        for (int i = 0; i < limit; i++) {
            JsonNode item = data.get(i);
            String title = firstNonBlank(nodeText(item, "name"), nodeText(item, "id"), "记录 " + (i + 1));
            summary.append("\n").append(i + 1).append(". ").append(clipStatic(title, 160));
            String person = firstNonBlank(nodeText(item, "bkhrccname"), nodeText(item, "khperson"));
            String period = firstNonBlank(nodeText(item, "khy"), nodeText(item, "kaoheyuefen"), nodeText(item, "khyquarter"));
            String score = firstNonBlank(nodeText(item, "kpitotal"), nodeText(item, "mbzs"));
            List<String> meta = new ArrayList<>();
            if (!person.isBlank()) meta.add("人员：" + person);
            if (!period.isBlank()) meta.add("期间：" + period);
            if (!score.isBlank()) meta.add("分值：" + score);
            if (!meta.isEmpty()) {
                summary.append("\n   ").append(String.join("；", meta));
            }
        }
        if (count > limit) {
            summary.append("\n其余 ").append(count - limit).append(" 条记录已省略。");
        }
        return summary.toString();
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

    private static String firstLine(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        int newline = value.indexOf('\n');
        return newline < 0 ? value.trim() : value.substring(0, newline).trim();
    }

    private static int parsePositiveInt(String value) {
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (Exception ignored) {
            return 0;
        }
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
                - 知识库只在用户显式选择知识库，或问题明确指向企业文档、制度、流程、规则、手册、产品配置、操作指南、口径、依据时使用；不要把每句对话都当成知识库问答。
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

    /**
     * Use the agent's preferred qwen model only when the routed provider is Aliyun Bailian.
     * Local OpenAI-compatible providers can expose qwen-family names with different IDs, so
     * they must use the org-level routed model exactly as configured.
     */
    private static String resolveModelName(String agentModel, String routedProvider, String routedModelName) {
        if ("aliyun-bailian".equals(routedProvider)
                && agentModel != null && !agentModel.isBlank()
                && agentModel.toLowerCase(Locale.ROOT).startsWith("qwen")) {
            return agentModel;
        }
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
        if (effectiveKnowledgeBaseIds == null || effectiveKnowledgeBaseIds.isEmpty()) {
            return false;
        }
        if (requestedKnowledgeBaseIds != null && !requestedKnowledgeBaseIds.isEmpty()) {
            return true;
        }
        String text = question == null ? "" : question.trim().toLowerCase(Locale.ROOT);
        if (text.isBlank()) {
            return false;
        }
        if (containsAny(text, List.of(
                "你好", "您好", "早上好", "晚上好", "谢谢", "感谢", "辛苦了",
                "讲个笑话", "上才艺", "唱首歌", "写首诗", "角色扮演", "随便聊聊"))) {
            return false;
        }
        if (containsAny(text, List.of(
                "知识库", "知识", "文档", "资料", "制度", "政策", "流程", "规则", "规范", "手册",
                "faq", "常见问题", "说明书", "操作指南", "配置", "口径", "依据", "条款", "产品说明",
                "报销制度", "价格政策", "实施指南"))) {
            return true;
        }
        if (containsAny(text, List.of(
                "查询", "查一下", "看下", "看一下", "拉取", "获取", "列出", "列表", "明细", "台账",
                "客户", "线索", "商机", "报价", "订单", "审批", "待办", "日程", "邮件", "发送", "创建", "更新"))) {
            if (isWecomKfSession(sessionId)) {
                return true;
            }
            return false;
        }
        return isWecomKfSession(sessionId);
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
        return new RagService.RetrievalResult(List.of(), List.of(), Map.of("total", 0L), false);
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
