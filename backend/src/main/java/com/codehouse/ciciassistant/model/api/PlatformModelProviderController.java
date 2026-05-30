package com.codehouse.ciciassistant.model.api;

import com.codehouse.ciciassistant.auth.RequirePlatformRole;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.model.service.ModelProviderService;
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
@RequestMapping("/platform/models")
@RequirePlatformRole
public class PlatformModelProviderController {

    private final ModelProviderService modelProviderService;

    public PlatformModelProviderController(ModelProviderService modelProviderService) {
        this.modelProviderService = modelProviderService;
    }

    @GetMapping("/providers")
    public ApiResponse<List<Map<String, Object>>> listProviders() {
        return ApiResponse.ok(modelProviderService.listPlatformProviders());
    }

    @PutMapping("/providers/{providerCode}")
    public ApiResponse<Map<String, Object>> updateProvider(@PathVariable String providerCode,
                                                           @Valid @RequestBody UpdateProviderRequest request) {
        return ApiResponse.ok(modelProviderService.updatePlatformProvider(
                providerCode,
                request.enabled(),
                request.apiBaseUrl(),
                request.apiKey()));
    }

    @PostMapping("/providers/{providerCode}/check")
    public ApiResponse<Map<String, Object>> checkProvider(@PathVariable String providerCode) {
        return ApiResponse.ok(modelProviderService.checkPlatformProvider(providerCode));
    }

    @GetMapping("/providers/{providerCode}/models")
    public ApiResponse<Map<String, Object>> providerModels(@PathVariable String providerCode) {
        return ApiResponse.ok(modelProviderService.platformProviderModels(providerCode));
    }

    @PostMapping("/providers/{providerCode}/models/fetch")
    public ApiResponse<Map<String, Object>> fetchProviderModels(@PathVariable String providerCode) {
        return ApiResponse.ok(modelProviderService.fetchPlatformProviderModels(providerCode));
    }

    @PutMapping("/providers/{providerCode}/selected-models")
    public ApiResponse<Map<String, Object>> updateSelectedModels(@PathVariable String providerCode,
                                                                 @Valid @RequestBody UpdateSelectedModelsRequest request) {
        return ApiResponse.ok(modelProviderService.updatePlatformSelectedModels(providerCode, request.selectedModels()));
    }

    public record UpdateProviderRequest(Boolean enabled, String apiBaseUrl, String apiKey) {
    }

    public record UpdateSelectedModelsRequest(List<String> selectedModels) {
    }
}
