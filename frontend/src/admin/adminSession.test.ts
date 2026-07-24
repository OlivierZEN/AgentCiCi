import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { readAuthPayload, writeAuthPayload } from "../auth/authStorage";
import { LS_ADMIN_TOKEN, LS_ASSISTANT_TOKEN } from "../constants";
import { readCurrentAdminSession } from "./adminSession";

describe("organization admin session", () => {
  const values = new Map<string, string>();
  const storage = {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => values.set(key, value),
    removeItem: (key: string) => values.delete(key),
  };

  beforeEach(() => {
    values.clear();
    vi.stubGlobal("localStorage", storage);
  });

  afterEach(() => vi.unstubAllGlobals());

  it("adopts the current assistant administrator session for the organization console", () => {
    writeAuthPayload(LS_ASSISTANT_TOKEN, {
      token: "assistant-admin-token",
      companyId: "company-a",
      userId: "user-a",
      roles: ["ORG_ADMIN"],
    });

    expect(readCurrentAdminSession()).toMatchObject({
      token: "assistant-admin-token",
      companyId: "company-a",
    });
    expect(readAuthPayload(LS_ADMIN_TOKEN)).toMatchObject({ token: "assistant-admin-token" });
  });

  it("also permits an owner assistant session", () => {
    writeAuthPayload(LS_ASSISTANT_TOKEN, {
      token: "assistant-owner-token",
      companyId: "company-owner",
      userId: "user-owner",
      roles: ["OWNER"],
    });

    expect(readCurrentAdminSession()).toMatchObject({
      token: "assistant-owner-token",
      companyId: "company-owner",
    });
  });

  it("rejects a non-admin assistant session and clears the legacy admin mirror", () => {
    writeAuthPayload(LS_ASSISTANT_TOKEN, {
      token: "assistant-member-token",
      companyId: "company-a",
      userId: "user-a",
      roles: ["ORG_USER"],
    });
    writeAuthPayload(LS_ADMIN_TOKEN, { token: "obsolete-admin-token" });

    expect(readCurrentAdminSession()).toBeNull();
    expect(readAuthPayload(LS_ADMIN_TOKEN)).toBeNull();
  });
});
