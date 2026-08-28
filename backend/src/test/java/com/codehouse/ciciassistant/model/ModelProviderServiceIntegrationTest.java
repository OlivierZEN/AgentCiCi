package com.codehouse.ciciassistant.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.codehouse.ciciassistant.ai.service.ModelRouterService;
import com.codehouse.ciciassistant.auth.config.PlatformAccountProperties;
import com.codehouse.ciciassistant.model.domain.CompanyModelConfigEntity;
import com.codehouse.ciciassistant.model.domain.CompanyModelConfigRepository;
import com.codehouse.ciciassistant.model.domain.ModelProviderConfigEntity;
import com.codehouse.ciciassistant.model.domain.ModelProviderConfigRepository;
import com.codehouse.ciciassistant.model.service.ModelProviderService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "spring.profiles.active=default")
class ModelProviderServiceIntegrationTest {

    @Autowired
    private ModelProviderService modelProviderService;

    @Autowired
    private ModelRouterService modelRouterService;

    @Autowired
    private CompanyModelConfigRepository orgModelConfigRepository;

    @Autowired
    private ModelProviderConfigRepository providerRepository;

    @Autowired
    private PlatformAccountProperties platformAccountProperties;

    @Test
    void agentBaseModelsOnlyExposePlatformSelectedModels() {
        String companyId = "model-provider-test-org-" + UUID.randomUUID();

        List<Map<String, Object>> providers = modelProviderService.listProviders(companyId);
        assertThat(providers).extracting(row -> row.get("providerCode"))
                .containsExactly(
                        ModelProviderService.PROVIDER_ALIYUN,
                        ModelProviderService.PROVIDER_DEEPSEEK,
                        ModelProviderService.PROVIDER_OLLAMA,
                        ModelProviderService.PROVIDER_LMSTUDIO,
                        ModelProviderService.PROVIDER_ONEKEYTOKEN,
                        ModelProviderService.PROVIDER_ANTHROPIC,
                        ModelProviderService.PROVIDER_OPENAI
                );
        assertThat(providers.stream()
                .filter(row -> ModelProviderService.PROVIDER_OLLAMA.equals(row.get("providerCode"))
                        || ModelProviderService.PROVIDER_LMSTUDIO.equals(row.get("providerCode"))))
                .allSatisfy(row -> assertThat(row.get("apiKeyRequired")).isEqualTo(false));

        providers.forEach(row -> modelProviderService.updatePlatformSelectedModels(
                String.valueOf(row.get("providerCode")),
                List.of()));

        assertThat(modelProviderService.agentBaseModels(companyId))
                .as("builtin provider presets must not appear as selectable agent base models")
                .isEmpty();

        configureTrustedTextModels(ModelProviderService.PROVIDER_ALIYUN, "qwen3.6-plus", "glm-5.1");

        List<Map<String, Object>> baseModels = modelProviderService.agentBaseModels(companyId);

        assertThat(baseModels).extracting(row -> row.get("modelName"))
                .containsExactly("qwen3.6-plus", "glm-5.1");
        assertThat(baseModels).extracting(row -> row.get("providerName"))
                .containsOnly("阿里云百炼");
        assertThat(baseModels).extracting(row -> row.get("modelName"))
                .doesNotContain("qwen3.5-plus", "deepseek-chat", "gpt-4o", "llama3.1:8b");
    }

    @Test
    void runtimeCredentialsResolveFromPlatformProviderScope() {
        String companyId = "runtime-provider-test-org-" + UUID.randomUUID();

        modelProviderService.listProviders(companyId);
        modelProviderService.updateProvider(
                companyId,
                ModelProviderService.PROVIDER_DEEPSEEK,
                true,
                "https://tenant.example.invalid/v1",
                "tenant-secret");
        modelProviderService.updatePlatformProvider(
                ModelProviderService.PROVIDER_DEEPSEEK,
                true,
                "https://platform.example.invalid/v1",
                "platform-secret");

        Map<String, String> credentials = modelProviderService.credentialsForProvider(
                companyId,
                ModelProviderService.PROVIDER_DEEPSEEK);

        assertThat(credentials.get("apiBaseUrl")).isEqualTo("https://platform.example.invalid/v1");
        assertThat(credentials.get("apiKey")).isEqualTo("platform-secret");
    }

    @Test
    void runtimeRouteIgnoresCompanyMockFallbackAndUsesPlatformSelectedModel() {
        String companyId = "runtime-route-test-org-" + UUID.randomUUID();
        modelProviderService.listProviders(companyId);
        modelProviderService.updatePlatformProvider(
                ModelProviderService.PROVIDER_ALIYUN,
                true,
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "platform-secret");
        configureTrustedTextModels(ModelProviderService.PROVIDER_ALIYUN, "platform-chat-model");
        orgModelConfigRepository.save(new CompanyModelConfigEntity(companyId, "chat", "mock", "cici-default"));

        modelProviderService.updatePlatformModelRoute(
                "chat",
                ModelProviderService.PROVIDER_ALIYUN,
                "platform-chat-model");

        Map<String, String> route = modelRouterService.route(companyId, "chat");

        assertThat(route.get("provider")).isEqualTo(ModelProviderService.PROVIDER_ALIYUN);
        assertThat(route.get("modelName")).isEqualTo("platform-chat-model");
        assertThat(route.get("routeSource")).isEqualTo("platform_scene");
    }

    @Test
    void runtimeRouteUsesPlatformManagedSceneBeforeAgentPreference() {
        String companyId = "runtime-scene-route-" + UUID.randomUUID();
        modelProviderService.listProviders(companyId);
        modelProviderService.updatePlatformProvider(
                ModelProviderService.PROVIDER_ALIYUN,
                true,
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "platform-secret");
        configureTrustedTextModels(ModelProviderService.PROVIDER_ALIYUN, "qwen3.6-plus", "qwen3.5-omni-flash");
        modelProviderService.updatePlatformModelRoute(
                "chat",
                ModelProviderService.PROVIDER_ALIYUN,
                "qwen3.6-plus");

        Map<String, String> sceneRoute = modelRouterService.route(companyId, "chat", "qwen3.5-omni-flash");

        assertThat(sceneRoute.get("provider")).isEqualTo(ModelProviderService.PROVIDER_ALIYUN);
        assertThat(sceneRoute.get("modelName")).isEqualTo("qwen3.6-plus");
        assertThat(sceneRoute.get("routeSource")).isEqualTo("platform_scene");

        modelProviderService.deletePlatformModelRoute("chat");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> modelRouterService.route(companyId, "chat", "qwen3.5-omni-flash"))
                .hasMessageContaining("场景模型路由未配置");
    }

    @Test
    void runtimeCapabilityCheckUsesTheSamePlatformScopeAsTheRuntimeRoute() {
        String companyId = "vision-org-" + UUID.randomUUID();
        modelProviderService.listProviders(companyId);
        modelProviderService.updatePlatformProvider(
                ModelProviderService.PROVIDER_ALIYUN,
                true,
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "platform-secret");
        configureTrustedVisionModel(ModelProviderService.PROVIDER_ALIYUN, "platform-vision-model");

        assertThat(modelProviderService.supportsTrustedCapability(
                companyId,
                ModelProviderService.PROVIDER_ALIYUN,
                "platform-vision-model",
                "vision"))
                .as("tenant runtime checks must read the platform-governed capability catalogue")
                .isTrue();
    }

    @Test
    void exposesDomainNeutralOntologyModelingSceneRoute() {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> routes = (List<Map<String, Object>>)
                modelProviderService.platformModelRouteSettings().get("routes");

        assertThat(routes)
                .anySatisfy(route -> {
                    assertThat(route.get("sceneCode")).isEqualTo("ontology-modeling");
                    assertThat(route.get("displayName")).isEqualTo("本体建模");
                    assertThat(route.get("description").toString())
                            .contains("业务语义", "草稿");
                });
    }

    private void configureTrustedTextModels(String providerCode, String... modelNames) {
        String scopeId = platformAccountProperties.getGovernanceCompanyId();
        if (scopeId == null || scopeId.isBlank()) {
            scopeId = PlatformAccountProperties.LEGACY_DEFAULT_GOVERNANCE_COMPANY_ID;
        }
        ModelProviderConfigEntity provider = providerRepository
                .findByCompanyIdAndProviderCode(scopeId, providerCode)
                .orElseThrow();
        String capabilities = java.util.Arrays.stream(modelNames)
                .map(modelName -> "\\\"" + modelName + "\\\":[\\\"text\\\"]")
                .collect(Collectors.joining(","));
        String evidence = java.util.Arrays.stream(modelNames)
                .map(modelName -> "\\\"" + modelName + "\\\":{\\\"source\\\":\\\"provider_catalog\\\",\\\"confirmedAt\\\":\\\"2026-08-13T00:00:00Z\\\"}")
                .collect(Collectors.joining(","));
        provider.setConfigJson("{\\\"modelCapabilities\\\":{" + capabilities + "},\\\"modelCapabilityConfirmations\\\":{" + evidence + "}}");
        provider.touch();
        providerRepository.save(provider);
        modelProviderService.updatePlatformSelectedModels(providerCode, List.of(modelNames));
    }

    private void configureTrustedVisionModel(String providerCode, String modelName) {
        String scopeId = platformAccountProperties.getGovernanceCompanyId();
        if (scopeId == null || scopeId.isBlank()) {
            scopeId = PlatformAccountProperties.LEGACY_DEFAULT_GOVERNANCE_COMPANY_ID;
        }
        ModelProviderConfigEntity provider = providerRepository
                .findByCompanyIdAndProviderCode(scopeId, providerCode)
                .orElseThrow();
        provider.setConfigJson("{\"modelCapabilities\":{\"" + modelName
                + "\":[\"text\",\"vision\"]},\"modelCapabilityConfirmations\":{\"" + modelName
                + "\":{\"source\":\"provider_catalog\",\"confirmedAt\":\"2026-08-13T00:00:00Z\"}}}");
        provider.touch();
        providerRepository.save(provider);
        modelProviderService.updatePlatformSelectedModels(providerCode, List.of(modelName));
    }
}
