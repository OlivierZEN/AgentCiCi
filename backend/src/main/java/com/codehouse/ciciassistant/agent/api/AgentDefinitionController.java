package com.codehouse.ciciassistant.agent.api;

import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentSpecEntity;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionEntity;
import com.codehouse.ciciassistant.agent.service.AgentDefinitionService;
import com.codehouse.ciciassistant.agent.service.AgentSkillBindingService;
import com.codehouse.ciciassistant.auth.RequireOrgAdmin;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.tenant.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agents")
@RequireOrgAdmin
public class AgentDefinitionController {

    private final AgentDefinitionService agentDefinitionService;
    private final AgentSkillBindingService agentSkillBindingService;
    private final ObjectMapper objectMapper;

    public AgentDefinitionController(AgentDefinitionService agentDefinitionService,
                                     AgentSkillBindingService agentSkillBindingService,
                                     ObjectMapper objectMapper) {
        this.agentDefinitionService = agentDefinitionService;
        this.agentSkillBindingService = agentSkillBindingService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(agentDefinitionService.list(orgId).stream().map(this::toDefinitionPayload).toList());
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody CreateAgentRequest request) {
        String orgId = TenantContext.requireOrgId();
        AgentDefinitionService.AgentDetail detail = agentDefinitionService.create(orgId, new AgentDefinitionService.CreateCommand(
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
                request.builtin(),
                request.enabled(),
                request.specText(),
                request.knowledgeBaseIds(),
                request.toolIds(),
                request.channels(),
                request.publishConfigs()
        ));
        return ApiResponse.ok(toDetailPayload(orgId, detail));
    }

    @GetMapping("/{agentId}")
    public ApiResponse<Map<String, Object>> get(@PathVariable String agentId) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(toDetailPayload(orgId, agentDefinitionService.get(orgId, agentId)));
    }

    @PutMapping("/{agentId}")
    public ApiResponse<Map<String, Object>> updateDefinition(@PathVariable String agentId,
                                                              @Valid @RequestBody UpdateDefinitionRequest request) {
        String orgId = TenantContext.requireOrgId();
        AgentDefinitionEntity updated = agentDefinitionService.updateDefinition(orgId, agentId, new AgentDefinitionService.UpsertDefinitionCommand(
                request.name(),
                request.summary(),
                request.greeting(),
                request.model(),
                request.systemPrompt(),
                request.handoffRule(),
                request.safetyLevel(),
                request.executionMode(),
                request.versionLabel(),
                request.enabled()
        ));
        return ApiResponse.ok(toDefinitionPayload(updated));
    }

    @PutMapping("/{agentId}/spec")
    public ApiResponse<Map<String, Object>> updateSpec(@PathVariable String agentId,
                                                        @Valid @RequestBody UpdateSpecRequest request) {
        String orgId = TenantContext.requireOrgId();
        AgentSpecEntity updated = agentDefinitionService.updateSpec(orgId, agentId, request.specText());
        return ApiResponse.ok(Map.of(
                "agentId", updated.getAgentId(),
                "specText", updated.getSpecText(),
                "updatedAt", updated.getUpdatedAt().toString()
        ));
    }

    @GetMapping("/{agentId}/bindings")
    public ApiResponse<Map<String, Object>> getBindings(@PathVariable String agentId) {
        String orgId = TenantContext.requireOrgId();
        AgentDefinitionService.AgentDetail detail = agentDefinitionService.get(orgId, agentId);
        return ApiResponse.ok(Map.of(
                "agentId", detail.definition().getAgentId(),
                "knowledgeBaseIds", detail.knowledgeBaseIds(),
                "toolIds", detail.toolIds(),
                "channels", detail.channels()
        ));
    }

    @GetMapping("/{agentId}/skills")
    public ApiResponse<Map<String, Object>> listSkillBindings(@PathVariable String agentId) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(Map.of(
                "bindings", agentSkillBindingService.listBindings(orgId, agentId)
        ));
    }

    @PutMapping("/{agentId}/skills")
    public ApiResponse<Map<String, Object>> replaceSkillBindings(@PathVariable String agentId,
                                                                 @Valid @RequestBody ReplaceSkillBindingsRequest request) {
        String orgId = TenantContext.requireOrgId();
        List<AgentSkillBindingService.AgentSkillBindingView> saved = agentSkillBindingService.replaceBindings(
                orgId,
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
        String orgId = TenantContext.requireOrgId();
        AgentDefinitionService.AgentBindings bindings = agentDefinitionService.replaceBindings(
                orgId,
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

    @PutMapping("/{agentId}/publish-configs")
    public ApiResponse<Map<String, Object>> replacePublishConfigs(@PathVariable String agentId,
                                                                   @Valid @RequestBody ReplacePublishConfigsRequest request) {
        String orgId = TenantContext.requireOrgId();
        Map<String, Object> saved = agentDefinitionService.replacePublishConfigs(orgId, agentId, request.publishConfigs());
        return ApiResponse.ok(Map.of(
                "agentId", agentId,
                "publishConfigs", saved
        ));
    }

    @GetMapping("/{agentId}/versions")
    public ApiResponse<List<Map<String, Object>>> listVersions(@PathVariable String agentId) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(agentDefinitionService.listVersions(orgId, agentId).stream().map(this::toVersionPayload).toList());
    }

    @PostMapping("/{agentId}/publish")
    public ApiResponse<Map<String, Object>> publish(@PathVariable String agentId,
                                                     @Valid @RequestBody VersionActionRequest request) {
        String orgId = TenantContext.requireOrgId();
        AgentWorkflowVersionEntity version = agentDefinitionService.publishVersion(orgId, agentId, request.versionNo());
        return ApiResponse.ok(toVersionPayload(version));
    }

    @PostMapping("/{agentId}/rollback")
    public ApiResponse<Map<String, Object>> rollback(@PathVariable String agentId,
                                                      @Valid @RequestBody VersionActionRequest request) {
        String orgId = TenantContext.requireOrgId();
        AgentWorkflowVersionEntity version = agentDefinitionService.rollbackVersion(orgId, agentId, request.versionNo());
        return ApiResponse.ok(toVersionPayload(version));
    }

    private Map<String, Object> toDetailPayload(String orgId, AgentDefinitionService.AgentDetail detail) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.putAll(toDefinitionPayload(detail.definition()));
        payload.put("specText", detail.specText());
        payload.put("knowledgeBaseIds", detail.knowledgeBaseIds());
        payload.put("toolIds", detail.toolIds());
        payload.put("channels", detail.channels());
        payload.put("publishConfigs", detail.publishConfigs());
        payload.put("skillBindings", agentSkillBindingService.listBindings(orgId, detail.definition().getAgentId()));
        return payload;
    }

    private Map<String, Object> toDefinitionPayload(AgentDefinitionEntity item) {
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
        payload.put("builtin", item.isBuiltin());
        payload.put("enabled", item.isEnabled());
        payload.put("publishedVersionId", item.getPublishedVersionId());
        payload.put("createdAt", item.getCreatedAt().toString());
        payload.put("updatedAt", item.getUpdatedAt().toString());
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

    public record VersionActionRequest(Integer versionNo) {
    }
}
