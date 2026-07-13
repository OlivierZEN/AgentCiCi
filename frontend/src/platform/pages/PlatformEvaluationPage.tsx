import { useCallback, useEffect, useMemo, useState } from "react";
import { Archive, EyeOff, PlayCircle, Plus, RefreshCw, Send, ShieldCheck } from "lucide-react";
import { LS_PLATFORM_TOKEN, PLATFORM_API_BASE } from "../../constants";
import "../../shared/evaluation-quality.css";

type Suite = {
  id: number; name: string; description?: string; scopeType: string; visibility: string;
  releaseStatus: string; templateCode: string; versionNo: number; appCode?: string; industryCode?: string;
  gateMode: string; minPassRate: number; caseCount: number; hiddenResults: boolean; mandatory: boolean;
};
type EvalCase = { id: number; name: string; priority: string; category: string; assertionType: string; status: string; hiddenCase: boolean };
type Run = { id: number; orgId: string; agentId: string; suiteId: number; versionNo: number; status: string; passRate: number; p0FailedCount: number; safetyFailedCount: number; startedAt: string };
type Overview = { summary?: Record<string, number>; suites?: Suite[]; recentRuns?: Run[] };
type Envelope<T> = { success?: boolean; data?: T; message?: string };

function readToken() {
  const raw = localStorage.getItem(LS_PLATFORM_TOKEN);
  if (!raw) return "";
  try { return (JSON.parse(raw) as { token?: string }).token ?? ""; } catch { return ""; }
}

function percent(value?: number) { return `${Math.round((value ?? 0) * 100)}%`; }
function dateTime(value?: string) { return value ? new Date(value).toLocaleString("zh-CN", { hour12: false }) : "—"; }

export default function PlatformEvaluationPage() {
  const token = readToken();
  const [tab, setTab] = useState<"overview" | "suites" | "runs">("overview");
  const [overview, setOverview] = useState<Overview>({});
  const [suites, setSuites] = useState<Suite[]>([]);
  const [runs, setRuns] = useState<Run[]>([]);
  const [cases, setCases] = useState<EvalCase[]>([]);
  const [selectedSuiteId, setSelectedSuiteId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [showSuiteForm, setShowSuiteForm] = useState(false);
  const [showCaseForm, setShowCaseForm] = useState(false);
  const [suiteName, setSuiteName] = useState("");
  const [templateCode, setTemplateCode] = useState("");
  const [scopeType, setScopeType] = useState("PLATFORM_CORE");
  const [appCode, setAppCode] = useState("");
  const [industryCode, setIndustryCode] = useState("");
  const [mandatory, setMandatory] = useState(true);
  const [hiddenResults, setHiddenResults] = useState(false);
  const [caseName, setCaseName] = useState("");
  const [caseInput, setCaseInput] = useState("");
  const [caseExpected, setCaseExpected] = useState("");
  const [caseHidden, setCaseHidden] = useState(false);

  const selectedSuite = suites.find((item) => item.id === selectedSuiteId) ?? null;
  const headers = useMemo(() => ({ Authorization: `Bearer ${token}`, "Content-Type": "application/json" }), [token]);
  const request = useCallback(async <T,>(path: string, init?: RequestInit): Promise<T> => {
    const response = await fetch(`${PLATFORM_API_BASE}/evaluation${path}`, { ...init, headers: { ...headers, ...(init?.headers ?? {}) } });
    const body = (await response.json()) as Envelope<T>;
    if (!response.ok || !body.success) throw new Error(body.message || `HTTP ${response.status}`);
    return body.data as T;
  }, [headers]);

  const loadAll = useCallback(async () => {
    setLoading(true); setError("");
    try {
      const [overviewData, suiteData, runData] = await Promise.all([
        request<Overview>("/overview"), request<Suite[]>("/suites"), request<Run[]>("/runs"),
      ]);
      setOverview(overviewData ?? {}); setSuites(suiteData ?? []); setRuns(runData ?? []);
      setSelectedSuiteId((current) => current ?? suiteData?.[0]?.id ?? null);
    } catch (cause) { setError(cause instanceof Error ? cause.message : String(cause)); }
    finally { setLoading(false); }
  }, [request]);

  const loadCases = useCallback(async (suiteId: number) => {
    try { setCases(await request<EvalCase[]>(`/suites/${suiteId}/cases`)); }
    catch (cause) { setCases([]); setError(cause instanceof Error ? cause.message : String(cause)); }
  }, [request]);

  useEffect(() => { void loadAll(); }, [loadAll]);
  useEffect(() => { if (selectedSuiteId) void loadCases(selectedSuiteId); else setCases([]); }, [loadCases, selectedSuiteId]);

  const createSuite = async () => {
    if (!suiteName.trim() || !templateCode.trim()) return;
    setBusy(true); setError("");
    try {
      await request("/suites", { method: "POST", body: JSON.stringify({
        name: suiteName.trim(), description: "平台维护的标准与行业质量基线。", gateMode: "BLOCKING", minPassRate: .95,
        scopeType, visibility: hiddenResults ? "SEALED" : "AUTHORIZED", templateCode: templateCode.trim(),
        appCode: appCode.trim() || null, industryCode: industryCode.trim() || null, hiddenResults, mandatory,
      }) });
      setNotice("已创建新的不可变草稿版本。添加并审核用例后再发布。 "); setShowSuiteForm(false); await loadAll(); setTab("suites");
    } catch (cause) { setError(cause instanceof Error ? cause.message : String(cause)); }
    finally { setBusy(false); }
  };

  const createCase = async () => {
    if (!selectedSuiteId || !caseName.trim() || !caseInput.trim()) return;
    setBusy(true); setError("");
    try {
      await request(`/suites/${selectedSuiteId}/cases`, { method: "POST", body: JSON.stringify({
        name: caseName.trim(), inputText: caseInput.trim(), assertionType: "OUTPUT_CONTAINS", expectedText: caseExpected.trim(),
        priority: "P0", category: "ANSWER_QUALITY", reviewStatus: "APPROVED", redactionStatus: "NOT_REQUIRED",
        assertionConfigJson: JSON.stringify({ assertions: [{ type: "OUTPUT_CONTAINS", expected: caseExpected.trim() }] }), hiddenCase: caseHidden,
      }) });
      setCaseName(""); setCaseInput(""); setCaseExpected(""); setShowCaseForm(false); setNotice("用例已加入当前草稿。 "); await loadCases(selectedSuiteId);
    } catch (cause) { setError(cause instanceof Error ? cause.message : String(cause)); }
    finally { setBusy(false); }
  };

  const changeRelease = async (action: "publish" | "archive") => {
    if (!selectedSuiteId) return;
    setBusy(true); setError("");
    try { await request(`/suites/${selectedSuiteId}/${action}`, { method: "POST" }); setNotice(action === "publish" ? "评测集版本已发布并进入适用门禁。" : "评测集已归档。 "); await loadAll(); }
    catch (cause) { setError(cause instanceof Error ? cause.message : String(cause)); }
    finally { setBusy(false); }
  };

  const summary = overview.summary ?? {};
  return <section className="evaluation-page" aria-labelledby="platform-evaluation-title">
    <header className="evaluation-page__header"><div><p className="evaluation-page__kicker">平台运营治理</p><h1 id="platform-evaluation-title">智能体质量</h1><p>维护平台标准、应用标准与行业评测资产；租户只能看到被授权的摘要或结果。</p></div><button type="button" className="evaluation-button evaluation-button--secondary" onClick={() => void loadAll()} disabled={loading || busy}><RefreshCw size={15} />刷新</button></header>
    <nav className="evaluation-tabs" aria-label="平台评测页面">{(["overview", "suites", "runs"] as const).map((item) => <button type="button" key={item} className={tab === item ? "is-active" : ""} onClick={() => setTab(item)}>{{ overview: "治理概览", suites: "标准评测资产", runs: "全局运行洞察" }[item]}</button>)}</nav>
    {notice ? <div className="evaluation-notice" role="status">{notice}</div> : null}{error ? <div className="evaluation-error" role="alert">{error}</div> : null}{loading ? <div className="evaluation-loading">正在汇总平台质量资产…</div> : null}

    {!loading && tab === "overview" ? <div className="evaluation-section-stack">
      <section className="evaluation-metrics"><div><span>标准评测集</span><strong>{summary.suiteCount ?? 0}</strong></div><div><span>已发布版本</span><strong>{summary.publishedSuiteCount ?? 0}</strong></div><div><span>草稿版本</span><strong>{summary.draftSuiteCount ?? 0}</strong></div><div><span>隐藏用例</span><strong>{summary.hiddenCaseCount ?? 0}</strong></div><div><span>P0 / 安全失败</span><strong>{(summary.recentP0FailureCount ?? 0) + (summary.recentSafetyFailureCount ?? 0)}</strong></div></section>
      <section className="evaluation-table-section"><div className="evaluation-section-title"><h2>资产版本状态</h2><span>已发布版本不可修改，新调整必须产生新版本</span></div><table className="evaluation-table"><thead><tr><th>评测资产</th><th>范围</th><th>版本</th><th>状态</th><th>用例</th><th>门禁</th></tr></thead><tbody>{suites.map((suite) => <tr key={suite.id}><td><strong>{suite.name}</strong><small>{suite.templateCode}</small></td><td>{suite.scopeType}</td><td>v{suite.versionNo}</td><td><span className={`evaluation-status is-${suite.releaseStatus.toLowerCase()}`}>{suite.releaseStatus}</span></td><td>{suite.caseCount}</td><td>{suite.mandatory ? "强制" : "建议"} · {percent(suite.minPassRate)}</td></tr>)}</tbody></table></section>
    </div> : null}

    {!loading && tab === "suites" ? <div className="evaluation-section-stack">
      <div className="evaluation-toolbar"><span>平台资产以 templateCode + versionNo 管理，发布后只读。</span><button type="button" className="evaluation-button evaluation-button--primary" onClick={() => setShowSuiteForm((value) => !value)}><Plus size={15} />新建评测版本</button></div>
      {showSuiteForm ? <section className="evaluation-inline-form"><h2>新建标准评测集草稿</h2><div className="evaluation-form-grid evaluation-form-grid--wide"><label><span>名称</span><input value={suiteName} onChange={(event) => setSuiteName(event.target.value)} /></label><label><span>模板编码</span><input value={templateCode} onChange={(event) => setTemplateCode(event.target.value)} placeholder="industry-sales-core" /></label><label><span>资产范围</span><select value={scopeType} onChange={(event) => setScopeType(event.target.value)}><option value="PLATFORM_CORE">平台核心</option><option value="APP_STANDARD">标准应用</option><option value="INDUSTRY_STANDARD">行业标准</option></select></label><label><span>{scopeType === "INDUSTRY_STANDARD" ? "行业编码" : "应用编码（可选）"}</span><input value={scopeType === "INDUSTRY_STANDARD" ? industryCode : appCode} onChange={(event) => scopeType === "INDUSTRY_STANDARD" ? setIndustryCode(event.target.value) : setAppCode(event.target.value)} /></label></div><div className="evaluation-check-row"><label><input type="checkbox" checked={mandatory} onChange={(event) => setMandatory(event.target.checked)} />作为强制发布门禁</label><label><input type="checkbox" checked={hiddenResults} onChange={(event) => setHiddenResults(event.target.checked)} />隐藏原始输入与断言细节</label></div><div className="evaluation-form-actions"><button type="button" className="evaluation-text-action" onClick={() => setShowSuiteForm(false)}>取消</button><button type="button" className="evaluation-button evaluation-button--primary" onClick={() => void createSuite()} disabled={busy}>保存草稿</button></div></section> : null}
      <div className="evaluation-master-detail"><aside className="evaluation-suite-list">{suites.map((suite) => <button key={suite.id} type="button" className={selectedSuiteId === suite.id ? "is-active" : ""} onClick={() => setSelectedSuiteId(suite.id)}><span><strong>{suite.name}</strong><small>{suite.templateCode} · v{suite.versionNo}</small></span><span className="evaluation-suite-list__meta">{suite.releaseStatus}<br />{suite.caseCount} 条</span></button>)}</aside><section className="evaluation-detail-pane">{selectedSuite ? <><div className="evaluation-section-title"><div><h2>{selectedSuite.name}</h2><p>{selectedSuite.description || "暂无说明"}</p></div><div className="evaluation-inline-meta"><span>{selectedSuite.scopeType}</span>{selectedSuite.hiddenResults ? <span><EyeOff size={11} /> 隐藏结果</span> : null}<span>{selectedSuite.mandatory ? "强制门禁" : "建议门禁"}</span></div></div><div className="evaluation-detail-actions"><button type="button" className="evaluation-text-action" onClick={() => setShowCaseForm((value) => !value)} disabled={selectedSuite.releaseStatus !== "DRAFT"}><Plus size={14} />添加用例</button><div>{selectedSuite.releaseStatus === "DRAFT" ? <button type="button" className="evaluation-button evaluation-button--primary" onClick={() => void changeRelease("publish")} disabled={busy}><Send size={14} />发布版本</button> : <button type="button" className="evaluation-button evaluation-button--secondary" onClick={() => void changeRelease("archive")} disabled={busy}><Archive size={14} />归档</button>}</div></div>{showCaseForm && selectedSuite.releaseStatus === "DRAFT" ? <div className="evaluation-inline-form"><div className="evaluation-form-grid evaluation-form-grid--wide"><label><span>用例名称</span><input value={caseName} onChange={(event) => setCaseName(event.target.value)} /></label><label><span>期望输出要点</span><input value={caseExpected} onChange={(event) => setCaseExpected(event.target.value)} /></label><label className="is-full"><span>评测输入</span><textarea rows={3} value={caseInput} onChange={(event) => setCaseInput(event.target.value)} /></label></div><div className="evaluation-check-row"><label><input type="checkbox" checked={caseHidden} onChange={(event) => setCaseHidden(event.target.checked)} />设为平台隐藏用例</label></div><div className="evaluation-form-actions"><button type="button" className="evaluation-button evaluation-button--primary" onClick={() => void createCase()} disabled={busy}>保存用例</button></div></div> : null}<table className="evaluation-table"><thead><tr><th>用例</th><th>风险</th><th>类别</th><th>断言</th><th>可见性</th></tr></thead><tbody>{cases.map((item) => <tr key={item.id}><td>{item.name}</td><td>{item.priority}</td><td>{item.category}</td><td>{item.assertionType}</td><td>{item.hiddenCase ? "平台隐藏" : "摘要可见"}</td></tr>)}{cases.length === 0 ? <tr><td colSpan={5} className="evaluation-empty">尚未添加用例。</td></tr> : null}</tbody></table></> : <p className="evaluation-empty">选择评测资产查看版本详情。</p>}</section></div>
    </div> : null}

    {!loading && tab === "runs" ? <section className="evaluation-table-section"><div className="evaluation-section-title"><h2>跨租户运行洞察</h2><span>仅展示治理指标，不向平台运营暴露租户原始对话内容</span></div><table className="evaluation-table"><thead><tr><th>运行</th><th>组织</th><th>智能体</th><th>版本</th><th>结果</th><th>通过率</th><th>P0 / 安全</th><th>时间</th></tr></thead><tbody>{runs.map((run) => <tr key={run.id}><td>#{run.id}</td><td>{run.orgId}</td><td>{run.agentId}</td><td>v{run.versionNo}</td><td>{run.status}</td><td>{percent(run.passRate)}</td><td>{run.p0FailedCount} / {run.safetyFailedCount}</td><td>{dateTime(run.startedAt)}</td></tr>)}{runs.length === 0 ? <tr><td colSpan={8} className="evaluation-empty"><PlayCircle size={16} />暂无运行数据。</td></tr> : null}</tbody></table></section> : null}
    <footer className="evaluation-page__foot"><ShieldCheck size={15} />标准评测资产仅在平台运营端维护；租户可见范围由授权与隐藏策略共同决定。</footer>
  </section>;
}
