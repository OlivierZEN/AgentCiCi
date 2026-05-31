import { describe, expect, it } from "vitest";
import { adminBillingLabels } from "./AdminBillingPage";

describe("AdminBillingPage labels", () => {
  it("formats organization billing labels for credits and limits", () => {
    expect(adminBillingLabels.formatCredits(12345.678)).toBe("12,345.68");
    expect(adminBillingLabels.formatLimit(null)).toBe("合同约定");
    expect(adminBillingLabels.ledgerTypeLabel("usage_debit")).toBe("用量扣减");
  });

  it("keeps customer-paid and quota warning semantics visible", () => {
    expect(adminBillingLabels.billingTypeLabel("customer_paid")).toBe("客户侧成本");
    expect(adminBillingLabels.billingTypeLabel("platform_paid")).toBe("订阅");
    expect(adminBillingLabels.warningLabel("critical")).toBe("临界");
  });

  it("uses admin-facing seat quota copy", () => {
    expect(adminBillingLabels.formatSeatEntitlement(0, 2, "已启用")).toBe("已启用 0 / 含 2");
    expect(adminBillingLabels.quotaDisplay({ code: "operation_seats", label: "操作席位", level: "ok", message: "1 / 100" })).toEqual({
      label: "团队成员",
      message: "已占用 1 个，套餐含 100 个",
    });
    expect(adminBillingLabels.quotaDisplay({ code: "builder_seats", label: "构建席位", level: "ok", message: "0 / 2" })).toEqual({
      label: "构建者席位",
      message: "已启用 0 个，套餐含 2 个",
    });
  });
});
