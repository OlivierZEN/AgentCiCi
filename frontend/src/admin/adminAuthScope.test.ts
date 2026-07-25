// @ts-expect-error Vitest executes this test in Node; production sources do not depend on Node types.
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import {
  createAdminAuthScopeKey,
  isAdminAsyncRequestCurrent,
} from "./adminAuthScope";

describe("admin auth async scope", () => {
  it("invalidates a response when either company, token or request changes", () => {
    const scope = createAdminAuthScopeKey("org-a", "token-a");

    expect(scope).not.toBe(createAdminAuthScopeKey("org-b", "token-a"));
    expect(scope).not.toBe(createAdminAuthScopeKey("org-a", "token-b"));
    expect(isAdminAsyncRequestCurrent(scope, 3, scope, 3)).toBe(true);
    expect(isAdminAsyncRequestCurrent(scope, 3, createAdminAuthScopeKey("org-b", "token-a"), 3)).toBe(false);
    expect(isAdminAsyncRequestCurrent(scope, 3, scope, 4)).toBe(false);
  });

  it("guards both admin profile loaders and reloads ontology lists for an org-only switch", () => {
    const shellSource = readFileSync(new URL("./AdminShell.tsx", import.meta.url), "utf8");
    const ontologySource = readFileSync(new URL("./pages/AdminOntologyPage.tsx", import.meta.url), "utf8");

    expect(shellSource).toContain("profileRequestIdRef");
    expect(shellSource).toContain("companyRequestIdRef");
    expect(shellSource).toContain("isAdminAsyncRequestCurrent");
    expect(shellSource).toContain("invalidateAdminAuthRequests");
    expect(ontologySource).toMatch(/useEffect\(\(\) => \{\s*void loadWorkspaces\(\);\s*void loadReferencePackages\(\);\s*\}, \[authScopeKey,/);
  });

  it("keeps the Semattice entry in the organization console behind the signed current-admin session", () => {
    const shellSource = readFileSync(new URL("./AdminShell.tsx", import.meta.url), "utf8");
    const stylesSource = readFileSync(new URL("../styles.css", import.meta.url), "utf8");

    expect(shellSource).toContain('"/auth/semattice/console"');
    expect(shellSource).toContain('target.hash.startsWith("#oact=")');
    expect(shellSource).toContain('window.location.assign(target.toString())');
    expect(shellSource).toContain("Semattice 管理端");
    expect(shellSource).not.toContain("cici_semattice_token");
    expect(stylesSource).toMatch(/\.admin-product-switch__menu\s*\{[^}]*left:\s*0;/s);
    expect(stylesSource).not.toMatch(/\.admin-product-switch__menu\s*\{[^}]*right:\s*0;/s);
  });
});
