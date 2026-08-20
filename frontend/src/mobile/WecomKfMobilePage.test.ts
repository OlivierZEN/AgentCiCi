// @ts-expect-error Vitest executes this test in Node; production sources do not depend on Node types.
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

describe("WeCom mobile customer service control contracts", () => {
  const source = readFileSync(new URL("./WecomKfMobilePage.tsx", import.meta.url), "utf8");
  const controller = readFileSync(new URL("../../../backend/src/main/java/com/codehouse/ciciassistant/wecom/api/WecomKfMobileController.java", import.meta.url), "utf8");
  const conversationService = readFileSync(new URL("../../../backend/src/main/java/com/codehouse/ciciassistant/wecom/service/WecomKfConversationService.java", import.meta.url), "utf8");

  it("keeps human chat in native WeCom after authoritative takeover", () => {
    expect(source).toContain('wx.invoke("navigateToKfChat"');
    expect(source).toContain('receipt.status !== "SUCCEEDED"');
    expect(source).toContain("receipt.readbackState !== 3");
    expect(source).not.toContain("textarea");
    expect(source).not.toContain("contentEditable");
  });

  it("uses a server-owned session and a same-origin write marker", () => {
    expect(controller).toContain(".httpOnly(true)");
    expect(controller).toContain(".secure(true)");
    expect(controller).toContain('sameSite("Lax")');
    expect(controller).toContain('RequestHeader(name = "X-Wecom-Kf-Request")');
    expect(source).toContain('"X-Wecom-Kf-Request": "1"');
  });

  it("never routes human-origin messages into the AI orchestrator", () => {
    expect(conversationService).toContain("boolean humanOrigin = message.origin() == 5");
    expect(conversationService).toMatch(/if \(humanOrigin \|\| !customerOrigin\) \{[\s\S]*?return;[\s\S]*?\}/);
  });
});
