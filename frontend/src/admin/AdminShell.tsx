import { useEffect, useMemo, useState } from "react";
import { NavLink, Navigate, Outlet, useNavigate } from "react-router-dom";
import { LS_ADMIN_TOKEN } from "../constants";
import type { AdminOutletContext } from "./useAdminToken";

type AuthPayload = { token: string; orgId: string; userId: string; roles: string[] };
type MePayload = { nickname?: string; avatarBase64?: string; mobile?: string };
type CurrentUserUpdatedDetail = { userId: string; mobile?: string; nickname?: string; avatarBase64?: string };

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
        <p className="brand admin-brand">
          控制台 / 运维
        </p>
        <p className="subtle">组织：{auth.orgId}</p>
        <div className="admin-current-user">
          <span className="admin-current-user__avatar" aria-hidden>
            {me.avatarBase64 ? <img src={me.avatarBase64} alt="avatar" /> : (me.nickname || me.mobile || "我").slice(0, 1)}
          </span>
          <span className="admin-current-user__name">{me.nickname || me.mobile || "当前用户"}</span>
        </div>
        <nav className="admin-nav-links">
          <NavLink to="/admin/kb" className={({ isActive }) => (isActive ? "active" : "")}>
            知识库
          </NavLink>
          <NavLink to="/admin/models" className={({ isActive }) => (isActive ? "active" : "")}>
            模型
          </NavLink>
          <NavLink to="/admin/tools" className={({ isActive }) => (isActive ? "active" : "")}>
            工具
          </NavLink>
          <NavLink to="/admin/skills" className={({ isActive }) => (isActive ? "active" : "")}>
            技能
          </NavLink>
          <NavLink to="/admin/agent-builder" className={({ isActive }) => (isActive ? "active" : "")}>
            智能体构建
          </NavLink>
          <NavLink to="/admin/integrations" className={({ isActive }) => (isActive ? "active" : "")}>
            集成应用
          </NavLink>
          <NavLink to="/admin/ops" className={({ isActive }) => (isActive ? "active" : "")}>
            运维
          </NavLink>
          <NavLink to="/admin/users" className={({ isActive }) => (isActive ? "active" : "")}>
            用户
          </NavLink>
        </nav>
        <div className="row admin-nav__logout-row">
          <button type="button" className="secondary" onClick={logout}>
            退出后台
          </button>
        </div>
      </aside>
      <main className="admin-main">
        <Outlet context={ctx} />
      </main>
    </div>
  );
}
