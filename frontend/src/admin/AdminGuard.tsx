import { useEffect, useState } from "react";
import { Navigate, Outlet, useLocation } from "react-router-dom";
import { authFetch, clearAuthPayload, readAuthPayload } from "../auth/authStorage";
import { useAuthStorageSync } from "../auth/useAuthStorageSync";
import { LS_ADMIN_TOKEN } from "../constants";
import { safeFetchJson } from "../utils/http";

type AuthPayload = { token: string; roles?: string[] };

function hasOrgAdminRole(roles: string[]): boolean {
  return roles.includes("OWNER") || roles.includes("ORG_ADMIN");
}

function readAdminAuth(): AuthPayload | null {
  return readAuthPayload<AuthPayload>(LS_ADMIN_TOKEN);
}

export default function AdminGuard() {
  const loc = useLocation();
  const [state, setState] = useState<"loading" | "ok" | "denied">(() =>
    readAdminAuth()?.token ? "loading" : "denied",
  );
  const [authVersion, setAuthVersion] = useState(0);

  useAuthStorageSync<AuthPayload>(LS_ADMIN_TOKEN, (payload) => {
    setState(payload?.token ? "loading" : "denied");
    setAuthVersion((current) => current + 1);
  });

  useEffect(() => {
    const auth = readAdminAuth();
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
        if (r.ok && hasOrgAdminRole(roles)) {
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
    return <Navigate to="/admin/login" replace state={{ from: loc.pathname }} />;
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
