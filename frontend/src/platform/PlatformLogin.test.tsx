import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import PlatformLogin, { buildPlatformLoginRequest, PLATFORM_LOGIN_ENDPOINT } from "./PlatformLogin";

vi.mock("react-router-dom", () => ({ useNavigate: () => () => undefined }));

describe("PlatformLogin reference-locked surface", () => {
  it("renders the supplied-image background structure without changing the auth contract", () => {
    const markup = renderToStaticMarkup(<PlatformLogin />);

    expect(markup).toContain('aria-label="运营平台安全登录"');
    expect(markup).toContain("platform-login--reference");
    expect(markup).toContain("platform-login__reference-control-layer");
    expect(markup).not.toContain("platform-login__orbit-scene");
    expect(PLATFORM_LOGIN_ENDPOINT).toBe("/auth/platform/password/login");
    expect(buildPlatformLoginRequest("  operator@example.com  ", "secret")).toEqual({
      identifier: "operator@example.com",
      password: "secret",
    });
  });
});
