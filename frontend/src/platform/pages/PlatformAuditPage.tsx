import { useEffect, useState } from "react";
import { LS_PLATFORM_TOKEN, PLATFORM_API_BASE } from "../../constants";

type PlatformAuditRow = {
  id: number;
  roleCode: string;
  eventType: string;
  resourceType: string;
  resourceKey: string;
  detail: string;
  createdAt: string;
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

export default function PlatformAuditPage() {
  const token = readToken();
  const [rows, setRows] = useState<PlatformAuditRow[]>([]);
  const eventCount = new Set(rows.map((row) => row.eventType)).size;
  const resourceCount = new Set(rows.map((row) => `${row.resourceType}:${row.resourceKey}`)).size;

  useEffect(() => {
    if (!token) return;
    void (async () => {
      const res = await fetch(`${PLATFORM_API_BASE}/audit/logs`, { headers: { Authorization: `Bearer ${token}` } });
      const json = await res.json();
      if (res.ok && json.success) {
        setRows((json.data ?? []) as PlatformAuditRow[]);
      }
    })();
  }, [token]);

  return (
    <div className="admin-page skills-catalog platform-page platform-audit-page">
      <header className="skills-catalog__header platform-page-head">
        <div className="platform-page-head__main">
          <p className="skills-catalog__kicker">Platform Audit</p>
          <h1 className="skills-catalog__title">平台审计</h1>
          <p className="subtle skills-catalog__subtitle">记录平台侧高风险治理与版本动作，便于回看最近事实。</p>
        </div>
        <div className="platform-page-head__aside">
          <span className="platform-inline-stat">记录 {rows.length}</span>
          <span className="platform-inline-stat">事件类型 {eventCount}</span>
          <span className="platform-inline-stat">资源 {resourceCount}</span>
        </div>
      </header>
      <div className="skills-table-wrap">
        <table className="skills-data-table">
          <thead>
            <tr>
              <th>时间</th>
              <th>角色</th>
              <th>事件</th>
              <th>资源</th>
              <th>详情</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.id}>
                <td className="skills-data-table__mono">{row.createdAt}</td>
                <td>{row.roleCode}</td>
                <td>{row.eventType}</td>
                <td>{row.resourceType}:{row.resourceKey}</td>
                <td className="skills-data-table__summary">{row.detail}</td>
              </tr>
            ))}
            {rows.length === 0 ? (
              <tr>
                <td colSpan={5} className="skills-data-table__summary">当前还没有平台审计记录。</td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </div>
    </div>
  );
}
