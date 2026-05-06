package com.codehouse.ciciassistant.skill.service;

import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionRepository;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionRepository;
import com.codehouse.ciciassistant.agent.service.AgentCapabilityResolverService;
import com.codehouse.ciciassistant.agent.service.AgentWorkflowSkillRefService;
import com.codehouse.ciciassistant.ai.service.ChatSessionStateService;
import com.codehouse.ciciassistant.cloudcc.CloudccOpenApiService;
import com.codehouse.ciciassistant.platform.service.PlatformGovernanceService;
import com.codehouse.ciciassistant.skill.domain.AgentSkillBindingEntity;
import com.codehouse.ciciassistant.skill.domain.AgentSkillBindingRepository;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillVersionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillVersionRepository;
import com.codehouse.ciciassistant.tool.service.ToolNameNormalizer;
import com.codehouse.ciciassistant.tool.tavily.TavilyToolService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class SkillResolverService {

    private static final List<String> CICI_DEFAULT_DISCOVERY_TOOLS = List.of(
            CloudccOpenApiService.toolName(),
            CloudccOpenApiService.toolNameGetStandardObjects(),
            CloudccOpenApiService.toolNameGetCustomObjects(),
            CloudccOpenApiService.toolNameGetObjectFields(),
            ToolNameNormalizer.GET_PENDING_APPROVALS
    );

    private final SkillDefinitionService skillDefinitionService;
    private final AgentDefinitionRepository agentDefinitionRepository;
    private final AgentWorkflowVersionRepository agentWorkflowVersionRepository;
    private final AgentCapabilityResolverService agentCapabilityResolverService;
    private final AgentSkillBindingRepository agentSkillBindingRepository;
    private final ChatSessionStateService chatSessionStateService;
    private final PlatformGovernanceService platformGovernanceService;
    private final AgentWorkflowSkillRefService agentWorkflowSkillRefService;
    private final SkillVersionRepository skillVersionRepository;
    private final ObjectMapper objectMapper;
    private final SkillApiToolService skillApiToolService;

    public SkillResolverService(SkillDefinitionService skillDefinitionService,
                                AgentDefinitionRepository agentDefinitionRepository,
                                AgentWorkflowVersionRepository agentWorkflowVersionRepository,
                                AgentCapabilityResolverService agentCapabilityResolverService,
                                AgentSkillBindingRepository agentSkillBindingRepository,
                                ChatSessionStateService chatSessionStateService,
                                PlatformGovernanceService platformGovernanceService,
                                AgentWorkflowSkillRefService agentWorkflowSkillRefService,
                                SkillVersionRepository skillVersionRepository,
                                ObjectMapper objectMapper,
                                SkillApiToolService skillApiToolService) {
        this.skillDefinitionService = skillDefinitionService;
        this.agentDefinitionRepository = agentDefinitionRepository;
        this.agentWorkflowVersionRepository = agentWorkflowVersionRepository;
        this.agentCapabilityResolverService = agentCapabilityResolverService;
        this.agentSkillBindingRepository = agentSkillBindingRepository;
        this.chatSessionStateService = chatSessionStateService;
        this.platformGovernanceService = platformGovernanceService;
        this.agentWorkflowSkillRefService = agentWorkflowSkillRefService;
        this.skillVersionRepository = skillVersionRepository;
        this.objectMapper = objectMapper;
        this.skillApiToolService = skillApiToolService;
    }

    public ResolvedSkillContext resolve(String orgId, String requestedAgentId, String sessionId) {
        return resolve(orgId, requestedAgentId, sessionId, Optional.empty());
    }

    /**
     * Resolves skills/tools for chat. Optional {@code activeSkillOverride} updates persisted {@code active_skill_code}
     * in session state before computing the effective tool list per skill activation rules.
     */
    public ResolvedSkillContext resolve(String orgId, String requestedAgentId, String sessionId,
                                        Optional<String> activeSkillOverride) {
        String agentId = resolveAgentId(requestedAgentId, sessionId);
        AgentCapabilityResolverService.AgentCapabilityResolution capability = agentCapabilityResolverService.resolve(
                orgId,
                agentId,
                List.of()
        );
        PublishedRuntimeBinding publishedRuntimeBinding = resolvePublishedRuntimeBinding(orgId, agentId);
        PlatformGovernanceService.RuntimePolicyBundle runtimePolicyBundle =
                platformGovernanceService.resolvePublishedPolicyBundle(orgId);
        List<AgentWorkflowSkillRefService.RuntimeSkillRef> pinnedSkillRefs = publishedRuntimeBinding.skillRefs();
        List<String> effectiveSkillCodes = pinnedSkillRefs.isEmpty()
                ? (publishedRuntimeBinding.skillCodes().isEmpty()
                ? capability.effectiveSkillCodes()
                : publishedRuntimeBinding.skillCodes())
                : pinnedSkillRefs.stream().map(AgentWorkflowSkillRefService.RuntimeSkillRef::skillCode).toList();
        List<SkillDefinitionEntity> entities = pinnedSkillRefs.isEmpty()
                ? resolveSkillEntities(orgId, agentId, effectiveSkillCodes)
                : List.of();
        if (pinnedSkillRefs.isEmpty() && entities.isEmpty() && !"cici-system".equals(agentId)) {
            agentId = "cici-system";
            capability = agentCapabilityResolverService.resolve(orgId, agentId, List.of());
            publishedRuntimeBinding = resolvePublishedRuntimeBinding(orgId, agentId);
            runtimePolicyBundle = platformGovernanceService.resolvePublishedPolicyBundle(orgId);
            pinnedSkillRefs = publishedRuntimeBinding.skillRefs();
            effectiveSkillCodes = pinnedSkillRefs.isEmpty()
                    ? (publishedRuntimeBinding.skillCodes().isEmpty()
                    ? capability.effectiveSkillCodes()
                    : publishedRuntimeBinding.skillCodes())
                    : pinnedSkillRefs.stream().map(AgentWorkflowSkillRefService.RuntimeSkillRef::skillCode).toList();
            entities = pinnedSkillRefs.isEmpty()
                    ? resolveSkillEntities(orgId, agentId, effectiveSkillCodes)
                    : List.of();
        }

        Optional<AgentDefinitionEntity> agentDef = agentDefinitionRepository.findByOrgIdAndAgentId(orgId, agentId);
        String agentSystemPrompt = agentDef.map(AgentDefinitionEntity::getSystemPrompt)
                .filter(s -> s != null && !s.isBlank())
                .orElse(null);
        String agentModel = agentDef.map(AgentDefinitionEntity::getModel)
                .filter(s -> s != null && !s.isBlank())
                .orElse(null);

        Map<Long, String> activationBySkillId = loadActivationModes(orgId, agentId);
        List<ResolvedSkillVersionRef> resolvedSkillRefs = pinnedSkillRefs.isEmpty()
                ? entities.stream().map(item -> buildCurrentSkillVersionRef(orgId, item, activationBySkillId)).toList()
                : pinnedSkillRefs.stream().map(this::toResolvedSkillVersionRef).toList();
        List<ResolvedSkill> skills = pinnedSkillRefs.isEmpty()
                ? entities.stream()
                .map(item -> new ResolvedSkill(
                        item.getSkillCode(),
                        item.getName(),
                        item.getPromptFragment(),
                        splitCsv(item.getToolWhitelist()),
                        splitCsv(item.getKbWhitelist()),
                        item.getHandoffRule(),
                        item.getOutputContract(),
                        item.getRiskLevel(),
                        activationBySkillId.getOrDefault(item.getId(), "always-on")
                ))
                .toList()
                : pinnedSkillRefs.stream()
                .map(item -> new ResolvedSkill(
                        item.skillCode(),
                        item.skillName(),
                        item.promptFragment(),
                        item.toolWhitelist(),
                        item.kbWhitelist(),
                        item.handoffRule(),
                        item.outputContract(),
                        item.riskLevel(),
                        item.referenceMode()
                ))
                .toList();
        List<String> agentDirectToolNames = List.copyOf(ToolNameNormalizer.canonicalizeAll(capability.agentDirectToolNames()));
        List<String> skillDeclaredToolNames = pinnedSkillRefs.isEmpty()
                ? List.copyOf(capability.skillDeclaredToolNames())
                : List.copyOf(ToolNameNormalizer.canonicalizeAll(
                pinnedSkillRefs.stream().flatMap(item -> item.toolWhitelist().stream()).toList()));
        LinkedHashSet<String> skillScopedToolNames = new LinkedHashSet<>(skillDeclaredToolNames);
        skillScopedToolNames.removeAll(new LinkedHashSet<>(agentDirectToolNames));

        List<String> boundSkillCodes = skills.stream().map(s -> s.skillCode().toLowerCase(Locale.ROOT)).toList();
        Optional<String> activeSkillEffective = chatSessionStateService.mergeAndGetActiveSkillCode(
                orgId, sessionId, agentId, activeSkillOverride, boundSkillCodes);

        LinkedHashSet<String> baselineUniversal = new LinkedHashSet<>(agentDirectToolNames);
        baselineUniversal.addAll(ToolNameNormalizer.canonicalizeAll(publishedRuntimeBinding.toolNames()));
        augmentBuiltinCiciToolset(agentId, baselineUniversal);

        LinkedHashSet<String> toolNames = new LinkedHashSet<>(baselineUniversal);
        LinkedHashSet<Long> runtimeApiVersionIds = new LinkedHashSet<>();
        for (ResolvedSkill skill : skills) {
            LinkedHashSet<String> declared = new LinkedHashSet<>(ToolNameNormalizer.canonicalizeAll(skill.toolWhitelist()));
            boolean ambient = isAmbientActivation(skill.activationMode());
            boolean activeMatches = activeSkillEffective.map(cur -> cur.equalsIgnoreCase(skill.skillCode())).orElse(false);
            if (ambient || activeMatches) {
                resolvedSkillRefs.stream()
                        .filter(ref -> ref.skillVersionId() != null)
                        .filter(ref -> ref.skillCode().equalsIgnoreCase(skill.skillCode()))
                        .map(ResolvedSkillVersionRef::skillVersionId)
                        .forEach(runtimeApiVersionIds::add);
            }
            for (String tool : declared) {
                if (baselineUniversal.contains(tool)) {
                    continue;
                }
                if (ambient || activeMatches) {
                    toolNames.add(tool);
                }
            }
        }
        List<SkillApiToolService.ResolvedSkillApiTool> skillApiTools =
                skillApiToolService.findRuntimeTools(orgId, runtimeApiVersionIds);
        skillApiTools.stream().map(SkillApiToolService.ResolvedSkillApiTool::toolName).forEach(toolNames::add);

        LinkedHashSet<String> kbIds = new LinkedHashSet<>(
                capability.effectiveKnowledgeBaseIds().stream().map(String::valueOf).toList());
        kbIds.addAll(publishedRuntimeBinding.knowledgeBaseIds());
        List<String> handoffRules = new ArrayList<>();
        String outputContract = null;

        for (ResolvedSkill skill : skills) {
            if (capability.effectiveKnowledgeBaseIds().isEmpty()) {
                kbIds.addAll(skill.kbWhitelist());
            }
            if (skill.handoffRule() != null && !skill.handoffRule().isBlank()) {
                handoffRules.add(skill.handoffRule().trim());
            }
            if (outputContract == null && skill.outputContract() != null && !skill.outputContract().isBlank()) {
                outputContract = skill.outputContract().trim();
            }
        }

        capability.effectiveHandoffRules().forEach(rule -> {
            if (!handoffRules.contains(rule)) {
                handoffRules.add(rule);
            }
        });
        if (!publishedRuntimeBinding.handoffRule().isBlank()
                && !handoffRules.contains(publishedRuntimeBinding.handoffRule())) {
            handoffRules.add(publishedRuntimeBinding.handoffRule());
        }
        runtimePolicyBundle.handoffRules().forEach(rule -> {
            if (!handoffRules.contains(rule)) {
                handoffRules.add(rule);
            }
        });

        List<String> runtimeAllowedToolNames = platformGovernanceService.filterRuntimeAllowedToolNames(
                orgId,
                List.copyOf(toolNames)
        );

        return new ResolvedSkillContext(
                agentId,
                skills,
                skills.stream().map(ResolvedSkill::skillCode).toList(),
                runtimeAllowedToolNames,
                agentDirectToolNames,
                skillDeclaredToolNames,
                List.copyOf(skillScopedToolNames),
                List.copyOf(kbIds),
                handoffRules,
                outputContract,
                agentSystemPrompt,
                agentModel,
                activeSkillEffective.orElse(null),
                publishedRuntimeBinding.maxToolCalls(),
                publishedRuntimeBinding.publishedVersionId(),
                resolvedSkillRefs,
                skillApiTools,
                new ResolvedPolicyBundle(
                        runtimePolicyBundle.bundleCode(),
                        runtimePolicyBundle.versionNo(),
                        runtimePolicyBundle.promptFragment(),
                        runtimePolicyBundle.handoffRules()
                )
        );
    }

    private Map<Long, String> loadActivationModes(String orgId, String agentId) {
        Map<Long, String> map = new LinkedHashMap<>();
        for (AgentSkillBindingEntity binding : agentSkillBindingRepository.findByOrgIdAndAgentIdAndEnabledTrueOrderByPriorityAscIdAsc(orgId, agentId)) {
            map.put(binding.getSkillId(), binding.getActivationMode());
        }
        return map;
    }

    private static boolean isAmbientActivation(String activationMode) {
        if (activationMode == null || activationMode.isBlank()) {
            return true;
        }
        String normalized = activationMode.trim();
        return "ALWAYS".equalsIgnoreCase(normalized) || "always-on".equalsIgnoreCase(normalized);
    }

    public List<String> resolveKnowledgeBaseIds(ResolvedSkillContext context, List<String> requestedKnowledgeBaseIds) {
        List<String> requested = requestedKnowledgeBaseIds == null ? List.of() : requestedKnowledgeBaseIds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
        if (context.defaultKnowledgeBaseIds().isEmpty()) {
            return requested;
        }
        if (requested.isEmpty()) {
            return context.defaultKnowledgeBaseIds();
        }
        return requested.stream()
                .filter(context.defaultKnowledgeBaseIds()::contains)
                .toList();
    }

    public String resolveAgentId(String requestedAgentId, String sessionId) {
        if (requestedAgentId != null && !requestedAgentId.isBlank()) {
            return skillDefinitionService.normalizeAgentId(requestedAgentId);
        }
        if (sessionId != null && sessionId.startsWith("assistant-ui-")) {
            return "cici-system";
        }
        return "cici-system";
    }

    private void augmentBuiltinCiciToolset(String agentId, LinkedHashSet<String> toolNames) {
        if (!"cici-system".equals(agentId)) {
            return;
        }
        toolNames.addAll(CICI_DEFAULT_DISCOVERY_TOOLS);
        toolNames.addAll(ToolNameNormalizer.canonicalizeAll(
                List.of(TavilyToolService.TOOL_SEARCH, TavilyToolService.TOOL_EXTRACT)));
    }

    private static List<String> splitCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    private PublishedRuntimeBinding resolvePublishedRuntimeBinding(String orgId, String agentId) {
        Optional<AgentDefinitionEntity> definition = agentDefinitionRepository.findByOrgIdAndAgentId(orgId, agentId);
        if (definition.isEmpty() || definition.get().getPublishedVersionId() == null) {
            return PublishedRuntimeBinding.EMPTY;
        }
        Optional<AgentWorkflowVersionEntity> version = agentWorkflowVersionRepository.findById(definition.get().getPublishedVersionId())
                .filter(item -> orgId.equals(item.getOrgId()) && agentId.equals(item.getAgentId()))
                .filter(item -> "PUBLISHED".equalsIgnoreCase(item.getPublishStatus()));
        if (version.isEmpty() || version.get().getWorkflowManifest() == null || version.get().getWorkflowManifest().isBlank()) {
            return PublishedRuntimeBinding.EMPTY;
        }
        try {
            Map<String, Object> manifest = objectMapper.readValue(
                    version.get().getWorkflowManifest(), new TypeReference<Map<String, Object>>() {});
            Map<String, Object> dependencies = getMap(manifest.get("dependencies"));
            Map<String, Object> policies = getMap(manifest.get("policies"));
            List<AgentWorkflowSkillRefService.RuntimeSkillRef> skillRefs =
                    agentWorkflowSkillRefService.listRuntimeSkillRefs(orgId, version.get().getId());
            return new PublishedRuntimeBinding(
                    toStringList(dependencies.get("skills")),
                    toStringList(dependencies.get("tools")),
                    toStringList(dependencies.get("knowledgeBases")),
                    asString(policies.get("handoffRule")),
                    toInteger(policies.get("maxToolCalls")),
                    version.get().getId(),
                    skillRefs
            );
        } catch (Exception ex) {
            return PublishedRuntimeBinding.EMPTY;
        }
    }

    private List<SkillDefinitionEntity> resolveSkillEntities(String orgId, String agentId, List<String> effectiveSkillCodes) {
        if (effectiveSkillCodes.isEmpty()) {
            return skillDefinitionService.listSkillsForAgent(orgId, agentId);
        }
        return effectiveSkillCodes.stream()
                .map(code -> skillDefinitionService.listSkills(orgId).stream()
                        .filter(item -> code.equals(item.getSkillCode()))
                        .findFirst()
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    private ResolvedSkillVersionRef buildCurrentSkillVersionRef(String orgId,
                                                                SkillDefinitionEntity skill,
                                                                Map<Long, String> activationBySkillId) {
        SkillVersionEntity version = resolveCurrentSkillVersion(orgId, skill).orElse(null);
        return new ResolvedSkillVersionRef(
                skill.getSkillCode(),
                skill.getId(),
                version == null ? null : version.getId(),
                version == null ? null : version.getVersionNo(),
                skill.getTemplateCode(),
                skill.getBaseTemplateVersion(),
                activationBySkillId.getOrDefault(skill.getId(), "always-on")
        );
    }

    private Optional<SkillVersionEntity> resolveCurrentSkillVersion(String orgId, SkillDefinitionEntity skill) {
        if (skill.getCurrentPublishedVersionId() != null) {
            Optional<SkillVersionEntity> currentPublished = skillVersionRepository.findById(skill.getCurrentPublishedVersionId())
                    .filter(version -> orgId.equals(version.getOrgId()) && Objects.equals(skill.getId(), version.getSkillId()));
            if (currentPublished.isPresent()) {
                return currentPublished;
            }
        }
        Optional<SkillVersionEntity> published = skillVersionRepository
                .findTopByOrgIdAndSkillIdAndPublishStatusOrderByVersionNoDesc(orgId, skill.getId(), "PUBLISHED");
        if (published.isPresent()) {
            return published;
        }
        return skillVersionRepository.findTopByOrgIdAndSkillIdOrderByVersionNoDesc(orgId, skill.getId());
    }

    private ResolvedSkillVersionRef toResolvedSkillVersionRef(AgentWorkflowSkillRefService.RuntimeSkillRef item) {
        return new ResolvedSkillVersionRef(
                item.skillCode(),
                item.skillId(),
                item.skillVersionId(),
                item.skillVersionNo(),
                item.templateCode(),
                item.templateVersionNo(),
                item.referenceMode()
        );
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
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    private static String asString(Object value) {
        return value == null ? "" : value.toString().trim();
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

    private record PublishedRuntimeBinding(
            List<String> skillCodes,
            List<String> toolNames,
            List<String> knowledgeBaseIds,
            String handoffRule,
            Integer maxToolCalls,
            Long publishedVersionId,
            List<AgentWorkflowSkillRefService.RuntimeSkillRef> skillRefs
    ) {
        private static final PublishedRuntimeBinding EMPTY =
                new PublishedRuntimeBinding(List.of(), List.of(), List.of(), "", null, null, List.of());
    }

    public record ResolvedSkill(
            String skillCode,
            String name,
            String promptFragment,
            List<String> toolWhitelist,
            List<String> kbWhitelist,
            String handoffRule,
            String outputContract,
            String riskLevel,
            String activationMode
    ) {
    }

    public record ResolvedSkillContext(
            String agentId,
            List<ResolvedSkill> skills,
            List<String> skillCodes,
            /** Effective tools exposed to the model for this session turn (policy-filtered). */
            List<String> allowedToolNames,
            /** Agent tool bindings only (allowed_tools). */
            List<String> agentDirectToolNames,
            /** Union of selected skills' toolWhitelist declarations. */
            List<String> skillDeclaredToolNames,
            /** Tools declared on skills but not on Agent direct bindings (audit). */
            List<String> skillScopedToolNames,
            List<String> defaultKnowledgeBaseIds,
            List<String> handoffRules,
            String outputContract,
            /** Custom system prompt from AgentDefinition; null means use global default. */
            String agentSystemPrompt,
            /** Preferred model name from AgentDefinition; null means use org routing. */
            String agentModel,
            /** Normalized skill code when skill-scoped tools are authorized for this session (nullable). */
            String activeSkillCode,
            /** Runtime policy max tool calls from published workflow manifest (if available). */
            Integer maxToolCalls,
            /** Bound published workflow version id (if available). */
            Long publishedVersionId,
            List<ResolvedSkillVersionRef> resolvedSkillRefs,
            List<SkillApiToolService.ResolvedSkillApiTool> skillApiTools,
            ResolvedPolicyBundle policyBundle
    ) {
    }

    public record ResolvedSkillVersionRef(
            String skillCode,
            Long skillId,
            Long skillVersionId,
            Integer skillVersionNo,
            String templateCode,
            Integer templateVersionNo,
            String referenceMode
    ) {
    }

    public record ResolvedPolicyBundle(
            String bundleCode,
            Integer versionNo,
            String promptFragment,
            List<String> handoffRules
    ) {
        public static final ResolvedPolicyBundle EMPTY =
                new ResolvedPolicyBundle("", null, "", List.of());

        public boolean hasContent() {
            return (promptFragment != null && !promptFragment.isBlank())
                    || (handoffRules != null && !handoffRules.isEmpty());
        }
    }
}
