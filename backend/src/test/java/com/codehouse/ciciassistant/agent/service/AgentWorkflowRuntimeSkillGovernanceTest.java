package com.codehouse.ciciassistant.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AgentWorkflowRuntimeSkillGovernanceTest {

    @Test
    void shouldExposePinnedSkillSnapshotGovernanceFields() {
        AgentWorkflowSkillRefService.RuntimeSkillRef reference =
                new AgentWorkflowSkillRefService.RuntimeSkillRef(
                        "crm-analysis",
                        "CRM 经营分析",
                        16L,
                        29L,
                        3,
                        "crm-standard",
                        2,
                        "PINNED_VERSION",
                        "prompt",
                        List.of("crm_product_sales_rank"),
                        List.of("8", "9"),
                        "handoff",
                        "output",
                        "MEDIUM");

        AgentWorkflowRuntimeService.RuntimeSkillGovernanceView view =
                AgentWorkflowRuntimeService.toRuntimeSkillGovernanceView(reference);

        assertThat(view.skillCode()).isEqualTo("crm-analysis");
        assertThat(view.skillName()).isEqualTo("CRM 经营分析");
        assertThat(view.skillVersionNo()).isEqualTo(3);
        assertThat(view.referenceMode()).isEqualTo("PINNED_VERSION");
        assertThat(view.riskLevel()).isEqualTo("MEDIUM");
        assertThat(view.toolCount()).isEqualTo(1);
        assertThat(view.kbCount()).isEqualTo(2);
    }

    @Test
    void shouldResolveDebugKnowledgeFromAgentDirectAndPinnedBoundariesOnly() {
        AgentCapabilityResolverService.AgentCapabilityResolution capability =
                new AgentCapabilityResolverService.AgentCapabilityResolution(
                        "agent-a",
                        List.of("mutable-skill"),
                        List.of("mutable-tool"),
                        List.of(999L),
                        List.of("mutable-handoff"),
                        "mutable-output",
                        List.of(),
                        null,
                        null,
                        List.of("agent-tool"),
                        List.of(7L),
                        List.of("agent-handoff"),
                        List.of("mutable-tool"),
                        List.of("mutable-tool"));
        AgentWorkflowSkillRefService.RuntimeSkillRef pinned =
                new AgentWorkflowSkillRefService.RuntimeSkillRef(
                        "pinned-skill",
                        "Pinned Skill",
                        16L,
                        29L,
                        3,
                        null,
                        null,
                        "PINNED_VERSION",
                        "prompt",
                        List.of(),
                        List.of("8"),
                        "handoff",
                        "output",
                        "LOW");

        assertThat(AgentWorkflowRuntimeService.resolvePinnedKnowledgeBaseIds(capability, List.of(pinned)))
                .containsExactly("7", "8")
                .doesNotContain("999");
    }
}
