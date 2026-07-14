package com.codehouse.ciciassistant.crmanalysis.service;

import static org.assertj.core.api.Assertions.assertThat;

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
}
