import { useEffect, useMemo, useState } from "react";
import {
  Check,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  CircleAlert,
  CircleHelp,
  Columns3,
  Database,
  FileWarning,
  Layers3,
  RefreshCw,
  Search,
  Sparkles,
  X,
} from "lucide-react";
import { authFetch } from "../auth/authStorage";
import { LS_ASSISTANT_TOKEN } from "../constants";
import { applyProductTheme, PRODUCT_THEMES, type ProductThemeCode } from "../theme/theme";

type AiTableField = {
  apiName: string;
  label: string;
  dataType: string;
  indexed: boolean;
  defaultVisible: boolean;
};

type AiTableObject = {
  apiName: string;
  label: string;
  description: string;
  searchFieldApiName: string;
  searchFieldLabel: string;
  fields: AiTableField[];
};

type AiTableRecord = {
  id: string;
  revision: number;
  data: Record<string, unknown>;
};

type Catalog = {
  companyName: string;
  preferenceScope: string;
  retrievedAt: string;
  objects: AiTableObject[];
};

type RecordPage = {
  objectApiName: string;
  records: AiTableRecord[];
  nextCursor: string;
  retrievedAt: string;
  queryFieldLabel: string;
  searchSupported: boolean;
};

type ApiEnvelope<T> = {
  success: boolean;
  data: T;
  message: string;
};

export async function requestAiTable<T>(url: string, signal?: AbortSignal): Promise<T> {
  const response = await authFetch(LS_ASSISTANT_TOKEN, url, { credentials: "same-origin", signal });
  const body = await response.json().catch(() => null) as ApiEnvelope<T> | null;
  if (!response.ok || !body?.success) {
    throw new Error(body?.message || "业务数据暂时不可用，请稍后重试。");
  }
  return body.data;
}

function formatTime(value: string) {
  if (!value) return "刚刚";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "刚刚";
  return new Intl.DateTimeFormat("zh-CN", { hour: "2-digit", minute: "2-digit" }).format(date);
}

function formatValue(value: unknown) {
  if (value === null || value === undefined || value === "") return "—";
  if (typeof value === "boolean") return value ? "是" : "否";
  if (typeof value === "number") return new Intl.NumberFormat("zh-CN").format(value);
  if (typeof value === "string") return value;
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}

function defaultColumns(object: AiTableObject) {
  return object.fields.filter((field) => field.defaultVisible).slice(0, 6).map((field) => field.apiName);
}

function storageKey(scope: string, objectApiName: string) {
  return `cici.ai-table.columns.${scope}.${objectApiName}`;
}

function restoreColumns(scope: string, object: AiTableObject) {
  if (!scope) return defaultColumns(object);
  try {
    const saved = JSON.parse(window.localStorage.getItem(storageKey(scope, object.apiName)) || "[]") as unknown;
    if (Array.isArray(saved)) {
      const allowed = new Set(object.fields.map((field) => field.apiName));
      const restored = saved.filter((value): value is string => typeof value === "string" && allowed.has(value));
      if (restored.length) return restored;
    }
  } catch {
    // A malformed local preference must never prevent the protected data view from loading.
  }
  return defaultColumns(object);
}

export function AiTableBusinessObjectList() {
  const [catalog, setCatalog] = useState<Catalog | null>(null);
  const [catalogLoading, setCatalogLoading] = useState(true);
  const [catalogError, setCatalogError] = useState("");
  const [activeApiName, setActiveApiName] = useState("");
  const [query, setQuery] = useState("");
  const [appliedQuery, setAppliedQuery] = useState("");
  const [page, setPage] = useState(1);
  const [cursors, setCursors] = useState<string[]>([""]);
  const [recordPage, setRecordPage] = useState<RecordPage | null>(null);
  const [recordsLoading, setRecordsLoading] = useState(false);
  const [recordsError, setRecordsError] = useState("");
  const [reloadNonce, setReloadNonce] = useState(0);
  const [selectedRecord, setSelectedRecord] = useState<AiTableRecord | null>(null);
  const [visibleColumns, setVisibleColumns] = useState<string[]>([]);
  const [columnMenuOpen, setColumnMenuOpen] = useState(false);
  const [themeMenuOpen, setThemeMenuOpen] = useState(false);
  const [themeCode, setThemeCode] = useState<ProductThemeCode>(() => (document.documentElement.dataset.theme as ProductThemeCode) || "gilded");
  const [notice, setNotice] = useState("");

  const activeObject = useMemo(
    () => catalog?.objects.find((object) => object.apiName === activeApiName) ?? null,
    [activeApiName, catalog],
  );
  const activeFields = useMemo(
    () => activeObject?.fields.filter((field) => visibleColumns.includes(field.apiName)) ?? [],
    [activeObject, visibleColumns],
  );
  const activeTheme = PRODUCT_THEMES.find((theme) => theme.code === themeCode) ?? PRODUCT_THEMES[0];
  const currentCursor = cursors[page - 1] || "";
  const searchSupported = recordPage?.searchSupported ?? Boolean(activeObject?.searchFieldApiName);

  const loadCatalog = () => {
    const controller = new AbortController();
    setCatalogLoading(true);
    setCatalogError("");
    void requestAiTable<Catalog>("/ai-table/catalog", controller.signal)
      .then((nextCatalog) => {
        setCatalog(nextCatalog);
        setActiveApiName((current) => current && nextCatalog.objects.some((object) => object.apiName === current)
          ? current : nextCatalog.objects[0]?.apiName || "");
      })
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === "AbortError") return;
        setCatalogError(error instanceof Error ? error.message : "业务对象目录加载失败。");
      })
      .finally(() => setCatalogLoading(false));
    return controller;
  };

  useEffect(() => {
    const controller = loadCatalog();
    return () => controller.abort();
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => setAppliedQuery(query.trim()), 320);
    return () => window.clearTimeout(timer);
  }, [query]);

  useEffect(() => {
    setPage(1);
    setCursors([""]);
    setRecordPage(null);
    setSelectedRecord(null);
    setColumnMenuOpen(false);
    if (activeObject) setVisibleColumns(restoreColumns(catalog?.preferenceScope || "", activeObject));
  }, [activeApiName, appliedQuery]);

  useEffect(() => {
    if (!activeObject) return;
    const controller = new AbortController();
    const params = new URLSearchParams({ limit: "25" });
    if (currentCursor) params.set("after", currentCursor);
    if (appliedQuery) params.set("query", appliedQuery);
    setRecordsLoading(true);
    setRecordsError("");
    void requestAiTable<RecordPage>(`/ai-table/objects/${encodeURIComponent(activeObject.apiName)}/records?${params}`, controller.signal)
      .then((nextPage) => setRecordPage(nextPage))
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === "AbortError") return;
        setRecordsError(error instanceof Error ? error.message : "业务记录加载失败。");
      })
      .finally(() => setRecordsLoading(false));
    return () => controller.abort();
  }, [activeObject?.apiName, appliedQuery, currentCursor, reloadNonce]);

  const selectObject = (object: AiTableObject) => {
    setActiveApiName(object.apiName);
    setQuery("");
    setAppliedQuery("");
    setNotice(`已切换至${object.label}对象`);
  };

  const refresh = () => {
    if (catalogLoading || recordsLoading) return;
    const controller = loadCatalog();
    window.setTimeout(() => controller.abort(), 30_000);
    setReloadNonce((current) => current + 1);
    setNotice("正在刷新业务数据…");
  };

  const toggleColumn = (fieldApiName: string) => {
    if (!activeObject) return;
    setVisibleColumns((current) => {
      const next = current.includes(fieldApiName)
        ? current.length > 1 ? current.filter((item) => item !== fieldApiName) : current
        : [...current, fieldApiName];
      if (catalog?.preferenceScope) {
        window.localStorage.setItem(storageKey(catalog.preferenceScope, activeObject.apiName), JSON.stringify(next));
      }
      return next;
    });
  };

  const selectTheme = (code: ProductThemeCode) => {
    const nextCode = applyProductTheme(code);
    setThemeCode(nextCode);
    setThemeMenuOpen(false);
    setNotice(`已切换至${PRODUCT_THEMES.find((theme) => theme.code === nextCode)?.name || "鎏金账房"}`);
  };

  const goNext = () => {
    if (!recordPage?.nextCursor) return;
    setCursors((current) => [...current.slice(0, page), recordPage.nextCursor]);
    setPage((current) => current + 1);
    setSelectedRecord(null);
  };

  const goPrevious = () => {
    setPage((current) => Math.max(1, current - 1));
    setSelectedRecord(null);
  };

  return (
    <section className="ai-table-list" aria-label="AI表格业务对象列表">
      <header className="ai-table-list__header">
        <div className="ai-table-list__identity">
          <div className="ai-table-list__app-mark"><Layers3 size={20} strokeWidth={1.7} aria-hidden="true" /></div>
          <div>
            <div className="ai-table-list__eyebrow"><span className="ai-table-list__live-dot" />当前租户应用</div>
            <h1>业务数据</h1>
            <p>Semattice · {catalog?.companyName || "正在读取当前租户"}</p>
          </div>
        </div>
        <div className="ai-table-list__header-actions">
          <span className="ai-table-list__sync"><span />实时连接 <em>·</em> 更新于 {formatTime(recordPage?.retrievedAt || catalog?.retrievedAt || "")}</span>
          <div className="ai-table-list__theme-picker">
            <button type="button" className="ai-table-list__theme-trigger" onClick={() => setThemeMenuOpen((open) => !open)} aria-expanded={themeMenuOpen} aria-haspopup="menu">
              <span className="ai-table-list__theme-swatch" style={{ background: activeTheme.colors[2] }} />{activeTheme.name}<ChevronDown size={14} aria-hidden="true" />
            </button>
            {themeMenuOpen ? <div className="ai-table-list__theme-menu" role="menu" aria-label="选择产品主题">
              <div className="ai-table-list__menu-heading">主题预览</div>
              {PRODUCT_THEMES.map((theme) => <button key={theme.code} type="button" role="menuitemradio" aria-checked={theme.code === themeCode} className={theme.code === themeCode ? "is-selected" : ""} onClick={() => selectTheme(theme.code)}>
                <span className="ai-table-list__theme-swatch" style={{ background: theme.colors[2] }} /><span>{theme.name}</span>{theme.code === themeCode ? <Check size={14} aria-hidden="true" /> : null}
              </button>)}
            </div> : null}
          </div>
          <button type="button" className="ai-table-list__help" aria-label="AI表格使用说明" title="AI表格为当前权限范围内的只读业务数据"><CircleHelp size={17} aria-hidden="true" /></button>
        </div>
      </header>

      <div className="ai-table-list__layout">
        <aside className="ai-table-list__objects" aria-label="业务对象目录">
          <div className="ai-table-list__objects-heading"><span>业务对象</span><button type="button" aria-label="业务对象说明" title="仅展示当前已发布应用模型中的对象"><CircleHelp size={14} aria-hidden="true" /></button></div>
          <div className="ai-table-list__object-total"><Database size={15} aria-hidden="true" /><span>已发布应用对象</span><strong>{catalog?.objects.length ?? "—"}</strong></div>
          <nav>
            {catalogLoading ? <div className="ai-table-list__object-loading">正在读取对象目录…</div> : null}
            {catalog?.objects.map((object) => <button key={object.apiName} type="button" className={`ai-table-list__object-item${object.apiName === activeApiName ? " is-active" : ""}`} onClick={() => selectObject(object)} aria-current={object.apiName === activeApiName ? "page" : undefined}>
              <span className="ai-table-list__object-icon"><Database size={17} strokeWidth={1.8} aria-hidden="true" /></span>
              <span className="ai-table-list__object-copy"><strong>{object.label}</strong><small>{object.description || object.apiName}</small></span>
              <em>{object.fields.length} 字段</em>
            </button>)}
          </nav>
          <div className="ai-table-list__objects-foot"><Sparkles size={14} aria-hidden="true" /><span>对象与记录均由当前权限实时裁决</span></div>
        </aside>

        <main className="ai-table-list__content">
          {catalogError ? <div className="ai-table-list__catalog-error"><CircleAlert size={18} aria-hidden="true" /><div><strong>无法读取业务对象</strong><span>{catalogError}</span></div><button type="button" className="ai-table-list__tool-button" onClick={refresh}>重试</button></div> : null}
          <div className="ai-table-list__content-heading">
            <div>
              <div className="ai-table-list__breadcrumb"><span>业务数据</span><ChevronRight size={13} aria-hidden="true" /><strong>{activeObject?.label || "业务对象"}</strong></div>
              <div className="ai-table-list__title-line"><h2>{activeObject?.label || "正在加载"}</h2><span>{activeObject ? `已发布 ${activeObject.fields.length} 个字段` : ""}</span></div>
            </div>
          </div>

          <div className="ai-table-list__filterline">
            <div className="ai-table-list__search"><Search size={16} aria-hidden="true" /><input value={query} onChange={(event) => setQuery(event.target.value)} disabled={!activeObject || !searchSupported} placeholder={searchSupported ? `按${recordPage?.queryFieldLabel || activeObject?.searchFieldLabel || "已索引字段"}前缀搜索` : "该对象尚未配置可查询的文本索引"} aria-label={`搜索${activeObject?.label || "业务对象"}`} />{query ? <button type="button" onClick={() => setQuery("")} aria-label="清除搜索"><X size={14} aria-hidden="true" /></button> : null}</div>
            <div className="ai-table-list__toolbar">
              <div className="ai-table-list__columns-picker">
                <button type="button" className="ai-table-list__tool-button" onClick={() => setColumnMenuOpen((open) => !open)} disabled={!activeObject} aria-expanded={columnMenuOpen} aria-haspopup="menu"><Columns3 size={16} aria-hidden="true" />表头</button>
                {columnMenuOpen && activeObject ? <div className="ai-table-list__columns-menu" role="menu" aria-label="自定义表头">
                  <div className="ai-table-list__menu-heading">显示字段 <small>{activeFields.length}/{activeObject.fields.length}</small></div>
                  {activeObject.fields.map((field) => <label key={field.apiName}><input type="checkbox" checked={visibleColumns.includes(field.apiName)} onChange={() => toggleColumn(field.apiName)} /><span>{field.label}</span></label>)}
                  <div className="ai-table-list__menu-tip">表头配置仅保存在当前成员的本浏览器中</div>
                </div> : null}
              </div>
              <button type="button" className="ai-table-list__tool-button" onClick={refresh} disabled={catalogLoading || recordsLoading}><RefreshCw size={16} className={catalogLoading || recordsLoading ? "is-spinning" : ""} aria-hidden="true" />刷新</button>
            </div>
          </div>

          <div className="ai-table-list__meta-row"><span>{appliedQuery ? `检索：${appliedQuery}` : "当前对象全部记录"}</span><span className="ai-table-list__meta-divider" /><span>{recordsLoading ? "正在加载…" : `本页 ${recordPage?.records.length ?? 0} 条`}</span><span className="ai-table-list__meta-spacer" /><span className="ai-table-list__permission"><Check size={13} aria-hidden="true" />当前权限只读</span></div>

          <div className="ai-table-list__table-wrap">
            <table>
              <thead><tr>{activeFields.map((field) => <th key={field.apiName}>{field.label}{field.indexed ? <ChevronDown size={12} aria-hidden="true" /> : null}</th>)}<th className="ai-table-list__action-cell"><span>操作</span></th></tr></thead>
              <tbody>
                {recordPage?.records.map((record) => <tr key={record.id} onClick={() => setSelectedRecord(record)} className={selectedRecord?.id === record.id ? "is-open" : ""}>
                  {activeFields.map((field, index) => <td key={field.apiName} className={index === 0 ? "is-primary" : ""}>{formatValue(record.data[field.apiName])}</td>)}
                  <td className="ai-table-list__action-cell"><button type="button" onClick={(event) => { event.stopPropagation(); setSelectedRecord(record); }} aria-label={`查看 ${record.id}`} title="查看详情"><ChevronRight size={16} aria-hidden="true" /></button></td>
                </tr>)}
                {!recordsLoading && !recordsError && activeObject && !recordPage?.records.length ? <tr><td className="ai-table-list__empty" colSpan={Math.max(2, activeFields.length + 1)}><FileWarning size={24} aria-hidden="true" /><strong>{appliedQuery ? "没有匹配的业务记录" : "当前权限范围内暂无业务记录"}</strong><span>{appliedQuery ? "可清除关键词后重新查询" : "记录创建或获授权后会在这里显示"}</span>{appliedQuery ? <button type="button" onClick={() => setQuery("")}>清除查询</button> : null}</td></tr> : null}
                {recordsLoading ? <tr><td className="ai-table-list__empty" colSpan={Math.max(2, activeFields.length + 1)}><RefreshCw className="is-spinning" size={22} aria-hidden="true" /><strong>正在读取业务记录</strong><span>数据仍在当前用户的权限范围内加载</span></td></tr> : null}
                {recordsError ? <tr><td className="ai-table-list__empty" colSpan={Math.max(2, activeFields.length + 1)}><CircleAlert size={24} aria-hidden="true" /><strong>无法读取业务记录</strong><span>{recordsError}</span><button type="button" onClick={refresh}>重新加载</button></td></tr> : null}
              </tbody>
            </table>
          </div>

          <footer className="ai-table-list__footer"><span>第 {page} 页{recordPage?.nextCursor ? " · 可继续加载" : " · 已到当前结果末页"}</span><div className="ai-table-list__pagination"><button type="button" onClick={goPrevious} disabled={page === 1} aria-label="上一页"><ChevronLeft size={15} aria-hidden="true" /></button><button type="button" className="is-active" aria-current="page">{page}</button><button type="button" onClick={goNext} disabled={!recordPage?.nextCursor || recordsLoading} aria-label="下一页"><ChevronRight size={15} aria-hidden="true" /></button></div></footer>
        </main>
      </div>

      {selectedRecord && activeObject ? <aside className="ai-table-list__detail" aria-label={`${selectedRecord.id}详情`}>
        <div className="ai-table-list__detail-head"><div><span>记录详情</span><h3>{formatValue(selectedRecord.data[activeFields[0]?.apiName || ""])}</h3><small>{selectedRecord.id}</small></div><button type="button" onClick={() => setSelectedRecord(null)} aria-label="关闭详情"><X size={18} aria-hidden="true" /></button></div>
        <div className="ai-table-list__detail-body"><div className="ai-table-list__detail-status"><span className="ai-table-list__app-mark"><Database size={18} aria-hidden="true" /></span><div><strong>{activeObject.label}</strong><small>当前权限可读取字段</small></div></div><dl>{activeObject.fields.map((field) => <div key={field.apiName}><dt>{field.label}</dt><dd>{formatValue(selectedRecord.data[field.apiName])}</dd></div>)}</dl><div className="ai-table-list__detail-note"><CircleHelp size={15} aria-hidden="true" /><span>字段值仅来自当前成员被授权读取的真实业务记录。</span></div></div>
        <footer className="ai-table-list__detail-foot"><button type="button" onClick={() => setSelectedRecord(null)}>关闭</button></footer>
      </aside> : null}
      {notice ? <div className="ai-table-list__notice" role="status" onAnimationEnd={() => setNotice("")}>{notice}</div> : null}
    </section>
  );
}

export default AiTableBusinessObjectList;
