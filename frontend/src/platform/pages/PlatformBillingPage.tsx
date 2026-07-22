import { useEffect, useMemo, useState } from "react";
import { ArrowLeft, ExternalLink } from "lucide-react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { LS_PLATFORM_TOKEN, PLATFORM_API_BASE } from "../../constants";

type BillingEdition = {
  editionCode: string;
  deploymentMode: string;
  displayName: string;
  description: string;
  enabled: boolean;
  operationSeatLimit: number | null;
  builderSeatLimit: number | null;
  agentLimit: number | null;
  skillLimit: number | null;
  workflowLimit: number | null;
  knowledgeBaseLimit: number | null;
  documentLimit: number | null;
  chunkLimit: number | null;
  knowledgeStorageMb: number | null;
  openApiQps: number | null;
  openApiConcurrency: number | null;
  openApiCredentialLimit: number | null;
  connectorLimit: number | null;
  meetingMinutesConcurrency: number | null;
  traceRetentionDays: number | null;
  auditRetentionDays: number | null;
  environmentLimit: number | null;
  includedCredits: number;
  overageMode: string;
  billingTypePolicy: string;
  slaTierCode: string;
  topUpPolicy: string;
  localModelTokenPolicy: string;
  platformPaidResourcePolicy: string;
  packageCodes: string[];
  versionNo: number;
  changeReason: string;
  updatedBy: string;
  updatedAt: string;
};

type BillingPackage = {
  packageCode: string;
  deploymentMode: string;
  packageType: string;
  displayName: string;
  description: string;
  enabled: boolean;
  configJson: string;
  versionNo: number;
  changeReason: string;
  updatedBy: string;
  updatedAt: string;
};

type BillingCatalog = {
  currentDeploymentMode: string;
  currentDeploymentModeLabel: string;
  editions: BillingEdition[];
  packages: BillingPackage[];
  overageModes: string[];
  billingTypes: string[];
  packageTypes: string[];
};

type EditionForm = Omit<BillingEdition, "versionNo" | "changeReason" | "updatedBy" | "updatedAt" | "deploymentMode" | "editionCode"> & {
  reason: string;
  packageCodesText: string;
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

function deploymentLabel(mode: string): string {
  switch (mode) {
    case "saas":
      return "SaaS";
    case "private_deployment":
      return "私有化";
    default:
      return mode || "未知";
  }
}

function overageLabel(mode: string): string {
  switch (mode) {
    case "auto_charge":
      return "自动超额";
    case "soft_limit":
      return "软限制";
    case "hard_limit":
      return "硬限制";
    case "contract_only":
      return "合同额度";
    default:
      return mode || "未配置";
  }
}

function billingTypeLabel(type: string): string {
  switch (type) {
    case "customer_paid":
      return "客户侧成本";
    case "platform_paid":
      return "平台代付";
    case "included":
      return "套餐内";
    case "non_billable":
      return "不计费";
    default:
      return type || "未配置";
  }
}

function packageTypeLabel(type: string): string {
  switch (type) {
    case "capacity":
      return "容量包";
    case "module":
      return "模块包";
    case "service":
      return "服务包";
    case "sla":
      return "SLA";
    case "credits":
      return "Credits";
    default:
      return type || "未分类";
  }
}

function formatLimit(value: number | null | undefined): string {
  return value == null ? "合同约定" : value.toLocaleString("zh-CN");
}

function formatDateTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value || "—";
  return new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(date);
}

function toNumberOrNull(value: string): number | null {
  if (value.trim() === "") return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : null;
}

function editionToForm(edition: BillingEdition): EditionForm {
  return {
    displayName: edition.displayName,
    description: edition.description,
    enabled: edition.enabled,
    operationSeatLimit: edition.operationSeatLimit,
    builderSeatLimit: edition.builderSeatLimit,
    agentLimit: edition.agentLimit,
    skillLimit: edition.skillLimit,
    workflowLimit: edition.workflowLimit,
    knowledgeBaseLimit: edition.knowledgeBaseLimit,
    documentLimit: edition.documentLimit,
    chunkLimit: edition.chunkLimit,
    knowledgeStorageMb: edition.knowledgeStorageMb,
    openApiQps: edition.openApiQps,
    openApiConcurrency: edition.openApiConcurrency,
    openApiCredentialLimit: edition.openApiCredentialLimit,
    connectorLimit: edition.connectorLimit,
    meetingMinutesConcurrency: edition.meetingMinutesConcurrency,
    traceRetentionDays: edition.traceRetentionDays,
    auditRetentionDays: edition.auditRetentionDays,
    environmentLimit: edition.environmentLimit,
    includedCredits: edition.includedCredits,
    overageMode: edition.overageMode,
    billingTypePolicy: edition.billingTypePolicy,
    slaTierCode: edition.slaTierCode,
    topUpPolicy: edition.topUpPolicy,
    localModelTokenPolicy: edition.localModelTokenPolicy,
    platformPaidResourcePolicy: edition.platformPaidResourcePolicy,
    packageCodes: edition.packageCodes,
    packageCodesText: edition.packageCodes.join(", "),
    reason: "",
  };
}

function numericInput(value: number | null, onChange: (value: number | null) => void) {
  return (
    <input
      value={value ?? ""}
      placeholder="合同约定"
      inputMode="numeric"
      onChange={(event) => onChange(toNumberOrNull(event.target.value))}
    />
  );
}

export const platformBillingLabels = {
  deploymentLabel,
  overageLabel,
  billingTypeLabel,
  packageTypeLabel,
  formatLimit,
};

export default function PlatformBillingPage() {
  const token = readToken();
  const navigate = useNavigate();
  const location = useLocation();
  const { editionCode, packageCode } = useParams<{ editionCode: string; packageCode: string }>();
  const view = packageCode ? "package-detail" : editionCode ? "edition" : location.pathname.endsWith("/packages") ? "packages" : "catalog";
  const [catalog, setCatalog] = useState<BillingCatalog | null>(null);
  const [modeFilter, setModeFilter] = useState("private_deployment");
  const [selectedEditionCode, setSelectedEditionCode] = useState("");
  const [selectedPackageCode, setSelectedPackageCode] = useState("");
  const [editionForm, setEditionForm] = useState<EditionForm | null>(null);
  const [packageForm, setPackageForm] = useState({ displayName: "", description: "", packageType: "capacity", enabled: true, configJson: "{}", reason: "" });
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  const selectedEdition = useMemo(
    () => catalog?.editions.find((item) => item.editionCode === selectedEditionCode) ?? null,
    [catalog, selectedEditionCode],
  );
  const selectedPackage = useMemo(
    () => catalog?.packages.find((item) => item.packageCode === selectedPackageCode) ?? null,
    [catalog, selectedPackageCode],
  );

  async function loadCatalog(preferredEdition?: string, preferredPackage?: string) {
    if (!token) return;
    setError("");
    try {
      const res = await fetch(`${PLATFORM_API_BASE}/billing/catalog?deploymentMode=${encodeURIComponent(modeFilter)}`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      const json = await res.json();
      if (!res.ok || !json.success) throw new Error(json.message || "加载计费配置失败");
      const nextCatalog = json.data as BillingCatalog;
      setCatalog(nextCatalog);
      const nextEditionCode = preferredEdition || editionCode || selectedEditionCode || nextCatalog.editions[0]?.editionCode || "";
      setSelectedEditionCode(nextEditionCode);
      const nextEdition = nextCatalog.editions.find((item) => item.editionCode === nextEditionCode) ?? nextCatalog.editions[0] ?? null;
      setEditionForm(nextEdition ? editionToForm(nextEdition) : null);
      const nextPackageCode = preferredPackage || packageCode || selectedPackageCode || nextCatalog.packages[0]?.packageCode || "";
      setSelectedPackageCode(nextPackageCode);
      const nextPackage = nextCatalog.packages.find((item) => item.packageCode === nextPackageCode) ?? nextCatalog.packages[0] ?? null;
      if (nextPackage) {
        setPackageForm({
          displayName: nextPackage.displayName,
          description: nextPackage.description,
          packageType: nextPackage.packageType,
          enabled: nextPackage.enabled,
          configJson: nextPackage.configJson,
          reason: "",
        });
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "加载计费配置失败");
    }
  }

  useEffect(() => {
    void loadCatalog();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, modeFilter, editionCode, packageCode]);

  useEffect(() => {
    if (selectedEdition) setEditionForm(editionToForm(selectedEdition));
  }, [selectedEdition]);

  useEffect(() => {
    if (!selectedPackage) return;
    setPackageForm({
      displayName: selectedPackage.displayName,
      description: selectedPackage.description,
      packageType: selectedPackage.packageType,
      enabled: selectedPackage.enabled,
      configJson: selectedPackage.configJson,
      reason: "",
    });
  }, [selectedPackage]);

  async function saveEdition() {
    if (!selectedEdition || !editionForm) return;
    setSaving(true);
    setError("");
    setMessage("");
    try {
      const packageCodes = editionForm.packageCodesText.split(",").map((item) => item.trim()).filter(Boolean);
      const res = await fetch(`${PLATFORM_API_BASE}/billing/editions/${selectedEdition.editionCode}`, {
        method: "PUT",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ ...editionForm, packageCodes }),
      });
      const json = await res.json();
      if (!res.ok || !json.success) throw new Error(json.message || "保存版本配置失败");
      setMessage(`已保存 ${selectedEdition.displayName}，新版本 v${json.data.versionNo}。`);
      await loadCatalog(selectedEdition.editionCode, selectedPackageCode);
    } catch (err) {
      setError(err instanceof Error ? err.message : "保存版本配置失败");
    } finally {
      setSaving(false);
    }
  }

  async function savePackage() {
    if (!selectedPackage) return;
    setSaving(true);
    setError("");
    setMessage("");
    try {
      JSON.parse(packageForm.configJson);
      const res = await fetch(`${PLATFORM_API_BASE}/billing/packages/${selectedPackage.packageCode}`, {
        method: "PUT",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify(packageForm),
      });
      const json = await res.json();
      if (!res.ok || !json.success) throw new Error(json.message || "保存包配置失败");
      setMessage(`已保存 ${selectedPackage.displayName}，新版本 v${json.data.versionNo}。`);
      await loadCatalog(selectedEditionCode, selectedPackage.packageCode);
    } catch (err) {
      setError(err instanceof Error ? err.message : "保存包配置失败");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="admin-page skills-catalog platform-page platform-billing-page">
      <header className="skills-catalog__header platform-page-head">
        <div className="platform-page-head__main">
          {view !== "catalog" ? <button type="button" className="platform-page__back" onClick={() => navigate(view === "edition" ? "/platform/billing" : "/platform/billing/packages")}><ArrowLeft size={15} />返回{view === "edition" ? "套餐目录" : "加购包目录"}</button> : null}
          <h1 className="skills-catalog__title">计费版本配置</h1>
          <p className="subtle skills-catalog__subtitle">{view === "catalog" ? "浏览 SaaS 与私有化套餐，进入单独的版本页面修改资源和计费策略。" : view === "edition" ? "维护当前套餐的资源、计费与适用包策略。" : "维护容量包、模块包、服务包与 Credits 策略。"}</p>
        </div>
        <div className="platform-page-head__aside">
          <span className="platform-inline-stat">当前模式 {deploymentLabel(catalog?.currentDeploymentMode ?? "")}</span>
          <span className="platform-inline-stat">版本 {catalog?.editions.length ?? 0}</span>
          <span className="platform-inline-stat">包 {catalog?.packages.length ?? 0}</span>
        </div>
      </header>

      <div className="platform-billing__toolbar" role="group" aria-label="部署模式">
        <button type="button" className={modeFilter === "private_deployment" ? "active" : ""} onClick={() => setModeFilter("private_deployment")}>
          私有化
        </button>
        <button type="button" className={modeFilter === "saas" ? "active" : ""} onClick={() => setModeFilter("saas")}>
          SaaS
        </button>
      </div>

      {error ? <div className="platform-console__banner platform-console__banner--error">{error}</div> : null}
      {message ? <div className="platform-console__banner platform-console__banner--success">{message}</div> : null}

      <div className="platform-console__grid platform-billing__grid">
        {view === "catalog" ? <section className="platform-console__panel skills-table-wrap">
          <table className="skills-data-table platform-billing__edition-table">
            <thead>
              <tr>
                <th>版本</th>
                <th>席位</th>
                <th>容量</th>
                <th>计费策略</th>
                <th>版本号</th>
                <th aria-label="操作" />
              </tr>
            </thead>
            <tbody>
              {(catalog?.editions ?? []).map((edition) => (
                <tr
                  key={edition.editionCode}
                  className="platform-console__select-row"
                  onClick={() => navigate(`/platform/billing/editions/${encodeURIComponent(edition.editionCode)}`)}
                >
                  <td>
                    <div className="skills-data-table__skill-name">{edition.displayName}</div>
                    <div className="skills-data-table__sub">{deploymentLabel(edition.deploymentMode)} · {edition.enabled ? "启用" : "停用"}</div>
                  </td>
                  <td>{formatLimit(edition.operationSeatLimit)} / {formatLimit(edition.builderSeatLimit)}</td>
                  <td>{formatLimit(edition.agentLimit)} Agent · {formatLimit(edition.knowledgeStorageMb)} MB</td>
                  <td>{billingTypeLabel(edition.billingTypePolicy)} · {overageLabel(edition.overageMode)}</td>
                  <td className="skills-data-table__mono">v{edition.versionNo}</td>
                  <td><button type="button" className="platform-table-link" onClick={(event) => { event.stopPropagation(); navigate(`/platform/billing/editions/${encodeURIComponent(edition.editionCode)}`); }}>设置 <ExternalLink size={13} /></button></td>
                </tr>
              ))}
              {!catalog?.editions.length ? (
                <tr>
                  <td colSpan={6} className="skills-data-table__summary">当前部署模式还没有计费版本。</td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </section> : null}

        {view === "edition" ? <section className="platform-console__panel platform-console__panel--detail">
          {selectedEdition && editionForm ? (
            <div className="platform-console__stack">
              <div className="platform-console__section">
                <p className="platform-section-label">版本配置</p>
                <h2 className="platform-console__heading">{selectedEdition.displayName}</h2>
                <p className="skills-data-table__summary">
                  {selectedEdition.editionCode} · v{selectedEdition.versionNo} · {formatDateTime(selectedEdition.updatedAt)}
                </p>
              </div>
              <div className="platform-billing__form-grid">
                <label>
                  展示名
                  <input value={editionForm.displayName} onChange={(e) => setEditionForm((prev) => prev && { ...prev, displayName: e.target.value })} />
                </label>
                <label className="platform-console__checkbox">
                  <input type="checkbox" checked={editionForm.enabled} onChange={(e) => setEditionForm((prev) => prev && { ...prev, enabled: e.target.checked })} />
                  平台启用
                </label>
                <label>
                  操作席位
                  {numericInput(editionForm.operationSeatLimit, (value) => setEditionForm((prev) => prev && { ...prev, operationSeatLimit: value }))}
                </label>
                <label>
                  构建席位
                  {numericInput(editionForm.builderSeatLimit, (value) => setEditionForm((prev) => prev && { ...prev, builderSeatLimit: value }))}
                </label>
                <label>
                  Agent 数量
                  {numericInput(editionForm.agentLimit, (value) => setEditionForm((prev) => prev && { ...prev, agentLimit: value }))}
                </label>
                <label>
                  Skill 数量
                  {numericInput(editionForm.skillLimit, (value) => setEditionForm((prev) => prev && { ...prev, skillLimit: value }))}
                </label>
                <label>
                  Workflow 数量
                  {numericInput(editionForm.workflowLimit, (value) => setEditionForm((prev) => prev && { ...prev, workflowLimit: value }))}
                </label>
                <label>
                  知识库数量
                  {numericInput(editionForm.knowledgeBaseLimit, (value) => setEditionForm((prev) => prev && { ...prev, knowledgeBaseLimit: value }))}
                </label>
                <label>
                  文档数
                  {numericInput(editionForm.documentLimit, (value) => setEditionForm((prev) => prev && { ...prev, documentLimit: value }))}
                </label>
                <label>
                  Chunk 数
                  {numericInput(editionForm.chunkLimit, (value) => setEditionForm((prev) => prev && { ...prev, chunkLimit: value }))}
                </label>
                <label>
                  知识容量 MB
                  {numericInput(editionForm.knowledgeStorageMb, (value) => setEditionForm((prev) => prev && { ...prev, knowledgeStorageMb: value }))}
                </label>
                <label>
                  Open API QPS
                  {numericInput(editionForm.openApiQps, (value) => setEditionForm((prev) => prev && { ...prev, openApiQps: value }))}
                </label>
                <label>
                  API 并发
                  {numericInput(editionForm.openApiConcurrency, (value) => setEditionForm((prev) => prev && { ...prev, openApiConcurrency: value }))}
                </label>
                <label>
                  API 凭证数
                  {numericInput(editionForm.openApiCredentialLimit, (value) => setEditionForm((prev) => prev && { ...prev, openApiCredentialLimit: value }))}
                </label>
                <label>
                  连接器数
                  {numericInput(editionForm.connectorLimit, (value) => setEditionForm((prev) => prev && { ...prev, connectorLimit: value }))}
                </label>
                <label>
                  听记并发
                  {numericInput(editionForm.meetingMinutesConcurrency, (value) => setEditionForm((prev) => prev && { ...prev, meetingMinutesConcurrency: value }))}
                </label>
                <label>
                  Trace 保留天数
                  {numericInput(editionForm.traceRetentionDays, (value) => setEditionForm((prev) => prev && { ...prev, traceRetentionDays: value }))}
                </label>
                <label>
                  审计保留天数
                  {numericInput(editionForm.auditRetentionDays, (value) => setEditionForm((prev) => prev && { ...prev, auditRetentionDays: value }))}
                </label>
                <label>
                  环境数
                  {numericInput(editionForm.environmentLimit, (value) => setEditionForm((prev) => prev && { ...prev, environmentLimit: value }))}
                </label>
                <label>
                  包含 Credits
                  <input value={editionForm.includedCredits ?? 0} inputMode="decimal" onChange={(e) => setEditionForm((prev) => prev && { ...prev, includedCredits: Number(e.target.value) || 0 })} />
                </label>
                <label>
                  超额模式
                  <select value={editionForm.overageMode} onChange={(e) => setEditionForm((prev) => prev && { ...prev, overageMode: e.target.value })}>
                    {catalog?.overageModes.map((mode) => <option key={mode} value={mode}>{overageLabel(mode)}</option>)}
                  </select>
                </label>
                <label>
                  计费类型
                  <select value={editionForm.billingTypePolicy} onChange={(e) => setEditionForm((prev) => prev && { ...prev, billingTypePolicy: e.target.value })}>
                    {catalog?.billingTypes.map((type) => <option key={type} value={type}>{billingTypeLabel(type)}</option>)}
                  </select>
                </label>
                <label>
                  SLA 层级
                  <input value={editionForm.slaTierCode} onChange={(e) => setEditionForm((prev) => prev && { ...prev, slaTierCode: e.target.value })} />
                </label>
                <label>
                  加购策略
                  <input value={editionForm.topUpPolicy} onChange={(e) => setEditionForm((prev) => prev && { ...prev, topUpPolicy: e.target.value })} />
                </label>
                <label className="platform-console__field--full">
                  关联包编码
                  <input value={editionForm.packageCodesText} onChange={(e) => setEditionForm((prev) => prev && { ...prev, packageCodesText: e.target.value })} />
                </label>
                <label className="platform-console__field--full">
                  本地模型 token 策略
                  <textarea rows={3} value={editionForm.localModelTokenPolicy} onChange={(e) => setEditionForm((prev) => prev && { ...prev, localModelTokenPolicy: e.target.value })} />
                </label>
                <label className="platform-console__field--full">
                  平台代付资源策略
                  <textarea rows={3} value={editionForm.platformPaidResourcePolicy} onChange={(e) => setEditionForm((prev) => prev && { ...prev, platformPaidResourcePolicy: e.target.value })} />
                </label>
                <label className="platform-console__field--full">
                  说明
                  <textarea rows={3} value={editionForm.description} onChange={(e) => setEditionForm((prev) => prev && { ...prev, description: e.target.value })} />
                </label>
                <label className="platform-console__field--full">
                  变更原因
                  <textarea rows={3} value={editionForm.reason} onChange={(e) => setEditionForm((prev) => prev && { ...prev, reason: e.target.value })} />
                </label>
              </div>
              <div className="platform-console__actions">
                <button type="button" className="platform-button platform-button--primary" disabled={saving || !editionForm.reason.trim()} onClick={saveEdition}>保存版本</button>
              </div>
            </div>
          ) : (
            <p className="skills-data-table__summary">请选择一个版本。</p>
          )}
        </section> : null}

        {view === "packages" ? <section className="platform-console__panel platform-console__panel--full">
          <div className="platform-billing__package-layout">
            <div className="skills-table-wrap">
              <table className="skills-data-table">
                <thead>
                  <tr>
                    <th>包</th>
                    <th>类型</th>
                    <th>状态</th>
                    <th>版本</th>
                    <th aria-label="操作" />
                  </tr>
                </thead>
                <tbody>
                  {(catalog?.packages ?? []).map((item) => (
                    <tr
                      key={item.packageCode}
                      className="platform-console__select-row"
                      onClick={() => navigate(`/platform/billing/packages/${encodeURIComponent(item.packageCode)}`)}
                    >
                      <td>
                        <div className="skills-data-table__skill-name">{item.displayName}</div>
                        <div className="skills-data-table__sub">{item.packageCode}</div>
                      </td>
                      <td>{packageTypeLabel(item.packageType)}</td>
                      <td>{item.enabled ? "启用" : "停用"}</td>
                      <td className="skills-data-table__mono">v{item.versionNo}</td>
                      <td><button type="button" className="platform-table-link" onClick={(event) => { event.stopPropagation(); navigate(`/platform/billing/packages/${encodeURIComponent(item.packageCode)}`); }}>设置 <ExternalLink size={13} /></button></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {null}
            {selectedPackage ? null : null}
          </div>
        </section> : null}
        {view === "package-detail" ? <section className="platform-console__panel platform-console__panel--detail">
          <div className="platform-billing__package-layout">
            {selectedPackage ? (
              <div className="platform-billing__package-form">
                <h3 className="platform-console__subheading">{selectedPackage.displayName}</h3>
                <label>
                  展示名
                  <input value={packageForm.displayName} onChange={(e) => setPackageForm((prev) => ({ ...prev, displayName: e.target.value }))} />
                </label>
                <label>
                  类型
                  <select value={packageForm.packageType} onChange={(e) => setPackageForm((prev) => ({ ...prev, packageType: e.target.value }))}>
                    {catalog?.packageTypes.map((type) => <option key={type} value={type}>{packageTypeLabel(type)}</option>)}
                  </select>
                </label>
                <label className="platform-console__checkbox">
                  <input type="checkbox" checked={packageForm.enabled} onChange={(e) => setPackageForm((prev) => ({ ...prev, enabled: e.target.checked }))} />
                  平台启用
                </label>
                <label>
                  说明
                  <textarea rows={3} value={packageForm.description} onChange={(e) => setPackageForm((prev) => ({ ...prev, description: e.target.value }))} />
                </label>
                <label>
                  配置 JSON
                  <textarea rows={7} value={packageForm.configJson} onChange={(e) => setPackageForm((prev) => ({ ...prev, configJson: e.target.value }))} />
                </label>
                <label>
                  变更原因
                  <textarea rows={3} value={packageForm.reason} onChange={(e) => setPackageForm((prev) => ({ ...prev, reason: e.target.value }))} />
                </label>
                <div className="platform-console__actions">
                  <button type="button" className="platform-button platform-button--primary" disabled={saving || !packageForm.reason.trim()} onClick={savePackage}>保存包</button>
                </div>
              </div>
            ) : <p className="skills-data-table__summary">未找到该加购包。</p>}
          </div>
        </section> : null}
      </div>
    </div>
  );
}
