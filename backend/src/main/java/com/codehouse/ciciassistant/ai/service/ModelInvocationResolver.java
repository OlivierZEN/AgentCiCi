package com.codehouse.ciciassistant.ai.service;

import com.codehouse.ciciassistant.model.service.ModelProviderService;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Resolves every production model call from the platform-owned scene route.
 * Callers must never supplement this result with environment or integration credentials.
 */
@Service
public class ModelInvocationResolver {

    private final ModelProviderService modelProviderService;

    public ModelInvocationResolver(ModelProviderService modelProviderService) {
        this.modelProviderService = modelProviderService;
    }

    public ResolvedModelRoute resolveRoute(String companyId, String sceneCode) {
        Map<String, String> route = modelProviderService.resolveRuntimeModelRoute(companyId, sceneCode, null);
        String providerCode = required(route, "provider", sceneCode);
        String modelName = required(route, "modelName", sceneCode);
        return new ResolvedModelRoute(sceneCode, providerCode, modelName);
    }

    public ResolvedModelInvocation resolve(String companyId, String sceneCode) {
        ResolvedModelRoute route = resolveRoute(companyId, sceneCode);
        String providerCode = route.providerCode();
        String modelName = route.modelName();
        Map<String, String> credentials = modelProviderService.credentialsForProvider(companyId, providerCode);
        if (!Boolean.parseBoolean(credentials.getOrDefault("enabled", "false"))) {
            throw unavailable(sceneCode, "厂商未启用");
        }
        String apiBaseUrl = credentials.getOrDefault("apiBaseUrl", "").trim();
        if (apiBaseUrl.isBlank()) {
            throw unavailable(sceneCode, "API 地址未配置");
        }
        boolean apiKeyRequired = Boolean.parseBoolean(credentials.getOrDefault("apiKeyRequired", "true"));
        String apiKey = credentials.getOrDefault("apiKey", "").trim();
        if (apiKeyRequired && apiKey.isBlank()) {
            throw unavailable(sceneCode, "API Key 未配置");
        }
        return new ResolvedModelInvocation(sceneCode, providerCode, modelName, apiBaseUrl, apiKey, apiKeyRequired);
    }

    private static String required(Map<String, String> route, String key, String sceneCode) {
        String value = route == null ? "" : route.getOrDefault(key, "").trim();
        if (value.isBlank()) {
            throw unavailable(sceneCode, "路由缺少 " + key);
        }
        return value;
    }

    private static IllegalStateException unavailable(String sceneCode, String reason) {
        return new IllegalStateException("场景模型不可用 [" + sceneCode + "]: " + reason);
    }

    public record ResolvedModelInvocation(
            String sceneCode,
            String providerCode,
            String modelName,
            String apiBaseUrl,
            String apiKey,
            boolean apiKeyRequired) {
    }

    public record ResolvedModelRoute(String sceneCode, String providerCode, String modelName) {
    }
}
