import { FormEvent, KeyboardEvent, PointerEvent as ReactPointerEvent, ReactNode, useEffect, useMemo, useRef, useState } from "react";
import {
  streamAiChat,
  streamSessionUpdates,
  type StreamToolResultEvent,
  type StreamToolCallEvent,
  type StreamPhaseEvent,
} from "../chat/streamChat";
import { BootLoginConversationDemo, BootLoginDataStream } from "../components/BootLoginEffects";
import AvatarView from "../components/AvatarView";
import ChatMarkdown from "../components/ChatMarkdown";
import { LS_ASSISTANT_TOKEN } from "../constants";
import { MeetingMinutesPanel } from "../meeting/MeetingMinutesPanel";
import AppVersionBadge from "../shared/AppVersionBadge";
import { getDisplayInitial } from "../shared/avatar";
import { useAsrVoiceInput } from "../shared/useAsrVoiceInput";
import { safeFetchJson } from "../utils/http";
import MyEmailAccountsModal from "./MyEmailAccountsModal";
import { CustomerInsightAppPanel } from "./customer-insight/CustomerInsightAppPanel";
import {
  appendAssistantDelta,
  assistantResponseNeedsUserFollowup,
  markTrailingAssistantModel,
  preserveAssistantModelNames,
  replaceTrailingAssistant,
  shouldKeepLocalStreamingMessages,
} from "./chatMessageState";
import {
  buildWorkbenchSessionId,
  createWorkbenchSessionId,
  isWorkbenchSessionIdForAgent,
} from "./workbenchSessions";
import { isMeetingMinutesStartCommand } from "./meetingMinutesCommand";
import { appendMeetingTranscriptSegment, speakerDisplayName } from "./meetingTranscript";

const FRONT_LOGIN_MODE_CONFIG: FrontLoginMode = "login_mode2";
const FRONT_LOGIN_USER_MODE_CONFIG: LoginMode = "agent";

type OrganizationOption = { orgId: string; orgName: string; memberId: string; roleCode: string; current?: boolean };
type AuthPayload = { token: string; orgId: string; orgName?: string; userId: string; memberId?: string; accountId?: string; roles: string[] };
type LoginPayload = AuthPayload & { requiresOrganizationSelection?: boolean; organizations?: OrganizationOption[] };
type ChatBubble = { role: "user" | "assistant"; content: string; time?: string; modelName?: string };
type KnowledgeBase = { id: number; name: string; description: string; status: string };
type MeProfile = {
  orgId?: string;
  orgName?: string;
  nickname?: string;
  mobile?: string;
  firstName?: string;
  lastName?: string;
  displayName?: string;
  email?: string;
  avatarBase64?: string;
};
type CurrentUserUpdatedDetail = {
  userId?: string;
  mobile?: string;
  nickname?: string;
  displayName?: string;
  avatarBase64?: string;
};
type WorkspaceTab = "chat" | "workbench" | "monitor" | "customers" | "crm" | "aiApps" | "settings" | "profile";
type LoginMode = "agent" | "human";
type FrontLoginMode = "login_mode1" | "login_mode2";
type LoginMode2CubePhase = "brand" | "loading";
type WorkbenchStateStatus = "处理中" | "检索中" | "等待确认" | "已完成" | "待命中";
type LoginMode2CubeFace = { className: string; image: string; label?: string; fit?: "cover" | "contain" };
type MeetingStatus = "idle" | "recording" | "stopping" | "summarizing" | "done" | "error";
type MeetingTranscriptSegment = {
  id: string;
  speakerId: string;
  speakerName: string;
  text: string;
  time: string;
  startMs?: number;
  endMs?: number;
};
type MeetingSpeakerEdit = { speakerId: string; lineId: string; value: string };

type AgentWorkspace = {
  id: string;
  name: string;
  avatar: string;
  avatarBase64?: string;
  category: "system" | "published";
  status: string;
  subtitle: string;
  description: string;
  channels: Array<ConversationThread["channel"]>;
  knowledgeMode: string;
  accent: string;
  pinned?: boolean;
};

type ConversationThread = {
  id: string;
  agentId: string;
  title: string;
  participantName: string;
  participantType: "employee" | "external" | "group";
  channel: "wechat" | "dingtalk" | "feishu" | "web";
  lastMessage: string;
  time: string;
  updatedAt?: string;
  unread: number;
  owner: string;
  summary: string;
  avatarUrl?: string;
};

type ConversationThreadPayload = {
  id: string;
  agentId?: string;
  title?: string;
  participantName?: string;
  participantType?: ConversationThread["participantType"];
  channel?: ConversationThread["channel"];
  lastMessage?: string;
  updatedAt?: string;
  unread?: number;
  owner?: string;
  summary?: string;
  avatarUrl?: string;
};

type ConversationMessagePayload = {
  role?: ChatBubble["role"];
  content?: string;
  createdAt?: string;
};

type WorkbenchDockAgent = {
  key: string;
  runtimeAgentId: string;
  name: string;
  label: string;
  short: string;
  avatarBase64?: string;
  color: string;
  stateMachine: WorkbenchStateMachine;
  messages: ChatBubble[];
};

type WorkbenchStateMachine = {
  status: WorkbenchStateStatus;
  previousTask: string;
  currentTask: string;
  nextTask: string;
  thoughts: string[];
};

type PublishedAgentPayload = {
  agentId: string;
  name: string;
  avatarBase64?: string | null;
  summary?: string | null;
  greeting?: string | null;
  builtin?: boolean;
  publishedVersionId?: number | null;
};

type WorkbenchMetric = { label: string; value: string };

type AiApplication = {
  code: "meeting-minutes" | "customer-insight";
  name: string;
  shortName: string;
  status: string;
  summary: string;
  description: string;
  meta: string;
};

type WorkbenchOverviewItem = {
  id: string;
  title: string;
  status: string;
  detail: string;
  prompt: string;
};

type WorkflowExecutionPayload = {
  id: number;
  routineKey?: string;
  triggerSource?: string;
  status?: string;
  scheduledAt?: string;
  finishedAt?: string;
  outputSummary?: string;
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
};

type AgentSkillBindingView = {
  skillId: number;
  skillCode: string;
  skillName: string;
  riskLevel?: string;
  activationMode?: string;
  activationCondition?: string;
  priority?: number;
  enabled: boolean;
  toolWhitelist?: string[];
  kbWhitelist?: string[];
  handoffRule?: string;
};

type UserQuickCommand = {
  id: number;
  title: string;
  promptText: string;
  sortOrder?: number;
  enabled?: boolean;
  createdAt?: string;
  updatedAt?: string;
};

const CHANNEL_LABELS: Record<ConversationThread["channel"], string> = {
  wechat: "企微",
  dingtalk: "钉钉",
  feishu: "飞书",
  web: "WebChat",
};

const DOCK_AGENT_COLORS = [
  "linear-gradient(135deg, #4a68f6, #6f88ff)",
  "linear-gradient(135deg, #ff9e67, #ffc370)",
  "linear-gradient(135deg, #22b7ab, #74dfd0)",
  "linear-gradient(135deg, #6672ef, #9eadff)",
  "linear-gradient(135deg, #f0b44d, #f7cf69)",
  "linear-gradient(135deg, #e05c7a, #ff8fa0)",
  "linear-gradient(135deg, #3bb56e, #5edd8f)",
];

const LOGIN_MODE2_ENTER_DELAY_MS = 3000;

function toWorkbenchDockAgent(agent: PublishedAgentPayload, colorIndex: number): WorkbenchDockAgent {
  const preset = WORKBENCH_DOCK_AGENTS.find((a) => a.key === agent.agentId);
  if (preset) {
    return {
      ...preset,
      avatarBase64: (agent.avatarBase64 ?? preset.avatarBase64 ?? "").trim(),
      stateMachine: { ...preset.stateMachine, thoughts: [...preset.stateMachine.thoughts] },
      messages: preset.messages.map((item) => ({ ...item })),
    };
  }
  const short = getDisplayInitial(agent.name ?? "A", "A").slice(0, 1);
  return {
    key: agent.agentId,
    runtimeAgentId: agent.agentId,
    name: agent.name ?? agent.agentId,
    label: agent.name ?? agent.agentId,
    short,
    avatarBase64: agent.avatarBase64 ?? "",
    color: DOCK_AGENT_COLORS[colorIndex % DOCK_AGENT_COLORS.length],
    stateMachine: {
      status: "待命中",
      previousTask: "—",
      currentTask: "等待任务",
      nextTask: "—",
      thoughts: ["已就绪，等待你的指令"],
    },
    messages: [],
  };
}

const AGENT_WORKSPACES: AgentWorkspace[] = [
  {
    id: "cici-system",
    name: "思思（CiCi）",
    avatar: "CiCi",
    category: "system",
    status: "系统内置",
    subtitle: "标准数字员工",
    description: "系统内置标准智能体，常驻智能体列表，可承接多渠道会话并复用统一知识与动作策略。",
    channels: ["wechat", "dingtalk", "feishu", "web"],
    knowledgeMode: "标准知识库策略",
    accent: "#5b7ff4",
    pinned: true,
  },
  {
    id: "sales-agent",
    name: "售前跟进 Agent",
    avatar: "售",
    category: "published",
    status: "已发布",
    subtitle: "报价与产品答疑",
    description: "面向售前与销售团队，负责客户问答、报价准备、人工转交建议。",
    channels: ["wechat", "dingtalk", "web"],
    knowledgeMode: "销售知识库 + CRM",
    accent: "#1ea672",
  },
  {
    id: "approval-agent",
    name: "审批推进 Agent",
    avatar: "审",
    category: "published",
    status: "已发布",
    subtitle: "流程催办与卡点识别",
    description: "聚焦审批待办、催办提醒和流程升级动作，服务内部协同场景。",
    channels: ["dingtalk", "feishu"],
    knowledgeMode: "制度规则 + 审批工具",
    accent: "#de8b3f",
  },
];

function formatConversationTime(updatedAt?: string) {
  if (!updatedAt) {
    return "";
  }
  const date = new Date(updatedAt);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  const now = new Date();
  const sameDay = date.toDateString() === now.toDateString();
  const yesterday = new Date(now);
  yesterday.setDate(now.getDate() - 1);
  if (date.toDateString() === yesterday.toDateString()) {
    return "昨天";
  }
  if (sameDay) {
    return new Intl.DateTimeFormat("zh-CN", {
      hour: "2-digit",
      minute: "2-digit",
      hour12: false,
    }).format(date);
  }
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
  }).format(date);
}

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
    case "feishu":
      return "飞书";
    case "wecom":
    case "wechat":
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
      return `${name} ${elapsed}`;
    })
    .join("；");
}

function formatTraceStepElapsed(ms?: number) {
  if (typeof ms !== "number" || !Number.isFinite(ms) || ms <= 0) return "0ms";
  return formatMonitorElapsed(ms);
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

function normalizeConversationThread(payload: ConversationThreadPayload): ConversationThread {
  const channel = payload.channel && payload.channel in CHANNEL_LABELS ? payload.channel : "web";
  const participantName = payload.participantName?.trim() || payload.title?.trim() || "未命名会话";
  const isWorkbenchSession = payload.id?.startsWith("workbench:");
  const fallbackTitleFromLastMessage = (payload.lastMessage?.trim() || "新工作台对话").slice(0, 24);
  const normalizedTitle = isWorkbenchSession && payload.title?.startsWith("会话 ")
    ? fallbackTitleFromLastMessage
    : (payload.title?.trim() || participantName);
  return {
    id: payload.id,
    agentId: payload.agentId?.trim() || "cici-system",
    title: normalizedTitle,
    participantName,
    participantType: payload.participantType ?? "external",
    channel,
    lastMessage: payload.lastMessage?.trim() || "暂无消息",
    time: formatConversationTime(payload.updatedAt),
    updatedAt: payload.updatedAt,
    unread: typeof payload.unread === "number" ? payload.unread : 0,
    owner: payload.owner?.trim() || "CiCi",
    summary: payload.summary?.trim() || "来自真实会话数据。",
    avatarUrl: payload.avatarUrl?.trim() || "",
  };
}

function normalizeConversationMessages(payloads: ConversationMessagePayload[]): ChatBubble[] {
  return payloads
    .map((item): ChatBubble => ({
      role: item.role === "user" ? "user" : "assistant",
      content: item.content?.trim() || "",
      time: formatConversationTime(item.createdAt),
    }))
    .filter((item) => item.content);
}

function createDraftConversationThread(
  sessionId: string,
  agentId: string,
  participantName: string,
  title = "新对话",
  avatarUrl = "",
): ConversationThread {
  const nowIso = new Date().toISOString();
  return {
    id: sessionId,
    agentId,
    title,
    participantName: participantName.trim() || "我",
    participantType: "employee",
    channel: "web",
    lastMessage: "等待首条消息",
    time: formatConversationTime(nowIso),
    updatedAt: nowIso,
    unread: 0,
    owner: "CiCi",
    summary: "新建会话，等待首条消息。",
    avatarUrl,
  };
}

function formatWorkbenchTime(date = new Date()) {
  return new Intl.DateTimeFormat("zh-CN", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(date);
}

const WORKBENCH_DOCK_AGENTS: WorkbenchDockAgent[] = [
  {
    key: "cici-system",
    runtimeAgentId: "cici-system",
    name: "思思",
    label: "思思",
    short: "思",
    color: "linear-gradient(135deg, #4a68f6, #6f88ff)",
    stateMachine: {
      status: "待命中",
      previousTask: "—",
      currentTask: "等待任务",
      nextTask: "—",
      thoughts: ["正在汇总任务、知识和协作上下文"],
    },
    messages: [],
  },
  {
    key: "approval-agent",
    runtimeAgentId: "approval-agent",
    name: "审批助手",
    label: "审批助手",
    short: "批",
    color: "linear-gradient(135deg, #ff9e67, #ffc370)",
    stateMachine: {
      status: "待命中",
      previousTask: "—",
      currentTask: "等待任务",
      nextTask: "—",
      thoughts: ["等待审批任务调用"],
    },
    messages: [],
  },
  {
    key: "sales-agent",
    runtimeAgentId: "sales-agent",
    name: "销售助手",
    label: "销售助手",
    short: "销",
    color: "linear-gradient(135deg, #22b7ab, #74dfd0)",
    stateMachine: {
      status: "待命中",
      previousTask: "—",
      currentTask: "等待任务",
      nextTask: "—",
      thoughts: ["等待销售任务调用"],
    },
    messages: [],
  },
];

const WORKBENCH_METRICS_DEFAULT: WorkbenchMetric[] = [
  { label: "今日任务", value: "—" },
  { label: "已完成", value: "—" },
  { label: "待审批", value: "—" },
  { label: "待跟进", value: "—" },
];

const AI_APPLICATIONS: AiApplication[] = [
  {
    code: "meeting-minutes",
    name: "AI 听记",
    shortName: "听",
    status: "内置",
    summary: "实时转写、发言人整理、结构化纪要生成。",
    description: "适合客户拜访、项目例会和售后回访，结束后生成重点、待办和 CRM 记录建议。",
    meta: "会议听记 · AI 纪要",
  },
  {
    code: "customer-insight",
    name: "客户洞察",
    shortName: "客",
    status: "内置",
    summary: "客户画像、合同订单、服务体验和一客一策分析。",
    description: "汇总 CRM、合同订单、客户服务和人工补充事实，形成可编辑的客户洞察报告。",
    meta: "CRM 洞察 · 业务闭环",
  },
];

function createInitialWorkbenchMessages() {
  return Object.fromEntries(
    WORKBENCH_DOCK_AGENTS.map((agent) => [agent.key, agent.messages.map((message) => ({ ...message }))]),
  ) as Record<string, ChatBubble[]>;
}

function createInitialWorkbenchRuntime() {
  return Object.fromEntries(
    WORKBENCH_DOCK_AGENTS.map((agent) => [
      agent.key,
      {
        ...agent.stateMachine,
        thoughts: [...agent.stateMachine.thoughts],
      },
    ]),
  ) as Record<string, WorkbenchStateMachine>;
}

function getWorkbenchDockAgent(key: string) {
  return WORKBENCH_DOCK_AGENTS.find((agent) => agent.key === key) ?? WORKBENCH_DOCK_AGENTS[0];
}

const IDLE_STATE: WorkbenchStateMachine = {
  status: "待命中",
  previousTask: "—",
  currentTask: "等待任务",
  nextTask: "—",
  thoughts: ["等待新的业务上下文"],
};

function getWorkbenchDefaultState(key: string): WorkbenchStateMachine {
  return getWorkbenchDockAgent(key).stateMachine ?? IDLE_STATE;
}

function deriveWorkbenchStateFromPrompt(question: string, agentKey: string): WorkbenchStateMachine {
  const base = getWorkbenchDefaultState(agentKey);
  const text = question.toLowerCase();
  if (text.includes("审批")) {
    return {
      status: "处理中",
      previousTask: base.currentTask,
      currentTask: "正在分析审批请求",
      nextTask: "按需查询审批记录或流程规则",
      thoughts: ["正在识别审批标题、申请人与当前阶段", "会先判断需要业务工具还是知识依据"],
    };
  }
  if (text.includes("客户") || text.includes("报价") || text.includes("线索")) {
    return {
      status: "处理中",
      previousTask: base.currentTask,
      currentTask: "正在分析客户请求",
      nextTask: "按需查询业务记录或知识口径",
      thoughts: ["正在判断客户、报价或线索所需的数据来源", "会优先给出下一步推进动作"],
    };
  }
  if (text.includes("提醒") || text.includes("日程") || text.includes("安排")) {
    return {
      status: "处理中",
      previousTask: base.currentTask,
      currentTask: "正在编排今日节奏与提醒",
      nextTask: "生成时间安排与提醒建议",
      thoughts: ["正在把任务和审批串成可执行节奏", "会给出下午优先级与提醒建议"],
    };
  }
  return {
    status: "处理中",
    previousTask: base.currentTask,
    currentTask: "正在理解你的办公请求",
    nextTask: "输出可执行的下一步动作",
    thoughts: ["正在汇总任务、知识和协作上下文", "会保持主对话清爽，只返回业务可读摘要"],
  };
}

function finishWorkbenchState(agentKey: string, fallback?: string, assistantContent?: string): WorkbenchStateMachine {
  const base = getWorkbenchDefaultState(agentKey);
  if (assistantResponseNeedsUserFollowup(assistantContent ?? "")) {
    return {
      status: "等待确认",
      previousTask: fallback || base.currentTask,
      currentTask: "本轮结果需要确认或补充",
      nextTask: "补充条件后继续处理",
      thoughts: ["回复中包含参数、失败或继续查询信号", "请补充必要信息，或直接让智能体重试"],
    };
  }
  return {
    status: "已完成",
    previousTask: fallback || base.currentTask,
    currentTask: "已完成本轮处理",
    nextTask: "等待你的下一步指令",
    thoughts: ["本轮处理已经结束", "可以继续追问细节，或切换到其他智能体"],
  };
}

const TOOL_LABEL_MAP: Record<string, string> = {
  get_pending_approvals: "查询待审批",
  search_customers: "搜索客户",
  get_customer: "获取客户详情",
  list_opportunities: "查询商机列表",
  get_schedule: "获取日程安排",
  create_reminder: "创建提醒",
  search_knowledge: "知识库检索",
};

function toolCallLabel(toolName: string): string {
  return TOOL_LABEL_MAP[toolName] ?? toolName.replace(/_/g, " ");
}

function WorkbenchAgentBar({
  agents,
  activeKey,
  onSelect,
}: {
  agents: WorkbenchDockAgent[];
  activeKey: string;
  onSelect: (agentKey: string) => void;
}) {
  return (
    <section className="cici-workbench__agent-bar">
      <div className="cici-workbench__agent-strip-wrap">
        <div className="cici-workbench__agent-strip">
          {agents.map((agent) => {
            const isActive = agent.key === activeKey;
            return (
              <button
                key={agent.key}
                type="button"
                className={`cici-workbench__agent-chip${isActive ? " is-active" : ""}`}
                onClick={() => onSelect(agent.key)}
              >
                <AvatarView
                  src={agent.avatarBase64}
                  fallback={agent.short}
                  className="cici-workbench__agent-avatar"
                  style={{ background: agent.color }}
                  alt={`${agent.name} 头像`}
                />
                <span className="cici-workbench__agent-label">{agent.label}</span>
              </button>
            );
          })}
        </div>
      </div>
    </section>
  );
}

function WorkbenchStateCard({
  agent,
  state,
  busy,
}: {
  agent: WorkbenchDockAgent;
  state: WorkbenchStateMachine;
  busy: boolean;
}) {
  return (
    <section className="cici-workbench__top-activity cici-workbench__top-activity--sidebar">
      <AvatarView
        src={agent.avatarBase64}
        fallback={agent.short}
        className="cici-workbench__top-activity-icon"
        style={{ background: agent.color }}
        alt={`${agent.name} 状态头像`}
      />
      <div className="cici-workbench__top-activity-machine">
        <div className="cici-workbench__machine-head">
          <strong>{agent.name}</strong>
          <span className={`cici-workbench__machine-status${busy ? " is-busy" : ""}`}>{state.status}</span>
        </div>
        <div className="cici-workbench__machine-lane is-prev">
          <span className="cici-workbench__machine-lane-label">上一项</span>
          <div className="cici-workbench__machine-lane-content">{state.previousTask}</div>
        </div>
        <div className="cici-workbench__machine-lane is-current">
          <span className="cici-workbench__machine-lane-label">当前</span>
          <div className="cici-workbench__machine-lane-content">{state.currentTask}</div>
        </div>
        <div className="cici-workbench__machine-lane is-next">
          <span className="cici-workbench__machine-lane-label">下一项</span>
          <div className="cici-workbench__machine-lane-content">{state.nextTask}</div>
        </div>
      </div>
    </section>
  );
}

const LOGIN_MODE2_BRAND_CUBE_FACES: LoginMode2CubeFace[] = [
  { className: "front", image: "/cici-login-default.png", fit: "cover" },
  { className: "back", image: "/login-cube-cloudcc.webp", fit: "contain" },
  { className: "right", image: "/login-cube-openai.webp", fit: "contain" },
  { className: "left", image: "/login-cube-deepseek.webp", fit: "contain" },
  { className: "top", image: "/login-cube-ai-chip.webp", fit: "cover" },
  { className: "bottom", image: "/login-cube-cloudcc.webp", fit: "contain" },
];

function LoginMode2Cube({ phase }: { phase: LoginMode2CubePhase }) {
  const cubeFaces = useMemo(() => {
    return LOGIN_MODE2_BRAND_CUBE_FACES;
  }, []);

  const handlePointerMove = (event: ReactPointerEvent<HTMLElement>) => {
    const rect = event.currentTarget.getBoundingClientRect();
    const x = (event.clientX - rect.left) / rect.width - 0.5;
    const y = (event.clientY - rect.top) / rect.height - 0.5;
    event.currentTarget.style.setProperty("--mode2-tilt-y", `${x * 22}deg`);
    event.currentTarget.style.setProperty("--mode2-tilt-x", `${y * -22}deg`);
  };

  const handlePointerLeave = (event: ReactPointerEvent<HTMLElement>) => {
    event.currentTarget.style.setProperty("--mode2-tilt-y", "0deg");
    event.currentTarget.style.setProperty("--mode2-tilt-x", "0deg");
  };

  return (
    <section
      className={`login-mode2__cube-zone login-mode2__cube-zone--${phase}`}
      onPointerMove={handlePointerMove}
      onPointerLeave={handlePointerLeave}
      aria-label="思思能力立方体"
    >
      <div className="login-mode2__cube-stage" aria-hidden>
        <div className="login-mode2__cube">
          {cubeFaces.map((face) => (
            <div
              key={face.className}
              className={`login-mode2__cube-face login-mode2__cube-face--${face.className}${face.fit === "contain" ? " is-contain" : ""}`}
            >
              <img src={face.image} alt="" decoding="async" loading="eager" draggable={false} />
              {face.label ? <span>{face.label}</span> : null}
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

function AgentLoginMode2({
  form,
  cubePhase,
  entering,
}: {
  form: ReactNode;
  cubePhase: LoginMode2CubePhase;
  entering: boolean;
}) {
  return (
    <main className="login-mode2">
      <section className="login-mode2__center">
        <LoginMode2Cube phase={cubePhase} />
        {!entering ? (
          <section className="login-mode2__form-shell" aria-label="前台账号登录">
            {form}
          </section>
        ) : null}
      </section>
    </main>
  );
}

function HumanLoginShowcaseArt() {
  return (
    <svg className="human-login__art-svg" viewBox="0 0 520 420" fill="none" aria-hidden>
      <defs>
        <linearGradient id="humanScreenStroke" x1="88" y1="70" x2="392" y2="332" gradientUnits="userSpaceOnUse">
          <stop stopColor="#75A9FF" />
          <stop offset="1" stopColor="#2F6BEE" />
        </linearGradient>
        <linearGradient id="humanPaper" x1="206" y1="274" x2="286" y2="364" gradientUnits="userSpaceOnUse">
          <stop stopColor="#F6A127" />
          <stop offset="1" stopColor="#E77F00" />
        </linearGradient>
        <linearGradient id="humanSleeve" x1="176" y1="176" x2="344" y2="234" gradientUnits="userSpaceOnUse">
          <stop stopColor="#FFA53A" />
          <stop offset="1" stopColor="#F38A12" />
        </linearGradient>
        <filter id="humanSoftGlow" x="54" y="58" width="410" height="304" filterUnits="userSpaceOnUse">
          <feGaussianBlur stdDeviation="14" result="blur" />
          <feColorMatrix
            in="blur"
            type="matrix"
            values="0 0 0 0 0.364 0 0 0 0 0.588 0 0 0 0 1 0 0 0 0.26 0"
            result="glow"
          />
          <feBlend in="SourceGraphic" in2="glow" />
        </filter>
      </defs>
      <path
        d="M74 230C102 170 174 140 254 126C340 110 430 128 462 190C490 244 450 306 380 330C312 352 206 350 134 320C74 294 44 264 74 230Z"
        fill="url(#humanBlob)"
      />
      <defs>
        <linearGradient id="humanBlob" x1="88" y1="134" x2="436" y2="334" gradientUnits="userSpaceOnUse">
          <stop stopColor="#F8FBFF" />
          <stop offset="1" stopColor="#EEF5FF" />
        </linearGradient>
      </defs>
      <g filter="url(#humanSoftGlow)">
        <path d="M160 152L126 276C124 283 129 290 137 290H172L190 160L160 152Z" fill="#2E6BEE" />
        <path d="M360 152L330 160L348 290H383C391 290 396 283 394 276L360 152Z" fill="#2E6BEE" />
        <path d="M145 289L112 344C109 349 113 355 119 355H184C190 355 194 349 191 344L173 289H145Z" fill="#477BFF" />
        <path d="M338 289L320 344C317 349 321 355 327 355H392C398 355 402 349 399 344L366 289H338Z" fill="#477BFF" />
        <path d="M171 160L185 280H132L150 154L171 160Z" fill="#DFF0FF" />
        <path d="M349 160L371 154L388 280H335L349 160Z" fill="#DFF0FF" />
        <path d="M192 188L250 206L228 250L168 232C160 230 154 221 154 212C154 196 176 183 192 188Z" fill="url(#humanSleeve)" />
        <path d="M328 188L270 206L292 250L352 232C360 230 366 221 366 212C366 196 344 183 328 188Z" fill="url(#humanSleeve)" />
        <path
          d="M218 218C226 206 238 198 250 198C262 198 274 206 282 218L296 237C300 242 300 249 296 253L287 262C282 267 274 267 270 262L260 251C255 246 247 246 242 251L232 262C228 267 220 267 215 262L206 253C202 249 202 242 206 237L218 218Z"
          fill="#FDB18B"
        />
        <path d="M249 184L232 205L250 214L268 205L249 184Z" fill="#FFD3BF" />
        <path d="M206 256L220 270" stroke="#6487C6" strokeWidth="6" strokeLinecap="round" />
        <path d="M230 258L242 274" stroke="#6487C6" strokeWidth="6" strokeLinecap="round" />
        <path d="M274 258L262 274" stroke="#6487C6" strokeWidth="6" strokeLinecap="round" />
        <path d="M294 256L280 270" stroke="#6487C6" strokeWidth="6" strokeLinecap="round" />
        <path d="M224 286H288L301 329H207L224 286Z" fill="url(#humanPaper)" />
        <path d="M230 302H286" stroke="#96531A" strokeWidth="4" strokeLinecap="round" strokeOpacity="0.28" />
        <path d="M226 318H280" stroke="#96531A" strokeWidth="4" strokeLinecap="round" strokeOpacity="0.22" />
        <path d="M268 334C273 326 280 321 289 320" stroke="#784117" strokeWidth="3" strokeLinecap="round" />
        <path d="M156 206C152 201 151 196 153 191" stroke="#2A4B83" strokeWidth="3" strokeLinecap="round" />
        <path d="M351 206C355 201 356 196 354 191" stroke="#2A4B83" strokeWidth="3" strokeLinecap="round" />
      </g>
      <path d="M230 92C214 76 219 50 244 50C248 35 263 28 278 31C292 34 301 45 302 57C318 57 330 67 332 81" stroke="#58D2F4" strokeWidth="14" strokeLinecap="round" />
      <path d="M272 110C286 94 312 88 334 92" stroke="#58D2F4" strokeWidth="14" strokeLinecap="round" />
      <path d="M188 130C198 114 214 106 232 108" stroke="#58D2F4" strokeWidth="14" strokeLinecap="round" />
      <g fill="#FFB86A" stroke="#FF8E42" strokeWidth="3">
        <ellipse cx="206" cy="144" rx="10" ry="7" transform="rotate(-28 206 144)" />
        <ellipse cx="184" cy="176" rx="10" ry="7" transform="rotate(-16 184 176)" />
        <ellipse cx="316" cy="176" rx="10" ry="7" transform="rotate(28 316 176)" />
        <ellipse cx="294" cy="144" rx="10" ry="7" transform="rotate(16 294 144)" />
      </g>
      <path d="M126 278C132 274 138 275 142 282" stroke="url(#humanScreenStroke)" strokeWidth="4" strokeLinecap="round" />
      <path d="M378 278C372 274 366 275 362 282" stroke="url(#humanScreenStroke)" strokeWidth="4" strokeLinecap="round" />
    </svg>
  );
}

function HumanLoginCloudLogo() {
  return (
    <div className="human-login__logo" aria-label="CloudCC">
      <svg viewBox="0 0 210 88" className="human-login__logo-mark" fill="none" aria-hidden>
        <path
          d="M40 58C28 58 18 49 18 37C18 25 28 16 40 16C44 7 53 1 64 1C79 1 91 12 92 27C104 29 114 39 114 52"
          stroke="#1971D1"
          strokeWidth="7"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <path d="M0 72C22 58 60 54 104 57C135 59 163 64 185 72" stroke="#1971D1" strokeWidth="5" strokeLinecap="round" />
      </svg>
      <div className="human-login__logo-copy">
        <strong>神州云动</strong>
        <span>CloudCC.com</span>
      </div>
    </div>
  );
}

function HumanModeStaticLogin() {
  return (
    <main className="human-login">
      <div className="human-login__shell">
        <section className="human-login__card">
          <div className="human-login__showcase">
            <div className="human-login__art-wrap">
              <HumanLoginShowcaseArt />
            </div>
            <div className="human-login__showcase-copy">
              <h2>销售云</h2>
              <p>销售自动化，助您快速成单</p>
            </div>
            <div className="human-login__pager" aria-hidden>
              <span className="is-active" />
              <span />
              <span />
            </div>
            <button type="button" className="human-login__detail-btn">
              查看详情
            </button>
          </div>

          <div className="human-login__form-pane">
            <HumanLoginCloudLogo />
            <div className="human-login__form-shell">
              <label className="human-login__field">
                <span className="human-login__field-icon" aria-hidden>
                  <svg viewBox="0 0 24 24" fill="none">
                    <circle cx="12" cy="8" r="4" stroke="currentColor" strokeWidth="1.8" />
                    <path d="M4 20C5.6 16.6 8.4 15 12 15C15.6 15 18.4 16.6 20 20" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
                  </svg>
                </span>
                <input value="zhengyan@cloudcc.com" readOnly aria-label="邮箱" />
                <span className="human-login__field-action" aria-hidden>
                  ×
                </span>
              </label>

              <label className="human-login__field">
                <span className="human-login__field-icon" aria-hidden>
                  <svg viewBox="0 0 24 24" fill="none">
                    <rect x="6" y="10" width="12" height="10" rx="2" stroke="currentColor" strokeWidth="1.8" />
                    <path d="M9 10V8C9 6.343 10.343 5 12 5C13.657 5 15 6.343 15 8V10" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
                  </svg>
                </span>
                <input value="••••••••" readOnly aria-label="密码" />
                <span className="human-login__field-tail" aria-hidden>
                  <svg viewBox="0 0 24 24" fill="none">
                    <path d="M2 12C4.8 7.8 8.133 5.7 12 5.7C15.867 5.7 19.2 7.8 22 12C19.2 16.2 15.867 18.3 12 18.3C8.133 18.3 4.8 16.2 2 12Z" stroke="currentColor" strokeWidth="1.6" />
                    <circle cx="12" cy="12" r="3" stroke="currentColor" strokeWidth="1.6" />
                  </svg>
                  <span className="human-login__field-action">×</span>
                </span>
              </label>

              <div className="human-login__meta">
                <label className="human-login__remember">
                  <input type="checkbox" checked readOnly />
                  <span>记住密码</span>
                </label>
                <button type="button" className="human-login__text-btn">
                  忘记密码
                </button>
              </div>

              <button type="button" className="human-login__submit">
                登录
              </button>

              <div className="human-login__alt-row">
                <button type="button" className="human-login__text-btn">
                  手机号密码登录
                </button>
                <span className="human-login__divider-bar" aria-hidden>
                  |
                </span>
                <button type="button" className="human-login__text-btn">
                  统一密码登录
                </button>
              </div>

              <div className="human-login__divider">
                <span />
                <em>其他登录方式</em>
                <span />
              </div>

              <button type="button" className="human-login__social">
                <svg viewBox="0 0 48 48" fill="none" aria-hidden>
                  <path d="M18 12C24.8 12 30 16.8 30 23.2C30 30.8 23.1 35.4 18 35.4C16.5 35.4 15.1 35.1 13.8 34.5L9.2 36L10.4 31.8C8.3 29.6 7 26.7 7 23.2C7 16.8 12.2 12 18 12Z" fill="#0F7BFF" />
                  <path d="M30 18C35.8 18 41 22.4 41 28C41 31.1 39.4 33.8 36.8 35.7L37.8 39.2L33.9 37.9C32.7 38.4 31.4 38.6 30 38.6C24.2 38.6 19 34.2 19 28.6C19 23 24.2 18 30 18Z" fill="#F7A11B" fillOpacity="0.96" />
                  <circle cx="23.5" cy="24" r="12.5" stroke="#0F7BFF" strokeOpacity="0.22" />
                </svg>
              </button>

              <p className="human-login__legal">
                登录即视为同意 <button type="button">《服务协议》</button> 和 <button type="button">《隐私政策》</button>
              </p>
            </div>
          </div>
        </section>
      </div>
    </main>
  );
}

export default function AssistantApp() {
  const [mobile, setMobile] = useState("13900009999");
  const [loginPassword, setLoginPassword] = useState("");
  const [organizationName, setOrganizationName] = useState("");
  const [registerMode, setRegisterMode] = useState(false);
  const [pendingOrganizations, setPendingOrganizations] = useState<OrganizationOption[]>([]);
  const [organizations, setOrganizations] = useState<OrganizationOption[]>([]);
  const [organizationMenuOpen, setOrganizationMenuOpen] = useState(false);
  const [notice, setNotice] = useState("");
  const [auth, setAuth] = useState<AuthPayload | null>(() => {
    const raw = localStorage.getItem(LS_ASSISTANT_TOKEN);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as AuthPayload;
    } catch {
      return null;
    }
  });
  const [me, setMe] = useState<MeProfile | null>(null);
  const [input, setInput] = useState("");
  const [chatLoading, setChatLoading] = useState(false);
  const [skillPickerOpen, setSkillPickerOpen] = useState(false);
  const [quickCommandMenuOpen, setQuickCommandMenuOpen] = useState(false);
  const [quickCommandDialogOpen, setQuickCommandDialogOpen] = useState(false);
  const [agentSkillBindingsByAgent, setAgentSkillBindingsByAgent] = useState<Record<string, AgentSkillBindingView[]>>({});
  const [agentSkillBindingsLoadingByAgent, setAgentSkillBindingsLoadingByAgent] = useState<Record<string, boolean>>({});
  const [agentSkillBindingsFailedByAgent, setAgentSkillBindingsFailedByAgent] = useState<Record<string, boolean>>({});
  const [activeSkillCodeByAgent, setActiveSkillCodeByAgent] = useState<Record<string, string>>({});
  const [quickCommandsByAgent, setQuickCommandsByAgent] = useState<Record<string, UserQuickCommand[]>>({});
  const [quickCommandsLoadingByAgent, setQuickCommandsLoadingByAgent] = useState<Record<string, boolean>>({});
  const [quickCommandSaving, setQuickCommandSaving] = useState(false);
  const [quickCommandDraft, setQuickCommandDraft] = useState({ title: "", promptText: "" });
  const [approvalPageHtml, setApprovalPageHtml] = useState<string | null>(null);
  const [approvalDrawerOpen, setApprovalDrawerOpen] = useState(false);
  const [speechNotice, setSpeechNotice] = useState("");
  const [kbs, setKbs] = useState<KnowledgeBase[]>([]);
  const [selectedKbIds, setSelectedKbIds] = useState<number[]>([]);
  const [conversationThreads, setConversationThreads] = useState<ConversationThread[]>([]);
  const [agentWorkspaces, setAgentWorkspaces] = useState<AgentWorkspace[]>(AGENT_WORKSPACES);
  const [activeAgentId, setActiveAgentId] = useState(AGENT_WORKSPACES[0].id);
  const [activeWorkbenchKey, setActiveWorkbenchKey] = useState(WORKBENCH_DOCK_AGENTS[0].key);
  const [activeConversationId, setActiveConversationId] = useState("");
  const [searchText, setSearchText] = useState("");
  const [activeChannel, setActiveChannel] = useState<"all" | ConversationThread["channel"]>("all");
  const [workspaceTab, setWorkspaceTab] = useState<WorkspaceTab>("workbench");
  const [activeAiAppCode, setActiveAiAppCode] = useState<AiApplication["code"]>(AI_APPLICATIONS[0].code);
  const [conversationMessages, setConversationMessages] = useState<Record<string, ChatBubble[]>>({});
  const [conversationListLoading, setConversationListLoading] = useState(false);
  const [conversationListNotice, setConversationListNotice] = useState("");
  const [conversationHistoryLoadingId, setConversationHistoryLoadingId] = useState("");
  const [workbenchDockAgents, setWorkbenchDockAgents] = useState<WorkbenchDockAgent[]>(WORKBENCH_DOCK_AGENTS);
  const [workbenchMessagesByAgent, setWorkbenchMessagesByAgent] = useState<Record<string, ChatBubble[]>>(createInitialWorkbenchMessages);
  const [workbenchRuntimeByAgent, setWorkbenchRuntimeByAgent] = useState<Record<string, WorkbenchStateMachine>>(createInitialWorkbenchRuntime);
  const [activeWorkbenchSessionIdByAgent, setActiveWorkbenchSessionIdByAgent] = useState<Record<string, string>>({});
  const [openWorkbenchSessionMenuId, setOpenWorkbenchSessionMenuId] = useState("");
  const [workbenchMetrics, setWorkbenchMetrics] = useState<WorkbenchMetric[]>(WORKBENCH_METRICS_DEFAULT);
  const [workbenchOverviewItems, setWorkbenchOverviewItems] = useState<WorkbenchOverviewItem[]>([]);
  const [monitorRunLogs, setMonitorRunLogs] = useState<AgentRunLogPayload[]>([]);
  const [monitorTraceDetail, setMonitorTraceDetail] = useState<AgentTraceDetailPayload | null>(null);
  const [monitorLogsLoading, setMonitorLogsLoading] = useState(false);
  const [monitorTraceLoadingId, setMonitorTraceLoadingId] = useState("");
  const [meetingDrawerOpen, setMeetingDrawerOpen] = useState(false);
  const [meetingStatus, setMeetingStatus] = useState<MeetingStatus>("idle");
  const [meetingNotice, setMeetingNotice] = useState("");
  const [meetingTranscript, setMeetingTranscript] = useState<MeetingTranscriptSegment[]>([]);
  const [meetingPartial, setMeetingPartial] = useState<MeetingTranscriptSegment | null>(null);
  const [meetingSummary, setMeetingSummary] = useState("");
  const [meetingSpeakerNames, setMeetingSpeakerNames] = useState<Record<string, string>>({});
  const [meetingSpeakerEdit, setMeetingSpeakerEdit] = useState<MeetingSpeakerEdit | null>(null);
  const meetingTranscriptRef = useRef<MeetingTranscriptSegment[]>([]);
  const meetingSpeakerNamesRef = useRef<Record<string, string>>({});
  const meetingShouldSummarizeRef = useRef(false);
  const [workbenchThoughtIndex, setWorkbenchThoughtIndex] = useState(0);
  const [loginMode2CubePhase, setLoginMode2CubePhase] = useState<LoginMode2CubePhase>("brand");
  const [loginMode2Entering, setLoginMode2Entering] = useState(false);
  const [loginSubmitting, setLoginSubmitting] = useState(false);
  const chatStreamRef = useRef<HTMLDivElement | null>(null);
  const meetingTranscriptScrollRef = useRef<HTMLDivElement | null>(null);
  const meetingSpeakerEditInputRef = useRef<HTMLInputElement | null>(null);
  const skillPickerRef = useRef<HTMLDivElement | null>(null);
  const quickCommandMenuRef = useRef<HTMLDivElement | null>(null);
  const organizationMenuRef = useRef<HTMLDivElement | null>(null);
  const organizationMenuCloseTimerRef = useRef<number | null>(null);
  const uploadInputRef = useRef<HTMLInputElement | null>(null);
  const composerInputRef = useRef<HTMLTextAreaElement | HTMLInputElement | null>(null);
  const { listening, speechSupported, start: startAsrSession, stop: stopAsrSession, abort: abortAsrSession } = useAsrVoiceInput();
  const activeConversationIdRef = useRef("");
  const workspaceTabRef = useRef<WorkspaceTab>("workbench");
  const [activeMonitorAgentKey, setActiveMonitorAgentKey] = useState("");
  const [activeMonitorLogId, setActiveMonitorLogId] = useState("");
  const [monitorSearchText, setMonitorSearchText] = useState("");

  const persistAuth = (payload: AuthPayload | null) => {
    if (payload) {
      localStorage.setItem(LS_ASSISTANT_TOKEN, JSON.stringify(payload));
    } else {
      localStorage.removeItem(LS_ASSISTANT_TOKEN);
    }
    setAuth(payload);
  };

  const attachComposerTextareaRef = (element: HTMLTextAreaElement | null) => {
    composerInputRef.current = element;
  };

  const attachComposerTextInputRef = (element: HTMLInputElement | null) => {
    composerInputRef.current = element;
  };

  const cancelOrganizationMenuClose = () => {
    if (organizationMenuCloseTimerRef.current !== null) {
      window.clearTimeout(organizationMenuCloseTimerRef.current);
      organizationMenuCloseTimerRef.current = null;
    }
  };

  const openOrganizationMenu = () => {
    cancelOrganizationMenuClose();
    setOrganizationMenuOpen(true);
  };

  const scheduleOrganizationMenuClose = () => {
    cancelOrganizationMenuClose();
    organizationMenuCloseTimerRef.current = window.setTimeout(() => {
      setOrganizationMenuOpen(false);
      organizationMenuCloseTimerRef.current = null;
    }, 120);
  };

  const loadMe = async (tokenOverride?: string) => {
    const token = tokenOverride ?? auth?.token;
    if (!token) {
      return;
    }
    try {
      const response = await fetch("/auth/me", { headers: { Authorization: `Bearer ${token}` } });
      const { body } = await safeFetchJson<MeProfile>(response);
      if (response.ok && body?.success) {
        setMe(body.data as MeProfile | null);
      }
    } catch {}
  };

  const loadOrganizations = async (tokenOverride?: string) => {
    const token = tokenOverride ?? auth?.token;
    if (!token) {
      setOrganizations([]);
      return;
    }
    try {
      const response = await fetch("/auth/organizations", { headers: { Authorization: `Bearer ${token}` } });
      const { body } = await safeFetchJson<{ organizations?: OrganizationOption[] }>(response);
      if (response.ok && body?.success) {
        setOrganizations(body.data?.organizations ?? []);
      }
    } catch {
      setOrganizations([]);
    }
  };

  const loadKbs = async () => {
    if (!auth) {
      return;
    }
    try {
      const response = await fetch("/kb", { headers: { Authorization: `Bearer ${auth.token}` } });
      const { body } = await safeFetchJson<KnowledgeBase[]>(response);
      setKbs((body?.data ?? []) as KnowledgeBase[]);
    } catch {
      setKbs([]);
    }
  };

  const loadAgentSkillBindings = async (agentId: string, tokenOverride?: string, retryOnce = true) => {
    const token = tokenOverride ?? auth?.token;
    if (!token || !agentId) {
      return;
    }
    setAgentSkillBindingsFailedByAgent((prev) => ({ ...prev, [agentId]: false }));
    setAgentSkillBindingsLoadingByAgent((prev) => ({ ...prev, [agentId]: true }));
    let keepLoadingForRetry = false;
    try {
      const response = await fetch(`/me/agents/${encodeURIComponent(agentId)}/skills`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      const { body } = await safeFetchJson<{ bindings?: AgentSkillBindingView[] }>(response);
      if (!response.ok || !body?.success) {
        throw new Error(body?.message ?? `HTTP ${response.status}`);
      }
      const bindings = (body.data?.bindings ?? []).filter((item) => item.enabled);
      setAgentSkillBindingsByAgent((prev) => ({ ...prev, [agentId]: bindings }));
      setAgentSkillBindingsFailedByAgent((prev) => ({ ...prev, [agentId]: false }));
      setActiveSkillCodeByAgent((prev) => {
        const selectedCode = prev[agentId];
        if (!selectedCode || bindings.some((item) => item.skillCode === selectedCode)) {
          return prev;
        }
        const next = { ...prev };
        delete next[agentId];
        return next;
      });
    } catch {
      setAgentSkillBindingsByAgent((prev) => {
        if (!(agentId in prev)) {
          return prev;
        }
        const next = { ...prev };
        delete next[agentId];
        return next;
      });
      if (retryOnce) {
        keepLoadingForRetry = true;
        window.setTimeout(() => {
          void loadAgentSkillBindings(agentId, token, false);
        }, 600);
      } else {
        setAgentSkillBindingsFailedByAgent((prev) => ({ ...prev, [agentId]: true }));
      }
    } finally {
      if (!keepLoadingForRetry) {
        setAgentSkillBindingsLoadingByAgent((prev) => ({ ...prev, [agentId]: false }));
      }
    }
  };

  const loadQuickCommands = async (agentId: string, tokenOverride?: string) => {
    const token = tokenOverride ?? auth?.token;
    if (!token || !agentId) {
      return;
    }
    setQuickCommandsLoadingByAgent((prev) => ({ ...prev, [agentId]: true }));
    try {
      const response = await fetch(`/me/agents/${encodeURIComponent(agentId)}/workflow/quick-commands`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      const { body } = await safeFetchJson<UserQuickCommand[]>(response);
      if (!response.ok || !body?.success) {
        throw new Error(body?.message ?? `HTTP ${response.status}`);
      }
      setQuickCommandsByAgent((prev) => ({ ...prev, [agentId]: (body.data ?? []) as UserQuickCommand[] }));
    } catch {
      setQuickCommandsByAgent((prev) => ({ ...prev, [agentId]: [] }));
    } finally {
      setQuickCommandsLoadingByAgent((prev) => ({ ...prev, [agentId]: false }));
    }
  };

  const saveQuickCommand = async () => {
    if (!auth || quickCommandSaving) {
      return;
    }
    const title = quickCommandDraft.title.trim();
    const promptText = quickCommandDraft.promptText.trim();
    if (!promptText) {
      setSpeechNotice("请先填写快捷指令内容。");
      return;
    }
    setQuickCommandSaving(true);
    try {
      const response = await fetch(`/me/agents/${encodeURIComponent(activeWorkbenchAgentId)}/workflow/quick-commands`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${auth.token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ title, promptText }),
      });
      const { body } = await safeFetchJson<UserQuickCommand>(response);
      if (!response.ok || !body?.success || !body.data) {
        throw new Error(body?.message ?? `HTTP ${response.status}`);
      }
      const created = body.data as UserQuickCommand;
      setQuickCommandsByAgent((prev) => ({
        ...prev,
        [activeWorkbenchAgentId]: [...(prev[activeWorkbenchAgentId] ?? []), created],
      }));
      setQuickCommandDraft({ title: "", promptText: "" });
      setQuickCommandDialogOpen(false);
      setSpeechNotice("快捷指令已添加。");
    } catch (error) {
      setSpeechNotice(`添加快捷指令失败：${error instanceof Error ? error.message : String(error)}`);
    } finally {
      setQuickCommandSaving(false);
    }
  };

  const loadWorkbenchAgents = async (token: string) => {
    try {
      const res = await fetch("/agents", { headers: { Authorization: `Bearer ${token}` } });
      const { body } = await safeFetchJson<PublishedAgentPayload[]>(res);
      if (!res.ok || !body?.success || !Array.isArray(body.data) || body.data.length === 0) return;
      const visible = (body.data as PublishedAgentPayload[]).filter(
        (a) => a.builtin || a.publishedVersionId != null,
      );
      if (visible.length === 0) return;

      const now = new Date();
      const timeStr = `${now.getHours().toString().padStart(2, "0")}:${now.getMinutes().toString().padStart(2, "0")}`;

      const nextDockAgents = visible.map((a, i) => {
        const base = toWorkbenchDockAgent(a, i);
        return {
          ...base,
          name: a.name ?? base.name,
          label: a.name ?? base.label,
          avatarBase64: (a.avatarBase64 ?? "").trim(),
          stateMachine: { ...base.stateMachine, thoughts: [...base.stateMachine.thoughts] },
          messages: base.messages.map((item) => ({ ...item })),
        };
      });
      setWorkbenchDockAgents(nextDockAgents);
      setAgentWorkspaces((prev) => {
        const next = prev.map((workspace) => {
          const matched = visible.find((item) => item.agentId === workspace.id);
          if (!matched) return workspace;
          const nextName = matched.name?.trim() || workspace.name;
          const avatarBase64 = (matched.avatarBase64 ?? "").trim();
          return {
            ...workspace,
            name: nextName,
            avatar: getDisplayInitial(nextName, workspace.avatar),
            avatarBase64,
            description: matched.summary?.trim() || workspace.description,
          };
        });

        for (const item of visible) {
          if (next.some((workspace) => workspace.id === item.agentId)) {
            continue;
          }
          const name = item.name?.trim() || item.agentId;
          next.push({
            id: item.agentId,
            name,
            avatar: getDisplayInitial(name, "A"),
            avatarBase64: (item.avatarBase64 ?? "").trim(),
            category: item.builtin ? "system" : "published",
            status: item.publishedVersionId != null ? "已发布" : (item.builtin ? "系统内置" : "待发布"),
            subtitle: item.builtin ? "系统智能体" : "自定义智能体",
            description: item.summary?.trim() || "由组织管理员配置的智能体。",
            channels: ["web"],
            knowledgeMode: "按智能体配置",
            accent: "#5b7ff4",
            pinned: item.builtin === true,
          });
        }
        return next;
      });
      setActiveAgentId((current) => (visible.some((item) => item.agentId === current) ? current : (visible[0]?.agentId ?? current)));

      // Populate initial greeting messages from agent settings (only if chat is still empty)
      setWorkbenchMessagesByAgent((prev) => {
        const next = { ...prev };
        for (const a of visible) {
          const key = a.agentId;
          const greetingText = (a.greeting ?? "").trim();
          if (!next[key] || next[key].length === 0) {
            next[key] = greetingText
              ? [{ role: "assistant" as const, content: greetingText, time: timeStr }]
              : [];
          }
        }
        return next;
      });

      setWorkbenchRuntimeByAgent((prev) => {
        const next = { ...prev };
        for (const agent of nextDockAgents) {
          if (!(agent.key in next)) {
            next[agent.key] = { ...agent.stateMachine, thoughts: [...agent.stateMachine.thoughts] };
          }
        }
        return next;
      });
      setActiveWorkbenchKey((current) =>
        nextDockAgents.some((a) => a.key === current) ? current : (nextDockAgents[0]?.key ?? current),
      );
    } catch {}
  };

  const loadWorkbenchStats = async (token: string) => {
    try {
      const res = await fetch("/me/agents/cici-system/workflow/executions", {
        headers: { Authorization: `Bearer ${token}` },
      });
      const { body } = await safeFetchJson<WorkflowExecutionPayload[]>(res);
      if (!res.ok || !body?.success || !Array.isArray(body.data)) return;
      const execs = body.data as WorkflowExecutionPayload[];

      const today = new Date().toISOString().slice(0, 10);
      const todayExecs = execs.filter((e) => (e.scheduledAt ?? e.finishedAt ?? "").startsWith(today));
      const successToday = todayExecs.filter((e) => e.status === "SUCCESS").length;
      const totalToday = todayExecs.length;

      setWorkbenchMetrics([
        { label: "今日任务", value: totalToday > 0 ? String(totalToday) : "—" },
        { label: "已完成", value: successToday > 0 ? String(successToday) : "—" },
        { label: "待审批", value: "—" },
        { label: "待跟进", value: "—" },
      ]);

      const sevenDaysAgo = Date.now() - 7 * 24 * 60 * 60 * 1000;
      const recentExecs = execs.filter((e) => {
        const rawTime = e.finishedAt ?? e.scheduledAt ?? "";
        if (!rawTime) {
          return false;
        }
        const timestamp = new Date(rawTime).getTime();
        return Number.isFinite(timestamp) && timestamp >= sevenDaysAgo;
      });

      // Recent successful executions → overview cards
      const recentSuccessful = recentExecs
        .filter((e) => e.status === "SUCCESS" && e.outputSummary)
        .slice(0, 3);

      if (recentSuccessful.length > 0) {
        const overviewItems: WorkbenchOverviewItem[] = recentSuccessful.map((e, idx) => {
          const summary = (e.outputSummary ?? "").slice(0, 120);
          const firstLine = summary.split("\n")[0].replace(/^【|】$/g, "").trim();
          const timeLabel = e.finishedAt
            ? new Date(e.finishedAt).toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit" })
            : "";
          return {
            id: `exec-${e.id ?? idx}`,
            title: firstLine || (e.routineKey ?? "近期任务"),
            status: "已完成",
            detail: timeLabel ? `完成于 ${timeLabel}` : "最近执行",
            prompt: `请总结一下这次任务的结果：${firstLine}`,
          };
        });
        setWorkbenchOverviewItems(overviewItems);
      }
    } catch {}
  };

  const loadMonitorRunLogs = async (tokenOverride?: string) => {
    const token = tokenOverride ?? auth?.token;
    if (!token) {
      return;
    }
    setMonitorLogsLoading(true);
    try {
      const params = new URLSearchParams({ limit: "80" });
      const response = await fetch(`/me/agents/run-logs?${params.toString()}`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      const { body } = await safeFetchJson<{ items?: AgentRunLogPayload[] } | AgentRunLogPayload[]>(response);
      if (!response.ok || !body?.success) {
        throw new Error(body?.message ?? `HTTP ${response.status}`);
      }
      const data = body.data as { items?: AgentRunLogPayload[] } | AgentRunLogPayload[] | undefined;
      const items = Array.isArray(data) ? data : (data?.items ?? []);
      setMonitorRunLogs(items.filter((item) => item.traceId));
    } catch {
      setMonitorRunLogs([]);
    } finally {
      setMonitorLogsLoading(false);
    }
  };

  const loadMonitorTraceDetail = async (traceId: string, tokenOverride?: string) => {
    const token = tokenOverride ?? auth?.token;
    if (!token || !traceId) {
      setMonitorTraceDetail(null);
      return;
    }
    setMonitorTraceLoadingId(traceId);
    try {
      const response = await fetch(`/me/agents/run-logs/${encodeURIComponent(traceId)}`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      const { body } = await safeFetchJson<AgentTraceDetailPayload>(response);
      if (!response.ok || !body?.success || !body.data) {
        throw new Error(body?.message ?? `HTTP ${response.status}`);
      }
      setMonitorTraceDetail(body.data as AgentTraceDetailPayload);
    } catch {
      setMonitorTraceDetail(null);
    } finally {
      setMonitorTraceLoadingId((current) => (current === traceId ? "" : current));
    }
  };

  const loadConversationThreads = async (preferredConversationId?: string) => {
    if (!auth) {
      setConversationThreads([]);
      setActiveConversationId("");
      return;
    }
    setConversationListLoading(true);
    try {
      const response = await fetch("/ai/sessions", {
        headers: { Authorization: `Bearer ${auth.token}` },
      });
      const { body } = await safeFetchJson<ConversationThreadPayload[]>(response);
      if (!response.ok || !body?.success) {
        setConversationListNotice(body?.message ?? "加载会话列表失败");
        setConversationThreads([]);
        setActiveConversationId("");
        return;
      }
      const nextThreads = ((body.data ?? []) as ConversationThreadPayload[]).map(normalizeConversationThread);
      setConversationThreads((previous) => {
        const drafts = previous.filter((item) => item.id.startsWith("workbench:") && !nextThreads.some((thread) => thread.id === item.id));
        return [...drafts, ...nextThreads];
      });
      setConversationListNotice("");
      setActiveConversationId((current) => {
        if (preferredConversationId && nextThreads.some((item) => item.id === preferredConversationId)) {
          return preferredConversationId;
        }
        if (current && nextThreads.some((item) => item.id === current)) {
          return current;
        }
        return nextThreads[0]?.id ?? "";
      });
    } catch {
      setConversationThreads([]);
      setActiveConversationId("");
      setConversationListNotice("加载会话列表失败");
    } finally {
      setConversationListLoading(false);
    }
  };

  const loadConversationMessages = async (conversationId: string, force = false) => {
    if (!auth || !conversationId) {
      return;
    }
    if (!force && conversationId in conversationMessages) {
      return;
    }
    setConversationHistoryLoadingId(conversationId);
    try {
      const response = await fetch(`/ai/sessions/${encodeURIComponent(conversationId)}/messages`, {
        headers: { Authorization: `Bearer ${auth.token}` },
      });
      if (response.status === 404) {
        setConversationMessages((prev) => {
          const existing = prev[conversationId] ?? [];
          if (shouldKeepLocalStreamingMessages(existing, [])) {
            return prev;
          }
          return { ...prev, [conversationId]: [] };
        });
        return;
      }
      const { body } = await safeFetchJson<ConversationMessagePayload[]>(response);
      if (!response.ok || !body?.success) {
        setConversationListNotice(body?.message ?? "加载会话消息失败");
        return;
      }
      const normalized = normalizeConversationMessages((body.data ?? []) as ConversationMessagePayload[]);
      setConversationMessages((prev) => {
        const existing = prev[conversationId] ?? [];
        if (shouldKeepLocalStreamingMessages(existing, normalized)) {
          return prev;
        }
        return { ...prev, [conversationId]: preserveAssistantModelNames(existing, normalized) };
      });
    } catch {
      setConversationListNotice("加载会话消息失败");
    } finally {
      setConversationHistoryLoadingId((current) => (current === conversationId ? "" : current));
    }
  };

  const loadWorkbenchMessages = async (agentKey: string, sessionId: string, force = false) => {
    if (!auth) {
      return;
    }
    if (!force && sessionId in conversationMessages) {
      setWorkbenchMessagesByAgent((prev) => ({
        ...prev,
        [agentKey]: conversationMessages[sessionId] ?? [],
      }));
      return;
    }
    try {
      const response = await fetch(`/ai/sessions/${encodeURIComponent(sessionId)}/messages`, {
        headers: { Authorization: `Bearer ${auth.token}` },
      });
      if (response.status === 404) {
        setConversationMessages((prev) => {
          const existing = prev[sessionId] ?? [];
          if (shouldKeepLocalStreamingMessages(existing, [])) {
            return prev;
          }
          return { ...prev, [sessionId]: [] };
        });
        setWorkbenchMessagesByAgent((prev) => {
          const existing = prev[agentKey] ?? [];
          if (shouldKeepLocalStreamingMessages(existing, [])) {
            return prev;
          }
          return { ...prev, [agentKey]: [] };
        });
        return;
      }
      const { body } = await safeFetchJson<ConversationMessagePayload[]>(response);
      if (!response.ok || !body?.success) {
        return;
      }
      const normalized = normalizeConversationMessages((body.data ?? []) as ConversationMessagePayload[]);
      setConversationMessages((prev) => {
        const existing = prev[sessionId] ?? [];
        if (shouldKeepLocalStreamingMessages(existing, normalized)) {
          return prev;
        }
        return { ...prev, [sessionId]: preserveAssistantModelNames(existing, normalized) };
      });
      setWorkbenchMessagesByAgent((prev) => {
        const existing = prev[agentKey] ?? [];
        // The backend commits the user turn before the assistant turn. A history refresh
        // during that window is older than the local streaming placeholder/partial text.
        if (shouldKeepLocalStreamingMessages(existing, normalized)) {
          return prev;
        }
        return { ...prev, [agentKey]: preserveAssistantModelNames(existing, normalized) };
      });
    } catch {
      // Keep optimistic UI messages if refresh fails.
    }
  };

  // Close the anchored skill picker when focus moves outside the composer menu.
  useEffect(() => {
    const handler = (event: MouseEvent) => {
      if (skillPickerRef.current && !skillPickerRef.current.contains(event.target as Node)) {
        setSkillPickerOpen(false);
      }
      if (quickCommandMenuRef.current && !quickCommandMenuRef.current.contains(event.target as Node)) {
        setQuickCommandMenuOpen(false);
      }
      if (organizationMenuRef.current && !organizationMenuRef.current.contains(event.target as Node)) {
        setOrganizationMenuOpen(false);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  useEffect(() => {
    return () => cancelOrganizationMenuClose();
  }, []);

  useEffect(() => {
    if (auth) {
      void loadKbs();
    }
  }, [auth?.token]);

  useEffect(() => {
    if (!auth && FRONT_LOGIN_USER_MODE_CONFIG === "agent" && FRONT_LOGIN_MODE_CONFIG === "login_mode2") {
      setLoginMode2CubePhase("brand");
      setLoginMode2Entering(false);
    }
  }, [auth]);

  useEffect(() => {
    if (auth) {
      void loadWorkbenchAgents(auth.token);
      void loadWorkbenchStats(auth.token);
    } else {
      setAgentWorkspaces(AGENT_WORKSPACES);
      setWorkbenchDockAgents(WORKBENCH_DOCK_AGENTS);
      setWorkbenchMetrics(WORKBENCH_METRICS_DEFAULT);
      setWorkbenchOverviewItems([]);
      setMonitorRunLogs([]);
      setMonitorTraceDetail(null);
      setMonitorTraceLoadingId("");
      setAgentSkillBindingsByAgent({});
      setAgentSkillBindingsLoadingByAgent({});
      setAgentSkillBindingsFailedByAgent({});
      setActiveSkillCodeByAgent({});
    }
  }, [auth?.token]);

  useEffect(() => {
    if (auth) {
      void loadMe();
      void loadOrganizations();
    } else {
      setOrganizations([]);
      setOrganizationMenuOpen(false);
    }
  }, [auth?.token]);

  useEffect(() => {
    if (!auth) return;
    const onCurrentUserUpdated = (event: Event) => {
      const detail = (event as CustomEvent<CurrentUserUpdatedDetail>).detail;
      if (!detail || detail.userId !== auth.userId) return;
      setMe((prev) => ({
        ...(prev ?? {}),
        mobile: detail.mobile ?? prev?.mobile,
        nickname: detail.displayName ?? detail.nickname ?? prev?.nickname,
        displayName: detail.displayName ?? prev?.displayName,
        avatarBase64: detail.avatarBase64 ?? prev?.avatarBase64,
      }));
    };
    window.addEventListener("assistant-current-user-updated", onCurrentUserUpdated);
    return () => window.removeEventListener("assistant-current-user-updated", onCurrentUserUpdated);
  }, [auth]);

  useEffect(() => {
    if (auth) {
      void loadConversationThreads();
    } else {
      setConversationThreads([]);
      setConversationMessages({});
      setConversationListNotice("");
      setActiveConversationId("");
    }
  }, [auth?.token]);

  useEffect(() => {
    activeConversationIdRef.current = activeConversationId;
  }, [activeConversationId]);

  useEffect(() => {
    workspaceTabRef.current = workspaceTab;
  }, [workspaceTab]);

  const sessionStreamActive =
    workspaceTab !== "workbench" &&
    workspaceTab !== "monitor" &&
    workspaceTab !== "crm" &&
    workspaceTab !== "settings" &&
    workspaceTab !== "profile";

  useEffect(() => {
    if (!auth || !sessionStreamActive) {
      return;
    }
    let stopped = false;
    let controller: AbortController | null = null;

    const connect = async () => {
      while (!stopped) {
        controller = new AbortController();
        try {
          setConversationListNotice((current) => (current === "实时同步连接已断开，正在重连..." ? "" : current));
          await streamSessionUpdates(
            auth.token,
            async (event) => {
              const sessionId = event.sessionId?.trim();
              if (!sessionId) {
                return;
              }
              setConversationListNotice((current) => (current === "实时同步连接已断开，正在重连..." ? "" : current));
              await loadConversationThreads(activeConversationIdRef.current || sessionId);
              if (
                workspaceTabRef.current === "workbench" ||
                workspaceTabRef.current === "monitor" ||
                workspaceTabRef.current === "crm" ||
                workspaceTabRef.current === "settings" ||
                workspaceTabRef.current === "profile"
              ) {
                return;
              }
              if (activeConversationIdRef.current && activeConversationIdRef.current !== sessionId) {
                return;
              }
              await loadConversationMessages(sessionId, true);
            },
            controller.signal,
          );
          if (!stopped) {
            setConversationListNotice((current) => current || "实时同步连接已断开，正在重连...");
          }
        } catch {
          if (controller.signal.aborted || stopped) {
            break;
          }
          setConversationListNotice((current) => current || "实时同步连接已断开，正在重连...");
        }
        await new Promise((resolve) => window.setTimeout(resolve, 1500));
      }
    };

    void connect();

    const timer = window.setInterval(() => {
      void (async () => {
        await loadConversationThreads();
        if (
          workspaceTabRef.current !== "workbench" &&
          workspaceTabRef.current !== "monitor" &&
          workspaceTabRef.current !== "settings" &&
          workspaceTabRef.current !== "profile" &&
          activeConversationIdRef.current
        ) {
          await loadConversationMessages(activeConversationIdRef.current, true);
        }
      })();
    }, 60000);
    return () => {
      stopped = true;
      controller?.abort();
      window.clearInterval(timer);
    };
  }, [auth?.token, sessionStreamActive]);

  const conversationsByAgent = useMemo(() => {
    const map = new Map<string, ConversationThread[]>();
    for (const thread of conversationThreads) {
      const group = map.get(thread.agentId);
      if (group) {
        group.push(thread);
      } else {
        map.set(thread.agentId, [thread]);
      }
    }
    return map;
  }, [conversationThreads]);

  const activeAgent = useMemo(
    () => agentWorkspaces.find((item) => item.id === activeAgentId) ?? agentWorkspaces[0] ?? AGENT_WORKSPACES[0],
    [activeAgentId, agentWorkspaces],
  );

  const availableThreads = useMemo(() => {
    const source = conversationsByAgent.get(activeAgentId) ?? [];
    return source.filter((thread) => {
      if (activeChannel !== "all" && thread.channel !== activeChannel) {
        return false;
      }
      if (!searchText.trim()) {
        return true;
      }
      const keyword = searchText.trim().toLowerCase();
      return (
        thread.title.toLowerCase().includes(keyword) ||
        thread.participantName.toLowerCase().includes(keyword) ||
        thread.lastMessage.toLowerCase().includes(keyword)
      );
    });
  }, [activeAgentId, activeChannel, conversationsByAgent, searchText]);

  useEffect(() => {
    if (
      workspaceTab === "workbench" ||
      workspaceTab === "monitor" ||
      workspaceTab === "crm" ||
      workspaceTab === "settings" ||
      workspaceTab === "profile" ||
      !auth ||
      !activeConversationId
    ) {
      return;
    }
    void loadConversationMessages(activeConversationId, true);
  }, [activeConversationId, auth?.token, workspaceTab]);

  useEffect(() => {
    if (workspaceTab === "workbench" || workspaceTab === "monitor" || workspaceTab === "crm" || workspaceTab === "settings" || workspaceTab === "profile") {
      return;
    }
    if (availableThreads.some((thread) => thread.id === activeConversationId)) {
      return;
    }
    setActiveConversationId(availableThreads[0]?.id ?? "");
  }, [activeConversationId, availableThreads, workspaceTab]);

  const activeConversation = useMemo(() => {
    return availableThreads.find((thread) => thread.id === activeConversationId) ?? availableThreads[0] ?? null;
  }, [activeConversationId, availableThreads]);

  const conversationMessagesLoaded = activeConversation ? activeConversation.id in conversationMessages : false;
  const conversationHistoryLoading =
    !!activeConversation && conversationHistoryLoadingId === activeConversation.id && !conversationMessagesLoaded;
  const messages = activeConversation ? conversationMessages[activeConversation.id] ?? [] : [];
  const activeWorkbenchAgent =
    workbenchDockAgents.find((a) => a.key === activeWorkbenchKey) ??
    workbenchDockAgents[0] ??
    WORKBENCH_DOCK_AGENTS[0];
  const activeWorkbenchAgentId = activeWorkbenchAgent.runtimeAgentId;
  const activeWorkbenchSkillBindingsLoaded = Object.prototype.hasOwnProperty.call(
    agentSkillBindingsByAgent,
    activeWorkbenchAgentId,
  );
  const activeWorkbenchSkillBindings = agentSkillBindingsByAgent[activeWorkbenchAgentId] ?? [];
  const activeWorkbenchSkillLoading = !!agentSkillBindingsLoadingByAgent[activeWorkbenchAgentId];
  const activeWorkbenchSkillLoadFailed = !!agentSkillBindingsFailedByAgent[activeWorkbenchAgentId];
  const activeWorkbenchSkillCode = activeSkillCodeByAgent[activeWorkbenchAgentId] ?? "";
  const activeWorkbenchSkill = activeWorkbenchSkillBindings.find((item) => item.skillCode === activeWorkbenchSkillCode) ?? null;
  const activeQuickCommands = quickCommandsByAgent[activeWorkbenchAgentId] ?? [];
  const activeQuickCommandsLoading = !!quickCommandsLoadingByAgent[activeWorkbenchAgentId];
  const workbenchSessionThreads = useMemo(() => {
    return conversationThreads
      .filter((thread) => isWorkbenchSessionIdForAgent(thread.id, activeWorkbenchKey))
      .sort((a, b) => (b.updatedAt ?? "").localeCompare(a.updatedAt ?? ""));
  }, [activeWorkbenchKey, conversationThreads]);
  const activeWorkbenchSessionId =
    activeWorkbenchSessionIdByAgent[activeWorkbenchKey] ??
    workbenchSessionThreads[0]?.id ??
    buildWorkbenchSessionId(activeWorkbenchKey);
  const workbenchMessages = workbenchMessagesByAgent[activeWorkbenchKey] ?? [];
  const activeWorkbenchState = workbenchRuntimeByAgent[activeWorkbenchKey] ?? getWorkbenchDefaultState(activeWorkbenchKey);
  const activeWorkbenchThoughts = activeWorkbenchState.thoughts.length ? activeWorkbenchState.thoughts : ["等待新的业务上下文"];
  // Keep the streaming assistant placeholder even when content is still empty so the
  // bubble appears immediately and text flows in incrementally.
  const workbenchConversation = workbenchMessages.filter(
    (message) => message.role === "assistant" ? (chatLoading || message.content.trim()) : message.content.trim(),
  );
  const visibleMessages = workspaceTab === "workbench" ? workbenchMessages : messages;

  useEffect(() => {
    if (!auth?.token || workspaceTab !== "workbench") {
      return;
    }
    if (activeWorkbenchSkillBindingsLoaded || activeWorkbenchSkillLoadFailed) {
      return;
    }
    void loadAgentSkillBindings(activeWorkbenchAgentId, auth.token);
  }, [activeWorkbenchAgentId, activeWorkbenchSkillBindingsLoaded, activeWorkbenchSkillLoadFailed, auth?.token, workspaceTab]);

  useEffect(() => {
    if (!auth?.token || workspaceTab !== "workbench" || !quickCommandMenuOpen) {
      return;
    }
    if (quickCommandsByAgent[activeWorkbenchAgentId]) {
      return;
    }
    void loadQuickCommands(activeWorkbenchAgentId, auth.token);
  }, [activeWorkbenchAgentId, auth?.token, quickCommandMenuOpen, quickCommandsByAgent, workspaceTab]);
  const activeKbNames = kbs.filter((kb) => selectedKbIds.includes(kb.id)).map((kb) => kb.name).join(", ") || "未选择";
  const userInitial = getDisplayInitial(me?.displayName || me?.nickname || me?.mobile || "我", "我");
  const currentOrgName = me?.orgName || auth?.orgName || auth?.orgId || "当前组织";
  const organizationOptions = auth
    ? organizations.length
      ? organizations
      : [{ orgId: auth.orgId, orgName: currentOrgName, memberId: auth.memberId ?? auth.userId, roleCode: auth.roles[0] ?? "", current: true }]
    : [];
  const agentUnread = (conversationsByAgent.get(activeAgent.id) ?? []).reduce((count, thread) => count + thread.unread, 0);
  const activeWorkbenchBusy = activeWorkbenchState.status !== "待命中" && activeWorkbenchState.status !== "已完成";
  const monitorRows = workbenchDockAgents.map((agent, index) => {
    const runtime = workbenchRuntimeByAgent[agent.key] ?? getWorkbenchDefaultState(agent.key);
    const threads = conversationsByAgent.get(agent.runtimeAgentId ?? agent.key) ?? [];
    const unread = threads.reduce((sum, item) => sum + item.unread, 0);
    const severity = runtime.status === "待命中" ? "idle" : runtime.status === "已完成" ? "ok" : runtime.status === "等待确认" ? "warn" : "busy";
    return {
      key: agent.key,
      name: agent.name,
      short: agent.short,
      avatarBase64: agent.avatarBase64,
      color: agent.color,
      status: runtime.status,
      currentTask: runtime.currentTask,
      nextTask: runtime.nextTask,
      previousTask: runtime.previousTask,
      thoughts: runtime.thoughts,
      unread,
      threadCount: threads.length,
      severity,
    } as const;
  });
  const monitorBusyCount = monitorRows.filter((row) => row.severity === "busy" || row.severity === "warn").length;
  const monitorActiveAgentKey = activeMonitorAgentKey || monitorRows[0]?.key || "";
  const monitorStatusClass = (severity: string) =>
    severity === "busy" ? "is-running" : severity === "warn" ? "is-waiting" : severity === "ok" ? "is-ok" : "is-idle";
  const monitorLogRows = monitorRunLogs.map((item) => {
    const matchedAgent = monitorRows.find((candidate) => candidate.key === item.agentId || candidate.key === item.agentId);
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
      agentKey: item.agentId ?? matchedAgent?.key ?? "",
      agentName: item.agentName ?? matchedAgent?.name ?? item.agentId ?? "智能体",
      title: item.title || item.summary || "未命名运行记录",
      detail: `${monitorChannelLabel(item.channel)} · ${formatMonitorDateTime(item.startedAt)}`,
      status: monitorStatusLabel(item.status),
      rawStatus: item.status ?? "",
      severity,
      chain: chainParts.length ? chainParts.join(" · ") : "消息链路",
      latency: formatMonitorElapsed(item.elapsedMs),
      summary: item.summary ?? "",
      source: item.source ?? "trace",
    };
  });
  const monitorSearchQuery = monitorSearchText.trim().toLowerCase();
  const monitorFilteredLogs = (activeMonitorAgentKey
    ? monitorLogRows.filter((item) => item.agentKey === activeMonitorAgentKey)
    : monitorLogRows
  ).filter((item) => {
    if (!monitorSearchQuery) {
      return true;
    }
    return [
      item.recordId,
      item.title,
      item.detail,
      item.agentName,
      item.summary,
      item.sessionId,
    ].some((value) => value.toLowerCase().includes(monitorSearchQuery));
  });
  const monitorSelectedLog =
    monitorFilteredLogs.find((item) => item.id === activeMonitorLogId) ??
    monitorFilteredLogs[0] ??
    (activeMonitorAgentKey ? undefined : monitorLogRows[0]);
  const monitorSelectedTrace =
    monitorTraceDetail && monitorSelectedLog && monitorTraceDetail.traceId === monitorSelectedLog.traceId
      ? monitorTraceDetail
      : null;
  const monitorVisibleThreadCount = monitorRows.reduce((sum, row) => sum + row.threadCount, 0);

  useEffect(() => {
    if (workspaceTab !== "monitor") {
      return;
    }
    if (activeMonitorAgentKey && !monitorRows.some((row) => row.key === activeMonitorAgentKey)) {
      setActiveMonitorAgentKey("");
      return;
    }
    if (monitorSelectedLog && activeMonitorLogId !== monitorSelectedLog.id) {
      setActiveMonitorLogId(monitorSelectedLog.id);
    }
  }, [activeMonitorAgentKey, activeMonitorLogId, monitorRows, monitorSelectedLog, workspaceTab]);

  useEffect(() => {
    if (workspaceTab !== "monitor" || !auth?.token) {
      return;
    }
    void loadWorkbenchAgents(auth.token);
    void loadWorkbenchStats(auth.token);
    void loadMonitorRunLogs(auth.token);
  }, [auth?.token, workspaceTab]);

  useEffect(() => {
    if (workspaceTab !== "monitor" || !auth?.token || !monitorSelectedLog?.traceId) {
      setMonitorTraceDetail(null);
      return;
    }
    void loadMonitorTraceDetail(monitorSelectedLog.traceId, auth.token);
  }, [auth?.token, monitorSelectedLog?.traceId, workspaceTab]);

  useEffect(() => {
    if (workspaceTab !== "workbench") {
      return;
    }
    if (auth) {
      void loadWorkbenchAgents(auth.token);
      void loadWorkbenchStats(auth.token);
      void loadWorkbenchMessages(activeWorkbenchKey, activeWorkbenchSessionId, true);
    }
    setActiveWorkbenchSessionIdByAgent((prev) => {
      if (prev[activeWorkbenchKey] === activeWorkbenchSessionId) {
        return prev;
      }
      return { ...prev, [activeWorkbenchKey]: activeWorkbenchSessionId };
    });
    setWorkbenchThoughtIndex(0);
    const timer = window.setInterval(() => {
      setWorkbenchThoughtIndex((current) => current + 1);
    }, 1800);
    return () => window.clearInterval(timer);
  }, [activeWorkbenchKey, activeWorkbenchSessionId, auth?.token, workspaceTab]);

  useEffect(() => {
    if (!openWorkbenchSessionMenuId) {
      return;
    }
    const handlePointerDown = (event: MouseEvent) => {
      const target = event.target as HTMLElement | null;
      if (!target?.closest(".cici-workbench__session-actions")) {
        setOpenWorkbenchSessionMenuId("");
      }
    };
    document.addEventListener("mousedown", handlePointerDown);
    return () => document.removeEventListener("mousedown", handlePointerDown);
  }, [openWorkbenchSessionMenuId]);

  const completeLogin = async (payload: AuthPayload, message = "登录成功。") => {
    setPendingOrganizations([]);
    setRegisterMode(false);
    setOrganizationMenuOpen(false);
    persistAuth(payload);
    await loadMe(payload.token);
    await loadOrganizations(payload.token);
    setNotice(message);
  };

  const login = async () => {
    if (loginSubmitting) {
      return;
    }
    setLoginSubmitting(true);
    try {
      setNotice("登录中...");
      const response = await fetch("/auth/password/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ identifier: mobile, password: loginPassword }),
      });
      const { body } = await safeFetchJson<LoginPayload>(response);
      if (!response.ok || !body?.success) {
        setNotice(`登录失败：${body?.message ?? `HTTP ${response.status}`}`);
        return;
      }
      if (body.data?.requiresOrganizationSelection) {
        setPendingOrganizations(body.data.organizations ?? []);
        setNotice("请选择要进入的组织。");
        return;
      }
      if (!body.data?.token) {
        setNotice("登录失败：服务端未返回 token");
        return;
      }

      if (FRONT_LOGIN_USER_MODE_CONFIG === "agent" && FRONT_LOGIN_MODE_CONFIG === "login_mode2") {
        setNotice("");
        setLoginMode2Entering(true);
        setLoginMode2CubePhase("loading");
        await new Promise((resolve) => window.setTimeout(resolve, LOGIN_MODE2_ENTER_DELAY_MS));
      }

      await completeLogin(body.data);
    } catch (error) {
      setNotice(`登录失败：${error instanceof Error ? error.message : String(error)}`);
      setLoginMode2Entering(false);
    } finally {
      setLoginSubmitting(false);
    }
  };

  const loginToOrganization = async (targetOrgId: string) => {
    if (loginSubmitting) {
      return;
    }
    setLoginSubmitting(true);
    try {
      setNotice("正在进入组织...");
      const response = await fetch("/auth/password/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ orgId: targetOrgId, identifier: mobile, password: loginPassword }),
      });
      const { body } = await safeFetchJson<AuthPayload>(response);
      if (!response.ok || !body?.success || !body.data?.token) {
        setNotice(`进入失败：${body?.message ?? `HTTP ${response.status}`}`);
        return;
      }
      await completeLogin(body.data);
    } catch (error) {
      setNotice(`进入失败：${error instanceof Error ? error.message : String(error)}`);
    } finally {
      setLoginSubmitting(false);
    }
  };

  const register = async () => {
    if (loginSubmitting) {
      return;
    }
    setLoginSubmitting(true);
    try {
      setNotice("正在创建组织...");
      const response = await fetch("/auth/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ mobile, password: loginPassword, organizationName }),
      });
      const { body } = await safeFetchJson<AuthPayload>(response);
      if (!response.ok || !body?.success || !body.data?.token) {
        setNotice(`创建失败：${body?.message ?? `HTTP ${response.status}`}`);
        return;
      }
      await completeLogin(body.data, "组织已创建。");
    } catch (error) {
      setNotice(`创建失败：${error instanceof Error ? error.message : String(error)}`);
    } finally {
      setLoginSubmitting(false);
    }
  };

  const switchOrganization = async (targetOrgId: string) => {
    if (!auth?.token || targetOrgId === auth.orgId) {
      setOrganizationMenuOpen(false);
      return;
    }
    try {
      setNotice("正在切换组织...");
      const response = await fetch("/auth/switch-organization", {
        method: "POST",
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${auth.token}` },
        body: JSON.stringify({ orgId: targetOrgId }),
      });
      const { body } = await safeFetchJson<AuthPayload>(response);
      if (!response.ok || !body?.success || !body.data?.token) {
        setNotice(`切换失败：${body?.message ?? `HTTP ${response.status}`}`);
        return;
      }
      await completeLogin(body.data, "组织已切换。");
    } catch (error) {
      setNotice(`切换失败：${error instanceof Error ? error.message : String(error)}`);
    }
  };

  const toggleKb = (id: number) => {
    setSelectedKbIds((prev) => (prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id]));
  };

  const updateConversationMessages = (
    conversationId: string,
    updater: (items: ChatBubble[]) => ChatBubble[],
  ) => {
    setConversationMessages((prev) => {
      const current = prev[conversationId] ?? [];
      return { ...prev, [conversationId]: updater(current) };
    });
  };

  const closeApprovalDrawer = () => {
    setApprovalDrawerOpen(false);
  };

  const startNewWorkbenchConversation = () => {
    const sessionId = createWorkbenchSessionId(activeWorkbenchKey);
    const draft = createDraftConversationThread(
      sessionId,
      activeWorkbenchAgent.runtimeAgentId,
      me?.nickname ?? me?.mobile ?? "我",
      "新工作台对话",
      me?.avatarBase64 ?? "",
    );
    setConversationThreads((prev) => [draft, ...prev.filter((item) => item.id !== sessionId)]);
    setConversationMessages((prev) => ({ ...prev, [sessionId]: [] }));
    setWorkbenchMessagesByAgent((prev) => ({ ...prev, [activeWorkbenchKey]: [] }));
    setActiveWorkbenchSessionIdByAgent((prev) => ({ ...prev, [activeWorkbenchKey]: sessionId }));
  };

  const selectWorkbenchConversation = async (sessionId: string) => {
    setActiveWorkbenchSessionIdByAgent((prev) => ({ ...prev, [activeWorkbenchKey]: sessionId }));
    await loadWorkbenchMessages(activeWorkbenchKey, sessionId, true);
  };

  const downloadWorkbenchConversation = async (session: ConversationThread) => {
    if (!auth) {
      return;
    }
    const sessionId = session.id;
    let items = conversationMessages[sessionId] ?? [];
    if (items.length === 0) {
      try {
        const response = await fetch(`/ai/sessions/${encodeURIComponent(sessionId)}/messages`, {
          headers: { Authorization: `Bearer ${auth.token}` },
        });
        if (response.ok) {
          const { body } = await safeFetchJson<ConversationMessagePayload[]>(response);
          if (body?.success) {
            items = normalizeConversationMessages((body.data ?? []) as ConversationMessagePayload[]);
            setConversationMessages((prev) => ({ ...prev, [sessionId]: items }));
          }
        }
      } catch {
        // fallback to current in-memory messages
      }
    }
    const title = (session.title || "工作台会话").replace(/[\\/:*?"<>|]/g, "_");
    const markdown = [
      `# ${session.title || "工作台会话"}`,
      "",
      `- 会话ID: \`${session.id}\``,
      `- 下载时间: ${new Date().toLocaleString("zh-CN")}`,
      "",
      "---",
      "",
      ...items.flatMap((message, index) => [
        `## ${index + 1}. ${message.role === "user" ? "用户" : "思思"}${message.time ? ` (${message.time})` : ""}`,
        "",
        message.content || "（空）",
        "",
      ]),
    ].join("\n");
    const blob = new Blob([markdown], { type: "text/markdown;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `${title}.md`;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
  };

  const deleteWorkbenchConversation = async (session: ConversationThread) => {
    if (!auth) {
      return;
    }
    const shouldDelete = window.confirm(`确认删除会话「${session.title || session.id}」吗？`);
    if (!shouldDelete) {
      return;
    }
    if (!session.id.startsWith("workbench:")) {
      return;
    }
    try {
      await fetch(`/ai/sessions/${encodeURIComponent(session.id)}`, {
        method: "DELETE",
        headers: { Authorization: `Bearer ${auth.token}` },
      });
    } catch {
      // ignore network errors, still remove local draft
    }
    setConversationThreads((prev) => prev.filter((item) => item.id !== session.id));
    setConversationMessages((prev) => {
      const next = { ...prev };
      delete next[session.id];
      return next;
    });
    if (session.id === activeWorkbenchSessionId) {
      const nextSessionId =
        workbenchSessionThreads.find((item) => item.id !== session.id)?.id ?? buildWorkbenchSessionId(activeWorkbenchKey);
      setActiveWorkbenchSessionIdByAgent((prev) => ({ ...prev, [activeWorkbenchKey]: nextSessionId }));
      await loadWorkbenchMessages(activeWorkbenchKey, nextSessionId, true);
    }
  };

  const updateMeetingTranscript = (updater: (prev: MeetingTranscriptSegment[]) => MeetingTranscriptSegment[]) => {
    setMeetingTranscript((prev) => {
      const next = updater(prev);
      meetingTranscriptRef.current = next;
      return next;
    });
  };

  const buildMeetingSegment = (text: string, speakerId?: string, speakerName?: string): MeetingTranscriptSegment => {
    const safeSpeakerId = speakerId?.trim() || "1";
    const safeSpeakerName = meetingSpeakerNamesRef.current[safeSpeakerId]?.trim() || speakerName?.trim() || speakerDisplayName(safeSpeakerId);
    return {
      id: `meeting-${Date.now()}-${Math.random().toString(16).slice(2)}`,
      speakerId: safeSpeakerId,
      speakerName: safeSpeakerName,
      text: text.trim(),
      time: formatWorkbenchTime(),
    };
  };

  const summarizeMeetingTranscript = async (fallbackText = "") => {
    if (!auth?.token) {
      return;
    }
    let segments = meetingTranscriptRef.current.filter((segment) => segment.text.trim());
    if (segments.length === 0 && fallbackText.trim()) {
      segments = [buildMeetingSegment(fallbackText)];
      meetingTranscriptRef.current = segments;
      setMeetingTranscript(segments);
    }
    if (segments.length === 0) {
      setMeetingStatus("error");
      setMeetingNotice("没有可生成纪要的转写内容。");
      return;
    }
    setMeetingStatus("summarizing");
    setMeetingNotice("正在调用 AI 听记技能生成会议纪要...");
    try {
      const response = await fetch("/ai/meeting-minutes/summary", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${auth.token}`,
        },
        body: JSON.stringify({
          title: "会议纪要",
          transcript: segments.map((segment) => ({
            speakerId: segment.speakerId,
            speakerName: segment.speakerName,
            text: segment.text,
            startMs: segment.startMs,
            endMs: segment.endMs,
          })),
        }),
      });
      const body = await response.json().catch(() => null) as { data?: { summary?: string; skillName?: string }; message?: string } | null;
      if (!response.ok || !body?.data?.summary) {
        throw new Error(body?.message || `HTTP ${response.status}`);
      }
      setMeetingSummary(body.data.summary);
      setMeetingStatus("done");
      setMeetingNotice(`${body.data.skillName || "AI 听记"}技能已生成会议纪要。`);
    } catch (error) {
      setMeetingStatus("error");
      setMeetingNotice(`会议纪要生成失败：${error instanceof Error ? error.message : String(error)}`);
    }
  };

  const startMeetingMinutes = async (triggerText: string, source: "workbench" | "aiApps" = "workbench") => {
    if (!auth) {
      setSpeechNotice("请先登录后再开始会议纪要。");
      return;
    }
    if (source === "workbench" && workspaceTab !== "workbench") {
      return;
    }
    if (listening) {
      setSpeechNotice("当前已有语音识别在进行，请先结束后再开始会议纪要。");
      return;
    }
    if (!speechSupported) {
      setSpeechNotice("当前浏览器不支持录音。");
      return;
    }

    const timestamp = formatWorkbenchTime();
    const agentKey = activeWorkbenchAgent.key;
    const sessionId = activeWorkbenchSessionId;
    const cleanTrigger = triggerText.trim();
    setInput("");
    setSkillPickerOpen(false);
    setQuickCommandMenuOpen(false);
    setMeetingDrawerOpen(source === "workbench");
    setMeetingStatus("recording");
    setMeetingNotice("正在请求麦克风权限...");
    setMeetingSummary("");
    setMeetingPartial(null);
    setMeetingSpeakerNames({});
    setMeetingSpeakerEdit(null);
    meetingShouldSummarizeRef.current = true;
    meetingTranscriptRef.current = [];
    meetingSpeakerNamesRef.current = {};
    setMeetingTranscript([]);

    if (source === "workbench") {
      const userBubble: ChatBubble = { role: "user", content: cleanTrigger, time: timestamp };
      const assistantBubble: ChatBubble = {
        role: "assistant",
        content: "已打开实时会议纪要。录音开始后，我会按发言人整理转写，结束时生成会议纪要。",
        time: timestamp,
      };
      setConversationThreads((prev) =>
        prev.map((thread) =>
          thread.id === sessionId
            ? {
                ...thread,
                title: thread.title === "新工作台对话" ? "会议纪要" : thread.title,
                lastMessage: cleanTrigger,
                time: timestamp,
                updatedAt: new Date().toISOString(),
              }
            : thread,
        ),
      );
      setWorkbenchMessagesByAgent((prev) => ({
        ...prev,
        [agentKey]: [...(prev[agentKey] ?? []), userBubble, assistantBubble],
      }));
      setConversationMessages((prev) => ({
        ...prev,
        [sessionId]: [...(prev[sessionId] ?? workbenchMessages), userBubble, assistantBubble],
      }));
      setWorkbenchRuntimeByAgent((prev) => ({
        ...prev,
        [agentKey]: {
          status: "处理中",
          previousTask: prev[agentKey]?.currentTask ?? "—",
          currentTask: "实时会议听记中",
          nextTask: "结束会议后生成纪要",
          thoughts: ["正在监听麦克风音频", "转写结果会按发言人实时显示"],
        },
      }));
    }

    await startAsrSession({
      token: auth.token,
      provider: "iflytek",
      speakerDiarization: true,
      getPrefix: () => "",
      onLiveText: () => {},
      onNotice: (message) => {
        const setupMessage =
          message.includes("Iflytek realtime ASR credentials are missing") ||
          message.includes("Iflytek realtime ASR is disabled")
            ? "讯飞实时转写未配置或未启用，请联系管理员在「管理后台 → 集成应用 → 讯飞实时转写」完成配置。"
            : message;
        setMeetingNotice(setupMessage);
        if (setupMessage.includes("失败") || message.includes("missing") || message.includes("disabled")) {
          setMeetingStatus("error");
        }
      },
      onTranscriptEvent: (event) => {
        if (!event.text.trim()) {
          return;
        }
        const segment = buildMeetingSegment(event.text, event.speakerId, event.speakerName);
        if (event.type === "partial") {
          setMeetingPartial(segment);
          return;
        }
        setMeetingPartial(null);
        updateMeetingTranscript((prev) => appendMeetingTranscriptSegment(prev, segment));
      },
      onFinished: async ({ asrText }) => {
        if (!meetingShouldSummarizeRef.current) {
          return;
        }
        meetingShouldSummarizeRef.current = false;
        await summarizeMeetingTranscript(asrText);
      },
    });
  };

  const stopMeetingAndSummarize = () => {
    if (meetingStatus === "recording") {
      setMeetingStatus("stopping");
      setMeetingNotice("正在结束录音...");
      stopAsrSession();
      return;
    }
    if (meetingStatus === "error" && meetingTranscriptRef.current.length > 0) {
      meetingShouldSummarizeRef.current = false;
      void summarizeMeetingTranscript();
    }
  };

  const closeMeetingDrawer = () => {
    if (meetingStatus === "recording" || meetingStatus === "stopping") {
      meetingShouldSummarizeRef.current = false;
      stopAsrSession();
    }
    setMeetingDrawerOpen(false);
    setMeetingSpeakerEdit(null);
  };

  const startMeetingSpeakerEdit = (lineId: string, speakerId: string, speakerName: string) => {
    setMeetingSpeakerEdit({
      lineId,
      speakerId,
      value: meetingSpeakerNames[speakerId] || speakerName || speakerDisplayName(speakerId),
    });
  };

  const commitMeetingSpeakerEdit = () => {
    if (!meetingSpeakerEdit) {
      return;
    }
    const nextName = meetingSpeakerEdit.value.trim() || speakerDisplayName(meetingSpeakerEdit.speakerId);
    meetingSpeakerNamesRef.current = { ...meetingSpeakerNamesRef.current, [meetingSpeakerEdit.speakerId]: nextName };
    setMeetingSpeakerNames((prev) => ({ ...prev, [meetingSpeakerEdit.speakerId]: nextName }));
    updateMeetingTranscript((prev) =>
      prev.map((segment) =>
        segment.speakerId === meetingSpeakerEdit.speakerId ? { ...segment, speakerName: nextName } : segment,
      ),
    );
    setMeetingPartial((prev) =>
      prev?.speakerId === meetingSpeakerEdit.speakerId ? { ...prev, speakerName: nextName } : prev,
    );
    setMeetingSpeakerEdit(null);
  };

  const cancelMeetingSpeakerEdit = () => {
    setMeetingSpeakerEdit(null);
  };

  const handleMeetingSpeakerEditKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "Enter") {
      event.preventDefault();
      commitMeetingSpeakerEdit();
    }
    if (event.key === "Escape") {
      event.preventDefault();
      cancelMeetingSpeakerEdit();
    }
  };

  const submitQuestion = async (question: string) => {
    if (!auth || !question.trim() || chatLoading) {
      return;
    }
    if (workspaceTab !== "workbench" && !activeConversation) {
      return;
    }
    const cleanQuestion = question.trim();
    const firstLine = cleanQuestion.split("\n")[0]?.trim() || cleanQuestion;
    const nextConversationTitle = firstLine.slice(0, 24);
    const timestamp = formatWorkbenchTime();
    setInput("");
    const isWorkbench = workspaceTab === "workbench";
    const conversationId = activeConversation?.id ?? "workbench";
    const sessionId = isWorkbench ? activeWorkbenchSessionId : conversationId;
    const userBubble: ChatBubble = { role: "user", content: cleanQuestion, time: timestamp };
    const assistantPlaceholder: ChatBubble = { role: "assistant", content: "", time: timestamp };
    if (isWorkbench) {
      const agentKey = activeWorkbenchAgent.key;
      setConversationThreads((prev) =>
        prev.map((thread) =>
          thread.id === activeWorkbenchSessionId
            ? {
                ...thread,
                title: thread.title === "新工作台对话" ? nextConversationTitle : thread.title,
                lastMessage: cleanQuestion,
                time: timestamp,
                updatedAt: new Date().toISOString(),
              }
            : thread,
        ),
      );
      setWorkbenchRuntimeByAgent((prev) => ({
        ...prev,
        [agentKey]: deriveWorkbenchStateFromPrompt(cleanQuestion, agentKey),
      }));
      setWorkbenchMessagesByAgent((prev) => ({
        ...prev,
        [agentKey]: [...(prev[agentKey] ?? []), userBubble, assistantPlaceholder],
      }));
      setConversationMessages((prev) => ({
        ...prev,
        [sessionId]: [...(prev[sessionId] ?? workbenchMessages), userBubble, assistantPlaceholder],
      }));
    } else {
      updateConversationMessages(conversationId, (prev) => [...prev, userBubble, assistantPlaceholder]);
    }
    setChatLoading(true);

    let renderedApproval = false;
    let suppress = false;
    let firstDeltaSeen = false;
    let streamedAssistantText = "";

    try {
      const kbIds = selectedKbIds.map(String);
      await streamAiChat(
        auth.token,
        {
          sessionId,
          question: cleanQuestion,
          knowledgeBaseIds: kbIds.length ? kbIds : [],
          agentId: isWorkbench ? activeWorkbenchAgent.runtimeAgentId : activeAgent.id,
          activeSkillCode: isWorkbench && activeWorkbenchSkillCode ? activeWorkbenchSkillCode : undefined,
        },
        (delta) => {
          if (suppress) {
            return;
          }
          streamedAssistantText += delta;
          if (isWorkbench && !firstDeltaSeen) {
            firstDeltaSeen = true;
            const agentKey = activeWorkbenchAgent.key;
            setWorkbenchRuntimeByAgent((prev) => ({
              ...prev,
              [agentKey]: {
                status: "处理中",
                previousTask: prev[agentKey]?.currentTask ?? "—",
                currentTask: "正在生成回复",
                nextTask: "输出完成后等待下一指令",
                thoughts: ["AI 正在流式输出回复内容…"],
              },
            }));
          }
          if (isWorkbench) {
            const agentKey = activeWorkbenchAgent.key;
            setWorkbenchMessagesByAgent((prev) => ({
              ...prev,
              [agentKey]: appendAssistantDelta(prev[agentKey] ?? [], delta, timestamp),
            }));
            setConversationMessages((prev) => ({
              ...prev,
              [sessionId]: appendAssistantDelta(prev[sessionId] ?? [], delta, timestamp),
            }));
          } else {
            updateConversationMessages(conversationId, (prev) => appendAssistantDelta(prev, delta, timestamp));
          }
        },
        (event: StreamToolResultEvent) => {
          if (event.toolName.toLowerCase() !== "get_pending_approvals") {
            return;
          }
          if (isWorkbench) {
            const agentKey = activeWorkbenchAgent.key;
            setWorkbenchRuntimeByAgent((prev) => ({
              ...prev,
              [agentKey]: {
                status: "等待确认",
                previousTask: "已识别审批工具响应",
                currentTask: "等待你确认审批页面内容",
                nextTask: "继续整理审批建议",
                thoughts: ["审批工具已返回结果", "已切换到审批页面供你确认"],
              },
            }));
          }
          setApprovalPageHtml(buildPendingApprovalsHtml(event.payload));
          setTimeout(() => setApprovalDrawerOpen(true), 24);
          renderedApproval = true;
          // Keep streaming visible in chat; approval drawer is an auxiliary surface.
          suppress = false;
        },
        (event: StreamToolCallEvent) => {
          if (!isWorkbench) return;
          const agentKey = activeWorkbenchAgent.key;
          const label = toolCallLabel(event.toolName);
          setWorkbenchRuntimeByAgent((prev) => ({
            ...prev,
            [agentKey]: {
              status: "处理中",
              previousTask: prev[agentKey]?.currentTask ?? "—",
              currentTask: `工具调用中：${label}`,
              nextTask: "整合工具结果并生成回复",
              thoughts: [`正在执行工具 ${event.toolName}…`, "工具返回后将继续生成回复"],
            },
          }));
        },
        (event: StreamPhaseEvent) => {
          if (isWorkbench) {
            const agentKey = activeWorkbenchAgent.key;
            const kbNames = event.knowledgeBaseNames?.length
              ? event.knowledgeBaseNames
              : (event.knowledgeBaseIds ?? []).map((id) => kbs.find((kb) => String(kb.id) === String(id))?.name ?? `知识库 ${id}`);
            const kbLabel = kbNames.length ? kbNames.join("、") : "已授权知识库";
            if (event.phase === "retrieving") {
              setWorkbenchRuntimeByAgent((prev) => ({
                ...prev,
                [agentKey]: {
                  status: "检索中",
                  previousTask: prev[agentKey]?.currentTask ?? "—",
                  currentTask: `正在检索知识库：${kbLabel}`,
                  nextTask: "命中知识片段后生成回复",
                  thoughts: [`检索范围：${kbLabel}`, "正在完成向量召回与权限校验"],
                },
              }));
            } else if (event.phase === "rag_done") {
              const elapsed = typeof event.elapsedMs === "number" ? `（${event.elapsedMs}ms）` : "";
              const count = typeof event.contextCount === "number" ? event.contextCount : 0;
              setWorkbenchRuntimeByAgent((prev) => ({
                ...prev,
                [agentKey]: {
                  status: "处理中",
                  previousTask: `知识库检索完成${elapsed}`,
                  currentTask: count > 0 ? `已命中 ${count} 条知识片段` : "未命中知识片段，转入模型判断",
                  nextTask: "基于问题与上下文生成回复",
                  thoughts: [
                    kbNames.length ? `引用知识库：${kbLabel}` : "本轮没有可展示的知识库名称",
                    event.fallbackUsed ? "向量召回为空，已使用最新可检索切片兜底" : "向量召回已完成",
                  ],
                },
              }));
            } else if (event.phase === "generating") {
              setWorkbenchRuntimeByAgent((prev) => ({
                ...prev,
                [agentKey]: {
                  status: "处理中",
                  previousTask: prev[agentKey]?.currentTask ?? "知识上下文已准备",
                  currentTask: "正在生成回复",
                  nextTask: "输出完成后等待下一指令",
                  thoughts: ["AI 正在组织知识库内容与回答结构"],
                },
              }));
            }
          }
          if (!event.modelName?.trim()) {
            return;
          }
          if (isWorkbench) {
            const agentKey = activeWorkbenchAgent.key;
            setWorkbenchMessagesByAgent((prev) => ({
              ...prev,
              [agentKey]: markTrailingAssistantModel(prev[agentKey] ?? [], event.modelName ?? "", timestamp),
            }));
            setConversationMessages((prev) => ({
              ...prev,
              [sessionId]: markTrailingAssistantModel(prev[sessionId] ?? [], event.modelName ?? "", timestamp),
            }));
          } else {
            updateConversationMessages(conversationId, (prev) =>
              markTrailingAssistantModel(prev, event.modelName ?? "", timestamp),
            );
          }
        },
      );

      if (renderedApproval) {
        if (isWorkbench) {
          const agentKey = activeWorkbenchAgent.key;
          setWorkbenchMessagesByAgent((prev) => ({
            ...prev,
            [agentKey]: replaceTrailingAssistant(prev[agentKey] ?? [], "已为你生成审批页面。", timestamp),
          }));
          setConversationMessages((prev) => ({
            ...prev,
            [sessionId]: replaceTrailingAssistant(prev[sessionId] ?? [], "已为你生成审批页面。", timestamp),
          }));
        } else {
          updateConversationMessages(conversationId, (prev) => replaceTrailingAssistant(prev, "已为你生成审批页面。"));
        }
      }
      if (isWorkbench) {
        const agentKey = activeWorkbenchAgent.key;
        setWorkbenchRuntimeByAgent((prev) => ({
          ...prev,
          [agentKey]: finishWorkbenchState(agentKey, prev[agentKey]?.currentTask, streamedAssistantText),
        }));
        await loadWorkbenchMessages(agentKey, sessionId, true);
      }
      if (!isWorkbench) {
        await loadConversationThreads(conversationId);
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      if (isWorkbench) {
        const agentKey = activeWorkbenchAgent.key;
        setWorkbenchRuntimeByAgent((prev) => ({
          ...prev,
          [agentKey]: {
            status: "等待确认",
            previousTask: prev[agentKey]?.currentTask ?? "处理中断",
            currentTask: "本轮处理出现中断",
            nextTask: "等待你重试或补充说明",
            thoughts: ["流式输出被中断", "可以重试，或补充更具体的任务描述"],
          },
        }));
        setWorkbenchMessagesByAgent((prev) => {
          const current = prev[agentKey] ?? [];
          const last = current[current.length - 1];
          const content = last?.role === "assistant" && last.content.trim()
            ? `${last.content}\n\n*(流式输出中断：${message})*`
            : `暂时无法完成回答：${message}`;
          const next = replaceTrailingAssistant(current, content, timestamp);
          return { ...prev, [agentKey]: next };
        });
        setConversationMessages((prev) => {
          const current = prev[sessionId] ?? [];
          const last = current[current.length - 1];
          const content = last?.role === "assistant" && last.content.trim()
            ? `${last.content}\n\n*(流式输出中断：${message})*`
            : `暂时无法完成回答：${message}`;
          return { ...prev, [sessionId]: replaceTrailingAssistant(current, content, timestamp) };
        });
      } else {
        updateConversationMessages(conversationId, (prev) => {
          const last = prev[prev.length - 1];
          const content = last?.role === "assistant" && last.content.trim()
            ? `${last.content}\n\n*(流式输出中断：${message})*`
            : `暂时无法完成回答：${message}`;
          return replaceTrailingAssistant(prev, content);
        });
      }
    } finally {
      setChatLoading(false);
      if (auth?.token) {
        void loadMonitorRunLogs(auth.token);
      }
    }
  };

  const runWorkbenchPrompt = async (prompt: string) => {
    if (workspaceTab !== "workbench") {
      setWorkspaceTab("workbench");
    }
    await submitQuestion(prompt);
  };

  const submitCurrentInput = async () => {
    const currentInput = input.trim();
    if (!currentInput) {
      return;
    }
    if (workspaceTab === "workbench" && isMeetingMinutesStartCommand(currentInput)) {
      await startMeetingMinutes(currentInput);
      return;
    }
    setSkillPickerOpen(false);
    setQuickCommandMenuOpen(false);
    await submitQuestion(currentInput);
  };

  const openQuickCommandMenu = () => {
    setSkillPickerOpen(false);
    if (!quickCommandsByAgent[activeWorkbenchAgentId] && !activeQuickCommandsLoading) {
      void loadQuickCommands(activeWorkbenchAgentId);
    }
    setQuickCommandMenuOpen((open) => !open);
    requestAnimationFrame(() => composerInputRef.current?.focus());
  };

  const openQuickCommandDialog = () => {
    setQuickCommandMenuOpen(false);
    setQuickCommandDialogOpen(true);
  };

  const closeQuickCommandDialog = () => {
    if (quickCommandSaving) {
      return;
    }
    setQuickCommandDialogOpen(false);
  };

  const handleComposerInputChange = (value: string) => {
    setInput(value);
    if (workspaceTab !== "workbench" || value.trim() !== "/") {
      return;
    }
    if ((!activeWorkbenchSkillBindingsLoaded || activeWorkbenchSkillLoadFailed) && !activeWorkbenchSkillLoading) {
      void loadAgentSkillBindings(activeWorkbenchAgentId);
    }
    setQuickCommandMenuOpen(false);
    setSkillPickerOpen(true);
  };

  const handleComposerFileSelection = (files: FileList | null) => {
    if (!files || files.length === 0) {
      return;
    }
    const names = Array.from(files).map((file) => file.name).join("、");
    setSpeechNotice(`已选择 ${names}，当前对话附件上传接口尚未接入发送流程。`);
    setSkillPickerOpen(false);
    setQuickCommandMenuOpen(false);
  };

  const ask = async (event: FormEvent) => {
    event.preventDefault();
    await submitCurrentInput();
  };

  const handleComposerTextareaKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (
      event.key !== "Enter" ||
      event.shiftKey ||
      event.altKey ||
      event.ctrlKey ||
      event.metaKey ||
      event.nativeEvent.isComposing
    ) {
      return;
    }
    event.preventDefault();
    void submitCurrentInput();
  };

  const startSpeechInput = async () => {
    if (!auth) {
      setSpeechNotice("请先登录后再使用语音输入。");
      return;
    }
    if (chatLoading) {
      setSpeechNotice("请等待当前回答结束后再开始语音。");
      return;
    }
    if (!speechSupported) {
      setSpeechNotice("当前浏览器不支持录音。");
      return;
    }
    const prefixBeforeSpeech = input;
    await startAsrSession({
      token: auth.token,
      getPrefix: () => prefixBeforeSpeech,
      onLiveText: (full) => {
        setInput(full);
      },
      onNotice: setSpeechNotice,
      onFinished: async ({ asrText, fullText }) => {
        if (asrText) {
          setInput(fullText);
          setSpeechNotice("实时转写完成，内容已生成到输入框。");
        } else {
          setSpeechNotice("未识别到有效语音内容。");
        }
        window.setTimeout(() => composerInputRef.current?.focus(), 0);
      },
      autoStopAfterNoSpeechMs: 5000,
    });
  };

  const stopSpeechInput = () => {
    stopAsrSession();
  };

  const logout = () => {
    abortAsrSession();
    persistAuth(null);
    setMe(null);
    setKbs([]);
    setSelectedKbIds([]);
    setApprovalDrawerOpen(false);
    setApprovalPageHtml(null);
    setSpeechNotice("");
    setMeetingDrawerOpen(false);
    setMeetingStatus("idle");
    setMeetingNotice("");
    setMeetingTranscript([]);
    setMeetingPartial(null);
    setMeetingSummary("");
    setMeetingSpeakerNames({});
    setMeetingSpeakerEdit(null);
    meetingTranscriptRef.current = [];
    meetingSpeakerNamesRef.current = {};
    meetingShouldSummarizeRef.current = false;
    setConversationThreads([]);
    setConversationMessages({});
    setConversationListNotice("");
    setQuickCommandMenuOpen(false);
    setQuickCommandDialogOpen(false);
    setQuickCommandsByAgent({});
    setQuickCommandsLoadingByAgent({});
    setQuickCommandDraft({ title: "", promptText: "" });
    setAgentSkillBindingsByAgent({});
    setAgentSkillBindingsLoadingByAgent({});
    setAgentSkillBindingsFailedByAgent({});
    setActiveSkillCodeByAgent({});
    setAgentWorkspaces(AGENT_WORKSPACES);
    setWorkbenchDockAgents(WORKBENCH_DOCK_AGENTS);
    setWorkbenchMessagesByAgent(createInitialWorkbenchMessages());
    setWorkbenchRuntimeByAgent(createInitialWorkbenchRuntime());
    setWorkbenchMetrics(WORKBENCH_METRICS_DEFAULT);
    setWorkbenchOverviewItems([]);
    setActiveAgentId(AGENT_WORKSPACES[0].id);
    setActiveWorkbenchKey(WORKBENCH_DOCK_AGENTS[0].key);
    setActiveConversationId("");
    setWorkspaceTab("workbench");
    setLoginPassword("");
    setOrganizationName("");
    setPendingOrganizations([]);
    setOrganizations([]);
    setOrganizationMenuOpen(false);
    setRegisterMode(false);
    setNotice("已退出。");
  };

  useEffect(() => {
    const element = chatStreamRef.current;
    if (element) {
      element.scrollTop = element.scrollHeight;
    }
  }, [activeConversationId, chatLoading, visibleMessages, workspaceTab]);

  useEffect(() => {
    const element = meetingTranscriptScrollRef.current;
    if ((!meetingDrawerOpen && workspaceTab !== "aiApps") || !element) {
      return;
    }
    const frame = window.requestAnimationFrame(() => {
      element.scrollTop = element.scrollHeight;
    });
    return () => window.cancelAnimationFrame(frame);
  }, [meetingDrawerOpen, meetingPartial?.text, meetingStatus, meetingTranscript, workspaceTab]);

  useEffect(() => {
    if (!meetingSpeakerEdit) {
      return;
    }
    const input = meetingSpeakerEditInputRef.current;
    input?.focus();
    input?.select();
  }, [meetingSpeakerEdit?.speakerId, meetingSpeakerEdit?.lineId]);

  useEffect(() => {
    meetingSpeakerNamesRef.current = meetingSpeakerNames;
  }, [meetingSpeakerNames]);

  const systemAgents = agentWorkspaces.filter((item) => item.category === "system");
  const publishedAgents = agentWorkspaces.filter((item) => item.category === "published");
  const activeAiApplication = AI_APPLICATIONS.find((item) => item.code === activeAiAppCode) ?? AI_APPLICATIONS[0];
  const aiMeetingCanStart =
    meetingStatus === "idle" ||
    meetingStatus === "done" ||
    (meetingStatus === "error" && meetingTranscript.length === 0 && !meetingPartial);
  const aiMeetingPrimaryLabel = meetingStatus === "recording"
    ? "结束并生成纪要"
    : aiMeetingCanStart
      ? meetingStatus === "done" ? "开始新听记" : "开始听记"
      : "生成纪要";
  const aiMeetingPrimaryDisabled =
    meetingStatus === "stopping" ||
    meetingStatus === "summarizing" ||
    ((listening || !speechSupported) && aiMeetingCanStart);
  const aiMeetingShowHeroAction = aiMeetingCanStart;
  const aiMeetingShowPanelPrimary = !aiMeetingCanStart;
  const handleAiMeetingPrimaryAction = () => {
    if (aiMeetingCanStart) {
      void startMeetingMinutes("开始会议纪要", "aiApps");
      return;
    }
    stopMeetingAndSummarize();
  };
  const agentLoginForm = (
    <>
      <div className="boot-login__form">
        <div className="boot-login__field">
          <label htmlFor="boot-mobile">电子邮件地址或手机号码</label>
          <input
            id="boot-mobile"
            className="boot-login__input"
            value={mobile}
            onChange={(event) => setMobile(event.target.value)}
            inputMode="email"
            autoComplete="username"
            placeholder="电子邮件地址或手机号码"
          />
        </div>
        <div className="boot-login__field">
          <label htmlFor="boot-password">密码</label>
          <input
            id="boot-password"
            className="boot-login__input"
            type="password"
            value={loginPassword}
            onChange={(event) => setLoginPassword(event.target.value)}
            autoComplete="off"
          />
        </div>
        {registerMode ? (
          <div className="boot-login__field">
            <label htmlFor="boot-organization-name">组织名称</label>
            <input
              id="boot-organization-name"
              className="boot-login__input"
              value={organizationName}
              onChange={(event) => setOrganizationName(event.target.value)}
              autoComplete="organization"
              placeholder="如 销售运营团队"
            />
          </div>
        ) : null}
      </div>
      {!registerMode && pendingOrganizations.length > 0 ? (
        <div className="boot-login__org-choice" role="group" aria-label="选择组织">
          <p>选择要进入的组织</p>
          {pendingOrganizations.map((item) => (
            <button
              key={item.orgId}
              type="button"
              className="boot-login__org-option"
              onClick={() => loginToOrganization(item.orgId)}
              disabled={loginSubmitting}
            >
              <span>{item.orgName}</span>
              <small>{item.roleCode}</small>
            </button>
          ))}
        </div>
      ) : null}
      {pendingOrganizations.length === 0 ? (
        <div className="boot-login__actions boot-login__actions--single">
          <button
            type="button"
            className="boot-login__btn boot-login__btn--primary"
            onClick={registerMode ? register : login}
            disabled={!mobile.trim() || !loginPassword.trim() || loginSubmitting || (registerMode && !organizationName.trim())}
          >
            <span className="boot-phone-icon" aria-hidden>
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none">
                <path d="M4 12h12" stroke="white" strokeWidth="2.4" strokeLinecap="round" />
                <path d="M12 5l8 7-8 7" stroke="white" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
            </span>
            <span className="sr-only">开始对话</span>
          </button>
        </div>
      ) : null}
      {notice ? <p className="boot-login__notice">{notice}</p> : null}
      <p className="boot-login__footer-link">
        还没有账户？<a href="https://agentcici.com/#demo" className="boot-login__link">立即预约</a>
      </p>
    </>
  );

  if (!auth) {
    if (FRONT_LOGIN_USER_MODE_CONFIG === "human") {
      return <HumanModeStaticLogin />;
    }

    if (FRONT_LOGIN_MODE_CONFIG === "login_mode2") {
      return (
        <AgentLoginMode2
          form={agentLoginForm}
          cubePhase={loginMode2CubePhase}
          entering={loginMode2Entering}
        />
      );
    }

    return (
      <main className="boot-login boot-login--cyber boot-login--fusion">
        <div className="boot-login__layers" aria-hidden>
          <BootLoginDataStream />
          <div className="boot-login__grid-floor boot-login__grid-floor--full" />
          <div className="boot-login__scanline boot-login__scanline--full" />
        </div>
        <div className="boot-login__shell">
          <section className="boot-login__panel boot-login__glass">
            <header className="boot-login__glass-copy">
              <div className="boot-login__brand-tag">CloudCC</div>
              <h1 className="boot-login__title">思思虚拟数字员工</h1>
              <p className="boot-login__tagline">
                7x24 小时在线协作，接入企业知识库、记忆系统、自定义 Skill、工作流与工具能力，持续处理问答、检索、审批推进与日常协作。
              </p>
              <BootLoginConversationDemo />
            </header>
            <div className="boot-login__glass-form-wrap">
              <div className="boot-panel-avatar" aria-hidden>
                <div className="boot-circle-bloom" />
                <div className="boot-circle-wrap boot-circle-wrap--panel">
                  <img className="boot-circle-graphic" src="/cici-circle-graphic.png" alt="" decoding="async" />
                </div>
              </div>
              {agentLoginForm}
            </div>
          </section>
        </div>
      </main>
    );
  }

  return (
    <div className="cici-app cici-app--hierarchy">
      <nav className="cici-rail">
        <div className="cici-rail__top">
          <button
            type="button"
            className={`cici-rail__avatar cici-rail__avatar--button${workspaceTab === "profile" ? " is-active" : ""}`}
            onClick={() => setWorkspaceTab("profile")}
            aria-label="个人简档"
          >
            <AvatarView
              src={me?.avatarBase64}
              fallback={userInitial}
              className="cici-rail__avatar-content"
              alt="当前用户头像"
            />
          </button>
        </div>
        <div className="cici-rail__nav">
          <button
            className={`cici-rail__nav-item cici-rail__menu-btn${workspaceTab === "workbench" ? " is-active" : ""}`}
            onClick={() => setWorkspaceTab("workbench")}
            data-menu-label="会话工作台"
            aria-label="会话工作台"
          >
            <svg viewBox="0 0 24 24">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
            </svg>
          </button>
          <button
            className={`cici-rail__nav-item cici-rail__menu-btn${workspaceTab === "customers" ? " is-active" : ""}`}
            onClick={() => setWorkspaceTab("customers")}
            data-menu-label="客户会话"
            aria-label="客户会话"
          >
            <svg viewBox="0 0 24 24">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
              <circle cx="9" cy="7" r="4" />
              <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
              <path d="M16 3.13a4 4 0 0 1 0 7.75" />
            </svg>
          </button>
          <button
            className={`cici-rail__nav-item cici-rail__menu-btn${workspaceTab === "aiApps" ? " is-active" : ""}`}
            onClick={() => setWorkspaceTab("aiApps")}
            data-menu-label="AI应用"
            aria-label="AI应用"
          >
            <svg viewBox="0 0 24 24">
              <rect x="4" y="4" width="6" height="6" rx="1.5" />
              <rect x="14" y="4" width="6" height="6" rx="1.5" />
              <rect x="4" y="14" width="6" height="6" rx="1.5" />
              <path d="M15 17h4" />
              <path d="M17 15v4" />
            </svg>
          </button>
          <button
            className={`cici-rail__nav-item cici-rail__menu-btn${workspaceTab === "crm" ? " is-active" : ""}`}
            onClick={() => setWorkspaceTab("crm")}
            data-menu-label="CRM 系统"
            aria-label="CRM 系统"
          >
            <svg viewBox="0 0 24 24">
              <rect x="3" y="4" width="18" height="16" rx="3" />
              <path d="M3 10h18" />
              <text x="12" y="17" textAnchor="middle" fontSize="6" fill="currentColor" stroke="none">CRM</text>
            </svg>
          </button>
        </div>
        <div className="cici-rail__bottom">
          <button
            type="button"
            className={`cici-rail__nav-item cici-rail__menu-btn${workspaceTab === "settings" ? " is-active" : ""}`}
            onClick={() => setWorkspaceTab("settings")}
            data-menu-label="设置"
            aria-label="设置"
          >
            <svg viewBox="0 0 24 24">
              <circle cx="12" cy="12" r="3" />
              <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
            </svg>
          </button>
          <button
            className="cici-rail__logout-btn cici-rail__menu-btn"
            onClick={logout}
            data-menu-label="退出登录"
            aria-label="退出登录"
          >
            <svg viewBox="0 0 24 24">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
              <polyline points="16 17 21 12 16 7" />
              <line x1="21" y1="12" x2="9" y2="12" />
            </svg>
          </button>
          <button
            type="button"
            className={`cici-rail__logo${organizationMenuOpen ? " is-active" : ""}`}
            onMouseEnter={openOrganizationMenu}
            onMouseLeave={scheduleOrganizationMenuClose}
            onFocus={openOrganizationMenu}
            onClick={() => {
              cancelOrganizationMenuClose();
              setOrganizationMenuOpen((open) => !open);
            }}
            aria-label={`切换组织，当前组织：${currentOrgName}`}
            aria-expanded={organizationMenuOpen}
          >
            <div className="cici-rail__logo-icon">CB</div>
          </button>
          <AppVersionBadge compact />
        </div>
      </nav>
      {organizationMenuOpen && auth?.token ? (
        <div
          className="cici-org-menu"
          ref={organizationMenuRef}
          role="dialog"
          aria-label="组织切换"
          onMouseEnter={openOrganizationMenu}
          onMouseLeave={scheduleOrganizationMenuClose}
        >
          <div className="cici-org-menu__head">
            <span>切换组织</span>
          </div>
          <div className="cici-org-menu__list">
            {organizationOptions.map((item) => {
              const isCurrent = item.orgId === auth.orgId || item.current;
              return (
                <button
                  key={item.orgId}
                  type="button"
                  className={`cici-org-menu__item${isCurrent ? " is-current" : ""}`}
                  onClick={() => switchOrganization(item.orgId)}
                  aria-current={isCurrent ? "true" : undefined}
                >
                  <span>{item.orgName}</span>
                  {isCurrent ? <small>当前</small> : null}
                </button>
              );
            })}
          </div>
        </div>
      ) : null}
      {quickCommandDialogOpen ? (
        <div
          className="cici-quick-command-dialog-backdrop"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) {
              closeQuickCommandDialog();
            }
          }}
        >
          <section
            className="cici-quick-command-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="quick-command-dialog-title"
          >
            <header className="cici-quick-command-dialog__header">
              <div>
                <h3 id="quick-command-dialog-title">添加快捷指令</h3>
                <p>{activeWorkbenchAgent.name} · 仅当前用户可见</p>
              </div>
              <button
                type="button"
                className="cici-quick-command-dialog__close"
                onClick={closeQuickCommandDialog}
                aria-label="关闭"
              >
                ×
              </button>
            </header>
            <form
              className="cici-quick-command-dialog__body"
              onSubmit={(event) => {
                event.preventDefault();
                void saveQuickCommand();
              }}
            >
              <label className="cici-quick-command-dialog__field">
                <span>名称</span>
                <input
                  value={quickCommandDraft.title}
                  onChange={(event) => setQuickCommandDraft((prev) => ({ ...prev, title: event.target.value }))}
                  placeholder="名称，可留空"
                  maxLength={80}
                />
              </label>
              <label className="cici-quick-command-dialog__field">
                <span>指令内容</span>
                <textarea
                  value={quickCommandDraft.promptText}
                  onChange={(event) => setQuickCommandDraft((prev) => ({ ...prev, promptText: event.target.value }))}
                  placeholder="输入自定义快捷指令"
                  maxLength={2000}
                  rows={6}
                  autoFocus
                />
              </label>
              <footer className="cici-quick-command-dialog__footer">
                <button
                  type="button"
                  className="cici-quick-command-dialog__btn cici-quick-command-dialog__btn--secondary"
                  onClick={closeQuickCommandDialog}
                  disabled={quickCommandSaving}
                >
                  取消
                </button>
                <button
                  type="submit"
                  className="cici-quick-command-dialog__btn cici-quick-command-dialog__btn--primary"
                  disabled={quickCommandSaving || !quickCommandDraft.promptText.trim()}
                >
                  {quickCommandSaving ? "保存中…" : "添加快捷指令"}
                </button>
              </footer>
            </form>
          </section>
        </div>
      ) : null}

      {workspaceTab === "workbench" ? (
        <main className="cici-workbench">
          <div className="cici-workbench__canvas">
            <div className="cici-workbench__layout">
              <section className="cici-workbench__main">
                <WorkbenchAgentBar
                  agents={workbenchDockAgents}
                  activeKey={activeWorkbenchKey}
                  onSelect={setActiveWorkbenchKey}
                />

                <section className="cici-workbench__chat-panel">
                  <div className="cici-workbench__chat-thread" ref={chatStreamRef}>
                    {workbenchConversation.map((message, index) => {
                      const isUser = message.role === "user";
                      return (
                        <div key={`workbench-${index}`} className={`cici-workbench__message${isUser ? " is-user" : ""}`}>
                          {!isUser ? (
                            <AvatarView
                              src={activeWorkbenchAgent.avatarBase64}
                              fallback={activeWorkbenchAgent.short}
                              className="cici-workbench__message-avatar"
                              style={{ background: activeWorkbenchAgent.color }}
                              alt={`${activeWorkbenchAgent.name} 消息头像`}
                            />
                          ) : null}
                          <div className="cici-workbench__message-body">
                            <div className="cici-workbench__message-meta">
                              {isUser ? "你" : activeWorkbenchAgent.name}
                              {message.time ? ` · ${message.time}` : ""}
                              {!isUser && message.modelName ? (
                                <span className="cici-message-model">{message.modelName}</span>
                              ) : null}
                            </div>
                            <div className={`cici-workbench__bubble${isUser ? " is-user" : ""}`}>
                              {isUser ? (
                                message.content
                              ) : (
                                <ChatMarkdown content={message.content} busy={chatLoading && index === workbenchConversation.length - 1} />
                              )}
                            </div>
                          </div>
                          {isUser ? (
                            <AvatarView
                              src={me?.avatarBase64}
                              fallback={userInitial}
                              className="cici-workbench__message-avatar cici-workbench__message-avatar--user"
                              alt="当前用户头像"
                            />
                          ) : null}
                        </div>
                      );
                    })}
                  </div>

                  <form className="cici-workbench__composer" onSubmit={ask}>
                    <div className="cici-workbench__composer-shell">
                      <textarea
                        ref={attachComposerTextareaRef}
                        value={input}
                        onChange={(event) => handleComposerInputChange(event.target.value)}
                        onKeyDown={handleComposerTextareaKeyDown}
                        placeholder="发消息或输入“/”选择技能"
                        disabled={chatLoading}
                      />
                      <div className="cici-workbench__composer-footer">
                        <div className="cici-workbench__composer-tools">
                          <input
                            ref={uploadInputRef}
                            className="cici-composer-upload-input"
                            type="file"
                            multiple
                            accept="image/*,.pdf,.doc,.docx,.xls,.xlsx,.csv,.txt,.md"
                            onChange={(event) => handleComposerFileSelection(event.target.files)}
                          />
                          <button
                            type="button"
                            className="cici-composer-tool cici-composer-tool--icon"
                            onClick={() => uploadInputRef.current?.click()}
                            title="上传文件或图片"
                            aria-label="上传文件或图片"
                          >
                            <svg viewBox="0 0 24 24">
                              <path d="M21.4 11.6 12 21a6 6 0 0 1-8.5-8.5l9.8-9.8a4 4 0 0 1 5.7 5.7l-9.9 9.9a2 2 0 0 1-2.8-2.8l8.8-8.8" />
                            </svg>
                          </button>
                          <div className="cici-composer-quick" ref={quickCommandMenuRef}>
                            <button
                              type="button"
                              className={`cici-composer-tool${quickCommandMenuOpen ? " is-active" : ""}`}
                              onClick={openQuickCommandMenu}
                              aria-haspopup="menu"
                              aria-expanded={quickCommandMenuOpen}
                            >
                              <svg viewBox="0 0 24 24">
                                <path d="M12 3c2.8 0 5.1 4 5.1 9S14.8 21 12 21 6.9 17 6.9 12 9.2 3 12 3Z" />
                                <path d="M3 12c0-2.8 4-5.1 9-5.1s9 2.3 9 5.1-4 5.1-9 5.1-9-2.3-9-5.1Z" />
                              </svg>
                              快捷指令
                            </button>
                            {quickCommandMenuOpen ? (
                              <div className="cici-composer-quick__menu" role="menu" aria-label="快捷指令">
                                {activeQuickCommandsLoading ? (
                                  <div className="cici-composer-quick__empty">正在加载快捷指令…</div>
                                ) : null}
                                {!activeQuickCommandsLoading && activeQuickCommands.length > 0 ? (
                                  <div className="cici-composer-quick__list">
                                    {activeQuickCommands.map((command) => (
                                      <button
                                        type="button"
                                        key={command.id}
                                        className="cici-composer-quick__item"
                                        role="menuitem"
                                        onClick={() => {
                                          setInput(command.promptText);
                                          setQuickCommandMenuOpen(false);
                                          requestAnimationFrame(() => composerInputRef.current?.focus());
                                        }}
                                      >
                                        <span className="cici-composer-quick__item-icon" aria-hidden="true">
                                          <svg viewBox="0 0 24 24">
                                            <path d="M5 5h14M5 12h10M5 19h7" />
                                          </svg>
                                        </span>
                                        <span className="cici-composer-quick__item-text">
                                          <strong>{command.title}</strong>
                                          <span>{command.promptText}</span>
                                        </span>
                                      </button>
                                    ))}
                                  </div>
                                ) : null}
                                {!activeQuickCommandsLoading && activeQuickCommands.length === 0 ? (
                                  <div className="cici-composer-quick__empty">当前智能体还没有快捷指令。</div>
                                ) : null}
                                <button
                                  type="button"
                                  className="cici-composer-quick__add"
                                  role="menuitem"
                                  onClick={openQuickCommandDialog}
                                >
                                  添加快捷指令
                                </button>
                              </div>
                            ) : null}
                          </div>
                          <div className="cici-composer-skill" ref={skillPickerRef}>
                            <button
                              type="button"
                              className={`cici-composer-tool${skillPickerOpen ? " is-active" : ""}${activeWorkbenchSkill ? " has-selection" : ""}`}
                              onClick={() => {
                                setQuickCommandMenuOpen(false);
                                if ((!activeWorkbenchSkillBindingsLoaded || activeWorkbenchSkillLoadFailed) && !activeWorkbenchSkillLoading) {
                                  void loadAgentSkillBindings(activeWorkbenchAgentId);
                                }
                                setSkillPickerOpen((open) => !open);
                              }}
                              aria-haspopup="listbox"
                              aria-expanded={skillPickerOpen}
                            >
                              <svg viewBox="0 0 24 24">
                                <rect x="4" y="4" width="6" height="6" rx="1.5" />
                                <rect x="4" y="14" width="6" height="6" rx="1.5" />
                                <rect x="14" y="14" width="6" height="6" rx="1.5" />
                                <path d="M17 3v6M14 6h6" />
                              </svg>
                              {activeWorkbenchSkill ? activeWorkbenchSkill.skillName : "技能"}
                            </button>
                            {skillPickerOpen ? (
                              <div className="cici-composer-skill__menu" role="listbox" aria-label="选择技能">
                                {activeWorkbenchSkillLoading ? (
                                  <div className="cici-composer-skill__empty">正在加载技能…</div>
                                ) : null}
                                {!activeWorkbenchSkillLoading && activeWorkbenchSkillLoadFailed ? (
                                  <div className="cici-composer-skill__empty">技能加载失败，请再次点击重试。</div>
                                ) : null}
                                {!activeWorkbenchSkillLoading && !activeWorkbenchSkillLoadFailed && activeWorkbenchSkillBindingsLoaded && activeWorkbenchSkillBindings.length === 0 ? (
                                  <div className="cici-composer-skill__empty">当前智能体暂无绑定技能。</div>
                                ) : null}
                                {activeWorkbenchSkillBindings.map((binding) => {
                                  const selected = binding.skillCode === activeWorkbenchSkillCode;
                                  return (
                                    <button
                                      type="button"
                                      key={binding.skillCode}
                                      className={`cici-composer-skill__item${selected ? " is-selected" : ""}`}
                                      role="option"
                                      aria-selected={selected}
                                      onClick={() => {
                                        setActiveSkillCodeByAgent((prev) => {
                                          const next = { ...prev };
                                          if (selected) {
                                            delete next[activeWorkbenchAgentId];
                                          } else {
                                            next[activeWorkbenchAgentId] = binding.skillCode;
                                          }
                                          return next;
                                        });
                                        if (input.trim() === "/") {
                                          setInput("");
                                        }
                                        setSkillPickerOpen(false);
                                      }}
                                    >
                                      <span className="cici-composer-skill__item-icon" aria-hidden="true">
                                        <svg viewBox="0 0 24 24">
                                          <circle cx="12" cy="12" r="7" />
                                          <circle cx="12" cy="12" r="2.5" />
                                        </svg>
                                      </span>
                                      <span className="cici-composer-skill__item-text">
                                        <strong>{binding.skillName}</strong>
                                      </span>
                                    </button>
                                  );
                                })}
                              </div>
                            ) : null}
                          </div>
                        </div>
                        <div className="cici-workbench__composer-actions">
                          <button
                            type="button"
                            className={`cici-composer__mic${listening ? " cici-composer__mic--on" : ""}`}
                            onClick={() => (listening ? stopSpeechInput() : startSpeechInput())}
                            disabled={!speechSupported}
                            title={listening ? "结束语音并生成文字" : "开始语音输入"}
                          >
                            <svg viewBox="0 0 24 24">
                              <rect x="9" y="3" width="6" height="12" rx="3" />
                              <path d="M5 11a7 7 0 0 0 14 0M12 18v3M9 21h6" />
                            </svg>
                          </button>
                          <button type="submit" disabled={chatLoading} className="cici-workbench__send-btn">
                            <svg viewBox="0 0 24 24">
                              <line x1="12" y1="19" x2="12" y2="5" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" />
                              <polyline points="5 12 12 5 19 12" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" fill="none" />
                            </svg>
                          </button>
                        </div>
                      </div>
                    </div>
                  </form>
                  {speechNotice ? <p className="cici-speech-notice">{speechNotice}</p> : null}
                </section>
              </section>

              <aside className="cici-workbench__sidebar">
                <section className="cici-workbench__sidebar-card cici-workbench__sidebar-card--top">
                  <WorkbenchStateCard
                    agent={activeWorkbenchAgent}
                    state={activeWorkbenchState}
                    busy={activeWorkbenchBusy}
                  />
                </section>

                <section className="cici-workbench__sidebar-card cici-workbench__sidebar-card--bottom">
                  <div className="cici-workbench__sidebar-block">
                    <h3 className="cici-workbench__sidebar-title">今日工作概览</h3>
                    <div className="cici-workbench__metrics">
                      {workbenchMetrics.map((metric) => (
                        <div key={metric.label} className="cici-workbench__metric">
                          <span>{metric.label}</span>
                          <strong>{metric.value}</strong>
                        </div>
                      ))}
                    </div>
                  </div>

                  <div className="cici-workbench__sidebar-block cici-workbench__sidebar-block--history">
                    <div className="cici-workbench__history-header">
                      <h3 className="cici-workbench__sidebar-title">会话历史</h3>
                      <button type="button" className="cici-workbench__new-session-btn" onClick={startNewWorkbenchConversation}>
                        新对话
                      </button>
                    </div>
                    <div className="cici-workbench__history-list">
                      {workbenchSessionThreads.map((session) => (
                        <div
                          key={session.id}
                          className={`cici-workbench__session-row${activeWorkbenchSessionId === session.id ? " is-active" : ""}`}
                          onMouseLeave={() => {
                            setOpenWorkbenchSessionMenuId((current) => (current === session.id ? "" : current));
                          }}
                        >
                          <button type="button" className="cici-workbench__session-main" onClick={() => void selectWorkbenchConversation(session.id)}>
                            <strong>{session.title || session.participantName || "未命名会话"}</strong>
                          </button>
                          <div className="cici-workbench__session-actions">
                            <button
                              type="button"
                              className="cici-workbench__session-more-btn"
                              title="更多操作"
                              aria-label="更多操作"
                              onClick={(event) => {
                                event.stopPropagation();
                                setOpenWorkbenchSessionMenuId((current) => (current === session.id ? "" : session.id));
                              }}
                            >
                              <span aria-hidden>⋯</span>
                            </button>
                            {openWorkbenchSessionMenuId === session.id ? (
                              <div className="cici-workbench__session-menu">
                                <button
                                  type="button"
                                  className="cici-workbench__session-menu-btn"
                                  title="下载 Markdown"
                                  aria-label="下载 Markdown"
                                  onClick={() => {
                                    void downloadWorkbenchConversation(session);
                                    setOpenWorkbenchSessionMenuId("");
                                  }}
                                >
                                  <svg className="cici-workbench__session-menu-icon" viewBox="0 0 24 24" aria-hidden>
                                    <path d="M12 4v9" />
                                    <path d="m8.5 10.5 3.5 3.5 3.5-3.5" />
                                    <path d="M5 19h14" />
                                  </svg>
                                </button>
                                <button
                                  type="button"
                                  className="cici-workbench__session-menu-btn is-danger"
                                  title="删除会话"
                                  aria-label="删除会话"
                                  onClick={() => {
                                    void deleteWorkbenchConversation(session);
                                    setOpenWorkbenchSessionMenuId("");
                                  }}
                                >
                                  <span className="cici-workbench__session-menu-glyph" aria-hidden>🗑</span>
                                </button>
                              </div>
                            ) : null}
                          </div>
                        </div>
                      ))}
                      {workbenchSessionThreads.length === 0 ? (
                        <div className="cici-workbench__history-empty">暂无会话，点击“新对话”开始。</div>
                      ) : null}
                    </div>
                  </div>
                </section>
              </aside>
            </div>
            <aside
              className={`cici-meeting-drawer${meetingDrawerOpen ? " is-open" : ""}`}
              aria-label="实时会议纪要"
            >
              <MeetingMinutesPanel
                status={meetingStatus}
                notice={meetingNotice || "输入“开始会议纪要”后自动开始。"}
                transcript={meetingTranscript}
                partial={meetingPartial}
                summary={meetingSummary}
                speakerEdit={meetingSpeakerEdit}
                transcriptScrollRef={meetingTranscriptScrollRef}
                speakerEditInputRef={meetingSpeakerEditInputRef}
                onClose={closeMeetingDrawer}
                onSecondaryAction={closeMeetingDrawer}
                onPrimaryAction={stopMeetingAndSummarize}
                primaryActionLabel={meetingStatus === "recording" ? "结束并生成纪要" : "生成纪要"}
                primaryActionDisabled={meetingStatus === "idle" || meetingStatus === "stopping" || meetingStatus === "summarizing" || meetingStatus === "done"}
                onSpeakerEditStart={startMeetingSpeakerEdit}
                onSpeakerEditValueChange={(value) => setMeetingSpeakerEdit((prev) => (prev ? { ...prev, value } : prev))}
                onSpeakerEditCommit={commitMeetingSpeakerEdit}
                onSpeakerEditKeyDown={handleMeetingSpeakerEditKeyDown}
              />
            </aside>
          </div>
        </main>
      ) : workspaceTab === "settings" ? (
        <MyEmailAccountsModal open={Boolean(auth?.token)} token={auth?.token ?? ""} variant="page" surface="settings" title={currentOrgName} />
      ) : workspaceTab === "profile" ? (
        <MyEmailAccountsModal open={Boolean(auth?.token)} token={auth?.token ?? ""} variant="page" surface="profile" title="个人简档" />
      ) : workspaceTab === "monitor" ? (
        <main className="cici-monitor">
          <header className="cici-monitor__topbar">
            <section>
              <p className="cici-monitor__kicker">AGENT OBSERVABILITY</p>
              <h1>智能体监控</h1>
              <p>查看每个智能体当前运行状态，并追踪最近 7 天的会话、任务、模型、工具、技能与知识库链路。</p>
            </section>
            <section className="cici-monitor__metrics" aria-label="监控指标">
              <article>
                <span>在线智能体</span>
                <strong>{monitorRows.length}</strong>
              </article>
              <article>
                <span>运行中</span>
                <strong>{monitorBusyCount}</strong>
              </article>
              <article>
                <span>异常/待确认</span>
                <strong>{monitorRows.filter((row) => row.severity === "warn").length}</strong>
              </article>
              <article>
                <span>可见会话</span>
                <strong>{monitorVisibleThreadCount || "—"}</strong>
              </article>
              <article>
                <span>真实链路</span>
                <strong>{monitorRunLogs.length || "—"}</strong>
              </article>
            </section>
          </header>

          <section className="cici-monitor__toolbar" aria-label="监控筛选">
            <button type="button" className="cici-monitor__select">近 7 天 <span aria-hidden>⌄</span></button>
            <button
              type="button"
              className="cici-monitor__select"
              onClick={() => {
                setActiveMonitorAgentKey("");
                setActiveMonitorLogId("");
              }}
            >
              {activeMonitorAgentKey ? monitorRows.find((row) => row.key === activeMonitorAgentKey)?.name ?? "全部智能体" : "全部智能体"} <span aria-hidden>⌄</span>
            </button>
            <label className="cici-monitor__search">
              <span className="cici-monitor__search-icon" aria-hidden />
              <input
                type="text"
                value={monitorSearchText}
                onChange={(event) => setMonitorSearchText(event.target.value)}
                placeholder="搜索执行记录或摘要"
                aria-label="搜索监控日志"
              />
            </label>
            <button
              type="button"
              className="cici-monitor__refresh"
              onClick={() => {
                if (!auth?.token) {
                  return;
                }
                void loadWorkbenchAgents(auth.token);
                void loadWorkbenchStats(auth.token);
                void loadMonitorRunLogs(auth.token);
              }}
            >
              刷新状态
            </button>
          </section>

          <section className="cici-monitor__workspace">
            <aside className="cici-monitor-panel cici-monitor-panel--agents">
              <header className="cici-monitor-panel__head">
                <h2>智能体状态</h2>
                <span>实时</span>
              </header>
              <div className="cici-monitor-agent-list">
                {monitorRows.map((row, index) => (
                  <button
                    key={row.key}
                    type="button"
                    className={`cici-monitor-agent${monitorActiveAgentKey === row.key ? " is-active" : ""}`}
                    onClick={() => {
                      setActiveMonitorAgentKey(row.key);
                      setActiveMonitorLogId("");
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
                      <span>{row.currentTask}</span>
                    </span>
                    <span className={`cici-monitor-status ${monitorStatusClass(row.severity)}`}>{row.status}</span>
                  </button>
                ))}
                {monitorRows.length === 0 ? (
                  <div className="cici-monitor__empty">当前没有可监控的智能体运行数据。</div>
                ) : null}
              </div>
            </aside>

            <section className="cici-monitor-panel cici-monitor-panel--logs">
              <header className="cici-monitor-panel__head">
                <h2>最近 7 天运行日志</h2>
                <span>{monitorLogsLoading ? "加载中" : `${monitorFilteredLogs.length} 条记录`}</span>
              </header>
              <nav className="cici-monitor-tabs" aria-label="日志范围">
                {["全部", "运行中", "异常", "待确认"].map((tab, index) => (
                  <button key={tab} type="button" className={`cici-monitor-tab${index === 0 ? " is-active" : ""}`}>
                    {tab}
                  </button>
                ))}
              </nav>
              <div className="cici-monitor-log-list">
                {monitorFilteredLogs.map((log) => (
                  <button
                    key={log.id}
                    type="button"
                    className={`cici-monitor-log${monitorSelectedLog?.id === log.id ? " is-selected" : ""}`}
                    onClick={() => setActiveMonitorLogId(log.id)}
                  >
                    <span className="cici-monitor-log__title">
                      <strong>{log.title}</strong>
                      <span>执行记录 {log.recordId} · {log.detail}</span>
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
                {monitorFilteredLogs.length === 0 ? (
                  <div className="cici-monitor__empty">当前筛选条件下没有运行日志。</div>
                ) : null}
              </div>
            </section>

            <aside className="cici-monitor-panel cici-monitor-panel--trace">
              <header className="cici-monitor-panel__head">
                <h2>链路追踪</h2>
                <span>{monitorSelectedLog ? `执行记录 ${monitorSelectedLog.recordId}` : "未选择"}</span>
              </header>
              {monitorSelectedLog ? (
                <>
                  <section className="cici-monitor-trace-summary">
                    <div>
                      <span>智能体</span>
                      <strong>{monitorSelectedLog.agentName}</strong>
                    </div>
                    <div>
                      <span>状态</span>
                      <strong>{monitorSelectedLog.status}</strong>
                    </div>
                    <div>
                      <span>渠道</span>
                      <strong>{monitorSelectedTrace ? monitorChannelLabel(monitorSelectedTrace.channel) : "—"}</strong>
                    </div>
                    <div>
                      <span>耗时</span>
                      <strong>{monitorSelectedTrace ? formatMonitorElapsed(monitorSelectedTrace.elapsedMs) : "—"}</strong>
                    </div>
                  </section>
                  <section className={`cici-monitor-trace-steps${monitorSelectedTrace?.nodes?.length ? "" : " cici-monitor-trace-steps--empty"}`}>
                    {monitorSelectedTrace?.nodes?.length ? (
                      monitorSelectedTrace.nodes.map((node, index) => (
                        <article className="cici-monitor-trace-step" key={node.id ?? `${node.type}-${index}`}>
                          <span className="cici-monitor-trace-step__dot" aria-hidden />
                          <div>
                            <h3>
                              <span>{node.title || node.type || "链路节点"}</span>
                              <time className="cici-monitor-trace-step__started-at">{formatMonitorDateTime(node.startedAt)}</time>
                            </h3>
                            <p>{node.summary || "节点已记录。"}</p>
                          </div>
                          <div className="cici-monitor-trace-step__meta">
                            <time>{formatTraceStepElapsed(node.elapsedMs)}</time>
                            {traceStepTokenSummary(node) ? (
                              <span className="cici-monitor-trace-step__tokens">{traceStepTokenSummary(node)}</span>
                            ) : null}
                          </div>
                        </article>
                      ))
                    ) : (
                      <article className="cici-monitor-trace-step">
                        <span className="cici-monitor-trace-step__dot" aria-hidden />
                        <div>
                          <h3>
                            <span>{monitorTraceLoadingId ? "正在加载链路日志" : "暂无链路详情"}</span>
                            <time className="cici-monitor-trace-step__started-at">—</time>
                          </h3>
                          <p>{monitorTraceLoadingId ? "正在读取本次运行的模型、工具、技能和知识库明细。" : "该记录可能是历史会话回填，或后端尚未返回详情。"}</p>
                        </div>
                        <div className="cici-monitor-trace-step__meta">
                          <time>0ms</time>
                        </div>
                      </article>
                    )}
                  </section>
                  <section className="cici-monitor-detail-groups">
                    <article>
                      <h3>大模型交互</h3>
                      <p>{monitorSelectedTrace ? monitorModelTraceSummary(monitorSelectedTrace) : "正在等待链路详情。"}</p>
                    </article>
                    <article>
                      <h3>工具调用</h3>
                      <p>{monitorSelectedTrace ? monitorToolTraceSummary(monitorSelectedTrace) : "正在等待链路详情。"}</p>
                    </article>
                    <article>
                      <h3>技能与知识库</h3>
                      <p>
                        {monitorSelectedTrace
                          ? [
                              (() => {
                                const activated = compactUnknownValue(
                                  monitorSelectedTrace.skills?.activatedSkillCodes ?? monitorSelectedTrace.skills?.skillNames,
                                  "",
                                );
                                const bound = compactUnknownValue(monitorSelectedTrace.skills?.boundSkillCodes, "");
                                return activated
                                  ? `本轮激活：${activated}`
                                  : bound
                                    ? `未激活业务技能 · 候选：${bound}`
                                    : "";
                              })(),
                              compactUnknownValue((monitorSelectedTrace.rag?.knowledgeBases as unknown[] | undefined)?.map((kb) => compactUnknownValue(kb, "")), ""),
                            ].filter(Boolean).join(" · ") || "本轮未命中技能或知识库"
                          : "正在等待链路详情。"}
                      </p>
                    </article>
                    <article>
                      <h3>摘要</h3>
                      <p>{monitorSelectedTrace?.summary || monitorSelectedLog.summary || monitorSelectedLog.title}</p>
                    </article>
                  </section>
                </>
              ) : (
                <div className="cici-monitor__empty">请选择一条运行日志查看链路追踪。</div>
              )}
            </aside>
          </section>
        </main>
      ) : workspaceTab === "aiApps" ? (
        <main className="cici-ai-apps">
          <aside className="cici-ai-apps__list" aria-label="AI应用列表">
            <header className="cici-ai-apps__list-head">
              <p>AI APPS</p>
              <h1>AI应用</h1>
            </header>
            <div className="cici-ai-apps__cards">
              {AI_APPLICATIONS.map((app) => {
                const isActive = app.code === activeAiAppCode;
                return (
                  <button
                    key={app.code}
                    type="button"
                    className={`cici-ai-app-card${isActive ? " is-active" : ""}`}
                    onClick={() => setActiveAiAppCode(app.code)}
                    aria-pressed={isActive}
                  >
                    <span className="cici-ai-app-card__mark" aria-hidden>{app.shortName}</span>
                    <span className="cici-ai-app-card__body">
                      <span className="cici-ai-app-card__title">
                        <strong>{app.name}</strong>
                        <small>{app.status}</small>
                      </span>
                      <span className="cici-ai-app-card__summary">{app.summary}</span>
                    </span>
                  </button>
                );
              })}
            </div>
          </aside>

          <section className="cici-ai-apps__main" aria-label={`${activeAiApplication.name}主页面`}>
            <header className="cici-ai-apps__hero">
              <div>
                {activeAiApplication.code === "meeting-minutes" ? <p>{activeAiApplication.meta}</p> : null}
                <h2>{activeAiApplication.name}</h2>
                <span>{activeAiApplication.description}</span>
              </div>
              {activeAiApplication.code === "meeting-minutes" && aiMeetingShowHeroAction ? (
                <button
                  type="button"
                  className="cici-ai-apps__primary"
                  onClick={handleAiMeetingPrimaryAction}
                  disabled={aiMeetingPrimaryDisabled}
                >
                  {aiMeetingPrimaryLabel}
                </button>
              ) : null}
            </header>
            {activeAiApplication.code === "customer-insight" ? (
              <CustomerInsightAppPanel token={auth?.token ?? ""} />
            ) : (
              <section className="cici-ai-apps__meeting-panel">
                <MeetingMinutesPanel
                  eyebrow="BUILT-IN AI APP"
                  title="AI 听记"
                  hideHeader
                  status={meetingStatus}
                  notice={meetingNotice || "点击开始听记后自动请求麦克风权限。"}
                  transcript={meetingTranscript}
                  partial={meetingPartial}
                  summary={meetingSummary}
                  speakerEdit={meetingSpeakerEdit}
                  transcriptScrollRef={meetingTranscriptScrollRef}
                  speakerEditInputRef={meetingSpeakerEditInputRef}
                  onPrimaryAction={handleAiMeetingPrimaryAction}
                  primaryActionLabel={aiMeetingPrimaryLabel}
                  primaryActionDisabled={aiMeetingPrimaryDisabled}
                  primaryActionVisible={aiMeetingShowPanelPrimary}
                  onSpeakerEditStart={startMeetingSpeakerEdit}
                  onSpeakerEditValueChange={(value) => setMeetingSpeakerEdit((prev) => (prev ? { ...prev, value } : prev))}
                  onSpeakerEditCommit={commitMeetingSpeakerEdit}
                  onSpeakerEditKeyDown={handleMeetingSpeakerEditKeyDown}
                />
              </section>
            )}
          </section>
        </main>
      ) : workspaceTab === "crm" ? (
        <main className="cici-crm">
          <iframe className="cici-crm__frame" title="CloudCC CRM" src="https://accounts.cloudcc.cn/#/login" />
        </main>
      ) : (
        <>
          <aside className="cici-agents">
            <div className="cici-agents__header">
              <p className="cici-agents__eyebrow">Agent Workspace</p>
            </div>

            <div className="cici-agents__section">
              <div className="cici-agents__section-title">系统内置</div>
              {systemAgents.map((agent) => (
                <AgentCard
                  key={agent.id}
                  agent={agent}
                  active={agent.id === activeAgentId}
                  conversations={conversationsByAgent.get(agent.id) ?? []}
                  onSelect={() => {
                    setActiveAgentId(agent.id);
                    setActiveChannel("all");
                    setSearchText("");
                  }}
                />
              ))}
            </div>

            <div className="cici-agents__section">
              <div className="cici-agents__section-title">已发布智能体</div>
              {publishedAgents.map((agent) => (
                <AgentCard
                  key={agent.id}
                  agent={agent}
                  active={agent.id === activeAgentId}
                  conversations={conversationsByAgent.get(agent.id) ?? []}
                  onSelect={() => {
                    setActiveAgentId(agent.id);
                    setActiveChannel("all");
                    setSearchText("");
                  }}
                />
              ))}
            </div>
          </aside>

          <aside className="cici-threads">
            <div className="cici-threads__header">
              <div className="cici-threads__meta">
                <AvatarView
                  src={activeAgent.avatarBase64}
                  fallback={activeAgent.avatar}
                  className="cici-threads__agent-avatar"
                  style={{ background: activeAgent.accent }}
                  alt={`${activeAgent.name} 头像`}
                />
                <div>
                  <p className="cici-threads__eyebrow">{activeAgent.status}</p>
                  <h2>{activeAgent.name}</h2>
                  <p>{activeAgent.subtitle} · {availableThreads.length} 个会话 · {agentUnread} 条未读</p>
                </div>
              </div>
            </div>

            <div className="cici-sessions__filters cici-sessions__filters--compact">
              <button className={`cici-sessions__filter${activeChannel === "all" ? " is-active" : ""}`} onClick={() => setActiveChannel("all")}>
                全部
              </button>
              <button
                className={`cici-sessions__filter${activeChannel === "wechat" ? " is-active" : ""}`}
                onClick={() => setActiveChannel("wechat")}
              >
                企微
              </button>
              <button
                className={`cici-sessions__filter${activeChannel === "dingtalk" ? " is-active" : ""}`}
                onClick={() => setActiveChannel("dingtalk")}
              >
                钉钉
              </button>
              <button
                className={`cici-sessions__filter${activeChannel === "feishu" ? " is-active" : ""}`}
                onClick={() => setActiveChannel("feishu")}
              >
                飞书
              </button>
              <button className={`cici-sessions__filter${activeChannel === "web" ? " is-active" : ""}`} onClick={() => setActiveChannel("web")}>
                Web
              </button>
            </div>

            <div className="cici-sessions__search">
              <svg viewBox="0 0 24 24">
                <circle cx="11" cy="11" r="7" />
                <path d="M21 21l-4.35-4.35" />
              </svg>
              <input type="text" placeholder="搜索会话…" value={searchText} onChange={(event) => setSearchText(event.target.value)} />
            </div>

            {conversationListNotice ? <div className="cici-threads__empty">{conversationListNotice}</div> : null}

            <div className="cici-threads__list">
              {conversationListLoading ? <div className="cici-threads__empty">会话列表加载中…</div> : null}
              {availableThreads.map((thread) => (
                <button
                  key={thread.id}
                  type="button"
                  className={`cici-thread-item${activeConversation?.id === thread.id ? " is-active" : ""}`}
                  onClick={() => setActiveConversationId(thread.id)}
                >
                  <div className="cici-thread-item__avatar" style={{ background: getAvatarColor(thread.participantName) }}>
                    {thread.avatarUrl ? (
                      <img
                        src={thread.avatarUrl}
                        alt={thread.participantName}
                        style={{ width: "100%", height: "100%", objectFit: "cover", borderRadius: "inherit" }}
                      />
                    ) : (
                      getDisplayInitial(thread.participantName)
                    )}
                  </div>
                  <div className="cici-thread-item__body">
                    <div className="cici-thread-item__title-row">
                      <div className="cici-thread-item__title">{thread.participantName}</div>
                      <span className="cici-thread-item__channel">{CHANNEL_LABELS[thread.channel]}</span>
                    </div>
                    <div className="cici-thread-item__subtitle">
                      <span className="cici-thread-item__preview">{thread.lastMessage}</span>
                    </div>
                  </div>
                  <div className="cici-thread-item__side">
                    <span className="cici-thread-item__time">{thread.time}</span>
                    {thread.unread > 0 ? <span className="cici-session-item__badge">{thread.unread}</span> : <span className="cici-thread-item__side-placeholder" />}
                  </div>
                </button>
              ))}
              {availableThreads.length === 0 ? (
                <div className="cici-threads__empty">当前筛选条件下没有会话。切换渠道或搜索条件试试。</div>
              ) : null}
            </div>
          </aside>

          <section className="cici-chat">
            <header className="cici-chat__header cici-chat__header--hierarchy">
              <div className="cici-chat__header-left">
                <AvatarView
                  src={activeAgent.avatarBase64}
                  fallback={activeAgent.avatar}
                  className="cici-chat__header-avatar"
                  style={{ background: activeAgent.accent }}
                  alt={`${activeAgent.name} 头像`}
                />
                <div>
                  <div className="cici-chat__header-path">
                    <span>{activeAgent.name}</span>
                    <span>/</span>
                    <span>{activeConversation?.participantName ?? "未选择会话"}</span>
                  </div>
                  <h1 className="cici-chat__header-title">{activeConversation?.title ?? "请选择会话"}</h1>
                  <p className="cici-chat__header-sub">
                    {activeConversation ? `${CHANNEL_LABELS[activeConversation.channel]} · ${activeConversation.owner} 负责 · 知识库: ${activeKbNames}` : "请选择左侧会话线程"}
                  </p>
                </div>
              </div>
              <div className="cici-chat__header-actions">
                <button className="cici-chat__header-btn" title="转交售前">
                  ⇄
                </button>
                <button className="cici-chat__header-btn" title="标记高优">
                  ⚑
                </button>
                <button className="cici-chat__header-btn" title="更多">
                  ⋯
                </button>
              </div>
            </header>

            <div className="cici-chat__messages" ref={chatStreamRef}>
              {conversationHistoryLoading ? <div className="cici-threads__empty">会话消息加载中…</div> : null}
              {messages.map((message, index) => (
                <div key={`${activeConversation?.id ?? "empty"}-${index}`} className={`cici-msg${message.role === "user" ? " cici-msg--user" : ""}`}>
                  {message.role === "assistant" ? (
                    <AvatarView
                      src={activeAgent.avatarBase64}
                      fallback={activeAgent.avatar}
                      className="cici-msg__avatar"
                      alt={`${activeAgent.name} 头像`}
                    />
                  ) : null}
                  <div className={`cici-msg__bubble${message.role === "user" ? " cici-msg__bubble--user" : ""}`}>
                    {message.role === "assistant" ? (
                      <ChatMarkdown content={message.content} busy={chatLoading && index === messages.length - 1} />
                    ) : (
                      message.content
                    )}
                  </div>
                  {message.role === "user" ? (
                    <AvatarView
                      src={me?.avatarBase64}
                      fallback={userInitial}
                      className="cici-msg__avatar cici-msg__avatar--user"
                      alt="当前用户头像"
                    />
                  ) : null}
                </div>
              ))}
              {!conversationHistoryLoading && activeConversation && messages.length === 0 ? (
                <div className="cici-threads__empty">当前会话还没有可展示的历史消息。</div>
              ) : null}
            </div>

            <form className="cici-composer" onSubmit={ask}>
              <div className="cici-composer__wrapper">
                <input
                  ref={attachComposerTextInputRef}
                  value={input}
                  onChange={(event) => setInput(event.target.value)}
                  placeholder={
                    activeConversation
                      ? `向 ${activeAgent.name} 的「${activeConversation.participantName}」会话发送消息…`
                      : "请选择一个会话线程…"
                  }
                  disabled={chatLoading || !activeConversation}
                />
                <div className="cici-composer__actions">
                  <button
                    type="button"
                    className={`cici-composer__mic${listening ? " cici-composer__mic--on" : ""}`}
                    onClick={() => (listening ? stopSpeechInput() : startSpeechInput())}
                    disabled={!speechSupported || !activeConversation}
                    title={listening ? "结束语音并生成文字" : "开始语音输入"}
                  >
                    <svg viewBox="0 0 24 24">
                      <rect x="9" y="3" width="6" height="12" rx="3" />
                      <path d="M5 11a7 7 0 0 0 14 0M12 18v3M9 21h6" />
                    </svg>
                  </button>
                  <button type="submit" disabled={chatLoading || !activeConversation} className="cici-composer__send">
                    <svg viewBox="0 0 24 24">
                      <path d="M22 2L11 13" />
                      <path d="M22 2L15 22l-4-9-9-4L22 2z" />
                    </svg>
                  </button>
                </div>
              </div>
            </form>
            {speechNotice ? <p className="cici-speech-notice">{speechNotice}</p> : null}
          </section>

          <aside className="cici-right-panel cici-right-panel--hierarchy">
            <div className="cici-right-section">
              <h3 className="cici-right-section__title">智能体档案</h3>
              <div className="cici-right-card cici-right-card--agent">
                <AvatarView
                  src={activeAgent.avatarBase64}
                  fallback={activeAgent.avatar}
                  className="cici-right-card__avatar"
                  style={{ background: activeAgent.accent }}
                  alt={`${activeAgent.name} 档案头像`}
                />
                <div>
                  <div className="cici-right-card__name">{activeAgent.name}</div>
                  <div className="cici-right-card__sub">{activeAgent.subtitle}</div>
                  <div className="cici-right-card__tags">
                    <span className="cici-tag">{activeAgent.status}</span>
                    {activeAgent.pinned ? <span className="cici-tag cici-tag--gold">常驻</span> : null}
                  </div>
                </div>
                <p className="cici-right-card__text">{activeAgent.description}</p>
              </div>
            </div>

            <div className="cici-right-section">
              <h3 className="cici-right-section__title">会话对象</h3>
              <div className="cici-right-card">
                <AvatarView
                  src={activeConversation?.avatarUrl}
                  fallback={getDisplayInitial(activeConversation?.participantName ?? "会", "会")}
                  className="cici-right-card__avatar"
                  alt={`${activeConversation?.participantName ?? "会话对象"} 头像`}
                />
                <div>
                  <div className="cici-right-card__name">{activeConversation?.participantName ?? "未选择"}</div>
                  <div className="cici-right-card__sub">
                    {activeConversation ? `${CHANNEL_LABELS[activeConversation.channel]} · ${activeConversation.participantType}` : "请选择会话"}
                  </div>
                  <div className="cici-right-card__tags">
                    <span className="cici-tag">{activeConversation?.owner ?? "待分配"}</span>
                    {activeConversation?.unread ? <span className="cici-tag cici-tag--danger">{activeConversation.unread} 未读</span> : null}
                  </div>
                </div>
              </div>
            </div>

            <div className="cici-right-section">
              <h3 className="cici-right-section__title">知识库 (RAG)</h3>
              <div className="cici-right-kb">
                {kbs.map((kb) => (
                  <label key={kb.id} className="cici-right-kb__item">
                    <input type="checkbox" checked={selectedKbIds.includes(kb.id)} onChange={() => toggleKb(kb.id)} />
                    <span>{kb.name}</span>
                  </label>
                ))}
                {kbs.length === 0 ? (
                  <label className="cici-right-kb__item cici-right-kb__item--empty">
                    <span>暂无知识库</span>
                  </label>
                ) : null}
              </div>
            </div>

            <div className="cici-right-section">
              <h3 className="cici-right-section__title">结构说明</h3>
              <div className="cici-right-card cici-right-card--ai">
                <div className="cici-right-card__title">Agent → Conversation → Message</div>
                <p className="cici-right-card__text">
                  当前工作台已按“智能体是顶层对象、渠道是会话属性、消息归属会话线程”的方式组织，便于后续接入多渠道真实数据。
                </p>
              </div>
            </div>

            <div className="cici-right-section">
              <h3 className="cici-right-section__title">快捷操作</h3>
              <div className="cici-quick-actions">
                <button className="cici-quick-actions__item"><span>◻</span> 发报价单</button>
                <button className="cici-quick-actions__item"><span>⇄</span> 转交售前</button>
                <button className="cici-quick-actions__item"><span>⚑</span> 标记高优先级</button>
                <button className="cici-quick-actions__item"><span>☐</span> 创建跟进任务</button>
              </div>
            </div>
          </aside>
        </>
      )}

      <div className={`cici-approval-drawer${approvalDrawerOpen ? " is-open" : ""}`}>
        <div className="cici-approval-drawer__header">
          <div>
            <h2>审批处理页</h2>
            <p>由思思在对话中自动拉起，可继续查看并处理当前审批内容。</p>
          </div>
          <button type="button" className="cici-chat__header-btn" onClick={closeApprovalDrawer} title="关闭审批页">
            ×
          </button>
        </div>
        {approvalPageHtml ? (
          <iframe className="cici-approval-drawer__frame" title="审批处理页" srcDoc={approvalPageHtml} />
        ) : (
          <div className="cici-approval-drawer__empty">还没有可展示的审批页。让思思帮你拉取待审批内容后，这里会自动打开。</div>
        )}
      </div>
    </div>
  );
}

type AgentCardProps = {
  agent: AgentWorkspace;
  conversations: ConversationThread[];
  active: boolean;
  onSelect: () => void;
};

function AgentCard({ agent, conversations, active, onSelect }: AgentCardProps) {
  const unread = conversations.reduce((count, thread) => count + thread.unread, 0);

  return (
    <button type="button" className={`cici-agent-card${active ? " is-active" : ""}`} onClick={onSelect}>
      <div className="cici-agent-card__top">
        <AvatarView
          src={agent.avatarBase64}
          fallback={agent.avatar}
          className="cici-agent-card__avatar"
          style={{ background: agent.accent }}
          alt={`${agent.name} 头像`}
        />
        <div className="cici-agent-card__meta">
          <div className="cici-agent-card__title-row">
            <span className="cici-agent-card__title">{agent.name}</span>
            <span className="cici-agent-card__status">{agent.status}</span>
          </div>
          <div className="cici-agent-card__subtitle">{agent.subtitle}</div>
        </div>
      </div>
      <div className="cici-agent-card__footer">
        <span>{conversations.length} 个会话</span>
        <span>{unread} 条未读</span>
      </div>
    </button>
  );
}

function getAvatarColor(seed: string) {
  const hue = Array.from(seed).reduce((sum, char) => sum + char.charCodeAt(0), 0) % 360;
  return `hsl(${hue} 68% 58%)`;
}

function buildPendingApprovalsHtml(raw: string): string {
  const rows = extractRowsFromToolPayload(raw);
  const timestamp = new Date().toLocaleString();
  if (rows.length === 0) {
    return `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"/><meta name="viewport" content="width=device-width, initial-scale=1"/><title>审批记录</title><style>${reportCss()}</style></head><body><div class="wrap"><div class="hero"><h1>审批记录</h1><p>待审批数据展示</p></div><div class="empty">未识别到结构化表格数据</div><details><summary>查看原始返回</summary><pre>${escapeHtml(raw || "(空)")}</pre></details><p class="ts">生成时间：${escapeHtml(timestamp)}</p></div></body></html>`;
  }
  const defs = buildColumnDefs(rows);
  const colgroup = defs.map((column) => `<col class="${column.colClass}"/>`).join("");
  const header = defs.map((column) => `<th class="${column.headClass}">${escapeHtml(column.label)}</th>`).join("");
  const body = rows
    .slice(0, 200)
    .map((row) => `<tr>${defs.map((column) => `<td class="${column.cellClass}">${renderCellByColumn(column, row[column.key])}</td>`).join("")}</tr>`)
    .join("");
  return `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"/><meta name="viewport" content="width=device-width, initial-scale=1"/><title>审批记录</title><style>${reportCss()}</style></head><body><div class="wrap"><div class="hero"><h1>CloudCC 待审批记录</h1><p>共解析 ${rows.length} 条记录</p></div><div class="table-wrap"><table><colgroup>${colgroup}</colgroup><thead><tr>${header}</tr></thead><tbody>${body}</tbody></table></div><p class="ts">生成时间：${escapeHtml(timestamp)}</p></div></body></html>`;
}

function extractRowsFromToolPayload(raw: string): Array<Record<string, unknown>> {
  const parsed = safeParseJson(raw);
  if (parsed !== null) {
    const rows = findFirstObjectArray(parsed);
    if (rows.length > 0) {
      return rows;
    }
  }
  const relaxed = tryParseRelaxedJson(raw);
  if (relaxed !== null) {
    const rows = findFirstObjectArray(relaxed);
    if (rows.length > 0) {
      return rows;
    }
  }
  return parseMarkdownTable(raw);
}

function findFirstObjectArray(node: unknown): Array<Record<string, unknown>> {
  if (Array.isArray(node)) {
    if (node.length > 0 && node.every((item) => typeof item === "object" && item !== null && !Array.isArray(item))) {
      return node as Array<Record<string, unknown>>;
    }
    for (const item of node) {
      const rows = findFirstObjectArray(item);
      if (rows.length > 0) {
        return rows;
      }
    }
  } else if (node && typeof node === "object") {
    for (const value of Object.values(node as Record<string, unknown>)) {
      const rows = findFirstObjectArray(value);
      if (rows.length > 0) {
        return rows;
      }
    }
  }
  return [];
}

function parseMarkdownTable(raw: string): Array<Record<string, unknown>> {
  const lines = raw
    .split("\n")
    .map((item) => item.trim())
    .filter((item) => item.includes("|"));
  if (lines.length < 3) {
    return [];
  }
  const headerParts = lines[0].split("|").map((item) => item.trim()).filter(Boolean);
  if (headerParts.length === 0) {
    return [];
  }
  const rows: Array<Record<string, unknown>> = [];
  for (const line of lines.slice(2)) {
    const values = line.split("|").map((item) => item.trim()).filter(Boolean);
    if (values.length === 0) {
      continue;
    }
    const row: Record<string, unknown> = {};
    for (let index = 0; index < headerParts.length; index += 1) {
      row[headerParts[index]] = values[index] ?? "";
    }
    rows.push(row);
  }
  return rows;
}

function safeParseJson(raw: string): unknown | null {
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

function tryParseRelaxedJson(raw: string): unknown | null {
  const start = raw.indexOf("{");
  const end = raw.lastIndexOf("}");
  if (start < 0 || end <= start) {
    return null;
  }
  try {
    return JSON.parse(
      raw
        .slice(start, end + 1)
        .replaceAll("'", '"')
        .replace(/\bNone\b/g, "null")
        .replace(/\bTrue\b/g, "true")
        .replace(/\bFalse\b/g, "false")
        .replace(/,\s*([}\]])/g, "$1"),
    );
  } catch {
    return null;
  }
}

function escapeHtml(value: string): string {
  return value.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#39;");
}

function stringVal(value: unknown): string {
  if (value === null || value === undefined) {
    return "";
  }
  if (typeof value === "string") {
    return value;
  }
  if (typeof value === "number" || typeof value === "boolean") {
    return String(value);
  }
  return JSON.stringify(value);
}

function renderCellText(value: unknown): string {
  const text = stringVal(value)
    .replace(/<br\s*\/?>/gi, "\n")
    .replace(/<\/p>/gi, "\n")
    .replace(/<[^>]+>/g, " ")
    .replace(/&nbsp;/gi, " ")
    .replace(/&amp;/gi, "&")
    .replace(/&lt;/gi, "<")
    .replace(/&gt;/gi, ">")
    .replace(/&quot;/gi, '"')
    .replace(/&#39;/gi, "'");
  return escapeHtml(text.replace(/\r/g, "").replace(/[ \t]+\n/g, "\n").replace(/\n{3,}/g, "\n\n").trim());
}

function renderCellByColumn(column: ApprovalColumnDef, value: unknown): string {
  const text = renderCellText(value);
  if (column.colClass === "is-id") {
    const collapsed = escapeHtml(toMiddleEllipsis(decodeHtmlEntities(text), 18, 10));
    return `<span class="cell-id-text" title="${text}">${collapsed}</span>`;
  }
  if (column.colClass === "is-long" && decodeHtmlEntities(text).length > 160) {
    const plain = decodeHtmlEntities(text);
    const folded = escapeHtml(plain.slice(0, 160).trimEnd() + "...");
    return `<details class="cell-expand"><summary>${folded}</summary><div class="cell-expand__full">${text}</div></details>`;
  }
  return text;
}

function toMiddleEllipsis(value: string, left: number, right: number): string {
  if (value.length <= left + right + 1) {
    return value;
  }
  return `${value.slice(0, left)}...${value.slice(-right)}`;
}

function decodeHtmlEntities(value: string): string {
  return value
    .replaceAll("&amp;", "&")
    .replaceAll("&lt;", "<")
    .replaceAll("&gt;", ">")
    .replaceAll("&quot;", '"')
    .replaceAll("&#39;", "'");
}

type ApprovalColumnDef = {
  key: string;
  label: string;
  colClass: string;
  headClass: string;
  cellClass: string;
};

function buildColumnDefs(rows: Array<Record<string, unknown>>): ApprovalColumnDef[] {
  const preferred = [
    ["name", "审批名称"],
    ["approvedtime", "审批时间"],
    ["createon", "创建时间"],
    ["ownername", "申请人"],
    ["workitemname", "待办标题"],
    ["id", "记录ID"],
    ["workitemid", "待办ID"],
  ] as const;
  const first = rows[0] ?? {};
  const used = new Set<string>();
  const defs: ApprovalColumnDef[] = [];
  for (const [key, label] of preferred) {
    if (Object.prototype.hasOwnProperty.call(first, key)) {
      const columnClass = getColumnClass(key);
      defs.push({ key, label, colClass: columnClass, headClass: `head-${columnClass}`, cellClass: `cell-${columnClass}` });
      used.add(key);
    }
  }
  for (const key of Object.keys(first)) {
    if (used.has(key)) {
      continue;
    }
    const columnClass = getColumnClass(key);
    defs.push({ key, label: key, colClass: columnClass, headClass: `head-${columnClass}`, cellClass: `cell-${columnClass}` });
  }
  return defs.slice(0, 10);
}

function getColumnClass(key: string): "is-id" | "is-long" | "is-meta" {
  const lower = key.toLowerCase();
  if (lower === "id" || lower.endsWith("id")) {
    return "is-id";
  }
  if (/(summary|content|desc|detail|memo|remark|name)/.test(lower)) {
    return "is-long";
  }
  return "is-meta";
}

function reportCss(): string {
  return `:root{--bg:#f4f7fb;--card:#fff;--line:#dbe4f0;--text:#0f172a;--sub:#475569;--accent:#2563eb}*{box-sizing:border-box}body{margin:0;background:linear-gradient(180deg,#eef3fb,#f8fbff);font:14px/1.5 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,"PingFang SC","Microsoft YaHei",sans-serif;color:var(--text)}.wrap{padding:20px}.hero{background:linear-gradient(120deg,#1d4ed8,#2563eb 42%,#3b82f6);color:#fff;border-radius:14px;padding:18px 20px;margin-bottom:14px;box-shadow:0 10px 24px rgba(37,99,235,.18)}.hero h1{margin:0 0 4px;font-size:20px}.hero p{margin:0;opacity:.95}.table-wrap{overflow:auto;border:1px solid var(--line);border-radius:12px;background:var(--card);box-shadow:0 6px 18px rgba(15,23,42,.06)}table{border-collapse:separate;border-spacing:0;min-width:980px;width:max-content;background:var(--card)}thead th{position:sticky;top:0;z-index:2;background:#eef4ff;color:#1e3a8a;font-weight:700;white-space:nowrap}th,td{border-bottom:1px solid var(--line);padding:10px 12px;text-align:left;vertical-align:top}td{white-space:pre-wrap;word-break:break-word}tbody tr:nth-child(2n){background:#fafcff}.is-id{width:164px}.is-meta{width:140px}.is-long{width:520px}.cell-is-id{white-space:nowrap;word-break:normal;font-family:ui-monospace,SFMono-Regular,Menlo,Monaco,Consolas,"Liberation Mono","Courier New",monospace;font-size:12px}.cell-id-text{display:inline-block;max-width:100%;overflow:hidden;text-overflow:ellipsis;vertical-align:top}.cell-is-long{max-width:620px;line-height:1.6}.cell-expand{margin:0}.cell-expand>summary{cursor:pointer;color:#1d4ed8;list-style:none;display:inline}.cell-expand>summary::-webkit-details-marker{display:none}.cell-expand__full{margin-top:6px;color:#0f172a}.empty{padding:20px;background:#fff;border:1px dashed #c9d8ee;border-radius:12px;color:#334155}.muted{color:var(--sub)}.ts{margin-top:12px;color:#64748b;font-size:12px}details{margin-top:10px}summary{cursor:pointer;color:#1d4ed8}pre{white-space:pre-wrap;background:#fff;border:1px solid var(--line);border-radius:10px;padding:12px}`;
}
