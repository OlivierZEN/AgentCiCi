package com.codehouse.ciciassistant.ops.api;

import com.codehouse.ciciassistant.auth.RequireOrgAdmin;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.ops.service.AuditService;
import com.codehouse.ciciassistant.tenant.TenantContext;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public ApiResponse<Map<String, Object>> logs(
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "eventType", required = false) String eventType,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(auditService.query(companyId, new AuditService.AuditLogQuery(
                parseInstant(from),
                parseInstant(to),
                blankToNull(eventType),
                blankToNull(q),
                limit
        )));
    }

    @GetMapping("/metrics/cost")
    public ApiResponse<Map<String, Object>> cost() {
        String companyId = TenantContext.requireCompanyId();
        int calls = auditService.latest(companyId).size();
        return ApiResponse.ok(Map.of(
                "companyId", companyId,
                "callCount", calls,
                "estimatedCostCny", String.format("%.2f", calls * 0.02)
        ));
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid instant: " + value);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
