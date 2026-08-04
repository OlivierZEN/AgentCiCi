import { beforeEach, describe, expect, it, vi } from "vitest";
import { authFetch } from "../auth/authStorage";
import { LS_ASSISTANT_TOKEN } from "../constants";
import { requestAiTable } from "./AiTableBusinessObjectList";

vi.mock("../auth/authStorage", () => ({
  authFetch: vi.fn(),
}));

const authFetchMock = vi.mocked(authFetch);

describe("requestAiTable", () => {
  beforeEach(() => {
    authFetchMock.mockReset();
  });

  it("uses the workbench Bearer session for protected business-data APIs", async () => {
    authFetchMock.mockResolvedValue(new Response(JSON.stringify({
      success: true,
      data: { objects: [] },
      message: "ok",
    }), { status: 200, headers: { "Content-Type": "application/json" } }));
    const controller = new AbortController();

    await expect(requestAiTable<{ objects: unknown[] }>("/ai-table/catalog", controller.signal))
      .resolves.toEqual({ objects: [] });

    expect(authFetchMock).toHaveBeenCalledWith(LS_ASSISTANT_TOKEN, "/ai-table/catalog", {
      credentials: "same-origin",
      signal: controller.signal,
    });
  });

  it("keeps protected API errors visible to the list state", async () => {
    authFetchMock.mockResolvedValue(new Response(JSON.stringify({
      success: false,
      message: "Authentication required",
    }), { status: 401, headers: { "Content-Type": "application/json" } }));

    await expect(requestAiTable("/ai-table/catalog")).rejects.toThrow("Authentication required");
  });
});
