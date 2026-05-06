import { useEffect, useMemo, useState } from "react";
import { NavLink, Navigate, Outlet, useNavigate } from "react-router-dom";
import { LS_ADMIN_TOKEN } from "../constants";
import type { AdminOutletContext } from "./useAdminToken";

type AuthPayload = { token: string; orgId: string; userId: string; roles: string[] };
type MePayload = { nickname?: string; avatarBase64?: string; mobile?: string };
type CurrentUserUpdatedDetail = { userId: string; mobile?: string; nickname?: string; avatarBase64?: string };

const adminNavItems = [
  { to: "/admin/kb", label: "知识库" },
  { to: "/admin/models", label: "模型" },
  { to: "/admin/tools", label: "工具" },
  { to: "/admin/skills", label: "技能" },
  { to: "/admin/agent-builder", label: "智能体构建" },
  { to: "/admin/integrations", label: "集成应用" },
  { to: "/admin/ops", label: "运维" },
  { to: "/admin/users", label: "用户" },
];

function readAuth(): AuthPayload | null {
  const raw = localStorage.getItem(LS_ADMIN_TOKEN);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as AuthPayload;
  } catch {
    return null;
  }
}

export default function AdminShell() {
  const nav = useNavigate();
  const auth = readAuth();
  const token = auth?.token ?? "";
  const [me, setMe] = useState<MePayload>({});

  const ctx = useMemo<AdminOutletContext>(() => ({ token }), [token]);

  useEffect(() => {
    if (!token) return;
    void (async () => {
      try {
        const res = await fetch("/auth/me", { headers: { Authorization: `Bearer ${token}` } });
        const json = await res.json();
        if (res.ok && json.success) {
          setMe((json.data ?? {}) as MePayload);
        }
      } catch {
        // ignore header profile fetch errors
      }
    })();
  }, [token]);

  useEffect(() => {
    const onCurrentUserUpdated = (evt: Event) => {
      if (!auth) return;
      const detail = (evt as CustomEvent<CurrentUserUpdatedDetail>).detail;
      if (!detail || detail.userId !== auth.userId) return;
      setMe((prev) => ({
        ...prev,
        mobile: detail.mobile ?? prev.mobile,
        nickname: detail.nickname ?? prev.nickname,
        avatarBase64: detail.avatarBase64 ?? prev.avatarBase64,
      }));
    };
    window.addEventListener("admin-current-user-updated", onCurrentUserUpdated);
    return () => window.removeEventListener("admin-current-user-updated", onCurrentUserUpdated);
  }, [auth]);

  const logout = () => {
    localStorage.removeItem(LS_ADMIN_TOKEN);
    nav("/admin/login", { replace: true });
  };

  if (!auth) {
    return <Navigate to="/admin/login" replace />;
  }

  return (
    <div className="admin-layout">
      <aside className="admin-nav">
        <div className="admin-nav__head">
          <p className="brand admin-brand">组织控制台</p>
          <button type="button" className="admin-nav__logout-icon" onClick={logout} aria-label="退出后台" title="退出后台">
            <svg viewBox="0 0 24 24" aria-hidden>
              <path d="M10 6H6.8A1.8 1.8 0 0 0 5 7.8v8.4A1.8 1.8 0 0 0 6.8 18H10" />
              <path d="M14 8l4 4-4 4" />
              <path d="M9 12h9" />
            </svg>
          </button>
        </div>
        <div className="admin-nav__identity" aria-label="当前组织和管理员">
          <span className="admin-current-user__avatar" aria-hidden>
            {me.avatarBase64 ? <img src={me.avatarBase64} alt="" /> : (me.nickname || me.mobile || "我").slice(0, 1)}
          </span>
          <span className="admin-nav__identity-body">
            <strong className="admin-nav__identity-name">{me.nickname || me.mobile || "当前用户"}</strong>
            <span className="admin-nav__identity-org">{auth.orgId}</span>
          </span>
        </div>
        <nav className="admin-nav-links" aria-label="组织管理菜单">
          {adminNavItems.map((item) => (
            <NavLink key={item.to} to={item.to} className={({ isActive }) => (isActive ? "active" : "")}>
              <span>{item.label}</span>
            </NavLink>
          ))}
        </nav>
      </aside>
      <main className="admin-main">
        <Outlet context={ctx} />
      </main>
    </div>
  );
}
