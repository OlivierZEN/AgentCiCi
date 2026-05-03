package com.codehouse.ciciassistant.ai.service;

import com.codehouse.ciciassistant.agent.service.AgentWorkflowExecutionLogService;
import com.codehouse.ciciassistant.agent.service.AgentWorkflowRuntimeService;
import com.codehouse.ciciassistant.ai.domain.ChatMessageEntity;
import com.codehouse.ciciassistant.ai.domain.ChatMessageRepository;
import com.codehouse.ciciassistant.ai.domain.ChatSessionEntity;
import com.codehouse.ciciassistant.ai.domain.ChatSessionRepository;
import com.codehouse.ciciassistant.ai.domain.ChatSessionStateEntity;
import com.codehouse.ciciassistant.ai.domain.ChatSessionStateRepository;
import com.codehouse.ciciassistant.ai.service.AliyunBailianClient.ChatCompletionResult;
import com.codehouse.ciciassistant.ai.service.AliyunBailianClient.ToolCallInfo;
import com.codehouse.ciciassistant.ai.service.RuntimeContextPromptService.RuntimeContext;
import com.codehouse.ciciassistant.feishu.domain.FeishuBotBindingEntity;
import com.codehouse.ciciassistant.feishu.domain.FeishuBotBindingRepository;
import com.codehouse.ciciassistant.memory.domain.UserMemoryEntity;
import com.codehouse.ciciassistant.memory.service.UserMemoryService;
import com.codehouse.ciciassistant.ops.service.AuditService;
import com.codehouse.ciciassistant.skill.service.SkillPromptAssembler;
import com.codehouse.ciciassistant.skill.service.SkillResolverService;
import com.codehouse.ciciassistant.skill.service.SkillResolverService.ResolvedSkillContext;
import com.codehouse.ciciassistant.tool.service.ToolNameNormalizer;
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

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ModelRouterService modelRouterService;
    private final ToolOrchestratorService toolOrchestratorService;
    private final RagService ragService;
    private final ChatThinkingConfigService chatThinkingConfigService;
    private final AuditService auditService;
    private final AliyunBailianClient aliyunBailianClient;
    private final SessionRealtimeEventService sessionRealtimeEventService;
    private final FeishuBotBindingRepository feishuBotBindingRepository;
    private final SkillResolverService skillResolverService;
    private final SkillPromptAssembler skillPromptAssembler;
    private final UserMemoryService userMemoryService;
    private final ChatSessionStateService chatSessionStateService;
    private final ChatSessionStateRepository chatSessionStateRepository;
    private final RuntimeContextPromptService runtimeContextPromptService;
    private final AgentWorkflowRuntimeService agentWorkflowRuntimeService;
    private final AgentWorkflowExecutionLogService agentWorkflowExecutionLogService;
    private final TransactionTemplate tx;

    public ChatOrchestratorService(ChatSessionRepository chatSessionRepository,
                                   ChatMessageRepository chatMessageRepository,
                                   ModelRouterService modelRouterService,
                                   ToolOrchestratorService toolOrchestratorService,
                                   RagService ragService,
                                   ChatThinkingConfigService chatThinkingConfigService,
                                   AuditService auditService,
                                   AliyunBailianClient aliyunBailianClient,
                                   SessionRealtimeEventService sessionRealtimeEventService,
                                   FeishuBotBindingRepository feishuBotBindingRepository,
                                   SkillResolverService skillResolverService,
                                   SkillPromptAssembler skillPromptAssembler,
                                   UserMemoryService userMemoryService,
                                   ChatSessionStateService chatSessionStateService,
                                   ChatSessionStateRepository chatSessionStateRepository,
                                   RuntimeContextPromptService runtimeContextPromptService,
                                   AgentWorkflowRuntimeService agentWorkflowRuntimeService,
                                   AgentWorkflowExecutionLogService agentWorkflowExecutionLogService,
                                   PlatformTransactionManager transactionManager) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.modelRouterService = modelRouterService;
        this.toolOrchestratorService = toolOrchestratorService;
        this.ragService = ragService;
        this.chatThinkingConfigService = chatThinkingConfigService;
        this.auditService = auditService;
        this.aliyunBailianClient = aliyunBailianClient;
        this.sessionRealtimeEventService = sessionRealtimeEventService;
        this.feishuBotBindingRepository = feishuBotBindingRepository;
        this.skillResolverService = skillResolverService;
        this.skillPromptAssembler = skillPromptAssembler;
        this.userMemoryService = userMemoryService;
        this.chatSessionStateService = chatSessionStateService;
        this.chatSessionStateRepository = chatSessionStateRepository;
        this.runtimeContextPromptService = runtimeContextPromptService;
        this.agentWorkflowRuntimeService = agentWorkflowRuntimeService;
        this.agentWorkflowExecutionLogService = agentWorkflowExecutionLogService;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public Map<String, Object> chat(String orgId, String userId, String sessionId,
                                     String question, List<String> kbIds, String requestedAgentId,
                                     String activeSkillCode) {
        ResolvedSkillContext skillContext = skillResolverService.resolve(
                orgId, requestedAgentId, sessionId, Optional.ofNullable(activeSkillCode));
        persistUserTurnCommitted(orgId, userId, sessionId, question, skillContext.agentId());

        Map<String, String> routedModel = modelRouterService.route(orgId, "chat");
        String modelName = resolveModelName(skillContext.agentModel(), routedModel.get("modelName"));
        boolean showThinking = chatThinkingConfigService.isEnabled(orgId);
        List<String> effectiveKnowledgeBaseIds = skillResolverService.resolveKnowledgeBaseIds(skillContext, kbIds);
        List<String> ragContext = ragService.retrieveContext(orgId, effectiveKnowledgeBaseIds, question);
        List<Map<String, Object>> tools = toolOrchestratorService.getToolDefinitions(orgId, skillContext.allowedToolNames());
        RuntimeContext runtimeContext = runtimeContextPromptService.current();

        chatSessionStateService.mergeUserTurn(orgId, sessionId, skillContext.agentId(), question);
        List<Map<String, Object>> messages = buildInitialMessages(
                sessionId, question, ragContext, showThinking, skillContext, orgId, userId,
                runtimeContext, routedModel.get("provider"), modelName);
        int maxToolRounds = resolveMaxToolRounds(skillContext.maxToolCalls());
        String answer = runToolLoop(modelName, messages, tools, orgId, userId, sessionId,
                showThinking, skillContext, maxToolRounds);
        long wfStarted = System.nanoTime();
        AgentWorkflowRuntimeService.RuntimeExecutionResult executionResult = agentWorkflowRuntimeService.evaluateForChat(
                orgId, skillContext.agentId(), question, skillContext.allowedToolNames());
        int wfMs = (int) Math.max(0L, (System.nanoTime() - wfStarted) / 1_000_000L);
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
        persistAssistantTurnCommitted(orgId, userId, sessionId, answer, "AI_CHAT", modelName);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orgId", orgId);
        payload.put("sessionId", sessionId);
        payload.put("agentId", skillContext.agentId());
        payload.put("answer", answer);
        payload.put("model", Map.of("modelName", modelName));
        payload.put("ragContext", ragContext);
        payload.put("effectiveKnowledgeBaseIds", effectiveKnowledgeBaseIds);
        payload.put("resolvedSkills", skillContext.skillCodes());
        payload.put("resolvedSkillVersions", skillContext.resolvedSkillRefs());
        payload.put("effectiveToolNames", skillContext.allowedToolNames());
        payload.put("agentDirectToolNames", skillContext.agentDirectToolNames());
        payload.put("skillDeclaredToolNames", skillContext.skillDeclaredToolNames());
        payload.put("skillScopedToolNames", skillContext.skillScopedToolNames());
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
            try {
                ResolvedSkillContext skillContext = skillResolverService.resolve(
                        orgId, requestedAgentId, sessionId, Optional.ofNullable(activeSkillCode));
                persistUserTurnCommitted(orgId, userId, sessionId, question, skillContext.agentId());

                Map<String, String> routedModel = modelRouterService.route(orgId, "chat");
                String modelName = resolveModelName(skillContext.agentModel(), routedModel.get("modelName"));
                boolean showThinking = chatThinkingConfigService.isEnabled(orgId);
                List<String> effectiveKnowledgeBaseIds = skillResolverService.resolveKnowledgeBaseIds(skillContext, kbIds);
                List<String> ragContext = ragService.retrieveContext(orgId, effectiveKnowledgeBaseIds, question);
                List<Map<String, Object>> tools = toolOrchestratorService.getToolDefinitions(orgId, skillContext.allowedToolNames());
                RuntimeContext runtimeContext = runtimeContextPromptService.current();
                chatSessionStateService.mergeUserTurn(orgId, sessionId, skillContext.agentId(), question);
                List<Map<String, Object>> messages = buildInitialMessages(
                        sessionId, question, ragContext, showThinking, skillContext, orgId, userId,
                        runtimeContext, routedModel.get("provider"), modelName);
                int maxToolRounds = resolveMaxToolRounds(skillContext.maxToolCalls());
                safeSendPhase(emitter, "model", modelName);

                boolean pendingApprovalsUsed = resolveToolCalls(
                        modelName, messages, tools, orgId, userId, sessionId,
                        showThinking, skillContext, emitter, maxToolRounds);
                if (pendingApprovalsUsed) {
                    // Keep chat concise when a dedicated approvals page is rendered on frontend.
                    messages.add(Map.of(
                            "role", "system",
                            "content", "You have already returned approval records via tool_result event. "
                                    + "Do not repeat long approval lists in chat; give only a short summary in 1-2 sentences."
                    ));
                }

                safeSendPhase(emitter, "generating", modelName);
                StringBuilder acc = new StringBuilder();
                log.info("chatStream start LLM stream: session={} model={} msgCount={} toolCount={}",
                        sessionId, modelName, messages.size(), tools.size());
                long streamStart = System.currentTimeMillis();
                try {
                    aliyunBailianClient.chatStreamWithMessages(
                            modelName,
                            messages,
                            tools.isEmpty() ? null : tools,
                            showThinking,
                            piece -> {
                                acc.append(piece);
                                safeSendDelta(emitter, piece);
                            });
                    log.info("chatStream LLM stream done: session={} chars={} elapsedMs={}",
                            sessionId, acc.length(), System.currentTimeMillis() - streamStart);
                } catch (Exception ex) {
                    log.warn("chatStream LLM stream failed: session={} elapsedMs={} err={}",
                            sessionId, System.currentTimeMillis() - streamStart, ex.getMessage());
                    String fallback = "（生成回复时发生错误，请重试。详情：" + ex.getMessage() + "）";
                    acc.append(fallback);
                    safeSendDeltaInChunks(emitter, fallback);
                }

                String finalText = showThinking ? acc.toString() : AssistantContentSanitizer.stripThinkingSections(acc.toString());
                if (finalText == null || finalText.trim().isEmpty()) {
                    finalText = buildToolResultFallbackMessage(messages);
                }
                long wfStarted = System.nanoTime();
                AgentWorkflowRuntimeService.RuntimeExecutionResult executionResult = agentWorkflowRuntimeService.evaluateForChat(
                        orgId, skillContext.agentId(), question, skillContext.allowedToolNames());
                int wfMs = (int) Math.max(0L, (System.nanoTime() - wfStarted) / 1_000_000L);
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
                persistAssistantTurnCommitted(orgId, userId, sessionId, finalText, "AI_CHAT_STREAM", modelName);
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
                               boolean showThinking, ResolvedSkillContext skillContext, int maxToolRounds) {
        for (int round = 0; round < maxToolRounds; round++) {
            ChatCompletionResult result = aliyunBailianClient.chatCompletion(
                    modelName, messages, tools.isEmpty() ? null : tools, !showThinking);

            if (!result.hasToolCalls()) {
                if (result.content() == null) {
                    return "No response from model.";
                }
                return showThinking ? result.content() : AssistantContentSanitizer.stripThinkingSections(result.content());
            }

            appendToolCallsAndResults(messages, result, orgId, userId, sessionId, skillContext, null);
        }
        return "Tool calling exceeded maximum rounds.";
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
                                  int maxToolRounds) {
        if (tools.isEmpty()) return false;
        boolean pendingApprovalsUsed = false;

        for (int round = 0; round < maxToolRounds; round++) {
            ChatCompletionResult result = aliyunBailianClient.chatCompletion(
                    modelName, messages, tools, !showThinking);

            if (!result.hasToolCalls()) {
                break;
            }
            pendingApprovalsUsed = appendToolCallsAndResults(
                    messages, result, orgId, userId, sessionId, skillContext, emitter)
                    || pendingApprovalsUsed;
        }
        return pendingApprovalsUsed;
    }

    private boolean appendToolCallsAndResults(List<Map<String, Object>> messages,
                                            ChatCompletionResult result, String orgId, String userId,
                                            String sessionId,
                                            ResolvedSkillContext skillContext,
                                            SseEmitter emitter) {
        List<Map<String, Object>> toolCallMaps = new ArrayList<>();
        for (ToolCallInfo tc : result.toolCalls()) {
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
        for (ToolCallInfo tc : result.toolCalls()) {
            if (emitter != null) {
                safeSendToolCall(emitter, tc.name());
            }
            log.info("Calling MCP tool: {} with args: {}", tc.name(), tc.arguments());
            String canonicalTool = ToolNameNormalizer.canonicalize(tc.name());
            String toolResult = toolOrchestratorService.executeTool(
                    orgId,
                    userId,
                    tc.name(),
                    tc.arguments(),
                    skillContext.allowedToolNames(),
                    skillContext.agentDirectToolNames());
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

    // ── Helpers ──

    private List<Map<String, Object>> buildInitialMessages(String sessionId, String question, List<String> ragContext,
                                                           boolean showThinking,
                                                           ResolvedSkillContext skillContext,
                                                           String orgId, String userId,
                                                           RuntimeContext runtimeContext,
                                                           String routedProvider,
                                                           String modelName) {
        List<Map<String, Object>> messages = new ArrayList<>();
        String baseSystem = showThinking ? AliyunBailianClient.SYSTEM_PROMPT_WITH_THINKING : AliyunBailianClient.SYSTEM_PROMPT;
        String system = skillPromptAssembler.assemble(baseSystem, skillContext);
        system = buildModelIdentityPromptBlock(routedProvider, modelName) + "\n---\n\n" + system;
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
                || sessionId.startsWith("dingtalk:")
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
        try {
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("phase", phase);
            if (modelName != null && !modelName.isBlank()) {
                payload.put("modelName", modelName);
            }
            emitter.send(SseEmitter.event().name("phase").data(payload));
        } catch (IOException ignored) {}
    }

    private static boolean isPendingApprovalsTool(String toolName) {
        return "get_pending_approvals".equalsIgnoreCase(toolName);
    }

    private static String buildToolResultFallbackMessage(List<Map<String, Object>> messages) {
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
            String clipped = normalized.length() > 4000 ? normalized.substring(0, 4000) + "..." : normalized;
            return "工具已返回结果：\n\n" + clipped;
        }
        return "本次工具调用已完成，但暂时没有可展示的数据。请调整筛选条件后重试。";
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

    /**
     * Use the agent's preferred model only when it is a model the current LLM provider
     * can actually serve (Aliyun Bailian: qwen-* family).  Any placeholder or third-party
     * model name (gpt-*, claude-*, gemini-*, cici-default, …) falls back to the
     * org-level routed model so we never send an unsupported model to the API.
     */
    private static String resolveModelName(String agentModel, String routedModelName) {
        if (agentModel != null && !agentModel.isBlank()
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
}
