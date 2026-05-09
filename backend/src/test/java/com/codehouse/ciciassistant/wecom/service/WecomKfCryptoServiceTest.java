package com.codehouse.ciciassistant.wecom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class WecomKfCryptoServiceTest {

    private final WecomKfCryptoService cryptoService = new WecomKfCryptoService();

    @Test
    void shouldEncryptDecryptAndVerifyCallbackSignature() {
        String aesKey = encodingAesKey();
        String token = "callback-token";
        String timestamp = "1777777777";
        String nonce = "nonce-1";
        String xml = """
                <xml>
                  <ToUserName><![CDATA[ww-demo]]></ToUserName>
                  <Event><![CDATA[kf_msg_or_event]]></Event>
                  <Token><![CDATA[sync-token]]></Token>
                  <OpenKfId><![CDATA[wk-demo]]></OpenKfId>
                </xml>
                """;

        String encrypted = cryptoService.encrypt(aesKey, "ww-demo", xml);
        String signature = cryptoService.signature(token, timestamp, nonce, encrypted);

        assertThat(cryptoService.matches(token, timestamp, nonce, encrypted, signature)).isTrue();
        assertThat(cryptoService.decrypt(aesKey, encrypted)).contains("<Event><![CDATA[kf_msg_or_event]]></Event>");
        assertThat(cryptoService.text(cryptoService.decrypt(aesKey, encrypted), "Token")).isEqualTo("sync-token");
    }

    @Test
    void shouldRejectInvalidAesKeyLength() {
        assertThatThrownBy(() -> cryptoService.decrypt("short", "payload"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("WeCom decrypt failed");
    }

    private String encodingAesKey() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes).substring(0, 43);
    }
}
