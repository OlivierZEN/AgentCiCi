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
    static final String ORDER_FIELDS = "id,name,accountid,contractid,opportunityid,podate,status,totalamount,paymentstatus,paidamount,currency,ownerid,description";
    static final String ORDER_ITEM_FIELDS = "id,name,orderid,product2id,quantity,unitprice,totalprice,status,shippingaccount,productcode,unit,description";
    static final String ACCOUNT_FIELDS = "id,name,hangye,fenji,currency,ownerid,beizhu";
    static final String OPPORTUNITY_FIELDS = "id,name,khmc,jieduan,jine,yqsr,jsrq,knx,currency,ownerid";
    static final String OPPORTUNITY_PRODUCT_FIELDS = "id,opportunity,product2,quantity,totalprice,unit,currency,ownerid";
    static final String CONTRACT_FIELDS = "id,name,khmc,opportunityid,htje,htksrq,htjsrq,zhuangtai,currency,ownerid";
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
            OptionalData optionalData = loadOptionalData(orgId, userId);
            return aggregate(metric, startDate, endDate, topN, comparePrevious,
                    products, orders, items, optionalData);
        } catch (CloudccOpenApiService.CloudccApiException ex) {
            return failure(mapFailureStatus(ex.code(), ex.getMessage()), metric, startDate, endDate, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return failure(mapFailureStatus("", ex.getMessage()), metric, startDate, endDate, ex.getMessage());
        } catch (RuntimeException ex) {
            return failure(ResultStatus.UPSTREAM_ERROR, metric, startDate, endDate, "CloudCC CRM 查询暂时不可用");
        }
    }

    private OptionalData loadOptionalData(String orgId, String userId) {
        List<String> sources = new ArrayList<>(SOURCE_OBJECTS);
        List<String> warnings = new ArrayList<>();
        List<Map<String, Object>> accounts = List.of();
        List<Map<String, Object>> opportunities = List.of();
        List<Map<String, Object>> opportunityProducts = List.of();
        List<Map<String, Object>> contracts = List.of();
        try {
            accounts = cloudcc.queryAllRecords(orgId, userId, "Account", ACCOUNT_FIELDS, "");
            sources.add("Account");
        } catch (RuntimeException ex) {
            warnings.add("客户结构增强数据不可用，已保留订单销售事实");
        }
        try {
            opportunities = cloudcc.queryAllRecords(orgId, userId, "Opportunity", OPPORTUNITY_FIELDS, "");
            sources.add("Opportunity");
        } catch (RuntimeException ex) {
            warnings.add("商机增强数据不可用，已保留订单销售事实");
        }
        try {
            opportunityProducts = cloudcc.queryAllRecords(
                    orgId, userId, "opportunitypdt", OPPORTUNITY_PRODUCT_FIELDS, "");
            sources.add("opportunitypdt");
        } catch (RuntimeException ex) {
            warnings.add("商机产品增强数据不可用，已保留订单销售事实");
        }
        try {
            contracts = cloudcc.queryAllRecords(orgId, userId, "contract", CONTRACT_FIELDS, "");
            sources.add("contract");
        } catch (RuntimeException ex) {
            warnings.add("合同增强数据不可用，已保留订单销售事实");
        }
        return new OptionalData(accounts, opportunities, opportunityProducts, contracts,
                List.copyOf(sources), List.copyOf(warnings));
    }

    private SalesRankResult aggregate(Metric metric,
                                      LocalDate startDate,
                                      LocalDate endDate,
                                      int topN,
                                      boolean comparePrevious,
                                      List<Map<String, Object>> products,
                                      List<Map<String, Object>> orders,
                                      List<Map<String, Object>> items,
                                      OptionalData optionalData) {
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
            OrderInfo info = new OrderInfo(
                    id,
                    recordId(order.get("accountid")),
                    date,
                    text(order.get("currency")),
                    recordId(order.get("contractid"))
            );
            if (!date.isBefore(startDate) && !date.isAfter(endDate)) {
                currentOrders.put(id, info);
            } else if (comparePrevious && !date.isBefore(previousStart) && !date.isAfter(previousEnd)) {
                previousOrders.put(id, info);
            }
        }

        Map<String, Aggregate> current = new LinkedHashMap<>();
        Map<String, Aggregate> previous = new LinkedHashMap<>();
        Set<String> currentCurrencies = new LinkedHashSet<>();
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
                        .add(orderId, currentOrder.accountId(), currentOrder.contractId(), quantity, amount);
                if (!currentOrder.currency().isBlank()) {
                    currentCurrencies.add(currentOrder.currency().toUpperCase(Locale.ROOT));
                }
                includedItems++;
                continue;
            }
            OrderInfo previousOrder = previousOrders.get(orderId);
            if (comparePrevious && previousOrder != null) {
                previous.computeIfAbsent(productId, ignored -> new Aggregate())
                        .add(orderId, previousOrder.accountId(), previousOrder.contractId(), quantity, amount);
            }
        }

        List<String> warnings = new ArrayList<>(optionalData.warnings());
        if (orders.isEmpty() && !items.isEmpty()) {
            warnings.add("当前账号可见订单明细，但看不到关联的订单主表，权限覆盖不完整");
            return new SalesRankResult(
                    ResultStatus.DATA_ACCESS_INCOMPLETE,
                    metric,
                    startDate,
                    endDate,
                    OffsetDateTime.now(clock),
                    optionalData.sourceObjects(),
                    List.of(),
                    new Coverage(0, 0, 0, items.size(), 0, items.size()),
                    List.copyOf(warnings)
            );
        }
        if (current.isEmpty()) {
            warnings.add("统计范围内没有可计入的有效订单明细");
            return new SalesRankResult(
                    ResultStatus.EMPTY,
                    metric,
                    startDate,
                    endDate,
                    OffsetDateTime.now(clock),
                    optionalData.sourceObjects(),
                    List.of(),
                    new Coverage(orders.size(), 0, orders.size(), items.size(), 0, items.size()),
                    List.copyOf(warnings)
            );
        }

        Set<String> invisibleProductIds = current.keySet().stream()
                .filter(productId -> !productById.containsKey(productId))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!invisibleProductIds.isEmpty() && invisibleProductIds.size() == current.size()) {
            warnings.add("有效订单明细关联的产品主数据全部不可见，当前账号权限覆盖不完整");
            return new SalesRankResult(
                    ResultStatus.DATA_ACCESS_INCOMPLETE,
                    metric,
                    startDate,
                    endDate,
                    OffsetDateTime.now(clock),
                    optionalData.sourceObjects(),
                    List.of(),
                    new Coverage(
                            orders.size(), currentOrders.size(), Math.max(0, orders.size() - currentOrders.size()),
                            items.size(), includedItems, Math.max(0, items.size() - includedItems)),
                    warnings.stream().distinct().toList()
            );
        }
        if (!invisibleProductIds.isEmpty()) {
            warnings.add("部分有效明细关联的产品主数据不可见，已从排行中排除不可验证的产品");
            invisibleProductIds.forEach(current::remove);
            invisibleProductIds.forEach(previous::remove);
        }

        boolean amountComparable = currentCurrencies.size() <= 1;
        if (!amountComparable && metric == Metric.SALES_AMOUNT) {
            warnings.add("统计范围内存在多币种订单，当前无可靠汇率折算，不合并输出金额或经济价值排行");
            return new SalesRankResult(
                    ResultStatus.DATA_QUALITY_BLOCKED,
                    metric,
                    startDate,
                    endDate,
                    OffsetDateTime.now(clock),
                    optionalData.sourceObjects(),
                    List.of(),
                    new Coverage(
                            orders.size(),
                            currentOrders.size(),
                            Math.max(0, orders.size() - currentOrders.size()),
                            items.size(),
                            includedItems,
                            Math.max(0, items.size() - includedItems)),
                    List.copyOf(warnings)
            );
        }
        if (!amountComparable) {
            warnings.add("统计范围内存在多币种订单；保留净销量排行，不合并订单销售额、实现均价或客户金额集中度");
        }

        BigDecimal totalSalesQuantity = current.values().stream()
                .map(value -> value.salesQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSalesAmount = amountComparable
                ? current.values().stream().map(value -> value.salesAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                : null;
        Set<String> allOrderIds = new LinkedHashSet<>();
        Set<String> allCustomerIds = new LinkedHashSet<>();
        current.values().forEach(value -> {
            allOrderIds.addAll(value.orderIds);
            allCustomerIds.addAll(value.customerIds);
        });
        List<Map.Entry<String, Aggregate>> quantityRanking = rankAll(
                current, Metric.SALES_QUANTITY, productById, amountComparable);
        List<Map.Entry<String, Aggregate>> amountRanking = amountComparable
                ? rankAll(current, Metric.SALES_AMOUNT, productById, true)
                : List.of();
        Map<String, Integer> quantityRanks = rankPositions(quantityRanking);
        Map<String, Integer> amountRanks = rankPositions(amountRanking);
        String currency = currentCurrencies.stream().findFirst().orElse("");
        SalesSummary summary = new SalesSummary(
                totalSalesQuantity,
                totalSalesAmount,
                allOrderIds.size(),
                allCustomerIds.size(),
                currency,
                amountComparable,
                leader(quantityRanking, productById, Metric.SALES_QUANTITY),
                leader(amountRanking, productById, Metric.SALES_AMOUNT)
        );
        Map<String, OpportunityInfo> opportunityById = mapOpportunities(optionalData.opportunities());
        Map<String, PipelineAggregate> pipelineByProduct = aggregatePipeline(
                optionalData.opportunityProducts(), opportunityById);
        Map<String, ContractInfo> contractById = mapContracts(optionalData.contracts());

        List<Map.Entry<String, Aggregate>> ranked = rankAll(current, metric, productById, amountComparable).stream()
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
            PipelineAggregate pipeline = pipelineByProduct.getOrDefault(productId, new PipelineAggregate());
            ProductContractSignal contractSignal = contractSignal(
                    value, contractById, pipeline, endDate);
            rows.add(new SalesRankRow(
                    index + 1,
                    product.id,
                    product.name,
                    product.code,
                    product.unit,
                    value.salesQuantity,
                    amountComparable ? value.salesAmount : null,
                    value.orderIds.size(),
                    value.customerIds.size(),
                    previousValue,
                    changeRate,
                    ratio(value.salesQuantity, totalSalesQuantity),
                    amountComparable ? ratio(value.salesAmount, totalSalesAmount) : null,
                    amountComparable ? ratio(value.positiveSalesAmount, value.positiveSalesQuantity) : null,
                    amountComparable ? customerConcentration(value, 1) : null,
                    amountComparable ? customerConcentration(value, 3) : null,
                    quantityRanks.getOrDefault(productId, 0),
                    amountRanks.getOrDefault(productId, 0),
                    pipeline.toSignal(),
                    contractSignal
            ));
        }
        List<BusinessInsight> insights = buildInsights(
                rows, current, previous, pipelineByProduct, productById);

        return new SalesRankResult(
                warnings.isEmpty() ? ResultStatus.SUCCESS : ResultStatus.PARTIAL,
                metric,
                startDate,
                endDate,
                OffsetDateTime.now(clock),
                optionalData.sourceObjects(),
                List.copyOf(rows),
                new Coverage(
                        orders.size(),
                        currentOrders.size(),
                        Math.max(0, orders.size() - currentOrders.size()),
                        items.size(),
                        includedItems,
                        Math.max(0, items.size() - includedItems)
                ),
                warnings.stream().distinct().toList(),
                summary,
                insights
        );
    }

    private static Map<String, OpportunityInfo> mapOpportunities(List<Map<String, Object>> opportunities) {
        Map<String, OpportunityInfo> result = new LinkedHashMap<>();
        for (Map<String, Object> opportunity : opportunities) {
            String id = recordId(opportunity.get("id"));
            String stage = text(opportunity.get("jieduan"));
            if (id.isBlank() || !isOpenOpportunityStage(stage)) {
                continue;
            }
            result.put(id, new OpportunityInfo(
                    id,
                    recordId(opportunity.get("khmc")),
                    parseDate(opportunity.get("jsrq")),
                    text(opportunity.get("currency"))
            ));
        }
        return result;
    }

    private static Map<String, PipelineAggregate> aggregatePipeline(
            List<Map<String, Object>> opportunityProducts,
            Map<String, OpportunityInfo> opportunityById) {
        Map<String, PipelineAggregate> result = new LinkedHashMap<>();
        for (Map<String, Object> opportunityProduct : opportunityProducts) {
            String opportunityId = recordId(opportunityProduct.get("opportunity"));
            String productId = recordId(opportunityProduct.get("product2"));
            OpportunityInfo opportunity = opportunityById.get(opportunityId);
            if (opportunity == null || productId.isBlank()) {
                continue;
            }
            result.computeIfAbsent(productId, ignored -> new PipelineAggregate()).add(
                    opportunity.id(),
                    opportunity.accountId(),
                    decimal(opportunityProduct.get("quantity")),
                    decimal(opportunityProduct.get("totalprice")),
                    opportunity.expectedCloseDate()
            );
        }
        return result;
    }

    private static Map<String, ContractInfo> mapContracts(List<Map<String, Object>> contracts) {
        Map<String, ContractInfo> result = new LinkedHashMap<>();
        for (Map<String, Object> contract : contracts) {
            String id = recordId(contract.get("id"));
            if (id.isBlank()) {
                continue;
            }
            result.put(id, new ContractInfo(
                    id,
                    recordId(contract.get("khmc")),
                    text(contract.get("zhuangtai")),
                    parseDate(contract.get("htjsrq"))
            ));
        }
        return result;
    }

    private static ProductContractSignal contractSignal(Aggregate sales,
                                                        Map<String, ContractInfo> contractById,
                                                        PipelineAggregate pipeline,
                                                        LocalDate periodEnd) {
        int active = 0;
        int expiring = 0;
        int expiringWithoutRenewal = 0;
        LocalDate expiryThreshold = periodEnd.plusDays(90);
        for (String contractId : sales.contractIds) {
            ContractInfo contract = contractById.get(contractId);
            if (contract == null || !isActiveContract(contract, periodEnd)) {
                continue;
            }
            active++;
            if (contract.endDate() != null && !contract.endDate().isAfter(expiryThreshold)) {
                expiring++;
                if (contract.accountId().isBlank() || !pipeline.accountIds.contains(contract.accountId())) {
                    expiringWithoutRenewal++;
                }
            }
        }
        return new ProductContractSignal(active, expiring, expiringWithoutRenewal);
    }

    private static List<BusinessInsight> buildInsights(
            List<SalesRankRow> rows,
            Map<String, Aggregate> current,
            Map<String, Aggregate> previous,
            Map<String, PipelineAggregate> pipelineByProduct,
            Map<String, ProductInfo> productById) {
        List<BusinessInsight> insights = new ArrayList<>();
        for (SalesRankRow row : rows) {
            Aggregate currentValue = current.get(row.productId());
            Aggregate previousValue = previous.get(row.productId());
            if (currentValue != null && previousValue != null) {
                BigDecimal quantityGrowth = changeRate(
                        currentValue.salesQuantity, previousValue.salesQuantity);
                BigDecimal amountGrowth = changeRate(
                        currentValue.salesAmount, previousValue.salesAmount);
                BigDecimal currentAveragePrice = ratio(
                        currentValue.positiveSalesAmount, currentValue.positiveSalesQuantity);
                BigDecimal previousAveragePrice = ratio(
                        previousValue.positiveSalesAmount, previousValue.positiveSalesQuantity);
                BigDecimal averagePriceDecline = currentAveragePrice == null || previousAveragePrice == null
                        ? null : changeRate(currentAveragePrice, previousAveragePrice).negate();
                if (quantityGrowth != null && quantityGrowth.compareTo(BigDecimal.ZERO) > 0
                        && amountGrowth != null && amountGrowth.compareTo(BigDecimal.ZERO) > 0
                        && row.quantityRank() == 1 && row.amountRank() == 1
                        && currentValue.customerIds.size() > previousValue.customerIds.size()) {
                    insights.add(new BusinessInsight(
                            "CORE_GROWTH",
                            row.productCode(),
                            row.productName() + " 是当前核心增长产品",
                            "销量增长 " + percent(quantityGrowth) + "，订单销售额增长 "
                                    + percent(amountGrowth) + "，客户数从 "
                                    + previousValue.customerIds.size() + " 增至 " + currentValue.customerIds.size(),
                            "联动交付、库存和销售团队保障供给，并对新增客户提前布置续约与增购计划"
                    ));
                }
                if (quantityGrowth != null && quantityGrowth.compareTo(BigDecimal.ZERO) > 0
                        && amountGrowth != null && amountGrowth.compareTo(quantityGrowth) < 0
                        && averagePriceDecline != null && averagePriceDecline.compareTo(BigDecimal.ZERO) > 0) {
                    insights.add(new BusinessInsight(
                            "DISCOUNT_DRIVEN",
                            row.productCode(),
                            row.productName() + " 可能存在折扣驱动的增长",
                            "销量增长 " + percent(quantityGrowth) + "，订单销售额增长 "
                                    + percent(amountGrowth) + "，实现均价下降 " + percent(averagePriceDecline),
                            "按客户和订单复核价格、折扣与产品组合，确认增长是否以牺牲价格为代价"
                    ));
                }
            }
            if (row.top1CustomerConcentration() != null
                    && row.top1CustomerConcentration().compareTo(new BigDecimal("0.6")) >= 0) {
                insights.add(new BusinessInsight(
                        "CUSTOMER_CONCENTRATION",
                        row.productCode(),
                        row.productName() + " 的销售对单一客户依赖较高",
                        "Top1 客户订单销售额占比 " + percent(row.top1CustomerConcentration()),
                        "复盘该客户的续约和交付风险，同时在同类客户中复制成功场景"
                ));
            }
            if (row.pipeline().openOpportunityCount() == 0) {
                insights.add(new BusinessInsight(
                        "PIPELINE_GAP",
                        row.productCode(),
                        row.productName() + " 存在后续订单断层信号",
                        "当前期已实现销量 " + number(row.salesQuantity()) + " " + safeUnit(row.unit())
                                + "，但未发现开放商机产品",
                        "优先从已购客户中识别增购或替换需求，建立明确的商机产品与预计签约日期"
                ));
            }
            if (row.contracts().expiringWithoutRenewalCount() > 0) {
                insights.add(new BusinessInsight(
                        "RENEWAL_RISK",
                        row.productCode(),
                        row.productName() + " 存在续约缺口",
                        "90 天内到期且未关联续约商机的合同 "
                                + row.contracts().expiringWithoutRenewalCount() + " 份",
                        "按合同到期日期排序建立续约清单，并将客户、产品和预计签约日期关联到商机"
                ));
            }
            if (row.amountRank() > 0 && row.quantityRank() > 0
                    && row.amountRank() < row.quantityRank()) {
                insights.add(new BusinessInsight(
                        "HIGH_VALUE_PRODUCT",
                        row.productCode(),
                        row.productName() + " 呈现高价值型特征",
                        "销售额排名第 " + row.amountRank() + "，高于销量排名第 " + row.quantityRank(),
                        "复盘高价值客户的行业、场景和组合方案，形成可复制的销售剧本"
                ));
            }
        }
        for (Map.Entry<String, PipelineAggregate> entry : pipelineByProduct.entrySet()) {
            if (current.containsKey(entry.getKey()) || entry.getValue().opportunityIds.isEmpty()) {
                continue;
            }
            ProductInfo product = productById.get(entry.getKey());
            if (product == null) {
                continue;
            }
            PipelineAggregate pipeline = entry.getValue();
            insights.add(new BusinessInsight(
                    "POTENTIAL_GROWTH",
                    product.code(),
                    product.name() + " 是潜在增长产品",
                    "当前期无已实现销售，但已有开放商机 "
                            + pipeline.opportunityIds.size() + " 个，管道数量 "
                            + number(pipeline.quantity) + " " + safeUnit(product.unit()),
                    "按最近预计签约日期跟进该产品的商机，提前验证交付能力和关键成交条件"
            ));
        }
        return List.copyOf(insights);
    }

    private static boolean isOpenOpportunityStage(String rawStage) {
        String stage = text(rawStage).toLowerCase(Locale.ROOT).replace(" ", "");
        if (stage.isBlank()) {
            return false;
        }
        return List.of("赢单", "成交", "签约", "丢单", "失败", "取消", "关闭", "closed", "won", "lost")
                .stream().noneMatch(stage::contains);
    }

    private static boolean isActiveContract(ContractInfo contract, LocalDate periodEnd) {
        String status = contract.status().toLowerCase(Locale.ROOT).replace(" ", "");
        boolean activeStatus = List.of("生效", "执行", "履约", "已启用", "审批通过", "active")
                .stream().anyMatch(status::contains);
        return activeStatus && (contract.endDate() == null || !contract.endDate().isBefore(periodEnd));
    }

    private static String percent(BigDecimal value) {
        return value == null ? "-" : value.multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%";
    }

    private static String number(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private static String safeUnit(String unit) {
        return unit == null || unit.isBlank() ? "单位" : unit;
    }

    private static List<Map.Entry<String, Aggregate>> rankAll(Map<String, Aggregate> values,
                                                               Metric metric,
                                                               Map<String, ProductInfo> productById,
                                                               boolean amountComparable) {
        Comparator<Map.Entry<String, Aggregate>> comparator = Comparator
                .<Map.Entry<String, Aggregate>, BigDecimal>comparing(entry -> metricValue(entry.getValue(), metric))
                .reversed();
        if (amountComparable && metric != Metric.SALES_AMOUNT) {
            comparator = comparator.thenComparing(
                    entry -> entry.getValue().salesAmount, Comparator.reverseOrder());
        }
        comparator = comparator.thenComparing(entry -> productById.getOrDefault(entry.getKey(),
                        ProductInfo.unknown(entry.getKey())).code)
                .thenComparing(Map.Entry::getKey);
        return values.entrySet().stream().sorted(comparator).toList();
    }

    private static Map<String, Integer> rankPositions(List<Map.Entry<String, Aggregate>> ranking) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int index = 0; index < ranking.size(); index++) {
            result.put(ranking.get(index).getKey(), index + 1);
        }
        return result;
    }

    private static SalesLeader leader(List<Map.Entry<String, Aggregate>> ranking,
                                      Map<String, ProductInfo> productById,
                                      Metric metric) {
        if (ranking.isEmpty()) {
            return null;
        }
        Map.Entry<String, Aggregate> first = ranking.getFirst();
        ProductInfo product = productById.getOrDefault(first.getKey(), ProductInfo.unknown(first.getKey()));
        return new SalesLeader(product.name, product.code, product.unit, metricValue(first.getValue(), metric));
    }

    private static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return numerator.divide(denominator, 4, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private static BigDecimal customerConcentration(Aggregate aggregate, int customerLimit) {
        if (aggregate.salesAmount.compareTo(BigDecimal.ZERO) <= 0 || aggregate.customerAmounts.isEmpty()) {
            return null;
        }
        BigDecimal topAmount = aggregate.customerAmounts.values().stream()
                .filter(value -> value.compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.reverseOrder())
                .limit(customerLimit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal concentration = ratio(topAmount, aggregate.salesAmount);
        if (concentration == null) {
            return null;
        }
        return concentration.max(BigDecimal.ZERO).min(BigDecimal.ONE).stripTrailingZeros();
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
        DATA_ACCESS_INCOMPLETE,
        DATA_QUALITY_BLOCKED,
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
            List<String> warnings,
            SalesSummary summary,
            List<BusinessInsight> insights
    ) {
        public SalesRankResult(ResultStatus status,
                               Metric metric,
                               LocalDate startDate,
                               LocalDate endDate,
                               OffsetDateTime dataAsOf,
                               List<String> sourceObjects,
                               List<SalesRankRow> rows,
                               Coverage coverage,
                               List<String> warnings) {
            this(status, metric, startDate, endDate, dataAsOf, sourceObjects, rows,
                    coverage, warnings, SalesSummary.empty(), List.of());
        }

        public SalesRankResult(ResultStatus status,
                               Metric metric,
                               LocalDate startDate,
                               LocalDate endDate,
                               OffsetDateTime dataAsOf,
                               List<String> sourceObjects,
                               List<SalesRankRow> rows,
                               Coverage coverage,
                               List<String> warnings,
                               SalesSummary summary) {
            this(status, metric, startDate, endDate, dataAsOf, sourceObjects, rows,
                    coverage, warnings, summary, List.of());
        }
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
            BigDecimal changeRate,
            BigDecimal quantityContributionRate,
            BigDecimal amountContributionRate,
            BigDecimal realizedAveragePrice,
            BigDecimal top1CustomerConcentration,
            BigDecimal top3CustomerConcentration,
            int quantityRank,
            int amountRank,
            ProductPipelineSignal pipeline,
            ProductContractSignal contracts
    ) {
    }

    public record SalesSummary(
            BigDecimal totalSalesQuantity,
            BigDecimal totalSalesAmount,
            int orderCount,
            int customerCount,
            String currency,
            boolean amountComparable,
            SalesLeader quantityLeader,
            SalesLeader amountLeader
    ) {
        static SalesSummary empty() {
            return new SalesSummary(BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, "", true, null, null);
        }
    }

    public record SalesLeader(
            String productName,
            String productCode,
            String unit,
            BigDecimal value
    ) {
    }

    public record ProductPipelineSignal(
            int openOpportunityCount,
            BigDecimal quantity,
            BigDecimal amount,
            LocalDate nearestExpectedCloseDate
    ) {
        static ProductPipelineSignal empty() {
            return new ProductPipelineSignal(0, BigDecimal.ZERO, BigDecimal.ZERO, null);
        }
    }

    public record ProductContractSignal(
            int activeContractCount,
            int expiringWithin90DaysCount,
            int expiringWithoutRenewalCount
    ) {
        static ProductContractSignal empty() {
            return new ProductContractSignal(0, 0, 0);
        }
    }

    public record BusinessInsight(
            String code,
            String productCode,
            String conclusion,
            String evidence,
            String action
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

    private record OrderInfo(String id,
                             String accountId,
                             LocalDate date,
                             String currency,
                             String contractId) {
    }

    private record OptionalData(
            List<Map<String, Object>> accounts,
            List<Map<String, Object>> opportunities,
            List<Map<String, Object>> opportunityProducts,
            List<Map<String, Object>> contracts,
            List<String> sourceObjects,
            List<String> warnings
    ) {
    }

    private record OpportunityInfo(
            String id,
            String accountId,
            LocalDate expectedCloseDate,
            String currency
    ) {
    }

    private record ContractInfo(
            String id,
            String accountId,
            String status,
            LocalDate endDate
    ) {
    }

    private static final class Aggregate {
        private BigDecimal salesQuantity = BigDecimal.ZERO;
        private BigDecimal salesAmount = BigDecimal.ZERO;
        private BigDecimal positiveSalesQuantity = BigDecimal.ZERO;
        private BigDecimal positiveSalesAmount = BigDecimal.ZERO;
        private final Set<String> orderIds = new LinkedHashSet<>();
        private final Set<String> customerIds = new LinkedHashSet<>();
        private final Set<String> contractIds = new LinkedHashSet<>();
        private final Map<String, BigDecimal> customerAmounts = new LinkedHashMap<>();

        void add(String orderId,
                 String customerId,
                 String contractId,
                 BigDecimal quantity,
                 BigDecimal amount) {
            salesQuantity = salesQuantity.add(quantity);
            salesAmount = salesAmount.add(amount);
            if (quantity.compareTo(BigDecimal.ZERO) > 0 && amount.compareTo(BigDecimal.ZERO) > 0) {
                positiveSalesQuantity = positiveSalesQuantity.add(quantity);
                positiveSalesAmount = positiveSalesAmount.add(amount);
            }
            orderIds.add(orderId);
            if (contractId != null && !contractId.isBlank()) {
                contractIds.add(contractId);
            }
            if (customerId != null && !customerId.isBlank()) {
                customerIds.add(customerId);
                customerAmounts.merge(customerId, amount, BigDecimal::add);
            }
        }
    }

    private static final class PipelineAggregate {
        private final Set<String> opportunityIds = new LinkedHashSet<>();
        private final Set<String> accountIds = new LinkedHashSet<>();
        private BigDecimal quantity = BigDecimal.ZERO;
        private BigDecimal amount = BigDecimal.ZERO;
        private LocalDate nearestExpectedCloseDate;

        void add(String opportunityId,
                 String accountId,
                 BigDecimal itemQuantity,
                 BigDecimal itemAmount,
                 LocalDate expectedCloseDate) {
            opportunityIds.add(opportunityId);
            if (accountId != null && !accountId.isBlank()) {
                accountIds.add(accountId);
            }
            quantity = quantity.add(itemQuantity);
            amount = amount.add(itemAmount);
            if (expectedCloseDate != null
                    && (nearestExpectedCloseDate == null || expectedCloseDate.isBefore(nearestExpectedCloseDate))) {
                nearestExpectedCloseDate = expectedCloseDate;
            }
        }

        ProductPipelineSignal toSignal() {
            return new ProductPipelineSignal(
                    opportunityIds.size(), quantity, amount, nearestExpectedCloseDate);
        }
    }
}
