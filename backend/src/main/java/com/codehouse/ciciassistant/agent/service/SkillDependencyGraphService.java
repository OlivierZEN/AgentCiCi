package com.codehouse.ciciassistant.agent.service;

import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionRepository;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowSkillRefEntity;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowSkillRefRepository;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionRepository;
import com.codehouse.ciciassistant.common.error.ResourceNotFoundException;
import com.codehouse.ciciassistant.kb.domain.KnowledgeBaseEntity;
import com.codehouse.ciciassistant.kb.domain.KnowledgeBaseRepository;
import com.codehouse.ciciassistant.skill.domain.AgentSkillBindingRepository;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionRepository;
import com.codehouse.ciciassistant.skill.domain.SkillVersionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillVersionRepository;
import com.codehouse.ciciassistant.tool.domain.ToolDefinitionEntity;
import com.codehouse.ciciassistant.tool.domain.ToolDefinitionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SkillDependencyGraphService {

    private static final int MAX_IMPACT_REFERENCES = 1000;

    private static final Map<String, Integer> NODE_TYPE_ORDER = Map.of(
            "AGENT", 0,
            "WORKFLOW_VERSION", 1,
            "SKILL", 2,
            "SKILL_VERSION", 3,
            "TOOL", 4,
            "KNOWLEDGE_BASE", 5);

    private final AgentDefinitionRepository agentDefinitionRepository;
    private final AgentWorkflowVersionRepository workflowVersionRepository;
    private final AgentWorkflowSkillRefRepository workflowSkillRefRepository;
    private final AgentSkillBindingRepository agentSkillBindingRepository;
    private final SkillDefinitionRepository skillDefinitionRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final ToolDefinitionRepository toolDefinitionRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final ObjectMapper objectMapper;

    public SkillDependencyGraphService(AgentDefinitionRepository agentDefinitionRepository,
                                       AgentWorkflowVersionRepository workflowVersionRepository,
                                       AgentWorkflowSkillRefRepository workflowSkillRefRepository,
                                       AgentSkillBindingRepository agentSkillBindingRepository,
                                       SkillDefinitionRepository skillDefinitionRepository,
                                       SkillVersionRepository skillVersionRepository,
                                       ToolDefinitionRepository toolDefinitionRepository,
                                       KnowledgeBaseRepository knowledgeBaseRepository,
                                       ObjectMapper objectMapper) {
        this.agentDefinitionRepository = agentDefinitionRepository;
        this.workflowVersionRepository = workflowVersionRepository;
        this.workflowSkillRefRepository = workflowSkillRefRepository;
        this.agentSkillBindingRepository = agentSkillBindingRepository;
        this.skillDefinitionRepository = skillDefinitionRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.toolDefinitionRepository = toolDefinitionRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.objectMapper = objectMapper;
    }

    public GraphView getAgentGraph(String companyId, String agentId, Integer versionNo) {
        AgentDefinitionEntity agent = agentDefinitionRepository.findByCompanyIdAndAgentId(companyId, agentId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent not found"));
        AgentWorkflowVersionEntity workflowVersion = selectWorkflowVersion(companyId, agent, versionNo);

        GraphBuilder graph = new GraphBuilder();
        graph.addNode(agentNode(agent));
        if (workflowVersion == null) {
            addCurrentBindings(companyId, agent, graph);
            return graph.build(new GraphScope(
                            "AGENT_BINDINGS",
                            agent.getAgentId(),
                            agent.getName(),
                            null,
                            null,
                            "DRAFT"),
                    "CURRENT_BINDINGS");
        }
        graph.addNode(workflowNode(workflowVersion));
        graph.addEdge(edge(
                "agent:" + agent.getAgentId(),
                "workflow-version:" + workflowVersion.getId(),
                "COMPILED_AS",
                "编译为"));

        List<AgentWorkflowSkillRefEntity> references = workflowSkillRefRepository
                .findByCompanyIdAndWorkflowVersionIdOrderByIdAsc(companyId, workflowVersion.getId());
        for (AgentWorkflowSkillRefEntity reference : references) {
            addPinnedSkill(companyId, workflowVersion, reference, graph);
        }

        return graph.build(new GraphScope(
                        "AGENT_WORKFLOW",
                        agent.getAgentId(),
                        agent.getName(),
                        workflowVersion.getId(),
                        workflowVersion.getVersionNo(),
                        safe(workflowVersion.getPublishStatus())),
                "PINNED_WORKFLOW_VERSION");
    }

    public GraphView getSkillImpactGraph(String companyId, Long skillId) {
        SkillDefinitionEntity skill = skillDefinitionRepository.findByIdAndCompanyId(skillId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Skill not found"));
        GraphBuilder graph = new GraphBuilder();
        graph.addNode(impactSkillNode(skill));

        List<AgentWorkflowSkillRefEntity> fetchedReferences = workflowSkillRefRepository
                .findTop1001ByCompanyIdAndSkillIdOrderBySkillVersionIdAscWorkflowVersionIdAsc(companyId, skillId);
        List<AgentWorkflowSkillRefEntity> references;
        if (fetchedReferences.size() > MAX_IMPACT_REFERENCES) {
            references = fetchedReferences.subList(0, MAX_IMPACT_REFERENCES);
            graph.addWarning("影响引用超过 " + MAX_IMPACT_REFERENCES + " 条，仅展示前 "
                    + MAX_IMPACT_REFERENCES + " 条。");
        } else {
            references = fetchedReferences;
        }
        List<Long> workflowVersionIds = references.stream()
                .map(AgentWorkflowSkillRefEntity::getWorkflowVersionId)
                .distinct()
                .sorted()
                .toList();
        Map<Long, AgentWorkflowVersionEntity> workflowVersions = workflowVersionIds.isEmpty()
                ? Map.of()
                : workflowVersionRepository.findByCompanyIdAndIdIn(companyId, workflowVersionIds).stream()
                .collect(Collectors.toMap(AgentWorkflowVersionEntity::getId, Function.identity()));
        List<Long> skillVersionIds = references.stream()
                .map(AgentWorkflowSkillRefEntity::getSkillVersionId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        Map<Long, SkillVersionEntity> skillVersions = skillVersionIds.isEmpty()
                ? Map.of()
                : skillVersionRepository.findByCompanyIdAndIdIn(companyId, skillVersionIds).stream()
                .collect(Collectors.toMap(SkillVersionEntity::getId, Function.identity()));

        List<com.codehouse.ciciassistant.skill.domain.AgentSkillBindingEntity> fetchedBindings =
                agentSkillBindingRepository.findTop1001ByCompanyIdAndSkillIdAndEnabledTrueOrderByAgentIdAscPriorityAsc(
                        companyId, skillId);
        List<com.codehouse.ciciassistant.skill.domain.AgentSkillBindingEntity> bindings;
        if (fetchedBindings.size() > MAX_IMPACT_REFERENCES) {
            bindings = fetchedBindings.subList(0, MAX_IMPACT_REFERENCES);
            graph.addWarning("Agent 当前绑定超过 " + MAX_IMPACT_REFERENCES + " 条，仅展示前 "
                    + MAX_IMPACT_REFERENCES + " 条。");
        } else {
            bindings = fetchedBindings;
        }
        Set<String> relatedAgentIds = new LinkedHashSet<>();
        workflowVersions.values().stream()
                .map(AgentWorkflowVersionEntity::getAgentId)
                .sorted()
                .forEach(relatedAgentIds::add);
        bindings.stream()
                .map(com.codehouse.ciciassistant.skill.domain.AgentSkillBindingEntity::getAgentId)
                .sorted()
                .forEach(relatedAgentIds::add);
        List<String> sortedAgentIds = relatedAgentIds.stream().sorted().toList();
        Map<String, AgentDefinitionEntity> agents = sortedAgentIds.isEmpty()
                ? Map.of()
                : agentDefinitionRepository.findByCompanyIdAndAgentIdIn(companyId, sortedAgentIds).stream()
                .collect(Collectors.toMap(AgentDefinitionEntity::getAgentId, Function.identity()));

        for (AgentWorkflowSkillRefEntity reference : references) {
            addImpactReference(skill, reference, workflowVersions, skillVersions, agents, graph);
        }
        for (com.codehouse.ciciassistant.skill.domain.AgentSkillBindingEntity binding : bindings) {
            AgentDefinitionEntity agent = agents.get(binding.getAgentId());
            if (agent == null) {
                graph.addWarning("Skill 当前绑定的 Agent " + binding.getAgentId() + " 已不存在。");
                continue;
            }
            graph.addNode(agentNode(agent, 3));
            graph.addEdge(edge(
                    "agent:" + agent.getAgentId(),
                    "skill:" + skill.getId(),
                    "BINDS_SKILL",
                    "当前绑定"));
        }

        return graph.build(new GraphScope(
                        "SKILL_IMPACT",
                        String.valueOf(skill.getId()),
                        skill.getName(),
                        null,
                        null,
                        skill.isEnabled() ? safe(skill.getLifecycleStatus()) : "DISABLED"),
                "SKILL_IMPACT");
    }

    private void addImpactReference(SkillDefinitionEntity skill,
                                    AgentWorkflowSkillRefEntity reference,
                                    Map<Long, AgentWorkflowVersionEntity> workflowVersions,
                                    Map<Long, SkillVersionEntity> skillVersions,
                                    Map<String, AgentDefinitionEntity> agents,
                                    GraphBuilder graph) {
        AgentWorkflowVersionEntity workflowVersion = workflowVersions.get(reference.getWorkflowVersionId());
        if (workflowVersion != null) {
            graph.addNode(workflowNode(workflowVersion, 2));
        } else {
            graph.addWarning("Skill 引用的工作流 " + reference.getWorkflowVersionId() + " 已不存在。");
        }

        String versionNodeId = null;
        if (reference.getSkillVersionId() == null) {
            graph.addWarning("工作流 " + reference.getWorkflowVersionId() + " 引用了 Skill，但没有钉住版本。");
        } else {
            SkillVersionEntity skillVersion = skillVersions.get(reference.getSkillVersionId());
            versionNodeId = "skill-version:" + reference.getSkillVersionId();
            if (skillVersion == null) {
                graph.addWarning("工作流引用的 Skill Version " + reference.getSkillVersionId() + " 已不存在。");
                graph.addNode(new GraphNode(
                        versionNodeId,
                        "SKILL_VERSION",
                        "历史版本",
                        "缺失版本 " + reference.getSkillVersionId(),
                        "MISSING",
                        1,
                        Map.of(
                                "skillId", skill.getId(),
                                "skillVersionId", reference.getSkillVersionId(),
                                "referenceMode", safe(reference.getReferenceMode()))));
            } else if (!Objects.equals(skillVersion.getSkillId(), skill.getId())) {
                graph.addWarning("工作流引用的 Skill Version " + reference.getSkillVersionId()
                        + " 不属于 Skill " + skill.getId() + "，已忽略。");
                versionNodeId = null;
            } else {
                graph.addNode(skillVersionNode(skillVersion, Map.of(
                        "referenceMode", safe(reference.getReferenceMode())), 1));
            }
        }

        if (versionNodeId != null) {
            graph.addEdge(edge(
                    versionNodeId,
                    "skill:" + skill.getId(),
                    "VERSION_OF",
                    "归属 Skill"));
            if (workflowVersion != null) {
                graph.addEdge(edge(
                        "workflow-version:" + workflowVersion.getId(),
                        versionNodeId,
                        "PINS_SKILL_VERSION",
                        "钉住版本"));
            }
        } else if (workflowVersion != null) {
            graph.addEdge(edge(
                    "workflow-version:" + workflowVersion.getId(),
                    "skill:" + skill.getId(),
                    "USES_SKILL",
                    "引用 Skill"));
        }

        if (workflowVersion == null) {
            return;
        }

        AgentDefinitionEntity agent = agents.get(workflowVersion.getAgentId());
        if (agent == null) {
            graph.addWarning("工作流 v" + workflowVersion.getVersionNo()
                    + " 所属 Agent " + workflowVersion.getAgentId() + " 已不存在。");
            return;
        }
        graph.addNode(agentNode(agent, 3));
        graph.addEdge(edge(
                "workflow-version:" + workflowVersion.getId(),
                "agent:" + agent.getAgentId(),
                "USED_BY_AGENT",
                "归属 Agent"));
    }

    private AgentWorkflowVersionEntity selectWorkflowVersion(String companyId,
                                                             AgentDefinitionEntity agent,
                                                             Integer versionNo) {
        if (versionNo != null) {
            return workflowVersionRepository.findByCompanyIdAndAgentIdAndVersionNo(
                            companyId, agent.getAgentId(), versionNo)
                    .orElseThrow(() -> new ResourceNotFoundException("Workflow version not found"));
        }
        List<AgentWorkflowVersionEntity> versions = workflowVersionRepository
                .findByCompanyIdAndAgentIdOrderByVersionNoDesc(companyId, agent.getAgentId());
        if (agent.getPublishedVersionId() != null) {
            for (AgentWorkflowVersionEntity candidate : versions) {
                if (agent.getPublishedVersionId().equals(candidate.getId())) {
                    return candidate;
                }
            }
        }
        return versions.stream().findFirst().orElse(null);
    }

    private void addCurrentBindings(String companyId,
                                    AgentDefinitionEntity agent,
                                    GraphBuilder graph) {
        agentSkillBindingRepository
                .findByCompanyIdAndAgentIdAndEnabledTrueOrderByPriorityAscIdAsc(companyId, agent.getAgentId())
                .forEach(binding -> {
                    SkillDefinitionEntity skill = skillDefinitionRepository
                            .findByIdAndCompanyId(binding.getSkillId(), companyId)
                            .orElse(null);
                    String skillNodeId = "skill:" + binding.getSkillId();
                    if (skill == null) {
                        graph.addWarning("Agent 绑定的 Skill " + binding.getSkillId() + " 已不存在。");
                        graph.addNode(new GraphNode(
                                skillNodeId,
                                "SKILL",
                                "Skill " + binding.getSkillId(),
                                "当前绑定缺少 Skill 定义",
                                "MISSING",
                                2,
                                Map.of("skillId", binding.getSkillId())));
                    } else {
                        graph.addNode(skillNode(skill));
                    }
                    graph.addEdge(edge(
                            "agent:" + agent.getAgentId(),
                            skillNodeId,
                            "BINDS_SKILL",
                            "当前绑定"));
                    if (skill == null) {
                        return;
                    }

                    SkillVersionEntity version = resolveCurrentSkillVersion(companyId, skill, graph);
                    if (version == null) {
                        graph.addWarning("Skill " + skill.getSkillCode() + " 没有可展示的当前版本。");
                        return;
                    }
                    String versionNodeId = "skill-version:" + version.getId();
                    graph.addNode(skillVersionNode(version, Map.of(
                            "referenceMode", "CURRENT_BINDING",
                            "activationMode", safe(binding.getActivationMode()),
                            "priority", binding.getPriority())));
                    graph.addEdge(edge(skillNodeId, versionNodeId, "CURRENT_SKILL_VERSION", "当前版本"));
                    addResources(companyId, version, versionNodeId, graph);
                });
    }

    private SkillVersionEntity resolveCurrentSkillVersion(String companyId,
                                                          SkillDefinitionEntity skill,
                                                          GraphBuilder graph) {
        if (skill.getCurrentPublishedVersionId() != null) {
            SkillVersionEntity current = skillVersionRepository
                    .findByIdAndCompanyId(skill.getCurrentPublishedVersionId(), companyId)
                    .orElse(null);
            if (current != null && Objects.equals(current.getSkillId(), skill.getId())) {
                return current;
            }
            if (current == null) {
                graph.addWarning("Skill " + skill.getSkillCode() + " 的当前发布版本 "
                        + skill.getCurrentPublishedVersionId() + " 已不存在，已回退。");
            } else {
                graph.addWarning("Skill " + skill.getSkillCode() + " 的当前发布版本 "
                        + skill.getCurrentPublishedVersionId() + " 不属于该 Skill，已回退。");
            }
        }
        return skillVersionRepository
                .findTopByCompanyIdAndSkillIdAndPublishStatusOrderByVersionNoDesc(companyId, skill.getId(), "PUBLISHED")
                .or(() -> skillVersionRepository.findTopByCompanyIdAndSkillIdOrderByVersionNoDesc(companyId, skill.getId()))
                .orElse(null);
    }

    private void addPinnedSkill(String companyId,
                                AgentWorkflowVersionEntity workflowVersion,
                                AgentWorkflowSkillRefEntity reference,
                                GraphBuilder graph) {
        SkillDefinitionEntity skill = skillDefinitionRepository.findByIdAndCompanyId(reference.getSkillId(), companyId)
                .orElse(null);
        String skillNodeId = "skill:" + reference.getSkillId();
        if (skill == null) {
            graph.addWarning("工作流引用的 Skill " + reference.getSkillId() + " 已不存在。");
            graph.addNode(new GraphNode(
                    skillNodeId,
                    "SKILL",
                    "Skill " + reference.getSkillId(),
                    "历史引用缺少 Skill 定义",
                    "MISSING",
                    2,
                    Map.of("skillId", reference.getSkillId())));
        } else {
            graph.addNode(skillNode(skill));
        }
        graph.addEdge(edge(
                "workflow-version:" + workflowVersion.getId(),
                skillNodeId,
                "USES_SKILL",
                "引用 Skill"));

        if (reference.getSkillVersionId() == null) {
            graph.addWarning("Skill " + reference.getSkillId() + " 的历史引用没有钉住版本。");
            return;
        }

        SkillVersionEntity skillVersion = skillVersionRepository
                .findByIdAndCompanyId(reference.getSkillVersionId(), companyId)
                .orElse(null);
        String versionNodeId = "skill-version:" + reference.getSkillVersionId();
        if (skillVersion == null) {
            graph.addWarning("工作流引用的 Skill Version " + reference.getSkillVersionId() + " 已不存在。");
            graph.addNode(new GraphNode(
                    versionNodeId,
                    "SKILL_VERSION",
                    "历史版本",
                    "缺失版本 " + reference.getSkillVersionId(),
                    "MISSING",
                    3,
                    Map.of(
                            "skillId", reference.getSkillId(),
                            "skillVersionId", reference.getSkillVersionId(),
                            "referenceMode", safe(reference.getReferenceMode()))));
        } else if (!Objects.equals(skillVersion.getSkillId(), reference.getSkillId())) {
            graph.addWarning("工作流引用的 Skill Version " + reference.getSkillVersionId()
                    + " 不属于 Skill " + reference.getSkillId() + "，已忽略。");
            return;
        } else {
            Map<String, Object> referenceMetadata = new LinkedHashMap<>();
            referenceMetadata.put("referenceMode", safe(reference.getReferenceMode()));
            referenceMetadata.put("templateCode", safe(reference.getTemplateCode()));
            if (reference.getTemplateVersionNo() != null) {
                referenceMetadata.put("templateVersionNo", reference.getTemplateVersionNo());
            }
            graph.addNode(skillVersionNode(skillVersion, referenceMetadata));
            addResources(companyId, skillVersion, versionNodeId, graph);
        }
        graph.addEdge(edge(
                "workflow-version:" + workflowVersion.getId(),
                versionNodeId,
                "PINS_SKILL_VERSION",
                "钉住版本"));
        graph.addEdge(edge(versionNodeId, skillNodeId, "VERSION_OF", "归属 Skill"));
    }

    private void addResources(String companyId,
                              SkillVersionEntity skillVersion,
                              String versionNodeId,
                              GraphBuilder graph) {
        for (String toolName : readIdentifiers(
                skillVersion.getEffectiveToolWhitelist(),
                "Skill v" + skillVersion.getVersionNo() + " 工具边界",
                graph)) {
            ToolDefinitionEntity tool = toolDefinitionRepository.findByCompanyIdAndToolName(companyId, toolName).orElse(null);
            graph.addNode(new GraphNode(
                    "tool:" + toolName,
                    "TOOL",
                    toolName,
                    tool == null ? "工具元数据不可用" : safe(tool.getDescription()),
                    tool == null ? "UNKNOWN" : (tool.isEnabled() ? "ENABLED" : "DISABLED"),
                    4,
                    tool == null
                            ? Map.of("toolName", toolName)
                            : Map.of("toolName", toolName, "riskLevel", safe(tool.getRiskLevel()))));
            graph.addEdge(edge(versionNodeId, "tool:" + toolName, "ALLOWS_TOOL", "允许调用"));
        }

        for (String rawId : readIdentifiers(
                skillVersion.getEffectiveKbWhitelist(),
                "Skill v" + skillVersion.getVersionNo() + " 知识库边界",
                graph)) {
            Long knowledgeBaseId = parseLong(rawId);
            KnowledgeBaseEntity knowledgeBase = knowledgeBaseId == null
                    ? null
                    : knowledgeBaseRepository.findByIdAndCompanyId(knowledgeBaseId, companyId).orElse(null);
            String nodeId = "knowledge-base:" + rawId;
            graph.addNode(new GraphNode(
                    nodeId,
                    "KNOWLEDGE_BASE",
                    knowledgeBase == null ? "知识库 " + rawId : knowledgeBase.getName(),
                    knowledgeBase == null ? "知识库元数据不可用" : safe(knowledgeBase.getDescription()),
                    knowledgeBase == null ? "UNKNOWN" : safe(knowledgeBase.getStatus()),
                    4,
                    Map.of("knowledgeBaseId", rawId)));
            graph.addEdge(edge(versionNodeId, nodeId, "ALLOWS_KNOWLEDGE_BASE", "允许引用"));
        }
    }

    private List<String> readIdentifiers(String raw, String context, GraphBuilder graph) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String normalized = raw.trim();
        if (!normalized.startsWith("[")) {
            Set<String> values = new LinkedHashSet<>();
            for (String item : normalized.split(",")) {
                String value = item.trim();
                if (!value.isEmpty()) {
                    values.add(value);
                }
            }
            return values.stream().sorted().toList();
        }
        try {
            JsonNode parsed = objectMapper.readTree(normalized);
            if (parsed.isArray()) {
                Set<String> values = new LinkedHashSet<>();
                parsed.forEach(item -> {
                    String value = item.asText("").trim();
                    if (!value.isEmpty()) {
                        values.add(value);
                    }
                });
                return values.stream().sorted().toList();
            }
        } catch (Exception ignored) {
            // Warning below keeps malformed structured boundaries visible to governance users.
        }
        if (normalized.startsWith("[")) {
            graph.addWarning(context + " 无法解析，已忽略。");
        }
        return List.of();
    }

    private GraphNode agentNode(AgentDefinitionEntity agent) {
        return agentNode(agent, 0);
    }

    private GraphNode agentNode(AgentDefinitionEntity agent, int layer) {
        return new GraphNode(
                "agent:" + agent.getAgentId(),
                "AGENT",
                agent.getName(),
                agent.getAgentId(),
                agent.isEnabled() ? "ENABLED" : "DISABLED",
                layer,
                Map.of(
                        "agentId", agent.getAgentId(),
                        "builtin", agent.isBuiltin()));
    }

    private GraphNode workflowNode(AgentWorkflowVersionEntity workflowVersion) {
        return workflowNode(workflowVersion, 1);
    }

    private GraphNode workflowNode(AgentWorkflowVersionEntity workflowVersion, int layer) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("workflowVersionId", workflowVersion.getId());
        metadata.put("versionNo", workflowVersion.getVersionNo());
        metadata.put("versionLabel", safe(workflowVersion.getVersionLabel()));
        return new GraphNode(
                "workflow-version:" + workflowVersion.getId(),
                "WORKFLOW_VERSION",
                "工作流 v" + workflowVersion.getVersionNo(),
                safe(workflowVersion.getVersionLabel()),
                safe(workflowVersion.getPublishStatus()),
                layer,
                Map.copyOf(metadata));
    }

    private GraphNode skillNode(SkillDefinitionEntity skill) {
        return new GraphNode(
                "skill:" + skill.getId(),
                "SKILL",
                skill.getName(),
                skill.getSkillCode(),
                skill.isEnabled() ? safe(skill.getLifecycleStatus()) : "DISABLED",
                2,
                Map.of(
                        "skillId", skill.getId(),
                        "skillCode", skill.getSkillCode(),
                        "riskLevel", safe(skill.getRiskLevel())));
    }

    private GraphNode impactSkillNode(SkillDefinitionEntity skill) {
        GraphNode node = skillNode(skill);
        return new GraphNode(
                node.id(),
                node.type(),
                node.label(),
                node.detail(),
                node.status(),
                0,
                node.metadata());
    }

    private GraphNode skillVersionNode(SkillVersionEntity version, Map<String, Object> extraMetadata) {
        return skillVersionNode(version, extraMetadata, 3);
    }

    private GraphNode skillVersionNode(SkillVersionEntity version,
                                       Map<String, Object> extraMetadata,
                                       int layer) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("skillId", version.getSkillId());
        metadata.put("skillVersionId", version.getId());
        metadata.put("versionNo", version.getVersionNo());
        metadata.put("riskLevel", safe(version.getRiskLevel()));
        metadata.putAll(extraMetadata);
        return new GraphNode(
                "skill-version:" + version.getId(),
                "SKILL_VERSION",
                "Skill v" + version.getVersionNo(),
                safe(String.valueOf(extraMetadata.getOrDefault("referenceMode", ""))),
                safe(version.getPublishStatus()),
                layer,
                Map.copyOf(metadata));
    }

    private GraphEdge edge(String source, String target, String type, String label) {
        return new GraphEdge(
                "edge:" + type.toLowerCase() + ":" + source + ":" + target,
                source,
                target,
                type,
                label);
    }

    private Long parseLong(String raw) {
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public record GraphView(
            GraphScope scope,
            String sourceMode,
            List<GraphNode> nodes,
            List<GraphEdge> edges,
            GraphSummary summary,
            List<String> warnings) {
    }

    public record GraphScope(
            String type,
            String id,
            String label,
            Long workflowVersionId,
            Integer versionNo,
            String publishStatus) {
    }

    public record GraphNode(
            String id,
            String type,
            String label,
            String detail,
            String status,
            int layer,
            Map<String, Object> metadata) {
    }

    public record GraphEdge(String id, String source, String target, String type, String label) {
    }

    public record GraphSummary(
            int agentCount,
            int workflowVersionCount,
            int skillCount,
            int skillVersionCount,
            int toolCount,
            int knowledgeBaseCount) {
    }

    private static final class GraphBuilder {

        private final Map<String, GraphNode> nodes = new LinkedHashMap<>();
        private final Map<String, GraphEdge> edges = new LinkedHashMap<>();
        private final Set<String> warnings = new LinkedHashSet<>();

        void addNode(GraphNode node) {
            nodes.putIfAbsent(node.id(), node);
        }

        void addEdge(GraphEdge edge) {
            edges.putIfAbsent(edge.id(), edge);
        }

        void addWarning(String warning) {
            warnings.add(warning);
        }

        GraphView build(GraphScope scope, String sourceMode) {
            List<GraphNode> orderedNodes = nodes.values().stream()
                    .sorted(Comparator.comparingInt(GraphNode::layer)
                            .thenComparingInt(node -> NODE_TYPE_ORDER.getOrDefault(node.type(), 99))
                            .thenComparing(GraphNode::id))
                    .toList();
            Map<String, Integer> nodeIndexes = new LinkedHashMap<>();
            for (int index = 0; index < orderedNodes.size(); index++) {
                nodeIndexes.put(orderedNodes.get(index).id(), index);
            }
            List<GraphEdge> orderedEdges = edges.values().stream()
                    .filter(edge -> nodeIndexes.containsKey(edge.source()) && nodeIndexes.containsKey(edge.target()))
                    .sorted(Comparator.comparingInt((GraphEdge edge) -> nodeIndexes.get(edge.source()))
                            .thenComparingInt(edge -> nodeIndexes.get(edge.target()))
                            .thenComparing(GraphEdge::type))
                    .toList();
            GraphSummary summary = new GraphSummary(
                    countType(orderedNodes, "AGENT"),
                    countType(orderedNodes, "WORKFLOW_VERSION"),
                    countType(orderedNodes, "SKILL"),
                    countType(orderedNodes, "SKILL_VERSION"),
                    countType(orderedNodes, "TOOL"),
                    countType(orderedNodes, "KNOWLEDGE_BASE"));
            return new GraphView(
                    scope,
                    sourceMode,
                    orderedNodes,
                    orderedEdges,
                    summary,
                    List.copyOf(new ArrayList<>(warnings)));
        }

        private static int countType(List<GraphNode> nodes, String type) {
            return (int) nodes.stream().filter(node -> type.equals(node.type())).count();
        }
    }
}
