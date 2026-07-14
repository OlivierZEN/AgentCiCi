import { useState, type CSSProperties } from "react";
import { useNavigate } from "react-router-dom";
import { writeAuthPayload } from "../auth/authStorage";
import platformLoginReference from "../assets/platform-login-reference-1672x941.png";
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
  const [isFormEngaged, setIsFormEngaged] = useState(false);
  const showInteractiveSurface = isFormEngaged || Boolean(identifier || password || notice);
  const referenceStyle = {
    "--platform-login-reference": `url(${platformLoginReference})`,
  } as CSSProperties;

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
    <main className="login-root login-root--admin platform-login platform-login--reference" aria-label="运营平台安全登录" style={referenceStyle}>
      <section className="platform-login__reference-control-layer" aria-labelledby="platform-login-title">
        <div className={`platform-login__reference-a11y ${showInteractiveSurface ? "is-engaged" : ""}`}>
          <div className="platform-login__intro">
            <p className="brand">运营平台</p>
            <h1 id="platform-login-title">运营平台登录</h1>
            <p className="platform-login__intro-copy">登录后可处理平台技能、内置工具、租户生命周期与平台审计。</p>
          </div>

          <div className="platform-login__form">
            <label htmlFor="platform-login-identifier">电子邮件地址或手机号码</label>
            <input
              id="platform-login-identifier"
              name="identifier"
              value={identifier}
              onChange={(e) => setIdentifier(e.target.value)}
              onFocus={() => setIsFormEngaged(true)}
              onBlur={() => setIsFormEngaged(Boolean(identifier || password))}
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
              onFocus={() => setIsFormEngaged(true)}
              onBlur={() => setIsFormEngaged(Boolean(identifier || password))}
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
      </section>
    </main>
  );
}
