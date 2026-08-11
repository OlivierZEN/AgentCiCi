import { describe, expect, it } from "vitest";
import { isAgentInvocationBlocked } from "./AssistantApp";

describe("assistant machine execution preflight", () => {
  it("keeps agents without machine bindings usable", () => {
    expect(isAgentInvocationBlocked({ bound: false, canInvoke: true, reasonCode: "NOT_REQUIRED", maxRole: "NONE", message: "" })).toBe(false);
  });

  it("blocks the composer before sending when the current member lacks application access", () => {
    expect(isAgentInvocationBlocked({ bound: true, canInvoke: false, reasonCode: "APP_ROLE_REQUIRED", maxRole: "NONE", message: "需要授权" })).toBe(true);
  });
});
