export type OidcAutoRedirectInput = {
  hasAuth: boolean;
  authStatus: "checking" | "authenticated" | "guest";
  loginSubmitting: boolean;
  redirectAttempted: boolean;
  search: string;
};

/**
 * OIDC and CloudCC completion tickets must be consumed by their dedicated flows.
 * A normal guest session, however, should proceed directly to the unified IdP.
 */
export function shouldAutoStartOidcLogin(input: OidcAutoRedirectInput): boolean {
  if (input.hasAuth || input.authStatus !== "guest" || input.loginSubmitting || input.redirectAttempted) {
    return false;
  }

  const params = new URLSearchParams(input.search);
  return !params.get("oidc_ticket")?.trim()
    && !params.get("ssoTicket")?.trim()
    && !params.get("ccSsoTicket")?.trim();
}
