import { describe, expect, it } from "vitest";
import { adminApi } from "./adminApi";

describe("admin browser API paths", () => {
  it("keeps user and machine-principal calls outside the SPA route namespace", () => {
    expect(adminApi.users()).toBe("/api/admin/users");
    expect(adminApi.users("/member-1/activation-email")).toBe("/api/admin/users/member-1/activation-email");
    expect(adminApi.servicePrincipals()).toBe("/api/admin/service-principals");
    expect(adminApi.servicePrincipals("/principal-1/rotate-secret")).toBe("/api/admin/service-principals/principal-1/rotate-secret");
  });
});
