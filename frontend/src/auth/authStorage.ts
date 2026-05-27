export type AuthPayload = {
  token?: unknown;
  [key: string]: unknown;
};

type AuthFetchOptions = {
  onUnauthorized?: () => void;
};

function getLocalStorage(): Storage | null {
  try {
    return globalThis.localStorage ?? null;
  } catch {
    return null;
  }
}

export function readAuthPayload<T extends AuthPayload = AuthPayload>(storageKey: string): T | null {
  const storage = getLocalStorage();
  if (!storage) {
    return null;
  }
  const raw = storage.getItem(storageKey);
  if (!raw) {
    return null;
  }
  try {
    const parsed = JSON.parse(raw) as T;
    return parsed && typeof parsed === "object" ? parsed : null;
  } catch {
    return null;
  }
}

export function readAuthToken(storageKey: string): string {
  const token = readAuthPayload(storageKey)?.token;
  return typeof token === "string" ? token.trim() : "";
}

export function writeAuthPayload(storageKey: string, payload: AuthPayload): void {
  getLocalStorage()?.setItem(storageKey, JSON.stringify(payload));
}

export function clearAuthPayload(storageKey: string): void {
  getLocalStorage()?.removeItem(storageKey);
}

function mergeAuthHeaders(headers: HeadersInit | undefined, token: string): Headers {
  const next = new Headers(headers);
  if (token) {
    next.set("Authorization", `Bearer ${token}`);
  } else {
    next.delete("Authorization");
  }
  return next;
}

export async function authFetch(
  storageKey: string,
  input: RequestInfo | URL,
  init: RequestInit = {},
  options: AuthFetchOptions = {},
): Promise<Response> {
  const tokenUsed = readAuthToken(storageKey);
  const firstResponse = await fetch(input, {
    ...init,
    headers: mergeAuthHeaders(init.headers, tokenUsed),
  });
  if (firstResponse.status !== 401) {
    return firstResponse;
  }

  const latestToken = readAuthToken(storageKey);
  if (latestToken && latestToken !== tokenUsed) {
    const retryResponse = await fetch(input, {
      ...init,
      headers: mergeAuthHeaders(init.headers, latestToken),
    });
    if (retryResponse.status === 401) {
      options.onUnauthorized?.();
    }
    return retryResponse;
  }

  options.onUnauthorized?.();
  return firstResponse;
}

export function handleAuthStorageChange<T extends AuthPayload = AuthPayload>(
  storageKey: string,
  callback: (payload: T | null) => void,
): (event: StorageEvent) => void {
  return (event: StorageEvent) => {
    if (event.storageArea && event.storageArea !== getLocalStorage()) {
      return;
    }
    if (event.key !== storageKey) {
      return;
    }
    callback(readAuthPayload<T>(storageKey));
  };
}
