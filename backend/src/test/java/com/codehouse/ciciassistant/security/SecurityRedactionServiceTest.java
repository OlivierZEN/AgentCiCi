package com.codehouse.ciciassistant.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.codehouse.ciciassistant.security.service.SecurityRedactionService;
import org.junit.jupiter.api.Test;

class SecurityRedactionServiceTest {

    private final SecurityRedactionService redactionService = new SecurityRedactionService();

    @Test
    void redactsCommonPersonalAndSecretDataBeforePersistence() {
        String raw = """
                联系电话 13812345678，邮箱 alice@example.com，
                身份证 110101199003071234，银行卡 6222021234567890123，
                Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signature
                apiKey=sk-test-abcdef1234567890
                -----BEGIN PRIVATE KEY-----
                secret
                -----END PRIVATE KEY-----
                登录 IP 192.168.31.10
                """;

        String redacted = redactionService.redact(raw);

        assertThat(redacted)
                .contains("138****5678")
                .contains("[email]")
                .contains("110101********1234")
                .contains("622202*********0123")
                .contains("Authorization: Bearer [redacted]")
                .contains("apiKey=[redacted]")
                .contains("[private-key]")
                .contains("192.168.*.*");
        assertThat(redacted)
                .doesNotContain("alice@example.com")
                .doesNotContain("110101199003071234")
                .doesNotContain("6222021234567890123")
                .doesNotContain("sk-test-abcdef1234567890")
                .doesNotContain("-----BEGIN PRIVATE KEY-----");
    }

    @Test
    void detectsPersonalAndSecretDataWithCategories() {
        var findings = redactionService.detect("手机号 13900001111，token=abc.def.ghi，邮箱 bob@example.com");

        assertThat(findings)
                .extracting(SecurityRedactionService.RedactionFinding::category)
                .contains("MOBILE_PHONE", "SECRET", "EMAIL");
    }
}
