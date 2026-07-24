package com.codehouse.ciciassistant.integration.api;

import com.codehouse.ciciassistant.auth.RequireOrgAdmin;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.integration.service.IntegrationAppService;
import com.codehouse.ciciassistant.tenant.TenantContext;
import com.codehouse.ciciassistant.tool.tavily.TavilyToolService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/integrations")
@RequireOrgAdmin
public class IntegrationAppController {

    private final IntegrationAppService integrationAppService;
    private final TavilyToolService tavilyToolService;

    public IntegrationAppController(IntegrationAppService integrationAppService,
                                    TavilyToolService tavilyToolService) {
        this.integrationAppService = integrationAppService;
        this.tavilyToolService = tavilyToolService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(integrationAppService.list(companyId));
    }

    @PutMapping("/{appCode}")
    public ApiResponse<Map<String, Object>> update(
            @PathVariable String appCode,
            @Valid @RequestBody UpdateIntegrationAppRequest request) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(integrationAppService.update(
                companyId,
                appCode,
                request.enabled(),
                request.description(),
                request.config()));
    }

    /**
     * Runs a minimal {@code tavily_search(query="ping", max_results=1)} to verify the tenant
     * integration. When {@code apiKey} is present in the body, it overrides the stored one
     * (useful when the admin is editing but has not yet saved).
     */
    @PostMapping("/tavily/test")
    public ApiResponse<Map<String, Object>> testTavily(@RequestBody(required = false) TestTavilyRequest request) {
        if (integrationAppService.isPlatformManagedApp(IntegrationAppService.APP_CODE_TAVILY)) {
            throw new com.codehouse.ciciassistant.common.error.ForbiddenException(IntegrationAppService.PLATFORM_MANAGED_MESSAGE);
        }
        String companyId = TenantContext.requireCompanyId();
        String override = request == null ? null : request.apiKey();
        return ApiResponse.ok(tavilyToolService.testConnection(companyId, override));
    }

    public record UpdateIntegrationAppRequest(
            boolean enabled,
            String description,
            Map<String, Object> config
    ) {
    }

    public record TestTavilyRequest(String apiKey) {
    }
}
