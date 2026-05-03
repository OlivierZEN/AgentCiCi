package com.codehouse.ciciassistant.ai.service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RuntimeContextPromptService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_CN = DateTimeFormatter.ofPattern("yyyy年M月d日");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    public RuntimeContext current() {
        return contextAt(ZonedDateTime.now(DEFAULT_ZONE));
    }

    RuntimeContext contextAt(ZonedDateTime now) {
        ZonedDateTime normalized = now.withZoneSameInstant(DEFAULT_ZONE).truncatedTo(ChronoUnit.SECONDS);
        return new RuntimeContext(
                DEFAULT_ZONE.getId(),
                normalized.toLocalDate().toString(),
                TIME.format(normalized),
                DATE_CN.format(normalized),
                weekdayCn(normalized)
        );
    }

    public String buildPromptBlock(RuntimeContext context) {
        return """
                Runtime context for this request:
                - Current date: %s
                - Current local date in Chinese: %s（%s）
                - Current local time: %s
                - Timezone: %s

                Date handling policy:
                - When the user says 今天、今日、today、明天、昨天、本周、this week, interpret it relative to the current date above.
                - If the user asks 今天是几号 / what date is today, answer from this runtime context directly.
                - Do not infer today's date from conversation history, training data, or prior tool results.
                """.formatted(
                context.currentDate(),
                context.currentDateCn(),
                context.weekdayCn(),
                context.currentTime(),
                context.timezone()
        ).trim();
    }

    public Map<String, Object> toPayload(RuntimeContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("currentDate", context.currentDate());
        payload.put("currentDateCn", context.currentDateCn());
        payload.put("weekdayCn", context.weekdayCn());
        payload.put("currentTime", context.currentTime());
        payload.put("timezone", context.timezone());
        return payload;
    }

    private String weekdayCn(ZonedDateTime now) {
        return switch (now.getDayOfWeek()) {
            case MONDAY -> "星期一";
            case TUESDAY -> "星期二";
            case WEDNESDAY -> "星期三";
            case THURSDAY -> "星期四";
            case FRIDAY -> "星期五";
            case SATURDAY -> "星期六";
            case SUNDAY -> "星期日";
        };
    }

    public record RuntimeContext(
            String timezone,
            String currentDate,
            String currentTime,
            String currentDateCn,
            String weekdayCn
    ) {
    }
}
