import { describe, expect, it } from "vitest";
import { buildCompileNotice, isCompileRequired, keepRecentVersionHistory } from "./compile-history";

describe("keepRecentVersionHistory", () => {
  it("keeps only the latest 10 records", () => {
    const rows = Array.from({ length: 13 }, (_, index) => ({ versionNo: 100 - index }));
    const sliced = keepRecentVersionHistory(rows, 10);
    expect(sliced).toHaveLength(10);
    expect(sliced[0]?.versionNo).toBe(100);
    expect(sliced[9]?.versionNo).toBe(91);
  });
});

describe("buildCompileNotice", () => {
  it("returns no-change message when compile did not change", () => {
    const msg = buildCompileNotice({
      changed: false,
      compileMessage: "未检测到可发布变更，本次不新增版本。",
      draftVersionNo: 9,
    });
    expect(msg).toContain("未检测到可发布变更");
    expect(msg).not.toContain("v9");
  });

  it("returns version-created message when compile changed", () => {
    const msg = buildCompileNotice({
      changed: true,
      draftVersionNo: 12,
    });
    expect(msg).toContain("v12");
  });
});

describe("isCompileRequired", () => {
  it("requires compile when draft differs from latest compile baseline", () => {
    expect(isCompileRequired("digest-new", "digest-old", "digest-old")).toBe(true);
  });

  it("does not require compile when draft equals latest compile baseline", () => {
    expect(isCompileRequired("digest-same", "digest-same", "digest-old")).toBe(false);
  });

  it("falls back to loaded baseline when no successful compile exists", () => {
    expect(isCompileRequired("digest-new", null, "digest-old")).toBe(true);
    expect(isCompileRequired("digest-same", null, "digest-same")).toBe(false);
  });
});
