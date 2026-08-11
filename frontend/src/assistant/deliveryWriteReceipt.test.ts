import { describe, expect, it } from "vitest";
import { extractDeliveryWriteReceipt, parseDeliveryWriteReceiptEvent } from "./deliveryWriteReceipt";

describe("delivery write receipt", () => {
  it("accepts only readback-verified Semattice success receipts", () => {
    expect(parseDeliveryWriteReceiptEvent({
      toolName: "semattice_project_delivery_create",
      payload: JSON.stringify({
        status: "SUCCESS",
        object_api_name: "dev_defect",
        title: "确认按钮无响应",
        code: "BUG-1234",
        record_id: "record-1",
        revision: 2,
        correlation_id: "corr-1",
        readback_verified: true,
      }),
    })).toMatchObject({
      status: "SUCCESS",
      objectApiName: "dev_defect",
      recordId: "record-1",
      revision: 2,
      correlationId: "corr-1",
    });

    expect(parseDeliveryWriteReceiptEvent({
      toolName: "semattice_project_delivery_create",
      payload: JSON.stringify({ status: "SUCCESS", record_id: "record-1" }),
    })).toEqual({
      status: "FAILED",
      message: "Semattice 成功回执不完整，不能确认创建成功。",
    });
  });

  it("restores a verified receipt from persisted assistant text", () => {
    expect(extractDeliveryWriteReceipt(
      "已在 Semattice 创建缺陷：确认按钮无响应（BUG-1234）。记录 ID：record-1；revision：2；关联号：corr-1。",
    )).toEqual({
      status: "SUCCESS",
      objectApiName: "dev_defect",
      subject: "确认按钮无响应",
      code: "BUG-1234",
      recordId: "record-1",
      revision: 2,
      correlationId: "corr-1",
    });
  });

  it("ignores unrelated tool results", () => {
    expect(parseDeliveryWriteReceiptEvent({ toolName: "get_pending_approvals", payload: "{}" })).toBeNull();
  });
});
