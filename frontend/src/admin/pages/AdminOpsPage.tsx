import { useEffect, useState } from "react";
import { useAdminToken } from "../useAdminToken";

type CostMetrics = { orgId: string; callCount: number; estimatedCostCny: string };

export default function AdminOpsPage() {
  const token = useAdminToken();
  const [auditLogs, setAuditLogs] = useState<Record<string, unknown>[]>([]);
  const [costMetrics, setCostMetrics] = useState<CostMetrics | null>(null);

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
    <div className="admin-page">
      <header className="chat-header">
        <h1>运维看板</h1>
        <p className="subtle">审计日志与调用成本估算</p>
      </header>
      <div className="kb-grid">
        <div className="kb-card">
          <h3>成本概览</h3>
          <div className="subtle">组织：{costMetrics?.orgId ?? "-"}</div>
          <div className="subtle">调用次数：{costMetrics?.callCount ?? 0}</div>
          <div className="subtle">预估成本(CNY)：{costMetrics?.estimatedCostCny ?? "0.00"}</div>
          <div className="row">
            <button type="button" onClick={() => void loadOps()}>
              刷新看板
            </button>
          </div>
        </div>
        <div className="kb-card">
          <h3>最近审计日志</h3>
          <pre>{JSON.stringify(auditLogs, null, 2)}</pre>
        </div>
      </div>
    </div>
  );
}
