import { describe, expect, it } from "vitest";
import { readIntegrationAppsResponse } from "./AdminIntegrationsPage";

describe("platform integration response handling", () => {
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

  it("reports an actionable error instead of silently rendering an empty page for SPA HTML", async () => {
    const response = new Response("<!doctype html><html></html>", {
      status: 200,
      headers: { "content-type": "text/html" },
    });

    await expect(readIntegrationAppsResponse(response)).rejects.toThrow("非 JSON 响应");
  });
});
