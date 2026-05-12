package com.codehouse.ciciassistant.model;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    void agentBaseModelsOnlyExposeConfiguredModels() {
        String orgId = "model-provider-test-org-" + UUID.randomUUID();

        assertThat(modelProviderService.agentBaseModels(orgId))
                .as("builtin provider presets must not appear as selectable agent base models")
                .isEmpty();

        modelProviderService.updateSelectedModels(
                orgId,
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
}
