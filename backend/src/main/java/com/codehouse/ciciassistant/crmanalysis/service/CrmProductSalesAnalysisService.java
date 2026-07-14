package com.codehouse.ciciassistant.crmanalysis.service;

import com.codehouse.ciciassistant.cloudcc.CloudccOpenApiService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CrmProductSalesAnalysisService {

    static final String PRODUCT_FIELDS = "id,name,cpdm,cpxl,unit,productprice,yqy,ownerid";
    static final String ORDER_FIELDS = "id,name,accountid,contractid,opportunityid,podate,status,totalamount,paymentstatus,paidamount,ownerid,description";
    static final String ORDER_ITEM_FIELDS = "id,name,orderid,product2id,quantity,unitprice,totalprice,status,shippingaccount,productcode,unit,description";
    private static final List<String> SOURCE_OBJECTS = List.of("product", "cloudccorder", "cloudccorderitem");
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> INVALID_STATUS_MARKERS = Set.of(
            "草稿", "取消", "作废", "无效", "退回", "关闭", "已删除", "draft", "cancel", "void", "invalid"
    );

    private final CloudccOpenApiService cloudcc;
    private final Clock clock;

    @Autowired
    public CrmProductSalesAnalysisService(CloudccOpenApiService cloudcc) {
        this(cloudcc, Clock.system(DEFAULT_ZONE));
    }

    CrmProductSalesAnalysisService(CloudccOpenApiService cloudcc, Clock clock) {
        this.cloudcc = cloudcc;
        this.clock = clock;
    }

    public SalesRankResult analyze(String orgId, String userId, SalesRankRequest rawRequest) {
        SalesRankRequest request = rawRequest == null
                ? new SalesRankRequest(null, null, null, null, null)
                : rawRequest;
        Metric metric = request.metric() == null ? Metric.SALES_QUANTITY : request.metric();
        LocalDate endDate = request.endDate() == null ? LocalDate.now(clock) : request.endDate();
        LocalDate startDate = request.startDate() == null ? endDate.minusDays(29) : request.startDate();
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("统计开始日期不能晚于结束日期");
        }
        int topN = request.topN() == null ? 5 : Math.max(1, Math.min(20, request.topN()));
        boolean comparePrevious = request.comparePrevious() == null || request.comparePrevious();

        try {
            List<Map<String, Object>> products = cloudcc.queryAllRecords(
                    orgId, userId, "product", PRODUCT_FIELDS, "");
            List<Map<String, Object>> orders = cloudcc.queryAllRecords(
                    orgId, userId, "cloudccorder", ORDER_FIELDS, "");
            List<Map<String, Object>> items = cloudcc.queryAllRecords(
                    orgId, userId, "cloudccorderitem", ORDER_ITEM_FIELDS, "");
            return aggregate(metric, startDate, endDate, topN, comparePrevious, products, orders, items);
        } catch (CloudccOpenApiService.CloudccApiException ex) {
            return failure(mapFailureStatus(ex.code(), ex.getMessage()), metric, startDate, endDate, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return failure(mapFailureStatus("", ex.getMessage()), metric, startDate, endDate, ex.getMessage());
        } catch (RuntimeException ex) {
            return failure(ResultStatus.UPSTREAM_ERROR, metric, startDate, endDate, "CloudCC CRM 查询暂时不可用");
        }
    }

    private SalesRankResult aggregate(Metric metric,
                                      LocalDate startDate,
                                      LocalDate endDate,
                                      int topN,
                                      boolean comparePrevious,
                                      List<Map<String, Object>> products,
                                      List<Map<String, Object>> orders,
                                      List<Map<String, Object>> items) {
        Map<String, ProductInfo> productById = new LinkedHashMap<>();
        for (Map<String, Object> product : products) {
            String id = recordId(product.get("id"));
            if (!id.isBlank()) {
                productById.put(id, new ProductInfo(
                        id,
                        text(product.get("name")),
                        text(product.get("cpdm")),
                        text(product.get("unit"))
                ));
            }
        }

        long periodDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        LocalDate previousEnd = startDate.minusDays(1);
        LocalDate previousStart = startDate.minusDays(periodDays);
        Map<String, OrderInfo> currentOrders = new LinkedHashMap<>();
        Map<String, OrderInfo> previousOrders = new LinkedHashMap<>();
        for (Map<String, Object> order : orders) {
            String id = recordId(order.get("id"));
            LocalDate date = parseDate(order.get("podate"));
            if (id.isBlank() || date == null || !isValidStatus(order.get("status"))) {
                continue;
            }
            OrderInfo info = new OrderInfo(id, recordId(order.get("accountid")), date);
            if (!date.isBefore(startDate) && !date.isAfter(endDate)) {
                currentOrders.put(id, info);
            } else if (comparePrevious && !date.isBefore(previousStart) && !date.isAfter(previousEnd)) {
                previousOrders.put(id, info);
            }
        }

        Map<String, Aggregate> current = new LinkedHashMap<>();
        Map<String, Aggregate> previous = new LinkedHashMap<>();
        int includedItems = 0;
        for (Map<String, Object> item : items) {
            if (!isValidStatus(item.get("status"))) {
                continue;
            }
            String orderId = recordId(item.get("orderid"));
            String productId = recordId(item.get("product2id"));
            if (orderId.isBlank() || productId.isBlank()) {
                continue;
            }
            BigDecimal quantity = decimal(item.get("quantity"));
            BigDecimal amount = decimal(item.get("totalprice"));
            if (amount.compareTo(BigDecimal.ZERO) == 0) {
                amount = quantity.multiply(decimal(item.get("unitprice")));
            }
            OrderInfo currentOrder = currentOrders.get(orderId);
            if (currentOrder != null) {
                current.computeIfAbsent(productId, ignored -> new Aggregate())
                        .add(orderId, currentOrder.accountId(), quantity, amount);
                includedItems++;
                continue;
            }
            OrderInfo previousOrder = previousOrders.get(orderId);
            if (comparePrevious && previousOrder != null) {
                previous.computeIfAbsent(productId, ignored -> new Aggregate())
                        .add(orderId, previousOrder.accountId(), quantity, amount);
            }
        }

        List<String> warnings = new ArrayList<>();
        if (current.isEmpty()) {
            warnings.add("统计范围内没有可计入的有效订单明细");
            return new SalesRankResult(
                    ResultStatus.EMPTY,
                    metric,
                    startDate,
                    endDate,
                    OffsetDateTime.now(clock),
                    SOURCE_OBJECTS,
                    List.of(),
                    new Coverage(orders.size(), 0, orders.size(), items.size(), 0, items.size()),
                    List.copyOf(warnings)
            );
        }

        Comparator<Map.Entry<String, Aggregate>> comparator = Comparator
                .<Map.Entry<String, Aggregate>, BigDecimal>comparing(entry -> metricValue(entry.getValue(), metric))
                .reversed()
                .thenComparing(entry -> entry.getValue().salesAmount, Comparator.reverseOrder())
                .thenComparing(entry -> productById.getOrDefault(entry.getKey(), ProductInfo.unknown(entry.getKey())).code)
                .thenComparing(Map.Entry::getKey);
        List<Map.Entry<String, Aggregate>> ranked = current.entrySet().stream()
                .sorted(comparator)
                .limit(topN)
                .toList();

        List<SalesRankRow> rows = new ArrayList<>();
        for (int index = 0; index < ranked.size(); index++) {
            Map.Entry<String, Aggregate> entry = ranked.get(index);
            String productId = entry.getKey();
            Aggregate value = entry.getValue();
            ProductInfo product = productById.get(productId);
            if (product == null) {
                product = ProductInfo.unknown(productId);
                warnings.add("产品 " + productId + " 未出现在当前用户可见的产品列表中");
            }
            Aggregate previousAggregate = previous.get(productId);
            BigDecimal previousValue = comparePrevious
                    ? metricValue(previousAggregate == null ? new Aggregate() : previousAggregate, metric)
                    : null;
            BigDecimal currentValue = metricValue(value, metric);
            BigDecimal changeRate = comparePrevious ? changeRate(currentValue, previousValue) : null;
            rows.add(new SalesRankRow(
                    index + 1,
                    product.id,
                    product.name,
                    product.code,
                    product.unit,
                    value.salesQuantity,
                    value.salesAmount,
                    value.orderIds.size(),
                    value.customerIds.size(),
                    previousValue,
                    changeRate
            ));
        }

        return new SalesRankResult(
                warnings.isEmpty() ? ResultStatus.SUCCESS : ResultStatus.PARTIAL,
                metric,
                startDate,
                endDate,
                OffsetDateTime.now(clock),
                SOURCE_OBJECTS,
                List.copyOf(rows),
                new Coverage(
                        orders.size(),
                        currentOrders.size(),
                        Math.max(0, orders.size() - currentOrders.size()),
                        items.size(),
                        includedItems,
                        Math.max(0, items.size() - includedItems)
                ),
                warnings.stream().distinct().toList()
        );
    }

    private SalesRankResult failure(ResultStatus status,
                                    Metric metric,
                                    LocalDate startDate,
                                    LocalDate endDate,
                                    String warning) {
        return new SalesRankResult(
                status,
                metric,
                startDate,
                endDate,
                OffsetDateTime.now(clock),
                SOURCE_OBJECTS,
                List.of(),
                new Coverage(0, 0, 0, 0, 0, 0),
                List.of(warning == null || warning.isBlank() ? "CloudCC CRM 查询失败" : warning)
        );
    }

    private ResultStatus mapFailureStatus(String code, String message) {
        String normalized = ((code == null ? "" : code) + " " + (message == null ? "" : message))
                .toLowerCase(Locale.ROOT);
        if (normalized.contains("绑定") || normalized.contains("访问令牌") || normalized.contains("token")) {
            return ResultStatus.CRM_NOT_CONNECTED;
        }
        if (normalized.contains("permission") || normalized.contains("forbidden") || normalized.contains("权限")) {
            return ResultStatus.PERMISSION_DENIED;
        }
        if (normalized.contains("field") || normalized.contains("object") || normalized.contains("字段") || normalized.contains("对象")) {
            return ResultStatus.SCHEMA_UNSUPPORTED;
        }
        return ResultStatus.UPSTREAM_ERROR;
    }

    private static BigDecimal metricValue(Aggregate aggregate, Metric metric) {
        return switch (metric) {
            case SALES_QUANTITY -> aggregate.salesQuantity;
            case SALES_AMOUNT -> aggregate.salesAmount;
            case ORDER_COUNT -> BigDecimal.valueOf(aggregate.orderIds.size());
            case CUSTOMER_COUNT -> BigDecimal.valueOf(aggregate.customerIds.size());
        };
    }

    private static BigDecimal changeRate(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(previous).divide(previous, 4, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private static boolean isValidStatus(Object rawStatus) {
        String status = text(rawStatus).toLowerCase(Locale.ROOT).replace(" ", "");
        if (status.isBlank()) {
            return true;
        }
        return INVALID_STATUS_MARKERS.stream().noneMatch(status::contains);
    }

    private static LocalDate parseDate(Object value) {
        String raw = text(value);
        if (raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.length() >= 10 ? raw.substring(0, 10) : raw);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        String raw = text(value).replace(",", "").trim();
        if (raw.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private static String recordId(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (String key : List.of("id", "Id", "value", "key")) {
                if (map.containsKey(key)) {
                    return recordId(map.get(key));
                }
            }
            return "";
        }
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty() ? "" : recordId(collection.iterator().next());
        }
        return text(value);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public enum Metric {
        SALES_QUANTITY,
        SALES_AMOUNT,
        ORDER_COUNT,
        CUSTOMER_COUNT
    }

    public enum ResultStatus {
        SUCCESS,
        EMPTY,
        CRM_NOT_CONNECTED,
        PERMISSION_DENIED,
        SCHEMA_UNSUPPORTED,
        PARTIAL,
        UPSTREAM_ERROR
    }

    public record SalesRankRequest(
            Metric metric,
            LocalDate startDate,
            LocalDate endDate,
            Integer topN,
            Boolean comparePrevious
    ) {
    }

    public record SalesRankResult(
            ResultStatus status,
            Metric metric,
            LocalDate startDate,
            LocalDate endDate,
            OffsetDateTime dataAsOf,
            List<String> sourceObjects,
            List<SalesRankRow> rows,
            Coverage coverage,
            List<String> warnings
    ) {
    }

    public record SalesRankRow(
            int rank,
            String productId,
            String productName,
            String productCode,
            String unit,
            BigDecimal salesQuantity,
            BigDecimal salesAmount,
            int orderCount,
            int customerCount,
            BigDecimal previousValue,
            BigDecimal changeRate
    ) {
    }

    public record Coverage(
            int scannedOrders,
            int includedOrders,
            int excludedOrders,
            int scannedItems,
            int includedItems,
            int excludedItems
    ) {
    }

    private record ProductInfo(String id, String name, String code, String unit) {
        static ProductInfo unknown(String id) {
            return new ProductInfo(id, "未识别产品", id, "");
        }
    }

    private record OrderInfo(String id, String accountId, LocalDate date) {
    }

    private static final class Aggregate {
        private BigDecimal salesQuantity = BigDecimal.ZERO;
        private BigDecimal salesAmount = BigDecimal.ZERO;
        private final Set<String> orderIds = new LinkedHashSet<>();
        private final Set<String> customerIds = new LinkedHashSet<>();

        void add(String orderId, String customerId, BigDecimal quantity, BigDecimal amount) {
            salesQuantity = salesQuantity.add(quantity);
            salesAmount = salesAmount.add(amount);
            orderIds.add(orderId);
            if (customerId != null && !customerId.isBlank()) {
                customerIds.add(customerId);
            }
        }
    }
}
