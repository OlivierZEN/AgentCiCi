package com.codehouse.ciciassistant.ai.service;

import com.codehouse.ciciassistant.ai.service.AliyunBailianClient.ChatCompletionResult;
import com.codehouse.ciciassistant.ai.service.AliyunBailianClient.ToolCallInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Uses the currently selected model to understand a complete DevAutopilot product-manager turn.
 * Natural-language routing is intentionally absent from this class: only the model's constrained
 * function result and explicit server protocols are parsed deterministically.
 */
@Service
public class DevAutopilotDialogueDecisionService {

    static final String TOOL_NAME = "resolve_devautopilot_dialogue";
    private static final int MAX_HISTORY_MESSAGES = 16;
    private static final int MAX_OUTPUT_TOKENS = 2_400;
    private static final int MAX_RESPONSE_BYTES = 96 * 1024;
    private static final Set<String> ACTIONS = Set.of(
            "CREATE_DRAFT", "QUERY", "DELETE_DRAFT", "TRANSFER_DRAFT", "CANCEL_DRAFT", "OTHER");
    private static final Set<String> OBJECT_TYPES = Set.of(
            "PROJECT", "REQUIREMENT", "TASK", "DEFECT", "CHANGE", "WORKLOG", "DELIVERY_EVENT", "UNKNOWN");
    private static final Set<String> CORE_FIELDS = Set.of("action", "object_type", "confidence");
    private static final List<String> TEXT_FIELDS = List.of(
            "reason", "name", "project", "requirement", "title", "classification_reason",
            "pm_assessment", "priority", "severity", "environment", "expected_result", "actual_result",
            "original_report", "clarification_question", "record_reference", "source_developer", "target_developer");
    private static final List<String> ARRAY_FIELDS = List.of(
            "reproduction_steps", "acceptance_criteria", "impact_analysis", "user_supplements", "assumptions");

    private final AliyunBailianClient modelClient;
    private final ObjectMapper objectMapper;

    public DevAutopilotDialogueDecisionService(AliyunBailianClient modelClient, ObjectMapper objectMapper) {
        this.modelClient = modelClient;
        this.objectMapper = objectMapper;
    }

    public DecisionResult decide(String modelName,
                                 List<Map<String, Object>> conversation,
                                 String currentQuestion,
                                 String apiBaseUrl,
                                 String apiKey) {
        Instant startedAt = Instant.now();
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", decisionPrompt()));
        List<Map<String, Object>> safeConversation = dialogueMessages(conversation);
        int start = Math.max(0, safeConversation.size() - MAX_HISTORY_MESSAGES);
        messages.addAll(safeConversation.subList(start, safeConversation.size()));
        if (messages.stream().noneMatch(message -> "user".equals(message.get("role"))
                && currentQuestion.equals(String.valueOf(message.get("content"))))) {
            messages.add(Map.of("role", "user", "content", currentQuestion));
        }

        ChatCompletionResult completion = modelClient.requiredToolCompletionWithCredentials(
                modelName, messages, decisionTool(), TOOL_NAME, apiBaseUrl, apiKey,
                MAX_OUTPUT_TOKENS, MAX_RESPONSE_BYTES);
        Optional<DialogueDecision> decision = parse(completion, safeConversation, currentQuestion);
        return new DecisionResult(
                decision,
                startedAt,
                Instant.now(),
                completion.promptTokens(),
                completion.completionTokens(),
                decision.isPresent() ? "SUCCESS" : "INVALID_STRUCTURED_RESULT");
    }

    Optional<DialogueDecision> parse(ChatCompletionResult completion,
                                     List<Map<String, Object>> conversation,
                                     String currentQuestion) {
        if (completion == null || completion.toolCalls() == null) {
            return Optional.empty();
        }
        if (completion.toolCalls().size() != 1) {
            return Optional.empty();
        }
        ToolCallInfo call = completion.toolCalls().stream()
                .filter(toolCall -> TOOL_NAME.equals(toolCall.name()))
                .findFirst()
                .orElse(null);
        if (call == null) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(call.arguments());
            if (!validShape(root)) {
                return Optional.empty();
            }
            String action = text(root, "action").toUpperCase(Locale.ROOT);
            String objectType = text(root, "object_type").toUpperCase(Locale.ROOT);
            double confidence = root.path("confidence").asDouble(0);
            if (!ACTIONS.contains(action) || !OBJECT_TYPES.contains(objectType)
                    || confidence < 0 || confidence > 1) {
                return Optional.empty();
            }
            List<String> historyUserMessages = dialogueMessages(conversation).stream()
                    .filter(message -> "user".equals(message.get("role")))
                    .map(message -> String.valueOf(message.get("content")))
                    .toList();
            List<String> userMessages;
            if (!historyUserMessages.contains(currentQuestion)) {
                List<String> expanded = new ArrayList<>(historyUserMessages);
                expanded.add(currentQuestion);
                userMessages = List.copyOf(expanded);
            } else {
                userMessages = historyUserMessages;
            }
            String reportedOriginal = rawText(root, "original_report");
            Optional<String> matchedOriginal = exactUserMessage(reportedOriginal, userMessages);
            boolean governedIntake = "CREATE_DRAFT".equals(action)
                    && Set.of("REQUIREMENT", "DEFECT", "CHANGE").contains(objectType);
            if (governedIntake && !reportedOriginal.isBlank() && matchedOriginal.isEmpty()) {
                return Optional.empty();
            }
            String originalReport = matchedOriginal.orElse(currentQuestion);
            List<String> supplements = new ArrayList<>();
            for (String reportedSupplement : rawTextArray(root.path("user_supplements"))) {
                Optional<String> matchedSupplement = exactUserMessage(reportedSupplement, userMessages);
                if (matchedSupplement.isEmpty()) {
                    return Optional.empty();
                }
                if (!matchedSupplement.get().equals(originalReport) && !supplements.contains(matchedSupplement.get())) {
                    supplements.add(matchedSupplement.get());
                }
            }
            String project = text(root, "project");
            String name = text(root, "name");
            if ("CREATE_DRAFT".equals(action) && "PROJECT".equals(objectType) && name.isBlank()) {
                // Some function-calling models place a newly named project in the generic project
                // slot. This is a structured field alias, not natural-language intent routing.
                name = project;
            }
            return Optional.of(new DialogueDecision(
                    action,
                    objectType,
                    confidence,
                    text(root, "reason"),
                    name,
                    project,
                    text(root, "requirement"),
                    text(root, "title"),
                    text(root, "classification_reason"),
                    text(root, "pm_assessment"),
                    normalizePriority(text(root, "priority")),
                    normalizeSeverity(text(root, "severity")),
                    text(root, "environment"),
                    textArray(root.path("reproduction_steps")),
                    text(root, "expected_result"),
                    text(root, "actual_result"),
                    textArray(root.path("acceptance_criteria")),
                    textArray(root.path("impact_analysis")),
                    originalReport,
                    List.copyOf(supplements),
                    textArray(root.path("assumptions")),
                    text(root, "clarification_question"),
                    text(root, "record_reference"),
                    text(root, "source_developer"),
                    text(root, "target_developer")));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public Optional<String> fixedAnswer(DialogueDecision decision) {
        if (decision == null) {
            return Optional.empty();
        }
        if (decision.confidence() < 0.70) {
            return Optional.of(clarification("请再说明你是要查询现有研发数据，还是新增、调整或删除一项研发事项。"));
        }
        return switch (decision.action()) {
            case "CREATE_DRAFT" -> Optional.of(renderCreateDraft(decision));
            case "DELETE_DRAFT" -> Optional.of(renderDeleteDraft(decision));
            case "CANCEL_DRAFT" -> Optional.of("## 研发事项受理状态\n\n- 状态：已取消\n- 数据写入：未执行\n\n本次受理已取消；如需继续，请重新描述要处理的事项。");
            default -> Optional.empty();
        };
    }

    private String renderCreateDraft(DialogueDecision decision) {
        if (!decision.clarificationQuestion().isBlank()) {
            return clarification(decision.clarificationQuestion());
        }
        return switch (decision.objectType()) {
            case "PROJECT" -> projectDraft(decision);
            case "TASK" -> taskDraft(decision);
            case "REQUIREMENT", "DEFECT", "CHANGE" -> intakeDraft(decision);
            default -> clarification("请补充你希望新增、修复或调整的具体业务结果。");
        };
    }

    private String projectDraft(DialogueDecision decision) {
        if (decision.name().isBlank()) {
            return clarification("请补充这个研发项目的名称。");
        }
        return "## 研发项目创建草案\n\n"
                + "| 字段 | 内容 |\n|---|---|\n"
                + "| 项目名称 | " + markdownCell(decision.name()) + " |\n"
                + "| 初始状态 | 规划中 |\n"
                + "| 健康度 | 待评估 |\n"
                + "| 进度 | 0% |\n"
                + "| 版本 | v0.1.0 |\n\n"
                + "- 当前状态：等待确认\n"
                + "- 数据写入：未执行\n\n"
                + "确认无误后，请回复：`确认创建项目：" + inlineCode(decision.name()) + "`";
    }

    private String taskDraft(DialogueDecision decision) {
        if (decision.requirement().isBlank()) {
            return clarification("请补充这项任务所属的父需求编号或标题。");
        }
        if (decision.title().isBlank()) {
            return clarification("请补充这项任务要交付的具体结果。");
        }
        return "## 研发任务创建草案\n\n"
                + "| 字段 | 内容 |\n|---|---|\n"
                + "| 父需求 | " + markdownCell(decision.requirement()) + " |\n"
                + "| 任务标题 | " + markdownCell(decision.title()) + " |\n\n"
                + "- 当前状态：等待确认\n"
                + "- 数据写入：未执行\n\n"
                + "确认无误后，请回复：`确认创建任务：需求=" + inlineCode(decision.requirement())
                + "；标题=" + inlineCode(decision.title()) + "`";
    }

    private String intakeDraft(DialogueDecision decision) {
        String classification = decision.objectType().toLowerCase(Locale.ROOT);
        String parent = "CHANGE".equals(decision.objectType()) ? decision.requirement() : decision.project();
        if (parent.isBlank()) {
            return clarification("CHANGE".equals(decision.objectType())
                    ? "请补充这项变更所针对的父需求编号或标题。"
                    : "请补充这个事项所属的父项目编号或名称。");
        }
        if (decision.title().isBlank() || decision.pmAssessment().isBlank()) {
            return clarification("请再说明你希望达到的业务结果，或当前实际发生的异常。");
        }
        List<String> acceptance = "REQUIREMENT".equals(decision.objectType())
                ? decision.acceptanceCriteria() : List.of();
        List<String> impact = "CHANGE".equals(decision.objectType())
                ? decision.impactAnalysis() : List.of();
        if ("REQUIREMENT".equals(decision.objectType()) && acceptance.isEmpty()) {
            return clarification("请再说明完成后可以观察到的业务结果。");
        }
        if ("CHANGE".equals(decision.objectType()) && impact.isEmpty()) {
            return clarification("请再说明希望调整的范围或规则。");
        }

        String label = switch (decision.objectType()) {
            case "DEFECT" -> "缺陷";
            case "CHANGE" -> "变更";
            default -> "需求";
        };
        StringBuilder answer = new StringBuilder("## ").append(label).append("受理草案\n\n")
                .append("| 字段 | 内容 |\n|---|---|\n")
                .append("| 事项类型 | ").append(label).append(" |\n")
                .append("| 标题 | ").append(markdownCell(decision.title())).append(" |\n")
                .append("| ").append("CHANGE".equals(decision.objectType()) ? "父需求" : "父项目")
                .append(" | ").append(markdownCell(parent)).append(" |\n")
                .append("| 优先级 | ").append(decision.priority()).append(" |\n")
                .append("| 分类依据 | ").append(markdownCell(decision.classificationReason())).append(" |\n\n")
                .append("### 用户原始描述\n\n").append(decision.originalReport()).append("\n\n")
                .append("### 产品经理整理\n\n- ").append(decision.pmAssessment()).append("\n");
        if (!acceptance.isEmpty()) {
            answer.append("\n### 验收标准\n\n");
            acceptance.forEach(item -> answer.append("- ").append(item).append("\n"));
        }
        if (!impact.isEmpty()) {
            answer.append("\n### 影响分析\n\n");
            impact.forEach(item -> answer.append("- ").append(item).append("\n"));
        }
        if ("DEFECT".equals(decision.objectType())) {
            answer.append("\n### 缺陷初评\n\n")
                    .append("- 严重度：").append(decision.severity()).append("\n")
                    .append("- 环境：").append(nonBlank(decision.environment(), "待开发者验证")).append("\n")
                    .append("- 预期结果：").append(nonBlank(decision.expectedResult(), "待开发者验证")).append("\n")
                    .append("- 实际结果：").append(nonBlank(decision.actualResult(), decision.originalReport())).append("\n");
        }
        answer.append("\n- 当前状态：等待确认\n- 数据写入：未执行\n\n")
                .append("确认无误后，请回复：`确认提交`");

        Map<String, Object> marker = new LinkedHashMap<>();
        marker.put("classification", classification);
        marker.put("classification_reason", decision.classificationReason());
        marker.put("project", decision.project());
        marker.put("requirement", decision.requirement());
        marker.put("title", decision.title());
        marker.put("original_report", decision.originalReport());
        marker.put("pm_assessment", decision.pmAssessment());
        marker.put("priority", decision.priority());
        marker.put("severity", "DEFECT".equals(decision.objectType()) ? decision.severity() : "");
        marker.put("environment", "DEFECT".equals(decision.objectType())
                ? nonBlank(decision.environment(), "待开发者验证") : "");
        marker.put("reproduction_steps", "DEFECT".equals(decision.objectType())
                ? nonEmpty(decision.reproductionSteps(), List.of("待开发者验证")) : List.of());
        marker.put("expected_result", "DEFECT".equals(decision.objectType())
                ? nonBlank(decision.expectedResult(), "待开发者验证") : "");
        marker.put("actual_result", "DEFECT".equals(decision.objectType())
                ? nonBlank(decision.actualResult(), decision.originalReport()) : "");
        marker.put("acceptance_criteria", acceptance);
        marker.put("impact_analysis", impact);
        marker.put("user_supplements", decision.userSupplements());
        marker.put("assumptions", decision.assumptions());
        marker.put("clarification_question", "");
        marker.put("ready_for_confirmation", true);
        marker.put("cancelled", false);
        try {
            String markerJson = objectMapper.writeValueAsString(marker)
                    .replace("--", "\\u002d\\u002d");
            answer.append("\n\n<!-- DEV_AUTOPILOT_INTAKE_V1 ")
                    .append(markerJson).append(" -->");
        } catch (Exception exception) {
            return clarification("草案结构化校验失败，请重新描述这个事项。");
        }
        return answer.toString();
    }

    private String renderDeleteDraft(DialogueDecision decision) {
        if (decision.recordReference().isBlank() || "UNKNOWN".equals(decision.objectType())) {
            return clarification("请补充要删除的研发记录类型，以及该记录的编号或名称。");
        }
        String label = objectLabel(decision.objectType());
        return "## 研发记录删除草案\n\n"
                + "| 字段 | 内容 |\n|---|---|\n"
                + "| 对象类型 | " + label + " |\n"
                + "| 目标记录 | " + markdownCell(decision.recordReference()) + " |\n"
                + "| 删除方式 | 移入回收站，30 天内可恢复 |\n\n"
                + "- 当前状态：等待确认\n"
                + "- 数据写入：未执行\n\n"
                + "确认无误后，请回复：`确认删除" + label + "：" + inlineCode(decision.recordReference()) + "`";
    }

    private static String clarification(String question) {
        return "## 需要补充信息\n\n- 判定状态：需要澄清\n- 数据写入：未执行\n\n" + question;
    }

    private static List<Map<String, Object>> dialogueMessages(List<Map<String, Object>> conversation) {
        if (conversation == null) {
            return List.of();
        }
        return conversation.stream()
                .filter(message -> "user".equals(message.get("role")) || "assistant".equals(message.get("role")))
                .filter(message -> message.get("content") instanceof String content && !content.isBlank())
                .map(message -> Map.<String, Object>of(
                        "role", String.valueOf(message.get("role")),
                        "content", String.valueOf(message.get("content"))))
                .toList();
    }

    private static Optional<String> exactUserMessage(String reported, List<String> userMessages) {
        if (reported == null || reported.isBlank()) {
            return Optional.empty();
        }
        return userMessages.contains(reported) ? Optional.of(reported) : Optional.empty();
    }

    private static String decisionPrompt() {
        return """
                你是 DEV Autopilot 产品经理对话的语义决策器。必须结合当前消息和最近会话整体语义判断用户真正要做什么，且必须调用 resolve_devautopilot_dialogue 返回结构化结果。

                不得按关键词、固定词位或孤立短语判断。显式出现“创建项目”可能是在否定、举例、询问或讨论，并不必然是创建；没有出现“创建”也可能确实希望新增项目。要识别否定、假设、反问、指代、上下文补充和隐含请求。

                action 语义：
                - CREATE_DRAFT：用户真实意图是新增项目、需求、任务、缺陷或变更，只生成草案，绝不执行写入。
                - QUERY：用户要读取现有 Semattice 研发业务记录的当前值、清单或汇总。
                - DELETE_DRAFT：用户要删除某条现有研发记录，只生成待确认草案。
                - TRANSFER_DRAFT：用户要把排队任务从一名开发者转给另一名开发者，只生成待确认草案。
                - CANCEL_DRAFT：用户明确取消当前待确认草案。
                - OTHER：以上均不是用户真实意图，交回普通对话。

                询问某次操作为什么失败、系统如何工作、应该怎样表达命令、假设性举例或一般建议属于 OTHER，除非用户同时明确要求读取现有 Semattice 业务记录的当前值。不要为了回答解释性问题而选择 QUERY。

                对 CREATE_DRAFT：
                - object_type 必须是 PROJECT、REQUIREMENT、TASK、DEFECT 或 CHANGE。
                - PROJECT 填 name；TASK 填 requirement 和 title。
                - REQUIREMENT/DEFECT/CHANGE 填完整专业草案字段；original_report 和 user_supplements 必须逐字引用真实用户消息。
                - 只有真正影响业务意图、父级归属或目标对象的歧义才填写一个 clarification_question；不要向普通用户索要严重度、优先级、技术环境、根因、测试方案等专业字段。
                - DEFECT 的未知工程细节由产品经理填写“待开发者验证”。
                - priority 使用 P0/P1/P2/P3，severity 使用 critical/high/medium/low。

                对 DELETE_DRAFT 填 object_type 和 record_reference；对 TRANSFER_DRAFT 填 source_developer 和 target_developer。QUERY、CANCEL_DRAFT、OTHER 不得伪造业务字段。confidence 是 0 到 1。reason 只写简洁分类依据，不写思维过程。
                """;
    }

    private static Map<String, Object> decisionTool() {
        Map<String, Object> property = Map.of("type", "string");
        Map<String, Object> arrayProperty = Map.of("type", "array", "items", property);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("action", Map.of("type", "string", "enum", ACTIONS));
        properties.put("object_type", Map.of("type", "string", "enum", OBJECT_TYPES));
        properties.put("confidence", Map.of("type", "number", "minimum", 0, "maximum", 1));
        for (String field : TEXT_FIELDS) {
            properties.put(field, property);
        }
        for (String field : ARRAY_FIELDS) {
            properties.put(field, arrayProperty);
        }
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", TOOL_NAME,
                        "description", "Return the semantic decision and normalized draft fields for one DevAutopilot dialogue turn.",
                        "parameters", Map.of(
                                "type", "object",
                                "additionalProperties", false,
                                "properties", properties,
                                "required", List.of("action", "object_type", "confidence", "reason"))));
    }

    private static String rawText(JsonNode root, String field) {
        JsonNode value = root.path(field);
        return value.isTextual() ? value.asText() : "";
    }

    private static boolean validShape(JsonNode root) {
        if (!root.isObject()) {
            return false;
        }
        if (!root.path("action").isTextual()
                || !root.path("object_type").isTextual()
                || !root.path("confidence").isNumber()) {
            return false;
        }
        Set<String> knownFields = new java.util.HashSet<>(CORE_FIELDS);
        knownFields.addAll(TEXT_FIELDS);
        knownFields.addAll(ARRAY_FIELDS);
        var fieldNames = root.fieldNames();
        while (fieldNames.hasNext()) {
            if (!knownFields.contains(fieldNames.next())) return false;
        }
        for (String field : TEXT_FIELDS) {
            if (root.has(field) && !root.path(field).isTextual()) return false;
        }
        for (String field : ARRAY_FIELDS) {
            if (root.has(field)) {
                if (!root.path(field).isArray()) return false;
                for (JsonNode value : root.path(field)) {
                    if (!value.isTextual()) return false;
                }
            }
        }
        return true;
    }

    private static String text(JsonNode root, String field) {
        return rawText(root, field).trim().replaceAll("\\s+", " ");
    }

    private static List<String> rawTextArray(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            if (item.isTextual() && !item.asText().isBlank()) {
                values.add(item.asText());
            }
        });
        return List.copyOf(values);
    }

    private static List<String> textArray(JsonNode node) {
        return rawTextArray(node).stream()
                .map(value -> value.trim().replaceAll("\\s+", " "))
                .filter(value -> !value.isBlank())
                .toList();
    }

    private static String normalizePriority(String value) {
        String normalized = value.toUpperCase(Locale.ROOT);
        return Set.of("P0", "P1", "P2", "P3").contains(normalized) ? normalized : "P2";
    }

    private static String normalizeSeverity(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return Set.of("critical", "high", "medium", "low").contains(normalized) ? normalized : "medium";
    }

    private static String markdownCell(String value) {
        return value.replace("|", "\\|").replace("\n", " ");
    }

    private static String inlineCode(String value) {
        return value.replace("`", "'").replace("\n", " ");
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static <T> List<T> nonEmpty(List<T> value, List<T> fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    private static String objectLabel(String objectType) {
        return switch (objectType) {
            case "PROJECT" -> "项目";
            case "REQUIREMENT" -> "需求";
            case "TASK" -> "任务";
            case "WORKLOG" -> "工时";
            case "CHANGE" -> "变更";
            case "DELIVERY_EVENT" -> "交付事件";
            case "DEFECT" -> "缺陷";
            default -> "研发记录";
        };
    }

    public record DecisionResult(Optional<DialogueDecision> decision,
                                 Instant startedAt,
                                 Instant endedAt,
                                 int promptTokens,
                                 int completionTokens,
                                 String status) {
    }

    public record DialogueDecision(String action,
                                   String objectType,
                                   double confidence,
                                   String reason,
                                   String name,
                                   String project,
                                   String requirement,
                                   String title,
                                   String classificationReason,
                                   String pmAssessment,
                                   String priority,
                                   String severity,
                                   String environment,
                                   List<String> reproductionSteps,
                                   String expectedResult,
                                   String actualResult,
                                   List<String> acceptanceCriteria,
                                   List<String> impactAnalysis,
                                   String originalReport,
                                   List<String> userSupplements,
                                   List<String> assumptions,
                                   String clarificationQuestion,
                                   String recordReference,
                                   String sourceDeveloper,
                                   String targetDeveloper) {
    }
}
