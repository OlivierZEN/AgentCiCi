package com.codehouse.ciciassistant.skill.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class SkillSpecSchemaValidator {

    public BuiltinSkillCreatorService.GeneratedSkillDraft sanitize(
            BuiltinSkillCreatorService.GeneratedSkillDraft draft,
            Set<String> availableToolNames,
            Set<String> availableKnowledgeBaseIds) {
        String skillCode = sanitizeSkillCode(draft.skillCode());
        if (skillCode.length() < 2) {
            skillCode = "skill-draft";
        }
        String name = trimToNull(draft.name());
        if (name == null) {
            name = "自定义业务技能";
        }
        String description = trimToNull(draft.description());
        if (description == null) {
            description = "用于处理特定业务场景，并在需要时调用知识库与工具输出结构化建议。";
        }
        String promptFragment = trimToNull(draft.promptFragment());
        if (promptFragment == null) {
            promptFragment = "先识别用户意图，再结合已授权工具与知识库给出结论、依据和下一步建议。";
        }
        String draftSpecText = trimToNull(draft.draftSpecText());
        if (draftSpecText == null) {
            draftSpecText = promptFragment;
        }
        String handoffRule = trimToNull(draft.handoffRule());
        String outputContract = trimToNull(draft.outputContract());
        if (outputContract == null) {
            outputContract = "输出包含结论、依据和下一步建议。";
        }
        String riskLevel = normalizeRiskLevel(draft.riskLevel());

        List<String> normalizedTools = normalizeList(draft.toolWhitelist()).stream()
                .filter(availableToolNames::contains)
                .toList();
        List<String> normalizedKbs = normalizeList(draft.kbWhitelist()).stream()
                .filter(availableKnowledgeBaseIds::contains)
                .toList();
        List<String> triggerHints = normalizeList(draft.triggerHints());
        List<String> intentExamples = normalizeList(draft.userIntentExamples());
        List<String> clarificationQuestions = normalizeList(draft.clarificationQuestions());
        List<String> warnings = normalizeList(draft.warnings());

        return new BuiltinSkillCreatorService.GeneratedSkillDraft(
                skillCode,
                name,
                description,
                promptFragment,
                draftSpecText,
                normalizedTools,
                normalizedKbs,
                handoffRule,
                outputContract,
                riskLevel,
                triggerHints,
                intentExamples,
                clarificationQuestions,
                warnings
        );
    }

    public String sanitizeSkillCode(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        normalized = normalized
                .replaceAll("[^a-z0-9\\s_-]", " ")
                .replaceAll("[\\s_]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
        if (normalized.isBlank()) {
            return "skill-draft";
        }
        if (!normalized.matches("[a-z0-9].*")) {
            normalized = "skill-" + normalized;
        }
        if (normalized.length() > 64) {
            normalized = normalized.substring(0, 64).replaceAll("-+$", "");
        }
        if (normalized.length() < 2) {
            return "skill-draft";
        }
        return normalized;
    }

    private List<String> normalizeList(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String item : raw) {
            String cleaned = trimToNull(item);
            if (cleaned != null) {
                out.add(cleaned);
            }
        }
        return List.copyOf(out);
    }

    private String normalizeRiskLevel(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "LOW", "MEDIUM", "HIGH" -> value;
            default -> "MEDIUM";
        };
    }

    private String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
