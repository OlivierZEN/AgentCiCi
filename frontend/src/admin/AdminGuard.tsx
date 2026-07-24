import { useEffect, useState } from "react";
import { Navigate, Outlet, useLocation } from "react-router-dom";
import { authFetch, clearAuthPayload } from "../auth/authStorage";
import { useAuthStorageSync } from "../auth/useAuthStorageSync";
import { LS_ADMIN_TOKEN } from "../constants";
import { safeFetchJson } from "../utils/http";
import { hasOrganizationAdminRole, readCurrentAdminSession, type OrganizationSession } from "./adminSession";

export default function AdminGuard() {
  const loc = useLocation();
  const [state, setState] = useState<"loading" | "ok" | "denied">(() =>
    readCurrentAdminSession()?.token ? "loading" : "denied",
  );
  const [authVersion, setAuthVersion] = useState(0);

  useAuthStorageSync<OrganizationSession>(LS_ADMIN_TOKEN, (payload) => {
    setState(payload?.token ? "loading" : "denied");
    setAuthVersion((current) => current + 1);
  });

  useEffect(() => {
    const auth = readCurrentAdminSession();
    if (!auth?.token) {
      setState("denied");
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        const r = await authFetch(LS_ADMIN_TOKEN, "/auth/me", {}, {
          onUnauthorized: () => clearAuthPayload(LS_ADMIN_TOKEN),
        });
        const { body } = await safeFetchJson<{ roles?: string[] }>(r);
        const roles = (body?.data?.roles ?? []) as string[];
        if (cancelled) return;
        if (r.ok && hasOrganizationAdminRole(roles)) {
          setState("ok");
          return;
        }
        clearAuthPayload(LS_ADMIN_TOKEN);
        setState("denied");
      } catch {
        if (cancelled) return;
        clearAuthPayload(LS_ADMIN_TOKEN);
        setState("denied");
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [loc.pathname, authVersion]);

  if (state === "denied") {
    return <Navigate to="/app" replace state={{ from: loc.pathname, adminAccessDenied: true }} />;
  }
  if (state === "loading") {
    return (
      <main className="login-root">
        <p className="subtle">校验管理权限...</p>
      </main>
    );
  }
  return <Outlet />;
}
