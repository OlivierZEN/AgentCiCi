package com.codehouse.ciciassistant.feishu.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class FeishuPairingCodeStore {

    private static final Duration PAIRING_TTL = Duration.ofMinutes(10);
    private static final Duration MESSAGE_DEDUPE_TTL = Duration.ofHours(24);

    private final SecureRandom random = new SecureRandom();
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Map<String, PairCodePayload> inMemoryCodes = new ConcurrentHashMap<>();
    private final Map<String, Instant> inMemoryMessageDedupe = new ConcurrentHashMap<>();

    public FeishuPairingCodeStore(ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                                  ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
        this.objectMapper = objectMapper;
    }

    public PairingCode createCode(String companyId, String userId, String agentCode) {
        for (int i = 0; i < 10; i++) {
            String code = String.format("%06d", random.nextInt(1_000_000));
            PairCodePayload payload = new PairCodePayload(userId, normalizeAgentCode(agentCode), Instant.now().plus(PAIRING_TTL));
            if (saveCode(companyId, code, payload)) {
                return new PairingCode(code, (int) PAIRING_TTL.toSeconds(), payload.agentCode());
            }
        }
        throw new IllegalArgumentException("生成飞书配对码失败，请稍后重试");
    }

    public PairCodePayload consumeCode(String companyId, String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("配对码不能为空");
        }
        if (redisTemplate != null) {
            String key = pairingKey(companyId, code);
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                throw new IllegalArgumentException("配对码不存在或已过期");
            }
            redisTemplate.delete(key);
            return readPayload(value);
        }
        PairCodePayload payload = inMemoryCodes.remove(pairingKey(companyId, code));
        if (payload == null || Instant.now().isAfter(payload.expiresAt())) {
            throw new IllegalArgumentException("配对码不存在或已过期");
        }
        return payload;
    }

    public boolean markMessageProcessed(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return false;
        }
        if (redisTemplate != null) {
            Boolean saved = redisTemplate.opsForValue().setIfAbsent(messageKey(messageId), "1", MESSAGE_DEDUPE_TTL);
            return Boolean.TRUE.equals(saved);
        }
        Instant now = Instant.now();
        inMemoryMessageDedupe.entrySet().removeIf(entry -> entry.getValue().isBefore(now.minus(MESSAGE_DEDUPE_TTL)));
        return inMemoryMessageDedupe.putIfAbsent(messageKey(messageId), now) == null;
    }

    private boolean saveCode(String companyId, String code, PairCodePayload payload) {
        if (redisTemplate != null) {
            Boolean saved = redisTemplate.opsForValue()
                    .setIfAbsent(pairingKey(companyId, code), writePayload(payload), PAIRING_TTL);
            return Boolean.TRUE.equals(saved);
        }
        String key = pairingKey(companyId, code);
        inMemoryCodes.entrySet().removeIf(entry -> Instant.now().isAfter(entry.getValue().expiresAt()));
        return inMemoryCodes.putIfAbsent(key, payload) == null;
    }

    private String normalizeAgentCode(String agentCode) {
        return agentCode == null || agentCode.isBlank() ? "cici" : agentCode.trim();
    }

    private String pairingKey(String companyId, String code) {
        return "feishu:pair:code:" + companyId + ":" + code;
    }

    private String messageKey(String messageId) {
        return "feishu:msg:dedupe:" + messageId;
    }

    private String writePayload(PairCodePayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalArgumentException("配对码序列化失败");
        }
    }

    private PairCodePayload readPayload(String value) {
        try {
            return objectMapper.readValue(value, PairCodePayload.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("配对码解析失败");
        }
    }

    public record PairingCode(String code, int expiresInSeconds, String agentCode) {
    }

    public record PairCodePayload(String userId, String agentCode, Instant expiresAt) {
    }
}
