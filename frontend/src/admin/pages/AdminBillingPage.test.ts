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
    expect(adminBillingLabels.warningLabel("critical")).toBe("临界");
  });
});
