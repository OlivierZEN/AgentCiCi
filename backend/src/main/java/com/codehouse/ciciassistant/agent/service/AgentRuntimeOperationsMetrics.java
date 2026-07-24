package com.codehouse.ciciassistant.agent.service;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Emits only fixed-cardinality, tenant-free runtime operations metrics. */
@Service
public class AgentRuntimeOperationsMetrics {

    private static final Set<String> PLAN_OUTCOMES = Set.of(
            "SELECTED", "NOT_SELECTED", "FALLBACK", "SUCCEEDED", "FAILED");
    private static final Set<String> PLAN_REASONS = Set.of(
            "NONE", "SCOPE_NOT_ALLOWLISTED", "PLAN_SETUP_FAILED", "RETRIEVE_STATE_UPDATE_FAILED",
            "SYNTHESIS_STATE_UPDATE_FAILED", "CHAT_EXECUTION_FAILED");
    private static final Set<String> REVIEW_OUTCOMES = Set.of("SKIPPED", "PASS", "HANDOFF");
    private static final Set<String> REVIEW_REASONS = Set.of(
            "NONE", "SCOPE_NOT_ALLOWLISTED", "AGENT_MISMATCH", "UNSUPPORTED_MODE", "RUN_NOT_SUCCEEDED",
            "STEP_BUDGET_VIOLATION", "STEP_NOT_SUCCEEDED", "REFLECT_BUDGET_EXHAUSTED",
            "CONFIRMATION_REQUIRED", "OUTPUT_EMPTY");

    private final MeterRegistry meterRegistry;

    public AgentRuntimeOperationsMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    static AgentRuntimeOperationsMetrics noop() {
        return new AgentRuntimeOperationsMetrics(null);
    }

    public void recordMode(AgentRuntimeModeRouter.ModeDecision decision) {
        AgentRuntimeModeRouter.ModeDecision safe = decision == null
                ? null : decision;
        String mode = safe == null ? "LEGACY_REACT" : safe.mode().name();
        String reason = safe == null || safe.reasonCodes().isEmpty()
                ? "INVALID_INPUT" : safe.reasonCodes().getFirst().name();
        increment("cici.agent_runtime.mode_decisions", mode, "SELECTED", reason);
    }

    public void recordPlanExec(String outcome, String reason) {
        increment("cici.agent_runtime.plan_exec", "PLAN_EXEC", allowed(PLAN_OUTCOMES, outcome),
                allowed(PLAN_REASONS, reason));
    }

    public void recordReflect(String outcome, String reason) {
        increment("cici.agent_runtime.reflect", "REFLECT", allowed(REVIEW_OUTCOMES, outcome),
                allowed(REVIEW_REASONS, reason));
    }

    private void increment(String name, String mode, String outcome, String reason) {
        if (meterRegistry == null) return;
        meterRegistry.counter(name, "mode", mode, "outcome", outcome, "reason", reason).increment();
    }

    private static String allowed(Set<String> values, String value) {
        return value != null && values.contains(value) ? value : "OTHER";
    }
}
