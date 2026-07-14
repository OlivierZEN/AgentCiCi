package com.codehouse.ciciassistant.crmanalysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CrmProductSalesAnalysisToolServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void exposesNarrowReadOnlySchemaForProductSalesRanking() {
        JsonNode schema = CrmProductSalesAnalysisToolService.toolSchema(objectMapper);

        assertThat(CrmProductSalesAnalysisToolService.TOOL_NAME).isEqualTo("crm_product_sales_rank");
        assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
        assertThat(schema.path("properties").path("metric").path("enum"))
                .extracting(JsonNode::asText)
                .containsExactly("SALES_QUANTITY", "SALES_AMOUNT", "ORDER_COUNT", "CUSTOMER_COUNT");
        assertThat(schema.path("properties").path("topN").path("maximum").asInt()).isEqualTo(20);
        assertThat(CrmProductSalesAnalysisToolService.toolDescription())
                .contains("不要自行查询多个 CRM 对象")
                .contains("确定性", "贡献率", "商机", "合同");
    }

    @Test
    void parsesArgumentsAndPassesCurrentUserIdentityToAnalysisService() throws Exception {
        CrmProductSalesAnalysisService analysis = mock(CrmProductSalesAnalysisService.class);
        when(analysis.analyze(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(new CrmProductSalesAnalysisService.SalesRankResult(
                        CrmProductSalesAnalysisService.ResultStatus.EMPTY,
                        CrmProductSalesAnalysisService.Metric.SALES_AMOUNT,
                        LocalDate.parse("2026-07-01"),
                        LocalDate.parse("2026-07-14"),
                        OffsetDateTime.parse("2026-07-14T12:00:00+08:00"),
                        List.of("product", "cloudccorder", "cloudccorderitem"),
                        List.of(),
                        new CrmProductSalesAnalysisService.Coverage(0, 0, 0, 0, 0, 0),
                        List.of("统计范围内没有可计入的有效订单明细")
                ));
        CrmProductSalesAnalysisToolService tool = new CrmProductSalesAnalysisToolService(analysis, objectMapper);

        String json = tool.dispatch("org-1", "user-9", """
                {
                  "metric": "SALES_AMOUNT",
                  "startDate": "2026-07-01",
                  "endDate": "2026-07-14",
                  "topN": 3,
                  "comparePrevious": false
                }
                """);

        ArgumentCaptor<CrmProductSalesAnalysisService.SalesRankRequest> request =
                ArgumentCaptor.forClass(CrmProductSalesAnalysisService.SalesRankRequest.class);
        verify(analysis).analyze(org.mockito.ArgumentMatchers.eq("org-1"),
                org.mockito.ArgumentMatchers.eq("user-9"), request.capture());
        assertThat(request.getValue().metric()).isEqualTo(CrmProductSalesAnalysisService.Metric.SALES_AMOUNT);
        assertThat(request.getValue().startDate()).hasToString("2026-07-01");
        assertThat(request.getValue().topN()).isEqualTo(3);
        assertThat(request.getValue().comparePrevious()).isFalse();
        assertThat(objectMapper.readTree(json).path("status").asText()).isEqualTo("EMPTY");
    }

    @Test
    void rejectsUnknownArgumentsBeforeCallingCrm() {
        CrmProductSalesAnalysisToolService tool = new CrmProductSalesAnalysisToolService(
                mock(CrmProductSalesAnalysisService.class), objectMapper);

        String json = tool.dispatch("org-1", "user-1", "{\"metric\":\"NOT_A_METRIC\"}");

        assertThat(json).contains("INVALID_ARGUMENTS").contains("metric");
    }
}
