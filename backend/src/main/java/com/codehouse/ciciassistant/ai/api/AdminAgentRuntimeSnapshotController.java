package com.codehouse.ciciassistant.ai.api;

import com.codehouse.ciciassistant.ai.service.AgentRunTraceService;
import com.codehouse.ciciassistant.auth.RequireOrgAdmin;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.tenant.TenantContext;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequireOrgAdmin
@RequestMapping("/admin/agents/runtime-snapshots")
public class AdminAgentRuntimeSnapshotController {

    private final AgentRunTraceService traceService;

    public AdminAgentRuntimeSnapshotController(AgentRunTraceService traceService) {
        this.traceService = traceService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list() {
        return ApiResponse.ok(traceService.listOrgRuntimeSnapshots(TenantContext.requireCompanyId()));
    }
}
