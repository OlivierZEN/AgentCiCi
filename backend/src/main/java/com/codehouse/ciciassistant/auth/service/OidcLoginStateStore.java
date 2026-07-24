package com.codehouse.ciciassistant.auth.service;

import com.codehouse.ciciassistant.common.crypto.SecretCipherService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Stores short-lived OIDC state and encrypted server-side refresh tokens. */
@Component
public class OidcLoginStateStore {

    private static final String TRANSACTION_PREFIX = "auth:oidc:transaction:";
    private static final String COMPLETION_PREFIX = "auth:oidc:completion:";
    private static final String SESSION_PREFIX = "auth:oidc:session:";

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final SecretCipherService secretCipherService;
    private final Map<String, TimedValue> fallback = new ConcurrentHashMap<>();

    public OidcLoginStateStore(ObjectMapper objectMapper,
                               ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                               SecretCipherService secretCipherService) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
        this.secretCipherService = secretCipherService;
    }

    public void saveTransaction(String state, LoginTransaction value, Duration ttl) {
        save(TRANSACTION_PREFIX + state, value, ttl);
    }

    public LoginTransaction consumeTransaction(String state) {
        return consume(TRANSACTION_PREFIX + state, LoginTransaction.class);
    }

    public void saveCompletion(String ticket, LoginCompletion value, Duration ttl) {
        save(COMPLETION_PREFIX + ticket, value, ttl);
    }

    public LoginCompletion consumeCompletion(String ticket) {
        return consume(COMPLETION_PREFIX + ticket, LoginCompletion.class);
    }

    public void saveRefreshSession(String sessionId, String refreshToken, Duration ttl) {
        SecretCipherService.EncryptedSecret encrypted = secretCipherService.encryptUtf8(refreshToken);
        save(SESSION_PREFIX + sessionId,
                new RefreshSession(encrypted.cipherBase64(), encrypted.ivBase64(), Instant.now().plus(ttl)), ttl);
    }

    private void save(String key, Object value, Duration ttl) {
        try {
            String payload = objectMapper.writeValueAsString(value);
            if (redisTemplate != null) {
                redisTemplate.opsForValue().set(key, payload, ttl);
                return;
            }
            fallback.put(key, new TimedValue(payload, Instant.now().plus(ttl)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to persist OIDC login state", ex);
        }
    }

    private <T> T consume(String key, Class<T> type) {
        try {
            String payload;
            if (redisTemplate != null) {
                payload = redisTemplate.opsForValue().getAndDelete(key);
            } else {
                TimedValue value = fallback.remove(key);
                payload = value == null || Instant.now().isAfter(value.expiresAt()) ? null : value.payload();
            }
            return payload == null || payload.isBlank() ? null : objectMapper.readValue(payload, type);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to consume OIDC login state", ex);
        }
    }

    public record LoginTransaction(String nonce, String pkceVerifier, String returnTo) {
    }

    public record LoginCompletion(Map<String, Object> login) {
    }

    private record RefreshSession(String cipher, String iv, Instant expiresAt) {
    }

    private record TimedValue(String payload, Instant expiresAt) {
    }
}
