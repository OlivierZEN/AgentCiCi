import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { AppWindow, ArrowLeft, GitBranch, Plus, Search, ShieldCheck, X } from "lucide-react";
import { useNavigate, useParams } from "react-router-dom";
import { PLATFORM_API_BASE } from "../../constants";
import { safeFetchJson } from "../../utils/http";
import { readPlatformToken } from "./platformTenantsShared";

export type InternalApplicationSummary = {
  appCode: string;
  displayName: string;
  summary: string;
  iconKey: string;
  ownerTeam: string;
  tenantMode: string;
  catalogStatus: string;
  trustedAppCode?: string | null;
  launchMode: string;
  launchRouteKey?: string | null;
  defaultVersion?: string | null;
  versionCount: number;
  createdAt: string;
  updatedAt: string;
};

type ApplicationDependency = {
  appCode: string;
  versionConstraint: string;
  dependencyType: string;
  activationPolicy: string;
};

type ApplicationStep = {
  code: string;
  type: string;
  capability: string;
  contractVersion: string;
};

type ApplicationVersion = {
  id: string;
  appCode: string;
  version: string;
  manifestSchemaVersion: string;
  providerBindingKey?: string | null;
  initializationEngine: string;
  manifestDigest: string;
  status: string;
  dependencies: ApplicationDependency[];
  createdBy: string;
  validatedBy?: string | null;
  validatedAt?: string | null;
  publishedBy?: string | null;
  publishedAt?: string | null;
  createdAt: string;
  updatedAt: string;
};

type InternalApplicationDetail = {
  application: InternalApplicationSummary;
  versions: ApplicationVersion[];
};

type ApplicationForm = {
  appCode: string;
  displayName: string;
  summary: string;
  iconKey: string;
  ownerTeam: string;
  tenantMode: string;
  trustedAppCode: string;
  launchMode: string;
  launchRouteKey: string;
};

type VersionForm = {
  version: string;
  providerBindingKey: string;
  initializationEngine: string;
  steps: ApplicationStep[];
  dependencies: ApplicationDependency[];
};

const EMPTY_APPLICATION: ApplicationForm = {
  appCode: "",
  displayName: "",
  summary: "",
  iconKey: "application",
  ownerTeam: "",
  tenantMode: "SHARED_RUNTIME_TENANT_ISOLATED",
  trustedAppCode: "",
  launchMode: "NONE",
  launchRouteKey: "",
};

const EMPTY_VERSION: VersionForm = {
  version: "1.0.0",
  providerBindingKey: "",
  initializationEngine: "NONE",
  steps: [],
  dependencies: [],
};

export function validApplicationCode(value: string): boolean {
  return /^[a-z][a-z0-9-]{1,63}$/.test(value.trim());
}

export function validSemanticVersion(value: string): boolean {
  return /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/.test(value.trim());
}

export function applicationCatalogStatusLabel(status: string): string {
  switch (status) {
    case "PUBLISHED": return "已发布";
    case "SUSPENDED": return "已暂停";
    case "RETIRED": return "已退役";
    default: return "草稿";
  }
}

export function versionStatusLabel(status: string): string {
  switch (status) {
    case "PUBLISHED": return "已发布";
    case "VALIDATED": return "已验证";
    case "DEPRECATED": return "已弃用";
    case "REVOKED": return "已撤销";
    default: return "草稿";
  }
}

function statusTone(status: string): string {
  if (status === "PUBLISHED") return "healthy";
  if (status === "SUSPENDED" || status === "DEPRECATED") return "pending";
  if (status === "RETIRED" || status === "REVOKED") return "danger";
  return "draft";
}

export default function PlatformInternalApplicationsPage() {
  const token = readPlatformToken();
  const navigate = useNavigate();
  const { appCode } = useParams();
  const [applications, setApplications] = useState<InternalApplicationSummary[]>([]);
  const [detail, setDetail] = useState<InternalApplicationDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("ALL");
  const [applicationModalOpen, setApplicationModalOpen] = useState(false);
  const [versionModalOpen, setVersionModalOpen] = useState(false);
  const [applicationForm, setApplicationForm] = useState<ApplicationForm>(EMPTY_APPLICATION);
  const [versionForm, setVersionForm] = useState<VersionForm>(EMPTY_VERSION);
  const [confirmation, setConfirmation] = useState<{ type: "publish" | "status"; version?: string; status?: string } | null>(null);
  const firstApplicationField = useRef<HTMLInputElement | null>(null);
  const firstVersionField = useRef<HTMLInputElement | null>(null);

  async function load() {
    if (!token) return;
    setLoading(true);
    setError("");
    try {
      if (appCode) {
        setDetail(await fetchApplicationDetail(token, appCode));
      } else {
        setApplications(await fetchInternalApplications(token));
        setDetail(null);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "应用中心加载失败");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void load(); }, [token, appCode]);

  useEffect(() => {
    if (applicationModalOpen) globalThis.requestAnimationFrame(() => firstApplicationField.current?.focus());
  }, [applicationModalOpen]);

  useEffect(() => {
    if (versionModalOpen) globalThis.requestAnimationFrame(() => firstVersionField.current?.focus());
  }, [versionModalOpen]);

  const filteredApplications = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    return applications.filter((application) => {
      const matchesStatus = status === "ALL" || application.catalogStatus === status;
      const matchesQuery = !keyword || [application.appCode, application.displayName, application.summary, application.ownerTeam]
        .some((value) => value.toLowerCase().includes(keyword));
      return matchesStatus && matchesQuery;
    });
  }, [applications, query, status]);

  function closeApplicationModal() {
    if (busy) return;
    setApplicationModalOpen(false);
    setApplicationForm(EMPTY_APPLICATION);
  }

  function closeVersionModal() {
    if (busy) return;
    setVersionModalOpen(false);
    setVersionForm(EMPTY_VERSION);
  }

  async function createApplication(event: FormEvent) {
    event.preventDefault();
    if (!token || !validApplicationCode(applicationForm.appCode)) return;
    setBusy(true); setError(""); setNotice("");
    try {
      const response = await fetch(`${PLATFORM_API_BASE}/internal-applications`, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({
          ...applicationForm,
          trustedAppCode: applicationForm.trustedAppCode.trim() || null,
          launchRouteKey: applicationForm.launchMode === "NONE" ? null : applicationForm.launchRouteKey.trim(),
        }),
      });
      const { body } = await safeFetchJson(response);
      if (!response.ok || !body?.success) throw new Error(body?.message ?? `HTTP ${response.status}`);
      closeApplicationModal();
      navigate(`/platform/internal-applications/${encodeURIComponent(applicationForm.appCode.trim())}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "应用草稿创建失败");
    } finally { setBusy(false); }
  }

  async function createVersion(event: FormEvent) {
    event.preventDefault();
    if (!token || !appCode || !validSemanticVersion(versionForm.version)) return;
    setBusy(true); setError(""); setNotice("");
    try {
      const response = await fetch(`${PLATFORM_API_BASE}/internal-applications/${encodeURIComponent(appCode)}/versions`, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({
          ...versionForm,
          providerBindingKey: versionForm.providerBindingKey.trim() || null,
        }),
      });
      const { body } = await safeFetchJson(response);
      if (!response.ok || !body?.success) throw new Error(body?.message ?? `HTTP ${response.status}`);
      setVersionModalOpen(false);
      setVersionForm(EMPTY_VERSION);
      setNotice(`版本 ${versionForm.version} 草稿已创建。请先验证，再正式发布。`);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "应用版本创建失败");
    } finally { setBusy(false); }
  }

  async function validateVersion(version: string) {
    if (!token || !appCode) return;
    setBusy(true); setError(""); setNotice("");
    try {
      const response = await fetch(`${PLATFORM_API_BASE}/internal-applications/${encodeURIComponent(appCode)}/versions/${encodeURIComponent(version)}/validations`, {
        method: "POST", headers: { Authorization: `Bearer ${token}` },
      });
      const { body } = await safeFetchJson(response);
      if (!response.ok || !body?.success) throw new Error(body?.message ?? `HTTP ${response.status}`);
      setNotice(`版本 ${version} 已通过清单、依赖和依赖图验证。`);
      await load();
    } catch (err) { setError(err instanceof Error ? err.message : "应用版本验证失败"); }
    finally { setBusy(false); }
  }

  async function executeConfirmation() {
    if (!token || !appCode || !confirmation) return;
    setBusy(true); setError(""); setNotice("");
    try {
      const endpoint = confirmation.type === "publish"
        ? `${PLATFORM_API_BASE}/internal-applications/${encodeURIComponent(appCode)}/versions/${encodeURIComponent(confirmation.version ?? "")}/publications`
        : `${PLATFORM_API_BASE}/internal-applications/${encodeURIComponent(appCode)}/status`;
      const response = await fetch(endpoint, {
        method: confirmation.type === "publish" ? "POST" : "PATCH",
        headers: { Authorization: `Bearer ${token}`, ...(confirmation.type === "status" ? { "Content-Type": "application/json" } : {}) },
        body: confirmation.type === "status" ? JSON.stringify({ status: confirmation.status }) : undefined,
      });
      const { body } = await safeFetchJson(response);
      if (!response.ok || !body?.success) throw new Error(body?.message ?? `HTTP ${response.status}`);
      setNotice(confirmation.type === "publish"
        ? `版本 ${confirmation.version} 已发布并成为默认目录版本。`
        : `应用目录状态已更新为${applicationCatalogStatusLabel(confirmation.status ?? "")}。`);
      setConfirmation(null);
      await load();
    } catch (err) { setError(err instanceof Error ? err.message : "治理操作失败"); }
    finally { setBusy(false); }
  }

  function addStep() {
    setVersionForm((current) => ({
      ...current,
      initializationEngine: "SAGA_V1",
      steps: [...current.steps, { code: "", type: "PROVIDER_CALLBACK", capability: "", contractVersion: "v1" }],
    }));
  }

  function addDependency() {
    setVersionForm((current) => ({
      ...current,
      dependencies: [...current.dependencies, { appCode: "", versionConstraint: ">=1.0.0", dependencyType: "REQUIRED_RUNTIME", activationPolicy: "REQUIRE_EXISTING" }],
    }));
  }

  if (appCode && detail) {
    return (
      <div className="admin-page skills-catalog platform-page internal-applications-page">
        <header className="skills-catalog__header platform-page-head">
          <div className="platform-page-head__main">
            <button type="button" className="system-api-back" onClick={() => navigate("/platform/internal-applications")}><ArrowLeft size={15} /> 应用中心</button>
            <h1 className="skills-catalog__title">{detail.application.displayName}</h1>
            <p className="subtle skills-catalog__subtitle">{detail.application.summary}</p>
          </div>
          <div className="platform-page-head__aside">
            <span className={`internal-application-status internal-application-status--${statusTone(detail.application.catalogStatus)}`}>{applicationCatalogStatusLabel(detail.application.catalogStatus)}</span>
            {detail.application.catalogStatus === "PUBLISHED" ? <button type="button" className="platform-button platform-button--secondary" onClick={() => setConfirmation({ type: "status", status: "SUSPENDED" })}>暂停目录</button> : detail.application.catalogStatus === "SUSPENDED" ? <button type="button" className="platform-button platform-button--primary" onClick={() => setConfirmation({ type: "status", status: "PUBLISHED" })}>恢复目录</button> : null}
            <button type="button" className="platform-button platform-button--primary" onClick={() => setVersionModalOpen(true)} disabled={detail.application.catalogStatus === "RETIRED"}><Plus size={15} />新建版本</button>
          </div>
        </header>

        {error ? <div className="platform-console__banner platform-console__banner--error">{error}</div> : null}
        {notice ? <div className="platform-console__banner platform-console__banner--success">{notice}</div> : null}

        <section className="platform-console__panel internal-application-detail" aria-label="应用治理详情">
          <dl className="internal-application-facts">
            <div><dt>应用代码</dt><dd><code>{detail.application.appCode}</code></dd></div>
            <div><dt>责任团队</dt><dd>{detail.application.ownerTeam}</dd></div>
            <div><dt>租户模式</dt><dd>{detail.application.tenantMode}</dd></div>
            <div><dt>默认版本</dt><dd>{detail.application.defaultVersion ?? "尚未发布"}</dd></div>
            <div><dt>入口方式</dt><dd>{detail.application.launchMode}</dd></div>
            <div><dt>逻辑入口</dt><dd>{detail.application.launchRouteKey ?? "不提供应用入口"}</dd></div>
          </dl>
        </section>

        <section className="platform-console__panel internal-application-versions" aria-labelledby="internal-application-versions-title">
          <div className="internal-applications-section-head">
            <div><p className="platform-section-label">不可变发布单元</p><h2 id="internal-application-versions-title" className="platform-console__heading">版本与依赖</h2></div>
            <span>{detail.versions.length} 个版本</span>
          </div>
          <table className="internal-applications-table">
            <colgroup><col className="internal-applications-table__version" /><col /><col className="internal-applications-table__dependency" /><col className="internal-applications-table__status" /><col className="internal-applications-table__actions" /></colgroup>
            <thead><tr><th>版本</th><th>初始化契约</th><th>依赖</th><th>状态</th><th><span className="sr-only">操作</span></th></tr></thead>
            <tbody>
              {detail.versions.map((version) => <tr key={version.id}>
                <td><strong>{version.version}</strong><small>{version.manifestDigest.slice(0, 12)}</small></td>
                <td><span>{version.initializationEngine}</span><small>{version.providerBindingKey ?? "平台内置，无 Provider 连接"}</small></td>
                <td>{version.dependencies.length ? version.dependencies.map((dependency) => <span key={dependency.appCode} className="internal-application-dependency"><GitBranch size={13} />{dependency.appCode} {dependency.versionConstraint}</span>) : <span className="subtle">无依赖</span>}</td>
                <td><span className={`internal-application-status internal-application-status--${statusTone(version.status)}`}>{versionStatusLabel(version.status)}</span></td>
                <td><div className="internal-application-row-actions">{version.status === "DRAFT" ? <button type="button" onClick={() => void validateVersion(version.version)} disabled={busy}>验证</button> : null}{version.status === "VALIDATED" ? <button type="button" onClick={() => setConfirmation({ type: "publish", version: version.version })} disabled={busy}>发布</button> : null}</div></td>
              </tr>)}
              {!detail.versions.length ? <tr><td colSpan={5} className="internal-applications-empty-row">尚未创建版本。应用只有发布有效版本后才会进入租户应用中心。</td></tr> : null}
            </tbody>
          </table>
        </section>

        {versionModalOpen ? createPortal(<VersionModal form={versionForm} setForm={setVersionForm} busy={busy} firstField={firstVersionField} onClose={closeVersionModal} onSubmit={createVersion} onAddStep={addStep} onAddDependency={addDependency} />, document.body) : null}
        {confirmation ? createPortal(<ConfirmationModal confirmation={confirmation} application={detail.application} busy={busy} onClose={() => !busy && setConfirmation(null)} onConfirm={() => void executeConfirmation()} />, document.body) : null}
      </div>
    );
  }

  return (
    <div className="admin-page skills-catalog platform-page internal-applications-page">
      <header className="skills-catalog__header platform-page-head">
        <div className="platform-page-head__main"><h1 className="skills-catalog__title">应用中心</h1><p className="subtle skills-catalog__subtitle">登记内部应用、发布不可变版本，并声明租户开通依赖与受控初始化契约。</p></div>
        <div className="platform-page-head__aside"><span className="platform-inline-stat">{applications.length} 个应用</span><button type="button" className="platform-button platform-button--primary" onClick={() => setApplicationModalOpen(true)}><Plus size={15} />登记应用</button></div>
      </header>
      {error ? <div className="platform-console__banner platform-console__banner--error">{error}</div> : null}
      {notice ? <div className="platform-console__banner platform-console__banner--success">{notice}</div> : null}
      <section className="platform-console__panel internal-applications-catalog" aria-label="内部应用中心">
        <div className="internal-applications-toolbar">
          <label className="internal-applications-search"><Search size={16} aria-hidden="true" /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="搜索应用名称、代码或责任团队" aria-label="搜索租户应用" /></label>
          <div className="internal-applications-status-tabs" role="tablist" aria-label="应用状态筛选">{["ALL", "PUBLISHED", "DRAFT", "SUSPENDED", "RETIRED"].map((item) => <button key={item} type="button" role="tab" aria-selected={status === item} className={status === item ? "is-active" : ""} onClick={() => setStatus(item)}>{item === "ALL" ? "全部" : applicationCatalogStatusLabel(item)}</button>)}</div>
        </div>
        <table className="internal-applications-table">
          <colgroup><col className="internal-applications-table__application" /><col className="internal-applications-table__owner" /><col className="internal-applications-table__version" /><col className="internal-applications-table__mode" /><col className="internal-applications-table__status" /><col className="internal-applications-table__actions" /></colgroup>
          <thead><tr><th>应用</th><th>责任团队</th><th>默认版本</th><th>租户模式</th><th>状态</th><th><span className="sr-only">操作</span></th></tr></thead>
          <tbody>
            {filteredApplications.map((application) => <tr key={application.appCode}>
              <td><button type="button" className="internal-application-name" onClick={() => navigate(`/platform/internal-applications/${encodeURIComponent(application.appCode)}`)}><span><AppWindow size={17} /></span><span><strong>{application.displayName}</strong><small>{application.appCode} · {application.summary}</small></span></button></td>
              <td>{application.ownerTeam}</td>
              <td>{application.defaultVersion ?? "未发布"}<small>{application.versionCount} 个版本</small></td>
              <td>{application.tenantMode === "PLATFORM_BASE" ? "平台基础应用" : "共享运行时 · 租户隔离"}</td>
              <td><span className={`internal-application-status internal-application-status--${statusTone(application.catalogStatus)}`}>{applicationCatalogStatusLabel(application.catalogStatus)}</span></td>
              <td><div className="internal-application-row-actions"><button type="button" onClick={() => navigate(`/platform/internal-applications/${encodeURIComponent(application.appCode)}`)}>查看</button></div></td>
            </tr>)}
            {!loading && !filteredApplications.length ? <tr><td colSpan={6} className="internal-applications-empty-row">{applications.length ? "没有符合当前筛选条件的应用。" : "尚未登记内部租户应用。创建草稿并发布版本后，应用才会进入租户应用中心。"}</td></tr> : null}
            {loading ? <tr><td colSpan={6} className="internal-applications-empty-row">正在读取受治理应用目录…</td></tr> : null}
          </tbody>
        </table>
      </section>
      {applicationModalOpen ? createPortal(<ApplicationModal form={applicationForm} setForm={setApplicationForm} busy={busy} firstField={firstApplicationField} onClose={closeApplicationModal} onSubmit={createApplication} />, document.body) : null}
    </div>
  );
}

function ApplicationModal({ form, setForm, busy, firstField, onClose, onSubmit }: { form: ApplicationForm; setForm: (value: ApplicationForm | ((current: ApplicationForm) => ApplicationForm)) => void; busy: boolean; firstField: React.RefObject<HTMLInputElement | null>; onClose: () => void; onSubmit: (event: FormEvent) => void }) {
  return <div className="tenant-lifecycle__modal-backdrop platform-modal-scope" role="presentation" onMouseDown={(event) => event.currentTarget === event.target && onClose()}><form className="tenant-lifecycle__modal internal-application-modal" role="dialog" aria-modal="true" aria-labelledby="internal-application-create-title" onSubmit={onSubmit}>
    <div className="tenant-lifecycle__modal-head"><div><p className="platform-section-label">受治理目录</p><h2 id="internal-application-create-title" className="platform-console__heading">登记内部租户应用</h2></div><button type="button" className="tenant-lifecycle__modal-close" onClick={onClose} disabled={busy} aria-label="关闭"><X size={17} /></button></div>
    <div className="tenant-lifecycle__modal-body internal-application-form-grid">
      <p className="internal-application-form-note"><ShieldCheck size={15} />这里只登记产品治理信息，不接收真实域名、Secret、SQL、脚本或任意回调 URL。</p>
      <label><span>应用代码</span><input ref={firstField} value={form.appCode} onChange={(event) => setForm((current) => ({ ...current, appCode: event.target.value.toLowerCase().replace(/\s+/g, "-") }))} placeholder="例如 sales-workbench" aria-invalid={form.appCode.length > 0 && !validApplicationCode(form.appCode)} required /><small>发布后不可修改，只允许小写字母、数字和连字符。</small></label>
      <label><span>应用名称</span><input value={form.displayName} onChange={(event) => setForm((current) => ({ ...current, displayName: event.target.value }))} maxLength={128} required /></label>
      <label className="internal-application-form-grid__full"><span>应用说明</span><textarea value={form.summary} onChange={(event) => setForm((current) => ({ ...current, summary: event.target.value }))} maxLength={500} rows={3} required /></label>
      <label><span>责任团队</span><input value={form.ownerTeam} onChange={(event) => setForm((current) => ({ ...current, ownerTeam: event.target.value }))} maxLength={128} required /></label>
      <label><span>图标语义</span><select value={form.iconKey} onChange={(event) => setForm((current) => ({ ...current, iconKey: event.target.value }))}><option value="application">应用</option><option value="workflow">工作流</option><option value="bot">智能体</option><option value="boxes">数据底座</option></select></label>
      <label><span>租户模式</span><select value={form.tenantMode} onChange={(event) => setForm((current) => ({ ...current, tenantMode: event.target.value }))}><option value="SHARED_RUNTIME_TENANT_ISOLATED">共享运行时 · 租户隔离</option><option value="PLATFORM_BASE">平台基础应用</option></select></label>
      <label><span>关联受信应用（可选）</span><input value={form.trustedAppCode} onChange={(event) => setForm((current) => ({ ...current, trustedAppCode: event.target.value.toLowerCase() }))} placeholder="Keycloak Client 治理记录" /></label>
      <label><span>入口方式</span><select value={form.launchMode} onChange={(event) => setForm((current) => ({ ...current, launchMode: event.target.value, launchRouteKey: event.target.value === "NONE" ? "" : current.launchRouteKey }))}><option value="NONE">不提供入口</option><option value="PLATFORM_ROUTE">平台相对路由</option><option value="SERVER_HANDOFF">服务端短时交接</option></select></label>
      {form.launchMode !== "NONE" ? <label><span>逻辑入口键</span><input value={form.launchRouteKey} onChange={(event) => setForm((current) => ({ ...current, launchRouteKey: event.target.value.toLowerCase() }))} placeholder="例如 sales-workbench.web" required /><small>由部署配置解析，不能填写域名或 URL。</small></label> : null}
    </div>
    <div className="tenant-lifecycle__modal-foot"><button type="button" className="platform-button platform-button--secondary" onClick={onClose} disabled={busy}>取消</button><button type="submit" className="platform-button platform-button--primary" disabled={busy || !validApplicationCode(form.appCode) || !form.displayName.trim() || !form.summary.trim() || !form.ownerTeam.trim()}>{busy ? "正在创建…" : "创建应用草稿"}</button></div>
  </form></div>;
}

function VersionModal({ form, setForm, busy, firstField, onClose, onSubmit, onAddStep, onAddDependency }: { form: VersionForm; setForm: React.Dispatch<React.SetStateAction<VersionForm>>; busy: boolean; firstField: React.RefObject<HTMLInputElement | null>; onClose: () => void; onSubmit: (event: FormEvent) => void; onAddStep: () => void; onAddDependency: () => void }) {
  return <div className="tenant-lifecycle__modal-backdrop platform-modal-scope" role="presentation" onMouseDown={(event) => event.currentTarget === event.target && onClose()}><form className="tenant-lifecycle__modal internal-application-version-modal" role="dialog" aria-modal="true" aria-labelledby="internal-application-version-title" onSubmit={onSubmit}>
    <div className="tenant-lifecycle__modal-head"><div><p className="platform-section-label">不可变发布单元</p><h2 id="internal-application-version-title" className="platform-console__heading">创建应用版本</h2></div><button type="button" className="tenant-lifecycle__modal-close" onClick={onClose} disabled={busy} aria-label="关闭"><X size={17} /></button></div>
    <div className="tenant-lifecycle__modal-body internal-application-version-form">
      <div className="internal-application-version-basics"><label><span>语义版本</span><input ref={firstField} value={form.version} onChange={(event) => setForm((current) => ({ ...current, version: event.target.value }))} placeholder="1.0.0" aria-invalid={!validSemanticVersion(form.version)} required /></label><label><span>初始化引擎</span><select value={form.initializationEngine} onChange={(event) => setForm((current) => ({ ...current, initializationEngine: event.target.value, steps: event.target.value === "NONE" ? [] : current.steps }))}><option value="NONE">NONE · 无初始化</option><option value="SAGA_V1">SAGA_V1 · 受控步骤</option></select></label><label><span>Provider 逻辑连接（可选）</span><input value={form.providerBindingKey} onChange={(event) => setForm((current) => ({ ...current, providerBindingKey: event.target.value.toLowerCase() }))} placeholder="例如 sales-workbench.lifecycle" /><small>只允许逻辑键，真实服务地址由部署配置持有。</small></label></div>
      <section className="internal-application-version-section"><div className="internal-application-version-section__head"><div><strong>初始化步骤</strong><span>仅允许平台能力、依赖能力和 Provider 标准回调。</span></div><button type="button" onClick={onAddStep} disabled={form.initializationEngine === "NONE"}><Plus size={14} />添加步骤</button></div>{form.steps.map((step, index) => <div className="internal-application-version-row internal-application-version-row--step" key={`${index}-${step.code}`}><input value={step.code} onChange={(event) => setForm((current) => ({ ...current, steps: current.steps.map((item, itemIndex) => itemIndex === index ? { ...item, code: event.target.value.toLowerCase() } : item) }))} placeholder="步骤代码" required /><select value={step.type} onChange={(event) => setForm((current) => ({ ...current, steps: current.steps.map((item, itemIndex) => itemIndex === index ? { ...item, type: event.target.value } : item) }))}><option value="PROVIDER_CALLBACK">Provider 回调</option><option value="PLATFORM_CAPABILITY">平台能力</option><option value="DEPENDENCY_CAPABILITY">依赖能力</option></select><input value={step.capability} onChange={(event) => setForm((current) => ({ ...current, steps: current.steps.map((item, itemIndex) => itemIndex === index ? { ...item, capability: event.target.value.toLowerCase() } : item) }))} placeholder="能力标识" required /><input value={step.contractVersion} onChange={(event) => setForm((current) => ({ ...current, steps: current.steps.map((item, itemIndex) => itemIndex === index ? { ...item, contractVersion: event.target.value.toLowerCase() } : item) }))} placeholder="v1" required /><button type="button" aria-label="删除步骤" onClick={() => setForm((current) => ({ ...current, steps: current.steps.filter((_, itemIndex) => itemIndex !== index) }))}><X size={15} /></button></div>)}{form.initializationEngine === "SAGA_V1" && !form.steps.length ? <p className="internal-application-version-empty">SAGA_V1 至少需要一个受控步骤。</p> : null}</section>
      <section className="internal-application-version-section"><div className="internal-application-version-section__head"><div><strong>应用依赖</strong><span>强依赖默认必须先开通，自动开通只声明允许，不会静默执行。</span></div><button type="button" onClick={onAddDependency}><Plus size={14} />添加依赖</button></div>{form.dependencies.map((dependency, index) => <div className="internal-application-version-row internal-application-version-row--dependency" key={`${index}-${dependency.appCode}`}><input value={dependency.appCode} onChange={(event) => setForm((current) => ({ ...current, dependencies: current.dependencies.map((item, itemIndex) => itemIndex === index ? { ...item, appCode: event.target.value.toLowerCase() } : item) }))} placeholder="依赖应用代码" required /><input value={dependency.versionConstraint} onChange={(event) => setForm((current) => ({ ...current, dependencies: current.dependencies.map((item, itemIndex) => itemIndex === index ? { ...item, versionConstraint: event.target.value } : item) }))} placeholder=">=1.0.0" required /><select value={dependency.dependencyType} onChange={(event) => setForm((current) => ({ ...current, dependencies: current.dependencies.map((item, itemIndex) => itemIndex === index ? { ...item, dependencyType: event.target.value } : item) }))}><option value="REQUIRED_RUNTIME">运行强依赖</option><option value="REQUIRED_ACTIVATION">开通强依赖</option><option value="OPTIONAL">可选依赖</option></select><select value={dependency.activationPolicy} onChange={(event) => setForm((current) => ({ ...current, dependencies: current.dependencies.map((item, itemIndex) => itemIndex === index ? { ...item, activationPolicy: event.target.value } : item) }))}><option value="REQUIRE_EXISTING">要求已开通</option><option value="AUTO_PROVISION_ALLOWED">允许联动计划</option></select><button type="button" aria-label="删除依赖" onClick={() => setForm((current) => ({ ...current, dependencies: current.dependencies.filter((_, itemIndex) => itemIndex !== index) }))}><X size={15} /></button></div>)}</section>
    </div>
    <div className="tenant-lifecycle__modal-foot"><button type="button" className="platform-button platform-button--secondary" onClick={onClose} disabled={busy}>取消</button><button type="submit" className="platform-button platform-button--primary" disabled={busy || !validSemanticVersion(form.version) || (form.initializationEngine === "SAGA_V1" && !form.steps.length)}>{busy ? "正在创建…" : "创建版本草稿"}</button></div>
  </form></div>;
}

function ConfirmationModal({ confirmation, application, busy, onClose, onConfirm }: { confirmation: { type: "publish" | "status"; version?: string; status?: string }; application: InternalApplicationSummary; busy: boolean; onClose: () => void; onConfirm: () => void }) {
  const publish = confirmation.type === "publish";
  return <div className="tenant-lifecycle__modal-backdrop platform-modal-scope" role="presentation" onMouseDown={(event) => event.currentTarget === event.target && onClose()}><div className="tenant-lifecycle__modal internal-application-confirmation" role="dialog" aria-modal="true" aria-labelledby="internal-application-confirmation-title"><div className="tenant-lifecycle__modal-head"><div><p className="platform-section-label">{publish ? "发布确认" : "目录状态"}</p><h2 id="internal-application-confirmation-title" className="platform-console__heading">{publish ? `发布 ${confirmation.version}` : confirmation.status === "SUSPENDED" ? "暂停应用目录" : "恢复应用目录"}</h2></div><button type="button" className="tenant-lifecycle__modal-close" onClick={onClose} disabled={busy} aria-label="关闭"><X size={17} /></button></div><div className="tenant-lifecycle__modal-body"><div className="internal-application-confirmation__subject"><AppWindow size={18} /><span><strong>{application.displayName}</strong><small>{application.appCode}</small></span></div><p>{publish ? "发布后该版本不可修改，并会成为新租户开通使用的默认版本。已有租户不会被自动升级。" : confirmation.status === "SUSPENDED" ? "暂停目录将阻止新租户开通，但不会删除或静默暂停现有租户数据。" : "恢复后，新租户可再次看到并按默认版本开通该应用。"}</p></div><div className="tenant-lifecycle__modal-foot"><button type="button" className="platform-button platform-button--secondary" onClick={onClose} disabled={busy}>取消</button><button type="button" className="platform-button platform-button--primary" onClick={onConfirm} disabled={busy}>{busy ? "正在处理…" : publish ? "确认发布" : "确认变更"}</button></div></div></div>;
}

async function fetchInternalApplications(token: string): Promise<InternalApplicationSummary[]> {
  const response = await fetch(`${PLATFORM_API_BASE}/internal-applications`, { headers: { Authorization: `Bearer ${token}` } });
  const { body } = await safeFetchJson(response);
  if (!response.ok || !body?.success || !Array.isArray(body.data)) throw new Error(body?.message ?? `HTTP ${response.status}`);
  return body.data as InternalApplicationSummary[];
}

async function fetchApplicationDetail(token: string, appCode: string): Promise<InternalApplicationDetail> {
  const response = await fetch(`${PLATFORM_API_BASE}/internal-applications/${encodeURIComponent(appCode)}`, { headers: { Authorization: `Bearer ${token}` } });
  const { body } = await safeFetchJson(response);
  if (!response.ok || !body?.success) throw new Error(body?.message ?? `HTTP ${response.status}`);
  return body.data as InternalApplicationDetail;
}
