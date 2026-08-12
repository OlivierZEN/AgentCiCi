import { describe, expect, it } from "vitest";
import { PLATFORM_INTEGRATIONS_API_BASE } from "./PlatformIntegrationsPage";

describe("platform integrations API route", () => {
  it("uses the same-origin backend API namespace", () => {
    expect(PLATFORM_INTEGRATIONS_API_BASE).toBe("/api/platform/integrations");
  });
});
