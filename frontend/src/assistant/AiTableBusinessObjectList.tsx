import { useMemo, useState, type ReactNode } from "react";
import {
  Activity,
  Building2,
  Check,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  CircleHelp,
  Columns3,
  Database,
  FolderKanban,
  Layers3,
  MoreHorizontal,
  RefreshCw,
  Search,
  SlidersHorizontal,
  Sparkles,
  Table2,
  X,
  type LucideIcon,
} from "lucide-react";
import { applyProductTheme, PRODUCT_THEMES, type ProductThemeCode } from "../theme/theme";

type ObjectKey = "customers" | "opportunities" | "projects";
type FieldKind = "text" | "status" | "date" | "amount";
type CellValue = string | number;

type FieldDefinition = {
  key: string;
  label: string;
  kind?: FieldKind;
  defaultVisible?: boolean;
  width?: number;
};

type BusinessRecord = {
  id: string;
  [key: string]: CellValue;
};

type BusinessObjectDefinition = {
  key: ObjectKey;
  label: string;
  description: string;
  count: number;
  icon: LucideIcon;
  accent: string;
  fields: FieldDefinition[];
  records: BusinessRecord[];
  filters: string[];
};

const OBJECTS: BusinessObjectDefinition[] = [
  {
    key: "customers",
    label: "客户",
    description: "企业与联系人档案",
    count: 1284,
    icon: Building2,
    accent: "var(--theme-data-2)",
    filters: ["全部客户", "重点客户", "待跟进"],
    fields: [
      { key: "name", label: "客户名称", defaultVisible: true, width: 220 },
      { key: "industry", label: "行业", defaultVisible: true, width: 126 },
      { key: "owner", label: "负责人", defaultVisible: true, width: 112 },
      { key: "lifecycle", label: "客户阶段", kind: "status", defaultVisible: true, width: 116 },
      { key: "health", label: "经营状态", kind: "status", defaultVisible: true, width: 112 },
      { key: "updatedAt", label: "最近更新", kind: "date", defaultVisible: true, width: 144 },
      { key: "contact", label: "主要联系人", defaultVisible: false, width: 150 },
    ],
    records: [
      { id: "CUS-100284", name: "杭州云序科技有限公司", industry: "企业服务", owner: "林晓", lifecycle: "重点客户", health: "健康", updatedAt: "2026-08-04 14:20", contact: "周明远" },
      { id: "CUS-100281", name: "深圳启明智造股份有限公司", industry: "智能制造", owner: "周哲", lifecycle: "跟进中", health: "需关注", updatedAt: "2026-08-04 11:08", contact: "陈思琪" },
      { id: "CUS-100276", name: "上海远帆医疗科技", industry: "医疗健康", owner: "林晓", lifecycle: "重点客户", health: "健康", updatedAt: "2026-08-03 18:42", contact: "赵嘉" },
      { id: "CUS-100269", name: "成都星桥数据服务有限公司", industry: "数据服务", owner: "唐宁", lifecycle: "新建", health: "待跟进", updatedAt: "2026-08-03 15:36", contact: "徐安" },
      { id: "CUS-100264", name: "苏州墨川新材料有限公司", industry: "新材料", owner: "陈放", lifecycle: "跟进中", health: "健康", updatedAt: "2026-08-02 16:12", contact: "顾婉" },
      { id: "CUS-100258", name: "北京北辰能源科技", industry: "新能源", owner: "周哲", lifecycle: "重点客户", health: "风险", updatedAt: "2026-08-02 09:47", contact: "许嘉诚" },
      { id: "CUS-100251", name: "厦门海岸线文旅集团", industry: "文旅服务", owner: "唐宁", lifecycle: "跟进中", health: "健康", updatedAt: "2026-08-01 17:24", contact: "沈微" },
      { id: "CUS-100246", name: "南京青禾教育科技", industry: "教育培训", owner: "林晓", lifecycle: "新建", health: "待跟进", updatedAt: "2026-08-01 10:03", contact: "陆川" },
      { id: "CUS-100238", name: "广州知行供应链", industry: "供应链", owner: "陈放", lifecycle: "跟进中", health: "健康", updatedAt: "2026-07-31 16:41", contact: "罗宁" },
      { id: "CUS-100229", name: "武汉禾木软件有限公司", industry: "软件服务", owner: "周哲", lifecycle: "重点客户", health: "健康", updatedAt: "2026-07-31 09:18", contact: "谢扬" },
    ],
  },
  {
    key: "opportunities",
    label: "商机",
    description: "销售机会与阶段推进",
    count: 326,
    icon: Activity,
    accent: "var(--theme-data-1)",
    filters: ["全部商机", "本季度", "高价值"],
    fields: [
      { key: "name", label: "商机名称", defaultVisible: true, width: 236 },
      { key: "customer", label: "所属客户", defaultVisible: true, width: 190 },
      { key: "stage", label: "销售阶段", kind: "status", defaultVisible: true, width: 120 },
      { key: "amount", label: "预计金额", kind: "amount", defaultVisible: true, width: 132 },
      { key: "owner", label: "负责人", defaultVisible: true, width: 112 },
      { key: "updatedAt", label: "最近更新", kind: "date", defaultVisible: true, width: 144 },
      { key: "closeDate", label: "预计成交", kind: "date", defaultVisible: false, width: 122 },
    ],
    records: [
      { id: "OPP-20381", name: "统一数据底座建设项目", customer: "杭州云序科技", stage: "方案评估", amount: 680000, owner: "林晓", updatedAt: "2026-08-04 13:54", closeDate: "2026-09-18" },
      { id: "OPP-20376", name: "售后智能化升级一期", customer: "深圳启明智造", stage: "商务谈判", amount: 420000, owner: "周哲", updatedAt: "2026-08-04 10:12", closeDate: "2026-08-29" },
      { id: "OPP-20364", name: "客户运营工作台项目", customer: "上海远帆医疗", stage: "需求确认", amount: 315000, owner: "林晓", updatedAt: "2026-08-03 17:30", closeDate: "2026-09-06" },
      { id: "OPP-20359", name: "知识中台与智能助手", customer: "成都星桥数据", stage: "初步接洽", amount: 198000, owner: "唐宁", updatedAt: "2026-08-02 16:05", closeDate: "2026-10-11" },
      { id: "OPP-20342", name: "集团数据治理服务", customer: "北京北辰能源", stage: "商务谈判", amount: 860000, owner: "周哲", updatedAt: "2026-08-01 14:26", closeDate: "2026-08-22" },
    ],
  },
  {
    key: "projects",
    label: "项目",
    description: "交付项目与业务进度",
    count: 94,
    icon: FolderKanban,
    accent: "var(--theme-data-3)",
    filters: ["全部项目", "进行中", "已完成"],
    fields: [
      { key: "name", label: "项目名称", defaultVisible: true, width: 240 },
      { key: "customer", label: "客户", defaultVisible: true, width: 186 },
      { key: "phase", label: "项目阶段", kind: "status", defaultVisible: true, width: 120 },
      { key: "progress", label: "完成度", defaultVisible: true, width: 108 },
      { key: "pm", label: "项目经理", defaultVisible: true, width: 112 },
      { key: "updatedAt", label: "最近更新", kind: "date", defaultVisible: true, width: 144 },
      { key: "deadline", label: "计划结束", kind: "date", defaultVisible: false, width: 122 },
    ],
    records: [
      { id: "PRJ-2026048", name: "云序数据语义建模", customer: "杭州云序科技", phase: "实施中", progress: "68%", pm: "李知行", updatedAt: "2026-08-04 14:08", deadline: "2026-08-28" },
      { id: "PRJ-2026041", name: "启明售后助手一期", customer: "深圳启明智造", phase: "需求确认", progress: "34%", pm: "苏婉", updatedAt: "2026-08-03 16:40", deadline: "2026-09-12" },
      { id: "PRJ-2026036", name: "远帆客户运营中台", customer: "上海远帆医疗", phase: "实施中", progress: "52%", pm: "李知行", updatedAt: "2026-08-02 11:25", deadline: "2026-09-20" },
      { id: "PRJ-2026028", name: "北辰能源数据治理", customer: "北京北辰能源", phase: "已完成", progress: "100%", pm: "苏婉", updatedAt: "2026-07-30 18:22", deadline: "2026-07-30" },
    ],
  },
];

const DEFAULT_VISIBLE_COLUMNS: Record<ObjectKey, string[]> = Object.fromEntries(
  OBJECTS.map((object) => [object.key, object.fields.filter((field) => field.defaultVisible !== false).map((field) => field.key)]),
) as Record<ObjectKey, string[]>;

function formatCell(value: CellValue, kind?: FieldKind) {
  if (kind === "amount" && typeof value === "number") {
    return `¥${value.toLocaleString("zh-CN")}`;
  }
  return String(value);
}

function statusTone(value: CellValue) {
  const normalized = String(value);
  if (["健康", "已完成", "重点客户"].includes(normalized)) return "is-success";
  if (["风险", "需关注"].includes(normalized)) return "is-danger";
  if (["待跟进", "初步接洽", "需求确认", "实施中"].includes(normalized)) return "is-warning";
  return "is-neutral";
}

function objectIcon(object: BusinessObjectDefinition) {
  const Icon = object.icon;
  return <Icon size={17} strokeWidth={1.8} aria-hidden="true" />;
}

export function AiTableBusinessObjectList() {
  const [activeObjectKey, setActiveObjectKey] = useState<ObjectKey>("customers");
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState("全部客户");
  const [page, setPage] = useState(1);
  const [selectedRecord, setSelectedRecord] = useState<BusinessRecord | null>(null);
  const [visibleColumns, setVisibleColumns] = useState(DEFAULT_VISIBLE_COLUMNS);
  const [columnMenuOpen, setColumnMenuOpen] = useState(false);
  const [themeMenuOpen, setThemeMenuOpen] = useState(false);
  const [themeCode, setThemeCode] = useState<ProductThemeCode>(() => (document.documentElement.dataset.theme as ProductThemeCode) || "gilded");
  const [refreshing, setRefreshing] = useState(false);
  const [lastSynced, setLastSynced] = useState("刚刚");
  const [notice, setNotice] = useState("");

  const activeObject = OBJECTS.find((object) => object.key === activeObjectKey) ?? OBJECTS[0];
  const activeFields = activeObject.fields.filter((field) => visibleColumns[activeObject.key].includes(field.key));

  const filteredRecords = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    const matchesFilter = (record: BusinessRecord) => {
      if (filter.startsWith("全部")) return true;
      if (activeObject.key === "customers") {
        return filter === "重点客户" ? record.lifecycle === "重点客户" : record.lifecycle === "新建" || record.health === "待跟进";
      }
      if (activeObject.key === "opportunities") {
        return filter === "高价值" ? Number(record.amount) >= 300000 : true;
      }
      return filter === "已完成" ? record.phase === "已完成" : record.phase !== "已完成";
    };
    return activeObject.records.filter((record) => {
      if (!matchesFilter(record)) return false;
      return !normalizedQuery || Object.values(record).some((value) => String(value).toLowerCase().includes(normalizedQuery));
    });
  }, [activeObject, filter, query]);

  const pageSize = 8;
  const pageCount = Math.max(1, Math.ceil(filteredRecords.length / pageSize));
  const visibleRecords = filteredRecords.slice((page - 1) * pageSize, page * pageSize);
  const activeTheme = PRODUCT_THEMES.find((theme) => theme.code === themeCode) ?? PRODUCT_THEMES[0];

  const selectObject = (key: ObjectKey) => {
    const nextObject = OBJECTS.find((object) => object.key === key) ?? OBJECTS[0];
    setActiveObjectKey(key);
    setFilter(nextObject.filters[0]);
    setQuery("");
    setPage(1);
    setSelectedRecord(null);
    setColumnMenuOpen(false);
    setNotice(`已切换至${nextObject.label}对象`);
  };

  const refreshList = () => {
    if (refreshing) return;
    setRefreshing(true);
    setNotice("正在刷新业务数据…");
    window.setTimeout(() => {
      setRefreshing(false);
      setLastSynced("刚刚");
      setNotice("业务数据已更新");
    }, 650);
  };

  const selectTheme = (code: ProductThemeCode) => {
    const nextCode = applyProductTheme(code);
    setThemeCode(nextCode);
    setThemeMenuOpen(false);
    const nextTheme = PRODUCT_THEMES.find((theme) => theme.code === nextCode);
    setNotice(`已切换至${nextTheme?.name ?? "鎏金账房"}`);
  };

  const toggleColumn = (key: string) => {
    setVisibleColumns((current) => {
      const currentKeys = current[activeObject.key];
      if (currentKeys.includes(key) && currentKeys.length === 1) return current;
      return {
        ...current,
        [activeObject.key]: currentKeys.includes(key) ? currentKeys.filter((item) => item !== key) : [...currentKeys, key],
      };
    });
  };

  const renderStatus = (value: CellValue) => <span className={`ai-table-list__status ${statusTone(value)}`}><i />{value}</span>;

  return (
    <section className="ai-table-list" aria-label="Semattice 业务对象列表">
      <header className="ai-table-list__header">
        <div className="ai-table-list__identity">
          <div className="ai-table-list__app-mark"><Layers3 size={20} strokeWidth={1.7} aria-hidden="true" /></div>
          <div>
            <div className="ai-table-list__eyebrow"><span className="ai-table-list__live-dot" />当前租户应用</div>
            <h1>业务数据</h1>
            <p>Semattice · 杭州云序科技有限公司</p>
          </div>
        </div>
        <div className="ai-table-list__header-actions">
          <span className="ai-table-list__sync"><span />已连接 <em>·</em> 更新于 {lastSynced}</span>
          <div className="ai-table-list__theme-picker">
            <button type="button" className="ai-table-list__theme-trigger" onClick={() => setThemeMenuOpen((open) => !open)} aria-expanded={themeMenuOpen} aria-haspopup="menu">
              <span className="ai-table-list__theme-swatch" style={{ background: activeTheme.colors[2] }} />
              {activeTheme.name}
              <ChevronDown size={14} aria-hidden="true" />
            </button>
            {themeMenuOpen ? (
              <div className="ai-table-list__theme-menu" role="menu" aria-label="选择产品主题">
                <div className="ai-table-list__menu-heading">主题预览</div>
                {PRODUCT_THEMES.map((theme) => (
                  <button key={theme.code} type="button" role="menuitemradio" aria-checked={theme.code === themeCode} className={theme.code === themeCode ? "is-selected" : ""} onClick={() => selectTheme(theme.code)}>
                    <span className="ai-table-list__theme-swatch" style={{ background: theme.colors[2] }} />
                    <span>{theme.name}</span>
                    {theme.code === themeCode ? <Check size={14} aria-hidden="true" /> : null}
                  </button>
                ))}
              </div>
            ) : null}
          </div>
          <button type="button" className="ai-table-list__help" aria-label="查看业务数据帮助" title="查看帮助"><CircleHelp size={17} aria-hidden="true" /></button>
        </div>
      </header>

      <div className="ai-table-list__layout">
        <aside className="ai-table-list__objects" aria-label="业务对象目录">
          <div className="ai-table-list__objects-heading"><span>业务对象</span><button type="button" aria-label="业务对象说明" title="业务对象说明"><CircleHelp size={14} aria-hidden="true" /></button></div>
          <div className="ai-table-list__object-total"><Database size={15} aria-hidden="true" /><span>已开通应用对象</span><strong>{OBJECTS.length}</strong></div>
          <nav>
            {OBJECTS.map((object) => (
              <button key={object.key} type="button" className={`ai-table-list__object-item${activeObject.key === object.key ? " is-active" : ""}`} onClick={() => selectObject(object.key)} aria-current={activeObject.key === object.key ? "page" : undefined}>
                <span className="ai-table-list__object-icon" style={{ color: object.accent }}>{objectIcon(object)}</span>
                <span className="ai-table-list__object-copy"><strong>{object.label}</strong><small>{object.description}</small></span>
                <em>{object.count.toLocaleString("zh-CN")}</em>
              </button>
            ))}
          </nav>
          <div className="ai-table-list__objects-foot"><Sparkles size={14} aria-hidden="true" /><span>由 Semattice 实时提供对象与记录</span></div>
        </aside>

        <main className="ai-table-list__content">
          <div className="ai-table-list__content-heading">
            <div>
              <div className="ai-table-list__breadcrumb"><span>业务数据</span><ChevronRight size={13} aria-hidden="true" /><strong>{activeObject.label}</strong></div>
              <div className="ai-table-list__title-line"><h2>{activeObject.label}</h2><span>{activeObject.count.toLocaleString("zh-CN")} 条记录</span></div>
            </div>
            <button type="button" className="ai-table-list__primary-action"><span>＋</span>新增{activeObject.label}</button>
          </div>

          <div className="ai-table-list__filterline">
            <div className="ai-table-list__search"><Search size={16} aria-hidden="true" /><input value={query} onChange={(event) => { setQuery(event.target.value); setPage(1); }} placeholder={`搜索${activeObject.label}名称、负责人或编号`} aria-label={`搜索${activeObject.label}`} />{query ? <button type="button" onClick={() => { setQuery(""); setPage(1); }} aria-label="清除搜索"><X size={14} aria-hidden="true" /></button> : null}</div>
            <div className="ai-table-list__filter-tabs" role="tablist" aria-label={`${activeObject.label}筛选`}>
              {activeObject.filters.map((item) => <button key={item} type="button" role="tab" aria-selected={filter === item} className={filter === item ? "is-active" : ""} onClick={() => setFilter(item)}>{item}</button>)}
            </div>
            <div className="ai-table-list__toolbar">
              <div className="ai-table-list__columns-picker">
                <button type="button" className="ai-table-list__tool-button" onClick={() => setColumnMenuOpen((open) => !open)} aria-expanded={columnMenuOpen} aria-haspopup="menu"><Columns3 size={16} aria-hidden="true" />表头</button>
                {columnMenuOpen ? (
                  <div className="ai-table-list__columns-menu" role="menu" aria-label="自定义表头">
                    <div className="ai-table-list__menu-heading">显示字段 <small>{activeFields.length}/{activeObject.fields.length}</small></div>
                    {activeObject.fields.map((field) => <label key={field.key}><input type="checkbox" checked={visibleColumns[activeObject.key].includes(field.key)} onChange={() => toggleColumn(field.key)} /><span>{field.label}</span></label>)}
                    <div className="ai-table-list__menu-tip">拖动排序将在正式版本开放</div>
                  </div>
                ) : null}
              </div>
              <button type="button" className="ai-table-list__tool-button" onClick={refreshList} disabled={refreshing}><RefreshCw size={16} className={refreshing ? "is-spinning" : ""} aria-hidden="true" />刷新</button>
              <button type="button" className="ai-table-list__tool-icon" aria-label="更多列表操作" title="更多操作"><MoreHorizontal size={17} aria-hidden="true" /></button>
            </div>
          </div>

          <div className="ai-table-list__meta-row"><span><SlidersHorizontal size={14} aria-hidden="true" />当前视图：{filter}</span><span className="ai-table-list__meta-divider" /><span>已显示 {filteredRecords.length} 条</span><span className="ai-table-list__meta-spacer" /><span className="ai-table-list__permission"><Check size={13} aria-hidden="true" />只读权限</span></div>

          <div className="ai-table-list__table-wrap">
            <table>
              <thead>
                <tr><th className="ai-table-list__check-cell"><input type="checkbox" aria-label="选择全部记录" /></th>{activeFields.map((field) => <th key={field.key} style={{ width: field.width }}>{field.label}<ChevronDown size={12} aria-hidden="true" /></th>)}<th className="ai-table-list__action-cell"><span>操作</span></th></tr>
              </thead>
              <tbody>
                {visibleRecords.length ? visibleRecords.map((record) => (
                  <tr key={record.id} onClick={() => setSelectedRecord(record)} className={selectedRecord?.id === record.id ? "is-open" : ""}>
                    <td className="ai-table-list__check-cell"><input type="checkbox" aria-label={`选择 ${record.name}`} onClick={(event) => event.stopPropagation()} /></td>
                    {activeFields.map((field, index) => <td key={field.key} className={index === 0 ? "is-primary" : ""}>{field.kind === "status" ? renderStatus(record[field.key]) : <span title={String(record[field.key])}>{formatCell(record[field.key], field.kind)}</span>}</td>)}
                    <td className="ai-table-list__action-cell"><button type="button" aria-label={`打开 ${record.name}`} onClick={(event) => { event.stopPropagation(); setSelectedRecord(record); }}><MoreHorizontal size={16} aria-hidden="true" /></button></td>
                  </tr>
                )) : (
                  <tr><td className="ai-table-list__empty" colSpan={activeFields.length + 2}><Table2 size={26} aria-hidden="true" /><strong>没有找到匹配的记录</strong><span>试试更换关键词，或清除当前筛选条件。</span>{query ? <button type="button" onClick={() => setQuery("")}>清除查询</button> : null}</td></tr>
                )}
              </tbody>
            </table>
          </div>

          <footer className="ai-table-list__footer"><span>第 {filteredRecords.length ? page : 0} / {pageCount} 页</span><div className="ai-table-list__pagination"><button type="button" aria-label="上一页" disabled={page <= 1} onClick={() => setPage((current) => Math.max(1, current - 1))}><ChevronLeft size={15} aria-hidden="true" /></button>{Array.from({ length: pageCount }, (_, index) => index + 1).map((pageNumber) => <button key={pageNumber} type="button" className={pageNumber === page ? "is-active" : ""} onClick={() => setPage(pageNumber)}>{pageNumber}</button>)}<button type="button" aria-label="下一页" disabled={page >= pageCount} onClick={() => setPage((current) => Math.min(pageCount, current + 1))}><ChevronRight size={15} aria-hidden="true" /></button></div><label>每页 <select defaultValue="8" aria-label="每页条数"><option value="8">8 条</option><option value="20">20 条</option><option value="50">50 条</option></select></label></footer>
        </main>
      </div>

      {selectedRecord ? (
        <aside className="ai-table-list__detail" aria-label={`${selectedRecord.name}详情`}>
          <div className="ai-table-list__detail-head"><div><span>记录详情</span><h3>{selectedRecord.name}</h3><small>{selectedRecord.id}</small></div><button type="button" onClick={() => setSelectedRecord(null)} aria-label="关闭详情"><X size={18} aria-hidden="true" /></button></div>
          <div className="ai-table-list__detail-body"><div className="ai-table-list__detail-status"><span className="ai-table-list__app-mark"><Database size={18} aria-hidden="true" /></span><div><strong>{activeObject.label}</strong><small>Semattice 业务对象</small></div><span className={`ai-table-list__status ${statusTone(selectedRecord.health ?? selectedRecord.phase ?? selectedRecord.stage ?? "已完成")}`}><i />{selectedRecord.health ?? selectedRecord.phase ?? selectedRecord.stage ?? "已完成"}</span></div><dl>{activeObject.fields.filter((field) => field.key !== "name").map((field) => <div key={field.key}><dt>{field.label}</dt><dd>{field.kind === "status" ? renderStatus(selectedRecord[field.key]) : formatCell(selectedRecord[field.key], field.kind)}</dd></div>)}</dl><div className="ai-table-list__detail-note"><Activity size={15} aria-hidden="true" /><span>详情数据来自当前租户已开通的 Semattice 应用。</span></div></div>
          <footer className="ai-table-list__detail-foot"><button type="button" onClick={() => setSelectedRecord(null)}>关闭</button><button type="button" className="ai-table-list__primary-action">打开完整记录 <ChevronRight size={14} aria-hidden="true" /></button></footer>
        </aside>
      ) : null}
      {notice ? <div className="ai-table-list__notice" role="status" onAnimationEnd={() => setNotice("")}>{notice}</div> : null}
    </section>
  );
}

export default AiTableBusinessObjectList;
