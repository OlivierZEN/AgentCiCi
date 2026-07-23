package com.codehouse.ciciassistant.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.agent.config.AgentRuntimeReflectProperties;
import com.codehouse.ciciassistant.agent.domain.AgentTaskEventRepository;
import com.codehouse.ciciassistant.agent.domain.AgentTaskReviewEntity;
import com.codehouse.ciciassistant.agent.domain.AgentTaskReviewRepository;
import com.codehouse.ciciassistant.agent.domain.AgentTaskRunEntity;
import com.codehouse.ciciassistant.agent.domain.AgentTaskRunRepository;
import com.codehouse.ciciassistant.agent.domain.AgentTaskStepEntity;
import com.codehouse.ciciassistant.agent.domain.AgentTaskStepRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AgentTaskReflectServiceTest {

    @Test
    void shouldSkipWithoutServerOwnedExactAllowlist() {
        Fixture fixture = new Fixture(false);

        AgentTaskReflectService.ReflectResult result = fixture.service.reflect(command(false));

        assertThat(result.selected()).isFalse();
        assertThat(result.gateStatus()).isEqualTo("SKIPPED");
        verifyNoInteractions(fixture.runRepository, fixture.stepRepository, fixture.reviewRepository, fixture.eventRepository);
    }

    @Test
    void shouldPersistPassOnlyForSucceededPlanWithSucceededSteps() {
        Fixture fixture = new Fixture(true);
        fixture.stubSucceededPlan();
        fixture.properties.setAllowedAgentIds(List.of("agent-a", "agent-other"));

        AgentTaskReflectService.ReflectResult result = fixture.service.reflect(command(false));

        assertThat(result.selected()).isTrue();
        assertThat(result.gateStatus()).isEqualTo("PASS");
        assertThat(result.reviewerStatus()).isEqualTo("PASS");
        assertThat(result.issueCodes()).isEmpty();
        verify(fixture.reviewRepository).saveAndFlush(any(AgentTaskReviewEntity.class));
        verify(fixture.eventRepository).save(any());
    }

    @Test
    void shouldBlockReviewerWhenConfirmationIsRequired() {
        Fixture fixture = new Fixture(true);
        fixture.stubSucceededPlan();

        AgentTaskReflectService.ReflectResult result = fixture.service.reflect(command(true));

        assertThat(result.gateStatus()).isEqualTo("BLOCKED");
        assertThat(result.reviewerStatus()).isEqualTo("HANDOFF");
        assertThat(result.issueCodes()).containsExactly("CONFIRMATION_REQUIRED");
        verify(fixture.reviewRepository).saveAndFlush(any(AgentTaskReviewEntity.class));
        verify(fixture.eventRepository).save(any());
    }

    @Test
    void shouldRejectAgentMismatchAndReflectBudgetExhaustion() {
        Fixture fixture = new Fixture(true);
        fixture.stubSucceededPlan();
        fixture.properties.setAllowedAgentIds(List.of("agent-a", "agent-other"));
        when(fixture.reviewRepository.findByOrgIdAndRunIdOrderByReviewRoundAsc("org-a", 10L))
                .thenReturn(List.of(mock(AgentTaskReviewEntity.class)));

        AgentTaskReflectService.ReflectResult exhausted = fixture.service.reflect(command(false));
        AgentTaskReflectService.ReflectResult mismatch = fixture.service.reflect(
                new AgentTaskReflectService.ReflectCommand("org-a", 10L, "agent-other", true, false, "答复"));

        assertThat(exhausted.issueCodes()).containsExactly("REFLECT_BUDGET_EXHAUSTED");
        assertThat(mismatch.issueCodes()).containsExactly("AGENT_MISMATCH");
    }

    private static AgentTaskReflectService.ReflectCommand command(boolean confirmationRequired) {
        return new AgentTaskReflectService.ReflectCommand(
                "org-a", 10L, "agent-a", true, confirmationRequired, "可审计的最终答复");
    }

    private static final class Fixture {
        private final AgentRuntimeReflectProperties properties = new AgentRuntimeReflectProperties();
        private final AgentTaskRunRepository runRepository = mock(AgentTaskRunRepository.class);
        private final AgentTaskStepRepository stepRepository = mock(AgentTaskStepRepository.class);
        private final AgentTaskReviewRepository reviewRepository = mock(AgentTaskReviewRepository.class);
        private final AgentTaskEventRepository eventRepository = mock(AgentTaskEventRepository.class);
        private final AgentTaskReflectService service;

        private Fixture(boolean enabled) {
            properties.setEnabled(enabled);
            properties.setAllowedOrgIds(List.of("org-a"));
            properties.setAllowedAgentIds(List.of("agent-a"));
            service = new AgentTaskReflectService(properties, runRepository, stepRepository, reviewRepository,
                    eventRepository, new ObjectMapper());
        }

        private void stubSucceededPlan() {
            AgentTaskRunEntity run = mock(AgentTaskRunEntity.class);
            AgentTaskStepEntity retrieve = mock(AgentTaskStepEntity.class);
            AgentTaskStepEntity synthesize = mock(AgentTaskStepEntity.class);
            AgentTaskReviewEntity review = mock(AgentTaskReviewEntity.class);
            when(run.getMode()).thenReturn("PLAN_EXEC");
            when(run.getAgentId()).thenReturn("agent-a");
            when(run.getStatus()).thenReturn(AgentTaskRunEntity.STATUS_SUCCEEDED);
            when(run.getMaxSteps()).thenReturn(2);
            when(retrieve.getStatus()).thenReturn(AgentTaskStepEntity.STATUS_SUCCEEDED);
            when(synthesize.getStatus()).thenReturn(AgentTaskStepEntity.STATUS_SUCCEEDED);
            when(runRepository.findByIdAndOrgId(10L, "org-a")).thenReturn(Optional.of(run));
            when(stepRepository.findByOrgIdAndRunIdOrderByStepOrderAsc("org-a", 10L))
                    .thenReturn(List.of(retrieve, synthesize));
            when(reviewRepository.findByOrgIdAndRunIdOrderByReviewRoundAsc("org-a", 10L)).thenReturn(List.of());
            when(reviewRepository.saveAndFlush(any())).thenReturn(review);
            when(review.getId()).thenReturn(33L);
        }
    }
}
