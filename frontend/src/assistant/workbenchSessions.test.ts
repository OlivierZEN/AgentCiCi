import { describe, expect, it } from "vitest";
import {
  buildCompanyScopedCacheKey,
  pickRecentHistoryLines,
} from "./workbenchSessions";

describe("workbenchSessions", () => {
  it("keeps browser cache entries isolated by company without changing API session ids", () => {
    const sessionId = "a4e69d1a-d974-45c2-a7e2-2d31c480c0e2";
    expect(buildCompanyScopedCacheKey("company-a", sessionId)).toBe(`company-a::${sessionId}`);
    expect(buildCompanyScopedCacheKey("company-b", sessionId)).toBe(`company-b::${sessionId}`);
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
