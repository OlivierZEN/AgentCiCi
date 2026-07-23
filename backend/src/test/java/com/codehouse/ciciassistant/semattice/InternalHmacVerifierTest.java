package com.codehouse.ciciassistant.semattice;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class InternalHmacVerifierTest {

    private static final String KEY = "test-only-internal-key-material-that-is-long-enough";

    @Test
    void acceptsFreshSignedRequestAndRejectsReplayAndTampering() throws Exception {
        InternalHmacVerifier verifier = new InternalHmacVerifier(KEY);
        String timestamp = Long.toString(java.time.Instant.now().getEpochSecond());
        String nonce = "0123456789abcdef0123456789abcdef";
        String body = "{\"companyId\":\"org2sva14i4udjmi2t4s\",\"idempotencyKey\":\"request-1\"}";
        String signature = signature("semattice", "POST", "/internal/semattice/provisioning/reservations", timestamp, nonce, body);

        assertDoesNotThrow(() -> verifier.verify("semattice", "POST", "/internal/semattice/provisioning/reservations",
                timestamp, nonce, signature, body));
        assertThrows(ResponseStatusException.class, () -> verifier.verify("semattice", "POST",
                "/internal/semattice/provisioning/reservations", timestamp, nonce, signature, body));
        assertThrows(ResponseStatusException.class, () -> verifier.verify("semattice", "POST",
                "/internal/semattice/provisioning/reservations", timestamp, "fedcba9876543210fedcba9876543210", signature, "{}"));
    }

    private String signature(String serviceId, String method, String path, String timestamp, String nonce, String body) throws Exception {
        String bodyHash = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return java.util.HexFormat.of().formatHex(mac.doFinal(String.join("\n", serviceId, method, path, timestamp, nonce, bodyHash)
                .getBytes(StandardCharsets.UTF_8)));
    }
}
