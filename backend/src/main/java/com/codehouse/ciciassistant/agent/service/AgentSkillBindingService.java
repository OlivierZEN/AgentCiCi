package com.codehouse.ciciassistant.agent.service;

import com.codehouse.ciciassistant.skill.domain.AgentSkillBindingEntity;
import com.codehouse.ciciassistant.skill.domain.AgentSkillBindingRepository;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentSkillBindingService {

    private final AgentSkillBindingRepository agentSkillBindingRepository;
    private final SkillDefinitionRepository skillDefinitionRepository;

    public AgentSkillBindingService(AgentSkillBindingRepository agentSkillBindingRepository,
                                    SkillDefinitionRepository skillDefinitionRepository) {
        this.agentSkillBindingRepository = agentSkillBindingRepository;
        this.skillDefinitionRepository = skillDefinitionRepository;
    }

    public List<AgentSkillBindingView> listBindings(String orgId, String agentId) {
        List<AgentSkillBindingEntity> bindings = agentSkillBindingRepository
                .findByOrgIdAndAgentIdAndEnabledTrueOrderByPriorityAscIdAsc(orgId, normalizeAgentId(agentId));
        if (bindings.isEmpty()) {
            return List.of();
        }
        List<Long> skillIds = bindings.stream().map(AgentSkillBindingEntity::getSkillId).distinct().toList();
        Map<Long, SkillDefinitionEntity> skillById = skillDefinitionRepository.findByOrgIdAndIdInAndEnabledTrue(orgId, skillIds)
                .stream()
                .collect(java.util.stream.Collectors.toMap(SkillDefinitionEntity::getId, item -> item));
        List<AgentSkillBindingView> result = new ArrayList<>();
        for (AgentSkillBindingEntity binding : bindings) {
            SkillDefinitionEntity skill = skillById.get(binding.getSkillId());
            if (skill == null || !skill.isVisibleToTenant()) {
                continue;
            }
            result.add(new AgentSkillBindingView(
                    binding.getSkillId(),
                    skill.getSkillCode(),
                    skill.getName(),
                    skill.getRiskLevel(),
                    binding.getActivationMode(),
                    Optional.ofNullable(binding.getActivationCondition()).orElse(""),
                    binding.getPriority(),
                    binding.isEnabled(),
                    splitCsv(skill.getToolWhitelist()),
                    splitCsv(skill.getKbWhitelist()),
                    Optional.ofNullable(skill.getHandoffRule()).orElse("")
            ));
        }
        return result;
    }

    @Transactional
    public List<AgentSkillBindingView> replaceBindings(String orgId, String agentId, List<ReplaceBindingInput> inputs) {
        String normalizedAgentId = normalizeAgentId(agentId);
        List<AgentSkillBindingEntity> existing = agentSkillBindingRepository
                .findByOrgIdAndAgentIdAndEnabledTrueOrderByPriorityAscIdAsc(orgId, normalizedAgentId);
        Map<Long, SkillDefinitionEntity> existingSkills = new LinkedHashMap<>();
        for (AgentSkillBindingEntity item : existing) {
            existingSkills.computeIfAbsent(
                    item.getSkillId(),
                    id -> skillDefinitionRepository.findByIdAndOrgId(id, orgId).orElse(null)
            );
        }
        agentSkillBindingRepository.deleteByOrgIdAndAgentId(orgId, normalizedAgentId);
        agentSkillBindingRepository.flush();
        Map<Long, SkillDefinitionEntity> skillById = new LinkedHashMap<>();
        List<AgentSkillBindingEntity> entities = new ArrayList<>();
        List<Long> selectedSkillIds = new ArrayList<>();
        int fallbackPriority = 10;
        for (ReplaceBindingInput input : inputs == null ? List.<ReplaceBindingInput>of() : inputs) {
            Long skillId = resolveSkillId(orgId, input);
            SkillDefinitionEntity skill = skillById.computeIfAbsent(
                    skillId,
                    id -> skillDefinitionRepository.findByIdAndOrgId(id, orgId)
                            .orElseThrow(() -> new IllegalArgumentException("skill not found: " + id))
            );
            if (!skill.isEnabled()) {
                throw new IllegalArgumentException("skill is disabled: " + skill.getSkillCode());
            }
            if (!skill.isVisibleToTenant() || skill.isInternalOnly()) {
                throw new IllegalArgumentException("skill is platform managed and cannot be bound manually");
            }
            selectedSkillIds.add(skillId);
            entities.add(new AgentSkillBindingEntity(
                    orgId,
                    normalizedAgentId,
                    skillId,
                    normalizeActivationMode(input.activationMode()),
                    input.activationCondition(),
                    input.priority() == null ? fallbackPriority : input.priority(),
                    input.enabled() == null || input.enabled()
            ));
            fallbackPriority += 10;
        }
        for (AgentSkillBindingEntity binding : existing) {
            SkillDefinitionEntity skill = existingSkills.get(binding.getSkillId());
            if (skill == null) {
                continue;
            }
            if (!skill.isVisibleToTenant() || skill.isMandatoryBinding() || skill.isInternalOnly()) {
                if (selectedSkillIds.contains(binding.getSkillId())) {
                    continue;
                }
                entities.add(new AgentSkillBindingEntity(
                        orgId,
                        normalizedAgentId,
                        binding.getSkillId(),
                        binding.getActivationMode(),
                        binding.getActivationCondition(),
                        binding.getPriority(),
                        binding.isEnabled()
                ));
            }
        }
        if (entities.isEmpty()) {
            return List.of();
        }
        agentSkillBindingRepository.saveAll(entities);
        return listBindings(orgId, normalizedAgentId);
    }

    private Long resolveSkillId(String orgId, ReplaceBindingInput input) {
        if (input.skillId() != null) {
            return input.skillId();
        }
        String code = input.skillCode() == null ? "" : input.skillCode().trim().toLowerCase();
        if (code.isBlank()) {
            throw new IllegalArgumentException("skillId or skillCode is required");
        }
        return skillDefinitionRepository.findByOrgIdAndSkillCode(orgId, code)
                .map(SkillDefinitionEntity::getId)
                .orElseThrow(() -> new IllegalArgumentException("skill not found for code: " + code));
    }

    private String normalizeAgentId(String raw) {
        if (raw == null || raw.isBlank()) {
            return "cici-system";
        }
        return raw.trim().toLowerCase();
    }

    private String normalizeActivationMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return "always-on";
        }
        String mode = raw.trim().toLowerCase();
        if (!List.of("always-on", "intent-route", "manual").contains(mode)) {
            throw new IllegalArgumentException("Unsupported activationMode: " + raw);
        }
        return mode;
    }

    private List<String> splitCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return List.of(raw.split(",")).stream()
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    public record ReplaceBindingInput(
            Long skillId,
            String skillCode,
            String activationMode,
            String activationCondition,
            Integer priority,
            Boolean enabled
    ) {
    }

    public record AgentSkillBindingView(
            Long skillId,
            String skillCode,
            String skillName,
            String riskLevel,
            String activationMode,
            String activationCondition,
            Integer priority,
            boolean enabled,
            List<String> toolWhitelist,
            List<String> kbWhitelist,
            String handoffRule
    ) {
    }
}
