import { useState } from "react";
import { NavLink, Navigate, Outlet, useNavigate } from "react-router-dom";
import { clearAuthPayload, readAuthPayload } from "../auth/authStorage";
import { useAuthStorageSync } from "../auth/useAuthStorageSync";
import { LS_PLATFORM_TOKEN } from "../constants";
import AppVersionBadge from "../shared/AppVersionBadge";

type AuthPayload = {
  token: string;
  platformAccountId?: string;
  email?: string;
  mobile?: string;
  displayName?: string;
};

function readAuth(): AuthPayload | null {
  return readAuthPayload<AuthPayload>(LS_PLATFORM_TOKEN);
}

function accountLabel(auth: AuthPayload): string {
  return auth.displayName?.trim() || auth.email?.trim() || auth.mobile?.trim() || auth.platformAccountId?.trim() || "平台账号";
}

export default function PlatformShell() {
  const [auth, setAuth] = useState<AuthPayload | null>(() => readAuth());
  const nav = useNavigate();

  useAuthStorageSync<AuthPayload>(LS_PLATFORM_TOKEN, (payload) => {
    setAuth(payload);
    if (!payload?.token) {
      nav("/platform/login", { replace: true });
    }
  });

  if (!auth) {
    return <Navigate to="/platform/login" replace />;
  }

  return (
    <div className="admin-layout platform-layout">
      <aside className="admin-nav platform-nav">
        <div className="platform-nav__head">
          <p className="brand admin-brand">运营平台</p>
          <h1 className="platform-nav__title">运营控制台</h1>
          <div className="platform-nav__meta">
            <span className="platform-nav__meta-label">平台账号</span>
            <strong className="platform-nav__org">{accountLabel(auth)}</strong>
          </div>
        </div>
        <nav className="admin-nav-links platform-nav__links">
          <NavLink to="/platform" end className={({ isActive }) => (isActive ? "active" : "")}>
            <span>平台概览</span>
          </NavLink>
          <NavLink to="/platform/skills" className={({ isActive }) => (isActive ? "active" : "")}>
            <span>标准技能</span>
          </NavLink>
          <NavLink to="/platform/models" className={({ isActive }) => (isActive ? "active" : "")}>
            <span>模型配置</span>
          </NavLink>
          <NavLink to="/platform/integrations" className={({ isActive }) => (isActive ? "active" : "")}>
            <span>集成配置</span>
          </NavLink>
          <NavLink to="/platform/tools" className={({ isActive }) => (isActive ? "active" : "")}>
            <span>内置工具</span>
          </NavLink>
          <NavLink to="/platform/evaluation" className={({ isActive }) => (isActive ? "active" : "")}>
            <span>智能体质量</span>
          </NavLink>
          <NavLink to="/platform/billing" className={({ isActive }) => (isActive ? "active" : "")}>
            <span>计费配置</span>
          </NavLink>
          <NavLink to="/platform/tenants" className={({ isActive }) => (isActive ? "active" : "")}>
            <span>租户生命周期</span>
          </NavLink>
          <NavLink to="/platform/website-leads" className={({ isActive }) => (isActive ? "active" : "")}>
            <span>网站注册</span>
          </NavLink>
          <NavLink to="/platform/audit" className={({ isActive }) => (isActive ? "active" : "")}>
            <span>平台审计</span>
          </NavLink>
        </nav>
        <div className="row admin-nav__logout-row platform-nav__logout-row">
          <button
            type="button"
            className="secondary platform-nav__logout"
            onClick={() => {
              clearAuthPayload(LS_PLATFORM_TOKEN);
              setAuth(null);
              nav("/platform/login", { replace: true });
            }}
          >
            退出登录
          </button>
        </div>
        <AppVersionBadge />
      </aside>
      <main className="admin-main platform-main">
        <div className="platform-main__inner">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
