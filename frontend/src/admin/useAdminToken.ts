import { useEffect } from "react";
import { useOutletContext } from "react-router-dom";

export type AdminOutletContext = {
  token: string;
  orgId: string;
  registerNavigationGuard: (message: string) => () => void;
};

export type AdminAuthScope = { token: string; orgId: string };

export function useAdminToken(): string {
  return useOutletContext<AdminOutletContext>().token;
}

export function useAdminAuthScope(): AdminAuthScope {
  const { token, orgId } = useOutletContext<AdminOutletContext>();
  return { token, orgId };
}

export function useAdminNavigationGuard(active: boolean, message: string): void {
  const { registerNavigationGuard } = useOutletContext<AdminOutletContext>();

  useEffect(() => {
    if (!active) return undefined;
    return registerNavigationGuard(message);
  }, [active, message, registerNavigationGuard]);
}
