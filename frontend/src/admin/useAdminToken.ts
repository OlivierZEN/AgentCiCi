import { useOutletContext } from "react-router-dom";

export type AdminOutletContext = { token: string };

export function useAdminToken(): string {
  return useOutletContext<AdminOutletContext>().token;
}
