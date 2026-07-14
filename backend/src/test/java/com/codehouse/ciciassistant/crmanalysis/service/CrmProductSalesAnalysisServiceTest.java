package com.codehouse.ciciassistant.crmanalysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.cloudcc.CloudccOpenApiService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        assertThat(result.sourceObjects()).containsExactly("product", "cloudccorder", "cloudccorderitem");
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
        return map(
                "id", id,
                "name", "订单-" + id,
                "accountid", Map.of("id", accountId, "name", "客户-" + accountId),
                "podate", date,
                "status", status,
                "totalamount", BigDecimal.ZERO
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
