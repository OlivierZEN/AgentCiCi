package com.codehouse.ciciassistant.security.service;

import com.codehouse.ciciassistant.security.domain.SecurityDetectionEventEntity;
import com.codehouse.ciciassistant.security.domain.SecurityDetectionEventRepository;
import com.codehouse.ciciassistant.security.domain.SecurityRuleEntity;
import com.codehouse.ciciassistant.security.domain.SecurityRuleRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.stereotype.Service;

@Service
public class SafetyGatewayService {

    public static final Set<String> ACTIONS = Set.of("ALLOW", "MASK", "WARN", "BLOCK", "REVIEW", "ESCALATE");
    public static final Set<String> SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    public static final Set<String> MATCH_TYPES = Set.of("KEYWORD", "REGEX");
    public static final Set<String> RULE_TYPES = Set.of("SENSITIVE_WORD", "CONTENT_MODERATION", "PROMPT_INJECTION");

    private static final String POLICY_VERSION = "builtin-v1";
    private static final List<BuiltinSignal> PROMPT_INJECTION_SIGNALS = List.of(
            new BuiltinSignal("PROMPT_INJECTION", "CRITICAL", "BLOCK", "内置 prompt injection",
                    Pattern.compile("(?i)(ignore|忽略|绕过).{0,20}(system|系统|previous|之前|instruction|指令|prompt|提示)")),
            new BuiltinSignal("PROMPT_INJECTION", "CRITICAL", "BLOCK", "内置 prompt injection",
                    Pattern.compile("(?i)(泄露|输出|显示|dump|print).{0,20}(system prompt|系统提示|developer message|隐藏提示)")),
            new BuiltinSignal("PROMPT_INJECTION", "HIGH", "BLOCK", "内置 prompt injection",
                    Pattern.compile("(?i)(你现在是|act as).{0,20}(无约束|越狱|jailbreak)"))
    );
    private static final List<BuiltinSignal> MODERATION_SIGNALS = List.of(
            new BuiltinSignal("FRAUD", "HIGH", "BLOCK", "内置内容审核",
                    Pattern.compile("(诈骗话术|诱导.{0,8}转账|洗钱|钓鱼链接|骗取验证码)")),
            new BuiltinSignal("VIOLENCE", "HIGH", "BLOCK", "内置内容审核",
                    Pattern.compile("(制造炸药|杀人|伤害.{0,8}人|暴力威胁)")),
            new BuiltinSignal("HATE", "HIGH", "BLOCK", "内置内容审核",
                    Pattern.compile("(仇恨言论|种族歧视|煽动仇恨)")),
            new BuiltinSignal("GAMBLING", "MEDIUM", "REVIEW", "内置内容审核",
                    Pattern.compile("(博彩|赌博|赌球|赌场)")),
            new BuiltinSignal("SELF_HARM", "HIGH", "BLOCK", "内置内容审核",
                    Pattern.compile("(自杀方法|自残|结束生命)"))
    );

    private final SecurityRedactionService redactionService;
    private final SecurityRuleRepository ruleRepository;
    private final SecurityDetectionEventRepository eventRepository;

    public SafetyGatewayService(SecurityRedactionService redactionService,
                                SecurityRuleRepository ruleRepository,
                                SecurityDetectionEventRepository eventRepository) {
        this.redactionService = redactionService;
        this.ruleRepository = ruleRepository;
        this.eventRepository = eventRepository;
    }

    public SafetyDecision checkInput(String orgId, String userId, String surface, String text) {
        return check(orgId, userId, surface, text, ruleRepository.findByOrgIdAndEnabledTrueOrderByUpdatedAtDescIdDesc(orgId));
    }

    public SafetyDecision checkOutput(String orgId, String userId, String surface, String text) {
        return check(orgId, userId, surface, text, ruleRepository.findByOrgIdAndEnabledTrueOrderByUpdatedAtDescIdDesc(orgId));
    }

    public SafetyDecision checkToolCall(String orgId, String userId, String toolName, String argumentsJson) {
        return checkInput(orgId, userId, "TOOL_CALL:" + safeSurface(toolName), argumentsJson);
    }

    public String redactForAudit(String raw) {
        return redactionService.redact(raw);
    }

    public SafetyDecision checkWithDraftRule(String orgId,
                                             String userId,
                                             String surface,
                                             String text,
                                             SecurityRuleEntity draftRule) {
        return check(orgId, userId, surface, text, List.of(draftRule));
    }

    private SafetyDecision check(String orgId,
                                 String userId,
                                 String surface,
                                 String text,
                                 List<SecurityRuleEntity> enabledRules) {
        String source = text == null ? "" : text;
        List<SecurityFinding> findings = new ArrayList<>();
        for (SecurityRedactionService.RedactionFinding item : redactionService.detect(source)) {
            findings.add(new SecurityFinding(
                    item.category(),
                    item.type(),
                    item.severity(),
                    "MASK",
                    item.matchedSummary(),
                    item.type(),
                    0.96
            ));
        }
        findings.addAll(matchBuiltins(source, PROMPT_INJECTION_SIGNALS));
        findings.addAll(matchBuiltins(source, MODERATION_SIGNALS));
        findings.addAll(matchCustomRules(source, enabledRules));

        if (findings.isEmpty()) {
            return new SafetyDecision("ALLOW", source, List.of(), false, POLICY_VERSION);
        }

        String action = strongestAction(findings);
        String severity = strongestSeverity(findings);
        boolean blocked = "BLOCK".equals(action) || "ESCALATE".equals(action);
        String safeText = blocked ? "" : redactionService.redact(source);
        SafetyDecision decision = new SafetyDecision(action, safeText, List.copyOf(findings), blocked, POLICY_VERSION);
        recordEvent(orgId, userId, surface, decision, severity);
        return decision;
    }

    private List<SecurityFinding> matchBuiltins(String source, List<BuiltinSignal> signals) {
        List<SecurityFinding> findings = new ArrayList<>();
        for (BuiltinSignal signal : signals) {
            if (signal.pattern.matcher(source).find()) {
                findings.add(new SecurityFinding(
                        signal.category,
                        signal.category,
                        signal.severity,
                        signal.action,
                        signal.category,
                        signal.ruleName,
                        0.88
                ));
            }
        }
        return findings;
    }

    private List<SecurityFinding> matchCustomRules(String source, List<SecurityRuleEntity> rules) {
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }
        List<SecurityFinding> findings = new ArrayList<>();
        for (SecurityRuleEntity rule : rules) {
            if (!rule.isEnabled() || !matches(rule, source)) {
                continue;
            }
            findings.add(new SecurityFinding(
                    normalize(rule.getCategory()),
                    normalize(rule.getRuleType()),
                    normalize(rule.getSeverity()),
                    normalize(rule.getAction()),
                    summarizeRule(rule),
                    rule.getName(),
                    0.93
            ));
        }
        return findings;
    }

    private boolean matches(SecurityRuleEntity rule, String source) {
        if ("REGEX".equals(normalize(rule.getMatchType()))) {
            try {
                return Pattern.compile(rule.getPatternText(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                        .matcher(source)
                        .find();
            } catch (PatternSyntaxException ex) {
                return false;
            }
        }
        return source.toLowerCase(Locale.ROOT).contains(rule.getPatternText().toLowerCase(Locale.ROOT));
    }

    private String summarizeRule(SecurityRuleEntity rule) {
        String pattern = rule.getPatternText() == null ? "" : rule.getPatternText().trim();
        return pattern.length() <= 64 ? pattern : pattern.substring(0, 61) + "...";
    }

    private void recordEvent(String orgId,
                             String userId,
                             String surface,
                             SafetyDecision decision,
                             String severity) {
        if (orgId == null || orgId.isBlank() || decision.findings().isEmpty()) {
            return;
        }
        SecurityFinding primary = decision.findings().stream()
                .max(Comparator.comparingInt(item -> severityRank(item.severity())))
                .orElse(decision.findings().getFirst());
        eventRepository.save(new SecurityDetectionEventEntity(
                orgId,
                userId,
                safeSurface(surface),
                decision.action(),
                severity,
                primary.category(),
                primary.ruleName(),
                primary.matchedSummary(),
                decision.safeText(),
                decision.policyVersion()
        ));
    }

    private static String strongestAction(List<SecurityFinding> findings) {
        if (findings.stream().anyMatch(item -> "BLOCK".equals(item.action()))) {
            return "BLOCK";
        }
        if (findings.stream().anyMatch(item -> "ESCALATE".equals(item.action()))) {
            return "ESCALATE";
        }
        if (findings.stream().anyMatch(item -> "REVIEW".equals(item.action()))) {
            return "REVIEW";
        }
        if (findings.stream().anyMatch(item -> "MASK".equals(item.action()))) {
            return "MASK";
        }
        if (findings.stream().anyMatch(item -> "WARN".equals(item.action()))) {
            return "WARN";
        }
        return "ALLOW";
    }

    private static String strongestSeverity(List<SecurityFinding> findings) {
        return findings.stream()
                .map(SecurityFinding::severity)
                .max(Comparator.comparingInt(SafetyGatewayService::severityRank))
                .orElse("LOW");
    }

    private static int severityRank(String severity) {
        return switch (normalize(severity)) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String safeSurface(String value) {
        if (value == null || value.isBlank()) {
            return "UNKNOWN";
        }
        String compact = value.trim();
        return compact.length() <= 64 ? compact : compact.substring(0, 64);
    }

    private record BuiltinSignal(String category,
                                 String severity,
                                 String action,
                                 String ruleName,
                                 Pattern pattern) {
    }

    public record SafetyDecision(String action,
                                 String safeText,
                                 List<SecurityFinding> findings,
                                 boolean blocked,
                                 String policyVersion) {
    }

    public record SecurityFinding(String category,
                                  String riskType,
                                  String severity,
                                  String action,
                                  String matchedSummary,
                                  String ruleName,
                                  double confidence) {
    }
}
