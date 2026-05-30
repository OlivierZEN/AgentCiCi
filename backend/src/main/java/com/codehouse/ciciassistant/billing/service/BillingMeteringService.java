package com.codehouse.ciciassistant.billing.service;

import com.codehouse.ciciassistant.billing.domain.BillingCreditLedgerEntity;
import com.codehouse.ciciassistant.billing.domain.BillingCreditLedgerRepository;
import com.codehouse.ciciassistant.billing.domain.BillingSubscriptionEntity;
import com.codehouse.ciciassistant.billing.domain.UsageMeterEventEntity;
import com.codehouse.ciciassistant.billing.domain.UsageMeterEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingMeteringService {

    private static final String RATE_CARD_VERSION = "official-2026-05-work-credits-v1";

    private final AdminBillingService adminBillingService;
    private final UsageMeterEventRepository usageMeterEventRepository;
    private final BillingCreditLedgerRepository creditLedgerRepository;
    private final ObjectMapper objectMapper;

    public BillingMeteringService(AdminBillingService adminBillingService,
                                  UsageMeterEventRepository usageMeterEventRepository,
                                  BillingCreditLedgerRepository creditLedgerRepository,
                                  ObjectMapper objectMapper) {
        this.adminBillingService = adminBillingService;
        this.usageMeterEventRepository = usageMeterEventRepository;
        this.creditLedgerRepository = creditLedgerRepository;
        this.objectMapper = objectMapper;
    }

    public List<RateCardItemView> rateCard() {
        return RATE_CARD.stream().map(item -> new RateCardItemView(
                RATE_CARD_VERSION,
                item.domain(),
                item.itemCode(),
                item.displayName(),
                item.unit(),
                item.creditsPerUnit(),
                item.billingType(),
                item.officialPricingSection(),
                item.productionReady()))
                .toList();
    }

    @Transactional
    public List<UsageMeterEventEntity> recordAssistantChat(ChatUsageCommand command) {
        String chatItemCode = command.ragChunkCount() > 0 ? "rag_message" : "standard_message";
        UsageMeterEventEntity chatEvent = recordUsage(new UsageCommand(
                command.orgId(),
                command.userId(),
                command.agentId(),
                "assistant_chat",
                chatItemCode,
                rateCardItem("assistant_chat", chatItemCode).displayName(),
                BigDecimal.ONE,
                "message",
                command.billingType(),
                "chat",
                command.sourceId() + ":assistant_chat",
                command.occurredAt(),
                Map.of(
                        "sessionId", command.sessionId(),
                        "modelName", command.modelName(),
                        "providerCode", command.providerCode(),
                        "ragChunkCount", command.ragChunkCount(),
                        "toolCallCount", command.toolCallCount(),
                        "rateCardVersion", RATE_CARD_VERSION
                )));

        int totalTokens = Math.max(0, command.promptTokens()) + Math.max(0, command.completionTokens());
        if (totalTokens <= 0) {
            return List.of(chatEvent);
        }
        BigDecimal tokenUnits = new BigDecimal(totalTokens).divide(new BigDecimal("1000"), 4, RoundingMode.HALF_UP);
        UsageMeterEventEntity modelEvent = recordUsage(new UsageCommand(
                command.orgId(),
                command.userId(),
                command.agentId(),
                "model_usage",
                "standard_model_1k_tokens",
                rateCardItem("model_usage", "standard_model_1k_tokens").displayName(),
                tokenUnits,
                "1k_tokens",
                command.billingType(),
                "chat",
                command.sourceId() + ":model_usage",
                command.occurredAt(),
                Map.of(
                        "sessionId", command.sessionId(),
                        "modelName", command.modelName(),
                        "providerCode", command.providerCode(),
                        "promptTokens", command.promptTokens(),
                        "completionTokens", command.completionTokens(),
                        "totalTokens", totalTokens,
                        "rateCardVersion", RATE_CARD_VERSION
                )));
        return List.of(chatEvent, modelEvent);
    }

    @Transactional
    public UsageMeterEventEntity recordUsage(UsageCommand command) {
        return usageMeterEventRepository.findBySourceTypeAndSourceId(command.sourceType(), command.sourceId())
                .orElseGet(() -> appendUsage(command));
    }

    private UsageMeterEventEntity appendUsage(UsageCommand command) {
        RateCardItem rate = rateCardItem(command.domain(), command.itemCode());
        BigDecimal quantity = command.quantity() == null ? BigDecimal.ONE : command.quantity();
        BigDecimal credits = rate.creditsPerUnit().multiply(quantity).setScale(2, RoundingMode.HALF_UP);
        if ("non_billable".equals(command.billingType())) {
            credits = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BillingSubscriptionEntity subscription = adminBillingService.ensureBillingState(command.orgId());
        UsageMeterEventEntity event = usageMeterEventRepository.save(new UsageMeterEventEntity(
                command.orgId(),
                command.userId(),
                command.agentId(),
                command.domain(),
                command.itemCode(),
                command.description(),
                quantity,
                command.unit(),
                credits,
                command.billingType(),
                command.sourceType(),
                command.sourceId(),
                command.occurredAt() == null ? Instant.now() : command.occurredAt(),
                writeJson(command.metadata())));

        if (credits.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal balanceAfter = subscription.getRemainingCredits().subtract(credits).max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
            creditLedgerRepository.save(new BillingCreditLedgerEntity(
                    command.orgId(),
                    "usage_debit",
                    credits.negate(),
                    balanceAfter,
                    event.getId(),
                    command.description(),
                    event.getOccurredAt(),
                    writeJson(Map.of(
                            "billableDomain", command.domain(),
                            "billableItemCode", command.itemCode(),
                            "billingType", command.billingType(),
                            "rateCardVersion", RATE_CARD_VERSION
                    ))));
        }
        adminBillingService.refreshSubscriptionBalance(subscription);
        return event;
    }

    private RateCardItem rateCardItem(String domain, String itemCode) {
        return RATE_CARD.stream()
                .filter(item -> item.domain().equals(domain) && item.itemCode().equals(itemCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown billing rate card item: " + domain + "/" + itemCode));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to serialize billing usage metadata");
        }
    }

    private static final List<RateCardItem> RATE_CARD = List.of(
            new RateCardItem("assistant_chat", "standard_message", "普通助手消息，未调用工具/知识库", "message",
                    new BigDecimal("1.00"), "platform_paid", "FEAT-022 / Work Credits / Action: 普通助手消息", true),
            new RateCardItem("assistant_chat", "rag_message", "启用知识库检索的助手消息", "message",
                    new BigDecimal("2.00"), "platform_paid", "FEAT-022 / Work Credits / Action: 启用知识库检索的助手消息", true),
            new RateCardItem("model_usage", "standard_model_1k_tokens", "标准模型推理 token envelope", "1k_tokens",
                    new BigDecimal("1.00"), "platform_paid", "FEAT-037 / Rating: model_usage", true),
            new RateCardItem("model_usage", "advanced_model_message", "高级模型或深度推理消息", "message",
                    new BigDecimal("4.00"), "platform_paid", "FEAT-022 / Work Credits / Action: 高级模型或深度推理消息", false),
            new RateCardItem("rag_retrieval", "kb_retrieval", "知识库检索", "retrieval",
                    new BigDecimal("1.00"), "included", "FEAT-037 / Rating: rag_retrieval", true),
            new RateCardItem("tool_call", "readonly_tool", "单次内置工具只读调用", "call",
                    new BigDecimal("1.00"), "included", "FEAT-022 / Work Credits / Action: 单次内置工具只读调用", false),
            new RateCardItem("tool_call", "write_tool", "单次业务写入或外部副作用工具调用", "call",
                    new BigDecimal("3.00"), "platform_paid", "FEAT-022 / Work Credits / Action: 单次业务写入或外部副作用工具调用", false),
            new RateCardItem("tool_call", "platform_paid_search", "单次平台代付第三方搜索/富化", "call",
                    new BigDecimal("5.00"), "platform_paid", "FEAT-022 / Work Credits / Action: 单次平台代付第三方搜索/富化", false),
            new RateCardItem("workflow_run", "standard_workflow_run", "一次标准工作流运行", "run",
                    new BigDecimal("3.00"), "included", "FEAT-037 / Rating: workflow_run", false),
            new RateCardItem("open_api_chat", "non_stream_chat", "一次 OpenAPI non-stream chat", "request",
                    new BigDecimal("2.00"), "platform_paid", "FEAT-022 / Work Credits / Action: Open API non-stream chat", false),
            new RateCardItem("open_api_chat", "stream_chat", "一次 OpenAPI stream chat", "request",
                    new BigDecimal("3.00"), "platform_paid", "FEAT-022 / Work Credits / Action: Open API stream chat", false),
            new RateCardItem("kb_indexing", "document_mb", "文档上传索引", "mb",
                    new BigDecimal("1.00"), "included", "FEAT-037 / Rating: kb_indexing", false)
    );

    private record RateCardItem(String domain,
                                String itemCode,
                                String displayName,
                                String unit,
                                BigDecimal creditsPerUnit,
                                String billingType,
                                String officialPricingSection,
                                boolean productionReady) {
    }

    public record RateCardItemView(String rateCardVersion,
                                   String domain,
                                   String itemCode,
                                   String displayName,
                                   String unit,
                                   BigDecimal creditsPerUnit,
                                   String billingType,
                                   String officialPricingSection,
                                   boolean productionReady) {
    }

    public record UsageCommand(String orgId,
                               String userId,
                               String agentId,
                               String domain,
                               String itemCode,
                               String description,
                               BigDecimal quantity,
                               String unit,
                               String billingType,
                               String sourceType,
                               String sourceId,
                               Instant occurredAt,
                               Map<String, Object> metadata) {
    }

    public record ChatUsageCommand(String orgId,
                                   String userId,
                                   String agentId,
                                   String sessionId,
                                   String providerCode,
                                   String modelName,
                                   int promptTokens,
                                   int completionTokens,
                                   int ragChunkCount,
                                   int toolCallCount,
                                   String billingType,
                                   String sourceId,
                                   Instant occurredAt) {
    }
}
