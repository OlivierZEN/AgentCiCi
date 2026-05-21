import { useEffect, useMemo, useState } from "react";
import { NavLink, Navigate, Outlet, useLocation, useNavigate } from "react-router-dom";
import { LS_ADMIN_TOKEN } from "../constants";
import type { AdminOutletContext } from "./useAdminToken";

type AuthPayload = { token: string; orgId: string; orgName?: string; userId: string; roles: string[] };
type MePayload = { nickname?: string; avatarBase64?: string; mobile?: string };
type CurrentUserUpdatedDetail = { userId: string; mobile?: string; nickname?: string; avatarBase64?: string };
type OrganizationProfileUpdatedDetail = { orgId: string; name: string; shortName?: string };

type AdminNavLinkItem = { kind: "link"; to: string; label: string };
type AdminNavGroupItem = { kind: "group"; label: string; children: AdminNavLinkItem[] };
type AdminNavItem = AdminNavLinkItem | AdminNavGroupItem;

const adminNavItems: AdminNavItem[] = [
  {
    kind: "group",
    label: "组织架构",
    children: [
      { kind: "link", to: "/admin/users", label: "用户" },
      { kind: "link", to: "/admin/organization", label: "组织简档" },
    ],
  },
  { kind: "link", to: "/admin/kb", label: "知识库" },
  { kind: "link", to: "/admin/models", label: "模型" },
  { kind: "link", to: "/admin/tools", label: "工具" },
  { kind: "link", to: "/admin/skills", label: "技能" },
  { kind: "link", to: "/admin/agent-builder", label: "智能体构建" },
  { kind: "link", to: "/admin/integrations", label: "集成应用" },
  { kind: "link", to: "/admin/embed-apps", label: "嵌入式智能应用" },
  { kind: "link", to: "/admin/channels/wechat-kf", label: "微信客服" },
  { kind: "link", to: "/admin/ops", label: "观测运维" },
];

function isNavPathActive(pathname: string, to: string) {
  return pathname === to || pathname.startsWith(`${to}/`);
}

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
  const location = useLocation();
  const auth = readAuth();
  const token = auth?.token ?? "";
  const [me, setMe] = useState<MePayload>({});
  const [organizationName, setOrganizationName] = useState(auth?.orgName || auth?.orgId || "");
  const [collapsedNavGroups, setCollapsedNavGroups] = useState<string[]>([]);

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
    if (!token || !auth) return;
    void (async () => {
      try {
        const res = await fetch("/admin/organization/profile", { headers: { Authorization: `Bearer ${token}` } });
        const json = await res.json();
        if (res.ok && json.success && json.data?.name) {
          setOrganizationName(json.data.name);
          localStorage.setItem(LS_ADMIN_TOKEN, JSON.stringify({ ...auth, orgName: json.data.name }));
        }
      } catch {
        // keep token payload organization fallback
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

  useEffect(() => {
    const onOrganizationProfileUpdated = (evt: Event) => {
      if (!auth) return;
      const detail = (evt as CustomEvent<OrganizationProfileUpdatedDetail>).detail;
      if (!detail || detail.orgId !== auth.orgId) return;
      setOrganizationName(detail.shortName || detail.name || auth.orgId);
      localStorage.setItem(LS_ADMIN_TOKEN, JSON.stringify({ ...auth, orgName: detail.name }));
    };
    window.addEventListener("admin-organization-profile-updated", onOrganizationProfileUpdated);
    return () => window.removeEventListener("admin-organization-profile-updated", onOrganizationProfileUpdated);
  }, [auth]);

  const logout = () => {
    localStorage.removeItem(LS_ADMIN_TOKEN);
    nav("/admin/login", { replace: true });
  };

  const toggleNavGroup = (label: string) => {
    setCollapsedNavGroups((current) =>
      current.includes(label) ? current.filter((item) => item !== label) : [...current, label],
    );
  };

  if (!auth) {
    return <Navigate to="/admin/login" replace />;
  }

  const displayOrganizationName = organizationName && organizationName !== auth.orgId ? organizationName : "组织名称未设置";

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
            <span className="admin-nav__identity-org" title={`${displayOrganizationName} · ${auth.orgId}`}>
              {displayOrganizationName}
              <small>{auth.orgId}</small>
            </span>
          </span>
        </div>
        <nav className="admin-nav-links" aria-label="组织管理菜单">
          {adminNavItems.map((item, index) => {
            if (item.kind === "group") {
              const groupActive = item.children.some((child) => isNavPathActive(location.pathname, child.to));
              const groupCollapsed = collapsedNavGroups.includes(item.label);
              const groupChildrenId = `admin-nav-group-${index}`;
              return (
                <div key={item.label} className={`admin-nav-group${groupActive ? " is-active" : ""}${groupCollapsed ? " is-collapsed" : ""}`}>
                  <button
                    type="button"
                    className="admin-nav-group__toggle"
                    aria-expanded={!groupCollapsed}
                    aria-controls={groupChildrenId}
                    onClick={() => toggleNavGroup(item.label)}
                  >
                    <span className="admin-nav-group__label">{item.label}</span>
                    <svg className="admin-nav-group__chevron" viewBox="0 0 16 16" aria-hidden>
                      <path d="M4 6l4 4 4-4" />
                    </svg>
                  </button>
                  <div id={groupChildrenId} className="admin-nav-group__children" hidden={groupCollapsed}>
                    {item.children.map((child) => (
                      <NavLink key={child.to} to={child.to} className={({ isActive }) => `admin-nav-link--child${isActive ? " active" : ""}`}>
                        <span>{child.label}</span>
                      </NavLink>
                    ))}
                  </div>
                </div>
              );
            }
            return (
              <NavLink key={item.to} to={item.to} className={({ isActive }) => (isActive ? "active" : "")}>
                <span>{item.label}</span>
              </NavLink>
            );
          })}
        </nav>
      </aside>
      <main className="admin-main">
        <Outlet context={ctx} />
      </main>
    </div>
  );
}
