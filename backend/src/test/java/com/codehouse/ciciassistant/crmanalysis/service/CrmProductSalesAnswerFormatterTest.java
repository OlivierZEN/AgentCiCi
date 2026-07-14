package com.codehouse.ciciassistant.crmanalysis.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CrmProductSalesAnswerFormatterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final CrmProductSalesAnswerFormatter formatter = new CrmProductSalesAnswerFormatter(objectMapper);

    @Test
    void formatsSuccessAsDeterministicFiveLayerBusinessAnalysis() throws Exception {
        CrmProductSalesAnalysisService.SalesRankResult result = successResult(
                CrmProductSalesAnalysisService.ResultStatus.SUCCESS, List.of());

        String answer = formatter.format(result);

        assertThat(answer)
                .contains("直接结论")
                .contains("销量冠军：智能巡检终端 X1")
                .contains("订单销售额冠军：边缘采集网关 G5")
                .contains("产品 Top 5")
                .contains("订单销售额")
                .contains("经营诊断")
                .contains("Top1 客户订单销售额占比 70%")
                .contains("前瞻信号")
                .contains("开放商机 2 个")
                .contains("90 天内到期 1 份")
                .contains("建议动作")
                .contains("口径与覆盖")
                .contains("2026-06-15 至 2026-07-14")
                .contains("扫描 48 张订单、144 条明细，计入 40 张订单、120 条明细")
                .doesNotContain("会计收入", "productId", "ownerId", "tool_result", "{");

        assertThat(formatter.formatJson(objectMapper.writeValueAsString(result))).isEqualTo(answer);
    }

    @Test
    void formatsPartialResultWithoutEchoingUnsafeWarningsOrIdentifiers() {
        CrmProductSalesAnalysisService.SalesRankResult result = successResult(
                CrmProductSalesAnalysisService.ResultStatus.PARTIAL,
                List.of("{\"status\":\"secret\",\"ownerId\":\"005-secret\",\"tool_result\":true}"));

        String answer = formatter.format(result);

        assertThat(answer)
                .contains("部分增强数据不可用")
                .contains("智能巡检终端 X1")
                .doesNotContain("{\"status\"", "ownerId", "005-secret", "tool_result", "secret")
                .doesNotContain("本轮不会在完成状态后自动追加回复")
                .doesNotContain("模型本轮未能生成最终自然语言总结");
    }

    @Test
    void distinguishesUnavailableEnhancementsFromVerifiedZeroSignals() {
        CrmProductSalesAnalysisService.SalesRankResult available = successResult(
                CrmProductSalesAnalysisService.ResultStatus.PARTIAL,
                List.of("商机增强数据不可用，已保留订单销售事实", "合同增强数据不可用，已保留订单销售事实"));
        CrmProductSalesAnalysisService.SalesRankResult result = new CrmProductSalesAnalysisService.SalesRankResult(
                available.status(), available.metric(), available.startDate(), available.endDate(), available.dataAsOf(),
                List.of("product", "cloudccorder", "cloudccorderitem"),
                available.rows(), available.coverage(), available.warnings(), available.summary(), List.of(
                        new CrmProductSalesAnalysisService.BusinessInsight(
                                "PIPELINE_GAP", "DEMO-X1", "X1 存在后续订单断层信号",
                                "未发现开放商机产品", "立即创建增购商机"),
                        new CrmProductSalesAnalysisService.BusinessInsight(
                                "RENEWAL_RISK", "DEMO-X1", "X1 存在续约缺口",
                                "临期合同没有续约商机", "立即建立续约清单"),
                        available.insights().getFirst()));

        String answer = formatter.format(result);

        assertThat(answer)
                .contains("商机前瞻不可用")
                .contains("合同信号不可用")
                .contains("数据来源：当前用户有权访问的产品、订单、订单明细")
                .doesNotContain("开放商机 0 个", "活跃合同 0 份")
                .doesNotContain("后续订单断层", "续约缺口", "立即创建增购商机", "立即建立续约清单")
                .doesNotContain("客户、商机产品与合同");
    }

    @Test
    void omitsUnverifiableRenewalGapWhenContractsAreAvailableButPipelineIsNot() {
        CrmProductSalesAnalysisService.SalesRankResult base = successResult(
                CrmProductSalesAnalysisService.ResultStatus.PARTIAL,
                List.of("商机增强数据不可用，已保留订单销售事实"));
        CrmProductSalesAnalysisService.SalesRankRow original = base.rows().getFirst();
        CrmProductSalesAnalysisService.SalesRankRow contractOnly =
                new CrmProductSalesAnalysisService.SalesRankRow(
                        original.rank(), original.productId(), original.productName(), original.productCode(),
                        original.unit(), original.salesQuantity(), original.salesAmount(), original.orderCount(),
                        original.customerCount(), original.previousValue(), original.changeRate(),
                        original.quantityContributionRate(), original.amountContributionRate(),
                        original.realizedAveragePrice(), original.top1CustomerConcentration(),
                        original.top3CustomerConcentration(), original.quantityRank(), original.amountRank(),
                        new CrmProductSalesAnalysisService.ProductPipelineSignal(
                                0, BigDecimal.ZERO, BigDecimal.ZERO, null),
                        new CrmProductSalesAnalysisService.ProductContractSignal(1, 1, 0));
        CrmProductSalesAnalysisService.SalesRankResult result =
                new CrmProductSalesAnalysisService.SalesRankResult(
                        base.status(), base.metric(), base.startDate(), base.endDate(), base.dataAsOf(),
                        List.of("product", "cloudccorder", "cloudccorderitem", "contract"),
                        List.of(contractOnly), base.coverage(), base.warnings(), base.summary(), List.of());

        String answer = formatter.format(result);

        assertThat(answer)
                .contains("商机前瞻不可用")
                .contains("活跃合同 1 份", "90 天内到期 1 份")
                .doesNotContain("未关联续约商机 0 份", "续约缺口");
    }

    @Test
    void formatsIncomparablePipelineAmountWithoutApplyingRealizedSalesCurrency() {
        CrmProductSalesAnalysisService.SalesRankResult base = successResult(
                CrmProductSalesAnalysisService.ResultStatus.PARTIAL,
                List.of("管道币种与已实现销售币种不一致"));
        CrmProductSalesAnalysisService.SalesRankRow row = base.rows().getFirst();
        CrmProductSalesAnalysisService.SalesRankRow incomparablePipelineRow =
                new CrmProductSalesAnalysisService.SalesRankRow(
                        row.rank(), row.productId(), row.productName(), row.productCode(), row.unit(),
                        row.salesQuantity(), row.salesAmount(), row.orderCount(), row.customerCount(),
                        row.previousValue(), row.changeRate(), row.quantityContributionRate(),
                        row.amountContributionRate(), row.realizedAveragePrice(),
                        row.top1CustomerConcentration(), row.top3CustomerConcentration(),
                        row.quantityRank(), row.amountRank(),
                        new CrmProductSalesAnalysisService.ProductPipelineSignal(
                                row.pipeline().openOpportunityCount(), row.pipeline().quantity(), null,
                                row.pipeline().nearestExpectedCloseDate()),
                        row.contracts());
        CrmProductSalesAnalysisService.SalesRankResult result =
                new CrmProductSalesAnalysisService.SalesRankResult(
                        base.status(), base.metric(), base.startDate(), base.endDate(), base.dataAsOf(),
                        base.sourceObjects(), List.of(incomparablePipelineRow, base.rows().get(1)),
                        base.coverage(), base.warnings(), base.summary(), base.insights());

        String answer = formatter.format(result);

        assertThat(answer)
                .contains("管道金额 不可比")
                .doesNotContain("管道金额 ¥", "管道金额 $", "管道金额 USD");
    }

    @ParameterizedTest
    @MethodSource("nonSuccessStatuses")
    void formatsEveryNonSuccessStateAsSafeNaturalLanguage(
            CrmProductSalesAnalysisService.ResultStatus status,
            String expectedPhrase) {
        CrmProductSalesAnalysisService.SalesRankResult result = new CrmProductSalesAnalysisService.SalesRankResult(
                status,
                CrmProductSalesAnalysisService.Metric.SALES_QUANTITY,
                LocalDate.parse("2026-06-15"),
                LocalDate.parse("2026-07-14"),
                OffsetDateTime.parse("2026-07-14T12:00:00+08:00"),
                List.of("product", "cloudccorder", "cloudccorderitem"),
                List.of(),
                new CrmProductSalesAnalysisService.Coverage(0, 0, 0, 1_888, 0, 1_888),
                List.of("{\"status\":\"unsafe\",\"productId\":\"hidden\"}"));

        String answer = formatter.format(result);

        assertThat(answer)
                .contains(expectedPhrase)
                .doesNotContain("{", "productId", "hidden", "tool_result");
    }

    @Test
    void safelyHandlesNullMalformedAndStructurallyInvalidPayloads() {
        CrmProductSalesAnalysisService.SalesRankResult invalid = new CrmProductSalesAnalysisService.SalesRankResult(
                null, null, null, null, null, null, null, null,
                List.of("ownerId=005-secret {\"status\":\"bad\"}"), null, null);

        assertThat(formatter.format(null)).contains("暂时无法完成这次 CRM 经营分析");
        assertThat(formatter.format(invalid))
                .contains("暂时无法完成这次 CRM 经营分析")
                .doesNotContain("ownerId", "005-secret", "{\"status\"");
        assertThat(formatter.formatJson("{not-json"))
                .contains("暂时无法完成这次 CRM 经营分析")
                .doesNotContain("{not-json");
        assertThat(formatter.formatJson("{\"status\":\"SUCCESS\",\"rows\":\"not-an-array\"}"))
                .contains("暂时无法完成这次 CRM 经营分析")
                .doesNotContain("not-an-array");
    }

    private static Stream<Arguments> nonSuccessStatuses() {
        return Stream.of(
                Arguments.of(CrmProductSalesAnalysisService.ResultStatus.EMPTY, "没有可计入的有效销售事实"),
                Arguments.of(CrmProductSalesAnalysisService.ResultStatus.DATA_ACCESS_INCOMPLETE,
                        "权限覆盖不完整，不能据此判断“没有销售”"),
                Arguments.of(CrmProductSalesAnalysisService.ResultStatus.DATA_QUALITY_BLOCKED,
                        "数据质量问题会导致排行失真"),
                Arguments.of(CrmProductSalesAnalysisService.ResultStatus.CRM_NOT_CONNECTED, "CRM 绑定或连接"),
                Arguments.of(CrmProductSalesAnalysisService.ResultStatus.PERMISSION_DENIED, "当前账号没有读取核心销售对象的权限"),
                Arguments.of(CrmProductSalesAnalysisService.ResultStatus.SCHEMA_UNSUPPORTED, "CRM 对象或字段结构"),
                Arguments.of(CrmProductSalesAnalysisService.ResultStatus.UPSTREAM_ERROR, "暂时无法完成这次 CRM 经营分析")
        );
    }

    private CrmProductSalesAnalysisService.SalesRankResult successResult(
            CrmProductSalesAnalysisService.ResultStatus status,
            List<String> warnings) {
        CrmProductSalesAnalysisService.SalesRankRow x1 = new CrmProductSalesAnalysisService.SalesRankRow(
                1, "internal-p1", "智能巡检终端 X1", "DEMO-X1", "台",
                new BigDecimal("130"), new BigDecimal("130000"), 12, 8,
                new BigDecimal("100"), new BigDecimal("0.3"),
                new BigDecimal("0.4"), new BigDecimal("0.35"), new BigDecimal("1000"),
                new BigDecimal("0.7"), new BigDecimal("0.9"), 1, 2,
                new CrmProductSalesAnalysisService.ProductPipelineSignal(
                        2, new BigDecimal("40"), new BigDecimal("50000"), LocalDate.parse("2026-08-20")),
                new CrmProductSalesAnalysisService.ProductContractSignal(3, 1, 1));
        CrmProductSalesAnalysisService.SalesRankRow g5 = new CrmProductSalesAnalysisService.SalesRankRow(
                2, "internal-p2", "边缘采集网关 G5", "DEMO-G5", "台",
                new BigDecimal("110"), new BigDecimal("180000"), 10, 7,
                new BigDecimal("100"), new BigDecimal("0.1"),
                new BigDecimal("0.34"), new BigDecimal("0.49"), new BigDecimal("1636.36"),
                new BigDecimal("0.4"), new BigDecimal("0.8"), 2, 1,
                CrmProductSalesAnalysisService.ProductPipelineSignal.empty(),
                CrmProductSalesAnalysisService.ProductContractSignal.empty());
        CrmProductSalesAnalysisService.SalesSummary summary = new CrmProductSalesAnalysisService.SalesSummary(
                new BigDecimal("325"), new BigDecimal("370000"), 40, 24, "CNY", true,
                new CrmProductSalesAnalysisService.SalesLeader(
                        "智能巡检终端 X1", "DEMO-X1", "台", new BigDecimal("130")),
                new CrmProductSalesAnalysisService.SalesLeader(
                        "边缘采集网关 G5", "DEMO-G5", "台", new BigDecimal("180000")));
        List<CrmProductSalesAnalysisService.BusinessInsight> insights = List.of(
                new CrmProductSalesAnalysisService.BusinessInsight(
                        "CUSTOMER_CONCENTRATION", "DEMO-X1", "X1 客户集中度较高",
                        "Top1 客户订单销售额占比 70%", "拓展同类客户并复盘续约风险"));
        return new CrmProductSalesAnalysisService.SalesRankResult(
                status,
                CrmProductSalesAnalysisService.Metric.SALES_QUANTITY,
                LocalDate.parse("2026-06-15"),
                LocalDate.parse("2026-07-14"),
                OffsetDateTime.parse("2026-07-14T12:00:00+08:00"),
                List.of("product", "cloudccorder", "cloudccorderitem", "Account", "Opportunity", "opportunitypdt", "contract"),
                List.of(x1, g5),
                new CrmProductSalesAnalysisService.Coverage(48, 40, 8, 144, 120, 24),
                warnings,
                summary,
                insights);
    }
}
