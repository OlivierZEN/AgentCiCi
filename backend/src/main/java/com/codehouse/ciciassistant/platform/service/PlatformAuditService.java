package com.codehouse.ciciassistant.platform.service;

import com.codehouse.ciciassistant.platform.domain.PlatformAuditLogEntity;
import com.codehouse.ciciassistant.platform.domain.PlatformAuditLogRepository;
import com.codehouse.ciciassistant.security.service.SecurityRedactionService;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class PlatformAuditService {

    private final PlatformAuditLogRepository repository;
    private final SecurityRedactionService redactionService;

    public PlatformAuditService(PlatformAuditLogRepository repository) {
        this(repository, new SecurityRedactionService());
    }

    @Autowired
    public PlatformAuditService(PlatformAuditLogRepository repository, SecurityRedactionService redactionService) {
        this.repository = repository;
        this.redactionService = redactionService;
    }

    public void log(String companyId,
                    String userId,
                    String roleCode,
                    String eventType,
                    String resourceType,
                    String resourceKey,
                    String detail) {
        repository.save(new PlatformAuditLogEntity(
                companyId, userId, roleCode, eventType, resourceType, resourceKey, redact(detail)));
    }

    public List<PlatformAuditLogEntity> latest(String companyId) {
        return repository.findTop100ByCompanyIdOrderByIdDesc(companyId);
    }

    public boolean hasEventDetail(String companyId,
                                  String eventType,
                                  String resourceKey,
                                  String detailFragment) {
        return repository.existsByCompanyIdAndEventTypeAndResourceKeyAndDetailContaining(
                companyId, eventType, resourceKey, detailFragment);
    }

    public Map<String, Object> query(String companyId, PlatformAuditLogQuery query) {
        Instant to = query.to() == null ? Instant.now() : query.to();
        Instant from = query.from() == null ? to.minus(Duration.ofDays(7)) : query.from();
        if (from.isAfter(to)) {
            from = to;
        }
        if (from.isBefore(to.minus(Duration.ofDays(7)))) {
            from = to.minus(Duration.ofDays(7));
        }
        int limit = Math.min(Math.max(query.limit(), 1), 100);
        String eventType = normalized(query.eventType());
        String resourceType = normalized(query.resourceType());
        String q = normalized(query.q());
        PageRequest pageRequest = PageRequest.of(0, limit + 1);
        List<PlatformAuditLogEntity> rows = q == null
                ? repository.filterPlatformAuditLogs(companyId, from, to, eventType, resourceType, pageRequest)
                : repository.searchPlatformAuditLogs(companyId, from, to, eventType, resourceType, q, pageRequest);
        boolean hasMore = rows.size() > limit;
        List<Map<String, Object>> items = rows.stream()
                .limit(limit)
                .map(this::toPayload)
                .toList();
        return Map.of(
                "items", items,
                "from", from.toString(),
                "to", to.toString(),
                "hasMore", hasMore,
                "nextCursor", ""
        );
    }

    public Map<String, Object> toPayload(PlatformAuditLogEntity item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", item.getId());
        payload.put("companyId", item.getCompanyId());
        payload.put("userId", item.getUserId());
        payload.put("roleCode", item.getRoleCode());
        payload.put("eventType", item.getEventType());
        payload.put("resourceType", item.getResourceType());
        payload.put("resourceKey", item.getResourceKey());
        payload.put("detail", redact(item.getDetail()));
        payload.put("createdAt", item.getCreatedAt().toString());
        return payload;
    }

    private String redact(String raw) {
        return redactionService.redact(raw);
    }

    private String normalized(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    public record PlatformAuditLogQuery(Instant from, Instant to, String eventType, String resourceType, String q, int limit) {
    }
}
