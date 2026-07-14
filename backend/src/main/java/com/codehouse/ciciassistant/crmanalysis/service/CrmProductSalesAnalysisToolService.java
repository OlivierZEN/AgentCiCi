package com.codehouse.ciciassistant.crmanalysis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class CrmProductSalesAnalysisToolService {

    public static final String TOOL_NAME = "crm_product_sales_rank";
    private static final Set<String> ALLOWED_ARGUMENTS = Set.of(
            "metric", "startDate", "endDate", "topN", "comparePrevious"
    );

    private final CrmProductSalesAnalysisService analysisService;
    private final ObjectMapper objectMapper;

    public CrmProductSalesAnalysisToolService(CrmProductSalesAnalysisService analysisService,
                                              ObjectMapper objectMapper) {
        this.analysisService = analysisService;
        this.objectMapper = objectMapper;
    }

    public String dispatch(String orgId, String userId, String argumentsJson) {
        try {
            JsonNode root = argumentsJson == null || argumentsJson.isBlank()
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(argumentsJson);
            if (!root.isObject()) {
                throw new IllegalArgumentException("参数必须是 JSON 对象");
            }
            Iterator<String> fieldNames = root.fieldNames();
            while (fieldNames.hasNext()) {
                String field = fieldNames.next();
                if (!ALLOWED_ARGUMENTS.contains(field)) {
                    throw new IllegalArgumentException("不支持的参数: " + field);
                }
            }
            CrmProductSalesAnalysisService.Metric metric = parseMetric(root);
            LocalDate startDate = root.hasNonNull("startDate")
                    ? LocalDate.parse(root.path("startDate").asText())
                    : null;
            LocalDate endDate = root.hasNonNull("endDate")
                    ? LocalDate.parse(root.path("endDate").asText())
                    : null;
            Integer topN = root.hasNonNull("topN") ? root.path("topN").asInt() : null;
            Boolean comparePrevious = root.hasNonNull("comparePrevious")
                    ? root.path("comparePrevious").asBoolean()
                    : null;
            CrmProductSalesAnalysisService.SalesRankResult result = analysisService.analyze(
                    orgId,
                    userId,
                    new CrmProductSalesAnalysisService.SalesRankRequest(
                            metric, startDate, endDate, topN, comparePrevious)
            );
            return objectMapper.writeValueAsString(result);
        } catch (Exception ex) {
            return writeError("INVALID_ARGUMENTS", "crm_product_sales_rank 参数错误: " + ex.getMessage());
        }
    }

    private CrmProductSalesAnalysisService.Metric parseMetric(JsonNode root) {
        if (!root.hasNonNull("metric")) {
            return null;
        }
        try {
            return CrmProductSalesAnalysisService.Metric.valueOf(
                    root.path("metric").asText().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("metric 不在允许的指标枚举中");
        }
    }

    private String writeError(String status, String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("status", status, "message", message));
        } catch (Exception ignored) {
            return "{\"status\":\"INVALID_ARGUMENTS\",\"message\":\"参数错误\"}";
        }
    }

    public static String toolDescription() {
        return "按当前用户权限统计 CloudCC CRM 产品销量、销售额、订单数或客户数排行。"
                + "涉及‘销量最好、销售额最高、热销产品’时直接调用本工具，不要自行查询多个 CRM 对象后计算。";
    }

    public static JsonNode toolSchema(ObjectMapper objectMapper) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        root.put("additionalProperties", false);
        ObjectNode properties = root.putObject("properties");

        ObjectNode metric = properties.putObject("metric");
        metric.put("type", "string");
        metric.put("description", "排行指标；用户说‘销量’时用 SALES_QUANTITY，说‘销售得好/销售额’时用 SALES_AMOUNT。");
        ArrayNode metricEnum = metric.putArray("enum");
        for (CrmProductSalesAnalysisService.Metric value : CrmProductSalesAnalysisService.Metric.values()) {
            metricEnum.add(value.name());
        }

        ObjectNode startDate = properties.putObject("startDate");
        startDate.put("type", "string");
        startDate.put("format", "date");
        startDate.put("description", "统计开始日期 YYYY-MM-DD；未提供时默认最近 30 天。");

        ObjectNode endDate = properties.putObject("endDate");
        endDate.put("type", "string");
        endDate.put("format", "date");
        endDate.put("description", "统计结束日期 YYYY-MM-DD；未提供时默认今天。");

        ObjectNode topN = properties.putObject("topN");
        topN.put("type", "integer");
        topN.put("minimum", 1);
        topN.put("maximum", 20);
        topN.put("default", 5);

        ObjectNode comparePrevious = properties.putObject("comparePrevious");
        comparePrevious.put("type", "boolean");
        comparePrevious.put("default", true);
        comparePrevious.put("description", "是否计算同长度上一周期的变化率。");
        return root;
    }
}
