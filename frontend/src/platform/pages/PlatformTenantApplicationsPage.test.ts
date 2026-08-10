import { describe, expect, it } from "vitest";
import {
  devAutopilotInitializationReady,
  ownerIdentityStatus,
  type DevAutopilotApplication,
  type TenantOwnerIdentity,
} from "./PlatformTenantApplicationsPage";

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

describe("tenant Owner identity status", () => {
  const identity: TenantOwnerIdentity = {
    companyId: "org3989ajn55ev8eqj51",
    memberId: "member-1",
    displayName: "UAT Owner",
    maskedEmail: "o***@example.test",
    maskedMobile: "139****0002",
    publicId: "U2026WVBJQGYU",
    memberStatus: "ACTIVE",
    identityState: "MISSING",
    recoverable: true,
  };

  it("explains a missing OIDC binding as a recoverable login fault", () => {
    expect(ownerIdentityStatus(identity)).toEqual({
      label: "统一身份缺失",
      description: "本地 Owner 已存在，但尚未建立统一身份，当前无法通过 OIDC 登录。",
      tone: "danger",
    });
  });

  it("distinguishes pending activation from an active identity", () => {
    expect(ownerIdentityStatus({ ...identity, identityState: "PENDING_ACTIVATION", memberStatus: "PENDING_ACTIVATION" }).label).toBe("等待用户激活");
    expect(ownerIdentityStatus({ ...identity, identityState: "ACTIVE", recoverable: false }).label).toBe("身份正常");
  });
});
