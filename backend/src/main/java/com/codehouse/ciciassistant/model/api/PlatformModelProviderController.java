package com.codehouse.ciciassistant.model.api;

import com.codehouse.ciciassistant.auth.RequirePlatformRole;
import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.auth.config.PlatformAccountProperties;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.integration.service.IntegrationAppService;
import com.codehouse.ciciassistant.model.service.ModelProviderService;
import com.codehouse.ciciassistant.platform.service.PlatformAuditService;
import com.codehouse.ciciassistant.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/platform/models")
@RequirePlatformRole
public class PlatformModelProviderController {

    private final ModelProviderService modelProviderService;
    private final IntegrationAppService integrationAppService;
    private final PlatformAuditService platformAuditService;
    private final PlatformAccountProperties platformAccountProperties;

    public PlatformModelProviderController(ModelProviderService modelProviderService,
                                           IntegrationAppService integrationAppService,
                                           PlatformAuditService platformAuditService,
                                           PlatformAccountProperties platformAccountProperties) {
        this.modelProviderService = modelProviderService;
        this.integrationAppService = integrationAppService;
        this.platformAuditService = platformAuditService;
        this.platformAccountProperties = platformAccountProperties;
    }

    @GetMapping("/providers")
    public ApiResponse<List<Map<String, Object>>> listProviders() {
        List<Map<String, Object>> providers = new ArrayList<>(modelProviderService.listPlatformProviders());
        providers.add(integrationAppService.realtimeAsrProviderView());
        return ApiResponse.ok(providers);
    }

    @PutMapping("/providers/{providerCode}")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<Map<String, Object>> updateProvider(@PathVariable String providerCode,
                                                           @Valid @RequestBody UpdateProviderRequest request) {
        boolean realtimeAsrProvider = IntegrationAppService.APP_CODE_IFLYTEK_ASR.equals(providerCode);
        Map<String, Object> payload = realtimeAsrProvider
                ? integrationAppService.updateRealtimeAsrProvider(request.enabled(), request.config())
                : modelProviderService.updatePlatformProvider(
                        providerCode,
                        request.enabled(),
                        request.apiBaseUrl(),
                        request.apiKey());
        writeAudit("platform.model.provider.update",
                "model_provider",
                providerCode,
                "enabled=" + payload.get("enabled")
                        + (realtimeAsrProvider
                                ? ", realtimeAsrConfigUpdated=" + (request.config() != null)
                                : ", apiBaseUrlUpdated=" + (request.apiBaseUrl() != null && !request.apiBaseUrl().isBlank())
                                        + ", apiKeyUpdated=" + (request.apiKey() != null)));
        return ApiResponse.ok(payload);
    }

    @PostMapping("/providers/{providerCode}/check")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<Map<String, Object>> checkProvider(@PathVariable String providerCode,
                                                           @RequestBody(required = false) CheckProviderRequest request) {
        if (IntegrationAppService.APP_CODE_IFLYTEK_ASR.equals(providerCode)) {
            return ApiResponse.ok(integrationAppService.checkRealtimeAsrProvider(
                    request == null ? null : request.enabled(),
                    request == null ? null : request.config()));
        }
        return ApiResponse.ok(modelProviderService.checkPlatformProvider(
                providerCode,
                request == null ? null : request.enabled(),
                request == null ? null : request.apiBaseUrl(),
                request == null ? null : request.apiKey()));
    }

    @GetMapping("/providers/{providerCode}/models")
    public ApiResponse<Map<String, Object>> providerModels(@PathVariable String providerCode) {
        return ApiResponse.ok(modelProviderService.platformProviderModels(providerCode));
    }

    @PostMapping("/providers/{providerCode}/models/fetch")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<Map<String, Object>> fetchProviderModels(@PathVariable String providerCode) {
        return ApiResponse.ok(modelProviderService.fetchPlatformProviderModels(providerCode));
    }

    @PutMapping("/providers/{providerCode}/selected-models")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<Map<String, Object>> updateSelectedModels(@PathVariable String providerCode,
                                                                 @Valid @RequestBody UpdateSelectedModelsRequest request) {
        Map<String, Object> payload = modelProviderService.updatePlatformSelectedModels(providerCode, request.selectedModels());
        List<?> selected = payload.get("selectedModels") instanceof List<?> list ? list : List.of();
        writeAudit("platform.model.selected_models.update",
                "model_provider",
                providerCode,
                "selectedModelCount=" + selected.size());
        return ApiResponse.ok(payload);
    }

    @PutMapping("/providers/{providerCode}/model-capabilities")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<Map<String, Object>> confirmModelCapabilities(@PathVariable String providerCode,
                                                                      @Valid @RequestBody ConfirmModelCapabilitiesRequest request) {
        Map<String, Object> payload = modelProviderService.confirmPlatformModelCapabilities(
                providerCode,
                request.modelName(),
                request.capabilities(),
                TenantContext.getUserId().orElse("platform-operator"));
        writeAudit("platform.model.capability.confirm",
                "model_capability",
                providerCode + "::" + request.modelName().trim(),
                "source=operator_confirmation, capabilityCount=" + request.capabilities().size());
        return ApiResponse.ok(payload);
    }

    @DeleteMapping("/providers/{providerCode}/model-capabilities")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<Map<String, Object>> revokeModelCapabilityConfirmation(@PathVariable String providerCode,
                                                                                @RequestParam @NotBlank String modelName) {
        Map<String, Object> payload = modelProviderService.revokePlatformModelCapabilityConfirmation(providerCode, modelName);
        writeAudit("platform.model.capability.revoke",
                "model_capability",
                providerCode + "::" + modelName.trim(),
                "source=operator_confirmation");
        return ApiResponse.ok(payload);
    }

    @GetMapping("/routes")
    public ApiResponse<Map<String, Object>> modelRoutes() {
        return ApiResponse.ok(modelProviderService.platformModelRouteSettings());
    }

    @PutMapping("/routes/{sceneCode}")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<Map<String, Object>> updateModelRoute(@PathVariable String sceneCode,
                                                             @Valid @RequestBody UpdateModelRouteRequest request) {
        Map<String, Object> payload = modelProviderService.updatePlatformModelRoute(
                sceneCode,
                request.providerCode(),
                request.modelName());
        writeAudit("platform.model.route.update",
                "model_route",
                String.valueOf(payload.getOrDefault("sceneCode", sceneCode)),
                "provider=" + payload.getOrDefault("providerCode", request.providerCode())
                        + ", model=" + payload.getOrDefault("modelName", request.modelName()));
        return ApiResponse.ok(payload);
    }

    @DeleteMapping("/routes/{sceneCode}")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<Map<String, Object>> deleteModelRoute(@PathVariable String sceneCode) {
        Map<String, Object> payload = modelProviderService.deletePlatformModelRoute(sceneCode);
        writeAudit("platform.model.route.delete",
                "model_route",
                String.valueOf(payload.getOrDefault("sceneCode", sceneCode)),
                "configured=false");
        return ApiResponse.ok(payload);
    }

    private void writeAudit(String eventType, String resourceType, String resourceKey, String detail) {
        platformAuditService.log(
                platformScopeId(),
                TenantContext.getUserId().orElse("platform-system"),
                TenantContext.getRoles().stream().filter(RoleCodes::isPlatformRole).findFirst().orElse(RoleCodes.PLATFORM_ADMIN),
                eventType,
                resourceType,
                resourceKey,
                detail);
    }

    private String platformScopeId() {
        String configured = platformAccountProperties.getGovernanceCompanyId();
        return configured == null || configured.isBlank()
                ? PlatformAccountProperties.LEGACY_DEFAULT_GOVERNANCE_COMPANY_ID
                : configured.trim();
    }

    public record UpdateProviderRequest(Boolean enabled, String apiBaseUrl, String apiKey, Map<String, Object> config) {
    }

    public record CheckProviderRequest(Boolean enabled, String apiBaseUrl, String apiKey, Map<String, Object> config) {
    }

    public record UpdateSelectedModelsRequest(List<String> selectedModels) {
    }

    public record ConfirmModelCapabilitiesRequest(
            @NotBlank String modelName,
            @NotEmpty List<@NotBlank String> capabilities
    ) {
    }

    public record UpdateModelRouteRequest(@NotBlank String providerCode, @NotBlank String modelName) {
    }
}
