import { describe, expect, it } from "vitest";
import { shouldAutoStartOidcLogin } from "./oidcAutoRedirect";

const guestInput = {
  hasAuth: false,
  authStatus: "guest" as const,
  loginSubmitting: false,
  redirectAttempted: false,
  search: "",
};

describe("OIDC automatic redirect", () => {
  it("starts the unified login flow for a normal guest session", () => {
    expect(shouldAutoStartOidcLogin(guestInput)).toBe(true);
  });

  it.each(["?oidc_ticket=one-time", "?ssoTicket=cloudcc", "?ccSsoTicket=cloudcc"])(
    "does not replace a callback flow with an automatic redirect: %s",
    (search) => {
      expect(shouldAutoStartOidcLogin({ ...guestInput, search })).toBe(false);
    },
  );

  it("does not repeat a redirect or interrupt an existing session", () => {
    expect(shouldAutoStartOidcLogin({ ...guestInput, redirectAttempted: true })).toBe(false);
    expect(shouldAutoStartOidcLogin({ ...guestInput, loginSubmitting: true })).toBe(false);
    expect(shouldAutoStartOidcLogin({ ...guestInput, hasAuth: true })).toBe(false);
    expect(shouldAutoStartOidcLogin({ ...guestInput, authStatus: "checking" })).toBe(false);
  });
});
