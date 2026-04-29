package com.codehouse.ciciassistant.skill.service;

import com.codehouse.ciciassistant.cloudcc.CloudccOpenApiService;
import com.codehouse.ciciassistant.agent.service.AgentCapabilityResolverService;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionRepository;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionRepository;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionEntity;
import com.codehouse.ciciassistant.tool.service.ToolNameNormalizer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
    private final ObjectMapper objectMapper;

    public SkillResolverService(SkillDefinitionService skillDefinitionService,
                                AgentDefinitionRepository agentDefinitionRepository,
                                AgentWorkflowVersionRepository agentWorkflowVersionRepository,
                                AgentCapabilityResolverService agentCapabilityResolverService,
                                ObjectMapper objectMapper) {
        this.skillDefinitionService = skillDefinitionService;
        this.agentDefinitionRepository = agentDefinitionRepository;
        this.agentWorkflowVersionRepository = agentWorkflowVersionRepository;
        this.agentCapabilityResolverService = agentCapabilityResolverService;
        this.objectMapper = objectMapper;
    }

    public ResolvedSkillContext resolve(String orgId, String requestedAgentId, String sessionId) {
        String agentId = resolveAgentId(requestedAgentId, sessionId);
        AgentCapabilityResolverService.AgentCapabilityResolution capability = agentCapabilityResolverService.resolve(
                orgId,
                agentId,
                List.of()
        );
        PublishedRuntimeBinding publishedRuntimeBinding = resolvePublishedRuntimeBinding(orgId, agentId);
        List<String> effectiveSkillCodes = publishedRuntimeBinding.skillCodes().isEmpty()
                ? capability.effectiveSkillCodes()
                : publishedRuntimeBinding.skillCodes();
        List<SkillDefinitionEntity> entities = effectiveSkillCodes.isEmpty()
                ? skillDefinitionService.listSkillsForAgent(orgId, agentId)
                : effectiveSkillCodes.stream()
                .map(code -> skillDefinitionService.listSkills(orgId).stream().filter(item -> code.equals(item.getSkillCode())).findFirst().orElse(null))
                .filter(Objects::nonNull)
                .toList();
        if (entities.isEmpty() && !"cici-system".equals(agentId)) {
            agentId = "cici-system";
            capability = agentCapabilityResolverService.resolve(orgId, agentId, List.of());
            publishedRuntimeBinding = resolvePublishedRuntimeBinding(orgId, agentId);
            effectiveSkillCodes = publishedRuntimeBinding.skillCodes().isEmpty()
                    ? capability.effectiveSkillCodes()
                    : publishedRuntimeBinding.skillCodes();
            entities = effectiveSkillCodes.isEmpty()
                    ? skillDefinitionService.listSkillsForAgent(orgId, agentId)
                    : effectiveSkillCodes.stream()
                    .map(code -> skillDefinitionService.listSkills(orgId).stream().filter(item -> code.equals(item.getSkillCode())).findFirst().orElse(null))
                    .filter(Objects::nonNull)
                    .toList();
        }

        // Load agent-level configuration (system prompt, handoff rule, preferred model).
        Optional<AgentDefinitionEntity> agentDef = agentDefinitionRepository.findByOrgIdAndAgentId(orgId, agentId);
        String agentSystemPrompt = agentDef.map(AgentDefinitionEntity::getSystemPrompt)
                .filter(s -> s != null && !s.isBlank())
                .orElse(null);
        String agentHandoffRule = agentDef.map(AgentDefinitionEntity::getHandoffRule)
                .filter(s -> s != null && !s.isBlank())
                .orElse(null);
        String agentModel = agentDef.map(AgentDefinitionEntity::getModel)
                .filter(s -> s != null && !s.isBlank())
                .orElse(null);

        List<ResolvedSkill> skills = entities.stream()
                .map(item -> new ResolvedSkill(
                        item.getSkillCode(),
                        item.getName(),
                        item.getPromptFragment(),
                        splitCsv(item.getToolWhitelist()),
                        splitCsv(item.getKbWhitelist()),
                        item.getHandoffRule(),
                        item.getOutputContract(),
                        item.getRiskLevel()
                ))
                .toList();

        // Always merge live capability with published-manifest snapshots. Published versions are a
        // compile-time checkpoint, but chat/tool-calling must honor current agent_tool_binding rows
        // as well — otherwise MCP 等工具在「已绑定未重新发布」或清单滞后时不会出现在模型侧。
        LinkedHashSet<String> toolNames = new LinkedHashSet<>(capability.effectiveToolNames());
        toolNames.addAll(ToolNameNormalizer.canonicalizeAll(publishedRuntimeBinding.toolNames()));
        augmentBuiltinCiciToolset(agentId, toolNames);
        LinkedHashSet<String> kbIds = new LinkedHashSet<>(
                capability.effectiveKnowledgeBaseIds().stream().map(String::valueOf).toList());
        kbIds.addAll(publishedRuntimeBinding.knowledgeBaseIds());
        List<String> handoffRules = new ArrayList<>();
        String outputContract = null;

        for (ResolvedSkill skill : skills) {
            if (capability.effectiveToolNames().isEmpty()) {
                toolNames.addAll(skill.toolWhitelist());
            }
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

        // Merge agent-level handoff rule into skill handoff rules if not already present.
        capability.effectiveHandoffRules().forEach(rule -> {
            if (!handoffRules.contains(rule)) {
                handoffRules.add(rule);
            }
        });
        if (!publishedRuntimeBinding.handoffRule().isBlank()
                && !handoffRules.contains(publishedRuntimeBinding.handoffRule())) {
            handoffRules.add(publishedRuntimeBinding.handoffRule());
        }

        return new ResolvedSkillContext(
                agentId,
                skills,
                skills.stream().map(ResolvedSkill::skillCode).toList(),
                List.copyOf(toolNames),
                List.copyOf(kbIds),
                handoffRules,
                outputContract,
                agentSystemPrompt,
                agentModel,
                publishedRuntimeBinding.maxToolCalls(),
                publishedRuntimeBinding.publishedVersionId()
        );
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
            return new PublishedRuntimeBinding(
                    toStringList(dependencies.get("skills")),
                    toStringList(dependencies.get("tools")),
                    toStringList(dependencies.get("knowledgeBases")),
                    asString(policies.get("handoffRule")),
                    toInteger(policies.get("maxToolCalls")),
                    version.get().getId()
            );
        } catch (Exception ex) {
            return PublishedRuntimeBinding.EMPTY;
        }
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
                        java.util.LinkedHashMap::new
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
            Long publishedVersionId
    ) {
        private static final PublishedRuntimeBinding EMPTY =
                new PublishedRuntimeBinding(List.of(), List.of(), List.of(), "", null, null);
    }

    public record ResolvedSkill(
            String skillCode,
            String name,
            String promptFragment,
            List<String> toolWhitelist,
            List<String> kbWhitelist,
            String handoffRule,
            String outputContract,
            String riskLevel
    ) {
    }

    public record ResolvedSkillContext(
            String agentId,
            List<ResolvedSkill> skills,
            List<String> skillCodes,
            List<String> allowedToolNames,
            List<String> defaultKnowledgeBaseIds,
            List<String> handoffRules,
            String outputContract,
            /** Custom system prompt from AgentDefinition; null means use global default. */
            String agentSystemPrompt,
            /** Preferred model name from AgentDefinition; null means use org routing. */
            String agentModel,
            /** Runtime policy max tool calls from published workflow manifest (if available). */
            Integer maxToolCalls,
            /** Bound published workflow version id (if available). */
            Long publishedVersionId
    ) {
    }
}
