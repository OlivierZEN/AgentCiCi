import { useMemo, useState, type FormEvent } from "react";
import { Bot, Check, RefreshCw, Sparkles } from "lucide-react";
import type {
  OntologyCatalogView,
  OntologyProposalRecord,
  OntologyProposalRequest,
  OntologySourceView,
} from "./ontologyTypes";

export interface OntologyProposalPanelProps {
  sources: OntologySourceView[];
  catalog: OntologyCatalogView | null;
  proposals: OntologyProposalRecord[];
  activeProposal: OntologyProposalRecord | null;
  loading: boolean;
  busy: boolean;
  locked: boolean;
  error: string;
  generateDisabledReason: string;
  applyDisabledReason: string;
  onReload: () => void | Promise<void>;
  onSelect: (proposal: OntologyProposalRecord) => void;
  onGenerate: (request: OntologyProposalRequest) => void | Promise<void>;
  onApply: (proposalId: number) => void | Promise<void>;
  onContinueManually: () => void;
}

function proposalStatusLabel(status: OntologyProposalRecord["status"]): string {
  if (status === "READY") return "待审阅";
  if (status === "APPLIED") return "已应用";
  if (status === "FAILED") return "生成失败";
  return "生成中";
}

function DiffColumn({
  title,
  tone,
  items,
}: {
  title: string;
  tone: "added" | "changed" | "removed";
  items: string[];
}) {
  return (
    <div className={`ontology-proposal-diff is-${tone}`}>
      <div><strong>{title}</strong><span>{items.length}</span></div>
      {items.length === 0 ? <p>无</p> : (
        <ul>{items.map((item) => <li key={item}>{item}</li>)}</ul>
      )}
    </div>
  );
}

export default function OntologyProposalPanel({
  sources,
  catalog,
  proposals,
  activeProposal,
  loading,
  busy,
  locked,
  error,
  generateDisabledReason,
  applyDisabledReason,
  onReload,
  onSelect,
  onGenerate,
  onApply,
  onContinueManually,
}: OntologyProposalPanelProps) {
  const [instruction, setInstruction] = useState("");
  const [sourceId, setSourceId] = useState("");
  const [objectKey, setObjectKey] = useState("");

  const sourceObjects = useMemo(() => {
    const numericSourceId = Number(sourceId);
    return catalog?.objects.filter((object) => object.dataSourceId === numericSourceId) ?? [];
  }, [catalog, sourceId]);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const intent = instruction.trim();
    if (!intent || generateDisabledReason) return;
    const selectedObject = sourceObjects.find((object) => object.key === objectKey);
    void onGenerate({
      instruction: intent,
      mode: sourceId && objectKey ? "DATA_SOURCE_FIRST" : "DOMAIN_FIRST",
      selectedSources: sourceId && selectedObject ? [{
        dataSourceId: Number(sourceId),
        objectKey: selectedObject.key,
        fieldKeys: selectedObject.fields.map((field) => field.key),
      }] : [],
    });
  };

  if (loading) {
    return (
      <div className="ontology-panel-loading" role="status" aria-label="正在加载 AI 提案">
        <span />
        <span />
        <span />
      </div>
    );
  }

  return (
    <section className="ontology-proposal" aria-label="AI 建模提案">
      <header className="ontology-section-header">
        <div>
          <span>AI 建模助手</span>
          <h2>先审阅提案，再决定是否应用</h2>
          <p>AI 只能生成草稿差异，不能验证通过后自动发布，也不能替换当前线上版本。</p>
        </div>
        <button type="button" className="ontology-text-action" disabled={busy || locked} onClick={() => void onReload()}>
          <RefreshCw size={14} aria-hidden /> 刷新提案
        </button>
      </header>

      {error && (
        <div className="ontology-ai-diagnostic" role="alert">
          <Bot size={18} aria-hidden />
          <div>
            <strong>{locked ? "AI 操作需要处理" : "AI 暂时无法生成提案"}</strong>
            <p>{error}</p>
            <button type="button" className="ontology-text-action" onClick={onContinueManually}>继续手工编辑</button>
          </div>
        </div>
      )}

      <form className="ontology-proposal__composer" onSubmit={submit}>
        <label>
          <span>这次希望补充或调整什么？</span>
          <textarea
            rows={4}
            value={instruction}
            disabled={busy || locked}
            placeholder="例如：补充负责人对交付任务的责任关系，并增加按状态统计任务数量的指标。"
            onChange={(event) => setInstruction(event.target.value)}
          />
        </label>
        <div className="ontology-proposal__source-picker">
          <label>
            <span>参考数据来源（可选）</span>
            <select
              value={sourceId}
              disabled={busy || locked}
              onChange={(event) => {
                const nextSource = event.target.value;
                setSourceId(nextSource);
                const firstObject = catalog?.objects.find((object) => object.dataSourceId === Number(nextSource));
                setObjectKey(firstObject?.key ?? "");
              }}
            >
              <option value="">只根据业务意图</option>
              {sources.map((source) => <option key={source.id} value={source.id}>{source.name}</option>)}
            </select>
          </label>
          <label>
            <span>参考数据对象</span>
            <select value={objectKey} disabled={busy || locked || !sourceId} onChange={(event) => setObjectKey(event.target.value)}>
              <option value="">不指定</option>
              {sourceObjects.map((object) => <option key={object.key} value={object.key}>{object.name}</option>)}
            </select>
          </label>
          <button type="submit" className="cici-btn cici-btn--primary" disabled={busy || locked || !instruction.trim() || Boolean(generateDisabledReason)}>
            <Sparkles size={15} aria-hidden /> {busy ? "正在生成" : "生成可审阅提案"}
          </button>
        </div>
        {generateDisabledReason && <p className="ontology-inline-note">{generateDisabledReason}</p>}
      </form>

      <div className="ontology-proposal__workspace">
        <nav className="ontology-proposal__history" aria-label="提案历史">
          <div className="ontology-subsection-heading">
            <Bot size={16} aria-hidden />
            <div><strong>提案记录</strong><span>{proposals.length} 条</span></div>
          </div>
          {proposals.length === 0 && <p className="ontology-inline-empty">还没有 AI 提案，手工建模不受影响。</p>}
          {proposals.map((proposal) => (
            <button
              key={proposal.id}
              type="button"
              className={`ontology-proposal-row${activeProposal?.id === proposal.id ? " is-selected" : ""}`}
              onClick={() => onSelect(proposal)}
            >
              <span>提案 #{proposal.id}</span>
              <strong>{proposalStatusLabel(proposal.status)}</strong>
              <small>{new Date(proposal.updatedAt).toLocaleString("zh-CN", { hour12: false })}</small>
            </button>
          ))}
        </nav>

        <div className="ontology-proposal__detail">
          {!activeProposal && (
            <div className="ontology-proposal__empty" role="status">
              <Sparkles size={22} aria-hidden />
              <strong>选择一条提案查看差异</strong>
              <span>没有明确点击“应用到草稿”前，当前草稿不会变化。</span>
            </div>
          )}
          {activeProposal && activeProposal.status === "FAILED" && (
            <div className="ontology-ai-diagnostic" role="alert">
              <Bot size={18} aria-hidden />
              <div>
                <strong>{activeProposal.diagnosticCode || "AI_PROPOSAL_INVALID"}</strong>
                <p>{activeProposal.diagnosticMessage || "提案未通过结构或引用校验，请调整意图后重试。"}</p>
                <button type="button" className="ontology-text-action" onClick={onContinueManually}>继续手工编辑</button>
              </div>
            </div>
          )}
          {activeProposal?.diff && (
            <>
              <div className="ontology-proposal__detail-head">
                <div>
                  <span>基于草稿修订 {activeProposal.diff.baseRevision}</span>
                  <strong>提案差异</strong>
                </div>
                <span className={`ontology-proposal-status is-${activeProposal.status.toLowerCase()}`}>{proposalStatusLabel(activeProposal.status)}</span>
              </div>
              <div className="ontology-proposal__diff-grid">
                <DiffColumn title="新增" tone="added" items={activeProposal.diff.added} />
                <DiffColumn title="修改" tone="changed" items={activeProposal.diff.changed} />
                <DiffColumn title="移除" tone="removed" items={activeProposal.diff.removed} />
              </div>
              {activeProposal.validation.length > 0 && (
                <div className="ontology-proposal__validation">
                  <strong>提案校验</strong>
                  {activeProposal.validation.map((issue, index) => (
                    <p key={`${issue.path}-${index}`}><span>{issue.severity}</span>{issue.path} · {issue.message}</p>
                  ))}
                </div>
              )}
              <div className="ontology-proposal__actions">
                <span>{applyDisabledReason || "应用后仍只是草稿，需要重新校验并由人工发布。"}</span>
                <button
                  type="button"
                  className="cici-btn cici-btn--primary"
                  disabled={busy || locked || activeProposal.status !== "READY" || Boolean(applyDisabledReason)}
                  onClick={() => void onApply(activeProposal.id)}
                >
                  <Check size={15} aria-hidden /> {activeProposal.status === "APPLIED" ? "已应用到草稿" : "应用到草稿"}
                </button>
              </div>
            </>
          )}
        </div>
      </div>
    </section>
  );
}
