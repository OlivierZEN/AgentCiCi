package com.codehouse.ciciassistant.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.codehouse.ciciassistant.agent.domain.AgentEvalCaseEntity;
import com.codehouse.ciciassistant.agent.service.AgentEvaluationAssertionEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentEvaluationAssertionEngineTest {

    private final AgentEvaluationAssertionEngine engine = new AgentEvaluationAssertionEngine(new ObjectMapper());

    @Test
    void shouldEvaluateMultipleAssertionsAndKeepFailureEvidence() {
        AgentEvalCaseEntity evalCase = evalCase("""
                {"assertions":[
                  {"type":"OUTPUT_CONTAINS","expected":"建议"},
                  {"type":"TOOL_ARGUMENT_CONTAINS","path":"customerId","expected":"C-100"},
                  {"type":"MAX_LATENCY_MS","expected":500}
                ]}
                """);

        var outcome = engine.evaluate(
                evalCase,
                "处理建议：优先联系客户",
                "published-executed",
                List.of("tool-invoke-best"),
                Map.of("toolCalls", List.of(Map.of(
                        "name", "crm.customer.get",
                        "arguments", Map.of("customerId", "C-100")))),
                680L);

        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.score()).isEqualTo(2.0d / 3.0d);
        assertThat(outcome.failureCategory()).isEqualTo("LATENCY_BUDGET_EXCEEDED");
        assertThat(outcome.assertionResults()).hasSize(3);
        assertThat(outcome.toolCallCount()).isEqualTo(1);
    }

    @Test
    void shouldFailClosedWhenAssertionConfigurationIsInvalid() {
        var outcome = engine.evaluate(evalCase("{invalid-json"), "任意输出", "ok", List.of(), Map.of(), 1L);

        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.failureCategory()).isEqualTo("ASSERTION_CONFIG_INVALID");
        assertThat(outcome.failureSummary()).isEqualTo("断言配置无法解析");
    }

    private AgentEvalCaseEntity evalCase(String assertionConfigJson) {
        return new AgentEvalCaseEntity(
                "demo-org", "evaluation-test", 1L, "复合断言", "请给出建议",
                AgentEvalCaseEntity.ASSERT_OUTPUT_CONTAINS, "建议", null, null, null, null, "P0",
                "multi-assertion", "ANSWER_QUALITY", "[]", "{}", assertionConfigJson,
                null, "[]", null, false, "APPROVED", "NOT_REQUIRED");
    }
}
