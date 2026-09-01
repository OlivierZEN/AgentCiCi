import { describe, expect, it, vi } from "vitest";
import { OIDC_LOGOUT_PATH, startOidcLogout } from "./oidcLogout";

describe("OIDC logout", () => {
  it("navigates through the same-origin server logout endpoint", () => {
    const navigate = vi.fn();

    startOidcLogout(navigate);

    expect(navigate).toHaveBeenCalledWith(OIDC_LOGOUT_PATH);
    expect(OIDC_LOGOUT_PATH).toBe("/auth/oidc/logout");
  });
});
