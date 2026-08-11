package com.codehouse.ciciassistant.semattice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class DevAutopilotDeveloperAssignmentServiceTest {

    @Test
    void selectsOnlyFromTheActiveTenantDeveloperQueryAndUsesAStableRoute() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), eq("org-1"))).thenReturn(List.of(
                new DevAutopilotDeveloperAssignmentService.DeveloperAssignment("developer-a", "鲁班"),
                new DevAutopilotDeveloperAssignmentService.DeveloperAssignment("developer-b", "墨子")));
        DevAutopilotDeveloperAssignmentService service = new DevAutopilotDeveloperAssignmentService(jdbc);

        var first = service.select("org-1", "项目A/退出后仍保持登录");
        var replay = service.select("org-1", "项目A/退出后仍保持登录");

        assertThat(first).isPresent();
        assertThat(replay).isEqualTo(first);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, org.mockito.Mockito.times(2)).query(sql.capture(), any(RowMapper.class), eq("org-1"));
        assertThat(sql.getAllValues()).allSatisfy(query -> assertThat(query)
                .contains("resource.logical_role = 'developer'")
                .contains("resource.lifecycle_state = 'ACTIVE'")
                .contains("principal.lifecycle_status = 'ACTIVE'")
                .contains("activation.company_id = ?"));
    }

    @Test
    void leavesTheItemUnassignedWhenNoActiveDeveloperExists() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), eq("org-1"))).thenReturn(List.of());

        assertThat(new DevAutopilotDeveloperAssignmentService(jdbc).select("org-1", "事项")).isEmpty();
    }
}
