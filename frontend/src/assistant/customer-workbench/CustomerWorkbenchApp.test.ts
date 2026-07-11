import { describe, expect, it } from "vitest";
import {
  assistantPhaseLabel,
  customerWorkbenchBodyClassName,
  defaultCustomerQueueFilter,
  isCurrentVoiceSession,
  scrollConversationToLatest,
} from "./CustomerWorkbenchApp";
import { parseCustomerAssistantStreamEvent } from "./customerWorkbenchApi";

describe("defaultCustomerQueueFilter", () => {
  it("keeps the new-customer priority queue focused", () => {
    expect(defaultCustomerQueueFilter("new")).toBe("focus");
  });

  it("shows all existing customers until the user chooses a filter", () => {
    expect(defaultCustomerQueueFilter("existing")).toBe("");
  });
});

describe("customer assistant conversation behavior", () => {
  it("keeps expanded and closed assistant layouts mutually exclusive", () => {
    expect(customerWorkbenchBodyClassName(true, false)).toBe("customer-workbench__body");
    expect(customerWorkbenchBodyClassName(true, true)).toBe("customer-workbench__body is-assistant-expanded");
    expect(customerWorkbenchBodyClassName(false, true)).toBe("customer-workbench__body is-assistant-closed");
  });

  it("ignores speech callbacks from an invalidated session after send", () => {
    expect(isCurrentVoiceSession(4, 5)).toBe(false);
    expect(isCurrentVoiceSession(5, 5)).toBe(true);
  });

  it("moves the message viewport to the latest content", () => {
    const element = { scrollTop: 12, scrollHeight: 480 };
    scrollConversationToLatest(element);
    expect(element.scrollTop).toBe(480);
  });

  it("maps runtime phases to immediate user-facing progress", () => {
    expect(assistantPhaseLabel("connecting")).toBe("正在连接智能助手...");
    expect(assistantPhaseLabel("retrieving")).toBe("正在检索相关资料...");
    expect(assistantPhaseLabel("generating")).toBe("正在生成回复...");
  });

  it("parses workbench SSE deltas and actions", () => {
    expect(parseCustomerAssistantStreamEvent("delta", '{"text":"客户"}')).toEqual({ type: "delta", text: "客户" });
    expect(parseCustomerAssistantStreamEvent("workbench", '{"action":"OPEN_TAB","actionPayload":{"tab":"timeline"}}')).toEqual({
      type: "workbench",
      result: { action: "OPEN_TAB", actionPayload: { tab: "timeline" } },
    });
  });
});
