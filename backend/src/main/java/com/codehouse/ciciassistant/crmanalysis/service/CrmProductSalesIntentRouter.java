package com.codehouse.ciciassistant.crmanalysis.service;

import java.util.Locale;
import java.util.Optional;

/** Deterministic intent gate for the platform-standard CRM product sales analysis path. */
public final class CrmProductSalesIntentRouter {

    private CrmProductSalesIntentRouter() {
    }

    public static Optional<String> route(String question) {
        String normalized = question == null
                ? ""
                : question.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        boolean productScope = normalized.contains("产品") || normalized.contains("商品");
        boolean rankingIntent = normalized.contains("销量")
                || normalized.contains("销售额")
                || normalized.contains("收入最高")
                || normalized.contains("卖得")
                || normalized.contains("热销")
                || normalized.contains("销售得")
                || normalized.contains("销售最好");
        if (!productScope || !rankingIntent) {
            return Optional.empty();
        }
        String metric = normalized.contains("销量") || normalized.contains("卖得")
                ? "SALES_QUANTITY"
                : "SALES_AMOUNT";
        return Optional.of("{\"metric\":\"" + metric
                + "\",\"topN\":5,\"comparePrevious\":true}");
    }
}
