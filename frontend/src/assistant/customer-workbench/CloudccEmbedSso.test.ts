import { describe, expect, it } from "vitest";
// @ts-expect-error Vitest executes this test in Node; production sources do not depend on Node types.
import { readFileSync } from "node:fs";
// @ts-expect-error Vitest executes this test in Node; production sources do not depend on Node types.
import { runInNewContext } from "node:vm";

const componentSource = readFileSync(
  new URL("../../../pagecomponents/customer-workbench/customer-workbench.vue", import.meta.url),
  "utf8",
);
const publishedBundle = readFileSync(
  new URL("../../../build/customer-workbench.umd.min.js", import.meta.url),
  "utf8",
);

describe("CloudCC embedded workbench SSO recovery contract", () => {
  it("keeps source and published bundle retry policies aligned", () => {
    expect(componentSource).toContain("const SSO_RETRY_DELAYS = [0, 800, 2000, 4000]");
    expect(componentSource).toContain("const SSO_MAX_RECOVERY_CYCLES = 1");
    expect(publishedBundle).toContain("var ssoRetryDelays = [0, 800, 2000, 4000]");
    expect(publishedBundle).toContain("var fallbackSsoRetryDelays = [0, 800, 2000, 4000]");
  });

  it("reacquires CloudCC token and user for every retry attempt", () => {
    const sourceAttempt = componentSource.slice(
      componentSource.indexOf("async runSsoAttempt"),
      componentSource.indexOf("retryOrFinishSso"),
    );

    expect(sourceAttempt).toContain("readCloudccRuntimeToken()");
    expect(sourceAttempt).toContain("readCloudccUserInfo(token)");
    expect(publishedBundle).toContain("runFallbackSsoAttempt(target, iframe, status, attemptIndex + 1)");
    expect(publishedBundle).toContain("return readCloudccRuntimeToken()");
  });

  it("uses the current CRM session token without invoking credential-based OpenAPI conversion", () => {
    expect(componentSource).toContain("tokenApi.getToken()");
    expect(componentSource).not.toContain("tokenApi.getOpenApiToken");
    expect(publishedBundle).toContain("tokenApi.getToken()");
    expect(publishedBundle).not.toContain("tokenApi.getOpenApiToken");
  });

  it("retries only transient statuses and releases both SSO locks", () => {
    const retryableStatuses = "status === 408 || status === 425 || status === 429 || status >= 500";
    const fallbackRetryableStatuses = "statusCode === 408 || statusCode === 425 || statusCode === 429 || statusCode >= 500";

    expect(componentSource).toContain(retryableStatuses);
    expect(componentSource).toContain("response.ok ? true : this.isRetryableSsoStatus(response.status)");
    expect(componentSource).toContain("status === 401 || status === 403");
    expect(componentSource).toContain("this.ssoStarted = false");
    expect(publishedBundle).toContain(fallbackRetryableStatuses);
    expect(publishedBundle).toContain("response.ok ? true : isRetryableFallbackSsoStatus(response.status)");
    expect(publishedBundle).toContain("statusCode === 401 || statusCode === 403");
    expect(publishedBundle).toContain("target.__customerWorkbenchSsoStarted = false");
  });

  it("does not expose token values or raw server errors in status messages", () => {
    expect(componentSource).not.toContain("error.message");
    expect(componentSource).not.toContain("JSON.stringify(body)");
    expect(publishedBundle).not.toContain("error.message");
    expect(publishedBundle).not.toContain("JSON.stringify(body)");
  });

  it("maps backend identity failures to truthful safe user guidance", () => {
    expect(componentSource).toContain("safeSsoFailureMessage(body && body.message, response.status)");
    expect(componentSource).toContain("CloudCC 登录凭证校验失败，请从 CRM 重新打开客户互动工作台。");
    expect(componentSource).toContain("CloudCC 当前用户尚未绑定到 AgentCiCi 成员，请联系管理员配置账号绑定。");
    expect(publishedBundle).toContain("safeFallbackSsoFailureMessage(body && body.message, response.status)");
    expect(publishedBundle).not.toContain(
      "CloudCC 当前用户与 AgentCiCi 账号未正确映射，请联系管理员检查账号绑定。",
    );
  });

  it("recovers from a transient ticket failure without reopening the CRM page", async () => {
    let fetchCalls = 0;
    const documentStub = {
      readyState: "complete",
      documentElement: {},
      body: {},
      head: { appendChild: () => undefined },
      getElementById: () => null,
      createElement: () => ({ style: {}, appendChild: () => undefined }),
      querySelectorAll: () => [],
    };
    const browserContext: Record<string, unknown> = {
      module: { exports: {} },
      exports: {},
      document: documentStub,
      location: { href: "https://ap6.lightning.cloudcc.cn/", origin: "https://ap6.lightning.cloudcc.cn" },
      URL,
      Promise,
      JSON,
      console,
      atob,
      decodeURIComponent,
      encodeURIComponent,
      MutationObserver: class {
        observe() {}
      },
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
      clearTimeout: () => undefined,
      setTimeout: (callback: () => void) => {
        queueMicrotask(callback);
        return 1;
      },
      fetch: async () => {
        fetchCalls += 1;
        if (fetchCalls === 1) {
          return { ok: false, status: 503, json: async () => ({ success: false }) };
        }
        return {
          ok: true,
          status: 200,
          json: async () => ({ success: true, data: { ticket: "recovered-ticket" } }),
        };
      },
      $CCDK: {
        CCToken: { getToken: () => "runtime-token" },
        CCUser: { getUserInfo: () => ({ username: "crm-user" }) },
      },
    };
    browserContext.self = browserContext;
    browserContext.window = browserContext;
    runInNewContext(publishedBundle, browserContext);

    const component = (browserContext.module as { exports: {
      data: () => Record<string, unknown>;
      methods: Record<string, (...args: unknown[]) => unknown>;
    } }).exports;
    const instance: Record<string, unknown> = {
      ...component.data(),
      elePropObj: {},
      workspaceUrl: "https://agentcici.example.test/app?aiApp=customer-workbench&embed=crm",
      agentCompanyId: "agent-org",
      enableSso: true,
      ssoEndpoint: "https://agentcici.example.test/auth/cloudcc-sso/ticket",
    };
    Object.entries(component.methods).forEach(([name, method]) => {
      instance[name] = method.bind(instance);
    });

    (instance.bootstrapSso as () => void)();
    for (let index = 0; index < 50 && instance.ssoStarted; index += 1) {
      await Promise.resolve();
      await new Promise((resolve) => setTimeout(resolve, 0));
    }

    expect(fetchCalls).toBe(2);
    expect(instance.ssoStarted).toBe(false);
    expect(instance.ssoMessage).toBe("");
    expect(instance.resolvedWorkspaceUrl).toContain("ssoTicket=recovered-ticket");
  });
});
