import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { LS_PLATFORM_TOKEN } from "../constants";
import { safeFetchJson } from "../utils/http";

type AuthPayload = { token: string; orgId: string; userId: string; roles: string[] };

function hasPlatformRole(roles: string[]): boolean {
  return roles.some((role) => role.startsWith("PLATFORM_"));
}

export default function PlatformLogin() {
  const nav = useNavigate();
  const [orgId, setOrgId] = useState("demo-org");
  const [mobile, setMobile] = useState("13800138111");
  const [code, setCode] = useState("");
  const [notice, setNotice] = useState("平台运营入口");

  const sendCode = async () => {
    try {
      setNotice("验证码发送中...");
      const res = await fetch("/auth/sms/send", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ orgId, mobile }),
      });
      const { body } = await safeFetchJson<{ devCode?: string }>(res);
      if (!res.ok || !body?.success) {
        setNotice(`发送失败：${body?.message ?? `HTTP ${res.status}`}`);
        return;
      }
      setNotice(`验证码已发送，本地开发验证码：${body.data?.devCode ?? "（未返回）"}`);
    } catch (err) {
      setNotice(`发送失败：${err instanceof Error ? err.message : String(err)}`);
    }
  };

  const login = async () => {
    try {
      setNotice("登录中...");
      const res = await fetch("/auth/sms/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ orgId, mobile, code }),
      });
      const { body } = await safeFetchJson<AuthPayload>(res);
      if (!res.ok || !body?.success || !body.data?.token) {
        setNotice(`登录失败：${body?.message ?? `HTTP ${res.status}`}`);
        return;
      }
      if (!hasPlatformRole(body.data.roles ?? [])) {
        setNotice("该手机号当前没有平台角色，请先加入 platform-*-mobiles 配置。");
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
            <p className="brand">Platform Console</p>
            <h1>平台运营登录</h1>
            <p className="subtle platform-login__lede">面向平台运营角色的独立控制面，集中处理治理、版本和审计工作。</p>
          </div>
          <div className="platform-login__chips">
            <span className="platform-login__chip">浅色控制台</span>
            <span className="platform-login__chip">紧凑工作流</span>
            <span className="platform-login__chip">独立权限</span>
          </div>
        </div>

        <div className="platform-login__body">
          <div className="platform-login__panel">
            <label>组织 ID</label>
            <input value={orgId} onChange={(e) => setOrgId(e.target.value)} />
            <label>手机号</label>
            <input value={mobile} onChange={(e) => setMobile(e.target.value)} />
            <label>验证码</label>
            <input value={code} onChange={(e) => setCode(e.target.value)} />
            <div className="row platform-login__actions">
              <button type="button" className="platform-button platform-button--secondary" onClick={sendCode}>
                获取验证码
              </button>
              <button type="button" className="platform-button platform-button--primary" onClick={login}>
                进入平台后台
              </button>
            </div>
            <p className="notice platform-login__notice">{notice}</p>
          </div>

          <aside className="platform-login__aside">
            <div className="platform-login__aside-block">
              <p className="platform-login__aside-title">适用角色</p>
              <p className="platform-login__aside-copy">仅平台角色可进入，组织管理后台与平台控制面分离。</p>
            </div>
            <div className="platform-login__aside-block">
              <p className="platform-login__aside-title">默认节奏</p>
              <p className="platform-login__aside-copy">先看控制面状态，再进入技能、工具或审计页面处理具体事项。</p>
            </div>
            <p className="subtle admin-login__assistant-link">
              组织管理？前往{" "}
              <Link to="/admin/login" className="text-link">
                管理控制台
              </Link>
            </p>
          </aside>
        </div>
      </section>
    </main>
  );
}
