import { describe, expect, it } from "vitest";
import {
  assistantPhaseLabel,
  customerWorkbenchBodyClassName,
  customerModeToWorkbenchMode,
  defaultCustomerQueueFilter,
  defaultCustomerQueueSort,
  formatTimelineDateTime,
  isCurrentVoiceSession,
  scrollConversationToLatest,
} from "./CustomerWorkbenchApp";
import { customerWorkbenchErrorMessage, parseCustomerAssistantStreamEvent } from "./customerWorkbenchApi";

describe("defaultCustomerQueueFilter", () => {
  it("keeps the new-customer priority queue focused", () => {
    expect(defaultCustomerQueueFilter("new")).toBe("focus");
  });

  it("shows all existing customers until the user chooses a filter", () => {
    expect(defaultCustomerQueueFilter("existing")).toBe("");
  });
});

describe("defaultCustomerQueueSort", () => {
  it("shows the most recently interacted customers first in both modes", () => {
    expect(defaultCustomerQueueSort()).toBe("interaction");
  });
});

describe("customerModeToWorkbenchMode", () => {
  it("aligns a global search result with its real customer workspace", () => {
    expect(customerModeToWorkbenchMode("EXISTING")).toBe("existing");
    expect(customerModeToWorkbenchMode("NEW")).toBe("new");
    expect(customerModeToWorkbenchMode("SEARCH")).toBeNull();
  });
});

describe("formatTimelineDateTime", () => {
  it("keeps the four-digit year visible for cross-year interaction timelines", () => {
    expect(formatTimelineDateTime("2024-09-20T09:36:00")).toBe("2024-09-20\n09:36");
    expect(formatTimelineDateTime("2026-09-20T09:36:00")).toBe("2026-09-20\n09:36");
  });

  it("preserves an unparseable source value instead of inventing a date", () => {
    expect(formatTimelineDateTime("时间待确认")).toBe("时间待确认");
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

describe("customer workbench request errors", () => {
  it("never exposes an nginx HTML timeout page", () => {
    const html = "<html><head><title>504 Gateway Time-out</title></head><body>nginx</body></html>";
    expect(customerWorkbenchErrorMessage(504, undefined, html))
      .toBe("CRM 数据同步耗时较长，系统仍在后台处理，请稍后重试。");
    expect(customerWorkbenchErrorMessage(503, undefined, html)).not.toContain("<html>");
  });
});
