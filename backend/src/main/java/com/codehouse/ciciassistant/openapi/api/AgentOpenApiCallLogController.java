package com.codehouse.ciciassistant.openapi.api;

import com.codehouse.ciciassistant.agent.domain.AgentPermission;
import com.codehouse.ciciassistant.agent.service.AgentAccessControlService;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.openapi.service.AgentOpenApiCallLogService;
import com.codehouse.ciciassistant.tenant.TenantContext;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agents/{agentId}/api-calls")
public class AgentOpenApiCallLogController {

    private final AgentOpenApiCallLogService callLogService;
    private final AgentAccessControlService accessControlService;

    public AgentOpenApiCallLogController(AgentOpenApiCallLogService callLogService,
                                         AgentAccessControlService accessControlService) {
        this.callLogService = callLogService;
        this.accessControlService = accessControlService;
    }

    @GetMapping
    public ApiResponse<List<AgentOpenApiCallLogService.CallLogView>> list(
            @PathVariable String agentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Long credentialId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q) {
        accessControlService.require(
                TenantContext.requireCompanyId(),
                TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context")),
                TenantContext.getRoles(),
                agentId,
                AgentPermission.LOG_VIEW);
        return ApiResponse.ok(callLogService.list(
                TenantContext.requireCompanyId(),
                agentId,
                from,
                to,
                credentialId,
                status,
                q));
    }
}
