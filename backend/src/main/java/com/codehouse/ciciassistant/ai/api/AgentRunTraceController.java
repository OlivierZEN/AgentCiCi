package com.codehouse.ciciassistant.ai.api;

import com.codehouse.ciciassistant.ai.service.AgentRunTraceService;
import com.codehouse.ciciassistant.agent.domain.AgentPermission;
import com.codehouse.ciciassistant.agent.service.AgentAccessControlService;
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
@RequestMapping("/me/agents/run-logs")
public class AgentRunTraceController {

    private final AgentRunTraceService traceService;
    private final AgentAccessControlService accessControlService;

    public AgentRunTraceController(AgentRunTraceService traceService,
                                   AgentAccessControlService accessControlService) {
        this.traceService = traceService;
        this.accessControlService = accessControlService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "agentId", required = false) String agentId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        String companyId = TenantContext.requireCompanyId();
        String userId = TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
        if (agentId != null && !agentId.isBlank()) {
            accessControlService.require(companyId, userId, TenantContext.getRoles(), agentId, AgentPermission.LOG_VIEW);
        }
        return ApiResponse.ok(traceService.listRunLogs(companyId, userId, new AgentRunTraceService.RunLogQuery(
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
        String userId = TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
        return ApiResponse.ok(traceService.traceDetail(companyId, userId, traceId));
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
