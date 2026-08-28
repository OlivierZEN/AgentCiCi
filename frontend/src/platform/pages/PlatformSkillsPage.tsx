import { useEffect, useMemo, useRef, useState } from "react";
import type { ReactNode } from "react";
import {
  AlertTriangle,
  ArrowLeft,
  CheckCircle2,
  ChevronRight,
  Eye,
  MoreHorizontal,
  Search,
  X,
} from "lucide-react";
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

type SkillDrawerTab = "overview" | "versions" | "dependencies";
type SkillEditorSection = "governance" | "template" | "boundary" | "notes";

const PLANNED_POLICY_PACKAGES = [
  {
    code: "data-egress",
    name: "数据出境策略",
    description: "约束敏感数据向外部模型、工具与连接器传递。",
    scope: "数据使用与出境",
    targets: "平台运行时",
  },
  {
    code: "model-access",
    name: "模型调用策略",
    description: "治理模型准入、场景选择、回退与调用边界。",
    scope: "模型调用与回退",
    targets: "模型运行场景",
  },
  {
    code: "tool-execution",
    name: "工具执行策略",
    description: "治理工具授权、高风险动作确认与执行审计。",
    scope: "工具授权与执行",
    targets: "平台工具目录",
  },
] as const;

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
  const isPolicyEditor = location.pathname.endsWith("/policy/edit");
  const isSkillEditor = requestedSkillId != null && location.pathname.endsWith("/edit");
  const isSkillPreview = requestedSkillId != null && location.pathname.endsWith("/preview");
  const isSkillDrawerOpen = requestedSkillId != null && !isSkillEditor && !isSkillPreview;
  const initialHomeView = location.pathname.endsWith("/policies") ? "policies" : "skills";
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
  const [homeView, setHomeView] = useState<"skills" | "policies">(initialHomeView);
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<"ALL" | "ENABLED" | "REVIEW">("ALL");
  const [drawerTab, setDrawerTab] = useState<SkillDrawerTab>(
    location.pathname.endsWith("/dependencies") ? "dependencies" : "overview",
  );
  const [editorSection, setEditorSection] = useState<SkillEditorSection>("governance");
  const [policyEditorTab, setPolicyEditorTab] = useState<"content" | "versions">("content");
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
  const filteredSkills = useMemo(() => {
    const normalizedQuery = query.trim().toLocaleLowerCase();
    return skills.filter((skill) => {
      const matchesQuery = !normalizedQuery
        || `${skill.name} ${skill.description ?? ""} ${skill.skillCode}`.toLocaleLowerCase().includes(normalizedQuery);
      const needsReview = skill.riskLevel === "HIGH" || skill.latestDraftVersionNo != null;
      const matchesStatus = statusFilter === "ALL"
        || (statusFilter === "ENABLED" ? skill.enabled : needsReview);
      return matchesQuery && matchesStatus;
    });
  }, [query, skills, statusFilter]);

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

  useEffect(() => {
    setHomeView(initialHomeView);
  }, [initialHomeView]);

  useEffect(() => {
    if (isSkillDrawerOpen) setDrawerTab("overview");
  }, [isSkillDrawerOpen, requestedSkillId]);

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

  const notices = (
    <>
      {error ? <div className="platform-console__banner platform-console__banner--error">{error}</div> : null}
      {message ? <div className="platform-console__banner platform-console__banner--success">{message}</div> : null}
    </>
  );

  const pageHeader = (
    title: string,
    description: string,
    options?: { backLabel?: string; backTo?: string; actions?: ReactNode },
  ) => (
    <header className="skills-catalog__header platform-page-head skill-governance__header">
      <div className="platform-page-head__main">
        {options?.backTo ? (
          <button type="button" className="platform-page__back" onClick={() => navigate(options.backTo!)}>
            <ArrowLeft size={15} />{options.backLabel ?? "返回技能治理"}
          </button>
        ) : <p className="skill-governance__breadcrumb">运营控制台 / 能力治理 / 技能治理</p>}
        <h1 className="skills-catalog__title">{title}</h1>
        <p className="subtle skills-catalog__subtitle">{description}</p>
      </div>
      {options?.actions ? <div className="skill-governance__header-actions">{options.actions}</div> : null}
    </header>
  );

  const renderImpactSummary = () => (
    <div className="skill-governance__impact-summary">
      <div><span>绑定 Agent</span><strong>{selectedSkill?.impact?.boundAgentCount ?? selectedSkill?.agentBindingCount ?? 0}</strong></div>
      <div><span>已发布工作流</span><strong>{selectedSkill?.impact?.publishedWorkflowCount ?? 0}</strong></div>
      <div><span>当前版本命中</span><strong>{selectedSkill?.impact?.currentVersionPinnedWorkflowCount ?? 0}</strong></div>
      <div><span>历史版本引用</span><strong>{selectedSkill?.impact?.historicalPinnedWorkflowCount ?? 0}</strong></div>
    </div>
  );

  if (isPolicyEditor) {
    return (
      <div className="admin-page platform-page platform-skills-page skill-governance-page skill-governance-page--focused">
        {pageHeader(
          `编辑策略 · ${policyBundle?.name ?? "平台核心安全策略"}`,
          "策略编辑使用独立页面，保存后生成新的草稿版本，不直接覆盖当前生效版本。",
          {
            backTo: "/platform/skills/policies",
            actions: policyEditorTab === "content" ? (
              <button className="platform-button platform-button--primary" disabled={saving} onClick={() => void savePolicyDraft()}>
                {saving ? "处理中…" : "保存为策略草稿"}
              </button>
            ) : undefined,
          },
        )}
        {notices}
        <nav className="skill-governance__text-tabs" aria-label="策略编辑内容">
          <button type="button" className={policyEditorTab === "content" ? "is-active" : ""} onClick={() => setPolicyEditorTab("content")}>策略内容</button>
          <button type="button" className={policyEditorTab === "versions" ? "is-active" : ""} onClick={() => setPolicyEditorTab("versions")}>版本记录</button>
        </nav>
        <div className="skill-governance__focused-content">
          {policyEditorTab === "content" ? (
            <section className="skill-governance__form-section">
              <div className="skill-governance__section-intro">
                <span>核心策略编辑器</span>
                <h2>通用安全与证据规则</h2>
                <p>现有 core-default 策略包保持原有来源技能、提示片段与人工移交逻辑。</p>
              </div>
              <div className="skill-governance__form-grid">
                <label>策略名称<input value={policyDraft.name} onChange={(event) => setPolicyDraft((current) => ({ ...current, name: event.target.value }))} /></label>
                <label>来源技能范围<input value={policyDraft.sourceSkillCodes} onChange={(event) => setPolicyDraft((current) => ({ ...current, sourceSkillCodes: event.target.value }))} /></label>
                <label className="is-full">策略说明<textarea value={policyDraft.description} onChange={(event) => setPolicyDraft((current) => ({ ...current, description: event.target.value }))} /></label>
                <label className="is-full">策略正文片段<textarea className="is-tall" value={policyDraft.promptFragment} onChange={(event) => setPolicyDraft((current) => ({ ...current, promptFragment: event.target.value }))} /></label>
                <label className="is-full">兜底移交规则<textarea value={policyDraft.handoffRules} onChange={(event) => setPolicyDraft((current) => ({ ...current, handoffRules: event.target.value }))} placeholder="每行一条兜底规则" /></label>
              </div>
            </section>
          ) : (
            <section className="skill-governance__simple-section">
              <div className="skill-governance__section-heading">
                <div><h2>策略版本记录</h2><p>审阅不可变版本，并沿用现有发布与回滚动作。</p></div>
                <span>{policyBundleVersions.length} 个版本</span>
              </div>
              <div className="skills-table-wrap skill-governance__table-wrap">
                <table className="skills-data-table skill-governance__table">
                  <thead><tr><th>版本</th><th>状态</th><th>说明</th><th>影响范围</th><th>时间</th><th>操作</th></tr></thead>
                  <tbody>{policyBundleVersions.map((version) => {
                    const isCurrent = version.versionNo === policyBundle?.versionNo;
                    const action = version.versionNo < (policyBundle?.versionNo ?? 0) ? "rollback" : "publish";
                    return <tr key={version.id}>
                      <td><strong>v{version.versionNo}</strong></td>
                      <td><span className={`skill-governance__status ${isCurrent ? "is-success" : ""}`}>{isCurrent ? "当前生效" : publishStatusLabel(version.publishStatus)}</span></td>
                      <td>{displayVersionNote(version.description, "已记录策略版本说明。")}</td>
                      <td>{version.impact?.livePublishedAgentCount ?? 0} 个智能体</td>
                      <td>{formatTs(version.publishedAt || version.createdAt)}</td>
                      <td><div className="skill-governance__row-actions">
                        <button type="button" className="platform-table-link" onClick={() => { setPolicyDraft(policyVersionToDraft(version)); setPolicyEditorTab("content"); }}>装载编辑</button>
                        {!isCurrent ? <button type="button" className="platform-table-link" disabled={saving} onClick={() => void applyPolicyVersion(version.versionNo, action)}>{action === "rollback" ? "回滚到此版本" : "发布此版本"}</button> : null}
                      </div></td>
                    </tr>;
                  })}</tbody>
                </table>
              </div>
            </section>
          )}
        </div>
      </div>
    );
  }

  if (isSkillEditor) {
    const editorItems: Array<[SkillEditorSection, string]> = [["governance", "治理设置"], ["template", "模板内容"], ["boundary", "能力边界"], ["notes", "本版说明"]];
    return (
      <div className="admin-page platform-page platform-skills-page skill-governance-page skill-governance-page--focused">
        {pageHeader(
          `编辑技能 · ${selectedSkill?.name ?? "加载中"}`,
          "在独立编辑页完成草稿；版本和依赖信息保留在技能速览中。",
          {
            backTo: selectedSkill ? `/platform/skills/${selectedSkill.id}` : "/platform/skills",
            actions: <>
              <button type="button" className="platform-button platform-button--secondary" disabled={!selectedSkill} onClick={() => selectedSkill && navigate(`/platform/skills/${selectedSkill.id}/preview`)}><Eye size={15} />预览草稿</button>
              <button type="button" className="platform-button platform-button--primary" disabled={saving || skillSelectionLoading || !selectedSkill} onClick={() => void saveDraft()}>{saving ? "处理中…" : "保存为新草稿版本"}</button>
            </>,
          },
        )}
        {notices}
        <nav className="skill-governance__text-tabs skill-governance__editor-tabs" aria-label="技能编辑步骤">
          {editorItems.map(([key, label], index) => <button type="button" key={key} className={editorSection === key ? "is-active" : ""} onClick={() => setEditorSection(key)}><span>{index + 1}</span>{label}</button>)}
        </nav>
        <div className="skill-governance__focused-content skill-governance__focused-content--editor">
          {editorSection === "governance" ? <section className="skill-governance__form-section">
            <div className="skill-governance__section-intro"><span>01 / 治理设置</span><h2>发布与绑定规则</h2><p>决定技能是否进入租户目录，以及 Agent 如何发现并绑定此能力。</p></div>
            <div className="skill-governance__form-grid">
              <label>可见性<select value={governance.visibility} onChange={(event) => setGovernance((current) => ({ ...current, visibility: event.target.value }))}><option value="VISIBLE">可见</option><option value="HIDDEN">隐藏</option></select></label>
              <label>绑定策略<select value={governance.bindingPolicy} onChange={(event) => setGovernance((current) => ({ ...current, bindingPolicy: event.target.value }))}><option value="OPTIONAL">按需绑定</option><option value="DEFAULT_ON">默认启用</option><option value="MANDATORY">强制启用</option></select></label>
              <label className="is-full skill-governance__switch"><span><strong>对租户启用</strong><small>关闭后，新建 Agent 和运行时均不可使用。</small></span><input type="checkbox" checked={governance.enabled} onChange={(event) => setGovernance((current) => ({ ...current, enabled: event.target.checked }))} /></label>
            </div>
          </section> : null}
          {editorSection === "template" ? <section className="skill-governance__form-section">
            <div className="skill-governance__section-intro"><span>02 / 模板内容</span><h2>技能模板编辑器</h2><p>保存后生成新草稿，不覆盖线上版本。</p></div>
            <div className="skill-governance__form-grid">
              <label>技能名称<input value={draft.name} onChange={(event) => setDraft((current) => ({ ...current, name: event.target.value }))} /></label>
              <label>风险等级<select value={draft.riskLevel} onChange={(event) => setDraft((current) => ({ ...current, riskLevel: event.target.value }))}><option value="LOW">低风险</option><option value="MEDIUM">中风险</option><option value="HIGH">高风险</option></select></label>
              <label className="is-full">能力说明<textarea value={draft.description} onChange={(event) => setDraft((current) => ({ ...current, description: event.target.value }))} /></label>
              <label className="is-full">模板正文片段<textarea className="is-tall" value={draft.promptFragment} onChange={(event) => setDraft((current) => ({ ...current, promptFragment: event.target.value }))} /></label>
            </div>
          </section> : null}
          {editorSection === "boundary" ? <section className="skill-governance__form-section">
            <div className="skill-governance__section-intro"><span>03 / 能力边界</span><h2>工具、知识与输出约束</h2><p>明确技能可以调用什么、引用什么，以及无法可靠完成时如何移交。</p></div>
            <div className="skill-governance__form-grid">
              <label className="is-full">可调用工具<input value={draft.toolWhitelist} onChange={(event) => setDraft((current) => ({ ...current, toolWhitelist: event.target.value }))} placeholder="多个工具使用逗号分隔" /></label>
              <label className="is-full">可引用知识库<input value={draft.kbWhitelist} onChange={(event) => setDraft((current) => ({ ...current, kbWhitelist: event.target.value }))} placeholder="多个知识库使用逗号分隔" /></label>
              <label className="is-full">兜底移交规则<textarea value={draft.handoffRule} onChange={(event) => setDraft((current) => ({ ...current, handoffRule: event.target.value }))} /></label>
              <label className="is-full">输出约束<textarea value={draft.outputContract} onChange={(event) => setDraft((current) => ({ ...current, outputContract: event.target.value }))} /></label>
            </div>
          </section> : null}
          {editorSection === "notes" ? <section className="skill-governance__form-section">
            <div className="skill-governance__section-intro"><span>04 / 本版说明</span><h2>草稿说明与发布提示</h2><p>记录本次修改目的，供版本审阅、发布和后续回滚使用。</p></div>
            <div className="skill-governance__form-grid">
              <label className="is-full">本版说明<textarea className="is-tall" value={draft.changelog} onChange={(event) => setDraft((current) => ({ ...current, changelog: event.target.value }))} /></label>
              <div className="is-full skill-governance__readiness"><CheckCircle2 size={19} /><div><strong>保存后生成独立草稿版本</strong><span>不会直接覆盖当前线上 v{selectedSkill?.currentTemplateVersionNo ?? "—"}。</span></div></div>
            </div>
          </section> : null}
        </div>
      </div>
    );
  }

  if (isSkillPreview) {
    return (
      <div className="admin-page platform-page platform-skills-page skill-governance-page skill-governance-page--focused">
        {pageHeader(
          `${selectedSkill?.name ?? "技能"} · 草稿预览`,
          "预览页只读呈现即将生成的草稿配置和当前影响范围。",
          { backTo: selectedSkill ? `/platform/skills/${selectedSkill.id}/edit` : "/platform/skills", backLabel: "返回技能编辑" },
        )}
        {notices}
        <div className="skill-governance__preview-layout">
          <div>
            <section className="skill-governance__simple-section">
              <div className="skill-governance__section-heading"><div><h2>草稿配置</h2><p>保存后将作为新版本进入版本记录。</p></div><span>{riskLabel(draft.riskLevel)}</span></div>
              <dl className="skill-governance__preview-def">
                <div><dt>技能名称</dt><dd>{draft.name || "—"}</dd></div>
                <div><dt>可见性</dt><dd>{visibilityLabel(governance.visibility)}</dd></div>
                <div><dt>绑定策略</dt><dd>{bindingPolicyLabel(governance.bindingPolicy)}</dd></div>
                <div><dt>对租户启用</dt><dd>{governance.enabled ? "是" : "否"}</dd></div>
                <div><dt>可调用工具</dt><dd>{draft.toolWhitelist || "未配置"}</dd></div>
                <div><dt>可引用知识库</dt><dd>{draft.kbWhitelist || "未配置"}</dd></div>
              </dl>
            </section>
            <section className="skill-governance__simple-section"><h2>模板与输出边界</h2><div className="skill-governance__readonly-copy"><h3>模板正文片段</h3><p>{draft.promptFragment || "未配置"}</p><h3>兜底移交规则</h3><p>{draft.handoffRule || "未配置"}</p><h3>输出约束</h3><p>{draft.outputContract || "未配置"}</p></div></section>
          </div>
          <aside className="skill-governance__preview-checks">
            <h2>发布检查</h2>
            <p className="is-success"><CheckCircle2 size={17} />治理配置已载入</p>
            <p className="is-success"><CheckCircle2 size={17} />当前线上版本保持不变</p>
            <p className="is-warning"><AlertTriangle size={17} />发布前请确认实际影响范围</p>
            <hr />
            <dl><div><dt>受影响 Agent</dt><dd>{selectedSkill?.impact?.boundAgentCount ?? 0}</dd></div><div><dt>受影响工作流</dt><dd>{selectedSkill?.impact?.publishedWorkflowCount ?? 0}</dd></div><div><dt>历史版本引用</dt><dd>{selectedSkill?.impact?.historicalPinnedWorkflowCount ?? 0}</dd></div></dl>
          </aside>
        </div>
      </div>
    );
  }

  return (
    <div className="admin-page platform-page platform-skills-page skill-governance-page">
      {pageHeader("技能治理", "统一维护平台标准技能与核心策略包；先选择治理对象，再进入详情或独立编辑。")}
      {notices}
      <nav className="skill-governance__entry-nav" aria-label="技能治理对象">
        <button type="button" className={homeView === "skills" ? "is-active" : ""} onClick={() => { setHomeView("skills"); navigate("/platform/skills", { replace: true }); }}><strong>技能列表</strong><span>{skills.length} 项标准技能，目录、版本与依赖治理</span></button>
        <button type="button" className={homeView === "policies" ? "is-active" : ""} onClick={() => { setHomeView("policies"); navigate("/platform/skills/policies", { replace: true }); }}><strong>核心策略包</strong><span>1 个生效，{PLANNED_POLICY_PACKAGES.length} 个规划方向</span></button>
      </nav>

      {homeView === "skills" ? (
        <section className="skills-table-wrap skill-governance__list-surface" aria-label="平台标准技能列表">
          <div className="skill-governance__toolbar">
            <label className="skill-governance__search"><Search size={17} /><span className="sr-only">搜索技能</span><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="搜索技能名称或能力说明" /></label>
            <div className="skill-governance__filters" role="tablist" aria-label="技能状态筛选">
              {([["ALL", "全部"], ["ENABLED", "已启用"], ["REVIEW", "待检查"]] as const).map(([value, label]) => <button type="button" role="tab" aria-selected={statusFilter === value} className={statusFilter === value ? "is-active" : ""} onClick={() => setStatusFilter(value)} key={value}>{label}</button>)}
            </div>
            <span className="skill-governance__result-count">共 {filteredSkills.length} 项</span>
          </div>
          <table className="skills-data-table skill-governance__table">
            <thead><tr><th>标准技能</th><th>当前版本</th><th>状态</th><th>风险</th><th>绑定 Agent</th><th>最近更新</th><th aria-label="操作" /></tr></thead>
            <tbody>{filteredSkills.map((skill) => {
              const needsReview = skill.riskLevel === "HIGH" || skill.latestDraftVersionNo != null;
              return <tr key={skill.id} className="platform-console__select-row" tabIndex={0} onClick={() => navigate(`/platform/skills/${skill.id}`)} onKeyDown={(event) => { if (event.key === "Enter") navigate(`/platform/skills/${skill.id}`); }}>
                <td><div className="skills-data-table__skill-name">{skill.name}</div><div className="skills-data-table__summary">{skill.description || skill.skillCode}</div></td>
                <td>v{skill.currentTemplateVersionNo ?? 1}<span className="skill-governance__cell-note">{skill.versionCount} 个版本</span></td>
                <td><span className={`skill-governance__status ${needsReview ? "is-warning" : "is-success"}`}>{needsReview ? "待检查" : skill.enabled ? "已启用" : "已停用"}</span></td>
                <td>{riskLabel(skill.riskLevel)}</td><td>{skill.agentBindingCount}</td><td>{formatTs(skill.updatedAt)}</td>
                <td><button type="button" className="cici-product-icon-button skill-governance__icon-button" aria-label={`查看 ${skill.name}`} onClick={(event) => { event.stopPropagation(); navigate(`/platform/skills/${skill.id}`); }}><MoreHorizontal size={18} /></button></td>
              </tr>;
            })}</tbody>
          </table>
          {!loading && filteredSkills.length === 0 ? <div className="skill-governance__empty">没有符合当前条件的标准技能。</div> : null}
        </section>
      ) : (
        <section className="skills-table-wrap skill-governance__list-surface" aria-label="核心策略包列表">
          <div className="skill-governance__toolbar skill-governance__toolbar--policy"><div><strong>策略包目录</strong><span>当前只启用平台核心安全策略；规划项不提供编辑或发布动作。</span></div><span className="skill-governance__result-count">1 个生效 · {PLANNED_POLICY_PACKAGES.length} 个规划中</span></div>
          <table className="skills-data-table skill-governance__table skill-governance__policy-table">
            <thead><tr><th>策略包</th><th>治理范围</th><th>当前版本</th><th>状态</th><th>适用对象</th><th>最近更新</th><th>操作</th></tr></thead>
            <tbody>
              {policyBundle ? <tr className="platform-console__select-row" tabIndex={0} onClick={() => navigate("/platform/skills/policy/edit")} onKeyDown={(event) => { if (event.key === "Enter") navigate("/platform/skills/policy/edit"); }}>
                <td><div className="skills-data-table__skill-name">{policyBundle.name || "平台核心安全策略"}</div><div className="skills-data-table__summary">{policyBundle.description || "统一高风险确认、证据边界与敏感信息保护规则。"}</div></td>
                <td>安全与可信运行</td><td>v{policyBundle.versionNo}</td><td><span className="skill-governance__status is-success">当前生效</span></td><td>{policyBundle.sourceSkillCodes.length || skills.length} 项标准技能</td><td>{formatTs(policyBundle.updatedAt)}</td><td><button type="button" className="platform-table-link" onClick={(event) => { event.stopPropagation(); navigate("/platform/skills/policy/edit"); }}>管理</button></td>
              </tr> : null}
              {PLANNED_POLICY_PACKAGES.map((item) => <tr key={item.code} className="skill-governance__planned-row"><td><div className="skills-data-table__skill-name">{item.name}</div><div className="skills-data-table__summary">{item.description}</div></td><td>{item.scope}</td><td>—</td><td><span className="skill-governance__status">规划中</span></td><td>{item.targets}</td><td>—</td><td><span className="skill-governance__planned-label">待启用</span></td></tr>)}
            </tbody>
          </table>
          <div className="skill-governance__policy-note"><strong>当前功能边界</strong><span>只有“平台核心安全策略”对应现有 core-default 策略包及版本、草稿、发布和回滚逻辑。</span></div>
        </section>
      )}

      {isSkillDrawerOpen && selectedSkill ? (
        <div className="skill-governance__drawer-layer" onMouseDown={() => navigate("/platform/skills")}>
          <aside className="skill-governance__drawer" role="dialog" aria-modal="true" aria-labelledby="skill-drawer-title" onMouseDown={(event) => event.stopPropagation()}>
            <header className="skill-governance__drawer-head"><div><span>标准技能速览</span><h2 id="skill-drawer-title">{selectedSkill.name}</h2><p>{selectedSkill.description}</p></div><button type="button" className="cici-product-icon-button skill-governance__icon-button" aria-label="关闭详情" onClick={() => navigate("/platform/skills")}><X size={20} /></button></header>
            <div className="skill-governance__drawer-body">
              <div className="skill-governance__drawer-summary"><span className={`skill-governance__status ${selectedSkill.enabled ? "is-success" : "is-warning"}`}>{selectedSkill.enabled ? "已启用" : "已停用"}</span><span>v{selectedSkill.currentTemplateVersionNo ?? 1} 当前版本</span><span>{riskLabel(selectedSkill.riskLevel)}</span><span>{selectedSkill.agentBindingCount} 个 Agent</span><span>{selectedSkill.impact?.publishedWorkflowCount ?? 0} 个已发布工作流</span></div>
              <nav className="skill-governance__text-tabs skill-governance__drawer-tabs" aria-label="技能速览内容">
                {([["overview", "概览"], ["versions", "技能版本"], ["dependencies", "依赖与影响"]] as const).map(([key, label]) => <button type="button" key={key} className={drawerTab === key ? "is-active" : ""} onClick={() => setDrawerTab(key)}>{label}</button>)}
              </nav>
              <div className="skill-governance__drawer-panel">
                {drawerTab === "overview" ? <>
                  <div className="skill-governance__overview-grid"><section><div className="skill-governance__section-heading"><h3>治理摘要</h3><span>配置完整</span></div><dl className="skill-governance__summary-list"><div><dt>可见性</dt><dd>{visibilityLabel(selectedSkill.visibility)}</dd></div><div><dt>绑定策略</dt><dd>{bindingPolicyLabel(selectedSkill.bindingPolicy)}</dd></div><div><dt>对租户启用</dt><dd>{selectedSkill.enabled ? "是" : "否"}</dd></div><div><dt>风险等级</dt><dd>{riskLabel(selectedSkill.riskLevel)}</dd></div><div><dt>最后更新</dt><dd>{formatTs(selectedSkill.updatedAt)}</dd></div></dl></section><section><div className="skill-governance__section-heading"><h3>运行与发布</h3><span>实时影响</span></div><dl className="skill-governance__summary-list"><div><dt>绑定 Agent</dt><dd>{selectedSkill.impact?.boundAgentCount ?? selectedSkill.agentBindingCount}</dd></div><div><dt>已发布工作流</dt><dd>{selectedSkill.impact?.publishedWorkflowCount ?? 0}</dd></div><div><dt>历史版本引用</dt><dd>{selectedSkill.impact?.historicalPinnedWorkflowCount ?? 0}</dd></div><div><dt>当前草稿</dt><dd>{selectedSkill.latestDraftVersionNo ? `v${selectedSkill.latestDraftVersionNo}` : "无"}</dd></div><div><dt>版本总数</dt><dd>{selectedSkill.versionCount}</dd></div></dl></section></div>
                  <section className="skill-governance__checks"><h3>发布检查</h3><p><CheckCircle2 size={17} />治理配置、模板内容与输出约束已载入。</p><p><CheckCircle2 size={17} />发布与回滚继续使用现有版本逻辑。</p>{selectedSkill.riskLevel === "HIGH" ? <p className="is-warning"><AlertTriangle size={17} />当前技能为高风险，发布前需确认影响范围。</p> : null}</section>
                </> : null}
                {drawerTab === "versions" ? <section><div className="skill-governance__section-heading"><div><h3>技能版本</h3><p>不可变版本、草稿状态与安全回滚。</p></div><button type="button" className="platform-button platform-button--secondary" onClick={() => navigate(`/platform/skills/${selectedSkill.id}/edit`)}>编辑并创建草稿</button></div><div className="skills-table-wrap skill-governance__table-wrap"><table className="skills-data-table skill-governance__table"><thead><tr><th>版本</th><th>状态</th><th>变更摘要</th><th>影响</th><th>时间</th><th>操作</th></tr></thead><tbody>{versions.map((version) => {
                  const isCurrent = version.versionNo === selectedSkill.currentTemplateVersionNo;
                  const action = version.versionNo < (selectedSkill.currentTemplateVersionNo ?? 0) ? "rollback" : "publish";
                  return <tr key={version.id}><td><strong>v{version.versionNo}</strong></td><td><span className={`skill-governance__status ${isCurrent ? "is-success" : version.publishStatus === "DRAFT" ? "is-warning" : ""}`}>{isCurrent ? "当前生效" : publishStatusLabel(version.publishStatus)}</span></td><td>{displayVersionNote(version.changelog || version.description)}</td><td>{version.impact?.pinnedWorkflowCount ?? 0} 个工作流</td><td>{formatTs(version.publishedAt || version.createdAt)}</td><td><div className="skill-governance__row-actions"><button type="button" className="platform-table-link" onClick={() => navigate(`/platform/skills/${selectedSkill.id}/preview`)}>预览</button><button type="button" className="platform-table-link" onClick={() => { setDraft(versionToDraft(version)); navigate(`/platform/skills/${selectedSkill.id}/edit`); }}>装载编辑</button>{!isCurrent ? <button type="button" className="platform-table-link" disabled={saving} onClick={() => void applyVersion(version.versionNo, action)}>{action === "rollback" ? "回滚" : "发布"}</button> : null}</div></td></tr>;
                })}</tbody></table></div><p className="skill-governance__inline-note"><CheckCircle2 size={17} />草稿不会覆盖线上版本，发布前仍需确认影响范围。</p></section> : null}
                {drawerTab === "dependencies" ? <section className="platform-skill-dependency"><div className="skill-governance__section-heading"><div><h3>依赖与影响</h3><p>当前线上版本被 Agent、工作流和历史版本引用的情况。</p></div><span>{dependencyGraph?.warnings.length ?? 0} 个提示</span></div>{renderImpactSummary()}<SkillDependencyGraph graph={displayedDependencyGraph} loading={dependencyGraphLoading} error={dependencyGraphError} emptyMessage="当前技能尚未被 Agent 或工作流版本引用。" ariaLabel={`${selectedSkill.name} 依赖影响图`} onRetry={() => void loadSkillDependencyGraph(selectedSkill.id)} /></section> : null}
              </div>
            </div>
            <footer className="skill-governance__drawer-footer"><button type="button" className="platform-table-link" onClick={() => navigate(`/platform/skills/${selectedSkill.id}/preview`)}><Eye size={15} />预览当前草稿</button><button type="button" className="platform-button platform-button--primary" onClick={() => navigate(`/platform/skills/${selectedSkill.id}/edit`)}>编辑技能 <ChevronRight size={15} /></button></footer>
          </aside>
        </div>
      ) : null}
    </div>
  );
}
