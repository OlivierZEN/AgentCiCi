package com.codehouse.ciciassistant.skill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionRepository;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionRepository;
import com.codehouse.ciciassistant.agent.service.AgentCapabilityResolverService;
import com.codehouse.ciciassistant.agent.service.AgentWorkflowSkillRefService;
import com.codehouse.ciciassistant.ai.service.ChatSessionStateService;
import com.codehouse.ciciassistant.platform.service.PlatformGovernanceService;
import com.codehouse.ciciassistant.skill.domain.AgentSkillBindingRepository;
import com.codehouse.ciciassistant.skill.domain.SkillVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SkillResolverPinnedRuntimeBoundaryTest {

    @Test
    void missingPinnedVersionMustNotRecoverMutableSkillKnowledgeOrHandoffBoundaries() {
        SkillDefinitionService skillDefinitionService = mock(SkillDefinitionService.class);
        AgentDefinitionRepository agentDefinitionRepository = mock(AgentDefinitionRepository.class);
        AgentWorkflowVersionRepository workflowVersionRepository = mock(AgentWorkflowVersionRepository.class);
        AgentCapabilityResolverService capabilityResolverService = mock(AgentCapabilityResolverService.class);
        AgentSkillBindingRepository skillBindingRepository = mock(AgentSkillBindingRepository.class);
        ChatSessionStateService sessionStateService = mock(ChatSessionStateService.class);
        PlatformGovernanceService platformGovernanceService = mock(PlatformGovernanceService.class);
        AgentWorkflowSkillRefService workflowSkillRefService = mock(AgentWorkflowSkillRefService.class);
        SkillVersionRepository skillVersionRepository = mock(SkillVersionRepository.class);
        SkillApiToolService skillApiToolService = mock(SkillApiToolService.class);

        SkillResolverService service = new SkillResolverService(
                skillDefinitionService,
                agentDefinitionRepository,
                workflowVersionRepository,
                capabilityResolverService,
                skillBindingRepository,
                sessionStateService,
                platformGovernanceService,
                workflowSkillRefService,
                skillVersionRepository,
                new ObjectMapper(),
                skillApiToolService);

        AgentCapabilityResolverService.AgentCapabilityResolution mutableCapability =
                new AgentCapabilityResolverService.AgentCapabilityResolution(
                        "agent-a",
                        List.of("mutable-skill"),
                        List.of("mutable-tool"),
                        List.of(999L),
                        List.of("mutable-skill-handoff"),
                        "mutable-output",
                        List.of(),
                        null,
                        null,
                        List.of("agent-tool"),
                        List.of(),
                        List.of(),
                        List.of("mutable-tool"),
                        List.of("mutable-tool"));
        AgentWorkflowSkillRefService.RuntimeSkillRef missingVersion =
                new AgentWorkflowSkillRefService.RuntimeSkillRef(
                        "missing-skill",
                        "Missing Skill",
                        14L,
                        null,
                        null,
                        null,
                        null,
                        "MISSING_VERSION",
                        "",
                        List.of(),
                        List.of(),
                        "",
                        "",
                        null);
        AgentDefinitionEntity definition = mock(AgentDefinitionEntity.class);
        AgentWorkflowVersionEntity publishedVersion = mock(AgentWorkflowVersionEntity.class);

        when(skillDefinitionService.normalizeAgentId("agent-a")).thenReturn("agent-a");
        when(capabilityResolverService.resolve("org-a", "agent-a", List.of())).thenReturn(mutableCapability);
        when(definition.getPublishedVersionId()).thenReturn(42L);
        when(agentDefinitionRepository.findByOrgIdAndAgentId("org-a", "agent-a"))
                .thenReturn(Optional.of(definition));
        when(workflowVersionRepository.findById(42L)).thenReturn(Optional.of(publishedVersion));
        when(publishedVersion.getId()).thenReturn(42L);
        when(publishedVersion.getOrgId()).thenReturn("org-a");
        when(publishedVersion.getAgentId()).thenReturn("agent-a");
        when(publishedVersion.getPublishStatus()).thenReturn("PUBLISHED");
        when(publishedVersion.getWorkflowManifest()).thenReturn("""
                {
                  "dependencies": {
                    "skills": ["missing-skill"],
                    "tools": ["manifest-tool"],
                    "knowledgeBases": ["8"]
                  },
                  "policies": {
                    "handoffRule": "manifest-handoff",
                    "maxToolCalls": 3
                  }
                }
                """);
        when(workflowSkillRefService.listRuntimeSkillRefs("org-a", 42L)).thenReturn(List.of(missingVersion));
        when(skillBindingRepository.findByOrgIdAndAgentIdAndEnabledTrueOrderByPriorityAscIdAsc("org-a", "agent-a"))
                .thenReturn(List.of());
        when(sessionStateService.mergeAndGetActiveSkillCode(
                anyString(), anyString(), anyString(), any(), anyList())).thenReturn(Optional.empty());
        when(platformGovernanceService.resolvePublishedPolicyBundle("org-a"))
                .thenReturn(PlatformGovernanceService.RuntimePolicyBundle.EMPTY);
        when(platformGovernanceService.filterRuntimeAllowedToolNames(anyString(), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(skillApiToolService.findRuntimeTools(anyString(), any())).thenReturn(List.of());

        SkillResolverService.ResolvedSkillContext context = service.resolve(
                "org-a", "agent-a", "session-a");

        assertThat(context.defaultKnowledgeBaseIds()).containsExactly("8");
        assertThat(context.handoffRules()).containsExactly("manifest-handoff");
        assertThat(context.outputContract()).isNull();
        assertThat(context.skills()).singleElement().satisfies(skill -> {
            assertThat(skill.toolWhitelist()).isEmpty();
            assertThat(skill.kbWhitelist()).isEmpty();
            assertThat(skill.handoffRule()).isEmpty();
            assertThat(skill.outputContract()).isEmpty();
        });
    }
}
