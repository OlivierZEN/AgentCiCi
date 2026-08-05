const ADMIN_BROWSER_API_ROOT = "/api/admin";

/**
 * Browser API paths must not share the `/admin/...` SPA route namespace.
 * Nginx forwards these paths to the existing protected backend controllers.
 */
export const adminApi = {
  path(path: string) {
    return `${ADMIN_BROWSER_API_ROOT}${path.startsWith("/") ? path : `/${path}`}`;
  },
  users(path = "") {
    return this.path(`/users${path}`);
  },
  servicePrincipals(path = "") {
    return this.path(`/service-principals${path}`);
  },
};
