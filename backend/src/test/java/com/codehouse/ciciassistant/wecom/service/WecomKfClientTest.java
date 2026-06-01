package com.codehouse.ciciassistant.wecom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.common.crypto.SecretCipherService;
import com.codehouse.ciciassistant.wecom.domain.WecomKfAccountEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class WecomKfClientTest {

    @Test
    void shouldTestConnectionByRefreshingAccessTokenWithoutReturningToken() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        SecretCipherService cipherService = new SecretCipherService("");
        SecretCipherService.EncryptedSecret secret = cipherService.encryptUtf8("kf-secret");
        SecretCipherService.EncryptedSecret aesKey = cipherService.encryptUtf8("abcdefghijklmnopqrstuvwxyzABCDEFG1234567890");
        WecomKfAccountEntity account = new WecomKfAccountEntity(
                "org-1",
                "ww-demo",
                "wk-demo",
                "售后客服",
                secret.cipherBase64(),
                secret.ivBase64(),
                "callback-token",
                aesKey.cipherBase64(),
                aesKey.ivBase64(),
                "after-sales-agent",
                "user-1");
        WecomKfConfigService configService = mock(WecomKfConfigService.class);
        when(configService.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        WecomKfProperties properties = new WecomKfProperties();
        properties.setApiBaseUrl("https://wecom.example.test");
        WecomKfClient client = new WecomKfClient(objectMapper, configService, cipherService, properties, (method, path, payload) -> {
            assertThat(method).isEqualTo("GET");
            assertThat(path).contains("/cgi-bin/gettoken", "corpid=ww-demo", "corpsecret=kf-secret");
            return objectMapper.readTree("""
                    {"errcode":0,"errmsg":"ok","access_token":"wecom-token-secret","expires_in":7200}
                    """);
        });

        WecomKfClient.ConnectionTestResult result = client.testConnection(
                new WecomKfConfigService.ResolvedAccount(account, "kf-secret", "abcdefghijklmnopqrstuvwxyzABCDEFG1234567890"));

        assertThat(result.status()).isEqualTo("connected");
        assertThat(result.accessTokenExpiresAt()).isNotNull();
        assertThat(result.toString()).doesNotContain("wecom-token-secret", "kf-secret");
        assertThat(cipherService.decryptUtf8(account.getAccessTokenCipher(), account.getAccessTokenIv()))
                .isEqualTo("wecom-token-secret");
    }
}
