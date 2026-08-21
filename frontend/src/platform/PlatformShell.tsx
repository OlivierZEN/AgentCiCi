import { useEffect, useState } from "react";
import { ChevronDown, ChevronRight, Palette } from "lucide-react";
import { NavLink, Navigate, Outlet, useLocation, useNavigate } from "react-router-dom";
import { authFetch, clearAuthPayload, readAuthPayload, writeAuthPayload } from "../auth/authStorage";
import { useAuthStorageSync } from "../auth/useAuthStorageSync";
import { LS_PLATFORM_TOKEN } from "../constants";
import AppVersionBadge from "../shared/AppVersionBadge";
import { applyProductTheme, PLATFORM_THEME_STORAGE_KEY, readStoredProductTheme } from "../theme/theme";

type AuthPayload = {
  token: string;
  platformAccountId?: string;
  email?: string;
  mobile?: string;
  displayName?: string;
  themeCode?: string;
};

export type PlatformNavigationItem = {
  to: string;
  label: string;
  end?: boolean;
  activePrefixes?: string[];
  children?: PlatformNavigationItem[];
};

type PlatformNavigationGroup = {
  id: string;
  label: string;
  items: PlatformNavigationItem[];
};

export const PLATFORM_NAVIGATION_GROUPS: PlatformNavigationGroup[] = [
  {
    id: "capability",
    label: "能力治理",
    items: [
      { to: "/platform/skills", label: "技能治理", activePrefixes: ["/platform/skills"] },
      { to: "/platform/models/providers", label: "模型配置", activePrefixes: ["/platform/models"] },
      { to: "/platform/integrations", label: "平台集成" },
      { to: "/platform/tools", label: "工具目录" },
      { to: "/platform/internal-applications", label: "应用中心", activePrefixes: ["/platform/internal-applications"] },
      {
        to: "/platform/system-apis",
        label: "系统 API",
        activePrefixes: ["/platform/system-apis"],
        children: [
          { to: "/platform/system-apis/agentcici", label: "AgentCiCi" },
          { to: "/platform/system-apis/semattice", label: "Semattice" },
        ],
      },
    ],
  },
  {
    id: "operations",
    label: "运营管理",
    items: [
      { to: "/platform/billing", label: "套餐目录", end: true },
      { to: "/platform/billing/packages", label: "加购包与 Credits" },
      { to: "/platform/tenants", label: "租户目录" },
      { to: "/platform/registered-users", label: "注册用户" },
      { to: "/platform/demo-leads", label: "演示线索" },
    ],
  },
  {
    id: "operations_center",
    label: "运维中心",
    items: [
      {
        to: "/platform/operations/deployment-installation",
        label: "部署安装",
        activePrefixes: ["/platform/operations/deployment-installation"],
      },
    ],
  },
  {
    id: "quality",
    label: "风险与质量",
    items: [
      { to: "/platform/evaluation", label: "质量总览", end: true },
      { to: "/platform/evaluation/suites", label: "标准评测资产" },
      { to: "/platform/evaluation/runs", label: "运行洞察" },
      { to: "/platform/audit", label: "平台审计" },
    ],
  },
];

function pathMatchesPrefix(pathname: string, prefix: string): boolean {
  return pathname === prefix || pathname.startsWith(`${prefix}/`);
}

export function isPlatformNavigationItemActive(item: PlatformNavigationItem, pathname: string): boolean {
  if (item.activePrefixes?.some((prefix) => pathMatchesPrefix(pathname, prefix))) return true;
  return item.end ? pathname === item.to : pathMatchesPrefix(pathname, item.to);
}

function readAuth(): AuthPayload | null {
  return readAuthPayload<AuthPayload>(LS_PLATFORM_TOKEN);
}

function accountLabel(auth: AuthPayload): string {
  return auth.displayName?.trim() || auth.email?.trim() || auth.mobile?.trim() || auth.platformAccountId?.trim() || "平台账号";
}

export default function PlatformShell() {
  const [auth, setAuth] = useState<AuthPayload | null>(() => {
    const storedAuth = readAuth();
    applyProductTheme(storedAuth?.themeCode ?? readStoredProductTheme(PLATFORM_THEME_STORAGE_KEY), {
      storageKey: PLATFORM_THEME_STORAGE_KEY,
    });
    return storedAuth;
  });
  const [expandedGroups, setExpandedGroups] = useState<Record<string, boolean>>({
    capability: true,
    operations: true,
    operations_center: true,
    quality: true,
  });
  const nav = useNavigate();
  const location = useLocation();

  useAuthStorageSync<AuthPayload>(LS_PLATFORM_TOKEN, (payload) => {
    setAuth(payload);
    if (!payload?.token) {
      nav("/platform/login", { replace: true });
    }
  });

  useEffect(() => {
    if (!auth?.token) return;
    let cancelled = false;
    void (async () => {
      try {
        const response = await authFetch(LS_PLATFORM_TOKEN, "/auth/platform/me");
        const body = await response.json();
        if (!cancelled && response.ok && body.success) {
          const profile = body.data as AuthPayload;
          applyProductTheme(profile.themeCode, { storageKey: PLATFORM_THEME_STORAGE_KEY });
          const next = { ...auth, ...profile, token: auth.token };
          writeAuthPayload(LS_PLATFORM_TOKEN, next);
          setAuth(next);
        }
      } catch {
        // Keep the locally applied theme when the profile endpoint is temporarily unavailable.
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [auth?.token]);

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
            <span>运营总览</span>
          </NavLink>
          {PLATFORM_NAVIGATION_GROUPS.map((group) => {
            const groupActive = group.items.some((item) => isPlatformNavigationItemActive(item, location.pathname));
            const expanded = expandedGroups[group.id];
            return (
              <section key={group.id} className={`platform-nav__group${groupActive ? " is-active" : ""}`}>
                <button
                  type="button"
                  className="platform-nav__group-toggle"
                  aria-expanded={expanded}
                  onClick={() => setExpandedGroups((current) => ({ ...current, [group.id]: !current[group.id] }))}
                >
                  <span>{group.label}</span>
                  {expanded ? <ChevronDown size={15} aria-hidden /> : <ChevronRight size={15} aria-hidden />}
                </button>
                {expanded ? (
                  <div className="platform-nav__subnav">
                    {group.items.map((item) => (
                      <div key={item.to} className={`platform-nav__item${item.children ? " has-children" : ""}`}>
                        <NavLink
                          to={item.to}
                          end={item.end}
                          className={() => (isPlatformNavigationItemActive(item, location.pathname) ? "active" : "")}
                        >
                          <span>{item.label}</span>
                        </NavLink>
                        {item.children && isPlatformNavigationItemActive(item, location.pathname) ? (
                          <div className="platform-nav__nested" aria-label={`${item.label}子菜单`}>
                            {item.children.map((child) => (
                              <NavLink
                                key={child.to}
                                to={child.to}
                                end={child.end}
                                className={() => (isPlatformNavigationItemActive(child, location.pathname) ? "active" : "")}
                              >
                                <span>{child.label}</span>
                              </NavLink>
                            ))}
                          </div>
                        ) : null}
                      </div>
                    ))}
                  </div>
                ) : null}
              </section>
            );
          })}
        </nav>
        <NavLink to="/platform/preferences/appearance" className={({ isActive }) => `platform-nav__preference${isActive ? " active" : ""}`}>
          <Palette size={15} aria-hidden />
          <span>平台偏好</span>
        </NavLink>
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
