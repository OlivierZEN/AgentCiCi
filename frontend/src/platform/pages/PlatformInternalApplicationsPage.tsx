import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Activity, AppWindow, ArrowLeft, BookOpen, Cable, CheckCircle2, GitBranch, Plus, Search, ShieldCheck, X } from "lucide-react";
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

type ProviderConnectionRevision = {
  id: string;
  revisionNumber: number;
  baseUrl: string;
  contractVersion: string;
  authType: string;
  secretRef?: string | null;
  healthPath: string;
  activatePath?: string | null;
  reconcilePath?: string | null;
  suspendPath?: string | null;
  resumePath?: string | null;
  upgradePath?: string | null;
  timeoutMs: number;
  maxAttempts: number;
  testStatus: string;
  lastTestedAt?: string | null;
  lastTestHttpStatus?: number | null;
  lastTestLatencyMs?: number | null;
  lastTestErrorCode?: string | null;
};

type ProviderConnection = {
  bindingKey: string;
  appCode: string;
  displayName: string;
  environmentKey: string;
  networkScope: string;
  status: string;
  activeRevisionId?: string | null;
  revisions: ProviderConnectionRevision[];
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

type ConnectionForm = {
  bindingKey: string;
  displayName: string;
  environmentKey: string;
  networkScope: string;
  baseUrl: string;
  contractVersion: string;
  authType: string;
  secretRef: string;
  healthPath: string;
  activatePath: string;
  reconcilePath: string;
  suspendPath: string;
  resumePath: string;
  upgradePath: string;
  timeoutMs: number;
  maxAttempts: number;
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

const EMPTY_CONNECTION: ConnectionForm = {
  bindingKey: "",
  displayName: "",
  environmentKey: "default",
  networkScope: "PUBLIC_HTTPS",
  baseUrl: "",
  contractVersion: "v1",
  authType: "NONE",
  secretRef: "",
  healthPath: "/internal/tenant-lifecycle/v1/health",
  activatePath: "/internal/tenant-lifecycle/v1/activations",
  reconcilePath: "/internal/tenant-lifecycle/v1/reconciliations",
  suspendPath: "/internal/tenant-lifecycle/v1/suspensions",
  resumePath: "/internal/tenant-lifecycle/v1/resumptions",
  upgradePath: "/internal/tenant-lifecycle/v1/upgrades",
  timeoutMs: 10000,
  maxAttempts: 2,
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

export function providerConnectionStatusLabel(status: string): string {
  if (status === "ACTIVE") return "已启用";
  if (status === "DISABLED") return "已停用";
  return "草稿";
}

export function suggestedDependencyConstraint(defaultVersion?: string | null): string {
  return defaultVersion && validSemanticVersion(defaultVersion) ? `>=${defaultVersion}` : ">=1.0.0";
}

function statusTone(status: string): string {
  if (status === "PUBLISHED") return "healthy";
  if (status === "SUSPENDED" || status === "DEPRECATED") return "pending";
  if (status === "RETIRED" || status === "REVOKED") return "danger";
  return "draft";
}

function connectionRevisionToForm(connection: ProviderConnection, revision: ProviderConnectionRevision): ConnectionForm {
  return {
    bindingKey: connection.bindingKey,
    displayName: connection.displayName,
    environmentKey: connection.environmentKey,
    networkScope: connection.networkScope,
    baseUrl: revision.baseUrl,
    contractVersion: revision.contractVersion,
    authType: revision.authType,
    secretRef: revision.secretRef ?? "",
    healthPath: revision.healthPath,
    activatePath: revision.activatePath ?? "",
    reconcilePath: revision.reconcilePath ?? "",
    suspendPath: revision.suspendPath ?? "",
    resumePath: revision.resumePath ?? "",
    upgradePath: revision.upgradePath ?? "",
    timeoutMs: revision.timeoutMs,
    maxAttempts: revision.maxAttempts,
  };
}

export default function PlatformInternalApplicationsPage() {
  const token = readPlatformToken();
  const navigate = useNavigate();
  const { appCode } = useParams();
  const [applications, setApplications] = useState<InternalApplicationSummary[]>([]);
  const [detail, setDetail] = useState<InternalApplicationDetail | null>(null);
  const [connections, setConnections] = useState<ProviderConnection[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("ALL");
  const [applicationModalOpen, setApplicationModalOpen] = useState(false);
  const [versionModalOpen, setVersionModalOpen] = useState(false);
  const [connectionModalOpen, setConnectionModalOpen] = useState(false);
  const [workspaceTab, setWorkspaceTab] = useState<"versions" | "connections">("versions");
  const [applicationForm, setApplicationForm] = useState<ApplicationForm>(EMPTY_APPLICATION);
  const [versionForm, setVersionForm] = useState<VersionForm>(EMPTY_VERSION);
  const [connectionForm, setConnectionForm] = useState<ConnectionForm>(EMPTY_CONNECTION);
  const [confirmation, setConfirmation] = useState<{ type: "publish" | "status"; version?: string; status?: string } | null>(null);
  const firstApplicationField = useRef<HTMLInputElement | null>(null);
  const firstVersionField = useRef<HTMLInputElement | null>(null);
  const firstConnectionField = useRef<HTMLInputElement | null>(null);

  async function load() {
    if (!token) return;
    setLoading(true);
    setError("");
    try {
      if (appCode) {
        const [nextDetail, nextConnections, nextApplications] = await Promise.all([
          fetchApplicationDetail(token, appCode),
          fetchProviderConnections(token, appCode),
          fetchInternalApplications(token),
        ]);
        setDetail(nextDetail);
        setConnections(nextConnections);
        setApplications(nextApplications);
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

  useEffect(() => {
    if (connectionModalOpen) globalThis.requestAnimationFrame(() => firstConnectionField.current?.focus());
  }, [connectionModalOpen]);

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

  function closeConnectionModal() {
    if (busy) return;
    setConnectionModalOpen(false);
    setConnectionForm(EMPTY_CONNECTION);
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

  async function createConnectionRevision(event: FormEvent) {
    event.preventDefault();
    if (!token || !appCode || !connectionForm.bindingKey.trim() || !connectionForm.baseUrl.trim()) return;
    setBusy(true); setError(""); setNotice("");
    try {
      const response = await fetch(`${PLATFORM_API_BASE}/internal-applications/${encodeURIComponent(appCode)}/connections`, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({
          ...connectionForm,
          secretRef: connectionForm.authType === "NONE" ? null : connectionForm.secretRef.trim(),
          reconcilePath: connectionForm.reconcilePath.trim() || null,
          suspendPath: connectionForm.suspendPath.trim() || null,
          resumePath: connectionForm.resumePath.trim() || null,
          upgradePath: connectionForm.upgradePath.trim() || null,
        }),
      });
      const { body } = await safeFetchJson(response);
      if (!response.ok || !body?.success) throw new Error(body?.message ?? `HTTP ${response.status}`);
      closeConnectionModal();
      setWorkspaceTab("connections");
      setNotice(`运行连接 ${connectionForm.bindingKey} 已创建新修订，请测试后启用。`);
      await load();
    } catch (err) { setError(err instanceof Error ? err.message : "运行连接修订创建失败"); }
    finally { setBusy(false); }
  }

  async function runConnectionAction(bindingKey: string, action: "tests" | "activations" | "disabling") {
    if (!token || !appCode) return;
    setBusy(true); setError(""); setNotice("");
    try {
      const response = await fetch(`${PLATFORM_API_BASE}/internal-applications/${encodeURIComponent(appCode)}/connections/${encodeURIComponent(bindingKey)}/${action}`, {
        method: "POST", headers: { Authorization: `Bearer ${token}` },
      });
      const { body } = await safeFetchJson(response);
      if (!response.ok || !body?.success) throw new Error(body?.message ?? `HTTP ${response.status}`);
      const result = body.data as { status?: string; errorCode?: string } | undefined;
      if (action === "tests" && result?.status !== "PASSED") {
        throw new Error(result?.errorCode ?? "连接测试未通过");
      }
      setNotice(action === "tests" ? `运行连接 ${bindingKey} 测试通过。` : action === "activations" ? `运行连接 ${bindingKey} 已启用。` : `运行连接 ${bindingKey} 已停用。`);
      await load();
    } catch (err) { setError(err instanceof Error ? err.message : "运行连接操作失败"); }
    finally { setBusy(false); }
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
            {detail.application.launchRouteKey === "demo-example.page" ? <button type="button" className="platform-button platform-button--primary" onClick={() => navigate("/platform/internal-applications/demo-example/example")}><AppWindow size={15} />打开示例页</button> : null}
            {detail.application.catalogStatus === "PUBLISHED" ? <button type="button" className="platform-button platform-button--secondary" onClick={() => setConfirmation({ type: "status", status: "SUSPENDED" })}>暂停目录</button> : detail.application.catalogStatus === "SUSPENDED" ? <button type="button" className="platform-button platform-button--primary" onClick={() => setConfirmation({ type: "status", status: "PUBLISHED" })}>恢复目录</button> : null}
            <button type="button" className="platform-button platform-button--secondary" onClick={() => navigate(`/platform/internal-applications/integration-guide?app=${encodeURIComponent(detail.application.appCode)}`)}><BookOpen size={15} />接入指南</button>
            <button type="button" className="platform-button platform-button--secondary" onClick={() => setConnectionModalOpen(true)} disabled={detail.application.catalogStatus === "RETIRED"}><Cable size={15} />新建连接</button>
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

        <nav className="internal-application-workspace-tabs" aria-label="应用治理工作区">
          <button type="button" className={workspaceTab === "versions" ? "is-active" : ""} aria-current={workspaceTab === "versions" ? "page" : undefined} onClick={() => setWorkspaceTab("versions")}><GitBranch size={15} />版本与依赖</button>
          <button type="button" className={workspaceTab === "connections" ? "is-active" : ""} aria-current={workspaceTab === "connections" ? "page" : undefined} onClick={() => setWorkspaceTab("connections")}><Cable size={15} />运行连接 <span>{connections.length}</span></button>
        </nav>

        {workspaceTab === "versions" ? <section className="platform-console__panel internal-application-versions" aria-labelledby="internal-application-versions-title">
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
        </section> : null}

        {workspaceTab === "connections" ? <section className="platform-console__panel internal-application-connections" aria-labelledby="internal-application-connections-title">
          <div className="internal-applications-section-head">
            <div><p className="platform-section-label">部署拓扑控制面</p><h2 id="internal-application-connections-title" className="platform-console__heading">运行连接</h2><p className="subtle">真实地址只存在于受管连接修订；应用版本引用连接键，浏览器不会直接调用 Provider。</p></div>
            <button type="button" className="platform-button platform-button--primary" onClick={() => setConnectionModalOpen(true)}><Plus size={15} />新建连接修订</button>
          </div>
          <div className="internal-application-connection-list">
            {connections.map((connection) => {
              const latest = connection.revisions[0];
              const active = connection.revisions.find((revision) => revision.id === connection.activeRevisionId);
              return <article key={connection.bindingKey} className="internal-application-connection">
                <header>
                  <span className="internal-application-connection__icon"><Cable size={18} /></span>
                  <div><strong>{connection.displayName}</strong><code>{connection.bindingKey}</code></div>
                  <span className={`internal-application-status internal-application-status--${connection.status === "ACTIVE" ? "healthy" : connection.status === "DISABLED" ? "danger" : "draft"}`}>{providerConnectionStatusLabel(connection.status)}</span>
                </header>
                {latest ? <dl>
                  <div><dt>环境</dt><dd>{connection.environmentKey}</dd></div>
                  <div><dt>网络范围</dt><dd>{connection.networkScope === "PUBLIC_HTTPS" ? "公网 HTTPS" : "平台内部网络"}</dd></div>
                  <div><dt>当前修订</dt><dd>r{latest.revisionNumber}{active ? ` · 活动 r${active.revisionNumber}` : ""}</dd></div>
                  <div><dt>服务地址</dt><dd className="internal-application-connection__url" title={latest.baseUrl}>{latest.baseUrl}</dd></div>
                  <div><dt>生命周期契约</dt><dd>{latest.contractVersion}</dd></div>
                  <div><dt>鉴权</dt><dd>{latest.authType === "NONE" ? "无需鉴权" : `${latest.authType} · ${latest.secretRef}`}</dd></div>
                  <div><dt>连接测试</dt><dd>{latest.testStatus === "PASSED" ? <span className="internal-application-connection__test is-passed"><CheckCircle2 size={14} />通过 · {latest.lastTestLatencyMs ?? 0} ms</span> : latest.testStatus === "FAILED" ? <span className="internal-application-connection__test is-failed">失败 · {latest.lastTestErrorCode}</span> : "尚未测试"}</dd></div>
                  <div><dt>策略</dt><dd>{latest.timeoutMs / 1000}s 超时 · 最多 {latest.maxAttempts} 次</dd></div>
                </dl> : null}
                <footer>
                  <button type="button" className="platform-button platform-button--secondary" disabled={busy || !latest} onClick={() => {
                    if (!latest) return;
                    setConnectionForm(connectionRevisionToForm(connection, latest));
                    setConnectionModalOpen(true);
                  }}>创建新修订</button>
                  <button type="button" className="platform-button platform-button--secondary" disabled={busy || !latest} onClick={() => void runConnectionAction(connection.bindingKey, "tests")}><Activity size={14} />测试连接</button>
                  {latest?.testStatus === "PASSED" && connection.activeRevisionId !== latest.id ? <button type="button" className="platform-button platform-button--primary" disabled={busy} onClick={() => void runConnectionAction(connection.bindingKey, "activations")}>启用 r{latest.revisionNumber}</button> : null}
                  {connection.status === "ACTIVE" ? <button type="button" className="platform-button platform-button--secondary" disabled={busy} onClick={() => void runConnectionAction(connection.bindingKey, "disabling")}>停用连接</button> : null}
                </footer>
              </article>;
            })}
            {!connections.length ? <div className="internal-application-connection-empty"><Cable size={24} /><strong>尚未配置运行连接</strong><span>先登记 Provider 的真实地址和生命周期接口，测试通过并启用后，应用版本才能引用。</span><div className="internal-application-connection-empty__actions"><button type="button" className="platform-button platform-button--secondary" onClick={() => navigate(`/platform/internal-applications/integration-guide?app=${encodeURIComponent(detail.application.appCode)}#connection`)}><BookOpen size={15} />查看配置步骤</button><button type="button" className="platform-button platform-button--primary" onClick={() => setConnectionModalOpen(true)}>新建运行连接</button></div></div> : null}
          </div>
        </section> : null}

        {versionModalOpen ? createPortal(<VersionModal form={versionForm} setForm={setVersionForm} busy={busy} firstField={firstVersionField} connections={connections} applications={applications.filter((application) => application.appCode !== appCode && application.catalogStatus === "PUBLISHED")} onClose={closeVersionModal} onSubmit={createVersion} onAddStep={addStep} onAddDependency={addDependency} />, document.body) : null}
        {connectionModalOpen ? createPortal(<ConnectionModal form={connectionForm} setForm={setConnectionForm} busy={busy} firstField={firstConnectionField} onClose={closeConnectionModal} onSubmit={createConnectionRevision} />, document.body) : null}
        {confirmation ? createPortal(<ConfirmationModal confirmation={confirmation} application={detail.application} busy={busy} onClose={() => !busy && setConfirmation(null)} onConfirm={() => void executeConfirmation()} />, document.body) : null}
      </div>
    );
  }

  return (
    <div className="admin-page skills-catalog platform-page internal-applications-page">
      <header className="skills-catalog__header platform-page-head">
        <div className="platform-page-head__main"><h1 className="skills-catalog__title">应用中心</h1><p className="subtle skills-catalog__subtitle">登记内部应用、发布不可变版本，并声明租户开通依赖与受控初始化契约。</p></div>
        <div className="platform-page-head__aside"><span className="platform-inline-stat">{applications.length} 个应用</span><button type="button" className="platform-button platform-button--secondary" onClick={() => navigate("/platform/internal-applications/integration-guide")}><BookOpen size={15} />接入指南</button><button type="button" className="platform-button platform-button--primary" onClick={() => setApplicationModalOpen(true)}><Plus size={15} />登记应用</button></div>
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

function VersionModal({ form, setForm, busy, firstField, connections, applications, onClose, onSubmit, onAddStep, onAddDependency }: { form: VersionForm; setForm: React.Dispatch<React.SetStateAction<VersionForm>>; busy: boolean; firstField: React.RefObject<HTMLInputElement | null>; connections: ProviderConnection[]; applications: InternalApplicationSummary[]; onClose: () => void; onSubmit: (event: FormEvent) => void; onAddStep: () => void; onAddDependency: () => void }) {
  const activeConnections = connections.filter((connection) => connection.status === "ACTIVE" && connection.activeRevisionId);
  return <div className="tenant-lifecycle__modal-backdrop platform-modal-scope" role="presentation" onMouseDown={(event) => event.currentTarget === event.target && onClose()}><form className="tenant-lifecycle__modal internal-application-version-modal" role="dialog" aria-modal="true" aria-labelledby="internal-application-version-title" onSubmit={onSubmit}>
    <div className="tenant-lifecycle__modal-head"><div><p className="platform-section-label">不可变发布单元</p><h2 id="internal-application-version-title" className="platform-console__heading">创建应用版本</h2></div><button type="button" className="tenant-lifecycle__modal-close" onClick={onClose} disabled={busy} aria-label="关闭"><X size={17} /></button></div>
    <div className="tenant-lifecycle__modal-body internal-application-version-form">
      <div className="internal-application-version-basics">
        <label><span>语义版本</span><input ref={firstField} value={form.version} onChange={(event) => setForm((current) => ({ ...current, version: event.target.value }))} placeholder="1.0.0" aria-invalid={!validSemanticVersion(form.version)} required /></label>
        <label><span>初始化方式</span><select value={form.initializationEngine} onChange={(event) => setForm((current) => ({ ...current, initializationEngine: event.target.value, providerBindingKey: event.target.value === "NONE" ? "" : current.providerBindingKey, steps: event.target.value === "NONE" ? [] : current.steps }))}><option value="NONE">无需初始化</option><option value="SAGA_V1">标准 Provider 生命周期</option></select></label>
        <label><span>运行连接</span><select value={form.providerBindingKey} disabled={form.initializationEngine === "NONE"} required={form.initializationEngine === "SAGA_V1"} onChange={(event) => {
          const selected = activeConnections.find((connection) => connection.bindingKey === event.target.value);
          const revision = selected?.revisions.find((item) => item.id === selected.activeRevisionId);
          setForm((current) => ({ ...current, providerBindingKey: event.target.value, initializationEngine: event.target.value ? "SAGA_V1" : current.initializationEngine, steps: event.target.value && !current.steps.length ? [{ code: "activation", type: "PROVIDER_CALLBACK", capability: "tenant.activate", contractVersion: revision?.contractVersion ?? "v1" }] : current.steps.map((step) => ({ ...step, contractVersion: revision?.contractVersion ?? step.contractVersion })) }));
        }}><option value="">请选择已测试并启用的连接</option>{activeConnections.map((connection) => <option value={connection.bindingKey} key={connection.bindingKey}>{connection.displayName} · {connection.environmentKey}</option>)}</select><small>{activeConnections.length ? "版本只保存连接键；实际地址按当前环境的活动修订解析。" : "请先到“运行连接”创建、测试并启用连接。"}</small></label>
      </div>
      <section className="internal-application-version-section"><div className="internal-application-version-section__head"><div><strong>初始化步骤</strong><span>新应用只执行受管 Provider 回调；平台后端负责幂等、重试和审计。</span></div><button type="button" onClick={onAddStep} disabled={form.initializationEngine === "NONE" || !form.providerBindingKey}><Plus size={14} />添加步骤</button></div>
        {form.steps.length ? <div className="internal-application-version-row-head internal-application-version-row-head--step"><span>步骤代码</span><span>执行类型</span><span>能力标识</span><span>契约版本</span><span /></div> : null}
        {form.steps.map((step, index) => <div className="internal-application-version-row internal-application-version-row--step" key={`${index}-${step.code}`}><input aria-label={`步骤 ${index + 1} 代码`} value={step.code} onChange={(event) => setForm((current) => ({ ...current, steps: current.steps.map((item, itemIndex) => itemIndex === index ? { ...item, code: event.target.value.toLowerCase() } : item) }))} placeholder="activation" required /><select aria-label={`步骤 ${index + 1} 类型`} value={step.type} disabled><option value="PROVIDER_CALLBACK">Provider 回调</option></select><input aria-label={`步骤 ${index + 1} 能力标识`} value={step.capability} onChange={(event) => setForm((current) => ({ ...current, steps: current.steps.map((item, itemIndex) => itemIndex === index ? { ...item, capability: event.target.value.toLowerCase() } : item) }))} placeholder="tenant.activate" required /><input aria-label={`步骤 ${index + 1} 契约版本`} value={step.contractVersion} readOnly /><button type="button" aria-label="删除步骤" onClick={() => setForm((current) => ({ ...current, steps: current.steps.filter((_, itemIndex) => itemIndex !== index) }))}><X size={15} /></button></div>)}
        {form.initializationEngine === "SAGA_V1" && !form.steps.length ? <p className="internal-application-version-empty">请选择运行连接，并至少声明一个 Provider 回调步骤。</p> : null}
      </section>
      <section className="internal-application-version-section"><div className="internal-application-version-section__head"><div><strong>应用依赖</strong><span>从已发布应用中选择；强依赖在开通前必须处于 ACTIVE。</span></div><button type="button" onClick={onAddDependency} disabled={!applications.length}><Plus size={14} />添加依赖</button></div>
        {form.dependencies.length ? <div className="internal-application-version-row-head internal-application-version-row-head--dependency"><span>依赖应用</span><span>版本约束</span><span>依赖类型</span><span>开通策略</span><span /></div> : null}
        {form.dependencies.map((dependency, index) => <div className="internal-application-version-row internal-application-version-row--dependency" key={`${index}-${dependency.appCode}`}><select aria-label={`依赖 ${index + 1} 应用`} value={dependency.appCode} required onChange={(event) => {
          const selected = applications.find((application) => application.appCode === event.target.value);
          setForm((current) => ({ ...current, dependencies: current.dependencies.map((item, itemIndex) => itemIndex === index ? { ...item, appCode: event.target.value, versionConstraint: selected ? suggestedDependencyConstraint(selected.defaultVersion) : item.versionConstraint } : item) }));
        }}><option value="">请选择依赖应用</option>{applications.map((application) => <option value={application.appCode} key={application.appCode} disabled={form.dependencies.some((item, itemIndex) => itemIndex !== index && item.appCode === application.appCode)}>{application.displayName} · {application.appCode} · {application.defaultVersion}</option>)}</select><input aria-label={`依赖 ${index + 1} 版本约束`} value={dependency.versionConstraint} onChange={(event) => setForm((current) => ({ ...current, dependencies: current.dependencies.map((item, itemIndex) => itemIndex === index ? { ...item, versionConstraint: event.target.value } : item) }))} placeholder=">=1.0.0" required /><select aria-label={`依赖 ${index + 1} 类型`} value={dependency.dependencyType} onChange={(event) => setForm((current) => ({ ...current, dependencies: current.dependencies.map((item, itemIndex) => itemIndex === index ? { ...item, dependencyType: event.target.value } : item) }))}><option value="REQUIRED_RUNTIME">运行强依赖</option><option value="REQUIRED_ACTIVATION">开通强依赖</option><option value="OPTIONAL">可选依赖</option></select><select aria-label={`依赖 ${index + 1} 开通策略`} value={dependency.activationPolicy} onChange={(event) => setForm((current) => ({ ...current, dependencies: current.dependencies.map((item, itemIndex) => itemIndex === index ? { ...item, activationPolicy: event.target.value } : item) }))}><option value="REQUIRE_EXISTING">要求已开通</option><option value="AUTO_PROVISION_ALLOWED">允许联动计划</option></select><button type="button" aria-label="删除依赖" onClick={() => setForm((current) => ({ ...current, dependencies: current.dependencies.filter((_, itemIndex) => itemIndex !== index) }))}><X size={15} /></button></div>)}
        {!applications.length ? <p className="internal-application-version-empty">当前没有其他已发布应用可作为依赖。</p> : null}
      </section>
    </div>
    <div className="tenant-lifecycle__modal-foot"><button type="button" className="platform-button platform-button--secondary" onClick={onClose} disabled={busy}>取消</button><button type="submit" className="platform-button platform-button--primary" disabled={busy || !validSemanticVersion(form.version) || (form.initializationEngine === "SAGA_V1" && (!form.providerBindingKey || !form.steps.length))}>{busy ? "正在创建…" : "创建版本草稿"}</button></div>
  </form></div>;
}

function ConnectionModal({ form, setForm, busy, firstField, onClose, onSubmit }: { form: ConnectionForm; setForm: React.Dispatch<React.SetStateAction<ConnectionForm>>; busy: boolean; firstField: React.RefObject<HTMLInputElement | null>; onClose: () => void; onSubmit: (event: FormEvent) => void }) {
  return <div className="tenant-lifecycle__modal-backdrop platform-modal-scope" role="presentation" onMouseDown={(event) => event.currentTarget === event.target && onClose()}><form className="tenant-lifecycle__modal internal-application-connection-modal" role="dialog" aria-modal="true" aria-labelledby="internal-application-connection-title" onSubmit={onSubmit}>
    <div className="tenant-lifecycle__modal-head"><div><p className="platform-section-label">部署拓扑控制面</p><h2 id="internal-application-connection-title" className="platform-console__heading">{form.bindingKey ? "创建连接新修订" : "新建运行连接"}</h2></div><button type="button" className="tenant-lifecycle__modal-close" onClick={onClose} disabled={busy} aria-label="关闭"><X size={17} /></button></div>
    <div className="tenant-lifecycle__modal-body internal-application-connection-form">
      <p className="internal-application-form-note"><ShieldCheck size={15} />超级管理员可配置真实地址；平台后端负责连接测试和生命周期调用。凭据只填写环境 Secret 引用，不填写 Token 或 Secret 原文。</p>
      <section><div className="internal-application-connection-form__heading"><span>01</span><div><strong>连接身份</strong><small>稳定逻辑键由应用版本引用，修改配置会产生不可变新修订。</small></div></div><div className="internal-application-connection-form__grid">
        <label><span>连接名称</span><input ref={firstField} value={form.displayName} onChange={(event) => setForm((current) => ({ ...current, displayName: event.target.value }))} placeholder="研发交付生命周期服务" required /></label>
        <label><span>逻辑连接键</span><input value={form.bindingKey} readOnly={Boolean(form.bindingKey)} onChange={(event) => setForm((current) => ({ ...current, bindingKey: event.target.value.toLowerCase() }))} placeholder="sales-workbench.lifecycle" required /><small>{form.bindingKey ? "连接键已存在，只创建新的配置修订。" : "创建后不可转移到其他应用。"}</small></label>
        <label><span>环境标识</span><input value={form.environmentKey} onChange={(event) => setForm((current) => ({ ...current, environmentKey: event.target.value.toLowerCase() }))} placeholder="default" required /></label>
        <label><span>网络范围</span><select value={form.networkScope} onChange={(event) => setForm((current) => ({ ...current, networkScope: event.target.value }))}><option value="PUBLIC_HTTPS">公网 HTTPS</option><option value="PLATFORM_INTERNAL">平台内部网络</option></select></label>
      </div></section>
      <section><div className="internal-application-connection-form__heading"><span>02</span><div><strong>服务与契约</strong><small>地址在运行时保存，不进入应用版本或前端制品。</small></div></div><div className="internal-application-connection-form__grid">
        <label className="internal-application-connection-form__full"><span>服务 Base URL</span><input value={form.baseUrl} onChange={(event) => setForm((current) => ({ ...current, baseUrl: event.target.value }))} placeholder="https://service.example.test" spellCheck={false} required /><small>公网连接必须使用 HTTPS；内部 HTTP 仅限 PLATFORM_INTERNAL。</small></label>
        <label><span>生命周期契约版本</span><input value={form.contractVersion} onChange={(event) => setForm((current) => ({ ...current, contractVersion: event.target.value.toLowerCase() }))} placeholder="v1" required /></label>
        <label><span>健康检查路径</span><input value={form.healthPath} onChange={(event) => setForm((current) => ({ ...current, healthPath: event.target.value }))} required /></label>
        <label><span>ACTIVATE 路径</span><input value={form.activatePath} onChange={(event) => setForm((current) => ({ ...current, activatePath: event.target.value }))} required /></label>
        <label><span>RECONCILE 路径</span><input value={form.reconcilePath} onChange={(event) => setForm((current) => ({ ...current, reconcilePath: event.target.value }))} /></label>
        <label><span>SUSPEND 路径</span><input value={form.suspendPath} onChange={(event) => setForm((current) => ({ ...current, suspendPath: event.target.value }))} /></label>
        <label><span>RESUME 路径</span><input value={form.resumePath} onChange={(event) => setForm((current) => ({ ...current, resumePath: event.target.value }))} /></label>
        <label><span>UPGRADE 路径</span><input value={form.upgradePath} onChange={(event) => setForm((current) => ({ ...current, upgradePath: event.target.value }))} /></label>
      </div></section>
      <section><div className="internal-application-connection-form__heading"><span>03</span><div><strong>鉴权与可靠性</strong><small>Secret 引用由后端从当前环境解析，浏览器无法读取真实凭据。</small></div></div><div className="internal-application-connection-form__grid">
        <label><span>鉴权方式</span><select value={form.authType} onChange={(event) => setForm((current) => ({ ...current, authType: event.target.value, secretRef: event.target.value === "NONE" ? "" : current.secretRef }))}><option value="NONE">无需鉴权</option><option value="BEARER_SECRET_REF">Bearer · Secret 引用</option><option value="HMAC_SHA256_SECRET_REF">HMAC-SHA256 · Secret 引用</option></select></label>
        <label><span>Secret 引用</span><input value={form.secretRef} disabled={form.authType === "NONE"} required={form.authType !== "NONE"} onChange={(event) => setForm((current) => ({ ...current, secretRef: event.target.value.toLowerCase() }))} placeholder="sales-workbench.lifecycle-key" autoComplete="off" /></label>
        <label><span>请求超时（毫秒）</span><input type="number" min={1000} max={60000} step={1000} value={form.timeoutMs} onChange={(event) => setForm((current) => ({ ...current, timeoutMs: Number(event.target.value) }))} required /></label>
        <label><span>最大尝试次数</span><input type="number" min={1} max={5} value={form.maxAttempts} onChange={(event) => setForm((current) => ({ ...current, maxAttempts: Number(event.target.value) }))} required /></label>
      </div></section>
    </div>
    <div className="tenant-lifecycle__modal-foot"><button type="button" className="platform-button platform-button--secondary" onClick={onClose} disabled={busy}>取消</button><button type="submit" className="platform-button platform-button--primary" disabled={busy || !form.bindingKey.trim() || !form.displayName.trim() || !form.baseUrl.trim() || (form.authType !== "NONE" && !form.secretRef.trim())}>{busy ? "正在保存…" : "保存连接修订"}</button></div>
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

async function fetchProviderConnections(token: string, appCode: string): Promise<ProviderConnection[]> {
  const response = await fetch(`${PLATFORM_API_BASE}/internal-applications/${encodeURIComponent(appCode)}/connections`, { headers: { Authorization: `Bearer ${token}` } });
  const { body } = await safeFetchJson(response);
  if (!response.ok || !body?.success || !Array.isArray(body.data)) throw new Error(body?.message ?? `HTTP ${response.status}`);
  return body.data as ProviderConnection[];
}
