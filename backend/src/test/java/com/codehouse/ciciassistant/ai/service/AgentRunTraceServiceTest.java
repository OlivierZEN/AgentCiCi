package com.codehouse.ciciassistant.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.agent.domain.AgentDefinitionRepository;
import com.codehouse.ciciassistant.agent.service.AgentTaskRuntimeService;
import com.codehouse.ciciassistant.ai.domain.AgentRunTraceEntity;
import com.codehouse.ciciassistant.ai.domain.AgentRunTraceRepository;
import com.codehouse.ciciassistant.ai.domain.ChatMessageRepository;
import com.codehouse.ciciassistant.ai.domain.ChatSessionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentRunTraceServiceTest {

    @Test
    void retainsRedactedAdminDetailSeparatelyFromCompactTraceSummary() throws Exception {
        AgentRunTraceRepository traces = mock(AgentRunTraceRepository.class);
        AgentRunTraceService service = new AgentRunTraceService(
                traces,
                mock(ChatSessionRepository.class),
                mock(ChatMessageRepository.class),
                mock(AgentDefinitionRepository.class),
                mock(AgentTaskRuntimeService.class));
        String question = "客户互动工作台上下文：" + "业务事实".repeat(400)
                + " password=super-secret 13900009999";

        service.recordChatRun(new AgentRunTraceService.ChatRunTraceInput(
                "org-1", "user-1", "web:session-1", "agent-1", question, "已完成",
                "model-1", "", "", List.of(), List.of(), null, List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), null, 0, Instant.parse("2026-07-21T00:00:00Z"),
                Instant.parse("2026-07-21T00:00:01Z")));

        ArgumentCaptor<AgentRunTraceEntity> saved = ArgumentCaptor.forClass(AgentRunTraceEntity.class);
        verify(traces).save(saved.capture());
        JsonNode detail = new ObjectMapper().readTree(saved.getValue().getDetailJson());

        assertThat(detail.path("request").path("question").asText()).endsWith("…");
        assertThat(detail.path("request").path("questionDetail").path("text").asText())
                .contains("业务事实")
                .doesNotContain("super-secret")
                .doesNotContain("13900009999");
        assertThat(detail.path("request").path("questionDetail").path("truncated").asBoolean()).isFalse();
    }

    @Test
    void recordsRequestedAndEffectiveForcedSkillContext() throws Exception {
        AgentRunTraceRepository traces = mock(AgentRunTraceRepository.class);
        AgentRunTraceService service = new AgentRunTraceService(
                traces,
                mock(ChatSessionRepository.class),
                mock(ChatMessageRepository.class),
                mock(AgentDefinitionRepository.class),
                mock(AgentTaskRuntimeService.class));

        service.recordChatRun(new AgentRunTraceService.ChatRunTraceInput(
                "org-1", "user-1", "web:session-1", "agent-1", "整理客户信息", "已完成",
                "model-1", "customer-research", "customer-research", List.of(), List.of(), null,
                List.of(), List.of("customer-research", "other-skill"), List.of("customer-research"),
                List.of(), List.of(), List.of(), List.of(), null, 0,
                Instant.parse("2026-07-21T00:00:00Z"), Instant.parse("2026-07-21T00:00:01Z")));

        ArgumentCaptor<AgentRunTraceEntity> saved = ArgumentCaptor.forClass(AgentRunTraceEntity.class);
        verify(traces).save(saved.capture());
        JsonNode skills = new ObjectMapper().readTree(saved.getValue().getDetailJson()).path("skills");

        assertThat(skills.path("requestedSkillCode").asText()).isEqualTo("customer-research");
        assertThat(skills.path("effectiveSkillCode").asText()).isEqualTo("customer-research");
        assertThat(skills.path("selectionStatus").asText()).isEqualTo("FORCED");
        assertThat(skills.path("selectionReason").asText()).contains("强制业务上下文");
        assertThat(skills.path("activatedSkillCodes").toString()).contains("customer-research");
    }

    @Test
    void projectsOnlyTheSameCompanyRuntimeFactsForAnExactlyLinkedTrace() {
        AgentRunTraceRepository traces = mock(AgentRunTraceRepository.class);
        AgentTaskRuntimeService runtime = mock(AgentTaskRuntimeService.class);
        AgentRunTraceService service = new AgentRunTraceService(
                traces, mock(ChatSessionRepository.class), mock(ChatMessageRepository.class),
                mock(AgentDefinitionRepository.class), runtime);
        Instant startedAt = Instant.parse("2026-07-23T00:00:00Z");
        AgentRunTraceEntity trace = new AgentRunTraceEntity(
                "trace-1", "org-1", "user-1", "web:session-1", "agent-1", "web", "COMPLETED",
                "运行", "摘要", "model-1", "", startedAt, startedAt.plusSeconds(2), 2000,
                1, 0, 0, "[]", "[]", "[]",
                "{\"runtimeExecution\":{\"contextSnapshot\":{\"runtimeTask\":{\"runtimeRunId\":42,\"riskLevel\":\"HIGH\",\"requiresConfirmation\":true}}}}",
                startedAt);
        when(traces.findByTraceIdAndCompanyId("trace-1", "org-1")).thenReturn(Optional.of(trace));
        when(runtime.traceExecution("org-1", 42L)).thenReturn(Optional.of(new AgentTaskRuntimeService.TraceExecutionView(
                42L, "PLAN_EXEC", "SUCCEEDED", 1, "PASS", "PASS", "token=hidden",
                List.of(new AgentTaskRuntimeService.TraceStepView(
                        "retrieve-context", "RETRIEVE", "SUCCEEDED", 1, startedAt, startedAt.plusSeconds(1),
                        "手机号 13900009999")),
                List.of(new AgentTaskRuntimeService.TraceEventView("RUN_SUCCEEDED", startedAt.plusSeconds(1))))));

        Map<String, Object> payload = service.orgTraceDetail("org-1", "trace-1");
        @SuppressWarnings("unchecked")
        Map<String, Object> execution = (Map<String, Object>) payload.get("runtimeExecution");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) execution.get("steps");

        assertThat(execution).containsEntry("associated", true)
                .containsEntry("runId", 42L)
                .containsEntry("riskLevel", "HIGH")
                .containsEntry("partialReason", "token=[redacted]");
        assertThat(steps).singleElement().satisfies(step ->
                assertThat(step.get("evidenceSummary")).isEqualTo("手机号 139****9999"));
        verify(runtime).traceExecution("org-1", 42L);
    }
}
