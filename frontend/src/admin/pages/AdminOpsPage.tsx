import { useEffect, useState } from "react";
import AdminAgentRunMonitor from "./AdminAgentRunMonitor";
import { useAdminToken } from "../useAdminToken";

type CostMetrics = { orgId: string; callCount: number; estimatedCostCny: string };
type OpsTab = "agents" | "cost" | "audit";

export default function AdminOpsPage() {
  const token = useAdminToken();
  const [auditLogs, setAuditLogs] = useState<Record<string, unknown>[]>([]);
  const [costMetrics, setCostMetrics] = useState<CostMetrics | null>(null);
  const [activeTab, setActiveTab] = useState<OpsTab>("agents");

  const loadOps = async () => {
    const [logsRes, costRes] = await Promise.all([
      fetch("/ops/audit/logs", { headers: { Authorization: `Bearer ${token}` } }),
      fetch("/ops/metrics/cost", { headers: { Authorization: `Bearer ${token}` } }),
    ]);
    const logsJson = await logsRes.json();
    const costJson = await costRes.json();
    setAuditLogs((logsJson.data ?? []) as Record<string, unknown>[]);
    setCostMetrics((costJson.data ?? null) as CostMetrics | null);
  };

  useEffect(() => {
    void loadOps();
  }, [token]);

  return (
    <div className="admin-page admin-ops-page">
      <header className="chat-header admin-ops-header">
        <div>
          <h1>观测与运维</h1>
          <p className="subtle">智能体运行、成本用量与审计日志在这里统一排查。</p>
        </div>
        <button type="button" className="dify-btn dify-btn--ghost admin-ops-refresh" onClick={() => void loadOps()}>
          刷新运维数据
        </button>
      </header>

      <nav className="admin-ops-tabs" aria-label="运维范围">
        {[
          ["agents", "智能体运行"],
          ["cost", "成本用量"],
          ["audit", "审计日志"],
        ].map(([value, label]) => (
          <button
            key={value}
            type="button"
            className={`admin-ops-tab${activeTab === value ? " is-active" : ""}`}
            onClick={() => setActiveTab(value as OpsTab)}
          >
            {label}
          </button>
        ))}
      </nav>

      {activeTab === "agents" ? (
        <AdminAgentRunMonitor token={token} />
      ) : activeTab === "cost" ? (
        <section className="admin-ops-panel" aria-label="成本用量">
          <header className="admin-ops-panel__head">
            <h2>成本概览</h2>
            <span>组织级估算</span>
          </header>
          <dl className="admin-ops-metrics">
            <div>
              <dt>组织</dt>
              <dd>{costMetrics?.orgId ?? "-"}</dd>
            </div>
            <div>
              <dt>调用次数</dt>
              <dd>{costMetrics?.callCount ?? 0}</dd>
            </div>
            <div>
              <dt>预估成本(CNY)</dt>
              <dd>{costMetrics?.estimatedCostCny ?? "0.00"}</dd>
            </div>
          </dl>
        </section>
      ) : (
        <section className="admin-ops-panel" aria-label="审计日志">
          <header className="admin-ops-panel__head">
            <h2>最近审计日志</h2>
            <span>{auditLogs.length} 条</span>
          </header>
          <pre className="admin-ops-audit-json">{JSON.stringify(auditLogs, null, 2)}</pre>
        </section>
      )}
    </div>
  );
}
