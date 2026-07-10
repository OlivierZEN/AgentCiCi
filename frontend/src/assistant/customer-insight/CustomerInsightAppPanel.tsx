import { FormEvent, useEffect, useMemo, useState } from "react";
import {
  createCustomerInsightProject,
  generateCustomerInsightFull,
  generateCustomerInsightSection,
  getCustomerInsightProject,
  listCustomerInsightProjects,
  refreshCustomerInsightSources,
  saveCustomerInsightSection,
} from "./customerInsightApi";
import { CustomerInsightModuleNav } from "./CustomerInsightModuleNav";
import { CustomerInsightReportPreview } from "./CustomerInsightReportPreview";
import { CustomerInsightSectionEditor } from "./CustomerInsightSectionEditor";
import { inputToText, sourceTypeLabel, textToInput } from "./customerInsightSections";
import type { CustomerInsightProject, CustomerInsightSection } from "./customerInsightTypes";

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
  title: "客户洞察",
  nameLabel: "客户名称",
  namePlaceholder: "输入客户名称",
  typeLabel: "行业",
  typePlaceholder: "可选",
  createButton: "新建洞察",
  recentTitle: "最近项目",
  loading: "正在加载客户洞察项目。",
  typeFallback: "待补充行业",
  empty: "还没有客户洞察项目。",
  workspaceFallbackTitle: "客户洞察",
  workspaceEmpty: "选择或新建客户项目开始分析",
  refresh: "刷新业务来源",
  starterTitle: "新建一个客户洞察项目",
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
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [generatingFull, setGeneratingFull] = useState(false);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      if (!token) return;
      setLoading(true);
      try {
        const rows = await listCustomerInsightProjects(token);
        if (cancelled) return;
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
