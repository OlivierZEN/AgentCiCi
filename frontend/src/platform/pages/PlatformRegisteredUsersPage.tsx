import { FormEvent, useEffect, useState } from "react";
import { LS_PLATFORM_TOKEN, PLATFORM_API_BASE } from "../../constants";
import { safeFetchJson } from "../../utils/http";

type RegisteredUser = {
  id: string;
  displayName: string;
  mobile: string;
  email: string;
  status: string;
  createdAt: string;
};

type RegisteredUserPage = {
  items: RegisteredUser[];
  total: number;
  page: number;
  pageSize: number;
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

function formatDateTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value || "-";
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

export default function PlatformRegisteredUsersPage() {
  const token = readToken();
  const [keyword, setKeyword] = useState("");
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<RegisteredUserPage>({ items: [], total: 0, page: 0, pageSize: 50 });
  const [loading, setLoading] = useState(false);
  const [notice, setNotice] = useState("");

  async function load() {
    setLoading(true);
    setNotice("");
    try {
      const params = new URLSearchParams({ q: query, page: String(page), pageSize: "50" });
      const response = await fetch(`${PLATFORM_API_BASE}/registered-users?${params.toString()}`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      const { body } = await safeFetchJson<RegisteredUserPage>(response);
      if (!response.ok || !body?.success || !body.data) {
        throw new Error(body?.message ?? `HTTP ${response.status}`);
      }
      setResult(body.data);
    } catch (error) {
      setResult((current) => ({ ...current, items: [], total: 0 }));
      setNotice(error instanceof Error ? error.message : "加载注册用户失败");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, page, query]);

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextQuery = keyword.trim();
    if (page === 0 && query === nextQuery) {
      void load();
      return;
    }
    setPage(0);
    setQuery(nextQuery);
  }

  const hasPreviousPage = page > 0;
  const hasNextPage = (page + 1) * result.pageSize < result.total;

  return (
    <div className="admin-page skills-catalog platform-page registered-users-page">
      <header className="skills-catalog__header platform-page-head">
        <div className="platform-page-head__main">
          <h1 className="skills-catalog__title">注册用户</h1>
          <p className="subtle skills-catalog__subtitle">查看系统中的个人注册用户；已加入任何组织的用户不会显示在此处。</p>
        </div>
        <div className="platform-page-head__aside">
          <span className="platform-inline-stat">共 {result.total}</span>
          <button type="button" className="platform-button platform-button--secondary" onClick={() => void load()} disabled={loading}>
            刷新列表
          </button>
        </div>
      </header>

      <section className="platform-console__panel registered-users-page__panel" aria-label="注册用户列表">
        <div className="registered-users-page__panel-head">
          <p className="platform-section-label">个人用户目录</p>
          <form className="registered-users-page__search" onSubmit={submitSearch}>
            <input
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="搜索昵称、手机号或邮箱"
              aria-label="搜索注册用户"
            />
            <button type="submit" className="platform-button platform-button--secondary">搜索</button>
          </form>
        </div>

        {notice ? <p className="notice">{notice}</p> : null}

        <div className="registered-users-page__table-wrap">
          <table className="skills-data-table registered-users-page__table">
            <thead>
              <tr>
                <th>用户</th>
                <th>手机号</th>
                <th>邮箱</th>
                <th>状态</th>
                <th>注册时间</th>
              </tr>
            </thead>
            <tbody>
              {result.items.map((user) => (
                <tr key={user.id}>
                  <td>
                    <div className="skills-data-table__skill-name">{user.displayName || "未设置昵称"}</div>
                    <div className="skills-data-table__skill-code">{user.id}</div>
                  </td>
                  <td>{user.mobile || "-"}</td>
                  <td>{user.email || "-"}</td>
                  <td><span className={`registered-users-page__status registered-users-page__status--${user.status.toLowerCase()}`}>{user.status === "ACTIVE" ? "正常" : user.status || "未知"}</span></td>
                  <td>{formatDateTime(user.createdAt)}</td>
                </tr>
              ))}
              {!loading && result.items.length === 0 ? (
                <tr><td colSpan={5} className="skills-data-table__summary">暂无符合条件的个人注册用户。</td></tr>
              ) : null}
              {loading ? (
                <tr><td colSpan={5} className="skills-data-table__summary">正在加载注册用户列表。</td></tr>
              ) : null}
            </tbody>
          </table>
        </div>

        <div className="registered-users-page__pagination" aria-label="注册用户翻页">
          <span>第 {page + 1} 页，每页 {result.pageSize} 条</span>
          <div>
            <button type="button" className="platform-button platform-button--secondary" onClick={() => setPage((current) => current - 1)} disabled={!hasPreviousPage || loading}>上一页</button>
            <button type="button" className="platform-button platform-button--secondary" onClick={() => setPage((current) => current + 1)} disabled={!hasNextPage || loading}>下一页</button>
          </div>
        </div>
      </section>
    </div>
  );
}
