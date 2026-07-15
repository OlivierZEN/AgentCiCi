import type { CSSProperties, MouseEvent as ReactMouseEvent } from "react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import AvatarView from "../components/AvatarView";
import AvatarCropperDialog from "../components/AvatarCropperDialog";
import SkillDependencyGraph, { type SkillDependencyGraphView } from "../shared/SkillDependencyGraph";
import { getDisplayInitial, readAvatarFileAsDataUrl } from "../shared/avatar";
import { safeFetchJson } from "../utils/http";
import { buildCompileNotice, isCompileRequired, keepRecentVersionHistory } from "./compile-history";
import AgentOpenApiDocsDialog from "./AgentOpenApiDocsDialog";
import AgentOpenApiKeysDialog from "./AgentOpenApiKeysDialog";
import AgentAccessManagementDialog from "./AgentAccessManagementDialog";

type KnowledgeBase = {
  id: number;
  name: string;
  description: string;
  status: string;
};

type PublishChannelId = "wechat" | "dingtalk" | "feishu" | "web" | "api";

type AgentDraft = {
  name: string;
  avatarBase64: string;
  summary: string;
  greeting: string;
  model: string;
  systemPrompt: string;
  specText: string;
  channels: PublishChannelId[];
  knowledgeBaseIds: number[];
  toolIds: string[];
  handoffRule: string;
  safetyLevel: "balanced" | "strict";
  executionMode: "copilot" | "auto";
  version: string;
  skillBindings: AgentSkillBindingDraft[];
};

type AgentSkillBindingDraft = {
  skillId: number;
  skillCode: string;
  skillName: string;
  riskLevel: "LOW" | "MEDIUM" | "HIGH";
  activationMode: "always-on" | "intent-route" | "manual";
  activationCondition: string;
  priority: number;
  enabled: boolean;
};

type FeishuPublishConfig = {
  appId: string;
  appSecret: string;
  defaultAgentCode: string;
  pairingCommandHint: string;
  autoSyncSchedulesOnPublish: boolean;
};

type PublishConfigDraft = {
  feishu: FeishuPublishConfig;
};

type AgentRecord = {
  id: string;
  name: string;
  summary: string;
  status: "框架中" | "待联调" | "已发布";
  builtin: boolean;
  lastEdited: string;
  channels: PublishChannelId[];
  draft: AgentDraft;
  publishConfig: PublishConfigDraft;
  access: AgentAccessSummary;
};

type AgentAccessSummary = {
  permissions?: string[];
  canManage?: boolean;
  canEdit?: boolean;
  canRun?: boolean;
  canOpenApi?: boolean;
  canViewLogs?: boolean;
};

type CompileArtifact = {
  code: string;
  manifest: string;
  preview: WorkflowPreview;
  summary: string[];
  warnings: string[];
  dependencies: string[];
};

type WorkflowPreviewNode = {
  id: string;
  label: string;
  detail: string;
  kind: "start" | "decision" | "knowledge" | "tool" | "generate" | "handoff" | "output";
};

type WorkflowPreviewEdge = {
  from: string;
  to: string;
  label?: string;
};

type WorkflowPreview = {
  format: "mermaid";
  diagramDsl: string;
  nodes: WorkflowPreviewNode[];
  edges: WorkflowPreviewEdge[];
};

type PositionedWorkflowNode = WorkflowPreviewNode & {
  x: number;
  y: number;
  level: number;
};

type PositionedWorkflowEdge = WorkflowPreviewEdge & {
  source: PositionedWorkflowNode;
  target: PositionedWorkflowNode;
  isActive: boolean;
  path: string;
  labelX: number;
  labelY: number;
};

type AgentBuilderShellProps = {
  kbs: KnowledgeBase[];
  orgId: string;
  token: string;
  pageMode?: "list" | "editor";
  focusAgentId?: string;
  onOpenAgent?: (agentId: string) => void;
  onBackToList?: () => void;
  onRequireModelConfig?: (message: string) => void;
};

export type BaseModelOption = {
  value: string;
  label: string;
  note: string;
};

export const MODEL_CONFIG_REQUIRED_NOTICE = "请先配置模型";
export const AGENT_MODEL_GOVERNANCE_NOTICE = "运行模型由平台统一策略自动选择，并由运营方集中管理路由、降级与成本控制。";

export function resolveAgentCreationModel(
  draftModel: string,
  modelOptions: BaseModelOption[],
): { model: string; requiresModelConfig: boolean } {
  if (draftModel) return { model: draftModel, requiresModelConfig: false };
  const fallback = modelOptions[0]?.value ?? "";
  if (fallback) return { model: fallback, requiresModelConfig: false };
  return { model: "", requiresModelConfig: true };
}

type CompileResponse = {
  workflowCode: string;
  workflowManifest: unknown;
  workflowPreview: WorkflowPreview;
  compileSummary: string[];
  warnings: string[];
  dependencies: string[];
  draftVersionNo?: number | null;
  changed?: boolean;
  compileMessage?: string;
  changeLog?: string[];
};

type VersionHistoryItem = {
  id: number;
  versionNo: number;
  versionLabel?: string;
  publishStatus?: string;
  createdAt: string;
  compileSummary?: string[];
  changeLog?: string[];
};

type ReadinessCheck = {
  code: string;
  status: string;
  severity: "info" | "warning" | "blocker" | string;
  message: string;
};

type AgentReadinessResult = {
  agentId: string;
  versionNo?: number | null;
  status: "ready" | "warning" | "blocked" | string;
  blocked: boolean;
  checks: ReadinessCheck[];
  summary?: Record<string, unknown>;
};

type AgentEvalSuite = {
  id: number;
  agentId: string;
  name: string;
  description?: string;
  gateMode: string;
  minPassRate: number;
  status: string;
};

type AgentEvalRun = {
  id: number;
  suiteId: number;
  versionNo: number;
  status: "PASSED" | "FAILED" | "EMPTY" | string;
  caseCount: number;
  passedCount: number;
  failedCount: number;
  p0FailedCount: number;
  safetyFailedCount: number;
  passRate: number;
  startedAt: string;
  finishedAt?: string;
};

type AgentApiRecord = {
  agentId: string;
  name: string;
  avatarBase64?: string;
  summary?: string;
  greeting?: string;
  model?: string;
  systemPrompt?: string;
  handoffRule?: string;
  safetyLevel?: string;
  executionMode?: string;
  versionLabel?: string;
  builtin?: boolean;
  enabled?: boolean;
  publishedVersionId?: number | null;
  specText?: string;
  knowledgeBaseIds?: number[];
  toolIds?: string[];
  channels?: string[];
  publishConfigs?: Record<string, unknown>;
  skillBindings?: AgentSkillBindingDraft[];
  access?: AgentAccessSummary;
};

type AgentDeletePayload = {
  agentId: string;
  name: string;
  deleted: boolean;
  retentionMessage?: string;
};

type SkillCatalogItem = {
  id: number;
  skillCode: string;
  name: string;
  riskLevel: "LOW" | "MEDIUM" | "HIGH";
  enabled: boolean;
};

type ToolCatalogItem = {
  id: string;
  name: string;
  description: string;
  level: string;
};

type McpServerSummary = {
  id: number;
  name: string;
  enabled: boolean;
  toolCacheCount: number;
};

type McpDiscoveredTool = {
  name: string;
  description?: string;
};

type McpToolCachePayload = {
  cacheStatus?: string;
  toolCount?: number;
  cacheUpdatedAt?: string;
  cacheErrorMessage?: string;
  tools?: McpDiscoveredTool[];
};

type McpPickerTool = {
  id: string;
  name: string;
  description: string;
  level: string;
};

type DebugTraceResult = {
  activeNodeIds: string[];
  notes: string[];
  outcomeLabel: string;
  activeSkills?: string[];
  governanceChips?: string[];
  skillResolutionChain?: DebugSkillResolutionItem[];
  effectiveToolNames?: string[];
  effectiveKnowledgeBaseIds?: string[];
};

type DebugSkillResolutionItem = {
  id: string;
  name: string;
  code: string;
  versionLabel: string;
  referenceLabel: string;
  riskLabel: string;
  activationLabel: string;
};

type DebugRuntimeSkillVersion = {
  skillCode?: string;
  skillName?: string;
  skillVersionNo?: number | null;
  templateCode?: string | null;
  templateVersionNo?: number | null;
};

type DebugRuntimePolicyBundle = {
  bundleCode?: string;
  versionNo?: number | null;
};

type DebugRuntimePayload = {
  activeSkills?: string[];
  effectiveToolNames?: string[];
  effectiveKnowledgeBaseIds?: string[];
  warnings?: string[];
  traceSteps?: string[];
  runtimeSource?: string;
  publishedVersionId?: number | null;
  resolvedSkillVersions?: DebugRuntimeSkillVersion[];
  policyBundle?: DebugRuntimePolicyBundle | null;
  runtimeGovernanceNotes?: string[];
  executionStatus?: string;
  executionOutput?: string;
  executionTrace?: string[];
  contextSnapshot?: Record<string, unknown>;
};

type CompileTab = "preview" | "triggers" | "debug" | "evaluation" | "history" | "publish" | "executions" | "summary" | "code" | "manifest";
type PreviewMode = "workflow" | "skill-dag";
export const AGENT_BUILDER_LIFECYCLE_TABS: ReadonlyArray<{ id: CompileTab; label: string; purpose: string }> = [
  { id: "preview", label: "流程图预览", purpose: "workflow-preview" },
  { id: "triggers", label: "触发与调度", purpose: "runtime-triggers" },
  { id: "debug", label: "试运行", purpose: "candidate-debug" },
  { id: "evaluation", label: "评测", purpose: "quality-governance" },
  { id: "history", label: "版本历史", purpose: "version-history" },
  { id: "publish", label: "发布渠道", purpose: "delivery-channels" },
  { id: "executions", label: "执行记录", purpose: "runtime-executions" },
  { id: "summary", label: "编译摘要", purpose: "compile-summary" },
  { id: "code", label: "流程代码", purpose: "compiled-code" },
  { id: "manifest", label: "Manifest", purpose: "governance-manifest" },
];

type AgentExecutionSource = "try_run" | "manual" | "schedule" | "channel" | "unknown";

type AgentExecutionRecord = {
  id: string;
  agentId: string;
  startedAt: number;
  endedAt: number;
  status: "成功" | "失败" | "运行中" | "取消";
  source: AgentExecutionSource;
  versionLabel: string;
  summary: string;
  errorHint?: string;
};

type ApiExecutionRow = {
  id: number;
  agentId: string;
  workflowVersionId: number | null;
  versionNo: number | null;
  source: string;
  status: string;
  durationMs: number;
  summary: string;
  errorHint: string | null;
  createdAt: string;
};

type TriggersCatalogPayload = {
  agentId: string;
  lifecycle: "NO_COMPILE" | "COMPILED_DRAFT" | "PUBLISHED";
  channelTriggers: Array<{ kind: string; channelId: string; label: string; detail: string }>;
  scheduleTriggers: Array<{
    kind: string;
    id: string;
    title: string;
    cadence?: string;
    detail?: string;
    stub?: boolean;
    source?: string;
    versionNo?: number;
    enabled?: boolean;
  }>;
  scheduleSource?: "none" | "inferred" | "persisted" | "placeholder";
  scheduleSyncHint?: string;
};

function renderFieldTitle(label: string, affectsExecution: boolean, hint: string) {
  return (
    <span className="cici-builder-field__title">
      <span>{label}</span>
      <span className={`cici-builder-field__impact${affectsExecution ? " is-runtime" : " is-meta"}`}>
        {affectsExecution ? "影响执行" : "描述信息"}
      </span>
      <span className="cici-builder-field__hint">{hint}</span>
    </span>
  );
}

function looksLikeAutoVersionLabel(value?: string | null): boolean {
  const text = (value ?? "").trim();
  if (!text) return false;
  // Only treat legacy bootstrap labels (v0.x) as auto-generated noise.
  return /^v?0(?:\.\d+){0,3}$/i.test(text);
}

function normalizePublishRemark(value?: string | null): string {
  const text = (value ?? "").trim();
  if (!text) return "";
  // Hide only legacy bootstrap labels (e.g. v0.1) from "发布备注".
  if (looksLikeAutoVersionLabel(text)) return "";
  return text;
}

function mapApiExecutionRow(row: ApiExecutionRow): AgentExecutionRecord {
  const sourceMap: Record<string, AgentExecutionSource> = {
    TRY_RUN: "try_run",
    MANUAL_PUBLISH: "manual",
    CHANNEL: "channel",
    SCHEDULE_STUB: "schedule",
  };
  const t = Date.parse(row.createdAt);
  const statusMap: Record<string, AgentExecutionRecord["status"]> = {
    SUCCESS: "成功",
    FAILED: "失败",
  };
  return {
    id: String(row.id),
    agentId: row.agentId,
    startedAt: Number.isFinite(t) ? t : Date.now(),
    endedAt: (Number.isFinite(t) ? t : Date.now()) + (row.durationMs ?? 0),
    status: statusMap[row.status] ?? "运行中",
    source: sourceMap[row.source] ?? "unknown",
    versionLabel: row.versionNo != null ? `v${row.versionNo}` : "—",
    summary: row.summary,
    errorHint: row.errorHint ?? undefined,
  };
}

const CHANNEL_OPTIONS: { id: PublishChannelId; label: string; tone: string }[] = [
  { id: "wechat", label: "企微", tone: "协作消息" },
  { id: "dingtalk", label: "钉钉", tone: "内部流转" },
  { id: "feishu", label: "飞书", tone: "知识协同" },
  { id: "web", label: "Web 浮窗", tone: "门户嵌入" },
  { id: "api", label: "开放 API", tone: "系统调用" },
];

function channelTriggerSummaryLine(draft: AgentDraft): string {
  if (draft.channels.length === 0) return "未绑定发布渠道";
  const labels = draft.channels
    .map((id) => CHANNEL_OPTIONS.find((c) => c.id === id)?.label ?? id)
    .filter(Boolean);
  return `已绑定：${labels.join(" · ")}`;
}

function executionSourceLabel(source: AgentExecutionSource): string {
  switch (source) {
    case "try_run":
      return "试运行";
    case "manual":
      return "手动";
    case "schedule":
      return "定时";
    case "channel":
      return "渠道";
    default:
      return "其他";
  }
}

const PREVIEW_NODE_WIDTH = 240;
const PREVIEW_NODE_HEIGHT = 104;
const PREVIEW_COLUMN_GAP = 88;
const PREVIEW_ROW_GAP = 72;
const PREVIEW_PADDING_X = 72;
const PREVIEW_PADDING_Y = 56;

function createDraft(orgId: string, kbIds: number[]): AgentDraft {
  return {
    name: "未命名 Agent",
    avatarBase64: "",
    summary: `${orgId} 的业务助手，负责把规则、知识和动作串起来。`,
    greeting: "你好，我是你的业务智能体，可以帮你检索知识、调用工具并生成标准化输出。",
    model: "",
    systemPrompt: "你是企业内部可执行 Agent。先判断用户请求类型，再决定是直接回答、检索知识库还是调用业务工具；不允许编造制度、价格或承诺。",
    specText: [
      "你是企业内部业务 Agent。",
      "先判断用户请求类型，再决定是检索知识库还是调用业务工具。",
      "如果知识命中不足或触发高风险动作，必须转人工。",
      "输出必须包含结论、依据和下一步建议。",
    ].join("\n"),
    channels: ["wechat", "dingtalk"],
    knowledgeBaseIds: kbIds,
    toolIds: [],
    handoffRule: "当命中知识不足、置信度较低或触发高风险操作时，转交人工。",
    safetyLevel: "balanced",
    executionMode: "copilot",
    version: "",
    skillBindings: [],
  };
}

function cloneDraft(draft: AgentDraft): AgentDraft {
  return {
    ...draft,
    channels: [...draft.channels],
    knowledgeBaseIds: [...draft.knowledgeBaseIds],
    toolIds: [...draft.toolIds],
    skillBindings: draft.skillBindings.map((item) => ({ ...item })),
  };
}

function createPublishConfigDraft(): PublishConfigDraft {
  return {
    feishu: {
      appId: "",
      appSecret: "",
      defaultAgentCode: "cici",
      pairingCommandHint: "配对",
      autoSyncSchedulesOnPublish: true,
    },
  };
}

function clonePublishConfigDraft(config: PublishConfigDraft): PublishConfigDraft {
  return {
    feishu: {
      ...config.feishu,
    },
  };
}

function escapeMermaidLabel(value: string): string {
  return value.replace(/"/g, '\\"').replace(/\n/g, " ");
}

function getNodeExpression(node: WorkflowPreviewNode): string {
  const label = escapeMermaidLabel(node.label);
  switch (node.kind) {
    case "start":
      return `${node.id}(["${label}"])`;
    case "decision":
      return `${node.id}{"${label}"}`;
    case "tool":
      return `${node.id}[["${label}"]]`;
    case "output":
      return `${node.id}(["${label}"])`;
    default:
      return `${node.id}["${label}"]`;
  }
}

function buildMermaidDiagram(nodes: WorkflowPreviewNode[], edges: WorkflowPreviewEdge[]): string {
  const classNames = {
    start: "start",
    decision: "decision",
    knowledge: "knowledge",
    tool: "tool",
    generate: "generate",
    handoff: "handoff",
    output: "output",
  } as const;

  const lines = [
    "flowchart TD",
    ...nodes.map((node) => `  ${getNodeExpression(node)}`),
    ...edges.map((edge) => `  ${edge.from} -->${edge.label ? `|${escapeMermaidLabel(edge.label)}|` : ""} ${edge.to}`),
    "  classDef start fill:#1d4ed8,stroke:#1e3a8a,color:#ffffff,stroke-width:1.4px;",
    "  classDef decision fill:#fff7d6,stroke:#d4a72c,color:#5b4300,stroke-width:1.4px;",
    "  classDef knowledge fill:#e6f4ef,stroke:#1e9b72,color:#0d4f3a,stroke-width:1.4px;",
    "  classDef tool fill:#edf2ff,stroke:#5b7ff4,color:#2742a4,stroke-width:1.4px;",
    "  classDef generate fill:#f4ebff,stroke:#8a55d8,color:#4d217f,stroke-width:1.4px;",
    "  classDef handoff fill:#fff1f2,stroke:#df4f67,color:#8f1830,stroke-width:1.4px;",
    "  classDef output fill:#eef2f7,stroke:#64748b,color:#1f2937,stroke-width:1.4px;",
    ...nodes.map((node) => `  class ${node.id} ${classNames[node.kind]};`),
  ];
  return lines.join("\n");
}

function generateWorkflowPreview(draft: AgentDraft, kbs: KnowledgeBase[], toolCatalog: ToolCatalogItem[]): WorkflowPreview {
  const selectedKbs = kbs.filter((kb) => draft.knowledgeBaseIds.includes(kb.id));
  const selectedTools = toolCatalog.filter((tool) => draft.toolIds.includes(tool.id));
  const businessTools = selectedTools.filter((tool) => tool.id !== "rag-search");
  const hasKnowledge = selectedKbs.length > 0 || selectedTools.some((tool) => tool.id === "rag-search");
  const nodes: WorkflowPreviewNode[] = [
    { id: "input", label: "接收用户输入", detail: "来自会话、IM 或门户渠道。", kind: "start" },
    { id: "intent", label: "识别意图", detail: "根据 Spec 判断是问答、查询还是执行请求。", kind: "decision" },
  ];
  const edges: WorkflowPreviewEdge[] = [{ from: "input", to: "intent" }];

  if (hasKnowledge) {
    nodes.push({
      id: "knowledge",
      label: "知识检索",
      detail: selectedKbs.length > 0 ? `检索 ${selectedKbs.map((kb) => kb.name).join("、")}` : "使用已授权知识上下文",
      kind: "knowledge",
    });
    edges.push({ from: "intent", to: "knowledge", label: "知识问答" });
  }

  if (businessTools.length > 0) {
    nodes.push({
      id: "tooling",
      label: businessTools.length > 1 ? `工具编排 (${businessTools.length})` : businessTools[0].name,
      detail: businessTools.map((tool) => tool.name).join("、"),
      kind: "tool",
    });
    edges.push({ from: "intent", to: "tooling", label: "查询 / 动作" });
  }

  nodes.push({
    id: "compose",
    label: "生成回复",
    detail: "按“结论 / 依据 / 下一步建议”输出。",
    kind: "generate",
  });

  if (hasKnowledge) {
    edges.push({ from: "knowledge", to: "compose", label: "命中充分" });
  }

  if (businessTools.length > 0) {
    edges.push({ from: "tooling", to: "compose", label: "结果可用" });
  }

  if (!hasKnowledge && businessTools.length === 0) {
    edges.push({ from: "intent", to: "compose", label: "直接生成" });
  }

  const needsHandoff = Boolean(draft.handoffRule.trim());
  if (needsHandoff) {
    nodes.push({
      id: "handoff",
      label: "人工兜底",
      detail: draft.handoffRule,
      kind: "handoff",
    });
    if (hasKnowledge) {
      edges.push({ from: "knowledge", to: "handoff", label: "低置信" });
    }
    if (businessTools.length > 0) {
      edges.push({ from: "tooling", to: "handoff", label: "高风险 / 异常" });
    }
    if (!hasKnowledge && businessTools.length === 0) {
      edges.push({ from: "intent", to: "handoff", label: "需人工确认" });
    }
  }

  nodes.push({
    id: "output",
    label: draft.executionMode === "auto" ? "输出并执行" : "输出建议",
    detail: draft.executionMode === "auto" ? "自动模式可继续触发后续动作。" : "协作模式下由人工决定是否执行。",
    kind: "output",
  });

  edges.push({ from: "compose", to: "output" });
  if (needsHandoff) {
    edges.push({ from: "handoff", to: "output", label: "人工接管" });
  }

  return {
    format: "mermaid",
    diagramDsl: buildMermaidDiagram(nodes, edges),
    nodes,
    edges,
  };
}

function getWorkflowNodeTheme(kind: WorkflowPreviewNode["kind"]) {
  switch (kind) {
    case "start":
      return { eyebrow: "START", accent: "#2958d9", surface: "#eef4ff", icon: "in" };
    case "decision":
      return { eyebrow: "ROUTER", accent: "#0f9f8a", surface: "#ebfbf6", icon: "if" };
    case "knowledge":
      return { eyebrow: "KNOWLEDGE", accent: "#0f9f8a", surface: "#eaf9f3", icon: "kb" };
    case "tool":
      return { eyebrow: "TOOLS", accent: "#6475ff", surface: "#eef1ff", icon: "fx" };
    case "generate":
      return { eyebrow: "GENERATE", accent: "#8b5cf6", surface: "#f5efff", icon: "ai" };
    case "handoff":
      return { eyebrow: "HANDOFF", accent: "#ef5f7a", surface: "#fff0f3", icon: "hm" };
    case "output":
      return { eyebrow: "OUTPUT", accent: "#667085", surface: "#f3f4f6", icon: "out" };
    default:
      return { eyebrow: "STEP", accent: "#667085", surface: "#f3f4f6", icon: "nd" };
  }
}

function buildWorkflowPreviewLayout(preview: WorkflowPreview, activeNodeIds: string[]) {
  const incomingCounts = new Map(preview.nodes.map((node) => [node.id, 0]));
  const levels = new Map(preview.nodes.map((node) => [node.id, 0]));

  preview.edges.forEach((edge) => {
    incomingCounts.set(edge.to, (incomingCounts.get(edge.to) ?? 0) + 1);
  });

  for (let index = 0; index < preview.nodes.length; index += 1) {
    preview.edges.forEach((edge) => {
      const nextLevel = (levels.get(edge.from) ?? 0) + 1;
      if (nextLevel > (levels.get(edge.to) ?? 0)) {
        levels.set(edge.to, nextLevel);
      }
    });
  }

  const columns = new Map<number, WorkflowPreviewNode[]>();
  preview.nodes.forEach((node) => {
    const level = levels.get(node.id) ?? 0;
    columns.set(level, [...(columns.get(level) ?? []), node]);
  });

  const maxCount = Math.max(...Array.from(columns.values(), (nodes) => nodes.length), 1);
  const maxLevel = Math.max(...Array.from(levels.values()), 0);
  const width = PREVIEW_PADDING_X * 2 + (maxLevel + 1) * PREVIEW_NODE_WIDTH + maxLevel * PREVIEW_COLUMN_GAP;
  const height = Math.max(
    420,
    PREVIEW_PADDING_Y * 2 + maxCount * PREVIEW_NODE_HEIGHT + Math.max(0, maxCount - 1) * PREVIEW_ROW_GAP,
  );

  const positionedNodes: PositionedWorkflowNode[] = preview.nodes.map((node) => {
    const level = levels.get(node.id) ?? 0;
    const columnNodes = columns.get(level) ?? [node];
    const nodeIndex = columnNodes.findIndex((item) => item.id === node.id);
    const columnHeight = columnNodes.length * PREVIEW_NODE_HEIGHT + Math.max(0, columnNodes.length - 1) * PREVIEW_ROW_GAP;
    const x = PREVIEW_PADDING_X + level * (PREVIEW_NODE_WIDTH + PREVIEW_COLUMN_GAP);
    const y = Math.round((height - columnHeight) / 2 + nodeIndex * (PREVIEW_NODE_HEIGHT + PREVIEW_ROW_GAP));
    return { ...node, x, y, level };
  });

  const nodesById = new Map(positionedNodes.map((node) => [node.id, node]));
  const activeNodeSet = new Set(activeNodeIds);
  const positionedEdges: PositionedWorkflowEdge[] = preview.edges
    .map((edge) => {
      const source = nodesById.get(edge.from);
      const target = nodesById.get(edge.to);
      if (!source || !target) return null;

      const startX = source.x + PREVIEW_NODE_WIDTH;
      const startY = source.y + PREVIEW_NODE_HEIGHT / 2;
      const endX = target.x;
      const endY = target.y + PREVIEW_NODE_HEIGHT / 2;
      const controlOffset = Math.max(48, (endX - startX) * 0.38);
      const controlX1 = startX + controlOffset;
      const controlX2 = endX - controlOffset;
      const path = `M ${startX} ${startY} C ${controlX1} ${startY}, ${controlX2} ${endY}, ${endX} ${endY}`;
      const labelT = 0.5;
      const labelX = Math.round(
        ((1 - labelT) ** 3) * startX
        + 3 * ((1 - labelT) ** 2) * labelT * controlX1
        + 3 * (1 - labelT) * (labelT ** 2) * controlX2
        + (labelT ** 3) * endX,
      );
      const labelY = Math.round(
        ((1 - labelT) ** 3) * startY
        + 3 * ((1 - labelT) ** 2) * labelT * startY
        + 3 * (1 - labelT) * (labelT ** 2) * endY
        + (labelT ** 3) * endY,
      );

      return {
        ...edge,
        source,
        target,
        isActive: activeNodeSet.has(source.id) && activeNodeSet.has(target.id),
        path,
        labelX,
        labelY,
      };
    })
    .filter((edge): edge is PositionedWorkflowEdge => Boolean(edge));

  return {
    width,
    height,
    nodes: positionedNodes,
    edges: positionedEdges,
    hasRootNode: Array.from(incomingCounts.values()).some((count) => count === 0),
  };
}

function PreviewNodeIcon({ kind }: { kind: WorkflowPreviewNode["kind"] }) {
  switch (kind) {
    case "start":
      return (
        <svg viewBox="0 0 24 24" aria-hidden>
          <path d="M8 6.5v11l9-5.5-9-5.5Z" fill="currentColor" />
        </svg>
      );
    case "decision":
      return (
        <svg viewBox="0 0 24 24" aria-hidden>
          <path d="M12 4.5 19.5 12 12 19.5 4.5 12 12 4.5Z" fill="none" stroke="currentColor" strokeWidth="2" />
        </svg>
      );
    case "knowledge":
      return (
        <svg viewBox="0 0 24 24" aria-hidden>
          <path d="M7 5.5h8.5A2.5 2.5 0 0 1 18 8v10.5H9.5A2.5 2.5 0 0 0 7 21V5.5Z" fill="none" stroke="currentColor" strokeWidth="2" />
          <path d="M7 7h6M7 11h7M7 15h5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
        </svg>
      );
    case "tool":
      return (
        <svg viewBox="0 0 24 24" aria-hidden>
          <path d="M14.5 5.5a4 4 0 0 0-4.23 5.32L5.5 15.59l2.91 2.91 4.77-4.77A4 4 0 1 0 14.5 5.5Z" fill="none" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" />
        </svg>
      );
    case "generate":
      return (
        <svg viewBox="0 0 24 24" aria-hidden>
          <path d="m12 4 1.55 4.45L18 10l-4.45 1.55L12 16l-1.55-4.45L6 10l4.45-1.55L12 4Z" fill="currentColor" />
        </svg>
      );
    case "handoff":
      return (
        <svg viewBox="0 0 24 24" aria-hidden>
          <path d="M7 12h10M12 7l5 5-5 5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      );
    case "output":
      return (
        <svg viewBox="0 0 24 24" aria-hidden>
          <rect x="5" y="5" width="14" height="14" rx="3" fill="none" stroke="currentColor" strokeWidth="2" />
          <path d="M9 12h6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
        </svg>
      );
    default:
      return null;
  }
}

function simulateDebugTrace(preview: WorkflowPreview, draft: AgentDraft, input: string): DebugTraceResult {
  const text = input.trim();
  const hasKnowledge = preview.nodes.some((node) => node.id === "knowledge");
  const hasTooling = preview.nodes.some((node) => node.id === "tooling");
  const hasHandoff = preview.nodes.some((node) => node.id === "handoff");
  const keyword = text.toLowerCase();
  const shouldUseTool = hasTooling && /报价|客户|审批|生成|创建|查询|crm|待办/.test(text);
  const shouldUseKnowledge = hasKnowledge && (!shouldUseTool || /产品|制度|规则|知识|说明|怎么|如何/.test(text));
  const shouldHandoff = hasHandoff && /人工|转人工|异常|承诺|折扣|合同|高风险/.test(text);
  const activeNodeIds = Array.from(new Set([
    "input",
    "intent",
    shouldUseKnowledge ? "knowledge" : "",
    shouldUseTool ? "tooling" : "",
    shouldHandoff ? "handoff" : "compose",
    "output",
  ].filter(Boolean)));

  const notes = [
    text ? `测试输入已收到：${text}` : "当前未填写测试输入，使用默认空白路径预览。",
    shouldUseKnowledge ? "根据关键词，本次会先走知识检索分支。" : "本次不优先命中知识检索分支。",
    shouldUseTool ? "根据关键词，本次会触发工具调用分支。" : "本次不会触发工具调用分支。",
    shouldHandoff
      ? "命中人工兜底规则，将优先转人工或升级确认。"
      : `未触发人工兜底，将按${draft.executionMode === "auto" ? "自动执行" : "建议输出"}完成结果生成。`,
  ];

  return {
    activeNodeIds,
    notes,
    outcomeLabel: shouldHandoff ? "人工接管" : draft.executionMode === "auto" ? "自动执行" : "建议输出",
    governanceChips: ["前端模拟"],
    skillResolutionChain: buildDebugSkillResolutionChain(
      draft.skillBindings.filter((binding) => binding.enabled).map((binding) => ({
        skillCode: binding.skillCode,
        skillName: binding.skillName,
        skillVersionNo: null,
      })),
      draft.skillBindings,
    ),
    effectiveToolNames: draft.toolIds,
    effectiveKnowledgeBaseIds: draft.knowledgeBaseIds.map(String),
  };
}

function toStringArray(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value.filter((item): item is string => typeof item === "string");
}

function buildDebugOutcomeLabel(executionStatus: string | undefined, runtimeSource: string | undefined, draft: AgentDraft): string {
  if (executionStatus === "published-executed") return "已按发布版本运行";
  if (executionStatus === "fallback-executed") return "后端回退运行";
  if (executionStatus === "published-invalid") return "发布版本异常";
  if (runtimeSource === "published_version") return "已走后端调试";
  return draft.executionMode === "auto" ? "自动执行" : "建议输出";
}

function buildRuntimeSourceLabel(runtimeSource: string | undefined): string {
  switch (runtimeSource) {
    case "published_version":
      return "已发布 Agent 版本";
    case "draft_capability":
      return "草稿能力解析";
    default:
      return runtimeSource ? `后端运行时（${runtimeSource}）` : "后端运行时";
  }
}

function buildRuntimeSkillSummary(item: DebugRuntimeSkillVersion): string {
  const name = item.skillName || item.skillCode || "unknown-skill";
  const version = item.skillVersionNo != null ? `v${item.skillVersionNo}` : "v?";
  if (item.templateCode) {
    const templateVersion = item.templateVersionNo != null ? `@${item.templateCode}#v${item.templateVersionNo}` : `@${item.templateCode}`;
    return `${name} ${version} ${templateVersion}`;
  }
  return `${name} ${version}`;
}

export function buildAgentSkillDagUrl(agentId: string, versionNo?: number | null): string {
  const base = `/agents/${encodeURIComponent(agentId)}/skill-dag`;
  return versionNo == null ? base : `${base}?versionNo=${encodeURIComponent(String(versionNo))}`;
}

export function isLatestSkillDagRequest(requestId: number, latestRequestId: number): boolean {
  return requestId === latestRequestId;
}

export function buildDebugSkillResolutionChain(
  resolvedVersions: DebugRuntimeSkillVersion[],
  bindings: AgentSkillBindingDraft[],
): DebugSkillResolutionItem[] {
  const riskLabels: Record<string, string> = { LOW: "低风险", MEDIUM: "中风险", HIGH: "高风险" };
  const activationLabels: Record<string, string> = {
    "always-on": "始终启用",
    "intent-route": "意图路由",
    manual: "手动启用",
  };
  return resolvedVersions.map((item, index) => {
    const code = item.skillCode || "";
    const name = item.skillName || code || `Skill ${index + 1}`;
    const binding = bindings.find((candidate) => candidate.skillCode === code || candidate.skillName === name);
    const versionLabel = item.skillVersionNo != null ? `v${item.skillVersionNo}` : "当前草稿";
    const templateVersion = item.templateVersionNo != null ? `@v${item.templateVersionNo}` : "";
    return {
      id: `${code || name}:${item.skillVersionNo ?? "current"}`,
      name,
      code: code || "—",
      versionLabel,
      referenceLabel: item.templateCode
        ? `模板 ${item.templateCode}${templateVersion}`
        : item.skillVersionNo != null ? "工作流钉住版本" : "当前绑定",
      riskLabel: riskLabels[binding?.riskLevel ?? ""] ?? "未声明",
      activationLabel: activationLabels[binding?.activationMode ?? ""] ?? "未声明",
    };
  });
}

function buildDebugActiveNodeIds(preview: WorkflowPreview,
                                 payload: DebugRuntimePayload): string[] {
  const nodeIds = new Set<string>(["input", "intent"]);
  const contextSnapshot = payload.contextSnapshot;
  const parsedNodes = toStringArray(contextSnapshot?.["parsedNodes"]);
  const hasNode = (nodeId: string) => preview.nodes.some((node) => node.id === nodeId);
  const knowledgeUsed = contextSnapshot?.["knowledgeUsed"] === true || parsedNodes.includes("knowledge-search");
  const toolInvoked = contextSnapshot?.["toolInvoked"] === true || parsedNodes.includes("tool-invoke-best");
  const responsePlanned = contextSnapshot?.["responsePlanned"] === true || parsedNodes.includes("response-generate");
  const handoffRequested = parsedNodes.includes("handoff-request");

  if (knowledgeUsed && hasNode("knowledge")) nodeIds.add("knowledge");
  if (toolInvoked && hasNode("tooling")) nodeIds.add("tooling");
  if (responsePlanned && hasNode("compose")) nodeIds.add("compose");
  if (handoffRequested && hasNode("handoff")) nodeIds.add("handoff");
  if (payload.executionStatus && payload.executionStatus !== "published-invalid" && hasNode("output")) nodeIds.add("output");
  return Array.from(nodeIds);
}

function buildBackendDebugTrace(preview: WorkflowPreview,
                                draft: AgentDraft,
                                input: string,
                                payload: DebugRuntimePayload): DebugTraceResult {
  const notes: string[] = [];
  const pushNote = (line: string | undefined | null) => {
    const normalized = line?.trim();
    if (!normalized || notes.includes(normalized)) return;
    notes.push(normalized);
  };

  const governanceChips: string[] = ["后端真实运行"];
  if (payload.runtimeSource) governanceChips.push(buildRuntimeSourceLabel(payload.runtimeSource));
  if (payload.publishedVersionId != null) governanceChips.push(`发布记录 #${payload.publishedVersionId}`);
  if (payload.policyBundle?.bundleCode) {
    const version = payload.policyBundle.versionNo != null ? `@v${payload.policyBundle.versionNo}` : "";
    governanceChips.push(`Policy ${payload.policyBundle.bundleCode}${version}`);
  }

  pushNote(input.trim() ? `测试输入已发送到后端真实运行：${input.trim()}` : "未填写测试输入，后端按空输入路径执行。");
  pushNote(`调试来源：${buildRuntimeSourceLabel(payload.runtimeSource)}`);
  if (payload.executionStatus) pushNote(`执行状态：${payload.executionStatus}`);
  if (payload.executionOutput) pushNote(`执行结果：${payload.executionOutput}`);
  if (payload.policyBundle?.bundleCode) {
    pushNote(`Policy bundle：${payload.policyBundle.bundleCode}@v${payload.policyBundle.versionNo ?? "?"}`);
  }
  if (payload.resolvedSkillVersions && payload.resolvedSkillVersions.length > 0) {
    pushNote(`Skill 版本：${payload.resolvedSkillVersions.map(buildRuntimeSkillSummary).join("；")}`);
  }
  if (payload.effectiveToolNames && payload.effectiveToolNames.length > 0) {
    pushNote(`工具范围：${payload.effectiveToolNames.join("、")}`);
  }
  if (payload.effectiveKnowledgeBaseIds && payload.effectiveKnowledgeBaseIds.length > 0) {
    pushNote(`知识库范围：${payload.effectiveKnowledgeBaseIds.join("、")}`);
  }
  (payload.runtimeGovernanceNotes ?? []).forEach((line) => pushNote(`治理摘要：${line}`));
  (payload.warnings ?? []).forEach((line) => pushNote(`运行告警：${line}`));
  (payload.traceSteps ?? []).forEach((line) => pushNote(`调试摘要：${line}`));
  (payload.executionTrace ?? []).forEach((line) => pushNote(`执行轨迹：${line}`));

  return {
    activeNodeIds: buildDebugActiveNodeIds(preview, payload),
    notes,
    outcomeLabel: buildDebugOutcomeLabel(payload.executionStatus, payload.runtimeSource, draft),
    activeSkills: payload.activeSkills ?? [],
    governanceChips,
    skillResolutionChain: buildDebugSkillResolutionChain(payload.resolvedSkillVersions ?? [], draft.skillBindings),
    effectiveToolNames: payload.effectiveToolNames ?? [],
    effectiveKnowledgeBaseIds: payload.effectiveKnowledgeBaseIds ?? [],
  };
}

function WorkflowPreviewCanvas({
  preview,
  activeNodeIds = [],
  startChannelSummary,
  startScheduleBadge,
  onInspectStartTriggers,
}: {
  preview: WorkflowPreview;
  activeNodeIds?: string[];
  /** Short line for START badges, e.g. "已绑定：企微 · 飞书" */
  startChannelSummary?: string;
  /** e.g. "定时 ×1" or null when none / unknown */
  startScheduleBadge?: string | null;
  /** Click START node to open triggers tab / panel */
  onInspectStartTriggers?: () => void;
}) {
  const layout = useMemo(() => buildWorkflowPreviewLayout(preview, activeNodeIds), [activeNodeIds, preview]);
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [scale, setScale] = useState(1);
  const [viewportRect, setViewportRect] = useState({ left: 0, top: 0, width: layout.width, height: layout.height });

  if (preview.nodes.length === 0 || preview.edges.length === 0) {
    return <div className="cici-builder-graph__fallback">当前还没有足够的节点数据，暂时无法生成流程图预览。</div>;
  }

  if (!layout.hasRootNode) {
    return <div className="cici-builder-graph__fallback">流程图数据存在循环依赖，暂时无法生成只读预览。</div>;
  }

  const scaledWidth = layout.width * scale;
  const scaledHeight = layout.height * scale;
  const miniMapWidth = 132;
  const miniMapHeight = 88;
  const miniMapScale = Math.min(miniMapWidth / layout.width, miniMapHeight / layout.height);
  const miniMapContentWidth = layout.width * miniMapScale;
  const miniMapContentHeight = layout.height * miniMapScale;
  const miniMapOffsetX = Math.round((miniMapWidth - miniMapContentWidth) / 2);
  const miniMapOffsetY = Math.round((miniMapHeight - miniMapContentHeight) / 2);

  const syncViewport = () => {
    const container = containerRef.current;
    if (!container) return;
    setViewportRect({
      left: container.scrollLeft / scale,
      top: container.scrollTop / scale,
      width: container.clientWidth / scale,
      height: container.clientHeight / scale,
    });
  };

  useEffect(() => {
    syncViewport();
  }, [layout.height, layout.width, scale]);

  const zoomBy = (nextScale: number) => {
    const container = containerRef.current;
    const boundedScale = Math.min(1.8, Math.max(0.55, nextScale));
    if (!container) {
      setScale(boundedScale);
      return;
    }

    const centerX = container.scrollLeft + container.clientWidth / 2;
    const centerY = container.scrollTop + container.clientHeight / 2;
    const contentX = centerX / scale;
    const contentY = centerY / scale;
    setScale(boundedScale);

    window.requestAnimationFrame(() => {
      container.scrollLeft = Math.max(0, contentX * boundedScale - container.clientWidth / 2);
      container.scrollTop = Math.max(0, contentY * boundedScale - container.clientHeight / 2);
      syncViewport();
    });
  };

  const fitCanvas = () => {
    const container = containerRef.current;
    if (!container) return;
    const nextScale = Math.min(1, Math.max(0.55, Math.min(container.clientWidth / layout.width, container.clientHeight / layout.height) - 0.04));
    setScale(nextScale);
    window.requestAnimationFrame(() => {
      container.scrollLeft = 0;
      container.scrollTop = 0;
      syncViewport();
    });
  };

  const handleMiniMapClick = (event: ReactMouseEvent<SVGSVGElement>) => {
    const container = containerRef.current;
    if (!container) return;
    const bounds = event.currentTarget.getBoundingClientRect();
    const clickX = event.clientX - bounds.left - miniMapOffsetX;
    const clickY = event.clientY - bounds.top - miniMapOffsetY;
    const contentX = clickX / miniMapScale;
    const contentY = clickY / miniMapScale;
    container.scrollLeft = Math.max(0, contentX * scale - container.clientWidth / 2);
    container.scrollTop = Math.max(0, contentY * scale - container.clientHeight / 2);
    syncViewport();
  };

  return (
    <div className="cici-builder-graph__canvas cici-builder-graph__canvas--cici">
      <div className="cici-builder-graph__scroll" ref={containerRef} onScroll={syncViewport}>
        <div className="cici-builder-graph__stage" style={{ width: scaledWidth, height: scaledHeight }}>
          <div className="cici-builder-graph__viewport" style={{ width: layout.width, height: layout.height, transform: `scale(${scale})` }}>
            <svg className="cici-builder-graph__edges" viewBox={`0 0 ${layout.width} ${layout.height}`} aria-hidden>
              {layout.edges.map((edge) => (
                <path
                  key={`${edge.from}-${edge.to}-${edge.label ?? "plain"}`}
                  className={`cici-builder-graph__edge${edge.isActive ? " is-active" : ""}`}
                  d={edge.path}
                />
              ))}
            </svg>

            {layout.edges.map((edge) => (
              edge.label ? (
                <div
                  key={`${edge.from}-${edge.to}-${edge.label}-label`}
                  className={`cici-builder-graph__edge-label${edge.isActive ? " is-active" : ""}`}
                  style={{ left: edge.labelX, top: edge.labelY }}
                >
                  {edge.label}
                </div>
              ) : null
            ))}

            {layout.nodes.map((node) => {
              const theme = getWorkflowNodeTheme(node.kind);
              const isActive = activeNodeIds.includes(node.id);
              const isStart = node.kind === "start";
              const nodeStyle = {
                left: node.x,
                top: node.y,
                "--node-accent": theme.accent,
                "--node-surface": theme.surface,
              } as CSSProperties;
              return (
                <article
                  key={node.id}
                  role={isStart && onInspectStartTriggers ? "button" : undefined}
                  tabIndex={isStart && onInspectStartTriggers ? 0 : undefined}
                  className={`cici-builder-flow-node cici-builder-flow-node--${node.kind}${isActive ? " is-active" : ""}${
                    isStart && onInspectStartTriggers ? " cici-builder-flow-node--start-clickable" : ""
                  }`}
                  style={nodeStyle}
                  onClick={isStart && onInspectStartTriggers ? () => onInspectStartTriggers() : undefined}
                  onKeyDown={
                    isStart && onInspectStartTriggers
                      ? (event) => {
                          if (event.key === "Enter" || event.key === " ") {
                            event.preventDefault();
                            onInspectStartTriggers();
                          }
                        }
                      : undefined
                  }
                >
                  <span className="cici-builder-flow-node__port cici-builder-flow-node__port--in" />
                  <span className="cici-builder-flow-node__port cici-builder-flow-node__port--out" />
                  <div className="cici-builder-flow-node__head">
                    <span className="cici-builder-flow-node__icon">
                      <PreviewNodeIcon kind={node.kind} />
                    </span>
                    <div className="cici-builder-flow-node__meta">
                      <span className="cici-builder-flow-node__eyebrow">{theme.eyebrow}</span>
                      <strong>{node.label}</strong>
                    </div>
                  </div>
                  <p className="cici-builder-flow-node__detail">{node.detail}</p>
                  {isStart && (startChannelSummary || startScheduleBadge) ? (
                    <div className="cici-builder-flow-node__badges">
                      {startChannelSummary ? (
                        <span className="cici-builder-flow-node__badge" title="入口与发布渠道（详见「触发与调度」）">
                          {startChannelSummary}
                        </span>
                      ) : null}
                      {startScheduleBadge ? (
                        <span className="cici-builder-flow-node__badge cici-builder-flow-node__badge--muted" title="定时触发（发布后由平台同步）">
                          {startScheduleBadge}
                        </span>
                      ) : null}
                      {onInspectStartTriggers ? (
                        <span className="cici-builder-flow-node__badge cici-builder-flow-node__badge--link">查看触发与调度 →</span>
                      ) : null}
                    </div>
                  ) : null}
                </article>
              );
            })}
          </div>
        </div>
      </div>

      <div className="cici-builder-graph__controls">
        <button type="button" className="cici-builder-graph__control-btn" onClick={() => zoomBy(scale - 0.12)} aria-label="缩小">-</button>
        <button type="button" className="cici-builder-graph__control-btn cici-builder-graph__control-btn--scale" onClick={() => zoomBy(1)}>
          {Math.round(scale * 100)}%
        </button>
        <button type="button" className="cici-builder-graph__control-btn" onClick={() => zoomBy(scale + 0.12)} aria-label="放大">+</button>
        <button type="button" className="cici-builder-graph__control-btn" onClick={fitCanvas}>适配</button>
      </div>

      <div className="cici-builder-graph__minimap">
        <svg viewBox={`0 0 ${miniMapWidth} ${miniMapHeight}`} onClick={handleMiniMapClick} role="img" aria-label="流程图缩略图导航">
          <rect x="0.5" y="0.5" width={miniMapWidth - 1} height={miniMapHeight - 1} rx="12" className="cici-builder-graph__minimap-frame" />
          <g transform={`translate(${miniMapOffsetX} ${miniMapOffsetY})`}>
            {layout.edges.map((edge) => (
              <path
                key={`${edge.from}-${edge.to}-${edge.label ?? "plain"}-mini`}
                d={edge.path}
                className={`cici-builder-graph__minimap-edge${edge.isActive ? " is-active" : ""}`}
                transform={`scale(${miniMapScale})`}
              />
            ))}
            {layout.nodes.map((node) => {
              const isActive = activeNodeIds.includes(node.id);
              return (
                <rect
                  key={`${node.id}-mini`}
                  x={node.x * miniMapScale}
                  y={node.y * miniMapScale}
                  width={PREVIEW_NODE_WIDTH * miniMapScale}
                  height={PREVIEW_NODE_HEIGHT * miniMapScale}
                  rx={10}
                  className={`cici-builder-graph__minimap-node${isActive ? " is-active" : ""}`}
                />
              );
            })}
            <rect
              x={viewportRect.left * miniMapScale}
              y={viewportRect.top * miniMapScale}
              width={Math.min(layout.width - viewportRect.left, viewportRect.width) * miniMapScale}
              height={Math.min(layout.height - viewportRect.top, viewportRect.height) * miniMapScale}
              rx={8}
              className="cici-builder-graph__minimap-viewport"
            />
          </g>
        </svg>
      </div>
    </div>
  );
}

function generateCompileArtifact(draft: AgentDraft, kbs: KnowledgeBase[], toolCatalog: ToolCatalogItem[]): CompileArtifact {
  const selectedKbs = kbs.filter((kb) => draft.knowledgeBaseIds.includes(kb.id));
  const selectedTools = toolCatalog.filter((tool) => draft.toolIds.includes(tool.id));
  const kbNames = selectedKbs.map((kb) => kb.name);
  const toolNames = selectedTools.map((tool) => tool.name);
  const warnings = [
    draft.executionMode === "auto" ? "当前为自动执行模式，发布前应补充更多测试样例。" : "",
    selectedTools.some((tool) => tool.level === "高风险") ? "已启用高风险工具，建议强制人工确认后再正式发布。" : "",
    kbNames.length === 0 ? "尚未绑定知识库，生成结果会缺少企业知识上下文。" : "",
    draft.specText.length < 80 ? "流程描述偏短，建议补充条件分支和异常规则。" : "",
  ].filter(Boolean);

  const dependencies = [
    `model:${draft.model}`,
    ...kbNames.map((name) => `kb:${name}`),
    ...toolNames.map((name) => `tool:${name}`),
  ];

  const summary = [
    `角色定义为「${draft.name}」，主场景是 ${draft.summary || "未填写业务定位"}。`,
    `编译器将优先使用 ${selectedModelLabel(draft.model)}，并采用 ${draft.executionMode === "copilot" ? "协作副驾" : "自动执行"} 策略。`,
    kbNames.length > 0 ? `知识检索范围锁定为：${kbNames.join("、")}。` : "当前未绑定知识库，运行时不会启用 RAG 检索。",
    toolNames.length > 0 ? `可调用工具白名单为：${toolNames.join("、")}。` : "当前未启用业务工具，只能做文本理解与总结。",
  ];

  const manifest = {
    entry: "runAgent",
    runtimeLang: "typescript-sandbox",
    dependencies: {
      model: draft.model,
      tools: draft.toolIds,
      knowledgeBases: draft.knowledgeBaseIds,
      channels: draft.channels,
    },
    policies: {
      safetyLevel: draft.safetyLevel,
      executionMode: draft.executionMode,
      handoffRule: draft.handoffRule,
      maxToolCalls: draft.executionMode === "auto" ? 4 : 2,
    },
    generatedFrom: {
      version: draft.version,
      specLength: draft.specText.length,
    },
    previewFormat: "mermaid",
  };
  const preview = generateWorkflowPreview(draft, kbs, toolCatalog);

  const code = [
    "export async function runAgent(ctx: WorkflowContext): Promise<WorkflowResult> {",
    `  const spec = ${JSON.stringify(draft.specText)};`,
    `  const role = ${JSON.stringify(draft.name)};`,
    `  const knowledgeBases = ${JSON.stringify(draft.knowledgeBaseIds)};`,
    `  const allowedTools = ${JSON.stringify(draft.toolIds)};`,
    "",
    "  const intent = await ctx.model.classify({",
    "    role,",
    "    input: ctx.input,",
    "    spec,",
    "  });",
    "",
    "  const knowledge = knowledgeBases.length > 0",
    "    ? await ctx.knowledge.search({ input: ctx.input, knowledgeBaseIds: knowledgeBases })",
    "    : null;",
    "",
    "  if (knowledge && knowledge.confidence < 0.7) {",
    `    return ctx.handoff.request({ reason: ${JSON.stringify(draft.handoffRule)} });`,
    "  }",
    "",
    "  const toolResult = intent.requiresTool && allowedTools.length > 0",
    "    ? await ctx.tools.invokeBest({ intent, allowedTools, input: ctx.input })",
    "    : null;",
    "",
    "  return ctx.model.generate({",
    "    role,",
    "    input: ctx.input,",
    "    systemPrompt: ctx.policy.systemPrompt,",
    "    knowledge,",
    "    toolResult,",
    "    outputTemplate: '结论 / 依据 / 下一步建议',",
    "  });",
    "}",
  ].join("\n");

  return {
    code,
    manifest: JSON.stringify(manifest, null, 2),
    preview,
    summary,
    warnings,
    dependencies,
  };
}

function toCompileArtifact(response: CompileResponse): CompileArtifact {
  return {
    code: response.workflowCode,
    manifest: JSON.stringify(response.workflowManifest, null, 2),
    preview: response.workflowPreview,
    summary: response.compileSummary,
    warnings: response.warnings,
    dependencies: response.dependencies,
  };
}

/** Fingerprint of the compile API body — when this diverges from the last backend compile, publish must not reuse old artifacts. */
function compilePayloadDigest(draft: AgentDraft, orgId: string): string {
  return JSON.stringify({
    ...draft,
    orgId,
    skillRefs: draft.skillBindings.filter((item) => item.enabled).map((item) => item.skillCode),
  });
}

function persistPayloadDigest(draft: AgentDraft, publishConfig: PublishConfigDraft, orgId: string): string {
  return JSON.stringify({
    ...draft,
    orgId,
    publishConfig: publishConfig.feishu,
  });
}

function isDraftCompileStaleForPublish(
  draft: AgentDraft,
  orgId: string,
  lastSuccessfulBackendCompileDigest: string | null,
  loadedAgentBaselineDigest: string | null,
): boolean {
  const digest = compilePayloadDigest(draft, orgId);
  if (lastSuccessfulBackendCompileDigest != null) {
    return digest !== lastSuccessfulBackendCompileDigest;
  }
  if (loadedAgentBaselineDigest != null) {
    return digest !== loadedAgentBaselineDigest;
  }
  return false;
}

function selectedModelLabel(model: string, options: BaseModelOption[] = []): string {
  const matched = options.find((option) => option.value === model)?.label;
  if (matched) return matched;
  if (model) return model;
  return "未配置模型";
}

function normalizeSafetyLevel(value?: string): AgentDraft["safetyLevel"] {
  return (value ?? "").toUpperCase() === "STRICT" ? "strict" : "balanced";
}

function normalizeExecutionMode(value?: string): AgentDraft["executionMode"] {
  return (value ?? "").toUpperCase() === "AUTO" ? "auto" : "copilot";
}

function toBackendSafetyLevel(value: AgentDraft["safetyLevel"]): string {
  return value === "strict" ? "STRICT" : "BALANCED";
}

function toBackendExecutionMode(value: AgentDraft["executionMode"]): string {
  return value === "auto" ? "AUTO" : "COPILOT";
}

function deriveStatus(publishedVersionId?: number | null): AgentRecord["status"] {
  return publishedVersionId ? "已发布" : "框架中";
}

function slugifyAgentId(name: string): string {
  const base = name
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 48);
  const core = base || "agent";
  return `${core}-${Date.now().toString().slice(-6)}`;
}

function toPublishConfig(configs?: Record<string, unknown>): PublishConfigDraft {
  const rawFeishu = (configs?.feishu ?? {}) as Record<string, unknown>;
  return {
    feishu: {
      appId: typeof rawFeishu.appId === "string" ? rawFeishu.appId : "",
      appSecret: typeof rawFeishu.appSecret === "string" ? rawFeishu.appSecret : "",
      defaultAgentCode: typeof rawFeishu.defaultAgentCode === "string" ? rawFeishu.defaultAgentCode : "cici",
      pairingCommandHint: typeof rawFeishu.pairingCommandHint === "string" ? rawFeishu.pairingCommandHint : "配对",
      autoSyncSchedulesOnPublish: typeof rawFeishu.autoSyncSchedulesOnPublish === "boolean"
        ? rawFeishu.autoSyncSchedulesOnPublish
        : true,
    },
  };
}

export function resolveAgentDetailTarget(agents: AgentApiRecord[], focusAgentId?: string): string {
  if (focusAgentId && agents.some((item) => item.agentId === focusAgentId)) {
    return focusAgentId;
  }
  return agents[0]?.agentId ?? "";
}

export function applyAgentDetailToList(agents: AgentApiRecord[], detail: AgentApiRecord): AgentApiRecord[] {
  return agents.map((item) => (item.agentId === detail.agentId ? detail : item));
}

export function resolveAgentAfterDelete<T extends { id: string }>(
  agents: T[],
  deletedAgentId: string,
  selectedAgentId: string,
): { nextAgents: T[]; fallbackAgentId: string } {
  const nextAgents = agents.filter((item) => item.id !== deletedAgentId);
  const fallbackAgentId = selectedAgentId && selectedAgentId !== deletedAgentId && nextAgents.some((item) => item.id === selectedAgentId)
    ? selectedAgentId
    : nextAgents[0]?.id ?? "";
  return { nextAgents, fallbackAgentId };
}

export function resolveAgentChannels(itemChannels: string[] | undefined, fallbackChannels: PublishChannelId[]): PublishChannelId[] {
  if (!Array.isArray(itemChannels)) {
    return fallbackChannels;
  }
  return itemChannels.filter((ch): ch is PublishChannelId =>
    CHANNEL_OPTIONS.some((opt) => opt.id === ch),
  );
}

function toAgentRecordFromApi(item: AgentApiRecord, orgId: string, kbs: KnowledgeBase[]): AgentRecord {
  const fallbackDraft = createDraft(orgId, kbs.slice(0, 1).map((kb) => kb.id));
  const model = item.model && item.model.trim() ? item.model : fallbackDraft.model;
  const channels = resolveAgentChannels(item.channels, fallbackDraft.channels);
  const draft: AgentDraft = {
    name: item.name ?? fallbackDraft.name,
    avatarBase64: item.avatarBase64 ?? "",
    summary: item.summary ?? fallbackDraft.summary,
    greeting: item.greeting ?? fallbackDraft.greeting,
    model,
    systemPrompt: item.systemPrompt ?? fallbackDraft.systemPrompt,
    specText: item.specText ?? fallbackDraft.specText,
    channels,
    knowledgeBaseIds: item.knowledgeBaseIds ?? fallbackDraft.knowledgeBaseIds,
    toolIds: item.toolIds ?? fallbackDraft.toolIds,
    handoffRule: item.handoffRule ?? fallbackDraft.handoffRule,
    safetyLevel: normalizeSafetyLevel(item.safetyLevel),
    executionMode: normalizeExecutionMode(item.executionMode),
    version: normalizePublishRemark(item.versionLabel ?? fallbackDraft.version),
    skillBindings: (item.skillBindings ?? []).map((binding) => ({
      ...binding,
      activationCondition: binding.activationCondition ?? "",
    })),
  };
  return {
    id: item.agentId,
    name: item.name ?? draft.name,
    summary: item.summary ?? draft.summary,
    status: deriveStatus(item.publishedVersionId),
    builtin: Boolean(item.builtin),
    lastEdited: "已同步",
    channels: [...draft.channels],
    draft,
    publishConfig: toPublishConfig(item.publishConfigs),
    access: item.access ?? {},
  };
}

export default function AgentBuilderShell({
  kbs,
  orgId,
  token,
  pageMode = "editor",
  focusAgentId,
  onOpenAgent,
  onBackToList,
  onRequireModelConfig,
}: AgentBuilderShellProps) {
  const [library, setLibrary] = useState<AgentRecord[]>([]);
  const [selectedAgentId, setSelectedAgentId] = useState<string>("");
  const [draft, setDraft] = useState<AgentDraft>(() => createDraft(orgId, []));
  const [avatarCropSource, setAvatarCropSource] = useState("");
  const [publishConfig, setPublishConfig] = useState<PublishConfigDraft>(() => createPublishConfigDraft());
  const [searchText, setSearchText] = useState("");
  const [notice, setNoticeText] = useState("自然语言 Spec 已作为主输入，编译结果默认显示流程图预览。");
  const [noticeVisible, setNoticeVisible] = useState(true);
  const [noticeTick, setNoticeTick] = useState(0);
  const [isCompiling, setIsCompiling] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [isPublishing, setIsPublishing] = useState(false);
  const [isDebugging, setIsDebugging] = useState(false);
  const [isLoadingLibrary, setIsLoadingLibrary] = useState(false);
  const [activePublishChannel, setActivePublishChannel] = useState<PublishChannelId>("feishu");
  const [activeCompileTab, setActiveCompileTab] = useState<CompileTab>("preview");
  const [previewMode, setPreviewMode] = useState<PreviewMode>("workflow");
  const [skillDagGraph, setSkillDagGraph] = useState<SkillDependencyGraphView | null>(null);
  const [skillDagLoading, setSkillDagLoading] = useState(false);
  const [skillDagError, setSkillDagError] = useState("");
  const [skillDagVersionNo, setSkillDagVersionNo] = useState<number | null>(null);
  const [executionRecordsFromServer, setExecutionRecordsFromServer] = useState<AgentExecutionRecord[]>([]);
  const [runtimeExecutionsLoading, setRuntimeExecutionsLoading] = useState(false);
  const [runtimeExecutionsError, setRuntimeExecutionsError] = useState<string | null>(null);
  const [triggersCatalog, setTriggersCatalog] = useState<TriggersCatalogPayload | null>(null);
  const [runtimeTriggersLoading, setRuntimeTriggersLoading] = useState(false);
  const [runtimeTriggersError, setRuntimeTriggersError] = useState<string | null>(null);
  const [versionHistoryLoading, setVersionHistoryLoading] = useState(false);
  const [versionHistoryError, setVersionHistoryError] = useState<string | null>(null);
  const [versionHistory, setVersionHistory] = useState<VersionHistoryItem[]>([]);
  const [runtimeScheduleSyncing, setRuntimeScheduleSyncing] = useState(false);
  const [runtimeScheduleActionKey, setRuntimeScheduleActionKey] = useState<string>("");
  const [executionFilter, setExecutionFilter] = useState<"all" | "production" | "try_run">("all");
  const [debugInput, setDebugInput] = useState("请帮我看看这个客户是否适合直接生成报价说明？");
  const [debugTrace, setDebugTrace] = useState<DebugTraceResult | null>(null);
  const [publishedVersionNo, setPublishedVersionNo] = useState<number | null>(null);
  const [latestCompiledVersionNo, setLatestCompiledVersionNo] = useState<number | null>(null);
  const [productionReadiness, setProductionReadiness] = useState<AgentReadinessResult | null>(null);
  const [readinessLoading, setReadinessLoading] = useState(false);
  const [readinessError, setReadinessError] = useState<string | null>(null);
  const [evaluationSuites, setEvaluationSuites] = useState<AgentEvalSuite[]>([]);
  const [evaluationRuns, setEvaluationRuns] = useState<AgentEvalRun[]>([]);
  const [evaluationLoading, setEvaluationLoading] = useState(false);
  const [evaluationError, setEvaluationError] = useState<string | null>(null);
  const [evaluationInput, setEvaluationInput] = useState("请用一句话回答当前 Agent 的职责。");
  const [evaluationExpectedText, setEvaluationExpectedText] = useState("");
  const [publishReadyFromCompile, setPublishReadyFromCompile] = useState(false);
  const [lastSuccessfulBackendCompileDigest, setLastSuccessfulBackendCompileDigest] = useState<string | null>(null);
  const [loadedAgentBaselineDigest, setLoadedAgentBaselineDigest] = useState<string | null>(null);
  const [persistedDraftDigest, setPersistedDraftDigest] = useState<string | null>(null);
  const [compileArtifact, setCompileArtifact] = useState<CompileArtifact>(() => generateCompileArtifact(draft, kbs, []));
  const [modelOptions, setModelOptions] = useState<BaseModelOption[]>([]);
  const [toolCatalog, setToolCatalog] = useState<ToolCatalogItem[]>([]);
  const [mcpServers, setMcpServers] = useState<McpServerSummary[]>([]);
  const [mcpToolsByServer, setMcpToolsByServer] = useState<Record<number, McpPickerTool[]>>({});
  const [mcpServerLoading, setMcpServerLoading] = useState<Record<number, boolean>>({});
  const [pickerToolTab, setPickerToolTab] = useState<"tool" | "mcp">("tool");
  const [expandedMcpServerIds, setExpandedMcpServerIds] = useState<number[]>([]);
  const [skillCatalog, setSkillCatalog] = useState<SkillCatalogItem[]>([]);
  const [pickerOpen, setPickerOpen] = useState<null | "skill" | "kb" | "tool">(null);
  const [pickerSelection, setPickerSelection] = useState<string[]>([]);
  const [openApiDocsOpen, setOpenApiDocsOpen] = useState(false);
  const [openApiKeysOpen, setOpenApiKeysOpen] = useState(false);
  const [accessDialogOpen, setAccessDialogOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<AgentRecord | null>(null);
  const [isDeletingAgent, setIsDeletingAgent] = useState(false);
  const skillDagRequestIdRef = useRef(0);

  const setNotice = (message: string) => {
    setNoticeText(message);
    setNoticeVisible(true);
    setNoticeTick((current) => current + 1);
  };

  const beginAvatarCrop = useCallback(async (file: File) => {
    try {
      const dataUrl = await readAvatarFileAsDataUrl(file);
      setAvatarCropSource(dataUrl);
      setNotice("");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "头像处理失败，请稍后重试");
    }
  }, []);

  useEffect(() => {
    if (!notice || !notice.trim()) {
      setNoticeVisible(false);
      return;
    }
    const timer = window.setTimeout(() => setNoticeVisible(false), 2600);
    return () => window.clearTimeout(timer);
  }, [noticeTick, notice]);

  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    const loadFromBackend = async () => {
      setIsLoadingLibrary(true);
      try {
        const listRes = await fetch("/agents", {
          method: "GET",
          headers: { Authorization: `Bearer ${token}` },
        });
        const { body: listBody } = await safeFetchJson<AgentApiRecord[]>(listRes);
        if (!listRes.ok || !listBody?.success || !Array.isArray(listBody.data) || listBody.data.length === 0) {
          return;
        }

        const detailAgentId = resolveAgentDetailTarget(listBody.data, focusAgentId);
        if (!detailAgentId) return;
        const detailRes = await fetch(`/agents/${encodeURIComponent(detailAgentId)}`, {
          method: "GET",
          headers: { Authorization: `Bearer ${token}` },
        });
        const { body: detailBody } = await safeFetchJson<AgentApiRecord>(detailRes);
        if (!detailRes.ok || !detailBody?.success || !detailBody.data) {
          return;
        }
        if (cancelled) return;

        const hydratedList = applyAgentDetailToList(listBody.data, detailBody.data);
        const nextLibrary = hydratedList.map((item) => toAgentRecordFromApi(item, orgId, kbs));
        const first = nextLibrary[0];
        const preferred = nextLibrary.find((item) => item.id === detailAgentId) ?? first;
        const skillsRes = await fetch("/skills", {
          method: "GET",
          headers: { Authorization: `Bearer ${token}` },
        });
        const { body: skillsBody } = await safeFetchJson<SkillCatalogItem[]>(skillsRes);
        if (skillsRes.ok && skillsBody?.success && Array.isArray(skillsBody.data)) {
          setSkillCatalog(skillsBody.data.filter((item) => item.enabled));
        }
        setLibrary(nextLibrary);
        setSelectedAgentId(preferred.id);
        setDraft(cloneDraft(preferred.draft));
        setPublishConfig(clonePublishConfigDraft(preferred.publishConfig));
        setLoadedAgentBaselineDigest(compilePayloadDigest(preferred.draft, orgId));
        setPersistedDraftDigest(persistPayloadDigest(preferred.draft, preferred.publishConfig, orgId));
        setLastSuccessfulBackendCompileDigest(null);
        setPublishReadyFromCompile(false);
        resetProductionGateState();
        setCompileArtifact(generateCompileArtifact(preferred.draft, kbs, toolCatalog));
        setActiveCompileTab("preview");
        setActivePublishChannel(preferred.draft.channels.includes("feishu") ? "feishu" : preferred.draft.channels[0] ?? "feishu");
        setNotice("已从后端加载 Agent 草稿。你现在的修改会保存到数据库。");
      } catch {
        setNotice("加载 Agent 失败，请检查后端接口或登录权限。");
      } finally {
        if (!cancelled) setIsLoadingLibrary(false);
      }
    };
    void loadFromBackend();
    return () => {
      cancelled = true;
    };
  }, [focusAgentId, kbs, orgId, token]);

  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    const loadToolCatalog = async () => {
      try {
        const res = await fetch("/tools", {
          method: "GET",
          headers: { Authorization: `Bearer ${token}` },
        });
        const { body } = await safeFetchJson<Array<{
          toolName: string;
          displayName?: string;
          description?: string;
          riskLevel?: string;
        }>>(res);
        if (!res.ok || !body?.success || !Array.isArray(body.data)) {
          return;
        }
        const next = body.data
          .map((item) => ({
            id: item.toolName,
            name: item.displayName || item.toolName,
            description: item.description || "",
            level: item.riskLevel || "未知风险",
          }))
          .filter((item) => item.id);
        if (!cancelled) {
          setToolCatalog(next);
        }
      } catch {
        if (!cancelled) setToolCatalog([]);
      }
    };
    void loadToolCatalog();
    return () => {
      cancelled = true;
    };
  }, [token]);

  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    const loadMcpServers = async () => {
      try {
        const res = await fetch("/mcp-servers", {
          method: "GET",
          headers: { Authorization: `Bearer ${token}` },
        });
        const { body } = await safeFetchJson<Array<{ id: number; name: string; enabled?: boolean; toolCacheCount?: number }>>(res);
        if (!res.ok || !body?.success || !Array.isArray(body.data)) {
          if (!cancelled) setMcpServers([]);
          return;
        }
        if (!cancelled) {
          setMcpServers(body.data.map((item) => ({
            id: item.id,
            name: item.name || `MCP-${item.id}`,
            enabled: item.enabled !== false,
            toolCacheCount: Number.isFinite(item.toolCacheCount) ? Number(item.toolCacheCount) : 0,
          })));
        }
      } catch {
        if (!cancelled) setMcpServers([]);
      }
    };
    void loadMcpServers();
    return () => {
      cancelled = true;
    };
  }, [token]);

  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    const loadAgentBaseModels = async () => {
      try {
        const res = await fetch("/models/agent/base-models", {
          method: "GET",
          headers: { Authorization: `Bearer ${token}` },
        });
        const { body } = await safeFetchJson<Array<{ providerCode: string; providerName: string; modelName: string; displayLabel?: string }>>(res);
        if (!res.ok || !body?.success || !Array.isArray(body.data) || body.data.length === 0) {
          return;
        }
        const mapped = body.data
          .map((item) => ({
            value: item.modelName,
            label: item.displayLabel || `${item.modelName} · ${item.providerName}`,
            note: item.providerName,
          }))
          .filter((item) => item.value && item.label);
        const deduped = Array.from(new Map(mapped.map((item) => [item.value, item])).values());
        if (!cancelled && deduped.length > 0) {
          setModelOptions(deduped);
          setDraft((current) => (deduped.some((option) => option.value === current.model)
            ? current
            : { ...current, model: deduped[0].value }));
        }
      } catch {
        if (!cancelled) setModelOptions([]);
      }
    };
    void loadAgentBaseModels();
    return () => {
      cancelled = true;
    };
  }, [token]);

  useEffect(() => {
    if (!selectedAgentId) return;
    setLibrary((current) =>
      current.map((item) =>
        item.id === selectedAgentId
          ? {
              ...item,
              name: draft.name || "未命名 Agent",
              summary: draft.summary || "待补充业务定位。",
              channels: [...draft.channels],
              lastEdited: "刚刚",
              draft: cloneDraft(draft),
              publishConfig: clonePublishConfigDraft(publishConfig),
            }
          : item,
      ),
    );
  }, [draft, publishConfig, selectedAgentId]);

  const filteredLibrary = useMemo(() => {
    const keyword = searchText.trim().toLowerCase();
    if (!keyword) return library;
    return library.filter((item) => `${item.name}${item.summary}`.toLowerCase().includes(keyword));
  }, [library, searchText]);

  const selectedAgent = library.find((item) => item.id === selectedAgentId) ?? null;
  const selectedAgentAccess = selectedAgent?.access ?? {};
  const selectedAgentPermissions = selectedAgentAccess.permissions ?? [];
  const canEditSelectedAgent = Boolean(selectedAgentAccess.canEdit);
  const canPublishSelectedAgent = selectedAgentPermissions.includes("PUBLISH");
  const selectedModel = modelOptions.find((option) => option.value === draft.model);
  const openApiBaseUrl = `${window.location.origin}/openapi/v1`;
  const readinessCount = [draft.name, draft.specText, draft.channels.length > 0, draft.knowledgeBaseIds.length > 0, draft.toolIds.length > 0].filter(Boolean).length;
  const targetReadinessVersionNo = latestCompiledVersionNo ?? publishedVersionNo;
  const activeEvaluationSuite = evaluationSuites[0] ?? null;
  const latestEvaluationRun = evaluationRuns[0] ?? null;
  const readinessBlockingChecks = productionReadiness?.checks?.filter((item) => item.severity === "blocker" && item.status !== "passed") ?? [];
  const readinessWarningChecks = productionReadiness?.checks?.filter((item) => item.severity === "warning" && item.status !== "passed") ?? [];

  const filteredExecutionRecords = useMemo(() => {
    const rows = executionRecordsFromServer.filter((row) => row.agentId === selectedAgentId);
    if (executionFilter === "all") return rows;
    if (executionFilter === "try_run") return rows.filter((row) => row.source === "try_run");
    return rows.filter((row) => row.source !== "try_run");
  }, [executionFilter, executionRecordsFromServer, selectedAgentId]);
  const executionRecordCounts = useMemo(() => {
    const rows = executionRecordsFromServer.filter((row) => row.agentId === selectedAgentId);
    const tryRun = rows.filter((row) => row.source === "try_run").length;
    return {
      all: rows.length,
      production: rows.length - tryRun,
      try_run: tryRun,
    };
  }, [executionRecordsFromServer, selectedAgentId]);

  const openTriggersFromGraph = useCallback(() => {
    setActiveCompileTab("triggers");
    setNotice("已切换到「触发与调度」：入口渠道与定时/调度说明与流程图 START 节点对齐。");
  }, []);

  const loadAgentSkillDag = useCallback(async (
    versionNo: number | null = null,
    agentIdOverride?: string,
  ) => {
    const agentId = agentIdOverride ?? selectedAgentId;
    if (!token || !agentId || agentId === "draft") {
      setSkillDagGraph(null);
      setSkillDagError("请先保存并编译 Agent，再查看 Skill 依赖。");
      return;
    }
    const requestId = ++skillDagRequestIdRef.current;
    setSkillDagLoading(true);
    setSkillDagError("");
    try {
      const response = await fetch(buildAgentSkillDagUrl(agentId, versionNo), {
        headers: { Authorization: `Bearer ${token}` },
      });
      const { body } = await safeFetchJson<SkillDependencyGraphView>(response);
      if (!response.ok || !body?.success || !body.data) {
        throw new Error(body?.message ?? `HTTP ${response.status}`);
      }
      if (!isLatestSkillDagRequest(requestId, skillDagRequestIdRef.current)) return;
      setSkillDagGraph(body.data);
    } catch (error) {
      if (!isLatestSkillDagRequest(requestId, skillDagRequestIdRef.current)) return;
      setSkillDagGraph(null);
      setSkillDagError(`Skill 依赖加载失败：${error instanceof Error ? error.message : String(error)}`);
    } finally {
      if (isLatestSkillDagRequest(requestId, skillDagRequestIdRef.current)) setSkillDagLoading(false);
    }
  }, [selectedAgentId, token]);

  const openSkillDagVersion = useCallback((versionNo: number) => {
    setSkillDagVersionNo(versionNo);
    setSkillDagGraph(null);
    setSkillDagError("");
    setPreviewMode("skill-dag");
    setActiveCompileTab("preview");
    void loadAgentSkillDag(versionNo);
  }, [loadAgentSkillDag]);

  const loadRuntimeExecutions = useCallback(async () => {
    if (!token || !selectedAgentId) return;
    setRuntimeExecutionsLoading(true);
    setRuntimeExecutionsError(null);
    try {
      const res = await fetch(`/agents/${encodeURIComponent(selectedAgentId)}/runtime/executions?limit=80`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      const { body } = await safeFetchJson<ApiExecutionRow[]>(res);
      if (!res.ok || !body?.success || !Array.isArray(body.data)) {
        throw new Error(body?.message ?? `HTTP ${res.status}`);
      }
      setExecutionRecordsFromServer(body.data.map(mapApiExecutionRow));
    } catch (error) {
      setExecutionRecordsFromServer([]);
      setRuntimeExecutionsError(error instanceof Error ? error.message : String(error));
    } finally {
      setRuntimeExecutionsLoading(false);
    }
  }, [selectedAgentId, token]);

  const loadRuntimeTriggers = useCallback(async () => {
    if (!token || !selectedAgentId) return;
    setRuntimeTriggersLoading(true);
    setRuntimeTriggersError(null);
    try {
      const res = await fetch(`/agents/${encodeURIComponent(selectedAgentId)}/runtime/triggers`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      const { body } = await safeFetchJson<TriggersCatalogPayload>(res);
      if (!res.ok || !body?.success || !body.data) {
        throw new Error(body?.message ?? `HTTP ${res.status}`);
      }
      setTriggersCatalog(body.data);
    } catch (error) {
      setTriggersCatalog(null);
      setRuntimeTriggersError(error instanceof Error ? error.message : String(error));
    } finally {
      setRuntimeTriggersLoading(false);
    }
  }, [selectedAgentId, token]);

  const loadPublishedVersionNo = useCallback(async () => {
    if (!token || !selectedAgentId) return;
    try {
      const versionsRes = await fetch(`/agents/${encodeURIComponent(selectedAgentId)}/versions`, {
        method: "GET",
        headers: { Authorization: `Bearer ${token}` },
      });
      const { body: versionsBody } = await safeFetchJson<Array<{ versionNo: number; publishStatus?: string }>>(versionsRes);
      if (!versionsRes.ok || !versionsBody?.success || !Array.isArray(versionsBody.data)) {
        setPublishedVersionNo(null);
        setLatestCompiledVersionNo(null);
        return;
      }
      setLatestCompiledVersionNo(versionsBody.data[0]?.versionNo ?? null);
      const published = versionsBody.data.find((item) => (item.publishStatus ?? "").toUpperCase() === "PUBLISHED");
      setPublishedVersionNo(published?.versionNo ?? null);
    } catch {
      setPublishedVersionNo(null);
      setLatestCompiledVersionNo(null);
    }
  }, [selectedAgentId, token]);

  const loadVersionHistory = useCallback(async () => {
    if (!token || !selectedAgentId) return;
    setVersionHistoryLoading(true);
    setVersionHistoryError(null);
    try {
      const versionsRes = await fetch(`/agents/${encodeURIComponent(selectedAgentId)}/versions`, {
        method: "GET",
        headers: { Authorization: `Bearer ${token}` },
      });
      const { body: versionsBody } = await safeFetchJson<VersionHistoryItem[]>(versionsRes);
      if (!versionsRes.ok || !versionsBody?.success || !Array.isArray(versionsBody.data)) {
        throw new Error(versionsBody?.message ?? `HTTP ${versionsRes.status}`);
      }
      setLatestCompiledVersionNo(versionsBody.data[0]?.versionNo ?? null);
      setVersionHistory(keepRecentVersionHistory(versionsBody.data, 10));
    } catch (error) {
      setVersionHistory([]);
      setVersionHistoryError(error instanceof Error ? error.message : String(error));
    } finally {
      setVersionHistoryLoading(false);
    }
  }, [selectedAgentId, token]);

  const loadProductionReadiness = useCallback(async (versionNoOverride?: number | null) => {
    if (!token || !selectedAgentId) return null;
    const versionNo = versionNoOverride ?? targetReadinessVersionNo;
    if (versionNo == null) {
      setProductionReadiness(null);
      setReadinessError("暂无可检查的编译版本，请先完成智能体编译。");
      return null;
    }
    setReadinessLoading(true);
    setReadinessError(null);
    try {
      const readinessRes = await fetch(`/agents/${encodeURIComponent(selectedAgentId)}/readiness?versionNo=${encodeURIComponent(String(versionNo))}`, {
        method: "GET",
        headers: { Authorization: `Bearer ${token}` },
      });
      const { body } = await safeFetchJson<AgentReadinessResult>(readinessRes);
      if (!readinessRes.ok || !body?.success || !body.data) {
        throw new Error(body?.message ?? `HTTP ${readinessRes.status}`);
      }
      setProductionReadiness(body.data);
      return body.data;
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      setProductionReadiness(null);
      setReadinessError(message);
      return null;
    } finally {
      setReadinessLoading(false);
    }
  }, [selectedAgentId, targetReadinessVersionNo, token]);

  const loadEvaluationSuites = useCallback(async () => {
    if (!token || !selectedAgentId) return;
    setEvaluationLoading(true);
    setEvaluationError(null);
    try {
      const suitesRes = await fetch(`/agents/${encodeURIComponent(selectedAgentId)}/evaluation/suites`, {
        method: "GET",
        headers: { Authorization: `Bearer ${token}` },
      });
      const { body } = await safeFetchJson<AgentEvalSuite[]>(suitesRes);
      if (!suitesRes.ok || !body?.success || !Array.isArray(body.data)) {
        throw new Error(body?.message ?? `HTTP ${suitesRes.status}`);
      }
      setEvaluationSuites(body.data);
      const firstSuite = body.data[0];
      if (!firstSuite) {
        setEvaluationRuns([]);
        return;
      }
      const runsRes = await fetch(`/agents/${encodeURIComponent(selectedAgentId)}/evaluation/suites/${firstSuite.id}/runs`, {
        method: "GET",
        headers: { Authorization: `Bearer ${token}` },
      });
      const { body: runsBody } = await safeFetchJson<AgentEvalRun[]>(runsRes);
      if (runsRes.ok && runsBody?.success && Array.isArray(runsBody.data)) {
        setEvaluationRuns(runsBody.data);
      } else {
        setEvaluationRuns([]);
      }
    } catch (error) {
      setEvaluationSuites([]);
      setEvaluationRuns([]);
      setEvaluationError(error instanceof Error ? error.message : String(error));
    } finally {
      setEvaluationLoading(false);
    }
  }, [selectedAgentId, token]);

  const syncRuntimeSchedules = useCallback(async () => {
    if (!token || !selectedAgentId) return;
    setRuntimeScheduleSyncing(true);
    try {
      const res = await fetch(`/agents/${encodeURIComponent(selectedAgentId)}/runtime/schedules/sync`, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}` },
      });
      const { body } = await safeFetchJson<Map<string, unknown>>(res);
      if (!res.ok || !body?.success) {
        throw new Error(body?.message ?? `HTTP ${res.status}`);
      }
      const synced = Number((body.data as { synced?: number } | undefined)?.synced ?? 0);
      const versionNo = Number((body.data as { sourceVersionNo?: number } | undefined)?.sourceVersionNo ?? 0);
      setNotice(synced > 0
        ? `已同步 ${synced} 条调度触发器（来源 v${versionNo || "?"}）。`
        : "已完成调度同步，但未识别到可执行的时间语义。");
      void loadRuntimeTriggers();
    } catch (error) {
      setNotice(`同步调度失败：${error instanceof Error ? error.message : String(error)}`);
    } finally {
      setRuntimeScheduleSyncing(false);
    }
  }, [loadRuntimeTriggers, selectedAgentId, token]);

  const updateRuntimeScheduleEnabled = useCallback(async (triggerKey: string, enabled: boolean) => {
    if (!token || !selectedAgentId || !triggerKey) return;
    setRuntimeScheduleActionKey(`${triggerKey}:${enabled ? "enable" : "disable"}`);
    try {
      const res = await fetch(`/agents/${encodeURIComponent(selectedAgentId)}/runtime/schedules/${encodeURIComponent(triggerKey)}`, {
        method: "PUT",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({ enabled }),
      });
      const { body } = await safeFetchJson<Map<string, unknown>>(res);
      if (!res.ok || !body?.success) {
        throw new Error(body?.message ?? `HTTP ${res.status}`);
      }
      setNotice(enabled ? "已启用触发器。" : "已停用触发器。");
      void loadRuntimeTriggers();
    } catch (error) {
      setNotice(`更新触发器失败：${error instanceof Error ? error.message : String(error)}`);
    } finally {
      setRuntimeScheduleActionKey("");
    }
  }, [loadRuntimeTriggers, selectedAgentId, token]);

  const runRuntimeScheduleNow = useCallback(async (triggerKey: string) => {
    if (!token || !selectedAgentId || !triggerKey) return;
    setRuntimeScheduleActionKey(`${triggerKey}:run`);
    try {
      const res = await fetch(`/agents/${encodeURIComponent(selectedAgentId)}/runtime/schedules/run-now`, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({ triggerKey }),
      });
      const { body } = await safeFetchJson<Map<string, unknown>>(res);
      if (!res.ok || !body?.success) {
        throw new Error(body?.message ?? `HTTP ${res.status}`);
      }
      setNotice("已加入立即执行队列（会写入执行记录）。");
      void loadRuntimeExecutions();
    } catch (error) {
      setNotice(`立即执行失败：${error instanceof Error ? error.message : String(error)}`);
    } finally {
      setRuntimeScheduleActionKey("");
    }
  }, [loadRuntimeExecutions, selectedAgentId, token]);

  useEffect(() => {
    if (!token || !selectedAgentId) return;
    void loadPublishedVersionNo();
  }, [loadPublishedVersionNo, selectedAgentId, token]);

  useEffect(() => {
    skillDagRequestIdRef.current += 1;
    setSkillDagGraph(null);
    setSkillDagError("");
    setSkillDagLoading(false);
    setSkillDagVersionNo(null);
    setPreviewMode("workflow");
  }, [selectedAgentId]);

  useEffect(() => {
    if (!token || !selectedAgentId || activeCompileTab !== "preview" || previewMode !== "skill-dag") return;
    if (skillDagGraph || skillDagLoading || skillDagError) return;
    void loadAgentSkillDag(skillDagVersionNo);
  }, [
    activeCompileTab,
    loadAgentSkillDag,
    previewMode,
    selectedAgentId,
    skillDagError,
    skillDagGraph,
    skillDagLoading,
    skillDagVersionNo,
    token,
  ]);

  useEffect(() => {
    if (!token || !selectedAgentId) return;
    if (activeCompileTab === "executions") {
      void loadRuntimeExecutions();
    } else if (activeCompileTab === "triggers") {
      void loadRuntimeTriggers();
    } else if (activeCompileTab === "history") {
      void loadVersionHistory();
    }
  }, [activeCompileTab, loadRuntimeExecutions, loadRuntimeTriggers, loadVersionHistory, selectedAgentId, token]);

  useEffect(() => {
    if (!token || !selectedAgentId || activeCompileTab !== "evaluation") return;
    void loadProductionReadiness();
    void loadEvaluationSuites();
  }, [activeCompileTab, loadEvaluationSuites, loadProductionReadiness, selectedAgentId, token]);

  const updateDraft = <K extends keyof AgentDraft>(key: K, value: AgentDraft[K]) => {
    setDraft((current) => ({ ...current, [key]: value }));
  };

  const toggleCollectionValue = (field: "channels" | "knowledgeBaseIds" | "toolIds", value: string | number) => {
    setDraft((current) => {
      const currentValues = current[field];
      const nextValues = currentValues.includes(value as never)
        ? currentValues.filter((item) => item !== value)
        : [...currentValues, value];
      return { ...current, [field]: nextValues };
    });
  };

  const updateFeishuPublishConfig = <K extends keyof FeishuPublishConfig>(key: K, value: FeishuPublishConfig[K]) => {
    setPublishConfig((current) => ({
      ...current,
      feishu: {
        ...current.feishu,
        [key]: value,
      },
    }));
  };

  const updateSkillBinding = (skillCode: string, patch: Partial<AgentSkillBindingDraft>) => {
    setDraft((current) => ({
      ...current,
      skillBindings: current.skillBindings.map((item) => (item.skillCode === skillCode ? { ...item, ...patch } : item)),
    }));
  };

  const removeSkillBinding = (skillCode: string) => {
    setDraft((current) => ({
      ...current,
      skillBindings: current.skillBindings.filter((item) => item.skillCode !== skillCode),
    }));
  };

  const openPicker = (type: "skill" | "kb" | "tool") => {
    if (type === "skill") {
      setPickerSelection(draft.skillBindings.map((item) => item.skillCode));
    } else if (type === "kb") {
      setPickerSelection(draft.knowledgeBaseIds.map((id) => String(id)));
    } else {
      setPickerSelection([...draft.toolIds]);
      setPickerToolTab("tool");
      setExpandedMcpServerIds([]);
    }
    setPickerOpen(type);
  };

  const togglePickerItem = (key: string, checked: boolean) => {
    setPickerSelection((prev) => {
      if (checked) {
        return prev.includes(key) ? prev : [...prev, key];
      }
      return prev.filter((item) => item !== key);
    });
  };

  const confirmPicker = () => {
    if (pickerOpen === "skill") {
      setDraft((current) => {
        const existingByCode = new Map(current.skillBindings.map((item) => [item.skillCode, item]));
        const nextBindings: AgentSkillBindingDraft[] = [];
        pickerSelection.forEach((code, index) => {
          const existing = existingByCode.get(code);
          if (existing) {
            nextBindings.push({ ...existing, enabled: true });
            return;
          }
          const catalog = skillCatalog.find((item) => item.skillCode === code);
          if (!catalog) return;
          nextBindings.push({
            skillId: catalog.id,
            skillCode: catalog.skillCode,
            skillName: catalog.name,
            riskLevel: catalog.riskLevel,
            activationMode: "always-on",
            activationCondition: "",
            priority: (index + 1) * 10,
            enabled: true,
          });
        });
        return { ...current, skillBindings: nextBindings };
      });
    } else if (pickerOpen === "kb") {
      const nextIds = pickerSelection
        .map((value) => Number(value))
        .filter((value) => Number.isFinite(value));
      setDraft((current) => ({ ...current, knowledgeBaseIds: nextIds }));
    } else if (pickerOpen === "tool") {
      setDraft((current) => ({ ...current, toolIds: [...pickerSelection] }));
    }
    setPickerOpen(null);
  };

  const pickerTitle = pickerOpen === "skill"
    ? "添加 Skill"
    : pickerOpen === "kb"
      ? "挂载知识库"
      : pickerOpen === "tool"
        ? "添加工具"
        : "";

  const pickerIntro = pickerOpen === "skill"
    ? "选择要挂到当前 Agent 的技能，支持多选；已挂载的会保留原有激活模式。"
    : pickerOpen === "kb"
      ? "选择要作为检索上下文的知识库，可多选。"
      : pickerOpen === "tool"
        ? "选择允许当前 Agent 调用的工具，可多选。"
        : "";

  const resetProductionGateState = () => {
    setProductionReadiness(null);
    setReadinessError(null);
    setEvaluationSuites([]);
    setEvaluationRuns([]);
    setEvaluationError(null);
    setLatestCompiledVersionNo(null);
  };

  type PickerItem = { key: string; title: string; subtitle?: string; tag?: string };
  const loadMcpTools = async (server: McpServerSummary) => {
    if (!token || !server.enabled || mcpToolsByServer[server.id] || mcpServerLoading[server.id]) return;
    setMcpServerLoading((current) => ({ ...current, [server.id]: true }));
    try {
      const res = await fetch(`/mcp-servers/${server.id}/tools`, {
        method: "GET",
        headers: { Authorization: `Bearer ${token}` },
      });
      const { body } = await safeFetchJson<McpToolCachePayload>(res);
      const toolRows = body?.data?.tools;
      if (!res.ok || !body?.success || !Array.isArray(toolRows)) {
        setMcpToolsByServer((current) => ({ ...current, [server.id]: [] }));
        return;
      }
      const nextTools = toolRows
        .filter((item) => item.name && item.name.trim())
        .map((item) => {
          const toolName = item.name.trim();
          const catalog = toolCatalog.find((entry) => entry.id === toolName || entry.name === toolName);
          return {
            id: catalog?.id ?? toolName,
            name: catalog?.name ?? toolName,
            description: item.description || catalog?.description || "",
            level: catalog?.level ?? "MCP",
          };
        });
      setMcpToolsByServer((current) => ({ ...current, [server.id]: nextTools }));
    } catch {
      setMcpToolsByServer((current) => ({ ...current, [server.id]: [] }));
    } finally {
      setMcpServerLoading((current) => ({ ...current, [server.id]: false }));
    }
  };

  const toggleMcpServerExpanded = (server: McpServerSummary) => {
    const willExpand = !expandedMcpServerIds.includes(server.id);
    setExpandedMcpServerIds((current) => (
      willExpand ? [...current, server.id] : current.filter((id) => id !== server.id)
    ));
    if (willExpand) {
      void loadMcpTools(server);
    }
  };

  const pickerItems: PickerItem[] = pickerOpen === "skill"
    ? skillCatalog.map((skill) => ({
        key: skill.skillCode,
        title: skill.name,
        subtitle: skill.skillCode,
        tag: skill.riskLevel,
      }))
    : pickerOpen === "kb"
      ? kbs.map((kb) => ({
          key: String(kb.id),
          title: kb.name,
          subtitle: kb.description || "已接入知识库，可作为检索上下文。",
        }))
      : pickerOpen === "tool"
        ? toolCatalog.map((tool) => ({
            key: tool.id,
            title: tool.name,
            subtitle: tool.description,
            tag: tool.level,
          }))
        : [];

  useEffect(() => {
    if (!focusAgentId || library.length === 0 || pageMode !== "editor") return;
    if (selectedAgentId === focusAgentId) return;
    if (library.some((item) => item.id === focusAgentId)) {
      void selectAgent(focusAgentId);
    }
  }, [focusAgentId, library, pageMode, selectedAgentId]);

  const selectAgent = async (agentId: string) => {
    if (pageMode === "list" && onOpenAgent) {
      onOpenAgent(agentId);
      return;
    }
    const target = library.find((item) => item.id === agentId);
    if (!target) return;
    try {
      const response = await fetch(`/agents/${encodeURIComponent(agentId)}`, {
        method: "GET",
        headers: { Authorization: `Bearer ${token}` },
      });
      const { body } = await safeFetchJson<AgentApiRecord>(response);
      if (!response.ok || !body?.success || !body.data) {
        throw new Error(body?.message ?? `HTTP ${response.status}`);
      }
      const refreshed = toAgentRecordFromApi(body.data, orgId, kbs);
      setLibrary((current) => current.map((item) => (item.id === agentId ? refreshed : item)));
      setSelectedAgentId(agentId);
      setDraft(cloneDraft(refreshed.draft));
      setPublishConfig(clonePublishConfigDraft(refreshed.publishConfig));
      setLoadedAgentBaselineDigest(compilePayloadDigest(refreshed.draft, orgId));
      setPersistedDraftDigest(persistPayloadDigest(refreshed.draft, refreshed.publishConfig, orgId));
      setLastSuccessfulBackendCompileDigest(null);
      setPublishReadyFromCompile(false);
      resetProductionGateState();
      setTriggersCatalog(null);
      setExecutionRecordsFromServer([]);
      setVersionHistory([]);
      setVersionHistoryError(null);
      setRuntimeExecutionsError(null);
      setRuntimeTriggersError(null);
      setCompileArtifact(generateCompileArtifact(refreshed.draft, kbs, toolCatalog));
      setActivePublishChannel(refreshed.draft.channels.includes("feishu") ? "feishu" : refreshed.draft.channels[0] ?? "feishu");
      setActiveCompileTab("preview");
      setDebugTrace(null);
      setNotice(`已切换到「${refreshed.name}」，并同步后端最新草稿。`);
      return;
    } catch {
      // Fall back to local snapshot for smoother UX.
    }
    setSelectedAgentId(agentId);
    setDraft(cloneDraft(target.draft));
    setPublishConfig(clonePublishConfigDraft(target.publishConfig));
    setLoadedAgentBaselineDigest(compilePayloadDigest(target.draft, orgId));
    setPersistedDraftDigest(persistPayloadDigest(target.draft, target.publishConfig, orgId));
    setLastSuccessfulBackendCompileDigest(null);
    setPublishReadyFromCompile(false);
    resetProductionGateState();
    setTriggersCatalog(null);
    setExecutionRecordsFromServer([]);
    setVersionHistory([]);
    setVersionHistoryError(null);
    setRuntimeExecutionsError(null);
    setRuntimeTriggersError(null);
    setCompileArtifact(generateCompileArtifact(target.draft, kbs, toolCatalog));
    setActivePublishChannel(target.draft.channels.includes("feishu") ? "feishu" : target.draft.channels[0] ?? "feishu");
    setActiveCompileTab("preview");
    setDebugTrace(null);
    setNotice(`已切换到「${target.name}」。可以继续补充 Spec，然后重新编译代码与流程图预览。`);
  };

  const createAgent = async () => {
    const nextDraft = createDraft(orgId, kbs.slice(0, 1).map((item) => item.id));
    const creationModel = resolveAgentCreationModel(nextDraft.model, modelOptions);
    nextDraft.model = creationModel.model;
    if (creationModel.requiresModelConfig) {
      if (onRequireModelConfig) {
        onRequireModelConfig(MODEL_CONFIG_REQUIRED_NOTICE);
      } else {
        setNotice(MODEL_CONFIG_REQUIRED_NOTICE);
      }
      return;
    }
    const proposedName = "未命名 Agent";
    const agentId = slugifyAgentId(proposedName);
    try {
      const response = await fetch("/agents", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          agentId,
          name: proposedName,
          avatarBase64: nextDraft.avatarBase64,
          summary: nextDraft.summary,
          greeting: nextDraft.greeting,
          model: nextDraft.model,
          systemPrompt: nextDraft.systemPrompt,
          handoffRule: nextDraft.handoffRule,
          safetyLevel: toBackendSafetyLevel(nextDraft.safetyLevel),
          executionMode: toBackendExecutionMode(nextDraft.executionMode),
          versionLabel: nextDraft.version,
          enabled: true,
          specText: nextDraft.specText,
          knowledgeBaseIds: nextDraft.knowledgeBaseIds,
          toolIds: nextDraft.toolIds,
          channels: nextDraft.channels,
          publishConfigs: { feishu: createPublishConfigDraft().feishu },
        }),
      });
      const { body } = await safeFetchJson<AgentApiRecord>(response);
      if (!response.ok || !body?.success || !body.data) {
        throw new Error(body?.message ?? `HTTP ${response.status}`);
      }
      const nextAgent = toAgentRecordFromApi(body.data, orgId, kbs);
      setLibrary((current) => [nextAgent, ...current]);
      if (pageMode === "list" && onOpenAgent) {
        onOpenAgent(nextAgent.id);
        setNotice("已创建新的 Agent。");
        return;
      }
      setSelectedAgentId(nextAgent.id);
      setDraft(cloneDraft(nextAgent.draft));
      setPublishConfig(clonePublishConfigDraft(nextAgent.publishConfig));
      setLoadedAgentBaselineDigest(compilePayloadDigest(nextAgent.draft, orgId));
      setPersistedDraftDigest(persistPayloadDigest(nextAgent.draft, nextAgent.publishConfig, orgId));
      setLastSuccessfulBackendCompileDigest(null);
      setPublishReadyFromCompile(false);
      resetProductionGateState();
      setTriggersCatalog(null);
      setExecutionRecordsFromServer([]);
      setRuntimeExecutionsError(null);
      setRuntimeTriggersError(null);
      setCompileArtifact(generateCompileArtifact(nextAgent.draft, kbs, toolCatalog));
      setActivePublishChannel("feishu");
      setActiveCompileTab("preview");
      setDebugTrace(null);
      setNotice("已创建新的 Agent，并完成后端落库。");
      return;
    } catch (error) {
      setNotice(`创建失败：${error instanceof Error ? error.message : String(error)}`);
    }
  };

  const resetEditorAfterDeletedLastAgent = () => {
    const fallbackDraft = createDraft(orgId, kbs.slice(0, 1).map((item) => item.id));
    setSelectedAgentId("");
    setDraft(fallbackDraft);
    setPublishConfig(createPublishConfigDraft());
    setLoadedAgentBaselineDigest(null);
    setPersistedDraftDigest(null);
    setLastSuccessfulBackendCompileDigest(null);
    setPublishReadyFromCompile(false);
    resetProductionGateState();
    setTriggersCatalog(null);
    setExecutionRecordsFromServer([]);
    setVersionHistory([]);
    setVersionHistoryError(null);
    setRuntimeExecutionsError(null);
    setRuntimeTriggersError(null);
    setCompileArtifact(generateCompileArtifact(fallbackDraft, kbs, toolCatalog));
    setActivePublishChannel("feishu");
    setActiveCompileTab("preview");
    setDebugTrace(null);
  };

  const deleteAgent = async () => {
    if (!deleteTarget || deleteTarget.builtin) return;
    setIsDeletingAgent(true);
    try {
      const response = await fetch(`/agents/${encodeURIComponent(deleteTarget.id)}`, {
        method: "DELETE",
        headers: { Authorization: `Bearer ${token}` },
      });
      const { body } = await safeFetchJson<AgentDeletePayload>(response);
      if (!response.ok || !body?.success || !body.data?.deleted) {
        throw new Error(body?.message ?? `HTTP ${response.status}`);
      }
      const { nextAgents, fallbackAgentId } = resolveAgentAfterDelete(library, deleteTarget.id, selectedAgentId);
      const fallbackAgent = nextAgents.find((item) => item.id === fallbackAgentId) ?? null;
      setLibrary(nextAgents);
      setDeleteTarget(null);
      if (selectedAgentId === deleteTarget.id) {
        if (fallbackAgent) {
          setSelectedAgentId(fallbackAgent.id);
          setDraft(cloneDraft(fallbackAgent.draft));
          setPublishConfig(clonePublishConfigDraft(fallbackAgent.publishConfig));
          setLoadedAgentBaselineDigest(compilePayloadDigest(fallbackAgent.draft, orgId));
          setPersistedDraftDigest(persistPayloadDigest(fallbackAgent.draft, fallbackAgent.publishConfig, orgId));
          setLastSuccessfulBackendCompileDigest(null);
          setPublishReadyFromCompile(false);
          resetProductionGateState();
          setTriggersCatalog(null);
          setExecutionRecordsFromServer([]);
          setVersionHistory([]);
          setVersionHistoryError(null);
          setRuntimeExecutionsError(null);
          setRuntimeTriggersError(null);
          setCompileArtifact(generateCompileArtifact(fallbackAgent.draft, kbs, toolCatalog));
          setActivePublishChannel(fallbackAgent.draft.channels.includes("feishu") ? "feishu" : fallbackAgent.draft.channels[0] ?? "feishu");
          setActiveCompileTab("preview");
          setDebugTrace(null);
          if (pageMode === "editor" && onOpenAgent) {
            onOpenAgent(fallbackAgent.id);
          }
        } else {
          resetEditorAfterDeletedLastAgent();
          if (pageMode === "editor" && onBackToList) {
            onBackToList();
          }
        }
      }
      setNotice(`已删除「${deleteTarget.name}」。${body.data.retentionMessage ?? "历史证据仍会保留。"}`);
    } catch (error) {
      setNotice(`删除失败：${error instanceof Error ? error.message : String(error)}`);
    } finally {
      setIsDeletingAgent(false);
    }
  };

  const persistDraftToBackend = async (options?: { silentSuccessNotice?: boolean }) => {
    if (!selectedAgentId) return;
    const silentSuccessNotice = options?.silentSuccessNotice ?? false;
    try {
      const definitionRes = await fetch(`/agents/${encodeURIComponent(selectedAgentId)}`, {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          name: draft.name,
          avatarBase64: draft.avatarBase64,
          summary: draft.summary,
          greeting: draft.greeting,
          model: draft.model,
          systemPrompt: draft.systemPrompt,
          handoffRule: draft.handoffRule,
          safetyLevel: toBackendSafetyLevel(draft.safetyLevel),
          executionMode: toBackendExecutionMode(draft.executionMode),
          versionLabel: draft.version,
          enabled: true,
        }),
      });
      const { body: definitionBody } = await safeFetchJson<AgentApiRecord>(definitionRes);
      if (!definitionRes.ok || !definitionBody?.success || !definitionBody.data) {
        throw new Error(definitionBody?.message ?? `HTTP ${definitionRes.status}`);
      }

      const specRes = await fetch(`/agents/${encodeURIComponent(selectedAgentId)}/spec`, {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ specText: draft.specText }),
      });
      const { body: specBody } = await safeFetchJson<unknown>(specRes);
      if (!specRes.ok || !specBody?.success) {
        throw new Error(specBody?.message ?? `HTTP ${specRes.status}`);
      }

      const bindingsRes = await fetch(`/agents/${encodeURIComponent(selectedAgentId)}/bindings`, {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          knowledgeBaseIds: draft.knowledgeBaseIds,
          toolIds: draft.toolIds,
          channels: draft.channels,
        }),
      });
      const { body: bindingsBody } = await safeFetchJson<unknown>(bindingsRes);
      if (!bindingsRes.ok || !bindingsBody?.success) {
        throw new Error(bindingsBody?.message ?? `HTTP ${bindingsRes.status}`);
      }

      const publishConfigRes = await fetch(`/agents/${encodeURIComponent(selectedAgentId)}/publish-configs`, {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ publishConfigs: { feishu: publishConfig.feishu } }),
      });
      const { body: publishConfigBody } = await safeFetchJson<unknown>(publishConfigRes);
      if (!publishConfigRes.ok || !publishConfigBody?.success) {
        throw new Error(publishConfigBody?.message ?? `HTTP ${publishConfigRes.status}`);
      }

      const skillsRes = await fetch(`/agents/${encodeURIComponent(selectedAgentId)}/skills`, {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          bindings: draft.skillBindings.map((item) => ({
            skillId: item.skillId,
            skillCode: item.skillCode,
            activationMode: item.activationMode,
            activationCondition: item.activationCondition ?? "",
            priority: item.priority,
            enabled: item.enabled,
          })),
        }),
      });
      const { body: skillsBody } = await safeFetchJson<{ bindings: AgentSkillBindingDraft[] }>(skillsRes);
      if (!skillsRes.ok || !skillsBody?.success) {
        throw new Error(skillsBody?.message ?? `HTTP ${skillsRes.status}`);
      }

      const refreshed = toAgentRecordFromApi(
        {
          ...definitionBody.data,
          specText: draft.specText,
          knowledgeBaseIds: draft.knowledgeBaseIds,
          toolIds: draft.toolIds,
          channels: draft.channels,
          publishConfigs: { feishu: publishConfig.feishu },
          skillBindings: skillsBody.data?.bindings ?? draft.skillBindings,
        },
        orgId,
        kbs,
      );
      setLibrary((current) => current.map((item) => (item.id === selectedAgentId ? refreshed : item)));
      setPersistedDraftDigest(persistPayloadDigest(draft, publishConfig, orgId));
      if (!silentSuccessNotice) {
        setNotice("草稿已保存到后端（definition/spec/bindings/skills/publish-configs）。");
      }
    } catch (error) {
      throw new Error(error instanceof Error ? error.message : String(error));
    }
  };

  const saveFramework = async () => {
    if (!selectedAgentId) return;
    setIsSaving(true);
    try {
      await persistDraftToBackend();
    } catch (error) {
      setNotice(`保存失败，当前仅保留本地草稿：${error instanceof Error ? error.message : String(error)}`);
    } finally {
      setIsSaving(false);
    }
  };

  const compileWorkflow = async () => {
    if (!compileNeedsRebuild) {
      setPublishReadyFromCompile(false);
      setNotice("当前智能体内容无变化，无需重新编译，也不会新增版本。");
      return;
    }
    setIsCompiling(true);
    setNotice("正在先保存草稿，再进行智能体编译…");
    try {
      if (selectedAgentId) {
        setIsSaving(true);
        try {
          await persistDraftToBackend({ silentSuccessNotice: true });
        } finally {
          setIsSaving(false);
        }
      }
      try {
        const response = await fetch(`/agents/${encodeURIComponent(selectedAgentId || "draft")}/compile`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${token}`,
          },
          body: JSON.stringify({
            ...draft,
            orgId,
            skillRefs: draft.skillBindings.filter((item) => item.enabled).map((item) => item.skillCode),
          }),
        });
        const { body } = await safeFetchJson<CompileResponse>(response);
        if (!response.ok || !body?.success || !body.data) {
          throw new Error(body?.message ?? `HTTP ${response.status}`);
        }
        setCompileArtifact(toCompileArtifact(body.data));
        setActiveCompileTab("preview");
        setDebugTrace(null);
        const digest = compilePayloadDigest(draft, orgId);
        setLastSuccessfulBackendCompileDigest(digest);
        setLoadedAgentBaselineDigest(digest);
        setPublishReadyFromCompile(body.data.changed === true && body.data.draftVersionNo != null);
        if (body.data.draftVersionNo != null) {
          setLatestCompiledVersionNo(body.data.draftVersionNo);
        }
        const compiledVersionNo = body.data.draftVersionNo ?? null;
        setSkillDagVersionNo(compiledVersionNo);
        setSkillDagGraph(null);
        setSkillDagError("");
        if (selectedAgentId) {
          void loadAgentSkillDag(compiledVersionNo, selectedAgentId);
        }
        setProductionReadiness(null);
        setReadinessError(null);
        setNotice(buildCompileNotice(body.data));
        void loadVersionHistory();
      } catch (error) {
        await new Promise((resolve) => window.setTimeout(resolve, 320));
        setCompileArtifact(generateCompileArtifact(draft, kbs, toolCatalog));
        setActiveCompileTab("preview");
        setDebugTrace(null);
        setPublishReadyFromCompile(false);
        skillDagRequestIdRef.current += 1;
        setSkillDagGraph(null);
        setSkillDagLoading(false);
        setSkillDagError("Skill 依赖需要后端编译结果，当前仅完成了前端模拟编译。");
        setNotice(`后端 compile 接口暂不可用，已回退到前端模拟编译：${error instanceof Error ? error.message : String(error)}`);
      }
    } catch (error) {
      setPublishReadyFromCompile(false);
      setNotice(`保存失败，已取消本次编译：${error instanceof Error ? error.message : String(error)}`);
    } finally {
      setIsCompiling(false);
    }
  };

  const runDebug = async () => {
    if (!selectedAgentId) {
      setNotice("请先选择或创建一个 Agent，再试运行。");
      return;
    }
    setIsDebugging(true);
    let finalTrace: DebugTraceResult | null = null;
    try {
      const response = await fetch(`/agents/${encodeURIComponent(selectedAgentId)}/debug`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          input: debugInput,
          requestedKnowledgeBaseIds: draft.knowledgeBaseIds.map((item) => String(item)),
          skillRefs: draft.skillBindings.filter((item) => item.enabled).map((item) => item.skillCode),
        }),
      });
      const { body } = await safeFetchJson<DebugRuntimePayload>(response);
      if (!body?.data) {
        throw new Error("后端未返回调试结果");
      }
      finalTrace = buildBackendDebugTrace(compileArtifact.preview, draft, debugInput, body.data);
      setDebugTrace(finalTrace);
    } catch (error) {
      await new Promise((resolve) => window.setTimeout(resolve, 240));
      finalTrace = simulateDebugTrace(compileArtifact.preview, draft, debugInput);
      finalTrace.notes = [
        `后端调试接口暂不可用，已回退到前端模拟路径：${error instanceof Error ? error.message : String(error)}`,
        ...finalTrace.notes,
      ];
      setDebugTrace(finalTrace);
    } finally {
      setActiveCompileTab("debug");
      setIsDebugging(false);
      void loadRuntimeExecutions();
    }
  };

  useEffect(() => {
    if (modelOptions.length === 0) return;
    setDraft((current) => (current.model && modelOptions.some((item) => item.value === current.model)
      ? current
      : { ...current, model: modelOptions[0].value }));
  }, [modelOptions]);

  const compileStaleBlocksPublish = useMemo(
    () => isDraftCompileStaleForPublish(draft, orgId, lastSuccessfulBackendCompileDigest, loadedAgentBaselineDigest),
    [draft, orgId, lastSuccessfulBackendCompileDigest, loadedAgentBaselineDigest],
  );
  const compileNeedsRebuild = useMemo(() => {
    const currentCompileDigest = compilePayloadDigest(draft, orgId);
    return isCompileRequired(currentCompileDigest, lastSuccessfulBackendCompileDigest, loadedAgentBaselineDigest);
  }, [draft, orgId, lastSuccessfulBackendCompileDigest, loadedAgentBaselineDigest]);
  const hasDraftChanges = useMemo(() => {
    if (persistedDraftDigest == null) return true;
    return persistPayloadDigest(draft, publishConfig, orgId) !== persistedDraftDigest;
  }, [draft, orgId, persistedDraftDigest, publishConfig]);
  const publishBlockedByCompileGate = !publishReadyFromCompile;
  const publishBlocked = isPublishing || isCompiling || compileStaleBlocksPublish || publishBlockedByCompileGate;
  const publishBlockedTitle = publishBlockedByCompileGate
    ? "请先执行「智能体编译」，且编译结果检测到变化并生成新版本后，才可发布。"
    : (compileStaleBlocksPublish
      ? "请先完成「智能体编译」，使编译产物与当前草稿一致后再发布。"
      : undefined);

  const activePublishMeta = CHANNEL_OPTIONS.find((channel) => channel.id === activePublishChannel) ?? CHANNEL_OPTIONS[0];
  const activePublishEnabled = draft.channels.includes(activePublishChannel);

  const createDefaultEvaluationSuite = async (): Promise<AgentEvalSuite | null> => {
    if (!selectedAgentId || !token) return null;
    setEvaluationLoading(true);
    setEvaluationError(null);
    try {
      const response = await fetch(`/agents/${encodeURIComponent(selectedAgentId)}/evaluation/suites`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          name: "发布门禁评测",
          description: "发布前必须通过的 P0 与安全回归用例。",
          gateMode: "BLOCKING",
          minPassRate: 1,
        }),
      });
      const { body } = await safeFetchJson<AgentEvalSuite>(response);
      if (!response.ok || !body?.success || !body.data) {
        throw new Error(body?.message ?? `HTTP ${response.status}`);
      }
      setEvaluationSuites((current) => [body.data as AgentEvalSuite, ...current]);
      setNotice("已创建 blocking 发布评测集。");
      return body.data as AgentEvalSuite;
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      setEvaluationError(message);
      setNotice(`创建评测集失败：${message}`);
      return null;
    } finally {
      setEvaluationLoading(false);
    }
  };

  const runEvaluationSuite = async (suite: AgentEvalSuite | null = activeEvaluationSuite) => {
    if (!selectedAgentId || !token) return;
    const targetSuite = suite ?? activeEvaluationSuite;
    const versionNo = targetReadinessVersionNo;
    if (!targetSuite) {
      setEvaluationError("请先创建发布评测集。");
      return;
    }
    if (versionNo == null) {
      setEvaluationError("请先完成智能体编译，再运行评测。");
      return;
    }
    setEvaluationLoading(true);
    setEvaluationError(null);
    try {
      const response = await fetch(`/agents/${encodeURIComponent(selectedAgentId)}/evaluation/suites/${targetSuite.id}/runs`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ versionNo }),
      });
      const { body } = await safeFetchJson<AgentEvalRun>(response);
      if (!response.ok || !body?.success || !body.data) {
        throw new Error(body?.message ?? `HTTP ${response.status}`);
      }
      setEvaluationRuns((current) => [body.data as AgentEvalRun, ...current]);
      setNotice(`评测完成：v${versionNo} ${body.data.status}，通过率 ${Math.round((body.data.passRate ?? 0) * 100)}%。`);
      void loadProductionReadiness(versionNo);
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      setEvaluationError(message);
      setNotice(`运行评测失败：${message}`);
    } finally {
      setEvaluationLoading(false);
    }
  };

  const addP0EvaluationCaseAndRun = async () => {
    if (!selectedAgentId || !token) return;
    const expectedText = evaluationExpectedText.trim();
    if (!expectedText) {
      setEvaluationError("请先填写期望输出关键词。");
      return;
    }
    const suite = activeEvaluationSuite ?? await createDefaultEvaluationSuite();
    if (!suite) return;
    setEvaluationLoading(true);
    setEvaluationError(null);
    try {
      const response = await fetch(`/agents/${encodeURIComponent(selectedAgentId)}/evaluation/suites/${suite.id}/cases`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          name: `P0 输出包含：${expectedText.slice(0, 18)}`,
          inputText: evaluationInput,
          assertionType: "OUTPUT_CONTAINS",
          expectedText,
          priority: "P0",
        }),
      });
      const { body } = await safeFetchJson<Record<string, unknown>>(response);
      if (!response.ok || !body?.success) {
        throw new Error(body?.message ?? `HTTP ${response.status}`);
      }
      setNotice("已添加 P0 评测用例，开始运行当前版本评测。");
      await runEvaluationSuite(suite);
      void loadEvaluationSuites();
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      setEvaluationError(message);
      setNotice(`添加评测用例失败：${message}`);
    } finally {
      setEvaluationLoading(false);
    }
  };

  const readinessStatusText = productionReadiness?.blocked
    ? "阻塞"
    : productionReadiness?.status === "ready"
      ? "可发布"
      : productionReadiness?.status === "warning"
        ? "有警告"
        : "待检查";

  const evaluationRunStatusText = latestEvaluationRun
    ? latestEvaluationRun.status === "PASSED"
      ? "通过"
      : latestEvaluationRun.status === "FAILED"
        ? "失败"
        : "空集"
    : "未运行";

  const renderProductionGatePanel = () => (
    <section className="cici-builder-production-gate" aria-label="生产就绪检查">
      <div className="cici-builder-production-gate__head">
        <div>
          <span className="cici-builder-production-gate__eyebrow">Production Gate</span>
          <h2>生产就绪</h2>
        </div>
        <div className="cici-builder-production-gate__actions">
          <span className={`cici-builder-production-gate__status is-${productionReadiness?.blocked ? "blocked" : productionReadiness?.status === "ready" ? "ready" : "warning"}`}>
            {readinessStatusText}
          </span>
          <button type="button" className="cici-builder__action cici-builder__action--ghost" onClick={() => void loadProductionReadiness()} disabled={readinessLoading || !targetReadinessVersionNo}>
            {readinessLoading ? "检查中…" : "刷新检查"}
          </button>
        </div>
      </div>

      <div className="cici-builder-production-gate__metrics">
        <span>目标版本 <strong>{targetReadinessVersionNo != null ? `v${targetReadinessVersionNo}` : "未编译"}</strong></span>
        <span>阻塞项 <strong>{readinessBlockingChecks.length}</strong></span>
        <span>警告项 <strong>{readinessWarningChecks.length}</strong></span>
        <span>线上版本 <strong>{publishedVersionNo != null ? `v${publishedVersionNo}` : "未发布"}</strong></span>
      </div>

      {readinessError ? <p className="cici-builder-production-gate__error">{readinessError}</p> : null}
      <div className="cici-builder-production-gate__checks">
        {(productionReadiness?.checks ?? []).slice(0, 6).map((check) => (
          <div key={`${check.code}-${check.status}`} className={`cici-builder-production-check is-${check.severity === "blocker" ? "blocked" : check.severity === "warning" ? "warning" : "ready"}`}>
            <span>{check.status === "passed" ? "通过" : check.severity === "blocker" ? "阻塞" : "提示"}</span>
            <strong>{check.code}</strong>
            <small>{check.message}</small>
          </div>
        ))}
        {!productionReadiness && !readinessError ? (
          <p className="cici-builder-production-gate__empty">打开评测页签后会自动读取后端 readiness。发布前也会强制刷新一次。</p>
        ) : null}
      </div>
    </section>
  );

  const renderEvaluationGatePanel = () => (
    <section className="cici-builder-evaluation-gate" aria-label="发布评测">
      <div className="cici-builder-evaluation-gate__head">
        <div>
          <span className="cici-builder-production-gate__eyebrow">Evaluation</span>
          <h3>发布评测</h3>
        </div>
        <button type="button" className="cici-builder__action cici-builder__action--ghost" onClick={() => void loadEvaluationSuites()} disabled={evaluationLoading || !selectedAgentId}>
          {evaluationLoading ? "同步中…" : "同步评测"}
        </button>
      </div>
      <div className="cici-builder-evaluation-gate__summary">
        <span>评测集 <strong>{activeEvaluationSuite?.name ?? "未配置"}</strong></span>
        <span>门禁 <strong>{activeEvaluationSuite?.gateMode ?? "未开启"}</strong></span>
        <span>最近运行 <strong>{evaluationRunStatusText}</strong></span>
        <span>通过率 <strong>{latestEvaluationRun ? `${Math.round(latestEvaluationRun.passRate * 100)}%` : "无"}</strong></span>
      </div>
      <div className="cici-builder-evaluation-gate__form">
        <label className="cici-builder-field">
          <span>评测输入</span>
          <textarea rows={2} value={evaluationInput} onChange={(event) => setEvaluationInput(event.target.value)} />
        </label>
        <label className="cici-builder-field">
          <span>P0 期望关键词</span>
          <input value={evaluationExpectedText} onChange={(event) => setEvaluationExpectedText(event.target.value)} placeholder="例如：报价说明、转人工、保修政策" />
        </label>
      </div>
      {evaluationError ? <p className="cici-builder-production-gate__error">{evaluationError}</p> : null}
      <div className="cici-builder-evaluation-gate__actions">
        <button type="button" className="cici-builder__action cici-builder__action--ghost" onClick={() => void createDefaultEvaluationSuite()} disabled={evaluationLoading || Boolean(activeEvaluationSuite)}>
          创建阻塞评测集
        </button>
        <button type="button" className="cici-builder__action cici-builder__action--ghost" onClick={() => void addP0EvaluationCaseAndRun()} disabled={evaluationLoading || !targetReadinessVersionNo}>
          添加 P0 用例并运行
        </button>
        <button type="button" className="cici-builder__action cici-builder__action--primary" onClick={() => void runEvaluationSuite()} disabled={evaluationLoading || !activeEvaluationSuite || !targetReadinessVersionNo}>
          运行评测
        </button>
      </div>
    </section>
  );

  const publishLatestVersion = async () => {
    if (!selectedAgentId) return;
    if (publishBlockedByCompileGate) {
      setNotice("请先执行「智能体编译」，且检测到变化生成新版本后，再发布。");
      return;
    }
    if (compileStaleBlocksPublish) {
      setNotice(
        "当前草稿与最近一次成功编译或后端同步基线不一致，请先点击「智能体编译」完成编译后再发布；未编译的改动不会在生产环境生效。",
      );
      return;
    }
    setIsPublishing(true);
    try {
      // Step 1: check for existing compiled versions
      const versionsRes = await fetch(`/agents/${encodeURIComponent(selectedAgentId)}/versions`, {
        method: "GET",
        headers: { Authorization: `Bearer ${token}` },
      });
      const { body: versionsBody } = await safeFetchJson<Array<{ versionNo: number }>>(versionsRes);
      if (!versionsRes.ok || !versionsBody?.success) {
        throw new Error(versionsBody?.message ?? `获取版本列表失败：HTTP ${versionsRes.status}`);
      }

      let latestVersionNo: number | null =
        Array.isArray(versionsBody.data) && versionsBody.data.length > 0
          ? versionsBody.data[0].versionNo
          : null;

      if (latestVersionNo == null) {
        throw new Error("未找到可发布的新编译版本，请先执行「智能体编译」。");
      }
      setLatestCompiledVersionNo(latestVersionNo);
      const readiness = await loadProductionReadiness(latestVersionNo);
      if (readiness?.blocked) {
        setActiveCompileTab(readiness.checks.some((check) => check.code.toLowerCase().includes("eval") && check.status !== "passed") ? "evaluation" : "publish");
        setNotice("发布已停止：生产就绪检查仍有阻塞项，请先处理检查清单。");
        return;
      }

      // Step 3: publish the target version
      const publishRes = await fetch(`/agents/${encodeURIComponent(selectedAgentId)}/publish`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ versionNo: latestVersionNo }),
      });
      const { body: publishBody } = await safeFetchJson<{ versionNo: number }>(publishRes);
      const publishPayload = publishBody?.data;
      if (!publishRes.ok || !publishBody?.success || !publishPayload) {
        throw new Error(publishBody?.message ?? `HTTP ${publishRes.status}`);
      }
      setLibrary((current) =>
        current.map((item) =>
          item.id === selectedAgentId
            ? { ...item, status: "已发布", lastEdited: "刚刚" }
            : item,
        ),
      );
      const publishedVersionNo = publishPayload.versionNo;
      setPublishedVersionNo(publishedVersionNo);
      setPublishReadyFromCompile(false);
      setNotice(`发布成功：已将 v${publishedVersionNo} 设为线上版本。`);
      void loadRuntimeExecutions();
      void loadRuntimeTriggers();
    } catch (error) {
      setNotice(`发布失败：${error instanceof Error ? error.message : String(error)}`);
    } finally {
      setIsPublishing(false);
    }
  };

  const rollbackToPreviousVersion = async () => {
    if (!selectedAgentId) return;
    setIsPublishing(true);
    try {
      const versionsRes = await fetch(`/agents/${encodeURIComponent(selectedAgentId)}/versions`, {
        method: "GET",
        headers: { Authorization: `Bearer ${token}` },
      });
      const { body: versionsBody } = await safeFetchJson<Array<{ versionNo: number }>>(versionsRes);
      if (!versionsRes.ok || !versionsBody?.success || !versionsBody.data || versionsBody.data.length < 2) {
        throw new Error(versionsBody?.message ?? "没有可回滚的历史版本。");
      }
      const rollbackVersionNo = versionsBody.data[1].versionNo;
      const rollbackRes = await fetch(`/agents/${encodeURIComponent(selectedAgentId)}/rollback`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ versionNo: rollbackVersionNo }),
      });
      const { body: rollbackBody } = await safeFetchJson<{ versionNo: number }>(rollbackRes);
      if (!rollbackRes.ok || !rollbackBody?.success || !rollbackBody.data) {
        throw new Error(rollbackBody?.message ?? `HTTP ${rollbackRes.status}`);
      }
      setNotice(`回滚成功：已切回 v${rollbackBody.data.versionNo}。`);
      setPublishedVersionNo(rollbackBody.data.versionNo);
      void loadRuntimeExecutions();
      void loadRuntimeTriggers();
    } catch (error) {
      setNotice(`回滚失败：${error instanceof Error ? error.message : String(error)}`);
    } finally {
      setIsPublishing(false);
    }
  };

  const renderPublishChannelPanel = () => {
    if (activePublishChannel === "feishu") {
      return (
        <div className="cici-builder-publish-panel__body">
          <div className="cici-builder-publish-panel__hero">
            <div>
              <h3>飞书机器人配置</h3>
              <p>在这里维护飞书机器人长连接所需凭证，并指定默认承接的 Agent 与配对指令。</p>
            </div>
            <button
              type="button"
              className={`cici-choice-chip${activePublishEnabled ? " is-active" : ""}`}
              onClick={() => toggleCollectionValue("channels", "feishu")}
            >
              {activePublishEnabled ? "已启用飞书发布" : "启用飞书发布"}
            </button>
          </div>

          <div className="cici-builder-publish-panel__stats">
            <article className="cici-builder-publish-stat">
              <span>接入方式</span>
              <strong>长连接机器人</strong>
              <small>和后端 `feishu_bot` 集成能力保持一致。</small>
            </article>
            <article className="cici-builder-publish-stat">
              <span>默认智能体</span>
              <strong>{publishConfig.feishu.defaultAgentCode || "cici"}</strong>
              <small>机器人完成配对后默认交给该 Agent 处理。</small>
            </article>
            <article className="cici-builder-publish-stat">
              <span>配对口令</span>
              <strong>{publishConfig.feishu.pairingCommandHint || "配对"}</strong>
              <small>工作台生成配对码后，飞书单聊里会使用这个指令前缀。</small>
            </article>
          </div>

          <div className="cici-builder-publish-form">
            <label className="cici-builder-field">
              <span>App ID</span>
              <input
                value={publishConfig.feishu.appId}
                onChange={(event) => updateFeishuPublishConfig("appId", event.target.value)}
                placeholder="例如：cli_a9f3c2b1..."
              />
            </label>
            <label className="cici-builder-field">
              <span>App Secret</span>
              <input
                type="password"
                value={publishConfig.feishu.appSecret}
                onChange={(event) => updateFeishuPublishConfig("appSecret", event.target.value)}
                placeholder="输入飞书应用密钥"
              />
            </label>
            <label className="cici-builder-field">
              <span>默认 Agent Code</span>
              <input
                value={publishConfig.feishu.defaultAgentCode}
                onChange={(event) => updateFeishuPublishConfig("defaultAgentCode", event.target.value)}
                placeholder="默认 cici"
              />
            </label>
            <label className="cici-builder-field">
              <span>配对指令前缀</span>
              <input
                value={publishConfig.feishu.pairingCommandHint}
                onChange={(event) => updateFeishuPublishConfig("pairingCommandHint", event.target.value)}
                placeholder="例如：配对"
              />
            </label>
            <label className="cici-builder-field cici-builder-field--checkbox">
              <input
                type="checkbox"
                checked={publishConfig.feishu.autoSyncSchedulesOnPublish}
                onChange={(event) => updateFeishuPublishConfig("autoSyncSchedulesOnPublish", event.target.checked)}
              />
              <span>发布后自动同步「触发与调度」中的周期触发器</span>
            </label>
          </div>

          <div className="cici-builder-publish-note">
            <strong>建议流程</strong>
            <p>先在这里补全飞书凭证，再到工作台生成配对码，最后用飞书机器人单聊完成绑定验证。</p>
          </div>
        </div>
      );
    }

    if (activePublishChannel === "api") {
      return (
        <div className="cici-builder-publish-panel__body">
          <div className="cici-builder-publish-panel__hero">
            <div>
              <h3>Agent Open API</h3>
              <p>允许外部业务系统通过 API Key 调用已发布 Agent，并把调用写入统一 trace 与审计链路。</p>
            </div>
            <button
              type="button"
              className={`cici-choice-chip${activePublishEnabled ? " is-active" : ""}`}
              onClick={() => toggleCollectionValue("channels", "api")}
            >
              {activePublishEnabled ? "已开放 API" : "开放 API"}
            </button>
          </div>

          <div className="cici-builder-publish-panel__stats">
            <article className="cici-builder-publish-stat">
              <span>基础地址</span>
              <strong>/openapi/v1</strong>
              <small>公网部署需确认 Nginx 已代理 `/openapi`。</small>
            </article>
            <article className="cici-builder-publish-stat">
              <span>调用身份</span>
              <strong>API Key + run-as</strong>
              <small>Key 绑定单个 Agent 与一个组织内用户。</small>
            </article>
            <article className="cici-builder-publish-stat">
              <span>当前状态</span>
              <strong>{publishedVersionNo != null ? (activePublishEnabled ? "可调用" : "未开放") : "未发布"}</strong>
              <small>调用前必须有线上版本并启用 API channel。</small>
            </article>
          </div>

          <div className="cici-builder-publish-note">
            <strong>开放 API 文档</strong>
            <p>页面头部的「开放API文档」会展示鉴权、健康检查、对话、流式对话、会话映射和错误码示例。API Key 管理接口已接入后端，前端管理入口后续会放到这里。</p>
          </div>
        </div>
      );
    }

    return (
      <div className="cici-builder-publish-panel__body">
        <div className="cici-builder-publish-panel__hero">
          <div>
            <h3>{activePublishMeta.label} 渠道</h3>
            <p>{activePublishMeta.tone}场景已经预留在 Agent Builder 中，后续可以在这里补齐专属凭证、欢迎语和入口策略。</p>
          </div>
          <button
            type="button"
            className={`cici-choice-chip${activePublishEnabled ? " is-active" : ""}`}
            onClick={() => toggleCollectionValue("channels", activePublishChannel)}
          >
            {activePublishEnabled ? "已加入发布计划" : "加入发布计划"}
          </button>
        </div>

        <div className="cici-builder-publish-placeholder">
          <strong>{activePublishMeta.label} 配置页即将开放</strong>
          <p>当前已经把渠道入口升级为独立 tab，后续可以继续按这个版式补齐渠道凭证、会话入口与灰度策略。</p>
        </div>
      </div>
    );
  };

  const renderLibraryPanel = (asPage: boolean) => (
    <aside className={`cici-sessions cici-builder-sidebar${asPage ? " cici-builder-sidebar--page" : ""}`}>
      <div className="cici-sessions__header cici-builder-sidebar__header">
        <div className="cici-builder-sidebar__header-main">
          <p className="cici-builder-sidebar__eyebrow">Agent 构建</p>
          <p className="cici-builder-sidebar__lead">让业务人员写流程文本，让系统编译成可执行 workflow code。</p>
        </div>
        <button type="button" className="cici-builder-sidebar__create" onClick={() => void createAgent()} disabled={isLoadingLibrary}>+ 新建 Agent</button>
      </div>
      <div className="cici-sessions__search cici-builder-sidebar__search">
        <svg viewBox="0 0 24 24"><circle cx="11" cy="11" r="7"/><path d="M21 21l-4.35-4.35"/></svg>
        <input type="text" placeholder="搜索 Agent 模板…" value={searchText} onChange={(event) => setSearchText(event.target.value)} />
      </div>
      <div className={`cici-sessions__list cici-builder-sidebar__list${asPage ? " cici-builder-sidebar__list--grid" : ""}`}>
        {filteredLibrary.map((item) => (
          <article
            key={item.id}
            className={`cici-agent-card${pageMode === "editor" && item.id === selectedAgentId ? " is-active" : ""}`}
            onClick={() => void selectAgent(item.id)}
          >
            <button type="button" className="cici-agent-card__select">
              <div className="cici-agent-card__top">
                <span className="cici-agent-card__name">{item.name}</span>
                <div className="cici-agent-card__badges">
                  {item.builtin ? <span className="cici-agent-card__builtin">系统内置</span> : null}
                  <span className={`cici-agent-card__status cici-agent-card__status--${item.status === "已发布" ? "published" : item.status === "待联调" ? "testing" : "draft"}`}>{item.status}</span>
                </div>
              </div>
              <p className="cici-agent-card__summary">{item.summary}</p>
              <div className="cici-agent-card__meta">
                <span>{item.lastEdited}</span>
                <span>{item.channels.map((channel) => CHANNEL_OPTIONS.find((option) => option.id === channel)?.label ?? channel).join(" · ")}</span>
              </div>
            </button>
            {!item.builtin ? (
              <div className="cici-agent-card__actions">
                <button
                  type="button"
                  className="cici-agent-card__delete"
                  onClick={(event) => {
                    event.stopPropagation();
                    setDeleteTarget(item);
                  }}
                  disabled={isDeletingAgent && deleteTarget?.id === item.id}
                >
                  删除
                </button>
              </div>
            ) : null}
          </article>
        ))}
        {filteredLibrary.length === 0 ? <div className="cici-agent-card cici-agent-card--empty">未找到匹配的 Agent 模板。</div> : null}
      </div>
    </aside>
  );

  const hasBackendCompileFingerprint = lastSuccessfulBackendCompileDigest !== null;
  const isAgentPublished = selectedAgent?.status === "已发布";

  const renderTriggersRuntimePanel = () => {
    const lc = triggersCatalog?.lifecycle;
    const inferNoCompile = lc != null ? lc === "NO_COMPILE" : !hasBackendCompileFingerprint;
    const inferPublished = lc != null ? lc === "PUBLISHED" : isAgentPublished;
    const scheduleRows = triggersCatalog?.scheduleTriggers ?? [];

    return (
      <div className="cici-builder-runtime">
        <p className="cici-builder-runtime__lede">
          与流程图 START「接收用户输入」对齐：数据来自后端 `/agents/.../runtime/triggers`（渠道绑定 + 生命周期 + 已发布时的定时占位）。
        </p>
        {runtimeTriggersError ? (
          <div className="cici-builder-runtime__empty">触发器接口暂不可用：{runtimeTriggersError}</div>
        ) : null}
        {runtimeTriggersLoading ? <div className="cici-builder-runtime__empty">正在加载触发器…</div> : null}

        <section className="cici-builder-runtime__block">
          <div className="cici-builder-runtime__block-head">
            <h3>定时 / 周期触发器</h3>
            <div className="cici-builder-runtime__cta-row">
              <span className="cici-builder-runtime__pill cici-builder-runtime__pill--soft">
                {triggersCatalog?.scheduleSource === "persisted"
                  ? "已同步"
                  : triggersCatalog?.scheduleSource === "inferred"
                  ? "Spec 推导"
                  : "发布后同步"}
              </span>
              <button
                type="button"
                className="cici-builder__action cici-builder__action--ghost cici-builder-runtime__cta"
                onClick={() => void syncRuntimeSchedules()}
                disabled={!selectedAgentId || runtimeScheduleSyncing || runtimeTriggersLoading}
                title={!selectedAgentId ? "请先选择 Agent" : undefined}
              >
                {runtimeScheduleSyncing ? "同步中…" : "同步到调度"}
              </button>
            </div>
          </div>
          {triggersCatalog?.scheduleSyncHint ? (
            <p className="cici-builder-runtime__muted">{triggersCatalog.scheduleSyncHint}</p>
          ) : null}
          {inferNoCompile ? (
            <div className="cici-builder-runtime__empty">
              还没有可用的编译版本，请先完成一次「生成流程代码」。编译前，调度类触发不会在运行环境生效。
            </div>
          ) : !inferPublished ? (
            <div className="cici-builder-runtime__empty">
              发布后会在这里看到定时/周期触发器（由平台从 routine 自动同步）。当前可先在「试运行」验证流程路径。
            </div>
          ) : scheduleRows.length === 0 ? (
            <div className="cici-builder-runtime__empty">已发布，但暂无定时触发占位数据。</div>
          ) : (
            <div className="cici-builder-runtime__trigger-cards">
              {scheduleRows.map((s) => {
                const enabled = s.enabled ?? true;
                const actionKeyBase = String(s.id ?? "");
                const toggling = runtimeScheduleActionKey === `${actionKeyBase}:${enabled ? "disable" : "enable"}`;
                const running = runtimeScheduleActionKey === `${actionKeyBase}:run`;
                return (
                  <article key={s.id} className="cici-builder-runtime__trigger-card">
                    <div className="cici-builder-runtime__trigger-main">
                      <strong className="cici-builder-runtime__list-title">{s.title}</strong>
                      <span className="cici-builder-runtime__list-meta">
                        {s.cadence ?? ""}
                        {s.detail ? ` · ${s.detail}` : ""}
                      </span>
                      <span className="cici-builder-runtime__list-meta">
                        {enabled ? "启用中" : "已停用"}
                        {s.source ? ` · ${s.source}` : ""}
                      </span>
                    </div>
                    <div className="cici-builder-runtime__cta-row">
                      <button
                        type="button"
                        className="cici-builder__action cici-builder__action--ghost cici-builder-runtime__cta"
                        onClick={() => void updateRuntimeScheduleEnabled(String(s.id), !enabled)}
                        disabled={toggling || running}
                      >
                        {toggling ? "处理中…" : enabled ? "停用" : "启用"}
                      </button>
                      <button
                        type="button"
                        className="cici-builder__action cici-builder__action--ghost cici-builder-runtime__cta"
                        onClick={() => void runRuntimeScheduleNow(String(s.id))}
                        disabled={running || toggling || !enabled}
                        title={!enabled ? "请先启用触发器" : undefined}
                      >
                        {running ? "执行中…" : "立即执行"}
                      </button>
                    </div>
                  </article>
                );
              })}
            </div>
          )}
        </section>
      </div>
    );
  };

  const renderExecutionsRuntimePanel = () => (
    <div className="cici-builder-runtime">
      <div className="cici-builder-runtime__toolbar">
        <div className="cici-builder-runtime__filters cici-builder-runtime__filters--scope" role="group" aria-label="执行记录筛选">
          <span className="cici-builder-runtime__filter-label">记录范围</span>
          {(["all", "production", "try_run"] as const).map((key) => (
            <button
              key={key}
              type="button"
              className={`cici-builder-runtime__filter${executionFilter === key ? " is-active" : ""}`}
              onClick={() => setExecutionFilter(key)}
            >
              <span>{key === "all" ? "全部" : key === "production" ? "生产类" : "试运行"}</span>
              <strong>{executionRecordCounts[key]}</strong>
            </button>
          ))}
        </div>
        <div className="cici-builder-runtime__cta-row">
          <button
            type="button"
            className="cici-builder__action cici-builder__action--ghost cici-builder-runtime__cta"
            onClick={() => void loadRuntimeExecutions()}
            disabled={!selectedAgentId || runtimeExecutionsLoading}
          >
            {runtimeExecutionsLoading ? "刷新中…" : "刷新列表"}
          </button>
          <button
            type="button"
            className="cici-builder__action cici-builder__action--ghost cici-builder-runtime__cta"
            onClick={() => {
              setActiveCompileTab("debug");
              setNotice("已在「试运行」tab：服务端会持久化 TRY_RUN 执行记录。");
            }}
            disabled={!selectedAgentId}
            title={!selectedAgentId ? "请先选择 Agent" : undefined}
          >
            执行默认试运行
          </button>
        </div>
      </div>
      <p className="cici-builder-runtime__muted">
        来源：`agent_workflow_execution_log`，包含试运行、发布和会话渠道执行。
      </p>
      {runtimeExecutionsError ? <div className="cici-builder-runtime__empty">执行记录接口暂不可用：{runtimeExecutionsError}</div> : null}
      {runtimeExecutionsLoading && filteredExecutionRecords.length === 0 ? (
        <div className="cici-builder-runtime__empty">正在加载执行记录…</div>
      ) : null}
      {!runtimeExecutionsLoading && filteredExecutionRecords.length === 0 ? (
        <div className="cici-builder-runtime__empty">
          {!hasBackendCompileFingerprint
            ? "编译后可在此查看服务端记录的试运行；会话中的渠道执行在聊天后也会写入。"
            : "还没有执行记录。发布、试运行或发起聊天后会在此出现。"}
        </div>
      ) : null}
      {filteredExecutionRecords.length > 0 ? (
        <div className="cici-builder-runtime__table-wrap">
          <table className="cici-builder-runtime__table">
            <thead>
              <tr>
                <th>执行 ID</th>
                <th>时间</th>
                <th>状态</th>
                <th>来源</th>
                <th>版本</th>
                <th>耗时</th>
                <th>摘要</th>
              </tr>
            </thead>
            <tbody>
              {filteredExecutionRecords.map((row) => {
                const duration = Math.max(0, row.endedAt - row.startedAt);
                const idCell = row.id.length <= 14 ? row.id : `${row.id.slice(0, 10)}…`;
                return (
                  <tr key={row.id}>
                    <td className="cici-builder-runtime__mono">{idCell}</td>
                    <td>{new Date(row.startedAt).toLocaleString()}</td>
                    <td>
                      <span className={`cici-builder-runtime__status cici-builder-runtime__status--${row.status === "成功" ? "ok" : "fail"}`}>
                        {row.status}
                      </span>
                    </td>
                    <td>{executionSourceLabel(row.source)}</td>
                    <td>{row.versionLabel}</td>
                    <td>{duration}ms</td>
                    <td className="cici-builder-runtime__ellipsis" title={row.summary}>
                      {row.summary}
                      {row.errorHint ? <span className="cici-builder-runtime__err"> · {row.errorHint}</span> : null}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      ) : null}
    </div>
  );

  const renderVersionHistoryPanel = () => (
    <div className="cici-builder-runtime">
      <div className="cici-builder-runtime__toolbar">
        <p className="cici-builder-runtime__muted">最近 10 次智能体编译记录；无变化时不会新增版本。</p>
        <div className="cici-builder-runtime__cta-row">
          <button
            type="button"
            className="cici-builder__action cici-builder__action--ghost cici-builder-runtime__cta"
            onClick={() => void loadVersionHistory()}
            disabled={!selectedAgentId || versionHistoryLoading}
          >
            {versionHistoryLoading ? "刷新中…" : "刷新历史"}
          </button>
        </div>
      </div>
      {versionHistoryError ? <div className="cici-builder-runtime__empty">版本历史加载失败：{versionHistoryError}</div> : null}
      {versionHistoryLoading && versionHistory.length === 0 ? <div className="cici-builder-runtime__empty">正在加载版本历史…</div> : null}
      {!versionHistoryLoading && versionHistory.length === 0 ? (
        <div className="cici-builder-runtime__empty">暂无编译版本历史，点击右上角「智能体编译」后会记录在这里。</div>
      ) : null}
      {versionHistory.length > 0 ? (
        <div className="cici-builder-runtime__table-wrap">
          <table className="cici-builder-runtime__table">
            <thead>
              <tr>
                <th>版本</th>
                <th>时间</th>
                <th>状态</th>
                <th>发布备注</th>
                <th>变化内容</th>
                <th>依赖</th>
              </tr>
            </thead>
            <tbody>
              {versionHistory.map((item) => (
                (() => {
                  const remark = (item.versionLabel ?? "").trim();
                  const fallbackChanges = (item.compileSummary ?? []).filter((line) => (line ?? "").trim()).slice(0, 2);
                  const changeLines = (item.changeLog ?? []).filter((line) => (line ?? "").trim());
                  const visibleChanges = changeLines.length > 0 ? changeLines : fallbackChanges;
                  return (
                <tr key={item.id}>
                  <td className="cici-builder-runtime__mono">{`v${item.versionNo}`}</td>
                  <td>{new Date(item.createdAt).toLocaleString()}</td>
                  <td>{(item.publishStatus ?? "").toUpperCase() === "PUBLISHED" ? "已发布" : "草稿"}</td>
                  <td>{remark || "—"}</td>
                  <td className="cici-builder-runtime__ellipsis" title={visibleChanges.join(" / ")}>
                    {visibleChanges.length > 0 ? visibleChanges.join("；") : "—"}
                  </td>
                  <td>
                    <button
                      type="button"
                      className="cici-builder-runtime__text-action"
                      onClick={() => openSkillDagVersion(item.versionNo)}
                    >
                      查看 Skill 依赖
                    </button>
                  </td>
                </tr>
                  );
                })()
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </div>
  );

  const renderDeleteModal = () => deleteTarget ? (
    <div className="cici-modal-backdrop" role="presentation">
      <div
        className="cici-modal cici-agent-delete-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="cici-agent-delete-title"
      >
        <div className="cici-modal__header">
          <h2 id="cici-agent-delete-title">删除「{deleteTarget.name}」？</h2>
          <button type="button" className="cici-modal__close" onClick={() => setDeleteTarget(null)} aria-label="关闭">×</button>
        </div>
        <div className="cici-agent-delete-modal__body">
          <p>删除后，这个自定义 Agent 会从构建列表中消失。</p>
          <p>历史运行记录、审计证据、OpenAPI 调用日志和已产生的版本记录仍会保留，便于后续追溯。</p>
        </div>
        <div className="cici-modal__footer">
          <button type="button" className="cici-btn" onClick={() => setDeleteTarget(null)} disabled={isDeletingAgent}>取消</button>
          <button type="button" className="cici-btn cici-btn--danger" onClick={() => void deleteAgent()} disabled={isDeletingAgent}>
            {isDeletingAgent ? "删除中..." : "确认删除"}
          </button>
        </div>
      </div>
    </div>
  ) : null;

  if (pageMode === "list") {
    return (
      <>
        {renderLibraryPanel(true)}
        {renderDeleteModal()}
      </>
    );
  }

  return (
    <>
      <section className="cici-builder cici-builder--full">
        <section className="cici-builder__guide" aria-label="智能体构建说明">
          <h2 className="cici-builder__guide-title">智能体构建说明</h2>
          <p>
            构建流程建议按「保存草稿 → 智能体编译 → 发布版本」执行：保存草稿会把 Agent 定义、Spec、知识库/工具/渠道绑定、
            发布配置与 Skill 绑定统一落盘，确保配置基线可追溯。
          </p>
          <p>
            智能体编译会先自动保存草稿，再基于当前草稿生成流程代码、Manifest、流程图和编译摘要；系统会校验编译输入是否发生变化，
            若无变化则提示“未发生变化”且不新增版本，若有变化才生成新版本并写入版本历史（最近 10 条）。
          </p>
          <p>
            发布版本默认禁用，只有当最近一次智能体编译检测到变化并成功生成新版本后才可发布；发布成功后按钮会重新锁定，需再次完成
            “有变化编译”后方可发布下一版。回滚版本会将线上版本指针切回历史版本，不会自动覆盖当前草稿内容。
          </p>
        </section>
        <header className="cici-builder__header">
          <div className="cici-builder__header-main">
            <div className="cici-builder__title-row">
              <h1 className="cici-builder__title">{draft.name}</h1>
              <div className="cici-builder__meta cici-builder__meta--inline">
                {selectedAgent?.builtin ? <span className="cici-builder__meta-chip">系统内置默认智能体</span> : null}
                <span className="cici-builder__meta-chip">线上版本：{publishedVersionNo != null ? `v${publishedVersionNo}` : "未发布"}</span>
              </div>
            </div>
          </div>
          <div className="cici-builder__header-actions">
            <div className="cici-builder__header-actions-row">
              {onBackToList ? (
                <button type="button" className="cici-builder__action cici-builder__action--ghost" onClick={onBackToList}>
                  返回列表
                </button>
              ) : null}
              <button
                type="button"
                className="cici-builder__action cici-builder__action--ghost cici-builder__action--doc"
                onClick={() => setOpenApiDocsOpen(true)}
                disabled={!selectedAgentId}
                title={!selectedAgentId ? "请先保存或选择一个 Agent。" : undefined}
              >
                开放API文档
              </button>
              {selectedAgentAccess.canManage ? (
                <button
                  type="button"
                  className="cici-builder__action cici-builder__action--ghost"
                  onClick={() => setAccessDialogOpen(true)}
                  disabled={!selectedAgentId}
                >
                  权限管理
                </button>
              ) : null}
              <button
                type="button"
                className="cici-builder__action cici-builder__action--ghost"
                onClick={() => void saveFramework()}
                disabled={isSaving || !hasDraftChanges || !canEditSelectedAgent}
                title={!canEditSelectedAgent ? "当前账号没有编辑权限。" : !hasDraftChanges ? "当前内容无变化，无需保存草稿。" : undefined}
              >
                {isSaving ? "保存中…" : "保存草稿"}
              </button>
              <button type="button" className="cici-builder__action cici-builder__action--ghost" onClick={() => void rollbackToPreviousVersion()} disabled={isPublishing}>
                {isPublishing ? "处理中…" : "回滚版本"}
              </button>
              <button
                type="button"
                className="cici-builder__action cici-builder__action--ghost"
                onClick={() => void compileWorkflow()}
                disabled={isCompiling || !canEditSelectedAgent}
                title={!canEditSelectedAgent ? "当前账号没有编辑权限。" : !compileNeedsRebuild ? "当前编译输入无变化，编译后将提示无变化且不新增版本。" : undefined}
              >
                {isCompiling ? "编译中…" : "智能体编译"}
              </button>
              <button
                type="button"
                className="cici-builder__action cici-builder__action--primary"
                onClick={() => void publishLatestVersion()}
                disabled={publishBlocked || !canPublishSelectedAgent}
                title={!canPublishSelectedAgent ? "当前账号没有发布权限。" : publishBlockedTitle}
              >
                {isPublishing ? "处理中…" : "发布版本"}
              </button>
            </div>
          </div>
        </header>

        <div className={`cici-builder__toast${noticeVisible ? " is-visible" : ""}`} role="status" aria-live="polite">
          {notice}
        </div>

        <div className="cici-builder__grid">
          <section className="cici-builder-card cici-builder-card--wide cici-builder-editor-card">
            <div className="cici-builder-card__head cici-builder-card__head--editor">
              <h2>Agent 定义</h2>
              <span>Identity</span>
            </div>

            <div className="cici-builder-editor-grid">
              <section className="cici-builder-editor-section">
                <label className="cici-builder-field">
                  {renderFieldTitle("Agent 名称", true, "用于身份标识与编译摘要")}
                  <input value={draft.name} onChange={(event) => updateDraft("name", event.target.value)} placeholder="例如：售前跟进 Agent" />
                </label>

                <div className="cici-builder-avatar-policy-row">
                  <div className="cici-builder-field">
                    {renderFieldTitle("智能体头像", false, "用于工作台、会话消息和智能体档案展示")}
                    <div className="cici-builder-avatar-row">
                      <AvatarView
                        src={draft.avatarBase64}
                        fallback={getDisplayInitial(draft.name || "A", "A")}
                        className="cici-builder-avatar-preview"
                        alt={`${draft.name || "Agent"} 头像`}
                      />
                      <div className="cici-builder-avatar-actions">
                        <label className="cici-builder__action cici-builder__action--ghost cici-builder-avatar-upload">
                          上传图片
                          <input
                            type="file"
                            accept="image/png,image/jpeg,image/webp"
                            onChange={(event) => {
                              const file = event.target.files?.[0];
                              event.currentTarget.value = "";
                              if (!file) return;
                              void beginAvatarCrop(file);
                            }}
                          />
                        </label>
                        <button
                          type="button"
                          className="cici-builder__action cici-builder__action--ghost"
                          onClick={() => updateDraft("avatarBase64", "")}
                        >
                          清除头像
                        </button>
                      </div>
                    </div>
                  </div>
                  <div className="cici-builder-field cici-builder-field--policy-inline">
                    {renderFieldTitle("策略开关", true, "控制安全等级与自动执行策略")}
                    <div className="cici-builder-choice-row">
                      <button type="button" className={`cici-choice-chip${draft.safetyLevel === "balanced" ? " is-active" : ""}`} onClick={() => updateDraft("safetyLevel", "balanced")}>平衡模式</button>
                      <button type="button" className={`cici-choice-chip${draft.safetyLevel === "strict" ? " is-active" : ""}`} onClick={() => updateDraft("safetyLevel", "strict")}>严格审批</button>
                      <button type="button" className={`cici-choice-chip${draft.executionMode === "copilot" ? " is-active" : ""}`} onClick={() => updateDraft("executionMode", "copilot")}>协作副驾</button>
                      <button type="button" className={`cici-choice-chip${draft.executionMode === "auto" ? " is-active" : ""}`} onClick={() => updateDraft("executionMode", "auto")}>自动执行</button>
                    </div>
                  </div>
                </div>

                <label className="cici-builder-field">
                  {renderFieldTitle("开场白", false, "仅用于会话欢迎文案展示")}
                  <textarea rows={3} value={draft.greeting} onChange={(event) => updateDraft("greeting", event.target.value)} placeholder="用户首次进入时看到的欢迎语。" />
                </label>
                <label className="cici-builder-field">
                  {renderFieldTitle("业务定位", false, "描述信息，参与编译元数据与版本比对")}
                  <textarea rows={3} value={draft.summary} onChange={(event) => updateDraft("summary", event.target.value)} placeholder="描述这个 Agent 负责解决什么问题。" />
                </label>
                <label className="cici-builder-field">
                  {renderFieldTitle("人工兜底规则", true, "命中条件时触发转人工")}
                  <textarea rows={3} value={draft.handoffRule} onChange={(event) => updateDraft("handoffRule", event.target.value)} placeholder="定义何时必须转人工或升级审批。" />
                </label>
                <label className="cici-builder-field">
                  {renderFieldTitle("发布备注（可选）", false, "仅用于版本说明与追溯")}
                  <input
                    value={draft.version}
                    onChange={(event) => updateDraft("version", event.target.value)}
                    placeholder="例如：Q2-审批优化、飞书灰度"
                  />
                </label>
              </section>

              <section className="cici-builder-editor-section cici-builder-editor-section--prompt">
                <label className="cici-builder-field cici-builder-field--grow">
                  {renderFieldTitle("系统提示词", true, "直接影响执行策略与输出风格")}
                  <textarea
                    className="cici-builder-editor__prompt"
                    rows={18}
                    value={draft.systemPrompt}
                    onChange={(event) => updateDraft("systemPrompt", event.target.value)}
                    placeholder="告诉智能体如何回答、何时调用工具、何时转人工。"
                  />
                </label>
                <p className="cici-builder-model-governance-note">
                  {AGENT_MODEL_GOVERNANCE_NOTICE}
                </p>
              </section>
            </div>
          </section>

          <div className="cici-builder-composer cici-builder-card--wide">
            <section className="cici-builder-card cici-builder-card--spec">
              <div className="cici-builder-card__head">
                <h2>自然语言流程 Spec</h2>
                <span>Spec</span>
              </div>
              <div className="cici-builder-card__body cici-builder-card__body--spec">
                <label className="cici-builder-field cici-builder-field--grow">
                  {renderFieldTitle("流程描述", true, "编译主输入，决定流程与触发器")}
                  <textarea className="cici-builder-spec__editor" rows={12} value={draft.specText} onChange={(event) => updateDraft("specText", event.target.value)} placeholder="用自然语言写清楚角色、判断条件、工具调用顺序、转人工规则和输出要求。" />
                </label>
              </div>
            </section>

            <div className="cici-builder-composer__side">
              <section className="cici-builder-card cici-builder-card--stacked cici-builder-resource">
                <div className="cici-builder-card__head cici-builder-resource__head">
                  <h2>Skill 范围</h2>
                  <div className="cici-builder-resource__meta">
                    <span className="cici-builder-resource__count">
                      {draft.skillBindings.filter((item) => item.enabled).length}/{draft.skillBindings.length} 启用
                    </span>
                    <button
                      type="button"
                      className="cici-builder-resource__add"
                      onClick={() => openPicker("skill")}
                    >
                      + 添加
                    </button>
                  </div>
                </div>
                <div className="cici-builder-resource__list">
                  {draft.skillBindings.length === 0 ? (
                    <div className="cici-builder-empty">尚未挂载 Skill，点击「添加」选择已启用的技能资产。</div>
                  ) : null}
                  {draft.skillBindings.map((binding) => (
                    <div
                      key={binding.skillCode}
                      className={`cici-builder-resource__row${binding.enabled ? "" : " is-disabled"}`}
                      title={`${binding.skillName} · ${binding.skillCode} · ${binding.activationMode || "always-on"}`}
                    >
                      <div className="cici-builder-resource__row-main">
                        <span className="cici-builder-resource__icon cici-builder-resource__icon--skill" aria-hidden="true">
                          {binding.skillName?.slice(0, 1).toUpperCase() || "S"}
                        </span>
                        <div className="cici-builder-resource__row-text">
                          <strong>{binding.skillName}</strong>
                        </div>
                      </div>
                      <div className="cici-builder-resource__row-actions">
                        <span className="cici-builder-badge">{binding.riskLevel}</span>
                        <select
                          className="cici-builder-resource__select"
                          value={binding.activationMode}
                          onChange={(event) => updateSkillBinding(binding.skillCode, {
                            activationMode: event.target.value as AgentSkillBindingDraft["activationMode"],
                          })}
                        >
                          <option value="always-on">always-on</option>
                          <option value="intent-route">intent-route</option>
                          <option value="manual">manual</option>
                        </select>
                        <button
                          type="button"
                          className={`cici-builder-switch${binding.enabled ? " is-on" : ""}`}
                          onClick={() => updateSkillBinding(binding.skillCode, { enabled: !binding.enabled })}
                          aria-label={binding.enabled ? "停用" : "启用"}
                        >
                          <span />
                        </button>
                        <button
                          type="button"
                          className="cici-builder-resource__icon-btn"
                          onClick={() => removeSkillBinding(binding.skillCode)}
                          aria-label="移除"
                        >
                          ×
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              </section>

              <section className="cici-builder-card cici-builder-card--stacked cici-builder-resource">
                <div className="cici-builder-card__head cici-builder-resource__head">
                  <h2>知识挂载</h2>
                  <div className="cici-builder-resource__meta">
                    <span className="cici-builder-resource__count">已挂载 {draft.knowledgeBaseIds.length}</span>
                    <button
                      type="button"
                      className="cici-builder-resource__add"
                      onClick={() => openPicker("kb")}
                    >
                      + 添加
                    </button>
                  </div>
                </div>
                <div className="cici-builder-resource__list cici-builder-resource__list--two-cols">
                  {draft.knowledgeBaseIds.length === 0 ? (
                    <div className="cici-builder-empty">尚未挂载知识库，点击「添加」选择要引入的知识资产。</div>
                  ) : null}
                  {draft.knowledgeBaseIds.map((id) => {
                    const kb = kbs.find((item) => item.id === id);
                    if (!kb) return null;
                    const kbTip = kb.description || "已接入知识库，可作为检索上下文。";
                    return (
                      <div key={id} className="cici-builder-resource__row" title={kbTip}>
                        <div className="cici-builder-resource__row-main">
                          <span className="cici-builder-resource__icon cici-builder-resource__icon--kb" aria-hidden="true">KB</span>
                          <div className="cici-builder-resource__row-text">
                            <strong>{kb.name}</strong>
                          </div>
                        </div>
                        <div className="cici-builder-resource__row-actions">
                          <button
                            type="button"
                            className="cici-builder-resource__icon-btn"
                            onClick={() => toggleCollectionValue("knowledgeBaseIds", id)}
                            aria-label="移除"
                          >
                            ×
                          </button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </section>

              <section className="cici-builder-card cici-builder-card--stacked cici-builder-resource">
                <div className="cici-builder-card__head cici-builder-resource__head">
                  <h2>工具白名单</h2>
                  <div className="cici-builder-resource__meta">
                    <span className="cici-builder-resource__count">
                      {draft.toolIds.length}/{toolCatalog.length} 启用
                    </span>
                    <button
                      type="button"
                      className="cici-builder-resource__add"
                      onClick={() => openPicker("tool")}
                    >
                      + 添加
                    </button>
                  </div>
                </div>
                <div className="cici-builder-resource__list cici-builder-resource__list--two-cols">
                  {draft.toolIds.length === 0 ? (
                    <div className="cici-builder-empty">尚未加入任何工具，点击「添加」选择允许调用的工具。</div>
                  ) : null}
                  {draft.toolIds.map((id) => {
                    const tool = toolCatalog.find((item) => item.id === id);
                    const title = tool?.name ?? id;
                    const description = tool?.description ?? "MCP 工具（待同步到工具目录）";
                    const level = tool?.level ?? "MCP";
                    const isMcpTool = level.toUpperCase() === "MCP";
                    return (
                      <div
                        key={id}
                        className="cici-builder-resource__row"
                        title={`${title} · ${id}\n${description}`}
                      >
                        <div className="cici-builder-resource__row-main">
                          <span
                            className={`cici-builder-resource__icon ${
                              isMcpTool ? "cici-builder-resource__icon--mcp" : "cici-builder-resource__icon--tool"
                            }`}
                            aria-hidden="true"
                          >
                            {isMcpTool ? (
                              <svg viewBox="0 0 24 24" width="16" height="16" fill="none">
                                <path
                                  d="M8.5 9.5 11.8 6.2a2.4 2.4 0 1 1 3.4 3.4l-4.5 4.5a2.6 2.6 0 0 1-3.7-3.7l5.1-5.1"
                                  stroke="currentColor"
                                  strokeWidth="1.8"
                                  strokeLinecap="round"
                                  strokeLinejoin="round"
                                />
                                <path
                                  d="m15.5 14.5-3.3 3.3a2.4 2.4 0 1 1-3.4-3.4l4.5-4.5a2.6 2.6 0 0 1 3.7 3.7l-5.1 5.1"
                                  stroke="currentColor"
                                  strokeWidth="1.8"
                                  strokeLinecap="round"
                                  strokeLinejoin="round"
                                />
                              </svg>
                            ) : "T"}
                          </span>
                          <div className="cici-builder-resource__row-text">
                            <strong>{title}</strong>
                          </div>
                        </div>
                        <div className="cici-builder-resource__row-actions">
                          <span className="cici-builder-badge">{level}</span>
                          <button
                            type="button"
                            className="cici-builder-resource__icon-btn"
                            onClick={() => toggleCollectionValue("toolIds", id)}
                            aria-label="移除"
                          >
                            ×
                          </button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </section>
            </div>
          </div>

          <section className="cici-builder-card cici-builder-card--wide cici-builder-compile-panel">
            <div className="cici-builder-card__head cici-builder-card__head--compile">
              <div className="cici-builder-card__head-row">
                <h2>版本控制与交付</h2>
                <span>Lifecycle</span>
              </div>
              <div className="cici-builder-tabs cici-builder-tabs--compile">
                {AGENT_BUILDER_LIFECYCLE_TABS.map((tab) => (
                  <button
                    key={tab.id}
                    type="button"
                    className={`cici-builder-tabs__item${activeCompileTab === tab.id ? " is-active" : ""}`}
                    onClick={() => setActiveCompileTab(tab.id)}
                  >
                    {tab.label}
                  </button>
                ))}
              </div>
            </div>
            <div className="cici-builder-compile cici-builder-compile--stacked">
              {activeCompileTab === "preview" ? (
                <div className="cici-builder-graph cici-builder-graph--full">
                  <div className="cici-builder-graph__modebar">
                    <div className="cici-product-mode-switch cici-builder-graph__mode-tabs" role="tablist" aria-label="编译结果视图">
                      <button
                        type="button"
                        role="tab"
                        aria-selected={previewMode === "workflow"}
                        className={previewMode === "workflow" ? "is-active" : ""}
                        onClick={() => setPreviewMode("workflow")}
                      >
                        工作流
                      </button>
                      <button
                        type="button"
                        role="tab"
                        aria-selected={previewMode === "skill-dag"}
                        className={previewMode === "skill-dag" ? "is-active" : ""}
                        onClick={() => {
                          setPreviewMode("skill-dag");
                          if (!skillDagGraph && !skillDagLoading && !skillDagError) {
                            void loadAgentSkillDag(skillDagVersionNo);
                          }
                        }}
                      >
                        Skill 依赖
                      </button>
                    </div>
                    <span className="cici-builder-graph__version-note">
                      {previewMode === "workflow"
                        ? "当前编译结果"
                        : skillDagGraph?.scope.versionNo != null
                          ? `工作流 v${skillDagGraph.scope.versionNo}`
                          : skillDagVersionNo != null ? `工作流 v${skillDagVersionNo}` : "当前生效版本"}
                    </span>
                  </div>
                  {previewMode === "workflow" ? (
                    <WorkflowPreviewCanvas
                      preview={compileArtifact.preview}
                      activeNodeIds={debugTrace?.activeNodeIds ?? []}
                      startChannelSummary={channelTriggerSummaryLine(draft)}
                      startScheduleBadge={isAgentPublished ? "定时 ×1" : null}
                      onInspectStartTriggers={openTriggersFromGraph}
                    />
                  ) : (
                    <SkillDependencyGraph
                      className="skill-dag--embedded"
                      graph={skillDagGraph?.summary.skillCount === 0
                        ? { ...skillDagGraph, nodes: [], edges: [] }
                        : skillDagGraph}
                      loading={skillDagLoading}
                      error={skillDagError}
                      emptyMessage="当前版本未解析到 Skill 依赖。"
                      ariaLabel={`${draft.name || "当前 Agent"} Skill 依赖图`}
                      onRetry={() => void loadAgentSkillDag(skillDagVersionNo)}
                    />
                  )}
                </div>
              ) : null}

              {activeCompileTab === "triggers" ? renderTriggersRuntimePanel() : null}

              {activeCompileTab === "executions" ? renderExecutionsRuntimePanel() : null}

              {activeCompileTab === "history" ? renderVersionHistoryPanel() : null}

              {activeCompileTab === "evaluation" ? (
                <div className="cici-builder-publish-stack cici-builder-evaluation-workspace">
                  <div className="cici-builder-evaluation-workspace__intro">
                    <div>
                      <h2>版本质量与发布门禁</h2>
                      <p>运行当前候选版本的适用评测集，检查平台基线、标准应用、行业包和组织私有回归用例。</p>
                    </div>
                    <a className="cici-builder-evaluation-workspace__link" href="/admin/evaluation">打开 AI 质量中心</a>
                  </div>
                  {renderProductionGatePanel()}
                  {renderEvaluationGatePanel()}
                </div>
              ) : null}

              {activeCompileTab === "publish" ? (
                <div className="cici-builder-publish-stack">
                  <div className="cici-builder-publish-hub">
                    <aside className="cici-builder-publish-menu" aria-label="发布渠道菜单">
                      {CHANNEL_OPTIONS.map((channel) => {
                        const enabled = draft.channels.includes(channel.id);
                        return (
                          <button
                            key={channel.id}
                            type="button"
                            className={`cici-builder-publish-menu__item${activePublishChannel === channel.id ? " is-active" : ""}`}
                            onClick={() => setActivePublishChannel(channel.id)}
                          >
                            <span className="cici-builder-publish-menu__label-row">
                              <strong>{channel.label}</strong>
                              <span className={`cici-builder-publish-menu__status${enabled ? " is-enabled" : ""}`}>
                                {enabled ? "已启用" : "未启用"}
                              </span>
                            </span>
                            <small>{channel.tone}</small>
                          </button>
                        );
                      })}
                    </aside>

                    <section className="cici-builder-publish-panel">
                      <div className="cici-builder-publish-panel__head">
                        <div>
                          <span className="cici-builder-publish-panel__eyebrow">Publish Channel</span>
                          <h2>{activePublishMeta.label}</h2>
                        </div>
                        <span>{activePublishEnabled ? "已纳入当前 Agent 的发布计划" : "当前未纳入发布计划"}</span>
                      </div>
                      {renderPublishChannelPanel()}
                    </section>
                  </div>
                </div>
              ) : null}

              {activeCompileTab === "summary" ? (
                <div className="cici-builder-compile__summary-grid">
                  <div className="cici-builder-compile__block">
                    <div className="cici-builder-compile__label">编译摘要</div>
                    <ul className="cici-builder-compile__list">
                      {compileArtifact.summary.map((line) => <li key={line}>{line}</li>)}
                    </ul>
                  </div>
                  <div className="cici-builder-compile__block">
                    <div className="cici-builder-compile__label">依赖推断</div>
                    <div className="cici-builder-compile__deps">
                      {compileArtifact.dependencies.map((item) => <span key={item} className="cici-builder-compile__dep">{item}</span>)}
                    </div>
                  </div>
                  <div className="cici-builder-compile__block">
                    <div className="cici-builder-compile__label">流程节点</div>
                    <ul className="cici-builder-compile__list">
                      {compileArtifact.preview.nodes.map((node) => (
                        <li key={node.id}>
                          <strong>{node.label}</strong>
                          {` · ${node.detail}`}
                        </li>
                      ))}
                    </ul>
                  </div>
                  <div className="cici-builder-compile__block">
                    <div className="cici-builder-compile__label">风险提示</div>
                    {compileArtifact.warnings.length > 0 ? (
                      <ul className="cici-builder-compile__warnings">
                        {compileArtifact.warnings.map((line) => <li key={line}>{line}</li>)}
                      </ul>
                    ) : (
                      <div className="cici-builder-empty">当前规则未发现显著风险，仍建议在发布前进行调试验证。</div>
                    )}
                  </div>
                </div>
              ) : null}

              {activeCompileTab === "code" ? (
                <div className="cici-builder-code">
                  <div className="cici-builder-code__title">
                    workflow.ts
                    <span className="cici-builder-code__meta">编译执行体</span>
                  </div>
                  <pre>{compileArtifact.code}</pre>
                </div>
              ) : null}

              {activeCompileTab === "manifest" ? (
                <div className="cici-builder-code cici-builder-code--manifest">
                  <div className="cici-builder-code__title">
                    workflow.manifest.json
                    <span className="cici-builder-code__meta">治理元数据</span>
                  </div>
                  <pre>{compileArtifact.manifest}</pre>
                </div>
              ) : null}

              {activeCompileTab === "debug" ? (
                <div className="cici-builder-debug">
                  <div className="cici-builder-card__head cici-builder-debug__head">
                    <h2>调试运行</h2>
                    <span>Debug</span>
                  </div>
                  <label className="cici-builder-field">
                    <span>测试输入</span>
                    <textarea rows={3} value={debugInput} onChange={(event) => setDebugInput(event.target.value)} placeholder="输入一段测试消息，预览本次会走到哪些流程节点。" />
                  </label>
                  <p className="cici-builder-debug__hint">
                    每次试运行结束会在「执行记录」中追加一条，来源为「试运行」（与生产触发共用列表，见 FEAT-004 方案 A）。
                  </p>
                  <div className="cici-builder-debug__actions">
                    <button type="button" className="cici-builder__action cici-builder__action--primary" onClick={() => void runDebug()} disabled={isDebugging}>
                      {isDebugging ? "试运行中…" : "试运行并高亮路径"}
                    </button>
                    <button
                      type="button"
                      className="cici-builder__action cici-builder__action--ghost"
                      onClick={() => {
                        setExecutionFilter("try_run");
                        setActiveCompileTab("executions");
                      }}
                    >
                      查看试运行记录
                    </button>
                    <span className="cici-builder-debug__status">{debugTrace?.outcomeLabel ?? "等待试运行"}</span>
                  </div>
                  <div className="cici-builder-debug__trace">
                    {(debugTrace?.activeNodeIds ?? []).map((nodeId) => {
                      const node = compileArtifact.preview.nodes.find((item) => item.id === nodeId);
                      return node ? <span key={node.id} className="cici-builder-debug__chip">{node.label}</span> : null;
                    })}
                  </div>
                  {debugTrace?.activeSkills && debugTrace.activeSkills.length > 0 ? (
                    <div className="cici-builder-debug__trace">
                      {debugTrace.activeSkills.map((skill) => (
                        <span key={skill} className="cici-builder-debug__chip">{skill}</span>
                      ))}
                    </div>
                  ) : null}
                  {debugTrace?.governanceChips && debugTrace.governanceChips.length > 0 ? (
                    <div className="cici-builder-debug__trace">
                      {debugTrace.governanceChips.map((item) => (
                        <span key={item} className="cici-builder-debug__chip">{item}</span>
                      ))}
                    </div>
                  ) : null}
                  {debugTrace?.skillResolutionChain && debugTrace.skillResolutionChain.length > 0 ? (
                    <section className="cici-builder-debug__skill-chain" aria-label="Skill 解析链路">
                      <header className="cici-builder-debug__skill-chain-head">
                        <h3>Skill 解析链路</h3>
                        <span>{debugTrace.skillResolutionChain.length} 个已解析 Skill</span>
                      </header>
                      <div className="cici-builder-debug__skill-rows">
                        {debugTrace.skillResolutionChain.map((item) => (
                          <div className="cici-builder-debug__skill-row" key={item.id}>
                            <div className="cici-builder-debug__skill-name">
                              <strong>{item.name}</strong>
                              <span>{item.code}</span>
                            </div>
                            <dl>
                              <div><dt>钉住版本</dt><dd>{item.versionLabel}</dd></div>
                              <div><dt>当前绑定风险</dt><dd>{item.riskLabel}</dd></div>
                              <div><dt>引用来源</dt><dd>{item.referenceLabel}</dd></div>
                              <div><dt>激活方式</dt><dd>{item.activationLabel}</dd></div>
                            </dl>
                          </div>
                        ))}
                      </div>
                      <div className="cici-builder-debug__effective-boundaries">
                        <div>
                          <span>最终有效工具</span>
                          <strong>{debugTrace.effectiveToolNames?.length ? debugTrace.effectiveToolNames.join("、") : "无"}</strong>
                        </div>
                        <div>
                          <span>最终有效知识库</span>
                          <strong>{debugTrace.effectiveKnowledgeBaseIds?.length ? debugTrace.effectiveKnowledgeBaseIds.join("、") : "无"}</strong>
                        </div>
                      </div>
                    </section>
                  ) : null}
                  <ul className="cici-builder-compile__list">
                    {(debugTrace?.notes ?? ["点击“试运行并高亮路径”后，这里会解释为什么命中当前节点。"]).map((line) => (
                      <li key={line}>{line}</li>
                    ))}
                  </ul>
                </div>
              ) : null}
            </div>
          </section>
        </div>
      </section>

      <AgentOpenApiDocsDialog
        open={openApiDocsOpen}
        agentId={selectedAgentId}
        agentName={draft.name}
        published={publishedVersionNo != null}
        apiChannelEnabled={draft.channels.includes("api")}
        baseUrl={openApiBaseUrl}
        keyManagementAvailable={Boolean(selectedAgentId && token && selectedAgentAccess.canOpenApi)}
        onOpenKeyManagement={() => setOpenApiKeysOpen(true)}
        onClose={() => setOpenApiDocsOpen(false)}
      />

      <AgentOpenApiKeysDialog
        open={openApiKeysOpen}
        agentId={selectedAgentId}
        agentName={draft.name}
        token={token}
        onClose={() => setOpenApiKeysOpen(false)}
      />
      <AgentAccessManagementDialog
        open={accessDialogOpen}
        token={token}
        agentId={selectedAgentId}
        agentName={draft.name}
        onClose={() => setAccessDialogOpen(false)}
      />

      {pickerOpen ? (
        <div
          className="cici-modal-backdrop"
          role="dialog"
          aria-modal="true"
          onClick={(event) => {
            if (event.target === event.currentTarget) setPickerOpen(null);
          }}
        >
          <div className="cici-modal cici-builder-picker">
            <div className="cici-modal__header">
              <h3>{pickerTitle}</h3>
              <button
                type="button"
                className="cici-modal__close"
                onClick={() => setPickerOpen(null)}
                aria-label="关闭"
              >
                ×
              </button>
            </div>
            {pickerIntro ? <p className="cici-modal__intro">{pickerIntro}</p> : null}
            {pickerOpen === "tool" ? (
              <div className="cici-builder-picker__tabs" role="tablist" aria-label="工具来源">
                <button
                  type="button"
                  role="tab"
                  aria-selected={pickerToolTab === "tool"}
                  className={`cici-builder-picker__tab${pickerToolTab === "tool" ? " is-active" : ""}`}
                  onClick={() => setPickerToolTab("tool")}
                >
                  工具
                </button>
                <button
                  type="button"
                  role="tab"
                  aria-selected={pickerToolTab === "mcp"}
                  className={`cici-builder-picker__tab${pickerToolTab === "mcp" ? " is-active" : ""}`}
                  onClick={() => setPickerToolTab("mcp")}
                >
                  MCP
                </button>
              </div>
            ) : null}
            <div className="cici-builder-picker__list">
              {pickerOpen === "tool" && pickerToolTab === "mcp" ? (
                <>
                  {mcpServers.length === 0 ? (
                    <div className="cici-modal__empty">暂无已配置 MCP 服务器。</div>
                  ) : null}
                  {mcpServers.map((server) => {
                    const expanded = expandedMcpServerIds.includes(server.id);
                    const loading = Boolean(mcpServerLoading[server.id]);
                    const tools = mcpToolsByServer[server.id] ?? [];
                    const selectedCount = tools.reduce((count, tool) => (
                      pickerSelection.includes(tool.id) ? count + 1 : count
                    ), 0);
                    const totalCount = server.toolCacheCount > 0 ? server.toolCacheCount : tools.length;
                    return (
                      <section key={server.id} className="cici-builder-picker__group">
                        <button
                          type="button"
                          className="cici-builder-picker__group-toggle"
                          onClick={() => toggleMcpServerExpanded(server)}
                          aria-expanded={expanded}
                        >
                          <span>{server.name}</span>
                          <span className="cici-builder-picker__group-meta">
                            <span>{selectedCount} / {totalCount}</span>
                            <span>{server.enabled ? "已启用" : "未启用"}</span>
                          </span>
                        </button>
                        {expanded ? (
                          <div className="cici-builder-picker__group-body">
                            {loading ? <div className="cici-modal__empty">正在加载工具…</div> : null}
                            {!loading && tools.length === 0 ? (
                              <div className="cici-modal__empty">该服务器暂无缓存工具，请先到 MCP 管理页手动刷新。</div>
                            ) : null}
                            {!loading && tools.map((item) => {
                              const checked = pickerSelection.includes(item.id);
                              return (
                                <label
                                  key={`${server.id}-${item.id}`}
                                  className={`cici-builder-picker__item${checked ? " is-checked" : ""}`}
                                >
                                  <input
                                    type="checkbox"
                                    checked={checked}
                                    onChange={(event) => togglePickerItem(item.id, event.target.checked)}
                                  />
                                  <span className="cici-builder-picker__text">
                                    <strong>{item.name}</strong>
                                    {item.description ? <small>{item.description}</small> : null}
                                  </span>
                                  {item.level ? <span className="cici-builder-badge">{item.level}</span> : null}
                                </label>
                              );
                            })}
                          </div>
                        ) : null}
                      </section>
                    );
                  })}
                </>
              ) : null}
              {(pickerOpen !== "tool" || pickerToolTab === "tool") && pickerItems.length === 0 ? (
                <div className="cici-modal__empty">
                  {pickerOpen === "skill" ? "暂无可用技能，请先在技能中心创建/启用。" :
                    pickerOpen === "kb" ? "当前组织暂无知识库，可先在管理端完成导入。" :
                      "暂无可选工具。"}
                </div>
              ) : null}
              {(pickerOpen !== "tool" || pickerToolTab === "tool") && pickerItems.map((item) => {
                const checked = pickerSelection.includes(item.key);
                return (
                  <label
                    key={item.key}
                    className={`cici-builder-picker__item${checked ? " is-checked" : ""}`}
                  >
                    <input
                      type="checkbox"
                      checked={checked}
                      onChange={(event) => togglePickerItem(item.key, event.target.checked)}
                    />
                    <span className="cici-builder-picker__text">
                      <strong>{item.title}</strong>
                      {item.subtitle ? <small>{item.subtitle}</small> : null}
                    </span>
                    {item.tag ? <span className="cici-builder-badge">{item.tag}</span> : null}
                  </label>
                );
              })}
            </div>
            <div className="cici-modal__footer">
              <button type="button" className="cici-btn" onClick={() => setPickerOpen(null)}>取消</button>
              <button type="button" className="cici-btn cici-btn--primary" onClick={confirmPicker}>
                确认 ({pickerSelection.length})
              </button>
            </div>
          </div>
        </div>
      ) : null}

      {renderDeleteModal()}

      <AvatarCropperDialog
        open={Boolean(avatarCropSource)}
        sourceDataUrl={avatarCropSource}
        title="裁剪智能体头像"
        onCancel={() => setAvatarCropSource("")}
        onConfirm={async (avatarBase64) => {
          updateDraft("avatarBase64", avatarBase64);
          setAvatarCropSource("");
          setNotice("");
        }}
      />
    </>
  );
}
