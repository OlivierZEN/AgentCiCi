package com.codehouse.ciciassistant.auth.service;

import com.codehouse.ciciassistant.auth.domain.AuthPasswordEntity;
import com.codehouse.ciciassistant.auth.domain.AuthPasswordRepository;
import com.codehouse.ciciassistant.common.error.UnauthorizedException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.stereotype.Service;

@Service
public class PasswordCredentialService {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    private final AuthPasswordRepository authPasswordRepository;

    public PasswordCredentialService(AuthPasswordRepository authPasswordRepository) {
        this.authPasswordRepository = authPasswordRepository;
    }

    public void verifyDefaultPassword(String password, String invalidMessage) {
        AuthPasswordEntity credential = authPasswordRepository.findById("default")
                .orElseThrow(() -> new UnauthorizedException("Password login is not initialized"));
        verifyPasswordHash(password,
                credential.getPasswordHash(),
                credential.getSalt(),
                credential.getIterations(),
                credential.getAlgorithm(),
                invalidMessage);
    }

    public void verifyPasswordHash(String password,
                                   String passwordHash,
                                   String salt,
                                   int iterations,
                                   String algorithm,
                                   String invalidMessage) {
        if (!ALGORITHM.equals(algorithm)) {
            throw new UnauthorizedException("Unsupported password credential");
        }
        try {
            byte[] expected = Base64.getDecoder().decode(passwordHash);
            KeySpec spec = new PBEKeySpec(
                    password == null ? new char[0] : password.toCharArray(),
                    salt.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    iterations,
                    expected.length * 8
            );
            byte[] actual = SecretKeyFactory.getInstance(algorithm).generateSecret(spec).getEncoded();
            if (!MessageDigest.isEqual(expected, actual)) {
                throw new UnauthorizedException(invalidMessage);
            }
        } catch (UnauthorizedException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new UnauthorizedException("Password verification failed");
        }
    }

    public PasswordHash hashPassword(String password) {
        byte[] saltBytes = new byte[16];
        new SecureRandom().nextBytes(saltBytes);
        String salt = Base64.getEncoder().encodeToString(saltBytes);
        int iterations = 120000;
        try {
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt.getBytes(java.nio.charset.StandardCharsets.UTF_8), iterations, 256);
            String passwordHash = Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded());
            return new PasswordHash(passwordHash, salt, iterations, ALGORITHM);
        } catch (Exception ex) {
            throw new IllegalStateException("Password hashing failed", ex);
        }
    }

    public record PasswordHash(String passwordHash, String salt, int iterations, String algorithm) {
    }
}
