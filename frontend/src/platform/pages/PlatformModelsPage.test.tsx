import { describe, expect, it } from "vitest";
import { buildProviderCheckRequest } from "./PlatformModelsPage";

describe("OneKeyToken provider check request", () => {
  it("sends the current unsaved form draft without preserving surrounding whitespace", () => {
    expect(buildProviderCheckRequest(true, " https://my.onekeytoken.com/v1/ ", "  draft-key  ")).toEqual({
      enabled: true,
      apiBaseUrl: "https://my.onekeytoken.com/v1/",
      apiKey: "draft-key",
    });
  });
});
