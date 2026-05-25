import { useState } from "react";
import { useNavigate } from "react-router-dom";
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
      const res = await fetch("/auth/platform/password/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          identifier: identifier.trim(),
          password,
        }),
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
      localStorage.setItem(LS_PLATFORM_TOKEN, JSON.stringify(body.data));
      setNotice("登录成功。");
      nav("/platform", { replace: true });
    } catch (err) {
      setNotice(`登录失败：${err instanceof Error ? err.message : String(err)}`);
    }
  };

  return (
    <main className="login-root login-root--admin platform-login">
      <section className="login-card login-card--admin platform-login__card">
        <div className="platform-login__intro">
          <div>
            <p className="brand">运营平台</p>
            <h1>运营平台登录</h1>
            <p className="platform-login__intro-copy">登录后可处理平台技能、内置工具、租户生命周期与平台审计。</p>
          </div>
        </div>

        <div className="platform-login__body">
          <div className="platform-login__panel">
            <div className="platform-login__form">
              <label>电子邮件地址或手机号码</label>
              <input
                value={identifier}
                onChange={(e) => setIdentifier(e.target.value)}
                inputMode="text"
                autoComplete="username"
                placeholder="请输入平台账号邮箱或手机号"
              />
              <label>密码</label>
              <input
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
      </section>
    </main>
  );
}
