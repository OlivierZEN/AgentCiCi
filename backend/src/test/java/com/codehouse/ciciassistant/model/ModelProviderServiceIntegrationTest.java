package com.codehouse.ciciassistant.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.codehouse.ciciassistant.ai.service.ModelRouterService;
import com.codehouse.ciciassistant.model.domain.OrgModelConfigEntity;
import com.codehouse.ciciassistant.model.domain.OrgModelConfigRepository;
import com.codehouse.ciciassistant.model.service.ModelProviderService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    private OrgModelConfigRepository orgModelConfigRepository;

    @Test
    void agentBaseModelsOnlyExposePlatformSelectedModels() {
        String orgId = "model-provider-test-org-" + UUID.randomUUID();

        List<Map<String, Object>> providers = modelProviderService.listProviders(orgId);
        assertThat(providers).extracting(row -> row.get("providerCode"))
                .containsExactly(
                        ModelProviderService.PROVIDER_ALIYUN,
                        ModelProviderService.PROVIDER_DEEPSEEK,
                        ModelProviderService.PROVIDER_OLLAMA,
                        ModelProviderService.PROVIDER_LMSTUDIO,
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

        assertThat(modelProviderService.agentBaseModels(orgId))
                .as("builtin provider presets must not appear as selectable agent base models")
                .isEmpty();

        modelProviderService.updatePlatformSelectedModels(
                ModelProviderService.PROVIDER_ALIYUN,
                List.of("qwen3.6-plus", "glm-5.1"));

        List<Map<String, Object>> baseModels = modelProviderService.agentBaseModels(orgId);

        assertThat(baseModels).extracting(row -> row.get("modelName"))
                .containsExactly("qwen3.6-plus", "glm-5.1");
        assertThat(baseModels).extracting(row -> row.get("providerName"))
                .containsOnly("阿里云百炼");
        assertThat(baseModels).extracting(row -> row.get("modelName"))
                .doesNotContain("qwen3.5-plus", "deepseek-chat", "gpt-4o", "llama3.1:8b");
    }

    @Test
    void runtimeCredentialsResolveFromPlatformProviderScope() {
        String orgId = "runtime-provider-test-org-" + UUID.randomUUID();

        modelProviderService.listProviders(orgId);
        modelProviderService.updateProvider(
                orgId,
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
                orgId,
                ModelProviderService.PROVIDER_DEEPSEEK);

        assertThat(credentials.get("apiBaseUrl")).isEqualTo("https://platform.example.invalid/v1");
        assertThat(credentials.get("apiKey")).isEqualTo("platform-secret");
    }

    @Test
    void runtimeRouteIgnoresOrganizationMockFallbackAndUsesPlatformSelectedModel() {
        String orgId = "runtime-route-test-org-" + UUID.randomUUID();
        modelProviderService.listProviders(orgId);
        modelProviderService.updatePlatformProvider(
                ModelProviderService.PROVIDER_ALIYUN,
                true,
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "platform-secret");
        modelProviderService.updatePlatformSelectedModels(
                ModelProviderService.PROVIDER_ALIYUN,
                List.of("platform-chat-model"));
        orgModelConfigRepository.save(new OrgModelConfigEntity(orgId, "chat", "mock", "cici-default"));

        Map<String, String> route = modelRouterService.route(orgId, "chat");

        assertThat(route.get("provider")).isEqualTo(ModelProviderService.PROVIDER_ALIYUN);
        assertThat(route.get("modelName")).isEqualTo("platform-chat-model");
    }
}
