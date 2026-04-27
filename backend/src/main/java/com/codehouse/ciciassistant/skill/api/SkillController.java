package com.codehouse.ciciassistant.skill.api;

import com.codehouse.ciciassistant.auth.RequireOrgAdmin;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.skill.domain.AgentSkillBindingEntity;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillVersionRepository;
import com.codehouse.ciciassistant.skill.service.SkillAuthoringService;
import com.codehouse.ciciassistant.skill.service.SkillDefinitionService;
import com.codehouse.ciciassistant.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/skills")
@RequireOrgAdmin
public class SkillController {

    private final SkillDefinitionService skillDefinitionService;
    private final SkillAuthoringService skillAuthoringService;
    private final SkillVersionRepository skillVersionRepository;

    public SkillController(SkillDefinitionService skillDefinitionService,
                           SkillAuthoringService skillAuthoringService,
                           SkillVersionRepository skillVersionRepository) {
        this.skillDefinitionService = skillDefinitionService;
        this.skillAuthoringService = skillAuthoringService;
        this.skillVersionRepository = skillVersionRepository;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listSkills() {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(skillDefinitionService.listSkills(orgId).stream()
                .map(item -> toPayload(orgId, item))
                .toList());
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> createSkill(@Valid @RequestBody UpsertSkillRequest request) {
        String orgId = TenantContext.requireOrgId();
        SkillDefinitionEntity created = skillDefinitionService.createSkill(orgId, toUpsertCommand(request));
        return ApiResponse.ok(toPayload(orgId, created));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getSkill(@PathVariable Long id) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(toPayload(orgId, skillDefinitionService.getSkill(orgId, id)));
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> updateSkill(@PathVariable Long id,
                                                        @Valid @RequestBody UpsertSkillRequest request) {
        String orgId = TenantContext.requireOrgId();
        SkillDefinitionEntity updated = skillDefinitionService.updateSkill(orgId, id, toUpsertCommand(request));
        return ApiResponse.ok(toPayload(orgId, updated));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> deleteSkill(@PathVariable Long id) {
        String orgId = TenantContext.requireOrgId();
        skillDefinitionService.deleteSkill(orgId, id);
        return ApiResponse.ok(Map.of("id", id, "status", "DISABLED"));
    }

    @PostMapping("/preview")
    public ApiResponse<SkillDefinitionService.PreviewResult> preview(@Valid @RequestBody SkillPreviewRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(skillDefinitionService.previewCompile(orgId, new SkillDefinitionService.PreviewCommand(
                request.skillCode(),
                request.name(),
                request.specText(),
                request.promptFragment(),
                request.toolWhitelist(),
                request.kbWhitelist(),
                request.handoffRule(),
                request.outputContract(),
                request.riskLevel()
        )));
    }

    @PostMapping("/authoring/generate")
    public ApiResponse<SkillAuthoringService.GenerateResult> generateSkillDraft(
            @Valid @RequestBody SkillAuthoringGenerateRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(skillAuthoringService.generate(orgId, new SkillAuthoringService.GenerateCommand(
                request.sourceText(),
                request.preferredName(),
                request.preferredSkillCode(),
                request.preferredModel(),
                request.preferredProvider()
        )));
    }

    @PostMapping("/authoring/refine")
    public ApiResponse<SkillAuthoringService.GenerateResult> refineSkillDraft(
            @Valid @RequestBody SkillAuthoringRefineRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(skillAuthoringService.refine(orgId, new SkillAuthoringService.RefineCommand(
                request.sessionId(),
                request.sourceText(),
                toGeneratedDraft(request.currentSkillSpec()),
                toClarificationAnswers(request.clarificationAnswers()),
                request.preferredModel(),
                request.preferredProvider()
        )));
    }

    @PostMapping("/authoring/create")
    public ApiResponse<SkillAuthoringService.CreateResult> createSkillFromAuthoring(
            @Valid @RequestBody SkillAuthoringCreateRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(skillAuthoringService.create(orgId, new SkillAuthoringService.CreateCommand(
                request.sourceText(),
                request.sessionId(),
                toGeneratedDraft(request.skillSpec()),
                request.preferredModel(),
                request.preferredProvider()
        )));
    }

    @GetMapping("/agents/{agentId}")
    public ApiResponse<List<Map<String, Object>>> listAgentSkills(@PathVariable String agentId) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(skillDefinitionService.listSkillsForAgent(orgId, agentId).stream()
                .map(item -> toPayload(orgId, item))
                .toList());
    }

    /**
     * @deprecated Agent-Skill 绑定主入口已迁移到 /agents/{agentId}/skills。保留此接口仅用于兼容旧调用方。
     */
    @Deprecated(forRemoval = false)
    @GetMapping("/agents/{agentId}/bindings")
    public ApiResponse<List<Map<String, Object>>> listAgentBindings(@PathVariable String agentId) {
        String orgId = TenantContext.requireOrgId();
        List<AgentSkillBindingEntity> bindings = skillDefinitionService.listBindings(orgId, agentId);
        Map<Long, SkillDefinitionEntity> skillById = skillDefinitionService.listSkills(orgId).stream()
                .collect(java.util.stream.Collectors.toMap(SkillDefinitionEntity::getId, item -> item));
        return ApiResponse.ok(bindings.stream().map(binding -> {
            SkillDefinitionEntity skill = skillById.get(binding.getSkillId());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", binding.getId());
            payload.put("agentId", binding.getAgentId());
            payload.put("skillId", binding.getSkillId());
            payload.put("skillCode", skill == null ? "" : skill.getSkillCode());
            payload.put("skillName", skill == null ? "" : skill.getName());
            payload.put("activationMode", binding.getActivationMode());
            payload.put("activationCondition", binding.getActivationCondition());
            payload.put("priority", binding.getPriority());
            payload.put("enabled", binding.isEnabled());
            return payload;
        }).toList());
    }

    /**
     * @deprecated Agent-Skill 绑定主入口已迁移到 /agents/{agentId}/skills。保留此接口仅用于兼容旧调用方。
     */
    @Deprecated(forRemoval = false)
    @PutMapping("/agents/{agentId}/bindings")
    public ApiResponse<List<Map<String, Object>>> replaceAgentBindings(@PathVariable String agentId,
                                                                       @Valid @RequestBody ReplaceBindingsRequest request) {
        String orgId = TenantContext.requireOrgId();
        List<AgentSkillBindingEntity> saved = skillDefinitionService.replaceBindings(
                orgId,
                agentId,
                request.bindings() == null ? List.of() : request.bindings().stream().map(item ->
                        new SkillDefinitionService.BindingInput(
                                item.skillId(),
                                item.skillCode(),
                                item.activationMode(),
                                item.activationCondition(),
                                item.priority(),
                                item.enabled()
                        )).toList()
        );
        Map<Long, SkillDefinitionEntity> skillById = skillDefinitionService.listSkills(orgId).stream()
                .collect(java.util.stream.Collectors.toMap(SkillDefinitionEntity::getId, item -> item));
        return ApiResponse.ok(saved.stream().map(binding -> {
            SkillDefinitionEntity skill = skillById.get(binding.getSkillId());
            return Map.<String, Object>of(
                    "id", binding.getId(),
                    "agentId", binding.getAgentId(),
                    "skillId", binding.getSkillId(),
                    "skillCode", skill == null ? "" : skill.getSkillCode(),
                    "activationMode", binding.getActivationMode(),
                    "activationCondition", binding.getActivationCondition() == null ? "" : binding.getActivationCondition(),
                    "priority", binding.getPriority(),
                    "enabled", binding.isEnabled()
            );
        }).toList());
    }

    private SkillDefinitionService.UpsertCommand toUpsertCommand(UpsertSkillRequest request) {
        return new SkillDefinitionService.UpsertCommand(
                request.skillCode(),
                request.name(),
                request.description(),
                request.enabled(),
                request.promptFragment(),
                request.draftSpecText(),
                request.toolWhitelist(),
                request.kbWhitelist(),
                request.handoffRule(),
                request.outputContract(),
                request.riskLevel(),
                null,
                null,
                null
        );
    }

    private Map<String, Object> toPayload(String orgId, SkillDefinitionEntity item) {
        Map<String, Object> payload = toPayload(item);
        skillVersionRepository.findTopByOrgIdAndSkillIdOrderByVersionNoDesc(orgId, item.getId()).ifPresent(v -> {
            payload.put("latestVersionNo", v.getVersionNo());
            payload.put("latestVersionPublishStatus", v.getPublishStatus());
            payload.put("latestVersionCreatedAt", v.getCreatedAt().toString());
        });
        skillVersionRepository
                .findTopByOrgIdAndSkillIdAndPublishStatusOrderByVersionNoDesc(orgId, item.getId(), "PUBLISHED")
                .ifPresent(v -> payload.put("lastPublishedAt", v.getCreatedAt().toString()));
        return payload;
    }

    private Map<String, Object> toPayload(SkillDefinitionEntity item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", item.getId());
        payload.put("skillCode", item.getSkillCode());
        payload.put("name", item.getName());
        payload.put("description", item.getDescription());
        payload.put("builtin", item.isBuiltin());
        payload.put("enabled", item.isEnabled());
        payload.put("riskLevel", item.getRiskLevel());
        payload.put("promptFragment", item.getPromptFragment());
        payload.put("draftSpecText", item.getDraftSpecText());
        payload.put("toolWhitelist", splitCsv(item.getToolWhitelist()));
        payload.put("kbWhitelist", splitCsv(item.getKbWhitelist()));
        payload.put("handoffRule", item.getHandoffRule());
        payload.put("outputContract", item.getOutputContract());
        payload.put("createdAt", item.getCreatedAt().toString());
        payload.put("updatedAt", item.getUpdatedAt().toString());
        return payload;
    }

    private List<String> splitCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    public record UpsertSkillRequest(
            @NotBlank String skillCode,
            @NotBlank String name,
            String description,
            Boolean enabled,
            String promptFragment,
            String draftSpecText,
            List<String> toolWhitelist,
            List<String> kbWhitelist,
            String handoffRule,
            String outputContract,
            String riskLevel
    ) {
    }

    public record SkillPreviewRequest(
            @NotBlank String skillCode,
            @NotBlank String name,
            String specText,
            String promptFragment,
            List<String> toolWhitelist,
            List<String> kbWhitelist,
            String handoffRule,
            String outputContract,
            String riskLevel
    ) {
    }

    public record SkillAuthoringGenerateRequest(
            @NotBlank String sourceText,
            String preferredName,
            String preferredSkillCode,
            String preferredModel,
            String preferredProvider
    ) {
    }

    public record SkillAuthoringRefineRequest(
            String sessionId,
            String sourceText,
            @NotNull SkillAuthoringSkillSpecRequest currentSkillSpec,
            java.util.List<ClarificationAnswerRequest> clarificationAnswers,
            String preferredModel,
            String preferredProvider
    ) {
    }

    public record SkillAuthoringCreateRequest(
            @NotBlank String sourceText,
            String sessionId,
            @NotNull SkillAuthoringSkillSpecRequest skillSpec,
            String preferredModel,
            String preferredProvider
    ) {
    }

    public record ClarificationAnswerRequest(String question, String answer) {
    }

    public record SkillAuthoringSkillSpecRequest(
            String skillCode,
            String name,
            String description,
            String promptFragment,
            String draftSpecText,
            List<String> toolWhitelist,
            List<String> kbWhitelist,
            String handoffRule,
            String outputContract,
            String riskLevel,
            List<String> triggerHints,
            List<String> userIntentExamples,
            List<String> clarificationQuestions,
            List<String> warnings
    ) {
    }

    public record ReplaceBindingsRequest(List<BindingInputRequest> bindings) {
    }

    public record BindingInputRequest(
            Long skillId,
            String skillCode,
            String activationMode,
            String activationCondition,
            Integer priority,
            Boolean enabled
    ) {
    }

    private java.util.List<SkillAuthoringService.ClarificationAnswer> toClarificationAnswers(
            java.util.List<ClarificationAnswerRequest> raw) {
        if (raw == null || raw.isEmpty()) {
            return java.util.List.of();
        }
        java.util.List<SkillAuthoringService.ClarificationAnswer> out = new java.util.ArrayList<>();
        for (ClarificationAnswerRequest item : raw) {
            if (item == null) {
                continue;
            }
            out.add(new SkillAuthoringService.ClarificationAnswer(item.question(), item.answer()));
        }
        return java.util.List.copyOf(out);
    }

    private com.codehouse.ciciassistant.skill.service.BuiltinSkillCreatorService.GeneratedSkillDraft toGeneratedDraft(
            SkillAuthoringSkillSpecRequest request) {
        if (request == null) {
            return null;
        }
        return new com.codehouse.ciciassistant.skill.service.BuiltinSkillCreatorService.GeneratedSkillDraft(
                request.skillCode(),
                request.name(),
                request.description(),
                request.promptFragment(),
                request.draftSpecText(),
                request.toolWhitelist() == null ? List.of() : request.toolWhitelist(),
                request.kbWhitelist() == null ? List.of() : request.kbWhitelist(),
                request.handoffRule(),
                request.outputContract(),
                request.riskLevel(),
                request.triggerHints() == null ? List.of() : request.triggerHints(),
                request.userIntentExamples() == null ? List.of() : request.userIntentExamples(),
                request.clarificationQuestions() == null ? List.of() : request.clarificationQuestions(),
                request.warnings() == null ? List.of() : request.warnings()
        );
    }
}
