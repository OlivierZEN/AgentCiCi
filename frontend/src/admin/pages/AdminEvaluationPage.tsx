import { useCallback, useEffect, useMemo, useState } from "react";
import { AlertTriangle, CheckCircle2, Play, Plus, RefreshCw, ShieldCheck } from "lucide-react";
import { useAdminToken } from "../useAdminToken";
import "../../shared/evaluation-quality.css";

type AgentItem = { agentId: string; name: string; publishedVersionId?: number | null };
type EvaluationSuite = {
  id: number;
  agentId: string;
  name: string;
  description?: string;
  scopeType: string;
  visibility: string;
  releaseStatus: string;
  gateMode: string;
  minPassRate: number;
  mandatory: boolean;
  platformOwned?: boolean;
  caseCount: number;
  versionNo?: number;
};
type EvaluationCase = {
  id: number;
  name: string;
  inputText?: string;
  category: string;
  priority: string;
  assertionType: string;
  expectedText?: string;
  expectedStatus?: string;
  assertionConfigJson?: string;
  status: string;
  hiddenCase?: boolean;
  redacted?: boolean;
  reviewStatus?: string;
  redactionStatus?: string;
};
type EvaluationRun = {
  id: number;
  agentId: string;
  suiteId: number;
  versionNo: number;
  baselineVersionNo?: number | null;
  status: string;
  caseCount: number;
  passedCount: number;
  failedCount: number;
  p0FailedCount: number;
  safetyFailedCount: number;
  passRate: number;
  avgLatencyMs?: number;
  startedAt: string;
};
type EvaluationIssue = {
  id: number;
  agentId: string;
  title: string;
  status: string;
  rootCauseType: string;
  severity: string;
  updatedAt: string;
};
type Overview = {
  summary?: {
    agentCount?: number;
    readyCount?: number;
    blockedCount?: number;
    notRunCount?: number;
    averagePassRate?: number;
    openIssueCount?: number;
  };
  agents?: Array<{ agentId: string; name: string; qualityStatus: string; latestRun?: EvaluationRun }>;
  recentRuns?: EvaluationRun[];
};
type ApiEnvelope<T> = { success?: boolean; data?: T; message?: string };
type PageTab = "overview" | "suites" | "runs" | "issues";

function percent(value?: number) {
  return `${Math.round((value ?? 0) * 100)}%`;
}

function dateTime(value?: string) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString("zh-CN", { hour12: false });
}

function statusText(status?: string) {
  switch ((status ?? "").toUpperCase()) {
    case "READY": case "PASSED": return "可发布";
    case "BLOCKED": case "FAILED": return "阻断";
    case "STALE": return "已过期";
    case "WARNING": return "有警告";
    case "NOT_RUN": return "未运行";
    default: return status || "未知";
  }
}

export default function AdminEvaluationPage() {
  const token = useAdminToken();
  const [activeTab, setActiveTab] = useState<PageTab>("overview");
  const [overview, setOverview] = useState<Overview>({});
  const [agents, setAgents] = useState<AgentItem[]>([]);
  const [suites, setSuites] = useState<EvaluationSuite[]>([]);
  const [cases, setCases] = useState<EvaluationCase[]>([]);
  const [runs, setRuns] = useState<EvaluationRun[]>([]);
  const [issues, setIssues] = useState<EvaluationIssue[]>([]);
  const [selectedAgentId, setSelectedAgentId] = useState("");
  const [selectedSuiteId, setSelectedSuiteId] = useState<number | null>(null);
  const [selectedRun, setSelectedRun] = useState<Record<string, unknown> | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [showSuiteForm, setShowSuiteForm] = useState(false);
  const [showCaseForm, setShowCaseForm] = useState(false);
  const [suiteName, setSuiteName] = useState("组织发布回归集");
  const [suitePassRate, setSuitePassRate] = useState("0.95");
  const [caseName, setCaseName] = useState("");
  const [caseInput, setCaseInput] = useState("");
  const [caseExpected, setCaseExpected] = useState("");

  const headers = useMemo(() => ({ Authorization: `Bearer ${token}`, "Content-Type": "application/json" }), [token]);
  const selectedSuite = suites.find((suite) => suite.id === selectedSuiteId) ?? null;

  const request = useCallback(async <T,>(url: string, init?: RequestInit): Promise<T> => {
    const response = await fetch(url, { ...init, headers: { ...headers, ...(init?.headers ?? {}) } });
    const body = (await response.json()) as ApiEnvelope<T>;
    if (!response.ok || !body.success) throw new Error(body.message || `HTTP ${response.status}`);
    return body.data as T;
  }, [headers]);

  const loadAll = useCallback(async () => {
    if (!token) return;
    setLoading(true);
    setError("");
    try {
      const [overviewData, agentData, suiteData, runData, issueData] = await Promise.all([
        request<Overview>("/evaluation/overview"),
        request<AgentItem[]>("/agents"),
        request<EvaluationSuite[]>("/evaluation/suites"),
        request<EvaluationRun[]>("/evaluation/runs"),
        request<EvaluationIssue[]>("/evaluation/issues"),
      ]);
      setOverview(overviewData ?? {});
      setAgents(agentData ?? []);
      setSuites(suiteData ?? []);
      setRuns(runData ?? []);
      setIssues(issueData ?? []);
      const nextAgent = selectedAgentId || agentData?.[0]?.agentId || "";
      setSelectedAgentId(nextAgent);
      setSelectedSuiteId((current) => current ?? suiteData?.find((item) => item.agentId === nextAgent || item.platformOwned)?.id ?? null);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause));
    } finally {
      setLoading(false);
    }
  }, [request, selectedAgentId, token]);

  const loadAgentSuites = useCallback(async (agentId: string) => {
    if (!agentId) return;
    try {
      const data = await request<EvaluationSuite[]>(`/evaluation/suites?agentId=${encodeURIComponent(agentId)}`);
      setSuites(data ?? []);
      setSelectedSuiteId(data?.[0]?.id ?? null);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause));
    }
  }, [request]);

  const loadCases = useCallback(async (suiteId: number, agentId: string) => {
    if (!suiteId || !agentId) return;
    try {
      const data = await request<EvaluationCase[]>(`/evaluation/suites/${suiteId}/cases?agentId=${encodeURIComponent(agentId)}`);
      setCases(data ?? []);
    } catch (cause) {
      setCases([]);
      setError(cause instanceof Error ? cause.message : String(cause));
    }
  }, [request]);

  useEffect(() => { void loadAll(); }, [loadAll]);
  useEffect(() => {
    if (selectedSuiteId && selectedAgentId) void loadCases(selectedSuiteId, selectedAgentId);
    else setCases([]);
  }, [loadCases, selectedAgentId, selectedSuiteId]);

  const createSuite = async () => {
    if (!selectedAgentId || !suiteName.trim()) return;
    setBusy(true);
    setError("");
    try {
      await request("/evaluation/suites", {
        method: "POST",
        body: JSON.stringify({
          agentId: selectedAgentId,
          name: suiteName.trim(),
          description: "组织维护的发布回归与业务规则用例。",
          gateMode: "BLOCKING",
          minPassRate: Number(suitePassRate) || 0.95,
        }),
      });
      setNotice("租户私有评测集已创建。");
      setShowSuiteForm(false);
      await loadAgentSuites(selectedAgentId);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause));
    } finally {
      setBusy(false);
    }
  };

  const createCase = async () => {
    if (!selectedSuiteId || !selectedAgentId || !caseName.trim() || !caseInput.trim()) return;
    setBusy(true);
    setError("");
    try {
      await request(`/evaluation/suites/${selectedSuiteId}/cases`, {
        method: "POST",
        body: JSON.stringify({
          agentId: selectedAgentId,
          name: caseName.trim(),
          inputText: caseInput.trim(),
          assertionType: "OUTPUT_CONTAINS",
          expectedText: caseExpected.trim(),
          priority: "P1",
          category: "ANSWER_QUALITY",
          reviewStatus: "APPROVED",
          redactionStatus: "NOT_REQUIRED",
          assertionConfigJson: JSON.stringify({ assertions: [{ type: "OUTPUT_CONTAINS", expected: caseExpected.trim() }] }),
        }),
      });
      setNotice("评测用例已添加，候选版本需要重新运行评测。");
      setCaseName(""); setCaseInput(""); setCaseExpected(""); setShowCaseForm(false);
      await loadCases(selectedSuiteId, selectedAgentId);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause));
    } finally {
      setBusy(false);
    }
  };

  const runSuite = async () => {
    if (!selectedSuiteId || !selectedAgentId) return;
    setBusy(true);
    setError("");
    try {
      const versions = await request<Array<{ versionNo: number; publishStatus?: string }>>(`/agents/${encodeURIComponent(selectedAgentId)}/versions`);
      const versionNo = versions?.[0]?.versionNo;
      if (!versionNo) throw new Error("当前智能体还没有可评测的编译版本。");
      const run = await request<EvaluationRun>("/evaluation/runs", {
        method: "POST",
        body: JSON.stringify({ agentId: selectedAgentId, suiteId: selectedSuiteId, versionNo, targetType: "CANDIDATE", triggerType: "MANUAL" }),
      });
      setNotice(`评测完成：${statusText(run.status)}，通过率 ${percent(run.passRate)}。`);
      await loadAll();
      setActiveTab("runs");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause));
    } finally {
      setBusy(false);
    }
  };

  const approveCase = async (item: EvaluationCase) => {
    if (!selectedSuiteId || !selectedAgentId) return;
    setBusy(true); setError("");
    try {
      await request(`/evaluation/suites/${selectedSuiteId}/cases/${item.id}`, {
        method: "PUT",
        body: JSON.stringify({
          agentId: selectedAgentId,
          name: item.name,
          inputText: item.inputText,
          assertionType: item.assertionType,
          expectedText: item.expectedText,
          expectedStatus: item.expectedStatus,
          priority: item.priority,
          category: item.category,
          assertionConfigJson: item.assertionConfigJson,
          reviewStatus: "APPROVED",
          redactionStatus: item.redactionStatus ?? "NOT_REQUIRED",
        }),
      });
      setNotice("回归用例已审核通过，将参与后续评测运行。 ");
      await loadCases(selectedSuiteId, selectedAgentId);
    } catch (cause) { setError(cause instanceof Error ? cause.message : String(cause)); }
    finally { setBusy(false); }
  };

  const openRun = async (runId: number) => {
    setBusy(true);
    try {
      setSelectedRun(await request<Record<string, unknown>>(`/evaluation/runs/${runId}`));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause));
    } finally {
      setBusy(false);
    }
  };

  const summary = overview.summary ?? {};

  return (
    <section className="evaluation-page" aria-labelledby="evaluation-page-title">
      <header className="evaluation-page__header">
        <div>
          <p className="evaluation-page__kicker">组织治理</p>
          <h1 id="evaluation-page-title">AI 质量</h1>
          <p>维护组织私有回归用例，查看标准基线与候选版本质量，评测通过后再发布智能体。</p>
        </div>
        <button className="evaluation-button evaluation-button--secondary" type="button" onClick={() => void loadAll()} disabled={loading || busy}>
          <RefreshCw size={15} aria-hidden="true" /> 刷新
        </button>
      </header>

      <nav className="evaluation-tabs" aria-label="AI质量页面">
        {(["overview", "suites", "runs", "issues"] as PageTab[]).map((tab) => (
          <button key={tab} type="button" className={activeTab === tab ? "is-active" : ""} onClick={() => setActiveTab(tab)}>
            {{ overview: "质量概览", suites: "评测集", runs: "运行记录", issues: "质量问题" }[tab]}
          </button>
        ))}
      </nav>

      {notice ? <div className="evaluation-notice" role="status">{notice}</div> : null}
      {error ? <div className="evaluation-error" role="alert"><AlertTriangle size={16} />{error}</div> : null}
      {loading ? <div className="evaluation-loading">正在读取评测资产与运行结果…</div> : null}

      {!loading && activeTab === "overview" ? (
        <div className="evaluation-section-stack">
          <section className="evaluation-metrics" aria-label="质量摘要">
            <div><span>启用智能体</span><strong>{summary.agentCount ?? 0}</strong></div>
            <div><span>生产可用</span><strong>{summary.readyCount ?? 0}</strong></div>
            <div><span>发布阻塞</span><strong>{summary.blockedCount ?? 0}</strong></div>
            <div><span>平均通过率</span><strong>{percent(summary.averagePassRate)}</strong></div>
            <div><span>开放问题</span><strong>{summary.openIssueCount ?? 0}</strong></div>
          </section>
          <section className="evaluation-table-section">
            <div className="evaluation-section-title"><h2>智能体质量状态</h2><span>最近一次有效运行</span></div>
            <table className="evaluation-table">
              <thead><tr><th>智能体</th><th>质量结论</th><th>通过率</th><th>P0失败</th><th>最近运行</th></tr></thead>
              <tbody>
                {(overview.agents ?? []).map((agent) => (
                  <tr key={agent.agentId}>
                    <td><strong>{agent.name}</strong><small>{agent.agentId}</small></td>
                    <td><span className={`evaluation-status is-${agent.qualityStatus.toLowerCase()}`}>{statusText(agent.qualityStatus)}</span></td>
                    <td>{agent.latestRun ? percent(agent.latestRun.passRate) : "—"}</td>
                    <td>{agent.latestRun?.p0FailedCount ?? "—"}</td>
                    <td>{dateTime(agent.latestRun?.startedAt)}</td>
                  </tr>
                ))}
                {(overview.agents ?? []).length === 0 ? <tr><td colSpan={5} className="evaluation-empty">暂无可评测智能体。</td></tr> : null}
              </tbody>
            </table>
          </section>
        </div>
      ) : null}

      {!loading && activeTab === "suites" ? (
        <div className="evaluation-section-stack">
          <div className="evaluation-toolbar">
            <label><span>目标智能体</span><select value={selectedAgentId} onChange={(event) => { const id = event.target.value; setSelectedAgentId(id); void loadAgentSuites(id); }}>
              {agents.map((agent) => <option key={agent.agentId} value={agent.agentId}>{agent.name}</option>)}
            </select></label>
            <div className="evaluation-toolbar__actions">
              <button type="button" className="evaluation-button evaluation-button--secondary" onClick={() => setShowSuiteForm((value) => !value)}><Plus size={15} /> 新建私有评测集</button>
              <button type="button" className="evaluation-button evaluation-button--primary" onClick={() => void runSuite()} disabled={!selectedSuiteId || busy}><Play size={15} /> 运行当前评测集</button>
            </div>
          </div>
          {showSuiteForm ? <section className="evaluation-inline-form"><h2>新建租户私有评测集</h2><div className="evaluation-form-grid">
            <label><span>名称</span><input value={suiteName} onChange={(event) => setSuiteName(event.target.value)} /></label>
            <label><span>最低通过率</span><input type="number" min="0" max="1" step="0.01" value={suitePassRate} onChange={(event) => setSuitePassRate(event.target.value)} /></label>
          </div><div className="evaluation-form-actions"><button type="button" className="evaluation-text-action" onClick={() => setShowSuiteForm(false)}>取消</button><button type="button" className="evaluation-button evaluation-button--primary" onClick={() => void createSuite()} disabled={busy}>保存评测集</button></div></section> : null}
          <div className="evaluation-master-detail">
            <aside className="evaluation-suite-list" aria-label="评测集列表">
              {suites.map((suite) => <button key={suite.id} type="button" className={selectedSuiteId === suite.id ? "is-active" : ""} onClick={() => setSelectedSuiteId(suite.id)}>
                <span><strong>{suite.name}</strong><small>{suite.scopeType} · v{suite.versionNo ?? 1}</small></span>
                <span className="evaluation-suite-list__meta">{suite.caseCount} 条<br />{suite.gateMode === "BLOCKING" ? "阻断" : "警告"}</span>
              </button>)}
              {suites.length === 0 ? <p className="evaluation-empty">当前智能体还没有适用评测集。</p> : null}
            </aside>
            <section className="evaluation-detail-pane">
              {selectedSuite ? <>
                <div className="evaluation-section-title"><div><h2>{selectedSuite.name}</h2><p>{selectedSuite.description || "暂无说明"}</p></div><div className="evaluation-inline-meta"><span>{selectedSuite.scopeType}</span><span>{selectedSuite.releaseStatus}</span><span>门禁 {Math.round(selectedSuite.minPassRate * 100)}%</span></div></div>
                <div className="evaluation-detail-actions"><button type="button" className="evaluation-text-action" onClick={() => setShowCaseForm((value) => !value)} disabled={selectedSuite.platformOwned}><Plus size={14} /> 添加用例</button>{selectedSuite.platformOwned ? <span>平台资产只读</span> : null}</div>
                {showCaseForm && !selectedSuite.platformOwned ? <div className="evaluation-inline-form"><div className="evaluation-form-grid evaluation-form-grid--wide"><label><span>用例名称</span><input value={caseName} onChange={(event) => setCaseName(event.target.value)} /></label><label><span>期望要点</span><input value={caseExpected} onChange={(event) => setCaseExpected(event.target.value)} /></label><label className="is-full"><span>测试输入</span><textarea rows={3} value={caseInput} onChange={(event) => setCaseInput(event.target.value)} /></label></div><div className="evaluation-form-actions"><button type="button" className="evaluation-text-action" onClick={() => setShowCaseForm(false)}>取消</button><button type="button" className="evaluation-button evaluation-button--primary" onClick={() => void createCase()} disabled={busy}>保存用例</button></div></div> : null}
                <table className="evaluation-table"><thead><tr><th>用例</th><th>风险</th><th>类别</th><th>断言</th><th>审核</th></tr></thead><tbody>{cases.map((item) => <tr key={item.id}><td><strong>{item.name}</strong>{item.redacted ? <small>平台隐藏用例，仅显示必要结论</small> : null}</td><td>{item.priority}</td><td>{item.category}</td><td>{item.assertionType}</td><td>{item.reviewStatus === "PENDING" && !selectedSuite.platformOwned ? <button type="button" className="evaluation-text-action" onClick={() => void approveCase(item)} disabled={busy}>审核通过</button> : item.reviewStatus ?? item.status}</td></tr>)}{cases.length === 0 ? <tr><td colSpan={5} className="evaluation-empty">暂无有效用例。</td></tr> : null}</tbody></table>
              </> : <div className="evaluation-empty">选择一个评测集查看用例和门禁设置。</div>}
            </section>
          </div>
        </div>
      ) : null}

      {!loading && activeTab === "runs" ? (
        <div className="evaluation-master-detail evaluation-master-detail--runs">
          <section className="evaluation-table-section"><div className="evaluation-section-title"><h2>运行记录</h2><span>候选版本、发布版本与回归运行</span></div><table className="evaluation-table"><thead><tr><th>运行</th><th>智能体</th><th>版本</th><th>结果</th><th>通过率</th><th>时间</th></tr></thead><tbody>{runs.map((run) => <tr key={run.id} className="is-clickable" onClick={() => void openRun(run.id)}><td>#{run.id}</td><td>{agents.find((agent) => agent.agentId === run.agentId)?.name ?? run.agentId}</td><td>v{run.versionNo}</td><td><span className={`evaluation-status is-${run.status.toLowerCase()}`}>{statusText(run.status)}</span></td><td>{percent(run.passRate)}</td><td>{dateTime(run.startedAt)}</td></tr>)}{runs.length === 0 ? <tr><td colSpan={6} className="evaluation-empty">暂无评测运行。</td></tr> : null}</tbody></table></section>
          <aside className="evaluation-run-detail"><h2>运行详情</h2>{selectedRun ? <pre>{JSON.stringify(selectedRun, null, 2)}</pre> : <p className="evaluation-empty">选择一条运行查看断言、失败原因和 Trace 证据。</p>}</aside>
        </div>
      ) : null}

      {!loading && activeTab === "issues" ? (
        <section className="evaluation-table-section"><div className="evaluation-section-title"><h2>质量问题</h2><span>失败归因、修复版本与验证运行</span></div><table className="evaluation-table"><thead><tr><th>问题</th><th>智能体</th><th>级别</th><th>根因</th><th>状态</th><th>更新时间</th></tr></thead><tbody>{issues.map((issue) => <tr key={issue.id}><td><strong>{issue.title}</strong><small>#{issue.id}</small></td><td>{issue.agentId}</td><td>{issue.severity}</td><td>{issue.rootCauseType}</td><td>{issue.status}</td><td>{dateTime(issue.updatedAt)}</td></tr>)}{issues.length === 0 ? <tr><td colSpan={6} className="evaluation-empty"><CheckCircle2 size={16} /> 暂无开放质量问题。</td></tr> : null}</tbody></table></section>
      ) : null}

      <footer className="evaluation-page__foot"><ShieldCheck size={15} />平台 P0、安全与权限门禁不可由租户关闭；评测环境默认阻断真实业务写入。</footer>
    </section>
  );
}
