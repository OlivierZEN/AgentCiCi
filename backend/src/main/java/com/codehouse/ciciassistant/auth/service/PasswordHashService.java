package com.codehouse.ciciassistant.auth.service;

import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.stereotype.Service;

@Service
public class PasswordHashService {

    public PasswordHash hash(String password) {
        byte[] saltBytes = new byte[16];
        new SecureRandom().nextBytes(saltBytes);
        String salt = Base64.getEncoder().encodeToString(saltBytes);
        int iterations = 120000;
        String algorithm = "PBKDF2WithHmacSHA256";
        try {
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt.getBytes(java.nio.charset.StandardCharsets.UTF_8), iterations, 256);
            String passwordHash = Base64.getEncoder()
                    .encodeToString(SecretKeyFactory.getInstance(algorithm).generateSecret(spec).getEncoded());
            return new PasswordHash(passwordHash, salt, iterations, algorithm);
        } catch (Exception ex) {
            throw new IllegalStateException("Password hashing failed", ex);
        }
    }

    public record PasswordHash(String passwordHash, String salt, int iterations, String algorithm) {
    }
}
