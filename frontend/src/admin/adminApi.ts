const ADMIN_BROWSER_API_ROOT = "/api/admin";

/**
 * Browser API paths must not share the `/admin/...` SPA route namespace.
 * Nginx forwards these paths to the existing protected backend controllers.
 */
export const adminApi = {
  users(path = "") {
    return `${ADMIN_BROWSER_API_ROOT}/users${path}`;
  },
  servicePrincipals(path = "") {
    return `${ADMIN_BROWSER_API_ROOT}/service-principals${path}`;
  },
};
