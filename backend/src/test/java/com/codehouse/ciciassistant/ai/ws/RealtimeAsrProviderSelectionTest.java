package com.codehouse.ciciassistant.ai.ws;

import static org.assertj.core.api.Assertions.assertThat;

import com.codehouse.ciciassistant.integration.service.IntegrationAppService;
import org.junit.jupiter.api.Test;

class RealtimeAsrProviderSelectionTest {

    @Test
    void keepsAutoOnAliyunSoSpeakerDiarizationCannotRerouteChat() {
        assertThat(AliyunRealtimeAsrWebSocketHandler.selectRealtimeProvider("auto"))
                .isEqualTo("aliyun");
    }

    @Test
    void preservesExplicitProviderSelection() {
        assertThat(AliyunRealtimeAsrWebSocketHandler.selectRealtimeProvider("iflytek"))
                .isEqualTo("iflytek");
        assertThat(AliyunRealtimeAsrWebSocketHandler.selectRealtimeProvider("xunfei"))
                .isEqualTo("iflytek");
        assertThat(AliyunRealtimeAsrWebSocketHandler.selectRealtimeProvider("aliyun"))
                .isEqualTo("aliyun");
        assertThat(AliyunRealtimeAsrWebSocketHandler.selectRealtimeProvider(""))
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
