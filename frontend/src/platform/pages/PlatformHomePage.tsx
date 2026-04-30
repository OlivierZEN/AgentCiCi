import { useEffect, useState } from "react";
import { LS_PLATFORM_TOKEN, PLATFORM_API_BASE } from "../../constants";

type BootstrapPayload = {
  orgId: string;
  roles: string[];
  skillCount: number;
  hiddenSkillCount: number;
  builtinToolCount: number;
  recentAuditCount: number;
  policyBundleCode: string;
  policyBundleVersionNo: number;
  policyBundleLivePublishedAgentCount: number;
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

export default function PlatformHomePage() {
  const token = readToken();
  const [data, setData] = useState<BootstrapPayload | null>(null);
  const [notice, setNotice] = useState("");

  useEffect(() => {
    if (!token) return;
    void (async () => {
      const res = await fetch(`${PLATFORM_API_BASE}/bootstrap`, { headers: { Authorization: `Bearer ${token}` } });
      const json = await res.json();
      if (!res.ok || !json.success) {
        setNotice(json.message ?? `HTTP ${res.status}`);
        return;
      }
      setData((json.data ?? null) as BootstrapPayload | null);
    })();
  }, [token]);

  return (
    <div className="admin-page skills-catalog platform-page platform-home-page">
      <header className="skills-catalog__header platform-page-head">
        <div className="platform-page-head__main">
          <p className="skills-catalog__kicker">Platform Console</p>
          <h1 className="skills-catalog__title">平台概览</h1>
          <p className="subtle skills-catalog__subtitle">先看当前治理面状态，再进入具体工作台处理版本、工具与审计事项。</p>
        </div>
        <div className="platform-page-head__aside">
          <span className="platform-inline-stat">角色 {data?.roles.length ?? 0}</span>
          <span className="platform-inline-stat">Skill {data?.skillCount ?? 0}</span>
          <span className="platform-inline-stat">Tool {data?.builtinToolCount ?? 0}</span>
        </div>
      </header>
      {notice ? <div className="platform-console__banner platform-console__banner--error">{notice}</div> : null}
      {data ? (
        <div className="platform-home__stack">
          <section className="platform-console__stats platform-home__stats">
            <article className="platform-console__stat">
              <span>平台 Skill</span>
              <strong>{data.skillCount}</strong>
            </article>
            <article className="platform-console__stat">
              <span>隐藏核心 Skill</span>
              <strong>{data.hiddenSkillCount}</strong>
            </article>
            <article className="platform-console__stat">
              <span>内置工具</span>
              <strong>{data.builtinToolCount}</strong>
            </article>
            <article className="platform-console__stat">
              <span>最近审计</span>
              <strong>{data.recentAuditCount}</strong>
            </article>
          </section>

          <div className="platform-console__grid platform-console__grid--balanced">
            <section className="platform-console__panel">
              <div className="platform-console__section">
                <h2 className="platform-console__heading">控制面状态</h2>
                <p className="skills-data-table__summary">当前平台运行治理事实与基础数据摘要。</p>
              </div>
              <div className="skills-table-wrap">
                <table className="skills-data-table">
                  <tbody>
                    <tr><th>组织</th><td>{data.orgId}</td></tr>
                    <tr><th>平台角色</th><td>{data.roles.join(", ") || "—"}</td></tr>
                    <tr><th>核心策略包</th><td>{data.policyBundleCode}@v{data.policyBundleVersionNo}</td></tr>
                    <tr><th>策略命中已发布 Agent</th><td>{data.policyBundleLivePublishedAgentCount}</td></tr>
                  </tbody>
                </table>
              </div>
            </section>

            <section className="platform-console__panel">
              <div className="platform-console__section">
                <h2 className="platform-console__heading">建议入口</h2>
                <p className="skills-data-table__summary">按常见运营顺序进入工作台，减少不必要的页面跳转。</p>
                <ul className="platform-console__summary-list platform-home__checklist">
                  <li>先看概览，确认核心策略包与平台基础盘点。</li>
                  <li>需要调整标准能力时进入平台技能页处理模板版本。</li>
                  <li>需要收口风险或依赖时进入内置工具页。</li>
                  <li>需要追踪动作事实时进入平台审计页。</li>
                </ul>
              </div>
            </section>
          </div>
        </div>
      ) : null}
    </div>
  );
}
