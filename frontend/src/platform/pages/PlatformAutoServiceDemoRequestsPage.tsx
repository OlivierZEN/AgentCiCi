import { useEffect, useMemo, useState } from "react";
import { LS_PLATFORM_TOKEN, PLATFORM_API_BASE } from "../../constants";
import { safeFetchJson } from "../../utils/http";

type DemoRequestStatus = "ALL" | "NEW" | "CONTACTED" | "CLOSED";

type DemoRequestRow = {
  id: number;
  site: string;
  companyName: string;
  contactName: string;
  mobile: string;
  email?: string;
  roleTitle?: string;
  scenario?: string;
  sourcePath?: string;
  status: string;
  handledNote?: string;
  createdAt: string;
  updatedAt: string;
};

const statusOptions: Array<[DemoRequestStatus, string]> = [
  ["ALL", "全部"],
  ["NEW", "待跟进"],
  ["CONTACTED", "已联系"],
  ["CLOSED", "已关闭"],
];

function readToken(): string {
  const raw = localStorage.getItem(LS_PLATFORM_TOKEN);
  if (!raw) return "";
  try {
    return (JSON.parse(raw) as { token?: string }).token ?? "";
  } catch {
    return "";
  }
}

function statusLabel(status: string) {
  switch (status) {
    case "NEW":
      return "待跟进";
    case "CONTACTED":
      return "已联系";
    case "CLOSED":
      return "已关闭";
    default:
      return status || "未知";
  }
}

function siteLabel(site: string) {
  return site === "global" ? "国际站" : "中文站";
}

function formatDateTime(value: string) {
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

export default function PlatformAutoServiceDemoRequestsPage() {
  const token = readToken();
  const [rows, setRows] = useState<DemoRequestRow[]>([]);
  const [status, setStatus] = useState<DemoRequestStatus>("ALL");
  const [keyword, setKeyword] = useState("");
  const [notice, setNotice] = useState("");
  const [loading, setLoading] = useState(false);
  const [updatingId, setUpdatingId] = useState<number | null>(null);

  const load = async () => {
    setLoading(true);
    setNotice("");
    try {
      const params = new URLSearchParams({ status, limit: "120" });
      if (keyword.trim()) {
        params.set("q", keyword.trim());
      }
      const response = await fetch(`${PLATFORM_API_BASE}/autoservice/demo-requests?${params.toString()}`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      const { body } = await safeFetchJson<{ items?: DemoRequestRow[] }>(response);
      if (!response.ok || !body?.success) {
        throw new Error(body?.message ?? `HTTP ${response.status}`);
      }
      setRows(body.data?.items ?? []);
    } catch (error) {
      setRows([]);
      setNotice(error instanceof Error ? error.message : "加载预约列表失败");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, [token, status]);

  const counts = useMemo(
    () => ({
      all: rows.length,
      new: rows.filter((row) => row.status === "NEW").length,
      contacted: rows.filter((row) => row.status === "CONTACTED").length,
    }),
    [rows],
  );

  const updateStatus = async (row: DemoRequestRow, nextStatus: "CONTACTED" | "CLOSED") => {
    setUpdatingId(row.id);
    setNotice("");
    try {
      const response = await fetch(`${PLATFORM_API_BASE}/autoservice/demo-requests/${encodeURIComponent(String(row.id))}`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
        body: JSON.stringify({ status: nextStatus, handledNote: nextStatus === "CONTACTED" ? "已在后台标记联系" : "已关闭预约" }),
      });
      const { body } = await safeFetchJson<DemoRequestRow>(response);
      if (!response.ok || !body?.success || !body.data) {
        throw new Error(body?.message ?? `HTTP ${response.status}`);
      }
      setRows((current) => current.map((item) => (item.id === row.id ? body.data as DemoRequestRow : item)));
      setNotice(nextStatus === "CONTACTED" ? "已标记为已联系" : "已关闭预约");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "更新状态失败");
    } finally {
      setUpdatingId(null);
    }
  };

  return (
    <div className="admin-page skills-catalog platform-page autoservice-demo-admin">
      <header className="skills-catalog__header platform-page-head autoservice-demo-admin__header">
        <div className="platform-page-head__main">
          <p className="skills-catalog__kicker">Website Leads</p>
          <h1 className="skills-catalog__title">网站注册与预约演示</h1>
          <p className="subtle skills-catalog__subtitle">来自 AutoService 官网的演示预约进入运营控制面，由平台运营人员统一跟进。</p>
        </div>
        <div className="platform-page-head__aside">
          <span className="platform-inline-stat">当前 {counts.all}</span>
          <span className="platform-inline-stat">待跟进 {counts.new}</span>
          <button type="button" className="dify-btn dify-btn--ghost" onClick={() => void load()} disabled={loading}>
            刷新列表
          </button>
        </div>
      </header>

      <section className="autoservice-demo-admin__summary" aria-label="预约概览">
        <div>
          <span>当前列表</span>
          <strong>{counts.all}</strong>
        </div>
        <div>
          <span>待跟进</span>
          <strong>{counts.new}</strong>
        </div>
        <div>
          <span>已联系</span>
          <strong>{counts.contacted}</strong>
        </div>
      </section>

      <section className="autoservice-demo-admin__toolbar" aria-label="预约筛选">
        <nav className="autoservice-demo-admin__tabs" aria-label="预约状态">
          {statusOptions.map(([value, label]) => (
            <button
              key={value}
              type="button"
              className={status === value ? "is-active" : ""}
              onClick={() => setStatus(value)}
              aria-pressed={status === value}
            >
              {label}
            </button>
          ))}
        </nav>
        <form
          className="autoservice-demo-admin__search"
          onSubmit={(event) => {
            event.preventDefault();
            void load();
          }}
        >
          <input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="搜索公司、联系人、电话或邮箱" />
          <button type="submit" className="dify-btn dify-btn--ghost">
            搜索
          </button>
        </form>
      </section>

      {notice && <p className="notice">{notice}</p>}

      <section className="autoservice-demo-admin__table-wrap" aria-label="预约演示用户列表">
        <table className="autoservice-demo-admin__table">
          <colgroup>
            <col className="autoservice-demo-admin__col-main" />
            <col className="autoservice-demo-admin__col-contact" />
            <col className="autoservice-demo-admin__col-status" />
            <col className="autoservice-demo-admin__col-time" />
            <col className="autoservice-demo-admin__col-actions" />
          </colgroup>
          <thead>
            <tr>
              <th>公司与场景</th>
              <th>联系人</th>
              <th>状态</th>
              <th>提交时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.id}>
                <td>
                  <div className="autoservice-demo-admin__main-cell">
                    <strong>{row.companyName}</strong>
                    <span>{row.scenario || "未填写关注场景"}</span>
                    <small>
                      {siteLabel(row.site)}
                      {row.sourcePath ? ` · ${row.sourcePath}` : ""}
                    </small>
                  </div>
                </td>
                <td>
                  <div className="autoservice-demo-admin__contact-cell">
                    <strong>{row.contactName}</strong>
                    <span>{row.mobile}</span>
                    <small>{[row.email, row.roleTitle].filter(Boolean).join(" · ") || "未填写邮箱/职位"}</small>
                  </div>
                </td>
                <td>
                  <span className={`autoservice-demo-admin__status autoservice-demo-admin__status--${row.status.toLowerCase()}`}>
                    {statusLabel(row.status)}
                  </span>
                </td>
                <td>{formatDateTime(row.createdAt)}</td>
                <td>
                  <div className="autoservice-demo-admin__actions">
                    {row.status === "NEW" && (
                      <button type="button" onClick={() => void updateStatus(row, "CONTACTED")} disabled={updatingId === row.id}>
                        标记联系
                      </button>
                    )}
                    {row.status !== "CLOSED" && (
                      <button type="button" onClick={() => void updateStatus(row, "CLOSED")} disabled={updatingId === row.id}>
                        关闭
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
            {!loading && rows.length === 0 && (
              <tr>
                <td colSpan={5}>
                  <div className="autoservice-demo-admin__empty">暂无预约演示用户</div>
                </td>
              </tr>
            )}
            {loading && (
              <tr>
                <td colSpan={5}>
                  <div className="autoservice-demo-admin__empty">正在加载预约列表</div>
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </section>
    </div>
  );
}
