import { describe, expect, it } from "vitest";
import {
  assistantPhaseLabel,
  customerWorkbenchBodyClassName,
  customerModeToWorkbenchMode,
  defaultCustomerQueueFilter,
  defaultCustomerQueueSort,
  formatTimelineDateTime,
  isCurrentVoiceSession,
  interactionConfirmationOutcome,
  lifecycleSourceLabel,
  preserveSelectedCustomer,
  scrollConversationToLatest,
  shouldSwitchWorkbenchMode,
  timelineItemKey,
  timelineSourceKind,
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

describe("customer context stability", () => {
  const queue = [
    { accountId: "account-a" },
    { accountId: "account-b" },
  ] as Parameters<typeof preserveSelectedCustomer>[1];

  it("keeps a selected customer even when it is outside the current queue page", () => {
    expect(preserveSelectedCustomer("searched-account", queue)).toBe("searched-account");
    expect(preserveSelectedCustomer("", queue)).toBe("account-a");
    expect(preserveSelectedCustomer("", [])).toBe("");
  });

  it("ignores an idempotent assistant mode action", () => {
    expect(shouldSwitchWorkbenchMode("existing", "existing")).toBe(false);
    expect(shouldSwitchWorkbenchMode("existing", "new")).toBe(true);
    expect(shouldSwitchWorkbenchMode("existing", "unknown")).toBe(false);
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

describe("customer timeline source semantics", () => {
  it("keeps public channels distinct from CRM business records", () => {
    expect(timelineSourceKind("WECHAT")).toBe("social-chat");
    expect(timelineSourceKind("PHONE")).toBe("phone");
    expect(timelineSourceKind("MEETING")).toBe("meeting");
    expect(timelineSourceKind("EMAIL")).toBe("email");
    expect(timelineSourceKind("CRM_TASK")).toBe("crm-task");
    expect(timelineSourceKind("CRM_EVENT")).toBe("crm-event");
    expect(timelineSourceKind("CUSTOMER_FEEDBACK")).toBe("feedback");
  });

  it("uses stable user-facing labels and a neutral fallback", () => {
    expect(lifecycleSourceLabel("CRM_TASK")).toBe("CRM 任务");
    expect(lifecycleSourceLabel("CRM_EVENT")).toBe("CRM 日程");
    expect(lifecycleSourceLabel("客户微信沟通")).toBe("微信");
    expect(lifecycleSourceLabel("")).toBe("客户互动");
    expect(timelineSourceKind("NEW_CHANNEL")).toBe("generic");
    expect(lifecycleSourceLabel("NEW_CHANNEL")).toBe("NEW_CHANNEL");
  });

  it("keeps duplicate CRM event ids distinct in the rendered timeline", () => {
    expect(timelineItemKey("crm-event-1", "2026-07-14T14:44:00", 0))
      .not.toBe(timelineItemKey("crm-event-1", "2026-07-14T14:44:00", 1));
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

describe("interaction-driven customer actions", () => {
  it("opens generated actions immediately after interaction confirmation", () => {
    expect(interactionConfirmationOutcome({ actionResult: { generated: 1, refreshed: 1, skipped: 0 } })).toEqual({
      actionCount: 2,
      tab: "recommendations",
      notice: "互动已归集，识别出 2 项经营动作，请确认后写入 CRM。",
    });
  });

  it("keeps informational interactions on the timeline", () => {
    expect(interactionConfirmationOutcome({ actionResult: { generated: 0, refreshed: 0, skipped: 1 } }).tab).toBe("timeline");
  });
});
