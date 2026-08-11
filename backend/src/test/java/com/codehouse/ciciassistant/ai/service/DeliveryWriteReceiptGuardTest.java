package com.codehouse.ciciassistant.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeliveryWriteReceiptGuardTest {

    @Test
    void replacesDeliverySuccessClaimWithoutAnyToolReceipt() {
        String guarded = DeliveryWriteReceiptGuard.enforce(
                "帮我记录一个 Bug：确认按钮没有作用",
                "已记录缺陷，编号 BUG-83290B4F。",
                List.of());

        assertThat(guarded).contains("没有获得 Semattice 的真实写入成功回执");
        assertThat(guarded).contains("尚未创建、记录或修改");
        assertThat(guarded).doesNotContain("BUG-83290B4F");
    }

    @Test
    void replacesClaimWhenToolTraceIsFailedOrMalformed() {
        AgentRunTraceService.ToolCallTraceInput failed = trace(
                "semattice_project_delivery_create",
                "{\"status\":\"FAILED\",\"message\":\"unavailable\"}",
                false);
        AgentRunTraceService.ToolCallTraceInput malformed = trace(
                "semattice_dev_defect_create", "not-json", true);

        assertThat(DeliveryWriteReceiptGuard.enforce(
                "提交这个缺陷", "缺陷创建成功。", List.of(failed, malformed)))
                .startsWith("本轮没有获得 Semattice");
    }

    @Test
    void preservesClaimOnlyWithSematticeLiveRecordReceipt() {
        AgentRunTraceService.ToolCallTraceInput receipt = trace(
                "semattice_dev_defect_create",
                "{\"status\":\"SUCCESS\",\"source\":\"SEMATTICE_LIVE\","
                        + "\"object_api_name\":\"dev_defect\",\"record_id\":\"record-1\","
                        + "\"revision\":1,\"correlation_id\":\"corr-1\",\"readback_verified\":true}",
                true);

        assertThat(DeliveryWriteReceiptGuard.enforce(
                "确认提交这个 Bug", "已在 Semattice 创建缺陷 BUG-001。", List.of(receipt)))
                .isEqualTo("已在 Semattice 创建缺陷 BUG-001。");
    }

    @Test
    void doesNotChangeDraftsOrNonDeliveryAnswers() {
        assertThat(DeliveryWriteReceiptGuard.enforce(
                "帮我创建项目", "确认后我会创建项目。", List.of()))
                .isEqualTo("确认后我会创建项目。");
        assertThat(DeliveryWriteReceiptGuard.enforce(
                "帮我提交一个 Bug", "这是缺陷草案；确认无误后我会成功提交缺陷并返回实际记录 ID。", List.of()))
                .isEqualTo("这是缺陷草案；确认无误后我会成功提交缺陷并返回实际记录 ID。");
        assertThat(DeliveryWriteReceiptGuard.enforce(
                "总结今天的会议", "总结已完成。", List.of()))
                .isEqualTo("总结已完成。");
    }

    @Test
    void stillBlocksCompletedClaimEvenWhenTheAnswerAlsoContainsDraftLanguage() {
        assertThat(DeliveryWriteReceiptGuard.enforce(
                "帮我提交一个 Bug",
                "请先确认草案。缺陷已成功提交，编号 BUG-FALSE。",
                List.of()))
                .startsWith("本轮没有获得 Semattice");
    }

    private static AgentRunTraceService.ToolCallTraceInput trace(String name, String result, boolean success) {
        Instant now = Instant.now();
        return new AgentRunTraceService.ToolCallTraceInput(
                "tool-1", name, "{}", result, success, now, now, 0);
    }
}
