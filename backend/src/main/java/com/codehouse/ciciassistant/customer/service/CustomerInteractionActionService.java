package com.codehouse.ciciassistant.customer.service;

import com.codehouse.ciciassistant.customer.domain.CustomerWorkbenchRecommendationEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerWorkbenchRecommendationRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerInteractionActionService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final Set<String> ACTION_TYPES = Set.of("CREATE_TASK", "CREATE_OPPORTUNITY", "UPDATE_OPPORTUNITY");
    private static final Set<String> OPEN_STATUSES = Set.of(
            CustomerWorkbenchRecommendationEntity.STATUS_PENDING,
            CustomerWorkbenchRecommendationEntity.STATUS_ACCEPTED,
            CustomerWorkbenchRecommendationEntity.STATUS_CONFIRMED,
            CustomerWorkbenchRecommendationEntity.STATUS_APPLYING,
            CustomerWorkbenchRecommendationEntity.STATUS_FAILED);

    private final CustomerWorkbenchRecommendationRepository recommendationRepository;
    private final ObjectMapper objectMapper;

    public CustomerInteractionActionService(CustomerWorkbenchRecommendationRepository recommendationRepository,
                                            ObjectMapper objectMapper) {
        this.recommendationRepository = recommendationRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> recordActions(String companyId, String accountId, String eventId, String batchId,
                                             Instant occurredAt, String analysisJson) {
        List<Map<String, Object>> candidates = mapList(parse(analysisJson).get("actionCandidates"));
        int generated = 0;
        int refreshed = 0;
        int skipped = 0;
        List<String> ids = new ArrayList<>();
        for (Map<String, Object> candidate : candidates) {
            NormalizedAction action = normalize(candidate, accountId, occurredAt);
            if (action == null) {
                skipped++;
                continue;
            }
            List<CustomerWorkbenchRecommendationEntity> sameKey = recommendationRepository
                    .findByCompanyIdAndCrmAccountIdAndRecommendationTypeAndActionKeyOrderByUpdatedAtDesc(
                            companyId, accountId, action.actionType(), action.actionKey());
            CustomerWorkbenchRecommendationEntity open = sameKey.stream()
                    .filter(item -> OPEN_STATUSES.contains(item.getStatus())).findFirst().orElse(null);
            if (open != null) {
                if (CustomerWorkbenchRecommendationEntity.STATUS_PENDING.equals(open.getStatus())
                        || CustomerWorkbenchRecommendationEntity.STATUS_FAILED.equals(open.getStatus())) {
                    open.refreshPending(action.title(), action.rationale(), action.confidence(), action.crmPayload(),
                            action.targetObject(), action.targetRecordId(), evidenceJson(eventId, batchId, occurredAt, action),
                            eventId, batchId, action.validUntil());
                    recommendationRepository.save(open);
                    ids.add(open.getPublicId());
                    refreshed++;
                } else {
                    skipped++;
                }
                continue;
            }
            boolean coolingDown = sameKey.stream()
                    .filter(item -> Set.of(CustomerWorkbenchRecommendationEntity.STATUS_APPLIED,
                            CustomerWorkbenchRecommendationEntity.STATUS_DISMISSED).contains(item.getStatus()))
                    .anyMatch(item -> item.getUpdatedAt().isAfter(occurredAt.minus(7, ChronoUnit.DAYS)));
            if (coolingDown) {
                skipped++;
                continue;
            }
            String publicId = "cwr_" + sha256(companyId + "|" + accountId + "|" + eventId + "|"
                    + action.actionType() + "|" + action.actionKey()).substring(0, 40);
            CustomerWorkbenchRecommendationEntity entity = new CustomerWorkbenchRecommendationEntity(
                    publicId, companyId, accountId, action.actionType(), action.title(), action.rationale(),
                    action.confidence(), action.crmPayload());
            entity.configureTarget(action.targetObject(), action.targetRecordId(),
                    evidenceJson(eventId, batchId, occurredAt, action));
            entity.configureTrigger(eventId, batchId, action.actionKey(), "INTERACTION_AI", action.validUntil());
            recommendationRepository.save(entity);
            ids.add(publicId);
            generated++;
        }
        return Map.of("generated", generated, "refreshed", refreshed, "skipped", skipped, "recommendationIds", ids);
    }

    private NormalizedAction normalize(Map<String, Object> candidate, String accountId, Instant occurredAt) {
        String actionType = text(candidate.get("actionType")).toUpperCase(Locale.ROOT);
        String actionKey = clip(normalized(text(candidate.get("businessKey"))), 128);
        String title = clip(text(candidate.get("title")), 256);
        String rationale = clip(text(candidate.get("reason")), 2000);
        String evidence = clip(text(candidate.get("evidence")), 2000);
        double confidenceValue = decimal(candidate.get("confidence"), 0, 1, 0);
        String targetRecordId = clip(text(candidate.get("targetRecordId")), 128);
        if (!ACTION_TYPES.contains(actionType) || actionKey.isBlank() || title.isBlank() || rationale.isBlank()
                || evidence.isBlank() || confidenceValue < 0.65
                || "UPDATE_OPPORTUNITY".equals(actionType) && targetRecordId.isBlank()) return null;
        int dueInDays = integer(candidate.get("dueInDays"), 1, 90, 7);
        int validDays = integer(candidate.get("validDays"), 7, 180, 30);
        Instant validUntil = occurredAt.plus(validDays, ChronoUnit.DAYS);
        if (!validUntil.isAfter(Instant.now())) return null;
        BigDecimal confidence = BigDecimal.valueOf(confidenceValue).setScale(2, RoundingMode.HALF_UP);
        LocalDate dueDate = occurredAt.atZone(ZoneOffset.UTC).toLocalDate().plusDays(dueInDays);
        String targetObject = "CREATE_TASK".equals(actionType) ? "Task" : "Opportunity";
        Map<String, Object> payload = new LinkedHashMap<>();
        if ("CREATE_TASK".equals(actionType)) {
            payload.put("subject", title);
            payload.put("relateid", accountId);
            payload.put("relateobj", "Account");
            payload.put("status", "未开始");
            payload.put("priority", "普通");
            payload.put("expiredate", dueDate.toString());
            payload.put("remark", rationale + "\n证据：" + evidence);
        } else if ("CREATE_OPPORTUNITY".equals(actionType)) {
            payload.put("name", title);
            payload.put("khmc", accountId);
            payload.put("jieduan", "1-发现机会");
            payload.put("xyb", rationale);
        } else {
            payload.put("id", targetRecordId);
            payload.put("xyb", rationale);
        }
        return new NormalizedAction(actionType, actionKey, title, rationale, evidence, confidence,
                targetObject, targetRecordId, toJson(payload), validUntil);
    }

    private String evidenceJson(String eventId, String batchId, Instant occurredAt, NormalizedAction action) {
        return toJson(List.of(Map.of(
                "eventId", eventId,
                "batchId", batchId,
                "title", action.title(),
                "detail", action.evidence(),
                "source", "互动识别",
                "occurredAt", occurredAt.toString())));
    }

    private Map<String, Object> parse(String value) {
        try { return objectMapper.readValue(text(value).isBlank() ? "{}" : value, MAP_TYPE); }
        catch (Exception ex) { return Map.of(); }
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

    private String toJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalArgumentException("经营动作 JSON 序列化失败", ex); }
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

    private record NormalizedAction(String actionType, String actionKey, String title, String rationale,
                                    String evidence, BigDecimal confidence, String targetObject,
                                    String targetRecordId, String crmPayload, Instant validUntil) {}
}
