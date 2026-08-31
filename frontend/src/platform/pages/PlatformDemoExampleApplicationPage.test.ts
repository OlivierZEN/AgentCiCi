import { describe, expect, it } from "vitest";
import {
  demoEffectiveParameterRows,
  demoProviderConnectionRows,
  type DemoApplicationDetail,
  type DemoProviderConnection,
} from "./PlatformDemoExampleApplicationPage";

const detail: DemoApplicationDetail = {
  application: {
    appCode: "demo-example",
    displayName: "DEMO示例应用",
    summary: "单页单对象的应用中心完整配置参考",
    iconKey: "application",
    ownerTeam: "AgentCiCi",
    tenantMode: "PLATFORM_BASE",
    catalogStatus: "PUBLISHED",
    trustedAppCode: null,
    launchMode: "PLATFORM_ROUTE",
    launchRouteKey: "demo-example.page",
    defaultVersion: "1.0.0",
  },
  versions: [{
    version: "1.0.0",
    manifestSchemaVersion: "tenant-application/v1",
    providerBindingKey: null,
    initializationEngine: "NONE",
    manifest: { schemaVersion: "tenant-application/v1", initializationEngine: "NONE", steps: [] },
    manifestDigest: "1".repeat(64),
    status: "PUBLISHED",
    dependencies: [{
      appCode: "semattice",
      versionConstraint: ">=1.0.0",
      dependencyType: "OPTIONAL",
      activationPolicy: "AUTO_PROVISION_ALLOWED",
    }],
  }],
};

const connection: DemoProviderConnection = {
  bindingKey: "demo-example.lifecycle",
  appCode: "demo-example",
  displayName: "DEMO 生命周期服务",
  environmentKey: "default",
  networkScope: "PUBLIC_HTTPS",
  status: "DRAFT",
  activeRevisionId: null,
  revisions: [{
    id: "catalog-demo-example-lifecycle-r1",
    revisionNumber: 1,
    baseUrl: "https://service.example.test",
    contractVersion: "v1",
    authType: "HMAC_SHA256_SECRET_REF",
    secretRef: "demo-example.lifecycle-key",
    healthPath: "/internal/tenant-lifecycle/v1/health",
    activatePath: "/internal/tenant-lifecycle/v1/activations",
    reconcilePath: "/internal/tenant-lifecycle/v1/reconciliations",
    suspendPath: "/internal/tenant-lifecycle/v1/suspensions",
    resumePath: "/internal/tenant-lifecycle/v1/resumptions",
    upgradePath: "/internal/tenant-lifecycle/v1/upgrades",
    timeoutMs: 10000,
    maxAttempts: 2,
    testStatus: "NOT_TESTED",
  }],
};

describe("DEMO example application parameter readback", () => {
  it("builds one complete effective configuration object from catalog facts", () => {
    const rows = demoEffectiveParameterRows(detail);
    expect(rows).toHaveLength(22);
    expect(rows).toContainEqual(expect.objectContaining({ parameter: "appCode", value: "demo-example" }));
    expect(rows).toContainEqual(expect.objectContaining({ parameter: "trustedAppCode", value: "未关联" }));
    expect(rows).toContainEqual(expect.objectContaining({ parameter: "providerBindingKey", value: "未绑定" }));
    expect(rows).toContainEqual(expect.objectContaining({ parameter: "dependencyType", value: "OPTIONAL" }));
  });

  it("builds every provider connection section from the governed draft revision", () => {
    const rows = demoProviderConnectionRows(connection);
    expect(rows).toHaveLength(20);
    expect(rows.map((row) => row.parameter)).toEqual(expect.arrayContaining([
      "bindingKey", "environmentKey", "networkScope", "baseUrl", "contractVersion",
      "healthPath", "activatePath", "reconcilePath", "suspendPath", "resumePath",
      "upgradePath", "authType", "secretRef", "timeoutMs", "maxAttempts", "status",
      "activeRevisionId", "revisionNumber", "testStatus",
    ]));
    expect(rows.find((row) => row.parameter === "baseUrl")?.value)
      .toBe("https://service.example.test");
    expect(rows.find((row) => row.parameter === "status")?.value).toBe("DRAFT");
    expect(rows.find((row) => row.parameter === "testStatus")?.value).toBe("NOT_TESTED");
    expect(rows.find((row) => row.parameter === "activeRevisionId")?.value).toBe("未启用");
  });
});
