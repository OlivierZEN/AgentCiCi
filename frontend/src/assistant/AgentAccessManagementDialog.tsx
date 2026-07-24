import { useEffect, useMemo, useState } from "react";
import { safeFetchJson } from "../utils/http";

type AgentAccessManagementDialogProps = {
  open: boolean;
  token: string;
  agentId: string;
  agentName: string;
  onClose: () => void;
};

type GrantRow = {
  id: string;
  principalType: string;
  principalId?: string | null;
  permission: string;
};

type GrantDraft = {
  key: string;
  principalType: "COMPANY" | "USER" | "SYSTEM_ROLE";
  principalId: string;
  permissions: string[];
};

type UserRow = {
  id: string;
  nickname?: string;
  mobile?: string;
  roleCode?: string;
  memberStatus?: string;
};

const PERMISSIONS = [
  ["VIEW", "可见"],
  ["RUN", "运行"],
  ["DEBUG", "调试"],
  ["EDIT", "编辑"],
  ["PUBLISH", "发布"],
  ["MANAGE", "管理"],
  ["OPENAPI", "OpenAPI"],
  ["LOG_VIEW", "日志"],
] as const;

function permissionLabel(permission: string) {
  return PERMISSIONS.find(([code]) => code === permission)?.[1] ?? permission;
}

function principalTypeLabel(type: GrantDraft["principalType"]) {
  if (type === "COMPANY") return "全公司";
  if (type === "SYSTEM_ROLE") return "系统角色";
  return "指定成员";
}

function groupGrants(rows: GrantRow[]): GrantDraft[] {
  const byPrincipal = new Map<string, GrantDraft>();
  rows.forEach((row) => {
    const principalType = row.principalType as GrantDraft["principalType"];
    if (!["COMPANY", "USER", "SYSTEM_ROLE"].includes(principalType)) return;
    const principalId = row.principalId ?? "";
    const key = `${principalType}:${principalId}`;
    const current = byPrincipal.get(key) ?? { key, principalType, principalId, permissions: [] };
    if (!current.permissions.includes(row.permission)) {
      current.permissions.push(row.permission);
    }
    byPrincipal.set(key, current);
  });
  return Array.from(byPrincipal.values());
}

function principalLabel(row: GrantDraft, users: UserRow[]) {
  if (row.principalType === "COMPANY") return "全体公司成员";
  if (row.principalType === "SYSTEM_ROLE") return row.principalId === "ORG_ADMIN" ? "组织管理员" : "普通成员";
  const user = users.find((item) => item.id === row.principalId);
  return user ? `${user.nickname || user.mobile || user.id} · ${user.mobile || user.id}` : row.principalId;
}

export default function AgentAccessManagementDialog({
  open,
  token,
  agentId,
  agentName,
  onClose,
}: AgentAccessManagementDialogProps) {
  const [grants, setGrants] = useState<GrantDraft[]>([]);
  const [users, setUsers] = useState<UserRow[]>([]);
  const [principalType, setPrincipalType] = useState<GrantDraft["principalType"]>("COMPANY");
  const [principalId, setPrincipalId] = useState("");
  const [selectedPermissions, setSelectedPermissions] = useState<string[]>(["VIEW", "RUN"]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState("");

  const activeUsers = useMemo(
    () => users.filter((item) => item.memberStatus === "ACTIVE" || !item.memberStatus),
    [users],
  );

  useEffect(() => {
    if (!open || !token || !agentId) return;
    let cancelled = false;
    const load = async () => {
      setLoading(true);
      setNotice("");
      try {
        const [grantResponse, userResponse] = await Promise.all([
          fetch(`/agents/${encodeURIComponent(agentId)}/access-grants`, {
            headers: { Authorization: `Bearer ${token}` },
          }),
          fetch("/admin/users", {
            headers: { Authorization: `Bearer ${token}` },
          }),
        ]);
        const { body: grantBody } = await safeFetchJson<{ grants?: GrantRow[] }>(grantResponse);
        const { body: userBody } = await safeFetchJson<UserRow[]>(userResponse);
        if (!grantResponse.ok || !grantBody?.success) {
          throw new Error(grantBody?.message ?? `HTTP ${grantResponse.status}`);
        }
        if (!cancelled) {
          setGrants(groupGrants(grantBody.data?.grants ?? []));
          if (userResponse.ok && userBody?.success && Array.isArray(userBody.data)) {
            setUsers(userBody.data);
          }
        }
      } catch (error) {
        if (!cancelled) setNotice(error instanceof Error ? error.message : "权限数据加载失败");
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [agentId, open, token]);

  useEffect(() => {
    if (principalType === "COMPANY") {
      setPrincipalId("");
    } else if (principalType === "SYSTEM_ROLE") {
      setPrincipalId("ORG_USER");
    } else {
      setPrincipalId(activeUsers[0]?.id ?? "");
    }
  }, [activeUsers, principalType]);

  if (!open) return null;

  const totalPermissions = grants.reduce((count, item) => count + item.permissions.length, 0);

  const togglePermission = (permission: string) => {
    setSelectedPermissions((current) => (
      current.includes(permission)
        ? current.filter((item) => item !== permission)
        : [...current, permission]
    ));
  };

  const addGrant = () => {
    if (selectedPermissions.length === 0) {
      setNotice("至少选择一个权限。");
      return;
    }
    if (principalType !== "COMPANY" && !principalId) {
      setNotice("请选择授权对象。");
      return;
    }
    const key = `${principalType}:${principalType === "COMPANY" ? "" : principalId}`;
    const next: GrantDraft = {
      key,
      principalType,
      principalId: principalType === "COMPANY" ? "" : principalId,
      permissions: selectedPermissions,
    };
    setGrants((current) => [next, ...current.filter((item) => item.key !== key)]);
    setNotice("");
  };

  const save = async () => {
    setSaving(true);
    setNotice("");
    try {
      const response = await fetch(`/agents/${encodeURIComponent(agentId)}/access-grants`, {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          grants: grants.map((item) => ({
            principalType: item.principalType,
            principalId: item.principalType === "COMPANY" ? null : item.principalId,
            permissions: item.permissions,
          })),
        }),
      });
      const { body } = await safeFetchJson<{ grants?: GrantRow[] }>(response);
      if (!response.ok || !body?.success) {
        throw new Error(body?.message ?? `HTTP ${response.status}`);
      }
      setGrants(groupGrants(body.data?.grants ?? []));
      setNotice("权限已保存。");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "权限保存失败");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="cici-openapi-keys-backdrop" role="presentation">
      <section
        className="cici-openapi-keys cici-access-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="cici-access-dialog-title"
      >
        <button type="button" className="cici-openapi-keys__close" onClick={onClose} aria-label="关闭">×</button>
        <header className="cici-openapi-keys__header">
          <div>
            <h2 id="cici-access-dialog-title">权限管理</h2>
            <p>{agentName} · {agentId}</p>
          </div>
          <div className="cici-access-dialog__summary" aria-label="当前授权摘要">
            <span>{grants.length} 个对象</span>
            <span>{totalPermissions} 项权限</span>
          </div>
        </header>

        {notice ? <p className="cici-openapi-keys__notice">{notice}</p> : null}

        <div className="cici-access-dialog__body">
          <section className="cici-access-dialog__composer" aria-label="新增授权">
            <header className="cici-access-dialog__section-head">
              <div>
                <strong>新增授权</strong>
                <span>选择对象后勾选允许动作，加入列表后统一保存。</span>
              </div>
              <button type="button" className="cici-builder__action cici-builder__action--ghost" onClick={addGrant}>
                加入列表
              </button>
            </header>

            <div className="cici-access-dialog__target-row">
              <label>
                <span>授权对象</span>
                <select value={principalType} onChange={(event) => setPrincipalType(event.target.value as GrantDraft["principalType"])}>
                  <option value="COMPANY">全公司</option>
                  <option value="USER">指定成员</option>
                  <option value="SYSTEM_ROLE">系统角色</option>
                </select>
              </label>
              {principalType === "USER" ? (
                <label>
                  <span>成员</span>
                  <select value={principalId} onChange={(event) => setPrincipalId(event.target.value)}>
                    {activeUsers.map((user) => (
                      <option key={user.id} value={user.id}>{user.nickname || user.mobile || user.id}</option>
                    ))}
                  </select>
                </label>
              ) : null}
              {principalType === "SYSTEM_ROLE" ? (
                <label>
                  <span>角色</span>
                  <select value={principalId} onChange={(event) => setPrincipalId(event.target.value)}>
                    <option value="ORG_USER">普通成员</option>
                    <option value="ORG_ADMIN">组织管理员</option>
                  </select>
                </label>
              ) : null}
            </div>

            <fieldset className="cici-access-dialog__permission-matrix">
              <legend>权限动作</legend>
              <div className="cici-access-dialog__checks">
                {PERMISSIONS.map(([permission, label]) => {
                  const checked = selectedPermissions.includes(permission);
                  return (
                    <label key={permission} className={checked ? "is-selected" : undefined}>
                      <input
                        type="checkbox"
                        checked={checked}
                        onChange={() => togglePermission(permission)}
                      />
                      <span>{label}</span>
                      <small>{permission}</small>
                    </label>
                  );
                })}
              </div>
            </fieldset>
          </section>

          <section className="cici-access-dialog__grants" aria-label="当前授权">
            <header className="cici-access-dialog__section-head">
              <div>
                <strong>当前授权</strong>
                <span>管理员与 owner 保留隐式全权限，不需要写入显式授权。</span>
              </div>
            </header>

            <table className="cici-openapi-keys__table cici-access-dialog__table">
              <thead>
                <tr>
                  <th>对象</th>
                  <th>来源</th>
                  <th>权限</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {loading ? (
                  <tr><td colSpan={4}>加载中...</td></tr>
                ) : grants.length === 0 ? (
                  <tr><td colSpan={4}>暂无显式授权；管理员与 owner 仍有隐式权限。</td></tr>
                ) : grants.map((row) => (
                  <tr key={row.key}>
                    <td>
                      <span className="cici-access-dialog__principal">{principalLabel(row, users)}</span>
                      <small>{row.principalType}</small>
                    </td>
                    <td>{principalTypeLabel(row.principalType)}</td>
                    <td>
                      <div className="cici-access-dialog__permission-list">
                        {row.permissions.map((permission) => (
                          <span key={permission}>{permissionLabel(permission)}</span>
                        ))}
                      </div>
                    </td>
                    <td>
                      <button
                        type="button"
                        className="cici-openapi-keys__inline-copy"
                        onClick={() => setGrants((current) => current.filter((item) => item.key !== row.key))}
                      >
                        移除
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>
        </div>

        <footer className="cici-modal__footer cici-access-dialog__footer">
          <button type="button" className="cici-btn" onClick={onClose}>关闭</button>
          <button type="button" className="cici-btn cici-btn--primary" onClick={() => void save()} disabled={saving || loading}>
            {saving ? "保存中..." : "保存权限"}
          </button>
        </footer>
      </section>
    </div>
  );
}
