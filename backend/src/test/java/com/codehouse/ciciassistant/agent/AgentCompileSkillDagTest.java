package com.codehouse.ciciassistant.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionRepository;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionRepository;
import com.codehouse.ciciassistant.agent.service.AgentCapabilityResolverService;
import com.codehouse.ciciassistant.agent.service.AgentCompileService;
import com.codehouse.ciciassistant.agent.service.AgentDefinitionService;
import com.codehouse.ciciassistant.agent.service.AgentWorkflowSkillRefService;
import com.codehouse.ciciassistant.kb.domain.KnowledgeBaseRepository;
import com.codehouse.ciciassistant.mcp.service.ApplicationMcpBindingService;
import com.codehouse.ciciassistant.skill.domain.AgentSkillBindingRepository;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionRepository;
import com.codehouse.ciciassistant.skill.domain.SkillVersionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillVersionRepository;
import com.codehouse.ciciassistant.spec.SpecCompilerService;
import com.codehouse.ciciassistant.tool.domain.ToolDefinitionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AgentCompileSkillDagTest {

    private static final String COMPANY_ID = "demo-org";
    private static final String AGENT_ID = "sales-agent";

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;
    @Mock
    private ToolDefinitionRepository toolDefinitionRepository;
    @Mock
    private ApplicationMcpBindingService applicationMcpBindings;
    @Mock
    private AgentDefinitionService agentDefinitionService;
    @Mock
    private AgentCapabilityResolverService capabilityResolverService;
    @Mock
    private AgentDefinitionRepository agentDefinitionRepository;
    @Mock
    private AgentWorkflowVersionRepository workflowVersionRepository;
    @Mock
    private AgentSkillBindingRepository agentSkillBindingRepository;
    @Mock
    private SkillDefinitionRepository skillDefinitionRepository;
    @Mock
    private SkillVersionRepository skillVersionRepository;
    @Mock
    private AgentWorkflowSkillRefService workflowSkillRefService;

    private AgentCompileService service;

    @BeforeEach
    void setUp() {
        service = new AgentCompileService(
                knowledgeBaseRepository,
                toolDefinitionRepository,
                applicationMcpBindings,
                agentDefinitionService,
                capabilityResolverService,
                agentDefinitionRepository,
                workflowVersionRepository,
                agentSkillBindingRepository,
                skillDefinitionRepository,
                skillVersionRepository,
                new SpecCompilerService(),
                workflowSkillRefService,
                new ObjectMapper());
    }

    @Test
    void compileShouldPersistSkillSnapshotForNewAndUnchangedDraftVersion() {
        AgentDefinitionEntity agent = org.mockito.Mockito.mock(AgentDefinitionEntity.class);
        SkillDefinitionEntity skill = org.mockito.Mockito.mock(SkillDefinitionEntity.class);
        SkillVersionEntity skillVersion = org.mockito.Mockito.mock(SkillVersionEntity.class);
        when(skill.getId()).thenReturn(16L);
        when(skill.getSkillCode()).thenReturn("crm-business-analysis");
        when(skillVersion.getVersionNo()).thenReturn(3);
        when(skillDefinitionRepository.findByCompanyIdAndSkillCode(COMPANY_ID, "crm-business-analysis"))
                .thenReturn(Optional.of(skill));
        when(skillVersionRepository.findTopByCompanyIdAndSkillIdOrderByVersionNoDesc(COMPANY_ID, 16L))
                .thenReturn(Optional.of(skillVersion));
        when(capabilityResolverService.resolve(COMPANY_ID, AGENT_ID, List.of("crm-business-analysis")))
                .thenReturn(new AgentCapabilityResolverService.AgentCapabilityResolution(
                        AGENT_ID,
                        List.of("crm-business-analysis"),
                        List.of("skill-only-tool"),
                        List.of(),
                        List.of(),
                        null,
                        List.of(),
                        null,
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of("skill-only-tool"),
                        List.of("skill-only-tool")));
        when(agentDefinitionRepository.findByCompanyIdAndAgentId(COMPANY_ID, AGENT_ID)).thenReturn(Optional.of(agent));
        AtomicReference<AgentWorkflowVersionEntity> storedVersion = new AtomicReference<>();
        when(workflowVersionRepository.findTopByCompanyIdAndAgentIdOrderByVersionNoDesc(COMPANY_ID, AGENT_ID))
                .thenAnswer(invocation -> Optional.ofNullable(storedVersion.get()));
        when(workflowVersionRepository.save(any(AgentWorkflowVersionEntity.class))).thenAnswer(invocation -> {
            AgentWorkflowVersionEntity saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 101L);
            storedVersion.set(saved);
            return saved;
        });

        AgentCompileService.CompileResult result = service.compile(COMPANY_ID, command());
        AgentCompileService.CompileResult unchanged = service.compile(COMPANY_ID, command());

        assertThat(result.draftVersionNo()).isEqualTo(1);
        assertThat(unchanged.draftVersionNo()).isEqualTo(1);
        assertThat(unchanged.changed()).isFalse();
        Object generatedFromRaw = result.workflowManifest().get("generatedFrom");
        assertThat(generatedFromRaw).isInstanceOf(java.util.Map.class);
        java.util.Map<?, ?> generatedFrom = (java.util.Map<?, ?>) generatedFromRaw;
        SpecCompilerService.SpecIr specIr = (SpecCompilerService.SpecIr) generatedFrom.get("specIr");
        assertThat(specIr).isNotNull();
        assertThat(specIr.toolRefs()).containsExactly("skill-only-tool");
        assertThat(result.workflowCode()).contains("skill-only-tool");
        ArgumentCaptor<AgentWorkflowVersionEntity> versionCaptor = ArgumentCaptor.forClass(AgentWorkflowVersionEntity.class);
        verify(workflowSkillRefService, org.mockito.Mockito.times(2)).ensureWorkflowSkillRefs(
                org.mockito.ArgumentMatchers.eq(COMPANY_ID),
                org.mockito.ArgumentMatchers.eq(AGENT_ID),
                versionCaptor.capture());
        assertThat(versionCaptor.getAllValues())
                .allSatisfy(version -> {
                    assertThat(version.getId()).isEqualTo(101L);
                    assertThat(version.getWorkflowManifest()).contains("crm-business-analysis");
                });
    }

    @Test
    void compileShouldCreateNewWorkflowVersionWhenResolvedSkillVersionChanges() {
        AgentDefinitionEntity agent = org.mockito.Mockito.mock(AgentDefinitionEntity.class);
        SkillDefinitionEntity skill = org.mockito.Mockito.mock(SkillDefinitionEntity.class);
        SkillVersionEntity versionThree = org.mockito.Mockito.mock(SkillVersionEntity.class);
        SkillVersionEntity versionFour = org.mockito.Mockito.mock(SkillVersionEntity.class);
        when(skill.getId()).thenReturn(16L);
        when(skill.getSkillCode()).thenReturn("crm-business-analysis");
        when(versionThree.getVersionNo()).thenReturn(3);
        when(versionFour.getVersionNo()).thenReturn(4);
        when(skillDefinitionRepository.findByCompanyIdAndSkillCode(COMPANY_ID, "crm-business-analysis"))
                .thenReturn(Optional.of(skill));
        when(skillVersionRepository.findTopByCompanyIdAndSkillIdOrderByVersionNoDesc(COMPANY_ID, 16L))
                .thenReturn(Optional.of(versionThree), Optional.of(versionFour));
        when(capabilityResolverService.resolve(COMPANY_ID, AGENT_ID, List.of("crm-business-analysis")))
                .thenReturn(new AgentCapabilityResolverService.AgentCapabilityResolution(
                        AGENT_ID,
                        List.of("crm-business-analysis"),
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        List.of(),
                        null,
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()));
        when(agentDefinitionRepository.findByCompanyIdAndAgentId(COMPANY_ID, AGENT_ID)).thenReturn(Optional.of(agent));
        AtomicReference<AgentWorkflowVersionEntity> storedVersion = new AtomicReference<>();
        when(workflowVersionRepository.findTopByCompanyIdAndAgentIdOrderByVersionNoDesc(COMPANY_ID, AGENT_ID))
                .thenAnswer(invocation -> Optional.ofNullable(storedVersion.get()));
        when(workflowVersionRepository.save(any(AgentWorkflowVersionEntity.class))).thenAnswer(invocation -> {
            AgentWorkflowVersionEntity saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 100L + saved.getVersionNo());
            storedVersion.set(saved);
            return saved;
        });

        AgentCompileService.CompileResult first = service.compile(COMPANY_ID, command());
        AgentCompileService.CompileResult afterSkillPublish = service.compile(COMPANY_ID, command());

        assertThat(first.draftVersionNo()).isEqualTo(1);
        assertThat(afterSkillPublish.draftVersionNo()).isEqualTo(2);
        assertThat(afterSkillPublish.changed()).isTrue();
        assertThat(storedVersion.get().getWorkflowManifest()).contains("\"versionNo\":4");
    }

    private AgentCompileService.CompileCommand command() {
        return new AgentCompileService.CompileCommand(
                AGENT_ID,
                "销售助手",
                "分析销售经营情况",
                "您好",
                "qwen3.6-plus",
                "仅使用授权能力回答。",
                "1. 理解经营分析意图。\n2. 读取授权数据。\n3. 信息不足时转人工。",
                List.of("web"),
                List.of(),
                List.of(),
                List.of("crm-business-analysis"),
                "信息不足时转人工。",
                "MEDIUM",
                "COPILOT",
                "v1");
    }
}
