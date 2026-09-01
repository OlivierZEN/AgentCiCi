export const OIDC_LOGOUT_PATH = "/auth/oidc/logout";

export function startOidcLogout(navigate: (path: string) => void = (path) => window.location.assign(path)): void {
  navigate(OIDC_LOGOUT_PATH);
}
