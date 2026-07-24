import { describe, expect, it } from "vitest";
import { getCompanyMonogram, getThemeSeriesClass } from "./avatar";

describe("getCompanyMonogram", () => {
  it("uses the first visible company character", () => {
    expect(getCompanyMonogram("智能体平台演示环境")).toBe("智");
    expect(getCompanyMonogram(" demo company")).toBe("D");
    expect(getCompanyMonogram("2号组织")).toBe("2");
    expect(getCompanyMonogram("   ")).toBe("组");
  });
});

describe("getThemeSeriesClass", () => {
  it("maps stable identities into the four theme-owned series", () => {
    expect(getThemeSeriesClass("agent-cici")).toMatch(/^is-series-[1-4]$/);
    expect(getThemeSeriesClass("agent-cici")).toBe(getThemeSeriesClass("agent-cici"));
    expect(getThemeSeriesClass("")).toBe("is-series-1");
  });
});
