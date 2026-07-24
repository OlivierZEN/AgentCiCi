import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  authFetch,
  clearAuthPayload,
  handleAuthStorageChange,
  readAuthPayload,
  readAuthToken,
  writeAuthPayload,
} from "./authStorage";

class MemoryStorage implements Storage {
  private values = new Map<string, string>();

  get length() {
    return this.values.size;
  }

  clear() {
    this.values.clear();
  }

  getItem(key: string) {
    return this.values.get(key) ?? null;
  }

  key(index: number) {
    return Array.from(this.values.keys())[index] ?? null;
  }

  removeItem(key: string) {
    this.values.delete(key);
  }

  setItem(key: string, value: string) {
    this.values.set(key, value);
  }
}

const storage = new MemoryStorage();

beforeEach(() => {
  Object.defineProperty(globalThis, "localStorage", { value: storage, configurable: true });
  storage.clear();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("authStorage", () => {
  it("parses auth payloads and trims tokens", () => {
    writeAuthPayload("auth", { token: " abc ", companyId: "o1" });

    expect(readAuthToken("auth")).toBe("abc");
    expect(readAuthPayload("auth")).toEqual({ token: " abc ", companyId: "o1" });
  });

  it("tolerates missing and malformed payloads", () => {
    localStorage.setItem("broken", "{");

    expect(readAuthToken("missing")).toBe("");
    expect(readAuthPayload("broken")).toBeNull();
  });

  it("clears auth payloads", () => {
    writeAuthPayload("auth", { token: "abc" });
    clearAuthPayload("auth");

    expect(readAuthPayload("auth")).toBeNull();
  });

  it("retries a 401 once when another tab has written a newer token", async () => {
    writeAuthPayload("auth", { token: "old" });
    const fetchMock = vi
      .fn()
      .mockImplementationOnce(async () => {
        writeAuthPayload("auth", { token: "new" });
        return new Response("unauthorized", { status: 401 });
      })
      .mockResolvedValueOnce(new Response("ok", { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    const response = await authFetch("auth", "/api/data", { headers: { "Content-Type": "application/json" } });

    expect(response.status).toBe(200);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[0][1].headers.get("Authorization")).toBe("Bearer old");
    expect(fetchMock.mock.calls[1][1].headers.get("Authorization")).toBe("Bearer new");
  });

  it("does not retry 401 responses when the token has not changed", async () => {
    writeAuthPayload("auth", { token: "old" });
    const onUnauthorized = vi.fn();
    const fetchMock = vi.fn().mockResolvedValue(new Response("unauthorized", { status: 401 }));
    vi.stubGlobal("fetch", fetchMock);

    const response = await authFetch("auth", "/api/data", {}, { onUnauthorized });

    expect(response.status).toBe(401);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it("notifies only for matching storage keys", () => {
    const callback = vi.fn();
    const listener = handleAuthStorageChange("auth", callback);
    writeAuthPayload("auth", { token: "next" });

    listener({ key: "other", storageArea: localStorage } as StorageEvent);
    listener({ key: "auth", storageArea: localStorage } as StorageEvent);

    expect(callback).toHaveBeenCalledTimes(1);
    expect(callback).toHaveBeenCalledWith({ token: "next" });
  });
});
