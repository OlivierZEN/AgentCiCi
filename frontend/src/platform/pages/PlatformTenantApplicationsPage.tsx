import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { AlertTriangle, AppWindow, Bot, Boxes, CheckCircle2, CircleDashed, Database, Mail, ShieldCheck, UserRound, Workflow } from "lucide-react";
import { useNavigate, useParams } from "react-router-dom";
import { PLATFORM_API_BASE } from "../../constants";
import { safeFetchJson } from "../../utils/http";
import {
  TenantDetail,
  isPlatformCompanyId,
  fetchTenantDetail,
  readPlatformToken,
} from "./platformTenantsShared";

export type DevAutopilotApplication = {
  appCode?: string;
  displayName?: string;
  summary?: string;
  iconKey?: string;
  ownerTeam?: string;
  tenantMode?: string;
  catalogStatus?: string;
  defaultVersion?: string | null;
  installedVersion?: string | null;
  enabled: boolean;
  initializationReady?: boolean;
  activationSupported?: boolean;
  templateVersion?: string | null;
  desiredState: string;
  actualState: string;
  healthState?: string;
  sematticeTenantId?: string | null;
  metadataVersionId?: string | null;
  activationStage?: string | null;
  failedStage?: string | null;
  lastErrorCode?: string | null;
  attemptCount?: number;
  resources?: Array<{ logicalRole: string; resourceType: string; resourceAlias: string; displayName: string; lifecycleState: string; primary: boolean }>;
  dependencies?: Array<{ appCode: string; versionConstraint: string; dependencyType: string; activationPolicy: string; actualState: string; required: boolean; satisfied: boolean }>;
  actions?: string[];
  managementRoute?: string;
};

export type TenantApplicationCatalog = {
  companyId: string;
  companyStatus: string;
  enabledCount: number;
  pendingCount: number;
  applications: DevAutopilotApplication[];
};

export function devAutopilotActivationKey(companyId: string): string {
  return `devautopilot-standard-v1-${companyId}`;
}

function devAutopilotStateLabel(state?: string): string {
  switch (state) {
    case "ACTIVE": return "运行中";
    case "SUSPENDED": return "已暂停";
    case "PROVISIONING": return "开通中";
    case "FAILED": return "开通失败";
    case "BLOCKED": return "依赖阻断";
    case "NOT_ENABLED": return "未开通";
    default: return "未开通";
  }
}

export function applicationStateLabel(state?: string): string {
  return devAutopilotStateLabel(state);
}

export function applicationIconKind(iconKey?: string): "bot" | "boxes" | "workflow" | "application" {
  if (iconKey === "bot") return "bot";
  if (iconKey === "boxes") return "boxes";
  if (iconKey === "workflow") return "workflow";
  return "application";
}

export function applicationActionLabel(action: string, application: DevAutopilotApplication): string {
  switch (action) {
    case "ACTIVATE": return `开通 ${application.displayName ?? "应用"}`;
    case "CONTINUE": return application.actualState === "FAILED" ? "重试开通" : "继续开通";
    case "RECONCILE": return application.initializationReady ? "同步标准模板" : "补齐初始化";
    case "SUSPEND": return "暂停应用";
    case "RESUME": return "恢复运行";
    case "OPEN": return "进入生命周期管理";
    default: return action;
  }
}

export type TenantOwnerIdentity = {
  companyId: string;
  memberId: string;
  displayName: string;
  maskedEmail: string;
  maskedMobile: string;
  publicId: string;
  memberStatus: string;
  identityState: "MISSING" | "PENDING_ACTIVATION" | "ACTIVE" | "BLOCKED";
  recoverable: boolean;
};

export function ownerIdentityStatus(identity: TenantOwnerIdentity | null): { label: string; description: string; tone: string } {
  switch (identity?.identityState) {
    case "MISSING":
      return { label: "统一身份缺失", description: "本地 Owner 已存在，但尚未建立统一身份，当前无法通过 OIDC 登录。", tone: "danger" };
    case "PENDING_ACTIVATION":
      return { label: "等待用户激活", description: "统一身份已建立，Owner 需要完成邮件验证和密码设置。", tone: "pending" };
    case "ACTIVE":
      return { label: "身份正常", description: "Owner 已完成统一身份激活，可通过 OIDC 登录。", tone: "healthy" };
    default:
      return { label: "暂不可恢复", description: "Owner 当前状态不允许执行统一身份协调，请先完成成员治理。", tone: "blocked" };
  }
}

export function devAutopilotInitializationReady(application: DevAutopilotApplication | null): boolean {
  if (!application?.enabled) return false;
  if (typeof application.initializationReady === "boolean") return application.initializationReady;
  const resources = application.resources ?? [];
  return resources.some((resource) => resource.logicalRole === "product_manager" && resource.resourceType === "AGENT" && resource.primary && resource.lifecycleState === "ACTIVE")
    && resources.some((resource) => resource.logicalRole === "product_manager" && resource.resourceType === "SERVICE_PRINCIPAL" && resource.primary && resource.lifecycleState === "ACTIVE");
}

export function isValidIntakeReconciliationInput(sessionId: string, recordId: string): boolean {
  return sessionId.trim().length > 0
    && sessionId.trim().length <= 64
    && /^[0-9a-fA-F-]{36}$/.test(recordId.trim());
}

export default function PlatformTenantApplicationsPage() {
  const token = readPlatformToken();
  const navigate = useNavigate();
  const { companyId = "" } = useParams();
  const [detail, setDetail] = useState<TenantDetail | null>(null);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [applicationCatalog, setApplicationCatalog] = useState<TenantApplicationCatalog | null>(null);
  const [ownerIdentity, setOwnerIdentity] = useState<TenantOwnerIdentity | null>(null);
  const [ownerModalOpen, setOwnerModalOpen] = useState(false);
  const [ownerConfirmation, setOwnerConfirmation] = useState("");
  const [ownerBusy, setOwnerBusy] = useState(false);
  const ownerTriggerRef = useRef<HTMLButtonElement | null>(null);
  const ownerConfirmationRef = useRef<HTMLInputElement | null>(null);
  const [intakeModalOpen, setIntakeModalOpen] = useState(false);
  const [intakeSessionId, setIntakeSessionId] = useState("");
  const [intakeRecordId, setIntakeRecordId] = useState("");
  const [intakeBusy, setIntakeBusy] = useState(false);
  const intakeTriggerRef = useRef<HTMLButtonElement | null>(null);
  const intakeSessionRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    if (!isPlatformCompanyId(companyId)) {
      navigate("/platform/tenants", { replace: true });
      return;
    }
    if (!token) return;
    void Promise.all([fetchTenantDetail(token, companyId), fetchTenantApplicationCatalog(token, companyId), fetchOwnerIdentity(token, companyId)])
      .then(([tenantDetail, catalog, identity]) => {
        setDetail(tenantDetail);
        setApplicationCatalog(catalog);
        setOwnerIdentity(identity);
      })
      .catch((err) => setError(err instanceof Error ? err.message : "加载租户应用失败"));
  }, [token, companyId, navigate]);

  useEffect(() => {
    if (ownerModalOpen) {
      globalThis.requestAnimationFrame(() => ownerConfirmationRef.current?.focus());
    }
  }, [ownerModalOpen]);

  useEffect(() => {
    if (intakeModalOpen) {
      globalThis.requestAnimationFrame(() => intakeSessionRef.current?.focus());
    }
  }, [intakeModalOpen]);

  useEffect(() => {
    if (!ownerModalOpen) return undefined;
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !ownerBusy) closeOwnerModal();
    };
    globalThis.addEventListener("keydown", closeOnEscape);
    return () => globalThis.removeEventListener("keydown", closeOnEscape);
  }, [ownerModalOpen, ownerBusy]);

  useEffect(() => {
    if (!intakeModalOpen) return undefined;
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !intakeBusy) closeIntakeModal();
    };
    globalThis.addEventListener("keydown", closeOnEscape);
    return () => globalThis.removeEventListener("keydown", closeOnEscape);
  }, [intakeModalOpen, intakeBusy]);

  function closeOwnerModal() {
    setOwnerModalOpen(false);
    setOwnerConfirmation("");
    globalThis.requestAnimationFrame(() => ownerTriggerRef.current?.focus());
  }

  function closeIntakeModal() {
    setIntakeModalOpen(false);
    setIntakeSessionId("");
    setIntakeRecordId("");
    globalThis.requestAnimationFrame(() => intakeTriggerRef.current?.focus());
  }

  async function refreshApplications() {
    if (!token || !companyId) return;
    setApplicationCatalog(await fetchTenantApplicationCatalog(token, companyId));
  }

  async function reconcileOwnerIdentity() {
    if (!companyId || !ownerIdentity || ownerConfirmation !== ownerIdentity.publicId) return;
    setOwnerBusy(true);
    setError("");
    setMessage("");
    try {
      const response = await fetch(`${PLATFORM_API_BASE}/tenants/${encodeURIComponent(companyId)}/owner-identity/reconciliations`, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({
          publicId: ownerIdentity.publicId,
          idempotencyKey: `owner-identity-${companyId}-${globalThis.crypto?.randomUUID?.() ?? Date.now()}`,
        }),
      });
      const { body } = await safeFetchJson(response);
      if (!response.ok || !body?.success) throw new Error(body?.message ?? `HTTP ${response.status}`);
      const updated = body.data as TenantOwnerIdentity;
      setOwnerIdentity(updated);
      setMessage(updated.identityState === "ACTIVE"
        ? "Owner 统一身份已协调并处于可登录状态。"
        : "Owner 统一身份已建立，激活邮件已发送，请通知 Owner 完成验证和密码设置。");
      closeOwnerModal();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Owner 统一身份协调失败");
    } finally {
      setOwnerBusy(false);
    }
  }

  async function provisionSemattice() {
    const semattice = applicationCatalog?.applications.find((application) => application.appCode === "semattice");
    if (!companyId || !detail || semattice?.actualState === "PROVISIONING") return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const idempotencyKey = `platform-${companyId}-${globalThis.crypto?.randomUUID?.() ?? Date.now()}`;
      const response = await fetch(`${PLATFORM_API_BASE}/tenants/${encodeURIComponent(companyId)}/semattice-provisionings`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          idempotencyKey,
          displayName: detail.tenant.name,
          serviceTier: "standard",
          entitlements: {},
        }),
      });
      const { body } = await safeFetchJson(response);
      if (!response.ok || !body?.success) {
        throw new Error(body?.message ?? `HTTP ${response.status}`);
      }
      await refreshApplications();
      setMessage("Semattice 已开通，并已完成企业身份绑定。");
    } catch (err) {
      try { await refreshApplications(); } catch { /* keep the last safe snapshot */ }
      setError(err instanceof Error ? err.message : "Semattice 开通失败");
    } finally {
      setBusy(false);
    }
  }

  async function activateDevAutopilot() {
    if (!companyId) return;
    setBusy(true); setError(""); setMessage("");
    try {
      const response = await fetch(`${PLATFORM_API_BASE}/tenants/${encodeURIComponent(companyId)}/applications/devautopilot/activations`, {
        method: "POST", headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({ idempotencyKey: devAutopilotActivationKey(companyId) }),
      });
      const { body } = await safeFetchJson(response);
      if (!response.ok || !body?.success) throw new Error(body?.message ?? `HTTP ${response.status}`);
      await refreshApplications();
      setMessage("DevAutopilot 已开通，已自动初始化研发产品经理智能体及其受控机器主体。开发者由租户管理员按需新增。");
    } catch (err) {
      try { await refreshApplications(); } catch { /* keep the last safe snapshot */ }
      setError(err instanceof Error ? err.message : "DevAutopilot 开通失败");
    } finally { setBusy(false); }
  }

  async function changeDevAutopilotState(action: "suspensions" | "resumptions") {
    if (!companyId) return;
    setBusy(true); setError("");
    try {
      const response = await fetch(`${PLATFORM_API_BASE}/tenants/${encodeURIComponent(companyId)}/applications/devautopilot/${action}`, { method: "POST", headers: { Authorization: `Bearer ${token}` } });
      const { body } = await safeFetchJson(response);
      if (!response.ok || !body?.success) throw new Error(body?.message ?? `HTTP ${response.status}`);
      await refreshApplications();
      setMessage(action === "suspensions" ? "DevAutopilot 已暂停，运行时入口已关闭。" : "DevAutopilot 已恢复运行。");
    } catch (err) { setError(err instanceof Error ? err.message : "生命周期操作失败"); } finally { setBusy(false); }
  }

  async function reconcileDevAutopilotInitialization() {
    if (!companyId) return;
    setBusy(true); setError(""); setMessage("");
    try {
      const response = await fetch(`${PLATFORM_API_BASE}/tenants/${encodeURIComponent(companyId)}/applications/devautopilot/initializations`, { method: "POST", headers: { Authorization: `Bearer ${token}` } });
      const { body } = await safeFetchJson(response);
      if (!response.ok || !body?.success) throw new Error(body?.message ?? `HTTP ${response.status}`);
      await refreshApplications();
      setMessage("DevAutopilot 标准模板已同步：Semattice 业务对象、产品经理智能体及其受控机器主体已就绪。");
    } catch (err) { setError(err instanceof Error ? err.message : "DevAutopilot 初始化补齐失败"); } finally { setBusy(false); }
  }

  async function executeGenericApplicationOperation(application: DevAutopilotApplication, action: string) {
    if (!companyId || !application.appCode) return;
    setBusy(true); setError(""); setMessage("");
    try {
      const idempotencyKey = `platform-${action.toLowerCase()}-${globalThis.crypto?.randomUUID?.() ?? Date.now()}`;
      const response = await fetch(`${PLATFORM_API_BASE}/tenants/${encodeURIComponent(companyId)}/applications/${encodeURIComponent(application.appCode)}/operations`, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({ operationType: action, idempotencyKey }),
      });
      const { body } = await safeFetchJson(response);
      if (!response.ok || !body?.success) throw new Error(body?.message ?? `HTTP ${response.status}`);
      const result = body.data as { status?: string; errorCode?: string } | undefined;
      if (result?.status !== "SUCCEEDED") throw new Error(result?.errorCode ?? "应用生命周期操作失败");
      await refreshApplications();
      setMessage(`${application.displayName ?? application.appCode} ${action === "ACTIVATE" ? "已完成初始化并开通" : action === "SUSPEND" ? "已暂停" : action === "RESUME" ? "已恢复" : "已完成状态协调"}。`);
    } catch (err) {
      try { await refreshApplications(); } catch { /* keep the last safe snapshot */ }
      setError(err instanceof Error ? err.message : "应用生命周期操作失败");
    } finally { setBusy(false); }
  }

  async function reconcileDevAutopilotIntake() {
    if (!companyId || !isValidIntakeReconciliationInput(intakeSessionId, intakeRecordId)) return;
    setIntakeBusy(true); setError(""); setMessage("");
    try {
      const response = await fetch(`${PLATFORM_API_BASE}/tenants/${encodeURIComponent(companyId)}/applications/devautopilot/intake-reconciliations`, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({ sessionId: intakeSessionId.trim(), recordId: intakeRecordId.trim() }),
      });
      const { body } = await safeFetchJson(response);
      if (!response.ok || !body?.success) throw new Error(body?.message ?? `HTTP ${response.status}`);
      const result = body.data as { status: string; revision: number; contentDigest: string; readbackVerified: boolean };
      if (!result.readbackVerified) throw new Error("Semattice 字段回读未通过");
      setMessage(`历史受理记录已${result.status === "UNCHANGED" ? "核验一致" : "完成校准"}：revision ${result.revision}，内容摘要 ${result.contentDigest.slice(0, 12)}。`);
      closeIntakeModal();
    } catch (err) {
      setError(err instanceof Error ? err.message : "历史受理记录校准失败");
    } finally {
      setIntakeBusy(false);
    }
  }

  async function runApplicationAction(application: DevAutopilotApplication, action: string) {
    if (action === "OPEN" && application.managementRoute) {
      navigate(application.managementRoute);
      return;
    }
    if (application.appCode === "semattice" && action === "ACTIVATE") {
      await provisionSemattice();
      return;
    }
    if (application.appCode !== "devautopilot") {
      await executeGenericApplicationOperation(application, action);
      return;
    }
    if (action === "ACTIVATE" || action === "CONTINUE") {
      await activateDevAutopilot();
    } else if (action === "RECONCILE") {
      await reconcileDevAutopilotInitialization();
    } else if (action === "SUSPEND") {
      await changeDevAutopilotState("suspensions");
    } else if (action === "RESUME") {
      await changeDevAutopilotState("resumptions");
    }
  }

  const ownerStatus = ownerIdentityStatus(ownerIdentity);

  return (
    <div className="admin-page skills-catalog platform-page platform-tenants-page">
      <header className="skills-catalog__header platform-page-head">
        <div className="platform-page-head__main">
          <h1 className="skills-catalog__title">租户应用</h1>
          <p className="subtle skills-catalog__subtitle">查看当前租户已开通的应用，并在应用卡片内完成独立开通操作。</p>
        </div>
        <div className="platform-page-head__aside">
          <button type="button" className="platform-button platform-button--secondary" onClick={() => navigate("/platform/tenants")}>
            返回租户列表
          </button>
        </div>
      </header>

      {error ? <div className="platform-console__banner platform-console__banner--error">{error}</div> : null}
      {message ? <div className="platform-console__banner platform-console__banner--success">{message}</div> : null}

      <div className="tenant-lifecycle tenant-lifecycle--detail">
        <section className="platform-console__panel tenant-lifecycle__detail tenant-lifecycle-detail" aria-label="租户应用详情">
          {detail ? (
            <div className="platform-console__stack">
              <div className="platform-console__section tenant-lifecycle__detail-head">
                <div>
                  <p className="platform-section-label">当前租户</p>
                  <h2 className="platform-console__heading">{detail.tenant.name}</h2>
                  <p className="skills-data-table__summary">{detail.tenant.companyId}</p>
                </div>
              </div>

              {ownerIdentity ? (
                <section className={`platform-console__section tenant-owner-identity tenant-owner-identity--${ownerStatus.tone}`} aria-labelledby="tenant-owner-identity-title">
                  <div className="tenant-owner-identity__head">
                    <span className="tenant-owner-identity__icon" aria-hidden="true"><UserRound size={20} strokeWidth={1.8} /></span>
                    <div>
                      <div className="tenant-owner-identity__title-line">
                        <h3 id="tenant-owner-identity-title" className="platform-console__subheading">Owner 身份</h3>
                        <span className="tenant-owner-identity__state">{ownerStatus.label}</span>
                      </div>
                      <p className="skills-data-table__summary">{ownerStatus.description}</p>
                    </div>
                  </div>
                  <dl className="tenant-owner-identity__facts">
                    <div><dt>Owner</dt><dd>{ownerIdentity.displayName}</dd></div>
                    <div><dt>邮箱</dt><dd>{ownerIdentity.maskedEmail}</dd></div>
                    <div><dt>手机号</dt><dd>{ownerIdentity.maskedMobile}</dd></div>
                    <div><dt>成员状态</dt><dd>OWNER · {ownerIdentity.memberStatus}</dd></div>
                    <div><dt>统一身份</dt><dd>{ownerIdentity.identityState === "MISSING" ? "未绑定" : ownerIdentity.identityState === "PENDING_ACTIVATION" ? "已绑定 · 待激活" : ownerIdentity.identityState === "ACTIVE" ? "已绑定 · 可登录" : "已绑定 · 受限"}</dd></div>
                    <div><dt>公共编号</dt><dd>{ownerIdentity.publicId}</dd></div>
                  </dl>
                  <div className="tenant-owner-identity__foot">
                    <span><ShieldCheck size={14} aria-hidden="true" />仅平台管理员可操作；不会改变 Owner 角色或重新创建租户。</span>
                    {ownerIdentity.recoverable ? (
                      <button
                        ref={ownerTriggerRef}
                        type="button"
                        className="platform-button platform-button--primary tenant-owner-identity__action"
                        onClick={() => {
                          setOwnerConfirmation("");
                          setOwnerModalOpen(true);
                        }}
                      >
                        {ownerIdentity.identityState === "MISSING" ? <ShieldCheck size={15} aria-hidden="true" /> : <Mail size={15} aria-hidden="true" />}
                        {ownerIdentity.identityState === "MISSING" ? "修复统一身份并发送激活邮件" : "重发激活邮件"}
                      </button>
                    ) : null}
                  </div>
                </section>
              ) : (
                <section className="platform-console__section tenant-owner-identity tenant-owner-identity--warning" aria-labelledby="tenant-owner-identity-title">
                  <div className="tenant-owner-identity__head">
                    <span className="tenant-owner-identity__icon" aria-hidden="true"><UserRound size={20} strokeWidth={1.8} /></span>
                    <div>
                      <div className="tenant-owner-identity__title-line">
                        <h3 id="tenant-owner-identity-title" className="platform-console__subheading">Owner 身份</h3>
                        <span className="tenant-owner-identity__state">待补齐</span>
                      </div>
                      <p className="skills-data-table__summary">当前租户缺少 Owner；身份治理需单独处理，但不阻断已授权的平台管理员维护租户应用。</p>
                    </div>
                  </div>
                </section>
              )}

              <section className="platform-console__section tenant-applications" aria-labelledby="tenant-applications-title">
                <div className="tenant-applications__heading">
                  <div>
                    <p className="platform-section-label">租户应用</p>
                    <h3 id="tenant-applications-title" className="platform-console__subheading">应用中心</h3>
                    <p className="skills-data-table__summary">每个应用独立开通、独立运行，并拥有自身的生命周期管理页面。</p>
                  </div>
                  <div className="tenant-applications__count" aria-label="应用开通汇总">
                    <span>已开通 <strong>{applicationCatalog?.enabledCount ?? 0}</strong></span>
                    <span>待处理 <strong>{applicationCatalog?.pendingCount ?? 0}</strong></span>
                  </div>
                </div>

                <div className="tenant-applications__grid">
                  {(applicationCatalog?.applications ?? []).map((application) => {
                    const actions = application.actions ?? [];
                    const displayActions = actions.filter((action) => action !== "OPEN" || application.appCode === "agentcici");
                    const requiredDependency = application.dependencies?.find((dependency) => dependency.required);
                    const stateTone = application.actualState === "ACTIVE" ? "healthy" : application.actualState?.toLowerCase() ?? "not_enabled";
                    const iconKind = applicationIconKind(application.iconKey);
                    return (
                      <article key={application.appCode} className={`tenant-application-card tenant-application-card--${application.appCode ?? "registered"}`} aria-labelledby={`${application.appCode}-application-title`}>
                        <div className="tenant-application-card__head">
                          <span className={`tenant-application-card__icon${iconKind === "boxes" ? " tenant-application-card__icon--semantic" : ""}`} aria-hidden="true">
                            {iconKind === "bot" ? <Bot size={22} strokeWidth={1.8} /> : iconKind === "boxes" ? <Boxes size={22} strokeWidth={1.7} /> : iconKind === "workflow" ? <Workflow size={22} strokeWidth={1.7} /> : <AppWindow size={22} strokeWidth={1.7} />}
                          </span>
                          <div className="tenant-application-card__title">
                            <h4 id={`${application.appCode}-application-title`}>{application.displayName}</h4>
                            <p>{application.summary}</p>
                          </div>
                          <span className={`tenant-application-card__state tenant-application-card__state--${stateTone}`}>
                            {application.actualState === "PROVISIONING" ? <CircleDashed size={14} aria-hidden="true" /> : application.appCode === "semattice" ? <Database size={14} aria-hidden="true" /> : <CheckCircle2 size={14} aria-hidden="true" />}
                            {applicationStateLabel(application.actualState)}
                          </span>
                        </div>
                        <dl className="tenant-application-card__facts">
                          <div><dt>目录版本</dt><dd>{application.defaultVersion ?? "未发布"}</dd></div>
                          <div><dt>租户标识</dt><dd>{detail.tenant.companyId}</dd></div>
                          <div><dt>应用归属</dt><dd>{application.ownerTeam ?? "平台内部应用"}</dd></div>
                          <div><dt>初始化状态</dt><dd>{application.initializationReady ? "已完成" : application.activationStage ?? "未开始"}</dd></div>
                          {requiredDependency ? <div><dt>强依赖</dt><dd>{requiredDependency.appCode} · {requiredDependency.satisfied ? "已就绪" : "未就绪"}</dd></div> : null}
                          {application.failedStage ? <div><dt>失败阶段</dt><dd>{application.failedStage}</dd></div> : null}
                          {application.lastErrorCode ? <div><dt>错误代码</dt><dd>{application.lastErrorCode}</dd></div> : null}
                        </dl>
                        <div className="tenant-application-card__foot tenant-application-card__foot--action">
                          <span><ShieldCheck size={14} aria-hidden="true" />{application.healthState === "BLOCKED" ? "强依赖未就绪，当前不能开通" : application.activationSupported === false ? "目录已发布，标准生命周期执行器将在下一阶段接管开通" : application.enabled ? "暂停只关闭本租户运行入口，不删除业务数据" : "开通将固定当前目录版本并记录完整操作审计"}</span>
                          <div className="tenant-application-card__actions">
                            {application.appCode === "devautopilot" && application.enabled ? <button ref={intakeTriggerRef} type="button" className="platform-button platform-button--secondary" disabled={busy} onClick={() => setIntakeModalOpen(true)}>校准历史受理</button> : null}
                            {displayActions.map((action, index) => <button key={action} type="button" className={`platform-button ${index === displayActions.length - 1 ? "platform-button--primary" : "platform-button--secondary"}`} disabled={busy || detail.tenant.status !== "ACTIVE"} onClick={() => void runApplicationAction(application, action)}>{applicationActionLabel(action, application)}</button>)}
                          </div>
                        </div>
                      </article>
                    );
                  })}
                  {applicationCatalog && applicationCatalog.applications.length === 0 ? <div className="tenant-applications__empty"><strong>当前没有已发布的租户应用</strong><span>请先在运营管理的应用中心登记、验证并发布应用版本。</span></div> : null}
                </div>
              </section>
            </div>
          ) : (
            <p className="skills-data-table__summary">正在加载租户应用。</p>
          )}
        </section>
      </div>

      {ownerModalOpen && detail && ownerIdentity ? createPortal((
        <div className="tenant-lifecycle__modal-backdrop platform-modal-scope" role="presentation" onMouseDown={(event) => {
          if (event.currentTarget === event.target && !ownerBusy) closeOwnerModal();
        }}>
          <div className="tenant-lifecycle__modal tenant-owner-identity__modal" role="dialog" aria-modal="true" aria-labelledby="tenant-owner-reconcile-title">
            <div className="tenant-lifecycle__modal-head">
              <div className="tenant-owner-identity__modal-title">
                <span className="tenant-owner-identity__warning" aria-hidden="true"><AlertTriangle size={18} /></span>
                <div>
                  <p className="platform-section-label">Owner 身份协调</p>
                  <h2 id="tenant-owner-reconcile-title" className="platform-console__heading">确认当前 Owner</h2>
                </div>
              </div>
              <button type="button" className="tenant-lifecycle__modal-close" onClick={closeOwnerModal} disabled={ownerBusy} aria-label="关闭">×</button>
            </div>
            <div className="tenant-lifecycle__modal-body">
              <p className="skills-data-table__summary">系统会补齐当前 Owner 的统一身份；仍需激活时，只重发邮箱验证与密码设置邮件。</p>
              <div className="tenant-owner-identity__subject">
                <span>{detail.tenant.name}</span>
                <strong>{ownerIdentity.displayName} · {ownerIdentity.maskedEmail}</strong>
                <code>{ownerIdentity.publicId}</code>
              </div>
              <p className="tenant-lifecycle__field-help">该操作不转让 Owner、不设置密码，也不改动租户业务数据；结果写入平台审计。</p>
              <label>
                <span>输入 Owner 公共编号以确认</span>
                <input
                  ref={ownerConfirmationRef}
                  value={ownerConfirmation}
                  onChange={(event) => setOwnerConfirmation(event.target.value.toUpperCase())}
                  placeholder={ownerIdentity.publicId}
                  autoComplete="off"
                  disabled={ownerBusy}
                />
                <small className="tenant-lifecycle__field-help">仅当输入内容与当前 Owner 完全一致时才可继续。</small>
              </label>
            </div>
            <div className="tenant-lifecycle__modal-foot">
              <button type="button" className="platform-button platform-button--secondary" onClick={closeOwnerModal} disabled={ownerBusy}>取消</button>
              <button
                type="button"
                className="platform-button platform-button--primary"
                onClick={() => void reconcileOwnerIdentity()}
                disabled={ownerBusy || ownerConfirmation !== ownerIdentity.publicId}
              >
                {ownerBusy ? "正在协调身份…" : "确认协调"}
              </button>
            </div>
          </div>
        </div>
      ), document.body) : null}

      {intakeModalOpen && detail ? createPortal((
        <div className="tenant-lifecycle__modal-backdrop platform-modal-scope" role="presentation" onMouseDown={(event) => {
          if (event.currentTarget === event.target && !intakeBusy) closeIntakeModal();
        }}>
          <div className="tenant-lifecycle__modal tenant-owner-identity__modal" role="dialog" aria-modal="true" aria-labelledby="devautopilot-intake-reconcile-title">
            <div className="tenant-lifecycle__modal-head">
              <div className="tenant-owner-identity__modal-title">
                <span className="tenant-owner-identity__warning" aria-hidden="true"><ShieldCheck size={18} /></span>
                <div>
                  <p className="platform-section-label">DevAutopilot 数据维护</p>
                  <h2 id="devautopilot-intake-reconcile-title" className="platform-console__heading">校准历史受理记录</h2>
                </div>
              </div>
              <button type="button" className="tenant-lifecycle__modal-close" onClick={closeIntakeModal} disabled={intakeBusy} aria-label="关闭">×</button>
            </div>
            <div className="tenant-lifecycle__modal-body">
              <p className="skills-data-table__summary">系统只会从该租户原会话中的产品经理草稿、用户确认和成功回执恢复字段，不能在此填写或修改业务内容。</p>
              <div className="tenant-owner-identity__subject">
                <span>{detail.tenant.name}</span>
                <strong>{detail.tenant.companyId}</strong>
              </div>
              <label>
                <span>原确认会话 ID</span>
                <input ref={intakeSessionRef} value={intakeSessionId} onChange={(event) => setIntakeSessionId(event.target.value)} placeholder="例如：workbench:devautopilot-pm" autoComplete="off" disabled={intakeBusy} />
              </label>
              <label>
                <span>Semattice 记录 ID</span>
                <input value={intakeRecordId} onChange={(event) => setIntakeRecordId(event.target.value)} placeholder="UUID" autoComplete="off" disabled={intakeBusy} />
                <small className="tenant-lifecycle__field-help">写入使用原确认人的产品经理 SERVICE 委托链；逐字段回读一致后才会返回成功。</small>
              </label>
            </div>
            <div className="tenant-lifecycle__modal-foot">
              <button type="button" className="platform-button platform-button--secondary" onClick={closeIntakeModal} disabled={intakeBusy}>取消</button>
              <button type="button" className="platform-button platform-button--primary" onClick={() => void reconcileDevAutopilotIntake()} disabled={intakeBusy || !isValidIntakeReconciliationInput(intakeSessionId, intakeRecordId)}>
                {intakeBusy ? "正在校准并回读…" : "开始校准"}
              </button>
            </div>
          </div>
        </div>
      ), document.body) : null}
    </div>
  );
}

export async function fetchTenantApplicationCatalog(token: string, companyId: string): Promise<TenantApplicationCatalog> {
  const response = await fetch(`${PLATFORM_API_BASE}/tenants/${encodeURIComponent(companyId)}/applications`, { headers: { Authorization: `Bearer ${token}` } });
  const { body } = await safeFetchJson(response);
  if (!response.ok || !body?.success) throw new Error(body?.message ?? `HTTP ${response.status}`);
  return body.data as TenantApplicationCatalog;
}

export async function fetchOwnerIdentity(token: string, companyId: string): Promise<TenantOwnerIdentity | null> {
  const response = await fetch(`${PLATFORM_API_BASE}/tenants/${encodeURIComponent(companyId)}/owner-identity`, { headers: { Authorization: `Bearer ${token}` } });
  const { body } = await safeFetchJson(response);
  if (response.status === 404) return null;
  if (!response.ok || !body?.success) throw new Error(body?.message ?? `HTTP ${response.status}`);
  return body.data as TenantOwnerIdentity;
}
