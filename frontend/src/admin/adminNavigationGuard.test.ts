import { describe, expect, it, vi } from "vitest";
import {
  confirmAdminNavigation,
  shouldBlockAdminRouteNavigation,
  type AdminNavigationGuard,
} from "./adminNavigationGuard";

describe("admin navigation guard", () => {
  const guard: AdminNavigationGuard = {
    id: 7,
    message: "当前本体草稿尚未安全落库，确认离开？",
  };

  it("allows clean navigation without opening a confirmation", () => {
    const confirm = vi.fn(() => false);

    expect(confirmAdminNavigation(null, confirm)).toBe(true);
    expect(confirm).not.toHaveBeenCalled();
  });

  it("keeps the current page when a guarded navigation is cancelled", () => {
    const confirm = vi.fn(() => false);

    expect(confirmAdminNavigation(guard, confirm)).toBe(false);
    expect(confirm).toHaveBeenCalledWith(guard.message);
  });

  it("allows a guarded navigation only after explicit confirmation", () => {
    expect(confirmAdminNavigation(guard, () => true)).toBe(true);
  });

  it("blocks route and browser-history navigation without trapping auth redirects", () => {
    expect(shouldBlockAdminRouteNavigation(true, "/admin/ontology", "/admin/kb")).toBe(true);
    expect(shouldBlockAdminRouteNavigation(true, "/admin/ontology", "/admin/ontology")).toBe(false);
    expect(shouldBlockAdminRouteNavigation(true, "/admin/ontology", "/admin/login")).toBe(true);
    expect(shouldBlockAdminRouteNavigation(false, "/admin/ontology", "/admin/kb")).toBe(false);
  });
});
