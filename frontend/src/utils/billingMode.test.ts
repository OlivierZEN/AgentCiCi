import { describe, expect, it } from "vitest";
import { defaultBillingModeView, isPrivateDeploymentBilling, normalizeBillingDeploymentMode } from "./billingMode";

describe("billingMode", () => {
  it("defaults unknown values to private deployment", () => {
    expect(normalizeBillingDeploymentMode(undefined)).toBe("private_deployment");
    expect(normalizeBillingDeploymentMode("unexpected")).toBe("private_deployment");
    expect(isPrivateDeploymentBilling("unexpected")).toBe(true);
  });

  it("normalizes SaaS aliases", () => {
    expect(normalizeBillingDeploymentMode("cloud-saas")).toBe("saas");
    expect(normalizeBillingDeploymentMode("SAAS")).toBe("saas");
    expect(isPrivateDeploymentBilling("saas")).toBe(false);
  });

  it("describes private deployment without local token double charging", () => {
    const view = defaultBillingModeView("private_deployment");

    expect(view.primaryRevenueModel).toContain("私有化年费许可");
    expect(view.localModelTokenPolicy).toContain("默认不对本地模型 token 二次收费");
    expect(view.supportedBillingTypes).toContain("customer_paid");
  });
});
