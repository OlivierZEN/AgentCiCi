package com.codehouse.ciciassistant.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentRuntimeOperationsMetricsTest {

    @Test
    void shouldEmitOnlyFixedCardinalityOutcomeAndReasonTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentRuntimeOperationsMetrics metrics = new AgentRuntimeOperationsMetrics(registry);
        AgentRuntimeModeRouter.ModeDecision decision = new AgentRuntimeModeRouter.ModeDecision(
                AgentRuntimeModeRouter.Mode.PLAN_EXEC,
                List.of(AgentRuntimeModeRouter.ReasonCode.EXPLICIT_DEPENDENCY),
                AgentRuntimeModeRouter.RiskLevel.LOW,
                new AgentRuntimeModeRouter.Budget(3, 6, 1, 1), false, false);

        metrics.recordMode(decision);
        metrics.recordPlanExec("FAILED", "untrusted runtime error");
        metrics.recordReflect("HANDOFF", "UNTRUSTED_REASON");

        assertThat(registry.find("cici.agent_runtime.mode_decisions").counter().count()).isEqualTo(1d);
        assertThat(registry.find("cici.agent_runtime.plan_exec")
                .tags("mode", "PLAN_EXEC", "outcome", "FAILED", "reason", "OTHER").counter().count()).isEqualTo(1d);
        assertThat(registry.find("cici.agent_runtime.reflect")
                .tags("mode", "REFLECT", "outcome", "HANDOFF", "reason", "OTHER").counter().count()).isEqualTo(1d);
    }
}
