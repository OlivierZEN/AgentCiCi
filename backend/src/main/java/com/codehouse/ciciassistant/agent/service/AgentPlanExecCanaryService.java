package com.codehouse.ciciassistant.agent.service;

import com.codehouse.ciciassistant.agent.config.AgentRuntimePlanExecProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * P2 bridge from chat to the durable P1 state machine.
 *
 * <p>It deliberately owns no model, RAG, tool, credential, or confirmation behavior. A canary run is
 * a fixed, read-only RETRIEVE → SYNTHESIZE plan whose work remains in the existing chat pipeline.</p>
 */
@Service
public class AgentPlanExecCanaryService {

    private static final int LEASE_SECONDS = 120;
    private final AgentRuntimePlanExecProperties properties;
    private final AgentTaskRuntimeService runtime;
    private final ObjectMapper objectMapper;

    public AgentPlanExecCanaryService(AgentRuntimePlanExecProperties properties,
                                      AgentTaskRuntimeService runtime,
                                      ObjectMapper objectMapper) {
        this.properties = properties;
        this.runtime = runtime;
        this.objectMapper = objectMapper;
    }

    public CanaryExecution start(String orgId, String sessionId, String agentId, String channel,
                                 String goalSummary, String executorId) {
        if (!properties.isEnabledFor(agentId)) return CanaryExecution.notSelected();
        try {
            AgentTaskRuntimeService.RunView run = runtime.createRun(new AgentTaskRuntimeService.CreateRunCommand(
                    orgId, sessionId, agentId, channel, "PLAN_EXEC", goalSummary, 2));
            runtime.attachInitialPlan(orgId, run.id(), fixedPlan(goalSummary));
            AgentTaskRuntimeService.ClaimedStep claimed = runtime.claimNextReadyStep(
                    orgId, run.id(), executorId, LEASE_SECONDS).orElseThrow();
            return CanaryExecution.active(run.id(), orgId, executorId, claimed.step());
        } catch (RuntimeException ex) {
            return CanaryExecution.fallback("PLAN_SETUP_FAILED");
        }
    }

    public void completeRetrieve(CanaryExecution execution, String summary) {
        complete(execution, "RETRIEVE", summary);
    }

    public void completeSynthesis(CanaryExecution execution, String summary) {
        complete(execution, "SYNTHESIZE", summary);
    }

    public void fail(CanaryExecution execution, String errorCode) {
        if (!execution.active() || execution.step() == null) return;
        try {
            runtime.failStep(execution.orgId(), execution.runId(), execution.step().id(), execution.executorId(),
                    execution.step().version(), errorCode == null || errorCode.isBlank() ? "CHAT_EXECUTION_FAILED" : errorCode);
            execution.clearStep();
        } catch (RuntimeException ignored) {
            // Chat's existing failure path remains authoritative; task state is best-effort evidence only.
        }
    }

    public Map<String, Object> payload(CanaryExecution execution) {
        if (execution == null || !execution.selected()) return Map.of("selected", false);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("selected", execution.active());
        payload.put("mode", "PLAN_EXEC");
        payload.put("status", execution.active() && execution.step() != null
                ? "RUNNING" : execution.fallbackReason().isBlank() ? "COMPLETED" : "FALLBACK");
        payload.put("runId", execution.runId());
        payload.put("currentStep", execution.step() == null ? "" : execution.step().key());
        payload.put("fallbackReason", execution.fallbackReason());
        return payload;
    }

    private void complete(CanaryExecution execution, String expectedKind, String summary) {
        if (!execution.active() || execution.step() == null) return;
        AgentTaskRuntimeService.StepView current = execution.step();
        if (!expectedKind.equals(current.kind())) {
            throw new IllegalStateException("Unexpected Plan-Exec canary step");
        }
        runtime.completeStep(execution.orgId(), execution.runId(), current.id(), execution.executorId(), current.version(), summary);
        if ("SYNTHESIZE".equals(expectedKind)) {
            execution.clearStep();
            return;
        }
        runtime.claimNextReadyStep(execution.orgId(), execution.runId(), execution.executorId(), LEASE_SECONDS)
                .ifPresentOrElse(next -> execution.setStep(next.step()), execution::clearStep);
    }

    private String fixedPlan(String goal) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "goal", goal,
                    "steps", List.of(
                            Map.of("key", "retrieve-context", "kind", "RETRIEVE", "dependsOn", List.of(),
                                    "allowedToolNames", List.of(), "expectedEvidence", List.of("rag-decision")),
                            Map.of("key", "synthesize-response", "kind", "SYNTHESIZE", "dependsOn", List.of("retrieve-context"),
                                    "allowedToolNames", List.of(), "expectedEvidence", List.of("model-response"))
                    )
            ));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot create Plan-Exec canary plan", ex);
        }
    }

    public static final class CanaryExecution {
        private final boolean selected;
        private final long runId;
        private final String orgId;
        private final String executorId;
        private final String fallbackReason;
        private AgentTaskRuntimeService.StepView step;

        private CanaryExecution(boolean selected, long runId, String orgId, String executorId,
                                AgentTaskRuntimeService.StepView step, String fallbackReason) {
            this.selected = selected;
            this.runId = runId;
            this.orgId = orgId;
            this.executorId = executorId;
            this.step = step;
            this.fallbackReason = fallbackReason;
        }
        public static CanaryExecution notSelected() { return new CanaryExecution(false, 0L, "", "", null, ""); }
        static CanaryExecution fallback(String reason) { return new CanaryExecution(true, 0L, "", "", null, reason); }
        static CanaryExecution active(long runId, String orgId, String executorId, AgentTaskRuntimeService.StepView step) {
            return new CanaryExecution(true, runId, orgId, executorId, step, "");
        }
        public boolean selected() { return selected; }
        public boolean active() { return selected && runId > 0 && fallbackReason.isBlank(); }
        public long runId() { return runId; }
        public String orgId() { return orgId; }
        public String executorId() { return executorId; }
        public AgentTaskRuntimeService.StepView step() { return step; }
        public String fallbackReason() { return fallbackReason; }
        void setStep(AgentTaskRuntimeService.StepView step) { this.step = step; }
        void clearStep() { this.step = null; }
    }
}
