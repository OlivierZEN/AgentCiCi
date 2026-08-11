import { describe, expect, it } from "vitest";
import {
  activationStatusNotice,
  canReconcileMemberIdentity,
  memberIdentityDescription,
  memberIdentityLabel,
} from "./AdminUsersPage";

describe("AdminUsersPage identity reconciliation", () => {
  it("labels the governed identity states", () => {
    expect(memberIdentityLabel("MISSING")).toBe("未绑定");
    expect(memberIdentityLabel("PENDING_ACTIVATION")).toBe("等待用户激活");
    expect(memberIdentityLabel("ACTIVE")).toBe("已绑定，可登录");
    expect(memberIdentityLabel("BLOCKED")).toBe("已停用");
  });

  it("offers reconciliation only to active members with a missing identity", () => {
    const base = {
      id: "member-1",
      mobile: "13900000001",
      roleCode: "ORG_ADMIN",
      createdAt: "2026-08-11T00:00:00Z",
    };
    expect(canReconcileMemberIdentity({ ...base, memberStatus: "ACTIVE", identityState: "MISSING" })).toBe(true);
    expect(canReconcileMemberIdentity({ ...base, memberStatus: "PENDING_ACTIVATION", identityState: "MISSING" })).toBe(false);
    expect(canReconcileMemberIdentity({ ...base, memberStatus: "ACTIVE", identityState: "ACTIVE" })).toBe(false);
    expect(canReconcileMemberIdentity({ ...base, memberStatus: "SUSPENDED", identityState: "MISSING" })).toBe(false);
  });

  it("distinguishes a resent invitation from an activated status sync", () => {
    expect(activationStatusNotice("PENDING_ACTIVATION")).toContain("初始化邮件已重新发送");
    expect(activationStatusNotice("ACTIVE")).toContain("成员状态已同步为有效");
  });

  it("describes identity as a member-level login fact", () => {
    expect(memberIdentityDescription("MISSING")).toContain("尚未建立统一登录身份");
    expect(memberIdentityDescription("PENDING_ACTIVATION")).toContain("激活邮件");
    expect(memberIdentityDescription("ACTIVE")).toBe("该成员已具备统一登录身份。");
    expect(memberIdentityDescription("BLOCKED")).toContain("不能使用统一登录");
  });
});
