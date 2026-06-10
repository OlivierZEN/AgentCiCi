package com.codehouse.ciciassistant.ops.service;

import com.codehouse.ciciassistant.ops.domain.AuditLogEntity;
import com.codehouse.ciciassistant.ops.domain.AuditLogRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(?i)(authorization|accessToken|refreshToken|api[_-]?key|token|password|secret|cookie)(\"?\\s*[:=]\\s*\"?)[^\",}\\s]+");
    private static final Pattern AUTHORIZATION_PATTERN = Pattern.compile(
            "(?i)(authorization\"?\\s*[:=]\\s*\"?)(bearer\\s+)?[A-Za-z0-9._~+/-]+(\\s+[A-Za-z0-9._~+/-]+)?");
    private static final Pattern MOBILE_PATTERN = Pattern.compile("(1[3-9]\\d)\\d{4}(\\d{4})");

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void log(String orgId, String userId, String eventType, String detail) {
        repository.save(new AuditLogEntity(orgId, userId, eventType, detail));
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
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String withoutAuth = AUTHORIZATION_PATTERN.matcher(raw).replaceAll("$1[redacted]");
        return MOBILE_PATTERN.matcher(SECRET_PATTERN.matcher(withoutAuth).replaceAll("$1$2[redacted]"))
                .replaceAll("$1****$2");
    }

    private String normalized(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    public record AuditLogQuery(Instant from, Instant to, String eventType, String q, int limit) {
    }
}
