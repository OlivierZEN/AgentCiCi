package com.codehouse.ciciassistant.datainsight.service;

import com.codehouse.ciciassistant.customer.domain.CustomerInteractionEventEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerInteractionEventRepository;
import com.codehouse.ciciassistant.customer.domain.CustomerWorkbenchRecommendationEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerWorkbenchRecommendationRepository;
import com.codehouse.ciciassistant.customer.domain.CustomerWorkbenchSnapshotEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerWorkbenchSnapshotRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataInsightService {

    private static final String DEMO_ORG_ID = "org2sva14i4udjmi2t4s";
    private static final TypeReference<Map<String, Object>> MAP_REF = new TypeReference<>() {};

    private final CustomerWorkbenchSnapshotRepository workbenchSnapshotRepository;
    private final CustomerInteractionEventRepository interactionEventRepository;
    private final CustomerWorkbenchRecommendationRepository recommendationRepository;
    private final ObjectMapper objectMapper;

    public DataInsightService(CustomerWorkbenchSnapshotRepository workbenchSnapshotRepository,
                              CustomerInteractionEventRepository interactionEventRepository,
                              CustomerWorkbenchRecommendationRepository recommendationRepository,
                              ObjectMapper objectMapper) {
        this.workbenchSnapshotRepository = workbenchSnapshotRepository;
        this.interactionEventRepository = interactionEventRepository;
        this.recommendationRepository = recommendationRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(String orgId) {
        List<CustomerWorkbenchSnapshotEntity> snapshots = workbenchSnapshotRepository.findByOrgIdOrderByUpdatedAtDesc(orgId);
        if (snapshots.isEmpty()) {
            return mockDashboard(orgId);
        }
        List<CustomerInteractionEventEntity> events = interactionEventRepository.findByOrgIdOrderByOccurredAtDesc(orgId);
        List<CustomerWorkbenchRecommendationEntity> recommendations =
                recommendationRepository.findByOrgIdOrderByUpdatedAtDesc(orgId);

        List<AccountMetric> accounts = snapshots.stream().map(this::accountMetric).toList();
        long totalPipeline = accounts.stream().mapToLong(AccountMetric::pipelineAmount).sum();
        long contractAmount = accounts.stream().mapToLong(AccountMetric::contractAmount).sum();
        long paidAmount = accounts.stream().mapToLong(AccountMetric::paidAmount).sum();
        long orderAmount = accounts.stream().mapToLong(AccountMetric::orderAmount).sum();
        long orderCount = accounts.stream().mapToLong(AccountMetric::orderCount).sum();
        long riskCustomers = accounts.stream().filter(item -> item.riskCount() > 0 || "RISK".equals(item.segment())).count();
        long highConfidenceRecommendations = recommendations.stream()
                .filter(item -> item.getConfidence() != null && item.getConfidence().doubleValue() >= 0.8)
                .count();
        long newCustomers = accounts.stream().filter(item -> "NEW".equals(item.segment())).count();
        long riskSegment = accounts.stream().filter(item -> "RISK".equals(item.segment())).count();
        long strategicCustomers = accounts.stream().filter(item -> "STRATEGIC".equals(item.segment())).count();
        long existingCustomers = accounts.stream().filter(item -> "EXISTING".equals(item.segment())).count();
        long totalLeads = Math.max(newCustomers + riskSegment, DEMO_ORG_ID.equals(orgId) ? 6 : Math.max(1, newCustomers));
        long openOpportunities = Math.max(1, accounts.size() - riskSegment);
        long paymentTarget = Math.max(paidAmount + 1, Math.round(contractAmount * 1.35));
        int avgHealth = (int) Math.round(accounts.stream().mapToInt(AccountMetric::healthScore).average().orElse(0));
        int avgProgress = (int) Math.round(accounts.stream().mapToInt(AccountMetric::progressScore).average().orElse(0));
        int winRate = Math.max(18, Math.min(86, (avgHealth + avgProgress) / 2));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sourceMode", DEMO_ORG_ID.equals(orgId) ? "REAL_CRM_DEMO" : "REAL_AGGREGATE");
        data.put("sourceLabel", DEMO_ORG_ID.equals(orgId) ? "智能体平台演示环境 · CRM 真实模拟数据" : "组织 CRM 聚合数据");
        data.put("sourceDescription", DEMO_ORG_ID.equals(orgId)
                ? "客户、联系人、商机、任务和互动来自绑定 CloudCC CRM 演示批次。"
                : "基于当前组织客户工作台聚合数据生成。");
        data.put("updatedAt", Instant.now().toString());
        data.put("context", context(DEMO_ORG_ID.equals(orgId)));
        data.put("summary", linkedMap(
                "totalCustomers", accounts.size(),
                "totalLeads", totalLeads,
                "openOpportunities", openOpportunities,
                "pipelineAmount", totalPipeline,
                "contractAmount", contractAmount,
                "paidAmount", paidAmount,
                "paymentTargetAmount", paymentTarget,
                "orderAmount", orderAmount,
                "orderCount", orderCount,
                "winRate", winRate,
                "paymentAchievementRate", Math.round((paidAmount * 1000.0) / paymentTarget) / 10.0,
                "avgHealth", avgHealth,
                "riskCustomers", riskCustomers,
                "interactionCount", events.size(),
                "recommendationCount", recommendations.size(),
                "highConfidenceRecommendationCount", highConfidenceRecommendations
        ));
        data.put("funnel", funnel(totalLeads, accounts.size(), openOpportunities, totalPipeline, contractAmount, orderAmount));
        data.put("segments", List.of(
                segment("NEW", "新客户推进", newCustomers, "#b58b2a"),
                segment("EXISTING", "老客户经营", existingCustomers, "#4e8d65"),
                segment("RISK", "风险挽回", riskSegment, "#c06d28"),
                segment("STRATEGIC", "战略客户", strategicCustomers, "#5b7fd6")
        ));
        data.put("trend", revenueTrend(totalPipeline, contractAmount, orderAmount, paidAmount, events.size()));
        data.put("rankings", rankings(accounts));
        data.put("geoDistribution", geoDistribution(accounts));
        data.put("accounts", accounts.stream().limit(10).map(AccountMetric::view).toList());
        data.put("risks", accounts.stream()
                .sorted(Comparator.comparingInt(AccountMetric::riskCount).reversed()
                        .thenComparing(Comparator.comparingInt(AccountMetric::healthScore)))
                .limit(8)
                .map(AccountMetric::riskView)
                .toList());
        data.put("recommendations", recommendations.stream()
                .sorted(Comparator.comparing(CustomerWorkbenchRecommendationEntity::getUpdatedAt).reversed())
                .limit(6)
                .map(this::recommendationView)
                .toList());
        return data;
    }

    private Map<String, Object> mockDashboard(String orgId) {
        long seed = Math.abs((orgId == null ? "mock" : orgId).hashCode());
        List<AccountMetric> accounts = List.of(
                new AccountMetric("mock-001", "北京智造科技有限公司", "制造业", "NEW", "华北", "方案评审", 82, 86, 1, 3, 1_760_000 + seed % 80_000, 680_000, 410_000, 520_000, 2, "MES 集成评审"),
                new AccountMetric("mock-002", "上海云链信息技术有限公司", "软件服务", "RISK", "华东", "续约挽回", 48, 58, 3, 2, 960_000, 720_000, 360_000, 480_000, 1, "续约风险"),
                new AccountMetric("mock-003", "广州海创智联有限公司", "装备制造", "EXISTING", "华南", "增购识别", 88, 70, 1, 2, 1_240_000, 940_000, 610_000, 760_000, 3, "增购机会"),
                new AccountMetric("mock-004", "深圳未来视界科技有限公司", "高科技", "NEW", "华南", "竞品比较", 64, 75, 1, 3, 1_780_000, 320_000, 120_000, 260_000, 1, "竞品比较"),
                new AccountMetric("mock-005", "南京星河软件有限公司", "软件服务", "NEW", "华东", "初步接触", 68, 61, 1, 3, 1_450_000, 260_000, 90_000, 180_000, 1, "初步接触")
        );
        long pipeline = accounts.stream().mapToLong(AccountMetric::pipelineAmount).sum();
        long contract = accounts.stream().mapToLong(AccountMetric::contractAmount).sum();
        long paid = accounts.stream().mapToLong(AccountMetric::paidAmount).sum();
        long order = accounts.stream().mapToLong(AccountMetric::orderAmount).sum();
        long target = Math.round(contract * 1.35);
        return linkedMap(
                "sourceMode", "MOCK",
                "sourceLabel", "演示样例",
                "sourceDescription", "当前组织暂无 CRM 聚合数据。",
                "updatedAt", Instant.now().toString(),
                "context", context(false),
                "summary", linkedMap(
                        "totalCustomers", 12,
                        "totalLeads", 18,
                        "openOpportunities", 9,
                        "pipelineAmount", pipeline,
                        "contractAmount", contract,
                        "paidAmount", paid,
                        "paymentTargetAmount", target,
                        "orderAmount", order,
                        "orderCount", 8,
                        "winRate", 64,
                        "paymentAchievementRate", Math.round((paid * 1000.0) / target) / 10.0,
                        "avgHealth", 73,
                        "riskCustomers", 3,
                        "interactionCount", 36,
                        "recommendationCount", 22,
                        "highConfidenceRecommendationCount", 14
                ),
                "funnel", funnel(18, 12, 9, pipeline, contract, order),
                "segments", List.of(
                        segment("NEW", "新客户推进", 4, "#b58b2a"),
                        segment("EXISTING", "老客户经营", 5, "#4e8d65"),
                        segment("RISK", "风险挽回", 2, "#c06d28"),
                        segment("STRATEGIC", "战略客户", 1, "#5b7fd6")
                ),
                "trend", revenueTrend(pipeline, contract, order, paid, 36),
                "rankings", rankings(accounts),
                "geoDistribution", geoDistribution(accounts),
                "accounts", accounts.stream().map(AccountMetric::view).toList(),
                "risks", accounts.stream().map(AccountMetric::riskView).toList(),
                "recommendations", List.of()
        );
    }

    private AccountMetric accountMetric(CustomerWorkbenchSnapshotEntity snapshot) {
        Map<String, Object> raw = readMap(snapshot.getSnapshotJson());
        int weight = Math.abs(snapshot.getCrmAccountId().hashCode() % 37);
        long pipeline = 360_000L + snapshot.getProgressScore() * 16_000L + weight * 9_000L;
        if ("STRATEGIC".equals(snapshot.getSegment())) {
            pipeline += 960_000L;
        }
        if ("RISK".equals(snapshot.getSegment())) {
            pipeline = Math.round(pipeline * 0.72);
        }
        long contract = switch (snapshot.getSegment()) {
            case "NEW" -> Math.round(pipeline * 0.18);
            case "RISK" -> Math.round(pipeline * 0.46);
            case "STRATEGIC" -> Math.round(pipeline * 0.64);
            default -> Math.round(pipeline * 0.58);
        };
        long paid = Math.round(contract * ("RISK".equals(snapshot.getSegment()) ? 0.48 : 0.64));
        long order = Math.round(contract * ("RISK".equals(snapshot.getSegment()) ? 0.54 : 0.78));
        long orderCount = Math.max(1, order / 260_000L);
        return new AccountMetric(
                snapshot.getCrmAccountId(),
                snapshot.getAccountName(),
                String.valueOf(raw.getOrDefault("industry", "待补充行业")),
                snapshot.getSegment(),
                String.valueOf(raw.getOrDefault("region", regionFor(snapshot.getAccountName()))),
                String.valueOf(raw.getOrDefault("stage", "经营跟进")),
                snapshot.getHealthScore(),
                snapshot.getProgressScore(),
                snapshot.getRiskCount(),
                snapshot.getNextActionCount(),
                pipeline,
                contract,
                paid,
                order,
                orderCount,
                String.valueOf(raw.getOrDefault("summary", "暂无摘要"))
        );
    }

    private Map<String, Object> context(boolean demo) {
        return linkedMap(
                "userName", "郑岩",
                "orgName", demo ? "北京神州云动内部系统DEMO环境挂载专用" : "当前组织",
                "currency", "CNY",
                "dashboardName", "销售云主页"
        );
    }

    private List<Map<String, Object>> funnel(long leads, long customers, long opportunities, long pipelineAmount, long contractAmount, long orderAmount) {
        long qualified = Math.max(customers, Math.round(leads * 0.72));
        long proposals = Math.max(1, Math.round(opportunities * 0.68));
        long contracts = Math.max(1, contractAmount / 720_000L);
        long orders = Math.max(1, orderAmount / 620_000L);
        return List.of(
                linkedMap("code", "leads", "label", "潜在客户", "value", leads, "amount", 0),
                linkedMap("code", "qualified", "label", "有效客户", "value", qualified, "amount", 0),
                linkedMap("code", "opportunities", "label", "活跃商机", "value", opportunities, "amount", pipelineAmount),
                linkedMap("code", "proposal", "label", "方案报价", "value", proposals, "amount", Math.round(pipelineAmount * 0.72)),
                linkedMap("code", "contract", "label", "签约合同", "value", contracts, "amount", contractAmount),
                linkedMap("code", "order", "label", "履约订单", "value", orders, "amount", orderAmount)
        );
    }

    private List<Map<String, Object>> revenueTrend(long pipelineAmount, long contractAmount, long orderAmount, long paidAmount, int eventCount) {
        String[] months = {"02月", "03月", "04月", "05月", "06月", "07月"};
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < months.length; i++) {
            double factor = 0.58 + i * 0.084;
            rows.add(linkedMap(
                    "month", months[i],
                    "pipeline", Math.round((pipelineAmount / 6.0) * factor),
                    "contract", Math.round((contractAmount / 6.0) * (factor - 0.08)),
                    "order", Math.round((orderAmount / 6.0) * (factor - 0.12)),
                    "paid", Math.round((paidAmount / 6.0) * (factor - 0.1)),
                    "interactions", Math.max(1, Math.round(eventCount / 6.0 + i % 3))
            ));
        }
        return rows;
    }

    private Map<String, Object> rankings(List<AccountMetric> accounts) {
        return linkedMap(
                "customerCount", rank(regionCounts(accounts), "count").stream().limit(10).toList(),
                "contractAmount", accounts.stream().sorted(Comparator.comparingLong(AccountMetric::contractAmount).reversed()).limit(10)
                        .map(item -> linkedMap("label", item.accountName(), "value", item.contractAmount())).toList(),
                "orderAmount", accounts.stream().sorted(Comparator.comparingLong(AccountMetric::orderAmount).reversed()).limit(10)
                        .map(item -> linkedMap("label", item.accountName(), "value", item.orderAmount())).toList(),
                "opportunityAmount", accounts.stream().sorted(Comparator.comparingLong(AccountMetric::pipelineAmount).reversed()).limit(10)
                        .map(item -> linkedMap("label", item.accountName(), "value", item.pipelineAmount())).toList()
        );
    }

    private List<Map<String, Object>> rank(Map<String, Long> values, String valueKey) {
        return values.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(item -> linkedMap("label", item.getKey(), "value", item.getValue()))
                .toList();
    }

    private Map<String, Long> regionCounts(List<AccountMetric> accounts) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (AccountMetric account : accounts) {
            counts.merge(account.region(), 1L, Long::sum);
        }
        return counts;
    }

    private List<Map<String, Object>> geoDistribution(List<AccountMetric> accounts) {
        Map<String, long[]> stats = new LinkedHashMap<>();
        for (AccountMetric account : accounts) {
            long[] row = stats.computeIfAbsent(account.region(), key -> new long[2]);
            row[0] += 1;
            row[1] += account.contractAmount();
        }
        if (stats.isEmpty()) {
            stats.put("华北", new long[]{2, 1_600_000});
            stats.put("华东", new long[]{4, 2_700_000});
            stats.put("华南", new long[]{3, 2_100_000});
            stats.put("西南", new long[]{1, 620_000});
        }
        String[] tones = {"teal", "green", "blue", "amber"};
        List<Map<String, Object>> rows = new ArrayList<>();
        int index = 0;
        for (Map.Entry<String, long[]> entry : stats.entrySet()) {
            rows.add(linkedMap(
                    "region", entry.getKey(),
                    "value", entry.getValue()[0],
                    "amount", entry.getValue()[1],
                    "tone", tones[index % tones.length]
            ));
            index++;
        }
        return rows;
    }

    private Map<String, Object> segment(String code, String label, long value, String color) {
        return linkedMap("code", code, "label", label, "value", value, "color", color);
    }

    private Map<String, Object> recommendationView(CustomerWorkbenchRecommendationEntity item) {
        return linkedMap(
                "title", item.getTitle(),
                "accountId", item.getCrmAccountId(),
                "type", item.getRecommendationType(),
                "confidence", item.getConfidence(),
                "status", item.getStatus(),
                "updatedAt", item.getUpdatedAt().toString()
        );
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_REF);
        } catch (Exception ex) {
            return Map.of("raw", json);
        }
    }

    private static String regionFor(String name) {
        String text = name == null ? "" : name;
        if (text.contains("北京") || text.contains("天津") || text.contains("河北")) return "华北";
        if (text.contains("上海") || text.contains("南京") || text.contains("杭州") || text.contains("宁波")) return "华东";
        if (text.contains("深圳") || text.contains("广州")) return "华南";
        if (text.contains("成都") || text.contains("重庆")) return "西南";
        return "全国";
    }

    private static Map<String, Object> linkedMap(Object... values) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            data.put(String.valueOf(values[i]), values[i + 1]);
        }
        return data;
    }

    private record AccountMetric(String accountId,
                                 String accountName,
                                 String industry,
                                 String segment,
                                 String region,
                                 String stage,
                                 int healthScore,
                                 int progressScore,
                                 int riskCount,
                                 int nextActionCount,
                                 long pipelineAmount,
                                 long contractAmount,
                                 long paidAmount,
                                 long orderAmount,
                                 long orderCount,
                                 String summary) {
        Map<String, Object> view() {
            return linkedMap(
                    "accountId", accountId,
                    "accountName", accountName,
                    "industry", industry,
                    "segment", segment,
                    "segmentLabel", segmentLabel(segment),
                    "owner", region,
                    "stage", stage,
                    "healthScore", healthScore,
                    "progressScore", progressScore,
                    "riskCount", riskCount,
                    "nextActionCount", nextActionCount,
                    "pipelineAmount", pipelineAmount,
                    "contractAmount", contractAmount,
                    "paidAmount", paidAmount,
                    "orderAmount", orderAmount,
                    "orderCount", orderCount,
                    "summary", summary
            );
        }

        Map<String, Object> riskView() {
            return linkedMap(
                    "accountId", accountId,
                    "accountName", accountName,
                    "riskLevel", riskCount >= 3 || healthScore < 55 ? "HIGH" : riskCount >= 2 ? "MEDIUM" : "LOW",
                    "riskCount", riskCount,
                    "healthScore", healthScore,
                    "nextActionCount", nextActionCount,
                    "summary", summary
            );
        }

        private static String segmentLabel(String segment) {
            return switch ((segment == null ? "" : segment).toUpperCase(Locale.ROOT)) {
                case "NEW" -> "新客户推进";
                case "EXISTING" -> "老客户经营";
                case "RISK" -> "风险挽回";
                case "STRATEGIC" -> "战略客户";
                default -> "经营客户";
            };
        }
    }
}

