import { useEffect, useState } from "react";
import { useAdminToken } from "../useAdminToken";

type IntegrationApp = {
  id: number;
  appCode: string;
  appName: string;
  description: string;
  enabled: boolean;
  config: Record<string, string>;
  configKeys: string[];
  builtin: boolean;
};

type IntegrationLoadState = "loading" | "ready" | "error";

type IntegrationAppsResponse = {
  success?: boolean;
  data?: unknown;
  message?: string;
};

export async function readIntegrationAppsResponse(res: Response): Promise<IntegrationApp[]> {
  const contentType = res.headers.get("content-type")?.toLowerCase() ?? "";
  if (!contentType.includes("application/json")) {
    throw new Error("平台集成接口返回了非 JSON 响应");
  }

  const json = (await res.json()) as IntegrationAppsResponse;
  if (!res.ok || !json.success) {
    throw new Error(json.message ?? "加载失败");
  }
  return Array.isArray(json.data) ? (json.data as IntegrationApp[]) : [];
}

const SECRET_FIELDS: Record<string, string[]> = {
  tavily: ["apiKey"],
  cloudcc_crm: ["secretKey"],
  feishu_bot: ["appSecret"],
  iflytek_asr: ["accessKeySecret"],
};

type FieldMeta = {
  label: string;
  hint?: string;
  placeholder?: string;
  required?: boolean;
};

const FIELD_META: Record<string, Record<string, FieldMeta>> = {
  tavily: {
    apiKey: {
      label: "API Key",
      required: true,
      placeholder: "tvly-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
      hint: "从 https://app.tavily.com 获取，格式以 tvly- 开头。保存后不会明文展示，留空则保持现有 Key 不变。",
    },
    defaultSearchDepth: {
      label: "默认搜索深度",
      placeholder: "basic",
      hint: "可选值：basic（快速，默认）或 advanced（深度，消耗更多 credits）。留空使用 basic。",
    },
    defaultMaxResults: {
      label: "默认最大结果数",
      placeholder: "5",
      hint: "每次搜索返回的最大结果条数，范围 1–20，留空使用 5。",
    },
    defaultTopic: {
      label: "默认话题类型",
      placeholder: "general",
      hint: "可选值：general（通用，默认）或 news（新闻）。留空使用 general。",
    },
    defaultIncludeAnswer: {
      label: "默认 include_answer",
      placeholder: "",
      hint: "是否让 Tavily 附带直接答案。可填 basic 或 advanced；留空（推荐）表示不附带答案。",
    },
    defaultExtractFormat: {
      label: "正文抽取格式",
      placeholder: "markdown",
      hint: "tavily_extract 返回的正文格式。可选值：markdown（默认）或 text。留空使用 markdown。",
    },
    timeoutMs: {
      label: "请求超时（毫秒）",
      placeholder: "15000",
      hint: "调用 Tavily API 的超时时间，单位毫秒。留空使用 15000（15 秒）。",
    },
  },
  feishu_bot: {
    appId: { label: "App ID", required: true, placeholder: "cli_xxxxxxxxxxxxxxxx", hint: "飞书开放平台应用的 App ID。" },
    appSecret: { label: "App Secret", required: true, placeholder: "xxxxxxxxxxxxxxxxxxxx", hint: "飞书开放平台应用的 App Secret，保存后不会明文展示。" },
    defaultAgentCode: { label: "默认 Agent", placeholder: "cici", hint: "收到飞书消息时默认路由到的 Agent Code，留空使用 cici。" },
    pairingCommandHint: { label: "配对指令提示", placeholder: "配对 123456", hint: "用户发送配对码时的识别前缀，留空使用「配对」。" },
  },
  cloudcc_crm: {
    orgId: {
      label: "CloudCC 组织 ID（orgId）",
      required: true,
      placeholder: "orgxxxxxxxxxxxxxxxx",
      hint: "CloudCC Token API 的 orgId。它不是当前 AgentCiCi 的租户 companyId。",
    },
    orgapi_switch_address: {
      label: "CloudCC 组织网关/发现地址",
      required: true,
      placeholder: "https://example.apis.cloudcc.cn/lightningapi",
      hint: "可填写 CloudCC 组织网关或官方 apidomain 发现地址；保存后由服务端解析实际网关。",
    },
    clientId: { label: "Client ID", required: true, placeholder: "xxxxxxxxxxx", hint: "CloudCC OAuth 客户端 ID。" },
    secretKey: { label: "Secret Key", required: true, placeholder: "xxxxxxxxxxxxxxxxxxxx", hint: "CloudCC OAuth 密钥，保存后不会明文展示；保留掩码不变即可继续使用现有密钥。" },
    loginDomain: { label: "登录域名", placeholder: "https://login.cloudcc.com", hint: "CloudCC 登录接口域名，留空使用默认值。" },
  },
  iflytek_asr: {
    appId: {
      label: "App ID",
      required: true,
      placeholder: "xxxxxxxx",
      hint: "讯飞开放平台实时语音转写应用的 App ID。",
    },
    accessKeyId: {
      label: "Access Key ID",
      required: true,
      placeholder: "xxxxxxxxxxxxxxxx",
      hint: "实时转写服务使用的 Access Key ID。",
    },
    accessKeySecret: {
      label: "Access Key Secret",
      required: true,
      placeholder: "xxxxxxxxxxxxxxxx",
      hint: "保存后不会明文展示，留空则保持现有 Secret 不变。",
    },
    realtimeUrl: {
      label: "Realtime URL",
      placeholder: "wss://office-api-ast-dx.iflyaisol.com/ast/communicate/v1",
      hint: "讯飞实时转写 WebSocket 地址，留空使用默认地址。",
    },
    lang: {
      label: "语言",
      placeholder: "autodialect",
      hint: "默认 autodialect，表示自动识别普通话和方言。",
    },
    domain: {
      label: "领域",
      placeholder: "com",
      hint: "默认 com，按讯飞实时转写服务的领域参数传递。",
    },
  },
};

function getFieldMeta(appCode: string, key: string): FieldMeta {
  return FIELD_META[appCode]?.[key] ?? { label: key };
}

function isSecretField(appCode: string, key: string): boolean {
  return SECRET_FIELDS[appCode]?.includes(key) ?? false;
}

type IntegrationSettingsPageProps = {
  token: string;
  apiBase: string;
  title: string;
  subtitle: string;
  className?: string;
};

export function IntegrationSettingsPage({ token, apiBase, title, subtitle, className = "" }: IntegrationSettingsPageProps) {
  const [notice, setNotice] = useState("");
  const [apps, setApps] = useState<IntegrationApp[]>([]);
  const [loadState, setLoadState] = useState<IntegrationLoadState>("loading");
  const [loadError, setLoadError] = useState("");
  const [editing, setEditing] = useState<IntegrationApp | null>(null);
  const [form, setForm] = useState<Record<string, string>>({});
  const [formDescription, setFormDescription] = useState("");
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<string>("");

  const loadApps = async () => {
    setLoadState("loading");
    setLoadError("");
    try {
      const res = await fetch(apiBase, { headers: { Authorization: `Bearer ${token}` } });
      setApps(await readIntegrationAppsResponse(res));
      setLoadState("ready");
    } catch (err) {
      setLoadError((err as Error).message || "加载失败");
      setLoadState("error");
    }
  };

  const openEdit = (app: IntegrationApp) => {
    setEditing(app);
    setFormDescription(app.description ?? "");
    setTestResult("");
    const next: Record<string, string> = {};
    for (const k of app.configKeys) next[k] = String(app.config?.[k] ?? "");
    setForm(next);
  };

  const testTavily = async () => {
    if (!editing || editing.appCode !== "tavily") return;
    setTesting(true);
    setTestResult("");
    try {
      // Only send apiKey when user edited it to something other than the mask
      const apiKey = form.apiKey && form.apiKey !== "tvly-****" ? form.apiKey : "";
      const res = await fetch(`${apiBase}/tavily/test`, {
        method: "POST",
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
        body: JSON.stringify({ apiKey }),
      });
      const json = await res.json();
      if (!res.ok || !json.success) {
        setTestResult(`测试失败：${json.message ?? "unknown error"}`);
        return;
      }
      const payload = (json.data ?? {}) as { ok?: boolean; latencyMs?: number; resultCount?: number; code?: string; message?: string };
      if (payload.ok) {
        setTestResult(`测试成功：${payload.latencyMs ?? 0}ms，返回 ${payload.resultCount ?? 0} 条结果`);
      } else {
        setTestResult(`测试失败（${payload.code ?? "ERROR"}）：${payload.message ?? ""}`);
      }
    } catch (err) {
      setTestResult(`测试失败：${(err as Error).message}`);
    } finally {
      setTesting(false);
    }
  };

  const save = async () => {
    if (!editing) return;
    setSaving(true);
    try {
      const res = await fetch(`${apiBase}/${editing.appCode}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
        body: JSON.stringify({ enabled: editing.enabled, description: formDescription, config: form }),
      });
      const json = await res.json();
      if (!res.ok || !json.success) {
        setNotice(`保存失败：${json.message ?? "unknown error"}`);
        return;
      }
      setNotice("保存成功");
      setEditing(null);
      await loadApps();
    } finally {
      setSaving(false);
    }
  };

  const toggleEnabled = async (app: IntegrationApp) => {
    const res = await fetch(`${apiBase}/${app.appCode}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
      body: JSON.stringify({ enabled: !app.enabled, description: app.description ?? "", config: app.config ?? {} }),
    });
    const json = await res.json();
    if (!res.ok || !json.success) {
      setNotice(`更新失败：${json.message ?? "unknown error"}`);
      return;
    }
    setNotice(app.enabled ? "已停用" : "已启用");
    await loadApps();
  };

  useEffect(() => {
    void loadApps();
  }, [token, apiBase]);

  return (
    <div className={`admin-page ${className}`.trim()}>
      <header className="chat-header">
        <h1>{title}</h1>
        <p className="subtle">{subtitle}</p>
      </header>

      {notice && <p className="notice integration-settings__notice" role="status">{notice}</p>}

      {loadState === "loading" && (
        <div className="integration-state" role="status" aria-live="polite">
          正在加载平台集成配置…
        </div>
      )}

      {loadState === "error" && (
        <div className="integration-state integration-state--error" role="alert">
          <div>
            <strong>平台集成加载失败</strong>
            <p>{loadError}</p>
          </div>
          <button type="button" className="cici-btn cici-btn--ghost" onClick={() => void loadApps()}>
            重试
          </button>
        </div>
      )}

      {loadState === "ready" && apps.length === 0 && (
        <div className="integration-state" role="status">
          <div>
            <strong>暂无平台集成</strong>
            <p>当前没有可配置的平台代管能力。</p>
          </div>
        </div>
      )}

      {loadState === "ready" && apps.length > 0 && (
        <div className="integration-grid">
          {apps.map((app) => (
            <article key={app.id} className="integration-card">
              <div className="integration-card__head">
                <div>
                  <h3>{app.appName}</h3>
                  <p className="subtle integration-card__desc">{app.description}</p>
                </div>
              </div>

              <div className="integration-card__actions">
                <button
                  type="button"
                  className="integration-icon-btn"
                  onClick={() => openEdit(app)}
                  aria-label="编辑应用配置"
                  title="编辑"
                >
                  <svg viewBox="0 0 24 24" width="22" height="22" fill="none" aria-hidden>
                    <path d="M12 2.75 13.65 4.4l2.33-.38.95 2.16 2.23.8-.2 2.35L20.75 12l-1.79 1.67.2 2.35-2.23.8-.95 2.16-2.33-.38L12 21.25l-1.65-1.65-2.33.38-.95-2.16-2.23-.8.2-2.35L3.25 12l1.79-1.67-.2-2.35 2.23-.8.95-2.16 2.33.38L12 2.75Z" stroke="currentColor" strokeWidth="1.4" strokeLinejoin="round" />
                    <circle cx="12" cy="12" r="3.25" stroke="currentColor" strokeWidth="1.6" />
                  </svg>
                </button>
                <div className="integration-card__toggle-wrap">
                  <span className="integration-card__toggle-status">{app.enabled ? "已启用" : "已停用"}</span>
                  <button
                    type="button"
                    className={`cici-toggle ${app.enabled ? "cici-toggle--on" : ""}`}
                    onClick={() => void toggleEnabled(app)}
                    aria-label={app.enabled ? "停用应用" : "启用应用"}
                  />
                </div>
              </div>
            </article>
          ))}
        </div>
      )}

      {editing && (
        <div className="cici-modal-overlay" onClick={() => setEditing(null)}>
          <div
            className="cici-modal cici-modal--wide"
            role="dialog"
            aria-modal="true"
            aria-labelledby="admin-integration-edit-title"
            onClick={(e) => e.stopPropagation()}
          >
            <h2 className="cici-modal__title" id="admin-integration-edit-title">编辑：{editing.appName}</h2>
            {testResult && (
              <div className={`admin-integrations__test-result ${testResult.startsWith("测试成功") ? "is-success" : "is-error"}`}>
                {testResult}
              </div>
            )}
            <div className="cici-modal__body">
              <label className="cici-field">
                <span className="cici-field__label">应用描述</span>
                <textarea
                  className="cici-field__textarea"
                  rows={2}
                  value={formDescription}
                  onChange={(e) => setFormDescription(e.target.value)}
                  placeholder="请输入应用描述"
                />
              </label>
              {editing.configKeys.map((key) => {
                const meta = getFieldMeta(editing.appCode, key);
                const secret = isSecretField(editing.appCode, key);
                return (
                  <label className="cici-field" key={key}>
                    <span className="cici-field__label">
                      {meta.label}
                      {meta.required && <span className="cici-field__required">*</span>}
                    </span>
                    <input
                      className="cici-field__input"
                      type={secret ? "password" : "text"}
                      autoComplete={secret ? "new-password" : undefined}
                      placeholder={meta.placeholder}
                      value={form[key] ?? ""}
                      onChange={(e) => setForm((p) => ({ ...p, [key]: e.target.value }))}
                    />
                    {meta.hint && <span className="cici-field__hint-text">{meta.hint}</span>}
                  </label>
                );
              })}
            </div>
            <div className="cici-modal__actions">
              <button type="button" className="cici-btn cici-btn--ghost" onClick={() => setEditing(null)}>取消</button>
              {editing.appCode === "tavily" && (
                <button
                  type="button"
                  className="cici-btn cici-btn--ghost"
                  disabled={testing}
                  onClick={() => void testTavily()}
                >
                  {testing ? "测试中..." : "测试连接"}
                </button>
              )}
              <button type="button" className="cici-btn cici-btn--primary" disabled={saving} onClick={() => void save()}>
                {saving ? "保存中..." : "保存"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default function AdminIntegrationsPage() {
  const token = useAdminToken();
  return (
    <IntegrationSettingsPage
      token={token}
      apiBase="/integrations"
      title="集成应用"
      subtitle="组织侧标准内置集成应用（不可手动新增）"
    />
  );
}
