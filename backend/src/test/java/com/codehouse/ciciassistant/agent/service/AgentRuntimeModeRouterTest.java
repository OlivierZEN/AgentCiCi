package com.codehouse.ciciassistant.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.codehouse.ciciassistant.agent.config.AgentRuntimeModeRouterProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentRuntimeModeRouterTest {

    @Test
    void shouldKeepLegacyPathWhenDisabledOrAgentIsNotExactlyAllowlisted() {
        AgentRuntimeModeRouterProperties properties = new AgentRuntimeModeRouterProperties();
        AgentRuntimeModeRouter router = new AgentRuntimeModeRouter(properties);

        assertThat(router.decide(input("agent-a", "先查询订单，再查询工单", List.of("get_order"), false, false)).mode())
                .isEqualTo(AgentRuntimeModeRouter.Mode.LEGACY_REACT);

        properties.setEnabled(true);
        properties.setAllowedOrgIds(List.of("org-a"));
        properties.setAllowedAgentIds(List.of("agent-ab"));
        assertThat(router.decide(input("agent-a", "先查询订单，再查询工单", List.of("get_order"), false, false)).mode())
                .isEqualTo(AgentRuntimeModeRouter.Mode.LEGACY_REACT);
    }

    @Test
    void shouldChooseDirectReactAndPlanExecWithStableReasons() {
        AgentRuntimeModeRouter router = enabledRouter();

        AgentRuntimeModeRouter.ModeDecision direct = router.decide(input("agent-a", "你好", List.of(), false, false));
        AgentRuntimeModeRouter.ModeDecision react = router.decide(input("agent-a", "查询今天的订单", List.of("get_order"), false, false));
        AgentRuntimeModeRouter.ModeDecision plan = router.decide(
                input("agent-a", "先查询订单，再查询工单并给出建议", List.of("get_order", "get_case"), false, false));

        assertThat(direct.mode()).isEqualTo(AgentRuntimeModeRouter.Mode.DIRECT);
        assertThat(direct.reasonCodes()).containsExactly(AgentRuntimeModeRouter.ReasonCode.NO_EXTERNAL_CONTEXT);
        assertThat(react.mode()).isEqualTo(AgentRuntimeModeRouter.Mode.REACT);
        assertThat(react.reasonCodes()).containsExactly(AgentRuntimeModeRouter.ReasonCode.READONLY_TOOL_LOOKUP);
        assertThat(plan.mode()).isEqualTo(AgentRuntimeModeRouter.Mode.PLAN_EXEC);
        assertThat(plan.reasonCodes()).containsExactly(AgentRuntimeModeRouter.ReasonCode.EXPLICIT_DEPENDENCY);
    }

    @Test
    void shouldKeepLegacyPathOutsideTheExactOrganizationAllowlist() {
        AgentRuntimeModeRouterProperties properties = new AgentRuntimeModeRouterProperties();
        properties.setEnabled(true);
        properties.setAllowedOrgIds(List.of("org-a"));
        properties.setAllowedAgentIds(List.of("agent-a"));
        AgentRuntimeModeRouter router = new AgentRuntimeModeRouter(properties);

        AgentRuntimeModeRouter.ModeDecision decision = router.decide(new AgentRuntimeModeRouter.RoutingInput(
                "org-b", "agent-a", "web", "先查询订单，再查询工单", List.of("get_order"), false, false));

        assertThat(decision.mode()).isEqualTo(AgentRuntimeModeRouter.Mode.LEGACY_REACT);
        assertThat(decision.reasonCodes()).containsExactly(AgentRuntimeModeRouter.ReasonCode.SCOPE_NOT_ALLOWLISTED);
    }

    @Test
    void shouldPreserveConfirmationContinuationAndOnlyMarkSensitiveIntent() {
        AgentRuntimeModeRouter router = enabledRouter();

        AgentRuntimeModeRouter.ModeDecision continuation = router.decide(
                input("agent-a", "继续", List.of("get_order"), false, true));
        AgentRuntimeModeRouter.ModeDecision sensitive = router.decide(
                input("agent-a", "先查询订单，再发送处理结果", List.of("get_order"), false, false));

        assertThat(continuation.mode()).isEqualTo(AgentRuntimeModeRouter.Mode.LEGACY_REACT);
        assertThat(continuation.reasonCodes()).containsExactly(
                AgentRuntimeModeRouter.ReasonCode.PENDING_CONFIRMATION_CONTINUATION);
        assertThat(sensitive.mode()).isEqualTo(AgentRuntimeModeRouter.Mode.PLAN_EXEC);
        assertThat(sensitive.requiresConfirmation()).isTrue();
        assertThat(sensitive.riskLevel()).isEqualTo(AgentRuntimeModeRouter.RiskLevel.HIGH);
        assertThat(sensitive.reflectRequired()).isTrue();
    }

    @Test
    void shouldFallBackToReactWhenTheIndependentPlanExecGateDoesNotStartARun() {
        AgentRuntimeModeRouter router = enabledRouter();
        AgentRuntimeModeRouter.ModeDecision requestedPlan = router.decide(
                input("agent-a", "先查询订单，再查询工单", List.of("get_order", "get_case"), false, false));

        AgentRuntimeModeRouter.ModeDecision fallback = router.fallbackToReact(requestedPlan);

        assertThat(fallback.mode()).isEqualTo(AgentRuntimeModeRouter.Mode.REACT);
        assertThat(fallback.reasonCodes()).containsExactly(AgentRuntimeModeRouter.ReasonCode.PLAN_EXEC_GATE_NOT_MET);
    }

    private static AgentRuntimeModeRouter enabledRouter() {
        AgentRuntimeModeRouterProperties properties = new AgentRuntimeModeRouterProperties();
        properties.setEnabled(true);
        properties.setAllowedOrgIds(List.of("org-a"));
        properties.setAllowedAgentIds(List.of("agent-a"));
        return new AgentRuntimeModeRouter(properties);
    }

    private static AgentRuntimeModeRouter.RoutingInput input(String agentId, String question,
                                                              List<String> tools, boolean externalFactRequired,
                                                              boolean pendingConfirmation) {
        return new AgentRuntimeModeRouter.RoutingInput(
                "org-a", agentId, "web", question, tools, externalFactRequired, pendingConfirmation);
    }
}
