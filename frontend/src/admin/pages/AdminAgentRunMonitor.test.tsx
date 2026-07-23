import { describe, expect, it } from "vitest";
import { traceNodeTextDetail, traceRuntimeEmptyMessage } from "./AdminAgentRunMonitor";

describe("traceNodeTextDetail", () => {
  it("prefers the separately retained and redacted user input over the compact node summary", () => {
    const detail = traceNodeTextDetail(
      { id: "input-1", type: "USER_MESSAGE", summary: "上下文…" },
      { traceId: "trace-1", detail: { request: { question: "旧摘要", questionDetail: { text: "完整的脱敏上下文", truncated: false } } } },
    );

    expect(detail).toEqual({ text: "完整的脱敏上下文", truncated: false, historicalFallback: false });
  });

  it("marks a legacy record as potentially truncated when only the previous detail field exists", () => {
    const detail = traceNodeTextDetail(
      { id: "input-2", type: "USER_MESSAGE", summary: "上下文…" },
      { traceId: "trace-2", detail: { request: { question: "旧版保留文本" } } },
    );

    expect(detail).toEqual({ text: "旧版保留文本", truncated: true, historicalFallback: true });
  });

  it("keeps historical traces explicit instead of inferring an execution run", () => {
    expect(traceRuntimeEmptyMessage()).toBe("此 Trace 没有关联运行执行事实");
    expect(traceRuntimeEmptyMessage({ associated: false, emptyReason: "NO_EXECUTION_FACTS" }))
      .toBe("此 Trace 没有关联运行执行事实");
    expect(traceRuntimeEmptyMessage({ associated: true, runId: 42 })).toBe("");
  });
});
