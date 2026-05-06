package com.codehouse.ciciassistant.skill.service;

import com.codehouse.ciciassistant.skill.domain.SkillDefinitionEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SkillAuthoringService {

    private final BuiltinSkillCreatorService builtinSkillCreatorService;
    private final SkillDefinitionService skillDefinitionService;
    private final SkillAuthoringSessionService skillAuthoringSessionService;
    private final ObjectMapper objectMapper;

    public SkillAuthoringService(BuiltinSkillCreatorService builtinSkillCreatorService,
                                 SkillDefinitionService skillDefinitionService,
                                 SkillAuthoringSessionService skillAuthoringSessionService,
                                 ObjectMapper objectMapper) {
        this.builtinSkillCreatorService = builtinSkillCreatorService;
        this.skillDefinitionService = skillDefinitionService;
        this.skillAuthoringSessionService = skillAuthoringSessionService;
        this.objectMapper = objectMapper;
    }

    public GenerateResult generate(String orgId, GenerateCommand command) {
        BuiltinSkillCreatorService.GeneratedSkillDraft draft = builtinSkillCreatorService.generate(
                orgId,
                new BuiltinSkillCreatorService.GenerateCommand(
                        command.sourceText(),
                        command.preferredName(),
                        command.preferredSkillCode(),
                        command.preferredModel(),
                        command.preferredProvider()
                )
        );
        SkillDefinitionService.PreviewResult preview = skillDefinitionService.previewCompile(
                orgId,
                new SkillDefinitionService.PreviewCommand(
                        draft.skillCode(),
                        draft.name(),
                        draft.draftSpecText(),
                        draft.promptFragment(),
                        draft.toolWhitelist(),
                        draft.kbWhitelist(),
                        draft.handoffRule(),
                        draft.outputContract(),
                        List.of(),
                        draft.riskLevel()
                )
        );
        String sessionId = skillAuthoringSessionService.createActiveSession(orgId, command.sourceText(), draft);
        return new GenerateResult(command.sourceText(), sessionId, draft, preview);
    }

    public GenerateResult refine(String orgId, RefineCommand command) {
        skillAuthoringSessionService.assertSessionActive(orgId, command.sessionId());
        BuiltinSkillCreatorService.GeneratedSkillDraft base = requireDraft(command.currentSkillSpec());
        List<ClarificationAnswer> clarificationAnswers = command.clarificationAnswers() == null
                ? List.of()
                : command.clarificationAnswers();
        String effectiveInstruction = normalizeRefineInstruction(command.sourceText(), clarificationAnswers);
        BuiltinSkillCreatorService.GeneratedSkillDraft current = mergeClarificationsIntoDraft(base, clarificationAnswers);
        BuiltinSkillCreatorService.GeneratedSkillDraft refined = builtinSkillCreatorService.generate(
                orgId,
                new BuiltinSkillCreatorService.GenerateCommand(
                        buildRefinementContext(effectiveInstruction, current),
                        current.name(),
                        current.skillCode(),
                        command.preferredModel(),
                        command.preferredProvider()
                )
        );
        BuiltinSkillCreatorService.GeneratedSkillDraft merged = mergeDraft(current, refined, effectiveInstruction);
        SkillDefinitionService.PreviewResult preview = skillDefinitionService.previewCompile(
                orgId,
                new SkillDefinitionService.PreviewCommand(
                        merged.skillCode(),
                        merged.name(),
                        merged.draftSpecText(),
                        merged.promptFragment(),
                        merged.toolWhitelist(),
                        merged.kbWhitelist(),
                        merged.handoffRule(),
                        merged.outputContract(),
                        List.of(),
                        merged.riskLevel()
                )
        );
        String sessionId = trimToNull(command.sessionId());
        if (sessionId != null) {
            skillAuthoringSessionService.updateActiveSession(orgId, sessionId, effectiveInstruction, merged);
        }
        return new GenerateResult(effectiveInstruction, sessionId, merged, preview);
    }

    public CreateResult create(String orgId, CreateCommand command) {
        BuiltinSkillCreatorService.GeneratedSkillDraft skillSpec = requireDraft(command.skillSpec());
        SkillSpecIr specIr = builtinSkillCreatorService.toSkillSpecIr(skillSpec);
        SkillDefinitionEntity created = skillDefinitionService.createSkill(
                orgId,
                new SkillDefinitionService.UpsertCommand(
                        skillSpec.skillCode(),
                        skillSpec.name(),
                        skillSpec.description(),
                        true,
                        skillSpec.promptFragment(),
                        skillSpec.draftSpecText(),
                        skillSpec.toolWhitelist(),
                        skillSpec.kbWhitelist(),
                        skillSpec.handoffRule(),
                        skillSpec.outputContract(),
                        List.of(),
                        skillSpec.riskLevel(),
                        "builtin-skill-creator",
                        toJson(specIr),
                        buildAuthoringNotes(skillSpec),
                        "AI 生成技能草稿",
                        "builtin-skill-creator"
                )
        );
        SkillDefinitionService.PreviewResult preview = skillDefinitionService.previewCompile(
                orgId,
                new SkillDefinitionService.PreviewCommand(
                        skillSpec.skillCode(),
                        skillSpec.name(),
                        skillSpec.draftSpecText(),
                        skillSpec.promptFragment(),
                        skillSpec.toolWhitelist(),
                        skillSpec.kbWhitelist(),
                        skillSpec.handoffRule(),
                        skillSpec.outputContract(),
                        List.of(),
                        skillSpec.riskLevel()
                )
        );
        skillAuthoringSessionService.completeSession(orgId, command.sessionId());
        return new CreateResult(command.sourceText(), trimToNull(command.sessionId()), skillSpec, created, preview);
    }

    private BuiltinSkillCreatorService.GeneratedSkillDraft requireDraft(
            BuiltinSkillCreatorService.GeneratedSkillDraft draft) {
        if (draft == null) {
            throw new IllegalArgumentException("currentSkillSpec is required");
        }
        return draft;
    }

    private String buildRefinementContext(String sourceText, BuiltinSkillCreatorService.GeneratedSkillDraft current) {
        String instruction = sourceText == null ? "" : sourceText.trim();
        if (instruction.isEmpty()) {
            throw new IllegalArgumentException("refinement instruction is required");
        }
        List<String> parts = new ArrayList<>();
        parts.add("当前技能名称：" + current.name());
        parts.add("当前描述：" + safe(current.description()));
        parts.add("当前提示片段：" + safe(current.promptFragment()));
        parts.add("当前规格正文：" + safe(current.draftSpecText()));
        parts.add("当前输出要求：" + safe(current.outputContract()));
        parts.add("当前转人工规则：" + safe(current.handoffRule()));
        parts.add("当前工具：" + join(current.toolWhitelist()));
        parts.add("当前知识库：" + join(current.kbWhitelist()));
        parts.add("优化要求：" + instruction);
        parts.add("合并规则：本次是对当前草稿的增量优化。除非优化要求明确要求删除、替换、重写或改顺序，否则必须保留当前提示片段和规格正文中的既有步骤、工具调用链、兜底规则和输出结构；遇到“增加、补充、加入、再增加”类要求，只在原流程基础上追加相关步骤或约束。");
        return String.join("\n", parts);
    }

    private String normalizeRefineInstruction(String sourceText, List<ClarificationAnswer> clarificationAnswers) {
        String trimmed = sourceText == null ? "" : sourceText.trim();
        if (!trimmed.isEmpty()) {
            return trimmed;
        }
        boolean hasAnswers = clarificationAnswers != null && clarificationAnswers.stream().anyMatch(item ->
                trimToNull(item.question()) != null && trimToNull(item.answer()) != null);
        if (hasAnswers) {
            return "仅根据管理员对结构化追问的补充答复更新技能草稿，保持 skillCode 不变，并据此修订描述、转人工规则与输出契约。";
        }
        throw new IllegalArgumentException("sourceText is required unless clarificationAnswers are provided");
    }

    private BuiltinSkillCreatorService.GeneratedSkillDraft mergeClarificationsIntoDraft(
            BuiltinSkillCreatorService.GeneratedSkillDraft draft,
            List<ClarificationAnswer> answers) {
        if (answers == null || answers.isEmpty()) {
            return draft;
        }
        LinkedHashSet<String> answeredQuestions = new LinkedHashSet<>();
        StringBuilder appendix = new StringBuilder();
        appendix.append("\n\n管理员对结构化追问的补充答复：\n");
        for (ClarificationAnswer answer : answers) {
            String question = trimToNull(answer.question());
            String value = trimToNull(answer.answer());
            if (question == null || value == null) {
                continue;
            }
            answeredQuestions.add(question);
            appendix.append("- 追问：").append(question).append("\n  答复：").append(value).append("\n");
        }
        if (answeredQuestions.isEmpty()) {
            return draft;
        }
        List<String> remainingQuestions = new ArrayList<>();
        for (String question : draft.clarificationQuestions()) {
            if (question == null || question.isBlank()) {
                continue;
            }
            String normalized = question.trim();
            boolean matched = answeredQuestions.stream().anyMatch(item -> item.trim().equals(normalized));
            if (!matched) {
                remainingQuestions.add(question);
            }
        }
        List<String> mergedWarnings = new ArrayList<>(draft.warnings() == null ? List.of() : draft.warnings());
        mergedWarnings.add("已合并管理员对结构化追问的补充答复，请在保存前再次核对工具与知识库边界。");
        String baseDraft = draft.draftSpecText() == null ? "" : draft.draftSpecText();
        return new BuiltinSkillCreatorService.GeneratedSkillDraft(
                draft.skillCode(),
                draft.name(),
                draft.description(),
                draft.promptFragment(),
                baseDraft + appendix,
                draft.toolWhitelist(),
                draft.kbWhitelist(),
                draft.handoffRule(),
                draft.outputContract(),
                draft.riskLevel(),
                draft.triggerHints(),
                draft.userIntentExamples(),
                remainingQuestions,
                mergedWarnings
        );
    }

    private BuiltinSkillCreatorService.GeneratedSkillDraft mergeDraft(
            BuiltinSkillCreatorService.GeneratedSkillDraft current,
            BuiltinSkillCreatorService.GeneratedSkillDraft refined,
            String sourceText) {
        String normalizedInstruction = sourceText == null ? "" : sourceText.trim();
        List<String> mergedWarnings = new ArrayList<>(union(current.warnings(), refined.warnings()));
        mergedWarnings.add("本次草稿已根据增量优化要求重新生成，请在保存前再次确认关键边界。");
        return new BuiltinSkillCreatorService.GeneratedSkillDraft(
                current.skillCode(),
                chooseText(current.name(), refined.name()),
                chooseText(current.description(), refined.description()),
                mergePromptFragment(current.promptFragment(), refined.promptFragment(), normalizedInstruction),
                mergeDraftSpecText(current.draftSpecText(), refined.draftSpecText(), normalizedInstruction),
                mergeRefs(current.toolWhitelist(), refined.toolWhitelist()),
                mergeRefs(current.kbWhitelist(), refined.kbWhitelist()),
                shouldRefreshHandoff(normalizedInstruction) ? chooseText(current.handoffRule(), refined.handoffRule()) : current.handoffRule(),
                shouldRefreshOutputContract(normalizedInstruction) ? chooseText(current.outputContract(), refined.outputContract()) : current.outputContract(),
                upgradeRisk(current.riskLevel(), refined.riskLevel(), normalizedInstruction),
                union(current.triggerHints(), refined.triggerHints()),
                union(current.userIntentExamples(), refined.userIntentExamples()),
                union(current.clarificationQuestions(), refined.clarificationQuestions()),
                mergedWarnings
        );
    }

    private List<String> mergeRefs(List<String> current, List<String> refined) {
        if (refined == null || refined.isEmpty()) {
            return current == null ? List.of() : List.copyOf(current);
        }
        return union(current, refined);
    }

    private List<String> union(List<String> left, List<String> right) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (left != null) {
            values.addAll(left);
        }
        if (right != null) {
            values.addAll(right);
        }
        values.removeIf(item -> item == null || item.isBlank());
        return List.copyOf(values);
    }

    private String chooseText(String current, String refined) {
        if (refined != null && !refined.isBlank()) {
            return refined;
        }
        return current;
    }

    private String mergePromptFragment(String current, String refined, String sourceText) {
        if (shouldAppendIncrementalRequirement(sourceText) && trimToNull(current) != null) {
            return appendIncrementalRequirement(current, sourceText);
        }
        return chooseText(current, refined);
    }

    /**
     * Preserve clarification answer blocks appended during Phase 2 session flow; refined generator may
     * otherwise replace the whole draft spec with a fresh template and drop the administrator replies.
     */
    private String mergeDraftSpecText(String current, String refined, String sourceText) {
        if (shouldAppendIncrementalRequirement(sourceText) && trimToNull(current) != null) {
            return appendIncrementalRequirement(current, sourceText);
        }
        String refinedText = refined == null || refined.isBlank() ? "" : refined;
        String currentText = current == null ? "" : current;
        String marker = "管理员对结构化追问的补充答复";
        if (currentText.contains(marker)) {
            int idx = currentText.indexOf(marker);
            String block = currentText.substring(idx).trim();
            if (!refinedText.contains(marker)) {
                if (refinedText.isBlank()) {
                    return currentText;
                }
                return refinedText.stripTrailing() + "\n\n" + block;
            }
        }
        if (!refinedText.isBlank()) {
            return refinedText;
        }
        return currentText;
    }

    private boolean shouldAppendIncrementalRequirement(String sourceText) {
        if (sourceText == null || sourceText.isBlank()) {
            return false;
        }
        return containsAny(sourceText, "再增加", "增加", "新增", "补充", "加入", "添加", "加上", "也要", "同时")
                && !containsAny(sourceText, "改成", "改为", "替换", "重写", "删除", "移除", "去掉", "不要", "不再", "重新生成");
    }

    private String appendIncrementalRequirement(String current, String sourceText) {
        String base = current == null ? "" : current.stripTrailing();
        String instruction = sourceText == null ? "" : sourceText.trim();
        if (instruction.isBlank() || base.contains(instruction)) {
            return base;
        }
        return base + "\n\n增量优化要求：在不改变上述既有步骤顺序和工具调用链的前提下，补充执行：" + stripTrailingSentencePunctuation(instruction) + "。";
    }

    private String stripTrailingSentencePunctuation(String raw) {
        String out = raw == null ? "" : raw.trim();
        while (out.endsWith("。") || out.endsWith("；") || out.endsWith(";") || out.endsWith(".") || out.endsWith("！") || out.endsWith("!")) {
            out = out.substring(0, out.length() - 1).trim();
        }
        return out;
    }

    private String join(List<String> items) {
        return items == null || items.isEmpty() ? "暂未指定" : String.join(", ", items);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "暂未指定" : value;
    }

    private boolean shouldRefreshOutputContract(String sourceText) {
        return containsAny(sourceText, "输出", "结果", "格式", "字段", "负责人", "动作", "建议");
    }

    private boolean shouldRefreshHandoff(String sourceText) {
        return containsAny(sourceText, "转人工", "人工", "确认", "审批", "升级", "兜底");
    }

    private String upgradeRisk(String currentRisk, String refinedRisk, String sourceText) {
        if (containsAny(sourceText, "高风险", "法务", "合同", "报价", "发送", "承诺")) {
            return "HIGH";
        }
        return riskRank(refinedRisk) > riskRank(currentRisk) ? refinedRisk : currentRisk;
    }

    private int riskRank(String risk) {
        if ("HIGH".equalsIgnoreCase(risk)) {
            return 3;
        }
        if ("MEDIUM".equalsIgnoreCase(risk)) {
            return 2;
        }
        return 1;
    }

    private boolean containsAny(String source, String... needles) {
        if (source == null || source.isBlank()) {
            return false;
        }
        for (String needle : needles) {
            if (source.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String buildAuthoringNotes(BuiltinSkillCreatorService.GeneratedSkillDraft draft) {
        List<String> lines = new ArrayList<>();
        if (draft.warnings() != null && !draft.warnings().isEmpty()) {
            lines.add("warnings:");
            for (String item : draft.warnings()) {
                lines.add("- " + item);
            }
        }
        if (draft.clarificationQuestions() != null && !draft.clarificationQuestions().isEmpty()) {
            lines.add("clarificationQuestions:");
            for (String item : draft.clarificationQuestions()) {
                lines.add("- " + item);
            }
        }
        return lines.isEmpty() ? null : String.join("\n", lines);
    }

    public record GenerateCommand(
            String sourceText,
            String preferredName,
            String preferredSkillCode,
            String preferredModel,
            String preferredProvider
    ) {
    }

    public record GenerateResult(
            String sourceText,
            String sessionId,
            BuiltinSkillCreatorService.GeneratedSkillDraft skillSpec,
            SkillDefinitionService.PreviewResult preview
    ) {
    }

    public record ClarificationAnswer(String question, String answer) {
    }

    public record RefineCommand(
            String sessionId,
            String sourceText,
            BuiltinSkillCreatorService.GeneratedSkillDraft currentSkillSpec,
            List<ClarificationAnswer> clarificationAnswers,
            String preferredModel,
            String preferredProvider
    ) {
    }

    public record CreateCommand(
            String sourceText,
            String sessionId,
            BuiltinSkillCreatorService.GeneratedSkillDraft skillSpec,
            String preferredModel,
            String preferredProvider
    ) {
    }

    public record CreateResult(
            String sourceText,
            String sessionId,
            BuiltinSkillCreatorService.GeneratedSkillDraft skillSpec,
            SkillDefinitionEntity createdSkill,
            SkillDefinitionService.PreviewResult preview
    ) {
    }
}
