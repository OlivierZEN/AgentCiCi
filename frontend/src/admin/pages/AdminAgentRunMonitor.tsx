import { useEffect, useMemo, useState } from "react";
import AvatarView from "../../components/AvatarView";
import { getDisplayInitial } from "../../shared/avatar";
import { safeFetchJson } from "../../utils/http";

type AgentRuntimeSnapshotPayload = {
  agentId: string;
  agentName?: string | null;
  avatarBase64?: string | null;
  status?: string;
  currentTask?: string;
  activeSessionCount?: number;
  sevenDaySessionCount?: number;
  sevenDayFailureCount?: number;
  avgLatencyMs?: number;
  lastActiveAt?: string;
  lastTraceId?: string;
  lastRunStatus?: string;
  lastChannel?: string;
  lastElapsedMs?: number;
};

type AgentRunLogPayload = {
  traceId: string;
  sessionId?: string;
  agentId?: string;
  agentName?: string;
  title?: string;
  channel?: string;
  status?: string;
  startedAt?: string;
  endedAt?: string;
  elapsedMs?: number;
  modelCallCount?: number;
  toolCallCount?: number;
  ragContextCount?: number;
  skillNames?: string[];
  activatedSkillCodes?: string[];
  boundSkillCodes?: string[];
  knowledgeBaseNames?: string[];
  summary?: string;
  errorReason?: string;
  source?: string;
};

type AgentTraceNodePayload = {
  id?: string;
  type?: string;
  title?: string;
  status?: string;
  startedAt?: string;
  endedAt?: string;
  elapsedMs?: number;
  summary?: string;
  metadata?: Record<string, unknown>;
};

type RuntimeExecutionStepPayload = {
  key?: string;
  kind?: string;
  status?: string;
  attemptNo?: number;
  startedAt?: string;
  completedAt?: string;
  elapsedMs?: number;
  evidenceSummary?: string;
};

type RuntimeExecutionPayload = {
  associated?: boolean;
  emptyReason?: string;
  runId?: number;
  mode?: string;
  terminalStatus?: string;
  riskLevel?: string;
  planRevision?: number;
  reviewStatus?: string;
  reviewGateStatus?: string;
  requiresConfirmation?: boolean;
  partialReason?: string;
  steps?: RuntimeExecutionStepPayload[];
  events?: Array<{ type?: string; occurredAt?: string }>;
};

type AgentTraceDetailPayload = {
  traceId: string;
  sessionId?: string;
  agentId?: string;
  agentName?: string;
  channel?: string;
  status?: string;
  startedAt?: string;
  endedAt?: string;
  elapsedMs?: number;
  summary?: string;
  nodes?: AgentTraceNodePayload[];
  model?: Record<string, unknown>;
  rag?: Record<string, unknown>;
  tools?: Array<Record<string, unknown>>;
  skills?: Record<string, unknown>;
  detail?: Record<string, unknown>;
  runtimeExecution?: RuntimeExecutionPayload;
  errorReason?: string;
};

type TraceTextDetail = {
  text: string;
  truncated: boolean;
  historicalFallback: boolean;
};

type Props = {
  token: string;
};

type RegressionSuitePayload = {
  id: number;
  name: string;
  platformOwned?: boolean;
};

type MonitorStatusFilter = "all" | "RUNNING" | "FAILED" | "WAITING_CONFIRMATION" | "tool" | "knowledge";

function formatMonitorDateTime(value?: string) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "—";
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(date);
}

function formatMonitorElapsed(ms?: number) {
  if (typeof ms !== "number" || !Number.isFinite(ms) || ms <= 0) return "—";
  if (ms < 1000) return `${Math.round(ms)}ms`;
  return `${(ms / 1000).toFixed(ms < 10_000 ? 1 : 0)}s`;
}

function monitorStatusLabel(status?: string) {
  switch ((status ?? "").toUpperCase()) {
    case "RUNNING":
      return "运行中";
    case "WAITING_CONFIRMATION":
      return "待确认";
    case "FAILED":
      return "异常";
    case "COMPLETED":
      return "已完成";
    case "IDLE":
      return "待命中";
    default:
      return status || "未知";
  }
}

function monitorStatusSeverity(status?: string) {
  switch ((status ?? "").toUpperCase()) {
    case "RUNNING":
      return "running";
    case "WAITING_CONFIRMATION":
      return "waiting";
    case "FAILED":
      return "failed";
    default:
      return "completed";
  }
}

function monitorChannelLabel(channel?: string) {
  switch ((channel ?? "").toLowerCase()) {
    case "api":
      return "Open API";
    case "feishu":
      return "飞书";
    case "wecom":
    case "wechat":
    case "wechat_kf":
      return "企微";
    case "dingtalk":
      return "钉钉";
    case "scheduled":
      return "定时任务";
    default:
      return "Web";
  }
}

function compactUnknownValue(value: unknown, fallback = "—"): string {
  if (value === null || value === undefined) return fallback;
  if (Array.isArray(value)) {
    return value.length > 0 ? value.map((item) => compactUnknownValue(item, "")).filter(Boolean).join("、") : fallback;
  }
  if (typeof value === "object") {
    const maybeName = (value as Record<string, unknown>).name ?? (value as Record<string, unknown>).title;
    if (maybeName) return String(maybeName);
    return fallback;
  }
  const text = String(value).trim();
  return text || fallback;
}

function monitorModelTraceSummary(trace?: AgentTraceDetailPayload | null) {
  const calls = trace?.detail?.modelCalls;
  if (Array.isArray(calls) && calls.length > 0) {
    return calls
      .map((call, index) => {
        const item = call as Record<string, unknown>;
        const phase = compactUnknownValue(item.phase, `调用 ${index + 1}`);
        const elapsed = typeof item.elapsedMs === "number" ? formatMonitorElapsed(item.elapsedMs) : "—";
        return `${phase} ${elapsed}`;
      })
      .join("；");
  }
  return compactUnknownValue(trace?.model?.modelName, "模型名未记录");
}

function monitorToolTraceSummary(trace?: AgentTraceDetailPayload | null) {
  if (!trace?.tools?.length) return "未调用工具";
  return trace.tools
    .map((tool) => {
      const name = compactUnknownValue(tool.name, "tool");
      const elapsed = typeof tool.elapsedMs === "number" ? formatMonitorElapsed(tool.elapsedMs) : "—";
      const failed = tool.success === false || String(tool.status ?? "").toUpperCase() === "FAILED";
      const reason = compactUnknownValue(tool.errorMessage ?? tool.result, "");
      return failed ? `${name} ${elapsed} · 失败：${reason || "未提供错误详情"}` : `${name} ${elapsed}`;
    })
    .join("；");
}

function formatTraceStepElapsed(ms?: number) {
  if (typeof ms !== "number" || !Number.isFinite(ms) || ms <= 0) return "0ms";
  return formatMonitorElapsed(ms);
}

function runtimeStatusLabel(status?: string) {
  switch ((status ?? "").toUpperCase()) {
    case "PLAN_EXEC": return "计划执行";
    case "DIRECT": return "直接生成";
    case "REACT": return "工具循环";
    case "LOW": return "低";
    case "MEDIUM": return "中";
    case "HIGH": return "高";
    case "RETRIEVE": return "检索";
    case "SYNTHESIZE": return "生成";
    case "VERIFY": return "核验";
    case "TOOL": return "工具";
    case "REQUEST_CONFIRMATION": return "请求确认";
    case "SUCCEEDED": return "已完成";
    case "FAILED": return "失败";
    case "RUNNING": return "运行中";
    case "READY": return "待执行";
    case "CREATED": return "已创建";
    case "HANDOFF": return "需人工处理";
    case "PASS": return "通过";
    case "NOT_REQUESTED": return "未请求";
    default: return status || "—";
  }
}

function numberFromMetadata(metadata: Record<string, unknown> | undefined, keys: string[]) {
  if (!metadata) return 0;
  for (const key of keys) {
    const value = metadata[key];
    if (typeof value === "number" && Number.isFinite(value)) return Math.max(0, Math.round(value));
    if (typeof value === "string" && value.trim()) {
      const parsed = Number.parseInt(value, 10);
      if (Number.isFinite(parsed)) return Math.max(0, parsed);
    }
  }
  return 0;
}

function traceStepTokenSummary(node: AgentTraceNodePayload) {
  if ((node.type ?? "").toUpperCase() !== "MODEL") return "";
  const inputTokens = numberFromMetadata(node.metadata, ["inputTokens", "promptTokens", "prompt_tokens", "input_tokens"]);
  const outputTokens = numberFromMetadata(node.metadata, ["outputTokens", "completionTokens", "completion_tokens", "output_tokens"]);
  return `输入 ${inputTokens} tokens · 输出 ${outputTokens} tokens`;
}

function recordValue(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

export function traceNodeTextDetail(node: AgentTraceNodePayload, trace?: AgentTraceDetailPayload | null): TraceTextDetail | null {
  const type = (node.type ?? "").toUpperCase();
  const detail = recordValue(trace?.detail);
  const payload = type === "USER_MESSAGE"
    ? recordValue(detail.request)
    : type === "MODEL" || type === "MODEL_CALL"
      ? recordValue(detail.response)
      : {};
  const detailKey = type === "USER_MESSAGE" ? "questionDetail" : "answerDetail";
  const legacyKey = type === "USER_MESSAGE" ? "question" : "answer";
  const stored = recordValue(payload[detailKey]);
  const text = typeof stored.text === "string" ? stored.text.trim() : "";
  if (text) {
    return {
      text,
      truncated: stored.truncated === true,
      historicalFallback: false,
    };
  }
  const legacyText = typeof payload[legacyKey] === "string" ? payload[legacyKey].trim() : "";
  if (!legacyText) return null;
  return {
    text: legacyText,
    truncated: true,
    historicalFallback: true,
  };
}

export function traceRuntimeEmptyMessage(runtime?: RuntimeExecutionPayload): string {
  return runtime?.associated ? "" : "此 Trace 没有关联运行执行事实";
}

export default function AdminAgentRunMonitor({ token }: Props) {
  const [runtimeSnapshots, setRuntimeSnapshots] = useState<AgentRuntimeSnapshotPayload[]>([]);
  const [runLogs, setRunLogs] = useState<AgentRunLogPayload[]>([]);
  const [traceDetail, setTraceDetail] = useState<AgentTraceDetailPayload | null>(null);
  const [logsLoading, setLogsLoading] = useState(false);
  const [traceLoadingId, setTraceLoadingId] = useState("");
  const [activeAgentId, setActiveAgentId] = useState("");
  const [activeLogId, setActiveLogId] = useState("");
  const [searchText, setSearchText] = useState("");
  const [statusFilter, setStatusFilter] = useState<MonitorStatusFilter>("all");
  const [traceFeedbackOpen, setTraceFeedbackOpen] = useState(false);
  const [regressionSuites, setRegressionSuites] = useState<RegressionSuitePayload[]>([]);
  const [regressionSuiteId, setRegressionSuiteId] = useState("");
  const [regressionCaseName, setRegressionCaseName] = useState("");
  const [feedbackBusy, setFeedbackBusy] = useState(false);
  const [feedbackNotice, setFeedbackNotice] = useState("");
  const [expandedTraceNodeIds, setExpandedTraceNodeIds] = useState<Set<string>>(new Set());
  const [expandedRuntimeStepKeys, setExpandedRuntimeStepKeys] = useState<Set<string>>(new Set());
  const [traceDetailNotice, setTraceDetailNotice] = useState("");

  const loadRuntimeSnapshots = async () => {
    try {
      const response = await fetch("/admin/agents/runtime-snapshots", { headers: { Authorization: `Bearer ${token}` } });
      const { body } = await safeFetchJson<{ items?: AgentRuntimeSnapshotPayload[] }>(response);
      if (!response.ok || !body?.success || !Array.isArray(body.data?.items)) {
        throw new Error(body?.message ?? `HTTP ${response.status}`);
      }
      setRuntimeSnapshots(body.data.items.filter((item) => item.agentId));
    } catch {
      setRuntimeSnapshots([]);
    }
  };

  const loadRunLogs = async () => {
    setLogsLoading(true);
    try {
      const params = new URLSearchParams({ limit: "100" });
      const response = await fetch(`/admin/agents/run-logs?${params.toString()}`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      const { body } = await safeFetchJson<{ items?: AgentRunLogPayload[] } | AgentRunLogPayload[]>(response);
      if (!response.ok || !body?.success) {
        throw new Error(body?.message ?? `HTTP ${response.status}`);
      }
      const data = body.data as { items?: AgentRunLogPayload[] } | AgentRunLogPayload[] | undefined;
      const items = Array.isArray(data) ? data : (data?.items ?? []);
      setRunLogs(items.filter((item) => item.traceId));
    } catch {
      setRunLogs([]);
    } finally {
      setLogsLoading(false);
    }
  };

  const loadTraceDetail = async (traceId: string) => {
    if (!traceId) {
      setTraceDetail(null);
      return;
    }
    setTraceLoadingId(traceId);
    try {
      const response = await fetch(`/admin/agents/run-logs/${encodeURIComponent(traceId)}`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      const { body } = await safeFetchJson<AgentTraceDetailPayload>(response);
      if (!response.ok || !body?.success || !body.data) {
        throw new Error(body?.message ?? `HTTP ${response.status}`);
      }
      setTraceDetail(body.data as AgentTraceDetailPayload);
    } catch {
      setTraceDetail(null);
    } finally {
      setTraceLoadingId((current) => (current === traceId ? "" : current));
    }
  };

  useEffect(() => {
    void loadRuntimeSnapshots();
    void loadRunLogs();
  }, [token]);

  const logRows = useMemo(() => runLogs.map((item) => {
    const severity = monitorStatusSeverity(item.status);
    const traceShort = item.traceId.length > 12 ? item.traceId.slice(0, 8) : item.traceId;
    const chainParts = [
      item.modelCallCount ? `模型 ${item.modelCallCount}` : "",
      item.toolCallCount ? `工具 ${item.toolCallCount}` : "",
      item.ragContextCount ? `知识 ${item.ragContextCount}` : "",
      item.skillNames?.length ? `技能 ${item.skillNames.length}` : "",
    ].filter(Boolean);
    return {
      id: item.traceId,
      traceId: item.traceId,
      recordId: traceShort,
      sessionId: item.sessionId ?? "",
      agentId: item.agentId ?? "",
      agentName: item.agentName ?? item.agentId ?? "智能体",
      title: item.title || item.summary || "未命名运行记录",
      detail: `${monitorChannelLabel(item.channel)} · ${formatMonitorDateTime(item.startedAt)}`,
      status: monitorStatusLabel(item.status),
      rawStatus: (item.status ?? "").toUpperCase(),
      severity,
      chain: chainParts.length ? chainParts.join(" · ") : "消息链路",
      latency: formatMonitorElapsed(item.elapsedMs),
      summary: item.summary ?? "",
      errorReason: item.errorReason ?? "",
      source: item.source ?? "trace",
    };
  }), [runLogs]);

  const agentRows = useMemo(() => {
    const rows = runtimeSnapshots.map((agent) => {
      const agentId = agent.agentId;
      const status = (agent.status ?? "").toUpperCase();
      const severity = status === "RUNNING"
        ? "busy"
        : status === "FAILED" || status === "WAITING_CONFIRMATION"
          ? "warn"
          : agent.sevenDaySessionCount
            ? "ok"
            : "idle";
      return {
        key: agentId,
        name: agent.agentName?.trim() || agentId,
        short: getDisplayInitial(agent.agentName?.trim() || agentId, "A").slice(0, 1),
        avatarBase64: (agent.avatarBase64 ?? "").trim(),
        status: monitorStatusLabel(agent.status),
        currentTask: agent.currentTask || "暂无运行记录",
        logCount: agent.sevenDaySessionCount ?? 0,
        failedCount: agent.sevenDayFailureCount ?? 0,
        avgLatencyMs: agent.avgLatencyMs ?? 0,
        severity,
      };
    });
    const known = new Set(rows.map((item) => item.key));
    for (const log of logRows) {
      if (!log.agentId || known.has(log.agentId)) continue;
      rows.push({
        key: log.agentId,
        name: log.agentName,
        short: getDisplayInitial(log.agentName, "A").slice(0, 1),
        avatarBase64: "",
        status: log.status,
        currentTask: log.summary || log.title,
        logCount: logRows.filter((item) => item.agentId === log.agentId).length,
        failedCount: logRows.filter((item) => item.agentId === log.agentId && (item.rawStatus === "FAILED" || item.rawStatus === "WAITING_CONFIRMATION")).length,
        avgLatencyMs: 0,
        severity: log.rawStatus === "FAILED" || log.rawStatus === "WAITING_CONFIRMATION" ? "warn" : "ok",
      });
    }
    return rows;
  }, [runtimeSnapshots, logRows]);

  const searchQuery = searchText.trim().toLowerCase();
  const filteredLogs = logRows.filter((item) => {
    if (activeAgentId && item.agentId !== activeAgentId) return false;
    if (statusFilter === "tool" && !item.chain.includes("工具")) return false;
    if (statusFilter === "knowledge" && !item.chain.includes("知识")) return false;
    if (!["all", "tool", "knowledge"].includes(statusFilter) && item.rawStatus !== statusFilter) return false;
    if (!searchQuery) return true;
    return [
      item.recordId,
      item.title,
      item.detail,
      item.agentName,
      item.summary,
      item.sessionId,
    ].some((value) => value.toLowerCase().includes(searchQuery));
  });

  const selectedLog = filteredLogs.find((item) => item.id === activeLogId) ?? filteredLogs[0] ?? logRows[0];
  const selectedTrace = traceDetail && selectedLog && traceDetail.traceId === selectedLog.traceId ? traceDetail : null;
  const busyCount = runtimeSnapshots.filter((row) => row.status === "RUNNING").length;
  const warningCount = runtimeSnapshots.filter((row) => row.status === "FAILED" || row.status === "WAITING_CONFIRMATION").length;

  useEffect(() => {
    if (selectedLog && activeLogId !== selectedLog.id) {
      setActiveLogId(selectedLog.id);
    }
  }, [activeLogId, selectedLog?.id]);

  useEffect(() => {
    if (!selectedLog?.traceId) {
      setTraceDetail(null);
      return;
    }
    void loadTraceDetail(selectedLog.traceId);
  }, [selectedLog?.traceId, token]);

  const openTraceFeedback = async () => {
    if (!selectedLog?.agentId) return;
    setFeedbackNotice("");
    setRegressionCaseName(`生产问题回归 · ${selectedLog.title}`);
    setTraceFeedbackOpen(true);
    try {
      const response = await fetch(`/evaluation/suites?agentId=${encodeURIComponent(selectedLog.agentId)}`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      const { body } = await safeFetchJson<RegressionSuitePayload[]>(response);
      if (!response.ok || !body?.success || !Array.isArray(body.data)) throw new Error(body?.message ?? `HTTP ${response.status}`);
      const editable = body.data.filter((suite) => !suite.platformOwned);
      setRegressionSuites(editable);
      setRegressionSuiteId(editable[0]?.id ? String(editable[0].id) : "");
    } catch (cause) {
      setRegressionSuites([]);
      setFeedbackNotice(cause instanceof Error ? cause.message : "评测集加载失败");
    }
  };

  const createRegressionCase = async () => {
    if (!selectedLog?.traceId || !selectedLog.agentId || !regressionSuiteId) return;
    setFeedbackBusy(true); setFeedbackNotice("");
    try {
      const response = await fetch("/evaluation/cases/from-trace", {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({
          traceId: selectedLog.traceId,
          agentId: selectedLog.agentId,
          suiteId: Number(regressionSuiteId),
          name: regressionCaseName.trim() || "来自生产 Trace 的回归用例",
          priority: selectedLog.rawStatus === "FAILED" ? "P0" : "P1",
          category: selectedLog.rawStatus === "FAILED" ? "RUNTIME_RELIABILITY" : "ANSWER_QUALITY",
        }),
      });
      const { body } = await safeFetchJson<Record<string, unknown>>(response);
      if (!response.ok || !body?.success) throw new Error(body?.message ?? `HTTP ${response.status}`);
      setFeedbackNotice("已脱敏加入回归集，当前状态为待审核。请在 AI 质量中心补充期望断言。 ");
    } catch (cause) {
      setFeedbackNotice(cause instanceof Error ? cause.message : "回归用例创建失败");
    } finally {
      setFeedbackBusy(false);
    }
  };

  const statusClass = (severity: string) =>
    severity === "busy" ? "is-running" : severity === "warn" ? "is-waiting" : severity === "ok" ? "is-ok" : "is-idle";

  const toggleTraceNodeDetail = (nodeId: string) => {
    setExpandedTraceNodeIds((current) => {
      const next = new Set(current);
      if (next.has(nodeId)) next.delete(nodeId); else next.add(nodeId);
      return next;
    });
    setTraceDetailNotice("");
  };

  const toggleRuntimeStepEvidence = (stepKey: string) => {
    setExpandedRuntimeStepKeys((current) => {
      const next = new Set(current);
      if (next.has(stepKey)) next.delete(stepKey); else next.add(stepKey);
      return next;
    });
    setTraceDetailNotice("");
  };

  const copyTraceNodeDetail = async (text: string) => {
    try {
      if (!navigator.clipboard?.writeText) throw new Error("Clipboard unavailable");
      await navigator.clipboard.writeText(text);
      setTraceDetailNotice("已复制脱敏后的详情内容。");
    } catch {
      setTraceDetailNotice("复制失败，请选择内容后手动复制。");
    }
  };

  return (
    <main className="cici-monitor cici-monitor--admin">
      <header className="cici-monitor__topbar">
        <section>
          <p className="cici-monitor__kicker">AGENT OBSERVABILITY</p>
          <h1>智能体运行</h1>
          <p>查看组织内智能体最近 7 天的运行日志、模型调用、工具执行、知识库检索与链路追踪。</p>
        </section>
        <section className="cici-monitor__metrics" aria-label="监控指标">
          <article>
            <span>组织智能体</span>
            <strong>{agentRows.length}</strong>
          </article>
          <article>
            <span>运行中</span>
            <strong>{busyCount || "—"}</strong>
          </article>
          <article>
            <span>异常/待确认</span>
            <strong>{warningCount || "—"}</strong>
          </article>
          <article>
            <span>7 天日志</span>
            <strong>{logRows.length || "—"}</strong>
          </article>
          <article>
            <span>真实链路</span>
            <strong>{runLogs.filter((item) => item.source !== "chat_session").length || "—"}</strong>
          </article>
        </section>
      </header>

      <section className="cici-monitor__toolbar" aria-label="监控筛选">
        <button type="button" className="cici-monitor__select">近 7 天 <span aria-hidden>⌄</span></button>
        <button
          type="button"
          className="cici-monitor__select"
          onClick={() => {
            setActiveAgentId("");
            setActiveLogId("");
          }}
        >
          {activeAgentId ? agentRows.find((row) => row.key === activeAgentId)?.name ?? "全部智能体" : "全部智能体"} <span aria-hidden>⌄</span>
        </button>
        <label className="cici-monitor__search">
          <span className="cici-monitor__search-icon" aria-hidden />
          <input
            type="text"
            value={searchText}
            onChange={(event) => setSearchText(event.target.value)}
            placeholder="搜索执行记录、用户会话或摘要"
            aria-label="搜索运行日志"
          />
        </label>
        <button
          type="button"
          className="cici-monitor__refresh"
          onClick={() => {
            void loadRuntimeSnapshots();
            void loadRunLogs();
          }}
        >
          刷新状态
        </button>
      </section>

      <section className="cici-monitor__workspace">
        <aside className="cici-monitor-panel cici-monitor-panel--agents">
          <header className="cici-monitor-panel__head">
            <h2>智能体状态</h2>
            <span>组织内</span>
          </header>
          <div className="cici-monitor-agent-list">
            {agentRows.map((row, index) => (
              <button
                key={row.key}
                type="button"
                className={`cici-monitor-agent${activeAgentId === row.key ? " is-active" : ""}`}
                onClick={() => {
                  setActiveAgentId(row.key);
                  setActiveLogId("");
                }}
              >
                <AvatarView
                  src={row.avatarBase64}
                  fallback={row.short}
                  className={`cici-monitor-agent__avatar cici-monitor-agent__avatar--${(index % 3) + 1}`}
                  alt={`${row.name} 监控头像`}
                />
                <span className="cici-monitor-agent__body">
                  <strong>{row.name}</strong>
                  <span>{row.logCount ? `7 天 ${row.logCount} 次 · 异常 ${row.failedCount}` : row.currentTask}</span>
                  {row.avgLatencyMs ? <span>平均耗时 {formatMonitorElapsed(row.avgLatencyMs)}</span> : null}
                </span>
                <span className={`cici-monitor-status ${statusClass(row.severity)}`}>{row.status}</span>
              </button>
            ))}
            {agentRows.length === 0 ? (
              <div className="cici-monitor__empty">当前组织没有可监控的智能体运行数据。</div>
            ) : null}
          </div>
        </aside>

        <section className="cici-monitor-panel cici-monitor-panel--logs">
          <header className="cici-monitor-panel__head">
            <h2>最近 7 天运行日志</h2>
            <span>{logsLoading ? "加载中" : `${filteredLogs.length} 条记录`}</span>
          </header>
          <nav className="cici-monitor-tabs" aria-label="日志范围">
            {[
              ["all", "全部"],
              ["RUNNING", "运行中"],
              ["FAILED", "异常"],
              ["WAITING_CONFIRMATION", "待确认"],
              ["tool", "工具调用"],
              ["knowledge", "知识库检索"],
            ].map(([value, label]) => (
              <button
                key={value}
                type="button"
                className={`cici-monitor-tab${statusFilter === value ? " is-active" : ""}`}
                onClick={() => {
                  setStatusFilter(value as MonitorStatusFilter);
                  setActiveLogId("");
                }}
              >
                {label}
              </button>
            ))}
          </nav>
          <div className="cici-monitor-log-list">
            {filteredLogs.map((log) => (
              <button
                key={log.id}
                type="button"
                className={`cici-monitor-log${selectedLog?.id === log.id ? " is-selected" : ""}`}
                onClick={() => setActiveLogId(log.id)}
              >
                <span className="cici-monitor-log__title">
                  <strong>{log.title}</strong>
                  <span>{log.errorReason ? `报错：${log.errorReason}` : `执行记录 ${log.recordId} · ${log.detail}`}</span>
                </span>
                <span className={`cici-monitor-status is-${log.severity}`}>{log.status}</span>
                <span className="cici-monitor-log__agent">
                  <strong>{log.agentName}</strong>
                  <span>{log.source === "chat_session" ? "历史会话回填" : "真实 trace"}</span>
                </span>
                <span className="cici-monitor-log__chain">
                  <span>{log.chain}</span>
                </span>
                <span className="cici-monitor-log__latency">
                  <strong>{log.latency}</strong>
                  <span>{log.latency === "—" ? "历史记录" : "总耗时"}</span>
                </span>
              </button>
            ))}
            {filteredLogs.length === 0 ? (
              <div className="cici-monitor__empty">当前筛选条件下没有运行日志。</div>
            ) : null}
          </div>
        </section>

        <aside className="cici-monitor-panel cici-monitor-panel--trace">
          <header className="cici-monitor-panel__head">
            <h2>链路追踪</h2>
            <div className="cici-monitor-trace-head-actions">
              <span>{selectedLog ? `执行记录 ${selectedLog.recordId}` : "未选择"}</span>
              <button type="button" onClick={() => void openTraceFeedback()} disabled={!selectedLog?.agentId}>加入回归集</button>
            </div>
          </header>
          {traceFeedbackOpen ? (
            <section className="cici-monitor-regression-form" aria-label="将生产链路加入回归集">
              <label><span>目标评测集</span><select value={regressionSuiteId} onChange={(event) => setRegressionSuiteId(event.target.value)}><option value="">选择组织私有评测集</option>{regressionSuites.map((suite) => <option key={suite.id} value={suite.id}>{suite.name}</option>)}</select></label>
              <label><span>用例名称</span><input value={regressionCaseName} onChange={(event) => setRegressionCaseName(event.target.value)} /></label>
              <div><button type="button" onClick={() => setTraceFeedbackOpen(false)}>取消</button><button type="button" onClick={() => void createRegressionCase()} disabled={!regressionSuiteId || feedbackBusy}>{feedbackBusy ? "处理中…" : "脱敏并加入"}</button></div>
              {feedbackNotice ? <p role="status">{feedbackNotice}</p> : null}
            </section>
          ) : null}
          {selectedLog ? (
            <>
              <section className="cici-monitor-trace-summary">
                <div>
                  <span>智能体</span>
                  <strong>{selectedLog.agentName}</strong>
                </div>
                <div>
                  <span>状态</span>
                  <strong>{selectedLog.status}</strong>
                </div>
                <div>
                  <span>渠道</span>
                  <strong>{selectedTrace ? monitorChannelLabel(selectedTrace.channel) : "—"}</strong>
                </div>
                <div>
                  <span>耗时</span>
                  <strong>{selectedTrace ? formatMonitorElapsed(selectedTrace.elapsedMs) : "—"}</strong>
                </div>
              </section>
              <section className="cici-monitor-runtime" aria-label="运行执行">
                <header className="cici-monitor-runtime__head">
                  <div>
                    <span>运行执行</span>
                    <p>计划运行事实</p>
                  </div>
                  {selectedTrace?.runtimeExecution?.associated ? <span>运行 #{selectedTrace.runtimeExecution.runId}</span> : null}
                </header>
                {selectedTrace?.runtimeExecution?.associated ? (
                  <>
                    <div className="cici-monitor-runtime__summary">
                      <div><span>模式</span><strong>{runtimeStatusLabel(selectedTrace.runtimeExecution.mode)}</strong></div>
                      <div><span>终态</span><strong>{runtimeStatusLabel(selectedTrace.runtimeExecution.terminalStatus)}</strong></div>
                      <div><span>风险</span><strong>{runtimeStatusLabel(selectedTrace.runtimeExecution.riskLevel)}</strong></div>
                      <div><span>计划修订</span><strong>v{selectedTrace.runtimeExecution.planRevision ?? 0}</strong></div>
                      <div><span>审查</span><strong>{runtimeStatusLabel(selectedTrace.runtimeExecution.reviewStatus)}</strong></div>
                    </div>
                    {selectedTrace.runtimeExecution.steps?.length ? (
                      <div className="cici-monitor-runtime__timeline" aria-label="运行步骤">
                        {selectedTrace.runtimeExecution.steps.map((step, index) => {
                          const stepKey = step.key || `runtime-step-${index}`;
                          const evidence = step.evidenceSummary?.trim() || "";
                          const expanded = expandedRuntimeStepKeys.has(stepKey);
                          return (
                            <article className="cici-monitor-runtime-step" key={stepKey}>
                              <span className={`cici-monitor-runtime-step__dot is-${monitorStatusSeverity(step.status)}`} aria-hidden />
                              <div>
                                <h3><span>{step.key || "运行步骤"}</span><em>{runtimeStatusLabel(step.kind)}</em></h3>
                                <p>{runtimeStatusLabel(step.status)} · {formatMonitorElapsed(step.elapsedMs)}</p>
                                {evidence ? (
                                  <div className="cici-monitor-runtime-step__evidence">
                                    <button
                                      type="button"
                                      className="cici-monitor-trace-step__detail-command"
                                      aria-expanded={expanded}
                                      aria-controls={`runtime-evidence-${stepKey}`}
                                      onClick={() => toggleRuntimeStepEvidence(stepKey)}
                                    >
                                      {expanded ? "收起证据" : "展开证据"}
                                    </button>
                                    {expanded ? (
                                      <section id={`runtime-evidence-${stepKey}`} className="cici-monitor-runtime-step__evidence-detail" aria-label={`${step.key || "运行步骤"}脱敏证据`}>
                                        <p>{evidence}</p>
                                        <button type="button" className="cici-monitor-trace-step__detail-command" onClick={() => void copyTraceNodeDetail(evidence)}>复制证据</button>
                                      </section>
                                    ) : null}
                                  </div>
                                ) : null}
                              </div>
                              <time>{formatMonitorDateTime(step.completedAt || step.startedAt)}</time>
                            </article>
                          );
                        })}
                      </div>
                    ) : null}
                    {selectedTrace.runtimeExecution.events?.length ? (
                      <p className="cici-monitor-runtime__events">运行事件：{selectedTrace.runtimeExecution.events
                        .map((event) => `${event.type || "EVENT"} ${formatMonitorDateTime(event.occurredAt)}`)
                        .join(" · ")}</p>
                    ) : null}
                    {selectedTrace.runtimeExecution.requiresConfirmation ? (
                      <p className="cici-monitor-runtime__exception">此运行受人工确认约束；未经既有确认流程不会执行写入。</p>
                    ) : null}
                    {selectedTrace.runtimeExecution.partialReason ? (
                      <p className="cici-monitor-runtime__exception is-warning">未完成原因：{selectedTrace.runtimeExecution.partialReason}</p>
                    ) : null}
                  </>
                ) : (
                  <p className="cici-monitor-runtime__empty">{traceRuntimeEmptyMessage(selectedTrace?.runtimeExecution)}</p>
                )}
              </section>
              <section className={`cici-monitor-trace-steps${selectedTrace?.nodes?.length ? "" : " cici-monitor-trace-steps--empty"}`}>
                {selectedTrace?.nodes?.length ? (
                  selectedTrace.nodes.map((node, index) => {
                    const nodeId = node.id ?? `${node.type}-${index}`;
                    const detail = traceNodeTextDetail(node, selectedTrace);
                    const expanded = expandedTraceNodeIds.has(nodeId);
                    return (
                    <article className="cici-monitor-trace-step" key={nodeId}>
                      <span className="cici-monitor-trace-step__dot" aria-hidden />
                      <div>
                        <h3>
                          <span>{node.title || node.type || "链路节点"}</span>
                          <time className="cici-monitor-trace-step__started-at">{formatMonitorDateTime(node.startedAt)}</time>
                        </h3>
                        <p>{node.summary || "节点已记录。"}</p>
                        {detail ? (
                          <div className="cici-monitor-trace-step__detail-actions">
                            <button
                              type="button"
                              className="cici-monitor-trace-step__detail-command"
                              aria-expanded={expanded}
                              aria-controls={`trace-detail-${nodeId}`}
                              onClick={() => toggleTraceNodeDetail(nodeId)}
                            >
                              {expanded ? "收起全文" : "展开全文"}
                            </button>
                            {expanded ? (
                              <section id={`trace-detail-${nodeId}`} className="cici-monitor-trace-step__detail" aria-label={`${node.title || "链路节点"}完整内容`}>
                                <pre>{detail.text}</pre>
                                <div>
                                  <span>{detail.historicalFallback ? "历史记录仅保留旧版详情，可能已截断。" : detail.truncated ? "内容超过保存上限，已显示保留部分。" : "已脱敏的完整详情。"}</span>
                                  <button type="button" className="cici-monitor-trace-step__detail-command" onClick={() => void copyTraceNodeDetail(detail.text)}>复制内容</button>
                                </div>
                              </section>
                            ) : null}
                          </div>
                        ) : null}
                      </div>
                      <div className="cici-monitor-trace-step__meta">
                        <time>{formatTraceStepElapsed(node.elapsedMs)}</time>
                        {traceStepTokenSummary(node) ? (
                          <span className="cici-monitor-trace-step__tokens">{traceStepTokenSummary(node)}</span>
                        ) : null}
                      </div>
                    </article>
                  );
                  })
                ) : (
                  <article className="cici-monitor-trace-step">
                    <span className="cici-monitor-trace-step__dot" aria-hidden />
                    <div>
                      <h3>
                        <span>{traceLoadingId ? "正在加载链路日志" : "暂无链路详情"}</span>
                        <time className="cici-monitor-trace-step__started-at">—</time>
                      </h3>
                      <p>{traceLoadingId ? "正在读取本次运行的模型、工具、技能和知识库明细。" : "该记录可能是历史会话回填，或后端尚未返回详情。"}</p>
                    </div>
                    <div className="cici-monitor-trace-step__meta">
                      <time>0ms</time>
                    </div>
                  </article>
                )}
              </section>
              {traceDetailNotice ? <p className="cici-monitor-trace-detail-notice" role="status">{traceDetailNotice}</p> : null}
              <section className="cici-monitor-detail-groups">
                <article>
                  <h3>大模型交互</h3>
                  <p>{selectedTrace ? monitorModelTraceSummary(selectedTrace) : "正在等待链路详情。"}</p>
                </article>
                <article>
                  <h3>工具调用</h3>
                  <p>{selectedTrace ? monitorToolTraceSummary(selectedTrace) : "正在等待链路详情。"}</p>
                </article>
                <article>
                  <h3>技能与知识库</h3>
                  <p>
                    {selectedTrace
                      ? [
                          (() => {
                            const activated = compactUnknownValue(
                              selectedTrace.skills?.activatedSkillCodes ?? selectedTrace.skills?.skillNames,
                              "",
                            );
                            const bound = compactUnknownValue(selectedTrace.skills?.boundSkillCodes, "");
                            const requested = compactUnknownValue(selectedTrace.skills?.requestedSkillCode, "");
                            const effective = compactUnknownValue(
                              selectedTrace.skills?.effectiveSkillCode ?? selectedTrace.skills?.activeSkillCode,
                              "",
                            );
                            const selectionStatus = compactUnknownValue(selectedTrace.skills?.selectionStatus, "");
                            const selectionReason = compactUnknownValue(selectedTrace.skills?.selectionReason, "");
                            const selection = requested
                              ? selectionStatus === "FORCED"
                                ? `用户选择：${requested} · 有效上下文：${effective || "未生效"}`
                                : `用户选择：${requested} · 未采纳：${selectionReason || "未形成有效上下文"}`
                              : effective
                                ? `有效上下文：${effective}`
                                : "";
                            const activation = activated
                              ? `实际激活：${activated}`
                              : bound
                                ? `未激活业务技能 · 候选：${bound}`
                                : "";
                            return [selection, activation].filter(Boolean).join(" · ");
                          })(),
                          compactUnknownValue((selectedTrace.rag?.knowledgeBases as unknown[] | undefined)?.map((kb) => compactUnknownValue(kb, "")), ""),
                        ].filter(Boolean).join(" · ") || "本轮未命中技能或知识库"
                      : "正在等待链路详情。"}
                  </p>
                </article>
                <article>
                  <h3>摘要</h3>
                  <p>{selectedTrace?.errorReason || selectedTrace?.summary || selectedLog.errorReason || selectedLog.summary || selectedLog.title}</p>
                </article>
              </section>
            </>
          ) : (
            <div className="cici-monitor__empty">请选择一条运行日志查看链路追踪。</div>
          )}
        </aside>
      </section>
    </main>
  );
}
