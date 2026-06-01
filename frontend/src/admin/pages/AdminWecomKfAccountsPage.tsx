import { useEffect, useMemo, useState } from "react";
import { safeFetchJson } from "../../utils/http";
import { useAdminToken } from "../useAdminToken";

type WecomKfAccount = {
  id: number;
  corpId: string;
  openKfId: string;
  name: string;
  agentId: string;
  runAsUserId: string;
  enabled: boolean;
  syncCursorPresent?: boolean;
  accessTokenExpiresAt?: string;
  callbackPath?: string;
};

type WecomKfConnectionTest = {
  status: string;
  checkedAt: string;
  accessTokenExpiresAt?: string;
  apiBaseUrl?: string;
};

type AgentOption = {
  agentId: string;
  name?: string | null;
  summary?: string | null;
  builtin?: boolean;
};

type UserOption = {
  id: string;
  mobile?: string;
  nickname?: string;
  roleCode?: string;
  memberStatus?: string;
};

type WecomKfForm = {
  name: string;
  corpId: string;
  openKfId: string;
  secret: string;
  token: string;
  encodingAesKey: string;
  agentId: string;
  runAsUserId: string;
  enabled: boolean;
};

const DEFAULT_AGENT_ID = "after-sales-agent";

function emptyForm(): WecomKfForm {
  return {
    name: "",
    corpId: "",
    openKfId: "",
    secret: "",
    token: "",
    encodingAesKey: "",
    agentId: DEFAULT_AGENT_ID,
    runAsUserId: "",
    enabled: true,
  };
}

function formFromAccount(account: WecomKfAccount): WecomKfForm {
  return {
    name: account.name ?? "",
    corpId: account.corpId ?? "",
    openKfId: account.openKfId ?? "",
    secret: "",
    token: "",
    encodingAesKey: "",
    agentId: account.agentId || DEFAULT_AGENT_ID,
    runAsUserId: account.runAsUserId ?? "",
    enabled: account.enabled,
  };
}

function userLabel(user: UserOption): string {
  const primary = user.nickname?.trim() || user.mobile?.trim() || user.id;
  const role = user.roleCode ? ` · ${user.roleCode}` : "";
  return `${primary}${role}`;
}

function formatDateTime(value?: string): string {
  if (!value) return "暂无缓存";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  })
    .format(date)
    .replace(/\//g, "-");
}

function callbackUrl(path?: string): string {
  if (!path) return "";
  if (typeof window === "undefined") return path;
  return `${window.location.origin}${path}`;
}

export default function AdminWecomKfAccountsPage() {
  const token = useAdminToken();
  const [accounts, setAccounts] = useState<WecomKfAccount[]>([]);
  const [agents, setAgents] = useState<AgentOption[]>([]);
  const [users, setUsers] = useState<UserOption[]>([]);
  const [selectedId, setSelectedId] = useState<number | "new">("new");
  const [form, setForm] = useState<WecomKfForm>(() => emptyForm());
  const [notice, setNotice] = useState("");
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [testingConnection, setTestingConnection] = useState(false);
  const [connectionTest, setConnectionTest] = useState<WecomKfConnectionTest | null>(null);

  const selectedAccount = useMemo(
    () => (selectedId === "new" ? null : accounts.find((account) => account.id === selectedId) ?? null),
    [accounts, selectedId],
  );

  const activeCallbackUrl = callbackUrl(selectedAccount?.callbackPath);
  const canSave =
    form.corpId.trim() &&
    form.openKfId.trim() &&
    (selectedAccount || form.token.trim()) &&
    form.agentId.trim() &&
    form.runAsUserId.trim() &&
    (selectedAccount || form.secret.trim()) &&
    (selectedAccount || form.encodingAesKey.trim());

  const authHeaders = useMemo(() => ({ Authorization: `Bearer ${token}` }), [token]);
  const jsonHeaders = useMemo(() => ({ ...authHeaders, "Content-Type": "application/json" }), [authHeaders]);

  const loadAccounts = async () => {
    setLoading(true);
    setNotice("");
    try {
      const response = await fetch("/admin/wecom/kf-accounts", { headers: authHeaders });
      const { body } = await safeFetchJson<WecomKfAccount[]>(response);
      if (!response.ok || !body?.success) {
        setNotice(body?.message ?? "微信客服配置加载失败");
        return;
      }
      const list = (body.data ?? []) as WecomKfAccount[];
      setAccounts(list);
      setSelectedId((current) => {
        if (current === "new") return list.length > 0 ? list[0].id : "new";
        return list.some((account) => account.id === current) ? current : list[0]?.id ?? "new";
      });
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "微信客服配置加载失败");
    } finally {
      setLoading(false);
    }
  };

  const loadOptions = async () => {
    try {
      const [agentResponse, userResponse] = await Promise.all([
        fetch("/agents", { headers: authHeaders }),
        fetch("/admin/users", { headers: authHeaders }),
      ]);
      const [agentBody, userBody] = await Promise.all([
        safeFetchJson<AgentOption[]>(agentResponse),
        safeFetchJson<UserOption[]>(userResponse),
      ]);
      if (agentResponse.ok && agentBody.body?.success) {
        setAgents((agentBody.body.data ?? []) as AgentOption[]);
      }
      if (userResponse.ok && userBody.body?.success) {
        const rows = ((userBody.body.data ?? []) as UserOption[]).filter((user) => user.memberStatus !== "SUSPENDED");
        setUsers(rows);
        setForm((current) => (current.runAsUserId ? current : { ...current, runAsUserId: rows[0]?.id ?? "" }));
      }
    } catch {
      // The account form can still be edited with manual ids if option loading fails.
    }
  };

  useEffect(() => {
    if (!token) return;
    void loadAccounts();
    void loadOptions();
  }, [token]);

  useEffect(() => {
    if (selectedAccount) {
      setForm(formFromAccount(selectedAccount));
      setConnectionTest(null);
      return;
    }
    setConnectionTest(null);
    setForm((current) => ({ ...emptyForm(), runAsUserId: current.runAsUserId || users[0]?.id || "" }));
  }, [selectedAccount?.id, users]);

  const startNew = () => {
    setSelectedId("new");
    setNotice("");
    setForm((current) => ({ ...emptyForm(), runAsUserId: current.runAsUserId || users[0]?.id || "" }));
  };

  const save = async () => {
    if (!canSave) {
      setNotice("请补全 CorpID、open_kfid、Token、Secret、EncodingAESKey、服务用户和售后 Agent");
      return;
    }
    if (form.encodingAesKey.trim() && form.encodingAesKey.trim().length !== 43) {
      setNotice("EncodingAESKey 必须是 43 位字符；编辑时如不更新可留空");
      return;
    }

    setSaving(true);
    setNotice("");
    try {
      const body = {
        name: form.name.trim(),
        corpId: form.corpId.trim(),
        openKfId: form.openKfId.trim(),
        secret: form.secret.trim(),
        token: form.token.trim(),
        encodingAesKey: form.encodingAesKey.trim(),
        agentId: form.agentId.trim() || DEFAULT_AGENT_ID,
        runAsUserId: form.runAsUserId.trim(),
        enabled: form.enabled,
      };
      const response = await fetch(selectedAccount ? `/admin/wecom/kf-accounts/${selectedAccount.id}` : "/admin/wecom/kf-accounts", {
        method: selectedAccount ? "PUT" : "POST",
        headers: jsonHeaders,
        body: JSON.stringify(body),
      });
      const { body: payload } = await safeFetchJson<WecomKfAccount>(response);
      if (!response.ok || !payload?.success) {
        setNotice(payload?.message ?? "保存失败");
        return;
      }
      const saved = payload.data as WecomKfAccount;
      setNotice("微信客服配置已保存");
      await loadAccounts();
      setSelectedId(saved.id);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "保存失败");
    } finally {
      setSaving(false);
    }
  };

  const setEnabled = async (account: WecomKfAccount, enabled: boolean) => {
    setNotice("");
    try {
      const response = await fetch(`/admin/wecom/kf-accounts/${account.id}/${enabled ? "enable" : "disable"}`, {
        method: "POST",
        headers: authHeaders,
      });
      const { body } = await safeFetchJson<WecomKfAccount>(response);
      if (!response.ok || !body?.success) {
        setNotice(body?.message ?? "状态更新失败");
        return;
      }
      setNotice(enabled ? "客服账号已启用" : "客服账号已停用");
      await loadAccounts();
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "状态更新失败");
    }
  };

  const copyCallback = async () => {
    if (!activeCallbackUrl) return;
    try {
      await navigator.clipboard.writeText(activeCallbackUrl);
      setNotice("回调地址已复制");
    } catch {
      setNotice("当前浏览器无法写入剪贴板，请手动复制回调地址");
    }
  };

  const testConnection = async () => {
    if (!selectedAccount) return;
    setTestingConnection(true);
    setNotice("");
    setConnectionTest(null);
    try {
      const response = await fetch(`/admin/wecom/kf-accounts/${selectedAccount.id}/connection-test`, {
        method: "POST",
        headers: authHeaders,
      });
      const { body } = await safeFetchJson<WecomKfConnectionTest>(response);
      if (!response.ok || !body?.success) {
        setNotice(body?.message ?? "连接测试失败");
        return;
      }
      const result = body.data as WecomKfConnectionTest;
      await loadAccounts();
      setConnectionTest(result);
      setNotice("企业微信连接测试通过");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "连接测试失败");
    } finally {
      setTestingConnection(false);
    }
  };

  return (
    <div className="admin-page wecom-kf-page">
      <header className="wecom-kf-header">
        <div>
          <h1>微信客服</h1>
          <p className="subtle">配置企业微信「微信客服」账号，让外部微信客户进入售后服务 Agent。</p>
        </div>
        <button type="button" className="wecom-kf-primary" onClick={startNew}>
          新增客服账号
        </button>
      </header>

      {notice ? <p className="notice wecom-kf-notice">{notice}</p> : null}

      <section className="wecom-kf-layout" aria-label="微信客服账号配置">
        <aside className="wecom-kf-list-panel">
          <div className="wecom-kf-list-head">
            <strong>账号配置</strong>
            <span>{accounts.length} 个</span>
          </div>
          <div className="wecom-kf-list" aria-busy={loading}>
            {accounts.map((account) => (
              <button
                key={account.id}
                type="button"
                className={`wecom-kf-row${selectedAccount?.id === account.id ? " is-active" : ""}`}
                onClick={() => {
                  setSelectedId(account.id);
                  setNotice("");
                }}
              >
                <span className="wecom-kf-row__name">{account.name || account.openKfId}</span>
                <span className="wecom-kf-row__meta">{account.openKfId}</span>
                <span className={`wecom-kf-status${account.enabled ? " is-enabled" : " is-disabled"}`}>
                  {account.enabled ? "已启用" : "已停用"}
                </span>
              </button>
            ))}
            {accounts.length === 0 ? <p className="subtle wecom-kf-empty">{loading ? "加载中..." : "尚未配置微信客服账号"}</p> : null}
          </div>
        </aside>

        <section className="wecom-kf-detail-panel">
          <div className="wecom-kf-detail-head">
            <div>
              <h2>{selectedAccount ? "编辑客服账号" : "新增客服账号"}</h2>
              <p className="subtle">{selectedAccount ? "Secret 与 EncodingAESKey 留空时保持原密文不变。" : "保存后会生成可复制到企业微信后台的回调地址。"}</p>
            </div>
            {selectedAccount ? (
              <div className="wecom-kf-head-actions">
                <button type="button" className="wecom-kf-text-action" disabled={testingConnection} onClick={() => void testConnection()}>
                  {testingConnection ? "测试中..." : "测试连接"}
                </button>
                <button type="button" className="wecom-kf-text-action" onClick={() => void setEnabled(selectedAccount, !selectedAccount.enabled)}>
                  {selectedAccount.enabled ? "停用" : "启用"}
                </button>
              </div>
            ) : null}
          </div>

          <div className="wecom-kf-form-grid">
            <label>
              <span>账号名称</span>
              <input
                value={form.name}
                onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))}
                placeholder="售后微信客服"
              />
            </label>
            <label>
              <span>CorpID</span>
              <input
                value={form.corpId}
                onChange={(event) => setForm((current) => ({ ...current, corpId: event.target.value }))}
                placeholder="wwxxxxxxxxxxxx"
                autoComplete="off"
              />
            </label>
            <label>
              <span>open_kfid</span>
              <input
                value={form.openKfId}
                onChange={(event) => setForm((current) => ({ ...current, openKfId: event.target.value }))}
                placeholder="wkf_xxxxxxxxx"
                disabled={Boolean(selectedAccount)}
                autoComplete="off"
              />
            </label>
            <label>
              <span>Token</span>
              <input
                value={form.token}
                onChange={(event) => setForm((current) => ({ ...current, token: event.target.value }))}
                placeholder={selectedAccount ? "留空保持不变" : "企业微信回调 Token"}
                autoComplete="off"
              />
            </label>
            <label>
              <span>Secret</span>
              <input
                type="password"
                value={form.secret}
                onChange={(event) => setForm((current) => ({ ...current, secret: event.target.value }))}
                placeholder={selectedAccount ? "留空保持不变" : "微信客服 Secret"}
                autoComplete="new-password"
              />
            </label>
            <label>
              <span>EncodingAESKey</span>
              <input
                type="password"
                value={form.encodingAesKey}
                onChange={(event) => setForm((current) => ({ ...current, encodingAesKey: event.target.value }))}
                placeholder={selectedAccount ? "留空保持不变" : "43 位 EncodingAESKey"}
                autoComplete="new-password"
              />
            </label>
            <label>
              <span>售后 Agent</span>
              <select value={form.agentId} onChange={(event) => setForm((current) => ({ ...current, agentId: event.target.value }))}>
                {agents.some((agent) => agent.agentId === DEFAULT_AGENT_ID) ? null : <option value={DEFAULT_AGENT_ID}>售后服务 Agent · {DEFAULT_AGENT_ID}</option>}
                {agents.map((agent) => (
                  <option key={agent.agentId} value={agent.agentId}>
                    {(agent.name ?? agent.agentId).trim() || agent.agentId} · {agent.agentId}
                  </option>
                ))}
              </select>
            </label>
            <label>
              <span>服务用户</span>
              <select value={form.runAsUserId} onChange={(event) => setForm((current) => ({ ...current, runAsUserId: event.target.value }))}>
                <option value="">请选择执行用户</option>
                {users.map((user) => (
                  <option key={user.id} value={user.id}>
                    {userLabel(user)}
                  </option>
                ))}
              </select>
            </label>
            <label className="wecom-kf-check-row">
              <input
                type="checkbox"
                checked={form.enabled}
                onChange={(event) => setForm((current) => ({ ...current, enabled: event.target.checked }))}
              />
              <span>保存后启用该客服账号</span>
            </label>
          </div>

          {selectedAccount ? (
            <div className="wecom-kf-callback">
              <div>
                <span className="wecom-kf-callback__label">企业微信回调 URL</span>
                <code>{activeCallbackUrl}</code>
              </div>
              <button type="button" className="wecom-kf-text-action" onClick={() => void copyCallback()}>
                复制
              </button>
            </div>
          ) : null}

          {selectedAccount ? (
            <dl className="wecom-kf-meta">
              <div>
                <dt>连接测试</dt>
                <dd>{connectionTest ? `已通过 · ${formatDateTime(connectionTest.checkedAt)}` : "尚未测试"}</dd>
              </div>
              <div>
                <dt>消息游标</dt>
                <dd>{selectedAccount.syncCursorPresent ? "已记录" : "未记录"}</dd>
              </div>
              <div>
                <dt>Token 缓存到期</dt>
                <dd>{formatDateTime(selectedAccount.accessTokenExpiresAt)}</dd>
              </div>
              <div>
                <dt>服务身份</dt>
                <dd>{selectedAccount.runAsUserId}</dd>
              </div>
            </dl>
          ) : null}

          <div className="wecom-kf-actions">
            <button type="button" className="secondary" onClick={() => selectedAccount ? setForm(formFromAccount(selectedAccount)) : startNew()}>
              重置
            </button>
            <button type="button" className="wecom-kf-primary" disabled={saving || !canSave} onClick={() => void save()}>
              {saving ? "保存中..." : "保存配置"}
            </button>
          </div>
        </section>
      </section>
    </div>
  );
}
