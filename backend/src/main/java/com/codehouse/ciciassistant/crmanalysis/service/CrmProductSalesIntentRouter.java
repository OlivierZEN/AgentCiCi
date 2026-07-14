package com.codehouse.ciciassistant.crmanalysis.service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic intent gate for the platform-standard CRM product sales analysis path. */
public final class CrmProductSalesIntentRouter {

    private static final int DEFAULT_TOP_N = 5;
    private static final int MAX_TOP_N = 20;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String CHINESE_NUMBER_CHARACTERS = "零〇一二两三四五六七八九十百千万亿";
    private static final String NUMBER_TOKEN = "(?:[0-9]+|[" + CHINESE_NUMBER_CHARACTERS + "]+)";
    private static final Pattern EXPLICIT_TOP_N_PATTERN = Pattern.compile(
            "(top|(?:排名)?前)(" + NUMBER_TOKEN + ")(?![0-9" + CHINESE_NUMBER_CHARACTERS + "])"
                    + "(?![.．][0-9])");
    private static final Pattern EXPLICIT_TOP_HINT_PATTERN = Pattern.compile("(?<![a-z])top");
    private static final Pattern RELATIVE_DAYS_PATTERN = Pattern.compile(
            "(?:最近|过去|近|前)(" + NUMBER_TOKEN + ")(?:天|日)");
    private static final Pattern ISO_DATE_RANGE_PATTERN = Pattern.compile(
            "(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})(?:至|到|~|～|—|－)"
                    + "(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})");
    private static final Pattern CHINESE_DATE_RANGE_PATTERN = Pattern.compile(
            "(\\d{4})年(\\d{1,2})月(\\d{1,2})日?(?:至|到|~|～|—|－)"
                    + "(\\d{4})年(\\d{1,2})月(\\d{1,2})日?");
    private static final Pattern DATE_TOKEN_PATTERN = Pattern.compile(
            "\\d{4}(?:[-/.]\\d{1,2}[-/.]\\d{1,2}|年\\d{1,2}月\\d{1,2}日?)");
    private static final Pattern EXPLICIT_TIME_HINT_PATTERN = Pattern.compile(
            "(?:最近|过去|近|前).{0,8}(?:天|日|周|星期|月|季度|季|年)"
                    + "|(?:本|上|下|这个|当)(?:周|月|季度|季|年)"
                    + "|(?:今年|去年|明年|\\d{4}年)");
    private static final Pattern NON_DESCENDING_DIRECTION_PATTERN = Pattern.compile(
            "最少|最低|最小|最差|倒数|升序|从少到多|由少到多|从低到高|由低到高");

    private CrmProductSalesIntentRouter() {
    }

    public static Optional<String> route(String question) {
        return route(question, LocalDate.now(BUSINESS_ZONE));
    }

    static Optional<String> route(String question, LocalDate today) {
        String normalized = question == null
                ? ""
                : question.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        boolean productScope = normalized.contains("产品") || normalized.contains("商品");
        boolean rankingIntent = normalized.contains("销量")
                || normalized.contains("出货量")
                || normalized.contains("销售数量")
                || normalized.contains("销售额")
                || normalized.contains("收入最高")
                || normalized.contains("订单数")
                || normalized.contains("订单最多")
                || normalized.contains("客户数")
                || normalized.contains("客户最多")
                || normalized.contains("客户覆盖")
                || normalized.contains("购买客户")
                || normalized.contains("买的客户")
                || normalized.contains("卖得")
                || normalized.contains("热销")
                || normalized.contains("销售得")
                || normalized.contains("销售最好");
        if (!productScope || !rankingIntent) {
            return Optional.empty();
        }
        Optional<String> metric = resolveMetric(normalized);
        if (metric.isEmpty()) {
            return Optional.empty();
        }
        Optional<Integer> topN = resolveTopN(normalized);
        if (topN.isEmpty()) {
            return Optional.empty();
        }
        TimeResolution time = resolveTimeRange(normalized, today);
        if (!time.valid()) {
            return Optional.empty();
        }
        StringBuilder arguments = new StringBuilder("{\"metric\":\"")
                .append(metric.get()).append("\"");
        if (time.explicit()) {
            arguments.append(",\"startDate\":\"").append(time.startDate()).append("\"")
                    .append(",\"endDate\":\"").append(time.endDate()).append("\"");
        }
        arguments.append(",\"topN\":").append(topN.get())
                .append(",\"comparePrevious\":true}");
        return Optional.of(arguments.toString());
    }

    private static Optional<String> resolveMetric(String normalized) {
        if (NON_DESCENDING_DIRECTION_PATTERN.matcher(normalized).find()) {
            return Optional.empty();
        }
        Set<String> metrics = new LinkedHashSet<>();
        if (normalized.contains("订单数") || normalized.contains("订单最多")) {
            metrics.add("ORDER_COUNT");
        }
        if (normalized.contains("客户数")
                || normalized.contains("客户最多")
                || normalized.contains("客户覆盖")
                || normalized.contains("购买客户")
                || normalized.contains("买的客户")) {
            metrics.add("CUSTOMER_COUNT");
        }
        if (normalized.contains("销量")
                || normalized.contains("卖得")
                || normalized.contains("出货量")
                || normalized.contains("销售数量")) {
            metrics.add("SALES_QUANTITY");
        }
        if (normalized.contains("销售额") || normalized.contains("收入最高")) {
            metrics.add("SALES_AMOUNT");
        }
        if (metrics.size() > 1) {
            return Optional.empty();
        }
        if (metrics.size() == 1) {
            return Optional.of(metrics.iterator().next());
        }
        return Optional.of("SALES_AMOUNT");
    }

    private static TimeResolution resolveTimeRange(String normalized, LocalDate today) {
        if (today == null) {
            return TimeResolution.invalid();
        }
        List<DateRange> candidates = new ArrayList<>();
        Matcher isoRange = ISO_DATE_RANGE_PATTERN.matcher(normalized);
        while (isoRange.find()) {
            TimeResolution parsed = parseDateRange(isoRange);
            if (!parsed.valid()) {
                return TimeResolution.invalid();
            }
            candidates.add(new DateRange(parsed.startDate(), parsed.endDate()));
        }
        Matcher chineseRange = CHINESE_DATE_RANGE_PATTERN.matcher(normalized);
        while (chineseRange.find()) {
            TimeResolution parsed = parseDateRange(chineseRange);
            if (!parsed.valid()) {
                return TimeResolution.invalid();
            }
            candidates.add(new DateRange(parsed.startDate(), parsed.endDate()));
        }

        Matcher relativeDays = RELATIVE_DAYS_PATTERN.matcher(normalized);
        while (relativeDays.find()) {
            int days = parseChineseOrArabicNumber(relativeDays.group(1));
            if (days < 1) {
                return TimeResolution.invalid();
            }
            try {
                candidates.add(new DateRange(today.minusDays(days - 1L), today));
            } catch (DateTimeException ex) {
                return TimeResolution.invalid();
            }
        }

        boolean currentMonth = normalized.contains("本月");
        boolean previousMonth = normalized.contains("上月") || normalized.contains("上个月");
        boolean currentQuarter = normalized.contains("本季度");
        if (currentMonth) {
            candidates.add(new DateRange(today.withDayOfMonth(1), today));
        }
        if (previousMonth) {
            LocalDate month = today.minusMonths(1);
            candidates.add(new DateRange(
                    month.withDayOfMonth(1), month.withDayOfMonth(month.lengthOfMonth())));
        }
        if (currentQuarter) {
            int quarterStartMonth = ((today.getMonthValue() - 1) / 3) * 3 + 1;
            candidates.add(new DateRange(LocalDate.of(today.getYear(), quarterStartMonth, 1), today));
        }

        String unresolved = ISO_DATE_RANGE_PATTERN.matcher(normalized).replaceAll("");
        unresolved = CHINESE_DATE_RANGE_PATTERN.matcher(unresolved).replaceAll("");
        unresolved = RELATIVE_DAYS_PATTERN.matcher(unresolved).replaceAll("");
        unresolved = unresolved.replace("本季度", "").replace("本月", "")
                .replace("上个月", "").replace("上月", "");
        if (DATE_TOKEN_PATTERN.matcher(unresolved).find()
                || EXPLICIT_TIME_HINT_PATTERN.matcher(unresolved).find()
                || unresolved.contains("几天")
                || unresolved.contains("几个月")
                || unresolved.contains("过去")) {
            return TimeResolution.invalid();
        }
        if (candidates.isEmpty()) {
            return TimeResolution.defaults();
        }
        DateRange resolved = candidates.getFirst();
        if (candidates.stream().anyMatch(candidate -> !candidate.equals(resolved))) {
            return TimeResolution.invalid();
        }
        return TimeResolution.explicit(resolved.startDate(), resolved.endDate());
    }

    private static TimeResolution parseDateRange(Matcher matcher) {
        try {
            LocalDate start = LocalDate.of(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)));
            LocalDate end = LocalDate.of(
                    Integer.parseInt(matcher.group(4)),
                    Integer.parseInt(matcher.group(5)),
                    Integer.parseInt(matcher.group(6)));
            return start.isAfter(end) ? TimeResolution.invalid() : TimeResolution.explicit(start, end);
        } catch (DateTimeException | NumberFormatException ex) {
            return TimeResolution.invalid();
        }
    }

    private static Optional<Integer> resolveTopN(String normalized) {
        String unresolvedTop = EXPLICIT_TOP_N_PATTERN.matcher(normalized).replaceAll("");
        if (EXPLICIT_TOP_HINT_PATTERN.matcher(unresolvedTop).find()) {
            return Optional.empty();
        }
        Matcher matcher = EXPLICIT_TOP_N_PATTERN.matcher(normalized);
        Integer resolved = null;
        while (matcher.find()) {
            if (matcher.group(1).endsWith("前") && hasTemporalSuffix(normalized, matcher.end())) {
                continue;
            }
            int candidate = parseChineseOrArabicNumber(matcher.group(2));
            if (candidate < 1 || candidate > MAX_TOP_N || (resolved != null && resolved != candidate)) {
                return Optional.empty();
            }
            resolved = candidate;
        }
        return Optional.of(resolved == null ? DEFAULT_TOP_N : resolved);
    }

    private static boolean hasTemporalSuffix(String normalized, int offset) {
        String suffix = normalized.substring(Math.min(offset, normalized.length()));
        return suffix.startsWith("天")
                || suffix.startsWith("日")
                || suffix.startsWith("个月")
                || suffix.startsWith("月")
                || suffix.startsWith("季度")
                || suffix.startsWith("季")
                || suffix.startsWith("年");
    }

    private static int parseChineseOrArabicNumber(String value) {
        if (value == null || value.isBlank()) {
            return -1;
        }
        if (value.chars().allMatch(Character::isDigit)) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        long total = 0;
        long section = 0;
        int pendingDigit = -1;
        int previousSmallUnit = 10_000;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            int digit = chineseDigit(current);
            if (digit >= 0) {
                if (pendingDigit > 0 || (pendingDigit == 0 && section == 0 && total == 0)) {
                    return -1;
                }
                pendingDigit = digit;
                continue;
            }
            int unit = chineseUnit(current);
            if (unit < 0) {
                return -1;
            }
            if (unit < 10_000) {
                if (unit >= previousSmallUnit) {
                    return -1;
                }
                if (pendingDigit < 0) {
                    if (unit != 10 || section != 0) {
                        return -1;
                    }
                    pendingDigit = 1;
                } else if (pendingDigit == 0) {
                    return -1;
                }
                section += (long) pendingDigit * unit;
                pendingDigit = -1;
                previousSmallUnit = unit;
                continue;
            }
            if (pendingDigit >= 0) {
                section += pendingDigit;
                pendingDigit = -1;
            }
            if (section == 0) {
                return -1;
            }
            total = unit == 10_000
                    ? total + section * unit
                    : (total + section) * unit;
            if (total > Integer.MAX_VALUE) {
                return -1;
            }
            section = 0;
            previousSmallUnit = 10_000;
        }
        if (pendingDigit >= 0) {
            if (pendingDigit == 0 && (section > 0 || total > 0)) {
                return -1;
            }
            section += pendingDigit;
        }
        long resolved = total + section;
        return resolved <= Integer.MAX_VALUE ? (int) resolved : -1;
    }

    private static int chineseDigit(char value) {
        return switch (value) {
            case '零', '〇' -> 0;
            case '一' -> 1;
            case '二', '两' -> 2;
            case '三' -> 3;
            case '四' -> 4;
            case '五' -> 5;
            case '六' -> 6;
            case '七' -> 7;
            case '八' -> 8;
            case '九' -> 9;
            default -> -1;
        };
    }

    private static int chineseUnit(char value) {
        return switch (value) {
            case '十' -> 10;
            case '百' -> 100;
            case '千' -> 1_000;
            case '万' -> 10_000;
            case '亿' -> 100_000_000;
            default -> -1;
        };
    }

    private record DateRange(LocalDate startDate, LocalDate endDate) {
    }

    private record TimeResolution(boolean valid, LocalDate startDate, LocalDate endDate) {
        private static TimeResolution defaults() {
            return new TimeResolution(true, null, null);
        }

        private static TimeResolution explicit(LocalDate startDate, LocalDate endDate) {
            return new TimeResolution(true, startDate, endDate);
        }

        private static TimeResolution invalid() {
            return new TimeResolution(false, null, null);
        }

        private boolean explicit() {
            return startDate != null && endDate != null;
        }
    }
}
