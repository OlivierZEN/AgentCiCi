package com.codehouse.ciciassistant.openapi.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import org.junit.jupiter.api.Test;

class SafeRemoteFileFetcherTest {

    @Test
    void shouldAcceptOnlyStructurallySafeHttpsUrls() {
        assertThatCode(() -> SafeRemoteFileFetcher.requireSafeUri("https://files.example.com/a.png?token=short"))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> SafeRemoteFileFetcher.requireSafeUri("http://files.example.com/a.png"))
                .isInstanceOf(AgentOpenApiException.class)
                .extracting("code").isEqualTo("INVALID_FILE_URL");
        assertThatThrownBy(() -> SafeRemoteFileFetcher.requireSafeUri("https://user:pass@files.example.com/a.png"))
                .isInstanceOf(AgentOpenApiException.class)
                .extracting("code").isEqualTo("INVALID_FILE_URL");
        assertThatThrownBy(() -> SafeRemoteFileFetcher.requireSafeUri("https://files.example.com/a.png#fragment"))
                .isInstanceOf(AgentOpenApiException.class)
                .extracting("code").isEqualTo("INVALID_FILE_URL");
    }

    @Test
    void shouldRejectPrivateReservedAndMetadataAddresses() throws Exception {
        for (String address : new String[]{"127.0.0.1", "10.0.0.1", "172.16.0.1", "192.168.0.1",
                "169.254.169.254", "100.64.0.1", "192.0.2.1", "198.51.100.1", "203.0.113.1", "::1", "fc00::1"}) {
            assertThatThrownBy(() -> SafeRemoteFileFetcher.requirePublicAddress(InetAddress.getByName(address)))
                    .as(address)
                    .isInstanceOf(AgentOpenApiException.class)
                    .extracting("code").isEqualTo("REMOTE_URL_FORBIDDEN");
        }
        assertThatCode(() -> SafeRemoteFileFetcher.requirePublicAddress(InetAddress.getByName("8.8.8.8")))
                .doesNotThrowAnyException();
    }
}

