package com.codehouse.ciciassistant.skill.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SkillPromptAssemblerTest {

    @Test
    void selectedSkillIsTheOnlyBusinessProcedureInjectedIntoThePrompt() {
        SkillResolverService.ResolvedSkill selected = new SkillResolverService.ResolvedSkill(
                "customer-research", "客户调研", "只按客户调研流程收集和整理信息。", List.of(), List.of(),
                "事实不足时交接人工确认。", "输出调研摘要和待确认项。", "LOW", "explicit");
        SkillResolverService.ResolvedSkill other = new SkillResolverService.ResolvedSkill(
                "campaign-plan", "营销方案", "不得注入的营销方案流程。", List.of(), List.of(),
                "", "不得注入的营销输出。", "LOW", "always-on");
        SkillResolverService.ResolvedSkillContext context = new SkillResolverService.ResolvedSkillContext(
                "agent-1", "大乔", List.of(selected, other), List.of("customer-research", "campaign-plan"),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(selected.handoffRule()),
                other.outputContract(), null, null, "customer-research", null, null,
                List.of(), List.of(), SkillResolverService.ResolvedPolicyBundle.EMPTY);

        String prompt = new SkillPromptAssembler().assemble("基础系统提示", context);

        assertThat(prompt)
                .contains("Current Agent Definition external name: 大乔")
                .contains("Use exactly this external name whenever you state your name, introduce yourself, or answer who you are.")
                .contains("CiCi / AgentCiCi is the hosting platform name, not your own name")
                .contains("Selected business skill is mandatory for this turn: customer-research")
                .contains("只按客户调研流程收集和整理信息。")
                .contains("Mandatory selected-skill output contract: 输出调研摘要和待确认项。")
                .doesNotContain("不得注入的营销方案流程。")
                .doesNotContain("不得注入的营销输出。");
    }

    @Test
    void agentDefinitionNameIsTheOnlyExternalIdentityEvenWhenBasePromptMentionsCici() {
        SkillResolverService.ResolvedSkillContext context = new SkillResolverService.ResolvedSkillContext(
                "devautopilot-pm", "研发产品经理", List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), null, "你负责研发交付。", null, null, null, null,
                List.of(), List.of(), SkillResolverService.ResolvedPolicyBundle.EMPTY);

        String prompt = new SkillPromptAssembler().assemble(
                "You are running on the CiCi platform. CiCi is not the Agent name.", context);

        assertThat(prompt)
                .startsWith("[Agent identity - authoritative]")
                .contains("Current Agent Definition external name: 研发产品经理")
                .contains("Use exactly this external name whenever you state your name, introduce yourself, or answer who you are.")
                .contains("Role descriptions, Skills, tools, model providers, and model names must not replace or override this external name.")
                .doesNotContain("You are CiCi");
    }
}
