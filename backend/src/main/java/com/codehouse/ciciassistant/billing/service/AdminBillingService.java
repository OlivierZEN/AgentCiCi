package com.codehouse.ciciassistant.billing.service;

import com.codehouse.ciciassistant.billing.config.BillingModeProperties;
import com.codehouse.ciciassistant.billing.domain.BillingCreditLedgerEntity;
import com.codehouse.ciciassistant.billing.domain.BillingCreditLedgerRepository;
import com.codehouse.ciciassistant.billing.domain.BillingEditionEntity;
import com.codehouse.ciciassistant.billing.domain.BillingEditionRepository;
import com.codehouse.ciciassistant.billing.domain.BillingPackageEntity;
import com.codehouse.ciciassistant.billing.domain.BillingPackageRepository;
import com.codehouse.ciciassistant.billing.domain.BillingSubscriptionEntity;
import com.codehouse.ciciassistant.billing.domain.BillingSubscriptionRepository;
import com.codehouse.ciciassistant.billing.domain.UsageMeterEventEntity;
import com.codehouse.ciciassistant.billing.domain.UsageMeterEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminBillingService {

    private static final BigDecimal PRIVATE_CONTRACT_CREDITS = new BigDecimal("50000.00");

    private final BillingSubscriptionRepository subscriptionRepository;
    private final BillingEditionRepository editionRepository;
    private final BillingPackageRepository packageRepository;
    private final UsageMeterEventRepository usageMeterEventRepository;
    private final BillingCreditLedgerRepository creditLedgerRepository;
    private final BillingEditionConfigurationService configurationService;
    private final BillingUsageMeteringService usageMeteringService;
    private final BillingModeProperties billingModeProperties;
    private final ObjectMapper objectMapper;

    public AdminBillingService(BillingSubscriptionRepository subscriptionRepository,
                               BillingEditionRepository editionRepository,
                               BillingPackageRepository packageRepository,
                               UsageMeterEventRepository usageMeterEventRepository,
                               BillingCreditLedgerRepository creditLedgerRepository,
                               BillingEditionConfigurationService configurationService,
                               BillingUsageMeteringService usageMeteringService,
                               BillingModeProperties billingModeProperties,
                               ObjectMapper objectMapper) {
        this.subscriptionRepository = subscriptionRepository;
        this.editionRepository = editionRepository;
        this.packageRepository = packageRepository;
        this.usageMeterEventRepository = usageMeterEventRepository;
        this.creditLedgerRepository = creditLedgerRepository;
        this.configurationService = configurationService;
        this.usageMeteringService = usageMeteringService;
        this.billingModeProperties = billingModeProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AdminBillingOverviewView overview(String orgId) {
        BillingSubscriptionEntity subscription = ensureBillingState(orgId);
        BillingEditionEntity edition = editionRepository.findByEditionCode(subscription.getEditionCode())
                .orElseThrow();
        List<UsageMeterEventEntity> events = usageMeterEventRepository.findTop100ByOrgIdOrderByOccurredAtDesc(orgId);
        List<BillingCreditLedgerEntity> ledger = creditLedgerRepository.findTop50ByOrgIdOrderByIdDesc(orgId);
        return new AdminBillingOverviewView(
                toSubscriptionView(subscription, edition),
                toCreditSummary(subscription),
                summarizeUsage(events),
                ledger.stream().map(this::toLedgerView).toList(),
                events.stream().limit(20).map(this::toUsageEventView).toList(),
                quotaWarnings(subscription, edition)
        );
    }

    @Transactional
    public AdminSubscriptionView subscription(String orgId) {
        BillingSubscriptionEntity subscription = ensureBillingState(orgId);
        BillingEditionEntity edition = editionRepository.findByEditionCode(subscription.getEditionCode())
                .orElseThrow();
        return toSubscriptionView(subscription, edition);
    }

    @Transactional
    public List<UsageEventView> usageEvents(String orgId) {
        ensureBillingState(orgId);
        return usageMeterEventRepository.findTop100ByOrgIdOrderByOccurredAtDesc(orgId).stream()
                .map(this::toUsageEventView)
                .toList();
    }

    @Transactional
    public List<LedgerEntryView> ledger(String orgId) {
        ensureBillingState(orgId);
        return creditLedgerRepository.findTop50ByOrgIdOrderByIdDesc(orgId).stream()
                .map(this::toLedgerView)
                .toList();
    }

    @Transactional
    public List<QuotaWarningView> quota(String orgId) {
        BillingSubscriptionEntity subscription = ensureBillingState(orgId);
        BillingEditionEntity edition = editionRepository.findByEditionCode(subscription.getEditionCode())
                .orElseThrow();
        return quotaWarnings(subscription, edition);
    }

    BillingSubscriptionEntity ensureBillingState(String orgId) {
        return usageMeteringService.ensureBillingState(orgId);
    }

    private BillingSubscriptionEntity createDefaultSubscription(String orgId) {
        String deploymentMode = billingModeProperties.toView().deploymentMode();
        BillingEditionEntity edition = editionRepository.findFirstByDeploymentModeAndEnabledTrueOrderBySortOrderAscEditionCodeAsc(deploymentMode)
                .orElseGet(() -> editionRepository.findFirstByDeploymentModeAndEnabledTrueOrderBySortOrderAscEditionCodeAsc("private_deployment")
                        .orElseThrow());
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        BillingSubscriptionEntity subscription = new BillingSubscriptionEntity(orgId, edition.getDeploymentMode(), edition.getEditionCode(),
                now.minus(7, ChronoUnit.DAYS), now.plus(358, ChronoUnit.DAYS));
        BigDecimal included = effectiveIncludedCredits(edition);
        subscription.setIncludedCredits(included);
        subscription.setRemainingCredits(included);
        subscription.setOperationSeatsUsed(1);
        subscription.setBuilderSeatsUsed(0);
        subscription.setPackageCodes(edition.getPackageCodes());
        subscription.setUpdatedAt(now);
        return subscriptionRepository.save(subscription);
    }

    private void seedUsageAndLedger(BillingSubscriptionEntity subscription) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        BigDecimal balance = subscription.getIncludedCredits();
        creditLedgerRepository.save(new BillingCreditLedgerEntity(
                subscription.getOrgId(),
                "included_grant",
                subscription.getIncludedCredits(),
                balance,
                null,
                "当前版本周期内含或合同治理 credits 发放",
                subscription.getPeriodStart(),
                writeJson(Map.of("editionCode", subscription.getEditionCode()))));

        List<UsageMeterEventEntity> events = List.of(
                usage(subscription, "assistant_chat", "standard_agent_run", "售后 Agent 标准对话运行", "cici-support", "platform_paid",
                        new BigDecimal("12.50"), now.minus(2, ChronoUnit.HOURS), Map.of("model", "qwen-plus", "messages", 3)),
                usage(subscription, "rag_retrieval", "kb_retrieval", "售后知识库检索", "cici-support", "included",
                        new BigDecimal("4.20"), now.minus(90, ChronoUnit.MINUTES), Map.of("chunks", 8, "knowledgeBase", "售后知识")),
                usage(subscription, "open_api_chat", "stream_chat", "Open API 流式会话", "service-agent", "platform_paid",
                        new BigDecimal("18.00"), now.minus(50, ChronoUnit.MINUTES), Map.of("credential", "prod-openapi", "streaming", true)),
                usage(subscription, "tool_call", "customer_paid_connector", "客户自有 CRM 只读查询", "service-agent", "customer_paid",
                        new BigDecimal("2.00"), now.minus(25, ChronoUnit.MINUTES), Map.of("billingType", "customer_paid", "connector", "crm_readonly"))
        );

        for (UsageMeterEventEntity event : usageMeterEventRepository.saveAll(events)) {
            balance = balance.subtract(event.getWorkCreditQuantity()).setScale(2, RoundingMode.HALF_UP);
            creditLedgerRepository.save(new BillingCreditLedgerEntity(
                    subscription.getOrgId(),
                    "usage_debit",
                    event.getWorkCreditQuantity().negate(),
                    balance,
                    event.getId(),
                    event.getDescription(),
                    event.getOccurredAt(),
                    writeJson(Map.of("billableDomain", event.getBillableDomain(), "billingType", event.getBillingType()))));
        }
    }

    private UsageMeterEventEntity usage(BillingSubscriptionEntity subscription,
                                        String domain,
                                        String itemCode,
                                        String description,
                                        String agentId,
                                        String billingType,
                                        BigDecimal credits,
                                        Instant occurredAt,
                                        Map<String, Object> metadata) {
        return new UsageMeterEventEntity(
                subscription.getOrgId(),
                "system",
                agentId,
                domain,
                itemCode,
                description,
                BigDecimal.ONE,
                "run",
                credits,
                billingType,
                "seed",
                subscription.getOrgId() + ":" + domain + ":" + itemCode,
                occurredAt,
                writeJson(metadata));
    }

    BillingSubscriptionEntity refreshSubscriptionBalance(BillingSubscriptionEntity subscription) {
        BigDecimal consumed = creditLedgerRepository.findByOrgIdOrderByOccurredAtAsc(subscription.getOrgId()).stream()
                .filter(item -> "usage_debit".equals(item.getEntryType()))
                .map(BillingCreditLedgerEntity::getCreditsDelta)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        subscription.setConsumedCredits(consumed);
        subscription.setRemainingCredits(subscription.getIncludedCredits().subtract(consumed).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
        subscription.setUpdatedAt(Instant.now());
        return subscriptionRepository.save(subscription);
    }

    private BigDecimal effectiveIncludedCredits(BillingEditionEntity edition) {
        if (edition.getIncludedCredits() != null && edition.getIncludedCredits().compareTo(BigDecimal.ZERO) > 0) {
            return edition.getIncludedCredits().setScale(2, RoundingMode.HALF_UP);
        }
        return PRIVATE_CONTRACT_CREDITS;
    }

    private AdminSubscriptionView toSubscriptionView(BillingSubscriptionEntity subscription, BillingEditionEntity edition) {
        List<String> packageCodes = readStringList(subscription.getPackageCodes());
        List<String> packageNames = packageCodes.stream()
                .map(code -> packageRepository.findByPackageCode(code).map(BillingPackageEntity::getDisplayName).orElse(code))
                .toList();
        return new AdminSubscriptionView(
                subscription.getOrgId(),
                subscription.getDeploymentMode(),
                deploymentLabel(subscription.getDeploymentMode()),
                subscription.getEditionCode(),
                edition.getDisplayName(),
                subscription.getStatus(),
                subscription.getPeriodStart().toString(),
                subscription.getPeriodEnd().toString(),
                subscription.getIncludedCredits(),
                subscription.getConsumedCredits(),
                subscription.getRemainingCredits(),
                edition.getOverageMode(),
                edition.getTopUpPolicy(),
                edition.getBillingTypePolicy(),
                edition.getLocalModelTokenPolicy(),
                subscription.getOperationSeatsUsed(),
                edition.getOperationSeatLimit(),
                subscription.getBuilderSeatsUsed(),
                edition.getBuilderSeatLimit(),
                edition.getAgentLimit(),
                edition.getOpenApiQps(),
                edition.getTraceRetentionDays(),
                packageNames
        );
    }

    private CreditSummaryView toCreditSummary(BillingSubscriptionEntity subscription) {
        BigDecimal included = subscription.getIncludedCredits();
        BigDecimal percent = included.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : subscription.getConsumedCredits().multiply(new BigDecimal("100")).divide(included, 1, RoundingMode.HALF_UP);
        return new CreditSummaryView(included, subscription.getConsumedCredits(), subscription.getRemainingCredits(), percent);
    }

    private List<UsageDomainView> summarizeUsage(List<UsageMeterEventEntity> events) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (UsageMeterEventEntity event : events) {
            totals.merge(event.getBillableDomain(), event.getWorkCreditQuantity(), BigDecimal::add);
            counts.merge(event.getBillableDomain(), 1, Integer::sum);
        }
        return totals.entrySet().stream()
                .map(entry -> new UsageDomainView(entry.getKey(), domainLabel(entry.getKey()), entry.getValue().setScale(2, RoundingMode.HALF_UP),
                        counts.getOrDefault(entry.getKey(), 0)))
                .sorted(Comparator.comparing(UsageDomainView::credits).reversed())
                .toList();
    }

    private List<QuotaWarningView> quotaWarnings(BillingSubscriptionEntity subscription, BillingEditionEntity edition) {
        List<QuotaWarningView> warnings = new ArrayList<>();
        if (subscription.getIncludedCredits().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal remainingPercent = subscription.getRemainingCredits().multiply(new BigDecimal("100"))
                    .divide(subscription.getIncludedCredits(), 1, RoundingMode.HALF_UP);
            String level = remainingPercent.compareTo(new BigDecimal("10")) <= 0 ? "critical"
                    : remainingPercent.compareTo(new BigDecimal("25")) <= 0 ? "warning" : "ok";
            warnings.add(new QuotaWarningView("credits", "Credits 余额", level,
                    "剩余额度 " + remainingPercent.stripTrailingZeros().toPlainString() + "%"));
        }
        warnings.add(limitWarning("operation_seats", "操作席位", subscription.getOperationSeatsUsed(), edition.getOperationSeatLimit()));
        warnings.add(limitWarning("builder_seats", "构建席位", subscription.getBuilderSeatsUsed(), edition.getBuilderSeatLimit()));
        return warnings;
    }

    private QuotaWarningView limitWarning(String code, String label, int used, Integer limit) {
        if (limit == null || limit == 0) {
            return new QuotaWarningView(code, label, "ok", used + " / 合同约定");
        }
        BigDecimal percent = new BigDecimal(used).multiply(new BigDecimal("100")).divide(new BigDecimal(limit), 1, RoundingMode.HALF_UP);
        String level = percent.compareTo(new BigDecimal("90")) >= 0 ? "critical"
                : percent.compareTo(new BigDecimal("75")) >= 0 ? "warning" : "ok";
        return new QuotaWarningView(code, label, level, used + " / " + limit);
    }

    private LedgerEntryView toLedgerView(BillingCreditLedgerEntity item) {
        return new LedgerEntryView(item.getId(), item.getEntryType(), item.getCreditsDelta(), item.getBalanceAfter(),
                item.getSourceEventId(), item.getDescription(), item.getOccurredAt().toString());
    }

    private UsageEventView toUsageEventView(UsageMeterEventEntity item) {
        Map<String, Object> metadata = readMetadata(item.getMetadataJson());
        return new UsageEventView(item.getId(), item.getBillableDomain(), domainLabel(item.getBillableDomain()),
                item.getBillableItemCode(), item.getDescription(), usageExplanation(item, metadata), quantityLabel(item),
                item.getAgentId(), item.getQuantity(), item.getUnit(), item.getWorkCreditQuantity(), item.getBillingType(),
                readOfficialPricingItem(metadata),
                item.getStatus(), item.getOccurredAt().toString());
    }

    private String deploymentLabel(String mode) {
        return "saas".equals(mode) ? "SaaS" : "私有化";
    }

    private String domainLabel(String domain) {
        return switch (domain) {
            case "assistant_chat" -> "智能体对话";
            case "model_usage" -> "模型推理";
            case "rag_retrieval" -> "知识检索";
            case "tool_call" -> "工具调用";
            case "workflow_run" -> "工作流";
            case "open_api_chat" -> "Open API";
            case "kb_indexing" -> "知识索引";
            default -> domain;
        };
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private Map<String, Object> readMetadata(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    private String readOfficialPricingItem(Map<String, Object> metadata) {
        Object value = metadata.get("officialPricingItem");
        return value == null ? null : String.valueOf(value);
    }

    private String quantityLabel(UsageMeterEventEntity item) {
        return formatDecimal(item.getQuantity()) + " " + item.getUnit();
    }

    private String usageExplanation(UsageMeterEventEntity item, Map<String, Object> metadata) {
        String credits = formatDecimal(item.getWorkCreditQuantity());
        return switch (item.getBillableDomain()) {
            case "assistant_chat" -> "智能体对话：" + quantityLabel(item) + "，消耗 " + credits + " Credits";
            case "model_usage" -> modelUsageExplanation(item, metadata, credits);
            case "rag_retrieval" -> "知识库检索：" + quantityLabel(item) + "，消耗 " + credits + " Credits";
            case "tool_call" -> "工具调用：" + quantityLabel(item) + "，消耗 " + credits + " Credits";
            case "workflow_run" -> "运行治理：" + quantityLabel(item) + "，消耗 " + credits + " Credits";
            case "kb_indexing" -> "文档处理：" + quantityLabel(item) + "，消耗 " + credits + " Credits";
            default -> item.getDescription() + "：" + quantityLabel(item) + "，消耗 " + credits + " Credits";
        };
    }

    private String modelUsageExplanation(UsageMeterEventEntity item, Map<String, Object> metadata, String credits) {
        String modelName = stringValue(metadata.get("modelName"), "模型");
        String inputTokens = stringValue(metadata.get("inputTokens"), "0");
        String outputTokens = stringValue(metadata.get("outputTokens"), "0");
        return "模型推理：" + modelName + " 输入 " + inputTokens + " tokens、输出 " + outputTokens
                + " tokens，消耗 " + credits + " Credits";
    }

    private String stringValue(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private String formatDecimal(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to serialize billing payload");
        }
    }

    public record AdminBillingOverviewView(
            AdminSubscriptionView subscription,
            CreditSummaryView creditSummary,
            List<UsageDomainView> usageByDomain,
            List<LedgerEntryView> recentLedger,
            List<UsageEventView> recentUsageEvents,
            List<QuotaWarningView> quotaWarnings
    ) {
    }

    public record AdminSubscriptionView(
            String orgId,
            String deploymentMode,
            String deploymentModeLabel,
            String editionCode,
            String editionName,
            String status,
            String periodStart,
            String periodEnd,
            BigDecimal includedCredits,
            BigDecimal consumedCredits,
            BigDecimal remainingCredits,
            String overageMode,
            String topUpPolicy,
            String billingTypePolicy,
            String localModelTokenPolicy,
            int operationSeatsUsed,
            Integer operationSeatLimit,
            int builderSeatsUsed,
            Integer builderSeatLimit,
            Integer agentLimit,
            Integer openApiQps,
            Integer traceRetentionDays,
            List<String> packageNames
    ) {
    }

    public record CreditSummaryView(
            BigDecimal includedCredits,
            BigDecimal consumedCredits,
            BigDecimal remainingCredits,
            BigDecimal consumedPercent
    ) {
    }

    public record UsageDomainView(String domain, String label, BigDecimal credits, int eventCount) {
    }

    public record LedgerEntryView(Long id,
                                  String entryType,
                                  BigDecimal creditsDelta,
                                  BigDecimal balanceAfter,
                                  Long sourceEventId,
                                  String description,
                                  String occurredAt) {
    }

    public record UsageEventView(Long id,
                                 String domain,
                                 String domainLabel,
                                 String itemCode,
                                 String description,
                                 String explanation,
                                 String quantityLabel,
                                 String agentId,
                                 BigDecimal quantity,
                                 String unit,
                                 BigDecimal credits,
                                 String billingType,
                                 String officialPricingItem,
                                 String status,
                                 String occurredAt) {
    }

    public record QuotaWarningView(String code, String label, String level, String message) {
    }
}
