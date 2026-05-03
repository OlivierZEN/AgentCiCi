package com.codehouse.ciciassistant.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChatOrchestratorServiceModelIdentityTest {

    @Test
    void shouldTellModelTheActualRoutedProviderAndModel() {
        String promptBlock = ChatOrchestratorService.buildModelIdentityPromptBlock(
                "aliyun-bailian",
                "deepseek-v4-pro");

        assertThat(promptBlock)
                .contains("阿里云百炼 (aliyun-bailian)")
                .contains("deepseek-v4-pro")
                .contains("只能依据以上两项回答")
                .contains("不得自称 Claude");
    }

    @Test
    void shouldKeepAnthropicLabelOnlyWhenProviderIsAnthropic() {
        String promptBlock = ChatOrchestratorService.buildModelIdentityPromptBlock(
                "anthropic",
                "claude-sonnet-4-5");

        assertThat(promptBlock)
                .contains("Anthropic")
                .contains("claude-sonnet-4-5");
    }
}
