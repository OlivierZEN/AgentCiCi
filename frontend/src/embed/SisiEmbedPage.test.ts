import { describe, expect, it } from "vitest";
import { exactConfirmation } from "./sisiEmbedContract";

describe("Sisi embedded confirmation boundary", () => {
  it("only extracts an exact server-provided confirmation phrase", () => {
    expect(exactConfirmation("这是高风险操作，请明确回复“确认写入客户记录”后继续。"))
      .toBe("确认写入客户记录");
    expect(exactConfirmation("确认口令：\"确认删除测试记录\""))
      .toBe("确认删除测试记录");
  });

  it("does not invent a confirmation action from generic warning copy", () => {
    expect(exactConfirmation("此操作风险较高，请确认后继续。"))
      .toBe("");
    expect(exactConfirmation("已经完成写入。"))
      .toBe("");
  });
});
