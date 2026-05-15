import ChatMarkdown from "../../components/ChatMarkdown";
import { compactDate, statusLabel, statusTone } from "./customerInsightSections";
import type { CustomerInsightJob, CustomerInsightProject, CustomerInsightSection, CustomerInsightSource } from "./customerInsightTypes";

type Props = {
  project: CustomerInsightProject | null;
  activeSection: CustomerInsightSection | null;
  sources: CustomerInsightSource[];
  jobs: CustomerInsightJob[];
  onGenerateFull: () => void;
  generatingFull: boolean;
};

export function CustomerInsightReportPreview({
  project,
  activeSection,
  sources,
  jobs,
  onGenerateFull,
  generatingFull,
}: Props) {
  if (!project) {
    return (
      <aside className="cici-customer-insight__preview" aria-label="客户洞察摘要">
        <p className="cici-customer-insight__empty">创建客户洞察项目后，这里会显示完整度、数据源和报告预览。</p>
      </aside>
    );
  }
  const report = project.sections?.find((section) => section.sectionCode === "report_preview");
  return (
    <aside className="cici-customer-insight__preview" aria-label="客户洞察摘要">
      <section className="cici-customer-insight__summary">
        <h3>{project.customerName}</h3>
        <dl>
          <div>
            <dt>行业</dt>
            <dd>{project.industry || "待补充"}</dd>
          </div>
          <div>
            <dt>来源</dt>
            <dd>{project.sourceType}</dd>
          </div>
          <div>
            <dt>完整度</dt>
            <dd>{project.completenessScore}%</dd>
          </div>
          <div>
            <dt>模块</dt>
            <dd>{project.generatedSectionCount}/{project.sectionCount}</dd>
          </div>
        </dl>
      </section>

      <section className="cici-customer-insight__side-section">
        <div className="cici-customer-insight__side-head">
          <h4>当前模块</h4>
          {activeSection ? <span className={`is-${statusTone(activeSection.status)}`}>{statusLabel(activeSection.status)}</span> : null}
        </div>
        <p>{activeSection?.description || "选择模块继续编辑。"}</p>
      </section>

      <section className="cici-customer-insight__side-section">
        <div className="cici-customer-insight__side-head">
          <h4>数据源</h4>
          <span>{sources.length}</span>
        </div>
        {sources.length ? (
          <ul className="cici-customer-insight__plain-list">
            {sources.slice(0, 4).map((source) => (
              <li key={source.id}>
                <strong>{source.sourceLabel}</strong>
                <span>{source.sourceType} · {compactDate(source.collectedAt)}</span>
              </li>
            ))}
          </ul>
        ) : (
          <p>未绑定系统数据，仍可用手工事实补充合同订单与服务记录。</p>
        )}
      </section>

      <section className="cici-customer-insight__side-section">
        <div className="cici-customer-insight__side-head">
          <h4>最近生成</h4>
          <span>{jobs.length}</span>
        </div>
        {jobs.length ? (
          <ul className="cici-customer-insight__plain-list">
            {jobs.slice(0, 4).map((job) => (
              <li key={job.id}>
                <strong>{job.requestSummary}</strong>
                <span>{statusLabel(job.status)} · {compactDate(job.createdAt)}</span>
              </li>
            ))}
          </ul>
        ) : (
          <p>尚未生成模块。</p>
        )}
      </section>

      <section className="cici-customer-insight__report">
        <div className="cici-customer-insight__side-head">
          <h4>报告预览</h4>
          <button type="button" onClick={onGenerateFull} disabled={generatingFull}>
            {generatingFull ? "汇总中" : "汇总"}
          </button>
        </div>
        {report?.markdown ? <ChatMarkdown content={report.markdown} /> : <p>生成整案后显示客户洞察报告。</p>}
      </section>
    </aside>
  );
}
