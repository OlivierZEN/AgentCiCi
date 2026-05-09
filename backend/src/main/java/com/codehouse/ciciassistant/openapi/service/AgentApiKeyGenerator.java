package com.codehouse.ciciassistant.openapi.service;

import com.codehouse.ciciassistant.openapi.config.AgentOpenApiProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AgentApiKeyGenerator {

    public static final String KEY_PREFIX = "cici_ak_live_";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AgentOpenApiProperties properties;
    private final String jwtSecret;

    public AgentApiKeyGenerator(AgentOpenApiProperties properties,
                                @Value("${app.auth.jwt-secret:}") String jwtSecret) {
        this.properties = properties;
        this.jwtSecret = jwtSecret == null ? "" : jwtSecret;
    }

    public GeneratedKey generate(String publicId) {
        String secret = randomBase64Url(32);
        String plainKey = KEY_PREFIX + publicId + "_" + secret;
        return new GeneratedKey(publicId, plainKey, KEY_PREFIX + publicId + "...", hash(plainKey));
    }

    public String newPublicId() {
        String candidate = "";
        while (candidate.length() < 16) {
            candidate += randomBase64Url(18).replace("-", "").replace("_", "");
        }
        return candidate.substring(0, 16);
    }

    public String publicIdFromPlainKey(String plainKey) {
        if (plainKey == null || !plainKey.startsWith(KEY_PREFIX)) {
            return "";
        }
        String suffix = plainKey.substring(KEY_PREFIX.length());
        int separator = suffix.indexOf('_');
        if (separator <= 0) {
            return "";
        }
        return suffix.substring(0, separator);
    }

    public String hash(String plainKey) {
        String pepper = properties.getKeyPepper();
        if (pepper == null || pepper.isBlank()) {
            pepper = jwtSecret;
        }
        if (pepper == null || pepper.isBlank()) {
            throw new IllegalStateException("Agent Open API key pepper is not configured");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(plainKey.getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to hash Agent Open API key", ex);
        }
    }

    public boolean matches(String plainKey, String expectedHash) {
        if (plainKey == null || expectedHash == null || expectedHash.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(plainKey).getBytes(StandardCharsets.UTF_8),
                expectedHash.getBytes(StandardCharsets.UTF_8));
    }

    private static String randomBase64Url(int byteCount) {
        byte[] bytes = new byte[byteCount];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(String.format("%02x", b));
        }
        return out.toString();
    }

    public record GeneratedKey(
            String publicId,
            String plainKey,
            String keyPrefix,
            String keyHash
    ) {
    }
}
