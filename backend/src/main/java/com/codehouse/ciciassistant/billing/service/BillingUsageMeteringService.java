package com.codehouse.ciciassistant.billing.service;

import com.codehouse.ciciassistant.ai.service.AgentRunTraceService;
import com.codehouse.ciciassistant.ai.service.RagService;
import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.billing.domain.BillingCreditLedgerEntity;
import com.codehouse.ciciassistant.billing.domain.BillingCreditLedgerRepository;
import com.codehouse.ciciassistant.billing.domain.BillingEditionEntity;
import com.codehouse.ciciassistant.billing.domain.BillingEditionRepository;
import com.codehouse.ciciassistant.billing.domain.BillingSubscriptionEntity;
import com.codehouse.ciciassistant.billing.domain.BillingSubscriptionRepository;
import com.codehouse.ciciassistant.billing.domain.UsageMeterEventEntity;
import com.codehouse.ciciassistant.billing.domain.UsageMeterEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingUsageMeteringService {

    private static final BigDecimal PRIVATE_CONTRACT_CREDITS = new BigDecimal("50000.00");
    private static final BigDecimal CHAT_TURN_CREDITS = new BigDecimal("1.00");
    private static final BigDecimal MODEL_INPUT_CREDITS_PER_1K = new BigDecimal("0.10");
    private static final BigDecimal MODEL_OUTPUT_CREDITS_PER_1K = new BigDecimal("0.50");
    private static final BigDecimal RAG_RETRIEVAL_CREDITS = new BigDecimal("0.20");
    private static final BigDecimal TOOL_CALL_CREDITS = new BigDecimal("0.50");
    private static final BigDecimal WORKFLOW_RUN_CREDITS = new BigDecimal("0.20");
    private static final BigDecimal OPEN_API_SYNC_CREDITS = new BigDecimal("2.00");
    private static final BigDecimal OPEN_API_STREAM_CREDITS = new BigDecimal("3.00");
    private static final BigDecimal KB_INDEXING_CREDITS_PER_CHUNK = new BigDecimal("0.20");

    private final BillingSubscriptionRepository subscriptionRepository;
    private final BillingEditionRepository editionRepository;
    private final UsageMeterEventRepository usageMeterEventRepository;
    private final BillingCreditLedgerRepository creditLedgerRepository;
    private final BillingEditionConfigurationService configurationService;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    public BillingUsageMeteringService(BillingSubscriptionRepository subscriptionRepository,
                                       BillingEditionRepository editionRepository,
                                       UsageMeterEventRepository usageMeterEventRepository,
                                       BillingCreditLedgerRepository creditLedgerRepository,
                                       BillingEditionConfigurationService configurationService,
                                       EntityManager entityManager,
                                       ObjectMapper objectMapper) {
        this.subscriptionRepository = subscriptionRepository;
        this.editionRepository = editionRepository;
        this.usageMeterEventRepository = usageMeterEventRepository;
        this.creditLedgerRepository = creditLedgerRepository;
        this.configurationService = configurationService;
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BillingRunMeteringResult recordChatRun(ChatRunMeteringInput input) {
        if (input == null || blank(input.companyId()) || blank(input.sessionId())) {
            return BillingRunMeteringResult.empty();
        }
        BillingSubscriptionEntity subscription = ensureBillingState(input.companyId());
        BillingEditionEntity edition = editionRepository.findByEditionCode(subscription.getEditionCode()).orElseThrow();
        boolean billableRun = input.billable();
        String billingType = billableRun ? billingTypeFor(edition) : "non_billable";
        boolean chargeCredits = billableRun && chargesCredits(billingType);
        List<UsageMeterEventEntity> events = buildEvents(input, billingType, chargeCredits);
        BigDecimal debited = BigDecimal.ZERO;
        int created = 0;
        for (UsageMeterEventEntity event : events) {
            if (usageMeterEventRepository.findBySourceTypeAndSourceId(event.getSourceType(), event.getSourceId()).isPresent()) {
                continue;
            }
            UsageMeterEventEntity saved = usageMeterEventRepository.save(event);
            created++;
            if (chargeCredits && saved.getWorkCreditQuantity().compareTo(BigDecimal.ZERO) > 0) {
                debited = debited.add(saved.getWorkCreditQuantity());
                appendUsageDebit(subscription, saved);
            }
        }
        refreshSubscriptionBalance(subscription);
        return new BillingRunMeteringResult(created, debited.setScale(2, RoundingMode.HALF_UP), chargeCredits, billingType);
    }

    public void recordChatRunSafely(ChatRunMeteringInput input) {
        try {
            recordChatRun(input);
        } catch (RuntimeException ignored) {
            // Runtime billing must never break the user's chat turn.
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BillingRunMeteringResult recordOpenApiChatRun(OpenApiChatMeteringInput input) {
        if (input == null || blank(input.companyId()) || blank(input.requestId())) {
            return BillingRunMeteringResult.empty();
        }
        BillingSubscriptionEntity subscription = ensureBillingState(input.companyId());
        BillingEditionEntity edition = editionRepository.findByEditionCode(subscription.getEditionCode()).orElseThrow();
        String billingType = billingTypeFor(edition);
        boolean chargeCredits = chargesCredits(billingType);
        Instant occurredAt = input.endedAt() == null ? Instant.now().truncatedTo(ChronoUnit.SECONDS) : input.endedAt().truncatedTo(ChronoUnit.SECONDS);
        BigDecimal credits = chargeCredits
                ? (input.stream() ? OPEN_API_STREAM_CREDITS : OPEN_API_SYNC_CREDITS)
                : BigDecimal.ZERO;
        UsageMeterEventEntity event = new UsageMeterEventEntity(
                input.companyId(),
                input.userId(),
                input.agentId(),
                "open_api_chat",
                input.stream() ? "stream_chat" : "non_stream_chat",
                input.stream() ? "Open API 流式对话请求 Credits" : "Open API 对话请求 Credits",
                BigDecimal.ONE,
                "request",
                credits,
                billingType,
                "open-api-chat",
                openApiSourceId(input),
                occurredAt,
                writeJson(mapOf(
                        "officialPricingItem", "Credits 包",
                        "credentialId", Math.max(0L, input.credentialId()),
                        "requestId", input.requestId(),
                        "idempotencyKeyPresent", !blank(input.idempotencyKey()),
                        "externalUserId", input.externalUserId(),
                        "sessionId", input.sessionId(),
                        "traceId", input.traceId(),
                        "responseMode", input.stream() ? "streaming" : "blocking",
                        "elapsedMs", Math.max(0, input.elapsedMs()),
                        "creditsPerRequest", input.stream() ? OPEN_API_STREAM_CREDITS : OPEN_API_SYNC_CREDITS)));
        return recordSingleEvent(subscription, event, chargeCredits);
    }

    public void recordOpenApiChatRunSafely(OpenApiChatMeteringInput input) {
        try {
            recordOpenApiChatRun(input);
        } catch (RuntimeException ignored) {
            // Runtime billing must never break Open API responses.
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BillingRunMeteringResult recordKbIndexing(KbIndexingMeteringInput input) {
        if (input == null || blank(input.companyId()) || blank(input.sourceId()) || input.chunkCount() <= 0) {
            return BillingRunMeteringResult.empty();
        }
        BillingSubscriptionEntity subscription = ensureBillingState(input.companyId());
        BillingEditionEntity edition = editionRepository.findByEditionCode(subscription.getEditionCode()).orElseThrow();
        String billingType = billingTypeFor(edition);
        boolean chargeCredits = chargesCredits(billingType);
        Instant occurredAt = input.endedAt() == null ? Instant.now().truncatedTo(ChronoUnit.SECONDS) : input.endedAt().truncatedTo(ChronoUnit.SECONDS);
        BigDecimal quantity = new BigDecimal(Math.max(0, input.chunkCount()));
        BigDecimal credits = chargeCredits
                ? KB_INDEXING_CREDITS_PER_CHUNK.multiply(quantity).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        UsageMeterEventEntity event = new UsageMeterEventEntity(
                input.companyId(),
                input.userId(),
                input.agentId(),
                "kb_indexing",
                "kb_indexing_credit",
                "知识库索引 Credits",
                quantity,
                "chunk",
                credits,
                billingType,
                "kb-indexing",
                input.sourceId(),
                occurredAt,
                writeJson(mapOf(
                        "officialPricingItem", "Credits 包",
                        "knowledgeBaseId", input.knowledgeBaseId(),
                        "documentId", input.documentId(),
                        "documentName", input.documentName(),
                        "documentBytes", Math.max(0L, input.documentBytes()),
                        "indexVersion", Math.max(0, input.indexVersion()),
                        "operation", input.operation(),
                        "creditsPerChunk", KB_INDEXING_CREDITS_PER_CHUNK)));
        return recordSingleEvent(subscription, event, chargeCredits);
    }

    public void recordKbIndexingSafely(KbIndexingMeteringInput input) {
        try {
            recordKbIndexing(input);
        } catch (RuntimeException ignored) {
            // Runtime billing must never break knowledge indexing.
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BillingRunMeteringResult recordWorkflowRun(WorkflowRunMeteringInput input) {
        if (input == null || blank(input.companyId()) || blank(input.sourceId())) {
            return BillingRunMeteringResult.empty();
        }
        BillingSubscriptionEntity subscription = ensureBillingState(input.companyId());
        BillingEditionEntity edition = editionRepository.findByEditionCode(subscription.getEditionCode()).orElseThrow();
        String billingType = billingTypeFor(edition);
        boolean chargeCredits = chargesCredits(billingType);
        Instant occurredAt = input.endedAt() == null ? Instant.now().truncatedTo(ChronoUnit.SECONDS) : input.endedAt().truncatedTo(ChronoUnit.SECONDS);
        UsageMeterEventEntity event = new UsageMeterEventEntity(
                input.companyId(),
                input.userId(),
                input.agentId(),
                "workflow_run",
                "workflow_credit",
                "工作流运行 Credits",
                BigDecimal.ONE,
                "run",
                chargeCredits ? WORKFLOW_RUN_CREDITS : BigDecimal.ZERO,
                billingType,
                blank(input.sourceType()) ? "workflow-run" : input.sourceType(),
                input.sourceId(),
                occurredAt,
                writeJson(mapOf(
                        "officialPricingItem", "Credits 包",
                        "workflowKind", input.workflowKind(),
                        "executionId", input.executionId(),
                        "routineKey", input.routineKey(),
                        "triggerSource", input.triggerSource(),
                        "elapsedMs", Math.max(0, input.elapsedMs()),
                        "creditsPerRun", WORKFLOW_RUN_CREDITS)));
        return recordSingleEvent(subscription, event, chargeCredits);
    }

    public void recordWorkflowRunSafely(WorkflowRunMeteringInput input) {
        try {
            recordWorkflowRun(input);
        } catch (RuntimeException ignored) {
            // Runtime billing must never break workflow execution.
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BillingRunMeteringResult recordMeetingMinutesRun(MeetingMinutesMeteringInput input) {
        if (input == null || blank(input.companyId()) || blank(input.sessionId())) {
            return BillingRunMeteringResult.empty();
        }
        BillingSubscriptionEntity subscription = ensureBillingState(input.companyId());
        BillingEditionEntity edition = editionRepository.findByEditionCode(subscription.getEditionCode()).orElseThrow();
        boolean billableRun = input.billable();
        String billingType = billableRun ? billingTypeFor(edition) : "non_billable";
        boolean chargeCredits = billableRun && chargesCredits(billingType);
        Instant occurredAt = input.endedAt() == null ? Instant.now().truncatedTo(ChronoUnit.SECONDS) : input.endedAt().truncatedTo(ChronoUnit.SECONDS);
        BigDecimal modelCredits = chargeCredits
                ? modelCredits(input.promptTokens(), input.completionTokens())
                : BigDecimal.ZERO;
        BigDecimal workflowCredits = chargeCredits ? WORKFLOW_RUN_CREDITS : BigDecimal.ZERO;
        List<UsageMeterEventEntity> events = List.of(
                meetingEvent(input, "workflow_run", "workflow_credit", "AI 听记纪要生成 Credits",
                        BigDecimal.ONE, "run", workflowCredits, billingType,
                        usageSourceId(input, "workflow_run"), occurredAt,
                        mapOf("officialPricingItem", "Credits 包", "appCode", "meeting-minutes",
                                "transcriptSegmentCount", Math.max(0, input.transcriptSegmentCount()),
                                "summaryChars", Math.max(0, input.summaryChars()))),
                meetingEvent(input, "model_usage", "model_token_credit", "AI 听记模型 token 工作量 Credits",
                        new BigDecimal(Math.max(0, input.promptTokens()) + Math.max(0, input.completionTokens())),
                        "token", modelCredits, billingType, usageSourceId(input, "model_usage"), occurredAt,
                        mapOf("officialPricingItem", "Credits 包", "appCode", "meeting-minutes",
                                "modelName", input.modelName(), "inputTokens", Math.max(0, input.promptTokens()),
                                "outputTokens", Math.max(0, input.completionTokens()),
                                "inputCreditsPer1k", MODEL_INPUT_CREDITS_PER_1K,
                                "outputCreditsPer1k", MODEL_OUTPUT_CREDITS_PER_1K))
        ).stream().filter(event -> event.getQuantity().compareTo(BigDecimal.ZERO) > 0
                || event.getBillableDomain().equals("workflow_run")).toList();

        BigDecimal debited = BigDecimal.ZERO;
        int created = 0;
        for (UsageMeterEventEntity event : events) {
            if (usageMeterEventRepository.findBySourceTypeAndSourceId(event.getSourceType(), event.getSourceId()).isPresent()) {
                continue;
            }
            UsageMeterEventEntity saved = usageMeterEventRepository.save(event);
            created++;
            if (chargeCredits && saved.getWorkCreditQuantity().compareTo(BigDecimal.ZERO) > 0) {
                debited = debited.add(saved.getWorkCreditQuantity());
                appendUsageDebit(subscription, saved);
            }
        }
        refreshSubscriptionBalance(subscription);
        return new BillingRunMeteringResult(created, debited.setScale(2, RoundingMode.HALF_UP), chargeCredits, billingType);
    }

    public void recordMeetingMinutesRunSafely(MeetingMinutesMeteringInput input) {
        try {
            recordMeetingMinutesRun(input);
        } catch (RuntimeException ignored) {
            // Runtime billing must never break meeting minutes generation.
        }
    }

    private List<UsageMeterEventEntity> buildEvents(ChatRunMeteringInput input, String billingType, boolean chargeCredits) {
        Instant occurredAt = input.endedAt() == null ? Instant.now().truncatedTo(ChronoUnit.SECONDS) : input.endedAt().truncatedTo(ChronoUnit.SECONDS);
        BigDecimal modelCredits = modelCredits(input.modelCalls());
        int inputTokens = input.modelCalls() == null ? 0 : input.modelCalls().stream().mapToInt(AgentRunTraceService.ModelCallTraceInput::inputTokens).sum();
        int outputTokens = input.modelCalls() == null ? 0 : input.modelCalls().stream().mapToInt(AgentRunTraceService.ModelCallTraceInput::outputTokens).sum();
        int ragContextCount = input.ragResult() == null ? 0 : input.ragResult().context().size();
        int toolCallCount = input.toolCalls() == null ? 0 : input.toolCalls().size();
        BigDecimal ragCredits = RAG_RETRIEVAL_CREDITS.multiply(new BigDecimal(ragContextCount)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal toolCredits = TOOL_CALL_CREDITS.multiply(new BigDecimal(toolCallCount)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal workflowCredits = input.workflowElapsedMs() > 0 ? WORKFLOW_RUN_CREDITS : BigDecimal.ZERO;
        BigDecimal effectiveChatCredits = chargeCredits ? CHAT_TURN_CREDITS : BigDecimal.ZERO;
        BigDecimal effectiveModelCredits = chargeCredits ? modelCredits : BigDecimal.ZERO;
        BigDecimal effectiveRagCredits = chargeCredits ? ragCredits : BigDecimal.ZERO;
        BigDecimal effectiveToolCredits = chargeCredits ? toolCredits : BigDecimal.ZERO;
        BigDecimal effectiveWorkflowCredits = chargeCredits ? workflowCredits : BigDecimal.ZERO;

        return List.of(
                event(input, "assistant_chat", "conversation_credit", "智能体对话 Credits", BigDecimal.ONE, "turn",
                        effectiveChatCredits, billingType, "chat-run", usageSourceId(input, "assistant_chat"), occurredAt,
                        mapOf("officialPricingItem", "Credits 包", "sessionId", input.sessionId())),
                event(input, "model_usage", "model_token_credit", "模型 token 工作量 Credits",
                        new BigDecimal(inputTokens + outputTokens), "token", effectiveModelCredits, billingType,
                        "chat-run", usageSourceId(input, "model_usage"), occurredAt,
                        mapOf("officialPricingItem", "Credits 包", "modelName", input.modelName(), "inputTokens", inputTokens,
                                "outputTokens", outputTokens, "inputCreditsPer1k", MODEL_INPUT_CREDITS_PER_1K,
                                "outputCreditsPer1k", MODEL_OUTPUT_CREDITS_PER_1K)),
                event(input, "rag_retrieval", "retrieval_credit", "知识库检索 Credits", new BigDecimal(ragContextCount),
                        "chunk", effectiveRagCredits, billingType, "chat-run", usageSourceId(input, "rag_retrieval"), occurredAt,
                        mapOf("officialPricingItem", "Credits 包", "contextCount", ragContextCount)),
                event(input, "tool_call", "tool_call_credit", "工具调用 Credits", new BigDecimal(toolCallCount),
                        "call", effectiveToolCredits, billingType, "chat-run", usageSourceId(input, "tool_call"), occurredAt,
                        mapOf("officialPricingItem", "Credits 包", "toolCallCount", toolCallCount)),
                event(input, "workflow_run", "workflow_credit", "运行治理 Credits", BigDecimal.ONE,
                        "run", effectiveWorkflowCredits, billingType, "chat-run", usageSourceId(input, "workflow_run"), occurredAt,
                        mapOf("officialPricingItem", "Credits 包", "workflowElapsedMs", input.workflowElapsedMs()))
        ).stream().filter(item -> item.getQuantity().compareTo(BigDecimal.ZERO) > 0
                || item.getBillableDomain().equals("assistant_chat")
                || item.getBillableDomain().equals("workflow_run")).toList();
    }

    private UsageMeterEventEntity event(ChatRunMeteringInput input,
                                        String domain,
                                        String itemCode,
                                        String description,
                                        BigDecimal quantity,
                                        String unit,
                                        BigDecimal credits,
                                        String billingType,
                                        String sourceType,
                                        String sourceId,
                                        Instant occurredAt,
                                        Map<String, Object> metadata) {
        return new UsageMeterEventEntity(
                input.companyId(),
                input.userId(),
                input.agentId(),
                domain,
                itemCode,
                description,
                quantity,
                unit,
                credits.setScale(2, RoundingMode.HALF_UP),
                billingType,
                sourceType,
                sourceId,
                occurredAt,
                writeJson(metadata));
    }

    private String usageSourceId(ChatRunMeteringInput input, String domain) {
        return input.companyId() + ":" + input.sessionId() + ":" + domain;
    }

    private UsageMeterEventEntity meetingEvent(MeetingMinutesMeteringInput input,
                                               String domain,
                                               String itemCode,
                                               String description,
                                               BigDecimal quantity,
                                               String unit,
                                               BigDecimal credits,
                                               String billingType,
                                               String sourceId,
                                               Instant occurredAt,
                                               Map<String, Object> metadata) {
        return new UsageMeterEventEntity(
                input.companyId(),
                input.userId(),
                "ai-meeting-notetaker",
                domain,
                itemCode,
                description,
                quantity,
                unit,
                credits.setScale(2, RoundingMode.HALF_UP),
                billingType,
                "meeting-minutes",
                sourceId,
                occurredAt,
                writeJson(metadata));
    }

    private String usageSourceId(MeetingMinutesMeteringInput input, String domain) {
        return input.companyId() + ":" + input.sessionId() + ":" + domain;
    }

    private void appendUsageDebit(BillingSubscriptionEntity subscription, UsageMeterEventEntity event) {
        BigDecimal currentBalance = currentBalance(subscription);
        BigDecimal nextBalance = currentBalance.subtract(event.getWorkCreditQuantity()).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        creditLedgerRepository.save(new BillingCreditLedgerEntity(
                subscription.getCompanyId(),
                "usage_debit",
                event.getWorkCreditQuantity().negate(),
                nextBalance,
                event.getId(),
                event.getDescription(),
                event.getOccurredAt(),
                writeJson(Map.of("billableDomain", event.getBillableDomain(), "itemCode", event.getBillableItemCode(),
                        "billingType", event.getBillingType(), "sourceType", event.getSourceType(), "sourceId", event.getSourceId()))));
    }

    private BillingRunMeteringResult recordSingleEvent(BillingSubscriptionEntity subscription,
                                                       UsageMeterEventEntity event,
                                                       boolean chargeCredits) {
        if (usageMeterEventRepository.findBySourceTypeAndSourceId(event.getSourceType(), event.getSourceId()).isPresent()) {
            refreshSubscriptionBalance(subscription);
            return new BillingRunMeteringResult(0, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), chargeCredits, event.getBillingType());
        }
        UsageMeterEventEntity saved = usageMeterEventRepository.save(event);
        BigDecimal debited = BigDecimal.ZERO;
        if (chargeCredits && saved.getWorkCreditQuantity().compareTo(BigDecimal.ZERO) > 0) {
            debited = saved.getWorkCreditQuantity();
            appendUsageDebit(subscription, saved);
        }
        refreshSubscriptionBalance(subscription);
        return new BillingRunMeteringResult(1, debited.setScale(2, RoundingMode.HALF_UP), chargeCredits, event.getBillingType());
    }

    BillingSubscriptionEntity ensureBillingState(String companyId) {
        configurationService.ensureDefaultCatalog();
        BillingSubscriptionEntity subscription = subscriptionRepository.findByCompanyId(companyId)
                .orElseGet(() -> createDefaultSubscription(companyId));
        if (creditLedgerRepository.findByCompanyIdOrderByOccurredAtAsc(companyId).isEmpty()) {
            creditLedgerRepository.save(new BillingCreditLedgerEntity(
                    companyId,
                    "included_grant",
                    subscription.getIncludedCredits(),
                    subscription.getIncludedCredits(),
                    null,
                    "当前版本周期内含或合同治理 credits 发放",
                    subscription.getPeriodStart(),
                    writeJson(Map.of("editionCode", subscription.getEditionCode()))));
        }
        return refreshSubscriptionBalance(subscription);
    }

    private BillingSubscriptionEntity createDefaultSubscription(String companyId) {
        BillingEditionEntity edition = editionRepository.findByEditionCode("saas_business")
                .orElseGet(() -> editionRepository.findFirstByDeploymentModeAndEnabledTrueOrderBySortOrderAscEditionCodeAsc("saas")
                        .orElseThrow());
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        BillingSubscriptionEntity subscription = new BillingSubscriptionEntity(companyId, edition.getDeploymentMode(), edition.getEditionCode(),
                now.minus(7, ChronoUnit.DAYS), now.plus(358, ChronoUnit.DAYS));
        BigDecimal included = effectiveIncludedCredits(edition);
        subscription.setIncludedCredits(included);
        subscription.setRemainingCredits(included);
        subscription.setOperationSeatsUsed(1);
        subscription.setBuilderSeatsUsed(activeBuilderSeatUsers(companyId));
        subscription.setPackageCodes(edition.getPackageCodes());
        subscription.setUpdatedAt(now);
        return subscriptionRepository.save(subscription);
    }

    BillingSubscriptionEntity refreshSubscriptionBalance(BillingSubscriptionEntity subscription) {
        BigDecimal consumed = creditLedgerRepository.findByCompanyIdOrderByOccurredAtAsc(subscription.getCompanyId()).stream()
                .filter(item -> "usage_debit".equals(item.getEntryType()))
                .map(BillingCreditLedgerEntity::getCreditsDelta)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        subscription.setConsumedCredits(consumed);
        subscription.setRemainingCredits(subscription.getIncludedCredits().subtract(consumed).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
        subscription.setBuilderSeatsUsed(activeBuilderSeatUsers(subscription.getCompanyId()));
        subscription.setUpdatedAt(Instant.now());
        return subscriptionRepository.save(subscription);
    }

    private int activeBuilderSeatUsers(String companyId) {
        Long count = entityManager.createQuery("""
                        select count(member)
                        from UserEntity member
                        where member.org.id = :companyId
                          and member.memberStatus = :memberStatus
                          and member.roleCode in :builderRoles
                        """, Long.class)
                .setParameter("companyId", companyId)
                .setParameter("memberStatus", UserEntity.STATUS_ACTIVE)
                .setParameter("builderRoles", List.of(RoleCodes.OWNER, RoleCodes.ORG_ADMIN))
                .getSingleResult();
        return Math.toIntExact(count);
    }

    private BigDecimal currentBalance(BillingSubscriptionEntity subscription) {
        List<BillingCreditLedgerEntity> ledger = creditLedgerRepository.findByCompanyIdOrderByIdAsc(subscription.getCompanyId());
        if (ledger.isEmpty()) {
            return subscription.getRemainingCredits().setScale(2, RoundingMode.HALF_UP);
        }
        return ledger.get(ledger.size() - 1).getBalanceAfter().setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal effectiveIncludedCredits(BillingEditionEntity edition) {
        if (edition.getIncludedCredits() != null && edition.getIncludedCredits().compareTo(BigDecimal.ZERO) > 0) {
            return edition.getIncludedCredits().setScale(2, RoundingMode.HALF_UP);
        }
        return PRIVATE_CONTRACT_CREDITS;
    }

    private BigDecimal modelCredits(List<AgentRunTraceService.ModelCallTraceInput> modelCalls) {
        if (modelCalls == null || modelCalls.isEmpty()) {
            return BigDecimal.ZERO;
        }
        int inputTokens = modelCalls.stream().mapToInt(AgentRunTraceService.ModelCallTraceInput::inputTokens).sum();
        int outputTokens = modelCalls.stream().mapToInt(AgentRunTraceService.ModelCallTraceInput::outputTokens).sum();
        return modelCredits(inputTokens, outputTokens);
    }

    private BigDecimal modelCredits(int inputTokens, int outputTokens) {
        BigDecimal inputCredits = new BigDecimal(inputTokens).multiply(MODEL_INPUT_CREDITS_PER_1K).divide(new BigDecimal("1000"), 4, RoundingMode.HALF_UP);
        BigDecimal outputCredits = new BigDecimal(outputTokens).multiply(MODEL_OUTPUT_CREDITS_PER_1K).divide(new BigDecimal("1000"), 4, RoundingMode.HALF_UP);
        return inputCredits.add(outputCredits).setScale(2, RoundingMode.HALF_UP);
    }

    private String billingTypeFor(BillingEditionEntity edition) {
        String policy = edition.getBillingTypePolicy() == null ? "" : edition.getBillingTypePolicy().trim();
        if (policy.isBlank() || "included".equals(policy)) {
            return "platform_paid";
        }
        return policy;
    }

    private boolean chargesCredits(String billingType) {
        return "platform_paid".equals(billingType) || "included".equals(billingType);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String openApiSourceId(OpenApiChatMeteringInput input) {
        String source = blank(input.idempotencyKey()) ? input.requestId() : input.idempotencyKey();
        String fingerprint = UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
        return input.companyId() + ":credential-" + Math.max(0L, input.credentialId()) + ":"
                + (input.stream() ? "stream" : "sync") + ":" + fingerprint;
    }

    private Map<String, Object> mapOf(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to serialize billing payload");
        }
    }

    public record ChatRunMeteringInput(
            String companyId,
            String userId,
            String agentId,
            String sessionId,
            String modelName,
            List<AgentRunTraceService.ModelCallTraceInput> modelCalls,
            List<AgentRunTraceService.ToolCallTraceInput> toolCalls,
                RagService.RetrievalResult ragResult,
                int workflowElapsedMs,
                boolean billable,
                Instant endedAt
    ) {
    }

    public record MeetingMinutesMeteringInput(
            String companyId,
            String userId,
            String sessionId,
            String modelName,
            int promptTokens,
            int completionTokens,
            int transcriptSegmentCount,
            int summaryChars,
            boolean billable,
            Instant endedAt
    ) {
    }

    public record OpenApiChatMeteringInput(
            String companyId,
            String userId,
            String agentId,
            long credentialId,
            String requestId,
            String idempotencyKey,
            String externalUserId,
            String sessionId,
            String traceId,
            boolean stream,
            int elapsedMs,
            Instant endedAt
    ) {
    }

    public record KbIndexingMeteringInput(
            String companyId,
            String userId,
            String agentId,
            String knowledgeBaseId,
            Long documentId,
            String documentName,
            long documentBytes,
            int indexVersion,
            int chunkCount,
            String operation,
            String sourceId,
            Instant endedAt
    ) {
    }

    public record WorkflowRunMeteringInput(
            String companyId,
            String userId,
            String agentId,
            String workflowKind,
            Long executionId,
            String routineKey,
            String triggerSource,
            int elapsedMs,
            String sourceType,
            String sourceId,
            Instant endedAt
    ) {
    }

    public record BillingRunMeteringResult(int createdEvents, BigDecimal debitedCredits, boolean chargedCredits, String billingType) {
        static BillingRunMeteringResult empty() {
            return new BillingRunMeteringResult(0, BigDecimal.ZERO, false, "non_billable");
        }
    }
}
