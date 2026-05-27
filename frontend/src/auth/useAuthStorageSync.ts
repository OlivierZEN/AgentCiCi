import { useEffect } from "react";
import { handleAuthStorageChange, type AuthPayload } from "./authStorage";

export function useAuthStorageSync<T extends AuthPayload>(
  storageKey: string,
  onChange: (payload: T | null) => void,
): void {
  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }
    const listener = handleAuthStorageChange<T>(storageKey, onChange);
    window.addEventListener("storage", listener);
    return () => window.removeEventListener("storage", listener);
  }, [storageKey, onChange]);
}
