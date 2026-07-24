import { useEffect, useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { PLATFORM_API_BASE } from "../../constants";
import { safeFetchJson } from "../../utils/http";
import {
  ExportJob,
  PurgeJob,
  TenantDetail,
  fetchTenantDetail,
  formatTs,
  fromDateInput,
  jobLabel,
  readPlatformToken,
  statusLabel,
  toDateInput,
} from "./platformTenantsShared";

type LocationState = {
  flash?: string;
} | null;

export default function PlatformTenantDetailPage() {
  const token = readPlatformToken();
  const navigate = useNavigate();
  const location = useLocation();
  const { companyId = "" } = useParams();
  const flashMessage = (location.state as LocationState)?.flash ?? "";
  const [detail, setDetail] = useState<TenantDetail | null>(null);
  const [form, setForm] = useState({
    graceUntil: "",
    suspendUntil: "",
    exportDeadline: "",
    purgeAfter: "",
    legalHold: false,
    policySource: "PLATFORM_MANUAL",
    legalHoldReason: "",
    legalHoldApprovedBy: "",
    legalHoldReviewAt: "",
  });
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [purgeModalOpen, setPurgeModalOpen] = useState(false);
  const [purgeMode, setPurgeMode] = useState<"execute" | "retry">("execute");
  const [retryJob, setRetryJob] = useState<PurgeJob | null>(null);
  const [purgeConfirmText, setPurgeConfirmText] = useState("");
  const [purgeReason, setPurgeReason] = useState("");

  const purgeJobs = detail?.jobs ?? [];
  const exportJobs = detail?.exportJobs ?? [];
  const latestManifest = purgeJobs.find((job) => job.manifest)?.manifest ?? null;
  const latestDryRun = purgeJobs.find((job) => job.dryRun && job.status === "SUCCEEDED") ?? null;
  const expectedPurgeText = companyId ? `PURGE ${companyId}` : "";
  const modalSourceDryRunId = purgeMode === "retry" ? retryJob?.sourceDryRunJobId : latestDryRun?.id;
  const modalSourceRows = purgeMode === "retry" ? retryJob?.totalRows : latestDryRun?.totalRows;
  const hasActiveRealPurge = purgeJobs.some((job) => !job.dryRun && ["QUEUED", "RUNNING"].includes(job.status));

  useEffect(() => {
    if (flashMessage) {
      setMessage(flashMessage);
    }
  }, [flashMessage]);

  useEffect(() => {
    if (!token || !companyId) return;
    void loadDetail().catch((err) => setError(err instanceof Error ? err.message : "加载租户详情失败"));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, companyId]);

  async function loadDetail() {
    const nextDetail = await fetchTenantDetail(token, companyId);
    setDetail(nextDetail);
    setForm({
      graceUntil: toDateInput(nextDetail.retention.graceUntil),
      suspendUntil: toDateInput(nextDetail.retention.suspendUntil),
      exportDeadline: toDateInput(nextDetail.retention.exportDeadline),
      purgeAfter: toDateInput(nextDetail.retention.purgeAfter),
      legalHold: nextDetail.retention.legalHold,
      policySource: nextDetail.retention.policySource || "PLATFORM_MANUAL",
      legalHoldReason: nextDetail.retention.legalHoldReason || "",
      legalHoldApprovedBy: nextDetail.retention.legalHoldApprovedBy || "",
      legalHoldReviewAt: toDateInput(nextDetail.retention.legalHoldReviewAt),
    });
  }

  function canRetryPurge(job: PurgeJob): boolean {
    return !job.dryRun && ["FAILED", "PARTIAL_FAILED"].includes(job.status) && Boolean(job.sourceDryRunJobId);
  }

  function canCancelPurge(job: PurgeJob): boolean {
    return !job.dryRun && job.status === "QUEUED";
  }

  function openExecutePurgeModal() {
    setPurgeMode("execute");
    setRetryJob(null);
    setPurgeConfirmText("");
    setPurgeReason("");
    setPurgeModalOpen(true);
  }

  function openRetryPurgeModal(job: PurgeJob) {
    setPurgeMode("retry");
    setRetryJob(job);
    setPurgeConfirmText("");
    setPurgeReason(job.reason ? `Retry after ${job.reason}` : `Retry purge job #${job.id}`);
    setPurgeModalOpen(true);
  }

  async function saveRetention() {
    if (!companyId) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const response = await fetch(`${PLATFORM_API_BASE}/tenants/${encodeURIComponent(companyId)}/retention`, {
        method: "PATCH",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          graceUntil: fromDateInput(form.graceUntil),
          suspendUntil: fromDateInput(form.suspendUntil),
          exportDeadline: fromDateInput(form.exportDeadline),
          purgeAfter: fromDateInput(form.purgeAfter),
          legalHold: form.legalHold,
          policySource: form.policySource || "PLATFORM_MANUAL",
          legalHoldReason: form.legalHoldReason || null,
          legalHoldApprovedBy: form.legalHoldApprovedBy || null,
          legalHoldApprovedAt: form.legalHold ? new Date().toISOString() : null,
          legalHoldReviewAt: fromDateInput(form.legalHoldReviewAt),
        }),
      });
      const { body } = await safeFetchJson<TenantDetail>(response);
      if (!response.ok || !body?.success || !body.data) {
        throw new Error(body?.message ?? `HTTP ${response.status}`);
      }
      setDetail(body.data);
      setMessage("保留策略已保存。");
    } catch (err) {
      setError(err instanceof Error ? err.message : "保存保留策略失败");
    } finally {
      setBusy(false);
    }
  }

  async function changeStatus(action: "suspend" | "resume") {
    if (!companyId) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const response = await fetch(`${PLATFORM_API_BASE}/tenants/${encodeURIComponent(companyId)}/${action}`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ reason: action === "suspend" ? "Platform lifecycle suspension" : "Platform lifecycle resume" }),
      });
      const { body } = await safeFetchJson(response);
      if (!response.ok || !body?.success) {
        throw new Error(body?.message ?? `HTTP ${response.status}`);
      }
      setMessage(action === "suspend" ? "租户已冻结。" : "租户已恢复。");
      await loadDetail();
    } catch (err) {
      setError(err instanceof Error ? err.message : "更新租户状态失败");
    } finally {
      setBusy(false);
    }
  }

  async function markPendingPurge() {
    if (!companyId) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const response = await fetch(`${PLATFORM_API_BASE}/tenants/${encodeURIComponent(companyId)}/pending-purge`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ reason: "Platform lifecycle pending purge" }),
      });
      const { body } = await safeFetchJson(response);
      if (!response.ok || !body?.success) {
        throw new Error(body?.message ?? `HTTP ${response.status}`);
      }
      setMessage("租户已进入待销毁。");
      await loadDetail();
    } catch (err) {
      setError(err instanceof Error ? err.message : "更新待销毁状态失败");
    } finally {
      setBusy(false);
    }
  }

  async function createExportJob() {
    if (!companyId) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const response = await fetch(`${PLATFORM_API_BASE}/tenants/${encodeURIComponent(companyId)}/export-jobs`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ reason: "Platform lifecycle export" }),
      });
      const { body } = await safeFetchJson<ExportJob>(response);
      if (!response.ok || !body?.success || !body.data) {
        throw new Error(body?.message ?? `HTTP ${response.status}`);
      }
      setMessage(body.data.status === "SUCCEEDED" ? "组织导出包已生成。" : "组织导出任务已记录，请查看任务状态。");
      await loadDetail();
    } catch (err) {
      setError(err instanceof Error ? err.message : "生成组织导出包失败");
    } finally {
      setBusy(false);
    }
  }

  async function createDryRun() {
    if (!companyId) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const response = await fetch(`${PLATFORM_API_BASE}/tenants/${encodeURIComponent(companyId)}/purge-jobs`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ dryRun: true, reason: "Platform dry-run manifest preview" }),
      });
      const { body } = await safeFetchJson<PurgeJob>(response);
      if (!response.ok || !body?.success || !body.data) {
        throw new Error(body?.message ?? `HTTP ${response.status}`);
      }
      setMessage(`预演清单已生成，共 ${body.data.totalRows ?? 0} 行候选记录。`);
      await loadDetail();
    } catch (err) {
      setError(err instanceof Error ? err.message : "生成预演清单失败");
    } finally {
      setBusy(false);
    }
  }

  async function submitPurgeConfirmation() {
    if (!companyId) return;
    if (purgeMode === "execute" && !latestDryRun) return;
    if (purgeMode === "retry" && !retryJob) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const endpoint = purgeMode === "retry"
        ? `${PLATFORM_API_BASE}/tenants/${encodeURIComponent(companyId)}/purge-jobs/${retryJob?.id}/retry`
        : `${PLATFORM_API_BASE}/tenants/${encodeURIComponent(companyId)}/purge-jobs`;
      const payload = purgeMode === "retry"
        ? {
            confirmationText: purgeConfirmText,
            reason: purgeReason || `Retry purge job #${retryJob?.id}`,
          }
        : {
            dryRun: false,
            sourceDryRunJobId: latestDryRun?.id,
            confirmationText: purgeConfirmText,
            reason: purgeReason || "Platform confirmed purge",
          };
      const response = await fetch(endpoint, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify(payload),
      });
      const { body } = await safeFetchJson<PurgeJob>(response);
      if (!response.ok || !body?.success || !body.data) {
        throw new Error(body?.message ?? `HTTP ${response.status}`);
      }
      setPurgeModalOpen(false);
      setRetryJob(null);
      setPurgeConfirmText("");
      setPurgeReason("");
      setMessage(body.data.status === "QUEUED" ? "真实销毁已排队，后台 worker 将执行。" : `真实销毁${jobLabel(body.data.status)}，请检查任务结果。`);
      await loadDetail();
    } catch (err) {
      setError(err instanceof Error ? err.message : (purgeMode === "retry" ? "重试真实销毁失败" : "执行真实销毁失败"));
    } finally {
      setBusy(false);
    }
  }

  async function cancelQueuedPurge(job: PurgeJob) {
    if (!companyId) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const response = await fetch(`${PLATFORM_API_BASE}/tenants/${encodeURIComponent(companyId)}/purge-jobs/${job.id}/cancel`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ reason: `Cancel queued purge job #${job.id}` }),
      });
      const { body } = await safeFetchJson<PurgeJob>(response);
      if (!response.ok || !body?.success || !body.data) {
        throw new Error(body?.message ?? `HTTP ${response.status}`);
      }
      setMessage("排队中的真实销毁已取消。");
      await loadDetail();
    } catch (err) {
      setError(err instanceof Error ? err.message : "取消真实销毁任务失败");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="admin-page skills-catalog platform-page platform-tenants-page">
      <header className="skills-catalog__header platform-page-head">
        <div className="platform-page-head__main">
          <h1 className="skills-catalog__title">AgentCiCi 应用生命周期</h1>
          <p className="subtle skills-catalog__subtitle">管理当前租户的 AgentCiCi 应用保留、导出、预演与真实销毁。</p>
        </div>
        <div className="platform-page-head__aside">
          <button type="button" className="platform-button platform-button--secondary" onClick={() => navigate(`/platform/tenants/${encodeURIComponent(companyId)}`)}>
            返回租户应用
          </button>
        </div>
      </header>

      {error ? <div className="platform-console__banner platform-console__banner--error">{error}</div> : null}
      {message ? <div className="platform-console__banner platform-console__banner--success">{message}</div> : null}

      <div className="tenant-lifecycle tenant-lifecycle--detail">
        <section className="platform-console__panel tenant-lifecycle__detail tenant-lifecycle-detail" aria-label="租户生命周期详情">
          {detail ? (
            <div className="platform-console__stack">
              <div className="platform-console__section tenant-lifecycle__detail-head">
                <div>
                  <p className="platform-section-label">当前租户</p>
                  <h2 className="platform-console__heading">{detail.tenant.name}</h2>
                  <p className="skills-data-table__summary">{detail.tenant.companyId}</p>
                </div>
                <div className="tenant-lifecycle__actions">
                  <button
                    type="button"
                    className="tenant-lifecycle__text-action tenant-lifecycle__text-action--danger"
                    onClick={() => void changeStatus("suspend")}
                    disabled={busy || detail.tenant.status === "SUSPENDED"}
                  >
                    冻结
                  </button>
                  <button
                    type="button"
                    className="tenant-lifecycle__text-action"
                    onClick={() => void changeStatus("resume")}
                    disabled={busy || detail.tenant.status === "ACTIVE"}
                  >
                    恢复
                  </button>
                  <button
                    type="button"
                    className="tenant-lifecycle__text-action tenant-lifecycle__text-action--danger"
                    onClick={() => void markPendingPurge()}
                    disabled={busy || detail.tenant.status === "PENDING_PURGE" || detail.tenant.status === "PURGED"}
                  >
                    待销毁
                  </button>
                  <button type="button" className="platform-button platform-button--secondary" onClick={createExportJob} disabled={busy}>
                    生成导出包
                  </button>
                  <button type="button" className="platform-button platform-button--primary" onClick={createDryRun} disabled={busy}>
                    生成预演清单
                  </button>
                  <button
                    type="button"
                    className="platform-button platform-button--danger"
                    onClick={openExecutePurgeModal}
                    disabled={busy || detail.tenant.status !== "PENDING_PURGE" || detail.retention.legalHold || !latestDryRun || hasActiveRealPurge}
                  >
                    真实销毁
                  </button>
                </div>
              </div>

              <div className="tenant-lifecycle__facts" aria-label="租户摘要">
                <div><span>状态</span><strong>{statusLabel(detail.tenant.status)}</strong></div>
                <div><span>成员</span><strong>{detail.tenant.memberCount}</strong></div>
                <div><span>最新清单</span><strong>{detail.tenant.latestJob ? jobLabel(detail.tenant.latestJob.status) : "无"}</strong></div>
                <div><span>候选记录</span><strong>{detail.tenant.latestJob?.totalRows ?? "—"}</strong></div>
              </div>

              <div className="platform-console__section">
                <h3 className="platform-console__subheading">保留策略</h3>
                <div className="platform-console__form-grid tenant-lifecycle__retention-form">
                  <label>
                    <span>宽限截止</span>
                    <input type="date" value={form.graceUntil} onChange={(event) => setForm((prev) => ({ ...prev, graceUntil: event.target.value }))} />
                  </label>
                  <label>
                    <span>冻结截止</span>
                    <input type="date" value={form.suspendUntil} onChange={(event) => setForm((prev) => ({ ...prev, suspendUntil: event.target.value }))} />
                  </label>
                  <label>
                    <span>导出截止</span>
                    <input type="date" value={form.exportDeadline} onChange={(event) => setForm((prev) => ({ ...prev, exportDeadline: event.target.value }))} />
                  </label>
                  <label>
                    <span>销毁候选日</span>
                    <input type="date" value={form.purgeAfter} onChange={(event) => setForm((prev) => ({ ...prev, purgeAfter: event.target.value }))} />
                  </label>
                  <label className="tenant-lifecycle__field--full">
                    <span>策略来源</span>
                    <input value={form.policySource} onChange={(event) => setForm((prev) => ({ ...prev, policySource: event.target.value }))} />
                  </label>
                  <label>
                    <span>法务保留审批人</span>
                    <input value={form.legalHoldApprovedBy} onChange={(event) => setForm((prev) => ({ ...prev, legalHoldApprovedBy: event.target.value }))} />
                  </label>
                  <label>
                    <span>法务保留复核日</span>
                    <input type="date" value={form.legalHoldReviewAt} onChange={(event) => setForm((prev) => ({ ...prev, legalHoldReviewAt: event.target.value }))} />
                  </label>
                  <label className="tenant-lifecycle__field--full">
                    <span>法务保留原因</span>
                    <input
                      value={form.legalHoldReason}
                      onChange={(event) => setForm((prev) => ({ ...prev, legalHoldReason: event.target.value }))}
                      placeholder="合同、法务或客户保留原因"
                    />
                  </label>
                  <label className="tenant-lifecycle__legal-hold">
                    <input type="checkbox" checked={form.legalHold} onChange={(event) => setForm((prev) => ({ ...prev, legalHold: event.target.checked }))} />
                    <span>开启法务保留，暂停自动销毁</span>
                  </label>
                </div>
                <div className="platform-console__actions">
                  <button type="button" className="platform-button platform-button--secondary" onClick={saveRetention} disabled={busy}>
                    保存策略
                  </button>
                  <span className="skills-data-table__summary">上次更新 {formatTs(detail.retention.updatedAt)}</span>
                </div>
              </div>

              <div className="platform-console__section">
                <h3 className="platform-console__subheading">组织导出</h3>
                <div className="skills-table-wrap tenant-lifecycle__inner-table">
                  <table className="skills-data-table">
                    <thead>
                      <tr>
                        <th>ID</th>
                        <th>状态</th>
                        <th>请求人</th>
                        <th>完成时间</th>
                      </tr>
                    </thead>
                    <tbody>
                      {exportJobs.map((job) => (
                        <tr key={job.id}>
                          <td className="skills-data-table__mono">#{job.id}</td>
                          <td>{jobLabel(job.status)}</td>
                          <td className="skills-data-table__mono">{job.requestedBy}</td>
                          <td className="skills-data-table__summary">{formatTs(job.finishedAt || job.createdAt)}</td>
                        </tr>
                      ))}
                      {exportJobs.length === 0 ? (
                        <tr>
                          <td colSpan={4} className="skills-data-table__summary">还没有导出记录。</td>
                        </tr>
                      ) : null}
                    </tbody>
                  </table>
                </div>
              </div>

              <div className="platform-console__section">
                <h3 className="platform-console__subheading">预演与销毁记录</h3>
                <div className="skills-table-wrap tenant-lifecycle__inner-table">
                  <table className="skills-data-table">
                    <thead>
                      <tr>
                        <th>ID</th>
                        <th>状态</th>
                        <th>候选记录</th>
                        <th>不支持域</th>
                        <th>操作</th>
                      </tr>
                    </thead>
                    <tbody>
                      {purgeJobs.map((job) => (
                        <tr key={job.id}>
                          <td className="skills-data-table__mono">#{job.id}</td>
                          <td>{job.dryRun ? "预演" : "真实销毁"} · {jobLabel(job.status)}</td>
                          <td className="skills-data-table__mono">{job.totalRows ?? "—"}</td>
                          <td className="skills-data-table__mono">{job.unsupportedCount ?? "—"}</td>
                          <td>
                            {canCancelPurge(job) ? (
                              <button
                                type="button"
                                className="tenant-lifecycle__text-action tenant-lifecycle__text-action--danger"
                                onClick={() => void cancelQueuedPurge(job)}
                                disabled={busy}
                              >
                                取消
                              </button>
                            ) : canRetryPurge(job) ? (
                              <button
                                type="button"
                                className="tenant-lifecycle__text-action tenant-lifecycle__text-action--danger"
                                onClick={() => openRetryPurgeModal(job)}
                                disabled={busy || detail.tenant.status !== "PENDING_PURGE" || detail.retention.legalHold || hasActiveRealPurge}
                              >
                                重试
                              </button>
                            ) : (
                              <span className="skills-data-table__summary">—</span>
                            )}
                          </td>
                        </tr>
                      ))}
                      {purgeJobs.length === 0 ? (
                        <tr>
                          <td colSpan={5} className="skills-data-table__summary">还没有预演或销毁记录。</td>
                        </tr>
                      ) : null}
                    </tbody>
                  </table>
                </div>
              </div>

              {latestManifest ? (
                <div className="platform-console__section">
                  <h3 className="platform-console__subheading">最近预演覆盖</h3>
                  <div className="skills-table-wrap tenant-lifecycle__inner-table">
                    <table className="skills-data-table">
                      <thead>
                        <tr>
                          <th>数据域</th>
                          <th>候选记录</th>
                          <th>表数量</th>
                        </tr>
                      </thead>
                      <tbody>
                        {latestManifest.domains.map((domain) => (
                          <tr key={domain.domain}>
                            <td>
                              <div className="skills-data-table__skill-name">{domain.label}</div>
                              <div className="skills-data-table__skill-code">{domain.domain}</div>
                            </td>
                            <td className="skills-data-table__mono">{domain.rows}</td>
                            <td className="skills-data-table__mono">{domain.tables?.length ?? 0}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  <div className="tenant-lifecycle__unsupported">
                    {latestManifest.unsupported.map((item) => (
                      <p key={item.domain}>
                        <strong>{item.label}</strong>
                        <span>{item.reason}</span>
                      </p>
                    ))}
                  </div>
                </div>
              ) : null}
            </div>
          ) : (
            <p className="skills-data-table__summary">正在加载租户生命周期详情。</p>
          )}
        </section>
      </div>

      {purgeModalOpen && detail ? (
        <div className="tenant-lifecycle__modal-backdrop" role="presentation">
          <div className="tenant-lifecycle__modal" role="dialog" aria-modal="true" aria-labelledby="tenant-purge-title">
            <div className="tenant-lifecycle__modal-head">
              <div>
                <p className="platform-section-label">{purgeMode === "retry" ? "重试销毁" : "真实销毁"}</p>
                <h2 id="tenant-purge-title" className="platform-console__heading">
                  {purgeMode === "retry" ? "重试真实销毁" : "确认真实销毁"}
                </h2>
              </div>
              <button
                type="button"
                className="tenant-lifecycle__modal-close"
                onClick={() => {
                  setPurgeModalOpen(false);
                  setRetryJob(null);
                }}
                aria-label="关闭"
              >
                ×
              </button>
            </div>
            <div className="tenant-lifecycle__modal-body">
              <p className="skills-data-table__summary">
                该动作会删除当前租户的业务数据、凭证、知识库记录、向量与已登记 KB 文件，只保留最小平台审计摘要。
              </p>
              <div className="tenant-lifecycle__confirm-line">
                <span>租户</span>
                <strong>{detail.tenant.name}</strong>
                <code>{detail.tenant.companyId}</code>
              </div>
              <div className="tenant-lifecycle__confirm-line">
                <span>来源预演</span>
                <strong>#{modalSourceDryRunId ?? "—"}</strong>
                <code>{modalSourceRows ?? 0} 行</code>
              </div>
              {purgeMode === "retry" && retryJob ? (
                <div className="tenant-lifecycle__confirm-line">
                  <span>重试任务</span>
                  <strong>#{retryJob.id} · {jobLabel(retryJob.status)}</strong>
                  <code>{retryJob.errorMessage || retryJob.reason || "failed"}</code>
                </div>
              ) : null}
                <div className="tenant-lifecycle__confirm-line">
                  <span>执行模式</span>
                  <strong>{purgeMode === "retry" ? "失败任务重试" : "首次真实销毁"}</strong>
                </div>
              <label>
                <span>销毁原因</span>
                <input value={purgeReason} onChange={(event) => setPurgeReason(event.target.value)} placeholder="客户确认导出后销毁" />
              </label>
              <label>
                <span>输入确认文本：{expectedPurgeText}</span>
                <input value={purgeConfirmText} onChange={(event) => setPurgeConfirmText(event.target.value)} placeholder={expectedPurgeText} />
              </label>
            </div>
            <div className="tenant-lifecycle__modal-foot">
              <button
                type="button"
                className="platform-button platform-button--secondary"
                onClick={() => {
                  setPurgeModalOpen(false);
                  setRetryJob(null);
                }}
              >
                取消
              </button>
              <button
                type="button"
                className="platform-button platform-button--danger"
                onClick={() => void submitPurgeConfirmation()}
                disabled={busy || purgeConfirmText !== expectedPurgeText || (purgeMode === "execute" ? !latestDryRun : !retryJob)}
              >
                {purgeMode === "retry" ? "确认重试" : "确认销毁"}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}
