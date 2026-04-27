package com.codehouse.ciciassistant.spec;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class SpecCompilerService {

    public SpecCompilation compile(SpecCompileCommand command) {
        List<String> normalizedTools = normalizeList(command.toolRefs());
        List<String> normalizedKnowledgeRefs = normalizeList(command.knowledgeRefs());
        String normalizedRiskLevel = normalizeRiskLevel(command.riskLevel());
        String specText = safe(command.specText()).trim();
        String handoffRule = trimToNull(command.handoffRule());
        List<String> steps = extractSteps(specText);
        List<String> intents = inferIntents(specText);
        List<String> decisionRules = inferDecisionRules(specText);

        List<String> warnings = new ArrayList<>();
        if (specText.length() < 40) {
            warnings.add("Spec 偏短，建议补充关键分支、异常规则和兜底策略。");
        }
        if (normalizedTools.isEmpty()) {
            warnings.add("未绑定工具，运行时仅能进行文本理解与总结。");
        }
        if ("HIGH".equals(normalizedRiskLevel) && (handoffRule == null || handoffRule.isBlank())) {
            warnings.add("高风险模式下建议明确 handoffRule。");
        }
        if (steps.size() < 2) {
            warnings.add("步骤拆解较少，建议增加可执行步骤描述。");
        }

        SpecIr specIr = new SpecIr(
                trimToNull(command.title()) == null ? "Unnamed Spec" : command.title().trim(),
                trimToNull(command.mode()) == null ? "generic" : command.mode().trim(),
                intents,
                steps,
                decisionRules,
                normalizedTools,
                normalizedKnowledgeRefs,
                handoffRule == null ? "" : handoffRule,
                normalizedRiskLevel
        );

        List<String> compileSummary = List.of(
                "specTitle=" + specIr.title() + ", mode=" + specIr.mode(),
                "steps=" + specIr.steps().size() + ", inferredIntents=" + specIr.intents().size(),
                normalizedTools.isEmpty() ? "toolRefs=none" : "toolRefs=" + String.join(", ", normalizedTools),
                normalizedKnowledgeRefs.isEmpty() ? "knowledgeRefs=none" : "knowledgeRefs=" + String.join(", ", normalizedKnowledgeRefs),
                "riskLevel=" + normalizedRiskLevel
        );

        return new SpecCompilation(
                specIr,
                normalizedTools,
                normalizedKnowledgeRefs,
                normalizedRiskLevel,
                warnings,
                compileSummary
        );
    }

    private List<String> extractSteps(String specText) {
        if (specText == null || specText.isBlank()) {
            return List.of("识别意图", "生成回复");
        }
        List<String> extracted = new ArrayList<>();
        String[] lines = specText.split("\\R");
        for (String line : lines) {
            String cleaned = line.trim();
            if (cleaned.isEmpty()) {
                continue;
            }
            cleaned = cleaned.replaceFirst("^[0-9]+[.)、]\\s*", "");
            cleaned = cleaned.replaceFirst("^[-*]\\s*", "");
            if (!cleaned.isEmpty()) {
                extracted.add(cleaned);
            }
            if (extracted.size() >= 8) {
                break;
            }
        }
        if (extracted.isEmpty()) {
            return List.of("识别意图", "生成回复");
        }
        return extracted;
    }

    private List<String> inferIntents(String specText) {
        String lower = safe(specText).toLowerCase(Locale.ROOT);
        LinkedHashSet<String> intents = new LinkedHashSet<>();
        if (lower.contains("报价") || lower.contains("quote")) {
            intents.add("quote");
        }
        if (lower.contains("审批") || lower.contains("approval")) {
            intents.add("approval");
        }
        if (lower.contains("查询") || lower.contains("query") || lower.contains("检索")) {
            intents.add("query");
        }
        if (lower.contains("转人工") || lower.contains("handoff")) {
            intents.add("handoff");
        }
        if (intents.isEmpty()) {
            intents.add("general");
        }
        return List.copyOf(intents);
    }

    private List<String> inferDecisionRules(String specText) {
        List<String> rules = new ArrayList<>();
        String[] lines = safe(specText).split("\\R");
        for (String line : lines) {
            String cleaned = line.trim();
            if (cleaned.isEmpty()) {
                continue;
            }
            String lower = cleaned.toLowerCase(Locale.ROOT);
            if (cleaned.contains("如果")
                    || cleaned.contains("则")
                    || cleaned.contains("必须")
                    || lower.contains("if ")
                    || lower.startsWith("if")
                    || lower.contains("must")) {
                rules.add(cleaned);
            }
        }
        return rules.stream().distinct().toList();
    }

    private List<String> normalizeList(List<String> refs) {
        if (refs == null || refs.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String item : refs) {
            if (item == null) {
                continue;
            }
            String cleaned = item.trim();
            if (!cleaned.isEmpty()) {
                values.add(cleaned);
            }
        }
        return List.copyOf(values);
    }

    private String normalizeRiskLevel(String riskLevel) {
        String normalized = safe(riskLevel).trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "MEDIUM";
        }
        if (List.of("LOW", "MEDIUM", "HIGH").contains(normalized)) {
            return normalized;
        }
        return "MEDIUM";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    public record SpecCompileCommand(
            String mode,
            String title,
            String specText,
            List<String> toolRefs,
            List<String> knowledgeRefs,
            String handoffRule,
            String riskLevel
    ) {
    }

    public record SpecCompilation(
            SpecIr specIr,
            List<String> normalizedToolRefs,
            List<String> normalizedKnowledgeRefs,
            String normalizedRiskLevel,
            List<String> warnings,
            List<String> compileSummary
    ) {
    }

    public record SpecIr(
            String title,
            String mode,
            List<String> intents,
            List<String> steps,
            List<String> decisionRules,
            List<String> toolRefs,
            List<String> knowledgeRefs,
            String handoffRule,
            String riskLevel
    ) {
    }
}
