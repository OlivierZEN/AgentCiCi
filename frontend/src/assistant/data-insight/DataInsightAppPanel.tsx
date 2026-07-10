import { useEffect, useMemo, useState } from "react";
import { getDataInsightDashboard } from "./dataInsightApi";
import { compactDateTime, compactMoney, compactNumber, segmentLabel, sourceTypeLabel } from "./dataInsightSections";
import type { DataInsightDashboard } from "./dataInsightTypes";

type Props = {
  token: string;
};

type Category = "sales" | "customer" | "opportunity" | "order";

const CATEGORY_LABELS: Array<{ code: Category; label: string }> = [
  { code: "sales", label: "销售业绩" },
  { code: "customer", label: "客户" },
  { code: "opportunity", label: "商机" },
  { code: "order", label: "订单回款" },
];

export function DataInsightAppPanel({ token }: Props) {
  const [dashboard, setDashboard] = useState<DataInsightDashboard | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [activeCategory, setActiveCategory] = useState<Category>("sales");

  useEffect(() => {
    let cancelled = false;
    async function load() {
      if (!token) return;
      setLoading(true);
      setError("");
      try {
        const payload = await getDataInsightDashboard(token);
        if (!cancelled) setDashboard(payload);
      } catch (loadError) {
        if (!cancelled) setError(loadError instanceof Error ? loadError.message : "数据洞察加载失败");
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, [token]);

  const maxTrend = useMemo(() => {
    if (!dashboard) return 1;
    return Math.max(...dashboard.trend.flatMap((item) => [item.pipeline, item.contract, item.order, item.paid]), 1);
  }, [dashboard]);

  if (loading && !dashboard) {
    return (
      <section className="cici-data-board cici-data-board--loading" aria-label="数据洞察">
        {Array.from({ length: 12 }).map((_, index) => (
          <span key={index} />
        ))}
      </section>
    );
  }

  if (!dashboard) {
    return (
      <section className="cici-data-board cici-data-board--empty" aria-label="数据洞察">
        <strong>数据洞察暂不可用</strong>
        <span>{error || "暂无 CRM 数据可生成仪表板。"}</span>
      </section>
    );
  }

  const summary = dashboard.summary;

  return (
    <section className="cici-data-board" aria-label="数据洞察">
      <header className="cici-data-board__bar">
        <strong>{dashboard.context.userName}</strong>
        <span>{dashboard.context.orgName}</span>
        <time>{compactDateTime(dashboard.updatedAt)}</time>
        <span>币种：{dashboard.context.currency}</span>
        <div className="cici-data-board__bar-actions">
          <button type="button" title="刷新" onClick={() => void getDataInsightDashboard(token).then(setDashboard).catch((err) => setError(err instanceof Error ? err.message : "刷新失败"))}>
            ↻
          </button>
          <span>{dashboard.context.dashboardName}</span>
        </div>
      </header>

      <nav className="cici-data-board__tabs" aria-label="仪表板分类">
        {CATEGORY_LABELS.map((item) => (
          <button
            key={item.code}
            type="button"
            className={activeCategory === item.code ? "is-active" : ""}
            onClick={() => setActiveCategory(item.code)}
          >
            {item.label}
          </button>
        ))}
        <em>{sourceTypeLabel(dashboard.sourceMode)}</em>
      </nav>

      <div className={`cici-data-board__grid is-${activeCategory}`}>
        {activeCategory === "sales" ? (
          <>
            <MetricTile title="累计合同金额" value={compactMoney(summary.contractAmount)} tone="teal" />
            <GoalTile
              title="合同达成目标"
              label="累计合同金额"
              value={summary.contractAmount}
              target={Math.max(summary.paymentTargetAmount, summary.contractAmount * 1.25)}
            />
            <MetricTile title="已回款金额" value={compactMoney(summary.paidAmount)} tone="indigo" />
            <GaugeTile title="回款目标达成率" percent={summary.paymentAchievementRate} value={compactMoney(summary.paidAmount)} />
            <TrendTile title="业绩趋势" items={dashboard.trend} max={maxTrend} />
            <RankingTile title="累计合同金额排名" items={dashboard.rankings.contractAmount} valueKind="money" />
            <AccountTile title="重点客户" items={dashboard.accounts} />
            <RiskTile title="风险客户" items={dashboard.risks} />
          </>
        ) : null}

        {activeCategory === "customer" ? (
          <>
            <MetricTile title="客户总数" value={compactNumber(summary.totalCustomers)} tone="teal" />
            <MetricTile title="潜客数量" value={compactNumber(summary.totalLeads)} tone="indigo" />
            <RankingTile title="客户数量排名" items={dashboard.rankings.customerCount} valueKind="number" />
            <GeoTile title="客户分布图" items={dashboard.geoDistribution} />
            <SegmentTile title="客户结构" items={dashboard.segments} total={summary.totalCustomers} />
            <AccountTile title="重点客户" items={dashboard.accounts} />
            <RiskTile title="风险客户" items={dashboard.risks} />
          </>
        ) : null}

        {activeCategory === "opportunity" ? (
          <>
            <MetricTile title="活跃商机数" value={compactNumber(summary.openOpportunities)} tone="teal" />
            <MetricTile title="商机金额" value={compactMoney(summary.pipelineAmount)} tone="indigo" />
            <FunnelTile title="销售漏斗" items={dashboard.funnel} />
            <RankingTile title="商机金额排名" items={dashboard.rankings.opportunityAmount} valueKind="money" />
            <TrendTile title="商机趋势" items={dashboard.trend} max={maxTrend} />
            <SegmentTile title="商机客户结构" items={dashboard.segments} total={summary.totalCustomers} />
            <AccountTile title="重点商机客户" items={dashboard.accounts} />
          </>
        ) : null}

        {activeCategory === "order" ? (
          <>
            <MetricTile title="订单金额" value={compactMoney(summary.orderAmount)} tone="teal" />
            <MetricTile title="订单数量" value={compactNumber(summary.orderCount)} tone="indigo" />
            <MetricTile title="已回款金额" value={compactMoney(summary.paidAmount)} tone="teal" />
            <GaugeTile title="回款目标达成率" percent={summary.paymentAchievementRate} value={compactMoney(summary.paidAmount)} />
            <RankingTile title="订单金额排名" items={dashboard.rankings.orderAmount} valueKind="money" />
            <RankingTile title="累计合同金额排名" items={dashboard.rankings.contractAmount} valueKind="money" />
            <TrendTile title="订单回款趋势" items={dashboard.trend} max={maxTrend} />
            <RiskTile title="回款风险客户" items={dashboard.risks} />
          </>
        ) : null}
      </div>
      {error ? <p className="cici-data-board__error">{error}</p> : null}
    </section>
  );
}

function MetricTile({ title, value, tone }: { title: string; value: string; tone: "teal" | "indigo" }) {
  return (
    <section className={`cici-data-card cici-data-card--metric is-${tone}`}>
      <h3>{title}</h3>
      <strong>{value}</strong>
    </section>
  );
}

function GoalTile({ title, label, value, target }: { title: string; label: string; value: number; target: number }) {
  const width = Math.min(100, Math.max(8, (value / Math.max(target, 1)) * 100));
  return (
    <section className="cici-data-card cici-data-card--goal">
      <h3>{title}</h3>
      <div className="cici-data-goal">
        <span>{label}</span>
        <div>
          <i style={{ inlineSize: `${width}%` }} />
          <b />
        </div>
        <footer>
          <span>0</span>
          <span>{compactMoney(target)}</span>
        </footer>
      </div>
    </section>
  );
}

function GaugeTile({ title, percent, value }: { title: string; percent: number; value: string }) {
  const clamped = Math.min(100, Math.max(0, percent));
  const angle = -108 + (clamped / 100) * 216;
  return (
    <section className="cici-data-card cici-data-card--gauge">
      <h3>{title}</h3>
      <svg viewBox="0 0 220 150" role="img" aria-label={`${title} ${clamped}%`}>
        <path d="M35 120 A75 75 0 0 1 185 120" pathLength="100" />
        <path d="M35 120 A75 75 0 0 1 185 120" pathLength="100" style={{ strokeDasharray: `${clamped} 100` }} />
        <line x1="110" y1="118" x2="110" y2="52" style={{ transform: `rotate(${angle}deg)`, transformOrigin: "110px 118px" }} />
        <text x="42" y="135">0</text>
        <text x="98" y="54">50%</text>
        <text x="171" y="135">100%</text>
      </svg>
      <strong>{value}（{clamped.toFixed(1)}%）</strong>
    </section>
  );
}

function RankingTile({ title, items, valueKind }: { title: string; items: Array<{ label: string; value: number }>; valueKind: "number" | "money" }) {
  const max = Math.max(...items.map((item) => item.value), 1);
  return (
    <section className="cici-data-card cici-data-card--ranking">
      <h3>{title}</h3>
      <div className="cici-data-ranking">
        {items.slice(0, 10).map((item) => (
          <div key={item.label}>
            <span>{item.label}</span>
            <i><b style={{ inlineSize: `${Math.max(8, (item.value / max) * 100)}%` }} /></i>
            <strong>{valueKind === "money" ? compactMoney(item.value) : compactNumber(item.value)}</strong>
          </div>
        ))}
      </div>
    </section>
  );
}

function GeoTile({ title, items }: { title: string; items: Array<{ region: string; value: number; amount: number; tone: string }> }) {
  const max = Math.max(...items.map((item) => item.value), 1);
  return (
    <section className="cici-data-card cici-data-card--geo">
      <h3>{title}</h3>
      <div className="cici-data-map" aria-label="客户区域分布">
        {items.slice(0, 16).map((item) => (
          <span
            key={item.region}
            className={`is-${item.tone}`}
            style={{ opacity: 0.38 + (item.value / max) * 0.62 }}
            title={`${item.region} ${item.value}`}
          >
            {item.region}
          </span>
        ))}
      </div>
      <footer>
        {items.slice(0, 4).map((item) => (
          <span key={item.region}>{item.region} {item.value}</span>
        ))}
      </footer>
    </section>
  );
}

function FunnelTile({ title, items }: { title: string; items: DataInsightDashboard["funnel"] }) {
  const max = Math.max(...items.map((item) => item.value), 1);
  return (
    <section className="cici-data-card cici-data-card--funnel">
      <h3>{title}</h3>
      {items.map((item) => (
        <div key={item.code} className="cici-data-funnel-row">
          <span>{item.label}</span>
          <i><b style={{ inlineSize: `${Math.max(8, (item.value / max) * 100)}%` }} /></i>
          <strong>{item.value}</strong>
        </div>
      ))}
    </section>
  );
}

function SegmentTile({ title, items, total }: { title: string; items: DataInsightDashboard["segments"]; total: number }) {
  const base = Math.max(total, 1);
  return (
    <section className="cici-data-card cici-data-card--segment">
      <h3>{title}</h3>
      <div>
        {items.map((item) => (
          <span key={item.code} style={{ inlineSize: `${Math.max(10, (item.value / base) * 100)}%`, background: item.color }}>
            {segmentLabel(item.code)} {item.value}
          </span>
        ))}
      </div>
    </section>
  );
}

function TrendTile({ title, items, max }: { title: string; items: DataInsightDashboard["trend"]; max: number }) {
  return (
    <section className="cici-data-card cici-data-card--trend">
      <h3>{title}</h3>
      <div className="cici-data-trend">
        {items.map((item) => (
          <div key={item.month}>
            <span>{item.month}</span>
            <i style={{ blockSize: `${Math.max(6, (item.pipeline / max) * 100)}%` }} />
            <i style={{ blockSize: `${Math.max(6, (item.contract / max) * 100)}%` }} />
            <i style={{ blockSize: `${Math.max(6, (item.order / max) * 100)}%` }} />
            <i style={{ blockSize: `${Math.max(6, (item.paid / max) * 100)}%` }} />
          </div>
        ))}
      </div>
    </section>
  );
}

function AccountTile({ title, items }: { title: string; items: DataInsightDashboard["accounts"] }) {
  return (
    <section className="cici-data-card cici-data-card--table">
      <h3>{title}</h3>
      <table>
        <tbody>
          {items.slice(0, 6).map((item) => (
            <tr key={item.accountId}>
              <td>{item.accountName}</td>
              <td>{item.stage}</td>
              <td>{compactMoney(item.pipelineAmount)}</td>
              <td>{item.healthScore}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}

function RiskTile({ title, items }: { title: string; items: DataInsightDashboard["risks"] }) {
  return (
    <section className="cici-data-card cici-data-card--risk">
      <h3>{title}</h3>
      {items.slice(0, 5).map((item) => (
        <div key={item.accountId}>
          <strong>{item.accountName}</strong>
          <span>{item.riskLevel === "HIGH" ? "高" : item.riskLevel === "MEDIUM" ? "中" : "低"} · {item.nextActionCount} 动作</span>
        </div>
      ))}
    </section>
  );
}
