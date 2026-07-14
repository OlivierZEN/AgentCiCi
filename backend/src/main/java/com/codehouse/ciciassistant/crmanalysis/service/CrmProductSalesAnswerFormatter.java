package com.codehouse.ciciassistant.crmanalysis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Turns the structured CRM analysis result into the single user-visible business answer.
 * It intentionally never echoes arbitrary warnings or the raw tool payload.
 */
@Service
public class CrmProductSalesAnswerFormatter {

    private static final String GENERIC_UPSTREAM_MESSAGE =
            "暂时无法完成这次 CRM 经营分析。请稍后重试；若持续失败，请检查 CRM 连接和当前账号的数据权限。";
    private static final Set<String> UNSAFE_MARKERS = Set.of(
            "productid", "ownerid", "userid", "tool_result", "toolresult",
            "argumentsjson", "accesstoken", "secret", "cookie", "password"
    );
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper;

    public CrmProductSalesAnswerFormatter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
    }

    public String formatJson(String resultJson) {
        if (resultJson == null || resultJson.isBlank()) {
            return GENERIC_UPSTREAM_MESSAGE;
        }
        try {
            CrmProductSalesAnalysisService.SalesRankResult result = objectMapper.readValue(
                    resultJson, CrmProductSalesAnalysisService.SalesRankResult.class);
            return format(result);
        } catch (Exception ignored) {
            return GENERIC_UPSTREAM_MESSAGE;
        }
    }

    public String format(CrmProductSalesAnalysisService.SalesRankResult result) {
        if (result == null || result.status() == null) {
            return GENERIC_UPSTREAM_MESSAGE;
        }
        return switch (result.status()) {
            case SUCCESS, PARTIAL -> formatAvailableSales(result);
            case EMPTY -> formatEmpty(result);
            case DATA_ACCESS_INCOMPLETE -> formatDataAccessIncomplete(result);
            case DATA_QUALITY_BLOCKED -> formatDataQualityBlocked(result);
            case CRM_NOT_CONNECTED -> "当前成员的 CRM 绑定或连接不可用，因此无法读取产品销售事实。请先重新进入 CloudCC 工作台或检查当前成员的 CRM 账号绑定，然后重试。";
            case PERMISSION_DENIED -> "当前账号没有读取核心销售对象的权限，本次不输出产品排行。请核对产品、订单和订单明细的现有权限范围，无需为此扩大组织级共享。";
            case SCHEMA_UNSUPPORTED -> "当前 CRM 对象或字段结构与标准销售分析口径不兼容，本次不猜测字段、也不输出伪排行。请检查标准产品、订单和订单明细字段是否可用。";
            case UPSTREAM_ERROR -> GENERIC_UPSTREAM_MESSAGE;
        };
    }

    private String formatAvailableSales(CrmProductSalesAnalysisService.SalesRankResult result) {
        List<CrmProductSalesAnalysisService.SalesRankRow> rows = safeRows(result.rows());
        if (rows.isEmpty()) {
            return GENERIC_UPSTREAM_MESSAGE;
        }
        CrmProductSalesAnalysisService.SalesSummary summary = normalizedSummary(result.summary(), rows);
        List<CrmProductSalesAnalysisService.BusinessInsight> insights = availableInsights(
                result.insights(), result.sourceObjects());
        StringBuilder answer = new StringBuilder();
        appendDirectConclusion(answer, result, summary, rows);
        appendRanking(answer, result, summary, rows);
        appendDiagnostics(answer, insights);
        appendForwardSignals(answer, summary, rows, result.sourceObjects());
        appendActions(answer, insights, rows);
        appendCoverage(answer, result, summary, result.sourceObjects());
        return answer.toString().trim();
    }

    private void appendDirectConclusion(StringBuilder answer,
                                        CrmProductSalesAnalysisService.SalesRankResult result,
                                        CrmProductSalesAnalysisService.SalesSummary summary,
                                        List<CrmProductSalesAnalysisService.SalesRankRow> rows) {
        CrmProductSalesAnalysisService.SalesLeader quantityLeader = summary.quantityLeader();
        CrmProductSalesAnalysisService.SalesLeader amountLeader = summary.amountLeader();
        answer.append("### 直接结论\n\n")
                .append(dateRange(result)).append("，");
        if (quantityLeader != null) {
            answer.append("销量冠军：")
                    .append(label(quantityLeader.productName(), "未识别产品"))
                    .append("（").append(number(quantityLeader.value())).append(' ')
                    .append(unit(quantityLeader.unit())).append("）")
                    .append("；");
        }
        if (summary.amountComparable() && amountLeader != null) {
            answer.append("订单销售额冠军：")
                    .append(label(amountLeader.productName(), "未识别产品"))
                    .append("（").append(amount(amountLeader.value(), summary.currency())).append("）。");
            if (quantityLeader != null
                    && !label(quantityLeader.productCode(), "").equals(label(amountLeader.productCode(), ""))) {
                answer.append("量与值的冠军不同，建议分别管理交付保障与高价值场景复制。");
            }
        } else {
            answer.append("由于存在未折算的多币种，本次只比较净销量，不合并金额。");
        }
        if (result.status() == CrmProductSalesAnalysisService.ResultStatus.PARTIAL) {
            answer.append("\n\n> 部分增强数据不可用；下方排行仍基于可验证的订单销售事实，未用模型补造数值。");
        }
        if (rows.size() == 1) {
            answer.append("\n\n当前仅有 1 个产品具备可验证的有效销售事实。");
        }
    }

    private void appendRanking(StringBuilder answer,
                               CrmProductSalesAnalysisService.SalesRankResult result,
                               CrmProductSalesAnalysisService.SalesSummary summary,
                               List<CrmProductSalesAnalysisService.SalesRankRow> rows) {
        answer.append("\n\n### 产品 Top 5\n\n")
                .append("| 排名 | 产品 | 净销量 | 订单销售额 | 数量/金额贡献 | 较上期 | 订单/客户 |\n")
                .append("|---:|---|---:|---:|---:|---:|---:|\n");
        for (CrmProductSalesAnalysisService.SalesRankRow row : rows) {
            answer.append('|').append(' ').append(Math.max(1, row.rank())).append(' ')
                    .append('|').append(' ').append(productLabel(row)).append(' ')
                    .append('|').append(' ').append(number(row.salesQuantity())).append(' ')
                    .append(unit(row.unit())).append(' ')
                    .append('|').append(' ')
                    .append(summary.amountComparable() ? amount(row.salesAmount(), summary.currency()) : "不可比")
                    .append(' ')
                    .append('|').append(' ').append(percent(row.quantityContributionRate())).append(" / ")
                    .append(summary.amountComparable() ? percent(row.amountContributionRate()) : "-").append(' ')
                    .append('|').append(' ').append(change(row.changeRate())).append(' ')
                    .append('|').append(' ').append(Math.max(0, row.orderCount())).append(" / ")
                    .append(Math.max(0, row.customerCount())).append(" |\n");
        }
        answer.append("\n口径：排行指标为 ")
                .append(metricName(result.metric()))
                .append("；贡献率分母使用 Top N 截断前的全量有效销售事实。");
    }

    private void appendDiagnostics(StringBuilder answer,
                                   List<CrmProductSalesAnalysisService.BusinessInsight> rawInsights) {
        List<CrmProductSalesAnalysisService.BusinessInsight> insights = safeInsights(rawInsights);
        answer.append("\n\n### 经营诊断\n\n");
        if (insights.isEmpty()) {
            answer.append("- 当前可验证事实未触发明确的量价、客户集中度、管道断层或续约风险规则，暂不生成模板化结论。");
            return;
        }
        for (CrmProductSalesAnalysisService.BusinessInsight insight : insights) {
            answer.append("- **").append(businessText(insight.conclusion(), "需要关注的经营信号"))
                    .append("**：")
                    .append(businessText(insight.evidence(), "该信号由当前可验证销售事实触发"))
                    .append("。\n");
        }
    }

    private void appendForwardSignals(StringBuilder answer,
                                      CrmProductSalesAnalysisService.SalesSummary summary,
                                      List<CrmProductSalesAnalysisService.SalesRankRow> rows,
                                      List<String> sourceObjects) {
        answer.append("\n### 前瞻信号\n\n");
        boolean pipelineAvailable = hasSource(sourceObjects, "Opportunity")
                && hasSource(sourceObjects, "opportunitypdt");
        boolean contractsAvailable = hasSource(sourceObjects, "contract");
        if (!pipelineAvailable) {
            answer.append("- 商机前瞻不可用：当前用户未能完整读取商机与商机产品；不将缺失解读为零管道。\n");
        }
        if (!contractsAvailable) {
            answer.append("- 合同信号不可用：当前用户未能读取合同增强数据；不将缺失解读为零合同。\n");
        }
        boolean appended = false;
        for (CrmProductSalesAnalysisService.SalesRankRow row : rows) {
            CrmProductSalesAnalysisService.ProductPipelineSignal pipeline = row.pipeline();
            CrmProductSalesAnalysisService.ProductContractSignal contracts = row.contracts();
            if (pipeline == null) {
                pipeline = CrmProductSalesAnalysisService.ProductPipelineSignal.empty();
            }
            if (contracts == null) {
                contracts = CrmProductSalesAnalysisService.ProductContractSignal.empty();
            }
            boolean hasPipelineSignal = pipelineAvailable && pipeline.openOpportunityCount() > 0;
            boolean hasContractSignal = contractsAvailable && contracts.activeContractCount() > 0;
            if (!hasPipelineSignal && !hasContractSignal) {
                continue;
            }
            appended = true;
            answer.append("- **").append(productLabel(row)).append("**：");
            if (hasPipelineSignal) {
                answer.append("开放商机 ")
                        .append(Math.max(0, pipeline.openOpportunityCount())).append(" 个，管道数量 ")
                        .append(number(pipeline.quantity())).append(' ').append(unit(row.unit()));
                if (summary.amountComparable()) {
                    answer.append("，管道金额 ").append(amount(pipeline.amount(), summary.currency()));
                }
                if (pipeline.nearestExpectedCloseDate() != null) {
                    answer.append("，最近预计签约 ").append(pipeline.nearestExpectedCloseDate());
                }
            }
            if (hasPipelineSignal && hasContractSignal) {
                answer.append("；");
            }
            if (hasContractSignal) {
                answer.append("活跃合同 ").append(Math.max(0, contracts.activeContractCount()))
                        .append(" 份，90 天内到期 ")
                        .append(Math.max(0, contracts.expiringWithin90DaysCount())).append(" 份，其中未关联续约商机 ")
                        .append(Math.max(0, contracts.expiringWithoutRenewalCount())).append(" 份");
            }
            answer.append("。\n");
        }
        if (!appended && pipelineAvailable && contractsAvailable) {
            answer.append("- 当前可见范围内未发现与 Top 产品直接关联的开放商机产品或活跃合同；这是覆盖信号，不等同于全组织没有管道。");
        }
    }

    private void appendActions(StringBuilder answer,
                               List<CrmProductSalesAnalysisService.BusinessInsight> rawInsights,
                               List<CrmProductSalesAnalysisService.SalesRankRow> rows) {
        answer.append("\n\n### 建议动作\n\n");
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (CrmProductSalesAnalysisService.BusinessInsight insight : safeInsights(rawInsights)) {
            actions.add(businessText(insight.action(), ""));
        }
        actions.removeIf(String::isBlank);
        if (actions.isEmpty()) {
            CrmProductSalesAnalysisService.SalesRankRow leader = rows.getFirst();
            actions.add("针对 " + productLabel(leader) + " 复核交付能力、库存与已购客户增购清单");
        }
        int index = 1;
        for (String action : actions.stream().limit(3).toList()) {
            answer.append(index++).append(". ").append(action).append("。\n");
        }
    }

    private void appendCoverage(StringBuilder answer,
                                CrmProductSalesAnalysisService.SalesRankResult result,
                                CrmProductSalesAnalysisService.SalesSummary summary,
                                List<String> sourceObjects) {
        CrmProductSalesAnalysisService.Coverage coverage = result.coverage() == null
                ? new CrmProductSalesAnalysisService.Coverage(0, 0, 0, 0, 0, 0)
                : result.coverage();
        answer.append("\n### 口径与覆盖\n\n")
                .append("- 统计期：").append(dateRange(result)).append("；数据截止：")
                .append(dataAsOf(result.dataAsOf())).append("。\n")
                .append("- 已排除草稿、取消、作废、退回和关闭等无效订单；负数退货从净销量和订单销售额中扣减。\n")
                .append("- 扫描 ").append(Math.max(0, coverage.scannedOrders())).append(" 张订单、")
                .append(Math.max(0, coverage.scannedItems())).append(" 条明细，计入 ")
                .append(Math.max(0, coverage.includedOrders())).append(" 张订单、")
                .append(Math.max(0, coverage.includedItems())).append(" 条明细。\n")
                .append("- 数据来源：当前用户有权访问的")
                .append(sourceLabels(sourceObjects))
                .append("。订单销售额是经营口径，不等同于履约后财务报表中的已确认收入。");
        if (!summary.amountComparable()) {
            answer.append("\n- 币种：存在多币种且未提供可靠汇率，所以金额、均价和基于金额的集中度未合并。");
        }
    }

    private String formatEmpty(CrmProductSalesAnalysisService.SalesRankResult result) {
        return dateRange(result) + " 在有效订单口径下没有可计入的有效销售事实。"
                + "这表示当前可见数据在该期间内为空，不代表其他权限范围也没有销售。"
                + "可扩大到近 60 天、近 90 天或本季度后重新查看。";
    }

    private String formatDataAccessIncomplete(CrmProductSalesAnalysisService.SalesRankResult result) {
        CrmProductSalesAnalysisService.Coverage coverage = result.coverage();
        int orders = coverage == null ? 0 : Math.max(0, coverage.scannedOrders());
        int items = coverage == null ? 0 : Math.max(0, coverage.scannedItems());
        return "当前账号的数据权限覆盖不完整，不能据此判断“没有销售”。"
                + "本次可见 " + items + " 条订单明细，但可见订单主表为 " + orders
                + " 张，或关联产品主数据不完整，无法可靠完成日期、状态和客户过滤。"
                + "请优先核对这批订单、明细和产品是否对当前销售人员保持一致的所有权与关联可见性。";
    }

    private String formatDataQualityBlocked(CrmProductSalesAnalysisService.SalesRankResult result) {
        return dateRange(result) + " 的可见销售事实存在数据质量问题会导致排行失真，"
                + "例如多币种未折算或核心引用大量缺失，因此本次不输出伪排行。"
                + "请先统一币种折算口径并补齐订单、产品和客户引用，再重新分析。";
    }

    private static CrmProductSalesAnalysisService.SalesSummary normalizedSummary(
            CrmProductSalesAnalysisService.SalesSummary raw,
            List<CrmProductSalesAnalysisService.SalesRankRow> rows) {
        if (raw != null && raw.quantityLeader() != null) {
            return raw;
        }
        CrmProductSalesAnalysisService.SalesRankRow quantityLeader = rows.stream()
                .max(Comparator.comparing(row -> zero(row.salesQuantity()))).orElse(rows.getFirst());
        CrmProductSalesAnalysisService.SalesRankRow amountLeader = rows.stream()
                .filter(row -> row.salesAmount() != null)
                .max(Comparator.comparing(row -> zero(row.salesAmount()))).orElse(null);
        return new CrmProductSalesAnalysisService.SalesSummary(
                rows.stream().map(row -> zero(row.salesQuantity())).reduce(BigDecimal.ZERO, BigDecimal::add),
                amountLeader == null ? null
                        : rows.stream().map(row -> zero(row.salesAmount())).reduce(BigDecimal.ZERO, BigDecimal::add),
                rows.stream().mapToInt(row -> Math.max(0, row.orderCount())).sum(),
                rows.stream().mapToInt(row -> Math.max(0, row.customerCount())).sum(),
                raw == null ? "" : raw.currency(),
                amountLeader != null,
                leader(quantityLeader, quantityLeader.salesQuantity()),
                amountLeader == null ? null : leader(amountLeader, amountLeader.salesAmount()));
    }

    private static CrmProductSalesAnalysisService.SalesLeader leader(
            CrmProductSalesAnalysisService.SalesRankRow row, BigDecimal value) {
        return new CrmProductSalesAnalysisService.SalesLeader(
                row.productName(), row.productCode(), row.unit(), value);
    }

    private static List<CrmProductSalesAnalysisService.SalesRankRow> safeRows(
            List<CrmProductSalesAnalysisService.SalesRankRow> rows) {
        if (rows == null) {
            return List.of();
        }
        return rows.stream().filter(java.util.Objects::nonNull).limit(20).toList();
    }

    private static boolean hasSource(List<String> sources, String source) {
        if (sources == null || source == null) {
            return false;
        }
        return sources.stream().filter(java.util.Objects::nonNull)
                .anyMatch(value -> value.equalsIgnoreCase(source));
    }

    private static String sourceLabels(List<String> sources) {
        List<String> labels = new ArrayList<>();
        if (hasSource(sources, "product")) labels.add("产品");
        if (hasSource(sources, "cloudccorder")) labels.add("订单");
        if (hasSource(sources, "cloudccorderitem")) labels.add("订单明细");
        if (hasSource(sources, "Account")) labels.add("客户");
        if (hasSource(sources, "Opportunity")) labels.add("商机");
        if (hasSource(sources, "opportunitypdt")) labels.add("商机产品");
        if (hasSource(sources, "contract")) labels.add("合同");
        return labels.isEmpty() ? "核心销售事实" : String.join("、", labels);
    }

    private static List<CrmProductSalesAnalysisService.BusinessInsight> safeInsights(
            List<CrmProductSalesAnalysisService.BusinessInsight> insights) {
        if (insights == null) {
            return List.of();
        }
        return insights.stream().filter(java.util.Objects::nonNull).limit(12).toList();
    }

    private static List<CrmProductSalesAnalysisService.BusinessInsight> availableInsights(
            List<CrmProductSalesAnalysisService.BusinessInsight> insights,
            List<String> sourceObjects) {
        boolean pipelineAvailable = hasSource(sourceObjects, "Opportunity")
                && hasSource(sourceObjects, "opportunitypdt");
        boolean contractsAvailable = hasSource(sourceObjects, "contract");
        return safeInsights(insights).stream()
                .filter(insight -> switch (label(insight.code(), "")) {
                    case "PIPELINE_GAP", "POTENTIAL_GROWTH" -> pipelineAvailable;
                    case "RENEWAL_RISK" -> pipelineAvailable && contractsAvailable;
                    default -> true;
                })
                .toList();
    }

    private static String dateRange(CrmProductSalesAnalysisService.SalesRankResult result) {
        LocalDate start = result == null ? null : result.startDate();
        LocalDate end = result == null ? null : result.endDate();
        if (start == null || end == null) {
            return "当前统计期";
        }
        return start + " 至 " + end;
    }

    private static String dataAsOf(OffsetDateTime value) {
        return value == null ? "未标记"
                : value.atZoneSameInstant(ZoneId.of("Asia/Shanghai")).format(DATE_TIME_FORMAT);
    }

    private static String productLabel(CrmProductSalesAnalysisService.SalesRankRow row) {
        String name = label(row.productName(), "未识别产品");
        String code = label(row.productCode(), "");
        return code.isBlank() || name.contains(code) ? name : name + "（" + code + "）";
    }

    private static String label(String raw, String fallback) {
        String value = raw == null ? "" : raw.replace('|', '/').replace('`', ' ').replaceAll("\\s+", " ").trim();
        if (value.isBlank() || unsafe(value) || value.indexOf('{') >= 0 || value.indexOf('}') >= 0) {
            return fallback;
        }
        return value.length() > 80 ? value.substring(0, 80) : value;
    }

    private static String businessText(String raw, String fallback) {
        String value = label(raw, fallback);
        return value.replace("**", "").replace("###", "").trim();
    }

    private static boolean unsafe(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).replace("_", "");
        return UNSAFE_MARKERS.stream().map(marker -> marker.replace("_", "")).anyMatch(normalized::contains);
    }

    private static String metricName(CrmProductSalesAnalysisService.Metric metric) {
        if (metric == null) {
            return "净销量";
        }
        return switch (metric) {
            case SALES_QUANTITY -> "净销量";
            case SALES_AMOUNT -> "订单销售额";
            case ORDER_COUNT -> "去重订单数";
            case CUSTOMER_COUNT -> "去重客户数";
        };
    }

    private static String unit(String raw) {
        return label(raw, "单位");
    }

    private static String number(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return value.setScale(Math.min(2, Math.max(0, value.scale())), RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    private static String amount(BigDecimal value, String currency) {
        if (value == null) {
            return "不可比";
        }
        String normalizedCurrency = label(currency, "").toUpperCase(Locale.ROOT);
        String prefix = switch (normalizedCurrency) {
            case "CNY", "RMB" -> "¥";
            case "USD" -> "$";
            case "EUR" -> "€";
            default -> normalizedCurrency.isBlank() ? "" : normalizedCurrency + " ";
        };
        return prefix + number(value);
    }

    private static String percent(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return value.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString() + "%";
    }

    private static String change(BigDecimal value) {
        if (value == null) {
            return "无可比基期";
        }
        String formatted = percent(value);
        return value.signum() > 0 ? "+" + formatted : formatted;
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
