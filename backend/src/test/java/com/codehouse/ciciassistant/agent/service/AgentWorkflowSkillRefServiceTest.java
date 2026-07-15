package com.codehouse.ciciassistant.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.agent.domain.AgentWorkflowSkillRefEntity;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowSkillRefRepository;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionEntity;
import com.codehouse.ciciassistant.platform.domain.PlatformSkillTemplateRepository;
import com.codehouse.ciciassistant.skill.domain.AgentSkillBindingRepository;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionRepository;
import com.codehouse.ciciassistant.skill.domain.SkillBindingPolicy;
import com.codehouse.ciciassistant.skill.domain.SkillEditPolicy;
import com.codehouse.ciciassistant.skill.domain.SkillSourceType;
import com.codehouse.ciciassistant.skill.domain.SkillUpdatePolicy;
import com.codehouse.ciciassistant.skill.domain.SkillVisibility;
import com.codehouse.ciciassistant.skill.domain.SkillVersionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AgentWorkflowSkillRefServiceTest {

    private static final String ORG_ID = "demo-org";

    @Mock
    private AgentWorkflowSkillRefRepository workflowSkillRefRepository;
    @Mock
    private AgentSkillBindingRepository agentSkillBindingRepository;
    @Mock
    private SkillDefinitionRepository skillDefinitionRepository;
    @Mock
    private SkillVersionRepository skillVersionRepository;
    @Mock
    private PlatformSkillTemplateRepository platformSkillTemplateRepository;

    private AgentWorkflowSkillRefService service;

    @BeforeEach
    void setUp() {
        service = new AgentWorkflowSkillRefService(
                workflowSkillRefRepository,
                agentSkillBindingRepository,
                skillDefinitionRepository,
                skillVersionRepository,
                platformSkillTemplateRepository,
                new ObjectMapper());
    }

    @Test
    void historicalManifestVersionShouldWinOverCurrentPublishedVersion() {
        AgentWorkflowVersionEntity workflowVersion = org.mockito.Mockito.mock(AgentWorkflowVersionEntity.class);
        SkillDefinitionEntity skill = org.mockito.Mockito.mock(SkillDefinitionEntity.class);
        SkillVersionEntity historicalVersion = org.mockito.Mockito.mock(SkillVersionEntity.class);
        when(workflowVersion.getId()).thenReturn(301L);
        when(workflowVersion.getWorkflowManifest()).thenReturn("""
                {"generatedFrom":{"resolvedSkillRefs":[{
                  "skillCode":"crm-business-analysis",
                  "skillId":16,
                  "versionNo":1,
                  "source":"explicit",
                  "resolved":true
                }]}}
                """);
        when(skill.getId()).thenReturn(16L);
        when(skillDefinitionRepository.findByIdAndOrgId(16L, ORG_ID)).thenReturn(Optional.of(skill));
        when(historicalVersion.getId()).thenReturn(201L);
        when(skillVersionRepository.findByOrgIdAndSkillIdAndVersionNo(ORG_ID, 16L, 1))
                .thenReturn(Optional.of(historicalVersion));
        when(agentSkillBindingRepository.findByOrgIdAndAgentIdAndEnabledTrueOrderByPriorityAscIdAsc(
                ORG_ID, "sales-agent")).thenReturn(List.of());

        service.ensureWorkflowSkillRefs(ORG_ID, "sales-agent", workflowVersion);

        ArgumentCaptor<AgentWorkflowSkillRefEntity> captor = ArgumentCaptor.forClass(AgentWorkflowSkillRefEntity.class);
        org.mockito.Mockito.verify(workflowSkillRefRepository).save(captor.capture());
        assertThat(captor.getValue().getSkillVersionId()).isEqualTo(201L);
    }

    @Test
    void missingHistoricalManifestVersionShouldStayMissingAndFailClosedAtRuntime() {
        AgentWorkflowVersionEntity workflowVersion = org.mockito.Mockito.mock(AgentWorkflowVersionEntity.class);
        SkillDefinitionEntity skill = new SkillDefinitionEntity(
                ORG_ID,
                "crm-business-analysis",
                "CRM 经营分析",
                "分析销售数据",
                false,
                true,
                "mutable current prompt",
                "draft",
                "crm.lookup",
                "42",
                "mutable handoff",
                "mutable output",
                "HIGH",
                SkillSourceType.TENANT_CUSTOM,
                SkillVisibility.VISIBLE,
                SkillEditPolicy.EDITABLE,
                SkillBindingPolicy.OPTIONAL,
                SkillUpdatePolicy.MANUAL,
                null,
                null);
        ReflectionTestUtils.setField(skill, "id", 16L);
        skill.setCurrentPublishedVersionId(202L);
        when(workflowVersion.getId()).thenReturn(302L);
        when(workflowVersion.getWorkflowManifest()).thenReturn("""
                {"generatedFrom":{"resolvedSkillRefs":[{
                  "skillCode":"crm-business-analysis",
                  "skillId":16,
                  "versionNo":99,
                  "source":"explicit",
                  "resolved":true
                }]}}
                """);
        when(skillDefinitionRepository.findByIdAndOrgId(16L, ORG_ID)).thenReturn(Optional.of(skill));
        when(skillVersionRepository.findByOrgIdAndSkillIdAndVersionNo(ORG_ID, 16L, 99))
                .thenReturn(Optional.empty());
        when(agentSkillBindingRepository.findByOrgIdAndAgentIdAndEnabledTrueOrderByPriorityAscIdAsc(
                ORG_ID, "sales-agent")).thenReturn(List.of());

        service.ensureWorkflowSkillRefs(ORG_ID, "sales-agent", workflowVersion);

        ArgumentCaptor<AgentWorkflowSkillRefEntity> captor = ArgumentCaptor.forClass(AgentWorkflowSkillRefEntity.class);
        verify(workflowSkillRefRepository).save(captor.capture());
        AgentWorkflowSkillRefEntity savedRef = captor.getValue();
        assertThat(savedRef.getSkillVersionId()).isNull();
        verify(skillVersionRepository, never()).findByIdAndOrgId(202L, ORG_ID);

        when(workflowSkillRefRepository.findByOrgIdAndWorkflowVersionIdOrderByIdAsc(ORG_ID, 302L))
                .thenReturn(List.of(savedRef));
        AgentWorkflowSkillRefService.RuntimeSkillRef runtimeRef = service.listRuntimeSkillRefs(ORG_ID, 302L).getFirst();
        assertThat(runtimeRef.skillVersionNo()).isNull();
        assertThat(runtimeRef.promptFragment()).isEmpty();
        assertThat(runtimeRef.toolWhitelist()).isEmpty();
        assertThat(runtimeRef.kbWhitelist()).isEmpty();
        assertThat(runtimeRef.handoffRule()).isEmpty();
        assertThat(runtimeRef.outputContract()).isEmpty();
    }
}
