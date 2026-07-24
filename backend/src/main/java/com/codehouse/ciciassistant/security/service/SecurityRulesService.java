package com.codehouse.ciciassistant.security.service;

import com.codehouse.ciciassistant.common.error.ResourceNotFoundException;
import com.codehouse.ciciassistant.security.domain.SecurityDetectionEventEntity;
import com.codehouse.ciciassistant.security.domain.SecurityDetectionEventRepository;
import com.codehouse.ciciassistant.security.domain.SecurityRuleEntity;
import com.codehouse.ciciassistant.security.domain.SecurityRuleRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SecurityRulesService {

    private final SecurityRuleRepository ruleRepository;
    private final SecurityDetectionEventRepository eventRepository;
    private final SafetyGatewayService safetyGatewayService;

    public SecurityRulesService(SecurityRuleRepository ruleRepository,
                                SecurityDetectionEventRepository eventRepository,
                                SafetyGatewayService safetyGatewayService) {
        this.ruleRepository = ruleRepository;
        this.eventRepository = eventRepository;
        this.safetyGatewayService = safetyGatewayService;
    }

    public Map<String, Object> overview(String companyId) {
        long totalEvents = eventRepository.countByCompanyId(companyId);
        return Map.of(
                "totalRules", ruleRepository.findByCompanyIdOrderByUpdatedAtDescIdDesc(companyId).size(),
                "enabledRules", ruleRepository.findByCompanyIdAndEnabledTrueOrderByUpdatedAtDescIdDesc(companyId).size(),
                "totalEvents", totalEvents,
                "blockedEvents", eventRepository.countByCompanyIdAndAction(companyId, "BLOCK"),
                "reviewEvents", eventRepository.countByCompanyIdAndAction(companyId, "REVIEW"),
                "pendingReviews", eventRepository.countByCompanyIdAndReviewedFalse(companyId),
                "policyVersion", "builtin-v1"
        );
    }

    public List<RuleView> listRules(String companyId) {
        return ruleRepository.findByCompanyIdOrderByUpdatedAtDescIdDesc(companyId).stream()
                .map(this::toRuleView)
                .toList();
    }

    @Transactional
    public RuleView createRule(String companyId, RuleCommand command) {
        NormalizedRule normalized = normalize(command);
        SecurityRuleEntity entity = new SecurityRuleEntity(
                companyId,
                normalized.name(),
                normalized.ruleType(),
                normalized.category(),
                normalized.matchType(),
                normalized.patternText(),
                normalized.severity(),
                normalized.action(),
                normalized.enabled(),
                normalized.description()
        );
        return toRuleView(ruleRepository.save(entity));
    }

    @Transactional
    public RuleView updateRule(String companyId, Long id, RuleCommand command) {
        SecurityRuleEntity entity = ruleRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Security rule not found"));
        NormalizedRule normalized = normalize(command);
        entity.update(
                normalized.name(),
                normalized.ruleType(),
                normalized.category(),
                normalized.matchType(),
                normalized.patternText(),
                normalized.severity(),
                normalized.action(),
                normalized.enabled(),
                normalized.description()
        );
        return toRuleView(ruleRepository.save(entity));
    }

    public TestResult testRule(String companyId, String text, RuleCommand command) {
        NormalizedRule normalized = normalize(command);
        SecurityRuleEntity draft = new SecurityRuleEntity(
                companyId,
                normalized.name(),
                normalized.ruleType(),
                normalized.category(),
                normalized.matchType(),
                normalized.patternText(),
                normalized.severity(),
                normalized.action(),
                true,
                normalized.description()
        );
        SafetyGatewayService.SafetyDecision decision =
                safetyGatewayService.checkWithDraftRule(companyId, "rule-test", "RULE_TEST", text, draft);
        return new TestResult(decision.action(), decision.safeText(), decision.findings());
    }

    public List<EventView> listEvents(String companyId, Boolean reviewed, int limit) {
        PageRequest pageRequest = PageRequest.of(0, Math.min(Math.max(limit, 1), 100));
        List<SecurityDetectionEventEntity> rows = reviewed == null
                ? eventRepository.findByCompanyIdOrderByCreatedAtDescIdDesc(companyId, pageRequest)
                : eventRepository.findByCompanyIdAndReviewedOrderByCreatedAtDescIdDesc(companyId, reviewed, pageRequest);
        return rows.stream().map(this::toEventView).toList();
    }

    @Transactional
    public EventView reviewEvent(String companyId, Long id, ReviewCommand command, String reviewer) {
        SecurityDetectionEventEntity event = eventRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Security event not found"));
        event.review(normalized(command.result(), "APPROVED"), text(command.note(), 500), reviewer);
        return toEventView(eventRepository.save(event));
    }

    private NormalizedRule normalize(RuleCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("规则不能为空");
        }
        String name = required(command.name(), "规则名称");
        String ruleType = enumValue(command.ruleType(), SafetyGatewayService.RULE_TYPES, "规则类型");
        String category = required(command.category(), "分类").toUpperCase(Locale.ROOT);
        String matchType = enumValue(command.matchType(), SafetyGatewayService.MATCH_TYPES, "匹配方式");
        String patternText = required(command.patternText(), "匹配内容");
        String severity = enumValue(command.severity(), SafetyGatewayService.SEVERITIES, "严重级别");
        String action = enumValue(command.action(), SafetyGatewayService.ACTIONS, "处置动作");
        if ("REGEX".equals(matchType)) {
            try {
                Pattern.compile(patternText);
            } catch (PatternSyntaxException ex) {
                throw new IllegalArgumentException("正则表达式无效: " + ex.getDescription());
            }
        }
        return new NormalizedRule(
                text(name, 128),
                ruleType,
                text(category, 64),
                matchType,
                patternText,
                severity,
                action,
                command.enabled(),
                text(command.description(), 500)
        );
    }

    private RuleView toRuleView(SecurityRuleEntity item) {
        return new RuleView(
                item.getId(),
                item.getName(),
                item.getRuleType(),
                item.getCategory(),
                item.getMatchType(),
                item.getPatternText(),
                item.getSeverity(),
                item.getAction(),
                item.isEnabled(),
                item.getDescription(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }

    private EventView toEventView(SecurityDetectionEventEntity item) {
        return new EventView(
                item.getId(),
                item.getUserId(),
                item.getSurface(),
                item.getAction(),
                item.getSeverity(),
                item.getCategory(),
                item.getRuleName(),
                item.getMatchedSummary(),
                item.getRedactedText(),
                item.getPolicyVersion(),
                item.isReviewed(),
                item.getReviewResult(),
                item.getReviewNote(),
                item.getReviewedBy(),
                item.getReviewedAt(),
                item.getCreatedAt()
        );
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static String enumValue(String value, java.util.Set<String> allowed, String fieldName) {
        String normalized = required(value, fieldName).toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException(fieldName + "无效");
        }
        return normalized;
    }

    private static String normalized(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String text(String value, int limit) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= limit ? trimmed : trimmed.substring(0, limit);
    }

    private record NormalizedRule(String name,
                                  String ruleType,
                                  String category,
                                  String matchType,
                                  String patternText,
                                  String severity,
                                  String action,
                                  boolean enabled,
                                  String description) {
    }

    public record RuleCommand(String name,
                              String ruleType,
                              String category,
                              String matchType,
                              String patternText,
                              String severity,
                              String action,
                              boolean enabled,
                              String description) {
    }

    public record ReviewCommand(String result, String note) {
    }

    public record RuleView(Long id,
                           String name,
                           String ruleType,
                           String category,
                           String matchType,
                           String patternText,
                           String severity,
                           String action,
                           boolean enabled,
                           String description,
                           Instant createdAt,
                           Instant updatedAt) {
    }

    public record TestResult(String action,
                             String safeText,
                             List<SafetyGatewayService.SecurityFinding> findings) {
    }

    public record EventView(Long id,
                            String userId,
                            String surface,
                            String action,
                            String severity,
                            String category,
                            String ruleName,
                            String matchedSummary,
                            String redactedText,
                            String policyVersion,
                            boolean reviewed,
                            String reviewResult,
                            String reviewNote,
                            String reviewedBy,
                            Instant reviewedAt,
                            Instant createdAt) {
    }
}
