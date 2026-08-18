package com.codehouse.ciciassistant.platform.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.codehouse.ciciassistant.spec.SpecCompilerService;
import java.util.List;
import org.junit.jupiter.api.Test;

class DevAutopilotProductManagerTemplateTest {

    @Test
    void standardTemplateSeparatesRuntimePolicyFromEightStepWorkflow() {
        assertThat(DevAutopilotProductManagerAgentPublisher.STANDARD_SYSTEM_PROMPT)
                .contains("研发产品经理智能体", "已绑定且已发布的 Skill", "明确人类确认", "当前租户")
                .doesNotContain("semattice_project_delivery_query");

        SpecCompilerService.SpecCompilation compilation = new SpecCompilerService().compile(
                new SpecCompilerService.SpecCompileCommand(
                        "agent-workflow",
                        "研发产品经理",
                        DevAutopilotProductManagerAgentPublisher.STANDARD_SPEC,
                        List.of(
                                "semattice_project_delivery_query",
                                "semattice_project_delivery_create",
                                "semattice_project_delivery_update",
                                "semattice_project_delivery_transfer",
                                "semattice_project_delivery_delete",
                                "semattice_project_delivery_review"),
                        List.of(),
                        "高风险操作必须确认",
                        "HIGH"));

        assertThat(compilation.specIr().steps()).hasSize(8);
        assertThat(compilation.specIr().steps().get(0)).startsWith("接收用户输入");
        assertThat(compilation.specIr().steps().get(7)).contains("失败关闭", "转人工兜底");
        assertThat(compilation.specIr().intents()).contains(
                "query", "intake", "planning", "create", "update", "delete", "transfer", "review", "acceptance", "handoff");
        assertThat(compilation.specIr().decisionRules())
                .anyMatch(rule -> rule.contains("明确确认"))
                .anyMatch(rule -> rule.contains("回读记录或事件标识"))
                .anyMatch(rule -> rule.contains("失败关闭"));
    }
}
