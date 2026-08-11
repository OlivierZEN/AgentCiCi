import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { AlertTriangle, Bot, Boxes, CheckCircle2, CircleDashed, Database, Mail, ShieldCheck, UserRound } from "lucide-react";
import { useNavigate, useParams } from "react-router-dom";
import { PLATFORM_API_BASE } from "../../constants";
import { safeFetchJson } from "../../utils/http";
import {
  TenantDetail,
  fetchSematticeProvisioning,
  isPlatformCompanyId,
  fetchTenantDetail,
  readPlatformToken,
  statusLabel,
} from "./platformTenantsShared";

type SematticeProvisioningState = "NOT_PROVISIONED" | "PROVISIONING" | "PROVISIONED" | "FAILED";
export type DevAutopilotApplication = {
  enabled: boolean;
  initializationReady?: boolean;
  templateVersion?: string | null;
  desiredState: string;
  actualState: string;
  sematticeTenantId?: string | null;
  metadataVersionId?: string | null;
  resources: Array<{ logicalRole: string; resourceType: string; resourceAlias: string; displayName: string; lifecycleState: string; primary: boolean }>;
};

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

function sematticeStateLabel(state: SematticeProvisioningState): string {
  switch (state) {
    case "PROVISIONING":
      return "开通中";
    case "PROVISIONED":
      return "运行中";
    case "FAILED":
      return "开通失败";
    default:
      return "未开通";
  }
}

export default function PlatformTenantApplicationsPage() {
  const token = readPlatformToken();
  const navigate = useNavigate();
  const { companyId = "" } = useParams();
  const [detail, setDetail] = useState<TenantDetail | null>(null);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [sematticeProvisioningState, setSematticeProvisioningState] = useState<SematticeProvisioningState>("NOT_PROVISIONED");
  const [devAutopilot, setDevAutopilot] = useState<DevAutopilotApplication | null>(null);
  const [ownerIdentity, setOwnerIdentity] = useState<TenantOwnerIdentity | null>(null);
  const [ownerModalOpen, setOwnerModalOpen] = useState(false);
  const [ownerConfirmation, setOwnerConfirmation] = useState("");
  const [ownerBusy, setOwnerBusy] = useState(false);
  const ownerTriggerRef = useRef<HTMLButtonElement | null>(null);
  const ownerConfirmationRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    if (!isPlatformCompanyId(companyId)) {
      navigate("/platform/tenants", { replace: true });
      return;
    }
    if (!token) return;
    void Promise.all([fetchTenantDetail(token, companyId), fetchSematticeProvisioning(token, companyId), fetchDevAutopilot(token, companyId), fetchOwnerIdentity(token, companyId)])
      .then(([tenantDetail, provisioning, application, identity]) => {
        setDetail(tenantDetail);
        setSematticeProvisioningState(provisioning.state === "RESERVED" ? "PROVISIONING" : provisioning.state);
        setDevAutopilot(application);
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
    if (!ownerModalOpen) return undefined;
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !ownerBusy) closeOwnerModal();
    };
    globalThis.addEventListener("keydown", closeOnEscape);
    return () => globalThis.removeEventListener("keydown", closeOnEscape);
  }, [ownerModalOpen, ownerBusy]);

  function closeOwnerModal() {
    setOwnerModalOpen(false);
    setOwnerConfirmation("");
    globalThis.requestAnimationFrame(() => ownerTriggerRef.current?.focus());
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
    if (!companyId || !detail || sematticeProvisioningState === "PROVISIONING") return;
    setBusy(true);
    setError("");
    setMessage("");
    setSematticeProvisioningState("PROVISIONING");
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
      setSematticeProvisioningState("PROVISIONED");
      setMessage("Semattice 已开通，并已完成企业身份绑定。");
    } catch (err) {
      setSematticeProvisioningState("FAILED");
      setError(err instanceof Error ? err.message : "Semattice 开通失败");
    } finally {
      setBusy(false);
    }
  }

  async function activateDevAutopilot() {
    if (!companyId || !devAutopilot) return;
    setBusy(true); setError(""); setMessage("");
    try {
      const response = await fetch(`${PLATFORM_API_BASE}/tenants/${encodeURIComponent(companyId)}/applications/devautopilot/activations`, {
        method: "POST", headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({ idempotencyKey: `devautopilot-${companyId}-${globalThis.crypto?.randomUUID?.() ?? Date.now()}` }),
      });
      const { body } = await safeFetchJson(response);
      if (!response.ok || !body?.success) throw new Error(body?.message ?? `HTTP ${response.status}`);
      setDevAutopilot(body.data as DevAutopilotApplication);
      setMessage("DevAutopilot 已开通，已自动初始化研发产品经理智能体及其受控机器主体。开发者由租户管理员按需新增。");
    } catch (err) { setError(err instanceof Error ? err.message : "DevAutopilot 开通失败"); } finally { setBusy(false); }
  }

  async function changeDevAutopilotState(action: "suspensions" | "resumptions") {
    if (!companyId) return;
    setBusy(true); setError("");
    try {
      const response = await fetch(`${PLATFORM_API_BASE}/tenants/${encodeURIComponent(companyId)}/applications/devautopilot/${action}`, { method: "POST", headers: { Authorization: `Bearer ${token}` } });
      const { body } = await safeFetchJson(response);
      if (!response.ok || !body?.success) throw new Error(body?.message ?? `HTTP ${response.status}`);
      setDevAutopilot(body.data as DevAutopilotApplication);
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
      setDevAutopilot(body.data as DevAutopilotApplication);
      setMessage("DevAutopilot 标准模板已同步：Semattice 业务对象、产品经理智能体及其受控机器主体已就绪。");
    } catch (err) { setError(err instanceof Error ? err.message : "DevAutopilot 初始化补齐失败"); } finally { setBusy(false); }
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
              ) : null}

              <section className="platform-console__section tenant-applications" aria-labelledby="tenant-applications-title">
                <div className="tenant-applications__heading">
                  <div>
                    <p className="platform-section-label">租户应用</p>
                    <h3 id="tenant-applications-title" className="platform-console__subheading">应用中心</h3>
                    <p className="skills-data-table__summary">每个应用独立开通、独立运行，并拥有自身的生命周期管理页面。</p>
                  </div>
                  <div className="tenant-applications__count" aria-label="应用开通汇总">
                    <span>已开通 <strong>{1 + (sematticeProvisioningState === "PROVISIONED" ? 1 : 0) + (devAutopilot?.enabled ? 1 : 0)}</strong></span>
                    <span>待处理 <strong>{(sematticeProvisioningState === "PROVISIONING" ? 1 : 0) + (devAutopilot?.actualState === "PROVISIONING" ? 1 : 0)}</strong></span>
                  </div>
                </div>

                <div className="tenant-applications__grid">
                  <article
                    className="tenant-application-card tenant-application-card--agentcici tenant-application-card--interactive"
                    aria-labelledby="agentcici-application-title"
                    role="link"
                    tabIndex={0}
                    onClick={() => navigate(`/platform/tenants/${encodeURIComponent(detail.tenant.companyId)}/applications/agentcici`)}
                    onKeyDown={(event) => {
                      if (event.key === "Enter" || event.key === " ") {
                        event.preventDefault();
                        navigate(`/platform/tenants/${encodeURIComponent(detail.tenant.companyId)}/applications/agentcici`);
                      }
                    }}
                  >
                    <div className="tenant-application-card__head">
                      <span className="tenant-application-card__icon" aria-hidden="true"><Bot size={22} strokeWidth={1.8} /></span>
                      <div className="tenant-application-card__title">
                        <h4 id="agentcici-application-title">AgentCiCi 智能体平台</h4>
                        <p>租户默认开通的智能体运行与治理应用</p>
                      </div>
                      <span className="tenant-application-card__state tenant-application-card__state--healthy"><CheckCircle2 size={14} aria-hidden="true" />运行中</span>
                    </div>
                    <dl className="tenant-application-card__facts">
                      <div><dt>租户标识</dt><dd>{detail.tenant.companyId}</dd></div>
                      <div><dt>成员</dt><dd>{detail.tenant.memberCount}</dd></div>
                      <div><dt>应用状态</dt><dd>{statusLabel(detail.tenant.status)}</dd></div>
                      <div><dt>开通方式</dt><dd>租户基础应用</dd></div>
                    </dl>
                    <div className="tenant-application-card__foot">
                      <span>进入 AgentCiCi 应用生命周期管理</span>
                    </div>
                  </article>

                  <article className="tenant-application-card tenant-application-card--semattice" aria-labelledby="semattice-application-title">
                    <div className="tenant-application-card__head">
                      <span className="tenant-application-card__icon tenant-application-card__icon--semantic" aria-hidden="true"><Boxes size={22} strokeWidth={1.7} /></span>
                      <div className="tenant-application-card__title">
                        <h4 id="semattice-application-title">Semattice 业务数据与语义运行底座</h4>
                        <p>面向智能体的业务数据与语义运行底座</p>
                      </div>
                      <span className={`tenant-application-card__state tenant-application-card__state--${sematticeProvisioningState.toLowerCase()}`}>
                        {sematticeProvisioningState === "PROVISIONING" ? <CircleDashed size={14} aria-hidden="true" /> : <Database size={14} aria-hidden="true" />}
                        {sematticeStateLabel(sematticeProvisioningState)}
                      </span>
                    </div>
                    <dl className="tenant-application-card__facts">
                      <div><dt>租户标识</dt><dd>{detail.tenant.companyId}</dd></div>
                      <div><dt>开户来源</dt><dd>AgentCiCi 运营端</dd></div>
                      <div><dt>身份校验</dt><dd>AgentCiCi 受控校验</dd></div>
                      <div><dt>接入方式</dt><dd>HTTP API · MCP · CLI</dd></div>
                    </dl>
                    <div className="tenant-application-card__foot tenant-application-card__foot--action">
                      <span><ShieldCheck size={14} aria-hidden="true" />生命周期由 Semattice 应用独立管理</span>
                      <button
                        type="button"
                        className="platform-button platform-button--primary tenant-application-card__primary-action"
                        onClick={() => void provisionSemattice()}
                        disabled={busy || sematticeProvisioningState === "PROVISIONING" || sematticeProvisioningState === "PROVISIONED" || detail.tenant.status !== "ACTIVE"}
                      >
                        {sematticeProvisioningState === "PROVISIONING" ? "正在开通" : sematticeProvisioningState === "PROVISIONED" ? "已开通" : "开通 Semattice"}
                      </button>
                    </div>
                  </article>

                  <article className="tenant-application-card tenant-application-card--semattice" aria-labelledby="devautopilot-application-title">
                    <div className="tenant-application-card__head">
                      <span className="tenant-application-card__icon" aria-hidden="true"><Bot size={22} strokeWidth={1.8} /></span>
                      <div className="tenant-application-card__title">
                        <h4 id="devautopilot-application-title">DevAutopilot 研发交付系统</h4>
                        <p>开通即初始化产品经理智能体，开发者由租户管理员按需新增</p>
                      </div>
                      <span className={`tenant-application-card__state tenant-application-card__state--${devAutopilot?.actualState === "ACTIVE" ? "healthy" : "not_provisioned"}`}><CheckCircle2 size={14} aria-hidden="true" />{devAutopilot?.actualState === "ACTIVE" ? "运行中" : devAutopilot?.actualState === "SUSPENDED" ? "已暂停" : "未开通"}</span>
                    </div>
                    {devAutopilot?.enabled ? <>
                      <dl className="tenant-application-card__facts">
                        <div><dt>模板版本</dt><dd>{devAutopilot.templateVersion}</dd></div><div><dt>租户标识</dt><dd>{detail.tenant.companyId}</dd></div>
                        <div><dt>数据底座</dt><dd>Semattice（已绑定）</dd></div><div><dt>初始化状态</dt><dd>{devAutopilotInitializationReady(devAutopilot) ? "已完成" : "待补齐"}</dd></div>
                      </dl>
                      <div className="tenant-application-card__foot tenant-application-card__foot--action">
                        <span><ShieldCheck size={14} aria-hidden="true" />{devAutopilotInitializationReady(devAutopilot) ? "关闭仅暂停本租户运行入口，不删除数据" : "历史开通记录缺少标准资源，需要补齐初始化"}</span>
                        <div className="tenant-application-card__actions">
                          {devAutopilotInitializationReady(devAutopilot) ? <button type="button" className="platform-button platform-button--secondary" disabled={busy} onClick={() => void reconcileDevAutopilotInitialization()}>同步标准模板</button> : null}
                          <button type="button" className="platform-button platform-button--primary tenant-application-card__primary-action" disabled={busy} onClick={() => void (devAutopilotInitializationReady(devAutopilot) ? changeDevAutopilotState(devAutopilot.actualState === "SUSPENDED" ? "resumptions" : "suspensions") : reconcileDevAutopilotInitialization())}>{devAutopilotInitializationReady(devAutopilot) ? (devAutopilot.actualState === "SUSPENDED" ? "恢复运行" : "暂停应用") : "补齐初始化"}</button>
                        </div>
                      </div>
                      <p className="skills-data-table__summary">研发产品经理智能体与其机器主体由模板创建，名称和负责人可由该租户的 ORG_ADMIN 在 AgentCiCi 管理端调整；开发者由租户按需新增。</p>
                    </> : <div className="tenant-application-card__foot tenant-application-card__foot--action">
                      <span>需先开通 Semattice；开通后自动创建标准产品经理智能体与机器主体。</span>
                      <button type="button" className="platform-button platform-button--primary tenant-application-card__primary-action" disabled={busy || sematticeProvisioningState !== "PROVISIONED"} onClick={() => void activateDevAutopilot()}>开通 DevAutopilot</button>
                    </div>}
                  </article>
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
    </div>
  );
}

async function fetchDevAutopilot(token: string, companyId: string): Promise<DevAutopilotApplication> {
  const response = await fetch(`${PLATFORM_API_BASE}/tenants/${encodeURIComponent(companyId)}/applications/devautopilot`, { headers: { Authorization: `Bearer ${token}` } });
  const { body } = await safeFetchJson(response);
  if (!response.ok || !body?.success) throw new Error(body?.message ?? `HTTP ${response.status}`);
  return body.data as DevAutopilotApplication;
}

async function fetchOwnerIdentity(token: string, companyId: string): Promise<TenantOwnerIdentity> {
  const response = await fetch(`${PLATFORM_API_BASE}/tenants/${encodeURIComponent(companyId)}/owner-identity`, { headers: { Authorization: `Bearer ${token}` } });
  const { body } = await safeFetchJson(response);
  if (!response.ok || !body?.success) throw new Error(body?.message ?? `HTTP ${response.status}`);
  return body.data as TenantOwnerIdentity;
}
