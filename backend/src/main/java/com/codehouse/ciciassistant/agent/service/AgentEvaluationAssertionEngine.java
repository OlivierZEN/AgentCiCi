package com.codehouse.ciciassistant.agent.service;

import com.codehouse.ciciassistant.agent.domain.AgentEvalCaseEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AgentEvaluationAssertionEngine {

    private final ObjectMapper objectMapper;

    public AgentEvaluationAssertionEngine(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AssertionOutcome evaluate(AgentEvalCaseEntity evalCase,
                                     String output,
                                     String actualStatus,
                                     List<String> trace,
                                     Map<String, Object> context,
                                     long elapsedMs) {
        List<AssertionDefinition> definitions = readDefinitions(evalCase);
        List<Map<String, Object>> results = new ArrayList<>();
        int passedCount = 0;
        String failureCategory = "";
        String failureSummary = "";
        for (AssertionDefinition definition : definitions) {
            SingleResult result = evaluateOne(definition, output, actualStatus, trace, context, elapsedMs);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", definition.type());
            payload.put("passed", result.passed());
            payload.put("expected", definition.expected());
            payload.put("actual", result.actual());
            payload.put("message", result.message());
            results.add(payload);
            if (result.passed()) {
                passedCount++;
            } else if (failureCategory.isBlank()) {
                failureCategory = failureCategory(definition.type());
                failureSummary = result.message();
            }
        }
        boolean passed = !definitions.isEmpty() && passedCount == definitions.size();
        double score = definitions.isEmpty() ? 0.0d : (double) passedCount / definitions.size();
        int toolCallCount = collectionSize(context.get("toolCalls"));
        if (toolCallCount == 0 && Boolean.TRUE.equals(context.get("toolInvoked"))) {
            toolCallCount = 1;
        }
        int ragHitCount = collectionSize(context.get("ragSources"));
        if (ragHitCount == 0 && Boolean.TRUE.equals(context.get("knowledgeUsed"))) {
            ragHitCount = 1;
        }
        return new AssertionOutcome(
                passed,
                score,
                failureCategory,
                failureSummary,
                List.copyOf(results),
                toolCallCount,
                ragHitCount
        );
    }

    private List<AssertionDefinition> readDefinitions(AgentEvalCaseEntity evalCase) {
        String json = evalCase.getAssertionConfigJson();
        if (json != null && !json.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(json);
                JsonNode items = root.isArray() ? root : root.path("assertions");
                if (items.isArray()) {
                    List<AssertionDefinition> definitions = new ArrayList<>();
                    for (JsonNode item : items) {
                        String type = text(item, "type");
                        if (type.isBlank()) continue;
                        Object expected = item.has("expected")
                                ? objectMapper.convertValue(item.get("expected"), Object.class)
                                : item.has("value")
                                ? objectMapper.convertValue(item.get("value"), Object.class)
                                : "";
                        String path = text(item, "path");
                        definitions.add(new AssertionDefinition(type.toUpperCase(Locale.ROOT), expected, path));
                    }
                    if (!definitions.isEmpty()) return definitions;
                }
            } catch (Exception ex) {
                return List.of(new AssertionDefinition("INVALID_ASSERTION_CONFIG", ex.getMessage(), ""));
            }
        }
        Object expected = switch (evalCase.getAssertionType()) {
            case AgentEvalCaseEntity.ASSERT_OUTPUT_CONTAINS -> evalCase.getExpectedText();
            case AgentEvalCaseEntity.ASSERT_OUTPUT_NOT_CONTAINS -> evalCase.getForbiddenText();
            case AgentEvalCaseEntity.ASSERT_STATUS_EQUALS -> evalCase.getExpectedStatus();
            case AgentEvalCaseEntity.ASSERT_TOOL_CALLED -> evalCase.getRequiredToolName();
            case AgentEvalCaseEntity.ASSERT_TOOL_NOT_CALLED -> evalCase.getForbiddenToolName();
            case AgentEvalCaseEntity.ASSERT_SAFETY_REFUSAL -> Map.of(
                    "text", empty(evalCase.getExpectedText()),
                    "status", empty(evalCase.getExpectedStatus()));
            default -> true;
        };
        return List.of(new AssertionDefinition(evalCase.getAssertionType(), expected, ""));
    }

    private SingleResult evaluateOne(AssertionDefinition definition,
                                     String output,
                                     String actualStatus,
                                     List<String> trace,
                                     Map<String, Object> context,
                                     long elapsedMs) {
        String expectedText = string(definition.expected());
        return switch (definition.type()) {
            case AgentEvalCaseEntity.ASSERT_OUTPUT_CONTAINS -> result(
                    contains(output, expectedText), expectedText, clip(output), "输出必须包含指定内容");
            case AgentEvalCaseEntity.ASSERT_OUTPUT_NOT_CONTAINS -> result(
                    !contains(output, expectedText), expectedText, clip(output), "输出包含禁止内容");
            case AgentEvalCaseEntity.ASSERT_STATUS_EQUALS -> result(
                    equalsText(actualStatus, expectedText), expectedText, actualStatus, "运行状态不符合预期");
            case AgentEvalCaseEntity.ASSERT_TOOL_CALLED -> result(
                    toolCalled(context, trace, expectedText), expectedText, toolEvidence(context), "未调用期望工具");
            case AgentEvalCaseEntity.ASSERT_TOOL_NOT_CALLED -> result(
                    !toolCalled(context, trace, expectedText), expectedText, toolEvidence(context), "调用了禁止工具");
            case "TOOL_ARGUMENT_CONTAINS" -> {
                boolean matched = toolArgumentsContain(context, definition.path(), definition.expected());
                yield result(matched, definition.expected(), toolEvidence(context), "工具参数不符合预期");
            }
            case AgentEvalCaseEntity.ASSERT_RAG_USED -> result(
                    Boolean.TRUE.equals(context.get("knowledgeUsed")) || traceContains(trace, "knowledge-search"),
                    true, context.getOrDefault("knowledgeUsed", false), "未执行知识检索");
            case "RAG_SOURCE_CONTAINS" -> result(
                    contains(string(context.get("ragSources")), expectedText), expectedText,
                    context.getOrDefault("ragSources", List.of()), "知识来源未命中指定内容");
            case AgentEvalCaseEntity.ASSERT_HANDOFF_REQUESTED -> result(
                    traceContains(trace, "handoff-request") || Boolean.TRUE.equals(context.get("handoffRequested")),
                    true, trace, "未触发人工接管");
            case AgentEvalCaseEntity.ASSERT_SAFETY_REFUSAL -> {
                Map<String, Object> expected = map(definition.expected());
                String text = string(expected.get("text"));
                String status = string(expected.get("status"));
                boolean matched = (!text.isBlank() && contains(output, text))
                        || (!status.isBlank() && equalsText(actualStatus, status));
                yield result(matched, definition.expected(), Map.of("output", clip(output), "status", actualStatus),
                        "未按要求拒答或转人工");
            }
            case "JSON_FIELD_EXISTS" -> result(
                    jsonFieldExists(output, definition.path().isBlank() ? expectedText : definition.path()),
                    definition.path().isBlank() ? expectedText : definition.path(), clip(output), "输出缺少结构化字段");
            case "MAX_LATENCY_MS" -> {
                long maximum = longValue(definition.expected());
                yield result(maximum > 0 && elapsedMs <= maximum, maximum, elapsedMs, "运行耗时超过上限");
            }
            case "MAX_TOOL_CALLS" -> {
                long maximum = longValue(definition.expected());
                int actual = collectionSize(context.get("toolCalls"));
                yield result(maximum >= 0 && actual <= maximum, maximum, actual, "工具调用次数超过上限");
            }
            case "MEMORY_CONTEXT_STATE" -> {
                String actual = string(context.get("memoryContextState"));
                yield result(equalsText(actual, expectedText), expectedText, actual, "记忆上下文状态不符合预期");
            }
            case "RUNTIME_MODE_EQUALS" -> {
                String actual = string(context.get("executionMode"));
                yield result(equalsText(actual, expectedText), expectedText, actual, "运行模式不符合预期");
            }
            case "REFLECT_STATUS_EQUALS" -> {
                String actual = string(context.get("reviewerStatus"));
                yield result(equalsText(actual, expectedText), expectedText, actual, "审查状态不符合预期");
            }
            case "NO_WRITE_BEFORE_CONFIRMATION" -> {
                boolean wrote = Boolean.TRUE.equals(context.get("writeSideEffectsExecuted"));
                boolean confirmationCompleted = Boolean.TRUE.equals(context.get("confirmationCompleted"));
                yield result(!wrote || confirmationCompleted, false, wrote, "确认前发生了写副作用");
            }
            case "INVALID_ASSERTION_CONFIG" -> result(false, definition.expected(), "", "断言配置无法解析");
            default -> result(false, definition.type(), "", "不支持的断言类型");
        };
    }

    private boolean toolCalled(Map<String, Object> context, List<String> trace, String toolName) {
        if (toolName == null || toolName.isBlank()) return false;
        Object calls = context.get("toolCalls");
        if (contains(string(calls), toolName)) return true;
        Object allowed = context.get("allowedToolNames");
        return traceContains(trace, "tool-invoke-best") && contains(string(allowed), toolName);
    }

    private boolean toolArgumentsContain(Map<String, Object> context, String path, Object expected) {
        Object calls = context.get("toolCalls");
        if (!(calls instanceof Collection<?> collection)) return false;
        for (Object item : collection) {
            Map<String, Object> call = map(item);
            Object arguments = call.getOrDefault("arguments", Map.of());
            Object actual = path == null || path.isBlank() ? arguments : valueAt(map(arguments), path);
            if (contains(string(actual), string(expected))) return true;
        }
        return false;
    }

    private boolean jsonFieldExists(String output, String path) {
        if (output == null || output.isBlank() || path == null || path.isBlank()) return false;
        try {
            Map<String, Object> root = objectMapper.readValue(output, new TypeReference<>() {});
            return valueAt(root, path) != null;
        } catch (Exception ex) {
            return false;
        }
    }

    private Object valueAt(Map<String, Object> root, String path) {
        Object current = root;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) return null;
            current = map.get(part);
            if (current == null) return null;
        }
        return current;
    }

    private SingleResult result(boolean passed, Object expected, Object actual, String failureMessage) {
        return new SingleResult(passed, expected, actual, passed ? "通过" : failureMessage);
    }

    private String failureCategory(String type) {
        return switch (type) {
            case AgentEvalCaseEntity.ASSERT_OUTPUT_CONTAINS, "JSON_FIELD_EXISTS" -> "ANSWER_MISSING_KEYPOINT";
            case AgentEvalCaseEntity.ASSERT_OUTPUT_NOT_CONTAINS -> "FORBIDDEN_CLAIM";
            case AgentEvalCaseEntity.ASSERT_TOOL_CALLED -> "TOOL_NOT_CALLED";
            case AgentEvalCaseEntity.ASSERT_TOOL_NOT_CALLED -> "FORBIDDEN_TOOL_CALLED";
            case "TOOL_ARGUMENT_CONTAINS" -> "TOOL_ARGUMENT_MISMATCH";
            case AgentEvalCaseEntity.ASSERT_RAG_USED, "RAG_SOURCE_CONTAINS" -> "RAG_MISS";
            case AgentEvalCaseEntity.ASSERT_HANDOFF_REQUESTED -> "HANDOFF_MISSING";
            case AgentEvalCaseEntity.ASSERT_SAFETY_REFUSAL -> "SAFETY_FAILED";
            case "MAX_LATENCY_MS" -> "LATENCY_BUDGET_EXCEEDED";
            case "MAX_TOOL_CALLS" -> "TOOL_BUDGET_EXCEEDED";
            case "RUNTIME_MODE_EQUALS" -> "RUNTIME_MODE_MISMATCH";
            case "REFLECT_STATUS_EQUALS" -> "REFLECT_STATUS_MISMATCH";
            case "NO_WRITE_BEFORE_CONFIRMATION" -> "WRITE_BEFORE_CONFIRMATION";
            case "INVALID_ASSERTION_CONFIG" -> "ASSERTION_CONFIG_INVALID";
            default -> "ASSERTION_FAILED";
        };
    }

    private String toolEvidence(Map<String, Object> context) {
        return string(context.getOrDefault("toolCalls", context.getOrDefault("allowedToolNames", List.of())));
    }

    private boolean traceContains(List<String> trace, String expected) {
        return trace != null && trace.stream().anyMatch(item -> contains(item, expected));
    }

    private boolean contains(String actual, String expected) {
        return actual != null && expected != null && !expected.isBlank()
                && actual.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
    }

    private boolean equalsText(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("").trim();
    }

    private int collectionSize(Object value) {
        return value instanceof Collection<?> collection ? collection.size() : 0;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        return Map.of();
    }

    private String string(Object value) {
        if (value == null) return "";
        if (value instanceof String text) return text;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private String empty(String value) {
        return value == null ? "" : value;
    }

    private String clip(String value) {
        if (value == null) return "";
        return value.length() <= 500 ? value : value.substring(0, 499) + "…";
    }

    private long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(string(value));
        } catch (NumberFormatException ex) {
            return -1L;
        }
    }

    private record AssertionDefinition(String type, Object expected, String path) {}
    private record SingleResult(boolean passed, Object expected, Object actual, String message) {}

    public record AssertionOutcome(
            boolean passed,
            double score,
            String failureCategory,
            String failureSummary,
            List<Map<String, Object>> assertionResults,
            int toolCallCount,
            int ragHitCount
    ) {}
}
