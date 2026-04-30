import { useEffect, useState } from "react";
import { Navigate, Outlet, useLocation } from "react-router-dom";
import { LS_PLATFORM_TOKEN } from "../constants";
import { safeFetchJson } from "../utils/http";

type AuthPayload = { token: string; roles?: string[] };

function readPlatformAuth(): AuthPayload | null {
  const raw = localStorage.getItem(LS_PLATFORM_TOKEN);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as AuthPayload;
  } catch {
    return null;
  }
}

function hasPlatformRole(roles: string[]): boolean {
  return roles.some((role) => role.startsWith("PLATFORM_"));
}

export default function PlatformGuard() {
  const loc = useLocation();
  const [state, setState] = useState<"loading" | "ok" | "denied">(() =>
    readPlatformAuth()?.token ? "loading" : "denied",
  );

  useEffect(() => {
    const auth = readPlatformAuth();
    if (!auth?.token) {
      setState("denied");
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        const r = await fetch("/auth/me", { headers: { Authorization: `Bearer ${auth.token}` } });
        const { body } = await safeFetchJson<{ roles?: string[] }>(r);
        const roles = (body?.data?.roles ?? []) as string[];
        if (cancelled) return;
        if (r.ok && hasPlatformRole(roles)) {
          setState("ok");
          return;
        }
        localStorage.removeItem(LS_PLATFORM_TOKEN);
        setState("denied");
      } catch {
        if (cancelled) return;
        localStorage.removeItem(LS_PLATFORM_TOKEN);
        setState("denied");
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [loc.pathname]);

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
