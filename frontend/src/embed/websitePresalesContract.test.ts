import { describe, expect, it } from "vitest";
// @ts-expect-error Vitest executes this test in Node; production sources do not depend on Node types.
import { readFileSync } from "node:fs";

const source = readFileSync(new URL("./SisiEmbedPage.tsx", import.meta.url), "utf8");

describe("website presales visitor contract", () => {
  it("requires an explicit choice before a returning visitor can continue", () => {
    expect(source).toContain("继续上次需求");
    expect(source).toContain("开始新咨询");
    expect(source).toContain("resumeChoiceRequired");
    expect(source).toContain("/website/visit-choice");
    expect(source).toContain("请先选择继续上次需求或开始新咨询");
  });

  it("renders the server-owned closed and service redirect states", () => {
    expect(source).toContain("SERVICE_REDIRECTED");
    expect(source).toContain("本次咨询已结束");
    expect(source).toContain("/website/ticket-entry");
    expect(source).toContain("websiteLifecycle?.canSend === false");
    expect(source).toContain('websiteLifecycle?.status === "COMPLETED"');
    expect(source).toContain('websiteLifecycle?.status === "SERVICE_REDIRECTED"');
    expect(source).toContain("websiteLifecycle && websiteVisitClosed");
  });

  it("uses public presales language instead of internal CRM suggestions", () => {
    expect(source).toContain("WEBSITE_SUGGESTIONS");
    expect(source).toContain("我负责产品售前咨询");
    expect(source).toContain("请勿提交密码、验证码等敏感信息");
  });
});
