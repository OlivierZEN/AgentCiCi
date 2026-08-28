import { useEffect, useMemo, useState } from "react";
import { useAdminToken } from "../useAdminToken";

type McpServer = {
  id: number;
  name: string;
  description: string;
  transportType: string;
  url: string;
  headers: string;
  timeoutSeconds: number;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
  toolCacheCount: number;
  toolCacheStatus: string;
  toolCacheUpdatedAt: string;
  toolCacheLastAttemptAt: string;
  toolCacheErrorMessage: string;
  toolCacheVersion?: string;
  authType: string;
  tokenUrl: string;
  clientId: string;
  clientSecretConfigured: boolean;
  tokenAudience: string;
  tokenScopes: string;
};
type McpTool = { name: string; description: string; inputSchema: string };
type McpToolPrefs = Record<string, { enabled: boolean; autoApprove: boolean }>;
type McpToolCacheResponse = {
  serverId: number;
  cacheStatus: string;
  cacheUpdatedAt: string;
  cacheLastAttemptAt: string;
  cacheErrorMessage: string;
  cacheVersion?: string;
  toolCount: number;
  tools: McpTool[];
};

type BuiltinTool = {
  toolName: string;
  displayName: string;
  description: string;
  riskLevel: string;
  category: string;
  builtin: boolean;
};

type TopTab = "builtin" | "mcp";
type McpView = "list" | "detail";
type ApplicationMcpBinding = { id: string; appCode: string; version: string; providerKey: string; serverId: number; serverName: string; status: string };

const TRANSPORT_OPTIONS = [
  { value: "streamableHttp", label: "可流式传输的 HTTP (streamableHttp)" },
  { value: "sse", label: "Server-Sent Events (SSE)" },
  { value: "stdio", label: "标准输入输出 (stdio)" },
];


type CategoryStyle = {
  label: string;
  glyph: string;
};

const CATEGORY_STYLES: Record<string, CategoryStyle> = {
  knowledge: {
    label: "知识",
    glyph: "📚",
  },
  crm: {
    label: "CRM",
    glyph: "🤝",
  },
  approval: {
    label: "审批",
    glyph: "✓",
  },
  email: {
    label: "邮件",
    glyph: "✉",
  },
  web: {
    label: "网络",
    glyph: "🌐",
  },
  custom: {
    label: "自定义",
    glyph: "🔧",
  },
};

const RISK_TAG: Record<string, { label: string; cls: string }> = {
  低风险: { label: "低风险", cls: "cici-doc-badge--published" },
  中风险: { label: "中风险", cls: "cici-doc-badge--uploaded" },
  高风险: { label: "高风险", cls: "cici-doc-badge--failed" },
};

function categoryStyle(category: string | null | undefined): CategoryStyle {
  if (!category) return CATEGORY_STYLES.custom;
  return CATEGORY_STYLES[category] ?? CATEGORY_STYLES.custom;
}

function formatSchemaForDisplay(inputSchema: unknown): string {
  if (inputSchema == null) return "";
  if (typeof inputSchema === "string") {
    const raw = inputSchema.trim();
    if (!raw) return "";
    try {
      return JSON.stringify(JSON.parse(raw), null, 2);
    } catch {
      return raw;
    }
  }
  try {
    return JSON.stringify(inputSchema, null, 2);
  } catch {
    return String(inputSchema);
  }
}

function formatRelativeTime(iso: string | null | undefined): string {
  if (!iso) return "未同步";
  const ts = Date.parse(iso);
  if (Number.isNaN(ts)) return "未同步";
  const diffSec = Math.max(0, Math.floor((Date.now() - ts) / 1000));
  if (diffSec < 60) return "刚刚";
  if (diffSec < 3600) return `${Math.floor(diffSec / 60)} 分钟前`;
  if (diffSec < 86400) return `${Math.floor(diffSec / 3600)} 小时前`;
  return `${Math.floor(diffSec / 86400)} 天前`;
}

export default function AdminToolsPage() {
  const token = useAdminToken();
  const [notice, setNotice] = useState("");
  const [topTab, setTopTab] = useState<TopTab>("builtin");

  /* ── builtin tools ── */
  const [builtinTools, setBuiltinTools] = useState<BuiltinTool[]>([]);
  const [builtinLoading, setBuiltinLoading] = useState(false);
  const [builtinQuery, setBuiltinQuery] = useState("");

  /* ── mcp state ── */
  const [servers, setServers] = useState<McpServer[]>([]);
  const [selected, setSelected] = useState<McpServer | null>(null);
  const [tools, setTools] = useState<McpTool[]>([]);
  const [toolPrefs, setToolPrefs] = useState<McpToolPrefs>({});
  const [mcpView, setMcpView] = useState<McpView>("list");
  const [showModal, setShowModal] = useState(false);
  const [editId, setEditId] = useState<number | null>(null);
  const [detailTab, setDetailTab] = useState<"general" | "tools" | "applications">("general");
  const [applicationBindings, setApplicationBindings] = useState<ApplicationMcpBinding[]>([]);
  const [bindingAppCode, setBindingAppCode] = useState("");
  const [bindingVersion, setBindingVersion] = useState("");
  const [bindingProviderKey, setBindingProviderKey] = useState("");
  const [connecting, setConnecting] = useState(false);
  const [discovering, setDiscovering] = useState(false);
  const [toolCacheStatus, setToolCacheStatus] = useState("empty");
  const [toolCacheUpdatedAt, setToolCacheUpdatedAt] = useState("");
  const [toolCacheErrorMessage, setToolCacheErrorMessage] = useState("");

  /* form */
  const [fName, setFName] = useState("");
  const [fDesc, setFDesc] = useState("");
  const [fType, setFType] = useState("streamableHttp");
  const [fUrl, setFUrl] = useState("");
  const [fHeaders, setFHeaders] = useState("");
  const [fTimeout, setFTimeout] = useState(60);
  const [fEnabled, setFEnabled] = useState(true);
  const [fAuthType, setFAuthType] = useState("NONE");
  const [fTokenUrl, setFTokenUrl] = useState("");
  const [fClientId, setFClientId] = useState("");
  const [fClientSecret, setFClientSecret] = useState("");
  const [fTokenAudience, setFTokenAudience] = useState("");
  const [fTokenScopes, setFTokenScopes] = useState("");

  const flash = (msg: string) => {
    setNotice(msg);
    setTimeout(() => setNotice(""), 3500);
  };
  const hdr = () => ({ Authorization: `Bearer ${token}` });

  /* ── builtin tools loading ── */
  const loadBuiltinTools = async () => {
    setBuiltinLoading(true);
    try {
      const r = await fetch("/tools", { headers: hdr() });
      const j = await r.json();
      const rows = (j.data ?? []) as BuiltinTool[];
      setBuiltinTools(rows.filter((t) => t.builtin));
    } catch (e: any) {
      flash(`加载失败：${e?.message ?? "请求异常"}`);
    } finally {
      setBuiltinLoading(false);
    }
  };

  const loadServers = async () => {
    const r = await fetch("/mcp-servers", { headers: hdr() });
    const j = await r.json();
    setServers((j.data ?? []) as McpServer[]);
  };

  const saveServer = async () => {
    if (!fName.trim() || !fUrl.trim()) { flash("名称和 URL 必填"); return; }
    if (fAuthType === "KEYCLOAK_CLIENT_CREDENTIALS" && (!fTokenUrl.trim() || !fClientId.trim() || (!editId && !fClientSecret.trim()))) {
      flash("Keycloak Token URL、Client ID 和首次配置的 Client Secret 必填"); return;
    }
    const body = { name: fName, description: fDesc, transportType: fType, url: fUrl, headers: fHeaders, timeoutSeconds: fTimeout, enabled: fEnabled,
      authType: fAuthType, tokenUrl: fAuthType === "NONE" ? null : fTokenUrl, clientId: fAuthType === "NONE" ? null : fClientId,
      clientSecret: fAuthType === "NONE" ? null : (fClientSecret.trim() || null), tokenAudience: fAuthType === "NONE" ? null : fTokenAudience,
      tokenScopes: fAuthType === "NONE" ? null : fTokenScopes };
    const isEdit = editId !== null;
    const url = isEdit ? `/mcp-servers/${editId}` : "/mcp-servers";
    const method = isEdit ? "PUT" : "POST";
    try {
      const r = await fetch(url, { method, headers: { "Content-Type": "application/json", ...hdr() }, body: JSON.stringify(body) });
      const j = await r.json();
      if (j.success) {
        flash(isEdit ? "已更新" : "已创建");
        setShowModal(false);
        resetForm();
        await loadServers();
        if (isEdit && selected && selected.id === editId) {
          setSelected(j.data as McpServer);
        }
      } else { flash(`失败：${j.message}`); }
    } catch (e: any) {
      flash(`创建失败：${e?.message ?? "请求异常"}`);
    }
  };

  const deleteServer = async (id: number) => {
    if (!window.confirm("确认删除该 MCP 服务器？")) return;
    const r = await fetch(`/mcp-servers/${id}`, { method: "DELETE", headers: hdr() });
    const j = await r.json();
    if (j.success) {
      flash("已删除");
      if (selected?.id === id) { setSelected(null); setMcpView("list"); setTools([]); }
      await loadServers();
    } else { flash(`删除失败：${j.message}`); }
  };

  const testConnection = async (id: number) => {
    setConnecting(true);
    try {
      const r = await fetch(`/mcp-servers/${id}/health`, { method: "POST", headers: hdr() });
      const j = await r.json();
      if (j.success) {
        flash(`连接成功！工具数量: ${j.data.toolCount}`);
      } else {
        const msg = String(j.message ?? "未知错误").replace(/^连接失败[:：]\s*/g, "");
        flash(`连接失败：${msg}`);
      }
    } catch (e: any) { flash(`连接异常: ${e.message}`); }
    finally { setConnecting(false); }
  };

  const discoverTools = async (id: number) => {
    setDiscovering(true);
    try {
      const r = await fetch(`/mcp-servers/${id}/discover`, { method: "POST", headers: hdr() });
      const j = await r.json();
      if (j.success) {
        const data = (j.data ?? {}) as McpToolCacheResponse;
        setTools(data.tools ?? []);
        setToolCacheStatus(data.cacheStatus ?? "empty");
        setToolCacheUpdatedAt(data.cacheUpdatedAt ?? "");
        setToolCacheErrorMessage(data.cacheErrorMessage ?? "");
        setSelected((prev) => {
          if (!prev || prev.id !== id) return prev;
          return {
            ...prev,
            toolCacheCount: data.toolCount ?? 0,
            toolCacheStatus: data.cacheStatus ?? "empty",
            toolCacheUpdatedAt: data.cacheUpdatedAt ?? "",
            toolCacheLastAttemptAt: data.cacheLastAttemptAt ?? "",
            toolCacheErrorMessage: data.cacheErrorMessage ?? "",
            toolCacheVersion: data.cacheVersion ?? "",
          };
        });
        const key = `mcp_tool_prefs_${id}`;
        try {
          const raw = localStorage.getItem(key);
          setToolPrefs(raw ? (JSON.parse(raw) as McpToolPrefs) : {});
        } catch {
          setToolPrefs({});
        }
        await loadServers();
        if (selected?.id === id) {
          const latest = (await fetch("/mcp-servers", { headers: hdr() }).then((res) => res.json())).data as McpServer[];
          const current = latest.find((item) => item.id === id);
          if (current) setSelected(current);
        }
        flash(`已刷新 ${(data.tools ?? []).length} 个工具`);
      } else { flash(`发现失败：${j.message}`); }
    } catch (e: any) { flash(`发现异常: ${e.message}`); }
    finally { setDiscovering(false); }
  };

  const loadToolCache = async (id: number) => {
    const r = await fetch(`/mcp-servers/${id}/tools`, { headers: hdr() });
    const j = await r.json();
    if (!j.success) {
      throw new Error(j.message ?? "读取缓存失败");
    }
    const data = (j.data ?? {}) as McpToolCacheResponse;
    setTools(data.tools ?? []);
    setToolCacheStatus(data.cacheStatus ?? "empty");
    setToolCacheUpdatedAt(data.cacheUpdatedAt ?? "");
    setToolCacheErrorMessage(data.cacheErrorMessage ?? "");
    setSelected((prev) => {
      if (!prev || prev.id !== id) return prev;
      return {
        ...prev,
        toolCacheCount: data.toolCount ?? 0,
        toolCacheStatus: data.cacheStatus ?? "empty",
        toolCacheUpdatedAt: data.cacheUpdatedAt ?? "",
        toolCacheLastAttemptAt: data.cacheLastAttemptAt ?? "",
        toolCacheErrorMessage: data.cacheErrorMessage ?? "",
        toolCacheVersion: data.cacheVersion ?? "",
      };
    });
  };

  const openDetail = (srv: McpServer) => {
    setSelected(srv);
    setMcpView("detail");
    setDetailTab("general");
    setTools([]);
    setToolPrefs({});
    setToolCacheStatus(srv.toolCacheStatus ?? "empty");
    setToolCacheUpdatedAt(srv.toolCacheUpdatedAt ?? "");
    setToolCacheErrorMessage(srv.toolCacheErrorMessage ?? "");
    setApplicationBindings([]);
  };

  const loadApplicationBindings = async () => {
    if (!bindingAppCode.trim()) return;
    const r = await fetch(`/tenant-applications/${encodeURIComponent(bindingAppCode.trim())}/mcp-bindings`, { headers: hdr() });
    const j = await r.json();
    if (!j.success) { flash(`读取应用绑定失败：${j.message}`); return; }
    setApplicationBindings((j.data ?? []) as ApplicationMcpBinding[]);
  };

  const bindSelectedServer = async () => {
    if (!selected || !bindingAppCode.trim() || !bindingVersion.trim() || !bindingProviderKey.trim()) { flash("应用代码、版本和 Provider Key 必填"); return; }
    const r = await fetch(`/tenant-applications/${encodeURIComponent(bindingAppCode.trim())}/mcp-bindings/${encodeURIComponent(bindingProviderKey.trim())}`, {
      method: "PUT", headers: { "Content-Type": "application/json", ...hdr() }, body: JSON.stringify({ version: bindingVersion.trim(), serverId: selected.id }),
    });
    const j = await r.json();
    if (!j.success) { flash(`绑定失败：${j.message}`); return; }
    flash("应用专属工具已绑定"); await loadApplicationBindings();
  };

  const setToolPref = (toolName: string, patch: Partial<{ enabled: boolean; autoApprove: boolean }>) => {
    if (!selected) return;
    setToolPrefs((prev) => {
      const next: McpToolPrefs = {
        ...prev,
        [toolName]: {
          enabled: prev[toolName]?.enabled ?? true,
          autoApprove: prev[toolName]?.autoApprove ?? true,
          ...patch,
        },
      };
      localStorage.setItem(`mcp_tool_prefs_${selected.id}`, JSON.stringify(next));
      return next;
    });
  };

  const openEdit = (srv?: McpServer) => {
    if (srv) {
      setEditId(srv.id);
      setFName(srv.name); setFDesc(srv.description); setFType(srv.transportType);
      setFUrl(srv.url); setFHeaders(srv.headers); setFTimeout(srv.timeoutSeconds); setFEnabled(srv.enabled);
      setFAuthType(srv.authType || "NONE"); setFTokenUrl(srv.tokenUrl || ""); setFClientId(srv.clientId || "");
      setFClientSecret(""); setFTokenAudience(srv.tokenAudience || ""); setFTokenScopes(srv.tokenScopes || "");
    } else {
      resetForm();
    }
    setShowModal(true);
  };

  const resetForm = () => {
    setEditId(null); setFName(""); setFDesc(""); setFType("streamableHttp");
    setFUrl(""); setFHeaders(""); setFTimeout(60); setFEnabled(true);
    setFAuthType("NONE"); setFTokenUrl(""); setFClientId(""); setFClientSecret(""); setFTokenAudience(""); setFTokenScopes("");
  };

  useEffect(() => {
    if (!token) return;
    void loadBuiltinTools();
    void loadServers();
  }, [token]);

  const filteredBuiltins = useMemo(() => {
    const q = builtinQuery.trim().toLowerCase();
    if (!q) return builtinTools;
    return builtinTools.filter((t) =>
      t.toolName.toLowerCase().includes(q) ||
      (t.displayName ?? "").toLowerCase().includes(q) ||
      (t.description ?? "").toLowerCase().includes(q) ||
      (t.category ?? "").toLowerCase().includes(q));
  }, [builtinTools, builtinQuery]);

  /* ── Tab bar shared across tabs ── */
  const TopTabs = (
    <div className="admin-tools-tabs">
      <button
        type="button"
        className={`admin-tools-tab ${topTab === "builtin" ? "admin-tools-tab--active" : ""}`}
        onClick={() => { setTopTab("builtin"); setMcpView("list"); }}
      >
        内置原生工具
        <span className="admin-tools-tab__count">{builtinTools.length}</span>
      </button>
      <button
        type="button"
        className={`admin-tools-tab ${topTab === "mcp" ? "admin-tools-tab--active" : ""}`}
        onClick={() => setTopTab("mcp")}
      >
        MCP 服务器
        <span className="admin-tools-tab__count">{servers.length}</span>
      </button>
    </div>
  );

  /* ── Builtin tools view ── */
  if (topTab === "builtin") {
    return (
      <div className="cici-kb-page">
        {notice && <div className="cici-toast">{notice}</div>}
        <div className="cici-kb-topbar">
          <h1 className="cici-kb-topbar__title">工具</h1>
        </div>
        {TopTabs}
        <div className="admin-tools-toolbar">
          <div className="cici-search cici-search--sm">
            <svg className="cici-search__icon" viewBox="0 0 20 20" fill="none"><circle cx="9" cy="9" r="6" stroke="currentColor" strokeWidth="1.5"/><path d="m14 14 3 3" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round"/></svg>
            <input
              className="cici-search__input"
              placeholder="搜索工具名 / 描述 / 分类"
              value={builtinQuery}
              onChange={(e) => setBuiltinQuery(e.target.value)}
            />
          </div>
          <div className="admin-tools-toolbar__hint">
            内置工具由平台维护，代理可在 Agent Builder 的「工具白名单」中勾选调用
          </div>
        </div>

        {builtinLoading && <p className="mcp-empty">加载中...</p>}
        {!builtinLoading && builtinTools.length === 0 && (
          <p className="mcp-empty">暂无内置工具。</p>
        )}
        {!builtinLoading && builtinTools.length > 0 && filteredBuiltins.length === 0 && (
          <p className="mcp-empty">没有匹配 “{builtinQuery}” 的工具。</p>
        )}

        {!builtinLoading && filteredBuiltins.length > 0 && (
          <div className="admin-tools-grid">
            {filteredBuiltins.map((tool) => renderBuiltinCard(tool))}
          </div>
        )}
      </div>
    );
  }

  /* ── MCP tab view (list/detail inside tab) ── */
  if (topTab === "mcp") {
    return (
      <div className="cici-kb-page">
        {notice && <div className="cici-toast">{notice}</div>}
        <div className="cici-kb-topbar">
          <h1 className="cici-kb-topbar__title">工具</h1>
        </div>
        {TopTabs}
        {mcpView === "list" && (
          <>
            <div className="admin-tools-tab-panel__header">
              <button type="button" className="cici-btn cici-btn--primary admin-tools__add-btn" onClick={() => openEdit()}>+ 添加 MCP 服务器</button>
            </div>
            <div className="mcp-server-list">
              {servers.length === 0 && <p className="mcp-empty">暂无 MCP 服务器，点击当前 Tab 内按钮添加。</p>}
              {servers.map(srv => (
                <div key={srv.id} className="mcp-server-row" onClick={() => openDetail(srv)}>
                  <div className="mcp-server-row__info">
                    <div className="mcp-server-row__head">
                      <span className="mcp-server-row__name">{srv.name}</span>
                      <span className={`cici-doc-badge ${srv.enabled ? "cici-doc-badge--published" : "cici-doc-badge--uploaded"}`}>
                        {srv.enabled ? "已启用" : "已停用"}
                      </span>
                    </div>
                    <div className="mcp-server-row__meta">
                      <span>{TRANSPORT_OPTIONS.find(o => o.value === srv.transportType)?.label ?? srv.transportType}</span>
                      <span className="mcp-server-row__url">{srv.url}</span>
                    </div>
                    <div className="mcp-server-row__cache">
                      <span>{srv.toolCacheCount ?? 0} 个工具</span>
                      <span>更新于 {formatRelativeTime(srv.toolCacheUpdatedAt)}</span>
                      {srv.toolCacheStatus === "empty" && <span className="mcp-cache-status mcp-cache-status--empty">未同步</span>}
                      {srv.toolCacheStatus === "error" && <span className="mcp-cache-status mcp-cache-status--error">上次刷新失败</span>}
                    </div>
                  </div>
                  <div className="mcp-server-row__actions" onClick={e => e.stopPropagation()}>
                    <button type="button" className="cici-btn cici-btn--text cici-btn--xs" onClick={() => openEdit(srv)}>编辑</button>
                    <button type="button" className="cici-btn cici-btn--text cici-btn--xs cici-btn--danger" onClick={() => void deleteServer(srv.id)}>删除</button>
                  </div>
                </div>
              ))}
            </div>
          </>
        )}

        {mcpView === "detail" && selected && (
          <div className="cici-kb-detail">
            <aside className="cici-kb-sidebar">
              <div className="cici-kb-sidebar__head">
                <div className="mcp-detail-badge">
                  <span className="mcp-detail-badge__name">{selected.name}</span>
                  <span className={`cici-doc-badge cici-doc-badge--sm ${selected.enabled ? "cici-doc-badge--published" : "cici-doc-badge--uploaded"}`}>
                    {selected.enabled ? "运行中" : "已停用"}
                  </span>
                </div>
              </div>
              <nav className="cici-kb-sidebar__nav">
                <button type="button" className={`cici-kb-sidebar__link ${detailTab === "general" ? "active" : ""}`} onClick={() => setDetailTab("general")}>
                  <svg viewBox="0 0 20 20" width="18" height="18" fill="none"><rect x="3" y="3" width="14" height="14" rx="3" stroke="currentColor" strokeWidth="1.4"/><path d="M7 7h6M7 10h4M7 13h5" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round"/></svg>
                  通用
                </button>
                <button type="button" className={`cici-kb-sidebar__link ${detailTab === "tools" ? "active" : ""}`} onClick={() => { setDetailTab("tools"); void loadToolCache(selected.id); }}>
                  <svg viewBox="0 0 20 20" width="18" height="18" fill="none"><path d="M10 3v2M10 15v2M3 10h2M15 10h2M5.6 5.6l1.4 1.4M13 13l1.4 1.4M14.4 5.6l-1.4 1.4M7 13l-1.4 1.4" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round"/><circle cx="10" cy="10" r="3" stroke="currentColor" strokeWidth="1.4"/></svg>
                  工具 ({selected.toolCacheCount ?? 0})
                </button>
                <button type="button" className={`cici-kb-sidebar__link ${detailTab === "applications" ? "active" : ""}`} onClick={() => setDetailTab("applications")}>
                  <svg viewBox="0 0 20 20" width="18" height="18" fill="none"><rect x="3" y="4" width="14" height="12" rx="2" stroke="currentColor" strokeWidth="1.4"/><path d="M7 8h6M7 12h4" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round"/></svg>
                  应用绑定
                </button>
              </nav>
              <div className="cici-kb-sidebar__footer">
                <button type="button" className="cici-btn cici-btn--ghost cici-btn--sm cici-btn--full" onClick={() => { setMcpView("list"); setSelected(null); setTools([]); }}>
                  ← 返回列表
                </button>
              </div>
            </aside>

            <main className="cici-kb-main">
              {detailTab === "general" && (
                <div className="mcp-general">
                  <div className="mcp-general__header">
                    <h2 className="cici-kb-main__title">通用配置</h2>
                    <div className="admin-tools__header-actions">
                      <button type="button" className="cici-btn cici-btn--ghost cici-btn--sm" disabled={connecting} onClick={() => void testConnection(selected.id)}>
                        {connecting ? "连接中..." : "测试连接"}
                      </button>
                      <button type="button" className="cici-btn cici-btn--ghost cici-btn--sm" onClick={() => openEdit(selected)}>编辑</button>
                      <button type="button" className="cici-btn cici-btn--danger cici-btn--sm" onClick={() => void deleteServer(selected.id)}>删除</button>
                    </div>
                  </div>

                  <div className="mcp-fields">
                    <div className="mcp-field-row">
                      <span className="mcp-field-row__label">* 名称</span>
                      <span className="mcp-field-row__value">{selected.name}</span>
                    </div>
                    <div className="mcp-field-row">
                      <span className="mcp-field-row__label">描述</span>
                      <span className="mcp-field-row__value mcp-field-row__value--muted">{selected.description || "无"}</span>
                    </div>
                    <div className="mcp-field-row">
                      <span className="mcp-field-row__label">* 类型</span>
                      <span className="mcp-field-row__value">{TRANSPORT_OPTIONS.find(o => o.value === selected.transportType)?.label ?? selected.transportType}</span>
                    </div>
                    <div className="mcp-field-row">
                      <span className="mcp-field-row__label">* URL</span>
                      <span className="mcp-field-row__value mcp-field-row__value--mono">{selected.url}</span>
                    </div>
                    <div className="mcp-field-row">
                      <span className="mcp-field-row__label">请求头</span>
                      <pre className="mcp-field-row__pre">{selected.headers || "无自定义请求头"}</pre>
                    </div>
                    <div className="mcp-field-row">
                      <span className="mcp-field-row__label">身份认证</span>
                      <span className="mcp-field-row__value">{selected.authType === "KEYCLOAK_CLIENT_CREDENTIALS" ? `Keycloak Client Credentials · ${selected.clientId}` : "无"}</span>
                    </div>
                    {selected.authType === "KEYCLOAK_CLIENT_CREDENTIALS" && <div className="mcp-field-row">
                      <span className="mcp-field-row__label">Audience / Scope</span>
                      <span className="mcp-field-row__value mcp-field-row__value--mono">{selected.tokenAudience} · {selected.tokenScopes}</span>
                    </div>}
                    <div className="mcp-field-row">
                      <span className="mcp-field-row__label">超时</span>
                      <span className="mcp-field-row__value">{selected.timeoutSeconds} 秒</span>
                    </div>
                    <div className="mcp-field-row">
                      <span className="mcp-field-row__label">状态</span>
                      <span className="mcp-field-row__value">
                        <span className={`cici-doc-badge ${selected.enabled ? "cici-doc-badge--published" : "cici-doc-badge--uploaded"}`}>
                          {selected.enabled ? "已启用" : "已停用"}
                        </span>
                      </span>
                    </div>
                  </div>
                </div>
              )}

              {detailTab === "tools" && (
                <div className="mcp-tools-tab">
                  <div className="mcp-tools-tab__header">
                    <div className="mcp-tools-tab__summary">
                      <h2 className="cici-kb-main__title">工具列表</h2>
                      <div className="mcp-tools-tab__summary-meta">
                        <span>包含 {tools.length} 个工具</span>
                        <span>更新于 {formatRelativeTime(toolCacheUpdatedAt)}</span>
                        {toolCacheStatus === "ready" && <span className="mcp-cache-status mcp-cache-status--ready">缓存可用</span>}
                        {toolCacheStatus === "empty" && <span className="mcp-cache-status mcp-cache-status--empty">未同步</span>}
                        {toolCacheStatus === "refreshing" && <span className="mcp-cache-status">刷新中</span>}
                        {toolCacheStatus === "error" && <span className="mcp-cache-status mcp-cache-status--error">上次刷新失败</span>}
                      </div>
                      {toolCacheStatus === "error" && toolCacheErrorMessage && (
                        <div className="mcp-tools-tab__error">失败原因：{toolCacheErrorMessage}</div>
                      )}
                    </div>
                    <button type="button" className="cici-btn cici-btn--ghost cici-btn--sm" disabled={discovering} onClick={() => void discoverTools(selected.id)}>
                      {discovering ? "发现中..." : "刷新工具"}
                    </button>
                  </div>
                  {tools.length === 0 && !discovering && <p className="mcp-empty">暂无缓存工具。点击「刷新工具」从 MCP 服务器同步。</p>}
                  {discovering && <p className="mcp-empty">正在从 MCP 服务器发现工具...</p>}
                  <div className="mcp-tool-list">
                    <div className="mcp-tool-list__header">
                      <div>可用工具</div>
                      <div>启用工具</div>
                      <div>自动批准</div>
                    </div>
                    {tools.map((t, i) => {
                      const pref = toolPrefs[t.name] ?? { enabled: true, autoApprove: true };
                      return (
                        <div key={i} className="mcp-tool-row">
                          <div className="mcp-tool-row__main">
                            <div className="mcp-tool-row__name">{t.name}</div>
                            <div className="mcp-tool-row__desc">{t.description || "无描述"}</div>
                            <details className="mcp-tool-row__schema">
                              <summary>展开参数 Schema</summary>
                              <pre>{formatSchemaForDisplay(t.inputSchema)}</pre>
                            </details>
                          </div>
                          <div className="mcp-tool-row__switch">
                            <button
                              type="button"
                              className={`cici-toggle ${pref.enabled ? "cici-toggle--on" : ""}`}
                              onClick={() => setToolPref(t.name, { enabled: !pref.enabled })}
                            />
                          </div>
                          <div className="mcp-tool-row__switch">
                            <button
                              type="button"
                              className={`cici-toggle ${pref.autoApprove ? "cici-toggle--on" : ""}`}
                              onClick={() => setToolPref(t.name, { autoApprove: !pref.autoApprove })}
                            />
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              )}
              {detailTab === "applications" && (
                <div className="mcp-tools-tab">
                  <div className="mcp-tools-tab__header"><div className="mcp-tools-tab__summary"><h2 className="cici-kb-main__title">绑定应用版本</h2><p>把当前连接精确绑定到应用版本声明的 Provider；绑定时会重新发现工具并校验完整白名单。</p></div></div>
                  <div className="mcp-modal-row">
                    <label className="cici-field admin-tools__field-flex"><span className="cici-field__label">应用代码</span><input className="cici-field__input" value={bindingAppCode} onChange={e => setBindingAppCode(e.target.value.toLowerCase())} placeholder="devautopilot" /></label>
                    <label className="cici-field admin-tools__field-flex"><span className="cici-field__label">应用版本</span><input className="cici-field__input" value={bindingVersion} onChange={e => setBindingVersion(e.target.value)} placeholder="1.0.0" /></label>
                    <label className="cici-field admin-tools__field-flex"><span className="cici-field__label">Provider Key</span><input className="cici-field__input" value={bindingProviderKey} onChange={e => setBindingProviderKey(e.target.value.toLowerCase())} placeholder="devautopilot.mcp" /></label>
                  </div>
                  <div className="cici-modal__actions"><button type="button" className="cici-btn cici-btn--ghost" onClick={() => void loadApplicationBindings()}>读取绑定</button><button type="button" className="cici-btn cici-btn--primary" onClick={() => void bindSelectedServer()}>校验并绑定当前服务器</button></div>
                  {applicationBindings.map(binding => <div className="mcp-tool-row" key={binding.id}><div className="mcp-tool-row__main"><div className="mcp-tool-row__name">{binding.appCode}@{binding.version} · {binding.providerKey}</div><div className="mcp-tool-row__desc">{binding.serverName} · {binding.status}</div></div></div>)}
                </div>
              )}
            </main>
          </div>
        )}
        {showModal && renderModal()}
      </div>
    );
  }

  function renderBuiltinCard(tool: BuiltinTool) {
    const style = categoryStyle(tool.category);
    const risk = RISK_TAG[tool.riskLevel];
    return (
      <div key={tool.toolName} className="admin-tools-card" title={tool.description || tool.toolName}>
        <div className="admin-tools-card__head">
          <div className="admin-tools-card__icon">
            <span aria-hidden>{style.glyph}</span>
          </div>
          <div className="admin-tools-card__title-wrap">
            <div className="admin-tools-card__name">{tool.displayName || tool.toolName}</div>
            <div className="admin-tools-card__slug">{tool.toolName}</div>
          </div>
        </div>
        <p className="admin-tools-card__desc">{tool.description || "暂无描述"}</p>
        <div className="admin-tools-card__footer">
          <span className="admin-tools-card__tag">
            {style.label}
          </span>
          {risk && (
            <span className={`cici-doc-badge ${risk.cls} cici-doc-badge--sm`}>{risk.label}</span>
          )}
          <span className="admin-tools-card__builtin">内置</span>
        </div>
      </div>
    );
  }

  function renderModal() {
    return (
      <div className="cici-modal-overlay" onClick={() => setShowModal(false)}>
        <div className="cici-modal cici-modal--wide" onClick={e => e.stopPropagation()}>
          <h2 className="cici-modal__title">{editId ? "编辑 MCP 服务器" : "添加 MCP 服务器"}</h2>
          <div className="cici-modal__body">
            <label className="cici-field">
              <span className="cici-field__label">名称<span className="cici-field__required">*</span></span>
              <input className="cici-field__input" value={fName} onChange={e => setFName(e.target.value)} placeholder="CC-MCP-Cloud" />
            </label>
            <label className="cici-field">
              <span className="cici-field__label">描述</span>
              <textarea className="cici-field__textarea" value={fDesc} onChange={e => setFDesc(e.target.value)} placeholder="描述此 MCP 服务器的用途" rows={2} />
            </label>
            <label className="cici-field">
              <span className="cici-field__label">传输类型<span className="cici-field__required">*</span></span>
              <select className="cici-field__input" value={fType} onChange={e => setFType(e.target.value)}>
                {TRANSPORT_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
              </select>
            </label>
            <label className="cici-field">
              <span className="cici-field__label">URL<span className="cici-field__required">*</span></span>
              <input className="cici-field__input" value={fUrl} onChange={e => setFUrl(e.target.value)} placeholder="https://mcp.cloudcc.cn/mcp" />
            </label>
            <label className="cici-field">
              <span className="cici-field__label">请求头</span>
              <textarea className="cici-field__textarea" value={fHeaders} onChange={e => setFHeaders(e.target.value)} placeholder={"X-Custom-Header=value"} rows={2} />
              <span className="cici-field__hint-text">每行一个 Key=Value；不要在这里保存 Bearer Token。</span>
            </label>
            <label className="cici-field"><span className="cici-field__label">身份认证</span><select className="cici-field__input" value={fAuthType} onChange={e => setFAuthType(e.target.value)}><option value="NONE">无</option><option value="KEYCLOAK_CLIENT_CREDENTIALS">Keycloak Client Credentials</option></select></label>
            {fAuthType === "KEYCLOAK_CLIENT_CREDENTIALS" && <>
              <label className="cici-field"><span className="cici-field__label">Keycloak Token URL<span className="cici-field__required">*</span></span><input className="cici-field__input" value={fTokenUrl} onChange={e => setFTokenUrl(e.target.value)} placeholder="由当前环境Keycloak提供" /></label>
              <div className="mcp-modal-row">
                <label className="cici-field admin-tools__field-flex"><span className="cici-field__label">Client ID<span className="cici-field__required">*</span></span><input className="cici-field__input" value={fClientId} onChange={e => setFClientId(e.target.value)} autoComplete="off" /></label>
                <label className="cici-field admin-tools__field-flex"><span className="cici-field__label">Client Secret{editId ? "（留空保持不变）" : "*"}</span><input className="cici-field__input" type="password" value={fClientSecret} onChange={e => setFClientSecret(e.target.value)} autoComplete="new-password" /></label>
              </div>
              <div className="mcp-modal-row">
                <label className="cici-field admin-tools__field-flex"><span className="cici-field__label">Audience</span><input className="cici-field__input" value={fTokenAudience} onChange={e => setFTokenAudience(e.target.value)} placeholder="devautopilot-mcp" /></label>
                <label className="cici-field admin-tools__field-flex"><span className="cici-field__label">Scope</span><input className="cici-field__input" value={fTokenScopes} onChange={e => setFTokenScopes(e.target.value)} placeholder="devautopilot:mcp" /></label>
              </div>
              <span className="cici-field__hint-text">Secret由后端AES-GCM加密保存；运行时只换取短时Keycloak Access Token。</span>
            </>}
            <div className="mcp-modal-row">
              <label className="cici-field admin-tools__field-flex">
                <span className="cici-field__label">超时（秒）</span>
                <input className="cici-field__input" type="number" value={fTimeout} onChange={e => setFTimeout(Number(e.target.value))} min={1} />
                <span className="cici-field__hint-text">默认 60 秒</span>
              </label>
              {editId !== null && (
                <label className="cici-field admin-tools__field-flex">
                  <span className="cici-field__label">启用状态</span>
                  <div className="admin-tools__toggle-row">
                    <button type="button" className={`cici-toggle ${fEnabled ? "cici-toggle--on" : ""}`} onClick={() => setFEnabled(!fEnabled)} />
                    <span className="admin-tools__toggle-label">{fEnabled ? "启用" : "停用"}</span>
                  </div>
                </label>
              )}
            </div>
          </div>
          <div className="cici-modal__actions">
            <button type="button" className="cici-btn cici-btn--ghost" onClick={() => setShowModal(false)}>取消</button>
            <button type="button" className="cici-btn cici-btn--primary" onClick={saveServer}>{editId ? "保存" : "创建"}</button>
          </div>
        </div>
      </div>
    );
  }
}
