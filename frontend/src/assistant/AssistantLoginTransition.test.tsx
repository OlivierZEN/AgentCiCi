import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { AgentLoginMode2 } from "./AssistantApp";

describe("AgentCiCi login transition", () => {
  it("renders the brand visual without a manual login panel", () => {
    const markup = renderToStaticMarkup(<AgentLoginMode2 cubePhase="brand" />);

    expect(markup).toContain("login-mode2__cube-zone");
    expect(markup).not.toContain("login-mode2__form-shell");
    expect(markup).not.toContain("<button");
    expect(markup).not.toContain("统一账号登录");
    expect(markup).not.toContain("还没有账户");
  });

  it("shows only a minimal alert when completion fails", () => {
    const markup = renderToStaticMarkup(
      <AgentLoginMode2 cubePhase="brand" errorMessage="统一登录失败：请稍后重试" />,
    );

    expect(markup).toContain('role="alert"');
    expect(markup).toContain("统一登录失败：请稍后重试");
    expect(markup).not.toContain("<button");
  });
});
