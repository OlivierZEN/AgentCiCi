import { useEffect, useState } from "react";
import { Bot, Boxes, CheckCircle2, CircleDashed, Database, ShieldCheck } from "lucide-react";
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
type DevAutopilotApplication = {
  enabled: boolean;
  templateVersion?: string | null;
  desiredState: string;
  actualState: string;
  sematticeTenantId?: string | null;
  metadataVersionId?: string | null;
  resources: Array<{ logicalRole: string; resourceType: string; resourceAlias: string; displayName: string; lifecycleState: string; primary: boolean }>;
};

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
  const [pmName, setPmName] = useState("");
  const [pmAlias, setPmAlias] = useState("product-manager");
  const [ownerMemberId, setOwnerMemberId] = useState("");
  const [developerName, setDeveloperName] = useState("");
  const [developerAlias, setDeveloperAlias] = useState("developer-1");
  const [developerOwnerMemberId, setDeveloperOwnerMemberId] = useState("");

  useEffect(() => {
    if (!isPlatformCompanyId(companyId)) {
      navigate("/platform/tenants", { replace: true });
      return;
    }
    if (!token) return;
    void Promise.all([fetchTenantDetail(token, companyId), fetchSematticeProvisioning(token, companyId), fetchDevAutopilot(token, companyId)])
      .then(([tenantDetail, provisioning, application]) => {
        setDetail(tenantDetail);
        setSematticeProvisioningState(provisioning.state === "RESERVED" ? "PROVISIONING" : provisioning.state);
        setDevAutopilot(application);
        setPmName((current) => current || `${tenantDetail.tenant.name} 产品经理`);
      })
      .catch((err) => setError(err instanceof Error ? err.message : "加载租户应用失败"));
  }, [token, companyId, navigate]);

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
    if (!companyId || !devAutopilot || !pmName.trim() || !pmAlias.trim() || !ownerMemberId.trim()) return;
    setBusy(true); setError(""); setMessage("");
    try {
      const response = await fetch(`${PLATFORM_API_BASE}/tenants/${encodeURIComponent(companyId)}/applications/devautopilot/activations`, {
        method: "POST", headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({ idempotencyKey: `devautopilot-${companyId}-${globalThis.crypto?.randomUUID?.() ?? Date.now()}`, productManagerName: pmName.trim(), productManagerAlias: pmAlias.trim(), ownerMemberId: ownerMemberId.trim() }),
      });
      const { body } = await safeFetchJson(response);
      if (!response.ok || !body?.success) throw new Error(body?.message ?? `HTTP ${response.status}`);
      setDevAutopilot(body.data as DevAutopilotApplication);
      setMessage("DevAutopilot 已开通。产品经理身份与租户数据基线均已独立初始化。");
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

  async function addDeveloper() {
    if (!companyId || !developerName.trim() || !developerAlias.trim() || !developerOwnerMemberId.trim()) return;
    setBusy(true); setError("");
    try {
      const response = await fetch(`${PLATFORM_API_BASE}/tenants/${encodeURIComponent(companyId)}/applications/devautopilot/developer-principals`, { method: "POST", headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" }, body: JSON.stringify({ displayName: developerName.trim(), resourceAlias: developerAlias.trim(), ownerMemberId: developerOwnerMemberId.trim() }) });
      const { body } = await safeFetchJson(response);
      if (!response.ok || !body?.success) throw new Error(body?.message ?? `HTTP ${response.status}`);
      setDevAutopilot((current) => current ? { ...current, resources: [...current.resources, body.data as DevAutopilotApplication["resources"][number]] } : current);
      setDeveloperName(""); setDeveloperAlias("developer-1"); setDeveloperOwnerMemberId(""); setMessage("已新增租户独立的开发者机器身份，密钥仅在受管创建流程中一次返回。");
    } catch (err) { setError(err instanceof Error ? err.message : "新增开发者失败"); } finally { setBusy(false); }
  }

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
                      <div><dt>Company ID</dt><dd>{detail.tenant.companyId}</dd></div>
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
                        <p>租户独立的产品经理智能体、机器身份与研发交付数据模型</p>
                      </div>
                      <span className={`tenant-application-card__state tenant-application-card__state--${devAutopilot?.actualState === "ACTIVE" ? "healthy" : "not_provisioned"}`}><CheckCircle2 size={14} aria-hidden="true" />{devAutopilot?.actualState === "ACTIVE" ? "运行中" : devAutopilot?.actualState === "SUSPENDED" ? "已暂停" : "未开通"}</span>
                    </div>
                    {devAutopilot?.enabled ? <>
                      <dl className="tenant-application-card__facts">
                        <div><dt>模板版本</dt><dd>{devAutopilot.templateVersion}</dd></div><div><dt>数据租户</dt><dd>{devAutopilot.sematticeTenantId ?? "-"}</dd></div>
                        <div><dt>产品经理</dt><dd>{devAutopilot.resources.find((item) => item.resourceType === "SERVICE_PRINCIPAL")?.displayName ?? "-"}</dd></div><div><dt>资源状态</dt><dd>{devAutopilot.resources.length} 个独立资源</dd></div>
                      </dl>
                      <div className="tenant-application-card__foot tenant-application-card__foot--action"><span><ShieldCheck size={14} aria-hidden="true" />关闭仅暂停本租户运行入口，不删除数据</span><button type="button" className="platform-button platform-button--primary tenant-application-card__primary-action" disabled={busy} onClick={() => void changeDevAutopilotState(devAutopilot.actualState === "SUSPENDED" ? "resumptions" : "suspensions")}>{devAutopilot.actualState === "SUSPENDED" ? "恢复运行" : "暂停应用"}</button></div>
                      {devAutopilot.actualState === "ACTIVE" ? <div className="tenant-application-card__setup"><input value={developerName} onChange={(event) => setDeveloperName(event.target.value)} aria-label="开发者显示名称" placeholder="开发者显示名称" /><input value={developerAlias} onChange={(event) => setDeveloperAlias(event.target.value)} aria-label="开发者别名" placeholder="developer-1" /><input value={developerOwnerMemberId} onChange={(event) => setDeveloperOwnerMemberId(event.target.value)} aria-label="开发者负责人成员 ID" placeholder="负责人成员 ID" /><button type="button" className="platform-button platform-button--primary tenant-application-card__primary-action" disabled={busy || !developerName.trim() || !developerAlias.trim() || !developerOwnerMemberId.trim()} onClick={() => void addDeveloper()}>新增开发者</button></div> : null}
                    </> : <div className="tenant-application-card__foot tenant-application-card__foot--action">
                      <span>需先开通 Semattice；产品经理名称可由租户定义。</span>
                      <div className="tenant-application-card__setup"><input value={pmName} onChange={(event) => setPmName(event.target.value)} aria-label="产品经理显示名称" placeholder="产品经理显示名称" /><input value={pmAlias} onChange={(event) => setPmAlias(event.target.value)} aria-label="产品经理别名" placeholder="product-manager" /><input value={ownerMemberId} onChange={(event) => setOwnerMemberId(event.target.value)} aria-label="负责人成员 ID" placeholder="负责人成员 ID" /><button type="button" className="platform-button platform-button--primary tenant-application-card__primary-action" disabled={busy || sematticeProvisioningState !== "PROVISIONED" || !pmName.trim() || !pmAlias.trim() || !ownerMemberId.trim()} onClick={() => void activateDevAutopilot()}>开通 DevAutopilot</button></div>
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
    </div>
  );
}

async function fetchDevAutopilot(token: string, companyId: string): Promise<DevAutopilotApplication> {
  const response = await fetch(`${PLATFORM_API_BASE}/tenants/${encodeURIComponent(companyId)}/applications/devautopilot`, { headers: { Authorization: `Bearer ${token}` } });
  const { body } = await safeFetchJson(response);
  if (!response.ok || !body?.success) throw new Error(body?.message ?? `HTTP ${response.status}`);
  return body.data as DevAutopilotApplication;
}
