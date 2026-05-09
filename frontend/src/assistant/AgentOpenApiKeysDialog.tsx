import { useEffect, useMemo, useState } from "react";

type AgentOpenApiKeysDialogProps = {
  open: boolean;
  agentId: string;
  agentName: string;
  token: string;
  onClose: () => void;
};

type ApiEnvelope<T> = {
  success: boolean;
  data?: T;
  message?: string;
};

type ApiKeyRow = {
  id: number;
  publicId: string;
  name: string;
  keyPrefix: string;
  status: string;
  runAsUserId: string;
  allowedIps: string[];
  rateLimitPerMinute: number;
  dailyQuota: number;
  maxPromptChars: number;
  maxResponseChars: number;
  allowStream: boolean;
  expiresAt?: string | null;
  lastUsedAt?: string | null;
  createdAt?: string | null;
  revokedAt?: string | null;
};

type ApiCallRow = {
  requestId: string;
  credentialId: number;
  externalSessionId?: string;
  internalSessionId: string;
  externalUserId?: string;
  clientIp?: string;
  status: string;
  httpStatus: number;
  errorCode?: string;
  traceId?: string;
  promptChars: number;
  responseChars: number;
  elapsedMs: number;
  requestSummary?: string;
  responseSummary?: string;
  createdAt: string;
  completedAt?: string | null;
};

type UserRow = {
  id: string;
  mobile: string;
  nickname?: string;
  roleCode?: string;
};

type TabKey = "keys" | "calls";

function formatTime(value?: string | null) {
  if (!value) return "无";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

function statusText(status: string) {
  if (status === "ACTIVE") return "启用";
  if (status === "PAUSED") return "停用";
  if (status === "REVOKED") return "已删除";
  if (status === "SUCCESS") return "成功";
  if (status === "FAILED") return "失败";
  return status || "未知";
}

function statusClass(status: string) {
  if (status === "ACTIVE" || status === "SUCCESS") return "is-active";
  if (status === "PAUSED") return "is-paused";
  if (status === "REVOKED" || status === "FAILED") return "is-danger";
  return "";
}

export default function AgentOpenApiKeysDialog({
  open,
  agentId,
  agentName,
  token,
  onClose,
}: AgentOpenApiKeysDialogProps) {
  const [activeTab, setActiveTab] = useState<TabKey>("keys");
  const [keys, setKeys] = useState<ApiKeyRow[]>([]);
  const [calls, setCalls] = useState<ApiCallRow[]>([]);
  const [users, setUsers] = useState<UserRow[]>([]);
  const [notice, setNotice] = useState("");
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [plainKey, setPlainKey] = useState("");
  const [callQuery, setCallQuery] = useState("");
  const [form, setForm] = useState({
    name: "生产调用 Key",
    runAsUserId: "",
    allowedIps: "",
    rateLimitPerMinute: "60",
    dailyQuota: "1000",
    maxPromptChars: "8000",
    maxResponseChars: "12000",
    allowStream: true,
  });

  const visibleKeys = useMemo(
    () => keys.filter((key) => key.status !== "REVOKED"),
    [keys],
  );

  const userDisplayName = (userId: string) => {
    const user = users.find((item) => item.id === userId);
    if (!user) return userId || "未绑定";
    return user.nickname || user.mobile || user.id;
  };

  const userDetailTitle = (userId: string) => {
    const user = users.find((item) => item.id === userId);
    if (!user) return userId || "未绑定用户";
    const parts = [
      user.nickname ? `名称：${user.nickname}` : "",
      user.mobile ? `手机：${user.mobile}` : "",
      user.roleCode ? `角色：${user.roleCode}` : "",
      user.id ? `用户ID：${user.id}` : "",
    ].filter(Boolean);
    return parts.join("\n");
  };

  const copyText = async (text: string, successMessage: string) => {
    if (!text) return;
    try {
      await navigator.clipboard.writeText(text);
      setNotice(successMessage);
    } catch {
      const textarea = document.createElement("textarea");
      textarea.value = text;
      textarea.setAttribute("readonly", "true");
      textarea.style.position = "fixed";
      textarea.style.left = "-9999px";
      document.body.appendChild(textarea);
      textarea.select();
      document.execCommand("copy");
      document.body.removeChild(textarea);
      setNotice(successMessage);
    }
  };

  const requestJson = async <T,>(url: string, init?: RequestInit) => {
    const res = await fetch(url, {
      ...init,
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
        ...(init?.headers ?? {}),
      },
    });
    const body = (await res.json()) as ApiEnvelope<T>;
    if (!res.ok || !body.success) {
      throw new Error(body.message || `HTTP ${res.status}`);
    }
    return body.data as T;
  };

  const load = async (clearNotice = true) => {
    if (!open || !agentId || !token) return;
    setLoading(true);
    if (clearNotice) setNotice("");
    try {
      const [keyRows, callRows, userRows] = await Promise.all([
        requestJson<ApiKeyRow[]>(`/agents/${encodeURIComponent(agentId)}/api-keys`),
        requestJson<ApiCallRow[]>(`/agents/${encodeURIComponent(agentId)}/api-calls`),
        requestJson<UserRow[]>("/admin/users"),
      ]);
      setKeys(keyRows ?? []);
      setCalls(callRows ?? []);
      setUsers(userRows ?? []);
      setForm((current) => ({
        ...current,
        runAsUserId: current.runAsUserId || userRows?.[0]?.id || "",
      }));
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "加载失败");
    } finally {
      setLoading(false);
    }
  };

  const loadCalls = async (query = callQuery) => {
    if (!agentId || !token) return;
    const search = query.trim();
    const suffix = search ? `?q=${encodeURIComponent(search)}` : "";
    const rows = await requestJson<ApiCallRow[]>(`/agents/${encodeURIComponent(agentId)}/api-calls${suffix}`);
    setCalls(rows ?? []);
  };

  useEffect(() => {
    if (!open) return;
    void load();
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [agentId, onClose, open, token]);

  useEffect(() => {
    if (open) return;
    setPlainKey("");
    setNotice("");
  }, [open]);

  if (!open) return null;

  const createKey = async () => {
    setSaving(true);
    setNotice("");
    setPlainKey("");
    try {
      const payload = {
        name: form.name.trim(),
        runAsUserId: form.runAsUserId,
        allowedIps: form.allowedIps
          .split(/[,\n]/)
          .map((item) => item.trim())
          .filter(Boolean),
        rateLimitPerMinute: Number(form.rateLimitPerMinute) || 60,
        dailyQuota: Number(form.dailyQuota) || 1000,
        maxPromptChars: Number(form.maxPromptChars) || 8000,
        maxResponseChars: Number(form.maxResponseChars) || 12000,
        allowStream: form.allowStream,
      };
      const created = await requestJson<{ credential: ApiKeyRow; plainKey: string }>(
        `/agents/${encodeURIComponent(agentId)}/api-keys`,
        {
          method: "POST",
          body: JSON.stringify(payload),
        },
      );
      setPlainKey(created.plainKey);
      setNotice("API Key 已创建，明文只显示一次。");
      await load(false);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "创建失败");
    } finally {
      setSaving(false);
    }
  };

  const rotateKey = async (credentialId: number) => {
    if (!window.confirm("重新生成会让旧完整 Key 立即失效。继续后请立刻复制新的完整 Key。")) {
      return;
    }
    setSaving(true);
    setNotice("");
    setPlainKey("");
    try {
      const rotated = await requestJson<{ credential: ApiKeyRow; plainKey: string }>(
        `/agents/${encodeURIComponent(agentId)}/api-keys/${credentialId}/rotate`,
        { method: "POST", body: "{}" },
      );
      setPlainKey(rotated.plainKey);
      setNotice("已重新生成完整 Key，旧 Key 已立即失效。请现在复制新 Key。");
      await load(false);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "重新生成失败");
    } finally {
      setSaving(false);
    }
  };

  const setKeyStatus = async (key: ApiKeyRow, nextStatus: "ACTIVE" | "PAUSED") => {
    setSaving(true);
    setNotice("");
    try {
      await requestJson<ApiKeyRow>(
        `/agents/${encodeURIComponent(agentId)}/api-keys/${key.id}`,
        {
          method: "PUT",
          body: JSON.stringify({ status: nextStatus }),
        },
      );
      setNotice(nextStatus === "ACTIVE" ? "API Key 已启用。" : "API Key 已停用，外部调用会被拒绝。");
      await load(false);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "状态更新失败");
    } finally {
      setSaving(false);
    }
  };

  const revokeKey = async (credentialId: number) => {
    if (!window.confirm("删除会永久作废这个 Key，历史调用日志仍会保留。完整 Key 无法恢复，只能重新创建。")) {
      return;
    }
    setSaving(true);
    setNotice("");
    try {
      await requestJson<ApiKeyRow>(
        `/agents/${encodeURIComponent(agentId)}/api-keys/${credentialId}/revoke`,
        { method: "POST", body: "{}" },
      );
      setNotice("API Key 已删除，后续调用会被拒绝。");
      await load(false);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "删除失败");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div
      className="cici-openapi-keys-backdrop"
      role="presentation"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <section
        className="cici-openapi-keys"
        role="dialog"
        aria-modal="true"
        aria-labelledby="agent-open-api-keys-title"
      >
        <button type="button" className="cici-openapi-keys__close" onClick={onClose} aria-label="关闭">×</button>
        <header className="cici-openapi-keys__header">
          <div>
            <h2 id="agent-open-api-keys-title">API 密钥与调用日志</h2>
            <p>{agentName || "未命名 Agent"} · {agentId}</p>
          </div>
          <button type="button" className="cici-builder__action cici-builder__action--ghost" onClick={() => void load()} disabled={loading}>
            刷新
          </button>
        </header>

        <div className="cici-openapi-keys__tabs" role="tablist" aria-label="Open API 管理">
          <button type="button" className={activeTab === "keys" ? "is-active" : ""} onClick={() => setActiveTab("keys")}>API Key</button>
          <button type="button" className={activeTab === "calls" ? "is-active" : ""} onClick={() => setActiveTab("calls")}>调用日志</button>
        </div>

        {notice ? <p className="cici-openapi-keys__notice">{notice}</p> : null}
        {plainKey ? (
          <div className="cici-openapi-keys__plain">
            <div className="cici-openapi-keys__plain-head">
              <strong>完整 Key 只显示这一次</strong>
              <span>列表以后只保留 Key 前缀用于识别，无法再次查看完整 Key。请现在复制到后端服务或密钥管理系统。</span>
            </div>
            <textarea readOnly value={plainKey} onFocus={(event) => event.currentTarget.select()} aria-label="完整 API Key" />
            <button type="button" className="cici-builder__action cici-builder__action--primary" onClick={() => void copyText(plainKey, "完整 Key 已复制。")}>
              复制完整 Key
            </button>
          </div>
        ) : null}

        {activeTab === "keys" ? (
          <div className="cici-openapi-keys__content">
            <div className="cici-openapi-keys__explain">
              <span><strong>停用</strong>：临时禁止调用，可再次启用。</span>
              <span><strong>重新生成</strong>：旧 Key 立即失效，并只显示一次新的完整 Key。</span>
              <span><strong>删除</strong>：永久作废这个 Key，历史日志保留。</span>
            </div>
            <div className="cici-openapi-keys__form">
              <label>
                <span>名称</span>
                <input value={form.name} onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))} />
              </label>
              <label>
                <span>run-as 用户</span>
                <select value={form.runAsUserId} onChange={(event) => setForm((current) => ({ ...current, runAsUserId: event.target.value }))}>
                  {users.map((user) => (
                    <option key={user.id} value={user.id}>{user.nickname ? `${user.nickname} · ${user.mobile}` : user.mobile}</option>
                  ))}
                </select>
              </label>
              <label>
                <span>来源 IP</span>
                <input value={form.allowedIps} onChange={(event) => setForm((current) => ({ ...current, allowedIps: event.target.value }))} placeholder="可选，逗号分隔，如 203.0.113.10/32" />
              </label>
              <label>
                <span>分钟限流</span>
                <input value={form.rateLimitPerMinute} onChange={(event) => setForm((current) => ({ ...current, rateLimitPerMinute: event.target.value }))} inputMode="numeric" />
              </label>
              <label>
                <span>日配额</span>
                <input value={form.dailyQuota} onChange={(event) => setForm((current) => ({ ...current, dailyQuota: event.target.value }))} inputMode="numeric" />
              </label>
              <label className="cici-openapi-keys__check">
                <input type="checkbox" checked={form.allowStream} onChange={(event) => setForm((current) => ({ ...current, allowStream: event.target.checked }))} />
                <span>允许流式对话</span>
              </label>
              <button type="button" className="cici-builder__action cici-builder__action--primary" onClick={() => void createKey()} disabled={saving || !form.runAsUserId || !form.name.trim()}>
                创建 Key
              </button>
            </div>

            <table className="cici-openapi-keys__table">
              <colgroup>
                <col style={{ width: "15%" }} />
                <col style={{ width: "13%" }} />
                <col style={{ width: "7%" }} />
                <col style={{ width: "28%" }} />
                <col style={{ width: "12%" }} />
                <col style={{ width: "9%" }} />
                <col style={{ width: "16%" }} />
              </colgroup>
              <thead>
                <tr>
                  <th>名称</th>
                  <th>执行用户</th>
                  <th>状态</th>
                  <th>Key 前缀</th>
                  <th>限制</th>
                  <th>最近使用</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {visibleKeys.map((key) => (
                  <tr key={key.id}>
                    <td title={`ID ${key.id}`}><span className="cici-openapi-keys__strong">{key.name}</span></td>
                    <td title={userDetailTitle(key.runAsUserId)}>{userDisplayName(key.runAsUserId)}</td>
                    <td><span className={`cici-openapi-keys__status ${statusClass(key.status)}`}>{statusText(key.status)}</span></td>
                    <td title="这里只是 Key 前缀，用于识别记录；完整 Key 只在创建或重新生成后显示一次。"><code>{key.keyPrefix}</code></td>
                    <td>{key.rateLimitPerMinute}/min · {key.dailyQuota}/day</td>
                    <td>{formatTime(key.lastUsedAt)}</td>
                    <td>
                      <div className="cici-openapi-keys__actions">
                        {key.status === "ACTIVE" ? (
                          <button type="button" onClick={() => void setKeyStatus(key, "PAUSED")} disabled={saving}>停用</button>
                        ) : (
                          <button type="button" onClick={() => void setKeyStatus(key, "ACTIVE")} disabled={saving}>启用</button>
                        )}
                        <button type="button" onClick={() => void rotateKey(key.id)} disabled={saving}>重新生成</button>
                        <button type="button" className="is-danger" onClick={() => void revokeKey(key.id)} disabled={saving}>删除</button>
                      </div>
                    </td>
                  </tr>
                ))}
                {visibleKeys.length === 0 ? (
                  <tr><td colSpan={7}>暂无可用 API Key。创建后完整 Key 只显示一次。</td></tr>
                ) : null}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="cici-openapi-keys__content">
            <div className="cici-openapi-keys__toolbar">
              <input
                value={callQuery}
                onChange={(event) => setCallQuery(event.target.value)}
                placeholder="搜索 requestId、traceId、外部用户或摘要"
              />
              <button type="button" className="cici-builder__action cici-builder__action--ghost" onClick={() => void loadCalls()}>
                查询
              </button>
            </div>
            <table className="cici-openapi-keys__table">
              <thead>
                <tr>
                  <th>时间</th>
                  <th>状态</th>
                  <th>Request</th>
                  <th>外部会话</th>
                  <th>耗时</th>
                  <th>摘要</th>
                </tr>
              </thead>
              <tbody>
                {calls.map((call) => (
                  <tr key={call.requestId}>
                    <td>{formatTime(call.createdAt)}</td>
                    <td>{statusText(call.status)} · {call.httpStatus}</td>
                    <td><code>{call.requestId}</code><br /><small>{call.traceId || "无 trace"}</small></td>
                    <td>{call.externalSessionId || "一次性"}<br /><small>{call.externalUserId || "无外部用户"}</small></td>
                    <td>{call.elapsedMs}ms</td>
                    <td>{call.responseSummary || call.requestSummary || call.errorCode || "无摘要"}</td>
                  </tr>
                ))}
                {calls.length === 0 ? (
                  <tr><td colSpan={6}>暂无调用记录。</td></tr>
                ) : null}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
