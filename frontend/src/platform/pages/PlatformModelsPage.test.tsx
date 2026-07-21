import { describe, expect, it } from "vitest";
import { buildProviderCheckRequest, catalogEmptyMessage } from "./PlatformModelsPage";

describe("OneKeyToken provider check request", () => {
  it("sends the current unsaved form draft without preserving surrounding whitespace", () => {
    expect(buildProviderCheckRequest(true, " https://my.onekeytoken.com/v1/ ", "  draft-key  ")).toEqual({
      enabled: true,
      apiBaseUrl: "https://my.onekeytoken.com/v1/",
      apiKey: "draft-key",
    });
  });
});

describe("provider catalog capability", () => {
  it("does not relabel an unavailable remote catalog as a preset model list", () => {
    expect(catalogEmptyMessage("unavailable", 0)).toBe("当前厂商未开放远程模型枚举，暂无可选模型。");
  });
});
