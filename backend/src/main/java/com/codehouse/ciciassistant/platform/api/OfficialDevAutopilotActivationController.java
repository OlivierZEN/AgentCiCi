package com.codehouse.ciciassistant.platform.api;

import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.platform.service.DevAutopilotTenantApplicationService;
import com.codehouse.ciciassistant.tenant.TenantContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Runtime resolve endpoint. The company is derived from the verified request context, never from a URL or body. */
@RestController
@RequestMapping("/openapi/v1/official/devautopilot/activation")
public class OfficialDevAutopilotActivationController {
    private final DevAutopilotTenantApplicationService applications;

    public OfficialDevAutopilotActivationController(DevAutopilotTenantApplicationService applications) {
        this.applications = applications;
    }

    @GetMapping
    public ApiResponse<DevAutopilotTenantApplicationService.View> resolve() {
        return ApiResponse.ok(applications.get(TenantContext.requireCompanyId()));
    }
}
