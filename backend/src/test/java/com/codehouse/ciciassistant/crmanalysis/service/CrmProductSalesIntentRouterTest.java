package com.codehouse.ciciassistant.crmanalysis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CrmProductSalesIntentRouterTest {

    @Test
    void shouldForceQuantityRankingForExplicitSalesVolumeQuestion() {
        assertThat(CrmProductSalesIntentRouter.route("嗯，销量最好的产品有哪些？"))
                .contains("{\"metric\":\"SALES_QUANTITY\",\"topN\":5,\"comparePrevious\":true}");
    }

    @Test
    void shouldUseAmountForGenericBestSellingAndIgnoreUnrelatedQuestions() {
        assertThat(CrmProductSalesIntentRouter.route("最近哪些产品销售得比较好"))
                .contains("{\"metric\":\"SALES_AMOUNT\",\"topN\":5,\"comparePrevious\":true}");
        assertThat(CrmProductSalesIntentRouter.route("最近有哪些重点客户"))
                .isEmpty();
    }

    @Test
    void shouldRouteAllSupportedProductRankingMetrics() {
        assertThat(CrmProductSalesIntentRouter.route("出货量最高的产品有哪些"))
                .contains("{\"metric\":\"SALES_QUANTITY\",\"topN\":5,\"comparePrevious\":true}");
        assertThat(CrmProductSalesIntentRouter.route("销售额最高的产品有哪些"))
                .contains("{\"metric\":\"SALES_AMOUNT\",\"topN\":5,\"comparePrevious\":true}");
        assertThat(CrmProductSalesIntentRouter.route("订单数最多的产品有哪些"))
                .contains("{\"metric\":\"ORDER_COUNT\",\"topN\":5,\"comparePrevious\":true}");
        assertThat(CrmProductSalesIntentRouter.route("购买客户最多的产品有哪些"))
                .contains("{\"metric\":\"CUSTOMER_COUNT\",\"topN\":5,\"comparePrevious\":true}");
    }

    @Test
    void shouldNotForceRouteForConflictingMetricsOrNonDescendingDirections() {
        assertThat(CrmProductSalesIntentRouter.route("销量和销售额最高的产品有哪些"))
                .isEmpty();
        assertThat(CrmProductSalesIntentRouter.route("订单数最少的产品有哪些"))
                .isEmpty();
        assertThat(CrmProductSalesIntentRouter.route("销售额最低的产品有哪些"))
                .isEmpty();
        assertThat(CrmProductSalesIntentRouter.route("客户数从少到多的产品排行"))
                .isEmpty();
    }

    @Test
    void shouldNotForceRouteForWorstRankingDirection() {
        assertThat(CrmProductSalesIntentRouter.route("销量最差的产品有哪些"))
                .isEmpty();
    }

    @Test
    void shouldHonorExplicitTopNOnlyInsideToolContractRange() {
        assertThat(CrmProductSalesIntentRouter.route("销量最高的产品 Top 10"))
                .contains("{\"metric\":\"SALES_QUANTITY\",\"topN\":10,\"comparePrevious\":true}");
        assertThat(CrmProductSalesIntentRouter.route("客户覆盖前十二名的产品"))
                .contains("{\"metric\":\"CUSTOMER_COUNT\",\"topN\":12,\"comparePrevious\":true}");
        assertThat(CrmProductSalesIntentRouter.route("销量最高的产品 Top 21"))
                .isEmpty();
        assertThat(CrmProductSalesIntentRouter.route("销量最高的产品 Top 0"))
                .isEmpty();
    }

    @Test
    void shouldRejectExplicitTopSyntaxThatIsNotAPositiveInteger() {
        assertThat(CrmProductSalesIntentRouter.route("销量最高的产品 Top -1"))
                .isEmpty();
        assertThat(CrmProductSalesIntentRouter.route("销量最高的产品 Top -20"))
                .isEmpty();
        assertThat(CrmProductSalesIntentRouter.route("销量最高的产品 Top -0"))
                .isEmpty();
        assertThat(CrmProductSalesIntentRouter.route("销量最高的产品 Top 3.5"))
                .isEmpty();
    }

    @Test
    void shouldParseCompleteChineseTopNWithoutPrefixTruncation() {
        assertThat(CrmProductSalesIntentRouter.route("销量最高的产品前两名"))
                .contains("{\"metric\":\"SALES_QUANTITY\",\"topN\":2,\"comparePrevious\":true}");
        assertThat(CrmProductSalesIntentRouter.route("销量最高的产品前二十名"))
                .contains("{\"metric\":\"SALES_QUANTITY\",\"topN\":20,\"comparePrevious\":true}");
        assertThat(CrmProductSalesIntentRouter.route("销量最高的产品前一百名"))
                .isEmpty();
        assertThat(CrmProductSalesIntentRouter.route("销量最高的产品 Top 两百"))
                .isEmpty();
    }

    @Test
    void shouldResolveSupportedRelativeAndCalendarRanges() {
        LocalDate today = LocalDate.of(2026, 7, 14);

        assertThat(CrmProductSalesIntentRouter.route("近7天销量最高的产品 Top 3", today))
                .contains("{\"metric\":\"SALES_QUANTITY\",\"startDate\":\"2026-07-08\","
                        + "\"endDate\":\"2026-07-14\",\"topN\":3,\"comparePrevious\":true}");
        assertThat(CrmProductSalesIntentRouter.route("本月订单数最多的产品", today))
                .contains("{\"metric\":\"ORDER_COUNT\",\"startDate\":\"2026-07-01\","
                        + "\"endDate\":\"2026-07-14\",\"topN\":5,\"comparePrevious\":true}");
        assertThat(CrmProductSalesIntentRouter.route("本季度购买客户最多的产品", today))
                .contains("{\"metric\":\"CUSTOMER_COUNT\",\"startDate\":\"2026-07-01\","
                        + "\"endDate\":\"2026-07-14\",\"topN\":5,\"comparePrevious\":true}");
    }

    @Test
    void shouldNotForceRouteWhenDifferentTimeExpressionsConflict() {
        LocalDate today = LocalDate.of(2026, 7, 14);

        assertThat(CrmProductSalesIntentRouter.route("近7天和本月销量最高的产品", today))
                .isEmpty();
        assertThat(CrmProductSalesIntentRouter.route(
                "2026-06-01到2026-06-30且近7天销量最高的产品", today))
                .isEmpty();
    }

    @Test
    void shouldResolvePreviousCalendarMonth() {
        assertThat(CrmProductSalesIntentRouter.route(
                "上月销量最高的产品", LocalDate.of(2026, 7, 14)))
                .contains("{\"metric\":\"SALES_QUANTITY\",\"startDate\":\"2026-06-01\","
                        + "\"endDate\":\"2026-06-30\",\"topN\":5,\"comparePrevious\":true}");
        assertThat(CrmProductSalesIntentRouter.route(
                "上个月销量最高的产品", LocalDate.of(2026, 7, 14)))
                .contains("{\"metric\":\"SALES_QUANTITY\",\"startDate\":\"2026-06-01\","
                        + "\"endDate\":\"2026-06-30\",\"topN\":5,\"comparePrevious\":true}");
    }

    @Test
    void shouldResolveExplicitDateRangesWithoutChangingRequestedMetricOrTopN() {
        assertThat(CrmProductSalesIntentRouter.route(
                "2026-06-01到2026-06-30销售额最高的产品前8名", LocalDate.of(2026, 7, 14)))
                .contains("{\"metric\":\"SALES_AMOUNT\",\"startDate\":\"2026-06-01\","
                        + "\"endDate\":\"2026-06-30\",\"topN\":8,\"comparePrevious\":true}");
        assertThat(CrmProductSalesIntentRouter.route(
                "2026年4月1日至2026年6月30日出货量最高的产品", LocalDate.of(2026, 7, 14)))
                .contains("{\"metric\":\"SALES_QUANTITY\",\"startDate\":\"2026-04-01\","
                        + "\"endDate\":\"2026-06-30\",\"topN\":5,\"comparePrevious\":true}");
    }

    @Test
    void shouldNotForceRouteWhenExplicitTimeOrRangeCannotBeResolvedReliably() {
        LocalDate today = LocalDate.of(2026, 7, 14);

        assertThat(CrmProductSalesIntentRouter.route("过去几天销量最高的产品", today)).isEmpty();
        assertThat(CrmProductSalesIntentRouter.route("过去三个月销量最高的产品", today)).isEmpty();
        assertThat(CrmProductSalesIntentRouter.route("2026-07-20到2026-07-01销量最高的产品", today)).isEmpty();
        assertThat(CrmProductSalesIntentRouter.route("2026-07-01销量最高的产品", today)).isEmpty();
    }
}
