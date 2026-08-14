package com.codehouse.ciciassistant.platform.api;

import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.platform.service.DevAutopilotExecutionAuthorizationService;
import com.codehouse.ciciassistant.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated backend boundary used by DevAutopilot after an explicit HUMAN confirmation. */
@RestController
@RequestMapping("/api/devautopilot/execution-authorizations")
public class DevAutopilotExecutionAuthorizationController {
    private final DevAutopilotExecutionAuthorizationService authorizations;

    public DevAutopilotExecutionAuthorizationController(DevAutopilotExecutionAuthorizationService authorizations) {
        this.authorizations = authorizations;
    }

    @PostMapping
    public ApiResponse<DevAutopilotExecutionAuthorizationService.AuthorizationView> authorize(
            @Valid @RequestBody AuthorizationRequest request) {
        return ApiResponse.ok(authorizations.authorize(
                TenantContext.requireCompanyId(),
                TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context")),
                request.operation()));
    }

    public record AuthorizationRequest(@NotNull DevAutopilotExecutionAuthorizationService.Operation operation) {
    }
}
