import { useEffect, useMemo, useRef, useState } from "react";
import { ArrowLeft, ExternalLink } from "lucide-react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { LS_PLATFORM_TOKEN, PLATFORM_API_BASE } from "../../constants";
import SkillDependencyGraph, { type SkillDependencyGraphView } from "../../shared/SkillDependencyGraph";

type PlatformSkillImpact = {
  boundAgentCount: number;
  derivedSkillCount: number;
  publishedWorkflowCount: number;
  currentVersionPinnedWorkflowCount: number;
  historicalPinnedWorkflowCount: number;
  sampleAgentIds: string[];
  rolloutHint: string;
  rollbackHint: string;
};

type PlatformSkill = {
  id: number;
  skillCode: string;
  name: string;
  description?: string;
  enabled: boolean;
  riskLevel: string;
  sourceType: string;
  visibility: string;
  bindingPolicy: string;
  updatePolicy: string;
  templateCode?: string;
  currentTemplateVersionNo?: number;
  derivedSkillCount: number;
  agentBindingCount: number;
  versionCount: number;
  latestDraftVersionNo?: number | null;
  updatedAt: string;
  impact?: PlatformSkillImpact;
};

type PlatformSkillVersionImpact = {
  pinnedWorkflowCount: number;
  pinnedAgentCount: number;
  sampleAgentIds: string[];
  summaryLines: string[];
  rolloutStage: string;
  rollbackReady: boolean;
};

type PlatformSkillVersion = {
  id: number;
  versionNo: number;
  name: string;
  description?: string;
  promptFragment?: string;
  toolWhitelist: string[];
  kbWhitelist: string[];
  handoffRule?: string;
  outputContract?: string;
  riskLevel: string;
  publishStatus: string;
  changelog?: string;
  createdBy: string;
  createdAt: string;
  publishedAt?: string;
  impact?: PlatformSkillVersionImpact;
};

type PolicyBundleSummary = {
  bundleCode: string;
  versionNo: number;
  name: string;
  description?: string;
  publishStatus: string;
  sourceSkillCodes: string[];
  handoffRules: string[];
  livePublishedAgentCount: number;
  promptLineCount: number;
  versionCount: number;
  latestDraftVersionNo?: number | null;
  sampleAgentIds: string[];
  rolloutHint: string;
  rollbackHint: string;
  updatedAt: string;
};

type PolicyBundleVersionImpact = {
  livePublishedAgentCount: number;
  sampleAgentIds: string[];
  summaryLines: string[];
  rolloutStage: string;
  rollbackReady: boolean;
};

type PolicyBundleVersion = {
  id: number;
  versionNo: number;
  name: string;
  description?: string;
  promptFragment?: string;
  handoffRules: string[];
  sourceSkillCodes: string[];
  publishStatus: string;
  createdBy: string;
  createdAt: string;
  publishedAt?: string;
  impact?: PolicyBundleVersionImpact;
};

type DraftForm = {
  name: string;
  description: string;
  promptFragment: string;
  toolWhitelist: string;
  kbWhitelist: string;
  handoffRule: string;
  outputContract: string;
  riskLevel: string;
  changelog: string;
};

type GovernanceForm = {
  enabled: boolean;
  visibility: string;
  bindingPolicy: string;
};

type PolicyBundleDraftForm = {
  name: string;
  description: string;
  promptFragment: string;
  handoffRules: string;
  sourceSkillCodes: string;
};

export function buildPlatformSkillDependencyGraphUrl(skillId: number): string {
  return `${PLATFORM_API_BASE}/skills/${encodeURIComponent(String(skillId))}/dependency-graph`;
}

export function isLatestPlatformSkillGraphRequest(requestId: number, latestRequestId: number): boolean {
  return requestId === latestRequestId;
}

export function isCurrentPlatformSkillRequest(
  requestId: number,
  latestRequestId: number,
  requestedSkillId: number,
  selectedSkillId: number | null,
  requestEpoch: number,
  currentEpoch: number,
): boolean {
  return requestId === latestRequestId
    && requestedSkillId === selectedSkillId
    && requestEpoch === currentEpoch;
}

export function resolvePlatformSkillRefreshTarget(
  operationSkillId: number,
  currentSelectedSkillId: number | null,
): number {
  return currentSelectedSkillId ?? operationSkillId;
}

export function canStartPlatformSkillWriteOperation(
  selectedSkillId: number | null,
  loadedSkillId: number | null,
  selectionLoading: boolean,
): boolean {
  return selectedSkillId != null && selectedSkillId === loadedSkillId && !selectionLoading;
}

export function preparePlatformSkillGraphForDisplay(graph: SkillDependencyGraphView): SkillDependencyGraphView {
  if (graph.summary.agentCount > 0 || graph.summary.workflowVersionCount > 0) return graph;
  return { ...graph, nodes: [], edges: [] };
}

function readToken(): string {
  const raw = localStorage.getItem(LS_PLATFORM_TOKEN);
  if (!raw) return "";
  try {
    return (JSON.parse(raw) as { token?: string }).token ?? "";
  } catch {
    return "";
  }
}

function csvToArray(raw: string): string[] {
  return raw
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

function arrayToCsv(items?: string[]): string {
  return (items ?? []).join(", ");
}

function formatTs(ts?: string): string {
  if (!ts) return "—";
  const d = new Date(ts);
  if (Number.isNaN(d.getTime())) return ts;
  return d.toLocaleString();
}

function riskLabel(level: string): string {
  switch (level) {
    case "LOW":
      return "低风险";
    case "MEDIUM":
      return "中风险";
    case "HIGH":
      return "高风险";
    default:
      return level || "未知";
  }
}

function publishStatusLabel(status: string): string {
  switch (status) {
    case "PUBLISHED":
      return "已发布";
    case "DRAFT":
      return "草稿";
    case "ARCHIVED":
      return "已归档";
    case "SUPERSEDED":
      return "已替换";
    default:
      return status || "未知";
  }
}

function visibilityLabel(value: string): string {
  switch (value) {
    case "VISIBLE":
      return "可见";
    case "HIDDEN":
      return "隐藏";
    default:
      return value || "未知";
  }
}

function bindingPolicyLabel(value: string): string {
  switch (value) {
    case "OPTIONAL":
      return "按需绑定";
    case "DEFAULT_ON":
      return "默认启用";
    case "MANDATORY":
      return "强制启用";
    default:
      return value || "未知";
  }
}

function rolloutStageLabel(value?: string): string {
  switch (value) {
    case "LIVE":
      return "线上生效";
    case "READY":
      return "可发布";
    case "ROLLBACK":
      return "可回滚";
    case "DRAFT":
      return "草稿";
    case "DRAFT_PENDING":
      return "待发布";
    case "CURRENT_PUBLISHED":
      return "当前生效";
    case "CURRENT_DEFAULT":
      return "当前生效";
    case "ROLLBACK_TARGET":
      return "可回滚";
    default:
      return value ? value : "待确认";
  }
}

function isInternalNote(text?: string | null): boolean {
  if (!text) return true;
  return /(seed from builtin|manual regression|policy bundle|debug trace|runtime|snapshot|workflow|agent|rollback target|draft pending|current published|prompt fragment)/i.test(
    text,
  );
}

function displayVersionNote(text?: string | null, fallback = "已记录本版变更。"): string {
  if (!text || isInternalNote(text)) return fallback;
  return text;
}

function versionToDraft(version: PlatformSkillVersion): DraftForm {
  return {
    name: version.name ?? "",
    description: version.description ?? "",
    promptFragment: version.promptFragment ?? "",
    toolWhitelist: arrayToCsv(version.toolWhitelist),
    kbWhitelist: arrayToCsv(version.kbWhitelist),
    handoffRule: version.handoffRule ?? "",
    outputContract: version.outputContract ?? "",
    riskLevel: version.riskLevel ?? "MEDIUM",
    changelog: "",
  };
}

function emptyDraftForm(): DraftForm {
  return {
    name: "",
    description: "",
    promptFragment: "",
    toolWhitelist: "",
    kbWhitelist: "",
    handoffRule: "",
    outputContract: "",
    riskLevel: "MEDIUM",
    changelog: "",
  };
}

function policyVersionToDraft(version: PolicyBundleVersion): PolicyBundleDraftForm {
  return {
    name: version.name ?? "",
    description: version.description ?? "",
    promptFragment: version.promptFragment ?? "",
    handoffRules: (version.handoffRules ?? []).join("\n"),
    sourceSkillCodes: arrayToCsv(version.sourceSkillCodes),
  };
}

export default function PlatformSkillsPage() {
  const token = readToken();
  const location = useLocation();
  const navigate = useNavigate();
  const { skillId } = useParams<{ skillId: string }>();
  const requestedSkillId = skillId && Number.isFinite(Number(skillId)) ? Number(skillId) : null;
  const view = location.pathname.endsWith("/policies")
    ? "policies"
    : location.pathname.endsWith("/dependencies")
      ? "dependencies"
      : requestedSkillId != null
        ? "detail"
        : "directory";
  const [skills, setSkills] = useState<PlatformSkill[]>([]);
  const [policyBundle, setPolicyBundle] = useState<PolicyBundleSummary | null>(null);
  const [policyBundleVersions, setPolicyBundleVersions] = useState<PolicyBundleVersion[]>([]);
  const [selectedSkillId, setSelectedSkillId] = useState<number | null>(null);
  const [versions, setVersions] = useState<PlatformSkillVersion[]>([]);
  const [dependencyGraph, setDependencyGraph] = useState<SkillDependencyGraphView | null>(null);
  const [dependencyGraphLoading, setDependencyGraphLoading] = useState(false);
  const [dependencyGraphError, setDependencyGraphError] = useState("");
  const [draft, setDraft] = useState<DraftForm>(() => emptyDraftForm());
  const [governance, setGovernance] = useState<GovernanceForm>({
    enabled: true,
    visibility: "VISIBLE",
    bindingPolicy: "OPTIONAL",
  });
  const [policyDraft, setPolicyDraft] = useState<PolicyBundleDraftForm>({
    name: "",
    description: "",
    promptFragment: "",
    handoffRules: "",
    sourceSkillCodes: "",
  });
  const [loading, setLoading] = useState(false);
  const [skillSelectionLoading, setSkillSelectionLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const versionRequestIdRef = useRef(0);
  const dependencyGraphRequestIdRef = useRef(0);
  const skillListRequestIdRef = useRef(0);
  const selectedSkillIdRef = useRef<number | null>(null);
  const skillSelectionEpochRef = useRef(0);
  const skillSelectionLoadingRef = useRef(false);

  const selectedSkill = useMemo(
    () => skills.find((item) => item.id === selectedSkillId) ?? null,
    [skills, selectedSkillId],
  );
  const displayedDependencyGraph = useMemo(
    () => dependencyGraph ? preparePlatformSkillGraphForDisplay(dependencyGraph) : null,
    [dependencyGraph],
  );

  async function loadSkills(preferredId?: number | null) {
    if (!token) return;
    const listRequestId = ++skillListRequestIdRef.current;
    const selectionEpoch = ++skillSelectionEpochRef.current;
    setLoading(true);
    setError("");
    try {
      const res = await fetch(`${PLATFORM_API_BASE}/skills`, { headers: { Authorization: `Bearer ${token}` } });
      const json = await res.json();
      if (!res.ok || !json.success) throw new Error(json.message || "加载平台技能失败");
      if (listRequestId !== skillListRequestIdRef.current || selectionEpoch !== skillSelectionEpochRef.current) return;
      const rows = (json.data ?? []) as PlatformSkill[];
      setSkills(rows);
      await loadPolicyBundle();
      if (listRequestId !== skillListRequestIdRef.current || selectionEpoch !== skillSelectionEpochRef.current) return;
      const nextId = preferredId ?? requestedSkillId ?? selectedSkillIdRef.current ?? rows[0]?.id ?? null;
      selectedSkillIdRef.current = nextId;
      setSelectedSkillId(nextId);
      if (nextId != null) {
        skillSelectionLoadingRef.current = true;
        setSkillSelectionLoading(true);
        setVersions([]);
        setDraft(emptyDraftForm());
        await Promise.all([
          loadVersions(nextId, rows, selectionEpoch),
          loadSkillDependencyGraph(nextId, selectionEpoch),
        ]);
      } else {
        skillSelectionLoadingRef.current = false;
        setSkillSelectionLoading(false);
        versionRequestIdRef.current += 1;
        dependencyGraphRequestIdRef.current += 1;
        setVersions([]);
        setDependencyGraph(null);
        setDependencyGraphError("");
      }
    } catch (err) {
      if (listRequestId === skillListRequestIdRef.current) {
        skillSelectionLoadingRef.current = false;
        setSkillSelectionLoading(false);
        setError(err instanceof Error ? err.message : "加载平台技能失败");
      }
    } finally {
      if (listRequestId === skillListRequestIdRef.current) setLoading(false);
    }
  }

  async function loadPolicyBundle() {
    if (!token) return;
    const [summaryRes, versionsRes] = await Promise.all([
      fetch(`${PLATFORM_API_BASE}/policies/core`, {
        headers: { Authorization: `Bearer ${token}` },
      }),
      fetch(`${PLATFORM_API_BASE}/policies/core/versions`, {
        headers: { Authorization: `Bearer ${token}` },
      }),
    ]);
    const summaryJson = await summaryRes.json();
    if (!summaryRes.ok || !summaryJson.success) {
      throw new Error(summaryJson.message || "加载核心策略包失败");
    }
    const versionsJson = await versionsRes.json();
    if (!versionsRes.ok || !versionsJson.success) {
      throw new Error(versionsJson.message || "加载核心策略包版本失败");
    }
    const summary = (summaryJson.data ?? null) as PolicyBundleSummary | null;
    const rows = (versionsJson.data ?? []) as PolicyBundleVersion[];
    setPolicyBundle(summary);
    setPolicyBundleVersions(rows);
    const currentVersion =
      rows.find((item) => item.versionNo === summary?.versionNo) ??
      rows.find((item) => item.publishStatus === "PUBLISHED") ??
      rows[0];
    if (currentVersion) {
      setPolicyDraft(policyVersionToDraft(currentVersion));
    }
  }

  async function loadVersions(
    skillId: number,
    skillRows?: PlatformSkill[],
    selectionEpoch = skillSelectionEpochRef.current,
  ) {
    if (!token) return;
    const requestId = ++versionRequestIdRef.current;
    setError("");
    try {
      const res = await fetch(`${PLATFORM_API_BASE}/skills/${skillId}/versions`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      const json = await res.json();
      if (!res.ok || !json.success) throw new Error(json.message || "加载版本失败");
      if (!isCurrentPlatformSkillRequest(
        requestId,
        versionRequestIdRef.current,
        skillId,
        selectedSkillIdRef.current,
        selectionEpoch,
        skillSelectionEpochRef.current,
      )) return;
      const rows = (json.data ?? []) as PlatformSkillVersion[];
      setVersions(rows);
      const currentSkill = (skillRows ?? skills).find((item) => item.id === skillId) ?? null;
      const currentVersion =
        rows.find((item) => item.versionNo === currentSkill?.currentTemplateVersionNo) ??
        rows.find((item) => item.publishStatus === "PUBLISHED") ??
        rows[0];
      if (currentVersion) {
        setDraft(versionToDraft(currentVersion));
      }
      if (currentSkill) {
        setGovernance({
          enabled: currentSkill.enabled,
          visibility: currentSkill.visibility,
          bindingPolicy: currentSkill.bindingPolicy,
        });
      }
    } catch (err) {
      if (isCurrentPlatformSkillRequest(
        requestId,
        versionRequestIdRef.current,
        skillId,
        selectedSkillIdRef.current,
        selectionEpoch,
        skillSelectionEpochRef.current,
      )) {
        setError(err instanceof Error ? err.message : "加载版本失败");
      }
    } finally {
      if (isCurrentPlatformSkillRequest(
        requestId,
        versionRequestIdRef.current,
        skillId,
        selectedSkillIdRef.current,
        selectionEpoch,
        skillSelectionEpochRef.current,
      )) {
        skillSelectionLoadingRef.current = false;
        setSkillSelectionLoading(false);
      }
    }
  }

  async function loadSkillDependencyGraph(
    skillId: number,
    selectionEpoch = skillSelectionEpochRef.current,
  ) {
    if (!token) return;
    const requestId = ++dependencyGraphRequestIdRef.current;
    setDependencyGraph(null);
    setDependencyGraphLoading(true);
    setDependencyGraphError("");
    try {
      const res = await fetch(buildPlatformSkillDependencyGraphUrl(skillId), {
        headers: { Authorization: `Bearer ${token}` },
      });
      const json = await res.json();
      if (!res.ok || !json.success || !json.data) {
        throw new Error(json.message || "加载 Skill 依赖失败");
      }
      if (!isCurrentPlatformSkillRequest(
        requestId,
        dependencyGraphRequestIdRef.current,
        skillId,
        selectedSkillIdRef.current,
        selectionEpoch,
        skillSelectionEpochRef.current,
      )) return;
      setDependencyGraph(json.data as SkillDependencyGraphView);
    } catch (err) {
      if (!isCurrentPlatformSkillRequest(
        requestId,
        dependencyGraphRequestIdRef.current,
        skillId,
        selectedSkillIdRef.current,
        selectionEpoch,
        skillSelectionEpochRef.current,
      )) return;
      setDependencyGraph(null);
      setDependencyGraphError(err instanceof Error ? err.message : "加载 Skill 依赖失败");
    } finally {
      if (isCurrentPlatformSkillRequest(
        requestId,
        dependencyGraphRequestIdRef.current,
        skillId,
        selectedSkillIdRef.current,
        selectionEpoch,
        skillSelectionEpochRef.current,
      )) {
        setDependencyGraphLoading(false);
      }
    }
  }

  function selectSkill(skillId: number) {
    if (skillId === selectedSkillIdRef.current) return;
    const selectionEpoch = ++skillSelectionEpochRef.current;
    skillSelectionLoadingRef.current = true;
    selectedSkillIdRef.current = skillId;
    setSkillSelectionLoading(true);
    setSelectedSkillId(skillId);
    setVersions([]);
    setDraft(emptyDraftForm());
    void Promise.all([
      loadVersions(skillId, undefined, selectionEpoch),
      loadSkillDependencyGraph(skillId, selectionEpoch),
    ]);
  }

  async function savePolicyDraft() {
    setSaving(true);
    setError("");
    setMessage("");
    try {
      const res = await fetch(`${PLATFORM_API_BASE}/policies/core/versions`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          name: policyDraft.name,
          description: policyDraft.description,
          promptFragment: policyDraft.promptFragment,
          handoffRules: policyDraft.handoffRules
            .split("\n")
            .map((item) => item.trim())
            .filter(Boolean),
          sourceSkillCodes: csvToArray(policyDraft.sourceSkillCodes),
        }),
      });
      const json = await res.json();
      if (!res.ok || !json.success) throw new Error(json.message || "保存策略草稿失败");
      setMessage("核心策略草稿版本已创建。");
      await loadSkills(selectedSkillIdRef.current);
    } catch (err) {
      setError(err instanceof Error ? err.message : "保存策略草稿失败");
    } finally {
      setSaving(false);
    }
  }

  async function applyPolicyVersion(versionNo: number, action: "publish" | "rollback") {
    setSaving(true);
    setError("");
    setMessage("");
    try {
      const res = await fetch(`${PLATFORM_API_BASE}/policies/core/${action}`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ versionNo }),
      });
      const json = await res.json();
      if (!res.ok || !json.success) throw new Error(json.message || "应用策略版本失败");
      setMessage(action === "publish" ? `核心策略已发布 v${versionNo}` : `核心策略已回滚到 v${versionNo}`);
      await loadSkills(selectedSkillIdRef.current);
    } catch (err) {
      setError(err instanceof Error ? err.message : "应用策略版本失败");
    } finally {
      setSaving(false);
    }
  }

  useEffect(() => {
    void loadSkills();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, requestedSkillId]);

  async function saveDraft() {
    if (!selectedSkill || !canStartPlatformSkillWriteOperation(
      selectedSkill.id,
      selectedSkillIdRef.current,
      skillSelectionLoadingRef.current,
    )) {
      setError("正在加载选中的 Skill，请稍候再保存。");
      return;
    }
    const operationSkillId = selectedSkill.id;
    const operationDraft = { ...draft };
    setSaving(true);
    setError("");
    setMessage("");
    try {
      const res = await fetch(`${PLATFORM_API_BASE}/skills/${operationSkillId}/versions`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          name: operationDraft.name,
          description: operationDraft.description,
          promptFragment: operationDraft.promptFragment,
          toolWhitelist: csvToArray(operationDraft.toolWhitelist),
          kbWhitelist: csvToArray(operationDraft.kbWhitelist),
          handoffRule: operationDraft.handoffRule,
          outputContract: operationDraft.outputContract,
          riskLevel: operationDraft.riskLevel,
          changelog: operationDraft.changelog,
        }),
      });
      const json = await res.json();
      if (!res.ok || !json.success) throw new Error(json.message || "保存草稿失败");
      if (selectedSkillIdRef.current === operationSkillId) {
        setMessage("草稿版本已创建。");
      }
      await loadSkills(resolvePlatformSkillRefreshTarget(operationSkillId, selectedSkillIdRef.current));
    } catch (err) {
      if (selectedSkillIdRef.current === operationSkillId) {
        setError(err instanceof Error ? err.message : "保存草稿失败");
      }
    } finally {
      setSaving(false);
    }
  }

  async function applyVersion(versionNo: number, action: "publish" | "rollback") {
    if (!selectedSkill || !canStartPlatformSkillWriteOperation(
      selectedSkill.id,
      selectedSkillIdRef.current,
      skillSelectionLoadingRef.current,
    )) {
      setError("正在加载选中的 Skill，请稍候再应用版本。");
      return;
    }
    const operationSkillId = selectedSkill.id;
    const operationGovernance = { ...governance };
    setSaving(true);
    setError("");
    setMessage("");
    try {
      const res = await fetch(`${PLATFORM_API_BASE}/skills/${operationSkillId}/${action}`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          versionNo,
          enabled: operationGovernance.enabled,
          visibility: operationGovernance.visibility,
          bindingPolicy: operationGovernance.bindingPolicy,
        }),
      });
      const json = await res.json();
      if (!res.ok || !json.success) throw new Error(json.message || "应用版本失败");
      if (selectedSkillIdRef.current === operationSkillId) {
        setMessage(action === "publish" ? `已发布 v${versionNo}` : `已回滚到 v${versionNo}`);
      }
      await loadSkills(resolvePlatformSkillRefreshTarget(operationSkillId, selectedSkillIdRef.current));
    } catch (err) {
      if (selectedSkillIdRef.current === operationSkillId) {
        setError(err instanceof Error ? err.message : "应用版本失败");
      }
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="admin-page skills-catalog platform-page platform-skills-page">
      <header className="skills-catalog__header platform-page-head">
        <div className="platform-page-head__main">
          {view === "detail" ? <button type="button" className="platform-page__back" onClick={() => navigate("/platform/skills")}><ArrowLeft size={15} />返回技能目录</button> : null}
          <h1 className="skills-catalog__title">平台标准技能</h1>
          <p className="subtle skills-catalog__subtitle">{view === "directory" ? "浏览平台标准技能；进入独立设置页维护模板与发布版本。" : view === "policies" ? "维护跨技能核心策略及其独立版本历史。" : view === "dependencies" ? "查看当前标准技能对 Agent 与工作流的依赖影响。" : "维护当前标准技能的治理配置、模板版本与发布回滚。"}</p>
        </div>
        <div className="platform-page-head__aside">
          <span className="platform-inline-stat">技能 {skills.length}</span>
          <span className="platform-inline-stat">版本 {versions.length}</span>
          <span className="platform-inline-stat">策略包 {policyBundle ? `v${policyBundle.versionNo}` : "—"}</span>
        </div>
      </header>

      {error ? <div className="platform-console__banner platform-console__banner--error">{error}</div> : null}
      {message ? <div className="platform-console__banner platform-console__banner--success">{message}</div> : null}

      <div className="platform-console__grid">
        {policyBundle && view === "policies" ? (
          <section className="platform-console__panel platform-console__panel--full">
            <div className="platform-console__stack">
              <div className="platform-console__section">
                <p className="platform-section-label">核心策略</p>
                <h3 className="platform-console__subheading">当前核心策略</h3>
                <p className="skills-data-table__summary">
                  当前生效版本 v{policyBundle.versionNo} · {publishStatusLabel(policyBundle.publishStatus)} · 共 {policyBundle.versionCount} 个版本 · 最近更新时间 {formatTs(policyBundle.updatedAt)}
                </p>
                <div className="platform-console__stats">
                  <article className="platform-console__stat">
                    <span>覆盖已发布智能体</span>
                    <strong>{policyBundle.livePublishedAgentCount}</strong>
                  </article>
                  <article className="platform-console__stat">
                    <span>策略提示行数</span>
                    <strong>{policyBundle.promptLineCount}</strong>
                  </article>
                  <article className="platform-console__stat">
                    <span>兜底规则数</span>
                    <strong>{policyBundle.handoffRules.length}</strong>
                  </article>
                  <article className="platform-console__stat">
                    <span>待发布草稿</span>
                    <strong>{policyBundle.latestDraftVersionNo ?? "—"}</strong>
                  </article>
                </div>
                <ul className="platform-console__summary-list">
                  <li>发布前先确认影响范围，并完成小范围验证。</li>
                  <li>回滚前先核对当前生效版本与待回退版本，避免误操作。</li>
                </ul>
              </div>

              <div className="platform-console__section">
                <p className="platform-section-label">版本编辑器</p>
                <h3 className="platform-console__subheading">新建策略版本</h3>
                <div className="platform-console__form-grid">
                  <label>
                    名称
                    <input
                      value={policyDraft.name}
                      onChange={(e) => setPolicyDraft((prev) => ({ ...prev, name: e.target.value }))}
                    />
                  </label>
                  <label>
                    来源技能范围
                    <input
                      value={policyDraft.sourceSkillCodes}
                      onChange={(e) => setPolicyDraft((prev) => ({ ...prev, sourceSkillCodes: e.target.value }))}
                      placeholder="conversation-core, knowledge-first, safe-handoff"
                    />
                  </label>
                  <label className="platform-console__field--full">
                    描述
                    <textarea
                      rows={2}
                      value={policyDraft.description}
                      onChange={(e) => setPolicyDraft((prev) => ({ ...prev, description: e.target.value }))}
                    />
                  </label>
                  <label className="platform-console__field--full">
                    策略正文片段
                    <textarea
                      rows={6}
                      value={policyDraft.promptFragment}
                      onChange={(e) => setPolicyDraft((prev) => ({ ...prev, promptFragment: e.target.value }))}
                    />
                  </label>
                  <label className="platform-console__field--full">
                    兜底移交规则
                    <textarea
                      rows={4}
                      value={policyDraft.handoffRules}
                      onChange={(e) => setPolicyDraft((prev) => ({ ...prev, handoffRules: e.target.value }))}
                      placeholder={"每行一条兜底规则"}
                    />
                  </label>
                </div>
                <div className="platform-console__actions">
                  <button className="platform-button platform-button--primary" disabled={saving} onClick={() => void savePolicyDraft()}>
                    {saving ? "处理中…" : "保存为新策略草稿"}
                  </button>
                </div>
              </div>

              <div className="platform-console__section">
                <p className="platform-section-label">历史记录</p>
                <h3 className="platform-console__subheading">策略版本历史</h3>
                <div className="skills-table-wrap">
                  <table className="skills-data-table">
                    <thead>
                      <tr>
                        <th>版本</th>
                        <th>状态</th>
                        <th>影响</th>
                        <th>时间</th>
                        <th>动作</th>
                      </tr>
                    </thead>
                    <tbody>
                      {policyBundleVersions.map((version) => {
                        const isCurrent = version.versionNo === policyBundle.versionNo;
                        const action = version.versionNo < policyBundle.versionNo ? "rollback" : "publish";
                        return (
                          <tr key={version.id}>
                            <td className="skills-data-table__mono">v{version.versionNo}</td>
                            <td>{publishStatusLabel(version.publishStatus)}</td>
                            <td className="skills-data-table__summary">
                              <div>{displayVersionNote(version.description, "已记录策略版本说明。")}</div>
                            </td>
                            <td className="skills-data-table__mono">{formatTs(version.publishedAt || version.createdAt)}</td>
                            <td className="skills-data-table__actions">
                              <div className="platform-console__badges platform-console__badges--compact">
                                <span className="skills-pill">{rolloutStageLabel(version.impact?.rolloutStage)}</span>
                                <span className="skills-pill">{version.impact?.livePublishedAgentCount ?? 0} 个智能体</span>
                              </div>
                              <button
                                className="platform-button platform-button--secondary"
                                onClick={() => setPolicyDraft(policyVersionToDraft(version))}
                              >
                                装载到编辑器
                              </button>
                              {!isCurrent ? (
                                <button
                                  className="platform-button platform-button--primary"
                                  disabled={saving}
                                  onClick={() => void applyPolicyVersion(version.versionNo, action)}
                                >
                                  {action === "rollback" ? "回滚到此版本" : "发布此版本"}
                                </button>
                              ) : (
                                <span className="skills-pill">当前生效</span>
                              )}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          </section>
        ) : null}

        {view === "directory" ? <section className="platform-console__panel skills-table-wrap">
          <div className="platform-panel__intro">
            <p className="platform-section-label">标准技能列表</p>
            <p className="skills-data-table__summary">选择左侧标准技能后，在右侧查看治理配置、影响摘要和版本历史。</p>
          </div>
          <table className="skills-data-table">
            <thead>
              <tr>
                <th>标准技能</th>
                <th>当前模板</th>
                <th>派生</th>
                <th>绑定智能体</th>
                <th>治理</th>
                <th aria-label="操作" />
              </tr>
            </thead>
            <tbody>
              {skills.map((skill) => (
                <tr
                  key={skill.id}
                  className="platform-console__select-row"
                  onClick={() => navigate(`/platform/skills/${skill.id}`)}
                >
                  <td>
                    <div className="skills-data-table__skill-name">{skill.name}</div>
                    {skill.description ? <div className="skills-data-table__summary">{skill.description}</div> : null}
                  </td>
                  <td className="skills-data-table__mono">
                    v{skill.currentTemplateVersionNo ?? 1}
                    <br />
                    <span className="subtle">{skill.versionCount} 个版本</span>
                  </td>
                  <td>{skill.derivedSkillCount}</td>
                  <td>{skill.agentBindingCount}</td>
                  <td>
                    <div className="skills-data-table__flags">
                      <span className="skills-pill">{visibilityLabel(skill.visibility)}</span>
                      <span className="skills-pill">{bindingPolicyLabel(skill.bindingPolicy)}</span>
                      <span className="skills-pill">{skill.enabled ? "已启用" : "已停用"}</span>
                    </div>
                  </td>
                  <td><button type="button" className="platform-table-link" onClick={(event) => { event.stopPropagation(); navigate(`/platform/skills/${skill.id}`); }}>设置 <ExternalLink size={13} /></button></td>
                </tr>
              ))}
              {!loading && skills.length === 0 ? (
                <tr>
                  <td colSpan={6} className="skills-data-table__summary">
                    当前还没有平台标准技能。
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </section> : null}

        {view === "detail" || view === "dependencies" ? <section className="platform-console__panel platform-console__panel--detail">
          {selectedSkill ? (
            <div className="platform-console__stack">
              {view === "dependencies" ? <div className="platform-console__section platform-skill-dependency__selector">
                <p className="platform-section-label">标准技能</p>
                <label>
                  查看依赖影响
                  <select value={selectedSkill.id} onChange={(event) => selectSkill(Number(event.target.value))}>
                    {skills.map((skill) => <option key={skill.id} value={skill.id}>{skill.name}</option>)}
                  </select>
                </label>
              </div> : null}
              {view === "detail" ? <>
              <div className="platform-console__section">
                <p className="platform-section-label">当前技能</p>
                <h2 className="platform-console__heading">{selectedSkill.name}</h2>
                <p className="skills-data-table__summary">
                  当前版本 v{selectedSkill.currentTemplateVersionNo ?? 1} · 最近更新时间 {formatTs(selectedSkill.updatedAt)}
                </p>
                <div className="platform-console__badges">
                  <span className="skills-pill">{visibilityLabel(selectedSkill.visibility)}</span>
                  <span className="skills-pill">{bindingPolicyLabel(selectedSkill.bindingPolicy)}</span>
                  <span className="skills-pill">{selectedSkill.enabled ? "已启用" : "已停用"}</span>
                </div>
              </div>

              <div className="platform-console__section">
                <p className="platform-section-label">治理配置</p>
                <h3 className="platform-console__subheading">治理设置</h3>
                <div className="platform-console__form-grid">
                  <label>
                    可见性
                    <select
                      value={governance.visibility}
                      onChange={(e) => setGovernance((prev) => ({ ...prev, visibility: e.target.value }))}
                    >
                      <option value="VISIBLE">可见</option>
                      <option value="HIDDEN">隐藏</option>
                    </select>
                  </label>
                  <label>
                    绑定策略
                    <select
                      value={governance.bindingPolicy}
                      onChange={(e) => setGovernance((prev) => ({ ...prev, bindingPolicy: e.target.value }))}
                    >
                      <option value="OPTIONAL">按需绑定</option>
                      <option value="DEFAULT_ON">默认启用</option>
                      <option value="MANDATORY">强制启用</option>
                    </select>
                  </label>
                  <label className="platform-console__checkbox">
                    <input
                      type="checkbox"
                      checked={governance.enabled}
                      onChange={(e) => setGovernance((prev) => ({ ...prev, enabled: e.target.checked }))}
                    />
                    对租户启用
                  </label>
                </div>
              </div>
              </> : null}

              {selectedSkill.impact && (view === "detail" || view === "dependencies") ? (
                <div className="platform-console__section">
                  <p className="platform-section-label">影响范围</p>
                  <h3 className="platform-console__subheading">影响摘要</h3>
                  <div className="platform-console__stats">
                    <article className="platform-console__stat">
                      <span>绑定智能体</span>
                      <strong>{selectedSkill.impact.boundAgentCount}</strong>
                    </article>
                    <article className="platform-console__stat">
                      <span>已发布工作流</span>
                      <strong>{selectedSkill.impact.publishedWorkflowCount}</strong>
                    </article>
                    <article className="platform-console__stat">
                      <span>当前版本命中</span>
                      <strong>{selectedSkill.impact.currentVersionPinnedWorkflowCount}</strong>
                    </article>
                    <article className="platform-console__stat">
                      <span>历史版本缓冲</span>
                      <strong>{selectedSkill.impact.historicalPinnedWorkflowCount}</strong>
                    </article>
                  </div>
                  <ul className="platform-console__summary-list">
                    <li>发布前先确认影响范围，再安排小范围验证。</li>
                    <li>回滚时优先核对当前线上版本与目标版本，避免影响已发布配置。</li>
                  </ul>
                </div>
              ) : null}

              <div className="platform-console__section platform-skill-dependency">
                <p className="platform-section-label">依赖治理</p>
                <div className="platform-skill-dependency__heading">
                  <h3 className="platform-console__subheading">依赖关系</h3>
                  <span>
                    {dependencyGraph?.summary.workflowVersionCount ?? 0} 个工作流版本 · {dependencyGraph?.summary.agentCount ?? 0} 个 Agent
                  </span>
                </div>
                <SkillDependencyGraph
                  graph={displayedDependencyGraph}
                  loading={dependencyGraphLoading}
                  error={dependencyGraphError}
                  emptyMessage="当前技能尚未被 Agent 或工作流版本引用。"
                  ariaLabel={`${selectedSkill.name} 依赖影响图`}
                  onRetry={() => void loadSkillDependencyGraph(selectedSkill.id)}
                />
              </div>

              {view === "detail" ? <div className="platform-console__section">
                <p className="platform-section-label">模板编辑器</p>
                <h3 className="platform-console__subheading">新建模板版本</h3>
                <div className="platform-console__form-grid">
                  <label>
                    名称
                    <input value={draft.name} onChange={(e) => setDraft((prev) => ({ ...prev, name: e.target.value }))} />
                  </label>
                  <label>
                    风险等级
                    <select
                      value={draft.riskLevel}
                      onChange={(e) => setDraft((prev) => ({ ...prev, riskLevel: e.target.value }))}
                    >
                      <option value="LOW">低风险</option>
                      <option value="MEDIUM">中风险</option>
                      <option value="HIGH">高风险</option>
                    </select>
                  </label>
                  <label className="platform-console__field--full">
                    描述
                    <textarea
                      rows={2}
                      value={draft.description}
                      onChange={(e) => setDraft((prev) => ({ ...prev, description: e.target.value }))}
                    />
                  </label>
                  <label className="platform-console__field--full">
                    模板正文片段
                    <textarea
                      rows={8}
                      value={draft.promptFragment}
                      onChange={(e) => setDraft((prev) => ({ ...prev, promptFragment: e.target.value }))}
                    />
                  </label>
                  <label>
                    可调用工具
                    <input
                      value={draft.toolWhitelist}
                      onChange={(e) => setDraft((prev) => ({ ...prev, toolWhitelist: e.target.value }))}
                      placeholder="a, b, c"
                    />
                  </label>
                  <label>
                    可引用知识库
                    <input
                      value={draft.kbWhitelist}
                      onChange={(e) => setDraft((prev) => ({ ...prev, kbWhitelist: e.target.value }))}
                      placeholder="kb-1, kb-2"
                    />
                  </label>
                  <label className="platform-console__field--full">
                    兜底移交规则
                    <textarea
                      rows={2}
                      value={draft.handoffRule}
                      onChange={(e) => setDraft((prev) => ({ ...prev, handoffRule: e.target.value }))}
                    />
                  </label>
                  <label className="platform-console__field--full">
                    输出约束
                    <textarea
                      rows={2}
                      value={draft.outputContract}
                      onChange={(e) => setDraft((prev) => ({ ...prev, outputContract: e.target.value }))}
                    />
                  </label>
                  <label className="platform-console__field--full">
                    本版说明
                    <textarea
                      rows={2}
                      value={draft.changelog}
                      onChange={(e) => setDraft((prev) => ({ ...prev, changelog: e.target.value }))}
                    />
                  </label>
                </div>
                <div className="platform-console__actions">
                  <button className="platform-button platform-button--primary" disabled={saving || skillSelectionLoading} onClick={() => void saveDraft()}>
                    {saving ? "处理中…" : "保存为新草稿版本"}
                  </button>
                </div>
              </div> : null}

              {view === "detail" ? <div className="platform-console__section">
                <p className="platform-section-label">历史记录</p>
                <h3 className="platform-console__subheading">版本历史</h3>
                <div className="skills-table-wrap">
                  <table className="skills-data-table">
                    <thead>
                      <tr>
                        <th>版本</th>
                        <th>状态</th>
                        <th>变更</th>
                        <th>时间</th>
                        <th>动作</th>
                      </tr>
                    </thead>
                    <tbody>
                      {versions.map((version) => {
                        const isCurrent = version.versionNo === selectedSkill.currentTemplateVersionNo;
                        const action = version.versionNo < (selectedSkill.currentTemplateVersionNo ?? 0) ? "rollback" : "publish";
                        return (
                          <tr key={version.id}>
                            <td className="skills-data-table__mono">v{version.versionNo}</td>
                            <td>{publishStatusLabel(version.publishStatus)}</td>
                            <td className="skills-data-table__summary">
                              <div>{displayVersionNote(version.changelog || version.description)}</div>
                            </td>
                            <td className="skills-data-table__mono">{formatTs(version.publishedAt || version.createdAt)}</td>
                            <td className="skills-data-table__actions">
                              <div className="platform-console__badges platform-console__badges--compact">
                                <span className="skills-pill">{rolloutStageLabel(version.impact?.rolloutStage)}</span>
                                <span className="skills-pill">{version.impact?.pinnedWorkflowCount ?? 0} 个工作流</span>
                              </div>
                              <button
                                className="platform-button platform-button--secondary"
                                onClick={() => setDraft(versionToDraft(version))}
                              >
                                装载到编辑器
                              </button>
                              {!isCurrent ? (
                                <button
                                  className="platform-button platform-button--primary"
                                  disabled={saving || skillSelectionLoading}
                                  onClick={() => void applyVersion(version.versionNo, action)}
                                >
                                  {action === "rollback" ? "回滚到此版本" : "发布此版本"}
                                </button>
                              ) : (
                                <span className="skills-pill">当前生效</span>
                              )}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div> : null}
            </div>
          ) : (
            <p className="skills-data-table__summary">请选择一个平台标准技能。</p>
          )}
        </section> : null}
      </div>
    </div>
  );
}
