import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { LS_ADMIN_TOKEN } from "../constants";
import { safeFetchJson } from "../utils/http";

type AuthPayload = { token: string; orgId: string; userId: string; roles: string[] };

function hasOrgAdminRole(roles: string[]): boolean {
  return roles.includes("OWNER") || roles.includes("ORG_ADMIN");
}

export default function AdminLogin() {
  const nav = useNavigate();
  const [orgId, setOrgId] = useState("demo-org");
  const [mobile, setMobile] = useState("13900009999");
  const [password, setPassword] = useState("");
  const [notice, setNotice] = useState("组织管理员专用入口");

  const login = async () => {
    try {
      setNotice("登录中...");
      const res = await fetch("/auth/password/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ orgId, identifier: mobile, password }),
      });
      const { body } = await safeFetchJson<AuthPayload>(res);
      if (!res.ok || !body?.success || !body.data?.token) {
        setNotice(`登录失败：${body?.message ?? `HTTP ${res.status}`}`);
        return;
      }
      const payload = body.data;
      const roles = payload.roles ?? [];
      if (!hasOrgAdminRole(roles)) {
        setNotice(
          "该账号不是组织管理员。若账号手机号已在 bootstrap-admin-mobiles 中，使用密码登录后服务端会提升角色；否则请由管理员在「用户」页授权，或换用管理员账号。",
        );
        return;
      }
      localStorage.setItem(LS_ADMIN_TOKEN, JSON.stringify(payload));
      setNotice("登录成功。");
      nav("/admin", { replace: true });
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      setNotice(`登录失败：${msg}`);
    }
  };

  return (
    <main className="login-root login-root--admin admin-login">
      <section className="login-card login-card--admin admin-login__card" aria-label="组织管理后台登录">
        <div className="admin-login__main">
          <div className="admin-login__intro">
            <p className="brand admin-login__brand">CONSOLE / 管理控制台</p>
            <h1>组织管理员登录</h1>
            <p className="subtle admin-login__lede">仅授权管理员可进入，管理后台与员工助手会话入口相互独立。</p>
          </div>

          <div className="admin-login__form">
            <label>组织 ID</label>
            <input value={orgId} onChange={(e) => setOrgId(e.target.value)} />
            <label>电子邮件地址或手机号码</label>
            <input value={mobile} onChange={(e) => setMobile(e.target.value)} inputMode="email" autoComplete="username" />
            <label>密码</label>
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="off" />
            <div className="row admin-login__actions">
              <button type="button" className="admin-login__primary" onClick={login} disabled={!password.trim()}>
                进入后台
              </button>
            </div>
            <p className="notice admin-login__notice">{notice}</p>
          </div>
        </div>

        <aside className="admin-login__aside" aria-label="入口说明">
          <div className="admin-login__aside-block">
            <p className="admin-login__aside-title">权限边界</p>
            <p className="admin-login__aside-copy">Owner 与组织管理员可进入后台维护知识、成员、模型、工具和技能。</p>
          </div>
          <div className="admin-login__aside-block">
            <p className="admin-login__aside-title">入口分离</p>
            <p className="admin-login__aside-copy">员工日常对话继续使用助手入口，后台配置不会混入会话工作区。</p>
          </div>
          <p className="subtle admin-login__assistant-link">
            员工使用助手？前往{" "}
            <Link to="/" className="text-link">
              助手登录
            </Link>
          </p>
        </aside>
      </section>
    </main>
  );
}
