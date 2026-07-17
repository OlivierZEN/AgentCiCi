import { useEffect } from "react";
import { useOutletContext } from "react-router-dom";

export type AdminOutletContext = {
  token: string;
  registerNavigationGuard: (message: string) => () => void;
};

export function useAdminToken(): string {
  return useOutletContext<AdminOutletContext>().token;
}

export function useAdminNavigationGuard(active: boolean, message: string): void {
  const { registerNavigationGuard } = useOutletContext<AdminOutletContext>();

  useEffect(() => {
    if (!active) return undefined;
    return registerNavigationGuard(message);
  }, [active, message, registerNavigationGuard]);
}
