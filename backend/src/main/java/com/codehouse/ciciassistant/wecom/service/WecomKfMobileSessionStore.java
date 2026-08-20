package com.codehouse.ciciassistant.wecom.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class WecomKfMobileSessionStore {

    private static final String OAUTH_PREFIX = "wecom:kf:mobile:oauth:";
    private static final String SESSION_PREFIX = "wecom:kf:mobile:session:";

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final Map<String, TimedValue> fallback = new ConcurrentHashMap<>();

    public WecomKfMobileSessionStore(ObjectMapper objectMapper,
                                     ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
    }

    public void saveOAuthState(String state, UUID entryId, Duration ttl) {
        save(OAUTH_PREFIX + state, new OAuthState(entryId), ttl);
    }

    public OAuthState consumeOAuthState(String state) {
        return consume(OAUTH_PREFIX + state, OAuthState.class);
    }

    public void saveSession(String token, MobileSession session, Duration ttl) {
        save(SESSION_PREFIX + token, session, ttl);
    }

    public MobileSession findSession(String token) {
        return read(SESSION_PREFIX + token, MobileSession.class);
    }

    private void save(String key, Object value, Duration ttl) {
        try {
            String payload = objectMapper.writeValueAsString(value);
            if (redisTemplate != null) {
                redisTemplate.opsForValue().set(key, payload, ttl);
            } else {
                fallback.put(key, new TimedValue(payload, Instant.now().plus(ttl)));
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to persist mobile customer service session", ex);
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
            return deserialize(payload, type);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to consume mobile customer service session", ex);
        }
    }

    private <T> T read(String key, Class<T> type) {
        try {
            String payload;
            if (redisTemplate != null) {
                payload = redisTemplate.opsForValue().get(key);
            } else {
                TimedValue value = fallback.get(key);
                if (value != null && Instant.now().isAfter(value.expiresAt())) {
                    fallback.remove(key, value);
                    value = null;
                }
                payload = value == null ? null : value.payload();
            }
            return deserialize(payload, type);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to read mobile customer service session", ex);
        }
    }

    private <T> T deserialize(String payload, Class<T> type) throws Exception {
        return payload == null || payload.isBlank() ? null : objectMapper.readValue(payload, type);
    }

    public record OAuthState(UUID entryId) {
    }

    public record MobileSession(UUID entryId, String companyId, String operatorUserId, Instant expiresAt) {
    }

    private record TimedValue(String payload, Instant expiresAt) {
    }
}
