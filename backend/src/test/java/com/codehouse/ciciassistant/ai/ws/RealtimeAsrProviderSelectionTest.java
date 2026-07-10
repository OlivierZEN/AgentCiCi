package com.codehouse.ciciassistant.ai.ws;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RealtimeAsrProviderSelectionTest {

    @Test
    void selectsIflytekForAutoDiarizationWhenConfigured() {
        assertThat(AliyunRealtimeAsrWebSocketHandler.selectRealtimeProvider("auto", true, true))
                .isEqualTo("iflytek");
    }

    @Test
    void fallsBackToAliyunWhenAutoDiarizationIsUnavailable() {
        assertThat(AliyunRealtimeAsrWebSocketHandler.selectRealtimeProvider("auto", true, false))
                .isEqualTo("aliyun");
    }

    @Test
    void preservesExplicitProviderSelection() {
        assertThat(AliyunRealtimeAsrWebSocketHandler.selectRealtimeProvider("iflytek", false, false))
                .isEqualTo("iflytek");
        assertThat(AliyunRealtimeAsrWebSocketHandler.selectRealtimeProvider("aliyun", true, true))
                .isEqualTo("aliyun");
        assertThat(AliyunRealtimeAsrWebSocketHandler.selectRealtimeProvider("", true, true))
                .isEqualTo("aliyun");
    }
}
