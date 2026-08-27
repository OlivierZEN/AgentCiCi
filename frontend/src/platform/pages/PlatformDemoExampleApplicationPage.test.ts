import { describe, expect, it } from "vitest";
import {
  DEMO_PROVIDER_REFERENCE_ROWS,
  demoEffectiveParameterRows,
  type DemoApplicationDetail,
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

describe("DEMO example application parameter readback", () => {
  it("builds one complete effective configuration object from catalog facts", () => {
    const rows = demoEffectiveParameterRows(detail);
    expect(rows).toHaveLength(22);
    expect(rows).toContainEqual(expect.objectContaining({ parameter: "appCode", value: "demo-example" }));
    expect(rows).toContainEqual(expect.objectContaining({ parameter: "trustedAppCode", value: "未关联" }));
    expect(rows).toContainEqual(expect.objectContaining({ parameter: "providerBindingKey", value: "未配置" }));
    expect(rows).toContainEqual(expect.objectContaining({ parameter: "dependencyType", value: "OPTIONAL" }));
  });

  it("covers every provider connection section without using a real environment address", () => {
    expect(DEMO_PROVIDER_REFERENCE_ROWS).toHaveLength(16);
    expect(DEMO_PROVIDER_REFERENCE_ROWS.map((row) => row.parameter)).toEqual(expect.arrayContaining([
      "bindingKey", "environmentKey", "networkScope", "baseUrl", "contractVersion",
      "healthPath", "activatePath", "reconcilePath", "suspendPath", "resumePath",
      "upgradePath", "authType", "secretRef", "timeoutMs", "maxAttempts",
    ]));
    expect(DEMO_PROVIDER_REFERENCE_ROWS.find((row) => row.parameter === "baseUrl")?.value)
      .toBe("https://service.example.test");
  });
});
