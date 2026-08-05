// @ts-expect-error Vitest executes this contract test in Node; production code does not depend on Node types.
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

describe("个人档案统一密码入口", () => {
  const source = readFileSync(new URL("./MyEmailAccountsModal.tsx", import.meta.url), "utf8");

  it("starts the Keycloak password action instead of updating the legacy local credential", () => {
    expect(source).toContain("/auth/oidc/password?return_to=${encodeURIComponent(returnTo)}");
    expect(source).not.toContain('fetchJson<{ updated: boolean }>("/auth/me/password"');
    expect(source).toContain("前往统一账号中心修改密码");
  });
});
