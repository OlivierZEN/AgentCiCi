import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { writeAuthPayload } from "../auth/authStorage";
import { LS_PLATFORM_TOKEN } from "../constants";
import { safeFetchJson } from "../utils/http";

type AuthPayload = {
  token: string;
  tokenType: "platform";
  platformAccountId: string;
  email: string;
  mobile: string;
  displayName: string;
  roles: string[];
};

export const PLATFORM_LOGIN_ENDPOINT = "/auth/platform/password/login";

export function buildPlatformLoginRequest(identifier: string, password: string) {
  return {
    identifier: identifier.trim(),
    password,
  };
}

function hasPlatformRole(roles: string[]): boolean {
  return roles.some((role) => role.startsWith("PLATFORM_"));
}

export default function PlatformLogin() {
  const nav = useNavigate();
  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [notice, setNotice] = useState("");

  const login = async () => {
    try {
      setNotice("登录中…");
      const res = await fetch(PLATFORM_LOGIN_ENDPOINT, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(buildPlatformLoginRequest(identifier, password)),
      });
      const { body } = await safeFetchJson<AuthPayload>(res);
      if (!res.ok || !body?.success || !body.data?.token) {
        setNotice(`登录失败：${body?.message ?? `HTTP ${res.status}`}`);
        return;
      }
      if (!hasPlatformRole(body.data.roles ?? [])) {
        setNotice("该账号当前没有平台角色，请先确认平台账号或手机号白名单配置。");
        return;
      }
      writeAuthPayload(LS_PLATFORM_TOKEN, body.data);
      setNotice("登录成功。");
      nav("/platform", { replace: true });
    } catch (err) {
      setNotice(`登录失败：${err instanceof Error ? err.message : String(err)}`);
    }
  };

  return (
    <main className="login-root login-root--admin platform-login" aria-label="运营平台安全登录">
      <section className="platform-login__visual" aria-hidden="true">
        <div className="platform-login__visual-brand">
          <span className="platform-login__signal-mark" />
          <span>运营平台</span>
        </div>
        <p className="platform-login__visual-kicker">AGENT OPERATIONS</p>

        <div className="platform-login__orbit-scene">
          <svg viewBox="0 0 760 760" role="presentation" focusable="false">
            <g className="platform-login__orbit-rings">
              <ellipse cx="378" cy="378" rx="176" ry="88" />
              <ellipse cx="378" cy="378" rx="238" ry="116" transform="rotate(28 378 378)" />
              <ellipse cx="378" cy="378" rx="294" ry="140" transform="rotate(-32 378 378)" />
              <ellipse cx="378" cy="378" rx="346" ry="166" transform="rotate(12 378 378)" />
              <circle cx="378" cy="378" r="82" />
              <circle cx="378" cy="378" r="114" />
            </g>
            <g className="platform-login__orbit-lines">
              <path d="M42 496C184 362 302 302 462 286C566 276 647 304 718 352" />
              <path d="M116 178C230 254 310 362 336 494C350 566 394 631 492 694" />
              <path d="M42 610C206 582 358 514 508 392C592 324 654 262 714 174" />
            </g>
            <g className="platform-login__orbit-points">
              <circle cx="120" cy="178" r="3.5" />
              <circle cx="42" cy="496" r="3" />
              <circle cx="178" cy="278" r="2.5" />
              <circle cx="242" cy="582" r="3" />
              <circle cx="492" cy="694" r="3.5" />
              <circle cx="614" cy="306" r="4" />
              <circle cx="714" cy="174" r="3" />
              <circle cx="718" cy="352" r="3.5" />
              <circle cx="558" cy="514" r="2.5" />
              <circle cx="378" cy="378" r="7" />
            </g>
            <g className="platform-login__orbit-core">
              <circle cx="378" cy="378" r="28" />
              <circle cx="378" cy="378" r="11" />
            </g>
          </svg>
        </div>

        <div className="platform-login__visual-status">
          <span className="platform-login__status-dot" />
          <span>运行状态 / 稳定</span>
        </div>
        <div className="platform-login__visual-rule" />
        <p className="platform-login__visual-footer">AGENT INTELLIGENCE LAYER</p>
      </section>

      <section className="platform-login__content" aria-labelledby="platform-login-title">
        <div className="platform-login__content-inner">
          <div className="platform-login__intro">
            <p className="brand">运营平台</p>
            <h1 id="platform-login-title">运营平台登录</h1>
            <p className="platform-login__intro-copy">登录后可处理平台技能、内置工具、租户生命周期与平台审计。</p>
          </div>

          <div className="platform-login__body">
            <div className="platform-login__panel">
              <div className="platform-login__form">
                <label htmlFor="platform-login-identifier">电子邮件地址或手机号码</label>
                <input
                  id="platform-login-identifier"
                  name="identifier"
                  value={identifier}
                  onChange={(e) => setIdentifier(e.target.value)}
                  inputMode="text"
                  autoComplete="username"
                  placeholder="请输入平台账号邮箱或手机号"
                />
                <label htmlFor="platform-login-password">密码</label>
                <input
                  id="platform-login-password"
                  name="password"
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  autoComplete="current-password"
                  placeholder="请输入密码"
                />
                <div className="row platform-login__actions">
                  <button type="button" className="platform-button platform-button--primary" onClick={login} disabled={!identifier.trim() || !password.trim()}>
                    进入运营平台
                  </button>
                </div>
                {notice ? (
                  <p className="notice platform-login__notice" aria-live="polite">
                    {notice}
                  </p>
                ) : null}
              </div>
            </div>
          </div>
        </div>
      </section>
    </main>
  );
}
