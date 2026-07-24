package com.codehouse.ciciassistant.model.api;

import com.codehouse.ciciassistant.auth.RequireOrgAdmin;
import com.codehouse.ciciassistant.ai.service.ChatThinkingConfigService;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.model.domain.CompanyModelConfigEntity;
import com.codehouse.ciciassistant.model.domain.CompanyModelConfigRepository;
import com.codehouse.ciciassistant.model.service.ModelProviderService;
import com.codehouse.ciciassistant.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
@RequestMapping("/models")
@RequireOrgAdmin
public class ModelConfigController {

    private final CompanyModelConfigRepository repository;
    private final ChatThinkingConfigService chatThinkingConfigService;
    private final ModelProviderService modelProviderService;

    public ModelConfigController(CompanyModelConfigRepository repository,
                                 ChatThinkingConfigService chatThinkingConfigService,
                                 ModelProviderService modelProviderService) {
        this.repository = repository;
        this.chatThinkingConfigService = chatThinkingConfigService;
        this.modelProviderService = modelProviderService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, String>>> list() {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(repository.findByCompanyId(companyId).stream()
                .filter(item -> !ChatThinkingConfigService.SCENE_CODE.equals(item.getSceneCode()))
                .map(item -> Map.of(
                        "companyId", item.getCompanyId(),
                        "sceneCode", item.getSceneCode(),
                        "provider", item.getProvider(),
                        "modelName", item.getModelName()
                ))
                .toList());
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> upsert(@Valid @RequestBody UpsertModelRequest request) {
        throw platformManagedModelConfig();
    }

    @DeleteMapping
    public ApiResponse<Map<String, Object>> delete(@RequestParam("sceneCode") String sceneCode) {
        throw platformManagedModelConfig();
    }

    @GetMapping("/settings/thinking")
    public ApiResponse<Map<String, Object>> getThinkingSetting() {
        String companyId = TenantContext.requireCompanyId();
        boolean enabled = chatThinkingConfigService.isEnabled(companyId);
        return ApiResponse.ok(Map.of("companyId", companyId, "enabled", enabled));
    }

    @PostMapping("/settings/thinking")
    public ApiResponse<Map<String, Object>> setThinkingSetting(@Valid @RequestBody ThinkingSettingRequest request) {
        String companyId = TenantContext.requireCompanyId();
        CompanyModelConfigEntity entity = repository.findByCompanyIdAndSceneCode(companyId, ChatThinkingConfigService.SCENE_CODE)
                .orElse(new CompanyModelConfigEntity(
                        companyId,
                        ChatThinkingConfigService.SCENE_CODE,
                        "system",
                        request.enabled() ? "true" : "false"));
        entity.update("system", request.enabled() ? "true" : "false");
        repository.save(entity);
        return ApiResponse.ok(Map.of("companyId", companyId, "enabled", request.enabled()));
    }

    // ----- Provider level config (阿里云百炼 / Ollama / Anthropic / OpenAI) -----

    @GetMapping("/providers")
    public ApiResponse<List<Map<String, Object>>> listProviders() {
        throw platformManagedModelConfig();
    }

    @PutMapping("/providers/{providerCode}")
    public ApiResponse<Map<String, Object>> updateProvider(
            @PathVariable String providerCode,
            @Valid @RequestBody UpdateProviderRequest request) {
        throw platformManagedModelConfig();
    }

    @PostMapping("/providers/{providerCode}/check")
    public ApiResponse<Map<String, Object>> checkProvider(@PathVariable String providerCode) {
        throw platformManagedModelConfig();
    }

    @GetMapping("/providers/{providerCode}/models")
    public ApiResponse<Map<String, Object>> providerModels(@PathVariable String providerCode) {
        throw platformManagedModelConfig();
    }

    @PostMapping("/providers/{providerCode}/models/fetch")
    public ApiResponse<Map<String, Object>> fetchProviderModels(@PathVariable String providerCode) {
        throw platformManagedModelConfig();
    }

    @PutMapping("/providers/{providerCode}/selected-models")
    public ApiResponse<Map<String, Object>> updateSelectedModels(
            @PathVariable String providerCode,
            @Valid @RequestBody UpdateSelectedModelsRequest request) {
        throw platformManagedModelConfig();
    }

    @GetMapping("/agent/base-models")
    public ApiResponse<List<Map<String, Object>>> listAgentBaseModels() {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(modelProviderService.agentBaseModels(companyId));
    }

    public record UpsertModelRequest(
            @NotBlank String sceneCode,
            @NotBlank String provider,
            @NotBlank String modelName
    ) {
    }

    public record ThinkingSettingRequest(boolean enabled) {
    }

    public record UpdateSelectedModelsRequest(List<String> selectedModels) {
    }

    public record UpdateProviderRequest(Boolean enabled, String apiBaseUrl, String apiKey) {
    }

    private ForbiddenException platformManagedModelConfig() {
        return new ForbiddenException("模型厂商和模型路由由运营平台统一配置，组织后台只开放计费用量查看。");
    }
}
