package com.codehouse.ciciassistant.agent.api;

import com.codehouse.ciciassistant.agent.service.AgentRuntimeCatalogService;
import com.codehouse.ciciassistant.agent.service.AgentRuntimeScheduleSyncService;
import com.codehouse.ciciassistant.agent.service.AgentWorkflowExecutionLogService;
import com.codehouse.ciciassistant.auth.RequireOrgAdmin;
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
@RequireOrgAdmin
public class AgentRuntimeController {

    private final AgentWorkflowExecutionLogService executionLogService;
    private final AgentRuntimeCatalogService runtimeCatalogService;
    private final AgentRuntimeScheduleSyncService runtimeScheduleSyncService;

    public AgentRuntimeController(AgentWorkflowExecutionLogService executionLogService,
                                  AgentRuntimeCatalogService runtimeCatalogService,
                                  AgentRuntimeScheduleSyncService runtimeScheduleSyncService) {
        this.executionLogService = executionLogService;
        this.runtimeCatalogService = runtimeCatalogService;
        this.runtimeScheduleSyncService = runtimeScheduleSyncService;
    }

    @GetMapping("/{agentId}/runtime/executions")
    public ApiResponse<List<Map<String, Object>>> listExecutions(
            @PathVariable String agentId,
            @RequestParam(name = "versionNo", required = false) Integer versionNo,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(executionLogService.list(orgId, agentId, versionNo, limit));
    }

    @GetMapping("/{agentId}/runtime/triggers")
    public ApiResponse<Map<String, Object>> listTriggers(@PathVariable String agentId) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(runtimeCatalogService.buildTriggers(orgId, agentId));
    }

    @PostMapping("/{agentId}/runtime/schedules/sync")
    public ApiResponse<Map<String, Object>> syncSchedules(@PathVariable String agentId) {
        String orgId = TenantContext.requireOrgId();
        Long publishedVersionId = runtimeCatalogService.publishedVersionId(orgId, agentId);
        return ApiResponse.ok(runtimeScheduleSyncService.syncFromCompiledVersion(orgId, agentId, publishedVersionId));
    }

    @PutMapping("/{agentId}/runtime/schedules/{triggerKey}")
    public ApiResponse<Map<String, Object>> updateSchedule(
            @PathVariable String agentId,
            @PathVariable String triggerKey,
            @RequestBody UpdateScheduleRequest request) {
        String orgId = TenantContext.requireOrgId();
        boolean enabled = request != null && request.enabled != null && request.enabled;
        return ApiResponse.ok(runtimeScheduleSyncService.updateEnabled(orgId, agentId, triggerKey, enabled));
    }

    @PostMapping("/{agentId}/runtime/schedules/run-now")
    public ApiResponse<Map<String, Object>> runScheduleNow(
            @PathVariable String agentId,
            @RequestBody RunNowRequest request) {
        String orgId = TenantContext.requireOrgId();
        if (request == null || request.triggerKey == null || request.triggerKey.isBlank()) {
            throw new IllegalArgumentException("triggerKey is required");
        }
        return ApiResponse.ok(runtimeScheduleSyncService.runNow(orgId, agentId, request.triggerKey));
    }

    public static final class UpdateScheduleRequest {
        public Boolean enabled;
    }

    public static final class RunNowRequest {
        public String triggerKey;
    }
}
