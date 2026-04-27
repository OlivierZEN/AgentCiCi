package com.codehouse.ciciassistant.common.crypto;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * AES-GCM symmetric encryption for at-rest secrets (e.g. personal mailbox password).
 *
 * <p>Key material is loaded from config property {@code app.security.secret-key} (base64 of 32 bytes).
 * If missing or invalid, a deterministic dev-only fallback is used. Production deployments MUST set
 * a real key via environment variable {@code APP_SECRET_KEY}.
 */
@Service
public class SecretCipherService {

    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BYTES = 32;

    // Dev fallback: deterministic 32 bytes derived from a fixed literal, only used when no real key
    // is configured. It is NOT meant for production and every sensitive field encrypted with this
    // fallback will need re-encryption after the real key is set.
    private static final byte[] DEV_FALLBACK_KEY = new byte[] {
            (byte) 0x43, (byte) 0x49, (byte) 0x43, (byte) 0x49,
            (byte) 0x2d, (byte) 0x44, (byte) 0x45, (byte) 0x56,
            (byte) 0x2d, (byte) 0x53, (byte) 0x45, (byte) 0x43,
            (byte) 0x52, (byte) 0x45, (byte) 0x54, (byte) 0x2d,
            (byte) 0x31, (byte) 0x36, (byte) 0x2d, (byte) 0x42,
            (byte) 0x59, (byte) 0x54, (byte) 0x45, (byte) 0x53,
            (byte) 0x2d, (byte) 0x50, (byte) 0x41, (byte) 0x44,
            (byte) 0x44, (byte) 0x49, (byte) 0x4e, (byte) 0x47
    };

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();
    private final boolean usingFallback;

    public SecretCipherService(@Value("${app.security.secret-key:}") String base64Key) {
        byte[] keyBytes = null;
        boolean fallback = false;
        String trimmed = base64Key == null ? "" : base64Key.trim();
        if (!trimmed.isEmpty()) {
            try {
                byte[] decoded = Base64.getDecoder().decode(trimmed);
                if (decoded.length != KEY_LENGTH_BYTES) {
                    throw new IllegalArgumentException(
                            "app.security.secret-key must decode to " + KEY_LENGTH_BYTES + " bytes, got " + decoded.length);
                }
                keyBytes = decoded;
            } catch (IllegalArgumentException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IllegalArgumentException("app.security.secret-key is not valid base64: " + ex.getMessage(), ex);
            }
        } else {
            keyBytes = DEV_FALLBACK_KEY;
            fallback = true;
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
        this.usingFallback = fallback;
    }

    @PostConstruct
    void announceFallback() {
        if (usingFallback) {
            org.slf4j.LoggerFactory.getLogger(SecretCipherService.class).warn(
                    "[SecretCipherService] Using DEV fallback key. Set APP_SECRET_KEY (base64-encoded 32 bytes) for production.");
        }
    }

    public boolean isUsingFallback() {
        return usingFallback;
    }

    public EncryptedSecret encryptUtf8(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext is required");
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new EncryptedSecret(
                    Base64.getEncoder().encodeToString(cipherBytes),
                    Base64.getEncoder().encodeToString(iv));
        } catch (Exception ex) {
            throw new IllegalStateException("Encrypt failed", ex);
        }
    }

    public String decryptUtf8(String cipherBase64, String ivBase64) {
        if (cipherBase64 == null || ivBase64 == null) {
            throw new IllegalArgumentException("cipher and iv are required");
        }
        try {
            byte[] cipherBytes = Base64.getDecoder().decode(cipherBase64);
            byte[] iv = Base64.getDecoder().decode(ivBase64);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plain = cipher.doFinal(cipherBytes);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Decrypt failed", ex);
        }
    }

    public record EncryptedSecret(String cipherBase64, String ivBase64) {
    }
}
