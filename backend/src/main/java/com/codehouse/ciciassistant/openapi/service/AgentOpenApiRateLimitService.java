package com.codehouse.ciciassistant.openapi.service;

import com.codehouse.ciciassistant.openapi.domain.AgentApiUsageDailyEntity;
import com.codehouse.ciciassistant.openapi.domain.AgentApiUsageDailyRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentOpenApiRateLimitService {

    private final AgentApiUsageDailyRepository usageDailyRepository;
    private final Map<String, WindowCounter> minuteCounters = new ConcurrentHashMap<>();

    public AgentOpenApiRateLimitService(AgentApiUsageDailyRepository usageDailyRepository) {
        this.usageDailyRepository = usageDailyRepository;
    }

    @Transactional
    public void reserve(AgentOpenApiAuthService.AuthenticatedCredential auth) {
        checkMinuteLimit(auth);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        AgentApiUsageDailyEntity usage = usageDailyRepository
                .findByCompanyIdAndCredentialIdAndUsageDate(auth.credential().getCompanyId(), auth.credential().getId(), today)
                .orElseGet(() -> usageDailyRepository.save(new AgentApiUsageDailyEntity(
                        auth.credential().getCompanyId(),
                        auth.credential().getId(),
                        today)));
        if (usage.getCallCount() >= auth.credential().getDailyQuota()) {
            throw new AgentOpenApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "daily_quota_exceeded",
                    "Daily quota exceeded");
        }
        usage.reserveCall();
    }

    @Transactional
    public void markSuccess(AgentOpenApiAuthService.AuthenticatedCredential auth, int elapsedMs) {
        usageForToday(auth).markSuccess(elapsedMs);
    }

    @Transactional
    public void markFailure(AgentOpenApiAuthService.AuthenticatedCredential auth, int elapsedMs) {
        usageForToday(auth).markFailure(elapsedMs);
    }

    private AgentApiUsageDailyEntity usageForToday(AgentOpenApiAuthService.AuthenticatedCredential auth) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return usageDailyRepository
                .findByCompanyIdAndCredentialIdAndUsageDate(auth.credential().getCompanyId(), auth.credential().getId(), today)
                .orElseGet(() -> usageDailyRepository.save(new AgentApiUsageDailyEntity(
                        auth.credential().getCompanyId(),
                        auth.credential().getId(),
                        today)));
    }

    private void checkMinuteLimit(AgentOpenApiAuthService.AuthenticatedCredential auth) {
        int limit = auth.credential().getRateLimitPerMinute();
        long minute = Instant.now().getEpochSecond() / 60L;
        String key = auth.credential().getId() + ":" + minute;
        WindowCounter counter = minuteCounters.compute(key, (ignored, existing) -> {
            if (existing == null || existing.minute() != minute) {
                return new WindowCounter(minute, new AtomicInteger(0));
            }
            return existing;
        });
        if (counter.count().incrementAndGet() > limit) {
            throw new AgentOpenApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "rate_limit_exceeded",
                    "Rate limit exceeded");
        }
    }

    private record WindowCounter(long minute, AtomicInteger count) {
    }
}
