import { describe, expect, it } from "vitest";
import {
  appendAssistantDelta,
  markTrailingAssistantModel,
  preserveAssistantModelNames,
  replaceTrailingAssistant,
  shouldKeepLocalStreamingMessages,
  type ChatMessageBubble,
} from "./chatMessageState";

describe("chatMessageState", () => {
  it("keeps local assistant placeholder when remote history only has the committed user turn", () => {
    const local: ChatMessageBubble[] = [
      { role: "user", content: "北京", time: "23:22" },
      { role: "assistant", content: "", time: "23:22" },
    ];
    const remote: ChatMessageBubble[] = [
      { role: "user", content: "北京", time: "23:22" },
    ];

    expect(shouldKeepLocalStreamingMessages(local, remote)).toBe(true);
  });

  it("lets committed assistant history replace the local stream", () => {
    const local: ChatMessageBubble[] = [
      { role: "user", content: "北京", time: "23:22" },
      { role: "assistant", content: "北", time: "23:22" },
    ];
    const remote: ChatMessageBubble[] = [
      { role: "user", content: "北京", time: "23:22" },
      { role: "assistant", content: "北京明天天气晴。", time: "23:23" },
    ];

    expect(shouldKeepLocalStreamingMessages(local, remote)).toBe(false);
  });

  it("recreates an assistant bubble when a delta arrives after a stale refresh removed the placeholder", () => {
    const next = appendAssistantDelta(
      [{ role: "user", content: "北京", time: "23:22" }],
      "北京明天",
      "23:22",
    );

    expect(next).toEqual([
      { role: "user", content: "北京", time: "23:22" },
      { role: "assistant", content: "北京明天", time: "23:22" },
    ]);
  });

  it("replaces the trailing assistant bubble or appends one when missing", () => {
    expect(
      replaceTrailingAssistant(
        [{ role: "user", content: "审批", time: "10:00" }],
        "已为你生成审批页面。",
        "10:01",
      ),
    ).toEqual([
      { role: "user", content: "审批", time: "10:00" },
      { role: "assistant", content: "已为你生成审批页面。", time: "10:01" },
    ]);
  });

  it("marks the trailing assistant bubble with the active model name", () => {
    const next = markTrailingAssistantModel(
      [
        { role: "user", content: "你好", time: "10:00" },
        { role: "assistant", content: "", time: "10:00" },
      ],
      "qwen3.6-plus",
      "10:00",
    );

    expect(next[1]).toMatchObject({ role: "assistant", modelName: "qwen3.6-plus" });
  });

  it("preserves local assistant model labels when replacing with remote history", () => {
    const next = preserveAssistantModelNames(
      [
        { role: "user", content: "你好", time: "10:00" },
        { role: "assistant", content: "您好", time: "10:00", modelName: "qwen3.6-plus" },
      ],
      [
        { role: "user", content: "你好", time: "10:00" },
        { role: "assistant", content: "您好，老板。", time: "10:01" },
      ],
    );

    expect(next[1]).toMatchObject({ content: "您好，老板。", modelName: "qwen3.6-plus" });
  });
});
