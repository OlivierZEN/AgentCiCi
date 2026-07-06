import { useCallback, useEffect, useMemo, useState } from "react";
import { useAdminToken } from "../useAdminToken";

type DataQualitySource = {
  sourceKey: string;
  sourceKind: string;
  sourceType: string;
  knowledgeBaseId: number;
  knowledgeBaseName?: string;
  name: string;
  status: string;
  lastSyncedAt?: string;
};

type QualityRun = {
  id: number;
  status: string;
  scannedChunkCount: number;
  duplicateIssueCount: number;
  invalidIssueCount: number;
  regexIssueCount: number;
  totalIssueCount: number;
  startedAt: string;
};

type QualityIssue = {
  id: number;
  issueType: string;
  severity: string;
  chunkId: number;
  evidence: string;
  status: string;
};

type QualityRule = {
  id: number;
  name: string;
  ruleType: string;
  pattern: string;
  replacement: string;
  enabled: boolean;
};

type QualityPreviewItem = {
  chunkId: number;
  contentHash: string;
  before: string;
  after: string;
};

type AnnotationSuggestion = {
  id: number;
  targetType: string;
  targetId: number;
  fieldKey: string;
  suggestedValue: string;
  confidence: number;
  rationale: string;
};

const DATA_QUALITY_API = "/data-quality";

function formatDate(iso?: string) {
  if (!iso) return "-";
  const d = new Date(iso);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

export default function AdminDataQualityPage() {
  const token = useAdminToken();
  const [notice, setNotice] = useState("");
  const [sources, setSources] = useState<DataQualitySource[]>([]);
  const [selectedSourceKey, setSelectedSourceKey] = useState("");
  const [runs, setRuns] = useState<QualityRun[]>([]);
  const [issues, setIssues] = useState<QualityIssue[]>([]);
  const [rules, setRules] = useState<QualityRule[]>([]);
  const [preview, setPreview] = useState<QualityPreviewItem[]>([]);
  const [suggestions, setSuggestions] = useState<AnnotationSuggestion[]>([]);
  const [ruleName, setRuleName] = useState("");
  const [ruleType, setRuleType] = useState("REGEX_REMOVE");
  const [rulePattern, setRulePattern] = useState("");
  const [ruleReplacement, setRuleReplacement] = useState("");
  const [selectedRuleId, setSelectedRuleId] = useState<number | null>(null);
  const [annotationTargetType, setAnnotationTargetType] = useState("CHUNK");
  const [annotationFieldKey, setAnnotationFieldKey] = useState("topic");

  const headers = useCallback(
    () => ({ Authorization: `Bearer ${token}` }),
    [token],
  );

  const flash = (msg: string) => {
    setNotice(msg);
    window.setTimeout(() => setNotice(""), 3000);
  };

  const selectedSource = useMemo(
    () => sources.find((item) => item.sourceKey === selectedSourceKey) ?? null,
    [sources, selectedSourceKey],
  );
  const selectedKbId = selectedSource?.knowledgeBaseId ?? null;

  const loadSources = useCallback(async () => {
    const res = await fetch(`${DATA_QUALITY_API}/sources`, { headers: headers() });
    const json = await res.json();
    if (!json.success) {
      flash(`数据源加载失败：${json.message}`);
      return;
    }
    const rows = (json.data ?? []) as DataQualitySource[];
    setSources(rows);
    setSelectedSourceKey((current) => current || rows[0]?.sourceKey || "");
  }, [headers]);

  const loadWorkspace = useCallback(
    async (kbId: number) => {
      const [runsRes, issuesRes, rulesRes, suggestionsRes] = await Promise.all([
        fetch(`${DATA_QUALITY_API}/knowledge-bases/${kbId}/runs`, { headers: headers() }),
        fetch(`${DATA_QUALITY_API}/knowledge-bases/${kbId}/issues?status=OPEN`, { headers: headers() }),
        fetch(`/kb/${kbId}/quality/rules`, { headers: headers() }),
        fetch(`/kb/${kbId}/quality/annotations/suggestions?status=PENDING`, { headers: headers() }),
      ]);
      const [runsJson, issuesJson, rulesJson, suggestionsJson] = await Promise.all([
        runsRes.json(),
        issuesRes.json(),
        rulesRes.json(),
        suggestionsRes.json(),
      ]);
      if (runsJson.success) setRuns((runsJson.data ?? []) as QualityRun[]);
      if (issuesJson.success) setIssues((issuesJson.data ?? []) as QualityIssue[]);
      if (rulesJson.success) {
        const nextRules = (rulesJson.data ?? []) as QualityRule[];
        setRules(nextRules);
        setSelectedRuleId((current) => current || nextRules[0]?.id || null);
      }
      if (suggestionsJson.success) setSuggestions((suggestionsJson.data ?? []) as AnnotationSuggestion[]);
    },
    [headers],
  );

  useEffect(() => {
    void loadSources();
  }, [loadSources]);

  useEffect(() => {
    if (!selectedKbId) return;
    void loadWorkspace(selectedKbId);
  }, [selectedKbId, loadWorkspace]);

  const startScan = async () => {
    if (!selectedKbId) return;
    const res = await fetch(`${DATA_QUALITY_API}/knowledge-bases/${selectedKbId}/runs`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...headers() },
      body: JSON.stringify({ triggerType: "MANUAL" }),
    });
    const json = await res.json();
    if (!json.success) {
      flash(`扫描失败：${json.message}`);
      return;
    }
    flash(`扫描完成，发现 ${json.data?.totalIssueCount ?? 0} 个问题`);
    await loadWorkspace(selectedKbId);
  };

  const saveRule = async () => {
    if (!selectedKbId || !ruleName.trim()) return;
    const res = await fetch(`/kb/${selectedKbId}/quality/rules`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...headers() },
      body: JSON.stringify({
        name: ruleName.trim(),
        ruleType,
        pattern: rulePattern,
        replacement: ruleReplacement,
        enabled: true,
      }),
    });
    const json = await res.json();
    if (!json.success) {
      flash(`规则保存失败：${json.message}`);
      return;
    }
    setRuleName("");
    setRulePattern("");
    setRuleReplacement("");
    setSelectedRuleId(Number(json.data?.id ?? selectedRuleId));
    flash("规则已保存");
    await loadWorkspace(selectedKbId);
  };

  const previewRule = async () => {
    if (!selectedRuleId) return;
    const res = await fetch(`/kb/quality/rules/${selectedRuleId}/preview`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...headers() },
      body: JSON.stringify({ limit: 20 }),
    });
    const json = await res.json();
    if (!json.success) {
      flash(`预览失败：${json.message}`);
      return;
    }
    setPreview((json.data?.items ?? []) as QualityPreviewItem[]);
  };

  const applyPreview = async () => {
    if (!selectedKbId || !selectedRuleId || preview.length === 0) return;
    const expectedContentHashes = Object.fromEntries(preview.map((item) => [String(item.chunkId), item.contentHash]));
    const res = await fetch(`/kb/quality/rules/${selectedRuleId}/apply`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...headers() },
      body: JSON.stringify({
        chunkIds: preview.map((item) => item.chunkId),
        expectedContentHashes,
        limit: preview.length,
      }),
    });
    const json = await res.json();
    if (!json.success) {
      flash(`应用失败：${json.message}`);
      return;
    }
    flash(`清洗已应用，更新 ${json.data?.updatedCount ?? 0} 个切片`);
    setPreview([]);
    await loadWorkspace(selectedKbId);
  };

  const markIssue = async (issueId: number, action: "resolve" | "ignore") => {
    if (!selectedKbId) return;
    const res = await fetch(`/kb/quality/issues/${issueId}/${action}`, { method: "POST", headers: headers() });
    const json = await res.json();
    if (!json.success) {
      flash(`问题处理失败：${json.message}`);
      return;
    }
    await loadWorkspace(selectedKbId);
  };

  const createSuggestions = async () => {
    if (!selectedKbId) return;
    const res = await fetch(`/kb/${selectedKbId}/quality/annotations/suggest`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...headers() },
      body: JSON.stringify({ targetType: annotationTargetType, fieldKey: annotationFieldKey, limit: 50 }),
    });
    const json = await res.json();
    if (!json.success) {
      flash(`标注建议失败：${json.message}`);
      return;
    }
    flash(`已生成 ${json.data?.createdCount ?? 0} 条建议`);
    await loadWorkspace(selectedKbId);
  };

  const reviewSuggestion = async (suggestionId: number, action: "accept" | "reject") => {
    if (!selectedKbId) return;
    const res = await fetch(`/kb/quality/annotations/suggestions/${suggestionId}/${action}`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...headers() },
      body: action === "accept" ? JSON.stringify({}) : undefined,
    });
    const json = await res.json();
    if (!json.success) {
      flash(`标注审核失败：${json.message}`);
      return;
    }
    await loadWorkspace(selectedKbId);
  };

  return (
    <div className="cici-kb-page">
      {notice && <div className="cici-toast">{notice}</div>}
      <div className="cici-kb-detail">
        <aside className="cici-kb-sidebar">
          <div className="cici-kb-sidebar__head">
            <h2 className="cici-kb-sidebar__name">数据质量平台</h2>
            <p className="cici-kb-sidebar__desc">统一扫描、清洗、复核和标注知识库及连接器数据。</p>
          </div>
          <nav className="cici-kb-sidebar__nav">
            {sources.map((source) => (
              <button
                type="button"
                key={source.sourceKey}
                className={`cici-kb-sidebar__link ${selectedSourceKey === source.sourceKey ? "active" : ""}`}
                onClick={() => {
                  setSelectedSourceKey(source.sourceKey);
                  setPreview([]);
                }}
              >
                <span>{source.sourceKind === "CONNECTOR" ? "↳" : "●"}</span>
                {source.name}
              </button>
            ))}
          </nav>
        </aside>
        <main className="cici-kb-main">
          <div className="cici-kb-main__header">
            <div>
              <h2 className="cici-kb-main__title">{selectedSource?.name ?? "暂无数据源"}</h2>
              <p className="cici-kb-main__subtitle">
                {selectedSource ? `${selectedSource.sourceKind} · ${selectedSource.sourceType} · ${selectedSource.status}` : "请先接入知识库或连接器数据源。"}
              </p>
            </div>
            <div className="cici-kb-main__actions">
              <button type="button" className="cici-btn cici-btn--primary" disabled={!selectedKbId} onClick={() => void startScan()}>
                发起扫描
              </button>
              <button type="button" className="cici-btn cici-btn--ghost" onClick={() => void loadSources()}>
                刷新数据源
              </button>
            </div>
          </div>

          <div className="cici-kb-upload-policy">
            <span>数据源 {sources.length}</span>
            <span>开放问题 {issues.length}</span>
            <span>待审核标注 {suggestions.length}</span>
            <span>最近扫描 {runs[0] ? formatDate(runs[0].startedAt) : "-"}</span>
          </div>

          <h3 className="cici-kb-main__title cici-kb-main__title--section">扫描记录</h3>
          <div className="cici-doc-table-wrap">
            <table className="cici-doc-table">
              <thead>
                <tr>
                  <th>时间</th>
                  <th>状态</th>
                  <th>扫描切片</th>
                  <th>重复</th>
                  <th>无效</th>
                  <th>规则命中</th>
                  <th>总数</th>
                </tr>
              </thead>
              <tbody>
                {runs.length === 0 && <tr><td colSpan={7} className="cici-doc-table__empty">暂无扫描记录</td></tr>}
                {runs.slice(0, 8).map((run) => (
                  <tr key={run.id}>
                    <td>{formatDate(run.startedAt)}</td>
                    <td>{run.status}</td>
                    <td>{run.scannedChunkCount}</td>
                    <td>{run.duplicateIssueCount}</td>
                    <td>{run.invalidIssueCount}</td>
                    <td>{run.regexIssueCount}</td>
                    <td>{run.totalIssueCount}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <h3 className="cici-kb-main__title cici-kb-main__title--section">问题复核</h3>
          <div className="cici-doc-table-wrap">
            <table className="cici-doc-table">
              <thead>
                <tr>
                  <th>类型</th>
                  <th>级别</th>
                  <th>chunk</th>
                  <th>证据</th>
                  <th className="cici-doc-table__th--actions">操作</th>
                </tr>
              </thead>
              <tbody>
                {issues.length === 0 && <tr><td colSpan={5} className="cici-doc-table__empty">暂无开放问题</td></tr>}
                {issues.map((issue) => (
                  <tr key={issue.id}>
                    <td>{issue.issueType}</td>
                    <td>{issue.severity}</td>
                    <td>{issue.chunkId || "-"}</td>
                    <td>{issue.evidence}</td>
                    <td className="cici-doc-table__actions">
                      <button type="button" className="cici-btn cici-btn--text cici-btn--xs" onClick={() => void markIssue(issue.id, "resolve")}>解决</button>
                      <button type="button" className="cici-btn cici-btn--text cici-btn--xs" onClick={() => void markIssue(issue.id, "ignore")}>忽略</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <h3 className="cici-kb-main__title cici-kb-main__title--section">清洗规则</h3>
          <div className="cici-kb-settings__grid cici-kb-settings__grid--metadata">
            <label className="cici-field">
              <span className="cici-field__label">规则名</span>
              <input className="cici-field__input" value={ruleName} onChange={(e) => setRuleName(e.target.value)} />
            </label>
            <label className="cici-field">
              <span className="cici-field__label">类型</span>
              <select className="cici-field__input" value={ruleType} onChange={(e) => setRuleType(e.target.value)}>
                <option value="REGEX_REMOVE">正则删除</option>
                <option value="REGEX_REPLACE">正则替换</option>
                <option value="TRIM">首尾空白</option>
                <option value="COLLAPSE_WHITESPACE">压缩空白</option>
                <option value="REMOVE_EMPTY_LINES">删除空行</option>
              </select>
            </label>
            <label className="cici-field">
              <span className="cici-field__label">pattern</span>
              <input className="cici-field__input" value={rulePattern} onChange={(e) => setRulePattern(e.target.value)} />
            </label>
            <label className="cici-field">
              <span className="cici-field__label">replacement</span>
              <input className="cici-field__input" value={ruleReplacement} onChange={(e) => setRuleReplacement(e.target.value)} />
            </label>
            <div className="cici-kb-settings__field-action">
              <button type="button" className="cici-btn cici-btn--primary" disabled={!selectedKbId || !ruleName.trim()} onClick={() => void saveRule()}>
                保存规则
              </button>
            </div>
          </div>
          <div className="cici-kb-settings__actions">
            <select className="cici-field__input" value={selectedRuleId ?? ""} onChange={(e) => setSelectedRuleId(e.target.value ? Number(e.target.value) : null)}>
              <option value="">选择规则</option>
              {rules.map((rule) => <option key={rule.id} value={rule.id}>{rule.name} · {rule.ruleType}</option>)}
            </select>
            <button type="button" className="cici-btn cici-btn--ghost" disabled={!selectedRuleId} onClick={() => void previewRule()}>预览</button>
            <button type="button" className="cici-btn cici-btn--primary" disabled={preview.length === 0} onClick={() => void applyPreview()}>应用预览结果</button>
          </div>
          {preview.length > 0 && (
            <div className="cici-doc-table-wrap">
              <table className="cici-doc-table">
                <thead><tr><th>chunk</th><th>清洗前</th><th>清洗后</th></tr></thead>
                <tbody>
                  {preview.map((item) => <tr key={item.chunkId}><td>{item.chunkId}</td><td>{item.before}</td><td>{item.after}</td></tr>)}
                </tbody>
              </table>
            </div>
          )}

          <h3 className="cici-kb-main__title cici-kb-main__title--section">智能标注</h3>
          <div className="cici-kb-settings__grid cici-kb-settings__grid--metadata">
            <label className="cici-field">
              <span className="cici-field__label">目标</span>
              <select className="cici-field__input" value={annotationTargetType} onChange={(e) => setAnnotationTargetType(e.target.value)}>
                <option value="CHUNK">chunk</option>
                <option value="DOCUMENT">document</option>
              </select>
            </label>
            <label className="cici-field">
              <span className="cici-field__label">fieldKey</span>
              <input className="cici-field__input" value={annotationFieldKey} onChange={(e) => setAnnotationFieldKey(e.target.value)} />
            </label>
            <div className="cici-kb-settings__field-action">
              <button type="button" className="cici-btn cici-btn--primary" disabled={!selectedKbId || !annotationFieldKey.trim()} onClick={() => void createSuggestions()}>
                生成建议
              </button>
            </div>
          </div>
          <div className="cici-doc-table-wrap">
            <table className="cici-doc-table">
              <thead><tr><th>目标</th><th>字段</th><th>建议值</th><th>置信度</th><th>依据</th><th className="cici-doc-table__th--actions">操作</th></tr></thead>
              <tbody>
                {suggestions.length === 0 && <tr><td colSpan={6} className="cici-doc-table__empty">暂无待审核标注建议</td></tr>}
                {suggestions.map((item) => (
                  <tr key={item.id}>
                    <td>{item.targetType} #{item.targetId}</td>
                    <td>{item.fieldKey}</td>
                    <td>{item.suggestedValue}</td>
                    <td>{item.confidence.toFixed(2)}</td>
                    <td>{item.rationale}</td>
                    <td className="cici-doc-table__actions">
                      <button type="button" className="cici-btn cici-btn--text cici-btn--xs" onClick={() => void reviewSuggestion(item.id, "accept")}>接受</button>
                      <button type="button" className="cici-btn cici-btn--text cici-btn--xs" onClick={() => void reviewSuggestion(item.id, "reject")}>拒绝</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </main>
      </div>
    </div>
  );
}
