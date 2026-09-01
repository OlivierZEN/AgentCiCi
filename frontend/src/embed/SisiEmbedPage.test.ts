import { describe, expect, it } from "vitest";
import { exactConfirmation } from "./sisiEmbedContract";
import { streamDeltaText } from "./sisiEmbedStream";
import { sisiPhaseLabel, sisiVisibleBusyLabel } from "./sisiEmbedPhase";
import { resolveSisiTheme } from "./sisiEmbedTheme";

describe("Sisi embedded theme", () => {
  it("uses CRM standard blue for the public website float", () => {
    expect(resolveSisiTheme("float", null)).toBe("crm-blue");
    expect(resolveSisiTheme("float", { source: "website", themeCode: "gilded" })).toBe("crm-blue");
  });

  it("preserves the configured theme for trusted page embedding", () => {
    expect(resolveSisiTheme("page", { source: "cloudcc", themeCode: "ocean" })).toBe("ocean");
    expect(resolveSisiTheme("page", { source: "cloudcc" })).toBe("gilded");
  });
});

describe("Sisi embedded confirmation boundary", () => {
  it("only extracts an exact server-provided confirmation phrase", () => {
    expect(exactConfirmation("这是高风险操作，请明确回复“确认写入客户记录”后继续。"))
      .toBe("确认写入客户记录");
    expect(exactConfirmation("确认口令：\"确认删除测试记录\""))
      .toBe("确认删除测试记录");
  });

  it("does not invent a confirmation action from generic warning copy", () => {
    expect(exactConfirmation("此操作风险较高，请确认后继续。"))
      .toBe("");
    expect(exactConfirmation("已经完成写入。"))
      .toBe("");
  });
});

describe("Sisi embedded stream delta contract", () => {
  it("reads the backend canonical text field", () => {
    expect(streamDeltaText({ text: "## AgentCiCi 为企业售前" }))
      .toBe("## AgentCiCi 为企业售前");
  });

  it("keeps compatibility with plain, content, and delta payloads", () => {
    expect(streamDeltaText("直接文本")).toBe("直接文本");
    expect(streamDeltaText({ content: "兼容 content" })).toBe("兼容 content");
    expect(streamDeltaText({ delta: "兼容 delta" })).toBe("兼容 delta");
    expect(streamDeltaText({})).toBe("");
  });
});

describe("Sisi embedded stream phase", () => {
  it("keeps the real generation mode for trusted embedding", () => {
    expect(sisiPhaseLabel({ phase: "tool_routing", shouldPlan: false })).toBe("正在组织回复");
    expect(sisiPhaseLabel({ phase: "generating", outputMode: "streaming" })).toBe("正在生成回复");
    expect(sisiPhaseLabel({ phase: "generating", outputMode: "buffered" })).toBe("正在完成安全校验");
  });

  it("does not expose internal execution phases in the public website widget", () => {
    expect(sisiVisibleBusyLabel("float", "正在选择所需工具")).toBeUndefined();
    expect(sisiVisibleBusyLabel("float", "正在安全执行工具")).toBeUndefined();
    expect(sisiVisibleBusyLabel("page", "正在生成回复")).toBe("正在生成回复");
  });
});
