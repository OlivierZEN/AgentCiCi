package com.codehouse.ciciassistant.wecom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.common.crypto.SecretCipherService;
import com.codehouse.ciciassistant.wecom.domain.WecomKfAccountEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

class WecomKfClientTest {

    @Test
    void shouldPreserveHumanOriginAndServicerWhenSyncingMessages() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        SecretCipherService cipherService = new SecretCipherService("");
        WecomKfAccountEntity account = account(cipherService);
        SecretCipherService.EncryptedSecret token = cipherService.encryptUtf8("cached-kf-token");
        account.updateAccessToken(token.cipherBase64(), token.ivBase64(), Instant.now().plusSeconds(600));
        WecomKfProperties properties = new WecomKfProperties();
        WecomKfClient client = new WecomKfClient(objectMapper, mock(WecomKfConfigService.class), cipherService, properties,
                (method, path, payload) -> {
                    assertThat(method).isEqualTo("POST");
                    assertThat(path).contains("/cgi-bin/kf/sync_msg", "cached-kf-token");
                    return objectMapper.readTree("""
                            {"errcode":0,"msg_list":[{"msgid":"m-1","open_kfid":"wk-demo","external_userid":"ext-1",
                            "origin":5,"servicer_userid":"agent-1","msgtype":"text","text":{"content":"人工回复"},"send_time":1}],
                            "next_cursor":"next","has_more":0}
                            """);
                });

        WecomKfClient.SyncedMessage message = client.syncMessages(
                new WecomKfConfigService.ResolvedAccount(account, "kf-secret", "aes"), "sync-token", "").messages().getFirst();

        assertThat(message.origin()).isEqualTo(5);
        assertThat(message.servicerUserId()).isEqualTo("agent-1");
        assertThat(message.content()).isEqualTo("人工回复");
    }

    @Test
    void shouldUseIndependentApplicationSecretForMobileMemberOAuth() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        SecretCipherService cipherService = new SecretCipherService("");
        WecomKfAccountEntity account = account(cipherService);
        WecomKfConfigService configService = mock(WecomKfConfigService.class);
        when(configService.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AtomicInteger calls = new AtomicInteger();
        WecomKfClient client = new WecomKfClient(objectMapper, configService, cipherService, new WecomKfProperties(),
                (method, path, payload) -> {
                    if (calls.getAndIncrement() == 0) {
                        assertThat(path).contains("corpsecret=mobile-app-secret").doesNotContain("kf-secret");
                        return objectMapper.readTree("{\"errcode\":0,\"access_token\":\"app-token\",\"expires_in\":7200}");
                    }
                    assertThat(path).contains("/cgi-bin/auth/getuserinfo", "access_token=app-token", "code=oauth-code");
                    return objectMapper.readTree("{\"errcode\":0,\"UserId\":\"agent-1\"}");
                });

        assertThat(client.resolveCurrentMember(
                new WecomKfConfigService.ResolvedAccount(account, "kf-secret", "aes", "mobile-app-secret"), "oauth-code"))
                .contains("agent-1");
        assertThat(calls).hasValue(2);
    }

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

    private WecomKfAccountEntity account(SecretCipherService cipherService) {
        SecretCipherService.EncryptedSecret secret = cipherService.encryptUtf8("kf-secret");
        SecretCipherService.EncryptedSecret aesKey = cipherService.encryptUtf8("abcdefghijklmnopqrstuvwxyzABCDEFG1234567890");
        return new WecomKfAccountEntity(
                "org-1", "ww-demo", "wk-demo", "售后客服", secret.cipherBase64(), secret.ivBase64(),
                "callback-token", aesKey.cipherBase64(), aesKey.ivBase64(), "after-sales-agent", "user-1");
    }
}
