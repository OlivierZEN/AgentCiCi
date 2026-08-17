import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Tenant,
  TenantOwnerResolution,
  TenantProvisionPayload,
  createTenant,
  fetchTenantList,
  jobLabel,
  readPlatformToken,
  resolveTenantOwner,
  statusLabel,
  tenantApplicationsPath,
} from "./platformTenantsShared";

type OwnerMode = "EXISTING" | "NEW";
type ProvisionStep = 1 | 2 | 3;

type ProvisionFormState = {
  tenantName: string;
  ownerSearch: string;
  ownerMobile: string;
  ownerDisplayName: string;
  ownerEmail: string;
  initialPassword: string;
  provisionNote: string;
};

const EMPTY_FORM: ProvisionFormState = {
  tenantName: "",
  ownerSearch: "",
  ownerMobile: "",
  ownerDisplayName: "",
  ownerEmail: "",
  initialPassword: "",
  provisionNote: "",
};

function newIdempotencyKey(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return `tenant-${crypto.randomUUID()}`;
  }
  return `tenant-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

function identityStatusLabel(status: TenantOwnerResolution["identityStatus"]): string {
  switch (status) {
    case "ACTIVE": return "统一身份已激活";
    case "BOUND": return "统一身份已绑定";
    case "PENDING_ACTIVATION": return "等待统一身份激活";
    case "NEEDS_RECONCILIATION": return "需要补齐统一身份";
    default: return "无需检查";
  }
}

function resolutionTone(resolution: TenantOwnerResolution["resolution"]): string {
  return resolution === "EXISTING_ACCOUNT" || resolution === "NEW_ACCOUNT" ? "success" : "danger";
}

function existingOwnerLookup(value: string) {
  const normalized = value.trim();
  if (/^U[0-9A-Z]{12}$/i.test(normalized)) return { ownerPublicId: normalized.toUpperCase() };
  if (/^1\d{10}$/.test(normalized)) return { ownerMobile: normalized };
  return { ownerEmail: normalized };
}

export default function PlatformTenantsPage() {
  const token = readPlatformToken();
  const navigate = useNavigate();
  const [tenants, setTenants] = useState<Tenant[]>([]);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [resolving, setResolving] = useState(false);
  const [provisionModalOpen, setProvisionModalOpen] = useState(false);
  const [step, setStep] = useState<ProvisionStep>(1);
  const [ownerMode, setOwnerMode] = useState<OwnerMode>("EXISTING");
  const [ownerResolution, setOwnerResolution] = useState<TenantOwnerResolution | null>(null);
  const [idempotencyKey, setIdempotencyKey] = useState("");
  const [form, setForm] = useState<ProvisionFormState>(EMPTY_FORM);

  useEffect(() => {
    if (!token) return;
    void loadTenants().catch((err) => setError(err instanceof Error ? err.message : "加载租户失败"));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  async function loadTenants() {
    setError("");
    setTenants(await fetchTenantList(token));
  }

  function openProvisionModal() {
    setForm(EMPTY_FORM);
    setError("");
    setStep(1);
    setOwnerMode("EXISTING");
    setOwnerResolution(null);
    setIdempotencyKey(newIdempotencyKey());
    setProvisionModalOpen(true);
  }

  function closeProvisionModal() {
    if (!busy && !resolving) setProvisionModalOpen(false);
  }

  function updateForm<K extends keyof ProvisionFormState>(key: K, value: ProvisionFormState[K]) {
    setForm((previous) => ({ ...previous, [key]: value }));
    if (["ownerSearch", "ownerMobile", "ownerEmail"].includes(key)) setOwnerResolution(null);
  }

  function changeOwnerMode(mode: OwnerMode) {
    setOwnerMode(mode);
    setOwnerResolution(null);
    setError("");
  }

  async function resolveOwner() {
    setResolving(true);
    setError("");
    try {
      const payload = ownerMode === "EXISTING"
        ? existingOwnerLookup(form.ownerSearch)
        : { ownerMobile: form.ownerMobile.trim(), ownerEmail: form.ownerEmail.trim() };
      setOwnerResolution(await resolveTenantOwner(payload));
    } catch (err) {
      setOwnerResolution(null);
      setError(err instanceof Error ? err.message : "识别 Owner 账号失败");
    } finally {
      setResolving(false);
    }
  }

  function canResolveOwner(): boolean {
    if (ownerMode === "EXISTING") {
      const search = form.ownerSearch.trim();
      return /^U[0-9A-Z]{12}$/i.test(search) || /^1\d{10}$/.test(search) || /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(search);
    }
    return /^1\d{10}$/.test(form.ownerMobile.trim()) && /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(form.ownerEmail.trim());
  }

  function canContinueOwner(): boolean {
    if (!ownerResolution) return false;
    return ownerMode === "EXISTING"
      ? ownerResolution.resolution === "EXISTING_ACCOUNT" && Boolean(ownerResolution.accountPublicId)
      : ownerResolution.resolution === "NEW_ACCOUNT"
        && Boolean(form.ownerDisplayName.trim())
        && (ownerResolution.unifiedIdentityEnabled || form.initialPassword.length >= 8);
  }

  async function submitProvision() {
    if (!ownerResolution) return;
    setBusy(true);
    setError("");
    try {
      const payload: TenantProvisionPayload = ownerMode === "EXISTING"
        ? {
          tenantName: form.tenantName.trim(),
          ownerMode,
          ownerAccountPublicId: ownerResolution.accountPublicId,
          provisionNote: form.provisionNote.trim() || null,
          idempotencyKey,
        }
        : {
          tenantName: form.tenantName.trim(),
          ownerMode,
          ownerMobile: form.ownerMobile.trim(),
          ownerDisplayName: form.ownerDisplayName.trim(),
          ownerEmail: form.ownerEmail.trim(),
          initialPassword: form.initialPassword || null,
          provisionNote: form.provisionNote.trim() || null,
          idempotencyKey,
        };
      const created = await createTenant(token, payload);
      setProvisionModalOpen(false);
      setMessage(created.ownerActivationRequired
        ? "新租户已开通，Owner 完成邮件中的统一账号激活后即可登录。"
        : created.reusedExistingAccount
          ? "新租户已开通，已复用并关联既有 Owner 统一账号。"
          : "新租户已开通。");
      const detailPath = tenantApplicationsPath(created.companyId);
      if (detailPath) {
        navigate(detailPath, { state: { flash: "新租户已开通" } });
      } else {
        await loadTenants();
        setError("新租户已开通，但未收到可用的租户标识。请从目录重新进入详情。");
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "开通新租户失败");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="admin-page skills-catalog platform-page platform-tenants-page">
      <header className="skills-catalog__header platform-page-head">
        <div className="platform-page-head__main">
          <h1 className="skills-catalog__title">租户生命周期</h1>
          <p className="subtle skills-catalog__subtitle">按租户进入独立治理页，处理保留策略、导出、预演与销毁动作。</p>
        </div>
        <div className="platform-page-head__aside">
          <span className="platform-inline-stat">租户 {tenants.length}</span>
          <span className="platform-inline-stat">冻结 {tenants.filter((tenant) => tenant.status === "SUSPENDED").length}</span>
          <button type="button" className="platform-button platform-button--primary" onClick={openProvisionModal}>开通新租户</button>
        </div>
      </header>

      {error && !provisionModalOpen ? <div className="platform-console__banner platform-console__banner--error">{error}</div> : null}
      {message ? <div className="platform-console__banner platform-console__banner--success">{message}</div> : null}

      <div className="tenant-lifecycle tenant-lifecycle--list">
        <section className="platform-console__panel skills-table-wrap tenant-lifecycle__list" aria-label="租户列表">
          <div className="tenant-lifecycle-list__panel-head"><p className="platform-section-label">租户目录</p></div>
          <table className="skills-data-table tenant-lifecycle__table">
            <colgroup><col className="tenant-lifecycle__col-org" /><col className="tenant-lifecycle__col-status" /><col className="tenant-lifecycle__col-members" /><col className="tenant-lifecycle__col-job" /></colgroup>
            <thead><tr><th>租户</th><th>状态</th><th>成员</th><th>最近生命周期记录</th></tr></thead>
            <tbody>
              {tenants.map((tenant) => {
                const detailPath = tenantApplicationsPath(tenant.companyId);
                return (
                  <tr key={tenant.companyId || tenant.name} tabIndex={detailPath ? 0 : -1} onClick={() => detailPath && navigate(detailPath)} onKeyDown={(event) => {
                    if (detailPath && (event.key === "Enter" || event.key === " ")) { event.preventDefault(); navigate(detailPath); }
                  }}>
                    <td><div className="skills-data-table__skill-name">{tenant.name}</div><div className="skills-data-table__skill-code">{tenant.companyId}</div></td>
                    <td><span className={`tenant-lifecycle__status tenant-lifecycle__status--${tenant.status.toLowerCase()}`}>{statusLabel(tenant.status)}</span></td>
                    <td className="skills-data-table__mono">{tenant.memberCount}</td>
                    <td className="skills-data-table__summary">{tenant.latestJob ? `${jobLabel(tenant.latestJob.status)} · ${tenant.latestJob.totalRows ?? 0} 行` : "尚未生成"}</td>
                  </tr>
                );
              })}
              {tenants.length === 0 ? <tr><td colSpan={4} className="skills-data-table__summary">当前还没有可管理租户。</td></tr> : null}
            </tbody>
          </table>
          <div className="tenant-lifecycle-mobile-list">
            {tenants.map((tenant) => {
              const detailPath = tenantApplicationsPath(tenant.companyId);
              return (
                <button
                  key={`${tenant.companyId || tenant.name}-mobile`}
                  type="button"
                  className="tenant-lifecycle-mobile-list__row"
                  onClick={() => detailPath && navigate(detailPath)}
                  disabled={!detailPath}
                >
                  <div className="tenant-lifecycle-mobile-list__head">
                    <div><div className="skills-data-table__skill-name">{tenant.name}</div><div className="skills-data-table__skill-code">{tenant.companyId}</div></div>
                    <span className={`tenant-lifecycle__status tenant-lifecycle__status--${tenant.status.toLowerCase()}`}>{statusLabel(tenant.status)}</span>
                  </div>
                  <div className="tenant-lifecycle-mobile-list__meta">
                    <div><span>成员</span><strong>{tenant.memberCount}</strong></div>
                    <div><span>最近记录</span><strong>{tenant.latestJob ? `${jobLabel(tenant.latestJob.status)} · ${tenant.latestJob.totalRows ?? 0} 行` : "尚未生成"}</strong></div>
                  </div>
                </button>
              );
            })}
            {tenants.length === 0 ? <p className="skills-data-table__summary">当前还没有可管理租户。</p> : null}
          </div>
        </section>
      </div>

      {provisionModalOpen ? (
        <div className="tenant-lifecycle__modal-backdrop platform-modal-scope" role="presentation" onMouseDown={closeProvisionModal}>
          <div className="tenant-lifecycle__modal tenant-lifecycle__modal--provision" role="dialog" aria-modal="true" aria-labelledby="tenant-provision-title" onMouseDown={(event) => event.stopPropagation()}>
            <div className="tenant-lifecycle__modal-head">
              <div><p className="platform-section-label">Tenant Provisioning</p><h2 id="tenant-provision-title" className="platform-console__heading">开通新租户</h2></div>
              <button type="button" className="tenant-lifecycle__modal-close" onClick={closeProvisionModal} aria-label="关闭">×</button>
            </div>

            <div className="tenant-provision__steps" aria-label="开通进度">
              {["租户信息", "Owner 身份", "确认开通"].map((label, index) => {
                const number = (index + 1) as ProvisionStep;
                return <div key={label} className={`tenant-provision__step ${step === number ? "is-current" : ""} ${step > number ? "is-complete" : ""}`}><span>{step > number ? "✓" : number}</span><strong>{label}</strong></div>;
              })}
            </div>

            <div className="tenant-lifecycle__modal-body tenant-provision__body">
              {error ? <div className="platform-console__banner platform-console__banner--error">{error}</div> : null}

              {step === 1 ? (
                <section className="tenant-provision__section">
                  <div className="tenant-provision__section-head"><h3>先建立租户信息</h3><p>Owner 身份将在下一步独立识别，不会因为手机号或邮箱已注册而直接失败。</p></div>
                  <div className="platform-console__form-grid tenant-lifecycle__provision-form">
                    <label className="tenant-lifecycle__field--full"><span>租户名称</span><input autoFocus value={form.tenantName} onChange={(event) => updateForm("tenantName", event.target.value)} placeholder="华东售后中心" /></label>
                    <label className="tenant-lifecycle__field--full"><span>平台开通备注</span><input value={form.provisionNote} onChange={(event) => updateForm("provisionNote", event.target.value)} placeholder="选填，例如：平台人工开通" /></label>
                  </div>
                </section>
              ) : null}

              {step === 2 ? (
                <section className="tenant-provision__section">
                  <div className="tenant-provision__section-head"><h3>确定 Owner 身份</h3><p>一个全局用户可以加入多个租户。复用已有用户时只新增 Owner 成员关系，不修改其登录资料。</p></div>
                  <div className="tenant-provision__mode" role="tablist" aria-label="Owner 身份类型">
                    <button type="button" role="tab" aria-selected={ownerMode === "EXISTING"} className={ownerMode === "EXISTING" ? "is-active" : ""} onClick={() => changeOwnerMode("EXISTING")}><strong>复用已有用户</strong><span>推荐 · 不重复创建统一账号</span></button>
                    <button type="button" role="tab" aria-selected={ownerMode === "NEW"} className={ownerMode === "NEW" ? "is-active" : ""} onClick={() => changeOwnerMode("NEW")}><strong>创建新用户</strong><span>仅用于尚未注册的自然人</span></button>
                  </div>

                  {ownerMode === "EXISTING" ? (
                    <div className="tenant-provision__lookup">
                      <label><span>手机号、邮箱或用户公共编号</span><div className="tenant-provision__lookup-line"><input autoFocus value={form.ownerSearch} onChange={(event) => updateForm("ownerSearch", event.target.value)} placeholder="精确输入已有用户标识" /><button type="button" className="platform-button platform-button--secondary" onClick={() => void resolveOwner()} disabled={resolving || !canResolveOwner()}>{resolving ? "识别中…" : "识别用户"}</button></div><small className="tenant-lifecycle__field-help">结果仅显示脱敏资料，确认后才会建立新租户 Owner 关系。</small></label>
                    </div>
                  ) : (
                    <div className="platform-console__form-grid tenant-lifecycle__provision-form">
                      <label><span>Owner 手机号</span><input autoFocus value={form.ownerMobile} onChange={(event) => updateForm("ownerMobile", event.target.value)} placeholder="13800138000" /></label>
                      <label><span>Owner 邮箱</span><input type="email" value={form.ownerEmail} onChange={(event) => updateForm("ownerEmail", event.target.value)} placeholder="zhangsan@example.com" /></label>
                      <label className="tenant-lifecycle__field--full"><span>Owner 显示名称</span><input value={form.ownerDisplayName} onChange={(event) => updateForm("ownerDisplayName", event.target.value)} placeholder="张三" /></label>
                      <div className="tenant-lifecycle__field--full tenant-provision__new-check"><p>创建前先检查手机号与邮箱是否已被全局账号使用。</p><button type="button" className="platform-button platform-button--secondary" onClick={() => void resolveOwner()} disabled={resolving || !canResolveOwner()}>{resolving ? "检查中…" : "检查身份"}</button></div>
                    </div>
                  )}

                  {ownerResolution ? (
                    <div className={`tenant-provision__resolution tenant-provision__resolution--${resolutionTone(ownerResolution.resolution)}`} role="status">
                      <div><strong>{ownerResolution.resolution === "EXISTING_ACCOUNT" ? "已识别到全局用户" : ownerResolution.resolution === "NEW_ACCOUNT" ? "可以创建新用户" : "当前不能继续"}</strong><p>{ownerResolution.message}</p></div>
                      {ownerResolution.resolution === "EXISTING_ACCOUNT" ? (
                        <dl><div><dt>用户</dt><dd>{ownerResolution.displayName}</dd></div><div><dt>手机号</dt><dd>{ownerResolution.maskedMobile}</dd></div><div><dt>邮箱</dt><dd>{ownerResolution.maskedEmail}</dd></div><div><dt>身份状态</dt><dd>{identityStatusLabel(ownerResolution.identityStatus)}</dd></div><div><dt>已加入租户</dt><dd>{ownerResolution.activeTenantCount} 个</dd></div><div><dt>公共编号</dt><dd><code>{ownerResolution.accountPublicId}</code></dd></div></dl>
                      ) : null}
                      {ownerMode === "NEW" && ownerResolution.resolution === "NEW_ACCOUNT" && !ownerResolution.unifiedIdentityEnabled ? <label className="tenant-provision__compat-password"><span>本地兼容密码</span><input type="password" value={form.initialPassword} onChange={(event) => updateForm("initialPassword", event.target.value)} placeholder="至少 8 位" /><small className="tenant-lifecycle__field-help">当前环境未启用统一认证，新用户必须设置本地兼容密码。</small></label> : null}
                      {ownerMode === "NEW" && ownerResolution.resolution === "EXISTING_ACCOUNT" ? <button type="button" className="platform-button platform-button--secondary" onClick={() => { setOwnerMode("EXISTING"); setForm((previous) => ({ ...previous, ownerSearch: ownerResolution.accountPublicId ?? "" })); }}>改为复用此用户</button> : null}
                    </div>
                  ) : null}
                </section>
              ) : null}

              {step === 3 && ownerResolution ? (
                <section className="tenant-provision__section">
                  <div className="tenant-provision__section-head"><h3>确认本次开通</h3><p>系统将以同一个幂等请求创建租户和 Owner 成员关系，重复提交不会重复建租户。</p></div>
                  <div className="tenant-provision__summary">
                    <div><span>租户</span><strong>{form.tenantName.trim()}</strong></div>
                    <div><span>Owner 方式</span><strong>{ownerMode === "EXISTING" ? "复用已有全局用户" : "创建新的统一账号"}</strong></div>
                    <div><span>Owner</span><strong>{ownerMode === "EXISTING" ? ownerResolution.displayName : form.ownerDisplayName.trim()}</strong></div>
                    <div><span>账号标识</span><strong>{ownerMode === "EXISTING" ? ownerResolution.accountPublicId : `${form.ownerMobile.trim()} · ${form.ownerEmail.trim()}`}</strong></div>
                    <div><span>开通后</span><strong>{ownerMode === "EXISTING" ? "新增 ACTIVE Owner 关系，不修改既有登录资料" : "创建账号并按统一身份状态完成激活"}</strong></div>
                    <div><span>备注</span><strong>{form.provisionNote.trim() || "无"}</strong></div>
                  </div>
                </section>
              ) : null}
            </div>

            <div className="tenant-lifecycle__modal-foot">
              <button type="button" className="platform-button platform-button--secondary" onClick={step === 1 ? closeProvisionModal : () => { setError(""); setStep((step - 1) as ProvisionStep); }} disabled={busy || resolving}>{step === 1 ? "取消" : "上一步"}</button>
              {step === 1 ? <button type="button" className="platform-button platform-button--primary" onClick={() => { setError(""); setStep(2); }} disabled={!form.tenantName.trim()}>下一步</button> : null}
              {step === 2 ? <button type="button" className="platform-button platform-button--primary" onClick={() => { setError(""); setStep(3); }} disabled={!canContinueOwner()}>下一步</button> : null}
              {step === 3 ? <button type="button" className="platform-button platform-button--primary" onClick={() => void submitProvision()} disabled={busy}>{busy ? "开通中…" : "确认开通"}</button> : null}
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}
