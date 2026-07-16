package com.codehouse.ciciassistant.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SecretKeyMatcherTest {

    @Test
    void recognizesSensitiveKeysAcrossCommonNamingConventions() {
        for (String key : List.of(
                "clientSecret",
                "db_password",
                "refresh-token",
                "credentialBundle",
                "encryptedValue",
                "apiKey",
                "api_key",
                "accessKey",
                "access_key",
                "authorizationHeader",
                "cookie",
                "privateKey",
                "private_key",
                "safetyMark",
                "config_json")) {
            assertThat(SecretKeyMatcher.matches(key)).as(key).isTrue();
        }
    }

    @Test
    void preservesOrdinaryBusinessKeys() {
        for (String key : List.of(
                "workspaceKey",
                "ownerKey",
                "businessLabel",
                "sampleDataJson",
                "customerTier",
                "catalog")) {
            assertThat(SecretKeyMatcher.matches(key)).as(key).isFalse();
        }
        assertThat(SecretKeyMatcher.matches(null)).isFalse();
    }
}
