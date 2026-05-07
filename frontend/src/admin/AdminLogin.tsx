import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { LS_ADMIN_TOKEN } from "../constants";
import { safeFetchJson } from "../utils/http";

type AuthPayload = { token: string; orgId: string; userId: string; roles: string[] };

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
        body: JSON.stringify({ orgId, mobile, password }),
      });
      const { body } = await safeFetchJson<AuthPayload>(res);
      if (!res.ok || !body?.success || !body.data?.token) {
        setNotice(`登录失败：${body?.message ?? `HTTP ${res.status}`}`);
        return;
      }
      const payload = body.data;
      const roles = payload.roles ?? [];
      if (!roles.includes("ORG_ADMIN")) {
        setNotice(
          "该账号不是组织管理员。若手机号已在 bootstrap-admin-mobiles 中，使用固定密码登录后服务端会提升角色；否则请由管理员在「用户」页授权，或换用管理员手机号。",
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
    <main className="login-root login-root--admin">
      <section className="login-card login-card--admin">
        <p className="brand">CONSOLE / 管理控制台</p>
        <h1>组织管理员登录</h1>
        <p className="subtle">仅授权管理员可进入；与员工助手会话入口相互独立</p>
        <label>组织 ID</label>
        <input value={orgId} onChange={(e) => setOrgId(e.target.value)} />
        <label>手机号</label>
        <input value={mobile} onChange={(e) => setMobile(e.target.value)} />
        <label>固定密码</label>
        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="off" />
        <div className="row">
          <button type="button" onClick={login} disabled={!password.trim()}>
            进入后台
          </button>
        </div>
        <p className="notice">{notice}</p>
        <p className="subtle admin-login__assistant-link">
          员工使用助手？前往{" "}
          <Link to="/" className="text-link">
            助手登录
          </Link>
        </p>
      </section>
    </main>
  );
}
