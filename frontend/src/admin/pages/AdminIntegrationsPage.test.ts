import { describe, expect, it } from "vitest";
import { PLATFORM_LONG_TASK_TIMEOUT_MAX_MS, readIntegrationAppsResponse } from "./AdminIntegrationsPage";

describe("platform integration response handling", () => {
  it("allows governed long-running integrations to configure up to 60 minutes", () => {
    expect(PLATFORM_LONG_TASK_TIMEOUT_MAX_MS).toBe(60 * 60 * 1000);
  });

  it("accepts the managed integration list returned by the backend", async () => {
    const response = new Response(JSON.stringify({
      success: true,
      data: [{ id: 1, appCode: "tavily", appName: "Tavily" }],
    }), {
      status: 200,
      headers: { "content-type": "application/json;charset=UTF-8" },
    });

    await expect(readIntegrationAppsResponse(response)).resolves.toHaveLength(1);
  });

  it("keeps the platform-managed code interpreter card in the returned catalog", async () => {
    const response = new Response(JSON.stringify({
      success: true,
      data: [{
        id: 3,
        appCode: "code_interpreter",
        appName: "代码解释器",
        description: "受管 Python 沙箱",
        enabled: false,
        config: { apiKey: "", model: "qwen3.5-plus" },
        configKeys: ["apiKey", "apiBaseUrl", "model", "timeoutMs", "maxInputChars"],
        builtin: true,
      }],
    }), {
      status: 200,
      headers: { "content-type": "application/json;charset=UTF-8" },
    });

    await expect(readIntegrationAppsResponse(response)).resolves.toEqual([
      expect.objectContaining({ appCode: "code_interpreter", appName: "代码解释器" }),
    ]);
  });

  it("keeps managed web search and extractor as two independent cards", async () => {
    const response = new Response(JSON.stringify({
      success: true,
      data: [
        { id: 4, appCode: "managed_web_search", appName: "联网搜索（百炼）", enabled: false },
        { id: 5, appCode: "managed_web_extractor", appName: "网页抓取（百炼）", enabled: false },
      ],
    }), {
      status: 200,
      headers: { "content-type": "application/json;charset=UTF-8" },
    });

    await expect(readIntegrationAppsResponse(response)).resolves.toEqual([
      expect.objectContaining({ appCode: "managed_web_search", enabled: false }),
      expect.objectContaining({ appCode: "managed_web_extractor", enabled: false }),
    ]);
  });

  it("reports an actionable error instead of silently rendering an empty page for SPA HTML", async () => {
    const response = new Response("<!doctype html><html></html>", {
      status: 200,
      headers: { "content-type": "text/html" },
    });

    await expect(readIntegrationAppsResponse(response)).rejects.toThrow("非 JSON 响应");
  });
});
