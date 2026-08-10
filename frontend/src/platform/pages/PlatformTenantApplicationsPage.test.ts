import { describe, expect, it } from "vitest";
import { devAutopilotInitializationReady, type DevAutopilotApplication } from "./PlatformTenantApplicationsPage";

function application(overrides: Partial<DevAutopilotApplication>): DevAutopilotApplication {
  return {
    enabled: true,
    desiredState: "ACTIVE",
    actualState: "ACTIVE",
    resources: [],
    ...overrides,
  };
}

describe("DevAutopilot initialization readiness", () => {
  it("trusts the server readiness signal instead of resource-row presence", () => {
    const legacyRows = [
      { logicalRole: "product_manager", resourceType: "AGENT", resourceAlias: "pm-agent", displayName: "天工产品经理", lifecycleState: "ACTIVE", primary: true },
      { logicalRole: "product_manager", resourceType: "SERVICE_PRINCIPAL", resourceAlias: "pm", displayName: "天工产品经理", lifecycleState: "ACTIVE", primary: true },
    ];

    expect(devAutopilotInitializationReady(application({ initializationReady: false, resources: legacyRows }))).toBe(false);
    expect(devAutopilotInitializationReady(application({ initializationReady: true, resources: legacyRows }))).toBe(true);
  });

  it("keeps the legacy resource fallback for an older compatible backend", () => {
    const resources = [
      { logicalRole: "product_manager", resourceType: "AGENT", resourceAlias: "pm-agent", displayName: "天工产品经理", lifecycleState: "ACTIVE", primary: true },
      { logicalRole: "product_manager", resourceType: "SERVICE_PRINCIPAL", resourceAlias: "pm", displayName: "天工产品经理", lifecycleState: "ACTIVE", primary: true },
    ];

    expect(devAutopilotInitializationReady(application({ resources }))).toBe(true);
  });
});
