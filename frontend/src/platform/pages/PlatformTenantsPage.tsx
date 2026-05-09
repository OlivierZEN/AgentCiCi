import { useEffect, useMemo, useState } from "react";
import { LS_PLATFORM_TOKEN, PLATFORM_API_BASE } from "../../constants";
import { safeFetchJson } from "../../utils/http";

type RetentionPolicy = {
  orgId: string;
  graceUntil?: string | null;
  suspendUntil?: string | null;
  exportDeadline?: string | null;
  purgeAfter?: string | null;
  legalHold: boolean;
  policySource: string;
  legalHoldReason?: string | null;
  legalHoldApprovedBy?: string | null;
  legalHoldApprovedAt?: string | null;
  legalHoldReviewAt?: string | null;
  updatedAt?: string | null;
};

type PurgeJob = {
  id: number;
  orgId: string;
  dryRun: boolean;
  status: string;
  phase: string;
  requestedBy: string;
  reason?: string | null;
  startedAt?: string | null;
  finishedAt?: string | null;
  totalRows?: number | null;
  unsupportedCount?: number | null;
  manifest?: PurgeManifest | null;
  result?: Record<string, unknown> | null;
  errorMessage?: string | null;
  sourceDryRunJobId?: number | null;
  manifestHash?: string | null;
  workerId?: string | null;
  lockExpiresAt?: string | null;
  attemptCount?: number | null;
  deadLetterAt?: string | null;
  createdAt?: string | null;
};

type ExportJob = {
  id: number;
  orgId: string;
  status: string;
  requestedBy: string;
  reason?: string | null;
  manifest?: Record<string, unknown> | null;
  errorMessage?: string | null;
  startedAt?: string | null;
  finishedAt?: string | null;
  createdAt?: string | null;
};

type Tenant = {
  orgId: string;
  name: string;
  status: string;
  memberCount: number;
  retention?: RetentionPolicy | null;
  latestJob?: PurgeJob | null;
};

type TenantDetail = {
  tenant: Tenant;
  retention: RetentionPolicy;
  jobs: PurgeJob[];
  exportJobs: ExportJob[];
};

type ManifestTable = {
  table: string;
  rows: number;
};

type ManifestDomain = {
  domain: string;
  label: string;
  rows: number;
  tables: ManifestTable[];
};

type PurgeManifest = {
  orgId: string;
  dryRun: boolean;
  generatedAt: string;
  totals: {
    rows: number;
    domains: number;
    unsupported: number;
  };
  domains: ManifestDomain[];
  unsupported: Array<{ domain: string; label: string; reason: string }>;
};

function readToken(): string {
  const raw = localStorage.getItem(LS_PLATFORM_TOKEN);
  if (!raw) return "";
  try {
    return (JSON.parse(raw) as { token?: string }).token ?? "";
  } catch {
    return "";
  }
}

function formatTs(ts?: string | null): string {
  if (!ts) return "—";
  const d = new Date(ts);
  if (Number.isNaN(d.getTime())) return ts;
  return d.toLocaleString();
}

function toDateInput(ts?: string | null): string {
  if (!ts) return "";
  const d = new Date(ts);
  if (Number.isNaN(d.getTime())) return "";
  return d.toISOString().slice(0, 10);
}

function fromDateInput(value: string): string | null {
  if (!value) return null;
  return `${value}T00:00:00Z`;
}

function statusLabel(status: string): string {
  switch (status.toUpperCase()) {
    case "ACTIVE":
      return "正常";
    case "SUSPENDED":
      return "已冻结";
    case "PAST_DUE":
      return "宽限";
    case "PENDING_PURGE":
      return "待销毁";
    case "PURGED":
      return "已销毁";
    default:
      return status || "未知";
  }
}

function jobLabel(status?: string | null): string {
  switch ((status ?? "").toUpperCase()) {
    case "SUCCEEDED":
      return "已完成";
    case "QUEUED":
      return "排队中";
    case "RUNNING":
      return "执行中";
    case "PARTIAL_FAILED":
      return "部分失败";
    case "FAILED":
      return "失败";
    case "CANCELED":
      return "已取消";
    case "DEAD_LETTER":
      return "死信";
    default:
      return status || "无记录";
  }
}

export default function PlatformTenantsPage() {
  const token = readToken();
  const [tenants, setTenants] = useState<Tenant[]>([]);
  const [selectedOrgId, setSelectedOrgId] = useState("");
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

  const selectedTenant = useMemo(
    () => tenants.find((tenant) => tenant.orgId === selectedOrgId) ?? null,
    [tenants, selectedOrgId],
  );

  const purgeJobs = detail?.jobs ?? [];
  const exportJobs = detail?.exportJobs ?? [];
  const latestManifest = purgeJobs.find((job) => job.manifest)?.manifest ?? null;
  const latestDryRun = purgeJobs.find((job) => job.dryRun && job.status === "SUCCEEDED") ?? null;
  const expectedPurgeText = selectedOrgId ? `PURGE ${selectedOrgId}` : "";
  const modalSourceDryRunId = purgeMode === "retry" ? retryJob?.sourceDryRunJobId : latestDryRun?.id;
  const modalSourceRows = purgeMode === "retry" ? retryJob?.totalRows : latestDryRun?.totalRows;
  const hasActiveRealPurge = purgeJobs.some((job) => !job.dryRun && ["QUEUED", "RUNNING"].includes(job.status));

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

  async function loadTenants(preferredOrgId?: string) {
    if (!token) return;
    setError("");
    const res = await fetch(`${PLATFORM_API_BASE}/tenants`, { headers: { Authorization: `Bearer ${token}` } });
    const { body } = await safeFetchJson<Tenant[]>(res);
    if (!res.ok || !body?.success) {
      throw new Error(body?.message ?? `HTTP ${res.status}`);
    }
    const rows = body.data ?? [];
    setTenants(rows);
    const nextOrgId = preferredOrgId || selectedOrgId || rows[0]?.orgId || "";
    setSelectedOrgId(nextOrgId);
    if (nextOrgId && rows.some((row) => row.orgId === nextOrgId)) {
      await loadDetail(nextOrgId);
    } else {
      setDetail(null);
    }
  }

  async function loadDetail(orgId: string) {
    if (!token || !orgId) return;
    setError("");
    const res = await fetch(`${PLATFORM_API_BASE}/tenants/${encodeURIComponent(orgId)}/retention`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    const { body } = await safeFetchJson<TenantDetail>(res);
    if (!res.ok || !body?.success || !body.data) {
      throw new Error(body?.message ?? `HTTP ${res.status}`);
    }
    setDetail(body.data);
    setForm({
      graceUntil: toDateInput(body.data.retention.graceUntil),
      suspendUntil: toDateInput(body.data.retention.suspendUntil),
      exportDeadline: toDateInput(body.data.retention.exportDeadline),
      purgeAfter: toDateInput(body.data.retention.purgeAfter),
      legalHold: body.data.retention.legalHold,
      policySource: body.data.retention.policySource || "PLATFORM_MANUAL",
      legalHoldReason: body.data.retention.legalHoldReason || "",
      legalHoldApprovedBy: body.data.retention.legalHoldApprovedBy || "",
      legalHoldReviewAt: toDateInput(body.data.retention.legalHoldReviewAt),
    });
  }

  useEffect(() => {
    void loadTenants().catch((err) => setError(err instanceof Error ? err.message : "加载租户失败"));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  async function selectTenant(orgId: string) {
    setSelectedOrgId(orgId);
    setMessage("");
    try {
      await loadDetail(orgId);
    } catch (err) {
      setError(err instanceof Error ? err.message : "加载租户详情失败");
    }
  }

  async function saveRetention() {
    if (!selectedOrgId) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const res = await fetch(`${PLATFORM_API_BASE}/tenants/${encodeURIComponent(selectedOrgId)}/retention`, {
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
      const { body } = await safeFetchJson<TenantDetail>(res);
      if (!res.ok || !body?.success || !body.data) {
        throw new Error(body?.message ?? `HTTP ${res.status}`);
      }
      setDetail(body.data);
      setMessage("保留策略已保存。");
      await loadTenants(selectedOrgId);
    } catch (err) {
      setError(err instanceof Error ? err.message : "保存保留策略失败");
    } finally {
      setBusy(false);
    }
  }

  async function changeStatus(action: "suspend" | "resume") {
    if (!selectedOrgId) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const res = await fetch(`${PLATFORM_API_BASE}/tenants/${encodeURIComponent(selectedOrgId)}/${action}`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ reason: action === "suspend" ? "Platform lifecycle suspension" : "Platform lifecycle resume" }),
      });
      const { body } = await safeFetchJson<Tenant>(res);
      if (!res.ok || !body?.success) {
        throw new Error(body?.message ?? `HTTP ${res.status}`);
      }
      setMessage(action === "suspend" ? "租户已冻结。" : "租户已恢复。");
      await loadTenants(selectedOrgId);
    } catch (err) {
      setError(err instanceof Error ? err.message : "更新租户状态失败");
    } finally {
      setBusy(false);
    }
  }

  async function markPendingPurge() {
    if (!selectedOrgId) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const res = await fetch(`${PLATFORM_API_BASE}/tenants/${encodeURIComponent(selectedOrgId)}/pending-purge`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ reason: "Platform lifecycle pending purge" }),
      });
      const { body } = await safeFetchJson<Tenant>(res);
      if (!res.ok || !body?.success) {
        throw new Error(body?.message ?? `HTTP ${res.status}`);
      }
      setMessage("租户已进入待销毁。");
      await loadTenants(selectedOrgId);
    } catch (err) {
      setError(err instanceof Error ? err.message : "更新待销毁状态失败");
    } finally {
      setBusy(false);
    }
  }

  async function createExportJob() {
    if (!selectedOrgId) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const res = await fetch(`${PLATFORM_API_BASE}/tenants/${encodeURIComponent(selectedOrgId)}/export-jobs`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ reason: "Platform lifecycle export" }),
      });
      const { body } = await safeFetchJson<ExportJob>(res);
      if (!res.ok || !body?.success || !body.data) {
        throw new Error(body?.message ?? `HTTP ${res.status}`);
      }
      setMessage(body.data.status === "SUCCEEDED" ? "组织导出包已生成。" : "组织导出任务已记录，请查看任务状态。");
      await loadDetail(selectedOrgId);
      await loadTenants(selectedOrgId);
    } catch (err) {
      setError(err instanceof Error ? err.message : "生成组织导出包失败");
    } finally {
      setBusy(false);
    }
  }

  async function createDryRun() {
    if (!selectedOrgId) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const res = await fetch(`${PLATFORM_API_BASE}/tenants/${encodeURIComponent(selectedOrgId)}/purge-jobs`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ dryRun: true, reason: "Platform dry-run manifest preview" }),
      });
      const { body } = await safeFetchJson<PurgeJob>(res);
      if (!res.ok || !body?.success || !body.data) {
        throw new Error(body?.message ?? `HTTP ${res.status}`);
      }
      setMessage(`Dry-run Manifest 已生成，共 ${body.data.totalRows ?? 0} 行候选记录。`);
      await loadDetail(selectedOrgId);
      await loadTenants(selectedOrgId);
    } catch (err) {
      setError(err instanceof Error ? err.message : "生成 Dry-run Manifest 失败");
    } finally {
      setBusy(false);
    }
  }

  async function submitPurgeConfirmation() {
    if (!selectedOrgId) return;
    if (purgeMode === "execute" && !latestDryRun) return;
    if (purgeMode === "retry" && !retryJob) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const endpoint = purgeMode === "retry"
        ? `${PLATFORM_API_BASE}/tenants/${encodeURIComponent(selectedOrgId)}/purge-jobs/${retryJob?.id}/retry`
        : `${PLATFORM_API_BASE}/tenants/${encodeURIComponent(selectedOrgId)}/purge-jobs`;
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
      const res = await fetch(endpoint, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify(payload),
      });
      const { body } = await safeFetchJson<PurgeJob>(res);
      if (!res.ok || !body?.success || !body.data) {
        throw new Error(body?.message ?? `HTTP ${res.status}`);
      }
      setPurgeModalOpen(false);
      setRetryJob(null);
      setPurgeConfirmText("");
      setPurgeReason("");
      setMessage(body.data.status === "QUEUED" ? "真实销毁已排队，后台 worker 将执行。" : `真实销毁${jobLabel(body.data.status)}，请检查任务结果。`);
      await loadDetail(selectedOrgId);
      await loadTenants(selectedOrgId);
    } catch (err) {
      setError(err instanceof Error ? err.message : (purgeMode === "retry" ? "重试真实销毁失败" : "执行真实销毁失败"));
    } finally {
      setBusy(false);
    }
  }

  async function cancelQueuedPurge(job: PurgeJob) {
    if (!selectedOrgId) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const res = await fetch(`${PLATFORM_API_BASE}/tenants/${encodeURIComponent(selectedOrgId)}/purge-jobs/${job.id}/cancel`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ reason: `Cancel queued purge job #${job.id}` }),
      });
      const { body } = await safeFetchJson<PurgeJob>(res);
      if (!res.ok || !body?.success || !body.data) {
        throw new Error(body?.message ?? `HTTP ${res.status}`);
      }
      setMessage("排队中的真实销毁已取消。");
      await loadDetail(selectedOrgId);
      await loadTenants(selectedOrgId);
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
          <p className="skills-catalog__kicker">Tenant Lifecycle</p>
          <h1 className="skills-catalog__title">租户生命周期</h1>
          <p className="subtle skills-catalog__subtitle">先用 dry-run manifest 证明数据域覆盖，再进入后续真实导出与销毁流程。</p>
        </div>
        <div className="platform-page-head__aside">
          <span className="platform-inline-stat">租户 {tenants.length}</span>
          <span className="platform-inline-stat">冻结 {tenants.filter((tenant) => tenant.status === "SUSPENDED").length}</span>
        </div>
      </header>

      {error ? <div className="platform-console__banner platform-console__banner--error">{error}</div> : null}
      {message ? <div className="platform-console__banner platform-console__banner--success">{message}</div> : null}

      <div className="tenant-lifecycle">
        <section className="platform-console__panel skills-table-wrap tenant-lifecycle__list" aria-label="租户列表">
          <table className="skills-data-table tenant-lifecycle__table">
            <colgroup>
              <col className="tenant-lifecycle__col-org" />
              <col className="tenant-lifecycle__col-status" />
              <col className="tenant-lifecycle__col-members" />
              <col className="tenant-lifecycle__col-job" />
            </colgroup>
            <thead>
              <tr>
                <th>租户</th>
                <th>状态</th>
                <th>成员</th>
                <th>最近清单</th>
              </tr>
            </thead>
            <tbody>
              {tenants.map((tenant) => (
                <tr
                  key={tenant.orgId}
                  tabIndex={0}
                  className={tenant.orgId === selectedOrgId ? "tenant-lifecycle__row--selected" : ""}
                  onClick={() => void selectTenant(tenant.orgId)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter" || event.key === " ") {
                      event.preventDefault();
                      void selectTenant(tenant.orgId);
                    }
                  }}
                >
                  <td>
                    <div className="skills-data-table__skill-name">{tenant.name}</div>
                    <div className="skills-data-table__skill-code">{tenant.orgId}</div>
                  </td>
                  <td>
                    <span className={`tenant-lifecycle__status tenant-lifecycle__status--${tenant.status.toLowerCase()}`}>
                      {statusLabel(tenant.status)}
                    </span>
                  </td>
                  <td className="skills-data-table__mono">{tenant.memberCount}</td>
                  <td className="skills-data-table__summary">
                    {tenant.latestJob ? `${jobLabel(tenant.latestJob.status)} · ${tenant.latestJob.totalRows ?? 0} 行` : "尚未生成"}
                  </td>
                </tr>
              ))}
              {tenants.length === 0 ? (
                <tr>
                  <td colSpan={4} className="skills-data-table__summary">当前还没有可管理租户。</td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </section>

        <section className="platform-console__panel tenant-lifecycle__detail" aria-label="租户生命周期详情">
          {selectedTenant && detail ? (
            <div className="platform-console__stack">
              <div className="platform-console__section tenant-lifecycle__detail-head">
                <div>
                  <p className="platform-section-label">当前租户</p>
                  <h2 className="platform-console__heading">{detail.tenant.name}</h2>
                  <p className="skills-data-table__summary">{detail.tenant.orgId}</p>
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
                    生成 Dry-run Manifest
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
                    <input
                      type="date"
                      value={form.graceUntil}
                      onChange={(event) => setForm((prev) => ({ ...prev, graceUntil: event.target.value }))}
                    />
                  </label>
                  <label>
                    <span>冻结截止</span>
                    <input
                      type="date"
                      value={form.suspendUntil}
                      onChange={(event) => setForm((prev) => ({ ...prev, suspendUntil: event.target.value }))}
                    />
                  </label>
                  <label>
                    <span>导出截止</span>
                    <input
                      type="date"
                      value={form.exportDeadline}
                      onChange={(event) => setForm((prev) => ({ ...prev, exportDeadline: event.target.value }))}
                    />
                  </label>
                  <label>
                    <span>销毁候选日</span>
                    <input
                      type="date"
                      value={form.purgeAfter}
                      onChange={(event) => setForm((prev) => ({ ...prev, purgeAfter: event.target.value }))}
                    />
                  </label>
                  <label className="tenant-lifecycle__field--full">
                    <span>策略来源</span>
                    <input
                      value={form.policySource}
                      onChange={(event) => setForm((prev) => ({ ...prev, policySource: event.target.value }))}
                    />
                  </label>
                  <label>
                    <span>Legal hold 审批人</span>
                    <input
                      value={form.legalHoldApprovedBy}
                      onChange={(event) => setForm((prev) => ({ ...prev, legalHoldApprovedBy: event.target.value }))}
                    />
                  </label>
                  <label>
                    <span>Legal hold 复核日</span>
                    <input
                      type="date"
                      value={form.legalHoldReviewAt}
                      onChange={(event) => setForm((prev) => ({ ...prev, legalHoldReviewAt: event.target.value }))}
                    />
                  </label>
                  <label className="tenant-lifecycle__field--full">
                    <span>Legal hold 原因</span>
                    <input
                      value={form.legalHoldReason}
                      onChange={(event) => setForm((prev) => ({ ...prev, legalHoldReason: event.target.value }))}
                      placeholder="合同、法务或客户保留原因"
                    />
                  </label>
                  <label className="tenant-lifecycle__legal-hold">
                    <input
                      type="checkbox"
                      checked={form.legalHold}
                      onChange={(event) => setForm((prev) => ({ ...prev, legalHold: event.target.checked }))}
                    />
                    <span>Legal hold，暂停自动销毁</span>
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
                <h3 className="platform-console__subheading">Dry-run 历史</h3>
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
                          <td>{job.dryRun ? "Dry-run" : "真实销毁"} · {jobLabel(job.status)}</td>
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
                          <td colSpan={5} className="skills-data-table__summary">还没有 dry-run 记录。</td>
                        </tr>
                      ) : null}
                    </tbody>
                  </table>
                </div>
              </div>

              {latestManifest ? (
                <div className="platform-console__section">
                  <h3 className="platform-console__subheading">最近 Manifest 覆盖</h3>
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
                        {(latestManifest.domains ?? []).map((domain) => (
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
                    {(latestManifest.unsupported ?? []).map((item) => (
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
            <p className="skills-data-table__summary">请选择一个租户查看生命周期策略。</p>
          )}
        </section>
      </div>

      {purgeModalOpen && detail ? (
        <div className="tenant-lifecycle__modal-backdrop" role="presentation">
          <div
            className="tenant-lifecycle__modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="tenant-purge-title"
          >
            <div className="tenant-lifecycle__modal-head">
              <div>
                <p className="platform-section-label">{purgeMode === "retry" ? "Retry purge" : "Real purge"}</p>
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
                <code>{detail.tenant.orgId}</code>
              </div>
              <div className="tenant-lifecycle__confirm-line">
                <span>来源 Dry-run</span>
                <strong>#{modalSourceDryRunId ?? "—"}</strong>
                <code>{modalSourceRows ?? 0} rows</code>
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
                <code>{purgeMode === "retry" ? "retry" : "execute"}</code>
              </div>
              <label>
                <span>销毁原因</span>
                <input
                  value={purgeReason}
                  onChange={(event) => setPurgeReason(event.target.value)}
                  placeholder="客户确认导出后销毁"
                />
              </label>
              <label>
                <span>输入确认文本：{expectedPurgeText}</span>
                <input
                  value={purgeConfirmText}
                  onChange={(event) => setPurgeConfirmText(event.target.value)}
                  placeholder={expectedPurgeText}
                />
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
