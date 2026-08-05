import { describe, expect, it } from "vitest";
import { servicePrincipalPresentation } from "./AdminServicePrincipalsPage";

describe("machine principal presentation", () => {
  it("presents lifecycle status without treating a suspended principal as rotatable", () => {
    expect(servicePrincipalPresentation.statusLabel("ACTIVE")).toBe("有效");
    expect(servicePrincipalPresentation.statusLabel("SUSPENDED")).toBe("已暂停");
    expect(servicePrincipalPresentation.canRotate("ACTIVE")).toBe(true);
    expect(servicePrincipalPresentation.canRotate("SUSPENDED")).toBe(false);
  });

  it("uses the governed human owner display name when present", () => {
    expect(servicePrincipalPresentation.ownerLabel({
      principalId: "sp-1",
      publicId: "SERVICE-1",
      displayName: "后羿",
      principalType: "SERVICE",
      lifecycleStatus: "ACTIVE",
      serviceKind: "DEVELOPER",
      clientId: "dev-autopilot-developer-houyi",
      ownerDisplayName: "Oliver",
    })).toBe("Oliver");
  });
});
