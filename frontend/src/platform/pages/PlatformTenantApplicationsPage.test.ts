import { afterEach, describe, expect, it, vi } from "vitest";
import {
  applicationActionLabel,
  devAutopilotInitializationReady,
  devAutopilotActivationKey,
  fetchOwnerIdentity,
  isValidIntakeReconciliationInput,
  ownerIdentityStatus,
  type DevAutopilotApplication,
  type TenantOwnerIdentity,
} from "./PlatformTenantApplicationsPage";

afterEach(() => {
  vi.unstubAllGlobals();
});

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
  it("uses one stable tenant activation key so a failed saga can resume", () => {
    expect(devAutopilotActivationKey("org00000000000000001"))
      .toBe("devautopilot-standard-v1-org00000000000000001");
  });
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

describe("tenant application open actions", () => {
  it("keeps AgentCiCi lifecycle copy and gives other safe routes an application label", () => {
    expect(applicationActionLabel("OPEN", application({ appCode: "agentcici" })))
      .toBe("进入生命周期管理");
    expect(applicationActionLabel("OPEN", application({ appCode: "demo-example", displayName: "DEMO示例应用" })))
      .toBe("打开应用");
  });
});

describe("DevAutopilot historical intake reconciliation input", () => {
  it("accepts only a bounded session id and UUID record id", () => {
    expect(isValidIntakeReconciliationInput("8f90d20c-233d-4e90-bf97-a22e9d4f23ad", "019ff668-6874-7348-ab3c-6d1c2635ad0a")).toBe(true);
    expect(isValidIntakeReconciliationInput("", "019ff668-6874-7348-ab3c-6d1c2635ad0a")).toBe(false);
    expect(isValidIntakeReconciliationInput("workbench:devautopilot-pm", "019ff668-6874-7348-ab3c-6d1c2635ad0a")).toBe(false);
    expect(isValidIntakeReconciliationInput("8f90d20c-233d-4e90-bf97-a22e9d4f23ad", "REQ-6F34ECF3")).toBe(false);
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

  it("does not block tenant application management when a legacy tenant has no Owner", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({
      success: false,
      message: "租户 Owner 不存在",
    }), { status: 404, headers: { "Content-Type": "application/json" } })));

    await expect(fetchOwnerIdentity("platform-token", "org00000000000000001")).resolves.toBeNull();
  });
});
