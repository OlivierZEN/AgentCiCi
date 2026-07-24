package com.codehouse.ciciassistant.agent.service;

import com.codehouse.ciciassistant.skill.domain.AgentSkillBindingEntity;
import com.codehouse.ciciassistant.skill.domain.AgentSkillBindingRepository;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionRepository;
import com.codehouse.ciciassistant.skill.service.SkillDefinitionService;
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
    private final SkillDefinitionService skillDefinitionService;

    public AgentSkillBindingService(AgentSkillBindingRepository agentSkillBindingRepository,
                                    SkillDefinitionRepository skillDefinitionRepository,
                                    SkillDefinitionService skillDefinitionService) {
        this.agentSkillBindingRepository = agentSkillBindingRepository;
        this.skillDefinitionRepository = skillDefinitionRepository;
        this.skillDefinitionService = skillDefinitionService;
    }

    public List<AgentSkillBindingView> listBindings(String companyId, String agentId) {
        skillDefinitionService.ensurePhaseOneDefaults(companyId);
        List<AgentSkillBindingEntity> bindings = agentSkillBindingRepository
                .findByCompanyIdAndAgentIdAndEnabledTrueOrderByPriorityAscIdAsc(companyId, normalizeAgentId(agentId));
        if (bindings.isEmpty()) {
            return List.of();
        }
        List<Long> skillIds = bindings.stream().map(AgentSkillBindingEntity::getSkillId).distinct().toList();
        Map<Long, SkillDefinitionEntity> skillById = skillDefinitionRepository.findByCompanyIdAndIdInAndEnabledTrue(companyId, skillIds)
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
    public List<AgentSkillBindingView> replaceBindings(String companyId, String agentId, List<ReplaceBindingInput> inputs) {
        skillDefinitionService.ensurePhaseOneDefaults(companyId);
        String normalizedAgentId = normalizeAgentId(agentId);
        List<AgentSkillBindingEntity> existing = agentSkillBindingRepository
                .findByCompanyIdAndAgentIdAndEnabledTrueOrderByPriorityAscIdAsc(companyId, normalizedAgentId);
        Map<Long, SkillDefinitionEntity> existingSkills = new LinkedHashMap<>();
        for (AgentSkillBindingEntity item : existing) {
            existingSkills.computeIfAbsent(
                    item.getSkillId(),
                    id -> skillDefinitionRepository.findByIdAndCompanyId(id, companyId).orElse(null)
            );
        }
        agentSkillBindingRepository.deleteByCompanyIdAndAgentId(companyId, normalizedAgentId);
        agentSkillBindingRepository.flush();
        Map<Long, SkillDefinitionEntity> skillById = new LinkedHashMap<>();
        List<AgentSkillBindingEntity> entities = new ArrayList<>();
        List<Long> selectedSkillIds = new ArrayList<>();
        int fallbackPriority = 10;
        for (ReplaceBindingInput input : inputs == null ? List.<ReplaceBindingInput>of() : inputs) {
            Long skillId = resolveSkillId(companyId, input);
            SkillDefinitionEntity skill = skillById.computeIfAbsent(
                    skillId,
                    id -> skillDefinitionRepository.findByIdAndCompanyId(id, companyId)
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
                    companyId,
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
                        companyId,
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
        return listBindings(companyId, normalizedAgentId);
    }

    private Long resolveSkillId(String companyId, ReplaceBindingInput input) {
        if (input.skillId() != null) {
            return input.skillId();
        }
        String code = input.skillCode() == null ? "" : input.skillCode().trim().toLowerCase();
        if (code.isBlank()) {
            throw new IllegalArgumentException("skillId or skillCode is required");
        }
        return skillDefinitionRepository.findByCompanyIdAndSkillCode(companyId, code)
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
