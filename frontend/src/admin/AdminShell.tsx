import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { NavLink, Navigate, Outlet, useLocation, useNavigate } from "react-router-dom";
import { authFetch, clearAuthPayload, readAuthPayload, writeAuthPayload } from "../auth/authStorage";
import { useAuthStorageSync } from "../auth/useAuthStorageSync";
import { LS_ADMIN_TOKEN } from "../constants";
import AppVersionBadge from "../shared/AppVersionBadge";
import type { AdminOutletContext } from "./useAdminToken";
import {
  confirmAdminNavigation,
  type AdminNavigationGuard,
} from "./adminNavigationGuard";
import {
  createAdminAuthScopeKey,
  isAdminAsyncRequestCurrent,
} from "./adminAuthScope";
import { applyProductTheme } from "../theme/theme";
import { endOrganizationAdminSession } from "./adminSession";
import { adminApi } from "./adminApi";

type AuthPayload = { token: string; companyId: string; companyName?: string; userId: string; roles: string[] };
type MePayload = { nickname?: string; avatarBase64?: string; mobile?: string; themeCode?: string };
type CurrentUserUpdatedDetail = { userId: string; mobile?: string; nickname?: string; avatarBase64?: string };
type CompanyProfileUpdatedDetail = { companyId: string; name: string; shortName?: string };

type AdminNavLinkItem = { kind: "link"; to: string; label: string };
type AdminNavGroupItem = { kind: "group"; label: string; children: AdminNavLinkItem[] };
type AdminNavItem = AdminNavLinkItem | AdminNavGroupItem;

const adminNavItems: AdminNavItem[] = [
  {
    kind: "group",
    label: "组织架构",
    children: [
      { kind: "link", to: "/admin/users", label: "用户" },
      { kind: "link", to: "/admin/service-principals", label: "机器主体" },
      { kind: "link", to: "/admin/company", label: "组织简档" },
    ],
  },
  { kind: "link", to: "/admin/kb", label: "知识库" },
  { kind: "link", to: "/admin/ontology", label: "业务本体" },
  { kind: "link", to: "/admin/data-quality", label: "数据清洗标注" },
  { kind: "link", to: "/admin/tools", label: "工具" },
  { kind: "link", to: "/admin/skills", label: "技能" },
  { kind: "link", to: "/admin/agent-builder", label: "智能体构建" },
  { kind: "link", to: "/admin/evaluation", label: "AI 质量" },
  { kind: "link", to: "/admin/integrations", label: "集成应用" },
  { kind: "link", to: "/admin/embed-apps", label: "嵌入式智能应用" },
  { kind: "link", to: "/admin/channels/wechat-kf", label: "微信客服" },
  { kind: "link", to: "/admin/ops", label: "观测运维" },
  { kind: "link", to: "/admin/billing", label: "计费用量" },
];

function isNavPathActive(pathname: string, to: string) {
  return pathname === to || pathname.startsWith(`${to}/`);
}

function readAuth(): AuthPayload | null {
  return readAuthPayload<AuthPayload>(LS_ADMIN_TOKEN);
}

export default function AdminShell() {
  const location = useLocation();
  const navigate = useNavigate();
  const [auth, setAuth] = useState<AuthPayload | null>(() => readAuth());
  const token = auth?.token ?? "";
  const [me, setMe] = useState<MePayload>({});
  const [companyName, setCompanyName] = useState(auth?.companyName || auth?.companyId || "");
  const [productMenuOpen, setProductMenuOpen] = useState(false);
  const [sematticeEntryPending, setSematticeEntryPending] = useState(false);
  const [productMenuNotice, setProductMenuNotice] = useState("");
  const [collapsedNavGroups, setCollapsedNavGroups] = useState<string[]>([]);
  const [navigationGuard, setNavigationGuard] = useState<AdminNavigationGuard | null>(null);
  const navigationGuardIdRef = useRef(0);
  const profileRequestIdRef = useRef(0);
  const companyRequestIdRef = useRef(0);
  const productMenuRef = useRef<HTMLDivElement | null>(null);
  const guardScope = createAdminAuthScopeKey(auth?.companyId ?? "", token);
  const authScopeRef = useRef(guardScope);
  const previousGuardScopeRef = useRef(guardScope);

  const invalidateAdminAuthRequests = useCallback((nextScope: string) => {
    authScopeRef.current = nextScope;
    profileRequestIdRef.current += 1;
    companyRequestIdRef.current += 1;
  }, []);

  if (authScopeRef.current !== guardScope) {
    invalidateAdminAuthRequests(guardScope);
  }

  useLayoutEffect(() => {
    if (authScopeRef.current !== guardScope) invalidateAdminAuthRequests(guardScope);
    return () => invalidateAdminAuthRequests(createAdminAuthScopeKey("", ""));
  }, [guardScope, invalidateAdminAuthRequests]);

  const registerNavigationGuard = useCallback((message: string) => {
    const id = ++navigationGuardIdRef.current;
    setNavigationGuard({ id, message });
    return () => {
      setNavigationGuard((current) => current?.id === id ? null : current);
    };
  }, []);

  const ctx = useMemo<AdminOutletContext>(() => ({
    token,
    companyId: auth?.companyId ?? "",
    userId: auth?.userId ?? "",
    registerNavigationGuard,
  }), [auth?.companyId, auth?.userId, registerNavigationGuard, token]);

  useEffect(() => {
    if (previousGuardScopeRef.current === guardScope) return;
    previousGuardScopeRef.current = guardScope;
    setNavigationGuard(null);
  }, [guardScope]);

  useAuthStorageSync<AuthPayload>(LS_ADMIN_TOKEN, (payload) => {
    const nextScope = createAdminAuthScopeKey(payload?.companyId ?? "", payload?.token ?? "");
    if (authScopeRef.current !== nextScope) invalidateAdminAuthRequests(nextScope);
    setAuth(payload);
    setMe({});
    setCompanyName(payload?.companyName || payload?.companyId || "");
  });

  useEffect(() => {
    if (!token) return;
    const requestScope = guardScope;
    const requestId = ++profileRequestIdRef.current;
    const isCurrent = () => isAdminAsyncRequestCurrent(
      requestScope,
      requestId,
      authScopeRef.current,
      profileRequestIdRef.current,
    );
    void (async () => {
      try {
        const res = await authFetch(LS_ADMIN_TOKEN, "/auth/me", {}, {
          onUnauthorized: () => {
            if (!isCurrent()) return;
            invalidateAdminAuthRequests(createAdminAuthScopeKey("", ""));
            clearAuthPayload(LS_ADMIN_TOKEN);
            setAuth(null);
            setMe({});
          },
        });
        if (!isCurrent()) return;
        const json = await res.json();
        if (!isCurrent()) return;
        if (res.ok && json.success) {
          const profile = (json.data ?? {}) as MePayload;
          setMe(profile);
          if (profile.themeCode) applyProductTheme(profile.themeCode);
        }
      } catch {
        // ignore header profile fetch errors
      }
    })();
  }, [guardScope, invalidateAdminAuthRequests, token]);

  useEffect(() => {
    if (!token || !auth) return;
    const authSnapshot = auth;
    const requestScope = guardScope;
    const requestId = ++companyRequestIdRef.current;
    const isCurrent = () => isAdminAsyncRequestCurrent(
      requestScope,
      requestId,
      authScopeRef.current,
      companyRequestIdRef.current,
    );
    void (async () => {
      try {
        const res = await authFetch(LS_ADMIN_TOKEN, adminApi.path("/company/profile"), {}, {
          onUnauthorized: () => {
            if (!isCurrent()) return;
            invalidateAdminAuthRequests(createAdminAuthScopeKey("", ""));
            clearAuthPayload(LS_ADMIN_TOKEN);
            setAuth(null);
            setMe({});
          },
        });
        if (!isCurrent()) return;
        const json = await res.json();
        if (!isCurrent()) return;
        if (res.ok && json.success && json.data?.name) {
          setCompanyName(json.data.name);
          const next = { ...authSnapshot, companyName: json.data.name };
          writeAuthPayload(LS_ADMIN_TOKEN, next);
          setAuth(next);
        }
      } catch {
        // keep token payload company fallback
      }
    })();
  }, [auth?.companyId, guardScope, invalidateAdminAuthRequests, token]);

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
    const onPointerDown = (event: MouseEvent) => {
      if (!productMenuRef.current?.contains(event.target as Node)) setProductMenuOpen(false);
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setProductMenuOpen(false);
    };
    document.addEventListener("mousedown", onPointerDown);
    window.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("mousedown", onPointerDown);
      window.removeEventListener("keydown", onKeyDown);
    };
  }, []);

  useEffect(() => {
    const onCompanyProfileUpdated = (evt: Event) => {
      if (!auth) return;
      const detail = (evt as CustomEvent<CompanyProfileUpdatedDetail>).detail;
      if (!detail || detail.companyId !== auth.companyId) return;
      setCompanyName(detail.shortName || detail.name || auth.companyId);
      const next = { ...auth, companyName: detail.name };
      writeAuthPayload(LS_ADMIN_TOKEN, next);
      setAuth(next);
    };
    window.addEventListener("admin-company-profile-updated", onCompanyProfileUpdated);
    return () => window.removeEventListener("admin-company-profile-updated", onCompanyProfileUpdated);
  }, [auth]);

  const logout = () => {
    if (!confirmAdminNavigation(navigationGuard, (message) => window.confirm(message))) return;
    invalidateAdminAuthRequests(createAdminAuthScopeKey("", ""));
    endOrganizationAdminSession();
    setAuth(null);
    setMe({});
    navigate("/app", { replace: true });
  };

  const enterSematticeConsole = async () => {
    if (sematticeEntryPending || !token) return;
    setSematticeEntryPending(true);
    setProductMenuNotice("");
    try {
      const response = await authFetch(LS_ADMIN_TOKEN, "/auth/semattice/console", { method: "POST" }, {
        onUnauthorized: () => clearAuthPayload(LS_ADMIN_TOKEN),
      });
      const json = await response.json().catch(() => null);
      const redirectUri = typeof json?.data?.redirectUri === "string" ? json.data.redirectUri : "";
      if (!response.ok || !redirectUri) {
        throw new Error("Semattice 管理端暂时无法进入，请确认当前组织已开通且具有管理权限。");
      }
      window.location.assign(redirectUri);
    } catch (error) {
      setProductMenuNotice(error instanceof Error ? error.message : "Semattice 管理端暂时无法进入，请稍后重试。");
      setSematticeEntryPending(false);
    }
  };

  const toggleNavGroup = (label: string) => {
    setCollapsedNavGroups((current) =>
      current.includes(label) ? current.filter((item) => item !== label) : [...current, label],
    );
  };

  if (!auth) {
    return <Navigate to="/app" replace />;
  }

  const displayCompanyName = companyName && companyName !== auth.companyId ? companyName : "组织名称未设置";

  return (
    <div className="admin-layout">
      <aside className="admin-nav">
        <div className="admin-nav__head">
          <p className="brand admin-brand">组织控制台</p>
          <div className="admin-product-switch" ref={productMenuRef}>
            <button
              type="button"
              className="admin-product-switch__trigger"
              aria-haspopup="menu"
              aria-expanded={productMenuOpen}
              onClick={() => {
                setProductMenuNotice("");
                setProductMenuOpen((open) => !open);
              }}
            >
              <span>AgentCiCi</span>
              <svg viewBox="0 0 16 16" aria-hidden><path d="m4 6 4 4 4-4" /></svg>
            </button>
            {productMenuOpen ? (
              <div className="admin-product-switch__menu" role="menu" aria-label="切换管理端">
                <button type="button" role="menuitem" className="admin-product-switch__item is-current" onClick={() => setProductMenuOpen(false)}>
                  <span><strong>AgentCiCi 管理端</strong><small>当前管理端</small></span>
                  <svg viewBox="0 0 16 16" aria-hidden><path d="m3.5 8 2.8 2.8 6.2-6.2" /></svg>
                </button>
                <button type="button" role="menuitem" className="admin-product-switch__item" onClick={() => void enterSematticeConsole()} disabled={sematticeEntryPending}>
                  <span><strong>{sematticeEntryPending ? "正在进入 Semattice…" : "Semattice 管理端"}</strong><small>对象、权限与运行治理</small></span>
                  <svg viewBox="0 0 16 16" aria-hidden><path d="M6 3.5 10.5 8 6 12.5" /></svg>
                </button>
                {productMenuNotice ? <p className="admin-product-switch__notice" role="status">{productMenuNotice}</p> : null}
              </div>
            ) : null}
          </div>
          <button type="button" className="admin-nav__logout-icon" onClick={logout} aria-label="返回前台工作台" title="返回前台工作台">
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
            <span className="admin-nav__identity-org" title={`${displayCompanyName} · ${auth.companyId}`}>
              {displayCompanyName}
              <small>{auth.companyId}</small>
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
                      <NavLink
                        key={child.to}
                        to={child.to}
                        className={({ isActive }) => `admin-nav-link--child${isActive ? " active" : ""}`}
                      >
                        <span>{child.label}</span>
                      </NavLink>
                    ))}
                  </div>
                </div>
              );
            }
            return (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) => (isActive ? "active" : "")}
              >
                <span>{item.label}</span>
              </NavLink>
            );
          })}
        </nav>
        <AppVersionBadge />
      </aside>
      <main className="admin-main">
        <Outlet context={ctx} />
      </main>
    </div>
  );
}
