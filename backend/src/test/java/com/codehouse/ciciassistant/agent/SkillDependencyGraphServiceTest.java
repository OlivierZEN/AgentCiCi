package com.codehouse.ciciassistant.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionRepository;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowSkillRefEntity;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowSkillRefRepository;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionRepository;
import com.codehouse.ciciassistant.agent.service.SkillDependencyGraphService;
import com.codehouse.ciciassistant.kb.domain.KnowledgeBaseEntity;
import com.codehouse.ciciassistant.kb.domain.KnowledgeBaseRepository;
import com.codehouse.ciciassistant.skill.domain.AgentSkillBindingRepository;
import com.codehouse.ciciassistant.skill.domain.AgentSkillBindingEntity;
import com.codehouse.ciciassistant.skill.domain.SkillBindingPolicy;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionRepository;
import com.codehouse.ciciassistant.skill.domain.SkillEditPolicy;
import com.codehouse.ciciassistant.skill.domain.SkillSourceType;
import com.codehouse.ciciassistant.skill.domain.SkillUpdatePolicy;
import com.codehouse.ciciassistant.skill.domain.SkillVersionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillVersionRepository;
import com.codehouse.ciciassistant.skill.domain.SkillVisibility;
import com.codehouse.ciciassistant.tool.domain.ToolDefinitionEntity;
import com.codehouse.ciciassistant.tool.domain.ToolDefinitionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SkillDependencyGraphServiceTest {

    private static final String ORG_ID = "demo-org";
    private static final String AGENT_ID = "sales-agent";
    private static final long WORKFLOW_VERSION_ID = 101L;
    private static final long SKILL_ID = 201L;
    private static final long SKILL_VERSION_ID = 301L;
    private static final long KNOWLEDGE_BASE_ID = 401L;

    @Mock
    private AgentDefinitionRepository agentDefinitionRepository;
    @Mock
    private AgentWorkflowVersionRepository workflowVersionRepository;
    @Mock
    private AgentWorkflowSkillRefRepository workflowSkillRefRepository;
    @Mock
    private AgentSkillBindingRepository agentSkillBindingRepository;
    @Mock
    private SkillDefinitionRepository skillDefinitionRepository;
    @Mock
    private SkillVersionRepository skillVersionRepository;
    @Mock
    private ToolDefinitionRepository toolDefinitionRepository;
    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    private SkillDependencyGraphService service;

    @BeforeEach
    void setUp() {
        service = new SkillDependencyGraphService(
                agentDefinitionRepository,
                workflowVersionRepository,
                workflowSkillRefRepository,
                agentSkillBindingRepository,
                skillDefinitionRepository,
                skillVersionRepository,
                toolDefinitionRepository,
                knowledgeBaseRepository,
                new ObjectMapper());
    }

    @Test
    void shouldBuildPinnedGraphForExplicitWorkflowVersion() {
        AgentDefinitionEntity agent = agent();
        AgentWorkflowVersionEntity workflowVersion = workflowVersion();
        SkillDefinitionEntity skill = skill();
        SkillVersionEntity skillVersion = skillVersion();
        AgentWorkflowSkillRefEntity reference = new AgentWorkflowSkillRefEntity(
                ORG_ID,
                WORKFLOW_VERSION_ID,
                SKILL_ID,
                SKILL_VERSION_ID,
                "crm-analysis",
                1,
                "PINNED_VERSION");
        ToolDefinitionEntity tool = new ToolDefinitionEntity(
                ORG_ID, "crm.lookup", "查询 CRM 客户", "LOW", true);
        KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity(
                ORG_ID, "销售知识库", "销售问答资料");
        ReflectionTestUtils.setField(knowledgeBase, "id", KNOWLEDGE_BASE_ID);

        when(agentDefinitionRepository.findByOrgIdAndAgentId(ORG_ID, AGENT_ID)).thenReturn(Optional.of(agent));
        when(workflowVersionRepository.findByOrgIdAndAgentIdAndVersionNo(ORG_ID, AGENT_ID, 1))
                .thenReturn(Optional.of(workflowVersion));
        when(workflowSkillRefRepository.findByOrgIdAndWorkflowVersionIdOrderByIdAsc(ORG_ID, WORKFLOW_VERSION_ID))
                .thenReturn(List.of(reference));
        when(skillDefinitionRepository.findByIdAndOrgId(SKILL_ID, ORG_ID)).thenReturn(Optional.of(skill));
        when(skillVersionRepository.findByIdAndOrgId(SKILL_VERSION_ID, ORG_ID)).thenReturn(Optional.of(skillVersion));
        when(toolDefinitionRepository.findByOrgIdAndToolName(ORG_ID, "crm.lookup")).thenReturn(Optional.of(tool));
        when(knowledgeBaseRepository.findByIdAndOrgId(KNOWLEDGE_BASE_ID, ORG_ID))
                .thenReturn(Optional.of(knowledgeBase));

        SkillDependencyGraphService.GraphView graph = service.getAgentGraph(ORG_ID, AGENT_ID, 1);

        assertThat(graph.sourceMode()).isEqualTo("PINNED_WORKFLOW_VERSION");
        assertThat(graph.nodes()).extracting(SkillDependencyGraphService.GraphNode::id)
                .containsExactly(
                        "agent:sales-agent",
                        "workflow-version:101",
                        "skill:201",
                        "skill-version:301",
                        "tool:crm.lookup",
                        "knowledge-base:401");
        assertThat(graph.edges()).extracting(SkillDependencyGraphService.GraphEdge::type)
                .containsExactly(
                        "COMPILED_AS",
                        "USES_SKILL",
                        "PINS_SKILL_VERSION",
                        "VERSION_OF",
                        "ALLOWS_TOOL",
                        "ALLOWS_KNOWLEDGE_BASE");
        assertThat(graph.edges())
                .filteredOn(edge -> "PINS_SKILL_VERSION".equals(edge.type()))
                .singleElement()
                .satisfies(edge -> {
                    assertThat(edge.source()).isEqualTo("workflow-version:101");
                    assertThat(edge.target()).isEqualTo("skill-version:301");
                });
        assertThat(graph.edges())
                .filteredOn(edge -> "VERSION_OF".equals(edge.type()))
                .singleElement()
                .satisfies(edge -> {
                    assertThat(edge.source()).isEqualTo("skill-version:301");
                    assertThat(edge.target()).isEqualTo("skill:201");
                });
        assertThat(graph.summary().skillVersionCount()).isEqualTo(1);
        assertThat(graph.summary().toolCount()).isEqualTo(1);
        assertThat(graph.summary().knowledgeBaseCount()).isEqualTo(1);
        assertThat(graph.warnings()).isEmpty();
    }

    @Test
    void shouldPreferPublishedWorkflowWhenVersionIsOmitted() {
        AgentDefinitionEntity agent = agent();
        AgentWorkflowVersionEntity published = workflowVersion(WORKFLOW_VERSION_ID, 1, "PUBLISHED");
        AgentWorkflowVersionEntity newerDraft = workflowVersion(102L, 2, "DRAFT");

        when(agentDefinitionRepository.findByOrgIdAndAgentId(ORG_ID, AGENT_ID)).thenReturn(Optional.of(agent));
        when(workflowVersionRepository.findByOrgIdAndAgentIdOrderByVersionNoDesc(ORG_ID, AGENT_ID))
                .thenReturn(List.of(newerDraft, published));
        when(workflowSkillRefRepository.findByOrgIdAndWorkflowVersionIdOrderByIdAsc(ORG_ID, WORKFLOW_VERSION_ID))
                .thenReturn(List.of());

        SkillDependencyGraphService.GraphView graph = service.getAgentGraph(ORG_ID, AGENT_ID, null);

        assertThat(graph.scope().workflowVersionId()).isEqualTo(WORKFLOW_VERSION_ID);
        assertThat(graph.scope().versionNo()).isEqualTo(1);
        assertThat(graph.nodes()).extracting(SkillDependencyGraphService.GraphNode::id)
                .containsExactly("agent:sales-agent", "workflow-version:101");
    }

    @Test
    void shouldBuildCurrentBindingGraphWhenAgentHasNoWorkflowVersion() {
        AgentDefinitionEntity agent = agent();
        agent.setPublishedVersionId(null);
        SkillDefinitionEntity skill = skill();
        ReflectionTestUtils.setField(skill, "currentPublishedVersionId", SKILL_VERSION_ID);
        ReflectionTestUtils.setField(skill, "lifecycleStatus", "PUBLISHED");
        SkillVersionEntity skillVersion = skillVersion();
        AgentSkillBindingEntity binding = new AgentSkillBindingEntity(
                ORG_ID,
                AGENT_ID,
                SKILL_ID,
                "ALWAYS",
                "",
                10,
                true);

        when(agentDefinitionRepository.findByOrgIdAndAgentId(ORG_ID, AGENT_ID)).thenReturn(Optional.of(agent));
        when(workflowVersionRepository.findByOrgIdAndAgentIdOrderByVersionNoDesc(ORG_ID, AGENT_ID))
                .thenReturn(List.of());
        when(agentSkillBindingRepository.findByOrgIdAndAgentIdAndEnabledTrueOrderByPriorityAscIdAsc(ORG_ID, AGENT_ID))
                .thenReturn(List.of(binding));
        when(skillDefinitionRepository.findByIdAndOrgId(SKILL_ID, ORG_ID)).thenReturn(Optional.of(skill));
        when(skillVersionRepository.findByIdAndOrgId(SKILL_VERSION_ID, ORG_ID)).thenReturn(Optional.of(skillVersion));
        when(toolDefinitionRepository.findByOrgIdAndToolName(ORG_ID, "crm.lookup"))
                .thenReturn(Optional.empty());
        when(knowledgeBaseRepository.findByIdAndOrgId(KNOWLEDGE_BASE_ID, ORG_ID))
                .thenReturn(Optional.empty());

        SkillDependencyGraphService.GraphView graph = service.getAgentGraph(ORG_ID, AGENT_ID, null);

        assertThat(graph.sourceMode()).isEqualTo("CURRENT_BINDINGS");
        assertThat(graph.scope().workflowVersionId()).isNull();
        assertThat(graph.nodes()).extracting(SkillDependencyGraphService.GraphNode::id)
                .containsExactly(
                        "agent:sales-agent",
                        "skill:201",
                        "skill-version:301",
                        "tool:crm.lookup",
                        "knowledge-base:401");
        assertThat(graph.edges()).extracting(SkillDependencyGraphService.GraphEdge::type)
                .containsExactly(
                        "BINDS_SKILL",
                        "CURRENT_SKILL_VERSION",
                        "ALLOWS_TOOL",
                        "ALLOWS_KNOWLEDGE_BASE");
    }

    @Test
    void shouldFallbackWhenCurrentPublishedVersionBelongsToAnotherSkill() {
        AgentDefinitionEntity agent = agent();
        agent.setPublishedVersionId(null);
        SkillDefinitionEntity skill = skill();
        ReflectionTestUtils.setField(skill, "currentPublishedVersionId", SKILL_VERSION_ID);
        SkillVersionEntity mismatchedVersion = skillVersion();
        ReflectionTestUtils.setField(mismatchedVersion, "skillId", 999L);
        SkillVersionEntity fallbackVersion = skillVersion();
        ReflectionTestUtils.setField(fallbackVersion, "id", 302L);
        AgentSkillBindingEntity binding = new AgentSkillBindingEntity(
                ORG_ID, AGENT_ID, SKILL_ID, "ALWAYS", "", 10, true);

        when(agentDefinitionRepository.findByOrgIdAndAgentId(ORG_ID, AGENT_ID)).thenReturn(Optional.of(agent));
        when(workflowVersionRepository.findByOrgIdAndAgentIdOrderByVersionNoDesc(ORG_ID, AGENT_ID))
                .thenReturn(List.of());
        when(agentSkillBindingRepository.findByOrgIdAndAgentIdAndEnabledTrueOrderByPriorityAscIdAsc(ORG_ID, AGENT_ID))
                .thenReturn(List.of(binding));
        when(skillDefinitionRepository.findByIdAndOrgId(SKILL_ID, ORG_ID)).thenReturn(Optional.of(skill));
        when(skillVersionRepository.findByIdAndOrgId(SKILL_VERSION_ID, ORG_ID))
                .thenReturn(Optional.of(mismatchedVersion));
        when(skillVersionRepository.findTopByOrgIdAndSkillIdAndPublishStatusOrderByVersionNoDesc(
                ORG_ID, SKILL_ID, "PUBLISHED"))
                .thenReturn(Optional.of(fallbackVersion));
        when(toolDefinitionRepository.findByOrgIdAndToolName(ORG_ID, "crm.lookup"))
                .thenReturn(Optional.empty());
        when(knowledgeBaseRepository.findByIdAndOrgId(KNOWLEDGE_BASE_ID, ORG_ID))
                .thenReturn(Optional.empty());

        SkillDependencyGraphService.GraphView graph = service.getAgentGraph(ORG_ID, AGENT_ID, null);

        assertThat(graph.nodes()).extracting(SkillDependencyGraphService.GraphNode::id)
                .contains("skill-version:302")
                .doesNotContain("skill-version:301");
        assertThat(graph.warnings()).containsExactly(
                "Skill crm-analysis 的当前发布版本 301 不属于该 Skill，已回退。");
    }

    @Test
    void shouldBuildSkillImpactGraphFromPinnedVersionsAndCurrentBindings() {
        SkillDefinitionEntity skill = skill();
        SkillVersionEntity skillVersion = skillVersion();
        AgentWorkflowVersionEntity workflowVersion = workflowVersion();
        AgentWorkflowSkillRefEntity reference = new AgentWorkflowSkillRefEntity(
                ORG_ID,
                WORKFLOW_VERSION_ID,
                SKILL_ID,
                SKILL_VERSION_ID,
                "crm-analysis",
                1,
                "PINNED_VERSION");
        AgentSkillBindingEntity currentBinding = new AgentSkillBindingEntity(
                ORG_ID,
                "draft-agent",
                SKILL_ID,
                "INTENT",
                "客户分析",
                20,
                true);
        AgentDefinitionEntity draftAgent = agent("draft-agent", "草稿助手");

        when(skillDefinitionRepository.findByIdAndOrgId(SKILL_ID, ORG_ID)).thenReturn(Optional.of(skill));
        when(workflowSkillRefRepository.findTop1001ByOrgIdAndSkillIdOrderBySkillVersionIdAscWorkflowVersionIdAsc(
                ORG_ID, SKILL_ID)).thenReturn(List.of(reference));
        when(workflowVersionRepository.findByOrgIdAndIdIn(ORG_ID, List.of(WORKFLOW_VERSION_ID)))
                .thenReturn(List.of(workflowVersion));
        when(skillVersionRepository.findByOrgIdAndIdIn(ORG_ID, List.of(SKILL_VERSION_ID)))
                .thenReturn(List.of(skillVersion));
        when(agentSkillBindingRepository.findTop1001ByOrgIdAndSkillIdAndEnabledTrueOrderByAgentIdAscPriorityAsc(
                ORG_ID, SKILL_ID)).thenReturn(List.of(currentBinding));
        when(agentDefinitionRepository.findByOrgIdAndAgentIdIn(
                ORG_ID, List.of("draft-agent", AGENT_ID)))
                .thenReturn(List.of(draftAgent, agent()));

        SkillDependencyGraphService.GraphView graph = service.getSkillImpactGraph(ORG_ID, SKILL_ID);

        assertThat(graph.scope().type()).isEqualTo("SKILL_IMPACT");
        assertThat(graph.sourceMode()).isEqualTo("SKILL_IMPACT");
        assertThat(graph.nodes()).extracting(SkillDependencyGraphService.GraphNode::id)
                .containsExactly(
                        "skill:201",
                        "skill-version:301",
                        "workflow-version:101",
                        "agent:draft-agent",
                        "agent:sales-agent");
        assertThat(graph.edges()).extracting(SkillDependencyGraphService.GraphEdge::type)
                .containsExactlyInAnyOrder(
                        "VERSION_OF",
                        "PINS_SKILL_VERSION",
                        "USED_BY_AGENT",
                        "BINDS_SKILL");
        assertThat(graph.edges())
                .filteredOn(edge -> "BINDS_SKILL".equals(edge.type()))
                .singleElement()
                .satisfies(edge -> {
                    assertThat(edge.source()).isEqualTo("agent:draft-agent");
                    assertThat(edge.target()).isEqualTo("skill:201");
                });
        assertThat(graph.summary().agentCount()).isEqualTo(2);
        assertThat(graph.summary().workflowVersionCount()).isEqualTo(1);
    }

    @Test
    void shouldCapCurrentAgentBindingsInSkillImpactGraph() {
        List<AgentSkillBindingEntity> bindings = IntStream.rangeClosed(0, 1000)
                .mapToObj(priority -> new AgentSkillBindingEntity(
                        ORG_ID,
                        "draft-agent",
                        SKILL_ID,
                        "INTENT",
                        "客户分析",
                        priority,
                        true))
                .toList();

        when(skillDefinitionRepository.findByIdAndOrgId(SKILL_ID, ORG_ID)).thenReturn(Optional.of(skill()));
        when(agentSkillBindingRepository.findTop1001ByOrgIdAndSkillIdAndEnabledTrueOrderByAgentIdAscPriorityAsc(
                ORG_ID, SKILL_ID)).thenReturn(bindings);
        when(agentDefinitionRepository.findByOrgIdAndAgentIdIn(ORG_ID, List.of("draft-agent")))
                .thenReturn(List.of(agent("draft-agent", "草稿助手")));

        SkillDependencyGraphService.GraphView graph = service.getSkillImpactGraph(ORG_ID, SKILL_ID);

        assertThat(graph.warnings()).contains("Agent 当前绑定超过 1000 条，仅展示前 1000 条。");
        assertThat(graph.edges()).filteredOn(edge -> "BINDS_SKILL".equals(edge.type())).hasSize(1);
    }

    @Test
    void shouldKeepWorkflowAndAgentImpactWhenPinnedVersionIsMissing() {
        AgentWorkflowSkillRefEntity reference = new AgentWorkflowSkillRefEntity(
                ORG_ID,
                WORKFLOW_VERSION_ID,
                SKILL_ID,
                SKILL_VERSION_ID,
                "crm-analysis",
                1,
                "PINNED_VERSION");

        when(skillDefinitionRepository.findByIdAndOrgId(SKILL_ID, ORG_ID)).thenReturn(Optional.of(skill()));
        when(workflowSkillRefRepository.findTop1001ByOrgIdAndSkillIdOrderBySkillVersionIdAscWorkflowVersionIdAsc(
                ORG_ID, SKILL_ID)).thenReturn(List.of(reference));
        when(workflowVersionRepository.findByOrgIdAndIdIn(ORG_ID, List.of(WORKFLOW_VERSION_ID)))
                .thenReturn(List.of(workflowVersion()));
        when(skillVersionRepository.findByOrgIdAndIdIn(ORG_ID, List.of(SKILL_VERSION_ID))).thenReturn(List.of());
        when(agentSkillBindingRepository.findTop1001ByOrgIdAndSkillIdAndEnabledTrueOrderByAgentIdAscPriorityAsc(
                ORG_ID, SKILL_ID)).thenReturn(List.of());
        when(agentDefinitionRepository.findByOrgIdAndAgentIdIn(ORG_ID, List.of(AGENT_ID)))
                .thenReturn(List.of(agent()));

        SkillDependencyGraphService.GraphView graph = service.getSkillImpactGraph(ORG_ID, SKILL_ID);

        assertThat(graph.nodes()).extracting(SkillDependencyGraphService.GraphNode::id)
                .containsExactly(
                        "skill:201",
                        "skill-version:301",
                        "workflow-version:101",
                        "agent:sales-agent");
        assertThat(graph.edges()).extracting(SkillDependencyGraphService.GraphEdge::type)
                .containsExactlyInAnyOrder("VERSION_OF", "PINS_SKILL_VERSION", "USED_BY_AGENT");
        assertThat(graph.warnings()).containsExactly(
                "工作流引用的 Skill Version 301 已不存在。");
    }

    @Test
    void shouldKeepWorkflowAndAgentImpactWhenReferenceHasNoPinnedVersion() {
        AgentWorkflowSkillRefEntity reference = new AgentWorkflowSkillRefEntity(
                ORG_ID,
                WORKFLOW_VERSION_ID,
                SKILL_ID,
                null,
                "crm-analysis",
                1,
                "PINNED_VERSION");

        when(skillDefinitionRepository.findByIdAndOrgId(SKILL_ID, ORG_ID)).thenReturn(Optional.of(skill()));
        when(workflowSkillRefRepository.findTop1001ByOrgIdAndSkillIdOrderBySkillVersionIdAscWorkflowVersionIdAsc(
                ORG_ID, SKILL_ID)).thenReturn(List.of(reference));
        when(workflowVersionRepository.findByOrgIdAndIdIn(ORG_ID, List.of(WORKFLOW_VERSION_ID)))
                .thenReturn(List.of(workflowVersion()));
        when(agentSkillBindingRepository.findTop1001ByOrgIdAndSkillIdAndEnabledTrueOrderByAgentIdAscPriorityAsc(
                ORG_ID, SKILL_ID)).thenReturn(List.of());
        when(agentDefinitionRepository.findByOrgIdAndAgentIdIn(ORG_ID, List.of(AGENT_ID)))
                .thenReturn(List.of(agent()));

        SkillDependencyGraphService.GraphView graph = service.getSkillImpactGraph(ORG_ID, SKILL_ID);

        assertThat(graph.nodes()).extracting(SkillDependencyGraphService.GraphNode::id)
                .containsExactly("skill:201", "workflow-version:101", "agent:sales-agent");
        assertThat(graph.edges()).extracting(SkillDependencyGraphService.GraphEdge::type)
                .containsExactlyInAnyOrder("USES_SKILL", "USED_BY_AGENT");
        assertThat(graph.warnings()).containsExactly(
                "工作流 101 引用了 Skill，但没有钉住版本。");
    }

    @Test
    void shouldIgnorePinnedVersionThatBelongsToAnotherSkill() {
        AgentWorkflowSkillRefEntity reference = new AgentWorkflowSkillRefEntity(
                ORG_ID,
                WORKFLOW_VERSION_ID,
                SKILL_ID,
                SKILL_VERSION_ID,
                "crm-analysis",
                1,
                "PINNED_VERSION");
        SkillVersionEntity mismatchedVersion = skillVersion();
        ReflectionTestUtils.setField(mismatchedVersion, "skillId", 999L);

        when(agentDefinitionRepository.findByOrgIdAndAgentId(ORG_ID, AGENT_ID)).thenReturn(Optional.of(agent()));
        when(workflowVersionRepository.findByOrgIdAndAgentIdAndVersionNo(ORG_ID, AGENT_ID, 1))
                .thenReturn(Optional.of(workflowVersion()));
        when(workflowSkillRefRepository.findByOrgIdAndWorkflowVersionIdOrderByIdAsc(ORG_ID, WORKFLOW_VERSION_ID))
                .thenReturn(List.of(reference));
        when(skillDefinitionRepository.findByIdAndOrgId(SKILL_ID, ORG_ID)).thenReturn(Optional.of(skill()));
        when(skillVersionRepository.findByIdAndOrgId(SKILL_VERSION_ID, ORG_ID))
                .thenReturn(Optional.of(mismatchedVersion));

        SkillDependencyGraphService.GraphView graph = service.getAgentGraph(ORG_ID, AGENT_ID, 1);

        assertThat(graph.nodes()).extracting(SkillDependencyGraphService.GraphNode::id)
                .containsExactly("agent:sales-agent", "workflow-version:101", "skill:201");
        assertThat(graph.warnings()).containsExactly(
                "工作流引用的 Skill Version 301 不属于 Skill 201，已忽略。");
    }

    private AgentDefinitionEntity agent() {
        AgentDefinitionEntity entity = agent(AGENT_ID, "销售助手");
        entity.setPublishedVersionId(WORKFLOW_VERSION_ID);
        return entity;
    }

    private AgentDefinitionEntity agent(String agentId, String name) {
        AgentDefinitionEntity entity = new AgentDefinitionEntity(
                ORG_ID,
                agentId,
                name,
                "",
                "",
                "qwen",
                "system",
                "",
                "BALANCED",
                "COPILOT",
                "v1",
                null,
                false,
                true);
        ReflectionTestUtils.setField(entity, "id", 11L);
        return entity;
    }

    private AgentWorkflowVersionEntity workflowVersion() {
        return workflowVersion(WORKFLOW_VERSION_ID, 1, "PUBLISHED");
    }

    private AgentWorkflowVersionEntity workflowVersion(long id, int versionNo, String publishStatus) {
        AgentWorkflowVersionEntity entity = new AgentWorkflowVersionEntity(
                ORG_ID,
                AGENT_ID,
                versionNo,
                "v" + versionNo,
                "spec",
                "workflow",
                "{}",
                "{}",
                "[]",
                "[]",
                "[]",
                "fingerprint",
                "initial",
                publishStatus);
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private SkillDefinitionEntity skill() {
        SkillDefinitionEntity entity = new SkillDefinitionEntity(
                ORG_ID,
                "crm-analysis",
                "CRM 经营分析",
                "分析销售数据",
                false,
                true,
                "",
                "",
                "crm.lookup",
                "401",
                "",
                "",
                "MEDIUM",
                SkillSourceType.TENANT_CUSTOM,
                SkillVisibility.VISIBLE,
                SkillEditPolicy.EDITABLE,
                SkillBindingPolicy.OPTIONAL,
                SkillUpdatePolicy.MANUAL,
                null,
                null);
        ReflectionTestUtils.setField(entity, "id", SKILL_ID);
        return entity;
    }

    private SkillVersionEntity skillVersion() {
        SkillVersionEntity entity = new SkillVersionEntity(
                ORG_ID,
                SKILL_ID,
                1,
                "spec",
                "DECLARATIVE",
                "TENANT_CUSTOM",
                "{}",
                "",
                "prompt",
                "{}",
                "crm.lookup",
                "401",
                "MEDIUM",
                "[]",
                "[]",
                "PUBLISHED");
        ReflectionTestUtils.setField(entity, "id", SKILL_VERSION_ID);
        return entity;
    }
}
