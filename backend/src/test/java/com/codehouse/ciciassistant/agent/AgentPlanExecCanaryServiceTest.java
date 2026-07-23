package com.codehouse.ciciassistant.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.agent.config.AgentRuntimePlanExecProperties;
import com.codehouse.ciciassistant.agent.service.AgentPlanExecCanaryService;
import com.codehouse.ciciassistant.agent.service.AgentTaskRuntimeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentPlanExecCanaryServiceTest {

    @Test
    void shouldNotCreateRuntimeWhenServerGateIsDisabled() {
        AgentRuntimePlanExecProperties properties = new AgentRuntimePlanExecProperties();
        properties.setAllowedAgentIds(List.of("agent-canary"));
        AgentTaskRuntimeService runtime = org.mockito.Mockito.mock(AgentTaskRuntimeService.class);
        AgentPlanExecCanaryService service = new AgentPlanExecCanaryService(properties, runtime, new ObjectMapper());

        AgentPlanExecCanaryService.CanaryExecution execution =
                service.start("org-a", "session-a", "agent-canary", "web", "查知识库", "executor-a");

        assertThat(execution.selected()).isFalse();
        assertThat(service.payload(execution)).containsEntry("selected", false);
        verifyNoInteractions(runtime);
    }

    @Test
    void shouldCreateAndAdvanceFixedReadOnlyPlanForExactAllowlistedAgent() {
        AgentRuntimePlanExecProperties properties = new AgentRuntimePlanExecProperties();
        properties.setEnabled(true);
        properties.setAllowedAgentIds(List.of("agent-canary"));
        AgentTaskRuntimeService runtime = org.mockito.Mockito.mock(AgentTaskRuntimeService.class);
        AgentPlanExecCanaryService service = new AgentPlanExecCanaryService(properties, runtime, new ObjectMapper());
        AgentTaskRuntimeService.StepView retrieve = new AgentTaskRuntimeService.StepView(
                101L, "retrieve-context", "RETRIEVE", "RUNNING", 1, 0L, "", "");
        AgentTaskRuntimeService.StepView synthesize = new AgentTaskRuntimeService.StepView(
                102L, "synthesize-response", "SYNTHESIZE", "RUNNING", 1, 1L, "", "");
        when(runtime.createRun(any())).thenReturn(new AgentTaskRuntimeService.RunView(
                77L, "org-a", "agent-canary", "PLAN_EXEC", "CREATED", "查知识库", null, 0L));
        when(runtime.claimNextReadyStep(anyString(), anyLong(), anyString(), anyInt()))
                .thenReturn(Optional.of(new AgentTaskRuntimeService.ClaimedStep(retrieve, Instant.now().plusSeconds(60))))
                .thenReturn(Optional.of(new AgentTaskRuntimeService.ClaimedStep(synthesize, Instant.now().plusSeconds(60))))
                .thenReturn(Optional.empty());

        AgentPlanExecCanaryService.CanaryExecution execution =
                service.start("org-a", "session-a", "agent-canary", "openapi", "查知识库", "executor-a");
        service.completeRetrieve(execution, "knowledge_context_count=2");
        service.completeSynthesis(execution, "已整理结果");

        ArgumentCaptor<String> plan = ArgumentCaptor.forClass(String.class);
        verify(runtime).attachInitialPlan(eq("org-a"), eq(77L), plan.capture());
        assertThat(plan.getValue()).contains("retrieve-context", "synthesize-response").doesNotContain("TOOL");
        verify(runtime).completeStep("org-a", 77L, 101L, "executor-a", 0L, "knowledge_context_count=2");
        verify(runtime).completeStep("org-a", 77L, 102L, "executor-a", 1L, "已整理结果");
        assertThat(service.payload(execution))
                .containsEntry("selected", true)
                .containsEntry("mode", "PLAN_EXEC")
                .containsEntry("status", "COMPLETED")
                .containsEntry("runId", 77L);
    }

    @Test
    void shouldNotSelectPrefixOrWildcardLikeAgentIds() {
        AgentRuntimePlanExecProperties properties = new AgentRuntimePlanExecProperties();
        properties.setEnabled(true);
        properties.setAllowedAgentIds(List.of("agent-canary"));
        AgentTaskRuntimeService runtime = org.mockito.Mockito.mock(AgentTaskRuntimeService.class);
        AgentPlanExecCanaryService service = new AgentPlanExecCanaryService(properties, runtime, new ObjectMapper());

        AgentPlanExecCanaryService.CanaryExecution execution =
                service.start("org-a", "session-a", "agent-canary-extra", "web", "查知识库", "executor-a");

        assertThat(execution.selected()).isFalse();
        verify(runtime, never()).createRun(any());
    }
}
