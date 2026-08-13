package com.codehouse.ciciassistant.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Prevents a model response from turning an unverified delivery write into a success claim.
 *
 * <p>The model may explain or draft any delivery operation, but a success statement is allowed only
 * when the current run contains a successful Semattice live-record receipt. This guard deliberately
 * runs after tool/model orchestration and before persistence or SSE delivery.</p>
 */
final class DeliveryWriteReceiptGuard {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final Set<String> SEMATTICE_WRITE_TOOLS = Set.of(
            "semattice_project_delivery_create",
            "semattice_project_delivery_update",
            "semattice_project_delivery_delete",
            "semattice_project_delivery_review",
            "semattice_dev_defect_create",
            "semattice_dev_defect_update"
    );
    private static final Pattern SUCCESS_CLAIM = Pattern.compile(
            "(?:已经|已|成功)(?:在\\s*Semattice\\s*)?(?:创建|记录|提交|写入|更新|修改|删除|关闭|重开|分派|保存|登记|处理完成)"
                    + "|(?:已经|已)(?:在\\s*Semattice\\s*)?[^。；\\n]{0,24}(?:创建|记录|提交|写入|更新|修改|删除|关闭|重开|分派|保存|登记)"
                    + "|(?:创建|记录|提交|写入|更新|修改|删除|关闭|重开|分派|保存|登记)(?:成功|完成)",
            Pattern.CASE_INSENSITIVE);

    private DeliveryWriteReceiptGuard() {
    }

    static String enforce(String question,
                          String answer,
                          List<AgentRunTraceService.ToolCallTraceInput> toolCallTraces) {
        if (!isDeliveryWriteIntent(question) || answer == null || !containsCompletedSuccessClaim(answer)) {
            return answer;
        }
        if (hasLiveRecordReceipt(toolCallTraces)) {
            return answer;
        }
        return "本轮没有获得 Semattice 的真实写入成功回执，因此尚未创建、记录或修改任何研发交付数据。"
                + "请核对对象能力和必填信息后重试；只有返回实际记录 ID 的操作才算成功。";
    }

    static boolean isDeliveryWriteIntent(String question) {
        String normalized = question == null ? "" : question.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return false;
        }
        boolean writeVerb = containsAny(normalized,
                "创建", "新建", "新增", "记录", "登记", "提交", "写入", "更新", "修改",
                "删除", "关闭", "重开", "分派", "指派", "create", "add", "record", "submit",
                "update", "delete", "close", "reopen", "assign");
        boolean deliveryEntity = containsAny(normalized,
                "研发项目", "项目", "需求", "任务", "缺陷", "bug", "defect", "工时", "变更",
                "交付事件", "project", "requirement", "task", "worklog", "change");
        return writeVerb && deliveryEntity;
    }

    static boolean hasLiveRecordReceipt(List<AgentRunTraceService.ToolCallTraceInput> traces) {
        for (AgentRunTraceService.ToolCallTraceInput trace : traces == null
                ? List.<AgentRunTraceService.ToolCallTraceInput>of()
                : traces) {
            if (trace == null || !trace.success() || !SEMATTICE_WRITE_TOOLS.contains(trace.name())) {
                continue;
            }
            try {
                JsonNode result = OBJECT_MAPPER.readTree(trace.result());
                boolean fieldDigestRequired = "semattice_project_delivery_create".equals(trace.name());
                String contentDigest = result.path("content_digest").asText();
                if ("SUCCESS".equals(result.path("status").asText())
                        && "SEMATTICE_LIVE".equals(result.path("source").asText())
                        && !result.path("object_api_name").asText().isBlank()
                        && !result.path("record_id").asText().isBlank()
                        && result.path("revision").asLong() > 0
                        && !result.path("correlation_id").asText().isBlank()
                        && result.path("readback_verified").asBoolean(false)
                        && (!fieldDigestRequired || contentDigest.matches("^[0-9a-f]{64}$"))) {
                    return true;
                }
            } catch (Exception ignored) {
                // Malformed or non-JSON results are not trusted as a write receipt.
            }
        }
        return false;
    }

    /**
     * A draft is allowed to explain what will happen after confirmation. Only a completed-action
     * claim is receipt-gated; conditional/future wording must not make the confirmation flow unusable.
     */
    private static boolean containsCompletedSuccessClaim(String answer) {
        var matcher = SUCCESS_CLAIM.matcher(answer);
        while (matcher.find()) {
            String claim = matcher.group().replaceAll("\\s+", "");
            if (containsAny(claim, "确认后", "确认无误后", "等待", "准备后")) {
                continue;
            }
            String prefix = answer.substring(Math.max(0, matcher.start() - 32), matcher.start())
                    .replaceAll("\\s+", "");
            if (containsAny(prefix,
                    "确认后", "确认无误后", "待确认后", "如果确认", "若确认", "经确认后",
                    "将", "会", "才会", "才能", "需要", "必须", "以便")) {
                continue;
            }
            return true;
        }
        return false;
    }

    private static boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
