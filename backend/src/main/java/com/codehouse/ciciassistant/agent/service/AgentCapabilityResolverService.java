package com.codehouse.ciciassistant.agent.service;

import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionRepository;
import com.codehouse.ciciassistant.agent.domain.AgentKnowledgeBindingEntity;
import com.codehouse.ciciassistant.agent.domain.AgentKnowledgeBindingRepository;
import com.codehouse.ciciassistant.agent.domain.AgentToolBindingEntity;
import com.codehouse.ciciassistant.agent.domain.AgentToolBindingRepository;
import com.codehouse.ciciassistant.skill.domain.AgentSkillBindingEntity;
import com.codehouse.ciciassistant.skill.domain.AgentSkillBindingRepository;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionRepository;
import com.codehouse.ciciassistant.tool.service.ToolNameNormalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class AgentCapabilityResolverService {

    private final AgentDefinitionRepository agentDefinitionRepository;
    private final AgentKnowledgeBindingRepository agentKnowledgeBindingRepository;
    private final AgentToolBindingRepository agentToolBindingRepository;
    private final AgentSkillBindingRepository agentSkillBindingRepository;
    private final SkillDefinitionRepository skillDefinitionRepository;
    private final AgentDefinitionService agentDefinitionService;

    public AgentCapabilityResolverService(AgentDefinitionRepository agentDefinitionRepository,
                                          AgentKnowledgeBindingRepository agentKnowledgeBindingRepository,
                                          AgentToolBindingRepository agentToolBindingRepository,
                                          AgentSkillBindingRepository agentSkillBindingRepository,
                                          SkillDefinitionRepository skillDefinitionRepository,
                                          AgentDefinitionService agentDefinitionService) {
        this.agentDefinitionRepository = agentDefinitionRepository;
        this.agentKnowledgeBindingRepository = agentKnowledgeBindingRepository;
        this.agentToolBindingRepository = agentToolBindingRepository;
        this.agentSkillBindingRepository = agentSkillBindingRepository;
        this.skillDefinitionRepository = skillDefinitionRepository;
        this.agentDefinitionService = agentDefinitionService;
    }

    public AgentCapabilityResolution resolve(String orgId, String agentId, List<String> explicitSkillRefs) {
        if (orgId != null && !orgId.isBlank()) {
            agentDefinitionService.warmupBuiltinAgents(orgId);
        }
        String normalizedAgentId = normalizeAgentId(agentId);
        Optional<AgentDefinitionEntity> agentDefinition = agentDefinitionRepository.findByOrgIdAndAgentId(orgId, normalizedAgentId);
        List<String> agentToolBoundary = agentToolBindingRepository
                .findByOrgIdAndAgentIdAndEnabledTrueOrderByPriorityAscIdAsc(orgId, normalizedAgentId)
                .stream()
                .map(AgentToolBindingEntity::getToolId)
                .toList();
        List<Long> agentKbBoundary = agentKnowledgeBindingRepository
                .findByOrgIdAndAgentIdAndEnabledTrueOrderByPriorityAscIdAsc(orgId, normalizedAgentId)
                .stream()
                .map(AgentKnowledgeBindingEntity::getKnowledgeBaseId)
                .toList();

        List<SkillDefinitionEntity> selectedSkills = loadSelectedSkills(orgId, normalizedAgentId, explicitSkillRefs);
        List<String> effectiveSkillCodes = selectedSkills.stream().map(SkillDefinitionEntity::getSkillCode).toList();
        List<String> skillToolUnion = selectedSkills.stream()
                .flatMap(skill -> splitCsv(skill.getToolWhitelist()).stream())
                .distinct()
                .toList();
        List<String> normalizedAgentToolBoundary = ToolNameNormalizer.canonicalizeAll(agentToolBoundary);
        List<String> normalizedSkillToolUnion = ToolNameNormalizer.canonicalizeAll(skillToolUnion);
        List<Long> skillKbUnion = selectedSkills.stream()
                .flatMap(skill -> splitLongCsv(skill.getKbWhitelist()).stream())
                .distinct()
                .toList();
        List<String> effectiveToolNames = mergeBoundary(normalizedAgentToolBoundary, normalizedSkillToolUnion);
        List<Long> effectiveKnowledgeBaseIds = mergeBoundaryLong(agentKbBoundary, skillKbUnion);

        List<String> effectiveHandoffRules = new ArrayList<>();
        agentDefinition.map(AgentDefinitionEntity::getHandoffRule)
                .filter(item -> item != null && !item.isBlank())
                .ifPresent(effectiveHandoffRules::add);
        selectedSkills.stream()
                .map(SkillDefinitionEntity::getHandoffRule)
                .filter(item -> item != null && !item.isBlank())
                .forEach(item -> {
                    if (!effectiveHandoffRules.contains(item)) {
                        effectiveHandoffRules.add(item);
                    }
                });

        String outputContract = selectedSkills.stream()
                .map(SkillDefinitionEntity::getOutputContract)
                .filter(item -> item != null && !item.isBlank())
                .findFirst()
                .orElse(null);

        List<String> warnings = new ArrayList<>();
        if (!normalizedAgentToolBoundary.isEmpty() && !normalizedSkillToolUnion.isEmpty()) {
            LinkedHashSet<String> toolInter = new LinkedHashSet<>(normalizedAgentToolBoundary);
            toolInter.retainAll(normalizedSkillToolUnion);
            if (toolInter.isEmpty()) {
                warnings.add("Agent.toolIds 与 Skill.toolWhitelist 无交集，运行时工具边界已严格收敛为空；请在两侧补充同名工具后重试。");
            } else if (effectiveToolNames.size() < normalizedAgentToolBoundary.size()) {
                warnings.add("Agent.toolIds 与 Skill.toolWhitelist 存在重叠，已按交集收敛。");
            }
        }
        if (!agentKbBoundary.isEmpty() && !skillKbUnion.isEmpty()) {
            LinkedHashSet<Long> kbInter = new LinkedHashSet<>(agentKbBoundary);
            kbInter.retainAll(skillKbUnion);
            if (kbInter.isEmpty()) {
                warnings.add("Agent.knowledgeBaseIds 与 Skill.kbWhitelist 无交集，为避免丢失 Agent 已绑定知识库，已合并为并集。");
            } else if (effectiveKnowledgeBaseIds.size() < agentKbBoundary.size()) {
                warnings.add("Agent.knowledgeBaseIds 与 Skill.kbWhitelist 存在重叠，已按交集收敛。");
            }
        }

        return new AgentCapabilityResolution(
                normalizedAgentId,
                effectiveSkillCodes,
                effectiveToolNames,
                effectiveKnowledgeBaseIds,
                effectiveHandoffRules,
                outputContract,
                warnings,
                agentDefinition.map(AgentDefinitionEntity::getSystemPrompt).orElse(null),
                agentDefinition.map(AgentDefinitionEntity::getModel).orElse(null)
        );
    }

    private List<SkillDefinitionEntity> loadSelectedSkills(String orgId, String agentId, List<String> explicitSkillRefs) {
        List<String> explicit = explicitSkillRefs == null ? List.of() : explicitSkillRefs.stream()
                .map(item -> item == null ? "" : item.trim().toLowerCase())
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
        if (!explicit.isEmpty()) {
            List<SkillDefinitionEntity> result = new ArrayList<>();
            for (String skillCode : explicit) {
                skillDefinitionRepository.findByOrgIdAndSkillCode(orgId, skillCode)
                        .filter(SkillDefinitionEntity::isEnabled)
                        .ifPresent(result::add);
            }
            return result;
        }

        List<AgentSkillBindingEntity> bindings = agentSkillBindingRepository
                .findByOrgIdAndAgentIdAndEnabledTrueOrderByPriorityAscIdAsc(orgId, agentId);
        if (bindings.isEmpty()) {
            return List.of();
        }
        List<Long> skillIds = bindings.stream().map(AgentSkillBindingEntity::getSkillId).distinct().toList();
        Map<Long, SkillDefinitionEntity> byId = new LinkedHashMap<>();
        for (SkillDefinitionEntity skill : skillDefinitionRepository.findByOrgIdAndIdInAndEnabledTrue(orgId, skillIds)) {
            byId.put(skill.getId(), skill);
        }
        List<SkillDefinitionEntity> result = new ArrayList<>();
        for (Long skillId : skillIds) {
            SkillDefinitionEntity skill = byId.get(skillId);
            if (skill != null) {
                result.add(skill);
            }
        }
        return result;
    }

    private String normalizeAgentId(String raw) {
        if (raw == null || raw.isBlank()) {
            return "cici-system";
        }
        return raw.trim().toLowerCase();
    }

    private List<String> splitCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return List.of(raw.split(",")).stream().map(String::trim).filter(item -> !item.isBlank()).toList();
    }

    private List<Long> splitLongCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (String item : raw.split(",")) {
            try {
                result.add(Long.parseLong(item.trim()));
            } catch (NumberFormatException ignore) {
                // Keep backward compatibility for non-numeric kb refs.
            }
        }
        return result;
    }

    private List<String> mergeBoundary(List<String> agentBoundary, List<String> skillBoundary) {
        LinkedHashSet<String> agent = new LinkedHashSet<>(agentBoundary == null ? List.of() : agentBoundary);
        LinkedHashSet<String> skill = new LinkedHashSet<>(skillBoundary == null ? List.of() : skillBoundary);
        if (!agent.isEmpty() && !skill.isEmpty()) {
            LinkedHashSet<String> intersection = new LinkedHashSet<>(agent);
            intersection.retainAll(skill);
            LinkedHashSet<String> merged = intersection;
            if (ToolNameNormalizer.containsMcpWildcard(agentBoundary) || ToolNameNormalizer.containsMcpWildcard(skillBoundary)) {
                merged.add(ToolNameNormalizer.MCP_WORKFLOW_WILDCARD);
            }
            return List.copyOf(merged);
        }
        if (!agent.isEmpty()) {
            return List.copyOf(agent);
        }
        if (!skill.isEmpty()) {
            return List.copyOf(skill);
        }
        return List.of();
    }

    private List<Long> mergeBoundaryLong(List<Long> agentBoundary, List<Long> skillBoundary) {
        LinkedHashSet<Long> agent = new LinkedHashSet<>(agentBoundary == null ? List.of() : agentBoundary);
        LinkedHashSet<Long> skill = new LinkedHashSet<>(skillBoundary == null ? List.of() : skillBoundary);
        if (!agent.isEmpty() && !skill.isEmpty()) {
            LinkedHashSet<Long> intersection = new LinkedHashSet<>(agent);
            intersection.retainAll(skill);
            if (!intersection.isEmpty()) {
                return List.copyOf(intersection);
            }
            LinkedHashSet<Long> union = new LinkedHashSet<>(agent);
            union.addAll(skill);
            return List.copyOf(union);
        }
        if (!agent.isEmpty()) {
            return List.copyOf(agent);
        }
        if (!skill.isEmpty()) {
            return List.copyOf(skill);
        }
        return List.of();
    }

    public record AgentCapabilityResolution(
            String agentId,
            List<String> effectiveSkillCodes,
            List<String> effectiveToolNames,
            List<Long> effectiveKnowledgeBaseIds,
            List<String> effectiveHandoffRules,
            String outputContract,
            List<String> warnings,
            String agentSystemPrompt,
            String agentModel
    ) {
    }
}
