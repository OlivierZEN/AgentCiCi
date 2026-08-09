import { useEffect, useMemo, useState } from "react";
import { useAdminToken } from "../useAdminToken";
import { adminApi } from "../adminApi";

type LifecycleStatus = "ACTIVE" | "SUSPENDED" | "REVOKED" | string;

type ServicePrincipal = {
  principalId: string;
  publicId: string;
  displayName: string;
  principalType: "SERVICE";
  lifecycleStatus: LifecycleStatus;
  serviceKind: string;
  clientId: string;
  tokenAudience?: string;
  scopes?: string[];
  availableScopes?: string[];
  lastRotatedAt?: string;
  createdAt?: string;
  ownerPublicId?: string;
  ownerDisplayName?: string;
};

type RotateSecretResult = {
  principalId: string;
  clientId: string;
  clientSecret: string;
  lastRotatedAt?: string;
  credentialNotice?: string;
};

type DevAutopilotTeam = {
  enabled: boolean;
  actualState: string;
  resources: Array<{ logicalRole: string; resourceType: string; displayName: string; externalId: string; primary: boolean }>;
};

type TeamResourceResult = RotateSecretResult & {
  resource: { logicalRole: string; resourceType: string; displayName: string; externalId: string };
};

const dateTimeFormatter = new Intl.DateTimeFormat("zh-CN", {
  timeZone: "Asia/Shanghai",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
  hour12: false,
});

export const servicePrincipalPresentation = {
  statusLabel(status: LifecycleStatus) {
    if (status === "ACTIVE") return "有效";
    if (status === "SUSPENDED") return "已暂停";
    if (status === "REVOKED") return "已撤销";
    return status || "未知";
  },
  statusTone(status: LifecycleStatus) {
    if (status === "ACTIVE") return "active";
    if (status === "SUSPENDED") return "suspended";
    if (status === "REVOKED") return "revoked";
    return "unknown";
  },
  canRotate(status: LifecycleStatus) {
    return status === "ACTIVE";
  },
  canRenameClientId(status: LifecycleStatus) {
    return status !== "REVOKED";
  },
  canManageScopes(status: LifecycleStatus) {
    return status !== "REVOKED";
  },
  normalizeScopes(scopes?: string[]) {
    return [...new Set((scopes ?? []).map((scope) => scope.trim()).filter(Boolean))].sort();
  },
  sameScopes(left?: string[], right?: string[]) {
    const normalizedLeft = this.normalizeScopes(left);
    const normalizedRight = this.normalizeScopes(right);
    return normalizedLeft.length === normalizedRight.length
      && normalizedLeft.every((scope, index) => scope === normalizedRight[index]);
  },
  isValidClientId(value: string) {
    return /^[a-z0-9][a-z0-9-]{2,127}$/.test(value);
  },
  serviceKindLabel(kind: string) {
    if (kind === "DEVELOPER") return "开发智能体";
    if (kind === "PRODUCT_MANAGER") return "产品经理智能体";
    return kind || "服务主体";
  },
  ownerLabel(item: ServicePrincipal) {
    return item.ownerDisplayName || item.ownerPublicId || "未设置负责人";
  },
};

function formatDateTime(value?: string) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : dateTimeFormatter.format(date).replace(/\//g, "-");
}

function initial(name: string) {
  return name.trim().slice(0, 1) || "机";
}

export default function AdminServicePrincipalsPage() {
  const token = useAdminToken();
  const [items, setItems] = useState<ServicePrincipal[]>([]);
  const [selectedId, setSelectedId] = useState("");
  const [notice, setNotice] = useState("");
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [confirmAction, setConfirmAction] = useState<"rename-client-id" | "scopes" | "rotate" | "suspend" | "activate" | null>(null);
  const [replacementClientId, setReplacementClientId] = useState("");
  const [replacementScopes, setReplacementScopes] = useState<string[]>([]);
  const [oneTimeSecret, setOneTimeSecret] = useState<RotateSecretResult | null>(null);
  const [devAutopilotTeam, setDevAutopilotTeam] = useState<DevAutopilotTeam | null>(null);
  const [newTeamMemberName, setNewTeamMemberName] = useState("");
  const [newTeamMemberRole, setNewTeamMemberRole] = useState<"product-managers" | "developers">("developers");

  const selected = useMemo(
    () => items.find((item) => item.principalId === selectedId) ?? items[0] ?? null,
    [items, selectedId],
  );
  const hasProductManager = devAutopilotTeam?.resources.some((item) => item.logicalRole === "product_manager" && item.resourceType === "SERVICE_PRINCIPAL" && item.primary) ?? false;

  const load = async (options?: { quiet?: boolean }) => {
    if (!options?.quiet) setLoading(true);
    setNotice("");
    try {
      const [res, teamRes] = await Promise.all([
        fetch(adminApi.servicePrincipals(), { headers: { Authorization: `Bearer ${token}` } }),
        fetch(adminApi.devAutopilotTeam(), { headers: { Authorization: `Bearer ${token}` } }),
      ]);
      const [json, teamJson] = await Promise.all([res.json(), teamRes.json()]);
      if (!res.ok || !json.success) {
        setNotice(json.message ?? "机器主体加载失败");
        return;
      }
      const next = (json.data ?? []) as ServicePrincipal[];
      setItems(next);
      setSelectedId((current) => next.some((item) => item.principalId === current) ? current : (next[0]?.principalId ?? ""));
      setDevAutopilotTeam(teamRes.ok && teamJson.success ? teamJson.data as DevAutopilotTeam : null);
    } catch {
      setNotice("机器主体加载失败，请检查网络后重试");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, [token]);

  useEffect(() => {
    setOneTimeSecret((current) => current?.principalId === selected?.principalId ? current : null);
    setConfirmAction(null);
    setReplacementClientId("");
    setReplacementScopes([]);
  }, [selected?.principalId]);

  const updateLifecycle = async (action: "suspend" | "activate") => {
    if (!selected) return;
    setSubmitting(true);
    try {
      const res = await fetch(adminApi.servicePrincipals(`/${encodeURIComponent(selected.principalId)}/${action}`), {
        method: "POST",
        headers: { Authorization: `Bearer ${token}` },
      });
      const json = await res.json();
      if (!res.ok || !json.success) {
        setNotice(json.message ?? "机器主体状态更新失败");
        return;
      }
      setNotice(action === "suspend" ? "机器主体已暂停，已有访问令牌将不能再用于新调用。" : "机器主体已恢复有效状态。");
      await load({ quiet: true });
    } catch {
      setNotice("机器主体状态更新失败，请稍后重试");
    } finally {
      setSubmitting(false);
      setConfirmAction(null);
    }
  };

  const rotateSecret = async () => {
    if (!selected) return;
    setSubmitting(true);
    try {
      const res = await fetch(adminApi.servicePrincipals(`/${encodeURIComponent(selected.principalId)}/rotate-secret`), {
        method: "POST",
        headers: { Authorization: `Bearer ${token}` },
      });
      const json = await res.json();
      if (!res.ok || !json.success) {
        setNotice(json.message ?? "Client Secret 轮换失败");
        return;
      }
      const result = json.data as RotateSecretResult;
      setOneTimeSecret(result);
      setNotice("新的 Client Secret 已生成；请立即保存到受管密钥库。");
      await load({ quiet: true });
    } catch {
      setNotice("Client Secret 轮换失败，请稍后重试");
    } finally {
      setSubmitting(false);
      setConfirmAction(null);
    }
  };

  const renameClientId = async () => {
    if (!selected || !servicePrincipalPresentation.isValidClientId(replacementClientId)) return;
    setSubmitting(true);
    try {
      const res = await fetch(adminApi.servicePrincipals(`/${encodeURIComponent(selected.principalId)}/rename-client-id`), {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({ clientId: replacementClientId }),
      });
      const json = await res.json();
      if (!res.ok || !json.success) {
        setNotice(json.message ?? "Client ID 更新失败");
        return;
      }
      setNotice(`“${selected.displayName}”的 Client ID 已更新为 ${json.data?.clientId ?? replacementClientId}；现有 Client Secret 保持不变。`);
      await load({ quiet: true });
    } catch {
      setNotice("Client ID 更新失败，请稍后重试");
    } finally {
      setSubmitting(false);
      setConfirmAction(null);
      setReplacementClientId("");
    }
  };

  const updateScopes = async () => {
    if (!selected) return;
    const scopes = servicePrincipalPresentation.normalizeScopes(replacementScopes);
    if (scopes.length === 0 || servicePrincipalPresentation.sameScopes(scopes, selected.scopes)) return;
    setSubmitting(true);
    try {
      const res = await fetch(adminApi.servicePrincipals(`/${encodeURIComponent(selected.principalId)}/scopes`), {
        method: "PUT",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({ scopes }),
      });
      const json = await res.json();
      if (!res.ok || !json.success) {
        setNotice(json.message ?? "授权范围更新失败");
        return;
      }
      setNotice(`“${selected.displayName}”的授权范围已更新；后续新签发令牌将使用新范围。`);
      await load({ quiet: true });
    } catch {
      setNotice("授权范围更新失败，请稍后重试");
    } finally {
      setSubmitting(false);
      setConfirmAction(null);
      setReplacementScopes([]);
    }
  };

  const openScopeEditor = () => {
    if (!selected) return;
    setReplacementScopes(servicePrincipalPresentation.normalizeScopes(selected.scopes));
    setConfirmAction("scopes");
  };

  const createDevAutopilotTeamMember = async () => {
    if (!newTeamMemberName.trim()) return;
    setSubmitting(true);
    setNotice("");
    try {
      const res = await fetch(adminApi.devAutopilotTeam(`/${newTeamMemberRole}`), {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({ displayName: newTeamMemberName.trim() }),
      });
      const json = await res.json();
      if (!res.ok || !json.success) {
        setNotice(json.message ?? "研发团队机器主体创建失败");
        return;
      }
      const result = json.data as TeamResourceResult;
      await load({ quiet: true });
      setSelectedId(result.principalId);
      setOneTimeSecret(result);
      setNewTeamMemberName("");
      setNotice(`“${result.resource.displayName}”已创建。Client Secret 仅本次显示，请立即写入受管密钥库。`);
    } catch {
      setNotice("研发团队机器主体创建失败，请稍后重试");
    } finally {
      setSubmitting(false);
    }
  };

  const copySecret = async () => {
    if (!oneTimeSecret) return;
    try {
      await navigator.clipboard.writeText(oneTimeSecret.clientSecret);
      setNotice("新密钥已复制；请粘贴到受管密钥库后清除此显示。");
    } catch {
      setNotice("复制失败，请手动复制。页面关闭或切换主体后将无法再次显示该密钥。");
    }
  };

  const runConfirmedAction = () => {
    if (confirmAction === "rename-client-id") void renameClientId();
    if (confirmAction === "scopes") void updateScopes();
    if (confirmAction === "rotate") void rotateSecret();
    if (confirmAction === "suspend") void updateLifecycle("suspend");
    if (confirmAction === "activate") void updateLifecycle("activate");
  };

  return (
    <div className="admin-page service-principals-page">
      <header className="service-principals-page__header">
        <div>
          <p className="service-principals-page__eyebrow">身份与访问 / MACHINE PRINCIPALS</p>
          <h1>机器主体</h1>
          <p className="subtle">由人类负责人委托和治理的 SERVICE 身份。Client Secret 不会被保存或回显。</p>
        </div>
        <button type="button" className="secondary service-principals-page__refresh" onClick={() => void load()} disabled={loading || submitting}>
          {loading ? "加载中…" : "刷新列表"}
        </button>
      </header>

      {notice && <p className="notice service-principals-page__notice" role="status">{notice}</p>}

      <section className="service-principals-team" aria-labelledby="devautopilot-team-title">
        <div>
          <p className="service-principals-page__eyebrow">DEVAUTOPILOT TEAM</p>
          <h2 id="devautopilot-team-title">研发交付团队</h2>
          {!devAutopilotTeam?.enabled ? (
            <p className="subtle">当前租户尚未开通 DevAutopilot。请联系平台运营先完成应用开通。</p>
          ) : devAutopilotTeam.actualState !== "ACTIVE" ? (
            <p className="subtle">DevAutopilot 当前为 {servicePrincipalPresentation.statusLabel(devAutopilotTeam.actualState)}，恢复应用后才能管理团队主体。</p>
          ) : (
            <p className="subtle">名称由本租户定义，负责人自动绑定为当前管理员；系统生成技术标识和模板最小权限。</p>
          )}
        </div>
        <div className="service-principals-team__form">
          <label>
            <span>主体类型</span>
            <select value={newTeamMemberRole} onChange={(event) => setNewTeamMemberRole(event.target.value as "product-managers" | "developers")} disabled={submitting || devAutopilotTeam?.actualState !== "ACTIVE"}>
              <option value="developers">开发者机器主体</option>
              <option value="product-managers" disabled={hasProductManager}>产品经理智能体</option>
            </select>
          </label>
          <label>
            <span>显示名称</span>
            <input value={newTeamMemberName} onChange={(event) => setNewTeamMemberName(event.target.value)} placeholder={newTeamMemberRole === "developers" ? "例如：悟空" : "例如：大乔"} disabled={submitting || devAutopilotTeam?.actualState !== "ACTIVE"} />
          </label>
          <button type="button" className="service-principal-button--primary" disabled={submitting || devAutopilotTeam?.actualState !== "ACTIVE" || !newTeamMemberName.trim() || (newTeamMemberRole === "product-managers" && hasProductManager)} onClick={() => void createDevAutopilotTeamMember()}>
            {submitting ? "创建中…" : newTeamMemberRole === "developers" ? "新增开发者" : "初始化产品经理"}
          </button>
        </div>
      </section>

      <div className="service-principals-layout">
        <aside className="service-principal-list" aria-label="机器主体列表">
          <div className="service-principal-list__head">
            <strong>机器主体</strong>
            <span>{items.length} 个已投影主体</span>
          </div>
          {loading ? (
            <p className="service-principal-list__empty">正在加载身份数据…</p>
          ) : items.length === 0 ? (
            <p className="service-principal-list__empty">当前租户还没有已投影的机器主体。</p>
          ) : items.map((item) => (
            <button
              type="button"
              key={item.principalId}
              className={`service-principal-list__item ${selected?.principalId === item.principalId ? "is-selected" : ""}`}
              onClick={() => setSelectedId(item.principalId)}
            >
              <span className="service-principal-list__avatar" aria-hidden>{initial(item.displayName)}</span>
              <span className="service-principal-list__identity">
                <strong>{item.displayName}</strong>
                <small>{item.clientId}</small>
              </span>
              <span className={`service-principal-status is-${servicePrincipalPresentation.statusTone(item.lifecycleStatus)}`}>
                {servicePrincipalPresentation.statusLabel(item.lifecycleStatus)}
              </span>
            </button>
          ))}
        </aside>

        <section className="service-principal-detail" aria-live="polite">
          {!selected && !loading ? (
            <div className="service-principal-detail__empty">选择一个机器主体，查看其访问与密钥治理信息。</div>
          ) : selected && (
            <>
              <div className="service-principal-detail__hero">
                <span className="service-principal-detail__avatar" aria-hidden>{initial(selected.displayName)}</span>
                <div>
                  <p className="service-principals-page__eyebrow">SERVICE IDENTITY</p>
                  <h2>{selected.displayName}</h2>
                  <p>{servicePrincipalPresentation.serviceKindLabel(selected.serviceKind)} · 人类负责人：{servicePrincipalPresentation.ownerLabel(selected)}</p>
                </div>
                <span className={`service-principal-status is-${servicePrincipalPresentation.statusTone(selected.lifecycleStatus)}`}>
                  {servicePrincipalPresentation.statusLabel(selected.lifecycleStatus)}
                </span>
              </div>

              <div className="service-principal-detail__actions">
                {servicePrincipalPresentation.canRenameClientId(selected.lifecycleStatus) && (
                  <button type="button" className="secondary" onClick={() => setConfirmAction("rename-client-id")} disabled={submitting}>
                    变更 Client ID
                  </button>
                )}
                {servicePrincipalPresentation.canManageScopes(selected.lifecycleStatus) && (
                  <button type="button" className="secondary" onClick={openScopeEditor} disabled={submitting}>
                    调整授权范围
                  </button>
                )}
                {selected.lifecycleStatus === "ACTIVE" && (
                  <button type="button" className="secondary" onClick={() => setConfirmAction("suspend")} disabled={submitting}>
                    暂停主体
                  </button>
                )}
                {selected.lifecycleStatus === "SUSPENDED" && (
                  <button type="button" className="service-principal-button--primary" onClick={() => setConfirmAction("activate")} disabled={submitting}>
                    恢复主体
                  </button>
                )}
              </div>

              <div className="service-principal-detail__grid">
                <div className="service-principal-info-card">
                  <h3>身份信息</h3>
                  <dl className="service-principal-info-list">
                    <div><dt>Client ID</dt><dd><code>{selected.clientId}</code></dd></div>
                    <div><dt>主体编号</dt><dd><code>{selected.publicId}</code></dd></div>
                    <div><dt>令牌受众</dt><dd><code>{selected.tokenAudience || "—"}</code></dd></div>
                    <div><dt>人类负责人</dt><dd>{servicePrincipalPresentation.ownerLabel(selected)}</dd></div>
                    <div><dt>创建时间</dt><dd>{formatDateTime(selected.createdAt)}</dd></div>
                    <div><dt>上次轮换</dt><dd>{formatDateTime(selected.lastRotatedAt)}</dd></div>
                  </dl>
                </div>

                <div className="service-principal-info-card">
                  <h3>已授权范围</h3>
                  <p className="subtle">此主体取得令牌后仅能以这些范围访问已授权资源。</p>
                  <div className="service-principal-scope-list">
                    {(selected.scopes ?? []).length > 0 ? selected.scopes?.map((scope) => <code key={scope}>{scope}</code>) : <span>未配置范围</span>}
                  </div>
                </div>
              </div>

              <section className="service-principal-secret-card">
                <div>
                  <p className="service-principals-page__eyebrow">CREDENTIAL ROTATION</p>
                  <h3>Client Secret</h3>
                  <p>出于安全原因，系统不会读取、保存或再次展示旧密钥。轮换会立即使旧密钥失效。</p>
                </div>
                {oneTimeSecret?.principalId === selected.principalId ? (
                  <div className="service-principal-secret-card__result">
                    <strong>仅本次显示的新密钥</strong>
                    <textarea value={oneTimeSecret.clientSecret} readOnly aria-label="仅本次显示的新 Client Secret" />
                    <div>
                      <button type="button" className="service-principal-button--primary" onClick={() => void copySecret()}>复制新密钥</button>
                      <button type="button" className="secondary" onClick={() => setOneTimeSecret(null)}>我已安全保存</button>
                    </div>
                  </div>
                ) : (
                  <div className="service-principal-secret-card__action">
                    <p>{servicePrincipalPresentation.canRotate(selected.lifecycleStatus) ? "请仅将新密钥写入受管密钥库或 CI/CD Secret，切勿发送至聊天、工单或提交到代码仓库。" : "主体处于非有效状态，恢复有效状态后才能轮换密钥。"}</p>
                    <button type="button" className="service-principal-button--primary" onClick={() => setConfirmAction("rotate")} disabled={!servicePrincipalPresentation.canRotate(selected.lifecycleStatus) || submitting}>
                      轮换 Client Secret
                    </button>
                  </div>
                )}
              </section>
            </>
          )}
        </section>
      </div>

      {confirmAction && selected && (
        <div className="service-principal-dialog-backdrop" role="presentation">
          <section className="service-principal-dialog" role="dialog" aria-modal="true" aria-labelledby="service-principal-dialog-title">
            <p className="service-principals-page__eyebrow">需要明确确认</p>
            <h2 id="service-principal-dialog-title">
              {confirmAction === "rename-client-id"
                ? "确认变更 Client ID？"
                : confirmAction === "scopes" ? "确认调整授权范围？"
                : confirmAction === "rotate" ? "确认轮换 Client Secret？" : confirmAction === "suspend" ? "确认暂停机器主体？" : "确认恢复机器主体？"}
            </h2>
            <p>
              {confirmAction === "rename-client-id"
                ? `变更后，旧 ID “${selected.clientId}”将立即失效。现有 Client Secret 保持不变；请同步更新所有调用方的配置。`
                : confirmAction === "scopes"
                  ? `提交后，“${selected.displayName}”后续新签发令牌将使用下列完整授权范围；Client ID 和 Client Secret 均不改变。`
                : confirmAction === "rotate"
                ? `“${selected.displayName}”的旧 Client Secret 将立即失效。新密钥仅会在下一步显示一次。`
                : confirmAction === "suspend"
                  ? `暂停后，“${selected.displayName}”将不能再获取新的访问令牌或执行受授权调用。`
                  : `恢复后，“${selected.displayName}”可再次按已授权范围获取访问令牌。`}
            </p>
            {confirmAction === "rename-client-id" && (
              <label className="service-principal-dialog__field">
                <span>新的 Client ID</span>
                <input
                  value={replacementClientId}
                  onChange={(event) => setReplacementClientId(event.target.value.trim())}
                  placeholder="例如 dev-autopilot-developer-wukong"
                  autoComplete="off"
                  spellCheck={false}
                  autoFocus
                />
                <small>仅允许小写字母、数字和连字符，长度 3–128 位。</small>
              </label>
            )}
            {confirmAction === "scopes" && (
              <fieldset className="service-principal-scope-editor">
                <legend>完整目标范围</legend>
                {servicePrincipalPresentation.normalizeScopes(selected.availableScopes).map((scope) => (
                  <label key={scope}>
                    <input
                      type="checkbox"
                      checked={replacementScopes.includes(scope)}
                      onChange={() => setReplacementScopes((current) => current.includes(scope)
                        ? current.filter((item) => item !== scope)
                        : [...current, scope])}
                    />
                    <code>{scope}</code>
                  </label>
                ))}
              </fieldset>
            )}
            <div className="service-principal-dialog__actions">
              <button type="button" className="secondary" onClick={() => setConfirmAction(null)} disabled={submitting}>取消</button>
              <button type="button" className="service-principal-button--primary" onClick={runConfirmedAction} disabled={submitting
                || (confirmAction === "rename-client-id" && !servicePrincipalPresentation.isValidClientId(replacementClientId))
                || (confirmAction === "scopes" && (replacementScopes.length === 0 || servicePrincipalPresentation.sameScopes(replacementScopes, selected.scopes)))}>
                {submitting ? "处理中…" : "确认执行"}
              </button>
            </div>
          </section>
        </div>
      )}
    </div>
  );
}
