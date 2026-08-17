import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { writeAuthPayload } from "../../auth/authStorage";
import { LS_PLATFORM_TOKEN } from "../../constants";
import { createTenant, fetchTenantList, isPlatformCompanyId, resolveTenantOwner, tenantApplicationsPath } from "./platformTenantsShared";

class MemoryStorage implements Storage {
  private values = new Map<string, string>();

  get length() {
    return this.values.size;
  }

  clear() {
    this.values.clear();
  }

  getItem(key: string) {
    return this.values.get(key) ?? null;
  }

  key(index: number) {
    return Array.from(this.values.keys())[index] ?? null;
  }

  removeItem(key: string) {
    this.values.delete(key);
  }

  setItem(key: string, value: string) {
    this.values.set(key, value);
  }
}

const storage = new MemoryStorage();
const companyId = "org00000000000000001";

beforeEach(() => {
  Object.defineProperty(globalThis, "localStorage", { value: storage, configurable: true });
  storage.clear();
  writeAuthPayload(LS_PLATFORM_TOKEN, { token: "platform-token", companyId });
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("platform tenant identity compatibility", () => {
  it("normalizes legacy orgId results before a tenant route is generated", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({
      success: true,
      data: [{ orgId: companyId, name: "示例租户", status: "ACTIVE", memberCount: 2 }],
    }), { status: 200, headers: { "Content-Type": "application/json" } })));

    await expect(fetchTenantList("ignored")).resolves.toMatchObject([{ companyId, name: "示例租户" }]);
    expect(tenantApplicationsPath(companyId)).toBe(`/platform/tenants/${companyId}`);
  });

  it("rejects undefined and malformed route parameters before details are requested", () => {
    expect(isPlatformCompanyId("undefined")).toBe(false);
    expect(isPlatformCompanyId("")).toBe(false);
    expect(tenantApplicationsPath("undefined")).toBeNull();
  });

  it("normalizes a legacy create result before the post-provisioning redirect", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({
      success: true,
      data: { orgId: companyId, companyName: "示例租户", status: "ACTIVE", ownerMemberId: "member-1", ownerAccountId: "account-1", reusedExistingAccount: false },
    }), { status: 200, headers: { "Content-Type": "application/json" } })));

    await expect(createTenant("ignored", {
      tenantName: "示例租户",
      ownerMode: "NEW",
      ownerMobile: "13800138000",
      idempotencyKey: "tenant-test-1",
    }))
      .resolves.toMatchObject({ companyId, companyName: "示例租户" });
  });

  it("resolves an existing owner before tenant provisioning", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({
      success: true,
      data: {
        resolution: "EXISTING_ACCOUNT",
        canProceed: true,
        accountPublicId: "U123456789ABC",
        displayName: "张三",
        maskedMobile: "138****8000",
        maskedEmail: "z***@example.com",
        identityStatus: "ACTIVE",
        activeTenantCount: 2,
        matchBasis: ["EMAIL"],
        unifiedIdentityEnabled: true,
        message: "检测到已注册用户，可以直接复用为新租户 Owner。",
      },
    }), { status: 200, headers: { "Content-Type": "application/json" } })));

    await expect(resolveTenantOwner({ ownerEmail: "zhangsan@example.com" })).resolves.toMatchObject({
      resolution: "EXISTING_ACCOUNT",
      accountPublicId: "U123456789ABC",
    });
  });
});
