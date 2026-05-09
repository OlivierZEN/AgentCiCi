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
          <p className="brand admin-brand">Platform Console</p>
          <h1 className="platform-nav__title">运营控制面</h1>
          <p className="platform-nav__summary">平台技能、内置工具、策略版本和审计事实在这里统一治理。</p>
          <div className="platform-nav__meta">
            <span className="platform-nav__meta-label">当前组织</span>
            <strong className="platform-nav__org">{auth.orgId}</strong>
          </div>
          <p className="platform-nav__hint">Policy Bundle · Skill · Tool · Audit</p>
        </div>
        <nav className="admin-nav-links platform-nav__links">
          <NavLink to="/platform" end className={({ isActive }) => (isActive ? "active" : "")}>
            <span>概览</span>
            <small>控制面状态</small>
          </NavLink>
          <NavLink to="/platform/skills" className={({ isActive }) => (isActive ? "active" : "")}>
            <span>平台技能</span>
            <small>模板与策略版本</small>
          </NavLink>
          <NavLink to="/platform/tools" className={({ isActive }) => (isActive ? "active" : "")}>
            <span>内置工具</span>
            <small>风险与依赖治理</small>
          </NavLink>
          <NavLink to="/platform/tenants" className={({ isActive }) => (isActive ? "active" : "")}>
            <span>租户生命周期</span>
            <small>保留策略与销毁预览</small>
          </NavLink>
          <NavLink to="/platform/website-leads" className={({ isActive }) => (isActive ? "active" : "")}>
            <span>网站注册</span>
            <small>预约演示用户</small>
          </NavLink>
          <NavLink to="/platform/audit" className={({ isActive }) => (isActive ? "active" : "")}>
            <span>平台审计</span>
            <small>最近平台动作</small>
          </NavLink>
        </nav>
        <div className="platform-nav__foot">
          <p className="platform-nav__foot-title">工作模式</p>
          <p className="platform-nav__foot-copy">高频运营界面默认保持紧凑布局，优先支持扫描、比对和配置修改。</p>
        </div>
        <div className="row admin-nav__logout-row platform-nav__logout-row">
          <button
            type="button"
            className="secondary platform-nav__logout"
            onClick={() => {
              localStorage.removeItem(LS_PLATFORM_TOKEN);
              nav("/platform/login", { replace: true });
            }}
          >
            退出平台后台
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
