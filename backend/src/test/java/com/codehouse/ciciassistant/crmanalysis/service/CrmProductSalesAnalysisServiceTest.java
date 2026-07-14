package com.codehouse.ciciassistant.crmanalysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.cloudcc.CloudccOpenApiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CrmProductSalesAnalysisServiceTest {

    private CloudccOpenApiService cloudcc;
    private CrmProductSalesAnalysisService service;

    @BeforeEach
    void setUp() {
        cloudcc = mock(CloudccOpenApiService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-07-14T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
        service = new CrmProductSalesAnalysisService(cloudcc, clock);
        when(cloudcc.queryAllRecords(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> records(invocation.getArgument(2)));
    }

    @Test
    void ranksValidOrderItemsByQuantityAndComparesPreviousPeriod() {
        CrmProductSalesAnalysisService.SalesRankResult result = service.analyze(
                "org-1",
                "user-1",
                new CrmProductSalesAnalysisService.SalesRankRequest(null, null, null, 5, true)
        );

        assertThat(result.status()).isEqualTo(CrmProductSalesAnalysisService.ResultStatus.SUCCESS);
        assertThat(result.metric()).isEqualTo(CrmProductSalesAnalysisService.Metric.SALES_QUANTITY);
        assertThat(result.startDate()).hasToString("2026-06-15");
        assertThat(result.endDate()).hasToString("2026-07-14");
        assertThat(result.sourceObjects()).containsExactly(
                "product", "cloudccorder", "cloudccorderitem",
                "Account", "Opportunity", "opportunitypdt", "contract");
        assertThat(result.rows()).extracting(CrmProductSalesAnalysisService.SalesRankRow::productCode)
                .containsExactly("DEMO-X1", "DEMO-G5");

        CrmProductSalesAnalysisService.SalesRankRow first = result.rows().getFirst();
        assertThat(first.rank()).isEqualTo(1);
        assertThat(first.productName()).isEqualTo("智能巡检终端 X1");
        assertThat(first.salesQuantity()).isEqualByComparingTo("15");
        assertThat(first.salesAmount()).isEqualByComparingTo("1500");
        assertThat(first.orderCount()).isEqualTo(2);
        assertThat(first.customerCount()).isEqualTo(1);
        assertThat(first.previousValue()).isEqualByComparingTo("10");
        assertThat(first.changeRate()).isEqualByComparingTo("0.5");
        assertThat(result.coverage().includedOrders()).isEqualTo(3);
        assertThat(result.coverage().excludedOrders()).isEqualTo(3);
        assertThat(result.coverage().includedItems()).isEqualTo(3);

        verify(cloudcc).queryAllRecords("org-1", "user-1", "product",
                "id,name,cpdm,cpxl,unit,productprice,yqy,ownerid", "");
    }

    @Test
    void ranksTheSameFactsBySalesAmountWithoutModelSideCalculation() {
        CrmProductSalesAnalysisService.SalesRankResult result = service.analyze(
                "org-1",
                "user-1",
                new CrmProductSalesAnalysisService.SalesRankRequest(
                        CrmProductSalesAnalysisService.Metric.SALES_AMOUNT,
                        null,
                        null,
                        2,
                        false
                )
        );

        assertThat(result.rows()).extracting(CrmProductSalesAnalysisService.SalesRankRow::productCode)
                .containsExactly("DEMO-G5", "DEMO-X1");
        assertThat(result.rows().getFirst().salesAmount()).isEqualByComparingTo("6000");
        assertThat(result.rows().getFirst().previousValue()).isNull();
        assertThat(result.rows().getFirst().changeRate()).isNull();
    }

    @Test
    void returnsEmptyResultInsteadOfInventingProducts() {
        when(cloudcc.queryAllRecords(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of());

        CrmProductSalesAnalysisService.SalesRankResult result = service.analyze(
                "org-1",
                "user-1",
                new CrmProductSalesAnalysisService.SalesRankRequest(null, null, null, null, null)
        );

        assertThat(result.status()).isEqualTo(CrmProductSalesAnalysisService.ResultStatus.EMPTY);
        assertThat(result.rows()).isEmpty();
        assertThat(result.warnings()).contains("统计范围内没有可计入的有效订单明细");
    }

    @Test
    void reportsDataAccessIncompleteWhenOrderItemsAreVisibleButOrderMastersAreNot() {
        List<Map<String, Object>> visibleItems = IntStream.range(0, 1_888)
                .mapToObj(index -> item("visible-" + index, "hidden-order-" + index,
                        "p1", "1", "100", "100", "已生效"))
                .toList();
        when(cloudcc.queryAllRecords(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> switch (invocation.<String>getArgument(2)) {
                    case "product" -> List.of(map("id", "p1", "name", "智能巡检终端 X1", "cpdm", "DEMO-X1", "unit", "台"));
                    case "cloudccorder" -> List.of();
                    case "cloudccorderitem" -> visibleItems;
                    default -> List.of();
                });

        CrmProductSalesAnalysisService.SalesRankResult result = service.analyze(
                "org-1", "sales-a",
                new CrmProductSalesAnalysisService.SalesRankRequest(null, null, null, 5, true));

        assertThat(result.status().name()).isEqualTo("DATA_ACCESS_INCOMPLETE");
        assertThat(result.rows()).isEmpty();
        assertThat(result.coverage().scannedOrders()).isZero();
        assertThat(result.coverage().scannedItems()).isEqualTo(1_888);
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("权限") && warning.contains("订单主表"));
    }

    @Test
    void reportsDataAccessIncompleteWhenVisibleOrdersDoNotMatchAnyItemOrderReference() {
        when(cloudcc.queryAllRecords(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> switch (invocation.<String>getArgument(2)) {
                    case "product" -> List.of(
                            map("id", "p1", "name", "智能巡检终端 X1", "cpdm", "DEMO-X1", "unit", "台"));
                    case "cloudccorder" -> List.of(
                            order("visible-unrelated", "2026-07-10", "已完成", "a1"));
                    case "cloudccorderitem" -> List.of(
                            item("i1", "hidden-order-1", "p1", "5", "100", "500", "已生效"),
                            item("i2", "hidden-order-2", "p1", "3", "100", "300", "已生效"));
                    default -> List.of();
                });

        CrmProductSalesAnalysisService.SalesRankResult result = service.analyze(
                "org-1", "sales-a",
                new CrmProductSalesAnalysisService.SalesRankRequest(null, null, null, 5, false));

        assertThat(result.status()).isEqualTo(CrmProductSalesAnalysisService.ResultStatus.DATA_ACCESS_INCOMPLETE);
        assertThat(result.rows()).isEmpty();
        assertThat(result.coverage().scannedOrders()).isEqualTo(1);
        assertThat(result.coverage().scannedItems()).isEqualTo(2);
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("订单引用") && warning.contains("不可见"));
    }

    @Test
    void keepsVerifiedSalesAndMarksPartialWhenSomeItemOrdersAreInvisible() {
        when(cloudcc.queryAllRecords(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> switch (invocation.<String>getArgument(2)) {
                    case "product" -> List.of(
                            map("id", "p1", "name", "智能巡检终端 X1", "cpdm", "DEMO-X1", "unit", "台"));
                    case "cloudccorder" -> List.of(
                            order("visible-order", "2026-07-10", "已完成", "a1"));
                    case "cloudccorderitem" -> List.of(
                            item("visible-item", "visible-order", "p1", "5", "100", "500", "已生效"),
                            item("hidden-item", "hidden-order", "p1", "50", "100", "5000", "已生效"));
                    default -> List.of();
                });

        CrmProductSalesAnalysisService.SalesRankResult result = service.analyze(
                "org-1", "sales-a",
                new CrmProductSalesAnalysisService.SalesRankRequest(null, null, null, 5, false));

        assertThat(result.status()).isEqualTo(CrmProductSalesAnalysisService.ResultStatus.PARTIAL);
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().getFirst().salesQuantity()).isEqualByComparingTo("5");
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("部分")
                && warning.contains("订单引用") && warning.contains("不可见"));
    }

    @Test
    void returnsEmptyWhenAllReferencedVisibleOrdersAreOutsideTheRequestedPeriod() {
        when(cloudcc.queryAllRecords(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> switch (invocation.<String>getArgument(2)) {
                    case "product" -> List.of(
                            map("id", "p1", "name", "智能巡检终端 X1", "cpdm", "DEMO-X1", "unit", "台"));
                    case "cloudccorder" -> List.of(
                            order("old-order", "2026-05-01", "已完成", "a1"));
                    case "cloudccorderitem" -> List.of(
                            item("old-item", "old-order", "p1", "5", "100", "500", "已生效"));
                    default -> List.of();
                });

        CrmProductSalesAnalysisService.SalesRankResult result = service.analyze(
                "org-1", "sales-a",
                new CrmProductSalesAnalysisService.SalesRankRequest(null, null, null, 5, false));

        assertThat(result.status()).isEqualTo(CrmProductSalesAnalysisService.ResultStatus.EMPTY);
        assertThat(result.rows()).isEmpty();
        assertThat(result.warnings()).doesNotContain(
                "当前账号可见有效订单明细，但其订单引用在可见订单主表中全部不可见，权限覆盖不完整");
    }

    @Test
    void reportsDataAccessIncompleteWhenVisibleReferencesAreOutsidePeriodAndAnotherReferenceIsInvisible() {
        when(cloudcc.queryAllRecords(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> switch (invocation.<String>getArgument(2)) {
                    case "product" -> List.of(
                            map("id", "p1", "name", "智能巡检终端 X1", "cpdm", "DEMO-X1", "unit", "台"));
                    case "cloudccorder" -> List.of(
                            order("visible-old", "2026-05-01", "已完成", "a1"));
                    case "cloudccorderitem" -> List.of(
                            item("old-item", "visible-old", "p1", "1", "100", "100", "已生效"),
                            item("hidden-item", "hidden-order", "p1", "9", "100", "900", "已生效"));
                    default -> List.of();
                });

        CrmProductSalesAnalysisService.SalesRankResult result = service.analyze(
                "org-1", "sales-a",
                new CrmProductSalesAnalysisService.SalesRankRequest(null, null, null, 5, false));

        assertThat(result.status()).isEqualTo(CrmProductSalesAnalysisService.ResultStatus.DATA_ACCESS_INCOMPLETE);
        assertThat(result.rows()).isEmpty();
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("订单引用")
                && warning.contains("不可见") && warning.contains("权限"));
        assertThat(result.warnings()).noneMatch(warning -> warning.contains("没有可计入"));
    }

    @Test
    void blocksCustomerCountWhenEveryCurrentSalesOrderHasMissingOrInvisibleAccountReference() {
        when(cloudcc.queryAllRecords(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> switch (invocation.<String>getArgument(2)) {
                    case "product" -> List.of(
                            map("id", "p1", "name", "智能巡检终端 X1", "cpdm", "DEMO-X1", "unit", "台"));
                    case "cloudccorder" -> List.of(
                            order("missing-account", "2026-07-10", "已生效", ""),
                            order("invisible-account", "2026-07-11", "已生效", "a-hidden"));
                    case "cloudccorderitem" -> List.of(
                            item("i1", "missing-account", "p1", "5", "100", "500", ""),
                            item("i2", "invisible-account", "p1", "3", "100", "300", ""));
                    case "Account" -> List.of(map("id", "a-visible", "name", "可见客户"));
                    default -> List.of();
                });

        CrmProductSalesAnalysisService.SalesRankResult result = service.analyze(
                "org-1", "user-1",
                new CrmProductSalesAnalysisService.SalesRankRequest(
                        CrmProductSalesAnalysisService.Metric.CUSTOMER_COUNT,
                        null, null, 5, false));

        assertThat(result.status()).isEqualTo(CrmProductSalesAnalysisService.ResultStatus.DATA_ACCESS_INCOMPLETE);
        assertThat(result.rows()).isEmpty();
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("客户引用")
                && warning.contains("全部") && warning.contains("不可验证"));
    }

    @Test
    void countsOnlyVerifiableCustomersAndMarksPartialWhenSomeAccountReferencesAreMissing() {
        when(cloudcc.queryAllRecords(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> switch (invocation.<String>getArgument(2)) {
                    case "product" -> List.of(
                            map("id", "p1", "name", "智能巡检终端 X1", "cpdm", "DEMO-X1", "unit", "台"));
                    case "cloudccorder" -> List.of(
                            order("visible-account", "2026-07-10", "已生效", "a-visible"),
                            order("missing-account", "2026-07-11", "已生效", ""),
                            order("invisible-account", "2026-07-12", "已生效", "a-hidden"));
                    case "cloudccorderitem" -> List.of(
                            item("i1", "visible-account", "p1", "5", "100", "500", ""),
                            item("i2", "missing-account", "p1", "3", "100", "300", ""),
                            item("i3", "invisible-account", "p1", "2", "100", "200", ""));
                    case "Account" -> List.of(map("id", "a-visible", "name", "可见客户"));
                    default -> List.of();
                });

        CrmProductSalesAnalysisService.SalesRankResult result = service.analyze(
                "org-1", "user-1",
                new CrmProductSalesAnalysisService.SalesRankRequest(
                        CrmProductSalesAnalysisService.Metric.CUSTOMER_COUNT,
                        null, null, 5, false));

        assertThat(result.status()).isEqualTo(CrmProductSalesAnalysisService.ResultStatus.PARTIAL);
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().getFirst().customerCount()).isEqualTo(1);
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("部分")
                && warning.contains("客户引用") && warning.contains("不可验证"));
    }

    @Test
    void blankItemStatusInheritsWhitelistedOrderStatusWhileNonblankUnknownStatusFailsClosed() {
        when(cloudcc.queryAllRecords(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> switch (invocation.<String>getArgument(2)) {
                    case "product" -> List.of(
                            map("id", "p1", "name", "智能巡检终端 X1", "cpdm", "DEMO-X1", "unit", "台"));
                    case "cloudccorder" -> List.of(
                            order("valid", "2026-07-10", "已生效", "a1"),
                            order("unknown", "2026-07-10", "待复核", "a1"),
                            order("blank", "2026-07-10", "", "a1"));
                    case "cloudccorderitem" -> List.of(
                            item("valid-blank-item", "valid", "p1", "5", "100", "500", ""),
                            item("valid-custom-item", "valid", "p1", "2", "100", "200", "自定义审核中"),
                            item("unknown-order-item", "unknown", "p1", "50", "100", "5000", ""),
                            item("blank-order-item", "blank", "p1", "70", "100", "7000", ""));
                    default -> List.of();
                });

        CrmProductSalesAnalysisService.SalesRankResult result = service.analyze(
                "org-1", "user-1",
                new CrmProductSalesAnalysisService.SalesRankRequest(null, null, null, 5, false));

        assertThat(result.status()).isEqualTo(CrmProductSalesAnalysisService.ResultStatus.PARTIAL);
        assertThat(result.rows().getFirst().salesQuantity()).isEqualByComparingTo("5");
        assertThat(result.coverage().includedItems()).isEqualTo(1);
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("订单状态")
                && warning.contains("未知") && warning.contains("排除"));
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("订单明细状态")
                && warning.contains("未知") && warning.contains("排除"));
    }

    @Test
    void calculatesContributionAveragePriceAndCustomerConcentrationFromAllFactsBeforeTopN() {
        when(cloudcc.queryAllRecords(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> switch (invocation.<String>getArgument(2)) {
                    case "product" -> List.of(
                            map("id", "p1", "name", "智能巡检终端 X1", "cpdm", "DEMO-X1", "unit", "台"),
                            map("id", "p2", "name", "边缘采集网关 G5", "cpdm", "DEMO-G5", "unit", "台"));
                    case "cloudccorder" -> List.of(
                            orderWithCurrency("o1", "2026-07-10", "已完成", "a1", "CNY"),
                            orderWithCurrency("o2", "2026-07-11", "已完成", "a2", "CNY"),
                            orderWithCurrency("o3", "2026-07-12", "已完成", "a3", "CNY"),
                            orderWithCurrency("o4", "2026-07-13", "已完成", "a4", "CNY"));
                    case "cloudccorderitem" -> List.of(
                            item("i1", "o1", "p1", "50", "100", "5000", "已生效"),
                            item("i2", "o2", "p1", "30", "100", "3000", "已生效"),
                            item("i3", "o3", "p1", "20", "100", "2000", "已生效"),
                            item("i4", "o4", "p2", "10", "1200", "12000", "已生效"));
                    default -> List.of();
                });

        CrmProductSalesAnalysisService.SalesRankResult result = service.analyze(
                "org-1", "user-1",
                new CrmProductSalesAnalysisService.SalesRankRequest(
                        CrmProductSalesAnalysisService.Metric.SALES_QUANTITY,
                        null, null, 1, false));

        assertThat(result.status()).isEqualTo(CrmProductSalesAnalysisService.ResultStatus.SUCCESS);
        assertThat(result.rows()).hasSize(1);
        CrmProductSalesAnalysisService.SalesRankRow row = result.rows().getFirst();
        assertThat(row.productCode()).isEqualTo("DEMO-X1");
        assertThat(row.quantityContributionRate()).isEqualByComparingTo("0.9091");
        assertThat(row.amountContributionRate()).isEqualByComparingTo("0.4545");
        assertThat(row.realizedAveragePrice()).isEqualByComparingTo("100");
        assertThat(row.top1CustomerConcentration()).isEqualByComparingTo("0.5");
        assertThat(row.top3CustomerConcentration()).isEqualByComparingTo("1");
        assertThat(row.quantityRank()).isEqualTo(1);
        assertThat(row.amountRank()).isEqualTo(2);
        assertThat(result.summary().totalSalesQuantity()).isEqualByComparingTo("110");
        assertThat(result.summary().totalSalesAmount()).isEqualByComparingTo("22000");
        assertThat(result.summary().quantityLeader().productCode()).isEqualTo("DEMO-X1");
        assertThat(result.summary().amountLeader().productCode()).isEqualTo("DEMO-G5");
        assertThat(result.summary().currency()).isEqualTo("CNY");
    }

    @Test
    void preservesExplicitZeroTotalPriceAndOnlyFallsBackWhenTotalPriceIsBlank() {
        when(cloudcc.queryAllRecords(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> switch (invocation.<String>getArgument(2)) {
                    case "product" -> List.of(
                            map("id", "p1", "name", "智能巡检终端 X1", "cpdm", "DEMO-X1", "unit", "台"));
                    case "cloudccorder" -> List.of(
                            orderWithCurrency("o1", "2026-07-10", "已生效", "a1", "CNY"));
                    case "cloudccorderitem" -> List.of(
                            item("gift", "o1", "p1", "2", "100", "0", ""),
                            item("missing-total", "o1", "p1", "3", "100", "", ""));
                    default -> List.of();
                });

        CrmProductSalesAnalysisService.SalesRankResult result = service.analyze(
                "org-1", "user-1",
                new CrmProductSalesAnalysisService.SalesRankRequest(null, null, null, 5, false));

        assertThat(result.rows().getFirst().salesQuantity()).isEqualByComparingTo("5");
        assertThat(result.rows().getFirst().salesAmount()).isEqualByComparingTo("300");
        assertThat(result.rows().getFirst().realizedAveragePrice()).isEqualByComparingTo("100");
    }

    @Test
    void blocksSalesAmountWhenNonblankTotalPriceIsInvalid() {
        stubInvalidCurrentTotalPrice();

        CrmProductSalesAnalysisService.SalesRankResult result = service.analyze(
                "org-1", "user-1",
                new CrmProductSalesAnalysisService.SalesRankRequest(
                        CrmProductSalesAnalysisService.Metric.SALES_AMOUNT,
                        null, null, 5, false));

        assertThat(result.status()).isEqualTo(CrmProductSalesAnalysisService.ResultStatus.DATA_QUALITY_BLOCKED);
        assertThat(result.rows()).isEmpty();
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("订单明细金额")
                && warning.contains("不可解析") && warning.contains("排除金额指标"));
    }

    @Test
    void keepsNonAmountFactsButSuppressesAmountMetricsWhenTotalPriceIsInvalid() {
        stubInvalidCurrentTotalPrice();

        for (CrmProductSalesAnalysisService.Metric metric : List.of(
                CrmProductSalesAnalysisService.Metric.SALES_QUANTITY,
                CrmProductSalesAnalysisService.Metric.ORDER_COUNT,
                CrmProductSalesAnalysisService.Metric.CUSTOMER_COUNT)) {
            CrmProductSalesAnalysisService.SalesRankResult result = service.analyze(
                    "org-1", "user-1",
                    new CrmProductSalesAnalysisService.SalesRankRequest(metric, null, null, 5, false));

            assertThat(result.status()).as(metric.name())
                    .isEqualTo(CrmProductSalesAnalysisService.ResultStatus.PARTIAL);
            assertThat(result.rows()).as(metric.name()).hasSize(1);
            CrmProductSalesAnalysisService.SalesRankRow row = result.rows().getFirst();
            assertThat(row.salesQuantity()).as(metric.name()).isEqualByComparingTo("3");
            assertThat(row.orderCount()).as(metric.name()).isEqualTo(1);
            assertThat(row.customerCount()).as(metric.name()).isEqualTo(1);
            assertThat(row.salesAmount()).as(metric.name()).isNull();
            assertThat(row.amountContributionRate()).as(metric.name()).isNull();
            assertThat(row.realizedAveragePrice()).as(metric.name()).isNull();
            assertThat(row.top1CustomerConcentration()).as(metric.name()).isNull();
            assertThat(row.amountRank()).as(metric.name()).isZero();
            assertThat(result.summary().totalSalesAmount()).as(metric.name()).isNull();
            assertThat(result.summary().amountComparable()).as(metric.name()).isFalse();
            assertThat(result.insights()).as(metric.name())
                    .extracting(CrmProductSalesAnalysisService.BusinessInsight::code)
                    .doesNotContain("CORE_GROWTH", "DISCOUNT_DRIVEN", "CUSTOMER_CONCENTRATION", "HIGH_VALUE_PRODUCT");
            assertThat(result.warnings()).as(metric.name())
                    .anyMatch(warning -> warning.contains("订单明细金额") && warning.contains("不可解析"));
        }
    }

    @Test
    void deductsReturnsFromNetSalesWithoutDistortingRealizedAveragePrice() {
        when(cloudcc.queryAllRecords(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> switch (invocation.<String>getArgument(2)) {
                    case "product" -> List.of(
                            map("id", "p1", "name", "智能巡检终端 X1", "cpdm", "DEMO-X1", "unit", "台"));
                    case "cloudccorder" -> List.of(
                            orderWithCurrency("o1", "2026-07-10", "已生效", "a1", "CNY"));
                    case "cloudccorderitem" -> List.of(
                            item("sale", "o1", "p1", "10", "100", "1000", ""),
                            item("return", "o1", "p1", "-2", "100", "-200", ""));
                    default -> List.of();
                });

        CrmProductSalesAnalysisService.SalesRankResult result = service.analyze(
                "org-1", "user-1",
                new CrmProductSalesAnalysisService.SalesRankRequest(null, null, null, 5, false));

        assertThat(result.rows().getFirst().salesQuantity()).isEqualByComparingTo("8");
        assertThat(result.rows().getFirst().salesAmount()).isEqualByComparingTo("800");
        assertThat(result.rows().getFirst().realizedAveragePrice()).isEqualByComparingTo("100");
    }

    @Test
    void blocksAmountAnalysisWhenVisibleFactsContainMultipleCurrencies() {
        when(cloudcc.queryAllRecords(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> switch (invocation.<String>getArgument(2)) {
                    case "product" -> List.of(
                            map("id", "p1", "name", "产品一", "cpdm", "P-1", "unit", "台"),
                            map("id", "p2", "name", "产品二", "cpdm", "P-2", "unit", "台"));
                    case "cloudccorder" -> List.of(
                            orderWithCurrency("o1", "2026-07-10", "已完成", "a1", "CNY"),
                            orderWithCurrency("o2", "2026-07-11", "已完成", "a2", "USD"));
                    case "cloudccorderitem" -> List.of(
                            item("i1", "o1", "p1", "1", "100", "100", "已生效"),
                            item("i2", "o2", "p2", "1", "100", "100", "已生效"));
                    default -> List.of();
                });

        CrmProductSalesAnalysisService.SalesRankResult result = service.analyze(
                "org-1", "user-1",
                new CrmProductSalesAnalysisService.SalesRankRequest(
                        CrmProductSalesAnalysisService.Metric.SALES_AMOUNT,
                        null, null, 5, false));

        assertThat(result.status().name()).isEqualTo("DATA_QUALITY_BLOCKED");
        assertThat(result.rows()).isEmpty();
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("多币种"));
    }

    @Test
    void addsOpportunityAndContractSignalsWithoutMixingPipelineIntoRealizedSales() {
        when(cloudcc.queryAllRecords(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> switch (invocation.<String>getArgument(2)) {
                    case "product" -> List.of(
                            map("id", "p1", "name", "智能巡检终端 X1", "cpdm", "DEMO-X1", "unit", "台"));
                    case "cloudccorder" -> List.of(
                            orderWithRelations("o1", "2026-07-10", "a1", "c1", "CNY"),
                            orderWithRelations("o2", "2026-07-11", "a2", "c2", "CNY"));
                    case "cloudccorderitem" -> List.of(
                            item("i1", "o1", "p1", "8", "1000", "8000", "已生效"),
                            item("i2", "o2", "p1", "2", "1000", "2000", "已生效"));
                    case "Account" -> List.of(
                            map("id", "a1", "name", "华东智造一厂"),
                            map("id", "a2", "name", "南方能源集团"));
                    case "Opportunity" -> List.of(
                            map("id", "op-renew", "name", "X1 续约扩容", "khmc", Map.of("id", "a1"),
                                    "jieduan", "商务谈判", "jsrq", "2026-08-20", "currency", "CNY"));
                    case "opportunitypdt" -> List.of(
                            map("id", "opd-1", "opportunity", Map.of("id", "op-renew"),
                                    "product2", Map.of("id", "p1"), "quantity", "20",
                                    "totalprice", "20000", "unit", "台", "currency", "CNY"));
                    case "contract" -> List.of(
                            map("id", "c1", "khmc", Map.of("id", "a1"), "zhuangtai", "执行中",
                                    "htksrq", "2026-01-01", "htjsrq", "2026-08-30", "currency", "CNY"),
                            map("id", "c2", "khmc", Map.of("id", "a2"), "zhuangtai", "已生效",
                                    "htksrq", "2026-01-01", "htjsrq", "2026-09-15", "currency", "CNY"));
                    default -> List.of();
                });

        CrmProductSalesAnalysisService.SalesRankResult result = service.analyze(
                "org-1", "user-1",
                new CrmProductSalesAnalysisService.SalesRankRequest(null, null, null, 5, false));

        assertThat(result.status()).isEqualTo(CrmProductSalesAnalysisService.ResultStatus.SUCCESS);
        CrmProductSalesAnalysisService.SalesRankRow row = result.rows().getFirst();
        assertThat(row.salesQuantity()).isEqualByComparingTo("10");
        assertThat(row.pipeline().openOpportunityCount()).isEqualTo(1);
        assertThat(row.pipeline().quantity()).isEqualByComparingTo("20");
        assertThat(row.pipeline().amount()).isEqualByComparingTo("20000");
        assertThat(row.pipeline().nearestExpectedCloseDate()).hasToString("2026-08-20");
        assertThat(row.contracts().activeContractCount()).isEqualTo(2);
        assertThat(row.contracts().expiringWithin90DaysCount()).isEqualTo(2);
        assertThat(row.contracts().expiringWithoutRenewalCount()).isEqualTo(1);
        assertThat(result.insights()).extracting(CrmProductSalesAnalysisService.BusinessInsight::code)
                .contains("CUSTOMER_CONCENTRATION", "RENEWAL_RISK");
        assertThat(result.insights()).allSatisfy(insight -> {
            assertThat(insight.evidence()).isNotBlank();
            assertThat(insight.action()).isNotBlank();
        });
    }

    @Test
    void suppressesPipelineAmountWhenItsCurrencyDiffersFromRealizedSales() {
        when(cloudcc.queryAllRecords(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> switch (invocation.<String>getArgument(2)) {
                    case "product" -> List.of(
                            map("id", "p1", "name", "智能巡检终端 X1", "cpdm", "DEMO-X1", "unit", "台"));
                    case "cloudccorder" -> List.of(
                            orderWithCurrency("o1", "2026-07-10", "已完成", "a1", "CNY"));
                    case "cloudccorderitem" -> List.of(
                            item("i1", "o1", "p1", "5", "100", "500", "已生效"));
                    case "Opportunity" -> List.of(
                            map("id", "op-usd", "khmc", Map.of("id", "a1"), "jieduan", "3-提出方案",
                                    "jsrq", "2026-08-20", "currency", "USD"));
                    case "opportunitypdt" -> List.of(
                            map("id", "opd-usd", "opportunity", Map.of("id", "op-usd"),
                                    "product2", Map.of("id", "p1"), "quantity", "10",
                                    "totalprice", "3000", "unit", "台", "currency", "USD"));
                    default -> List.of();
                });

        CrmProductSalesAnalysisService.SalesRankResult result = service.analyze(
                "org-1", "user-1",
                new CrmProductSalesAnalysisService.SalesRankRequest(null, null, null, 5, false));

        assertThat(result.status()).isEqualTo(CrmProductSalesAnalysisService.ResultStatus.PARTIAL);
        assertThat(result.summary().currency()).isEqualTo("CNY");
        assertThat(result.rows().getFirst().pipeline().openOpportunityCount()).isEqualTo(1);
        assertThat(result.rows().getFirst().pipeline().amount()).isNull();
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("管道") && warning.contains("币种"));
    }

    @Test
    void includesTask205OpenStagesAndFailsClosedForUnknownOpportunityStages() {
        when(cloudcc.queryAllRecords(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> switch (invocation.<String>getArgument(2)) {
                    case "product" -> List.of(
                            map("id", "p1", "name", "智能巡检终端 X1", "cpdm", "DEMO-X1", "unit", "台"));
                    case "cloudccorder" -> List.of(
                            orderWithCurrency("o1", "2026-07-10", "已生效", "a1", "CNY"));
                    case "cloudccorderitem" -> List.of(
                            item("i1", "o1", "p1", "5", "100", "500", ""));
                    case "Opportunity" -> List.of(
                            opportunity("op1", "a1", "1-发现机会", "CNY"),
                            opportunity("op6", "a1", "6-商讨/审核", "CNY"),
                            opportunity("op7", "a1", "7-签约关单", "CNY"),
                            opportunity("op8", "a1", "8-丢单", "CNY"),
                            opportunity("op9", "a1", "9-待复核", "CNY"),
                            opportunity("op-blank", "a1", "", "CNY"));
                    case "opportunitypdt" -> List.of(
                            opportunityItem("opd1", "op1", "p1", "CNY"),
                            opportunityItem("opd6", "op6", "p1", "CNY"),
                            opportunityItem("opd7", "op7", "p1", "CNY"),
                            opportunityItem("opd8", "op8", "p1", "CNY"),
                            opportunityItem("opd9", "op9", "p1", "CNY"),
                            opportunityItem("opd-blank", "op-blank", "p1", "CNY"));
                    default -> List.of();
                });

        CrmProductSalesAnalysisService.SalesRankResult result = service.analyze(
                "org-1", "user-1",
                new CrmProductSalesAnalysisService.SalesRankRequest(null, null, null, 5, false));

        assertThat(result.status()).isEqualTo(CrmProductSalesAnalysisService.ResultStatus.PARTIAL);
        assertThat(result.rows().getFirst().pipeline().openOpportunityCount()).isEqualTo(2);
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("商机阶段")
                && warning.contains("未知") && warning.contains("排除"));
    }

    @Test
    void keepsRealizedSalesWhenOptionalOpportunityQueryIsDenied() {
        when(cloudcc.queryAllRecords(anyString(), anyString(), eq("Opportunity"), anyString(), anyString()))
                .thenThrow(new CloudccOpenApiService.CloudccApiException("403", "没有商机对象权限"));

        CrmProductSalesAnalysisService.SalesRankResult result = service.analyze(
                "org-1", "user-1",
                new CrmProductSalesAnalysisService.SalesRankRequest(null, null, null, 5, false));

        assertThat(result.status()).isEqualTo(CrmProductSalesAnalysisService.ResultStatus.PARTIAL);
        assertThat(result.rows()).isNotEmpty();
        assertThat(result.rows().getFirst().salesQuantity()).isEqualByComparingTo("15");
        assertThat(result.rows().getFirst().pipeline().openOpportunityCount()).isZero();
        assertThat(result.insights()).extracting(CrmProductSalesAnalysisService.BusinessInsight::code)
                .doesNotContain("PIPELINE_GAP");
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("商机") && warning.contains("已保留订单销售事实"));
    }

    @Test
    void doesNotInferMissingRenewalWhenOpportunityDataIsUnavailable() {
        when(cloudcc.queryAllRecords(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String objectName = invocation.getArgument(2);
                    if ("Opportunity".equals(objectName)) {
                        throw new CloudccOpenApiService.CloudccApiException("403", "没有商机对象权限");
                    }
                    return switch (objectName) {
                        case "product" -> List.of(
                                map("id", "p1", "name", "智能巡检终端 X1", "cpdm", "DEMO-X1", "unit", "台"));
                        case "cloudccorder" -> List.of(
                                orderWithRelations("o1", "2026-07-10", "a1", "c1", "CNY"));
                        case "cloudccorderitem" -> List.of(
                                item("i1", "o1", "p1", "8", "1000", "8000", "已生效"));
                        case "contract" -> List.of(
                                map("id", "c1", "khmc", Map.of("id", "a1"), "zhuangtai", "已生效",
                                        "htjsrq", "2026-08-30", "currency", "CNY"));
                        default -> List.of();
                    };
                });

        CrmProductSalesAnalysisService.SalesRankResult result = service.analyze(
                "org-1", "user-1",
                new CrmProductSalesAnalysisService.SalesRankRequest(null, null, null, 5, false));

        assertThat(result.status()).isEqualTo(CrmProductSalesAnalysisService.ResultStatus.PARTIAL);
        assertThat(result.rows().getFirst().contracts().activeContractCount()).isEqualTo(1);
        assertThat(result.rows().getFirst().contracts().expiringWithin90DaysCount()).isEqualTo(1);
        assertThat(result.rows().getFirst().contracts().expiringWithoutRenewalCount()).isZero();
        assertThat(result.insights()).extracting(CrmProductSalesAnalysisService.BusinessInsight::code)
                .doesNotContain("RENEWAL_RISK");
    }

    @Test
    void keepsQuantityRankingButSuppressesIncomparableAmountsForMultipleCurrencies() {
        when(cloudcc.queryAllRecords(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> switch (invocation.<String>getArgument(2)) {
                    case "product" -> List.of(
                            map("id", "p1", "name", "产品一", "cpdm", "P-1", "unit", "台"),
                            map("id", "p2", "name", "产品二", "cpdm", "P-2", "unit", "台"));
                    case "cloudccorder" -> List.of(
                            orderWithCurrency("o1", "2026-07-10", "已完成", "a1", "CNY"),
                            orderWithCurrency("o2", "2026-07-11", "已完成", "a2", "USD"));
                    case "cloudccorderitem" -> List.of(
                            item("i1", "o1", "p1", "10", "100", "1000", "已生效"),
                            item("i2", "o2", "p2", "5", "200", "1000", "已生效"));
                    default -> List.of();
                });

        CrmProductSalesAnalysisService.SalesRankResult result = service.analyze(
                "org-1", "user-1",
                new CrmProductSalesAnalysisService.SalesRankRequest(
                        CrmProductSalesAnalysisService.Metric.SALES_QUANTITY,
                        null, null, 5, false));

        assertThat(result.status()).isEqualTo(CrmProductSalesAnalysisService.ResultStatus.PARTIAL);
        assertThat(result.rows()).extracting(CrmProductSalesAnalysisService.SalesRankRow::productCode)
                .containsExactly("P-1", "P-2");
        assertThat(result.rows()).allSatisfy(row -> {
            assertThat(row.salesAmount()).isNull();
            assertThat(row.amountContributionRate()).isNull();
            assertThat(row.realizedAveragePrice()).isNull();
        });
        assertThat(result.summary().amountComparable()).isFalse();
        assertThat(result.summary().totalSalesAmount()).isNull();
        assertThat(result.summary().amountLeader()).isNull();
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("多币种"));
    }

    @Test
    void recognizesEnabledAndApprovedContractsAsActiveTenantValues() {
        when(cloudcc.queryAllRecords(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> switch (invocation.<String>getArgument(2)) {
                    case "product" -> List.of(map("id", "p1", "name", "X1", "cpdm", "P-1", "unit", "台"));
                    case "cloudccorder" -> List.of(
                            orderWithRelations("o1", "2026-07-10", "a1", "c1", "CNY"),
                            orderWithRelations("o2", "2026-07-11", "a2", "c2", "CNY"),
                            orderWithRelations("o3", "2026-07-12", "a3", "c3", "CNY"),
                            orderWithRelations("o4", "2026-07-13", "a4", "c4", "CNY"),
                            orderWithRelations("o5", "2026-07-14", "a5", "c5", "CNY"));
                    case "cloudccorderitem" -> List.of(
                            item("i1", "o1", "p1", "1", "100", "100", "已生效"),
                            item("i2", "o2", "p1", "1", "100", "100", "已生效"),
                            item("i3", "o3", "p1", "1", "100", "100", "已生效"),
                            item("i4", "o4", "p1", "1", "100", "100", "已生效"),
                            item("i5", "o5", "p1", "1", "100", "100", "已生效"));
                    case "contract" -> List.of(
                            map("id", "c1", "khmc", Map.of("id", "a1"), "zhuangtai", "已启用", "htjsrq", "2026-12-31"),
                            map("id", "c2", "khmc", Map.of("id", "a2"), "zhuangtai", "审批通过", "htjsrq", "2026-12-31"),
                            map("id", "c3", "khmc", Map.of("id", "a3"), "zhuangtai", "待复核", "htjsrq", "2026-12-31"),
                            map("id", "c4", "khmc", Map.of("id", "a4"), "zhuangtai", "", "htjsrq", "2026-12-31"),
                            map("id", "c5", "khmc", Map.of("id", "a5"), "zhuangtai", "未生效", "htjsrq", "2026-12-31"));
                    default -> List.of();
                });

        CrmProductSalesAnalysisService.SalesRankResult result = service.analyze(
                "org-1", "user-1", new CrmProductSalesAnalysisService.SalesRankRequest(null, null, null, 5, false));

        assertThat(result.status()).isEqualTo(CrmProductSalesAnalysisService.ResultStatus.PARTIAL);
        assertThat(result.rows().getFirst().contracts().activeContractCount()).isEqualTo(2);
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("合同状态")
                && warning.contains("未知") && warning.contains("排除"));
    }

    @Test
    void reportsDataAccessIncompleteWhenEveryReferencedProductIsInvisible() {
        when(cloudcc.queryAllRecords(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> switch (invocation.<String>getArgument(2)) {
                    case "product" -> List.of();
                    case "cloudccorder" -> List.of(order("o1", "2026-07-10", "已完成", "a1"));
                    case "cloudccorderitem" -> List.of(
                            item("i1", "o1", "hidden-p1", "5", "100", "500", "已生效"));
                    default -> List.of();
                });

        CrmProductSalesAnalysisService.SalesRankResult result = service.analyze(
                "org-1", "user-1", new CrmProductSalesAnalysisService.SalesRankRequest(null, null, null, 5, false));

        assertThat(result.status()).isEqualTo(CrmProductSalesAnalysisService.ResultStatus.DATA_ACCESS_INCOMPLETE);
        assertThat(result.rows()).isEmpty();
        assertThat(result.coverage().includedOrders()).isZero();
        assertThat(result.coverage().includedItems()).isZero();
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("产品主数据") && warning.contains("权限"));
    }

    @Test
    void diagnosesDiscountDrivenGrowthAndPipelineLedPotentialGrowthFromFacts() {
        when(cloudcc.queryAllRecords(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> switch (invocation.<String>getArgument(2)) {
                    case "product" -> List.of(
                            map("id", "p1", "name", "现有产品", "cpdm", "P-1", "unit", "台"),
                            map("id", "p2", "name", "潜力产品", "cpdm", "P-2", "unit", "台"));
                    case "cloudccorder" -> List.of(
                            order("current", "2026-07-10", "已完成", "a1"),
                            order("previous", "2026-06-10", "已完成", "a1"));
                    case "cloudccorderitem" -> List.of(
                            item("current-item", "current", "p1", "20", "80", "1600", "已生效"),
                            item("previous-item", "previous", "p1", "10", "100", "1000", "已生效"));
                    case "Opportunity" -> List.of(
                            map("id", "future-op", "khmc", Map.of("id", "a2"), "jieduan", "3-提出方案",
                                    "jsrq", "2026-08-25", "currency", "CNY"));
                    case "opportunitypdt" -> List.of(
                            map("id", "future-line", "opportunity", Map.of("id", "future-op"),
                                    "product2", Map.of("id", "p2"), "quantity", "30", "totalprice", "30000"));
                    default -> List.of();
                });

        CrmProductSalesAnalysisService.SalesRankResult result = service.analyze(
                "org-1", "user-1",
                new CrmProductSalesAnalysisService.SalesRankRequest(null, null, null, 5, true));

        assertThat(result.insights()).extracting(CrmProductSalesAnalysisService.BusinessInsight::code)
                .contains("DISCOUNT_DRIVEN", "POTENTIAL_GROWTH");
        assertThat(result.insights()).filteredOn(insight -> "DISCOUNT_DRIVEN".equals(insight.code()))
                .singleElement()
                .satisfies(insight -> assertThat(insight.evidence())
                        .contains("销量增长 100%", "订单销售额增长 60%", "实现均价下降 20%"));
        assertThat(result.insights()).filteredOn(insight -> "POTENTIAL_GROWTH".equals(insight.code()))
                .singleElement()
                .satisfies(insight -> assertThat(insight.evidence())
                        .contains("当前期无已实现销售", "开放商机 1 个", "管道数量 30"));
    }

    @Test
    void doesNotCalculateAmountTrendsAcrossDifferentPeriodCurrencies() {
        when(cloudcc.queryAllRecords(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> switch (invocation.<String>getArgument(2)) {
                    case "product" -> List.of(
                            map("id", "p1", "name", "现有产品", "cpdm", "P-1", "unit", "台"));
                    case "cloudccorder" -> List.of(
                            orderWithCurrency("current", "2026-07-10", "已完成", "a1", "CNY"),
                            orderWithCurrency("previous", "2026-06-10", "已完成", "a1", "USD"));
                    case "cloudccorderitem" -> List.of(
                            item("current-item", "current", "p1", "20", "80", "1600", "已生效"),
                            item("previous-item", "previous", "p1", "10", "100", "1000", "已生效"));
                    default -> List.of();
                });

        CrmProductSalesAnalysisService.SalesRankResult result = service.analyze(
                "org-1", "user-1",
                new CrmProductSalesAnalysisService.SalesRankRequest(null, null, null, 5, true));

        assertThat(result.status()).isEqualTo(CrmProductSalesAnalysisService.ResultStatus.PARTIAL);
        assertThat(result.rows().getFirst().salesAmount()).isEqualByComparingTo("1600");
        assertThat(result.summary().currency()).isEqualTo("CNY");
        assertThat(result.insights()).extracting(CrmProductSalesAnalysisService.BusinessInsight::code)
                .doesNotContain("CORE_GROWTH", "DISCOUNT_DRIVEN");
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("当前期")
                && warning.contains("上期") && warning.contains("币种"));
    }

    @Test
    void doesNotExposeSalesAmountGrowthAcrossDifferentPeriodCurrencies() {
        when(cloudcc.queryAllRecords(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> switch (invocation.<String>getArgument(2)) {
                    case "product" -> List.of(
                            map("id", "p1", "name", "现有产品", "cpdm", "P-1", "unit", "台"));
                    case "cloudccorder" -> List.of(
                            orderWithCurrency("current", "2026-07-10", "已完成", "a1", "CNY"),
                            orderWithCurrency("previous", "2026-06-10", "已完成", "a1", "USD"));
                    case "cloudccorderitem" -> List.of(
                            item("current-item", "current", "p1", "20", "80", "1600", "已生效"),
                            item("previous-item", "previous", "p1", "10", "100", "1000", "已生效"));
                    default -> List.of();
                });

        CrmProductSalesAnalysisService.SalesRankResult result = service.analyze(
                "org-1", "user-1",
                new CrmProductSalesAnalysisService.SalesRankRequest(
                        CrmProductSalesAnalysisService.Metric.SALES_AMOUNT,
                        null, null, 5, true));

        assertThat(result.status()).isEqualTo(CrmProductSalesAnalysisService.ResultStatus.PARTIAL);
        assertThat(result.rows().getFirst().previousValue()).isNull();
        assertThat(result.rows().getFirst().changeRate()).isNull();
        String answer = new CrmProductSalesAnswerFormatter(new ObjectMapper().findAndRegisterModules())
                .format(result);
        assertThat(answer).contains("无可比基期").doesNotContain("+60%");
    }

    @Test
    void missingCurrencyInAnyCurrentFactSuppressesAmountsAndBlocksAmountRanking() {
        when(cloudcc.queryAllRecords(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> switch (invocation.<String>getArgument(2)) {
                    case "product" -> List.of(
                            map("id", "p1", "name", "产品一", "cpdm", "P-1", "unit", "台"),
                            map("id", "p2", "name", "产品二", "cpdm", "P-2", "unit", "台"));
                    case "cloudccorder" -> List.of(
                            orderWithCurrency("o1", "2026-07-10", "已完成", "a1", "CNY"),
                            orderWithCurrency("o2", "2026-07-11", "已完成", "a2", ""));
                    case "cloudccorderitem" -> List.of(
                            item("i1", "o1", "p1", "10", "100", "1000", "已生效"),
                            item("i2", "o2", "p2", "5", "200", "1000", "已生效"));
                    default -> List.of();
                });

        CrmProductSalesAnalysisService.SalesRankResult quantityResult = service.analyze(
                "org-1", "user-1",
                new CrmProductSalesAnalysisService.SalesRankRequest(
                        CrmProductSalesAnalysisService.Metric.SALES_QUANTITY,
                        null, null, 5, false));

        assertThat(quantityResult.status()).isEqualTo(CrmProductSalesAnalysisService.ResultStatus.PARTIAL);
        assertThat(quantityResult.summary().amountComparable()).isFalse();
        assertThat(quantityResult.summary().totalSalesAmount()).isNull();
        assertThat(quantityResult.rows()).allSatisfy(row -> {
            assertThat(row.salesAmount()).isNull();
            assertThat(row.amountContributionRate()).isNull();
            assertThat(row.realizedAveragePrice()).isNull();
            assertThat(row.top1CustomerConcentration()).isNull();
            assertThat(row.amountRank()).isZero();
        });
        assertThat(quantityResult.insights())
                .extracting(CrmProductSalesAnalysisService.BusinessInsight::code)
                .doesNotContain("CORE_GROWTH", "DISCOUNT_DRIVEN", "CUSTOMER_CONCENTRATION", "HIGH_VALUE_PRODUCT");
        assertThat(quantityResult.warnings()).anyMatch(warning -> warning.contains("币种缺失"));

        CrmProductSalesAnalysisService.SalesRankResult amountResult = service.analyze(
                "org-1", "user-1",
                new CrmProductSalesAnalysisService.SalesRankRequest(
                        CrmProductSalesAnalysisService.Metric.SALES_AMOUNT,
                        null, null, 5, false));

        assertThat(amountResult.status()).isEqualTo(CrmProductSalesAnalysisService.ResultStatus.DATA_QUALITY_BLOCKED);
        assertThat(amountResult.rows()).isEmpty();
        assertThat(amountResult.summary().amountComparable()).isFalse();
        assertThat(amountResult.summary().totalSalesAmount()).isNull();
    }

    @Test
    void allCurrentCurrenciesMissingAlsoFailsClosedForAmounts() {
        when(cloudcc.queryAllRecords(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> switch (invocation.<String>getArgument(2)) {
                    case "product" -> List.of(
                            map("id", "p1", "name", "产品一", "cpdm", "P-1", "unit", "台"));
                    case "cloudccorder" -> List.of(
                            orderWithCurrency("o1", "2026-07-10", "已完成", "a1", ""));
                    case "cloudccorderitem" -> List.of(
                            item("i1", "o1", "p1", "10", "100", "1000", "已生效"));
                    default -> List.of();
                });

        CrmProductSalesAnalysisService.SalesRankResult quantityResult = service.analyze(
                "org-1", "user-1",
                new CrmProductSalesAnalysisService.SalesRankRequest(
                        CrmProductSalesAnalysisService.Metric.SALES_QUANTITY,
                        null, null, 5, false));
        CrmProductSalesAnalysisService.SalesRankResult amountResult = service.analyze(
                "org-1", "user-1",
                new CrmProductSalesAnalysisService.SalesRankRequest(
                        CrmProductSalesAnalysisService.Metric.SALES_AMOUNT,
                        null, null, 5, false));

        assertThat(quantityResult.status()).isEqualTo(CrmProductSalesAnalysisService.ResultStatus.PARTIAL);
        assertThat(quantityResult.rows()).hasSize(1);
        assertThat(quantityResult.rows().getFirst().salesQuantity()).isEqualByComparingTo("10");
        assertThat(quantityResult.rows().getFirst().salesAmount()).isNull();
        assertThat(quantityResult.summary().amountComparable()).isFalse();
        assertThat(quantityResult.warnings()).anyMatch(warning -> warning.contains("币种缺失"));
        assertThat(amountResult.status()).isEqualTo(CrmProductSalesAnalysisService.ResultStatus.DATA_QUALITY_BLOCKED);
        assertThat(amountResult.rows()).isEmpty();
        assertThat(amountResult.summary().amountComparable()).isFalse();
        assertThat(amountResult.summary().totalSalesAmount()).isNull();
    }

    @Test
    void missingPipelineCurrencySuppressesPipelineAmount() {
        when(cloudcc.queryAllRecords(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> switch (invocation.<String>getArgument(2)) {
                    case "product" -> List.of(
                            map("id", "p1", "name", "产品一", "cpdm", "P-1", "unit", "台"));
                    case "cloudccorder" -> List.of(
                            orderWithCurrency("o1", "2026-07-10", "已完成", "a1", "CNY"));
                    case "cloudccorderitem" -> List.of(
                            item("i1", "o1", "p1", "10", "100", "1000", "已生效"));
                    case "Opportunity" -> List.of(
                            opportunity("op1", "a1", "3-提出方案", "CNY"));
                    case "opportunitypdt" -> List.of(
                            opportunityItem("opd1", "op1", "p1", ""));
                    default -> List.of();
                });

        CrmProductSalesAnalysisService.SalesRankResult result = service.analyze(
                "org-1", "user-1",
                new CrmProductSalesAnalysisService.SalesRankRequest(null, null, null, 5, false));

        assertThat(result.status()).isEqualTo(CrmProductSalesAnalysisService.ResultStatus.PARTIAL);
        assertThat(result.rows().getFirst().pipeline().openOpportunityCount()).isEqualTo(1);
        assertThat(result.rows().getFirst().pipeline().quantity()).isEqualByComparingTo("1");
        assertThat(result.rows().getFirst().pipeline().amount()).isNull();
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("管道") && warning.contains("币种"));
    }

    @Test
    void opportunityProductMasterVisibilityGapDisablesPipelineAndRenewalAbsenceInsights() {
        when(cloudcc.queryAllRecords(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> switch (invocation.<String>getArgument(2)) {
                    case "product" -> List.of(
                            map("id", "p1", "name", "产品一", "cpdm", "P-1", "unit", "台"));
                    case "cloudccorder" -> List.of(
                            orderWithRelations("o1", "2026-07-10", "a1", "c1", "CNY"));
                    case "cloudccorderitem" -> List.of(
                            item("i1", "o1", "p1", "10", "100", "1000", "已生效"));
                    case "Account" -> List.of(map("id", "a1", "name", "客户一"));
                    case "Opportunity" -> List.of();
                    case "opportunitypdt" -> List.of(
                            opportunityItem("opd-hidden", "op-hidden", "p1", "CNY"));
                    case "contract" -> List.of(
                            map("id", "c1", "khmc", Map.of("id", "a1"), "zhuangtai", "已生效",
                                    "htjsrq", "2026-08-30", "currency", "CNY"));
                    default -> List.of();
                });

        CrmProductSalesAnalysisService.SalesRankResult result = service.analyze(
                "org-1", "user-1",
                new CrmProductSalesAnalysisService.SalesRankRequest(null, null, null, 5, false));

        assertThat(result.status()).isEqualTo(CrmProductSalesAnalysisService.ResultStatus.PARTIAL);
        assertThat(result.sourceObjects()).doesNotContain("Opportunity", "opportunitypdt");
        assertThat(result.rows().getFirst().pipeline().openOpportunityCount()).isZero();
        assertThat(result.rows().getFirst().contracts().activeContractCount()).isEqualTo(1);
        assertThat(result.rows().getFirst().contracts().expiringWithin90DaysCount()).isEqualTo(1);
        assertThat(result.rows().getFirst().contracts().expiringWithoutRenewalCount()).isZero();
        assertThat(result.insights()).extracting(CrmProductSalesAnalysisService.BusinessInsight::code)
                .doesNotContain("PIPELINE_GAP", "RENEWAL_RISK");
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("商机产品")
                && warning.contains("可见性") && warning.contains("不可用"));
    }

    @Test
    void referencedContractVisibilityGapDisablesContractEnhancement() {
        when(cloudcc.queryAllRecords(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> switch (invocation.<String>getArgument(2)) {
                    case "product" -> List.of(
                            map("id", "p1", "name", "产品一", "cpdm", "P-1", "unit", "台"));
                    case "cloudccorder" -> List.of(
                            orderWithRelations("o1", "2026-07-10", "a1", "contract-hidden", "CNY"));
                    case "cloudccorderitem" -> List.of(
                            item("i1", "o1", "p1", "10", "100", "1000", "已生效"));
                    case "contract" -> List.of();
                    default -> List.of();
                });

        CrmProductSalesAnalysisService.SalesRankResult result = service.analyze(
                "org-1", "user-1",
                new CrmProductSalesAnalysisService.SalesRankRequest(null, null, null, 5, false));

        assertThat(result.status()).isEqualTo(CrmProductSalesAnalysisService.ResultStatus.PARTIAL);
        assertThat(result.sourceObjects()).doesNotContain("contract");
        assertThat(result.rows().getFirst().contracts().activeContractCount()).isZero();
        assertThat(result.insights()).extracting(CrmProductSalesAnalysisService.BusinessInsight::code)
                .doesNotContain("RENEWAL_RISK");
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("订单")
                && warning.contains("合同") && warning.contains("可见性") && warning.contains("不可用"));
    }

    @Test
    void coverageCountsOnlyOrdersAndItemsThatContributeVerifiedSalesFacts() {
        when(cloudcc.queryAllRecords(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> switch (invocation.<String>getArgument(2)) {
                    case "product" -> List.of(
                            map("id", "p1", "name", "产品一", "cpdm", "P-1", "unit", "台"));
                    case "cloudccorder" -> List.of(
                            orderWithCurrency("o1", "2026-07-10", "已完成", "a1", "CNY"),
                            orderWithCurrency("o2", "2026-07-11", "已完成", "a2", "CNY"));
                    case "cloudccorderitem" -> List.of(
                            item("i1", "o1", "p1", "10", "100", "1000", "已生效"),
                            item("i2", "o2", "p-hidden", "5", "100", "500", "已生效"));
                    default -> List.of();
                });

        CrmProductSalesAnalysisService.SalesRankResult result = service.analyze(
                "org-1", "user-1",
                new CrmProductSalesAnalysisService.SalesRankRequest(null, null, null, 5, false));

        assertThat(result.coverage().scannedOrders()).isEqualTo(2);
        assertThat(result.coverage().includedOrders()).isEqualTo(1);
        assertThat(result.coverage().excludedOrders()).isEqualTo(1);
        assertThat(result.coverage().scannedItems()).isEqualTo(2);
        assertThat(result.coverage().includedItems()).isEqualTo(1);
        assertThat(result.coverage().excludedItems()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "p-hidden"})
    void blankOrInvisibleOpportunityProductReferenceDisablesPipelineEnhancement(String opportunityProductId) {
        when(cloudcc.queryAllRecords(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> switch (invocation.<String>getArgument(2)) {
                    case "product" -> List.of(
                            map("id", "p1", "name", "产品一", "cpdm", "P-1", "unit", "台"));
                    case "cloudccorder" -> List.of(
                            orderWithCurrency("o1", "2026-07-10", "已完成", "a1", "CNY"));
                    case "cloudccorderitem" -> List.of(
                            item("i1", "o1", "p1", "10", "100", "1000", "已生效"));
                    case "Account" -> List.of(map("id", "a1", "name", "客户一"));
                    case "Opportunity" -> List.of(
                            opportunity("op1", "a1", "3-提出方案", "CNY"));
                    case "opportunitypdt" -> List.of(
                            opportunityItem("opd1", "op1", opportunityProductId, "CNY"));
                    default -> List.of();
                });

        CrmProductSalesAnalysisService.SalesRankResult result = service.analyze(
                "org-1", "user-1",
                new CrmProductSalesAnalysisService.SalesRankRequest(null, null, null, 5, false));

        assertThat(result.status()).isEqualTo(CrmProductSalesAnalysisService.ResultStatus.PARTIAL);
        assertThat(result.sourceObjects()).doesNotContain("Opportunity", "opportunitypdt");
        assertThat(result.rows().getFirst().pipeline().openOpportunityCount()).isZero();
        assertThat(result.insights()).extracting(CrmProductSalesAnalysisService.BusinessInsight::code)
                .doesNotContain("PIPELINE_GAP", "POTENTIAL_GROWTH");
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("商机产品")
                && warning.contains("产品引用") && warning.contains("不可用"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a-hidden"})
    void missingOrInvisibleOpportunityCustomerDisablesRenewalLinkage(String opportunityAccountId) {
        stubRenewalLinkageFacts(opportunityAccountId, "a1");

        assertRenewalLinkageUnavailable(service.analyze(
                "org-1", "user-1",
                new CrmProductSalesAnalysisService.SalesRankRequest(null, null, null, 5, false)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a-hidden"})
    void missingOrInvisibleContractCustomerDisablesRenewalLinkage(String contractAccountId) {
        stubRenewalLinkageFacts("a1", contractAccountId);

        assertRenewalLinkageUnavailable(service.analyze(
                "org-1", "user-1",
                new CrmProductSalesAnalysisService.SalesRankRequest(null, null, null, 5, false)));
    }

    private void stubRenewalLinkageFacts(String opportunityAccountId, String contractAccountId) {
        when(cloudcc.queryAllRecords(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> switch (invocation.<String>getArgument(2)) {
                    case "product" -> List.of(
                            map("id", "p1", "name", "产品一", "cpdm", "P-1", "unit", "台"));
                    case "cloudccorder" -> List.of(
                            orderWithRelations("o1", "2026-07-10", "a1", "c1", "CNY"));
                    case "cloudccorderitem" -> List.of(
                            item("i1", "o1", "p1", "10", "100", "1000", "已生效"));
                    case "Account" -> List.of(map("id", "a1", "name", "客户一"));
                    case "Opportunity" -> List.of(
                            opportunity("op1", opportunityAccountId, "3-提出方案", "CNY"));
                    case "opportunitypdt" -> List.of(
                            opportunityItem("opd1", "op1", "p1", "CNY"));
                    case "contract" -> List.of(
                            map("id", "c1", "khmc", Map.of("id", contractAccountId),
                                    "zhuangtai", "已生效", "htjsrq", "2026-08-30", "currency", "CNY"));
                    default -> List.of();
                });
    }

    private void assertRenewalLinkageUnavailable(CrmProductSalesAnalysisService.SalesRankResult result) {
        assertThat(result.status()).isEqualTo(CrmProductSalesAnalysisService.ResultStatus.PARTIAL);
        assertThat(result.sourceObjects())
                .contains("Opportunity", "opportunitypdt", "contract")
                .doesNotContain("Account");
        CrmProductSalesAnalysisService.SalesRankRow row = result.rows().getFirst();
        assertThat(row.pipeline().openOpportunityCount()).isEqualTo(1);
        assertThat(row.contracts().activeContractCount()).isEqualTo(1);
        assertThat(row.contracts().expiringWithin90DaysCount()).isEqualTo(1);
        assertThat(row.contracts().expiringWithoutRenewalCount()).isZero();
        assertThat(result.insights()).extracting(CrmProductSalesAnalysisService.BusinessInsight::code)
                .doesNotContain("RENEWAL_RISK");
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("续约")
                && warning.contains("客户引用") && warning.contains("不可用"));
        String answer = new CrmProductSalesAnswerFormatter(new ObjectMapper().findAndRegisterModules())
                .format(result);
        assertThat(answer).contains("续约关联不可用")
                .doesNotContain("未关联续约商机 0 份", "存在续约缺口");
    }

    private void stubInvalidCurrentTotalPrice() {
        when(cloudcc.queryAllRecords(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> switch (invocation.<String>getArgument(2)) {
                    case "product" -> List.of(
                            map("id", "p1", "name", "智能巡检终端 X1", "cpdm", "DEMO-X1", "unit", "台"));
                    case "cloudccorder" -> List.of(
                            orderWithCurrency("o1", "2026-07-10", "已生效", "a1", "CNY"));
                    case "cloudccorderitem" -> List.of(
                            item("invalid-total", "o1", "p1", "3", "100", "not-a-number", ""));
                    case "Account" -> List.of(map("id", "a1", "name", "可见客户"));
                    default -> List.of();
                });
    }

    private List<Map<String, Object>> records(String objectApiName) {
        return switch (objectApiName) {
            case "product" -> List.of(
                    map("id", "p1", "name", "智能巡检终端 X1", "cpdm", "DEMO-X1", "unit", "台"),
                    map("id", "p2", "name", "边缘采集网关 G5", "cpdm", "DEMO-G5", "unit", "台")
            );
            case "cloudccorder" -> List.of(
                    order("o1", "2026-07-10", "已完成", "a1"),
                    order("o2", "2026-07-11", "已确认", "a1"),
                    order("o3", "2026-07-12", "已取消", "a2"),
                    order("o4", "2026-07-13", "已完成", "a2"),
                    order("po1", "2026-06-10", "已完成", "a3"),
                    order("future", "2026-07-20", "已完成", "a4")
            );
            case "cloudccorderitem" -> List.of(
                    item("i1", "o1", "p1", "10", "100", "1000", "已生效"),
                    item("i2", "o2", "p1", "5", "100", "500", "已生效"),
                    item("i3", "o3", "p1", "999", "100", "99900", "已生效"),
                    item("i4", "o4", "p2", "12", "500", "6000", "已生效"),
                    item("pi1", "po1", "p1", "10", "100", "1000", "已生效"),
                    item("fi1", "future", "p2", "500", "500", "250000", "已生效"),
                    item("draft-item", "o1", "p2", "800", "500", "400000", "草稿")
            );
            default -> List.of();
        };
    }

    private Map<String, Object> order(String id, String date, String status, String accountId) {
        return orderWithCurrency(id, date, status, accountId, "CNY");
    }

    private Map<String, Object> orderWithCurrency(String id,
                                                  String date,
                                                  String status,
                                                  String accountId,
                                                  String currency) {
        return map(
                "id", id,
                "name", "订单-" + id,
                "accountid", Map.of("id", accountId, "name", "客户-" + accountId),
                "podate", date,
                "status", status,
                "currency", currency,
                "totalamount", BigDecimal.ZERO
        );
    }

    private Map<String, Object> orderWithRelations(String id,
                                                   String date,
                                                   String accountId,
                                                   String contractId,
                                                   String currency) {
        Map<String, Object> order = orderWithCurrency(id, date, "已完成", accountId, currency);
        order.put("contractid", Map.of("id", contractId));
        return order;
    }

    private Map<String, Object> opportunity(String id,
                                            String accountId,
                                            String stage,
                                            String currency) {
        return map(
                "id", id,
                "khmc", Map.of("id", accountId),
                "jieduan", stage,
                "jsrq", "2026-08-20",
                "currency", currency
        );
    }

    private Map<String, Object> opportunityItem(String id,
                                                String opportunityId,
                                                String productId,
                                                String currency) {
        return map(
                "id", id,
                "opportunity", Map.of("id", opportunityId),
                "product2", Map.of("id", productId),
                "quantity", "1",
                "totalprice", "100",
                "unit", "台",
                "currency", currency
        );
    }

    private Map<String, Object> item(String id,
                                     String orderId,
                                     String productId,
                                     String quantity,
                                     String unitPrice,
                                     String totalPrice,
                                     String status) {
        return map(
                "id", id,
                "orderid", Map.of("id", orderId),
                "product2id", Map.of("id", productId),
                "quantity", quantity,
                "unitprice", unitPrice,
                "totalprice", totalPrice,
                "status", status,
                "unit", "台"
        );
    }

    private Map<String, Object> map(Object... values) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }
}
