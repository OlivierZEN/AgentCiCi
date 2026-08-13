import { describe, expect, it } from "vitest";
import {
  buildProviderCheckRequest,
  capabilityConfirmationError,
  catalogEmptyMessage,
  modelApiFailureMessage,
  readResolvedModel,
  readValidatedModel,
} from "./PlatformModelsPage";

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

  it("offers only the model confirmed by a successful provider check", () => {
    expect(readValidatedModel({ validatedModel: " onekeytoken/auto " })).toBe("onekeytoken/auto");
    expect(readValidatedModel({ validatedModel: 42 })).toBe("");
    expect(readValidatedModel(null)).toBe("");
  });

  it("keeps the gateway-resolved model as diagnostic information", () => {
    expect(readResolvedModel({ resolvedModel: " qwen3.5-flash " })).toBe("qwen3.5-flash");
    expect(readResolvedModel({ resolvedModel: 42 })).toBe("");
  });
});

describe("manual capability confirmation", () => {
  it("requires only one or more selected capabilities", () => {
    expect(capabilityConfirmationError([])).toBe("请至少选择一项模型能力。");
    expect(capabilityConfirmationError(["text", "reasoning"])).toBe("");
  });

  it("turns a gateway HTML response into an actionable confirmation error", () => {
    expect(modelApiFailureMessage(405, undefined, "<html><h1>405 Not Allowed</h1></html>", "确认模型能力失败"))
      .toBe("确认模型能力失败：服务未返回预期数据（HTTP 405）。请刷新页面并确认前后端版本一致后重试。");
  });
});
