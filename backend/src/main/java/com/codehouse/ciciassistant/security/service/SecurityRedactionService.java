package com.codehouse.ciciassistant.security.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class SecurityRedactionService {

    private static final List<DetectorRule> RULES = List.of(
            new DetectorRule("PRIVATE_KEY", "SECRET", "HIGH",
                    Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----"),
                    "[private-key]"),
            new DetectorRule("AUTHORIZATION", "SECRET", "HIGH",
                    Pattern.compile("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[A-Za-z0-9._~+/-]+"),
                    "$1[redacted]"),
            new DetectorRule("SECRET", "SECRET", "HIGH",
                    Pattern.compile("(?i)((?:api[_-]?key|access[_-]?token|refresh[_-]?token|token|password|secret|cookie)\\s*[:=]\\s*)\"?[^\",}\\s]+"),
                    "$1[redacted]"),
            new DetectorRule("JWT", "SECRET", "HIGH",
                    Pattern.compile("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{6,}\\b"),
                    "[jwt]"),
            new DetectorRule("EMAIL", "PRIVACY", "MEDIUM",
                    Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"),
                    "[email]"),
            new DetectorRule("ID_CARD", "PRIVACY", "HIGH",
                    Pattern.compile("\\b([1-9]\\d{5})\\d{8}(\\d{3}[0-9Xx])\\b"),
                    "$1********$2"),
            new DetectorRule("BANK_CARD", "PRIVACY", "HIGH",
                    Pattern.compile("\\b(62\\d{4})\\d{9,13}(\\d{4})\\b"),
                    "$1*********$2"),
            new DetectorRule("MOBILE_PHONE", "PRIVACY", "MEDIUM",
                    Pattern.compile("\\b(1[3-9]\\d)\\d{4}(\\d{4})\\b"),
                    "$1****$2"),
            new DetectorRule("IP_ADDRESS", "PRIVACY", "LOW",
                    Pattern.compile("\\b((?:10|172|192)\\.)(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\b"),
                    "$1$2.*.*")
    );

    public String redact(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String redacted = raw;
        for (DetectorRule rule : RULES) {
            redacted = rule.pattern.matcher(redacted).replaceAll(rule.replacement);
        }
        return redacted;
    }

    public List<RedactionFinding> detect(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<RedactionFinding> findings = new ArrayList<>();
        for (DetectorRule rule : RULES) {
            Matcher matcher = rule.pattern.matcher(raw);
            while (matcher.find()) {
                findings.add(new RedactionFinding(
                        rule.type,
                        rule.type,
                        rule.severity,
                        snippet(matcher.group()),
                        matcher.start(),
                        matcher.end()
                ));
            }
        }
        return findings.stream()
                .sorted(Comparator.comparingInt(RedactionFinding::start))
                .toList();
    }

    private static String snippet(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        return compact.length() <= 64 ? compact : compact.substring(0, 61) + "...";
    }

    private record DetectorRule(String type, String category, String severity, Pattern pattern, String replacement) {
    }

    public record RedactionFinding(String type,
                                   String category,
                                   String severity,
                                   String matchedSummary,
                                   int start,
                                   int end) {

        public String normalizedCategory() {
            return category == null ? "" : category.toUpperCase(Locale.ROOT);
        }
    }
}
