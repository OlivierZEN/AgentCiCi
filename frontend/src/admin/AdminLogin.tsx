import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { LS_ADMIN_TOKEN } from "../constants";
import { safeFetchJson } from "../utils/http";

type AuthPayload = { token: string; orgId: string; orgName?: string; userId: string; roles: string[] };
type OrganizationOption = { orgId: string; orgName: string; roleCode: string };
type LoginPayload = AuthPayload & { requiresOrganizationSelection?: boolean; organizations?: OrganizationOption[] };

function hasOrgAdminRole(roles: string[]): boolean {
  return roles.includes("OWNER") || roles.includes("ORG_ADMIN");
}

function hasAdminOrganizationRole(roleCode: string): boolean {
  return hasOrgAdminRole([roleCode]);
}

function formatOrganizationRole(roleCode: string): string {
  if (roleCode === "OWNER") {
    return "Owner";
  }
  if (roleCode === "ORG_ADMIN") {
    return "组织管理员";
  }
  return "组织成员";
}

export default function AdminLogin() {
  const nav = useNavigate();
  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [notice, setNotice] = useState("组织管理员专用入口");
  const [pendingOrganizations, setPendingOrganizations] = useState<OrganizationOption[]>([]);
  const [loginSubmitting, setLoginSubmitting] = useState(false);

  const resetPendingOrganizations = () => {
    if (pendingOrganizations.length > 0) {
      setPendingOrganizations([]);
      setNotice("组织管理员专用入口");
    }
  };

  const completeAdminLogin = (payload: AuthPayload, message = "登录成功。") => {
    setPendingOrganizations([]);
    localStorage.setItem(LS_ADMIN_TOKEN, JSON.stringify(payload));
    setNotice(message);
    nav("/admin", { replace: true });
  };

  const login = async () => {
    if (loginSubmitting) {
      return;
    }
    setLoginSubmitting(true);
    try {
      setNotice("登录中...");
      const res = await fetch("/auth/password/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ identifier, password }),
      });
      const { body } = await safeFetchJson<LoginPayload>(res);
      if (!res.ok || !body?.success) {
        setNotice(`登录失败：${body?.message ?? `HTTP ${res.status}`}`);
        return;
      }
      if (body.data?.requiresOrganizationSelection) {
        const adminOrganizations = (body.data.organizations ?? []).filter((item) => hasAdminOrganizationRole(item.roleCode));
        if (adminOrganizations.length === 0) {
          setPendingOrganizations([]);
          setNotice("该账号没有可进入的管理组织，请联系 Owner 或组织管理员授权。");
          return;
        }
        setPendingOrganizations(adminOrganizations);
        setNotice("请选择要进入的组织。");
        return;
      }
      if (!body.data?.token) {
        setNotice("登录失败：服务端未返回 token");
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
      completeAdminLogin(payload);
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      setNotice(`登录失败：${msg}`);
    } finally {
      setLoginSubmitting(false);
    }
  };

  const loginToOrganization = async (targetOrgId: string) => {
    if (loginSubmitting) {
      return;
    }
    setLoginSubmitting(true);
    try {
      setNotice("正在进入组织...");
      const res = await fetch("/auth/password/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ orgId: targetOrgId, identifier, password }),
      });
      const { body } = await safeFetchJson<AuthPayload>(res);
      if (!res.ok || !body?.success || !body.data?.token) {
        setNotice(`进入失败：${body?.message ?? `HTTP ${res.status}`}`);
        return;
      }
      const roles = body.data.roles ?? [];
      if (!hasOrgAdminRole(roles)) {
        setNotice("当前组织下没有后台管理权限，请改选其他组织。");
        return;
      }
      completeAdminLogin(body.data, "登录成功。");
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      setNotice(`进入失败：${msg}`);
    } finally {
      setLoginSubmitting(false);
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
            <label>电子邮件地址或手机号码</label>
            <input
              value={identifier}
              onChange={(e) => {
                resetPendingOrganizations();
                setIdentifier(e.target.value);
              }}
              inputMode="email"
              autoComplete="username"
            />
            <label>密码</label>
            <input
              type="password"
              value={password}
              onChange={(e) => {
                resetPendingOrganizations();
                setPassword(e.target.value);
              }}
              autoComplete="off"
            />
            {pendingOrganizations.length > 0 ? (
              <div className="admin-login__org-choice" role="group" aria-label="选择要进入的组织">
                <p className="admin-login__org-choice-title">选择要进入的组织</p>
                {pendingOrganizations.map((item) => (
                  <button
                    key={item.orgId}
                    type="button"
                    className="admin-login__org-option"
                    onClick={() => loginToOrganization(item.orgId)}
                    disabled={loginSubmitting}
                  >
                    <strong>{item.orgName}</strong>
                    <small>{formatOrganizationRole(item.roleCode)}</small>
                  </button>
                ))}
              </div>
            ) : (
              <div className="row admin-login__actions">
                <button type="button" className="admin-login__primary" onClick={login} disabled={!identifier.trim() || !password.trim() || loginSubmitting}>
                  进入后台
                </button>
              </div>
            )}
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
