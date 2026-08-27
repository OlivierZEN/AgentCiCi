import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { adminApi } from "../adminApi";
import { useAdminToken } from "../useAdminToken";

type ApiEnvelope<T> = {
  success?: boolean;
  message?: string;
  data?: T;
};

type EmbedAppConfig = {
  enabled: boolean;
  allowedOrigins: string[];
  runAsUserId: string;
  sourceBindings: Record<string, unknown>;
  scopeOverrides: string[];
  tokenTtlSeconds: number;
  updatedAt?: string;
};

type EmbedApp = {
  appCode: string;
  name: string;
  description: string;
  platformStatus: string;
  status: "ENABLED" | "DISABLED" | "UNCONFIGURED";
  embedMode: string;
  stableSdkUrl: string;
  versionedSdkUrl: string;
  embedUrl: string;
  requiredScopes: string[];
  supportedSources: string[];
  version: string;
  defaultTokenTtlSeconds: number;
  doc?: {
    cloudccVueExample?: string;
    postMessageEvents?: string[];
  };
  config: EmbedAppConfig;
};

type UserRow = {
  id: string;
  mobile: string;
  roleCode: string;
  nickname?: string;
  memberStatus?: string;
};

type SessionRow = {
  sessionId: string;
  status: string;
  source: string;
  objectType: string;
  objectId: string;
  recordName: string;
  customerName: string;
  parentOrigin: string;
  traceId: string;
  externalUserId: string;
  externalTenantId?: string;
  agentId?: string;
  createdAt: string;
  updatedAt: string;
};

type TokenIssue = {
  embedToken: string;
  expiresAt: string;
  embedUrl: string;
  permissions: string[];
  ttlSeconds: number;
};

type DetailTab = "overview" | "config" | "docs" | "debug" | "logs";

const tabs: Array<{ key: DetailTab; label: string }> = [
  { key: "overview", label: "概览" },
  { key: "config", label: "接入配置" },
  { key: "docs", label: "SDK / iframe" },
  { key: "debug", label: "调试" },
  { key: "logs", label: "调用日志" },
];

function statusText(status: EmbedApp["status"]) {
  if (status === "ENABLED") return "已启用";
  if (status === "DISABLED") return "已停用";
  return "未配置";
}

function modeText(mode: string) {
  if (mode === "sdk_iframe") return "SDK + iframe";
  if (mode === "iframe_only") return "iframe";
  if (mode === "api_only") return "API";
  return mode;
}

function formatTime(value?: string) {
  if (!value) return "暂无";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString("zh-CN", { hour12: false });
}

async function requestJson<T>(input: RequestInfo, init?: RequestInit): Promise<T> {
  const res = await fetch(input, init);
  const json = (await res.json()) as ApiEnvelope<T>;
  if (!res.ok || !json.success) {
    throw new Error(json.message ?? "请求失败");
  }
  return json.data as T;
}

function splitLines(value: string) {
  return value
    .split(/\r?\n/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function codeBlock(value: string) {
  return value.trim();
}

export default function AdminEmbedAppsPage() {
  const token = useAdminToken();
  const nav = useNavigate();
  const { appCode } = useParams();
  const selectedAppCode = appCode ?? "";
  const [apps, setApps] = useState<EmbedApp[]>([]);
  const [detail, setDetail] = useState<EmbedApp | null>(null);
  const [users, setUsers] = useState<UserRow[]>([]);
  const [sessions, setSessions] = useState<SessionRow[]>([]);
  const [activeTab, setActiveTab] = useState<DetailTab>("overview");
  const [notice, setNotice] = useState("");
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [debugging, setDebugging] = useState(false);
  const [configForm, setConfigForm] = useState({
    enabled: true,
    allowedOrigins: "",
    runAsUserId: "",
    scopeOverrides: [] as string[],
    tokenTtlSeconds: 900,
    externalTenantId: "",
    agentId: "cici-system",
  });
  const [debugForm, setDebugForm] = useState({
    source: "cloudcc",
    parentOrigin: "http://localhost:5173",
    objectType: "Opportunity",
    objectId: "debug-001",
    recordName: "调试商机",
    customerName: "调试客户",
    externalUserId: "debug-user",
    displayName: "调试用户",
    externalTenantId: "",
  });
  const [tokenIssue, setTokenIssue] = useState<TokenIssue | null>(null);

  const authHeaders = useMemo(() => ({ Authorization: `Bearer ${token}` }), [token]);
  const baseUrl = useMemo(() => (typeof window === "undefined" ? "" : window.location.origin), []);

  const loadApps = async () => {
    setLoading(true);
    setNotice("");
    try {
      const list = await requestJson<EmbedApp[]>("/embed/v1/admin/apps", { headers: authHeaders });
      setApps(list ?? []);
      if (!selectedAppCode && list?.length) {
        nav(`/admin/embed-apps/${encodeURIComponent(list[0].appCode)}`, { replace: true });
      }
    } catch (err) {
      setNotice((err as Error).message);
    } finally {
      setLoading(false);
    }
  };

  const loadDetail = async (code: string) => {
    if (!code) return;
    setLoading(true);
    setNotice("");
    try {
      const [app, sessionList] = await Promise.all([
        requestJson<EmbedApp>(`/embed/v1/admin/apps/${encodeURIComponent(code)}`, { headers: authHeaders }),
        requestJson<SessionRow[]>(`/embed/v1/admin/apps/${encodeURIComponent(code)}/sessions?limit=20`, { headers: authHeaders }),
      ]);
      setDetail(app);
      setSessions(sessionList ?? []);
      setConfigForm({
        enabled: app.config.enabled,
        allowedOrigins: (app.config.allowedOrigins ?? []).join("\n"),
        runAsUserId: app.config.runAsUserId ?? "",
        scopeOverrides: app.config.scopeOverrides?.length ? app.config.scopeOverrides : app.requiredScopes,
        tokenTtlSeconds: app.config.tokenTtlSeconds ?? app.defaultTokenTtlSeconds,
        externalTenantId: String((app.config.sourceBindings?.cloudcc as Record<string, unknown> | undefined)?.externalTenantId ?? ""),
        agentId: String((app.config.sourceBindings?.cloudcc as Record<string, unknown> | undefined)?.agentId ?? "cici-system"),
      });
      setDebugForm((prev) => ({
        ...prev,
        source: app.supportedSources[0] ?? prev.source,
        parentOrigin: app.config.allowedOrigins?.[0] ?? prev.parentOrigin,
        externalTenantId: String((app.config.sourceBindings?.cloudcc as Record<string, unknown> | undefined)?.externalTenantId ?? prev.externalTenantId),
      }));
      setTokenIssue(null);
    } catch (err) {
      setNotice((err as Error).message);
    } finally {
      setLoading(false);
    }
  };

  const loadUsers = async () => {
    try {
      const list = await requestJson<UserRow[]>(adminApi.users(), { headers: authHeaders });
      setUsers((list ?? []).filter((user) => user.memberStatus !== "SUSPENDED"));
    } catch {
      setUsers([]);
    }
  };

  useEffect(() => {
    void loadApps();
  }, [token]);

  useEffect(() => {
    if (selectedAppCode) {
      void loadDetail(selectedAppCode);
    }
  }, [selectedAppCode, token]);

  useEffect(() => {
    void loadUsers();
  }, [token]);

  const saveConfig = async () => {
    if (!detail) return;
    setSaving(true);
    setNotice("");
    try {
      const saved = await requestJson<EmbedApp>(`/embed/v1/admin/apps/${encodeURIComponent(detail.appCode)}/config`, {
        method: "PUT",
        headers: { "Content-Type": "application/json", ...authHeaders },
        body: JSON.stringify({
          enabled: configForm.enabled,
          allowedOrigins: splitLines(configForm.allowedOrigins),
          runAsUserId: configForm.runAsUserId,
          scopeOverrides: configForm.scopeOverrides,
          tokenTtlSeconds: Number(configForm.tokenTtlSeconds),
          sourceBindings: detail.appCode === "sisi" ? {
            cloudcc: {
              externalTenantId: configForm.externalTenantId.trim(),
              agentId: configForm.agentId.trim(),
            },
          } : detail.config.sourceBindings,
        }),
      });
      setDetail(saved);
      setNotice("配置已保存");
      await loadApps();
    } catch (err) {
      setNotice(`保存失败：${(err as Error).message}`);
    } finally {
      setSaving(false);
    }
  };

  const issueDebugToken = async () => {
    if (!detail) return;
    setDebugging(true);
    setNotice("");
    setTokenIssue(null);
    try {
      const issued = await requestJson<TokenIssue>(`/embed/v1/admin/apps/${encodeURIComponent(detail.appCode)}/debug-token`, {
        method: "POST",
        headers: { "Content-Type": "application/json", ...authHeaders },
        body: JSON.stringify({
          source: debugForm.source,
          parentOrigin: debugForm.parentOrigin,
          externalTenantId: debugForm.externalTenantId,
          user: {
            externalUserId: debugForm.externalUserId,
            displayName: debugForm.displayName,
          },
          context: {
            objectType: debugForm.objectType,
            objectId: debugForm.objectId,
            recordName: debugForm.recordName,
            customerName: debugForm.customerName,
          },
          permissions: configForm.scopeOverrides,
          ttlSeconds: Number(configForm.tokenTtlSeconds),
        }),
      });
      setTokenIssue(issued);
      setNotice("已生成一次性调试 token");
    } catch (err) {
      setNotice(`调试失败：${(err as Error).message}`);
    } finally {
      setDebugging(false);
    }
  };

  const toggleScope = (scope: string) => {
    setConfigForm((prev) => {
      const exists = prev.scopeOverrides.includes(scope);
      return {
        ...prev,
        scopeOverrides: exists ? prev.scopeOverrides.filter((item) => item !== scope) : [...prev.scopeOverrides, scope],
      };
    });
  };

  const selectedApp = detail ?? apps.find((app) => app.appCode === selectedAppCode) ?? apps[0] ?? null;
  const cloudccExample = selectedApp
    ? selectedApp.appCode === "sisi" ? codeBlock(`
<div id="sisi-agent" style="height: 720px"></div>
<script src="${baseUrl}${selectedApp.stableSdkUrl}"></script>
<script>
  const sisi = window.AgentCiCiSisi.create({
    mode: "page", // 切换为 float 即使用右侧悬浮面板
    container: "#sisi-agent",
    tokenProvider: () => fetchEmbedTokenFromCloudCCServer({
      cloudccOrgId: currentOrg.id,
      username: currentUser.username,
      objectType: record.objectType,
      objectId: record.id
    })
  });
</script>
`) : codeBlock(`
<script src="${baseUrl}${selectedApp.stableSdkUrl}"></script>
<script>
  const embedToken = await fetchEmbedTokenFromCloudCCServer();
  window.AgentCiCiMeeting.open({
    token: embedToken,
    mode: "drawer",
    width: 960,
    locale: "zh-CN",
    context: {
      source: "cloudcc",
      objectType: "Opportunity",
      objectId: record.id,
      recordName: record.name,
      customerName: account.name
    }
  });
</script>
`)
    : "";
  const tokenCurl = selectedApp
    ? codeBlock(`
curl -X POST "${baseUrl}/embed/v1/apps/${selectedApp.appCode}/tokens" \\
  -H "X-Cici-Api-Key: cici_ak_live_xxx" \\
  -H "Content-Type: application/json" \\
  -d '{
    "source": "cloudcc",
    "parentOrigin": "https://crm.example.com",
    "externalTenantId": "cloudcc-org-id",
    "user": { "externalUserId": "cloudcc-bound-username" },
    "context": {
      "objectType": "Opportunity",
      "objectId": "006xx000001",
      "recordName": "华东区续费商机",
      "customerName": "某某集团"
    },
    "permissions": ${selectedApp.appCode === "sisi" ? '["chat:read", "chat:write", "attachment:write", "voice:input"]' : '["meeting:start", "meeting:summary", "crm:writeback"]'},
    "ttlSeconds": 600
  }'
`)
    : "";

  return (
    <div className="admin-page embed-apps-page">
      <header className="embed-apps-header">
        <div>
          <p className="embed-apps-header__kicker">Embedded Apps</p>
          <h1>嵌入式智能应用</h1>
          <p className="subtle">统一管理可嵌入外部系统的标准能力、授权域名、调试 token 和调用记录。</p>
        </div>
        <button type="button" className="embed-apps-secondary" onClick={() => selectedAppCode && void loadDetail(selectedAppCode)} disabled={loading}>
          刷新
        </button>
      </header>

      {notice ? <p className="embed-apps-notice" aria-live="polite">{notice}</p> : null}

      <div className="embed-apps-layout">
        <section className="embed-apps-list" aria-label="嵌入式智能应用列表">
          <div className="embed-apps-list__head">
            <h2>应用目录</h2>
            <span>{apps.length} 项</span>
          </div>
          <table className="embed-apps-table">
            <colgroup>
              <col className="embed-apps-table__col-name" />
              <col className="embed-apps-table__col-mode" />
              <col className="embed-apps-table__col-status" />
            </colgroup>
            <thead>
              <tr>
                <th>应用</th>
                <th>接入方式</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              {apps.map((app) => (
                <tr key={app.appCode} className={app.appCode === selectedAppCode ? "is-selected" : ""}>
                  <td>
                    <Link to={`/admin/embed-apps/${encodeURIComponent(app.appCode)}`} className="embed-apps-table__name">
                      {app.name}
                    </Link>
                    <span className="embed-apps-table__code">{app.appCode}</span>
                  </td>
                  <td>{modeText(app.embedMode)}</td>
                  <td><span className={`embed-apps-status is-${app.status.toLowerCase()}`}>{statusText(app.status)}</span></td>
                </tr>
              ))}
              {!apps.length && (
                <tr>
                  <td colSpan={3}>{loading ? "加载中..." : "暂无嵌入式智能应用"}</td>
                </tr>
              )}
            </tbody>
          </table>
        </section>

        {selectedApp ? (
          <section className="embed-apps-detail" aria-label={`${selectedApp.name}详情`}>
            <div className="embed-apps-detail__head">
              <div>
                <h2>{selectedApp.name}</h2>
                <p>{selectedApp.description}</p>
              </div>
              <div className="embed-apps-detail__meta">
                <span>版本 {selectedApp.version}</span>
                <span>{modeText(selectedApp.embedMode)}</span>
              </div>
            </div>

            <div className="embed-apps-tabs" role="tablist" aria-label="嵌入式智能应用详情">
              {tabs.map((tab) => (
                <button
                  key={tab.key}
                  type="button"
                  className={activeTab === tab.key ? "is-active" : ""}
                  onClick={() => setActiveTab(tab.key)}
                  role="tab"
                  aria-selected={activeTab === tab.key}
                >
                  {tab.label}
                </button>
              ))}
            </div>

            {activeTab === "overview" && (
              <div className="embed-apps-section">
                <dl className="embed-apps-facts">
                  <div>
                    <dt>组织状态</dt>
                    <dd>{statusText(selectedApp.status)}</dd>
                  </div>
                  <div>
                    <dt>支持系统</dt>
                    <dd>{selectedApp.supportedSources.join(" / ")}</dd>
                  </div>
                  <div>
                    <dt>权限范围</dt>
                    <dd>{selectedApp.requiredScopes.join(", ")}</dd>
                  </div>
                  <div>
                    <dt>最近配置</dt>
                    <dd>{formatTime(selectedApp.config.updatedAt)}</dd>
                  </div>
                </dl>
                <div className="embed-apps-rule-list">
                  <p>{selectedApp.appCode === "sisi" ? "用于 CRM 记录页内与 AgentCiCi 受治理智能体持续对话、检索知识、上传附件、语音输入并执行工具。" : "用于 CRM 记录页内启动会议听记、生成 AI 纪要，并在用户确认后进入写回流程。"}</p>
                  <p>短期 token 绑定来源域名、业务对象和权限 scope，浏览器不接触长期 API Key。</p>
                  <p>后续新增客户摘要、工单助手或商机建议时，继续从此目录统一接入。</p>
                </div>
              </div>
            )}

            {activeTab === "config" && (
              <div className="embed-apps-section embed-apps-form">
                <label className="embed-apps-check">
                  <input
                    type="checkbox"
                    checked={configForm.enabled}
                    onChange={(event) => setConfigForm((prev) => ({ ...prev, enabled: event.target.checked }))}
                  />
                  <span>组织内启用此嵌入式智能应用</span>
                </label>
                <label>
                  <span>允许父页面 origin</span>
                  <textarea
                    rows={4}
                    value={configForm.allowedOrigins}
                    onChange={(event) => setConfigForm((prev) => ({ ...prev, allowedOrigins: event.target.value }))}
                    placeholder={"https://crm.example.com\nhttps://*.cloudcc.com"}
                  />
                  <small>每行一个 origin。为空时仅允许本地开发 origin。</small>
                </label>
                <div className="embed-apps-form__grid">
                  {selectedApp.appCode === "sisi" && <>
                    <label>
                      <span>CloudCC 组织 ID</span>
                      <input value={configForm.externalTenantId} onChange={(event) => setConfigForm((prev) => ({ ...prev, externalTenantId: event.target.value }))} placeholder="外部租户唯一标识" />
                    </label>
                    <label>
                      <span>映射智能体 ID</span>
                      <input value={configForm.agentId} onChange={(event) => setConfigForm((prev) => ({ ...prev, agentId: event.target.value }))} placeholder="cici-system" />
                    </label>
                  </>}
                  <label>
                    <span>默认 run-as 用户</span>
                    <select
                      value={configForm.runAsUserId}
                      onChange={(event) => setConfigForm((prev) => ({ ...prev, runAsUserId: event.target.value }))}
                    >
                      <option value="">签发方或当前管理员</option>
                      {users.map((user) => (
                        <option key={user.id} value={user.id}>{user.nickname || user.mobile}（{user.roleCode}）</option>
                      ))}
                    </select>
                  </label>
                  <label>
                    <span>Token TTL（秒）</span>
                    <input
                      type="number"
                      min={60}
                      max={1800}
                      value={configForm.tokenTtlSeconds}
                      onChange={(event) => setConfigForm((prev) => ({ ...prev, tokenTtlSeconds: Number(event.target.value) }))}
                    />
                  </label>
                </div>
                <fieldset className="embed-apps-scopes">
                  <legend>允许 scope</legend>
                  {selectedApp.requiredScopes.map((scope) => (
                    <label key={scope}>
                      <input type="checkbox" checked={configForm.scopeOverrides.includes(scope)} onChange={() => toggleScope(scope)} />
                      <span>{scope}</span>
                    </label>
                  ))}
                </fieldset>
                <div className="embed-apps-actions">
                  <button type="button" className="embed-apps-primary" disabled={saving} onClick={() => void saveConfig()}>
                    {saving ? "保存中..." : "保存配置"}
                  </button>
                </div>
              </div>
            )}

            {activeTab === "docs" && (
              <div className="embed-apps-section embed-apps-docs">
                <dl className="embed-apps-endpoints">
                  <div>
                    <dt>稳定 SDK</dt>
                    <dd>{baseUrl}{selectedApp.stableSdkUrl}</dd>
                  </div>
                  <div>
                    <dt>版本 SDK</dt>
                    <dd>{baseUrl}{selectedApp.versionedSdkUrl}</dd>
                  </div>
                  <div>
                    <dt>iframe</dt>
                    <dd>{baseUrl}{selectedApp.embedUrl}</dd>
                  </div>
                </dl>
                <h3>CloudCC Vue 页面示例</h3>
                <pre><code>{cloudccExample}</code></pre>
                <h3>服务端 token 签发</h3>
                <pre><code>{tokenCurl}</code></pre>
                <h3>postMessage 事件</h3>
                <p>{(selectedApp.doc?.postMessageEvents ?? []).join(" / ") || "后续嵌入页接入时补充事件表。"}</p>
              </div>
            )}

            {activeTab === "debug" && (
              <div className="embed-apps-section embed-apps-form">
                <div className="embed-apps-form__grid">
                  <label>
                    <span>来源系统</span>
                    <select value={debugForm.source} onChange={(event) => setDebugForm((prev) => ({ ...prev, source: event.target.value }))}>
                      {selectedApp.supportedSources.map((source) => <option key={source} value={source}>{source}</option>)}
                    </select>
                  </label>
                  <label>
                    <span>父页面 origin</span>
                    <input value={debugForm.parentOrigin} onChange={(event) => setDebugForm((prev) => ({ ...prev, parentOrigin: event.target.value }))} />
                  </label>
                  {selectedApp.appCode === "sisi" && <label>
                    <span>CloudCC 组织 ID</span>
                    <input value={debugForm.externalTenantId} onChange={(event) => setDebugForm((prev) => ({ ...prev, externalTenantId: event.target.value }))} />
                  </label>}
                  <label>
                    <span>对象类型</span>
                    <input value={debugForm.objectType} onChange={(event) => setDebugForm((prev) => ({ ...prev, objectType: event.target.value }))} />
                  </label>
                  <label>
                    <span>对象 ID</span>
                    <input value={debugForm.objectId} onChange={(event) => setDebugForm((prev) => ({ ...prev, objectId: event.target.value }))} />
                  </label>
                  <label>
                    <span>记录名称</span>
                    <input value={debugForm.recordName} onChange={(event) => setDebugForm((prev) => ({ ...prev, recordName: event.target.value }))} />
                  </label>
                  <label>
                    <span>客户名称</span>
                    <input value={debugForm.customerName} onChange={(event) => setDebugForm((prev) => ({ ...prev, customerName: event.target.value }))} />
                  </label>
                </div>
                <div className="embed-apps-actions">
                  <button type="button" className="embed-apps-primary" disabled={debugging} onClick={() => void issueDebugToken()}>
                    {debugging ? "生成中..." : "生成调试 token"}
                  </button>
                </div>
                {tokenIssue ? (
                  <div className="embed-apps-token">
                    <div>
                      <strong>调试 token</strong>
                      <span>过期时间 {formatTime(tokenIssue.expiresAt)}，权限 {tokenIssue.permissions.join(", ")}</span>
                    </div>
                    <textarea readOnly rows={4} value={tokenIssue.embedToken} onFocus={(event) => event.currentTarget.select()} />
                    <div className="embed-apps-preview">
                      <div>
                        <strong>iframe 预览</strong>
                        <span>使用当前一次性 token 加载嵌入页，真实录音仍需要浏览器授予麦克风权限。</span>
                      </div>
                      <iframe
                        title={`${selectedApp.name}嵌入页调试预览`}
                        src={selectedApp.appCode === "sisi"
                          ? `${baseUrl}${tokenIssue.embedUrl}?mode=page#token=${encodeURIComponent(tokenIssue.embedToken)}`
                          : `${baseUrl}${tokenIssue.embedUrl}?token=${encodeURIComponent(tokenIssue.embedToken)}&mode=admin-debug`}
                        allow="microphone"
                      />
                    </div>
                  </div>
                ) : null}
              </div>
            )}

            {activeTab === "logs" && (
              <div className="embed-apps-section">
                <table className="embed-apps-log-table">
                  <colgroup>
                    <col className="embed-apps-log-table__col-session" />
                    <col className="embed-apps-log-table__col-target" />
                    <col className="embed-apps-log-table__col-origin" />
                    <col className="embed-apps-log-table__col-status" />
                    <col className="embed-apps-log-table__col-time" />
                  </colgroup>
                  <thead>
                    <tr>
                      <th>Session</th>
                      <th>业务对象</th>
                      <th>来源</th>
                      <th>状态</th>
                      <th>更新时间</th>
                    </tr>
                  </thead>
                  <tbody>
                    {sessions.map((session) => (
                      <tr key={session.sessionId}>
                        <td>
                          <span className="embed-apps-table__code">{session.sessionId}</span>
                          {session.traceId ? <span className="embed-apps-table__sub">trace {session.traceId}</span> : null}
                        </td>
                        <td>
                          <span>{session.objectType} / {session.objectId}</span>
                          <span className="embed-apps-table__sub">{session.recordName || session.customerName || "未记录名称"}</span>
                        </td>
                        <td>
                          <span>{session.source}</span>
                          <span className="embed-apps-table__sub">{session.parentOrigin}</span>
                        </td>
                        <td>{session.status}</td>
                        <td>{formatTime(session.updatedAt)}</td>
                      </tr>
                    ))}
                    {!sessions.length && (
                      <tr>
                        <td colSpan={5}>暂无调用记录。生成调试 token 并创建 session 后会出现在这里。</td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        ) : (
          <section className="embed-apps-detail embed-apps-detail--empty">暂无可配置的嵌入式智能应用。</section>
        )}
      </div>
    </div>
  );
}
