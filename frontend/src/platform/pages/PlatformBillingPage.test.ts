import { describe, expect, it } from "vitest";
import { platformBillingLabels } from "./PlatformBillingPage";

describe("PlatformBillingPage labels", () => {
  it("keeps private deployment billing semantics explicit", () => {
    expect(platformBillingLabels.deploymentLabel("private_deployment")).toBe("私有化");
    expect(platformBillingLabels.billingTypeLabel("customer_paid")).toBe("客户侧成本");
    expect(platformBillingLabels.formatLimit(null)).toBe("合同约定");
  });

  it("labels SaaS credits and add-on package types", () => {
    expect(platformBillingLabels.deploymentLabel("saas")).toBe("SaaS");
    expect(platformBillingLabels.billingTypeLabel("platform_paid")).toBe("平台代付");
    expect(platformBillingLabels.packageTypeLabel("capacity")).toBe("容量包");
    expect(platformBillingLabels.overageLabel("auto_charge")).toBe("自动超额");
  });
});
