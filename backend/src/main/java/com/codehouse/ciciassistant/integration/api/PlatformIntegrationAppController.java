package com.codehouse.ciciassistant.integration.api;

import com.codehouse.ciciassistant.auth.RequirePlatformRole;
import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.auth.config.PlatformAccountProperties;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.integration.service.IntegrationAppService;
import com.codehouse.ciciassistant.platform.service.PlatformAuditService;
import com.codehouse.ciciassistant.tenant.TenantContext;
import com.codehouse.ciciassistant.tool.codeinterpreter.SandboxCodeInterpreterService;
import com.codehouse.ciciassistant.tool.managedweb.ManagedWebToolService;
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
    private final SandboxCodeInterpreterService sandboxCodeInterpreterService;
    private final ManagedWebToolService managedWebToolService;
    private final PlatformAuditService platformAuditService;
    private final PlatformAccountProperties platformAccountProperties;

    public PlatformIntegrationAppController(IntegrationAppService integrationAppService,
                                            TavilyToolService tavilyToolService,
                                            SandboxCodeInterpreterService sandboxCodeInterpreterService,
                                            ManagedWebToolService managedWebToolService,
                                            PlatformAuditService platformAuditService,
                                            PlatformAccountProperties platformAccountProperties) {
        this.integrationAppService = integrationAppService;
        this.tavilyToolService = tavilyToolService;
        this.sandboxCodeInterpreterService = sandboxCodeInterpreterService;
        this.managedWebToolService = managedWebToolService;
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
        if (IntegrationAppService.APP_CODE_CODE_INTERPRETER.equals(appCode)) {
            sandboxCodeInterpreterService.validateConfigurationDraft(request.config());
        }
        managedWebToolService.validateConfigurationDraft(appCode, request.config());
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

    @PostMapping("/code-interpreter/test")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<Map<String, Object>> testCodeInterpreter(
            @RequestBody(required = false) TestCodeInterpreterRequest request) {
        String apiKey = request == null ? null : request.apiKey();
        String apiBaseUrl = request == null ? null : request.apiBaseUrl();
        String model = request == null ? null : request.model();
        Map<String, Object> result = sandboxCodeInterpreterService.testConnection(apiKey, apiBaseUrl, model);
        writeAudit("platform.integration.test", IntegrationAppService.APP_CODE_CODE_INTERPRETER,
                "ok=" + result.getOrDefault("ok", false));
        return ApiResponse.ok(result);
    }

    @PostMapping("/managed-web-search/test")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<Map<String, Object>> testManagedWebSearch(
            @RequestBody(required = false) TestManagedWebRequest request) {
        return testManagedWeb(IntegrationAppService.APP_CODE_MANAGED_WEB_SEARCH, request);
    }

    @PostMapping("/managed-web-extractor/test")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<Map<String, Object>> testManagedWebExtractor(
            @RequestBody(required = false) TestManagedWebRequest request) {
        return testManagedWeb(IntegrationAppService.APP_CODE_MANAGED_WEB_EXTRACTOR, request);
    }

    private ApiResponse<Map<String, Object>> testManagedWeb(String appCode, TestManagedWebRequest request) {
        String apiKey = request == null ? null : request.apiKey();
        String apiBaseUrl = request == null ? null : request.apiBaseUrl();
        String model = request == null ? null : request.model();
        Map<String, Object> result = managedWebToolService.testConnection(appCode, apiKey, apiBaseUrl, model);
        writeAudit("platform.integration.test", appCode, "ok=" + result.getOrDefault("ok", false));
        return ApiResponse.ok(result);
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
        return configured == null || configured.isBlank()
                ? PlatformAccountProperties.LEGACY_DEFAULT_GOVERNANCE_COMPANY_ID
                : configured.trim();
    }

    public record UpdateIntegrationAppRequest(
            boolean enabled,
            String description,
            Map<String, Object> config
    ) {
    }

    public record TestTavilyRequest(String apiKey) {
    }

    public record TestCodeInterpreterRequest(String apiKey, String apiBaseUrl, String model) {
    }

    public record TestManagedWebRequest(String apiKey, String apiBaseUrl, String model) {
    }
}
