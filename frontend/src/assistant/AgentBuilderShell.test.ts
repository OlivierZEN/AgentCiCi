import { describe, expect, it } from "vitest";
import { MODEL_CONFIG_REQUIRED_NOTICE, resolveAgentCreationModel, type BaseModelOption } from "./AgentBuilderShell";

const modelOptions: BaseModelOption[] = [
  { value: "qwen3.6-plus", label: "qwen3.6-plus · Bailian", note: "Bailian" },
];

describe("resolveAgentCreationModel", () => {
  it("requires model configuration when no draft model or fallback model exists", () => {
    expect(resolveAgentCreationModel("", [])).toEqual({ model: "", requiresModelConfig: true });
    expect(MODEL_CONFIG_REQUIRED_NOTICE).toBe("请先配置模型");
  });

  it("uses the first available base model for new Agent creation", () => {
    expect(resolveAgentCreationModel("", modelOptions)).toEqual({ model: "qwen3.6-plus", requiresModelConfig: false });
  });

  it("preserves an existing draft model", () => {
    expect(resolveAgentCreationModel("deepseek-chat", modelOptions)).toEqual({
      model: "deepseek-chat",
      requiresModelConfig: false,
    });
  });
});
