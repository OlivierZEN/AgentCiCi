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

function formatTs(ts: string): string {
  const date = new Date(ts);
  if (Number.isNaN(date.getTime())) return ts;
  return new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(date);
}

function roleLabel(role: string): string {
  switch (role) {
    case "PLATFORM_ADMIN":
      return "平台管理员";
    case "PLATFORM_SUPER_ADMIN":
      return "平台超级管理员";
    case "PLATFORM_OPERATOR":
      return "平台运营";
    case "PLATFORM_AUDITOR":
      return "平台审计";
    default:
      return role || "未知角色";
  }
}

function eventLabel(eventType: string): string {
  switch (eventType) {
    case "platform.tenant.purge.dry_run":
      return "生成预演清单";
    case "platform.tenant.create":
      return "开通租户";
    case "platform.tenant.suspend":
      return "冻结租户";
    case "platform.tenant.resume":
      return "恢复租户";
    case "platform.tenant.mark_pending_purge":
      return "标记待销毁";
    case "platform.tenant.export":
      return "生成导出包";
    case "platform.tenant.dry_run":
      return "生成预演清单";
    case "platform.tenant.execute_purge":
      return "执行真实销毁";
    case "platform.skill.publish":
      return "发布技能版本";
    case "platform.skill.rollback":
      return "回滚技能版本";
    case "platform.policy.publish":
      return "发布核心策略";
    case "platform.policy.rollback":
      return "回滚核心策略";
    case "platform.policy.version.create":
      return "新建策略版本";
    default:
      return eventType || "未知动作";
  }
}

function resourceLabel(resourceType: string, resourceKey: string): string {
  const label = (() => {
    switch (resourceType) {
      case "tenant":
        return "租户";
      case "PLATFORM_POLICY_BUNDLE":
        return "核心策略";
      case "PLATFORM_POLICY_BUNDLE_VERSION":
        return "策略版本";
      case "skill":
        return "标准技能";
      case "policy_bundle":
        return "核心策略";
      case "tool":
        return "内置工具";
      default:
        return resourceType || "资源";
    }
  })();
  const versionMatch = resourceKey.match(/@v(\d+)/i);
  const readableKey = versionMatch ? `v${versionMatch[1]}` : resourceKey;
  return readableKey ? `${label} · ${readableKey}` : label;
}

function detailLabel(detail: string): string {
  switch (detail) {
    case "Generated dry-run purge manifest":
      return "已生成租户预演清单。";
    case "draft policy bundle version created":
      return "已创建核心策略草稿版本。";
    case "published platform policy bundle version":
      return "已发布核心策略版本。";
    case "rolled back platform policy bundle version":
      return "已回滚核心策略版本。";
    default:
      return detail || "—";
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
          <h1 className="skills-catalog__title">平台审计</h1>
          <p className="subtle skills-catalog__subtitle">记录平台侧高风险治理与版本动作，便于回看最近事实。</p>
        </div>
        <div className="platform-page-head__aside">
          <span className="platform-inline-stat">记录 {rows.length}</span>
          <span className="platform-inline-stat">事件类型 {eventCount}</span>
          <span className="platform-inline-stat">资源 {resourceCount}</span>
        </div>
      </header>
      <div className="skills-table-wrap platform-audit__table-wrap">
        <table className="skills-data-table platform-audit__table">
          <colgroup>
            <col className="platform-audit__col-time" />
            <col className="platform-audit__col-role" />
            <col className="platform-audit__col-event" />
            <col className="platform-audit__col-resource" />
            <col className="platform-audit__col-detail" />
          </colgroup>
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
                <td className="skills-data-table__mono">{formatTs(row.createdAt)}</td>
                <td>{roleLabel(row.roleCode)}</td>
                <td>{eventLabel(row.eventType)}</td>
                <td>{resourceLabel(row.resourceType, row.resourceKey)}</td>
                <td className="skills-data-table__summary">{detailLabel(row.detail)}</td>
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
