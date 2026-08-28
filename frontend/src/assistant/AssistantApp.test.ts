import { describe, expect, it } from "vitest";
import { acceptComposerDraft, AI_APPLICATIONS, DEV_AUTOPILOT_URL, isExternalAiApplication } from "./AssistantApp";

describe("AI 应用启动器", () => {
  it("提供 DEV Autopilot 的独立研发交付入口", () => {
    const application = AI_APPLICATIONS.find((item) => item.code === "dev-autopilot");

    expect(application).toMatchObject({
      name: "DEV Autopilot",
      shortName: "研",
      status: "研发交付",
      externalUrl: DEV_AUTOPILOT_URL,
    });
    expect(application).toBeDefined();
    expect(isExternalAiApplication(application!)).toBe(true);
  });
});

describe("工作台消息提交", () => {
  it("接受提交时规范化问题并把下一输入状态置空", () => {
    expect(acceptComposerDraft("  这是内部对话  \n")).toEqual({
      question: "这是内部对话",
      nextInput: "",
    });
  });
});
