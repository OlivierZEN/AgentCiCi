import { useEffect, useState } from "react";
import AdminAgentRunMonitor from "./AdminAgentRunMonitor";
import { useAdminToken } from "../useAdminToken";
import { safeFetchJson } from "../../utils/http";

type OpsTab = "agents" | "audit";

type AuditLogPayload = {
  id: number;
  userId?: string;
  eventType?: string;
  detail?: string;
  createdAt?: string;
};

function formatAuditDateTime(value?: string) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "—";
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  }).format(date);
}

export default function AdminOpsPage() {
  const token = useAdminToken();
  const [auditLogs, setAuditLogs] = useState<AuditLogPayload[]>([]);
  const [activeTab, setActiveTab] = useState<OpsTab>("agents");
  const [auditQuery, setAuditQuery] = useState("");
  const [auditEventType, setAuditEventType] = useState("");
  const [auditLoading, setAuditLoading] = useState(false);
  const [auditError, setAuditError] = useState("");

  const loadOps = async () => {
    setAuditLoading(true);
    setAuditError("");
    try {
      const params = new URLSearchParams({ limit: "80" });
      if (auditQuery.trim()) params.set("q", auditQuery.trim());
      if (auditEventType.trim()) params.set("eventType", auditEventType.trim());
      const logsRes = await fetch(`/ops/audit/logs?${params.toString()}`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      const { body } = await safeFetchJson<{ items?: AuditLogPayload[] }>(logsRes);
      if (!logsRes.ok || !body?.success) {
        throw new Error(body?.message ?? `HTTP ${logsRes.status}`);
      }
      const items = Array.isArray(body.data) ? body.data : body.data?.items;
      if (!Array.isArray(items)) {
        throw new Error("审计日志返回格式异常");
      }
      setAuditLogs(items);
    } catch (error) {
      setAuditLogs([]);
      setAuditError(error instanceof Error ? error.message : "审计日志加载失败");
    } finally {
      setAuditLoading(false);
    }
  };

  useEffect(() => {
    void loadOps();
  }, [token, auditEventType]);

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
            <span>{auditLoading ? "加载中" : `${auditLogs.length} 条`}</span>
          </header>
          <section className="admin-ops-audit-toolbar" aria-label="审计日志筛选">
            <label className="cici-monitor__search admin-ops-audit-search">
              <span className="cici-monitor__search-icon" aria-hidden />
              <input
                type="text"
                value={auditQuery}
                onChange={(event) => setAuditQuery(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === "Enter") void loadOps();
                }}
                placeholder="搜索用户、事件类型或脱敏后的详情"
                aria-label="搜索审计日志"
              />
            </label>
            <input
              className="admin-ops-audit-filter"
              value={auditEventType}
              onChange={(event) => setAuditEventType(event.target.value)}
              placeholder="事件类型"
              aria-label="按事件类型筛选"
            />
            <button type="button" className="cici-monitor__refresh" onClick={() => void loadOps()}>
              查询
            </button>
          </section>
          <div className="admin-ops-audit-list">
            {auditLogs.map((log, index) => (
              <article className="admin-ops-audit-row" key={log.id ?? `${log.createdAt ?? "audit"}-${index}`}>
                <time>{formatAuditDateTime(log.createdAt)}</time>
                <strong>{log.eventType || "audit.event"}</strong>
                <span>{log.userId || "unknown-user"}</span>
                <p>{log.detail || "无详情"}</p>
              </article>
            ))}
            {auditLogs.length === 0 ? (
              <div className="cici-monitor__empty">{auditError || "当前筛选条件下没有审计日志。"}</div>
            ) : null}
          </div>
        </section>
      )}
    </div>
  );
}
