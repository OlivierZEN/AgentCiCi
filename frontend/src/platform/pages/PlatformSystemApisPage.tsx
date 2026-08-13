import { FormEvent, useEffect, useMemo, useState } from "react";
import { ArrowLeft, ChevronRight, ExternalLink, Search, X } from "lucide-react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { authFetch } from "../../auth/authStorage";
import { LS_PLATFORM_TOKEN, PLATFORM_API_BASE } from "../../constants";
import { safeFetchJson } from "../../utils/http";

export type SystemApi = {
  id: string;
  title: string;
  summary: string;
  description: string;
  category: string;
  method: string;
  path: string;
  protocols: string[];
  authType: string;
  audience: string;
  requiredScope: string;
  riskLevel: string;
  version: string;
  state: string;
  idempotencyRequired: boolean;
  executionMode: string;
  approvalRequired: boolean;
  consumers: string[];
  inputSchema: unknown;
  outputSchema: unknown;
  requestExample: unknown;
  responseExample: unknown;
  errorCodes: string[];
  compatibility: string;
  sourceContract: string;
  callNotes: string[];
};

export type SystemApiProvider = {
  code: string;
  name: string;
  description: string;
  contractVersion: string;
  status: string;
  statusMessage: string;
  apis: SystemApi[];
};

type Catalog = {
  contractVersion: string;
  notice: string;
  providers: SystemApiProvider[];
};

const EMPTY_CATALOG: Catalog = { contractVersion: "v1", notice: "", providers: [] };
export const SYSTEM_API_CATALOG_ENDPOINT = `${PLATFORM_API_BASE}/system-apis`;

export function systemApiCatalogFailureMessage(status: number, message: string | undefined, rawText: string): string {
  if (message?.trim()) return message.trim();
  if (rawText.trimStart().startsWith("<")) {
    return `系统 API 目录加载失败：服务未返回预期数据（HTTP ${status}）。请刷新页面并确认前后端版本一致后重试。`;
  }
  return `系统 API 目录加载失败（HTTP ${status}）。请稍后重试。`;
}

export function filterSystemApis(apis: SystemApi[], query: string, category: string, risk: string) {
  const needle = query.trim().toLocaleLowerCase("zh-CN");
  return apis.filter((api) => {
    const matchesQuery = !needle || [api.title, api.id, api.summary, api.path, api.requiredScope]
      .some((value) => value.toLocaleLowerCase("zh-CN").includes(needle));
    return matchesQuery && (!category || api.category === category) && (!risk || api.riskLevel === risk);
  });
}

export function systemApiRequestPrelude(api: SystemApi): string {
  const lines = [`${api.method} \${SYSTEM_API_ORIGIN}${api.path}`];
  if (api.authType === "Bearer AgentCiCi HUMAN token") {
    lines.push("Authorization: Bearer ${AGENTCICI_USER_TOKEN}");
  } else if (api.authType === "Keycloak SERVICE Bearer") {
    lines.push("Authorization: Bearer ${KEYCLOAK_SERVICE_TOKEN}");
  } else if (api.authType === "Bearer OACT") {
    lines.push("Authorization: Bearer ${OACT}");
  } else if (api.authType.includes("Internal HMAC")) {
    lines.push("# 按契约生成 Internal HMAC 请求头");
  }
  if (!["GET", "HEAD"].includes(api.method.toUpperCase())) lines.push("Content-Type: application/json");
  return lines.join("\n");
}

function json(value: unknown) {
  return JSON.stringify(value ?? {}, null, 2);
}

function riskLabel(value: string) {
  if (value === "high") return "高风险";
  if (value === "medium") return "中风险";
  return "低风险";
}

function stateLabel(value: string) {
  return value === "published" ? "已发布" : value || "未知";
}

function providerPath(code: string) {
  return `/platform/system-apis/${code}`;
}

function apiPath(providerCode: string, apiId: string) {
  return `${providerPath(providerCode)}/${encodeURIComponent(apiId)}`;
}

export default function PlatformSystemApisPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { providerCode, apiId } = useParams();
  const isDocs = location.pathname.endsWith("/docs");
  const [catalog, setCatalog] = useState<Catalog>(EMPTY_CATALOG);
  const [loading, setLoading] = useState(true);
  const [notice, setNotice] = useState("");
  const [keyword, setKeyword] = useState("");
  const [query, setQuery] = useState("");
  const [category, setCategory] = useState("");
  const [risk, setRisk] = useState("");

  async function loadCatalog() {
    setLoading(true);
    setNotice("");
    try {
      const response = await authFetch(LS_PLATFORM_TOKEN, SYSTEM_API_CATALOG_ENDPOINT, {
        headers: { Accept: "application/json" },
      });
      const { body, rawText } = await safeFetchJson<Catalog>(response);
      if (!response.ok || !body?.success || !body.data) {
        throw new Error(systemApiCatalogFailureMessage(response.status, body?.message, rawText));
      }
      setCatalog(body.data);
    } catch (error) {
      setCatalog(EMPTY_CATALOG);
      setNotice(error instanceof Error ? error.message : "系统 API 目录加载失败");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void loadCatalog(); }, []);

  const provider = catalog.providers.find((item) => item.code === providerCode);
  const selectedApi = provider?.apis.find((item) => item.id === apiId);
  const categories = useMemo(() => Array.from(new Set(provider?.apis.map((api) => api.category) ?? [])).sort(), [provider]);
  const filteredApis = useMemo(() => filterSystemApis(provider?.apis ?? [], query, category, risk), [provider, query, category, risk]);

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setQuery(keyword);
  }

  if (isDocs && provider && selectedApi) {
    return (
      <div className="admin-page platform-page system-api-docs">
        <button type="button" className="system-api-back" onClick={() => navigate(apiPath(provider.code, selectedApi.id))}>
          <ArrowLeft size={16} /> 返回 API 速览
        </button>
        <header className="system-api-docs__head">
          <div>
            <span>{provider.name} · {selectedApi.category}</span>
            <h1>{selectedApi.title}</h1>
            <p>{selectedApi.summary}</p>
          </div>
          <div className="system-api-docs__identity"><code>{selectedApi.method}</code><code>{selectedApi.path}</code></div>
        </header>
        <div className="system-api-docs__layout">
          <aside className="system-api-docs__toc" aria-label="本文目录">
            <strong>调用说明</strong>
            <a href="#contract">契约摘要</a><a href="#request">请求</a><a href="#response">响应</a><a href="#errors">错误与约束</a><a href="#compatibility">兼容策略</a>
          </aside>
          <article className="system-api-docs__article">
            <section id="contract"><h2>契约摘要</h2><p>{selectedApi.description}</p><dl className="system-api-definition-list"><div><dt>鉴权</dt><dd>{selectedApi.authType}</dd></div><div><dt>Audience</dt><dd>{selectedApi.audience}</dd></div><div><dt>所需 Scope</dt><dd><code>{selectedApi.requiredScope}</code></dd></div><div><dt>协议投影</dt><dd>{selectedApi.protocols.join(" / ")}</dd></div><div><dt>风险与执行</dt><dd>{riskLabel(selectedApi.riskLevel)} · {selectedApi.executionMode}{selectedApi.approvalRequired ? " · 需要审批" : ""}</dd></div><div><dt>契约事实源</dt><dd>{selectedApi.sourceContract}</dd></div></dl></section>
            <section id="request"><h2>请求</h2><p>调用地址由部署环境注入，请勿在应用代码中固化环境域名。</p><pre><code>{systemApiRequestPrelude(selectedApi)}</code></pre><h3>请求示例</h3><pre><code>{json(selectedApi.requestExample)}</code></pre><h3>输入 Schema</h3><pre><code>{json(selectedApi.inputSchema)}</code></pre></section>
            <section id="response"><h2>响应</h2><h3>成功示例</h3><pre><code>{json(selectedApi.responseExample)}</code></pre><h3>输出 Schema</h3><pre><code>{json(selectedApi.outputSchema)}</code></pre></section>
            <section id="errors"><h2>错误与调用约束</h2><ul>{selectedApi.callNotes.map((note) => <li key={note}>{note}</li>)}</ul><div className="system-api-error-codes">{selectedApi.errorCodes.map((code) => <code key={code}>{code}</code>)}</div></section>
            <section id="compatibility"><h2>兼容策略</h2><p>{selectedApi.compatibility}</p><p className="system-api-docs__permission-note">出现在系统 API 目录中不代表调用方自动获得权限。调用方仍需完成应用激活、主体绑定、scope 和提供方授权门禁。</p></section>
          </article>
        </div>
      </div>
    );
  }

  return (
    <div className="admin-page skills-catalog platform-page system-api-page">
      <header className="skills-catalog__header platform-page-head">
        <div className="platform-page-head__main">
          {provider ? <button type="button" className="system-api-back" onClick={() => navigate("/platform/system-apis")}><ArrowLeft size={15} /> 系统 API</button> : null}
          <h1 className="skills-catalog__title">{provider ? provider.name : "系统 API"}</h1>
          <p className="subtle skills-catalog__subtitle">{provider ? provider.description : "面向内部生态应用的稳定跨应用契约目录。这里只展示经过治理、可被依赖的核心 API。"}</p>
        </div>
        <div className="platform-page-head__aside"><span className="platform-inline-stat">契约 {provider?.contractVersion ?? catalog.contractVersion}</span><button type="button" className="platform-button platform-button--secondary" onClick={() => void loadCatalog()} disabled={loading}>刷新目录</button></div>
      </header>

      {catalog.notice && !provider ? <div className="system-api-governance-note"><strong>目录边界</strong><span>{catalog.notice}</span></div> : null}
      {notice ? <p className="platform-console__banner platform-console__banner--error">{notice}</p> : null}

      {!providerCode ? (
        <section className="skills-table-wrap system-api-provider-list" aria-label="系统 API 提供方">
          <div className="system-api-section-head"><div><span className="platform-section-label">提供方目录</span><p>选择系统后进入其独立 API 列表；具体调用说明在记录详情中逐级展开。</p></div><span>{catalog.providers.reduce((sum, item) => sum + item.apis.length, 0)} 项核心契约</span></div>
          <table className="skills-data-table system-api-provider-table"><thead><tr><th>系统</th><th>职责边界</th><th>契约版本</th><th>API 数量</th><th>目录状态</th><th aria-label="操作" /></tr></thead><tbody>
            {catalog.providers.map((item) => <tr key={item.code} className="platform-console__select-row" tabIndex={0} onClick={() => navigate(providerPath(item.code))} onKeyDown={(event) => { if (event.key === "Enter") navigate(providerPath(item.code)); }}><td><div className="skills-data-table__skill-name">{item.name}</div><code className="skills-data-table__skill-code">{item.code}</code></td><td><div className="skills-data-table__summary">{item.description}</div>{item.statusMessage ? <div className="system-api-provider-warning">{item.statusMessage}</div> : null}</td><td>{item.contractVersion}</td><td>{item.apis.length}</td><td><span className={`system-api-status is-${item.status}`}>{item.status === "available" ? "可用" : "暂不可用"}</span></td><td><ChevronRight size={17} /></td></tr>)}
          </tbody></table>
          {!loading && catalog.providers.length === 0 ? <div className="system-api-empty">暂无可用的系统 API 提供方。</div> : null}
        </section>
      ) : provider ? (
        <>
          {provider.status !== "available" ? <p className="platform-console__banner platform-console__banner--error">{provider.statusMessage || "提供方目录暂不可用"}</p> : null}
          <section className="skills-table-wrap system-api-list" aria-label={`${provider.name} API 列表`}>
            <div className="system-api-toolbar">
              <form onSubmit={submitSearch} className="system-api-search"><Search size={16} aria-hidden /><input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="搜索 API 名称、ID、路径或 Scope" aria-label="搜索系统 API" /><button type="submit" className="platform-button platform-button--secondary">搜索</button></form>
              <div className="system-api-filters"><select value={category} onChange={(event) => setCategory(event.target.value)} aria-label="按分类筛选"><option value="">全部分类</option>{categories.map((item) => <option key={item}>{item}</option>)}</select><select value={risk} onChange={(event) => setRisk(event.target.value)} aria-label="按风险筛选"><option value="">全部风险</option><option value="low">低风险</option><option value="medium">中风险</option><option value="high">高风险</option></select><span>{filteredApis.length} 项</span></div>
            </div>
            <table className="skills-data-table system-api-table"><thead><tr><th>API</th><th>分类</th><th>调用</th><th>所需 Scope</th><th>风险</th><th>版本 / 状态</th><th aria-label="操作" /></tr></thead><tbody>
              {filteredApis.map((api) => <tr key={api.id} className={`platform-console__select-row${selectedApi?.id === api.id ? " platform-console__row--active" : ""}`} tabIndex={0} onClick={() => navigate(apiPath(provider.code, api.id))} onKeyDown={(event) => { if (event.key === "Enter") navigate(apiPath(provider.code, api.id)); }}><td><div className="skills-data-table__skill-name">{api.title}</div><code className="skills-data-table__skill-code">{api.id}</code><div className="skills-data-table__summary">{api.summary}</div></td><td>{api.category}</td><td><code className={`system-api-method is-${api.method.toLowerCase()}`}>{api.method}</code><code className="system-api-route">{api.path}</code></td><td><code className="system-api-scope">{api.requiredScope}</code></td><td>{riskLabel(api.riskLevel)}</td><td>{api.version}<span className="system-api-cell-note">{stateLabel(api.state)}</span></td><td><ChevronRight size={17} /></td></tr>)}
            </tbody></table>
            {!loading && filteredApis.length === 0 ? <div className="system-api-empty">没有符合当前筛选条件的 API。</div> : null}
          </section>
        </>
      ) : !loading ? <div className="system-api-not-found"><h2>未找到系统提供方</h2><button type="button" className="platform-button platform-button--secondary" onClick={() => navigate("/platform/system-apis")}>返回目录</button></div> : null}

      {provider && selectedApi && !isDocs ? (
        <div className="system-api-drawer-layer" onMouseDown={() => navigate(providerPath(provider.code))}>
          <aside className="system-api-drawer" role="dialog" aria-modal="true" aria-labelledby="system-api-drawer-title" onMouseDown={(event) => event.stopPropagation()}>
            <header className="system-api-drawer__head"><div><span>{provider.name} · API 速览</span><h2 id="system-api-drawer-title">{selectedApi.title}</h2><code>{selectedApi.id}</code><p>{selectedApi.summary}</p></div><button type="button" className="system-api-icon-button" aria-label="关闭 API 详情" onClick={() => navigate(providerPath(provider.code))}><X size={20} /></button></header>
            <div className="system-api-drawer__body">
              <div className="system-api-drawer__route"><code className={`system-api-method is-${selectedApi.method.toLowerCase()}`}>{selectedApi.method}</code><code>{selectedApi.path}</code></div>
              <section><h3>调用约束</h3><dl className="system-api-definition-list"><div><dt>鉴权方式</dt><dd>{selectedApi.authType}</dd></div><div><dt>Audience</dt><dd>{selectedApi.audience}</dd></div><div><dt>所需 Scope</dt><dd><code>{selectedApi.requiredScope}</code></dd></div><div><dt>风险等级</dt><dd>{riskLabel(selectedApi.riskLevel)}</dd></div><div><dt>幂等</dt><dd>{selectedApi.idempotencyRequired ? "支持 / 需要稳定幂等键" : "不要求"}</dd></div><div><dt>协议投影</dt><dd>{selectedApi.protocols.join(" / ")}</dd></div></dl></section>
              <section><h3>典型消费者</h3><div className="system-api-consumers">{selectedApi.consumers.map((consumer) => <span key={consumer}>{consumer}</span>)}</div></section>
              <section><h3>关键说明</h3><ul>{selectedApi.callNotes.map((note) => <li key={note}>{note}</li>)}</ul></section>
              <section><h3>请求示例</h3><pre><code>{json(selectedApi.requestExample)}</code></pre></section>
              <p className="system-api-drawer__permission">目录可见不代表已获得调用权限；实际调用仍需满足该契约的身份、租户、scope 与提供方授权。</p>
            </div>
            <footer className="system-api-drawer__footer"><span>{selectedApi.sourceContract}</span><button type="button" className="platform-button platform-button--primary" onClick={() => navigate(`${apiPath(provider.code, selectedApi.id)}/docs`)}>查看完整调用文档 <ExternalLink size={15} /></button></footer>
          </aside>
        </div>
      ) : null}
    </div>
  );
}
