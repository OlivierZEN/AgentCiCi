package com.codehouse.ciciassistant.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class RuntimeContextPromptServiceTest {

    private final RuntimeContextPromptService service = new RuntimeContextPromptService();

    @Test
    void shouldBuildCurrentDatePromptBlockFromRuntimeClock() {
        RuntimeContextPromptService.RuntimeContext context = service.contextAt(
                ZonedDateTime.of(2026, 4, 30, 22, 45, 12, 0, ZoneId.of("Asia/Shanghai")));

        String promptBlock = service.buildPromptBlock(context);

        assertThat(context.currentDate()).isEqualTo("2026-04-30");
        assertThat(context.currentDateCn()).isEqualTo("2026年4月30日");
        assertThat(context.weekdayCn()).isEqualTo("星期四");
        assertThat(promptBlock)
                .contains("Current date: 2026-04-30")
                .contains("Current local date in Chinese: 2026年4月30日（星期四）")
                .contains("Timezone: Asia/Shanghai")
                .contains("今天是几号")
                .contains("Do not infer today's date from conversation history");
    }
}
