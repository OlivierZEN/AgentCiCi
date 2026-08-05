import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Check,
  Database,
  GitCompareArrows,
  RefreshCw,
  ShieldCheck,
  UploadCloud,
} from "lucide-react";
import { OntologyApiError, type OntologyApi } from "./ontologyApi";
import type {
  OntologyDocument,
  OntologySematticeBinding,
  OntologySematticeImportProposal,
  OntologySematticeOperation,
  SematticeMetadataApproval,
} from "./ontologyTypes";

interface OntologySematticePanelProps {
  api: OntologyApi;
  workspaceId: number;
  draftRevision: number;
  currentDocument: OntologyDocument;
  userId: string;
  blocked: boolean;
  onApplyImport: (candidate: OntologyDocument, expectedRevision: number) => Promise<void>;
  onBindingChange: (binding: OntologySematticeBinding) => void;
  onActivated: () => Promise<void>;
}

type LoadState = "loading" | "ready" | "error";

const SYNC_LABELS: Record<OntologySematticeBinding["syncStatus"], string> = {
  NOT_LINKED: "尚未绑定",
  LINKED: "已绑定",
  IN_SYNC: "版本一致",
  DRIFTED: "发现漂移",
  PUBLISHING: "正在发布",
  FAILED: "需要处理",
};

const OPERATION_LABELS: Record<OntologySematticeOperation["status"], string> = {
  COMPILING: "正在编译",
  VALIDATED: "候选版本已校验",
  APPROVAL_PENDING: "等待独立审批",
  APPROVED: "审批已通过",
  BACKFILLING: "正在回填",
  READY: "可以激活",
  ACTIVE: "已激活",
  FAILED: "执行失败",
  CANCELED: "已取消",
  ROLLED_BACK: "已回滚",
};

function operationKind(value: OntologySematticeOperation): string {
  return value.operationType === "INITIAL_PUBLISH" ? "首次发布" : "受控变更";
}

function riskLabel(value: string | null): string {
  if (value === "high") return "高风险";
  if (value === "medium") return "中风险";
  if (value === "low") return "低风险";
  return "待评估";
}

function timeLabel(value: string | null): string {
  if (!value) return "尚未检查";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "尚未检查";
  return new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(date);
}

function errorMessage(error: unknown): string {
  if (error instanceof OntologyApiError) {
    const known: Record<string, string> = {
      SEMATTICE_TENANT_NOT_PROVISIONED: "当前组织尚未开通 Semattice，请先完成组织级开通。",
      SEMATTICE_METADATA_DRIFTED: "远端元数据已变化，请先检查漂移并决定以哪一侧为准。",
      SEMATTICE_APPROVAL_REQUIRED: "候选版本还没有独立审批。",
      SEMATTICE_ONTOLOGY_OPERATION_REQUESTER_REQUIRED: "只有本次变更的发起人可以继续激活。",
      ONTOLOGY_REVISION_CONFLICT: "草稿修订已变化，请刷新后重新编译。",
      SEMATTICE_OBJECT_DELETION_NOT_SUPPORTED: "当前阶段不自动删除远端业务对象，请保留对象或先人工迁移。",
      SEMATTICE_FIELD_DELETION_REQUIRES_TOMBSTONE: "字段删除需要先完成兼容迁移，本次编译已安全停止。",
      SEMATTICE_RELATION_DELETION_NOT_SUPPORTED: "当前阶段不自动删除远端关系，请先人工迁移。",
    };
    return known[error.code] ?? "Semattice 运行治理操作未完成，请核对当前状态后重试。";
  }
  return "Semattice 运行治理操作未完成，请稍后重试。";
}

export default function OntologySematticePanel({
  api,
  workspaceId,
  draftRevision,
  currentDocument,
  userId,
  blocked,
  onApplyImport,
  onBindingChange,
  onActivated,
}: OntologySematticePanelProps) {
  const [loadState, setLoadState] = useState<LoadState>("loading");
  const [binding, setBinding] = useState<OntologySematticeBinding | null>(null);
  const [operation, setOperation] = useState<OntologySematticeOperation | null>(null);
  const [approvals, setApprovals] = useState<SematticeMetadataApproval[]>([]);
  const [proposal, setProposal] = useState<OntologySematticeImportProposal | null>(null);
  const [busy, setBusy] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const load = useCallback(async () => {
    setLoadState("loading");
    setError("");
    try {
      const [nextBinding, nextOperation, nextApprovals] = await Promise.all([
        api.getSematticeBinding(workspaceId),
        api.getLatestSematticeOperation(workspaceId),
        api.listSematticeMetadataApprovals(),
      ]);
      setBinding(nextBinding);
      setOperation(nextOperation);
      setApprovals(nextApprovals);
      onBindingChange(nextBinding);
      setLoadState("ready");
    } catch (cause) {
      setError(errorMessage(cause));
      setLoadState("error");
    }
  }, [api, onBindingChange, workspaceId]);

  useEffect(() => {
    void load();
  }, [load]);

  const run = async (label: string, action: () => Promise<void>) => {
    if (busy) return;
    setBusy(label);
    setError("");
    setNotice("");
    try {
      await action();
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setBusy("");
    }
  };

  const pendingApprovals = useMemo(
    () => approvals.filter((item) => item.state === "PENDING"),
    [approvals],
  );
  const operationIsCurrent = operation?.sourceRevision === draftRevision;
  const canRequestApproval = operationIsCurrent
    && operation?.status === "VALIDATED"
    && !operation.approvalRequestId;
  const canActivate = operationIsCurrent
    && Boolean(operation?.approvalRequestId)
    && ["APPROVAL_PENDING", "APPROVED", "BACKFILLING", "READY"].includes(operation?.status ?? "");
  const canCompile = binding?.syncStatus !== "NOT_LINKED"
    && !blocked
    && currentDocument.concepts.some((concept) => concept.enabled)
    && (!operationIsCurrent || ["ACTIVE", "FAILED", "CANCELED", "ROLLED_BACK"].includes(operation?.status ?? ""));

  if (loadState === "loading") {
    return (
      <section className="ontology-runtime" aria-busy="true" aria-label="正在载入运行治理状态">
        <div className="ontology-panel-loading" role="status"><span /><span /><span /></div>
      </section>
    );
  }

  return (
    <section className="ontology-runtime" aria-busy={Boolean(busy)}>
      <header className="ontology-section-header">
        <div>
          <span>发布账簿</span>
          <h2>Semattice 运行治理</h2>
          <p>草稿留在 AgentCiCi；已批准的对象、字段与关系进入 Semattice 运行元数据。</p>
        </div>
        <button type="button" className="ontology-text-action" disabled={Boolean(busy)} onClick={() => void load()}>
          <RefreshCw size={14} aria-hidden /> 刷新状态
        </button>
      </header>

      {error && <p className="ontology-runtime__message is-error" role="alert">{error}</p>}
      {notice && <p className="ontology-runtime__message is-success" role="status">{notice}</p>}

      {binding && (
        <div className="ontology-runtime__summary">
          <div>
            <span>连接状态</span>
            <strong className={`is-${binding.syncStatus.toLowerCase()}`}>{SYNC_LABELS[binding.syncStatus]}</strong>
          </div>
          <div><span>运行版本</span><strong>{binding.activeSequence == null ? "尚未发布" : `序列 ${binding.activeSequence}`}</strong></div>
          <div><span>稳定绑定</span><strong>{binding.boundElements} 个语义元素</strong></div>
          <div><span>最近核对</span><strong>{timeLabel(binding.lastCheckedAt)}</strong></div>
        </div>
      )}

      {binding?.syncStatus === "NOT_LINKED" ? (
        <div className="ontology-runtime__onboarding">
          <Database size={22} aria-hidden />
          <div>
            <strong>先连接当前业务本体与组织的 Semattice 租户</strong>
            <span>连接只建立稳定身份映射，不会发布草稿或覆盖远端元数据。</span>
          </div>
          <button type="button" className="cici-btn cici-btn--primary" disabled={Boolean(busy)} onClick={() => void run("建立连接", async () => {
            const next = await api.linkSemattice(workspaceId);
            setBinding(next);
            onBindingChange(next);
            setNotice("已建立连接，可以检查远端版本或编译当前草稿。");
          })}>建立连接</button>
        </div>
      ) : binding && (
        <>
          <section className="ontology-runtime__stage" aria-labelledby="ontology-runtime-read-title">
            <div className="ontology-runtime__stage-head">
              <div><span>阶段一</span><h3 id="ontology-runtime-read-title">读取、核对与导入提案</h3></div>
              <div>
                <button type="button" className="ontology-text-action" disabled={Boolean(busy) || !binding.activeMetadataVersionId} onClick={() => void run("检查漂移", async () => {
                  const next = await api.checkSematticeDrift(workspaceId);
                  setBinding(next);
                  onBindingChange(next);
                  setNotice(next.syncStatus === "DRIFTED" ? "发现远端变化，发布动作已停止。" : "当前绑定与远端运行版本一致。");
                })}><GitCompareArrows size={14} aria-hidden /> 检查漂移</button>
                <button type="button" className="ontology-text-action" disabled={Boolean(busy) || !binding.activeMetadataVersionId} onClick={() => void run("生成导入提案", async () => {
                  const next = await api.createSematticeImportProposal(workspaceId);
                  setProposal(next);
                  setNotice("已生成只读导入提案，当前草稿未改变。");
                })}><UploadCloud size={14} aria-hidden /> 生成导入提案</button>
              </div>
            </div>
            <p>{binding.activeMetadataVersionId ? "可读取当前已发布元数据并识别远端漂移；导入结果必须人工审阅后才会写入草稿。" : "远端尚无运行版本，可直接进入首次编译。"}</p>
            {proposal && (
              <div className="ontology-runtime__proposal">
                <div>
                  <strong>远端提案：{proposal.diff.objects} 个对象、{proposal.diff.fields} 个字段、{proposal.diff.relations} 条关系</strong>
                  <span>基于运行序列 {proposal.metadataSequence}，应用后仍是 AgentCiCi 草稿。</span>
                </div>
                <div className="ontology-runtime__proposal-list">
                  {proposal.candidate.concepts.map((concept) => (
                    <div key={concept.key}><strong>{concept.name}</strong><span>{concept.properties.map((property) => property.name).join("、") || "暂无字段"}</span></div>
                  ))}
                </div>
                <div className="ontology-runtime__proposal-actions">
                  <button type="button" className="ontology-text-action" disabled={Boolean(busy)} onClick={() => setProposal(null)}>放弃提案</button>
                  <button type="button" className="cici-btn cici-btn--primary" disabled={Boolean(busy) || blocked || proposal.expectedRevision !== draftRevision} onClick={() => void run("应用导入提案", async () => {
                    await onApplyImport(proposal.candidate, proposal.expectedRevision);
                    setProposal(null);
                    setNotice("导入提案已应用到草稿，请继续审阅并运行校验。");
                  })}>应用到草稿</button>
                </div>
              </div>
            )}
          </section>

          <section className="ontology-runtime__stage" aria-labelledby="ontology-runtime-publish-title">
            <div className="ontology-runtime__stage-head">
              <div><span>阶段二</span><h3 id="ontology-runtime-publish-title">编译、独立审批与激活</h3></div>
              <button type="button" className="cici-btn cici-btn--primary" disabled={Boolean(busy) || !canCompile} onClick={() => void run("编译候选版本", async () => {
                const next = await api.compileSemattice(workspaceId, draftRevision);
                setOperation(next);
                setNotice("候选版本已编译并完成 Semattice 变更校验。");
              })}>编译候选版本</button>
            </div>
            <p>{blocked ? "请先保存草稿和映射，再编译确定性运行契约。" : "每个草稿修订只生成一个幂等候选操作；删除等不兼容变化会安全停止。"}</p>

            {operation && (
              <div className="ontology-runtime__operation">
                <div className="ontology-runtime__operation-status">
                  <span>{operationKind(operation)}</span>
                  <strong>{OPERATION_LABELS[operation.status]}</strong>
                  <span>{riskLabel(operation.riskLevel)}{operation.requiresBackfill ? "，需要数据回填" : "，无需数据回填"}</span>
                  {!operationIsCurrent && <span className="is-warning">该操作基于修订 {operation.sourceRevision}，当前草稿为修订 {draftRevision}。</span>}
                </div>
                <div className="ontology-runtime__operation-actions">
                  <button type="button" className="ontology-text-action" disabled={Boolean(busy) || !canRequestApproval} onClick={() => void run("发起审批", async () => {
                    const next = await api.requestSematticeApproval(workspaceId, operation.operationId);
                    setOperation(next);
                    const nextApprovals = await api.listSematticeMetadataApprovals();
                    setApprovals(nextApprovals);
                    setNotice("审批请求已创建，需要另一位组织管理员批准。");
                  })}><ShieldCheck size={14} aria-hidden /> 发起独立审批</button>
                  <button type="button" className="cici-btn cici-btn--primary" disabled={Boolean(busy) || !canActivate} onClick={() => void run("激活运行版本", async () => {
                    const next = await api.activateSemattice(workspaceId, operation.operationId);
                    setOperation(next);
                    if (next.status === "BACKFILLING") {
                      setNotice("本次回填尚未完成，请稍后继续激活；已完成批次不会重复处理。");
                      return;
                    }
                    const nextBinding = await api.getSematticeBinding(workspaceId);
                    setBinding(nextBinding);
                    onBindingChange(nextBinding);
                    await onActivated();
                    setNotice("运行版本已激活，AgentCiCi 本体版本也已同步发布。");
                  })}><Check size={14} aria-hidden /> {operation.status === "BACKFILLING" ? "继续回填并激活" : "激活运行版本"}</button>
                </div>
              </div>
            )}
          </section>

          <section className="ontology-runtime__approvals" aria-labelledby="ontology-runtime-approval-title">
            <div className="ontology-runtime__stage-head">
              <div><span>组织控制</span><h3 id="ontology-runtime-approval-title">待审批的元数据变更</h3></div>
              <span>{pendingApprovals.length} 项待处理</span>
            </div>
            {pendingApprovals.length === 0 && <p>当前组织没有待审批的元数据变更。</p>}
            {pendingApprovals.map((approval) => {
              const requestedByCurrentUser = approval.requesterMemberId === userId;
              return (
                <div className="ontology-runtime__approval-row" key={approval.approvalId}>
                  <div><strong>{approval.summary}</strong><span>{requestedByCurrentUser ? "由你发起，必须由另一位组织管理员审批" : `创建于 ${timeLabel(approval.createdAt)}`}</span></div>
                  <button type="button" className="ontology-text-action" disabled={Boolean(busy) || requestedByCurrentUser} onClick={() => void run("批准变更", async () => {
                    await api.approveSematticeMetadata(approval.approvalId);
                    setApprovals((items) => items.filter((item) => item.approvalId !== approval.approvalId));
                    setNotice("审批已通过，原发起人可以继续激活运行版本。");
                  })}><ShieldCheck size={14} aria-hidden /> {requestedByCurrentUser ? "等待他人审批" : "批准"}</button>
                </div>
              );
            })}
          </section>
        </>
      )}
    </section>
  );
}
