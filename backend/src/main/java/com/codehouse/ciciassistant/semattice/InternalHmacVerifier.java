package com.codehouse.ciciassistant.semattice;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class InternalHmacVerifier {

    private static final long MAX_SKEW_SECONDS = 300;
    private final String peerHmacKey;
    private final Map<String, Long> usedNonces = new ConcurrentHashMap<>();

    public InternalHmacVerifier(@Value("${app.native-agentcici.internal-hmac-key:}") String peerHmacKey) {
        this.peerHmacKey = peerHmacKey == null ? "" : peerHmacKey.trim();
    }

    public void verify(String serviceId, String method, String path, String timestamp, String nonce, String signature, String body) {
        long now = Instant.now().getEpochSecond();
        long requestTime;
        try {
            requestTime = Long.parseLong(timestamp);
        } catch (RuntimeException exception) {
            throw forbidden();
        }
        if (!"semattice".equals(serviceId) || peerHmacKey.length() < 32
                || nonce == null || nonce.length() < 16 || signature == null
                || Math.abs(now - requestTime) > MAX_SKEW_SECONDS) {
            throw forbidden();
        }
        String payload = String.join("\n", serviceId, method, path, timestamp, nonce, sha256(body == null ? "" : body));
        String expected = hmac(payload, peerHmacKey);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), signature.getBytes(StandardCharsets.US_ASCII))) {
            throw forbidden();
        }
        usedNonces.entrySet().removeIf(entry -> entry.getValue() < now);
        if (usedNonces.putIfAbsent(serviceId + ':' + nonce, now + MAX_SKEW_SECONDS) != null) {
            throw forbidden();
        }
    }

    private String hmac(String payload, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw forbidden();
        }
    }

    private String sha256(String raw) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw forbidden();
        }
    }

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "internal service authentication failed");
    }
}
