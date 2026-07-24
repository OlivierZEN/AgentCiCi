package com.codehouse.ciciassistant.auth.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class SmsCodeStore {

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration SEND_WINDOW = Duration.ofMinutes(1);
    private static final int MAX_SEND_PER_DAY = 100;

    private final SecureRandom random = new SecureRandom();
    private final String mode;
    private final boolean rateLimitEnabled;
    private final StringRedisTemplate redisTemplate;
    private final Map<String, CodeEntry> codes = new ConcurrentHashMap<>();
    private final Map<String, RateEntry> rates = new ConcurrentHashMap<>();

    public SmsCodeStore(@Value("${app.auth.sms.store:memory}") String mode,
                        @Value("${app.auth.sms.rate-limit-enabled:true}") boolean rateLimitEnabled,
                        ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.mode = mode;
        this.rateLimitEnabled = rateLimitEnabled;
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
    }

    public String createCode(String companyId, String mobile) {
        if (isRedisMode()) {
            return createCodeWithRedis(companyId, mobile);
        }
        return createCodeInMemory(companyId, mobile);
    }

    public void verifyCode(String companyId, String mobile, String code) {
        if (isRedisMode()) {
            verifyCodeWithRedis(companyId, mobile, code);
            return;
        }
        verifyCodeInMemory(companyId, mobile, code);
    }

    private boolean isRedisMode() {
        return "redis".equalsIgnoreCase(mode) && redisTemplate != null;
    }

    private String createCodeInMemory(String companyId, String mobile) {
        String key = key(companyId, mobile);
        Instant now = Instant.now();
        if (rateLimitEnabled) {
            RateEntry rate = rates.computeIfAbsent(key, ignored -> new RateEntry(now, 0, Instant.EPOCH));
            rate.rotateDayIfNeeded(now);

            if (Duration.between(rate.lastSentAt, now).compareTo(SEND_WINDOW) < 0) {
                throw new IllegalArgumentException("SMS request too frequent, please retry later");
            }
            if (rate.sentToday >= MAX_SEND_PER_DAY) {
                throw new IllegalArgumentException("Daily SMS limit reached");
            }

            rate.sentToday += 1;
            rate.lastSentAt = now;
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        codes.put(key, new CodeEntry(code, now.plus(CODE_TTL)));
        return code;
    }

    private void verifyCodeInMemory(String companyId, String mobile, String code) {
        String key = key(companyId, mobile);
        CodeEntry entry = codes.get(key);
        if (entry == null || Instant.now().isAfter(entry.expiresAt)) {
            throw new IllegalArgumentException("Verification code expired or missing");
        }
        if (!entry.code.equals(code)) {
            throw new IllegalArgumentException("Invalid verification code");
        }
        codes.remove(key);
    }

    private String createCodeWithRedis(String companyId, String mobile) {
        String key = key(companyId, mobile);
        String codeKey = "auth:sms:code:" + key;
        String sendWindowKey = "auth:sms:window:" + key;
        String dailyLimitKey = "auth:sms:daily:" + key + ":" + Instant.now().toString().substring(0, 10);

        if (rateLimitEnabled) {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(sendWindowKey))) {
                throw new IllegalArgumentException("SMS request too frequent, please retry later");
            }

            Long sentToday = redisTemplate.opsForValue().increment(dailyLimitKey);
            if (sentToday != null && sentToday == 1) {
                redisTemplate.expire(dailyLimitKey, Duration.ofHours(24));
            }
            if (sentToday != null && sentToday > MAX_SEND_PER_DAY) {
                throw new IllegalArgumentException("Daily SMS limit reached");
            }
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        redisTemplate.opsForValue().set(codeKey, code, CODE_TTL);
        if (rateLimitEnabled) {
            redisTemplate.opsForValue().set(sendWindowKey, "1", SEND_WINDOW);
        }
        return code;
    }

    private void verifyCodeWithRedis(String companyId, String mobile, String code) {
        String codeKey = "auth:sms:code:" + key(companyId, mobile);
        String savedCode = redisTemplate.opsForValue().get(codeKey);
        if (savedCode == null) {
            throw new IllegalArgumentException("Verification code expired or missing");
        }
        if (!savedCode.equals(code)) {
            throw new IllegalArgumentException("Invalid verification code");
        }
        redisTemplate.delete(codeKey);
    }

    private String key(String companyId, String mobile) {
        return companyId + "::" + mobile;
    }

    private static final class CodeEntry {
        private final String code;
        private final Instant expiresAt;

        private CodeEntry(String code, Instant expiresAt) {
            this.code = code;
            this.expiresAt = expiresAt;
        }
    }

    private static final class RateEntry {
        private Instant dayAnchor;
        private int sentToday;
        private Instant lastSentAt;

        private RateEntry(Instant dayAnchor, int sentToday, Instant lastSentAt) {
            this.dayAnchor = dayAnchor;
            this.sentToday = sentToday;
            this.lastSentAt = lastSentAt;
        }

        private void rotateDayIfNeeded(Instant now) {
            if (Duration.between(dayAnchor, now).toHours() >= 24) {
                dayAnchor = now;
                sentToday = 0;
            }
        }
    }
}
