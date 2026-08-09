import { describe, expect, it } from "vitest";
import { adminApi } from "./adminApi";

describe("admin browser API paths", () => {
  it("keeps every browser management API outside the SPA route namespace", () => {
    expect(adminApi.users()).toBe("/api/admin/users");
    expect(adminApi.users("/member-1/activation-email")).toBe("/api/admin/users/member-1/activation-email");
    expect(adminApi.servicePrincipals()).toBe("/api/admin/service-principals");
    expect(adminApi.servicePrincipals("/principal-1/rotate-secret")).toBe("/api/admin/service-principals/principal-1/rotate-secret");
    expect(adminApi.devAutopilotTeam()).toBe("/api/admin/devautopilot/team");
    expect(adminApi.devAutopilotTeam("/developers")).toBe("/api/admin/devautopilot/team/developers");
    expect(adminApi.path("company/profile")).toBe("/api/admin/company/profile");
    expect(adminApi.path("/billing/overview")).toBe("/api/admin/billing/overview");
    expect(adminApi.path("/ontologies/42")).toBe("/api/admin/ontologies/42");
  });
});
