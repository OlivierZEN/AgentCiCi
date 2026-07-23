package com.codehouse.ciciassistant.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codehouse.ciciassistant.agent.service.AgentTaskRuntimeService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AgentTaskRuntimeIntegrationTest {

    @Autowired private AgentTaskRuntimeService runtimeService;

    @Test
    void shouldPersistAndAdvanceDependentPlanSteps() {
        AgentTaskRuntimeService.RunView run = runtimeService.createRun(new AgentTaskRuntimeService.CreateRunCommand(
                "demo-org", "task-session-plan", "task-agent", "web", "PLAN_EXEC", "先查再总结", 3));
        AgentTaskRuntimeService.PlanView plan = runtimeService.attachInitialPlan("demo-org", run.id(), """
                {"goal":"先查再总结","steps":[
                  {"key":"retrieve","kind":"RETRIEVE","dependsOn":[],"allowedToolNames":[],"expectedEvidence":["source"]},
                  {"key":"synthesize","kind":"SYNTHESIZE","dependsOn":["retrieve"],"allowedToolNames":[],"expectedEvidence":["answer"]}
                ]}
                """);
        assertEquals(2, plan.steps().size());
        assertEquals("READY", plan.steps().get(0).status());
        assertEquals("PENDING", plan.steps().get(1).status());

        AgentTaskRuntimeService.ClaimedStep first = runtimeService.claimNextReadyStep("demo-org", run.id(), "executor-a", 30).orElseThrow();
        assertEquals("retrieve", first.step().key());
        AgentTaskRuntimeService.StepView finished = runtimeService.completeStep("demo-org", run.id(), first.step().id(), "executor-a",
                first.step().version(), "retrieval completed");
        assertEquals("SUCCEEDED", finished.status());

        AgentTaskRuntimeService.ClaimedStep second = runtimeService.claimNextReadyStep("demo-org", run.id(), "executor-a", 30).orElseThrow();
        assertEquals("synthesize", second.step().key());
        runtimeService.completeStep("demo-org", run.id(), second.step().id(), "executor-a", second.step().version(), "final answer");

        AgentTaskRuntimeService.RunSnapshot snapshot = runtimeService.snapshot("demo-org", run.id());
        assertEquals("SUCCEEDED", snapshot.run().status());
        assertEquals(2, snapshot.steps().stream().filter(item -> "SUCCEEDED".equals(item.status())).count());
        assertTrue(snapshot.events().stream().anyMatch(item -> "PLAN_VALIDATED".equals(item.type())));
        assertTrue(snapshot.events().stream().anyMatch(item -> "RUN_SUCCEEDED".equals(item.type())));
    }

    @Test
    void shouldRejectCyclicPlansAndStaleStepVersions() {
        AgentTaskRuntimeService.RunView invalidRun = runtimeService.createRun(new AgentTaskRuntimeService.CreateRunCommand(
                "demo-org", "task-session-cycle", "task-agent", "web", "PLAN_EXEC", "cycle check", 2));
        assertThrows(IllegalArgumentException.class, () -> runtimeService.attachInitialPlan("demo-org", invalidRun.id(), """
                {"goal":"cycle check","steps":[
                  {"key":"one","kind":"VERIFY","dependsOn":["two"],"allowedToolNames":[],"expectedEvidence":[]},
                  {"key":"two","kind":"VERIFY","dependsOn":["one"],"allowedToolNames":[],"expectedEvidence":[]}
                ]}
                """));

        AgentTaskRuntimeService.RunView run = runtimeService.createRun(new AgentTaskRuntimeService.CreateRunCommand(
                "demo-org", "task-session-version", "task-agent", "web", "PLAN_EXEC", "version check", 1));
        runtimeService.attachInitialPlan("demo-org", run.id(), """
                {"goal":"version check","steps":[
                  {"key":"verify","kind":"VERIFY","dependsOn":[],"allowedToolNames":[],"expectedEvidence":[]}
                ]}
                """);
        AgentTaskRuntimeService.ClaimedStep claimed = runtimeService.claimNextReadyStep("demo-org", run.id(), "executor-a", 30).orElseThrow();
        assertThrows(IllegalStateException.class, () -> runtimeService.completeStep("demo-org", run.id(), claimed.step().id(), "executor-a",
                claimed.step().version() - 1, "must reject stale completion"));
    }

    @Test
    void shouldRecoverOnlyExpiredLease() {
        AgentTaskRuntimeService.RunView run = runtimeService.createRun(new AgentTaskRuntimeService.CreateRunCommand(
                "demo-org", "task-session-recovery", "task-agent", "web", "PLAN_EXEC", "recover plan", 1));
        runtimeService.attachInitialPlan("demo-org", run.id(), """
                {"goal":"recover plan","steps":[
                  {"key":"verify","kind":"VERIFY","dependsOn":[],"allowedToolNames":[],"expectedEvidence":[]}
                ]}
                """);
        AgentTaskRuntimeService.ClaimedStep claimed = runtimeService.claimNextReadyStep("demo-org", run.id(), "executor-a", 1).orElseThrow();
        assertFalse(runtimeService.recoverExpiredLease("demo-org", run.id(), Instant.now()));
        assertTrue(runtimeService.recoverExpiredLease("demo-org", run.id(), claimed.leaseExpiresAt().plusSeconds(1)));
        AgentTaskRuntimeService.ClaimedStep reclaimed = runtimeService.claimNextReadyStep("demo-org", run.id(), "executor-b", 30).orElseThrow();
        assertEquals(claimed.step().id(), reclaimed.step().id());
        assertEquals(2, reclaimed.step().attemptNo());
    }
}
