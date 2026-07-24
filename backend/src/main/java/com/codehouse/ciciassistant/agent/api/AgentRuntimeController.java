package com.codehouse.ciciassistant.agent.api;

import com.codehouse.ciciassistant.agent.domain.AgentPermission;
import com.codehouse.ciciassistant.agent.service.AgentAccessControlService;
import com.codehouse.ciciassistant.agent.service.AgentRuntimeCatalogService;
import com.codehouse.ciciassistant.agent.service.AgentRuntimeScheduleSyncService;
import com.codehouse.ciciassistant.agent.service.AgentWorkflowExecutionLogService;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.tenant.TenantContext;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agents")
public class AgentRuntimeController {

    private final AgentWorkflowExecutionLogService executionLogService;
    private final AgentRuntimeCatalogService runtimeCatalogService;
    private final AgentRuntimeScheduleSyncService runtimeScheduleSyncService;
    private final AgentAccessControlService accessControlService;

    public AgentRuntimeController(AgentWorkflowExecutionLogService executionLogService,
                                  AgentRuntimeCatalogService runtimeCatalogService,
                                  AgentRuntimeScheduleSyncService runtimeScheduleSyncService,
                                  AgentAccessControlService accessControlService) {
        this.executionLogService = executionLogService;
        this.runtimeCatalogService = runtimeCatalogService;
        this.runtimeScheduleSyncService = runtimeScheduleSyncService;
        this.accessControlService = accessControlService;
    }

    @GetMapping("/{agentId}/runtime/executions")
    public ApiResponse<List<Map<String, Object>>> listExecutions(
            @PathVariable String agentId,
            @RequestParam(name = "versionNo", required = false) Integer versionNo,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        String companyId = TenantContext.requireCompanyId();
        accessControlService.require(companyId, requireUserId(), TenantContext.getRoles(), agentId, AgentPermission.DEBUG);
        return ApiResponse.ok(executionLogService.list(companyId, agentId, versionNo, limit));
    }

    @GetMapping("/{agentId}/runtime/triggers")
    public ApiResponse<Map<String, Object>> listTriggers(@PathVariable String agentId) {
        String companyId = TenantContext.requireCompanyId();
        accessControlService.require(companyId, requireUserId(), TenantContext.getRoles(), agentId, AgentPermission.VIEW);
        return ApiResponse.ok(runtimeCatalogService.buildTriggers(companyId, agentId));
    }

    @PostMapping("/{agentId}/runtime/schedules/sync")
    public ApiResponse<Map<String, Object>> syncSchedules(@PathVariable String agentId) {
        String companyId = TenantContext.requireCompanyId();
        accessControlService.require(companyId, requireUserId(), TenantContext.getRoles(), agentId, AgentPermission.PUBLISH);
        Long publishedVersionId = runtimeCatalogService.publishedVersionId(companyId, agentId);
        return ApiResponse.ok(runtimeScheduleSyncService.syncFromCompiledVersion(companyId, agentId, publishedVersionId));
    }

    @PutMapping("/{agentId}/runtime/schedules/{triggerKey}")
    public ApiResponse<Map<String, Object>> updateSchedule(
            @PathVariable String agentId,
            @PathVariable String triggerKey,
            @RequestBody UpdateScheduleRequest request) {
        String companyId = TenantContext.requireCompanyId();
        accessControlService.require(companyId, requireUserId(), TenantContext.getRoles(), agentId, AgentPermission.EDIT);
        boolean enabled = request != null && request.enabled != null && request.enabled;
        return ApiResponse.ok(runtimeScheduleSyncService.updateEnabled(companyId, agentId, triggerKey, enabled));
    }

    @PostMapping("/{agentId}/runtime/schedules/run-now")
    public ApiResponse<Map<String, Object>> runScheduleNow(
            @PathVariable String agentId,
            @RequestBody RunNowRequest request) {
        String companyId = TenantContext.requireCompanyId();
        accessControlService.require(companyId, requireUserId(), TenantContext.getRoles(), agentId, AgentPermission.RUN);
        if (request == null || request.triggerKey == null || request.triggerKey.isBlank()) {
            throw new IllegalArgumentException("triggerKey is required");
        }
        return ApiResponse.ok(runtimeScheduleSyncService.runNow(companyId, agentId, request.triggerKey));
    }

    public static final class UpdateScheduleRequest {
        public Boolean enabled;
    }

    public static final class RunNowRequest {
        public String triggerKey;
    }

    private String requireUserId() {
        return TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
    }
}
