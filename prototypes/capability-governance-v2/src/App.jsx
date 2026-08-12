import { useEffect, useMemo, useState } from "react";
import {
  ArrowLeft,
  CaretRight,
  CheckCircle,
  DotsThree,
  Eye,
  MagnifyingGlass,
  WarningCircle,
  X,
} from "@phosphor-icons/react";

const skills = [
  { id: "meeting", name: "AI 听记", desc: "面向会议实时转写后的结构化纪要与行动项生成。", version: "v3", status: "已启用", risk: "中风险", agents: 6, workflows: 3, updated: "今天 17:10" },
  { id: "health", name: "CRM 商机健康扫描", desc: "评估推进速度、停留时长与风险信号。", version: "v2", status: "已启用", risk: "低风险", agents: 4, workflows: 2, updated: "今天 15:42" },
  { id: "lead", name: "CRM 线索分诊", desc: "识别线索价值并输出后续跟进建议。", version: "v1", status: "已启用", risk: "中风险", agents: 2, workflows: 1, updated: "昨天 20:18" },
  { id: "analysis", name: "CRM 经营分析", desc: "基于已授权 CRM 数据输出经营诊断与趋势。", version: "v3", status: "已启用", risk: "高风险", agents: 5, workflows: 4, updated: "昨天 18:26" },
  { id: "renewal", name: "CRM 续约预警", desc: "识别续约窗口内的流失风险并给出保留动作。", version: "v1", status: "已启用", risk: "中风险", agents: 3, workflows: 2, updated: "8 月 9 日" },
  { id: "delivery", name: "Semattice 研发交付管理", desc: "创建并评审研发项目、需求和缺陷。", version: "v4", status: "待检查", risk: "高风险", agents: 3, workflows: 2, updated: "8 月 9 日" },
  { id: "developer", name: "CloudCC 二次开发专家", desc: "面向 CRM 元数据和自动化实施提供专业协助。", version: "v2", status: "已启用", risk: "中风险", agents: 2, workflows: 1, updated: "8 月 8 日" },
];

const providers = [
  { id: "onekey", name: "OneKeyToken", endpoint: "https://my.onekeytoken.com/v1", status: "已启用", models: 1, routes: 5, checked: "今天 18:30" },
  { id: "aliyun", name: "阿里云百炼", endpoint: "https://dashscope.aliyuncs.com/compatible-mode/v1", status: "未启用", models: 0, routes: 0, checked: "未检测" },
  { id: "deepseek", name: "深度求索", endpoint: "https://api.deepseek.com/v1", status: "未启用", models: 0, routes: 0, checked: "未检测" },
  { id: "openai", name: "OpenAI", endpoint: "https://api.openai.com/v1", status: "未启用", models: 0, routes: 0, checked: "未检测" },
  { id: "ollama", name: "本地 Ollama", endpoint: "http://host.docker.internal:11434/v1", status: "未启用", models: 0, routes: 0, checked: "未检测" },
];

const policyPackages = [
  { id: "core-default", name: "平台核心安全策略", desc: "统一高风险确认、证据边界与敏感信息保护规则。", scope: "安全与可信运行", version: "v1", status: "当前生效", targets: "17 项标准技能", updated: "8 月 8 日", available: true },
  { id: "data-egress", name: "数据出境策略", desc: "约束敏感数据向外部模型、工具与连接器传递。", scope: "数据使用与出境", version: "—", status: "规划中", targets: "平台运行时", updated: "—", available: false },
  { id: "model-access", name: "模型调用策略", desc: "治理模型准入、场景选择、回退与调用边界。", scope: "模型调用与回退", version: "—", status: "规划中", targets: "模型运行场景", updated: "—", available: false },
  { id: "tool-execution", name: "工具执行策略", desc: "治理工具授权、高风险动作确认与执行审计。", scope: "工具授权与执行", version: "—", status: "规划中", targets: "平台工具目录", updated: "—", available: false },
];

function useHashRoute() {
  const read = () => window.location.hash.replace(/^#\/?/, "") || "skills";
  const [route, setRoute] = useState(read);
  useEffect(() => {
    const handler = () => setRoute(read());
    window.addEventListener("hashchange", handler);
    return () => window.removeEventListener("hashchange", handler);
  }, []);
  const navigate = (next) => {
    window.location.hash = next;
    setRoute(next);
  };
  return [route, navigate];
}

function Status({ children, tone = "success" }) {
  return <span className={`status status-${tone}`}>{children}</span>;
}

function Shell({ route, navigate, onFrozen, children }) {
  const active = route.startsWith("skill") || route.startsWith("policy") ? "skills" : "";
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand-block">
          <span className="eyebrow">运营平台</span>
          <h1>运营控制台</h1>
          <div className="account-box"><span>平台账号</span><strong>CloudCC Platform Admin</strong></div>
        </div>
        <nav className="side-nav" aria-label="运营控制台导航">
          <button type="button" className="root-link">运营总览</button>
          <div className="nav-group">
            <div className="nav-group-title"><span>能力治理</span><span>4</span></div>
            <button type="button" className={active === "skills" ? "nav-link active" : "nav-link"} onClick={() => navigate("skills")}>技能治理</button>
            <button type="button" className="nav-link frozen-link" onClick={() => onFrozen("模型配置")}>模型配置</button>
            <button type="button" className="nav-link frozen-link" onClick={() => onFrozen("平台集成")}>平台集成</button>
            <button type="button" className="nav-link frozen-link" onClick={() => onFrozen("工具目录")}>工具目录</button>
          </div>
          <div className="nav-group muted-group">
            <div className="nav-group-title"><span>运营管理</span><span>5</span></div>
            {["套餐目录", "加购包与 Credits", "租户目录", "注册用户", "演示线索"].map((item) => <button type="button" className="nav-link" key={item}>{item}</button>)}
          </div>
          <div className="nav-group muted-group">
            <div className="nav-group-title"><span>风险与质量</span><span>4</span></div>
            {["质量总览", "标准评测资产", "运行洞察", "平台审计"].map((item) => <button type="button" className="nav-link" key={item}>{item}</button>)}
          </div>
        </nav>
        <div className="sidebar-footer"><span>平台偏好</span><span>2.8.62-dev.1</span></div>
      </aside>
      <main className="main-area">{children}</main>
    </div>
  );
}

function PageHeader({ eyebrow, title, description, actions, back, navigate }) {
  return (
    <header className="page-header">
      <div>
        {back ? <button type="button" className="back-link" onClick={() => navigate(back.route)}><ArrowLeft size={16} />{back.label}</button> : <p className="breadcrumb">运营控制台 / 能力治理 / {eyebrow}</p>}
        <h2>{title}</h2>
        <p className="page-description">{description}</p>
      </div>
      {actions && <div className="page-actions">{actions}</div>}
    </header>
  );
}

function SkillGovernanceHome({ navigate, onSelect, query, setQuery, filter, setFilter, toast, initialView = "skills" }) {
  const [view, setView] = useState(initialView);
  useEffect(() => setView(initialView), [initialView]);
  const rows = useMemo(() => skills.filter((skill) => (filter === "全部" || skill.status === filter) && `${skill.name}${skill.desc}`.includes(query.trim())), [query, filter]);
  return (
    <div className="page">
      <PageHeader eyebrow="技能治理" title="技能治理" description="统一维护平台标准技能与核心策略包；先选择治理对象，再进入详情或独立编辑。" navigate={navigate} />
      <nav className="governance-entry-nav" aria-label="技能治理对象">
        <button type="button" className={view === "skills" ? "active" : ""} onClick={() => setView("skills")}><span>技能列表</span><small>17 项标准技能，目录、版本与依赖治理</small></button>
        <button type="button" className={view === "policy" ? "active" : ""} onClick={() => setView("policy")}><span>核心策略包</span><small>1 个生效，3 个规划方向</small></button>
      </nav>
      {view === "skills" ? <section className="list-surface governance-list" aria-label="平台标准技能列表">
        <div className="list-toolbar">
          <div className="search-field"><MagnifyingGlass size={17} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="搜索技能名称或能力说明" /></div>
          <div className="filter-tabs" role="tablist" aria-label="技能状态筛选">
            {["全部", "已启用", "待检查"].map((item) => <button type="button" role="tab" aria-selected={filter === item} className={filter === item ? "filter-tab active" : "filter-tab"} onClick={() => setFilter(item)} key={item}>{item}</button>)}
          </div>
          <span className="result-count">共 {rows.length} 项</span>
        </div>
        <table className="data-table">
          <colgroup><col style={{ width: "31%" }} /><col style={{ width: "12%" }} /><col style={{ width: "13%" }} /><col style={{ width: "12%" }} /><col style={{ width: "13%" }} /><col style={{ width: "14%" }} /><col style={{ width: "5%" }} /></colgroup>
          <thead><tr><th>标准技能</th><th>当前版本</th><th>状态</th><th>风险</th><th>绑定 Agent</th><th>最近更新</th><th><span className="sr-only">操作</span></th></tr></thead>
          <tbody>{rows.map((skill) => <tr key={skill.id} tabIndex="0" onClick={() => onSelect(skill)} onKeyDown={(event) => { if (event.key === "Enter") onSelect(skill); }}>
            <td><div className="cell-primary">{skill.name}</div><div className="cell-secondary">{skill.desc}</div></td>
            <td>{skill.version}</td><td><Status tone={skill.status === "待检查" ? "warning" : "success"}>{skill.status}</Status></td>
            <td>{skill.risk}</td><td>{skill.agents}</td><td>{skill.updated}</td><td><button type="button" className="icon-button" aria-label={`查看 ${skill.name}`} onClick={(event) => { event.stopPropagation(); onSelect(skill); }}><DotsThree size={19} /></button></td>
          </tr>)}</tbody>
        </table>
        {rows.length === 0 && <div className="empty-row">没有符合当前条件的标准技能。</div>}
      </section> : <section className="list-surface policy-package-list" aria-label="核心策略包列表">
        <div className="list-toolbar policy-package-toolbar">
          <div><strong>策略包目录</strong><span className="toolbar-note">当前只启用平台核心安全策略；规划项用于预留未来治理边界，不提供编辑或发布动作。</span></div>
          <span className="result-count">1 个生效 · 3 个规划中</span>
        </div>
        <table className="data-table policy-package-table">
          <colgroup><col style={{ width: "27%" }} /><col style={{ width: "16%" }} /><col style={{ width: "10%" }} /><col style={{ width: "12%" }} /><col style={{ width: "16%" }} /><col style={{ width: "12%" }} /><col style={{ width: "7%" }} /></colgroup>
          <thead><tr><th>策略包</th><th>治理范围</th><th>当前版本</th><th>状态</th><th>适用对象</th><th>最近更新</th><th><span className="sr-only">操作</span></th></tr></thead>
          <tbody>{policyPackages.map((item) => <tr key={item.id} className={item.available ? "policy-package-row" : "policy-package-row planned"} tabIndex={item.available ? "0" : undefined} onClick={() => item.available && navigate("policy/edit")} onKeyDown={(event) => { if (item.available && event.key === "Enter") navigate("policy/edit"); }}>
            <td><div className="cell-primary">{item.name}</div><div className="cell-secondary">{item.desc}</div></td>
            <td>{item.scope}</td><td>{item.version}</td><td><Status tone={item.available ? "success" : "neutral"}>{item.status}</Status></td><td>{item.targets}</td><td>{item.updated}</td>
            <td>{item.available ? <button className="text-action" type="button" onClick={(event) => { event.stopPropagation(); navigate("policy/edit"); }}>管理</button> : <button className="text-action planned-action" type="button" onClick={() => toast(`${item.name}尚处于规划阶段，当前未启用配置能力`)}>说明</button>}</td>
          </tr>)}</tbody>
        </table>
        <div className="policy-package-footnote"><span>当前功能边界</span><p>只有“平台核心安全策略”对应现有 core-default 策略包及版本、草稿、发布和回滚逻辑。</p></div>
      </section>}
    </div>
  );
}

function SkillDrawer({ skill, onClose, navigate, toast, initialTab = "overview" }) {
  const [tab, setTab] = useState(initialTab);
  if (!skill) return null;
  return <div className="drawer-layer" onMouseDown={onClose}>
    <aside className="drawer skill-drawer" role="dialog" aria-modal="true" aria-labelledby="drawer-title" onMouseDown={(event) => event.stopPropagation()}>
      <header className="drawer-header"><div><span className="section-label">标准技能速览</span><h2 id="drawer-title">{skill.name}</h2><p>{skill.desc}</p></div><button type="button" className="icon-button" aria-label="关闭详情" onClick={onClose}><X size={20} /></button></header>
      <div className="drawer-body">
        <div className="drawer-summary-bar"><Status tone={skill.status === "待检查" ? "warning" : "success"}>{skill.status}</Status><span>{skill.version} 当前版本</span><span>{skill.risk}</span><span>{skill.agents} 个 Agent</span><span>{skill.workflows} 个已发布工作流</span></div>
        <nav className="drawer-tabs" role="tablist" aria-label="技能速览内容">
          {[["overview", "概览"], ["versions", "技能版本"], ["dependencies", "依赖与影响"]].map(([key, label]) => <button type="button" role="tab" aria-selected={tab === key} className={tab === key ? "active" : ""} onClick={() => setTab(key)} key={key}>{label}</button>)}
        </nav>
        {tab === "overview" && <div className="drawer-panel"><div className="drawer-overview-grid"><section><div className="drawer-section-heading"><h3>治理摘要</h3><span>配置完整</span></div><dl className="summary-list"><div><dt>可见性</dt><dd>租户可见</dd></div><div><dt>绑定策略</dt><dd>按需绑定</dd></div><div><dt>对租户启用</dt><dd>是</dd></div><div><dt>风险等级</dt><dd>{skill.risk}</dd></div><div><dt>最后校验</dt><dd>今天 17:10</dd></div></dl></section><section><div className="drawer-section-heading"><h3>运行与发布</h3><span>无阻断项</span></div><dl className="summary-list"><div><dt>绑定 Agent</dt><dd>{skill.agents}</dd></div><div><dt>已发布工作流</dt><dd>{skill.workflows}</dd></div><div><dt>历史版本引用</dt><dd>{skill.id === "meeting" ? 1 : 0}</dd></div><div><dt>当前草稿</dt><dd>{skill.id === "meeting" ? "v4" : "无"}</dd></div><div><dt>稳定回滚点</dt><dd>v2</dd></div></dl></section></div><section className="drawer-checks"><h3>发布检查</h3><div className="check-line"><CheckCircle size={18} /><span>治理配置、模板内容与输出约束完整。</span></div><div className="check-line"><CheckCircle size={18} /><span>存在稳定回滚点，当前没有发布阻断项。</span></div>{skill.status === "待检查" && <div className="check-line warning"><WarningCircle size={18} /><span>当前草稿仍有高风险变更待检查。</span></div>}</section></div>}
        {tab === "versions" && <div className="drawer-panel"><div className="panel-heading"><div><h3>技能版本</h3><p>不可变版本、草稿状态与安全回滚点。</p></div><button className="button secondary" type="button" onClick={() => navigate("skill/edit")}>编辑并创建草稿</button></div><table className="data-table drawer-table"><thead><tr><th>版本</th><th>状态</th><th>变更摘要</th><th>影响</th><th>发布时间</th><th>操作</th></tr></thead><tbody><tr><td><strong>v4</strong></td><td><Status tone="warning">草稿</Status></td><td>强化行动项证据边界</td><td>待预检</td><td>今天 18:12</td><td><button className="text-action" type="button" onClick={() => navigate("skill/preview")}>预览</button></td></tr><tr><td><strong>v3</strong></td><td><Status>当前生效</Status></td><td>强化行动项与证据边界</td><td>3 个工作流</td><td>今天 17:10</td><td><button className="text-action" type="button" onClick={() => toast("正在查看 v3 版本详情")}>查看</button></td></tr><tr><td><strong>v2</strong></td><td>稳定版本</td><td>增加说话人归并策略</td><td>1 个历史引用</td><td>8 月 6 日</td><td><button className="text-action" type="button" onClick={() => toast("v2 已设为安全回滚点")}>设为回滚点</button></td></tr><tr><td><strong>v1</strong></td><td>已归档</td><td>首次发布</td><td>无运行引用</td><td>7 月 29 日</td><td><button className="text-action" type="button" onClick={() => toast("正在查看 v1 版本详情")}>查看</button></td></tr></tbody></table><div className="drawer-inline-note"><CheckCircle size={18} /><span>草稿不会覆盖线上版本，发布前仍需完成预检。</span></div></div>}
        {tab === "dependencies" && <div className="drawer-panel"><div className="panel-heading"><div><h3>依赖与影响</h3><p>当前线上版本被 Agent、工作流和历史发布版本引用的情况。</p></div><span className="panel-count">0 个阻断项</span></div><div className="drawer-impact-summary"><div><span>绑定 Agent</span><strong>{skill.agents}</strong></div><div><span>已发布工作流</span><strong>{skill.workflows}</strong></div><div><span>历史版本引用</span><strong>{skill.id === "meeting" ? 1 : 0}</strong></div><div><span>阻断项</span><strong>0</strong></div></div><table className="data-table drawer-table"><thead><tr><th>引用对象</th><th>类型</th><th>固定版本</th><th>环境</th><th>状态</th></tr></thead><tbody><tr><td><strong>会议纪要助手</strong></td><td>Agent</td><td>v3</td><td>生产</td><td><Status>正常</Status></td></tr><tr><td><strong>销售周会纪要</strong></td><td>工作流</td><td>v3</td><td>生产</td><td><Status>正常</Status></td></tr><tr><td><strong>客户成功复盘</strong></td><td>工作流</td><td>v2</td><td>生产</td><td><Status tone="warning">历史引用</Status></td></tr></tbody></table><div className="drawer-inline-note warning"><WarningCircle size={18} /><span>客户成功复盘仍固定引用 v2，发布新版本不会自动迁移该引用。</span></div></div>}
      </div>
      <footer className="drawer-footer"><button type="button" className="text-action" onClick={() => navigate("skill/preview")}><Eye size={16} />预览当前草稿</button><button type="button" className="button primary" onClick={() => navigate("skill/edit")}>编辑技能 <CaretRight size={16} /></button></footer>
    </aside>
  </div>;
}

function EditorNav({ section, setSection }) {
  const items = [["governance", "治理设置"], ["template", "模板内容"], ["boundary", "能力边界"], ["notes", "本版说明"]];
  return <nav className="object-nav editor-nav" aria-label="技能编辑步骤">
    {items.map(([key, label], index) => <button type="button" className={section === key ? "active" : ""} onClick={() => setSection(key)} key={key}><span className="step-number">{index + 1}</span>{label}</button>)}
  </nav>;
}

function SkillEditor({ navigate, toast }) {
  const [section, setSection] = useState("governance");
  return <div className="page focused-page">
    <PageHeader back={{ route: "skills", label: "返回技能治理" }} title="编辑技能 · AI 听记" description="在独立编辑页完成草稿；版本和依赖保持在技能速览中，编辑字段与发布逻辑不变。" navigate={navigate}
      actions={<><button className="button secondary" type="button" onClick={() => navigate("skill/preview")}><Eye size={16} />预览草稿</button><button className="button primary" type="button" onClick={() => toast("已保存为 v4 草稿版本")}>保存为新草稿版本</button></>} />
    <EditorNav section={section} setSection={setSection} />
    <div className="focused-content editor-content">
      {section === "governance" && <section className="form-section single-section"><div className="section-intro"><span className="section-kicker">01 / 治理设置</span><h3>发布与绑定规则</h3><p>决定技能是否进入租户目录，以及 Agent 如何发现并绑定此能力。</p></div><div className="form-grid">
        <label><span>可见性</span><select defaultValue="租户可见"><option>租户可见</option><option>平台内部</option><option>隐藏</option></select></label>
        <label><span>绑定策略</span><select defaultValue="按需绑定"><option>按需绑定</option><option>默认绑定</option><option>禁止新增绑定</option></select></label>
        <label className="switch-field"><span><strong>对租户启用</strong><small>关闭后，新建 Agent 和运行时均不可使用。</small></span><input type="checkbox" defaultChecked /></label>
      </div></section>}
      {section === "template" && <section className="form-section single-section"><div className="section-intro"><span className="section-kicker">02 / 模板内容</span><h3>技能模板编辑器</h3><p>维护技能名称、用途说明和运行时提示模板。保存后生成新草稿，不覆盖线上版本。</p></div><div className="form-grid">
        <label><span>技能名称</span><input defaultValue="AI 听记" /></label>
        <label><span>风险等级</span><select defaultValue="中风险"><option>低风险</option><option>中风险</option><option>高风险</option></select></label>
        <label className="full"><span>能力说明</span><textarea defaultValue="面向会议实时转写后的结构化纪要与行动项生成。" /></label>
        <label className="full"><span>模板正文片段</span><textarea className="template-textarea" defaultValue="你负责把实时转写整理为可追溯的结构化纪要。\n先识别议题、决策和行动项，再关联对应发言证据；不得补写转写中不存在的事实。" /></label>
      </div></section>}
      {section === "boundary" && <section className="form-section single-section"><div className="section-intro"><span className="section-kicker">03 / 能力边界</span><h3>工具、知识与输出约束</h3><p>明确技能可以调用什么、引用什么，以及无法可靠完成时如何移交。</p></div><div className="form-grid">
        <label className="full"><span>可调用工具</span><input defaultValue="听记转写读取、会议参与人查询、待办创建" /></label>
        <label className="full"><span>可引用知识库</span><input defaultValue="会议制度、行动项规范、组织通讯录" /></label>
        <label className="full"><span>兜底移交规则</span><textarea defaultValue="转写缺失、说话人无法识别或涉及高风险承诺时，标记不确定项并移交人工确认。" /></label>
        <label className="full"><span>输出约束</span><input defaultValue="结构化纪要 + 行动项 + 对应证据；禁止补写事实" /></label>
      </div></section>}
      {section === "notes" && <section className="form-section single-section"><div className="section-intro"><span className="section-kicker">04 / 本版说明</span><h3>草稿说明与发布提示</h3><p>记录本次修改目的，供版本审阅、发布审批和后续回滚使用。</p></div><div className="form-grid">
        <label className="full"><span>本版说明</span><textarea className="template-textarea" defaultValue="强化行动项证据边界；补充说话人无法识别时的人工确认规则。" /></label>
        <div className="editor-readiness full"><CheckCircle size={19} /><div><strong>草稿信息完整</strong><span>保存后可回到技能速览的“技能版本”页签继续审阅；不会直接覆盖当前线上 v3。</span></div></div>
      </div></section>}
    </div>
  </div>;
}

function SkillVersions({ navigate, toast }) {
  return <div className="page focused-page"><PageHeader back={{ route: "skills/policies", label: "返回策略与版本" }} title="AI 听记 · 模板版本" description="版本页只负责审阅不可变版本、草稿状态与安全回滚点。" navigate={navigate} actions={<button className="button primary" type="button" onClick={() => navigate("skill/edit")}>编辑并创建草稿</button>} />
    <div className="focused-content"><section className="simple-section"><div className="section-heading"><div><h3>版本时间线</h3><p>线上版本不会被草稿直接覆盖。</p></div></div><table className="data-table version-table"><thead><tr><th>版本</th><th>状态</th><th>变更摘要</th><th>影响</th><th>发布时间</th><th>操作</th></tr></thead><tbody>
      <tr><td><strong>v3</strong></td><td><Status>当前生效</Status></td><td>强化行动项与证据边界</td><td>3 个工作流</td><td>今天 17:10</td><td><button className="text-action" type="button">查看</button></td></tr>
      <tr><td><strong>v2</strong></td><td>稳定版本</td><td>增加说话人归并策略</td><td>1 个历史引用</td><td>8 月 6 日</td><td><button className="text-action" type="button">设为回滚点</button></td></tr>
      <tr><td><strong>v1</strong></td><td>已归档</td><td>首次发布</td><td>无运行引用</td><td>7 月 29 日</td><td><button className="text-action" type="button">查看</button></td></tr>
    </tbody></table></section></div></div>;
}

function PoliciesOverview({ navigate }) {
  const [scope, setScope] = useState("templates");
  return <div className="page"><PageHeader eyebrow="策略与版本" title="策略与版本" description="先选择要治理的版本对象，再进入对应的版本历史或策略编辑页。" navigate={navigate} />
    <nav className="object-nav page-level" aria-label="策略与版本类型"><button type="button" className={scope === "templates" ? "active" : ""} onClick={() => setScope("templates")}>技能模板版本</button><button type="button" className={scope === "policy" ? "active" : ""} onClick={() => setScope("policy")}>核心策略包</button></nav>
    {scope === "templates" ? <section className="list-surface policy-list"><div className="list-toolbar"><div className="search-field"><MagnifyingGlass size={17} /><input placeholder="搜索技能名称" /></div><span className="result-count">17 项标准技能</span></div><table className="data-table"><thead><tr><th>标准技能</th><th>当前版本</th><th>草稿</th><th>发布状态</th><th>最近更新</th><th></th></tr></thead><tbody>{skills.map((skill) => <tr key={skill.id} tabIndex="0" onClick={() => navigate("skill/versions")} onKeyDown={(event) => event.key === "Enter" && navigate("skill/versions")}><td><strong>{skill.name}</strong></td><td>{skill.version}</td><td>{skill.id === "meeting" ? "v4 草稿" : "—"}</td><td><Status tone={skill.status === "待检查" ? "warning" : "success"}>{skill.status === "待检查" ? "待审阅" : "已发布"}</Status></td><td>{skill.updated}</td><td><button className="text-action" type="button">查看版本</button></td></tr>)}</tbody></table></section>
      : <section className="list-surface policy-list"><div className="list-toolbar"><div><strong>平台核心策略包</strong><span className="toolbar-note">统一约束标准技能的安全边界与人工确认规则</span></div><span className="result-count">1 个策略包</span></div><table className="data-table"><thead><tr><th>策略包</th><th>当前版本</th><th>适用技能</th><th>规则摘要</th><th>最近更新</th><th></th></tr></thead><tbody><tr><td><strong>平台核心安全策略</strong></td><td>v1</td><td>17 项</td><td>高风险确认、证据边界、敏感信息保护</td><td>8 月 8 日</td><td><button className="text-action" type="button" onClick={() => navigate("policy/edit")}>编辑策略</button></td></tr></tbody></table></section>}
  </div>;
}

function PolicyEditor({ navigate, toast }) {
  return <div className="page focused-page"><PageHeader back={{ route: "skills/policies", label: "返回技能治理" }} title="编辑策略 · 平台核心安全策略" description="策略编辑使用独立页面，保存后生成新的策略草稿版本。" navigate={navigate} actions={<button className="button primary" type="button" onClick={() => toast("已保存为策略包 v2 草稿")}>保存为策略草稿</button>} />
    <div className="focused-content editor-content"><section className="form-section single-section"><div className="section-intro"><span className="section-kicker">核心策略编辑器</span><h3>通用安全与证据规则</h3><p>这组规则由所有平台标准技能在运行时共同引用。</p></div><div className="form-grid"><label><span>策略名称</span><input defaultValue="平台核心安全策略" /></label><label><span>适用范围</span><select defaultValue="全部标准技能"><option>全部标准技能</option><option>高风险技能</option></select></label><label className="full"><span>策略说明</span><textarea defaultValue="统一定义高风险动作确认、证据引用和敏感信息保护规则。" /></label><label className="full"><span>提示片段</span><textarea className="template-textarea" defaultValue="涉及外部写入、权限变更或不可逆动作时，必须先向用户展示影响范围并获得明确确认。" /></label><label className="full"><span>人工移交规则</span><textarea defaultValue="证据不足、权限不确定或规则冲突时停止执行，并将当前上下文移交人工审阅。" /></label></div></section></div>
  </div>;
}

function DependenciesOverview({ navigate, onSelect }) {
  return <div className="page"><PageHeader eyebrow="依赖与影响" title="依赖与影响" description="从技能维度扫描 Agent、工作流与历史版本引用；点击记录查看影响摘要。" navigate={navigate} />
    <section className="list-surface"><div className="list-toolbar"><div className="search-field"><MagnifyingGlass size={17} /><input placeholder="搜索技能或引用对象" /></div><div className="filter-tabs"><button className="filter-tab active" type="button">全部</button><button className="filter-tab" type="button">存在历史引用</button><button className="filter-tab" type="button">存在阻断项</button></div><span className="result-count">17 项</span></div><table className="data-table"><thead><tr><th>标准技能</th><th>绑定 Agent</th><th>已发布工作流</th><th>历史版本引用</th><th>发布风险</th><th>最近校验</th><th></th></tr></thead><tbody>{skills.map((skill) => <tr key={skill.id} tabIndex="0" onClick={() => onSelect(skill)} onKeyDown={(event) => event.key === "Enter" && onSelect(skill)}><td><strong>{skill.name}</strong></td><td>{skill.agents}</td><td>{skill.workflows}</td><td>{skill.id === "meeting" ? 1 : 0}</td><td><Status tone={skill.risk === "高风险" ? "warning" : "success"}>{skill.risk}</Status></td><td>{skill.updated}</td><td><button className="text-action" type="button">查看影响</button></td></tr>)}</tbody></table></section>
  </div>;
}

function ImpactDrawer({ skill, onClose, navigate }) {
  if (!skill) return null;
  return <div className="drawer-layer" onMouseDown={onClose}><aside className="drawer" role="dialog" aria-modal="true" aria-labelledby="impact-title" onMouseDown={(event) => event.stopPropagation()}><header className="drawer-header"><div><span className="section-label">依赖影响速览</span><h2 id="impact-title">{skill.name}</h2></div><button type="button" className="icon-button" aria-label="关闭影响详情" onClick={onClose}><X size={20} /></button></header><div className="drawer-body"><p className="drawer-description">当前线上版本 {skill.version} 的生产引用摘要。</p><section className="drawer-section"><h3>影响范围</h3><dl className="summary-list"><div><dt>绑定 Agent</dt><dd>{skill.agents}</dd></div><div><dt>已发布工作流</dt><dd>{skill.workflows}</dd></div><div><dt>历史版本引用</dt><dd>{skill.id === "meeting" ? 1 : 0}</dd></div><div><dt>发布阻断项</dt><dd>0</dd></div></dl></section><section className="drawer-section"><h3>运营判断</h3><div className="check-line"><CheckCircle size={18} /><span>当前没有发布阻断项。</span></div>{skill.id === "meeting" && <div className="check-line warning"><WarningCircle size={18} /><span>客户成功复盘仍固定引用历史版本 v2。</span></div>}</section></div><footer className="drawer-footer"><span></span><button className="button primary" type="button" onClick={() => navigate("skill/dependencies")}>查看完整依赖 <CaretRight size={16} /></button></footer></aside></div>;
}

function SkillDependencies({ navigate }) {
  return <div className="page focused-page"><PageHeader back={{ route: "skills/dependencies", label: "返回依赖与影响" }} title="AI 听记 · 完整依赖" description="独立查看当前版本被哪些 Agent、工作流和历史发布版本引用。" navigate={navigate} />
    <div className="focused-content"><div className="impact-summary"><div><span>绑定 Agent</span><strong>6</strong></div><div><span>已发布工作流</span><strong>3</strong></div><div><span>历史版本引用</span><strong>1</strong></div><div><span>阻断项</span><strong>0</strong></div></div>
      <section className="simple-section"><div className="section-heading"><div><h3>运行引用</h3><p>只展示与当前技能版本有关的生产引用。</p></div></div><table className="data-table"><thead><tr><th>引用对象</th><th>类型</th><th>固定版本</th><th>环境</th><th>状态</th></tr></thead><tbody><tr><td><strong>会议纪要助手</strong></td><td>Agent</td><td>v3</td><td>生产</td><td><Status>正常</Status></td></tr><tr><td><strong>销售周会纪要</strong></td><td>工作流</td><td>v3</td><td>生产</td><td><Status>正常</Status></td></tr><tr><td><strong>客户成功复盘</strong></td><td>工作流</td><td>v2</td><td>生产</td><td><Status tone="warning">历史引用</Status></td></tr></tbody></table></section>
    </div></div>;
}

function SkillPreview({ route, navigate, toast }) {
  return <div className="page focused-page preview-page"><PageHeader back={{ route: "skill/edit", label: "返回技能编辑" }} title="AI 听记 · 草稿预览" description="预览页只读呈现将要进入版本审阅的差异和影响范围。" navigate={navigate} actions={<button className="button primary" type="button" onClick={() => toast("草稿预检已通过")}>运行草稿预检</button>} />
    <div className="preview-layout"><div className="preview-main"><section className="simple-section"><div className="section-heading"><div><h3>变更摘要</h3><p>草稿 v4 相对当前线上版本 v3。</p></div></div><div className="diff-row"><span>输出契约</span><div><del>结构化纪要 + 行动项</del><ins>结构化纪要 + 行动项 + 证据说明</ins></div></div><div className="diff-row"><span>风险等级</span><div><del>低风险</del><ins>中风险</ins></div></div></section><section className="simple-section"><h3>生效后配置</h3><dl className="preview-def"><div><dt>可见性</dt><dd>租户可见</dd></div><div><dt>绑定策略</dt><dd>按需绑定</dd></div><div><dt>对租户启用</dt><dd>是</dd></div><div><dt>目标版本</dt><dd>v4</dd></div></dl></section></div>
      <aside className="preview-checks"><h3>发布检查</h3><div className="check-line"><CheckCircle size={18} /><span>治理配置完整</span></div><div className="check-line"><CheckCircle size={18} /><span>存在稳定回滚点 v3</span></div><div className="check-line warning"><WarningCircle size={18} /><span>建议先验证 1 个小范围 Agent</span></div><hr /><dl className="summary-list"><div><dt>受影响 Agent</dt><dd>6</dd></div><div><dt>受影响工作流</dt><dd>3</dd></div><div><dt>阻断项</dt><dd>0</dd></div></dl></aside>
    </div></div>;
}

function ModelNav({ route, navigate }) {
  return <nav className="object-nav page-level" aria-label="模型配置导航"><button type="button" className={route === "models/providers" ? "active" : ""} onClick={() => navigate("models/providers")}>模型厂商</button><button type="button" className={route === "models/routes" ? "active" : ""} onClick={() => navigate("models/routes")}>场景模型路由</button></nav>;
}

function ModelProviders({ navigate, selected, setSelected }) {
  return <div className="page"><PageHeader eyebrow="模型配置" title="模型厂商" description="维护平台级模型厂商、连接状态和允许进入运行目录的模型。" navigate={navigate} /><ModelNav route="models/providers" navigate={navigate} /><section className="list-surface"><div className="list-toolbar"><div className="search-field"><MagnifyingGlass size={17} /><input placeholder="搜索模型厂商" /></div><span className="result-count">7 个厂商 · 1 个已启用</span></div><table className="data-table"><thead><tr><th>模型厂商</th><th>状态</th><th>可用模型</th><th>场景路由</th><th>最近检测</th><th></th></tr></thead><tbody>{providers.map((provider) => <tr key={provider.id} tabIndex="0" onClick={() => setSelected(provider)} onKeyDown={(event) => event.key === "Enter" && setSelected(provider)}><td><div className="cell-primary">{provider.name}</div><div className="cell-secondary">{provider.endpoint}</div></td><td><Status tone={provider.status === "已启用" ? "success" : "neutral"}>{provider.status}</Status></td><td>{provider.models}</td><td>{provider.routes}</td><td>{provider.checked}</td><td><button className="icon-button" aria-label={`查看 ${provider.name}`} type="button"><DotsThree size={19} /></button></td></tr>)}</tbody></table></section>{selected && <ProviderDrawer provider={selected} onClose={() => setSelected(null)} navigate={navigate} />}</div>;
}

function ProviderDrawer({ provider, onClose, navigate }) {
  return <div className="drawer-layer" onMouseDown={onClose}><aside className="drawer" role="dialog" aria-modal="true" onMouseDown={(event) => event.stopPropagation()}><header className="drawer-header"><div><span className="section-label">模型厂商速览</span><h2>{provider.name}</h2></div><button className="icon-button" type="button" aria-label="关闭详情" onClick={onClose}><X size={20} /></button></header><div className="drawer-body"><div className="drawer-meta"><Status tone={provider.status === "已启用" ? "success" : "neutral"}>{provider.status}</Status><span>{provider.models} 个可用模型</span></div><section className="drawer-section"><h3>连接信息</h3><dl className="summary-list"><div><dt>API 地址</dt><dd className="break-value">{provider.endpoint}</dd></div><div><dt>凭据状态</dt><dd>{provider.status === "已启用" ? "已安全保存" : "未配置"}</dd></div><div><dt>最近检测</dt><dd>{provider.checked}</dd></div></dl></section><section className="drawer-section"><h3>运行影响</h3><dl className="summary-list"><div><dt>场景路由</dt><dd>{provider.routes}</dd></div><div><dt>有效模型</dt><dd>{provider.models}</dd></div></dl></section></div><footer className="drawer-footer"><span></span><button className="button primary" type="button" onClick={() => navigate("models/provider-edit")}>进入厂商配置 <CaretRight size={16} /></button></footer></aside></div>;
}

function ProviderEdit({ navigate, toast }) {
  return <div className="page focused-page"><PageHeader back={{ route: "models/providers", label: "返回模型厂商" }} title="OneKeyToken" description="编辑平台级连接参数；凭据值不会在页面中直接显示。" navigate={navigate} actions={<><button className="button secondary" type="button" onClick={() => toast("连接检测成功")}>检测连接</button><button className="button primary" type="button" onClick={() => toast("厂商配置已保存")}>保存配置</button></>} /><div className="focused-content"><section className="form-section"><div className="section-intro"><h3>连接配置</h3><p>仅平台运营账号可维护，保存后通过受管密钥存储生效。</p></div><div className="form-grid"><label className="full"><span>API 地址</span><input defaultValue="https://my.onekeytoken.com/v1" /></label><label className="full"><span>API Key</span><div className="compound-input"><input type="password" defaultValue="configured-secret-value" /><button type="button">显示</button><button type="button">重置</button></div></label><label className="switch-field"><span><strong>启用厂商</strong><small>启用前必须通过连接检测。</small></span><input type="checkbox" defaultChecked /></label></div></section><section className="simple-section"><div className="section-heading"><div><h3>已选模型</h3><p>只有经过检测的模型可以加入平台运行目录。</p></div><button type="button" className="button secondary">管理模型</button></div><table className="data-table"><thead><tr><th>模型</th><th>来源</th><th>状态</th><th>最近验证</th></tr></thead><tbody><tr><td><strong>onekeytoken/auto</strong></td><td>直接验证</td><td><Status>可用</Status></td><td>今天 18:30</td></tr></tbody></table></section></div></div>;
}

function ModelRoutes({ navigate, toast }) {
  const rows = [["对话运行", "onekeytoken/auto", "平台可用模型", "有效", "今天 18:30"], ["技能编写", "onekeytoken/auto", "Agent 偏好", "有效", "今天 18:30"], ["AI 听记", "onekeytoken/auto", "平台可用模型", "有效", "今天 17:10"], ["客户洞察", "未配置", "平台可用模型", "使用回退", "—"], ["评测运行", "onekeytoken/auto", "平台可用模型", "有效", "昨天 20:18"]];
  return <div className="page"><PageHeader eyebrow="模型配置" title="场景模型路由" description="为每个运行场景配置首选模型；失效时按平台目录与 Agent 偏好回退。" navigate={navigate} actions={<button className="button primary" type="button" onClick={() => toast("场景路由已保存")}>保存路由</button>} /><ModelNav route="models/routes" navigate={navigate} /><section className="list-surface route-list"><table className="data-table"><thead><tr><th>业务场景</th><th>首选模型</th><th>回退策略</th><th>状态</th><th>最近验证</th></tr></thead><tbody>{rows.map((row) => <tr key={row[0]}><td><strong>{row[0]}</strong></td><td><select defaultValue={row[1]}><option>{row[1]}</option><option>onekeytoken/auto</option><option>未配置</option></select></td><td>{row[2]}</td><td><Status tone={row[3] === "有效" ? "success" : "warning"}>{row[3]}</Status></td><td>{row[4]}</td></tr>)}</tbody></table></section></div>;
}

function SimpleDirectory({ kind, navigate }) {
  const integrations = kind === "integrations";
  const rows = integrations ? [["网络搜索", "为 Agent 提供受管互联网检索", "待配置凭据", "未检测"], ["实时语音转写", "为 AI 听记提供语音识别", "已启用", "今天 16:20"]] : [["知识库检索", "原生工具", "14 个技能", "可用"], ["CRM 记录查询", "业务工具", "8 个技能", "可用"], ["研发项目创建", "写入工具", "1 个技能", "需确认"], ["邮件发送", "高风险工具", "3 个技能", "需确认"]];
  return <div className="page"><PageHeader eyebrow={integrations ? "平台集成" : "工具目录"} title={integrations ? "平台托管集成" : "平台工具目录"} description={integrations ? "查看并配置由平台托管的外部能力与凭据。" : "查看 Agent 可以执行的工具、风险等级和技能引用。"} navigate={navigate} actions={<button className="button primary" type="button">{integrations ? "新增平台集成" : "新增平台工具"}</button>} /><section className="list-surface"><div className="list-toolbar"><div className="search-field"><MagnifyingGlass size={17} /><input placeholder={integrations ? "搜索集成名称" : "搜索工具名称"} /></div><span className="result-count">{rows.length} 项</span></div><table className="data-table"><thead><tr><th>{integrations ? "集成" : "工具"}</th><th>用途 / 类型</th><th>{integrations ? "配置状态" : "关联技能"}</th><th>{integrations ? "最近检测" : "状态"}</th><th></th></tr></thead><tbody>{rows.map((row) => <tr key={row[0]}><td><strong>{row[0]}</strong></td><td>{row[1]}</td><td>{row[2]}</td><td>{row[3]}</td><td><button className="icon-button" type="button" aria-label={`查看 ${row[0]}`}><DotsThree size={19} /></button></td></tr>)}</tbody></table></section></div>;
}

export function App() {
  const [route, navigate] = useHashRoute();
  const [selectedSkill, setSelectedSkill] = useState(null);
  const [selectedProvider, setSelectedProvider] = useState(null);
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState("全部");
  const [message, setMessage] = useState("");
  const toast = (text) => { setMessage(text); window.setTimeout(() => setMessage(""), 2200); };
  useEffect(() => { setSelectedSkill(null); setSelectedProvider(null); window.scrollTo(0, 0); }, [route]);
  let content;
  if (route === "skills" || route === "skills/dependencies") content = <SkillGovernanceHome navigate={navigate} onSelect={setSelectedSkill} query={query} setQuery={setQuery} filter={filter} setFilter={setFilter} toast={toast} />;
  else if (route === "skills/policies") content = <SkillGovernanceHome navigate={navigate} onSelect={setSelectedSkill} query={query} setQuery={setQuery} filter={filter} setFilter={setFilter} toast={toast} initialView="policy" />;
  else if (route === "skill/edit" || route === "skill/config") content = <SkillEditor navigate={navigate} toast={toast} />;
  else if (route === "skill/versions" || route === "skill/dependencies") content = <SkillGovernanceHome navigate={navigate} onSelect={setSelectedSkill} query={query} setQuery={setQuery} filter={filter} setFilter={setFilter} toast={toast} />;
  else if (route === "policy/edit") content = <PolicyEditor navigate={navigate} toast={toast} />;
  else if (route === "skill/preview") content = <SkillPreview route={route} navigate={navigate} toast={toast} />;
  else if (route === "models/providers") content = <ModelProviders navigate={navigate} selected={selectedProvider} setSelected={setSelectedProvider} />;
  else if (route === "models/provider-edit") content = <ProviderEdit navigate={navigate} toast={toast} />;
  else if (route === "models/routes") content = <ModelRoutes navigate={navigate} toast={toast} />;
  else if (route === "integrations") content = <SimpleDirectory kind="integrations" navigate={navigate} />;
  else content = <SkillGovernanceHome navigate={navigate} onSelect={setSelectedSkill} query={query} setQuery={setQuery} filter={filter} setFilter={setFilter} toast={toast} />;
  return <Shell route={route} navigate={navigate} onFrozen={(name) => toast(`${name}保持现有页面，本原型不调整`)}>{content}{selectedSkill && <SkillDrawer skill={selectedSkill} onClose={() => setSelectedSkill(null)} navigate={navigate} toast={toast} />}{message && <div className="toast" role="status"><CheckCircle size={18} />{message}</div>}</Shell>;
}
