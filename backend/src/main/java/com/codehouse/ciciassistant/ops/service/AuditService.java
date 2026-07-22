package com.codehouse.ciciassistant.ops.service;

import com.codehouse.ciciassistant.ops.domain.AuditLogEntity;
import com.codehouse.ciciassistant.ops.domain.AuditLogRepository;
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
public class AuditService {

    private final AuditLogRepository repository;
    private final SecurityRedactionService redactionService;

    public AuditService(AuditLogRepository repository) {
        this(repository, new SecurityRedactionService());
    }

    @Autowired
    public AuditService(AuditLogRepository repository, SecurityRedactionService redactionService) {
        this.repository = repository;
        this.redactionService = redactionService;
    }

    public void log(String orgId, String userId, String eventType, String detail) {
        repository.save(new AuditLogEntity(orgId, userId, eventType, redact(detail)));
    }

    public List<Map<String, Object>> latest(String orgId) {
        return repository.findTop50ByOrgIdOrderByIdDesc(orgId).stream()
                .map(this::toPayload)
                .toList();
    }

    public Map<String, Object> query(String orgId, AuditLogQuery query) {
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
        String q = normalized(query.q());
        List<AuditLogEntity> rows = repository.searchOrgAuditLogs(
                orgId, from, to, eventType, q, PageRequest.of(0, limit + 1));
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

    private Map<String, Object> toPayload(AuditLogEntity item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", item.getId());
        payload.put("orgId", item.getOrgId());
        payload.put("userId", item.getUserId());
        payload.put("eventType", item.getEventType());
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

    public record AuditLogQuery(Instant from, Instant to, String eventType, String q, int limit) {
    }
}
