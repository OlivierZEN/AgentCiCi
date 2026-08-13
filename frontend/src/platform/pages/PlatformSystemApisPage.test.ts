import { describe, expect, it } from "vitest";
import {
  filterSystemApis,
  SYSTEM_API_CATALOG_ENDPOINT,
  systemApiCatalogFailureMessage,
  systemApiRequestPrelude,
  type SystemApi,
} from "./PlatformSystemApisPage";

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

describe("system API catalog transport", () => {
  it("uses the governed browser API namespace rather than the SPA route", () => {
    expect(SYSTEM_API_CATALOG_ENDPOINT).toBe("/api/platform/system-apis");
  });

  it("turns an HTML SPA fallback into an actionable error", () => {
    expect(systemApiCatalogFailureMessage(200, undefined, "<!doctype html><html></html>"))
      .toBe("系统 API 目录加载失败：服务未返回预期数据（HTTP 200）。请刷新页面并确认前后端版本一致后重试。");
  });

  it("preserves a structured backend error message", () => {
    expect(systemApiCatalogFailureMessage(503, "Semattice 目录暂不可用", "{}"))
      .toBe("Semattice 目录暂不可用");
  });
});

describe("system API invocation documentation", () => {
  it("uses a HUMAN session token for company context APIs", () => {
    const companyList = api("agentcici.company.list", "可访问公司列表", "身份与公司", "low", "authenticated HUMAN member");
    companyList.method = "GET";
    companyList.path = "/auth/companies";
    companyList.authType = "Bearer AgentCiCi HUMAN token";

    expect(systemApiRequestPrelude(companyList)).toBe([
      "GET ${SYSTEM_API_ORIGIN}/auth/companies",
      "Authorization: Bearer ${AGENTCICI_USER_TOKEN}",
    ].join("\n"));
  });

  it("keeps content type for the company switch request", () => {
    const companySwitch = api("agentcici.company.switch", "切换当前公司", "身份与公司", "medium", "authenticated HUMAN member");
    companySwitch.path = "/auth/switch-company";
    companySwitch.authType = "Bearer AgentCiCi HUMAN token";

    expect(systemApiRequestPrelude(companySwitch)).toContain("Content-Type: application/json");
  });
});
