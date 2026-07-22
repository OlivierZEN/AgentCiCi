import { useEffect, useState } from "react";
import { ArrowRight, CheckCircle2, ShieldAlert, X } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { authFetch, readAuthToken } from "../../auth/authStorage";
import { LS_PLATFORM_TOKEN, PLATFORM_API_BASE } from "../../constants";

type BootstrapPayload = {
  roles: string[];
  skillCount: number;
  hiddenSkillCount: number;
  builtinToolCount: number;
  recentAuditCount: number;
  policyBundleCode: string;
  policyBundleVersionNo: number;
  policyBundleLivePublishedAgentCount: number;
};

function roleLabel(role: string): string {
  switch (role) {
    case "ORG_ADMIN":
      return "组织管理员";
    case "PLATFORM_ADMIN":
      return "平台管理员";
    case "PLATFORM_SUPER_ADMIN":
      return "平台超级管理员";
    case "PLATFORM_OPERATOR":
      return "平台运营";
    case "PLATFORM_AUDITOR":
      return "平台审计";
    default:
      return role;
  }
}

function readToken(): string {
  return readAuthToken(LS_PLATFORM_TOKEN);
}

export default function PlatformHomePage() {
  const token = readToken();
  const navigate = useNavigate();
  const [data, setData] = useState<BootstrapPayload | null>(null);
  const [notice, setNotice] = useState("");
  const [selectedAction, setSelectedAction] = useState<"policy" | "quality" | "audit" | null>(null);

  const actionDetails = {
    policy: { title: "核心策略版本", detail: data ? `当前为 v${data.policyBundleVersionNo}，覆盖 ${data.policyBundleLivePublishedAgentCount} 个已发布智能体。` : "正在加载策略状态。", to: "/platform/skills/policies" },
    quality: { title: "质量运行洞察", detail: "查看跨租户质量运行指标与失败信号，不展示原始对话内容。", to: "/platform/evaluation/runs" },
    audit: { title: "平台审计", detail: data ? `最近审计记录 ${data.recentAuditCount} 条，可按时间与操作对象追溯。` : "正在加载审计状态。", to: "/platform/audit" },
  } as const;

  useEffect(() => {
    if (!token) return;
    void (async () => {
      const res = await authFetch(LS_PLATFORM_TOKEN, `${PLATFORM_API_BASE}/bootstrap`);
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
          <p className="platform-section-label">运营总览</p>
          <h1 className="skills-catalog__title">今日治理优先事项</h1>
          <p className="subtle skills-catalog__subtitle">先处理需要决策的能力、风险与审计事项，再进入对应工作区完成操作。</p>
        </div>
        <div className="platform-page-head__aside">
          <span className="platform-inline-stat">角色 {data?.roles.length ?? 0}</span>
          <span className="platform-inline-stat">技能 {data?.skillCount ?? 0}</span>
          <span className="platform-inline-stat">工具 {data?.builtinToolCount ?? 0}</span>
        </div>
      </header>
      {notice ? <div className="platform-console__banner platform-console__banner--error">{notice}</div> : null}
      {data ? (
        <div className="platform-home__stack">
          <section className="platform-console__stats platform-home__stats" aria-label="平台治理概况">
            <article className="platform-console__stat">
              <span>标准技能</span>
              <strong>{data.skillCount}</strong>
            </article>
            <article className="platform-console__stat">
              <span>隐藏技能</span>
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

          <section className="platform-home__workbench" aria-label="运营工作台">
            <div className="platform-home__queue">
              <div className="platform-home__section-head"><div><p className="platform-section-label">优先队列</p><h2 className="platform-console__heading">需要确认的治理事项</h2></div><span>{data.hiddenSkillCount > 0 ? `${data.hiddenSkillCount} 项需关注` : "当前无阻塞项"}</span></div>
              <button type="button" className="platform-home__action" onClick={() => setSelectedAction("policy")}><span className="platform-home__action-mark"><ShieldAlert size={17} /></span><span><strong>确认核心策略版本</strong><small>当前 v{data.policyBundleVersionNo}，覆盖 {data.policyBundleLivePublishedAgentCount} 个已发布智能体</small></span><ArrowRight size={17} /></button>
              <button type="button" className="platform-home__action" onClick={() => setSelectedAction("quality")}><span className="platform-home__action-mark"><CheckCircle2 size={17} /></span><span><strong>查看质量运行洞察</strong><small>进入独立运行页判断是否需要回滚或补充评测资产</small></span><ArrowRight size={17} /></button>
              <button type="button" className="platform-home__action" onClick={() => setSelectedAction("audit")}><span className="platform-home__action-mark"><ShieldAlert size={17} /></span><span><strong>复核近期平台审计</strong><small>{data.recentAuditCount} 条近期记录待追溯或归档</small></span><ArrowRight size={17} /></button>
            </div>
            <aside className="platform-home__health" aria-label="治理健康度"><p className="platform-section-label">治理健康</p><h2 className="platform-console__heading">能力运行基线</h2><dl><div><dt>平台角色</dt><dd>{data.roles.map(roleLabel).join("、") || "—"}</dd></div><div><dt>标准技能</dt><dd>{data.skillCount} 个，其中隐藏 {data.hiddenSkillCount} 个</dd></div><div><dt>内置工具</dt><dd>{data.builtinToolCount} 个，进入工具目录管理风险级别</dd></div></dl><button type="button" className="platform-button platform-button--secondary" onClick={() => navigate("/platform/tools")}>查看工具目录</button></aside>
          </section>
          {selectedAction ? <aside className="platform-home__drawer" aria-label={actionDetails[selectedAction].title}><div><button type="button" className="platform-home__drawer-close" aria-label="关闭详情" onClick={() => setSelectedAction(null)}><X size={17} /></button><p className="platform-section-label">事项详情</p><h2>{actionDetails[selectedAction].title}</h2><p>{actionDetails[selectedAction].detail}</p></div><button type="button" className="platform-button platform-button--primary" onClick={() => navigate(actionDetails[selectedAction].to)}>进入工作区 <ArrowRight size={15} /></button></aside> : null}
        </div>
      ) : null}
    </div>
  );
}
