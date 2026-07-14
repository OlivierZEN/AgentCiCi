import { describe, expect, it } from "vitest";
import { getOrganizationMonogram, getThemeSeriesClass } from "./avatar";

describe("getOrganizationMonogram", () => {
  it("uses the first visible organization character", () => {
    expect(getOrganizationMonogram("智能体平台演示环境")).toBe("智");
    expect(getOrganizationMonogram(" demo organization")).toBe("D");
    expect(getOrganizationMonogram("2号组织")).toBe("2");
    expect(getOrganizationMonogram("   ")).toBe("组");
  });
});

describe("getThemeSeriesClass", () => {
  it("maps stable identities into the four theme-owned series", () => {
    expect(getThemeSeriesClass("agent-cici")).toMatch(/^is-series-[1-4]$/);
    expect(getThemeSeriesClass("agent-cici")).toBe(getThemeSeriesClass("agent-cici"));
    expect(getThemeSeriesClass("")).toBe("is-series-1");
  });
});
