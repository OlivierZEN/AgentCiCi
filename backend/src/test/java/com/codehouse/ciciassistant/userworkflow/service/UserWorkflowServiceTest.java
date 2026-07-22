package com.codehouse.ciciassistant.userworkflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserWorkflowServiceTest {

    @Test
    void shouldParseDailyClockCadenceIntoExecutableCronAndNextFireTime() throws Exception {
        UserWorkflowService service = serviceForScheduleParsing();

        List<?> routines = parseRoutines(service, "每天 09:00 搜索美国 K12 教育机构");

        assertThat(routines).hasSize(1);
        assertThat(routineValue(routines.get(0), "cronExpr")).isEqualTo("0 0 9 * * *");
        assertThat(nextFireAt(service, "0 0 9 * * *", Instant.parse("2026-07-22T00:00:00Z")))
                .isEqualTo(Instant.parse("2026-07-22T01:00:00Z"));
    }

    @Test
    void shouldPreserveMeridiemWhenParsingClockCadence() throws Exception {
        UserWorkflowService service = serviceForScheduleParsing();

        List<?> routines = parseRoutines(service, "每天下午 3点30分发送日报");

        assertThat(routines).hasSize(1);
        assertThat(routineValue(routines.get(0), "cronExpr")).isEqualTo("0 30 15 * * *");
    }

    private UserWorkflowService serviceForScheduleParsing() {
        return new UserWorkflowService(
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, new ObjectMapper());
    }

    @SuppressWarnings("unchecked")
    private List<?> parseRoutines(UserWorkflowService service, String sourceText) throws Exception {
        Method method = UserWorkflowService.class.getDeclaredMethod("parseRoutines", String.class, List.class, String.class);
        method.setAccessible(true);
        return (List<?>) method.invoke(service, sourceText, List.of(), "Asia/Shanghai");
    }

    private String routineValue(Object routine, String field) throws Exception {
        Method accessor = routine.getClass().getDeclaredMethod(field);
        accessor.setAccessible(true);
        return (String) accessor.invoke(routine);
    }

    private Instant nextFireAt(UserWorkflowService service, String cronExpr, Instant from) throws Exception {
        Method method = UserWorkflowService.class.getDeclaredMethod(
                "computeNextFire", String.class, String.class, String.class, Integer.class, Instant.class);
        method.setAccessible(true);
        return (Instant) method.invoke(service, "SCHEDULE", cronExpr, "Asia/Shanghai", null, from);
    }
}
