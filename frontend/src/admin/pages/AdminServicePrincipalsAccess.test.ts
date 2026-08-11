import { describe, expect, it } from "vitest";
import { buildDevAutopilotAccessRoleRequest } from "./AdminServicePrincipalsPage";

describe("DevAutopilot application role request", () => {
  it("excludes automatic tenant administrators and governance owners", () => {
    const common = { publicId: "U1", displayName: "Member", memberRole: "ORG_USER", explicitRole: null, effectiveRole: "NONE", governanceOwner: false };
    expect(buildDevAutopilotAccessRoleRequest([
      { ...common, memberId: "admin", memberRole: "ORG_ADMIN", source: "TENANT_ADMIN" },
      { ...common, memberId: "owner", source: "GOVERNANCE_OWNER", governanceOwner: true },
      { ...common, memberId: "contributor", source: "EXPLICIT", explicitRole: "VIEWER", effectiveRole: "VIEWER" },
      { ...common, memberId: "none", source: "NONE" },
    ], { admin: "VIEWER", owner: "VIEWER", contributor: "CONTRIBUTOR", none: "" })).toEqual([
      { memberId: "contributor", roleCode: "CONTRIBUTOR" },
    ]);
  });
});
