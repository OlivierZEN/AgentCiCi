import { FormEvent, useEffect, useMemo, useState } from "react";
import {
  createCustomerInsightProject,
  generateCustomerInsightFull,
  generateCustomerInsightSection,
  getCustomerInsightDashboard,
  getCustomerInsightProject,
  listCustomerInsightProjects,
  refreshCustomerInsightSources,
  saveCustomerInsightSection,
} from "./customerInsightApi";
import { CustomerInsightModuleNav } from "./CustomerInsightModuleNav";
import { CustomerInsightReportPreview } from "./CustomerInsightReportPreview";
import { CustomerInsightSectionEditor } from "./CustomerInsightSectionEditor";
import { compactDate, compactMoney, inputToText, segmentLabel, sourceTypeLabel, textToInput } from "./customerInsightSections";
import type { CustomerInsightDashboard, CustomerInsightProject, CustomerInsightSection } from "./customerInsightTypes";

type Props = {
  token: string;
};

const CUSTOMER_COPY = {
  ariaApp: "客户洞察 AI 应用",
  ariaProjects: "客户洞察项目",
  loadError: "客户洞察加载失败",
  projectLoadError: "客户洞察项目加载失败",
  missingName: "请先填写客户名称。",
  created: "客户洞察项目已创建。",
  createError: "创建客户洞察项目失败",
  reportDone: "客户洞察报告已汇总。",
  eyebrow: "CRM 洞察 · 业务闭环",
  title: "数据洞察",
  nameLabel: "客户名称",
  namePlaceholder: "输入客户名称",
  typeLabel: "行业",
  typePlaceholder: "可选",
  createButton: "新建洞察",
  recentTitle: "最近项目",
  loading: "正在加载客户洞察项目。",
  typeFallback: "待补充行业",
  empty: "还没有客户洞察项目。",
  workspaceFallbackTitle: "数据洞察",
  workspaceEmpty: "选择或新建客户项目开始分析",
  refresh: "刷新业务来源",
  starterTitle: "新建一个数据洞察项目",
  starterCopy: "填写客户名称与行业后，可以逐段生成客户画像、合同订单、客户服务、竞争关系和一客一策。",
};

export function CustomerInsightAppPanel({ token }: Props) {
  const copy = CUSTOMER_COPY;
  const [projects, setProjects] = useState<CustomerInsightProject[]>([]);
  const [activeProject, setActiveProject] = useState<CustomerInsightProject | null>(null);
  const [activeSectionCode, setActiveSectionCode] = useState("customer_info");
  const [customerName, setCustomerName] = useState("");
  const [industry, setIndustry] = useState("");
  const [inputText, setInputText] = useState("");
  const [notice, setNotice] = useState("");
  const [dashboard, setDashboard] = useState<CustomerInsightDashboard | null>(null);
  const [dashboardError, setDashboardError] = useState("");
  const [loading, setLoading] = useState(false);
  const [dashboardLoading, setDashboardLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [generatingFull, setGeneratingFull] = useState(false);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      if (!token) return;
      setLoading(true);
      setDashboardLoading(true);
      try {
        const [rows, dashboardPayload] = await Promise.all([
          listCustomerInsightProjects(token),
          getCustomerInsightDashboard(token),
        ]);
        if (cancelled) return;
        setDashboard(dashboardPayload);
        setDashboardError("");
        setProjects(rows);
        if (rows[0]) {
          const detail = await getCustomerInsightProject(token, rows[0].projectId);
          if (cancelled) return;
          setActiveProject(detail);
          setActiveSectionCode(detail.sections?.[0]?.sectionCode || "customer_info");
        }
      } catch (error) {
        if (!cancelled) setNotice(error instanceof Error ? error.message : copy.loadError);
      } finally {
        if (!cancelled) setLoading(false);
        if (!cancelled) setDashboardLoading(false);
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, [token]);

  const sections = activeProject?.sections ?? [];
  const activeSection = useMemo(
    () => sections.find((section) => section.sectionCode === activeSectionCode) ?? sections[0] ?? null,
    [activeSectionCode, sections],
  );

  useEffect(() => {
    if (activeSection) {
      setInputText(inputToText(activeSection.input));
    }
  }, [activeSection?.sectionCode, activeSection?.updatedAt]);

  async function openProject(projectId: string) {
    setLoading(true);
    setNotice("");
    try {
      const detail = await getCustomerInsightProject(token, projectId);
      setActiveProject(detail);
      setActiveSectionCode(detail.sections?.[0]?.sectionCode || "customer_info");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : copy.projectLoadError);
    } finally {
      setLoading(false);
    }
  }

  async function handleCreate(event: FormEvent) {
    event.preventDefault();
    const name = customerName.trim();
    if (!name) {
      setNotice(copy.missingName);
      return;
    }
    setSaving(true);
    setNotice("");
    try {
      const project = await createCustomerInsightProject(token, {
        customerName: name,
        industry: industry.trim(),
        sourceType: "MANUAL",
      });
      setProjects((prev) => [project, ...prev.filter((item) => item.projectId !== project.projectId)]);
      setActiveProject(project);
      setActiveSectionCode(project.sections?.[0]?.sectionCode || "customer_info");
      setCustomerName("");
      setIndustry("");
      setNotice(copy.created);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : copy.createError);
    } finally {
      setSaving(false);
    }
  }

  async function reloadActiveProject(projectId = activeProject?.projectId) {
    if (!projectId) return null;
    const detail = await getCustomerInsightProject(token, projectId);
    setActiveProject(detail);
    setProjects((prev) => [detail, ...prev.filter((item) => item.projectId !== detail.projectId)]);
    return detail;
  }

  async function handleRefreshSources() {
    if (!activeProject) return;
    setSaving(true);
    setNotice("");
    try {
      await refreshCustomerInsightSources(token, activeProject.projectId);
      await reloadActiveProject(activeProject.projectId);
      await reloadDashboard();
      setNotice("数据源状态已刷新。");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "数据源刷新失败");
    } finally {
      setSaving(false);
    }
  }

  async function handleSaveSection() {
    if (!activeProject || !activeSection) return;
    setSaving(true);
    setNotice("");
    try {
      const section = await saveCustomerInsightSection(token, activeProject.projectId, activeSection.sectionCode, {
        input: textToInput(inputText),
        markdown: activeSection.markdown,
      });
      mergeSection(section);
      setNotice("模块草稿已保存。");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "保存模块失败");
    } finally {
      setSaving(false);
    }
  }

  async function handleGenerateSection() {
    if (!activeProject || !activeSection) return;
    setGenerating(true);
    setNotice("");
    try {
      const result = await generateCustomerInsightSection(token, activeProject.projectId, activeSection.sectionCode, {
        input: textToInput(inputText),
        markdown: activeSection.markdown,
      });
      setActiveProject((prev) => mergeProjectPayload(prev, result.project, result.section));
      setProjects((prev) => [result.project, ...prev.filter((item) => item.projectId !== result.project.projectId)]);
      setNotice(result.success ? "当前模块已生成。" : result.error || "模块生成失败");
      await reloadActiveProject(result.project.projectId);
      await reloadDashboard();
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "模块生成失败");
    } finally {
      setGenerating(false);
    }
  }

  async function handleGenerateFull() {
    if (!activeProject) return;
    setGeneratingFull(true);
    setNotice("");
    try {
      const result = await generateCustomerInsightFull(token, activeProject.projectId);
      setActiveSectionCode(result.section.sectionCode);
      await reloadActiveProject(result.project.projectId);
      await reloadDashboard();
      setNotice(copy.reportDone);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "整案汇总失败");
    } finally {
      setGeneratingFull(false);
    }
  }

  function mergeSection(section: CustomerInsightSection) {
    setActiveProject((prev) => mergeProjectPayload(prev, null, section));
  }

  async function reloadDashboard() {
    try {
      const payload = await getCustomerInsightDashboard(token);
      setDashboard(payload);
      setDashboardError("");
    } catch (error) {
      setDashboardError(error instanceof Error ? error.message : "数据洞察仪表板刷新失败");
    }
  }

  return (
    <section className="cici-customer-insight" aria-label={copy.ariaApp}>
      <aside className="cici-customer-insight__projects" aria-label={copy.ariaProjects}>
        <form className="cici-customer-insight__create" onSubmit={handleCreate}>
          <div className="cici-customer-insight__create-head">
            <p>{copy.eyebrow}</p>
            <h3>{copy.title}</h3>
          </div>
          <label>
            {copy.nameLabel}
            <input value={customerName} onChange={(event) => setCustomerName(event.target.value)} placeholder={copy.namePlaceholder} />
          </label>
          <label>
            {copy.typeLabel}
            <input value={industry} onChange={(event) => setIndustry(event.target.value)} placeholder={copy.typePlaceholder} />
          </label>
          <button type="submit" className="cici-customer-insight__primary" disabled={saving}>
            {copy.createButton}
          </button>
        </form>

        <div className="cici-customer-insight__project-list">
          <h3>{copy.recentTitle}</h3>
          {loading && !projects.length ? <p className="cici-customer-insight__empty">{copy.loading}</p> : null}
          {projects.map((project) => {
            const active = project.projectId === activeProject?.projectId;
            return (
              <button
                key={project.projectId}
                type="button"
                className={`cici-customer-insight__project${active ? " is-active" : ""}`}
                onClick={() => void openProject(project.projectId)}
              >
                <strong>{project.customerName}</strong>
                <span>{project.industry || copy.typeFallback} · {project.generatedSectionCount}/{project.sectionCount}</span>
              </button>
            );
          })}
          {!loading && !projects.length ? <p className="cici-customer-insight__empty">{copy.empty}</p> : null}
        </div>
      </aside>

      <section className="cici-customer-insight__workspace">
        <header className="cici-customer-insight__toolbar">
          <div>
            <p>{copy.title}</p>
            <h3>{activeProject?.customerName || copy.workspaceFallbackTitle}</h3>
            <span>{activeProject ? `${activeProject.industry || copy.typeFallback} · ${sourceTypeLabel(activeProject.sourceType)}` : copy.workspaceEmpty}</span>
          </div>
          <div className="cici-customer-insight__toolbar-actions">
            <button type="button" className="cici-customer-insight__secondary" onClick={handleRefreshSources} disabled={!activeProject || saving}>
              {copy.refresh}
            </button>
            <button type="button" className="cici-customer-insight__primary" onClick={handleGenerateSection} disabled={!activeSection || generating}>
              {generating ? "生成中" : "生成模块"}
            </button>
          </div>
        </header>

        {notice ? <p className="cici-customer-insight__notice">{notice}</p> : null}

        <DataInsightDashboard dashboard={dashboard} loading={dashboardLoading} error={dashboardError} />

        {activeProject && activeSection ? (
          <div className="cici-customer-insight__workgrid">
            <CustomerInsightModuleNav
              sections={sections}
              activeSectionCode={activeSection.sectionCode}
              onSelect={setActiveSectionCode}
            />
            <CustomerInsightSectionEditor
              section={activeSection}
              inputText={inputText}
              saving={saving}
              generating={generating}
              onInputChange={setInputText}
              onSave={handleSaveSection}
              onGenerate={handleGenerateSection}
            />
            <CustomerInsightReportPreview
              project={activeProject}
              activeSection={activeSection}
              sources={activeProject.sources ?? []}
              jobs={activeProject.jobs ?? []}
              onGenerateFull={handleGenerateFull}
              generatingFull={generatingFull}
            />
          </div>
        ) : (
          <div className="cici-customer-insight__starter">
            <h3>{copy.starterTitle}</h3>
            <p>{copy.starterCopy}</p>
          </div>
        )}
      </section>
    </section>
  );
}

function DataInsightDashboard({
  dashboard,
  loading,
  error,
}: {
  dashboard: CustomerInsightDashboard | null;
  loading: boolean;
  error: string;
}) {
  if (loading && !dashboard) {
    return (
      <section className="cici-data-insight cici-data-insight--loading" aria-label="数据洞察仪表板">
        {Array.from({ length: 8 }).map((_, index) => (
          <span key={index} />
        ))}
      </section>
    );
  }
  if (!dashboard) {
    return (
      <section className="cici-data-insight cici-data-insight--empty" aria-label="数据洞察仪表板">
        <strong>数据洞察暂不可用</strong>
        <span>{error || "刷新后会展示 CRM 客户、商机、合同订单和销售业绩指标。"}</span>
      </section>
    );
  }
  const summary = dashboard.summary;
  const maxFunnel = Math.max(...dashboard.funnel.map((item) => item.value), 1);
  const maxTrend = Math.max(
    ...dashboard.trend.flatMap((item) => [item.pipeline, item.contract, item.order]),
    1,
  );
  const segmentTotal = Math.max(dashboard.segments.reduce((sum, item) => sum + item.value, 0), 1);
  return (
    <section className="cici-data-insight" aria-label="数据洞察仪表板">
      <div className="cici-data-insight__head">
        <div>
          <p>{sourceTypeLabel(dashboard.sourceMode)}</p>
          <h3>CRM 经营总览</h3>
          <span>{dashboard.sourceDescription}</span>
        </div>
        <time>{compactDate(dashboard.updatedAt)}</time>
      </div>

      <div className="cici-data-insight__metrics" aria-label="核心指标">
        <Metric label="潜在客户" value={`${summary.totalLeads}`} detail={`${summary.openOpportunities} 个活跃商机`} />
        <Metric label="管道金额" value={compactMoney(summary.pipelineAmount)} detail={`赢率 ${summary.winRate}%`} />
        <Metric label="合同金额" value={compactMoney(summary.contractAmount)} detail={`${summary.totalCustomers} 个客户`} />
        <Metric label="订单金额" value={compactMoney(summary.orderAmount)} detail={`健康度 ${summary.avgHealth}`} />
        <Metric label="风险客户" value={`${summary.riskCustomers}`} detail={`${summary.highConfidenceRecommendationCount} 条高置信建议`} />
      </div>

      <div className="cici-data-insight__grid">
        <section className="cici-data-insight__panel cici-data-insight__panel--funnel">
          <div className="cici-data-insight__panel-head">
            <h4>销售漏斗</h4>
            <span>Lead → Order</span>
          </div>
          <div className="cici-data-insight__funnel">
            {dashboard.funnel.map((item) => (
              <div key={item.code} className="cici-data-insight__funnel-row">
                <span>{item.label}</span>
                <div>
                  <i style={{ inlineSize: `${Math.max(10, (item.value / maxFunnel) * 100)}%` }} />
                </div>
                <strong>{item.value}</strong>
              </div>
            ))}
          </div>
        </section>

        <section className="cici-data-insight__panel">
          <div className="cici-data-insight__panel-head">
            <h4>客户结构</h4>
            <span>{segmentTotal} 个对象</span>
          </div>
          <div className="cici-data-insight__segments">
            {dashboard.segments.map((item) => (
              <div key={item.code}>
                <b style={{ background: item.color }} />
                <span>{item.label}</span>
                <i>{Math.round((item.value / segmentTotal) * 100)}%</i>
              </div>
            ))}
          </div>
        </section>

        <section className="cici-data-insight__panel cici-data-insight__panel--trend">
          <div className="cici-data-insight__panel-head">
            <h4>业绩趋势</h4>
            <span>管道 / 合同 / 订单</span>
          </div>
          <div className="cici-data-insight__trend">
            {dashboard.trend.map((item) => (
              <div key={item.month}>
                <span>{item.month}</span>
                <div>
                  <i style={{ blockSize: `${Math.max(8, (item.pipeline / maxTrend) * 100)}%` }} />
                  <i style={{ blockSize: `${Math.max(8, (item.contract / maxTrend) * 100)}%` }} />
                  <i style={{ blockSize: `${Math.max(8, (item.order / maxTrend) * 100)}%` }} />
                </div>
              </div>
            ))}
          </div>
        </section>

        <section className="cici-data-insight__panel cici-data-insight__panel--accounts">
          <div className="cici-data-insight__panel-head">
            <h4>重点客户</h4>
            <span>{dashboard.accounts.length} 条</span>
          </div>
          <div className="cici-data-insight__accounts">
            {dashboard.accounts.slice(0, 5).map((account) => (
              <article key={account.accountId}>
                <div>
                  <strong>{account.accountName}</strong>
                  <span>{account.industry} · {segmentLabel(account.segment)} · {account.stage}</span>
                </div>
                <dl>
                  <div><dt>管道</dt><dd>{compactMoney(account.pipelineAmount)}</dd></div>
                  <div><dt>合同</dt><dd>{compactMoney(account.contractAmount)}</dd></div>
                  <div><dt>健康</dt><dd>{account.healthScore}</dd></div>
                </dl>
              </article>
            ))}
          </div>
        </section>

        <section className="cici-data-insight__panel cici-data-insight__panel--risks">
          <div className="cici-data-insight__panel-head">
            <h4>风险与建议</h4>
            <span>{summary.recommendationCount} 条建议</span>
          </div>
          <ul className="cici-data-insight__risk-list">
            {dashboard.risks.slice(0, 4).map((risk) => (
              <li key={risk.accountId}>
                <strong>{risk.accountName}</strong>
                <span>{risk.summary}</span>
                <em>{risk.riskLevel === "HIGH" ? "高风险" : risk.riskLevel === "MEDIUM" ? "中风险" : "低风险"} · {risk.nextActionCount} 个动作</em>
              </li>
            ))}
          </ul>
        </section>
      </div>
      {error ? <p className="cici-data-insight__error">{error}</p> : null}
    </section>
  );
}

function Metric({ label, value, detail }: { label: string; value: string; detail: string }) {
  return (
    <div className="cici-data-insight__metric">
      <span>{label}</span>
      <strong>{value}</strong>
      <small>{detail}</small>
    </div>
  );
}

function mergeProjectPayload(
  current: CustomerInsightProject | null,
  nextProject: CustomerInsightProject | null,
  nextSection: CustomerInsightSection,
) {
  const base = nextProject ?? current;
  if (!base) return current;
  const sections = (current?.sections ?? base.sections ?? []).map((section) =>
    section.sectionCode === nextSection.sectionCode ? nextSection : section,
  );
  return { ...base, sections };
}
