package com.codehouse.ciciassistant.ai.api;

import com.codehouse.ciciassistant.ai.service.AgentRunTraceService;
import com.codehouse.ciciassistant.auth.RequireOrgAdmin;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.tenant.TenantContext;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequireOrgAdmin
@RequestMapping("/admin/agents/run-logs")
public class AdminAgentRunTraceController {

    private final AgentRunTraceService traceService;

    public AdminAgentRunTraceController(AgentRunTraceService traceService) {
        this.traceService = traceService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "agentId", required = false) String agentId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "limit", defaultValue = "80") int limit) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(traceService.listOrgRunLogs(companyId, new AgentRunTraceService.RunLogQuery(
                parseInstant(from),
                parseInstant(to),
                blankToNull(agentId),
                blankToNull(status),
                blankToNull(type),
                blankToNull(q),
                limit
        )));
    }

    @GetMapping("/{traceId}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String traceId) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(traceService.orgTraceDetail(companyId, traceId));
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
