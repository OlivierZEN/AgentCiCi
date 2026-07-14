package com.codehouse.ciciassistant.openapi.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentOpenApiConversationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSanitizeInternalCrmEventsBeforePublishingAgentThoughtObservation() throws Exception {
        Map<String, Object> rawPayload = Map.of(
                "toolName", "crm_product_sales_rank",
                "payload", "{\"status\":\"SUCCESS\",\"productId\":\"p-secret\",\"ownerId\":\"005-secret\"}",
                "arguments", Map.of("userId", "u-secret", "accessToken", "token-secret"));

        Object sanitized = AgentOpenApiConversationService.safeAgentThoughtObservation("tool_result", rawPayload);
        String serialized = objectMapper.writeValueAsString(sanitized);

        assertThat(serialized)
                .contains("工具处理完成")
                .doesNotContain("crm_product_sales_rank", "SUCCESS", "productId", "p-secret",
                        "ownerId", "005-secret", "arguments", "userId", "u-secret",
                        "accessToken", "token-secret", "{\"");
    }

    @Test
    void shouldNotForwardUnknownInternalEventPayloadIntoAgentThoughtObservation() throws Exception {
        Object sanitized = AgentOpenApiConversationService.safeAgentThoughtObservation(
                "internal_runtime_event", Map.of(
                "credentials", "secret-value",
                "internalId", "id-secret"));
        String serialized = objectMapper.writeValueAsString(sanitized);

        assertThat(serialized)
                .contains("运行状态已更新")
                .doesNotContain("credentials", "secret-value", "internalId", "id-secret");
    }
}
