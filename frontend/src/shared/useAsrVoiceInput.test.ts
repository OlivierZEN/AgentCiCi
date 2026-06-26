import { describe, expect, it } from "vitest";
import { extractAsrMessageText, isAsrStartedMessage, mergePrefixAsr } from "./useAsrVoiceInput";

describe("useAsrVoiceInput helpers", () => {
  it("merges existing prefix and recognized speech text", () => {
    expect(mergePrefixAsr("帮我", "看看今天的邮件")).toBe("帮我 看看今天的邮件");
    expect(mergePrefixAsr("帮我 ", "看看今天的邮件")).toBe("帮我 看看今天的邮件");
    expect(mergePrefixAsr("", "看看今天的邮件")).toBe("看看今天的邮件");
  });

  it("extracts text from common ASR websocket message shapes", () => {
    expect(extractAsrMessageText({ type: "final", text: "直接文本" })).toBe("直接文本");
    expect(extractAsrMessageText({ type: "partial", transcript: "转写文本" })).toBe("转写文本");
    expect(extractAsrMessageText({ type: "final", result: "结果文本" })).toBe("结果文本");
    expect(extractAsrMessageText({ type: "partial", payload: { text: "payload 文本" } })).toBe("payload 文本");
    expect(extractAsrMessageText({ type: "partial", payload: { output: { sentence: { text: "阿里云句子" } } } })).toBe(
      "阿里云句子",
    );
    expect(extractAsrMessageText({ type: "final", sentence: { text: "句子文本" } })).toBe("句子文本");
    expect(extractAsrMessageText({ type: "final", data: { output: { sentence: { text: "嵌套数据文本" } } } })).toBe(
      "嵌套数据文本",
    );
  });

  it("detects ASR started status exactly", () => {
    expect(isAsrStartedMessage({ type: "status", message: "started" })).toBe(true);
    expect(isAsrStartedMessage({ type: "status", message: "connected" })).toBe(false);
  });
});
