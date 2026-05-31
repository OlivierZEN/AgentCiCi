import { useEffect, useState } from "react";
import AdminAgentRunMonitor from "./AdminAgentRunMonitor";
import { useAdminToken } from "../useAdminToken";

type OpsTab = "agents" | "audit";

export default function AdminOpsPage() {
  const token = useAdminToken();
  const [auditLogs, setAuditLogs] = useState<Record<string, unknown>[]>([]);
  const [activeTab, setActiveTab] = useState<OpsTab>("agents");

  const loadOps = async () => {
    const logsRes = await fetch("/ops/audit/logs", { headers: { Authorization: `Bearer ${token}` } });
    const logsJson = await logsRes.json();
    setAuditLogs((logsJson.data ?? []) as Record<string, unknown>[]);
  };

  useEffect(() => {
    void loadOps();
  }, [token]);

  return (
    <div className="admin-page admin-ops-page">
      <header className="chat-header admin-ops-header">
        <div>
          <h1>观测与运维</h1>
          <p className="subtle">智能体运行与审计日志在这里统一排查。</p>
        </div>
        <button type="button" className="cici-btn cici-btn--ghost admin-ops-refresh" onClick={() => void loadOps()}>
          刷新运维数据
        </button>
      </header>

      <nav className="admin-ops-tabs" aria-label="运维范围">
        {[
          ["agents", "智能体运行"],
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
