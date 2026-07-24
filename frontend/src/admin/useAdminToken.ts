import { useEffect } from "react";
import { useOutletContext } from "react-router-dom";

export type AdminOutletContext = {
  token: string;
  companyId: string;
  userId: string;
  registerNavigationGuard: (message: string) => () => void;
};

export type AdminAuthScope = { token: string; companyId: string; userId: string };

export function useAdminToken(): string {
  return useOutletContext<AdminOutletContext>().token;
}

export function useAdminAuthScope(): AdminAuthScope {
  const { token, companyId, userId } = useOutletContext<AdminOutletContext>();
  return { token, companyId, userId };
}

export function useAdminNavigationGuard(active: boolean, message: string): void {
  const { registerNavigationGuard } = useOutletContext<AdminOutletContext>();

  useEffect(() => {
    if (!active) return undefined;
    return registerNavigationGuard(message);
  }, [active, message, registerNavigationGuard]);
}
