import { describe, expect, it, vi } from "vitest";
import {
  confirmAdminNavigation,
  shouldGuardAdminNavigationClick,
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

  it("guards same-tab primary clicks but leaves modified links alone", () => {
    const primaryClick = { button: 0, metaKey: false, ctrlKey: false, shiftKey: false, altKey: false };

    expect(shouldGuardAdminNavigationClick(primaryClick, "/admin/ontology", "/admin/kb")).toBe(true);
    expect(shouldGuardAdminNavigationClick(primaryClick, "/admin/ontology", "/admin/ontology")).toBe(false);
    expect(shouldGuardAdminNavigationClick({ ...primaryClick, metaKey: true }, "/admin/ontology", "/admin/kb")).toBe(false);
    expect(shouldGuardAdminNavigationClick({ ...primaryClick, button: 1 }, "/admin/ontology", "/admin/kb")).toBe(false);
  });
});
