package com.codehouse.ciciassistant.ops.api;

import com.codehouse.ciciassistant.auth.RequireOrgAdmin;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.ops.domain.AuditLogEntity;
import com.codehouse.ciciassistant.ops.service.AuditService;
import com.codehouse.ciciassistant.tenant.TenantContext;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ops")
@RequireOrgAdmin
public class OpsController {

    private final AuditService auditService;

    public OpsController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/audit/logs")
    public ApiResponse<Object> logs() {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(auditService.latest(orgId));
    }

    @GetMapping("/metrics/cost")
    public ApiResponse<Map<String, Object>> cost() {
        String orgId = TenantContext.requireOrgId();
        int calls = auditService.latest(orgId).size();
        return ApiResponse.ok(Map.of(
                "orgId", orgId,
                "callCount", calls,
                "estimatedCostCny", String.format("%.2f", calls * 0.02)
        ));
    }
}
