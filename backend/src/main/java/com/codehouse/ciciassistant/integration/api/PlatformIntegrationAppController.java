package com.codehouse.ciciassistant.integration.api;

import com.codehouse.ciciassistant.auth.RequirePlatformRole;
import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.auth.config.PlatformAccountProperties;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.integration.service.IntegrationAppService;
import com.codehouse.ciciassistant.platform.service.PlatformAuditService;
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
@RequestMapping("/platform/integrations")
@RequirePlatformRole
public class PlatformIntegrationAppController {

    private final IntegrationAppService integrationAppService;
    private final TavilyToolService tavilyToolService;
    private final PlatformAuditService platformAuditService;
    private final PlatformAccountProperties platformAccountProperties;

    public PlatformIntegrationAppController(IntegrationAppService integrationAppService,
                                            TavilyToolService tavilyToolService,
                                            PlatformAuditService platformAuditService,
                                            PlatformAccountProperties platformAccountProperties) {
        this.integrationAppService = integrationAppService;
        this.tavilyToolService = tavilyToolService;
        this.platformAuditService = platformAuditService;
        this.platformAccountProperties = platformAccountProperties;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(integrationAppService.listPlatformManaged());
    }

    @PutMapping("/{appCode}")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<Map<String, Object>> update(
            @PathVariable String appCode,
            @Valid @RequestBody UpdateIntegrationAppRequest request) {
        Map<String, Object> payload = integrationAppService.updatePlatformManaged(
                appCode,
                request.enabled(),
                request.description(),
                request.config());
        writeAudit("platform.integration.update", appCode, "enabled=" + payload.get("enabled"));
        return ApiResponse.ok(payload);
    }

    @PostMapping("/tavily/test")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<Map<String, Object>> testTavily(@RequestBody(required = false) TestTavilyRequest request) {
        String override = request == null ? null : request.apiKey();
        return ApiResponse.ok(tavilyToolService.testConnection(platformScopeId(), override));
    }

    private void writeAudit(String eventType, String resourceKey, String detail) {
        platformAuditService.log(
                platformScopeId(),
                TenantContext.getUserId().orElse("platform-system"),
                TenantContext.getRoles().stream().filter(RoleCodes::isPlatformRole).findFirst().orElse(RoleCodes.PLATFORM_ADMIN),
                eventType,
                "integration_app",
                resourceKey,
                detail);
    }

    private String platformScopeId() {
        String configured = platformAccountProperties.getGovernanceCompanyId();
        return configured == null || configured.isBlank() ? "demo-org" : configured.trim();
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
