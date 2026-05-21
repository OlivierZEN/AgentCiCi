import { NavLink, Navigate, Outlet, useNavigate } from "react-router-dom";
import { LS_PLATFORM_TOKEN } from "../constants";

type AuthPayload = { token: string; orgId: string };

function readAuth(): AuthPayload | null {
  const raw = localStorage.getItem(LS_PLATFORM_TOKEN);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as AuthPayload;
  } catch {
    return null;
  }
}

export default function PlatformShell() {
  const auth = readAuth();
  const nav = useNavigate();

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
            <span className="platform-nav__meta-label">当前组织</span>
            <strong className="platform-nav__org">{auth.orgId}</strong>
          </div>
        </div>
        <nav className="admin-nav-links platform-nav__links">
          <NavLink to="/platform" end className={({ isActive }) => (isActive ? "active" : "")}>
            <span>平台概览</span>
          </NavLink>
          <NavLink to="/platform/skills" className={({ isActive }) => (isActive ? "active" : "")}>
            <span>标准技能</span>
          </NavLink>
          <NavLink to="/platform/tools" className={({ isActive }) => (isActive ? "active" : "")}>
            <span>内置工具</span>
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
              localStorage.removeItem(LS_PLATFORM_TOKEN);
              nav("/platform/login", { replace: true });
            }}
          >
            退出登录
          </button>
        </div>
      </aside>
      <main className="admin-main platform-main">
        <div className="platform-main__inner">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
