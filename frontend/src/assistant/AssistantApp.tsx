import { FormEvent, KeyboardEvent, useEffect, useMemo, useRef, useState } from "react";
import { Link } from "react-router-dom";
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
import { getDisplayInitial } from "../shared/avatar";
import { useAsrVoiceInput } from "../shared/useAsrVoiceInput";
import { safeFetchJson } from "../utils/http";
import MyEmailAccountsModal from "./MyEmailAccountsModal";
import {
  appendAssistantDelta,
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

const LS_LOGIN_MODE = "cici_login_mode";

type AuthPayload = { token: string; orgId: string; userId: string; roles: string[] };
type ChatBubble = { role: "user" | "assistant"; content: string; time?: string; modelName?: string };
type KnowledgeBase = { id: number; name: string; description: string; status: string };
type MeProfile = { nickname?: string; mobile?: string; avatarBase64?: string };
type CurrentUserUpdatedDetail = { userId?: string; mobile?: string; nickname?: string; avatarBase64?: string };
type LoginMode = "agent" | "human";
type WorkbenchStateStatus = "处理中" | "检索中" | "等待确认" | "已完成" | "待命中";

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
      status: "检索中",
      previousTask: base.currentTask,
      currentTask: "正在整理审批节点与材料",
      nextTask: "输出审批建议或调用审批工具",
      thoughts: ["正在识别审批标题、申请人与当前阶段", "会优先判断是否需要补件或催办"],
    };
  }
  if (text.includes("客户") || text.includes("报价") || text.includes("线索")) {
    return {
      status: "检索中",
      previousTask: base.currentTask,
      currentTask: "正在梳理客户上下文与口径",
      nextTask: "生成客户跟进建议",
      thoughts: ["正在组织客户摘要、报价和话术线索", "会优先给出下一步推进动作"],
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

function finishWorkbenchState(agentKey: string, fallback?: string): WorkbenchStateMachine {
  const base = getWorkbenchDefaultState(agentKey);
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

function LoginModeSwitch({
  mode,
  onChange,
  theme,
}: {
  mode: LoginMode;
  onChange: (mode: LoginMode) => void;
  theme: "dark" | "light";
}) {
  return (
    <div className={`login-mode-toggle login-mode-toggle--${theme}`} role="tablist" aria-label="登录模式切换">
      <button
        type="button"
        className={`login-mode-toggle__item${mode === "agent" ? " is-active" : ""}`}
        onClick={() => onChange("agent")}
        aria-pressed={mode === "agent"}
      >
        智能体模式
      </button>
      <button
        type="button"
        className={`login-mode-toggle__item${mode === "human" ? " is-active" : ""}`}
        onClick={() => onChange("human")}
        aria-pressed={mode === "human"}
      >
        人机模式
      </button>
    </div>
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

function HumanModeStaticLogin({ onSwitchMode }: { onSwitchMode: (mode: LoginMode) => void }) {
  return (
    <main className="human-login">
      <div className="human-login__shell">
        <LoginModeSwitch mode="human" onChange={onSwitchMode} theme="light" />
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
                  动态验证码登录
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
  const [orgId, setOrgId] = useState("demo-org");
  const [mobile, setMobile] = useState("18611892001");
  const [code, setCode] = useState("");
  const [notice, setNotice] = useState("");
  const [loginMode, setLoginMode] = useState<LoginMode>(() => {
    const raw = localStorage.getItem(LS_LOGIN_MODE);
    return raw === "human" ? "human" : "agent";
  });
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
  const [showPlusMenu, setShowPlusMenu] = useState(false);
  const [plusMenuPos, setPlusMenuPos] = useState<{ bottom: number; left: number } | null>(null);
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
  const [workspaceTab, setWorkspaceTab] = useState<"chat" | "workbench" | "monitor" | "customers" | "crm">("workbench");
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
  const [workbenchThoughtIndex, setWorkbenchThoughtIndex] = useState(0);
  const [profilePanelOpen, setProfilePanelOpen] = useState(false);
  const chatStreamRef = useRef<HTMLDivElement | null>(null);
  const plusMenuRef = useRef<HTMLDivElement | null>(null);
  const composerInputRef = useRef<HTMLTextAreaElement | HTMLInputElement | null>(null);
  const { listening, speechSupported, start: startAsrSession, stop: stopAsrSession, abort: abortAsrSession } = useAsrVoiceInput();
  const activeConversationIdRef = useRef("");
  const workspaceTabRef = useRef<"chat" | "workbench" | "monitor" | "customers" | "crm">("workbench");
  const [monitorPulseTick, setMonitorPulseTick] = useState(0);

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

      // Recent successful executions → overview cards
      const recentSuccessful = execs
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

  // Close plus menu on outside click
  useEffect(() => {
    const handler = (event: MouseEvent) => {
      if (plusMenuRef.current && !plusMenuRef.current.contains(event.target as Node)) {
        setShowPlusMenu(false);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  useEffect(() => {
    if (auth) {
      void loadKbs();
    }
  }, [auth?.token]);

  useEffect(() => {
    if (auth) {
      void loadWorkbenchAgents(auth.token);
      void loadWorkbenchStats(auth.token);
    } else {
      setAgentWorkspaces(AGENT_WORKSPACES);
      setWorkbenchDockAgents(WORKBENCH_DOCK_AGENTS);
      setWorkbenchMetrics(WORKBENCH_METRICS_DEFAULT);
      setWorkbenchOverviewItems([]);
    }
  }, [auth?.token]);

  useEffect(() => {
    if (auth) {
      void loadMe();
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
        nickname: detail.nickname ?? prev?.nickname,
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

  const sessionStreamActive = workspaceTab !== "workbench" && workspaceTab !== "monitor" && workspaceTab !== "crm";

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
                workspaceTabRef.current === "crm"
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
    if (workspaceTab === "workbench" || workspaceTab === "monitor" || workspaceTab === "crm" || !auth || !activeConversationId) {
      return;
    }
    void loadConversationMessages(activeConversationId, true);
  }, [activeConversationId, auth?.token, workspaceTab]);

  useEffect(() => {
    if (workspaceTab === "workbench" || workspaceTab === "monitor" || workspaceTab === "crm") {
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
  const activeKbNames = kbs.filter((kb) => selectedKbIds.includes(kb.id)).map((kb) => kb.name).join(", ") || "未选择";
  const userInitial = getDisplayInitial(me?.nickname || me?.mobile || "我", "我");
  const agentUnread = (conversationsByAgent.get(activeAgent.id) ?? []).reduce((count, thread) => count + thread.unread, 0);
  const activeWorkbenchBusy = activeWorkbenchState.status !== "待命中" && activeWorkbenchState.status !== "已完成";
  const monitorRows = workbenchDockAgents.map((agent, index) => {
    const runtime = workbenchRuntimeByAgent[agent.key] ?? getWorkbenchDefaultState(agent.key);
    const threads = conversationsByAgent.get(agent.runtimeAgentId ?? agent.key) ?? [];
    const unread = threads.reduce((sum, item) => sum + item.unread, 0);
    const severity = runtime.status === "待命中" ? "idle" : runtime.status === "已完成" ? "ok" : runtime.status === "等待确认" ? "warn" : "busy";
    const baseline = unread + threads.length * 2 + index * 3 + monitorPulseTick;
    const queueDepth = Math.max(1, (baseline % 7) + (severity === "busy" ? 3 : severity === "warn" ? 2 : 1));
    const latencyMs = 160 + ((baseline * 57) % 340) + (severity === "busy" ? 120 : 0);
    const flowProgress = Math.min(98, 42 + ((baseline * 9) % 54) + (severity === "ok" ? 8 : 0));
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
      queueDepth,
      latencyMs,
      flowProgress,
    } as const;
  });
  const monitorBusyCount = monitorRows.filter((row) => row.severity === "busy" || row.severity === "warn").length;
  const monitorTotalUnread = monitorRows.reduce((sum, row) => sum + row.unread, 0);
  const monitorTimelineItems =
    workbenchOverviewItems.length > 0
      ? workbenchOverviewItems
      : [
          { id: "pulse-1", title: "线索清洗与意图归类", detail: "状态机从 Intake -> Intent Match，等待置信度确认。", status: "运行中", prompt: "" },
          { id: "pulse-2", title: "工单优先级重排", detail: "根据 SLA 与渠道压力动态分流到不同队列。", status: "排队中", prompt: "" },
          { id: "pulse-3", title: "知识检索增强回复", detail: "从 CRM 历史跟进记录补全上下文后生成回复。", status: "待确认", prompt: "" },
          { id: "pulse-4", title: "回访任务自动派发", detail: "完成后写回 CRM 并创建下一步提醒。", status: "待执行", prompt: "" },
        ];

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
    if (workspaceTab !== "monitor") {
      return;
    }
    const timer = window.setInterval(() => {
      setMonitorPulseTick((current) => current + 1);
    }, 1500);
    return () => window.clearInterval(timer);
  }, [workspaceTab]);

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

  const sendCode = async () => {
    try {
      setNotice("验证码发送中...");
      const response = await fetch("/auth/sms/send", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ orgId, mobile }),
      });
      const { body } = await safeFetchJson<{ devCode?: string }>(response);
      if (!response.ok || !body?.success) {
        setNotice(`发送失败：${body?.message ?? `HTTP ${response.status}`}`);
        return;
      }
      setNotice(`验证码已发送，本地开发验证码：${body.data?.devCode ?? "（未返回）"}`);
    } catch (error) {
      setNotice(`发送失败：${error instanceof Error ? error.message : String(error)}`);
    }
  };

  const login = async () => {
    try {
      setNotice("登录中...");
      const response = await fetch("/auth/sms/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ orgId, mobile, code }),
      });
      const { body } = await safeFetchJson<AuthPayload>(response);
      if (!response.ok || !body?.success || !body.data?.token) {
        setNotice(`登录失败：${body?.message ?? `HTTP ${response.status}`}`);
        return;
      }
      persistAuth(body.data);
      await loadMe(body.data.token);
      setNotice("登录成功。");
    } catch (error) {
      setNotice(`登录失败：${error instanceof Error ? error.message : String(error)}`);
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

    try {
      const kbIds = selectedKbIds.map(String);
      await streamAiChat(
        auth.token,
        {
          sessionId,
          question: cleanQuestion,
          knowledgeBaseIds: kbIds.length ? kbIds : [],
          agentId: isWorkbench ? activeWorkbenchAgent.runtimeAgentId : activeAgent.id,
        },
        (delta) => {
          if (suppress) {
            return;
          }
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
          [agentKey]: finishWorkbenchState(agentKey, prev[agentKey]?.currentTask),
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
    }
  };

  const runWorkbenchPrompt = async (prompt: string) => {
    if (workspaceTab !== "workbench") {
      setWorkspaceTab("workbench");
    }
    await submitQuestion(prompt);
  };

  const submitCurrentInput = async () => {
    if (!input.trim()) {
      return;
    }
    setShowPlusMenu(false);
    await submitQuestion(input.trim());
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
    setConversationThreads([]);
    setConversationMessages({});
    setConversationListNotice("");
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
    setNotice("已退出。");
  };

  useEffect(() => {
    const element = chatStreamRef.current;
    if (element) {
      element.scrollTop = element.scrollHeight;
    }
  }, [activeConversationId, chatLoading, visibleMessages, workspaceTab]);

  useEffect(() => {
    localStorage.setItem(LS_LOGIN_MODE, loginMode);
  }, [loginMode]);

  const systemAgents = agentWorkspaces.filter((item) => item.category === "system");
  const publishedAgents = agentWorkspaces.filter((item) => item.category === "published");

  if (!auth) {
    if (loginMode === "human") {
      return <HumanModeStaticLogin onSwitchMode={setLoginMode} />;
    }

    return (
      <main className="boot-login boot-login--cyber boot-login--fusion">
        <div className="boot-login__layers" aria-hidden>
          <BootLoginDataStream />
          <div className="boot-login__grid-floor boot-login__grid-floor--full" />
          <div className="boot-login__scanline boot-login__scanline--full" />
        </div>
        <div className="boot-login__shell">
          <LoginModeSwitch mode={loginMode} onChange={setLoginMode} theme="dark" />
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
              <div className="boot-login__form">
                <div className="boot-login__field">
                  <label htmlFor="boot-org">组织 ID</label>
                  <input
                    id="boot-org"
                    className="boot-login__input"
                    value={orgId}
                    onChange={(event) => setOrgId(event.target.value)}
                    autoComplete="organization"
                  />
                </div>
                <div className="boot-login__field">
                  <label htmlFor="boot-mobile">手机号</label>
                  <input
                    id="boot-mobile"
                    className="boot-login__input"
                    value={mobile}
                    onChange={(event) => setMobile(event.target.value)}
                    inputMode="tel"
                    autoComplete="tel"
                  />
                </div>
                <div className="boot-login__field">
                  <label htmlFor="boot-code">短信验证码</label>
                  <input
                    id="boot-code"
                    className="boot-login__input"
                    value={code}
                    onChange={(event) => setCode(event.target.value)}
                    inputMode="numeric"
                    autoComplete="one-time-code"
                  />
                </div>
              </div>
              <div className="boot-login__actions">
                <button type="button" className="boot-login__btn boot-login__btn--ghost" onClick={sendCode} disabled={code.length >= 4}>
                  获取验证码
                </button>
                <button type="button" className="boot-login__btn boot-login__btn--primary" onClick={login} disabled={!code.trim()}>
                  <span className="boot-phone-icon" aria-hidden>
                    <svg viewBox="0 0 24 24" width="20" height="20" fill="none">
                      <path d="M4 12h12" stroke="white" strokeWidth="2.4" strokeLinecap="round" />
                      <path d="M12 5l8 7-8 7" stroke="white" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
                    </svg>
                  </span>
                  <span className="sr-only">开始对话</span>
                </button>
              </div>
              {notice ? <p className="boot-login__notice">{notice}</p> : null}
              <p className="boot-login__footer-link">
                需要配置知识库或成员？ <Link to="/admin/login" className="boot-login__link">管理控制台</Link>
              </p>
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
            className="cici-rail__avatar cici-rail__avatar--button"
            onClick={() => setProfilePanelOpen(true)}
            data-menu-label="个人设置"
            aria-label="个人设置"
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
            className={`cici-rail__nav-item cici-rail__menu-btn${workspaceTab === "monitor" ? " is-active" : ""}`}
            onClick={() => setWorkspaceTab("monitor")}
            data-menu-label="智能体监控"
            aria-label="智能体监控"
          >
            <svg viewBox="0 0 24 24">
              <rect x="3" y="3" width="7" height="7" rx="1" />
              <rect x="14" y="3" width="7" height="7" rx="1" />
              <rect x="3" y="14" width="7" height="7" rx="1" />
              <rect x="14" y="14" width="7" height="7" rx="1" />
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
          <button className="cici-rail__nav-item cici-rail__menu-btn" data-menu-label="系统设置" aria-label="系统设置">
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
          <div className="cici-rail__logo">
            <div className="cici-rail__logo-icon">CB</div>
          </div>
        </div>
      </nav>
      {auth?.token ? (
        <MyEmailAccountsModal
          open={profilePanelOpen}
          token={auth.token}
          onClose={() => setProfilePanelOpen(false)}
        />
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
                      {/* + button (left side) */}
                      <div className="cici-composer-plus" ref={plusMenuRef}>
                        <button
                          type="button"
                          className="cici-composer-plus__btn"
                          onClick={(e: React.MouseEvent<HTMLButtonElement>) => {
                            const wasOpen = showPlusMenu;
                            if (!wasOpen) {
                              const rect = e.currentTarget.getBoundingClientRect();
                              setPlusMenuPos({
                                bottom: window.innerHeight - rect.top + 8,
                                left: rect.left,
                              });
                            } else {
                              setPlusMenuPos(null);
                            }
                            setShowPlusMenu(!wasOpen);
                          }}
                          title="更多操作"
                        >
                          <svg viewBox="0 0 24 24">
                            <line x1="12" y1="5" x2="12" y2="19" />
                            <line x1="5" y1="12" x2="19" y2="12" />
                          </svg>
                        </button>
                        {showPlusMenu && plusMenuPos && (
                          <div className="cici-composer-plus__menu" style={{ bottom: `${plusMenuPos.bottom}px`, left: `${plusMenuPos.left}px` }}>
                            <button type="button" className="cici-composer-plus__menu-item">
                              <svg viewBox="0 0 24 24">
                                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                                <polyline points="17 8 12 3 7 8" />
                                <line x1="12" y1="3" x2="12" y2="15" />
                              </svg>
                              上传文件
                            </button>
                            <button type="button" className="cici-composer-plus__menu-item">
                              <svg viewBox="0 0 24 24">
                                <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
                              </svg>
                              添加快捷短语
                            </button>
                          </div>
                        )}
                      </div>
                      {/* textarea (center, fills remaining space) */}
                      <textarea
                        ref={attachComposerTextareaRef}
                        value={input}
                        onChange={(event) => setInput(event.target.value)}
                        onKeyDown={handleComposerTextareaKeyDown}
                        placeholder="输入任务，例如：先判断今天最优先的审批，再生成客户跟进摘要。"
                        disabled={chatLoading}
                      />
                      {/* actions (right side: mic + send) */}
                      <div className="cici-workbench__composer-actions">
                        {/* mic button */}
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
                        {/* send button */}
                        <button type="submit" disabled={chatLoading} className="cici-workbench__send-btn">
                          <svg viewBox="0 0 24 24">
                            <line x1="12" y1="19" x2="12" y2="5" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" />
                            <polyline points="5 12 12 5 19 12" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" fill="none" />
                          </svg>
                        </button>
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
          </div>
        </main>
      ) : workspaceTab === "monitor" ? (
        <main className="cici-monitor">
          <header className="cici-monitor__hero">
            <div>
              <p className="cici-monitor__kicker">NEURAL OPS GRID</p>
              <h1>智能体状态机监控控制台</h1>
              <p>当前用户已接入智能体的运行状态、任务阶段与会话压力总览。</p>
            </div>
            <div className="cici-monitor__stats">
              <article>
                <span>在线智能体</span>
                <strong>{monitorRows.length}</strong>
              </article>
              <article>
                <span>活跃/待确认</span>
                <strong>{monitorBusyCount}</strong>
              </article>
              <article>
                <span>累计未读</span>
                <strong>{monitorTotalUnread}</strong>
              </article>
            </div>
          </header>

          <section className="cici-monitor__grid">
            {monitorRows.map((row) => (
              <article key={row.key} className={`cici-monitor-card cici-monitor-card--${row.severity}`}>
                <div className="cici-monitor-card__head">
                  <AvatarView
                    src={row.avatarBase64}
                    fallback={row.short}
                    className="cici-monitor-card__avatar"
                    style={{ background: row.color }}
                    alt={`${row.name} 监控头像`}
                  />
                  <div>
                    <h3>{row.name}</h3>
                    <p>
                      {row.threadCount} 个会话 · {row.unread} 未读
                    </p>
                  </div>
                  <span className="cici-monitor-card__state">{row.status}</span>
                </div>
                <div className="cici-monitor-card__lanes">
                  <div>
                    <span>PREV</span>
                    <strong>{row.previousTask}</strong>
                  </div>
                  <div>
                    <span>NOW</span>
                    <strong>{row.currentTask}</strong>
                  </div>
                  <div>
                    <span>NEXT</span>
                    <strong>{row.nextTask}</strong>
                  </div>
                </div>
                <div className="cici-monitor-card__thoughts">
                  {(row.thoughts.length ? row.thoughts : ["等待新的业务上下文"]).slice(0, 2).map((t) => (
                    <p key={t}>{t}</p>
                  ))}
                </div>
                <div className="cici-monitor-card__flow">
                  <span>FLOW {row.flowProgress}% · 队列 {row.queueDepth} · 延迟 {row.latencyMs}ms</span>
                  <div className="cici-monitor-card__flow-track">
                    <i style={{ width: `${row.flowProgress}%` }} />
                  </div>
                </div>
              </article>
            ))}
            {monitorRows.length === 0 ? (
              <div className="cici-monitor__empty">当前没有可监控的智能体运行数据。</div>
            ) : null}
          </section>

          <section className="cici-monitor__timeline">
            <h2>任务脉冲 · Task Pulse</h2>
            <div className="cici-monitor__timeline-list">
              {monitorTimelineItems.map((item) => (
                <article key={item.id} className="cici-monitor__timeline-item">
                  <strong>{item.title}</strong>
                  <p>{item.detail}</p>
                  <span>{item.status}</span>
                </article>
              ))}
            </div>
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
