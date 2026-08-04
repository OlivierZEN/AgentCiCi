import { describe, expect, it } from "vitest";
import {
  buildWorkbenchSessionId,
  buildCompanyScopedCacheKey,
  buildWorkbenchSessionPrefix,
  createWorkbenchSessionId,
  isWorkbenchSessionIdForAgent,
  pickRecentHistoryLines,
} from "./workbenchSessions";

describe("workbenchSessions", () => {
  it("builds stable workbench session ids per agent", () => {
    expect(buildWorkbenchSessionId("cici-system")).toBe("workbench:cici-system");
    expect(buildWorkbenchSessionId("sales-agent")).toBe("workbench:sales-agent");
    expect(buildWorkbenchSessionPrefix("sales-agent")).toBe("workbench:sales-agent:");
    expect(isWorkbenchSessionIdForAgent("workbench:sales-agent", "sales-agent")).toBe(true);
    expect(isWorkbenchSessionIdForAgent("workbench:sales-agent:abc", "sales-agent")).toBe(true);
    expect(isWorkbenchSessionIdForAgent("workbench:approval-agent:abc", "sales-agent")).toBe(false);
    expect(createWorkbenchSessionId("sales-agent").startsWith("workbench:sales-agent:")).toBe(true);
  });

  it("keeps browser cache entries isolated by company without changing API session ids", () => {
    expect(buildCompanyScopedCacheKey("company-a", buildWorkbenchSessionId("cici-system")))
      .toBe("company-a::workbench:cici-system");
    expect(buildCompanyScopedCacheKey("company-b", buildWorkbenchSessionId("cici-system")))
      .toBe("company-b::workbench:cici-system");
  });

  it("does not let a default workbench state reuse the unscoped cache key", () => {
    expect(buildCompanyScopedCacheKey("company-a", "cici-system"))
      .not.toBe(buildCompanyScopedCacheKey(undefined, "cici-system"));
  });

  it("keeps chronological recent history and trims blanks", () => {
    const lines = pickRecentHistoryLines(
      [
        { role: "assistant", content: "  " },
        { role: "user", content: "在吗", time: "10:40" },
        { role: "assistant", content: "在的", time: "10:40" },
        { role: "user", content: "?", time: "10:41" },
        { role: "assistant", content: "请说", time: "10:41" },
        { role: "user", content: "?", time: "10:44" },
        { role: "assistant", content: "本次未返回文字内容。", time: "10:44" },
      ],
      4,
    );
    expect(lines).toHaveLength(4);
    expect(lines.map((item) => item.content)).toEqual(["?", "请说", "?", "本次未返回文字内容。"]);
  });
});
