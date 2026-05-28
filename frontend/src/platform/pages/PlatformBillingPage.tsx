import { useEffect, useMemo, useState } from "react";
import { LS_PLATFORM_TOKEN, PLATFORM_API_BASE } from "../../constants";

export type BillingConfigItem = {
  id: number;
  itemType: string;
  itemCode: string;
  displayName: string;
  deploymentMode: string;
  versionNo: number;
  publishStatus: string;
  enabled: boolean;
  billingTypePolicy: string;
  includedCredits: number;
  operationSeatLimit?: number | null;
  builderSeatLimit?: number | null;
  agentLimit?: number | null;
  skillWorkflowLimit?: number | null;
  knowledgeCapacityGb?: number | null;
  openApiQps?: number | null;
  openApiConcurrency?: number | null;
  openApiCredentialLimit?: number | null;
  connectorLimit?: number | null;
  meetingConcurrency?: number | null;
  traceRetentionDays?: number | null;
  auditRetentionDays?: number | null;
  environmentLimit?: number | null;
  overageMode: string;
  slaTierCode?: string | null;
  addonCategory?: string | null;
  pricingUnit?: string | null;
  policyJson?: string | null;
  changeReason: string;
  updatedAt: string;
  publishedAt?: string | null;
  latestVersionNo: number;
  publishedVersionNo?: number | null;
  versionCount: number;
};

type BillingConfigForm = {
  itemType: string;
  itemCode: string;
  displayName: string;
  deploymentMode: string;
  enabled: boolean;
  billingTypePolicy: string;
  includedCredits: string;
  operationSeatLimit: string;
  builderSeatLimit: string;
  agentLimit: string;
  skillWorkflowLimit: string;
  knowledgeCapacityGb: string;
  openApiQps: string;
  openApiConcurrency: string;
  openApiCredentialLimit: string;
  connectorLimit: string;
  meetingConcurrency: string;
  traceRetentionDays: string;
  auditRetentionDays: string;
  environmentLimit: string;
  overageMode: string;
  slaTierCode: string;
  addonCategory: string;
  pricingUnit: string;
  policyJson: string;
  changeReason: string;
};

const defaultForm: BillingConfigForm = {
  itemType: "PLAN",
  itemCode: "",
  displayName: "",
  deploymentMode: "saas",
  enabled: true,
  billingTypePolicy: "platform_paid",
  includedCredits: "0",
  operationSeatLimit: "",
  builderSeatLimit: "",
  agentLimit: "",
  skillWorkflowLimit: "",
  knowledgeCapacityGb: "",
  openApiQps: "",
  openApiConcurrency: "",
  openApiCredentialLimit: "",
  connectorLimit: "",
  meetingConcurrency: "",
  traceRetentionDays: "",
  auditRetentionDays: "",
  environmentLimit: "",
  overageMode: "soft_limit",
  slaTierCode: "",
  addonCategory: "",
  pricingUnit: "",
  policyJson: "{}",
  changeReason: "TASK-143 平台计费配置调整",
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

function numeric(value: string): number | null {
  const cleaned = value.trim();
  if (!cleaned) return null;
  const parsed = Number(cleaned);
  return Number.isFinite(parsed) ? parsed : null;
}

export function itemTypeLabel(type: string): string {
  switch (type) {
    case "PLAN":
      return "版本套餐";
    case "CAPACITY_PACK":
      return "容量包";
    case "MODULE_PACK":
      return "模块包";
    case "SERVICE_PACK":
      return "服务包";
    case "SLA_TIER":
      return "SLA";
    case "CREDITS_POLICY":
      return "Credits 策略";
    default:
      return type || "未知";
  }
}

export function deploymentModeLabel(mode: string): string {
  switch (mode) {
    case "saas":
      return "SaaS";
    case "private_deployment":
      return "私有化";
    case "all":
      return "通用";
    default:
      return mode || "未知";
  }
}

export function publishStatusLabel(status: string): string {
  switch (status) {
    case "PUBLISHED":
      return "已发布";
    case "DRAFT":
      return "草稿";
    case "SUPERSEDED":
      return "已替换";
    default:
      return status || "未知";
  }
}

export function formFromItem(item: BillingConfigItem): BillingConfigForm {
  return {
    itemType: item.itemType,
    itemCode: item.itemCode,
    displayName: item.displayName,
    deploymentMode: item.deploymentMode,
    enabled: item.enabled,
    billingTypePolicy: item.billingTypePolicy,
    includedCredits: String(item.includedCredits ?? 0),
    operationSeatLimit: item.operationSeatLimit == null ? "" : String(item.operationSeatLimit),
    builderSeatLimit: item.builderSeatLimit == null ? "" : String(item.builderSeatLimit),
    agentLimit: item.agentLimit == null ? "" : String(item.agentLimit),
    skillWorkflowLimit: item.skillWorkflowLimit == null ? "" : String(item.skillWorkflowLimit),
    knowledgeCapacityGb: item.knowledgeCapacityGb == null ? "" : String(item.knowledgeCapacityGb),
    openApiQps: item.openApiQps == null ? "" : String(item.openApiQps),
    openApiConcurrency: item.openApiConcurrency == null ? "" : String(item.openApiConcurrency),
    openApiCredentialLimit: item.openApiCredentialLimit == null ? "" : String(item.openApiCredentialLimit),
    connectorLimit: item.connectorLimit == null ? "" : String(item.connectorLimit),
    meetingConcurrency: item.meetingConcurrency == null ? "" : String(item.meetingConcurrency),
    traceRetentionDays: item.traceRetentionDays == null ? "" : String(item.traceRetentionDays),
    auditRetentionDays: item.auditRetentionDays == null ? "" : String(item.auditRetentionDays),
    environmentLimit: item.environmentLimit == null ? "" : String(item.environmentLimit),
    overageMode: item.overageMode,
    slaTierCode: item.slaTierCode ?? "",
    addonCategory: item.addonCategory ?? "",
    pricingUnit: item.pricingUnit ?? "",
    policyJson: item.policyJson ?? "{}",
    changeReason: item.changeReason || defaultForm.changeReason,
  };
}

export function buildBillingPayload(form: BillingConfigForm) {
  return {
    itemType: form.itemType,
    itemCode: form.itemCode.trim(),
    displayName: form.displayName.trim(),
    deploymentMode: form.deploymentMode,
    enabled: form.enabled,
    billingTypePolicy: form.billingTypePolicy,
    includedCredits: numeric(form.includedCredits) ?? 0,
    operationSeatLimit: numeric(form.operationSeatLimit),
    builderSeatLimit: numeric(form.builderSeatLimit),
    agentLimit: numeric(form.agentLimit),
    skillWorkflowLimit: numeric(form.skillWorkflowLimit),
    knowledgeCapacityGb: numeric(form.knowledgeCapacityGb),
    openApiQps: numeric(form.openApiQps),
    openApiConcurrency: numeric(form.openApiConcurrency),
    openApiCredentialLimit: numeric(form.openApiCredentialLimit),
    connectorLimit: numeric(form.connectorLimit),
    meetingConcurrency: numeric(form.meetingConcurrency),
    traceRetentionDays: numeric(form.traceRetentionDays),
    auditRetentionDays: numeric(form.auditRetentionDays),
    environmentLimit: numeric(form.environmentLimit),
    overageMode: form.overageMode,
    slaTierCode: form.slaTierCode.trim() || null,
    addonCategory: form.addonCategory.trim() || null,
    pricingUnit: form.pricingUnit.trim() || null,
    policyJson: form.policyJson.trim() || "{}",
    changeReason: form.changeReason.trim(),
  };
}

function formatTs(ts?: string | null): string {
  if (!ts) return "未发布";
  const d = new Date(ts);
  if (Number.isNaN(d.getTime())) return ts;
  return d.toLocaleString();
}

function billingTypeLabel(value: string): string {
  switch (value) {
    case "platform_paid":
      return "平台代付资源";
    case "customer_paid":
      return "客户自担资源";
    case "included":
      return "套餐内权益";
    case "non_billable":
      return "不计费";
    default:
      return value || "未知";
  }
}

function overageLabel(value: string): string {
  switch (value) {
    case "auto_charge":
      return "自动超额";
    case "soft_limit":
      return "软限制";
    case "hard_limit":
      return "硬限制";
    default:
      return value || "未知";
  }
}

export default function PlatformBillingPage() {
  const token = readToken();
  const [items, setItems] = useState<BillingConfigItem[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [form, setForm] = useState<BillingConfigForm>(defaultForm);
  const [filter, setFilter] = useState("ALL");
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");

  const selected = useMemo(() => items.find((item) => item.id === selectedId) ?? null, [items, selectedId]);
  const visibleItems = useMemo(
    () => (filter === "ALL" ? items : items.filter((item) => item.itemType === filter)),
    [filter, items],
  );
  const publishedCount = items.filter((item) => item.publishStatus === "PUBLISHED").length;
  const draftCount = items.filter((item) => item.publishStatus === "DRAFT").length;

  async function loadItems(preferredId?: number | null) {
    if (!token) return;
    setError("");
    try {
      const response = await fetch(`${PLATFORM_API_BASE}/billing/plans`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      const json = await response.json();
      if (!response.ok || !json.success) throw new Error(json.message || "加载计费配置失败");
      const rows = (json.data ?? []) as BillingConfigItem[];
      setItems(rows);
      const nextId = preferredId ?? selectedId ?? rows[0]?.id ?? null;
      setSelectedId(nextId);
      const next = rows.find((item) => item.id === nextId) ?? rows[0] ?? null;
      if (next) {
        setForm(formFromItem(next));
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "加载计费配置失败");
    }
  }

  useEffect(() => {
    void loadItems();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  useEffect(() => {
    if (!selected) return;
    setForm(formFromItem(selected));
  }, [selected]);

  function updateField<K extends keyof BillingConfigForm>(key: K, value: BillingConfigForm[K]) {
    setForm((prev) => ({ ...prev, [key]: value }));
  }

  async function submitDraft(createNew: boolean) {
    setSaving(true);
    setError("");
    setNotice("");
    try {
      const response = await fetch(
        createNew || !selected ? `${PLATFORM_API_BASE}/billing/plans` : `${PLATFORM_API_BASE}/billing/plans/${selected.id}`,
        {
          method: createNew || !selected ? "POST" : "PUT",
          headers: {
            Authorization: `Bearer ${token}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify(buildBillingPayload(form)),
        },
      );
      const json = await response.json();
      if (!response.ok || !json.success) throw new Error(json.message || "保存计费配置失败");
      const saved = json.data as BillingConfigItem;
      setNotice(`已保存 ${saved.displayName} v${saved.versionNo} 草稿。`);
      await loadItems(saved.id);
    } catch (err) {
      setError(err instanceof Error ? err.message : "保存计费配置失败");
    } finally {
      setSaving(false);
    }
  }

  async function runAction(action: "publish" | "enabled", enabled?: boolean) {
    if (!selected) return;
    setSaving(true);
    setError("");
    setNotice("");
    try {
      const response = await fetch(
        action === "publish"
          ? `${PLATFORM_API_BASE}/billing/plans/${selected.id}/publish`
          : `${PLATFORM_API_BASE}/billing/plans/${selected.id}/enabled`,
        {
          method: "POST",
          headers: {
            Authorization: `Bearer ${token}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify(action === "publish" ? { changeReason: form.changeReason } : { enabled, changeReason: form.changeReason }),
        },
      );
      const json = await response.json();
      if (!response.ok || !json.success) throw new Error(json.message || "执行计费配置动作失败");
      const saved = json.data as BillingConfigItem;
      setNotice(action === "publish" ? `已发布 ${saved.displayName} v${saved.versionNo}。` : `已更新 ${saved.displayName} 启停状态。`);
      await loadItems(saved.id);
    } catch (err) {
      setError(err instanceof Error ? err.message : "执行计费配置动作失败");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="admin-page skills-catalog platform-page platform-billing-page">
      <header className="skills-catalog__header platform-page-head">
        <div className="platform-page-head__main">
          <h1 className="skills-catalog__title">计费版本配置</h1>
          <p className="subtle skills-catalog__subtitle">维护 SaaS、私有化版本、容量包、服务包、SLA 与 Credits 策略，所有高风险变更必须留下原因。</p>
        </div>
        <div className="platform-page-head__aside">
          <span className="platform-inline-stat">配置 {items.length}</span>
          <span className="platform-inline-stat">已发布 {publishedCount}</span>
          <span className="platform-inline-stat">草稿 {draftCount}</span>
        </div>
      </header>

      {error ? <div className="platform-console__banner platform-console__banner--error">{error}</div> : null}
      {notice ? <div className="platform-console__banner platform-console__banner--success">{notice}</div> : null}

      <section className="platform-console__stats platform-billing__stats">
        <article className="platform-console__stat">
          <span>SaaS 版本</span>
          <strong>{items.filter((item) => item.deploymentMode === "saas" && item.itemType === "PLAN").length}</strong>
        </article>
        <article className="platform-console__stat">
          <span>私有化版本</span>
          <strong>{items.filter((item) => item.deploymentMode === "private_deployment" && item.itemType === "PLAN").length}</strong>
        </article>
        <article className="platform-console__stat">
          <span>扩展包</span>
          <strong>{items.filter((item) => item.itemType !== "PLAN").length}</strong>
        </article>
        <article className="platform-console__stat">
          <span>启用项</span>
          <strong>{items.filter((item) => item.enabled).length}</strong>
        </article>
      </section>

      <div className="platform-billing">
        <section className="platform-console__panel skills-table-wrap platform-billing__list">
          <div className="platform-billing__toolbar">
            <select value={filter} onChange={(event) => setFilter(event.target.value)}>
              <option value="ALL">全部类型</option>
              <option value="PLAN">版本套餐</option>
              <option value="CAPACITY_PACK">容量包</option>
              <option value="MODULE_PACK">模块包</option>
              <option value="SERVICE_PACK">服务包</option>
              <option value="SLA_TIER">SLA</option>
              <option value="CREDITS_POLICY">Credits 策略</option>
            </select>
            <button
              type="button"
              className="platform-button platform-button--secondary"
              onClick={() => {
                setSelectedId(null);
                setForm(defaultForm);
              }}
            >
              新建
            </button>
          </div>
          <table className="skills-data-table platform-billing__table">
            <thead>
              <tr>
                <th>配置项</th>
                <th>模式</th>
                <th>版本</th>
                <th>状态</th>
                <th>策略</th>
              </tr>
            </thead>
            <tbody>
              {visibleItems.map((item) => (
                <tr
                  key={item.id}
                  className={`platform-console__select-row${item.id === selectedId ? " platform-console__row--active" : ""}`}
                  onClick={() => setSelectedId(item.id)}
                  tabIndex={0}
                  onKeyDown={(event) => {
                    if (event.key === "Enter" || event.key === " ") setSelectedId(item.id);
                  }}
                >
                  <td>
                    <div className="skills-data-table__skill-name">{item.displayName}</div>
                    <div className="skills-data-table__skill-code">{itemTypeLabel(item.itemType)} · {item.itemCode}</div>
                  </td>
                  <td>{deploymentModeLabel(item.deploymentMode)}</td>
                  <td>v{item.versionNo}</td>
                  <td>{publishStatusLabel(item.publishStatus)} · {item.enabled ? "启用" : "停用"}</td>
                  <td>{billingTypeLabel(item.billingTypePolicy)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>

        <section className="platform-console__panel platform-billing__detail">
          <div className="platform-console__section">
            <p className="platform-section-label">当前配置</p>
            <h2 className="platform-console__heading">{selected ? selected.displayName : "新建计费配置"}</h2>
            <p className="skills-data-table__summary">
              {selected
                ? `${itemTypeLabel(selected.itemType)} · v${selected.versionNo} · ${publishStatusLabel(selected.publishStatus)} · 更新于 ${formatTs(selected.updatedAt)}`
                : "创建新的套餐、扩展包或策略项。"}
            </p>
            {selected ? (
              <div className="platform-console__badges">
                <span className="skills-pill">{deploymentModeLabel(selected.deploymentMode)}</span>
                <span className="skills-pill">{overageLabel(selected.overageMode)}</span>
                <span className="skills-pill">已发布 v{selected.publishedVersionNo ?? "—"}</span>
                <span className="skills-pill">共 {selected.versionCount} 版</span>
              </div>
            ) : null}
          </div>

          <div className="platform-console__section">
            <h3 className="platform-console__subheading">基础信息</h3>
            <div className="platform-console__form-grid">
              <label>
                类型
                <select value={form.itemType} onChange={(event) => updateField("itemType", event.target.value)}>
                  <option value="PLAN">版本套餐</option>
                  <option value="CAPACITY_PACK">容量包</option>
                  <option value="MODULE_PACK">模块包</option>
                  <option value="SERVICE_PACK">服务包</option>
                  <option value="SLA_TIER">SLA</option>
                  <option value="CREDITS_POLICY">Credits 策略</option>
                </select>
              </label>
              <label>
                内部代码
                <input value={form.itemCode} onChange={(event) => updateField("itemCode", event.target.value)} />
              </label>
              <label>
                中文名称
                <input value={form.displayName} onChange={(event) => updateField("displayName", event.target.value)} />
              </label>
              <label>
                部署模式
                <select value={form.deploymentMode} onChange={(event) => updateField("deploymentMode", event.target.value)}>
                  <option value="saas">SaaS</option>
                  <option value="private_deployment">私有化</option>
                  <option value="all">通用</option>
                </select>
              </label>
              <label>
                计费策略
                <select value={form.billingTypePolicy} onChange={(event) => updateField("billingTypePolicy", event.target.value)}>
                  <option value="platform_paid">平台代付资源</option>
                  <option value="customer_paid">客户自担资源</option>
                  <option value="included">套餐内权益</option>
                  <option value="non_billable">不计费</option>
                </select>
              </label>
              <label>
                超额模式
                <select value={form.overageMode} onChange={(event) => updateField("overageMode", event.target.value)}>
                  <option value="auto_charge">自动超额</option>
                  <option value="soft_limit">软限制</option>
                  <option value="hard_limit">硬限制</option>
                </select>
              </label>
              <label className="platform-console__checkbox">
                <input type="checkbox" checked={form.enabled} onChange={(event) => updateField("enabled", event.target.checked)} />
                平台启用
              </label>
            </div>
          </div>

          <div className="platform-console__section">
            <h3 className="platform-console__subheading">控制指标</h3>
            <div className="platform-console__form-grid platform-billing__metric-grid">
              <label>Work Credits<input value={form.includedCredits} onChange={(event) => updateField("includedCredits", event.target.value)} /></label>
              <label>操作席位<input value={form.operationSeatLimit} onChange={(event) => updateField("operationSeatLimit", event.target.value)} /></label>
              <label>构建席位<input value={form.builderSeatLimit} onChange={(event) => updateField("builderSeatLimit", event.target.value)} /></label>
              <label>Agent 数<input value={form.agentLimit} onChange={(event) => updateField("agentLimit", event.target.value)} /></label>
              <label>Skill/Workflow<input value={form.skillWorkflowLimit} onChange={(event) => updateField("skillWorkflowLimit", event.target.value)} /></label>
              <label>知识容量 GB<input value={form.knowledgeCapacityGb} onChange={(event) => updateField("knowledgeCapacityGb", event.target.value)} /></label>
              <label>Open API QPS<input value={form.openApiQps} onChange={(event) => updateField("openApiQps", event.target.value)} /></label>
              <label>Open API 并发<input value={form.openApiConcurrency} onChange={(event) => updateField("openApiConcurrency", event.target.value)} /></label>
              <label>凭证数<input value={form.openApiCredentialLimit} onChange={(event) => updateField("openApiCredentialLimit", event.target.value)} /></label>
              <label>连接器<input value={form.connectorLimit} onChange={(event) => updateField("connectorLimit", event.target.value)} /></label>
              <label>听记并发<input value={form.meetingConcurrency} onChange={(event) => updateField("meetingConcurrency", event.target.value)} /></label>
              <label>环境数<input value={form.environmentLimit} onChange={(event) => updateField("environmentLimit", event.target.value)} /></label>
              <label>Trace 保留天<input value={form.traceRetentionDays} onChange={(event) => updateField("traceRetentionDays", event.target.value)} /></label>
              <label>审计保留天<input value={form.auditRetentionDays} onChange={(event) => updateField("auditRetentionDays", event.target.value)} /></label>
              <label>SLA 代码<input value={form.slaTierCode} onChange={(event) => updateField("slaTierCode", event.target.value)} /></label>
              <label>定价单位<input value={form.pricingUnit} onChange={(event) => updateField("pricingUnit", event.target.value)} /></label>
              <label>包分类<input value={form.addonCategory} onChange={(event) => updateField("addonCategory", event.target.value)} /></label>
            </div>
          </div>

          <div className="platform-console__section">
            <h3 className="platform-console__subheading">策略与审计</h3>
            <div className="platform-console__form-grid">
              <label className="platform-console__field--full">
                策略 JSON
                <textarea rows={5} value={form.policyJson} onChange={(event) => updateField("policyJson", event.target.value)} />
              </label>
              <label className="platform-console__field--full">
                变更原因
                <textarea rows={3} value={form.changeReason} onChange={(event) => updateField("changeReason", event.target.value)} />
              </label>
            </div>
          </div>

          <div className="platform-console__actions platform-billing__actions">
            <button type="button" className="platform-button platform-button--secondary" disabled={saving} onClick={() => void submitDraft(true)}>
              另存新草稿
            </button>
            <button type="button" className="platform-button platform-button--primary" disabled={saving} onClick={() => void submitDraft(false)}>
              保存草稿
            </button>
            <button type="button" className="platform-button platform-button--secondary" disabled={saving || !selected} onClick={() => void runAction("publish")}>
              发布版本
            </button>
            <button
              type="button"
              className="platform-button platform-button--secondary"
              disabled={saving || !selected}
              onClick={() => void runAction("enabled", !selected?.enabled)}
            >
              {selected?.enabled ? "停用" : "启用"}
            </button>
          </div>
        </section>
      </div>
    </div>
  );
}
