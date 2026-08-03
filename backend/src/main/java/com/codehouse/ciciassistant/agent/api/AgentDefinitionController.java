package com.codehouse.ciciassistant.agent.api;

import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentPermission;
import com.codehouse.ciciassistant.agent.domain.AgentSpecEntity;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionEntity;
import com.codehouse.ciciassistant.agent.service.AgentAccessControlService;
import com.codehouse.ciciassistant.agent.service.AgentDefinitionService;
import com.codehouse.ciciassistant.agent.service.AgentEvaluationService;
import com.codehouse.ciciassistant.agent.service.AgentEvaluationControlPlaneService;
import com.codehouse.ciciassistant.agent.service.AgentProductionReadinessService;
import com.codehouse.ciciassistant.agent.service.AgentServicePrincipalExecutionService;
import com.codehouse.ciciassistant.agent.service.AgentSkillBindingService;
import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.tenant.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agents")
public class AgentDefinitionController {

    private final AgentDefinitionService agentDefinitionService;
    private final AgentSkillBindingService agentSkillBindingService;
    private final AgentAccessControlService accessControlService;
    private final AgentProductionReadinessService productionReadinessService;
    private final AgentServicePrincipalExecutionService executionPrincipalService;
    private final AgentEvaluationService agentEvaluationService;
    private final AgentEvaluationControlPlaneService evaluationControlPlaneService;
    private final ObjectMapper objectMapper;

    public AgentDefinitionController(AgentDefinitionService agentDefinitionService,
                                     AgentSkillBindingService agentSkillBindingService,
                                     AgentAccessControlService accessControlService,
                                     AgentProductionReadinessService productionReadinessService,
                                     AgentServicePrincipalExecutionService executionPrincipalService,
                                     AgentEvaluationService agentEvaluationService,
                                     AgentEvaluationControlPlaneService evaluationControlPlaneService,
                                     ObjectMapper objectMapper) {
        this.agentDefinitionService = agentDefinitionService;
        this.agentSkillBindingService = agentSkillBindingService;
        this.accessControlService = accessControlService;
        this.productionReadinessService = productionReadinessService;
        this.executionPrincipalService = executionPrincipalService;
        this.agentEvaluationService = agentEvaluationService;
        this.evaluationControlPlaneService = evaluationControlPlaneService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        String companyId = TenantContext.requireCompanyId();
        String userId = requireUserId();
        List<String> roles = TenantContext.getRoles();
        return ApiResponse.ok(agentDefinitionService.listWithChannels(companyId)
                .stream()
                .filter(item -> accessControlService.can(companyId, userId, roles, item.definition().getAgentId(), AgentPermission.VIEW)
                        || accessControlService.can(companyId, userId, roles, item.definition().getAgentId(), AgentPermission.RUN))
                .map(item -> toListPayload(companyId, userId, roles, item))
                .toList());
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody CreateAgentRequest request) {
        String companyId = TenantContext.requireCompanyId();
        String userId = requireUserId();
        requireCompanyAdmin();
        AgentDefinitionService.AgentDetail detail = agentDefinitionService.create(companyId, new AgentDefinitionService.CreateCommand(
                request.agentId(),
                request.name(),
                request.summary(),
                request.greeting(),
                request.model(),
                request.systemPrompt(),
                request.handoffRule(),
                request.safetyLevel(),
                request.executionMode(),
                request.versionLabel(),
                request.avatarBase64(),
                userId,
                request.builtin(),
                request.enabled(),
                request.specText(),
                request.knowledgeBaseIds(),
                request.toolIds(),
                request.channels(),
                request.publishConfigs()
        ));
        return ApiResponse.ok(toDetailPayload(companyId, userId, TenantContext.getRoles(), detail));
    }

    @GetMapping("/{agentId}")
    public ApiResponse<Map<String, Object>> get(@PathVariable String agentId) {
        String companyId = TenantContext.requireCompanyId();
        String userId = requireUserId();
        List<String> roles = TenantContext.getRoles();
        AgentDefinitionService.AgentDetail detail = agentDefinitionService.get(companyId, agentId);
        accessControlService.require(companyId, userId, roles, agentId, AgentPermission.VIEW);
        return ApiResponse.ok(toDetailPayload(companyId, userId, roles, detail));
    }

    @PutMapping("/{agentId}")
    public ApiResponse<Map<String, Object>> updateDefinition(@PathVariable String agentId,
                                                              @Valid @RequestBody UpdateDefinitionRequest request) {
        String companyId = TenantContext.requireCompanyId();
        String userId = requireUserId();
        List<String> roles = TenantContext.getRoles();
        accessControlService.require(companyId, userId, roles, agentId, AgentPermission.EDIT);
        AgentDefinitionEntity updated = agentDefinitionService.updateDefinition(companyId, agentId, new AgentDefinitionService.UpsertDefinitionCommand(
                request.name(),
                request.summary(),
                request.greeting(),
                request.model(),
                request.systemPrompt(),
                request.handoffRule(),
                request.safetyLevel(),
                request.executionMode(),
                request.versionLabel(),
                request.avatarBase64(),
                request.enabled()
        ));
        return ApiResponse.ok(toDefinitionPayload(companyId, userId, roles, updated));
    }

    @DeleteMapping("/{agentId}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable String agentId) {
        String companyId = TenantContext.requireCompanyId();
        agentDefinitionService.get(companyId, agentId);
        accessControlService.require(companyId, requireUserId(), TenantContext.getRoles(), agentId, AgentPermission.MANAGE);
        AgentDefinitionService.AgentDeleteResult result = agentDefinitionService.deleteCustomAgent(companyId, agentId);
        return ApiResponse.ok(Map.of(
                "agentId", result.agentId(),
                "name", result.name(),
                "deleted", true,
                "retentionMessage", result.retentionMessage()
        ));
    }

    @PutMapping("/{agentId}/spec")
    public ApiResponse<Map<String, Object>> updateSpec(@PathVariable String agentId,
                                                        @Valid @RequestBody UpdateSpecRequest request) {
        String companyId = TenantContext.requireCompanyId();
        accessControlService.require(companyId, requireUserId(), TenantContext.getRoles(), agentId, AgentPermission.EDIT);
        AgentSpecEntity updated = agentDefinitionService.updateSpec(companyId, agentId, request.specText());
        return ApiResponse.ok(Map.of(
                "agentId", updated.getAgentId(),
                "specText", updated.getSpecText(),
                "updatedAt", updated.getUpdatedAt().toString()
        ));
    }

    @GetMapping("/{agentId}/bindings")
    public ApiResponse<Map<String, Object>> getBindings(@PathVariable String agentId) {
        String companyId = TenantContext.requireCompanyId();
        accessControlService.require(companyId, requireUserId(), TenantContext.getRoles(), agentId, AgentPermission.VIEW);
        AgentDefinitionService.AgentDetail detail = agentDefinitionService.get(companyId, agentId);
        return ApiResponse.ok(Map.of(
                "agentId", detail.definition().getAgentId(),
                "knowledgeBaseIds", detail.knowledgeBaseIds(),
                "toolIds", detail.toolIds(),
                "channels", detail.channels()
        ));
    }

    @GetMapping("/{agentId}/skills")
    public ApiResponse<Map<String, Object>> listSkillBindings(@PathVariable String agentId) {
        String companyId = TenantContext.requireCompanyId();
        accessControlService.require(companyId, requireUserId(), TenantContext.getRoles(), agentId, AgentPermission.VIEW);
        return ApiResponse.ok(Map.of(
                "bindings", agentSkillBindingService.listBindings(companyId, agentId)
        ));
    }

    @PutMapping("/{agentId}/skills")
    public ApiResponse<Map<String, Object>> replaceSkillBindings(@PathVariable String agentId,
                                                                 @Valid @RequestBody ReplaceSkillBindingsRequest request) {
        String companyId = TenantContext.requireCompanyId();
        accessControlService.require(companyId, requireUserId(), TenantContext.getRoles(), agentId, AgentPermission.EDIT);
        List<AgentSkillBindingService.AgentSkillBindingView> saved = agentSkillBindingService.replaceBindings(
                companyId,
                agentId,
                request.bindings() == null ? List.of() : request.bindings().stream().map(item ->
                        new AgentSkillBindingService.ReplaceBindingInput(
                                item.skillId(),
                                item.skillCode(),
                                item.activationMode(),
                                item.activationCondition(),
                                item.priority(),
                                item.enabled()
                        )).toList()
        );
        return ApiResponse.ok(Map.of("bindings", saved));
    }

    @PutMapping("/{agentId}/bindings")
    public ApiResponse<Map<String, Object>> replaceBindings(@PathVariable String agentId,
                                                             @Valid @RequestBody ReplaceBindingsRequest request) {
        String companyId = TenantContext.requireCompanyId();
        accessControlService.require(companyId, requireUserId(), TenantContext.getRoles(), agentId, AgentPermission.EDIT);
        AgentDefinitionService.AgentBindings bindings = agentDefinitionService.replaceBindings(
                companyId,
                agentId,
                new AgentDefinitionService.ReplaceBindingsCommand(
                        request.knowledgeBaseIds(),
                        request.toolIds(),
                        request.channels()
                )
        );
        return ApiResponse.ok(Map.of(
                "agentId", agentId,
                "knowledgeBaseIds", bindings.knowledgeBaseIds(),
                "toolIds", bindings.toolIds(),
                "channels", bindings.channels()
        ));
    }

    @GetMapping("/{agentId}/execution-principal")
    public ApiResponse<Map<String, Object>> getExecutionPrincipal(@PathVariable String agentId) {
        String companyId = TenantContext.requireCompanyId();
        accessControlService.require(companyId, requireUserId(), TenantContext.getRoles(), agentId, AgentPermission.VIEW);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agentId", agentId);
        payload.put("binding", executionPrincipalService.findBinding(companyId, agentId).orElse(null));
        return ApiResponse.ok(payload);
    }

    @PutMapping("/{agentId}/execution-principal")
    public ApiResponse<Map<String, Object>> configureExecutionPrincipal(
            @PathVariable String agentId,
            @Valid @RequestBody ConfigureExecutionPrincipalRequest request) {
        String companyId = TenantContext.requireCompanyId();
        String userId = requireUserId();
        accessControlService.require(companyId, userId, TenantContext.getRoles(), agentId, AgentPermission.EDIT);
        AgentServicePrincipalExecutionService.BindingView binding = executionPrincipalService.configure(
                companyId,
                agentId,
                request.servicePrincipalId(),
                request.enabled() == null || request.enabled(),
                userId);
        return ApiResponse.ok(Map.of("agentId", agentId, "binding", binding));
    }

    @PutMapping("/{agentId}/publish-configs")
    public ApiResponse<Map<String, Object>> replacePublishConfigs(@PathVariable String agentId,
                                                                   @Valid @RequestBody ReplacePublishConfigsRequest request) {
        String companyId = TenantContext.requireCompanyId();
        accessControlService.require(companyId, requireUserId(), TenantContext.getRoles(), agentId, AgentPermission.EDIT);
        Map<String, Object> saved = agentDefinitionService.replacePublishConfigs(companyId, agentId, request.publishConfigs());
        return ApiResponse.ok(Map.of(
                "agentId", agentId,
                "publishConfigs", saved
        ));
    }

    @GetMapping("/{agentId}/versions")
    public ApiResponse<List<Map<String, Object>>> listVersions(@PathVariable String agentId) {
        String companyId = TenantContext.requireCompanyId();
        accessControlService.require(companyId, requireUserId(), TenantContext.getRoles(), agentId, AgentPermission.VIEW);
        return ApiResponse.ok(agentDefinitionService.listVersions(companyId, agentId).stream().map(this::toVersionPayload).toList());
    }

    @GetMapping("/{agentId}/readiness")
    public ApiResponse<AgentProductionReadinessService.ReadinessResult> readiness(
            @PathVariable String agentId,
            @RequestParam(name = "versionNo", required = false) Integer versionNo) {
        String companyId = TenantContext.requireCompanyId();
        accessControlService.require(companyId, requireUserId(), TenantContext.getRoles(), agentId, AgentPermission.PUBLISH);
        return ApiResponse.ok(productionReadinessService.check(companyId, agentId, versionNo));
    }

    @GetMapping("/{agentId}/evaluation/suites")
    public ApiResponse<List<Map<String, Object>>> listEvaluationSuites(@PathVariable String agentId) {
        String companyId = TenantContext.requireCompanyId();
        accessControlService.require(companyId, requireUserId(), TenantContext.getRoles(), agentId, AgentPermission.VIEW);
        return ApiResponse.ok(agentEvaluationService.listSuites(companyId, agentId));
    }

    @PostMapping("/{agentId}/evaluation/suites")
    public ApiResponse<Map<String, Object>> createEvaluationSuite(@PathVariable String agentId,
                                                                   @Valid @RequestBody EvaluationSuiteRequest request) {
        String companyId = TenantContext.requireCompanyId();
        accessControlService.require(companyId, requireUserId(), TenantContext.getRoles(), agentId, AgentPermission.EDIT);
        return ApiResponse.ok(agentEvaluationService.createSuite(companyId, agentId, new AgentEvaluationService.SuiteCommand(
                request.name(),
                request.description(),
                request.gateMode(),
                request.minPassRate()
        )));
    }

    @GetMapping("/{agentId}/evaluation/suites/{suiteId}/cases")
    public ApiResponse<List<Map<String, Object>>> listEvaluationCases(@PathVariable String agentId,
                                                                       @PathVariable Long suiteId) {
        String companyId = TenantContext.requireCompanyId();
        accessControlService.require(companyId, requireUserId(), TenantContext.getRoles(), agentId, AgentPermission.VIEW);
        return ApiResponse.ok(agentEvaluationService.listCases(companyId, agentId, suiteId));
    }

    @PostMapping("/{agentId}/evaluation/suites/{suiteId}/cases")
    public ApiResponse<Map<String, Object>> addEvaluationCase(@PathVariable String agentId,
                                                               @PathVariable Long suiteId,
                                                               @Valid @RequestBody EvaluationCaseRequest request) {
        String companyId = TenantContext.requireCompanyId();
        accessControlService.require(companyId, requireUserId(), TenantContext.getRoles(), agentId, AgentPermission.EDIT);
        return ApiResponse.ok(agentEvaluationService.addCase(companyId, agentId, suiteId, new AgentEvaluationService.CaseCommand(
                request.name(),
                request.inputText(),
                request.assertionType(),
                request.expectedText(),
                request.forbiddenText(),
                request.expectedStatus(),
                request.requiredToolName(),
                request.forbiddenToolName(),
                request.priority()
        )));
    }

    @PostMapping("/{agentId}/evaluation/suites/{suiteId}/runs")
    public ApiResponse<Map<String, Object>> runEvaluationSuite(@PathVariable String agentId,
                                                                @PathVariable Long suiteId,
                                                                @Valid @RequestBody EvaluationRunRequest request) {
        String companyId = TenantContext.requireCompanyId();
        accessControlService.require(companyId, requireUserId(), TenantContext.getRoles(), agentId, AgentPermission.PUBLISH);
        return ApiResponse.ok(agentEvaluationService.runSuite(companyId, agentId, suiteId, request.versionNo()));
    }

    @GetMapping("/{agentId}/evaluation/suites/{suiteId}/runs")
    public ApiResponse<List<Map<String, Object>>> listEvaluationRuns(@PathVariable String agentId,
                                                                      @PathVariable Long suiteId) {
        String companyId = TenantContext.requireCompanyId();
        accessControlService.require(companyId, requireUserId(), TenantContext.getRoles(), agentId, AgentPermission.VIEW);
        return ApiResponse.ok(agentEvaluationService.listRuns(companyId, agentId, suiteId));
    }

    @GetMapping("/{agentId}/evaluation/runs/{runId}/results")
    public ApiResponse<List<Map<String, Object>>> listEvaluationResults(@PathVariable String agentId,
                                                                         @PathVariable Long runId) {
        String companyId = TenantContext.requireCompanyId();
        accessControlService.require(companyId, requireUserId(), TenantContext.getRoles(), agentId, AgentPermission.VIEW);
        return ApiResponse.ok(agentEvaluationService.listResults(companyId, agentId, runId));
    }

    @PostMapping("/{agentId}/publish")
    public ApiResponse<Map<String, Object>> publish(@PathVariable String agentId,
                                                     @Valid @RequestBody VersionActionRequest request) {
        String companyId = TenantContext.requireCompanyId();
        accessControlService.require(companyId, requireUserId(), TenantContext.getRoles(), agentId, AgentPermission.PUBLISH);
        AgentWorkflowVersionEntity version = agentDefinitionService.publishVersion(companyId, agentId, request.versionNo());
        try {
            evaluationControlPlaneService.recordPublishReference(
                    companyId, agentId, version.getVersionNo(), requireUserId());
        } catch (RuntimeException ignored) {
            // Publishing has already completed; evaluation audit persistence must not turn success into an API failure.
        }
        Map<String, Object> payload = new LinkedHashMap<>(toVersionPayload(version));
        payload.put("published", true);
        payload.put("readiness", productionReadinessService.check(companyId, agentId, version.getVersionNo()));
        return ApiResponse.ok(payload);
    }

    @PostMapping("/{agentId}/rollback")
    public ApiResponse<Map<String, Object>> rollback(@PathVariable String agentId,
                                                      @Valid @RequestBody VersionActionRequest request) {
        String companyId = TenantContext.requireCompanyId();
        accessControlService.require(companyId, requireUserId(), TenantContext.getRoles(), agentId, AgentPermission.PUBLISH);
        AgentWorkflowVersionEntity version = agentDefinitionService.rollbackVersion(companyId, agentId, request.versionNo());
        return ApiResponse.ok(toVersionPayload(version));
    }

    @GetMapping("/{agentId}/access-grants")
    public ApiResponse<Map<String, Object>> listAccessGrants(@PathVariable String agentId) {
        String companyId = TenantContext.requireCompanyId();
        accessControlService.require(companyId, requireUserId(), TenantContext.getRoles(), agentId, AgentPermission.MANAGE);
        return ApiResponse.ok(Map.of(
                "agentId", agentId,
                "grants", accessControlService.listGrants(companyId, agentId)
        ));
    }

    @PutMapping("/{agentId}/access-grants")
    public ApiResponse<Map<String, Object>> replaceAccessGrants(@PathVariable String agentId,
                                                                @Valid @RequestBody ReplaceAccessGrantsRequest request) {
        String companyId = TenantContext.requireCompanyId();
        String userId = requireUserId();
        accessControlService.require(companyId, userId, TenantContext.getRoles(), agentId, AgentPermission.MANAGE);
        return ApiResponse.ok(Map.of(
                "agentId", agentId,
                "grants", accessControlService.replaceGrants(
                        companyId,
                        agentId,
                        userId,
                        new AgentAccessControlService.ReplaceGrantsCommand(request.grants()))
        ));
    }

    private Map<String, Object> toDetailPayload(String companyId,
                                                String userId,
                                                List<String> roles,
                                                AgentDefinitionService.AgentDetail detail) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.putAll(toDefinitionPayload(companyId, userId, roles, detail.definition()));
        payload.put("specText", detail.specText());
        payload.put("knowledgeBaseIds", detail.knowledgeBaseIds());
        payload.put("toolIds", detail.toolIds());
        payload.put("channels", detail.channels());
        payload.put("publishConfigs", detail.publishConfigs());
        payload.put("skillBindings", agentSkillBindingService.listBindings(companyId, detail.definition().getAgentId()));
        payload.put("executionPrincipal", executionPrincipalService
                .findBinding(companyId, detail.definition().getAgentId()).orElse(null));
        return payload;
    }

    private Map<String, Object> toDefinitionPayload(String companyId, String userId, List<String> roles, AgentDefinitionEntity item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", item.getId());
        payload.put("agentId", item.getAgentId());
        payload.put("name", item.getName());
        payload.put("summary", item.getSummary());
        payload.put("greeting", item.getGreeting());
        payload.put("model", item.getModel());
        payload.put("systemPrompt", item.getSystemPrompt());
        payload.put("handoffRule", item.getHandoffRule());
        payload.put("safetyLevel", item.getSafetyLevel());
        payload.put("executionMode", item.getExecutionMode());
        payload.put("versionLabel", item.getVersionLabel());
        payload.put("avatarBase64", item.getAvatarBase64());
        payload.put("ownerUserId", item.getOwnerUserId() == null ? "" : item.getOwnerUserId());
        payload.put("builtin", item.isBuiltin());
        payload.put("enabled", item.isEnabled());
        payload.put("publishedVersionId", item.getPublishedVersionId());
        payload.put("createdAt", item.getCreatedAt().toString());
        payload.put("updatedAt", item.getUpdatedAt().toString());
        payload.put("access", accessControlService.permissionPayload(companyId, userId, roles, item.getAgentId()));
        return payload;
    }

    private String requireUserId() {
        return TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
    }

    private void requireCompanyAdmin() {
        if (TenantContext.getRoles().stream().noneMatch(RoleCodes::isOrgAdminRole)) {
            throw new ForbiddenException("需要组织管理员权限");
        }
    }

    private Map<String, Object> toListPayload(String companyId,
                                              String userId,
                                              List<String> roles,
                                              AgentDefinitionService.AgentListItem item) {
        Map<String, Object> payload = toDefinitionPayload(companyId, userId, roles, item.definition());
        payload.put("channels", item.channels());
        return payload;
    }

    private Map<String, Object> toVersionPayload(AgentWorkflowVersionEntity item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        List<String> compileSummary = readStringList(item.getCompileSummary());
        List<String> changeLog = readStringList(item.getChangeLog());
        if (changeLog.isEmpty() && !compileSummary.isEmpty()) {
            changeLog = compileSummary.stream().limit(2).toList();
        }
        payload.put("id", item.getId());
        payload.put("agentId", item.getAgentId());
        payload.put("versionNo", item.getVersionNo());
        payload.put("versionLabel", item.getVersionLabel() == null ? "" : item.getVersionLabel());
        payload.put("publishStatus", item.getPublishStatus());
        payload.put("createdAt", item.getCreatedAt().toString());
        payload.put("compileSummary", compileSummary);
        payload.put("changeLog", changeLog);
        return payload;
    }

    private List<String> readStringList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<String>>() {
            });
        } catch (Exception ignore) {
            List<String> fallback = new ArrayList<>();
            fallback.add(raw);
            return fallback;
        }
    }

    public record CreateAgentRequest(
            @NotBlank String agentId,
            @NotBlank String name,
            String summary,
            String greeting,
            @NotBlank String model,
            String systemPrompt,
            String handoffRule,
            String safetyLevel,
            String executionMode,
            String versionLabel,
            String avatarBase64,
            Boolean builtin,
            Boolean enabled,
            String specText,
            List<Long> knowledgeBaseIds,
            List<String> toolIds,
            List<String> channels,
            Map<String, Object> publishConfigs
    ) {
    }

    public record UpdateDefinitionRequest(
            @NotBlank String name,
            String summary,
            String greeting,
            @NotBlank String model,
            String systemPrompt,
            String handoffRule,
            String safetyLevel,
            String executionMode,
            String versionLabel,
            String avatarBase64,
            Boolean enabled
    ) {
    }

    public record UpdateSpecRequest(String specText) {
    }

    public record ReplaceBindingsRequest(
            List<Long> knowledgeBaseIds,
            List<String> toolIds,
            List<String> channels
    ) {
    }

    public record ReplaceSkillBindingsRequest(List<SkillBindingInput> bindings) {
    }

    public record SkillBindingInput(
            Long skillId,
            String skillCode,
            String activationMode,
            String activationCondition,
            Integer priority,
            Boolean enabled
    ) {
    }

    public record ReplacePublishConfigsRequest(Map<String, Object> publishConfigs) {
    }

    public record ConfigureExecutionPrincipalRequest(
            @NotBlank String servicePrincipalId,
            Boolean enabled
    ) {
    }

    public record VersionActionRequest(Integer versionNo) {
    }

    public record EvaluationSuiteRequest(
            @NotBlank String name,
            String description,
            String gateMode,
            Double minPassRate
    ) {
    }

    public record EvaluationCaseRequest(
            @NotBlank String name,
            @NotBlank String inputText,
            @NotBlank String assertionType,
            String expectedText,
            String forbiddenText,
            String expectedStatus,
            String requiredToolName,
            String forbiddenToolName,
            String priority
    ) {
    }

    public record EvaluationRunRequest(Integer versionNo) {
    }

    public record ReplaceAccessGrantsRequest(List<AgentAccessControlService.GrantInput> grants) {
    }
}
