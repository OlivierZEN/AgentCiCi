import { describe, expect, it } from "vitest";
import { buildBillingPayload, deploymentModeLabel, formFromItem, itemTypeLabel, publishStatusLabel, type BillingConfigItem } from "./PlatformBillingPage";

describe("PlatformBillingPage helpers", () => {
  it("labels platform billing item dimensions", () => {
    expect(itemTypeLabel("PLAN")).toBe("版本套餐");
    expect(itemTypeLabel("CAPACITY_PACK")).toBe("容量包");
    expect(deploymentModeLabel("private_deployment")).toBe("私有化");
    expect(publishStatusLabel("SUPERSEDED")).toBe("已替换");
  });

  it("normalizes editable form values into API payload", () => {
    const item: BillingConfigItem = {
      id: 1,
      itemType: "PLAN",
      itemCode: "private_enterprise",
      displayName: "企业版",
      deploymentMode: "private_deployment",
      versionNo: 2,
      publishStatus: "DRAFT",
      enabled: true,
      billingTypePolicy: "customer_paid",
      includedCredits: 0,
      operationSeatLimit: 200,
      builderSeatLimit: 50,
      agentLimit: 120,
      skillWorkflowLimit: 500,
      knowledgeCapacityGb: 1000,
      openApiQps: 200,
      openApiConcurrency: 50,
      openApiCredentialLimit: 20,
      connectorLimit: 100,
      meetingConcurrency: 8,
      traceRetentionDays: 180,
      auditRetentionDays: 1095,
      environmentLimit: 3,
      overageMode: "soft_limit",
      slaTierCode: "business",
      addonCategory: null,
      pricingUnit: "license_year",
      policyJson: "{\"localModelTokenDoubleCharge\":false}",
      changeReason: "TASK-143 平台计费配置调整",
      updatedAt: "2026-05-28T08:00:00Z",
      latestVersionNo: 2,
      publishedVersionNo: 1,
      versionCount: 2,
    };

    const payload = buildBillingPayload({
      ...formFromItem(item),
      openApiQps: "250",
      connectorLimit: "",
      changeReason: " TASK-143 调整私有化套餐容量 ",
    });

    expect(payload.itemCode).toBe("private_enterprise");
    expect(payload.openApiQps).toBe(250);
    expect(payload.connectorLimit).toBeNull();
    expect(payload.billingTypePolicy).toBe("customer_paid");
    expect(payload.policyJson).toContain("localModelTokenDoubleCharge");
    expect(payload.changeReason).toBe("TASK-143 调整私有化套餐容量");
  });
});
