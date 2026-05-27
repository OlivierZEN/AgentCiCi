import { useEffect, useState } from "react";
import { Navigate, Outlet, useLocation } from "react-router-dom";
import { authFetch, clearAuthPayload, readAuthPayload } from "../auth/authStorage";
import { useAuthStorageSync } from "../auth/useAuthStorageSync";
import { LS_PLATFORM_TOKEN } from "../constants";
import { safeFetchJson } from "../utils/http";

type AuthPayload = { token: string; roles?: string[] };

function readPlatformAuth(): AuthPayload | null {
  return readAuthPayload<AuthPayload>(LS_PLATFORM_TOKEN);
}

function hasPlatformRole(roles: string[]): boolean {
  return roles.some((role) => role.startsWith("PLATFORM_"));
}

export default function PlatformGuard() {
  const loc = useLocation();
  const [state, setState] = useState<"loading" | "ok" | "denied">(() =>
    readPlatformAuth()?.token ? "loading" : "denied",
  );
  const [authVersion, setAuthVersion] = useState(0);

  useAuthStorageSync<AuthPayload>(LS_PLATFORM_TOKEN, (payload) => {
    setState(payload?.token ? "loading" : "denied");
    setAuthVersion((current) => current + 1);
  });

  useEffect(() => {
    const auth = readPlatformAuth();
    if (!auth?.token) {
      setState("denied");
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        const r = await authFetch(LS_PLATFORM_TOKEN, "/auth/platform/me", {}, {
          onUnauthorized: () => clearAuthPayload(LS_PLATFORM_TOKEN),
        });
        const { body } = await safeFetchJson<{ roles?: string[] }>(r);
        const roles = (body?.data?.roles ?? []) as string[];
        if (cancelled) return;
        if (r.ok && hasPlatformRole(roles)) {
          setState("ok");
          return;
        }
        clearAuthPayload(LS_PLATFORM_TOKEN);
        setState("denied");
      } catch {
        if (cancelled) return;
        clearAuthPayload(LS_PLATFORM_TOKEN);
        setState("denied");
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [loc.pathname, authVersion]);

  if (state === "denied") {
    return <Navigate to="/platform/login" replace state={{ from: loc.pathname }} />;
  }
  if (state === "loading") {
    return (
      <main className="login-root">
        <p className="subtle">校验平台权限...</p>
      </main>
    );
  }
  return <Outlet />;
}
