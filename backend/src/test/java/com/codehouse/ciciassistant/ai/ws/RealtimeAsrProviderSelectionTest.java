package com.codehouse.ciciassistant.ai.ws;

import static org.assertj.core.api.Assertions.assertThat;

import com.codehouse.ciciassistant.integration.service.IntegrationAppService;
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

    @Test
    void recognizesGovernedIflytekSceneRoute() {
        assertThat(AliyunRealtimeAsrWebSocketHandler.isIflytekRoute("iflytek_asr")).isTrue();
        assertThat(AliyunRealtimeAsrWebSocketHandler.isIflytekRoute("aliyun-bailian")).isFalse();
        assertThat(AliyunRealtimeAsrWebSocketHandler.isIflytekRoute("iflytek")).isFalse();
    }

    @Test
    void normalizesTheOfficialIflytekHostRootToItsRealtimeProtocolPath() {
        assertThat(IntegrationAppService.normalizeIflytekRealtimeUrl("wss://office-api-ast-dx.iflyaisol.com/"))
                .isEqualTo(IntegrationAppService.DEFAULT_IFLYTEK_REALTIME_URL);
        assertThat(IntegrationAppService.normalizeIflytekRealtimeUrl("wss://speech.example.test/custom"))
                .isEqualTo("wss://speech.example.test/custom");
    }
}
