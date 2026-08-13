import { describe, expect, it } from "vitest";
import { filterSystemApis, type SystemApi } from "./PlatformSystemApisPage";

function api(id: string, title: string, category: string, riskLevel: string, scope: string): SystemApi {
  return {
    id, title, category, riskLevel, requiredScope: scope,
    summary: `${title} 摘要`, description: "", method: "POST", path: `/v1/${id}`,
    protocols: ["HTTP"], authType: "Bearer OACT", audience: "Semattice", version: "v1",
    state: "published", idempotencyRequired: true, executionMode: "synchronous", approvalRequired: false,
    consumers: [], inputSchema: {}, outputSchema: {}, requestExample: {}, responseExample: {}, errorCodes: [],
    compatibility: "", sourceContract: "", callNotes: [],
  };
}

describe("system API catalog filters", () => {
  const apis = [
    api("runtime.record.query", "业务记录查询", "业务数据", "low", "runtime.record.read"),
    api("identity.principal.sync", "主体同步", "身份与授权", "medium", "identity.principal.sync"),
  ];

  it("searches across title, id, path and scope", () => {
    expect(filterSystemApis(apis, "record", "", "")).toHaveLength(1);
    expect(filterSystemApis(apis, "主体", "", "")[0].id).toBe("identity.principal.sync");
    expect(filterSystemApis(apis, "runtime.record.read", "", "")[0].id).toBe("runtime.record.query");
  });

  it("combines category and risk filters", () => {
    expect(filterSystemApis(apis, "", "身份与授权", "medium")).toEqual([apis[1]]);
    expect(filterSystemApis(apis, "", "业务数据", "medium")).toEqual([]);
  });
});
