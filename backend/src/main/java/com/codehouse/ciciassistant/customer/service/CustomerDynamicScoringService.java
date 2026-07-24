package com.codehouse.ciciassistant.customer.service;

import com.codehouse.ciciassistant.customer.domain.CustomerDynamicSignalEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerDynamicSignalRepository;
import com.codehouse.ciciassistant.customer.domain.CustomerInteractionEventEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerInteractionEventRepository;
import com.codehouse.ciciassistant.customer.domain.CustomerScoreSnapshotEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerScoreSnapshotRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerDynamicScoringService {
    private static final String VERSION = "ai-evidence-v1";
    private static final Set<String> DIMENSIONS = Set.of("HEALTH", "EXPANSION", "RENEWAL", "RELATIONSHIP", "RISK");
    private static final Set<String> DIRECTIONS = Set.of("POSITIVE", "NEGATIVE");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final CustomerDynamicSignalRepository signalRepository;
    private final CustomerScoreSnapshotRepository snapshotRepository;
    private final CustomerInteractionEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    public CustomerDynamicScoringService(CustomerDynamicSignalRepository signalRepository,
                                         CustomerScoreSnapshotRepository snapshotRepository,
                                         CustomerInteractionEventRepository eventRepository,
                                         ObjectMapper objectMapper) {
        this.signalRepository = signalRepository;
        this.snapshotRepository = snapshotRepository;
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> recordAnalysis(String companyId, String accountId, String eventId, String batchId,
                                              String sourceType, Instant occurredAt, String analysisJson) {
        persistAnalysis(companyId, accountId, eventId, batchId, sourceType, occurredAt, analysisJson, false);
        return explanation(companyId, accountId);
    }

    private void persistAnalysis(String companyId, String accountId, String eventId, String batchId,
                                 String sourceType, Instant occurredAt, String analysisJson, boolean legacyFallback) {
        Map<String, Object> analysis = parse(analysisJson);
        List<Map<String, Object>> candidates = mapList(analysis.get("scoringSignals"));
        if (candidates.isEmpty() && legacyFallback) candidates = legacyPendingSignals(analysis);
        List<CustomerDynamicSignalEntity> previous = signalRepository.findByCompanyIdAndSourceEventId(companyId, eventId);
        Map<String, CustomerDynamicSignalEntity> previousById = previous.stream()
                .collect(Collectors.toMap(CustomerDynamicSignalEntity::getPublicId, Function.identity(), (left, right) -> left));
        Set<String> currentIds = new HashSet<>();
        List<CustomerDynamicSignalEntity> changed = new ArrayList<>();
        for (Map<String, Object> candidate : candidates) {
            String dimension = text(candidate.get("dimension")).toUpperCase(Locale.ROOT);
            String direction = text(candidate.get("direction")).toUpperCase(Locale.ROOT);
            String title = clip(text(candidate.get("title")), 256);
            String evidence = clip(text(candidate.get("evidence")), 2000);
            String rationale = clip(text(candidate.get("reason")), 2000);
            if (!DIMENSIONS.contains(dimension) || !DIRECTIONS.contains(direction) || title.isBlank() || evidence.isBlank()) continue;
            int impact = integer(candidate.get("impact"), 1, 10, 5);
            double confidence = decimal(candidate.get("confidence"), 0, 1, 0.5);
            int validDays = integer(candidate.get("validDays"), 7, 365, 90);
            String fingerprint = sha256(dimension + "|" + direction + "|" + normalized(evidence));
            String publicId = "cds_" + sha256(companyId + "|" + eventId + "|" + fingerprint).substring(0, 40);
            currentIds.add(publicId);
            CustomerDynamicSignalEntity entity = previousById.get(publicId);
            if (entity == null) {
                entity = new CustomerDynamicSignalEntity(publicId, companyId, accountId, eventId, batchId,
                        normalizedSource(sourceType), dimension, direction, impact, confidence, title,
                        rationale, evidence, occurredAt, occurredAt.plus(validDays, ChronoUnit.DAYS),
                        fingerprint, VERSION);
            } else {
                entity.refresh(normalizedSource(sourceType), dimension, direction, impact, confidence, title,
                        rationale, evidence, occurredAt, occurredAt.plus(validDays, ChronoUnit.DAYS),
                        fingerprint, VERSION);
            }
            changed.add(entity);
        }
        previous.stream().filter(item -> !currentIds.contains(item.getPublicId())).forEach(CustomerDynamicSignalEntity::supersede);
        signalRepository.saveAll(previous);
        signalRepository.saveAll(changed);
    }

    @Transactional
    public Map<String, Object> explanation(String companyId, String accountId) {
        List<CustomerDynamicSignalEntity> signals = signalRepository
                .findByCompanyIdAndCrmAccountIdOrderByOccurredAtDesc(companyId, accountId);
        Set<String> representedEvents = signals.stream()
                .map(CustomerDynamicSignalEntity::getSourceEventId)
                .collect(Collectors.toSet());
        List<CustomerInteractionEventEntity> backfillEvents = eventRepository
                .findByCompanyIdAndCrmAccountIdOrderByOccurredAtDesc(companyId, accountId).stream()
                .filter(event -> event.getSourceBatchId() != null && !event.getSourceBatchId().isBlank())
                .filter(event -> !representedEvents.contains(event.getPublicId()))
                .filter(event -> hasSignalCandidates(event.getAnalysisJson()))
                .limit(50)
                .toList();
        backfillEvents.forEach(event -> persistAnalysis(companyId, accountId, event.getPublicId(), event.getSourceBatchId(),
                event.getSourceType(), event.getOccurredAt(), event.getAnalysisJson(), true));
        if (!backfillEvents.isEmpty()) {
            signals = signalRepository.findByCompanyIdAndCrmAccountIdOrderByOccurredAtDesc(companyId, accountId);
        }
        return explanationFrom(companyId, accountId, signals);
    }

    @Transactional
    public void overlayScores(String companyId, List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) return;
        List<String> accountIds = items.stream().map(item -> text(item.get("accountId"))).filter(id -> !id.isBlank()).distinct().toList();
        Map<String, CustomerScoreSnapshotEntity> snapshots = snapshotRepository
                .findByCompanyIdAndCrmAccountIdIn(companyId, accountIds).stream()
                .collect(Collectors.toMap(CustomerScoreSnapshotEntity::getCrmAccountId, Function.identity()));
        for (Map<String, Object> item : items) overlay(item, snapshots.get(text(item.get("accountId"))));
    }

    public void overlay(Map<String, Object> view, Map<String, Object> explanation) {
        if (view == null || explanation == null) return;
        view.put("healthScore", explanation.getOrDefault("healthScore", 50));
        view.put("scoreSnapshot", explanation);
        Object metricsValue = view.get("metrics");
        if (metricsValue instanceof Map<?, ?> rawMetrics) {
            @SuppressWarnings("unchecked")
            Map<String, Object> metrics = new LinkedHashMap<>((Map<String, Object>) rawMetrics);
            Map<String, Object> health = new LinkedHashMap<>();
            health.put("value", explanation.getOrDefault("healthScore", 50));
            health.put("definition", "AI 根据信号证据、置信度、时效和来源可靠性动态计算");
            health.put("source", "AI_EVIDENCE");
            health.put("lastCalculatedAt", explanation.getOrDefault("calculatedAt", Instant.now().toString()));
            health.put("drilldownTarget", "score-explanation");
            metrics.put("health", health);
            view.put("metrics", metrics);
        }
    }

    private Map<String, Object> explanationFrom(String companyId, String accountId, List<CustomerDynamicSignalEntity> signals) {
        Instant now = Instant.now();
        boolean changedStatus = false;
        Set<String> seenFingerprints = new HashSet<>();
        Set<String> seenTopics = new HashSet<>();
        for (CustomerDynamicSignalEntity signal : signals) {
            boolean scoreable = CustomerDynamicSignalEntity.STATUS_ACTIVE.equals(signal.getStatus())
                    || CustomerDynamicSignalEntity.STATUS_PENDING.equals(signal.getStatus());
            if (scoreable
                    && signal.getValidUntil() != null && signal.getValidUntil().isBefore(now)) {
                signal.expire();
                changedStatus = true;
                continue;
            }
            if (scoreable) {
                String topic = signal.getDimension() + "|" + normalized(signal.getTitle());
                if (!seenFingerprints.add(signal.getContentFingerprint()) || !seenTopics.add(topic)) {
                    signal.supersede();
                    changedStatus = true;
                }
            }
        }
        if (changedStatus) signalRepository.saveAll(signals);
        Map<String, Double> totals = new LinkedHashMap<>();
        DIMENSIONS.forEach(dimension -> totals.put(dimension, 0d));
        double recentHealthDelta = 0;
        int activeCount = 0;
        int pendingCount = 0;
        List<Map<String, Object>> views = new ArrayList<>();
        for (CustomerDynamicSignalEntity signal : signals) {
            double contribution = contribution(signal, now);
            if (CustomerDynamicSignalEntity.STATUS_ACTIVE.equals(signal.getStatus())) {
                totals.computeIfPresent(signal.getDimension(), (key, value) -> value + contribution);
                activeCount++;
                if (signal.getOccurredAt().isAfter(now.minus(30, ChronoUnit.DAYS))) {
                    if ("HEALTH".equals(signal.getDimension())) recentHealthDelta += contribution * 0.5;
                    if ("RELATIONSHIP".equals(signal.getDimension())) recentHealthDelta += contribution * 0.2;
                    if ("RISK".equals(signal.getDimension())) recentHealthDelta += contribution * 0.3;
                }
            } else if (CustomerDynamicSignalEntity.STATUS_PENDING.equals(signal.getStatus())) pendingCount++;
            views.add(signalView(signal, contribution));
        }
        int healthDimension = score(totals.get("HEALTH"));
        int expansion = score(totals.get("EXPANSION"));
        int renewal = score(totals.get("RENEWAL"));
        int relationship = score(totals.get("RELATIONSHIP"));
        int risk = score(-totals.get("RISK"));
        int health = clamp((int) Math.round(healthDimension * 0.5 + relationship * 0.2 + (100 - risk) * 0.3));
        CustomerScoreSnapshotEntity snapshot = snapshotRepository.findByCompanyIdAndCrmAccountId(companyId, accountId)
                .orElseGet(() -> new CustomerScoreSnapshotEntity(companyId, accountId));
        snapshot.update(health, healthDimension, expansion, renewal, relationship, risk,
                round(recentHealthDelta), activeCount, pendingCount, VERSION);
        snapshotRepository.save(snapshot);
        Map<String, Object> result = snapshotView(snapshot);
        result.put("signals", views);
        result.put("status", activeCount == 0 ? "INSUFFICIENT_EVIDENCE" : "READY");
        return result;
    }

    private void overlay(Map<String, Object> item, CustomerScoreSnapshotEntity snapshot) {
        if (snapshot == null) {
            item.put("healthScore", 50);
            item.put("scoreStatus", "INSUFFICIENT_EVIDENCE");
            return;
        }
        item.put("healthScore", snapshot.getHealthScore());
        item.put("scoreStatus", snapshot.getActiveSignalCount() == 0 ? "INSUFFICIENT_EVIDENCE" : "READY");
        item.put("scoreNetChange30d", snapshot.getNetChange30d());
    }

    private Map<String, Object> snapshotView(CustomerScoreSnapshotEntity snapshot) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("healthScore", snapshot.getHealthScore());
        result.put("healthDimensionScore", snapshot.getHealthDimensionScore());
        result.put("expansionScore", snapshot.getExpansionScore());
        result.put("renewalScore", snapshot.getRenewalScore());
        result.put("relationshipScore", snapshot.getRelationshipScore());
        result.put("riskScore", snapshot.getRiskScore());
        result.put("netChange30d", snapshot.getNetChange30d());
        result.put("activeSignalCount", snapshot.getActiveSignalCount());
        result.put("pendingSignalCount", snapshot.getPendingSignalCount());
        result.put("calculationVersion", snapshot.getCalculationVersion());
        result.put("calculatedAt", snapshot.getCalculatedAt().toString());
        return result;
    }

    private Map<String, Object> signalView(CustomerDynamicSignalEntity signal, double contribution) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("signalId", signal.getPublicId());
        view.put("dimension", signal.getDimension());
        view.put("direction", signal.getDirection());
        view.put("impact", signal.getImpact());
        view.put("confidence", signal.getConfidence());
        view.put("contribution", round(contribution));
        view.put("title", signal.getTitle());
        view.put("reason", signal.getRationale());
        view.put("evidence", signal.getEvidenceQuote());
        view.put("status", signal.getStatus());
        view.put("sourceType", signal.getSourceType());
        view.put("sourceEventId", signal.getSourceEventId());
        view.put("sourceBatchId", signal.getSourceBatchId() == null ? "" : signal.getSourceBatchId());
        view.put("occurredAt", signal.getOccurredAt().toString());
        view.put("validUntil", signal.getValidUntil() == null ? "" : signal.getValidUntil().toString());
        return view;
    }

    private double contribution(CustomerDynamicSignalEntity signal, Instant now) {
        if (!CustomerDynamicSignalEntity.STATUS_ACTIVE.equals(signal.getStatus())) return 0;
        double direction = "POSITIVE".equals(signal.getDirection()) ? 1 : -1;
        double ageDays = Math.max(0, Duration.between(signal.getOccurredAt(), now).toHours() / 24d);
        double decay = Math.pow(0.5, ageDays / 90d);
        double reliability = switch (signal.getSourceType()) {
            case "MEETING", "EMAIL", "WECHAT", "PHONE" -> 1;
            case "CUSTOMER_FEEDBACK" -> 0.95;
            default -> 0.85;
        };
        return direction * signal.getImpact() * signal.getConfidence() * reliability * decay;
    }

    private static int score(double contribution) { return clamp((int) Math.round(50 + contribution)); }
    private static int clamp(int value) { return Math.max(0, Math.min(100, value)); }
    private static double round(double value) { return Math.round(value * 10d) / 10d; }
    private static String normalizedSource(String value) {
        String source = text(value).toUpperCase(Locale.ROOT);
        return source.isBlank() ? "OTHER" : source;
    }
    private Map<String, Object> parse(String value) {
        try { return objectMapper.readValue(text(value).isBlank() ? "{}" : value, MAP_TYPE); }
        catch (Exception ex) { return Map.of(); }
    }

    private boolean hasSignalCandidates(String analysisJson) {
        Map<String, Object> analysis = parse(analysisJson);
        return !mapList(analysis.get("scoringSignals")).isEmpty()
                || hasItems(analysis.get("risks"))
                || hasItems(analysis.get("opportunities"))
                || hasItems(analysis.get("commitments"));
    }

    private static boolean hasItems(Object value) {
        return value instanceof Collection<?> collection && !collection.isEmpty();
    }
    private static List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof Collection<?> collection)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : collection) {
            if (item instanceof Map<?, ?> raw) {
                Map<String, Object> mapped = new LinkedHashMap<>();
                raw.forEach((key, element) -> mapped.put(String.valueOf(key), element));
                result.add(mapped);
            }
        }
        return result;
    }

    private static List<Map<String, Object>> legacyPendingSignals(Map<String, Object> analysis) {
        List<Map<String, Object>> signals = new ArrayList<>();
        appendLegacy(signals, analysis.get("risks"), "RISK", "NEGATIVE", "历史风险证据");
        appendLegacy(signals, analysis.get("opportunities"), "EXPANSION", "POSITIVE", "历史机会证据");
        appendLegacy(signals, analysis.get("commitments"), "RELATIONSHIP", "POSITIVE", "历史承诺证据");
        return signals;
    }

    private static void appendLegacy(List<Map<String, Object>> target, Object value, String dimension,
                                     String direction, String titlePrefix) {
        if (!(value instanceof Collection<?> items)) return;
        for (Object item : items) {
            String evidence = clip(text(item), 2000);
            if (evidence.isBlank()) continue;
            Map<String, Object> signal = new LinkedHashMap<>();
            signal.put("dimension", dimension);
            signal.put("direction", direction);
            signal.put("impact", 5);
            signal.put("confidence", 0.60);
            signal.put("title", clip(titlePrefix + "：" + evidence, 256));
            signal.put("evidence", evidence);
            signal.put("reason", "历史 AI 分析未提供影响强度和置信度，保留为待确认信号，不计入当前评分");
            signal.put("validDays", 365);
            target.add(signal);
            if (target.size() >= 30) return;
        }
    }
    private static int integer(Object value, int min, int max, int fallback) {
        try { return Math.max(min, Math.min(max, Integer.parseInt(text(value)))); }
        catch (Exception ex) { return fallback; }
    }
    private static double decimal(Object value, double min, double max, double fallback) {
        try { return Math.max(min, Math.min(max, Double.parseDouble(text(value)))); }
        catch (Exception ex) { return fallback; }
    }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private static String normalized(String value) { return text(value).toLowerCase(Locale.ROOT).replaceAll("\\s+", " "); }
    private static String clip(String value, int length) { return value.length() <= length ? value : value.substring(0, length); }
    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
