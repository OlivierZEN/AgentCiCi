import { describe, expect, it } from "vitest";
import {
  defaultCustomerQueueFilter,
  isCurrentVoiceSession,
  scrollConversationToLatest,
} from "./CustomerWorkbenchApp";

describe("defaultCustomerQueueFilter", () => {
  it("keeps the new-customer priority queue focused", () => {
    expect(defaultCustomerQueueFilter("new")).toBe("focus");
  });

  it("shows all existing customers until the user chooses a filter", () => {
    expect(defaultCustomerQueueFilter("existing")).toBe("");
  });
});

describe("customer assistant conversation behavior", () => {
  it("ignores speech callbacks from an invalidated session after send", () => {
    expect(isCurrentVoiceSession(4, 5)).toBe(false);
    expect(isCurrentVoiceSession(5, 5)).toBe(true);
  });

  it("moves the message viewport to the latest content", () => {
    const element = { scrollTop: 12, scrollHeight: 480 };
    scrollConversationToLatest(element);
    expect(element.scrollTop).toBe(480);
  });
});
