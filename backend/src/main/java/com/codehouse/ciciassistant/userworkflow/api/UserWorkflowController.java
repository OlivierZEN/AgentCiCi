package com.codehouse.ciciassistant.userworkflow.api;

import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.tenant.TenantContext;
import com.codehouse.ciciassistant.userworkflow.domain.UserAgentProfileEntity;
import com.codehouse.ciciassistant.userworkflow.domain.UserQuickCommandEntity;
import com.codehouse.ciciassistant.userworkflow.domain.UserWorkflowExecutionEntity;
import com.codehouse.ciciassistant.userworkflow.domain.UserWorkflowSpecEntity;
import com.codehouse.ciciassistant.userworkflow.domain.UserWorkflowTriggerEntity;
import com.codehouse.ciciassistant.userworkflow.domain.UserWorkflowVersionEntity;
import com.codehouse.ciciassistant.userworkflow.service.UserWorkflowService;
import jakarta.validation.Valid;
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
@RequestMapping("/me/agents/{agentId}/workflow")
public class UserWorkflowController {

    private final UserWorkflowService userWorkflowService;

    public UserWorkflowController(UserWorkflowService userWorkflowService) {
        this.userWorkflowService = userWorkflowService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> get(@PathVariable String agentId) {
        String orgId = TenantContext.requireOrgId();
        String userId = currentUser();
        UserWorkflowService.WorkflowBundle bundle = userWorkflowService.getBundle(orgId, userId, agentId);
        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, Object> agentPayload = new LinkedHashMap<>();
        agentPayload.put("agentId", bundle.agent().agentId());
        agentPayload.put("name", bundle.agent().definition().getName());
        agentPayload.put("publishedVersionId", bundle.agent().definition().getPublishedVersionId());
        agentPayload.put("allowedToolIds", bundle.agent().allowedToolIds());
        payload.put("agent", agentPayload);
        payload.put("profile", toProfilePayload(bundle.profile()));
        payload.put("spec", toSpecPayload(bundle.spec()));
        payload.put("versions", bundle.versions().stream().map(this::toVersionPayload).toList());
        payload.put("triggers", bundle.triggers().stream().map(this::toTriggerPayload).toList());
        payload.put("executions", bundle.executions().stream().map(this::toExecutionPayload).toList());
        if (bundle.latestDraftVersion() != null) {
            payload.put("latestDraftVersion", toVersionDetailPayload(bundle.latestDraftVersion()));
        }
        return ApiResponse.ok(payload);
    }

    @PutMapping("/profile")
    public ApiResponse<Map<String, Object>> updateProfile(@PathVariable String agentId,
                                                          @Valid @RequestBody UpdateProfileRequest request) {
        String orgId = TenantContext.requireOrgId();
        String userId = currentUser();
        UserAgentProfileEntity updated = userWorkflowService.updateProfile(
                orgId,
                userId,
                agentId,
                new UserWorkflowService.UpdateProfileCommand(
                        request.timezone(),
                        request.locale(),
                        request.notificationTarget(),
                        request.personalContext(),
                        request.enabled()
                )
        );
        return ApiResponse.ok(toProfilePayload(updated));
    }

    @PutMapping("/spec")
    public ApiResponse<Map<String, Object>> updateSpec(@PathVariable String agentId,
                                                       @Valid @RequestBody UpdateSpecRequest request) {
        String orgId = TenantContext.requireOrgId();
        String userId = currentUser();
        UserWorkflowSpecEntity updated = userWorkflowService.updateSpec(orgId, userId, agentId, request.sourceText());
        return ApiResponse.ok(toSpecPayload(updated));
    }

    @PostMapping("/compile")
    public ApiResponse<Map<String, Object>> compile(@PathVariable String agentId,
                                                    @Valid @RequestBody UpdateSpecRequest request) {
        String orgId = TenantContext.requireOrgId();
        String userId = currentUser();
        UserWorkflowService.CompileResult result = userWorkflowService.compile(
                orgId,
                userId,
                agentId,
                new UserWorkflowService.CompileCommand(request.sourceText())
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.putAll(toVersionDetailPayload(result.version()));
        payload.put("workflowManifest", result.workflowManifest());
        payload.put("workflowPreview", result.workflowPreview());
        payload.put("compileSummary", result.compileSummary());
        payload.put("warnings", result.warnings());
        payload.put("dependencies", result.dependencies());
        payload.put("routines", result.routines().stream().map(routine -> Map.of(
                "routineKey", routine.routineKey(),
                "name", routine.name(),
                "triggerType", routine.triggerType(),
                "cronExpr", routine.cronExpr() == null ? "" : routine.cronExpr(),
                "intervalSeconds", routine.intervalSeconds() == null ? 0 : routine.intervalSeconds(),
                "allowedTools", routine.allowedTools()
        )).toList());
        return ApiResponse.ok(payload);
    }

    @GetMapping("/versions")
    public ApiResponse<List<Map<String, Object>>> versions(@PathVariable String agentId) {
        String orgId = TenantContext.requireOrgId();
        String userId = currentUser();
        return ApiResponse.ok(userWorkflowService.listVersions(orgId, userId, agentId).stream().map(this::toVersionPayload).toList());
    }

    @GetMapping("/quick-commands")
    public ApiResponse<List<Map<String, Object>>> quickCommands(@PathVariable String agentId) {
        String orgId = TenantContext.requireOrgId();
        String userId = currentUser();
        return ApiResponse.ok(userWorkflowService.listQuickCommands(orgId, userId, agentId).stream().map(this::toQuickCommandPayload).toList());
    }

    @PostMapping("/quick-commands")
    public ApiResponse<Map<String, Object>> createQuickCommand(@PathVariable String agentId,
                                                               @Valid @RequestBody CreateQuickCommandRequest request) {
        String orgId = TenantContext.requireOrgId();
        String userId = currentUser();
        return ApiResponse.ok(toQuickCommandPayload(userWorkflowService.createQuickCommand(
                orgId,
                userId,
                agentId,
                new UserWorkflowService.CreateQuickCommandCommand(request.title(), request.promptText())
        )));
    }

    @PostMapping("/publish")
    public ApiResponse<Map<String, Object>> publish(@PathVariable String agentId,
                                                    @Valid @RequestBody VersionActionRequest request) {
        String orgId = TenantContext.requireOrgId();
        String userId = currentUser();
        return ApiResponse.ok(toVersionPayload(userWorkflowService.publish(orgId, userId, agentId, request.versionNo())));
    }

    @PostMapping("/rollback")
    public ApiResponse<Map<String, Object>> rollback(@PathVariable String agentId,
                                                     @Valid @RequestBody VersionActionRequest request) {
        String orgId = TenantContext.requireOrgId();
        String userId = currentUser();
        return ApiResponse.ok(toVersionPayload(userWorkflowService.rollback(orgId, userId, agentId, request.versionNo())));
    }

    @GetMapping("/triggers")
    public ApiResponse<List<Map<String, Object>>> triggers(@PathVariable String agentId) {
        String orgId = TenantContext.requireOrgId();
        String userId = currentUser();
        return ApiResponse.ok(userWorkflowService.listTriggers(orgId, userId, agentId).stream().map(this::toTriggerPayload).toList());
    }

    @PutMapping("/triggers/{triggerId}")
    public ApiResponse<Map<String, Object>> updateTrigger(@PathVariable String agentId,
                                                          @PathVariable Long triggerId,
                                                          @Valid @RequestBody UpdateTriggerRequest request) {
        String orgId = TenantContext.requireOrgId();
        String userId = currentUser();
        return ApiResponse.ok(toTriggerPayload(userWorkflowService.updateTrigger(
                orgId,
                userId,
                agentId,
                triggerId,
                new UserWorkflowService.UpdateTriggerCommand(request.enabled())
        )));
    }

    @PostMapping("/debug")
    public ApiResponse<Map<String, Object>> debug(@PathVariable String agentId,
                                                  @Valid @RequestBody RunNowRequest request) {
        String orgId = TenantContext.requireOrgId();
        String userId = currentUser();
        return ApiResponse.ok(toExecutionPayload(userWorkflowService.runNow(orgId, userId, agentId, request.routineKey())));
    }

    @PostMapping("/run-now")
    public ApiResponse<Map<String, Object>> runNow(@PathVariable String agentId,
                                                   @Valid @RequestBody RunNowRequest request) {
        String orgId = TenantContext.requireOrgId();
        String userId = currentUser();
        return ApiResponse.ok(toExecutionPayload(userWorkflowService.runNow(orgId, userId, agentId, request.routineKey())));
    }

    @GetMapping("/executions")
    public ApiResponse<List<Map<String, Object>>> executions(@PathVariable String agentId) {
        String orgId = TenantContext.requireOrgId();
        String userId = currentUser();
        return ApiResponse.ok(userWorkflowService.listExecutions(orgId, userId, agentId).stream().map(this::toExecutionPayload).toList());
    }

    @GetMapping("/executions/{executionId}")
    public ApiResponse<Map<String, Object>> execution(@PathVariable String agentId, @PathVariable Long executionId) {
        String orgId = TenantContext.requireOrgId();
        String userId = currentUser();
        return ApiResponse.ok(toExecutionPayload(userWorkflowService.getExecution(orgId, userId, agentId, executionId)));
    }

    private String currentUser() {
        return TenantContext.getUserId()
                .orElseThrow(() -> new IllegalArgumentException("Missing user context"));
    }

    private Map<String, Object> toProfilePayload(UserAgentProfileEntity item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timezone", item.getTimezone());
        payload.put("locale", item.getLocale());
        payload.put("notificationTarget", readJson(item.getNotificationTargetJson()));
        payload.put("personalContext", readJson(item.getPersonalContextJson()));
        payload.put("enabled", item.isEnabled());
        payload.put("updatedAt", item.getUpdatedAt().toString());
        return payload;
    }

    private Map<String, Object> toSpecPayload(UserWorkflowSpecEntity item) {
        return Map.of(
                "sourceText", item.getSourceText(),
                "status", item.getStatus(),
                "draftVersionNo", item.getDraftVersionNo() == null ? 0 : item.getDraftVersionNo(),
                "publishedVersionId", item.getPublishedVersionId() == null ? 0 : item.getPublishedVersionId(),
                "updatedAt", item.getUpdatedAt().toString()
        );
    }

    private Map<String, Object> toVersionPayload(UserWorkflowVersionEntity item) {
        return Map.of(
                "id", item.getId(),
                "versionNo", item.getVersionNo(),
                "versionLabel", item.getVersionLabel() == null ? "" : item.getVersionLabel(),
                "publishStatus", item.getPublishStatus(),
                "createdAt", item.getCreatedAt().toString()
        );
    }

    private Map<String, Object> toVersionDetailPayload(UserWorkflowVersionEntity item) {
        Map<String, Object> payload = new LinkedHashMap<>(toVersionPayload(item));
        payload.put("workflowCode", item.getWorkflowCode());
        payload.put("workflowManifest", readJson(item.getWorkflowManifest()));
        payload.put("workflowPreview", readJson(item.getWorkflowPreview()));
        payload.put("compileSummary", readJsonList(item.getCompileSummary()));
        payload.put("warnings", readJsonList(item.getWarnings()));
        payload.put("dependencies", readJsonList(item.getDependencies()));
        return payload;
    }

    private Map<String, Object> toQuickCommandPayload(UserQuickCommandEntity item) {
        return Map.of(
                "id", item.getId(),
                "title", item.getTitle(),
                "promptText", item.getPromptText(),
                "sortOrder", item.getSortOrder(),
                "enabled", item.isEnabled(),
                "createdAt", item.getCreatedAt().toString(),
                "updatedAt", item.getUpdatedAt().toString()
        );
    }

    private Map<String, Object> toTriggerPayload(UserWorkflowTriggerEntity item) {
        return Map.of(
                "id", item.getId(),
                "routineKey", item.getRoutineKey(),
                "routineName", item.getRoutineName(),
                "triggerType", item.getTriggerType(),
                "cronExpr", item.getCronExpr() == null ? "" : item.getCronExpr(),
                "timezone", item.getTimezone() == null ? "" : item.getTimezone(),
                "intervalSeconds", item.getIntervalSeconds() == null ? 0 : item.getIntervalSeconds(),
                "enabled", item.isEnabled(),
                "nextFireAt", item.getNextFireAt() == null ? "" : item.getNextFireAt().toString(),
                "lastTriggeredAt", item.getLastTriggeredAt() == null ? "" : item.getLastTriggeredAt().toString()
        );
    }

    private Map<String, Object> toExecutionPayload(UserWorkflowExecutionEntity item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", item.getId());
        payload.put("routineKey", item.getRoutineKey());
        payload.put("triggerSource", item.getTriggerSource());
        payload.put("status", item.getStatus());
        payload.put("scheduledAt", item.getScheduledAt() == null ? "" : item.getScheduledAt().toString());
        payload.put("startedAt", item.getStartedAt() == null ? "" : item.getStartedAt().toString());
        payload.put("finishedAt", item.getFinishedAt() == null ? "" : item.getFinishedAt().toString());
        payload.put("outputSummary", item.getOutputSummary() == null ? "" : item.getOutputSummary());
        payload.put("trace", readJsonList(item.getTraceJson()));
        payload.put("errorCode", item.getErrorCode() == null ? "" : item.getErrorCode());
        payload.put("errorMessage", item.getErrorMessage() == null ? "" : item.getErrorMessage());
        return payload;
    }

    private Map<String, Object> readJson(String value) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(value, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private List<Object> readJsonList(String value) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(value, new com.fasterxml.jackson.core.type.TypeReference<List<Object>>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    public record UpdateProfileRequest(
            String timezone,
            String locale,
            Map<String, Object> notificationTarget,
            Map<String, Object> personalContext,
            Boolean enabled
    ) {
    }

    public record UpdateSpecRequest(String sourceText) {
    }

    public record CreateQuickCommandRequest(String title, String promptText) {
    }

    public record VersionActionRequest(Integer versionNo) {
    }

    public record UpdateTriggerRequest(Boolean enabled) {
    }

    public record RunNowRequest(String routineKey) {
    }
}
