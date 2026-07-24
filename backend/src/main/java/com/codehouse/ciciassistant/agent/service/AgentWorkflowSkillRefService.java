package com.codehouse.ciciassistant.agent.service;

import com.codehouse.ciciassistant.agent.domain.AgentWorkflowSkillRefEntity;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowSkillRefRepository;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionEntity;
import com.codehouse.ciciassistant.platform.domain.PlatformSkillTemplateRepository;
import com.codehouse.ciciassistant.skill.domain.AgentSkillBindingEntity;
import com.codehouse.ciciassistant.skill.domain.AgentSkillBindingRepository;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionRepository;
import com.codehouse.ciciassistant.skill.domain.SkillVersionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillVersionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentWorkflowSkillRefService {

    private final AgentWorkflowSkillRefRepository agentWorkflowSkillRefRepository;
    private final AgentSkillBindingRepository agentSkillBindingRepository;
    private final SkillDefinitionRepository skillDefinitionRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final PlatformSkillTemplateRepository platformSkillTemplateRepository;
    private final ObjectMapper objectMapper;

    public AgentWorkflowSkillRefService(AgentWorkflowSkillRefRepository agentWorkflowSkillRefRepository,
                                        AgentSkillBindingRepository agentSkillBindingRepository,
                                        SkillDefinitionRepository skillDefinitionRepository,
                                        SkillVersionRepository skillVersionRepository,
                                        PlatformSkillTemplateRepository platformSkillTemplateRepository,
                                        ObjectMapper objectMapper) {
        this.agentWorkflowSkillRefRepository = agentWorkflowSkillRefRepository;
        this.agentSkillBindingRepository = agentSkillBindingRepository;
        this.skillDefinitionRepository = skillDefinitionRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.platformSkillTemplateRepository = platformSkillTemplateRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void ensureWorkflowSkillRefs(String companyId, String agentId, AgentWorkflowVersionEntity workflowVersion) {
        if (workflowVersion == null || workflowVersion.getId() == null) {
            return;
        }
        if (agentWorkflowSkillRefRepository.existsByCompanyIdAndWorkflowVersionId(companyId, workflowVersion.getId())) {
            return;
        }
        List<SnapshotCandidate> candidates = resolveSnapshotCandidates(companyId, agentId, workflowVersion);
        if (candidates.isEmpty()) {
            return;
        }
        Map<Long, String> referenceModeBySkillId = loadReferenceModes(companyId, agentId);
        for (SnapshotCandidate candidate : candidates) {
            SkillDefinitionEntity skill = candidate.skill();
            Optional<SkillVersionEntity> pinnedVersion = resolvePinnedSkillVersion(companyId, skill, candidate.explicitVersionNo());
            agentWorkflowSkillRefRepository.save(new AgentWorkflowSkillRefEntity(
                    companyId,
                    workflowVersion.getId(),
                    skill.getId(),
                    pinnedVersion.map(SkillVersionEntity::getId).orElse(null),
                    trimToNull(skill.getTemplateCode()),
                    resolveTemplateVersionNo(companyId, skill, pinnedVersion.orElse(null)),
                    normalizeReferenceMode(referenceModeBySkillId.get(skill.getId()))
            ));
        }
    }

    public List<RuntimeSkillRef> listRuntimeSkillRefs(String companyId, Long workflowVersionId) {
        if (workflowVersionId == null) {
            return List.of();
        }
        List<AgentWorkflowSkillRefEntity> refs = agentWorkflowSkillRefRepository
                .findByCompanyIdAndWorkflowVersionIdOrderByIdAsc(companyId, workflowVersionId);
        if (refs.isEmpty()) {
            return List.of();
        }
        List<RuntimeSkillRef> result = new ArrayList<>();
        for (AgentWorkflowSkillRefEntity ref : refs) {
            SkillDefinitionEntity skill = skillDefinitionRepository.findByIdAndCompanyId(ref.getSkillId(), companyId).orElse(null);
            SkillVersionEntity version = resolveSkillVersion(companyId, ref.getSkillId(), ref.getSkillVersionId()).orElse(null);
            String skillCode = skill == null ? ("skill-" + ref.getSkillId()) : skill.getSkillCode();
            String skillName = skill == null ? skillCode : skill.getName();
            boolean pinnedVersionAvailable = version != null;
            result.add(new RuntimeSkillRef(
                    skillCode,
                    skillName,
                    ref.getSkillId(),
                    ref.getSkillVersionId(),
                    version == null ? null : version.getVersionNo(),
                    ref.getTemplateCode(),
                    ref.getTemplateVersionNo(),
                    normalizeReferenceMode(ref.getReferenceMode()),
                    pinnedVersionAvailable ? safe(version.getCompiledPromptFragment()) : "",
                    pinnedVersionAvailable ? splitCsv(version.getEffectiveToolWhitelist(), null) : List.of(),
                    pinnedVersionAvailable ? splitCsv(version.getEffectiveKbWhitelist(), null) : List.of(),
                    pinnedVersionAvailable && skill != null ? safe(skill.getHandoffRule()) : "",
                    pinnedVersionAvailable && skill != null ? safe(skill.getOutputContract()) : "",
                    pinnedVersionAvailable ? safe(version.getRiskLevel()) : ""
            ));
        }
        return List.copyOf(result);
    }

    private Map<Long, String> loadReferenceModes(String companyId, String agentId) {
        Map<Long, String> result = new LinkedHashMap<>();
        if (agentId == null || agentId.isBlank()) {
            return result;
        }
        for (AgentSkillBindingEntity binding : agentSkillBindingRepository
                .findByCompanyIdAndAgentIdAndEnabledTrueOrderByPriorityAscIdAsc(companyId, agentId)) {
            result.putIfAbsent(binding.getSkillId(), binding.getActivationMode());
        }
        return result;
    }

    private List<SnapshotCandidate> resolveSnapshotCandidates(String companyId,
                                                              String agentId,
                                                              AgentWorkflowVersionEntity workflowVersion) {
        LinkedHashMap<Long, SnapshotCandidate> bySkillId = new LinkedHashMap<>();
        parseManifestCandidates(companyId, workflowVersion).forEach(candidate -> bySkillId.putIfAbsent(
                candidate.skill().getId(), candidate));
        if (!bySkillId.isEmpty()) {
            return List.copyOf(bySkillId.values());
        }
        if (agentId == null || agentId.isBlank()) {
            return List.of();
        }
        for (AgentSkillBindingEntity binding : agentSkillBindingRepository
                .findByCompanyIdAndAgentIdAndEnabledTrueOrderByPriorityAscIdAsc(companyId, agentId)) {
            skillDefinitionRepository.findByIdAndCompanyId(binding.getSkillId(), companyId)
                    .ifPresent(skill -> bySkillId.putIfAbsent(skill.getId(), new SnapshotCandidate(skill, null)));
        }
        return List.copyOf(bySkillId.values());
    }

    private List<SnapshotCandidate> parseManifestCandidates(String companyId, AgentWorkflowVersionEntity workflowVersion) {
        if (workflowVersion.getWorkflowManifest() == null || workflowVersion.getWorkflowManifest().isBlank()) {
            return List.of();
        }
        try {
            Map<String, Object> manifest = objectMapper.readValue(
                    workflowVersion.getWorkflowManifest(), new TypeReference<Map<String, Object>>() {});
            LinkedHashMap<Long, SnapshotCandidate> result = new LinkedHashMap<>();
            Map<String, Object> generatedFrom = getMap(manifest.get("generatedFrom"));
            Object resolvedRefsRaw = generatedFrom.get("resolvedSkillRefs");
            if (resolvedRefsRaw instanceof List<?> resolvedRefs) {
                for (Object item : resolvedRefs) {
                    resolveManifestSkillRef(companyId, item).ifPresent(candidate -> result.putIfAbsent(
                            candidate.skill().getId(), candidate));
                }
            }
            if (!result.isEmpty()) {
                return List.copyOf(result.values());
            }
            Map<String, Object> dependencies = getMap(manifest.get("dependencies"));
            for (String skillCode : toStringList(dependencies.get("skills"))) {
                skillDefinitionRepository.findByCompanyIdAndSkillCode(companyId, skillCode)
                        .ifPresent(skill -> result.putIfAbsent(skill.getId(), new SnapshotCandidate(skill, null)));
            }
            return List.copyOf(result.values());
        } catch (Exception ex) {
            return List.of();
        }
    }

    private Optional<SnapshotCandidate> resolveManifestSkillRef(String companyId, Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return Optional.empty();
        }
        boolean resolved = !"false".equalsIgnoreCase(safe(map.get("resolved")));
        if (!resolved) {
            return Optional.empty();
        }
        Integer versionNo = toInteger(map.get("versionNo"));
        Long skillId = toLong(map.get("skillId"));
        if (skillId != null) {
            Optional<SkillDefinitionEntity> byId = skillDefinitionRepository.findByIdAndCompanyId(skillId, companyId);
            if (byId.isPresent()) {
                return Optional.of(new SnapshotCandidate(byId.get(), versionNo));
            }
        }
        String skillCode = trimToNull(safe(map.get("skillCode")));
        if (skillCode == null) {
            return Optional.empty();
        }
        return skillDefinitionRepository.findByCompanyIdAndSkillCode(companyId, skillCode)
                .map(skill -> new SnapshotCandidate(skill, versionNo));
    }

    private Optional<SkillVersionEntity> resolvePinnedSkillVersion(String companyId,
                                                                   SkillDefinitionEntity skill,
                                                                   Integer explicitVersionNo) {
        if (explicitVersionNo != null) {
            return skillVersionRepository.findByCompanyIdAndSkillIdAndVersionNo(
                    companyId, skill.getId(), explicitVersionNo);
        }
        if (skill.getCurrentPublishedVersionId() != null) {
            Optional<SkillVersionEntity> currentPublished = resolveSkillVersion(
                    companyId, skill.getId(), skill.getCurrentPublishedVersionId());
            if (currentPublished.isPresent()) {
                return currentPublished;
            }
        }
        Optional<SkillVersionEntity> published = skillVersionRepository
                .findTopByCompanyIdAndSkillIdAndPublishStatusOrderByVersionNoDesc(companyId, skill.getId(), "PUBLISHED");
        if (published.isPresent()) {
            return published;
        }
        return skillVersionRepository.findTopByCompanyIdAndSkillIdOrderByVersionNoDesc(companyId, skill.getId());
    }

    private Optional<SkillVersionEntity> resolveSkillVersion(String companyId, Long skillId, Long skillVersionId) {
        if (skillVersionId == null) {
            return Optional.empty();
        }
        return skillVersionRepository.findByIdAndCompanyId(skillVersionId, companyId)
                .filter(version -> Objects.equals(skillId, version.getSkillId()));
    }

    private Integer resolveTemplateVersionNo(String companyId,
                                             SkillDefinitionEntity skill,
                                             SkillVersionEntity pinnedVersion) {
        String templateCode = trimToNull(skill.getTemplateCode());
        if (templateCode != null) {
            Integer currentTemplateVersionNo = platformSkillTemplateRepository.findByCompanyIdAndTemplateCode(companyId, templateCode)
                    .map(item -> item.getCurrentVersionNo())
                    .orElse(null);
            if (currentTemplateVersionNo != null) {
                return currentTemplateVersionNo;
            }
        }
        if (skill.getBaseTemplateVersion() != null) {
            return skill.getBaseTemplateVersion();
        }
        return pinnedVersion == null ? null : pinnedVersion.getVersionNo();
    }

    private static Map<String, Object> getMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        return map.entrySet().stream()
                .filter(entry -> entry.getKey() != null)
                .collect(java.util.stream.Collectors.toMap(
                        entry -> entry.getKey().toString(),
                        Map.Entry::getValue,
                        (left, right) -> right,
                        LinkedHashMap::new
                ));
    }

    private static List<String> toStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(AgentWorkflowSkillRefService::safe)
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    private static Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private static String safe(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeReferenceMode(String value) {
        String normalized = safe(value).trim().toLowerCase(Locale.ROOT);
        if (!List.of("always-on", "intent-route", "manual").contains(normalized)) {
            return "manual";
        }
        return normalized;
    }

    private static List<String> splitCsv(String primary, String fallback) {
        String raw = trimToNull(primary) != null ? primary : fallback;
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    private record SnapshotCandidate(
            SkillDefinitionEntity skill,
            Integer explicitVersionNo
    ) {
    }

    public record RuntimeSkillRef(
            String skillCode,
            String skillName,
            Long skillId,
            Long skillVersionId,
            Integer skillVersionNo,
            String templateCode,
            Integer templateVersionNo,
            String referenceMode,
            String promptFragment,
            List<String> toolWhitelist,
            List<String> kbWhitelist,
            String handoffRule,
            String outputContract,
            String riskLevel
    ) {
    }
}
