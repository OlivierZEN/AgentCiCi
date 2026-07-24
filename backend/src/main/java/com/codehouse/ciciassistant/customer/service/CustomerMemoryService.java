package com.codehouse.ciciassistant.customer.service;

import com.codehouse.ciciassistant.customer.domain.CustomerMemoryItemEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerMemoryItemRepository;
import com.codehouse.ciciassistant.customer.domain.CustomerInteractionEventEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerInteractionEventRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerMemoryService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final int RECENT_INTERACTION_LIMIT = 20;
    private static final int EVIDENCE_LIMIT = 8;
    private static final Duration DEFAULT_RECENT_WINDOW = Duration.ofDays(90);
    private static final Map<String, String> ANALYSIS_TYPES = Map.ofEntries(
            Map.entry("facts", "FACT"),
            Map.entry("customerNeeds", "NEED"),
            Map.entry("risks", "RISK"),
            Map.entry("opportunities", "OPPORTUNITY"),
            Map.entry("commitments", "COMMITMENT"),
            Map.entry("nextActions", "NEXT_ACTION"),
            Map.entry("pendingQuestions", "PENDING_QUESTION"));

    private final CustomerMemoryItemRepository repository;
    private final CustomerInteractionEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    public CustomerMemoryService(CustomerMemoryItemRepository repository,
                                 CustomerInteractionEventRepository eventRepository,
                                 ObjectMapper objectMapper) {
        this.repository = repository;
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<Map<String, Object>> replaceForEvent(String companyId, String accountId,
                                                     String eventId, String batchId,
                                                     Instant occurredAt, String analysisJson,
                                                     List<String> evidenceIds) {
        repository.deleteByCompanyIdAndSourceEventId(companyId, eventId);
        Map<String, Object> analysis = parseMap(analysisJson);
        double confidence = Boolean.TRUE.equals(analysis.get("degraded")) ? 0.55 : 0.85;
        String evidence = json(evidenceIds == null ? List.of() : evidenceIds);
        List<CustomerMemoryItemEntity> created = new ArrayList<>();
        ANALYSIS_TYPES.forEach((field, type) -> strings(analysis.get(field)).forEach(content -> {
            String publicId = "cmi_" + sha256(companyId + ":" + eventId + ":" + type + ":" + content).substring(0, 40);
            created.add(new CustomerMemoryItemEntity(publicId, companyId, accountId, eventId, batchId,
                    type, clip(content, 2000), confidence, occurredAt, evidence));
        }));
        if (created.isEmpty() && !text(analysis.get("summary")).isBlank()) {
            String content = clip(text(analysis.get("summary")), 2000);
            created.add(new CustomerMemoryItemEntity(
                    "cmi_" + sha256(companyId + ":" + eventId + ":FACT:" + content).substring(0, 40),
                    companyId, accountId, eventId, batchId, "FACT", content, confidence, occurredAt, evidence));
        }
        return repository.saveAll(created).stream().map(this::view).toList();
    }

    @Transactional
    public List<Map<String, Object>> activeMemory(String companyId, String accountId) {
        ensureBackfilled(companyId, accountId);
        return repository.findByCompanyIdAndCrmAccountIdAndStatusOrderByOccurredAtDesc(
                companyId, accountId, CustomerMemoryItemEntity.STATUS_ACTIVE).stream().map(this::view).toList();
    }

    @Transactional
    public AssistantContext buildAssistantContext(String companyId, String accountId,
                                                   String userMessage, Map<String, Object> customer) {
        ensureBackfilled(companyId, accountId);
        boolean historyRequested = requestsHistory(userMessage);
        List<Map<String, Object>> fullTimeline = maps(customer.get("timeline"));
        Instant cutoff = Instant.now().minus(DEFAULT_RECENT_WINDOW);
        List<Map<String, Object>> recent = fullTimeline.stream()
                .filter(item -> historyRequested || !instant(item.get("occurredAt")).isBefore(cutoff))
                .limit(RECENT_INTERACTION_LIMIT)
                .map(this::compactInteraction)
                .toList();

        List<CustomerMemoryItemEntity> ranked = repository
                .findByCompanyIdAndCrmAccountIdAndStatusOrderByOccurredAtDesc(
                        companyId, accountId, CustomerMemoryItemEntity.STATUS_ACTIVE).stream()
                .sorted(Comparator.comparingDouble((CustomerMemoryItemEntity item) -> relevance(item, userMessage)).reversed()
                        .thenComparing(CustomerMemoryItemEntity::getOccurredAt, Comparator.reverseOrder()))
                .limit(EVIDENCE_LIMIT)
                .toList();
        List<Map<String, Object>> memories = ranked.stream().map(this::view).toList();

        List<Map<String, Object>> evidence = evidence(ranked, recent);
        Map<String, Object> meta = Map.of(
                "recentWindowDays", historyRequested ? 0 : 90,
                "recentInteractionCount", recent.size(),
                "activeMemoryCount", memories.size(),
                "evidenceCount", evidence.size(),
                "historyRequested", historyRequested);
        return new AssistantContext(compactCustomer(customer), recent, memories, evidence, meta);
    }

    private void ensureBackfilled(String companyId, String accountId) {
        for (CustomerInteractionEventEntity event : eventRepository
                .findByCompanyIdAndCrmAccountIdOrderByOccurredAtDesc(companyId, accountId)) {
            if (text(event.getSourceBatchId()).isBlank() || "{}".equals(text(event.getAnalysisJson()))) continue;
            if (!repository.findByCompanyIdAndSourceEventId(companyId, event.getPublicId()).isEmpty()) continue;
            replaceForEvent(companyId, accountId, event.getPublicId(), event.getSourceBatchId(),
                    event.getOccurredAt(), event.getAnalysisJson(), List.of());
        }
    }

    private List<Map<String, Object>> evidence(List<CustomerMemoryItemEntity> memories,
                                               List<Map<String, Object>> recent) {
        List<Map<String, Object>> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (CustomerMemoryItemEntity item : memories) {
            if (!seen.add(item.getSourceEventId())) continue;
            out.add(evidenceRow(out.size() + 1, item.getSourceEventId(), item.getSourceBatchId(),
                    item.getOccurredAt(), item.getMemoryType(), item.getContent(), true));
            if (out.size() >= EVIDENCE_LIMIT) return out;
        }
        for (Map<String, Object> item : recent) {
            String eventId = text(item.get("eventId"));
            if (eventId.isBlank() || !seen.add(eventId)) continue;
            out.add(evidenceRow(out.size() + 1, eventId, text(item.get("sourceBatchId")),
                    instant(item.get("occurredAt")), text(item.get("subject")), text(item.get("summary")),
                    Boolean.TRUE.equals(item.get("archiveAvailable"))));
            if (out.size() >= EVIDENCE_LIMIT) break;
        }
        return out;
    }

    private Map<String, Object> evidenceRow(int index, String eventId, String batchId, Instant at,
                                            String label, String content, boolean archiveAvailable) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("evidenceId", "E" + index);
        row.put("eventId", eventId);
        row.put("batchId", batchId == null ? "" : batchId);
        row.put("occurredAt", at.toString());
        row.put("label", label);
        row.put("content", clip(content, 500));
        row.put("archiveAvailable", archiveAvailable);
        return row;
    }

    private Map<String, Object> compactCustomer(Map<String, Object> customer) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : List.of("accountId", "name", "owner", "stage", "customerMode", "healthScore",
                "progressScore", "summary", "risks", "newCustomerSignals", "existingCustomerSignals",
                "nextActions", "metrics", "crmConnection")) {
            if (customer.containsKey(key)) out.put(key, customer.get(key));
        }
        out.put("activeOpportunities", maps(customer.get("opportunities")).stream().limit(3).toList());
        out.put("pendingRecommendations", maps(customer.get("recommendations")).stream()
                .filter(item -> "PENDING".equals(text(item.get("status"))) || "ACCEPTED".equals(text(item.get("status"))))
                .limit(5).toList());
        return out;
    }

    private Map<String, Object> compactInteraction(Map<String, Object> item) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : List.of("eventId", "sourceBatchId", "sourceType", "occurredAt", "subject", "summary",
                "sentiment", "intentTags", "lifecycleArea", "archiveAvailable")) {
            if (item.containsKey(key)) out.put(key, item.get(key));
        }
        return out;
    }

    private double relevance(CustomerMemoryItemEntity item, String query) {
        String normalizedQuery = text(query).toLowerCase(Locale.ROOT);
        if (!text(item.getSourceEventId()).isBlank()
                && normalizedQuery.contains(item.getSourceEventId().toLowerCase(Locale.ROOT))) {
            return 100.0;
        }
        double score = switch (item.getMemoryType()) {
            case "RISK", "COMMITMENT", "NEXT_ACTION", "PENDING_QUESTION" -> 4.0;
            case "NEED", "OPPORTUNITY" -> 3.0;
            default -> 2.0;
        };
        String normalized = normalizedQuery.replaceAll("\\s+", "");
        String content = item.getContent().toLowerCase(Locale.ROOT);
        for (int i = 0; i + 1 < normalized.length(); i++) {
            if (content.contains(normalized.substring(i, i + 2))) score += 0.35;
        }
        long ageDays = Math.max(0, Duration.between(item.getOccurredAt(), Instant.now()).toDays());
        return score + Math.max(0, 1.5 - ageDays / 90.0);
    }

    private boolean requestsHistory(String message) {
        String value = text(message);
        return List.of("历史", "以前", "曾经", "最早", "哪年", "过去", "多年", "历次").stream().anyMatch(value::contains);
    }

    private Map<String, Object> view(CustomerMemoryItemEntity item) {
        return Map.ofEntries(
                Map.entry("memoryId", item.getPublicId()),
                Map.entry("accountId", item.getCrmAccountId()),
                Map.entry("sourceEventId", item.getSourceEventId()),
                Map.entry("sourceBatchId", text(item.getSourceBatchId())),
                Map.entry("type", item.getMemoryType()),
                Map.entry("content", item.getContent()),
                Map.entry("status", item.getStatus()),
                Map.entry("confidence", item.getConfidence()),
                Map.entry("occurredAt", item.getOccurredAt().toString()),
                Map.entry("evidence", parseList(item.getEvidenceJson())));
    }

    private Map<String, Object> parseMap(String json) {
        try { return objectMapper.readValue(text(json).isBlank() ? "{}" : json, MAP_TYPE); }
        catch (Exception ignored) { return Map.of(); }
    }

    private List<Object> parseList(String json) {
        try { return objectMapper.readValue(text(json).isBlank() ? "[]" : json, new TypeReference<>() {}); }
        catch (Exception ignored) { return List.of(); }
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception ignored) { return "[]"; }
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(CustomerMemoryService::text).filter(item -> !item.isBlank()).distinct().limit(30).toList();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList();
    }

    private static Instant instant(Object value) {
        try { return Instant.parse(text(value)); }
        catch (Exception ignored) { return Instant.EPOCH; }
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private static String clip(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }

    private static String sha256(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte item : digest) out.append(String.format("%02x", item));
            return out.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    public record AssistantContext(Map<String, Object> customer,
                                   List<Map<String, Object>> recentInteractions,
                                   List<Map<String, Object>> activeMemories,
                                   List<Map<String, Object>> evidence,
                                   Map<String, Object> metadata) {}
}
