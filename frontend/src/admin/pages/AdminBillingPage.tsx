import { useEffect, useMemo, useState } from "react";
import { useAdminToken } from "../useAdminToken";

type AdminSubscription = {
  orgId: string;
  deploymentMode: string;
  deploymentModeLabel: string;
  editionCode: string;
  editionName: string;
  status: string;
  periodStart: string;
  periodEnd: string;
  includedCredits: number;
  consumedCredits: number;
  remainingCredits: number;
  overageMode: string;
  topUpPolicy: string;
  billingTypePolicy: string;
  localModelTokenPolicy: string;
  operationSeatsUsed: number;
  operationSeatLimit: number | null;
  builderSeatsUsed: number;
  builderSeatLimit: number | null;
  agentLimit: number | null;
  openApiQps: number | null;
  traceRetentionDays: number | null;
  packageNames: string[];
};

type CreditSummary = {
  includedCredits: number;
  consumedCredits: number;
  remainingCredits: number;
  consumedPercent: number;
};

type UsageDomain = {
  domain: string;
  label: string;
  credits: number;
  eventCount: number;
};

type LedgerEntry = {
  id: number;
  entryType: string;
  creditsDelta: number;
  balanceAfter: number;
  sourceEventId: number | null;
  description: string;
  occurredAt: string;
};

type UsageEvent = {
  id: number;
  domain: string;
  domainLabel: string;
  itemCode: string;
  description: string;
  agentId: string | null;
  quantity: number;
  unit: string;
  credits: number;
  billingType: string;
  status: string;
  occurredAt: string;
};

type QuotaWarning = {
  code: string;
  label: string;
  level: string;
  message: string;
};

type BillingOverview = {
  subscription: AdminSubscription;
  creditSummary: CreditSummary;
  usageByDomain: UsageDomain[];
  recentLedger: LedgerEntry[];
  recentUsageEvents: UsageEvent[];
  quotaWarnings: QuotaWarning[];
};

type ApiEnvelope<T> = {
  success?: boolean;
  data?: T;
  message?: string;
};

async function parseOverviewResponse(res: Response): Promise<ApiEnvelope<BillingOverview>> {
  const contentType = res.headers.get("content-type") ?? "";
  const bodyText = await res.text();
  if (contentType.includes("text/html") || bodyText.trimStart().startsWith("<")) {
    throw new Error("计费用量接口返回了页面内容，请检查本地 API 代理或后端服务。");
  }
  try {
    return JSON.parse(bodyText) as ApiEnvelope<BillingOverview>;
  } catch {
    throw new Error("计费用量接口返回格式异常。");
  }
}

function formatCredits(value: number | null | undefined): string {
  const amount = Number(value ?? 0);
  return amount.toLocaleString("zh-CN", { maximumFractionDigits: 2 });
}

function formatLimit(value: number | null | undefined): string {
  return value == null ? "合同约定" : value.toLocaleString("zh-CN");
}

function formatDate(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value || "-";
  return new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(date);
}

function formatDateTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value || "-";
  return new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(date);
}

function ledgerTypeLabel(type: string): string {
  switch (type) {
    case "included_grant":
      return "额度发放";
    case "top_up_grant":
      return "加购入账";
    case "usage_debit":
      return "用量扣减";
    case "adjustment_credit":
      return "人工增补";
    case "adjustment_debit":
      return "人工扣减";
    case "reversal_credit":
      return "冲正返还";
    default:
      return type || "未知";
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

function warningLabel(level: string): string {
  switch (level) {
    case "critical":
      return "临界";
    case "warning":
      return "预警";
    default:
      return "正常";
  }
}

export const adminBillingLabels = {
  formatCredits,
  formatLimit,
  ledgerTypeLabel,
  billingTypeLabel,
  warningLabel,
};

export default function AdminBillingPage() {
  const token = useAdminToken();
  const [overview, setOverview] = useState<BillingOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  async function loadOverview() {
    setLoading(true);
    setError("");
    try {
      const res = await fetch("/admin/billing/overview", {
        headers: { Authorization: `Bearer ${token}` },
      });
      const json = await parseOverviewResponse(res);
      if (!res.ok || !json.success) throw new Error(json.message || "加载计费用量失败");
      if (!json.data) throw new Error("计费用量接口未返回数据。");
      setOverview(json.data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "加载计费用量失败");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadOverview();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  const consumedPercent = useMemo(() => overview?.creditSummary.consumedPercent ?? 0, [overview]);

  return (
    <div className="admin-page admin-billing-page">
      <header className="chat-header admin-billing-header">
        <div>
          <h1>计费用量</h1>
          <p className="subtle">当前组织的版本、credits 余额、用量事件和账本明细。</p>
        </div>
        <button type="button" className="cici-btn cici-btn--ghost" onClick={() => void loadOverview()} disabled={loading}>
          {loading ? "刷新中" : "刷新"}
        </button>
      </header>

      {error ? <div className="platform-console__banner platform-console__banner--error">{error}</div> : null}

      {overview ? (
        <>
          <section className="admin-billing-summary" aria-label="组织计费概览">
            <div className="admin-billing-summary__primary">
              <p className="platform-section-label">当前版本</p>
              <h2>{overview.subscription.editionName}</h2>
              <p>
                {overview.subscription.deploymentModeLabel} · {overview.subscription.status} · {overview.subscription.editionCode}
              </p>
            </div>
            <dl className="admin-billing-metrics">
              <div>
                <dt>剩余 Credits</dt>
                <dd>{formatCredits(overview.creditSummary.remainingCredits)}</dd>
              </div>
              <div>
                <dt>本期消耗</dt>
                <dd>{formatCredits(overview.creditSummary.consumedCredits)}</dd>
              </div>
              <div>
                <dt>总额度</dt>
                <dd>{formatCredits(overview.creditSummary.includedCredits)}</dd>
              </div>
            </dl>
          </section>

          <section className="admin-billing-progress" aria-label="Credits 使用进度">
            <div className="admin-billing-progress__bar">
              <span style={{ width: `${Math.min(100, Math.max(0, consumedPercent))}%` }} />
            </div>
            <p>{formatCredits(consumedPercent)}% 已使用，周期 {formatDate(overview.subscription.periodStart)} 至 {formatDate(overview.subscription.periodEnd)}</p>
          </section>

          <div className="admin-billing-grid">
            <section className="admin-ops-panel admin-billing-panel" aria-label="权益和额度">
              <header className="admin-ops-panel__head">
                <h2>权益与额度</h2>
                <span>{overview.subscription.packageNames.length} 个附加包</span>
              </header>
              <dl className="admin-billing-entitlements">
                <div>
                  <dt>操作席位</dt>
                  <dd>{overview.subscription.operationSeatsUsed} / {formatLimit(overview.subscription.operationSeatLimit)}</dd>
                </div>
                <div>
                  <dt>构建席位</dt>
                  <dd>{overview.subscription.builderSeatsUsed} / {formatLimit(overview.subscription.builderSeatLimit)}</dd>
                </div>
                <div>
                  <dt>Agent 数</dt>
                  <dd>{formatLimit(overview.subscription.agentLimit)}</dd>
                </div>
                <div>
                  <dt>Open API QPS</dt>
                  <dd>{formatLimit(overview.subscription.openApiQps)}</dd>
                </div>
                <div>
                  <dt>Trace 保留</dt>
                  <dd>{formatLimit(overview.subscription.traceRetentionDays)} 天</dd>
                </div>
                <div>
                  <dt>计费策略</dt>
                  <dd>{billingTypeLabel(overview.subscription.billingTypePolicy)}</dd>
                </div>
              </dl>
              <p className="admin-billing-note">{overview.subscription.localModelTokenPolicy}</p>
              {overview.subscription.packageNames.length ? (
                <div className="admin-billing-packages">
                  {overview.subscription.packageNames.map((name) => <span key={name}>{name}</span>)}
                </div>
              ) : null}
            </section>

            <section className="admin-ops-panel admin-billing-panel" aria-label="额度状态">
              <header className="admin-ops-panel__head">
                <h2>Quota 状态</h2>
                <span>{overview.quotaWarnings.length} 项</span>
              </header>
              <div className="admin-billing-quota-list">
                {overview.quotaWarnings.map((item) => (
                  <div key={item.code} className={`admin-billing-quota admin-billing-quota--${item.level}`}>
                    <strong>{item.label}</strong>
                    <span>{warningLabel(item.level)}</span>
                    <p>{item.message}</p>
                  </div>
                ))}
              </div>
            </section>
          </div>

          <section className="admin-ops-panel admin-billing-panel" aria-label="消耗分布">
            <header className="admin-ops-panel__head">
              <h2>消耗分布</h2>
              <span>{overview.usageByDomain.length} 个维度</span>
            </header>
            <div className="admin-billing-domain-list">
              {overview.usageByDomain.map((item) => (
                <div key={item.domain} className="admin-billing-domain-row">
                  <span>{item.label}</span>
                  <strong>{formatCredits(item.credits)} credits</strong>
                  <small>{item.eventCount} 条事件</small>
                </div>
              ))}
            </div>
          </section>

          <div className="admin-billing-grid admin-billing-grid--tables">
            <section className="admin-ops-panel admin-billing-panel" aria-label="最近账本">
              <header className="admin-ops-panel__head">
                <h2>Credits 账本</h2>
                <span>最近 {overview.recentLedger.length} 条</span>
              </header>
              <table className="skills-data-table admin-billing-table">
                <thead>
                  <tr>
                    <th>时间</th>
                    <th>类型</th>
                    <th>变动</th>
                    <th>余额</th>
                  </tr>
                </thead>
                <tbody>
                  {overview.recentLedger.map((item) => (
                    <tr key={item.id}>
                      <td>{formatDateTime(item.occurredAt)}</td>
                      <td>
                        <strong>{ledgerTypeLabel(item.entryType)}</strong>
                        <small>{item.description}</small>
                      </td>
                      <td className={item.creditsDelta < 0 ? "admin-billing-negative" : "admin-billing-positive"}>
                        {item.creditsDelta > 0 ? "+" : ""}{formatCredits(item.creditsDelta)}
                      </td>
                      <td>{formatCredits(item.balanceAfter)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </section>

            <section className="admin-ops-panel admin-billing-panel" aria-label="最近用量事件">
              <header className="admin-ops-panel__head">
                <h2>消耗明细</h2>
                <span>最近 {overview.recentUsageEvents.length} 条</span>
              </header>
              <table className="skills-data-table admin-billing-table">
                <thead>
                  <tr>
                    <th>时间</th>
                    <th>动作</th>
                    <th>归属</th>
                    <th>Credits</th>
                  </tr>
                </thead>
                <tbody>
                  {overview.recentUsageEvents.map((item) => (
                    <tr key={item.id}>
                      <td>{formatDateTime(item.occurredAt)}</td>
                      <td>
                        <strong>{item.domainLabel}</strong>
                        <small>{item.description}</small>
                      </td>
                      <td>{item.agentId || "-"} · {billingTypeLabel(item.billingType)}</td>
                      <td>{formatCredits(item.credits)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </section>
          </div>
        </>
      ) : loading ? (
        <section className="admin-ops-panel admin-billing-panel">正在加载计费链路...</section>
      ) : (
        <section className="admin-ops-panel admin-billing-panel">暂无计费数据。</section>
      )}
    </div>
  );
}
