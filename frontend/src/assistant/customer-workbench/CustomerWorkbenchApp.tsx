import { useEffect, useMemo, useRef, useState } from "react";
import {
  AlertTriangle,
  ArrowRightLeft,
  Bell,
  Bot,
  CalendarDays,
  Check,
  ChevronDown,
  ClipboardCheck,
  ClipboardList,
  ExternalLink,
  FileText,
  Inbox,
  Info,
  Keyboard,
  Link2,
  List as ListIcon,
  LoaderCircle,
  MessageSquare,
  MessageCircle,
  Mic,
  PanelRightClose,
  PanelRightOpen,
  Pencil,
  Phone,
  RefreshCw,
  Search,
  Send,
  Settings2,
  Users,
  X,
  type LucideIcon,
} from "lucide-react";
import { useAsrVoiceInput } from "../../shared/useAsrVoiceInput";
import ChatMarkdown from "../../components/ChatMarkdown";
import {
  acceptCustomerRecommendation,
  applyCustomerRecommendation,
  confirmCustomerRecommendation,
  dismissCustomerRecommendation,
  getCustomerWorkbenchIntegrationStatus,
  getCustomerAssistantHistory,
  getCustomerWorkbenchNotifications,
  getCustomerWorkbenchSupervisorSummary,
  getCustomerWorkbenchQueue,
  getCustomerWorkbenchDetail,
  setCustomerFollowed,
  saveCustomerInteraction,
  submitCustomerRecommendationFeedback,
  streamCustomerWorkbenchAssistant,
  updateCustomerRecommendation,
  type CustomerAssistantResult,
  type CustomerRecommendation,
  type CustomerWorkbenchAccount,
  type CustomerWorkbenchDetail,
} from "./customerWorkbenchApi";

type ChatMessage = {
  id: string;
  role: "user" | "assistant";
  text: string;
  time: string;
  busy?: boolean;
  phase?: string;
};

type WorkbenchMode = "new" | "existing";
type DetailTab =
  | "overview"
  | "timeline"
  | "signals"
  | "recommendations"
  | "actions"
  | "service"
  | "value"
  | "renewal"
  | "relationship";
type RecommendationAction = "accept" | "edit" | "dismiss" | "confirm" | "apply";

export function isCurrentVoiceSession(sessionId: number, currentSessionId: number): boolean {
  return sessionId === currentSessionId;
}

export function scrollConversationToLatest(element: Pick<HTMLElement, "scrollTop" | "scrollHeight"> | null): void {
  if (element) element.scrollTop = element.scrollHeight;
}

export function assistantPhaseLabel(phase: string): string {
  return ({
    connecting: "正在连接智能助手...",
    context_ready: "客户上下文已就绪...",
    run: "正在准备回答...",
    model: "正在分析客户信息...",
    retrieving: "正在检索相关资料...",
    rag_done: "相关资料已就绪...",
    generating: "正在生成回复...",
    tool_call: "正在查询业务数据...",
  } as Record<string, string>)[phase] ?? "正在处理...";
}

export function customerWorkbenchBodyClassName(assistantOpen: boolean, assistantExpanded: boolean): string {
  return `customer-workbench__body${assistantOpen ? "" : " is-assistant-closed"}${assistantOpen && assistantExpanded ? " is-assistant-expanded" : ""}`;
}

type IconName =
  | "alert"
  | "bell"
  | "bot"
  | "calendar"
  | "check"
  | "chevronDown"
  | "clipboard"
  | "close"
  | "document"
  | "edit"
  | "external"
  | "inbox"
  | "info"
  | "keyboard"
  | "link"
  | "list"
  | "message"
  | "mic"
  | "people"
  | "phone"
  | "panelExpand"
  | "panelRestore"
  | "refresh"
  | "search"
  | "send"
  | "sliders"
  | "swap"
  | "task"
  | "wechat";

const workbenchIcons: Record<IconName, LucideIcon> = {
  alert: AlertTriangle,
  bell: Bell,
  bot: Bot,
  calendar: CalendarDays,
  check: Check,
  chevronDown: ChevronDown,
  clipboard: ClipboardCheck,
  close: X,
  document: FileText,
  edit: Pencil,
  external: ExternalLink,
  inbox: Inbox,
  info: Info,
  keyboard: Keyboard,
  link: Link2,
  list: ListIcon,
  message: MessageSquare,
  mic: Mic,
  people: Users,
  phone: Phone,
  panelExpand: PanelRightOpen,
  panelRestore: PanelRightClose,
  refresh: RefreshCw,
  search: Search,
  send: Send,
  sliders: Settings2,
  swap: ArrowRightLeft,
  task: ClipboardList,
  wechat: MessageCircle,
};

const segmentLabels: Record<string, string> = {
  NEW: "新客户",
  EXISTING: "老客户",
  STRATEGIC: "战略客户",
  RISK: "风险客户",
};

const modeFilterOptions: Record<WorkbenchMode, Array<[string, string]>> = {
  new: [
    ["focus", "重点推进"],
    ["follow", "待跟进"],
    ["risk", "风险客户"],
    ["recommendations", "待确认建议"],
  ],
  existing: [
    ["renewal", "续约90天"],
    ["health", "健康下降"],
    ["service", "服务异常"],
    ["expansion", "增购信号"],
  ],
};

const modeTabs: Record<WorkbenchMode, Array<[DetailTab, string]>> = {
  new: [
    ["overview", "推进概览"],
    ["timeline", "互动时间线"],
    ["signals", "推进信号"],
    ["recommendations", "CRM 落地建议"],
    ["actions", "下一步行动"],
  ],
  existing: [
    ["overview", "经营概览"],
    ["timeline", "互动时间线"],
    ["service", "服务问题"],
    ["value", "价值兑现"],
    ["renewal", "续约增购"],
    ["relationship", "关系地图"],
  ],
};

export function defaultCustomerQueueFilter(mode: WorkbenchMode) {
  return mode === "new" ? "focus" : "";
}

function nowTime() {
  const now = new Date();
  return `${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}`;
}

function chatTime(value: string) {
  if (!value) return nowTime();
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return nowTime();
  return `${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
}

function segmentLabel(segment: string) {
  return segmentLabels[segment] ?? segment;
}

function roleLabel(role: string) {
  return ({ OWNER: "组织负责人", ORG_ADMIN: "组织管理员", ORG_USER: "业务用户" } as Record<string, string>)[role] ?? role;
}

function shortDate(value: string) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const now = new Date();
  const dayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const itemDayStart = new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
  const time = `${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
  if (itemDayStart === dayStart) return `今天 ${time}`;
  if (itemDayStart === dayStart - 24 * 60 * 60 * 1000) return `昨天 ${time}`;
  return `${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")} ${time}`;
}

function formatConfidence(value: number) {
  const normalized = Number(value);
  if (!Number.isFinite(normalized)) return "—";
  const percent = normalized > 1 ? normalized : normalized * 100;
  return `${Math.round(percent)}%`;
}

function metricValue(detail: CustomerWorkbenchDetail | null, key: string, fallback: number) {
  const value = Number(detail?.metrics?.[key]?.value);
  return Number.isFinite(value) ? value : fallback;
}

function queueStatus(account: CustomerWorkbenchAccount) {
  if (account.segment === "RISK") return "风险";
  if (account.pendingRecommendationCount > 0) return "关注";
  if (account.segment === "EXISTING" || account.segment === "STRATEGIC") return "健康";
  return "待跟进";
}

function queueStatusClass(account: CustomerWorkbenchAccount) {
  const status = queueStatus(account);
  if (status === "风险") return "is-risk";
  if (status === "健康") return "is-healthy";
  if (status === "待跟进") return "is-pending";
  return "is-focus";
}

function lifecycleSourceLabel(value: string) {
  if (!value) return "客户互动";
  const normalized = value.toUpperCase();
  if (value.includes("微信") || normalized.includes("WECHAT")) return "微信";
  if (value.includes("电话") || normalized.includes("PHONE")) return "通话录音";
  if (value.includes("会议") || normalized.includes("MEETING") || normalized.includes("EVENT")) return "会议纪要";
  return value;
}

function sourceIconName(value: string): IconName {
  const label = lifecycleSourceLabel(value);
  if (label === "微信") return "wechat";
  if (label === "通话录音") return "phone";
  if (label === "会议纪要") return "calendar";
  return "message";
}

function metricIconName(label: string): IconName {
  if (label.includes("风险")) return "alert";
  if (label.includes("任务")) return "calendar";
  if (label.includes("互动")) return "message";
  return "clipboard";
}

function recommendationIconName(type: string, index: number): IconName {
  const normalized = type.toUpperCase();
  if (normalized.includes("RISK")) return "alert";
  if (normalized.includes("CONTACT")) return "people";
  if (normalized.includes("DEMAND")) return "document";
  return ["task", "alert", "people", "document"][index % 4] as IconName;
}

function evidenceLabel(value: unknown) {
  if (typeof value === "string") return value;
  if (!value || typeof value !== "object") return "CRM 事实";
  const item = value as Record<string, unknown>;
  return String(item.title || item.detail || item.subject || item.source || "CRM 事实");
}

function Icon({ name, className = "" }: { name: IconName; className?: string }) {
  const Component = workbenchIcons[name];
  return <Component className={`customer-workbench-icon${className ? ` ${className}` : ""}`} strokeWidth={1.8} aria-hidden />;
}

type CustomerWorkbenchAppProps = {
  token: string;
  embedded?: boolean;
  userName?: string;
  userRole?: string;
};

export function CustomerWorkbenchApp({ token, embedded = false, userName = "我", userRole = "当前用户" }: CustomerWorkbenchAppProps) {
  const initialParams = new URLSearchParams(window.location.search);
  const initialAccountId = initialParams.get("accountId")?.trim() || "";
  const initialMode: WorkbenchMode = initialParams.get("mode") === "existing" ? "existing" : "new";
  const [accounts, setAccounts] = useState<CustomerWorkbenchAccount[]>([]);
  const [workbenchMode, setWorkbenchMode] = useState<WorkbenchMode>(initialMode);
  const [activeAccountId, setActiveAccountId] = useState(initialAccountId);
  const [detail, setDetail] = useState<CustomerWorkbenchDetail | null>(null);
  const [activeTab, setActiveTab] = useState<DetailTab>("overview");
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState(defaultCustomerQueueFilter(initialMode));
  const [sort, setSort] = useState("priority");
  const [page, setPage] = useState(1);
  const [queueMeta, setQueueMeta] = useState({ totalElements: 0, totalPages: 0, filterCounts: {} as Record<string, number>, dataAsOf: "" });
  const [integration, setIntegration] = useState<{ ready: boolean; label: string; baseUrl?: string; message?: string }>({ ready: false, label: "正在连接 CRM" });
  const [notifications, setNotifications] = useState<Array<{ accountId: string; accountName: string; title: string; customerMode?: string }>>([]);
  const [supervisorSummary, setSupervisorSummary] = useState<{ visibleAccounts: number; riskAccounts: number; pendingRecommendations: number; writeSuccessRate: number } | null>(null);
  const [showNotifications, setShowNotifications] = useState(false);
  const [showQueueSettings, setShowQueueSettings] = useState(false);
  const [compactQueue, setCompactQueue] = useState(false);
  const [pageSize, setPageSize] = useState(12);
  const [assistantOpen, setAssistantOpen] = useState(true);
  const [assistantExpanded, setAssistantExpanded] = useState(false);
  const [editingRecommendation, setEditingRecommendation] = useState<CustomerRecommendation | null>(null);
  const [interactionEditorOpen, setInteractionEditorOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [notice, setNotice] = useState("");
  const [assistantInput, setAssistantInput] = useState("");
  const [assistantReplying, setAssistantReplying] = useState(false);
  const [assistantMessages, setAssistantMessages] = useState<ChatMessage[]>([
    { id: "welcome", role: "assistant", text: "我可以根据当前工作台数据总结互动、查看风险、切换客户，或形成待确认的 CRM 落地建议。", time: nowTime() },
  ]);
  const recommendationRef = useRef<HTMLDivElement | null>(null);
  const composerInputRef = useRef<HTMLTextAreaElement | null>(null);
  const assistantChatRef = useRef<HTMLDivElement | null>(null);
  const searchInputRef = useRef<HTMLInputElement | null>(null);
  const deepLinkedAccountIdRef = useRef(initialAccountId);
  const voiceSessionIdRef = useRef(0);
  const chatMessageSequenceRef = useRef(0);
  const assistantStreamAbortRef = useRef<AbortController | null>(null);
  const { listening, speechSupported, start: startAsrSession, stop: stopAsrSession, abort: abortAsrSession } = useAsrVoiceInput();

  useEffect(() => {
    if (!token) return;
    let ignore = false;
    const timer = window.setTimeout(() => {
      setLoading(true);
      getCustomerWorkbenchQueue(token, { mode: workbenchMode, filter, sort, direction: "desc", query, page, size: pageSize })
        .then((result) => {
          if (ignore) return;
          setAccounts(result.items);
          setQueueMeta({ totalElements: result.totalElements, totalPages: result.totalPages, filterCounts: result.filterCounts, dataAsOf: result.dataAsOf || "" });
          setActiveAccountId((current) => {
            if (deepLinkedAccountIdRef.current && current === deepLinkedAccountIdRef.current) return current;
            return result.items.some((item) => item.accountId === current) ? current : result.items[0]?.accountId || "";
          });
        })
        .catch((error) => setNotice(error instanceof Error ? error.message : String(error)))
        .finally(() => !ignore && setLoading(false));
    }, 220);
    return () => {
      ignore = true;
      window.clearTimeout(timer);
    };
  }, [filter, page, pageSize, query, sort, token, workbenchMode]);

  useEffect(() => {
    if (!token) return;
    Promise.all([getCustomerWorkbenchIntegrationStatus(token), getCustomerWorkbenchNotifications(token)])
      .then(([status, items]) => { setIntegration(status); setNotifications(items); })
      .catch((error) => setIntegration({ ready: false, label: "CRM 连接异常", message: error instanceof Error ? error.message : String(error) }));
    getCustomerWorkbenchSupervisorSummary(token).then(setSupervisorSummary).catch(() => setSupervisorSummary(null));
  }, [token]);

  useEffect(() => {
    if (!token || !activeAccountId) {
      setDetail(null);
      return;
    }
    let ignore = false;
    setDetail(null);
    getCustomerWorkbenchDetail(token, activeAccountId)
      .then((item) => {
        if (!ignore) {
          setDetail(item);
          if (deepLinkedAccountIdRef.current === item.accountId) deepLinkedAccountIdRef.current = "";
        }
      })
      .catch((error) => {
        deepLinkedAccountIdRef.current = "";
        setNotice(error instanceof Error ? error.message : String(error));
      });
    return () => {
      ignore = true;
    };
  }, [token, activeAccountId]);

  const activeAccount = useMemo(() => accounts.find((item) => item.accountId === activeAccountId) ?? accounts[0], [accounts, activeAccountId]);

  useEffect(() => {
    if (!token || !activeAccountId) return;
    let ignore = false;
    assistantStreamAbortRef.current?.abort();
    assistantStreamAbortRef.current = null;
    setAssistantReplying(false);
    const accountName = activeAccount?.name || "当前客户";
    setAssistantMessages([{ id: `account-${activeAccountId}`, role: "assistant", text: `已进入${accountName}。可以查询互动、风险和 CRM 建议。`, time: nowTime() }]);
    getCustomerAssistantHistory(token, activeAccountId)
      .then((items) => {
        if (ignore || !items.length) return;
        setAssistantMessages(items.map((item, index) => ({
          id: `history-${activeAccountId}-${index}`,
          role: item.role === "user" ? "user" : "assistant",
          text: item.content,
          time: chatTime(item.createdAt),
        })));
      })
      .catch(() => undefined);
    return () => { ignore = true; };
  }, [activeAccount?.name, activeAccountId, token]);

  useEffect(() => {
    const frame = window.requestAnimationFrame(() => scrollConversationToLatest(assistantChatRef.current));
    return () => window.cancelAnimationFrame(frame);
  }, [assistantMessages]);

  const switchMode = (mode: WorkbenchMode) => {
    setWorkbenchMode(mode);
    setFilter(defaultCustomerQueueFilter(mode));
    setPage(1);
    setActiveTab("overview");
    deepLinkedAccountIdRef.current = "";
    setActiveAccountId("");
  };

  const reloadDetail = async () => {
    if (!token || !activeAccountId) return;
    setDetail(await getCustomerWorkbenchDetail(token, activeAccountId));
    const result = await getCustomerWorkbenchQueue(token, { mode: workbenchMode, filter, sort, direction: "desc", query, page, size: pageSize, refresh: true });
    setAccounts(result.items);
    setQueueMeta({ totalElements: result.totalElements, totalPages: result.totalPages, filterCounts: result.filterCounts, dataAsOf: result.dataAsOf || "" });
  };

  const handleRecommendation = async (item: CustomerRecommendation, action: RecommendationAction) => {
    if (!token) return;
    try {
      if (action === "edit") {
        setEditingRecommendation(item);
        return;
      } else if (action === "accept") {
        await acceptCustomerRecommendation(token, item.recommendationId);
        setNotice("建议已采纳，请核对字段后确认执行。");
      } else if (action === "confirm") {
        await confirmCustomerRecommendation(token, item.recommendationId);
        setNotice("建议已确认，现在可以写入 CRM。");
      } else if (action === "dismiss") {
        await dismissCustomerRecommendation(token, item.recommendationId, "用户在客户互动工作台选择忽略");
        setNotice("建议已忽略，未写入 CRM。");
      } else {
        const result = await applyCustomerRecommendation(token, item.recommendationId);
        setNotice(result.message || "CRM 落地动作已完成。");
      }
      await reloadDetail();
    } catch (error) {
      setNotice(error instanceof Error ? error.message : String(error));
    }
  };

  const saveRecommendationEdit = async (draft: Partial<CustomerRecommendation>) => {
    if (!token || !editingRecommendation) return;
    try {
      await updateCustomerRecommendation(token, editingRecommendation.recommendationId, draft);
      setEditingRecommendation(null);
      setNotice("建议已更新，请重新采纳并确认后执行。");
      await reloadDetail();
    } catch (error) {
      setNotice(error instanceof Error ? error.message : String(error));
    }
  };

  const handleRecommendationFeedback = async (item: CustomerRecommendation, rating: "HELPFUL" | "NOT_HELPFUL") => {
    try {
      await submitCustomerRecommendationFeedback(token, item.recommendationId, rating);
      setNotice(rating === "HELPFUL" ? "已记录：该建议有帮助。" : "已记录：该建议需要改进。");
      await reloadDetail();
    } catch (error) {
      setNotice(error instanceof Error ? error.message : String(error));
    }
  };

  const handleAssistantResult = (result: CustomerAssistantResult) => {
    if (result.action === "SWITCH_ACCOUNT" && result.actionPayload?.accountId) {
      setActiveAccountId(result.actionPayload.accountId);
      setActiveTab("overview");
    }
    if (result.action === "FOCUS_RECOMMENDATIONS") {
      setActiveTab("recommendations");
      window.setTimeout(() => recommendationRef.current?.scrollIntoView({ behavior: "smooth", block: "start" }), 120);
    }
    if (result.action === "SWITCH_MODE" && result.actionPayload?.mode) switchMode(result.actionPayload.mode);
    if (result.action === "OPEN_TAB" && result.actionPayload?.tab) setActiveTab(result.actionPayload.tab as DetailTab);
    if (result.action === "SELECT_NEXT_ACCOUNT" && accounts.length) {
      const index = accounts.findIndex((item) => item.accountId === activeAccountId);
      setActiveAccountId(accounts[(index + 1) % accounts.length].accountId);
    }
    if (result.action === "PROPOSE_RECOMMENDATION") setActiveTab("recommendations");
  };

  const submitAssistant = async (preset?: string) => {
    const message = (preset ?? assistantInput).trim();
    if (!message || !token || assistantReplying) return;
    voiceSessionIdRef.current += 1;
    abortAsrSession();
    assistantStreamAbortRef.current?.abort();
    const streamController = new AbortController();
    assistantStreamAbortRef.current = streamController;
    const sequence = ++chatMessageSequenceRef.current;
    const userMessageId = `user-${Date.now()}-${sequence}`;
    const assistantMessageId = `assistant-${Date.now()}-${sequence}`;
    let workbenchResult: CustomerAssistantResult | null = null;
    setAssistantInput("");
    setAssistantReplying(true);
    setAssistantMessages((prev) => [
      ...prev,
      { id: userMessageId, role: "user", text: message, time: nowTime() },
      { id: assistantMessageId, role: "assistant", text: "", time: nowTime(), busy: true, phase: assistantPhaseLabel("connecting") },
    ]);
    try {
      await streamCustomerWorkbenchAssistant(token, { accountId: activeAccountId, message }, async (event) => {
        if (event.type === "workbench") {
          workbenchResult = event.result;
          return;
        }
        if (event.type === "phase") {
          setAssistantMessages((prev) => prev.map((item) => item.id === assistantMessageId
            ? { ...item, phase: assistantPhaseLabel(event.phase) }
            : item));
          return;
        }
        if (event.type === "tool_call") {
          setAssistantMessages((prev) => prev.map((item) => item.id === assistantMessageId
            ? { ...item, phase: assistantPhaseLabel("tool_call") }
            : item));
          return;
        }
        if (event.type === "delta") {
          setAssistantMessages((prev) => prev.map((item) => item.id === assistantMessageId
            ? { ...item, text: `${item.text}${event.text}`, phase: assistantPhaseLabel("generating") }
            : item));
          await new Promise<void>((resolve) => window.setTimeout(resolve, 0));
          return;
        }
        if (event.type === "error") throw new Error(event.message);
      }, streamController.signal);
      setAssistantMessages((prev) => prev.map((item) => item.id === assistantMessageId
        ? { ...item, busy: false, phase: "", text: item.text || "智能助手暂未生成有效回复，请重试。" }
        : item));
      if (workbenchResult) handleAssistantResult(workbenchResult);
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") return;
      const messageText = error instanceof Error ? error.message : String(error);
      setAssistantMessages((prev) => prev.map((item) => item.id === assistantMessageId
        ? { ...item, busy: false, phase: "", text: item.text ? `${item.text}\n\n回复中断：${messageText}` : `暂时无法完成回复：${messageText}` }
        : item));
    } finally {
      if (assistantStreamAbortRef.current === streamController) assistantStreamAbortRef.current = null;
      setAssistantReplying(false);
    }
  };

  const startVoice = async () => {
    if (listening) {
      stopAsrSession();
      setNotice("正在结束语音录入...");
      return;
    }
    if (!speechSupported) {
      setNotice("当前浏览器不支持录音，可直接输入指令。");
      return;
    }
    const prefixBeforeSpeech = assistantInput;
    const voiceSessionId = voiceSessionIdRef.current + 1;
    voiceSessionIdRef.current = voiceSessionId;
    await startAsrSession({
      token,
      provider: "aliyun",
      speakerDiarization: false,
      getPrefix: () => prefixBeforeSpeech,
      onLiveText: (text) => {
        if (isCurrentVoiceSession(voiceSessionId, voiceSessionIdRef.current)) setAssistantInput(text);
      },
      onNotice: setNotice,
      onFinished: async ({ asrText, fullText }) => {
        if (!isCurrentVoiceSession(voiceSessionId, voiceSessionIdRef.current)) return;
        if (asrText) {
          setAssistantInput(fullText);
          setNotice("语音录入完成，内容已生成到输入框。");
        } else {
          setNotice("未识别到有效语音内容。");
        }
        window.setTimeout(() => composerInputRef.current?.focus(), 0);
      },
      autoStopAfterNoSpeechMs: 5000,
    });
  };

  if (!token) {
    return <section className="customer-workbench-empty">请先登录后使用客户互动工作台。</section>;
  }

  return (
    <section className={`customer-workbench${embedded ? " customer-workbench--embedded" : ""}`}>
      <header className="customer-workbench__topbar">
        <div className="customer-workbench__app-title">
          <h1>客户互动工作台</h1>
        </div>
        <div className="customer-workbench__top-actions">
          <div className="customer-workbench__mode-switch cici-product-mode-switch" aria-label="客户互动工作台模式">
            <button type="button" className={workbenchMode === "new" ? "is-active" : ""} onClick={() => switchMode("new")}>新客户推进</button>
            <button type="button" className={workbenchMode === "existing" ? "is-active" : ""} onClick={() => switchMode("existing")}>老客户经营</button>
          </div>
          <button type="button" className="customer-workbench__crm-state" onClick={() => void reloadDetail()} title={integration.message || "刷新 CRM 数据"}>
            <span aria-hidden className={integration.ready ? "is-ready" : "is-error"} />{integration.label}<Icon name="refresh" />
          </button>
          <div className="customer-workbench__notification-wrap">
            <button type="button" className="customer-workbench__icon-button cici-product-icon-button" aria-label="客户提醒" title="客户提醒" onClick={() => setShowNotifications((value) => !value)}><Icon name="bell" /></button>
            {notifications.length ? <b className="customer-workbench__notification-count">{notifications.length}</b> : null}
            {showNotifications ? (
              <div className="customer-workbench__notification-popover">
                <strong>客户提醒</strong>
                {supervisorSummary ? <div className="customer-workbench__supervisor-summary">
                  <span>可见客户<b>{supervisorSummary.visibleAccounts}</b></span>
                  <span>风险客户<b>{supervisorSummary.riskAccounts}</b></span>
                  <span>待处理建议<b>{supervisorSummary.pendingRecommendations}</b></span>
                  <span>写回成功率<b>{supervisorSummary.writeSuccessRate}%</b></span>
                </div> : null}
                {notifications.length ? notifications.map((item) => (
                  <button key={`${item.accountId}-${item.title}`} type="button" onClick={() => {
                    const targetMode: WorkbenchMode = item.customerMode === "EXISTING" ? "existing" : "new";
                    if (targetMode !== workbenchMode) switchMode(targetMode);
                    deepLinkedAccountIdRef.current = item.accountId;
                    setActiveAccountId(item.accountId);
                    setShowNotifications(false);
                  }}>
                    <span>{item.accountName}</span><small>{item.title}</small>
                  </button>
                )) : <p>暂无待处理提醒</p>}
              </div>
            ) : null}
          </div>
          <div className="customer-workbench__profile">
            <i aria-hidden>{userName.trim().slice(0, 1) || "我"}</i>
            <span>{userName}</span>
            <small>{roleLabel(userRole)}</small>
          </div>
        </div>
      </header>

      <div className={customerWorkbenchBodyClassName(assistantOpen, assistantExpanded)}>
        <aside
          className={`customer-workbench__queue${showQueueSettings ? " has-settings" : ""}`}
          aria-label={workbenchMode === "new" ? "新客户推进队列" : "老客户经营队列"}
          aria-hidden={assistantExpanded}
          inert={assistantExpanded ? true : undefined}
        >
          <header>
            <div className="customer-workbench__queue-title">
              <small>CRM · {workbenchMode === "new" ? "新客户" : "存量客户"}</small>
              <strong>{workbenchMode === "new" ? "新客户推进队列" : "老客户经营队列"}</strong>
            </div>
            <div className="customer-workbench__queue-tools" aria-label="队列工具">
              <button type="button" aria-label="列表设置" title="列表设置" aria-expanded={showQueueSettings} className={`cici-product-icon-button${showQueueSettings ? " is-active" : ""}`} onClick={() => setShowQueueSettings((value) => !value)}><Icon name="sliders" /></button>
            </div>
          </header>
          {showQueueSettings ? (
            <section className="customer-workbench__queue-settings" aria-label="列表显示设置">
              <label className="customer-workbench__queue-density"><input type="checkbox" checked={compactQueue} onChange={(event) => setCompactQueue(event.target.checked)} /><span>紧凑列表</span></label>
              <label><span>每页数量</span><select value={pageSize} onChange={(event) => { setPageSize(Number(event.target.value)); setPage(1); }}><option value="8">8</option><option value="12">12</option><option value="20">20</option></select></label>
              <button type="button" onClick={() => void reloadDetail()}><Icon name="refresh" />刷新数据</button>
            </section>
          ) : null}
          <label className="customer-workbench__search">
            <span aria-hidden><Icon name="search" /></span>
            <input
              ref={searchInputRef}
              value={query}
              onChange={(event) => { setQuery(event.target.value); setPage(1); }}
              placeholder="搜索客户名称 / 负责人 / 关键字"
              aria-label="搜索客户"
            />
          </label>
          <nav aria-label="客户筛选">
            {modeFilterOptions[workbenchMode].map(([key, label]) => (
              <button key={key} type="button" className={filter === key ? "is-active" : ""} onClick={() => { setFilter(key); setPage(1); }}>
                {label}{queueMeta.filterCounts[key] !== undefined ? <small>{queueMeta.filterCounts[key]}</small> : null}
              </button>
            ))}
          </nav>
          <div className="customer-workbench__sortline">
            <select value={sort} onChange={(event) => { setSort(event.target.value); setPage(1); }} aria-label="客户排序">
              {workbenchMode === "new" ? <option value="priority">推进优先</option> : <option value="risk">风险优先</option>}
              <option value="interaction">最近互动</option>
              <option value="health">健康度</option>
              {workbenchMode === "existing" ? <option value="renewal">续约日期</option> : null}
            </select>
            <span>共 {queueMeta.totalElements} 位客户</span>
          </div>
          <div className={`customer-workbench__accounts${compactQueue ? " is-compact" : ""}`}>
            {accounts.map((item) => (
              <button
                key={item.accountId}
                type="button"
                className={`customer-workbench-account${item.accountId === activeAccountId ? " is-active" : ""}`}
                onClick={() => setActiveAccountId(item.accountId)}
              >
                <span className={`customer-workbench-account__dot is-${item.segment.toLowerCase()}`} />
                <span className="customer-workbench-account__body">
                  <span className="customer-workbench-account__title">
                    <strong>{item.name}</strong>
                    <em className={queueStatusClass(item)}>{queueStatus(item)}</em>
                  </span>
                  <span className="customer-workbench-account__meta">
                    <small>{item.owner} · {item.stage}</small>
                    <time>{shortDate(item.updatedAt || "") || "今天 09:30"}</time>
                  </span>
                  <span className="customer-workbench-account__badges">
                    {workbenchMode === "new" ? <em>商机 {item.opportunityCount ?? 0}</em> : <em>健康 {item.healthScore}</em>}
                    {item.riskCount ? <em className="is-risk">{workbenchMode === "new" ? "风险信号" : "关系风险"} {item.riskCount}</em> : null}
                    {item.pendingRecommendationCount ? <em className="is-warn">{workbenchMode === "new" ? "未确认建议" : "经营动作"} {item.pendingRecommendationCount}</em> : null}
                  </span>
                  {item.lastInteraction ? <span className="customer-workbench-account__last">{item.lastInteraction}</span> : null}
                </span>
              </button>
            ))}
            {loading ? <p className="customer-workbench__muted">正在加载客户...</p> : null}
          </div>
          <footer className="customer-workbench__pager">
            <button type="button" disabled={page <= 1} onClick={() => setPage((value) => Math.max(1, value - 1))}>‹</button>
            <span>{queueMeta.totalPages ? page : 0} / {queueMeta.totalPages}</span>
            <button type="button" disabled={page >= queueMeta.totalPages} onClick={() => setPage((value) => value + 1)}>›</button>
          </footer>
        </aside>

        <main className="customer-workbench__main">
          <header className="customer-workbench__head">
            <div>
              <h2>{detail?.name || activeAccount?.name || "客户互动工作台"} <button type="button" className="customer-workbench__more-menu customer-workbench__copy-link cici-product-icon-button" aria-label="复制客户工作台链接" title="复制客户工作台链接" onClick={async () => {
                const link = new URL(window.location.href);
                if (!embedded) link.searchParams.set("aiApp", "customer-workbench");
                link.searchParams.set("accountId", activeAccountId);
                link.searchParams.set("mode", workbenchMode);
                await navigator.clipboard.writeText(link.toString());
                setNotice("客户工作台链接已复制。");
              }}><Icon name="link" /></button></h2>
              <p className="customer-workbench__entity-line">
                <em>Account</em>
                <button type="button" onClick={() => setActiveTab(workbenchMode === "new" ? "signals" : "renewal")}>Opportunity <b>{detail?.opportunityCount ?? activeAccount?.opportunityCount ?? 0}</b></button>
                <span>{detail?.owner || activeAccount?.owner || "负责人"}</span>
                <button type="button" className={detail?.followed ? "is-followed" : ""} onClick={async () => {
                  if (!activeAccountId) return;
                  try { await setCustomerFollowed(token, activeAccountId, !detail?.followed); await reloadDetail(); }
                  catch (error) { setNotice(error instanceof Error ? error.message : String(error)); }
                }}>{detail?.followed ? "已关注" : "关注"}</button>
                <span>最近互动：{shortDate(detail?.updatedAt || activeAccount?.updatedAt || "")}（{lifecycleSourceLabel(detail?.lastInteractionType || "CRM")}）</span>
              </p>
            </div>
            {!assistantOpen ? <button type="button" className="customer-workbench__open-assistant" onClick={() => { setAssistantExpanded(false); setAssistantOpen(true); }}><Icon name="bot" />打开 AI 助理</button> : null}
          </header>

        {!integration.ready ? <div className="customer-workbench__notice is-demo">{integration.message || "当前显示只读演示数据，不能写回 CRM。"}</div> : null}
        {notice ? <div className="customer-workbench__notice">{notice}</div> : null}

        <section className="customer-workbench__metrics" aria-label="客户指标">
          {workbenchMode === "new" ? (
            <>
              <Metric label="未确认建议" value={metricValue(detail, "pendingRecommendations", detail?.pendingRecommendationCount ?? 0)} suffix="" onClick={() => setActiveTab("recommendations")} />
              <Metric label="风险信号" value={metricValue(detail, "risks", detail?.riskCount ?? 0)} suffix="" onClick={() => setActiveTab("signals")} />
              <Metric label="下一步任务" value={metricValue(detail, "nextActions", detail?.nextActionCount ?? 0)} suffix="" onClick={() => setActiveTab("actions")} />
              <Metric label="最近互动" value={metricValue(detail, "interactions", detail?.timeline?.length ?? 0)} suffix="" onClick={() => setActiveTab("timeline")} />
            </>
          ) : (
            <>
              <Metric label="客户健康度" value={metricValue(detail, "health", detail?.healthScore ?? 0)} suffix="" onClick={() => setActiveTab("overview")} />
              <Metric label="续约倒计时" value={metricValue(detail, "renewalDays", detail?.renewalDays ?? -1) < 0 ? "待确认" : metricValue(detail, "renewalDays", detail?.renewalDays ?? -1)} suffix={metricValue(detail, "renewalDays", detail?.renewalDays ?? -1) < 0 ? "" : "天"} onClick={() => setActiveTab("renewal")} />
              <Metric label="未闭环问题" value={metricValue(detail, "openIssues", 0)} suffix="" onClick={() => setActiveTab("service")} />
              <Metric label="增购信号" value={metricValue(detail, "expansionSignals", 0)} suffix="" onClick={() => setActiveTab("renewal")} />
            </>
          )}
        </section>

        <nav className="customer-workbench__tabs" aria-label="客户详情视图">
          {modeTabs[workbenchMode].map(([key, label]) => (
            <button key={key} type="button" className={activeTab === key ? "is-active" : ""} onClick={() => setActiveTab(key)}>
              {label}
            </button>
          ))}
        </nav>

        <section className="customer-workbench__content">
          {activeTab === "overview" ? <Overview detail={detail} mode={workbenchMode} onAction={handleRecommendation} onFeedback={handleRecommendationFeedback} onNotice={setNotice} onOpenTab={setActiveTab} /> : null}
          {activeTab === "timeline" ? <Timeline detail={detail} /> : null}
          {activeTab === "signals" ? <NewCustomerPanel detail={detail} /> : null}
          {activeTab === "service" ? <ExistingCustomerPanel detail={detail} focus="service" /> : null}
          {activeTab === "value" ? <ExistingCustomerPanel detail={detail} focus="value" /> : null}
          {activeTab === "renewal" ? <ExistingCustomerPanel detail={detail} focus="renewal" /> : null}
          {activeTab === "relationship" ? <ExistingCustomerPanel detail={detail} focus="relationship" /> : null}
          {activeTab === "recommendations" ? (
            <div ref={recommendationRef}>
              <Recommendations detail={detail} onAction={handleRecommendation} onFeedback={handleRecommendationFeedback} onNotice={setNotice} />
            </div>
          ) : null}
          {activeTab === "actions" ? <NextActionPanel detail={detail} onAction={handleRecommendation} /> : null}
        </section>
      </main>

      {assistantOpen ? <aside className="customer-workbench__assistant" aria-label="AI 客户助理">
        <header>
          <div>
            <strong>AI 客户助理</strong>
          </div>
          <div className="customer-workbench__assistant-tools">
            <button
              type="button"
              aria-label={assistantExpanded ? "恢复 AI 助理默认宽度" : "展开 AI 助理"}
              title={assistantExpanded ? "恢复默认宽度" : "展开 AI 助理"}
              aria-pressed={assistantExpanded}
              className={`cici-product-icon-button${assistantExpanded ? " is-active" : ""}`}
              onClick={() => setAssistantExpanded((value) => !value)}
            ><Icon name={assistantExpanded ? "panelRestore" : "panelExpand"} /></button>
            <button type="button" className="cici-product-icon-button" aria-label="关闭 AI 助理" title="关闭" onClick={() => { setAssistantExpanded(false); setAssistantOpen(false); }}><Icon name="close" /></button>
          </div>
        </header>
        <div className="customer-workbench__chat" ref={assistantChatRef}>
          <div className="customer-workbench__dayline">今天</div>
          {assistantMessages.map((message, index) => (
            <div key={`${message.time}-${index}`} className={`customer-workbench-message is-${message.role}`}>
              {message.role === "assistant" ? <span className="customer-workbench-message__avatar" aria-hidden><Icon name="bot" /></span> : null}
              <div className={`customer-workbench-message__body${message.busy ? " is-streaming" : ""}`}>
                <div className="customer-workbench-message__content">
                  {message.role === "assistant" ? (
                    message.busy && !message.text ? (
                      <span className="customer-workbench-message__status" role="status"><LoaderCircle aria-hidden />{message.phase || "正在处理..."}</span>
                    ) : <ChatMarkdown content={message.text} busy={message.busy} />
                  ) : <p>{message.text}</p>}
                </div>
                <span>{message.time}</span>
              </div>
              {message.role === "user" ? <span className="customer-workbench-message__avatar is-user" aria-hidden>{userName.trim().slice(0, 1) || "我"}</span> : null}
            </div>
          ))}
        </div>
        <div className="customer-workbench__quick-actions">
          <button type="button" disabled={assistantReplying} onClick={() => void submitAssistant("为当前客户生成跟进任务建议")}>生成跟进任务</button>
          <button type="button" disabled={assistantReplying} onClick={() => void submitAssistant("查看当前客户的风险信号")}>查看风险</button>
          <button type="button" disabled={assistantReplying} onClick={() => setInteractionEditorOpen(true)}>整理互动记录</button>
          <button type="button" disabled={assistantReplying} onClick={() => void submitAssistant("切换到下一个客户")}>切换下个客户</button>
        </div>
        <div className="customer-workbench__composer">
          <textarea ref={composerInputRef} value={assistantInput} onChange={(event) => setAssistantInput(event.target.value)} placeholder="输入问题或指令..." />
          <div className="customer-workbench__composer-actions">
            <button
              type="button"
              className={`customer-workbench__composer-icon${listening ? " is-recording" : ""}`}
              onClick={() => void startVoice()}
              aria-label={listening ? "停止语音输入" : "语音输入"}
              disabled={!speechSupported || assistantReplying}
            >
              <Icon name="mic" />
            </button>
            <button type="button" className="customer-workbench__send" disabled={assistantReplying || !assistantInput.trim()} onClick={() => void submitAssistant()} aria-label={assistantReplying ? "正在回复" : "发送"} title={assistantReplying ? "正在回复" : "发送"}><Icon name="send" /></button>
          </div>
        </div>
        <p className="customer-workbench__ai-note">AI 生成内容仅供参考，请结合实际情况判断</p>
      </aside> : null}
      </div>
      {editingRecommendation ? (
        <RecommendationEditor key={editingRecommendation.recommendationId} item={editingRecommendation}
          onClose={() => setEditingRecommendation(null)} onSave={saveRecommendationEdit} />
      ) : null}
      {interactionEditorOpen ? (
        <InteractionEditor onClose={() => setInteractionEditorOpen(false)} onSave={async (draft) => {
          if (!activeAccountId) return;
          try {
            const saved = await saveCustomerInteraction(token, activeAccountId, draft);
            setInteractionEditorOpen(false);
            setNotice(saved.deduplicated ? "该互动记录已存在，未重复保存。" : "互动记录已确认并进入时间线。");
            await reloadDetail();
            setActiveTab("timeline");
          } catch (error) {
            setNotice(error instanceof Error ? error.message : String(error));
          }
        }} />
      ) : null}
    </section>
  );
}

function Metric({ label, value, suffix, onClick }: { label: string; value: number | string; suffix: string; onClick: () => void }) {
  return (
    <button type="button" onClick={onClick}>
      <i aria-hidden><Icon name={metricIconName(label)} /></i>
      <span>{label}</span>
      <strong>{value}<small>{suffix}</small></strong>
      <em aria-hidden>›</em>
    </button>
  );
}

function Overview({
  detail,
  mode,
  onAction,
  onFeedback,
  onNotice,
  onOpenTab,
}: {
  detail: CustomerWorkbenchDetail | null;
  mode: WorkbenchMode;
  onAction: (item: CustomerRecommendation, action: RecommendationAction) => void;
  onFeedback: (item: CustomerRecommendation, rating: "HELPFUL" | "NOT_HELPFUL") => void;
  onNotice: (message: string) => void;
  onOpenTab: (tab: DetailTab) => void;
}) {
  return (
    <div className="customer-workbench-overview-wrap">
      <div className="customer-workbench-overview">
        <section className="customer-workbench-panel customer-workbench-panel--timeline">
          <header>
            <h3>{mode === "new" ? "新客户互动时间线" : "老客户互动时间线"}</h3>
            <button type="button" className="customer-workbench__panel-filter" onClick={() => onOpenTab("timeline")}><span>全部类型</span><Icon name="chevronDown" /></button>
          </header>
          <TimelineCards detail={detail} compact />
          <button type="button" className="customer-workbench__more" onClick={() => onOpenTab("timeline")}>查看全部互动记录 ›</button>
        </section>
        <section className="customer-workbench-panel customer-workbench-panel--recommendations">
          <header>
            <h3>{mode === "new" ? "CRM 落地建议" : "老客户经营动作"}（{detail?.recommendations?.length ?? 0}）</h3>
            <button type="button" className="customer-workbench__panel-filter" onClick={() => onOpenTab("recommendations")}><span>{mode === "new" ? "全部建议" : "按影响排序"}</span><Icon name="chevronDown" /></button>
          </header>
          <Recommendations detail={detail} onAction={onAction} onFeedback={onFeedback} onNotice={onNotice} compact />
          <button type="button" className="customer-workbench__more" onClick={() => onOpenTab("recommendations")}>{mode === "new" ? "查看全部建议" : "查看全部经营动作"} ›</button>
        </section>
      </div>
      <WorkbenchBottomPanel detail={detail} mode={mode} />
    </div>
  );
}

function Timeline({ detail }: { detail: CustomerWorkbenchDetail | null }) {
  const [source, setSource] = useState("all");
  const events = detail?.timeline ?? [];
  return (
    <div className="customer-workbench-timeline-page">
      <header><h3>互动时间线</h3><select aria-label="互动来源" value={source} onChange={(event) => setSource(event.target.value)}>
        <option value="all">全部类型</option>
        <option value="wechat">微信</option><option value="phone">电话</option><option value="meeting">会议</option><option value="task">CRM 任务</option>
      </select></header>
      <TimelineCards detail={{ ...detail, timeline: source === "all" ? events : events.filter((item) => {
        const normalized = item.sourceType.toUpperCase();
        if (source === "wechat") return normalized.includes("WECHAT");
        if (source === "phone") return normalized.includes("PHONE");
        if (source === "meeting") return normalized.includes("MEETING") || normalized.includes("EVENT");
        return normalized.includes("TASK");
      }) } as CustomerWorkbenchDetail} />
    </div>
  );
}

function TimelineCards({ detail, compact = false }: { detail: CustomerWorkbenchDetail | null; compact?: boolean }) {
  return (
    <div className={`customer-workbench-timeline${compact ? " is-compact" : ""}`}>
      {(detail?.timeline ?? []).slice(0, compact ? 5 : undefined).map((item) => (
        <article key={item.eventId}>
          <time>{shortDate(item.occurredAt)}</time>
          <span className={`customer-workbench-timeline__icon is-${lifecycleSourceLabel(item.sourceType)}`} aria-hidden>
            <Icon name={sourceIconName(item.sourceType)} />
          </span>
          <div>
            <strong>{item.subject}</strong>
            <p>{item.summary}</p>
            <span>来源：{lifecycleSourceLabel(item.sourceType)} · {item.lifecycleArea}</span>
            {item.intentTags?.[0] ? <em>{item.intentTags[0]}</em> : null}
          </div>
        </article>
      ))}
    </div>
  );
}

function NewCustomerPanel({ detail }: { detail: CustomerWorkbenchDetail | null }) {
  const score = detail?.progressScore ?? 0;
  const signals = detail?.newCustomerSignals ?? [];
  const actions = detail?.nextActions ?? [];
  const gaps = (detail?.signals ?? []).filter((item) => item.type.includes("GAP") || item.type.includes("OVERDUE"));
  return (
    <div className="customer-workbench-signals">
      <header>
        <div>
          <h3>新客户推进</h3>
          <p>围绕需求、预算、决策链和商机动作判断推进质量。</p>
        </div>
        <strong>{score}<small>分</small></strong>
      </header>
      <div className="customer-workbench-stage">
        {["初步接触", "需求确认", "方案沟通", "评估决策", "商机推进"].map((stage, index) => (
          <span key={stage} className={index <= Math.min(3, Math.floor(score / 24)) ? "is-done" : ""}>{stage}</span>
        ))}
      </div>
      <div className="customer-workbench-signal-grid">
        <section>
          <h4>推进信号</h4>
          <List items={signals} empty="暂无明确信号" />
        </section>
        <section>
          <h4>建议动作</h4>
          <List items={actions} empty="暂无建议动作" />
        </section>
        <section>
          <h4>CRM 补齐项</h4>
          <List items={gaps.map((item) => item.detail)} empty="当前 CRM 关键字段无明显缺口" />
        </section>
      </div>
    </div>
  );
}

function ExistingCustomerPanel({
  detail,
  focus = "service",
}: {
  detail: CustomerWorkbenchDetail | null;
  focus?: "service" | "value" | "renewal" | "relationship";
}) {
  const score = detail?.healthScore ?? 0;
  const focusCopy = {
    service: ["服务问题", "聚焦未闭环工单、服务压力和异常反馈。"],
    value: ["价值兑现", "对照客户承诺、使用反馈和业务收益沉淀复盘材料。"],
    renewal: ["续约增购", "关注续约倒计时、合同风险、增购触发信号。"],
    relationship: ["关系地图", "检查关键人覆盖、角色缺口和沟通频率。"],
  }[focus];
  const focusItems: Array<{ title: string; detail: string; meta: string }> = focus === "service"
    ? (detail?.serviceIssues ?? []).map((item) => ({ title: item.title || item.number, detail: item.description || "CRM 个案未填写问题描述", meta: `${item.status || "待处理"} · ${item.priority || "普通"}` }))
    : focus === "value"
      ? (detail?.valueItems ?? []).map((item) => ({ title: item.title, detail: `金额 ${Number(item.amount || 0).toLocaleString("zh-CN")}`, meta: `${item.source} · ${item.status || "状态待确认"}` }))
      : focus === "renewal"
        ? [...(detail?.renewal?.contracts ?? []).map((item) => ({ title: item.title, detail: `合同状态：${item.status || "待确认"}`, meta: `距最近到期 ${detail?.renewal?.days ?? -1} 天` })),
            ...(detail?.renewal?.opportunities ?? []).map((item) => ({ title: String(item.name || "业务机会"), detail: String(item.nextStep || "下一步待补齐"), meta: String(item.stage || "阶段待确认") }))]
        : (detail?.relationshipMap ?? []).map((item) => ({ title: item.name, detail: `${item.title || "职务待补"} · ${item.role || "角色待补"}`, meta: item.lastContactAt ? `最近联系 ${shortDate(item.lastContactAt)}` : "最近联系待补" }));
  return (
    <div className="customer-workbench-signals">
      <header>
        <div>
          <h3>{focusCopy[0]}</h3>
          <p>{focusCopy[1]}</p>
        </div>
        <strong>{score}<small>分</small></strong>
      </header>
      <div className="customer-workbench-health-grid">
        <strong>健康度<small>{metricValue(detail, "health", score)} 分</small></strong>
        <strong>未闭环服务<small>{metricValue(detail, "openIssues", 0)} 个</small></strong>
        <strong>增购机会<small>{metricValue(detail, "expansionSignals", 0)} 个</small></strong>
        <strong>关系覆盖<small>{detail?.relationshipMap?.length ?? 0} 人</small></strong>
      </div>
      <div className="customer-workbench-record-grid">
        {focusItems.map((item, index) => <article key={`${item.title}-${index}`}><strong>{item.title}</strong><p>{item.detail}</p><span>{item.meta}</span></article>)}
        {!focusItems.length ? <p className="customer-workbench__muted">当前用户可见的 CRM 数据中暂无相关记录。</p> : null}
      </div>
    </div>
  );
}

function WorkbenchBottomPanel({ detail, mode }: { detail: CustomerWorkbenchDetail | null; mode: WorkbenchMode }) {
  const items = (detail?.signals ?? []).filter((item) => mode === "new" ? item.mode === "NEW" : item.mode === "EXISTING").slice(0, 3);
  return (
    <section className="customer-workbench-bottom-panel" aria-label={mode === "new" ? "推进关键项" : "服务与关系预警"}>
      <header>
        <h3>{mode === "new" ? "推进关键项" : "服务与关系预警"}</h3>
        <span>AI 从工单、会议、微信和 CRM 更新中提取</span>
      </header>
      <div>
        {items.map((item) => (
          <article key={item.type}>
            <strong>{item.title}</strong>
            <p>{item.detail}</p>
            <em>{item.severity === "HIGH" ? "高优先级" : item.severity === "MEDIUM" ? "需关注" : "信息"}</em>
          </article>
        ))}
        {!items.length ? <p className="customer-workbench__muted">当前没有需要提示的关键项。</p> : null}
      </div>
      {detail?.summary ? <p className="customer-workbench-bottom-panel__summary">{detail.summary}</p> : null}
    </section>
  );
}

function NextActionPanel({ detail, onAction }: { detail: CustomerWorkbenchDetail | null; onAction: (item: CustomerRecommendation, action: RecommendationAction) => void }) {
  const taskRecommendation = detail?.recommendations?.find((item) => item.type === "CREATE_TASK" && item.status !== "APPLIED" && item.status !== "DISMISSED");
  return (
    <div className="customer-workbench-actions">
      {(detail?.nextActions ?? []).map((item, index) => (
        <article key={`${item}-${index}`}>
          <strong>{item}</strong>
          <p>建议负责人在 24 小时内确认并同步到 CRM 任务。</p>
          <button type="button" disabled={!taskRecommendation} onClick={() => taskRecommendation && onAction(taskRecommendation, "accept")}>形成待确认任务</button>
        </article>
      ))}
      {detail?.nextActions?.length ? null : <p className="customer-workbench__muted">暂无下一步行动。</p>}
    </div>
  );
}

function Recommendations({
  detail,
  onAction,
  onFeedback,
  onNotice,
  compact = false,
}: {
  detail: CustomerWorkbenchDetail | null;
  onAction: (item: CustomerRecommendation, action: RecommendationAction) => void;
  onFeedback: (item: CustomerRecommendation, rating: "HELPFUL" | "NOT_HELPFUL") => void;
  onNotice: (message: string) => void;
  compact?: boolean;
}) {
  const items = detail?.recommendations ?? [];
  const [expandedEvidenceId, setExpandedEvidenceId] = useState("");
  return (
    <div className={`customer-workbench-recommendations${compact ? " is-compact" : ""}`}>
      {items.slice(0, compact ? 4 : undefined).map((item, index) => (
        <article key={item.recommendationId}>
          <header>
            <i className={`is-${recommendationIconName(item.type, index)}`} aria-hidden>
              <Icon name={recommendationIconName(item.type, index)} />
            </i>
            <div>
              <strong>{item.title}</strong>
              <span>{item.rationale}</span>
            </div>
          </header>
          <p><b>置信度</b>{formatConfidence(item.confidence)} <span>依据：{item.evidence?.length ? String(item.evidence.length) + " 条 CRM 事实" : "当前客户 CRM 数据"}</span></p>
          {!compact && item.evidence?.length ? <div className="customer-workbench-recommendation__evidence">
            <button type="button" onClick={() => setExpandedEvidenceId((current) => current === item.recommendationId ? "" : item.recommendationId)}>
              {expandedEvidenceId === item.recommendationId ? "收起依据" : `查看依据 (${item.evidence.length})`}
            </button>
            {expandedEvidenceId === item.recommendationId ? <ul>{item.evidence.map((evidence, evidenceIndex) => <li key={`${item.recommendationId}-${evidenceIndex}`}>{evidenceLabel(evidence)}</li>)}</ul> : null}
          </div> : null}
          {item.lastErrorMessage ? <p className="customer-workbench-recommendation__error">上次执行失败：{item.lastErrorMessage}</p> : null}
          <footer>
            {item.status === "PENDING" ? <button type="button" onClick={() => onAction(item, "accept")}><Icon name="check" />采纳</button> : null}
            {item.status === "ACCEPTED" ? <button type="button" onClick={() => onAction(item, "confirm")}><Icon name="check" />确认</button> : null}
            {item.status === "CONFIRMED" || item.status === "FAILED" ? <button type="button" onClick={() => onAction(item, "apply")}><Icon name="check" />{item.status === "FAILED" ? "重试" : "写入 CRM"}</button> : null}
            {item.status === "APPLYING" ? <button type="button" disabled><Icon name="check" />执行中</button> : null}
            {item.status === "APPLIED" ? <button type="button" disabled><Icon name="check" />已写入</button> : null}
            {item.status !== "APPLIED" && item.status !== "APPLYING" && item.status !== "DISMISSED" ? <button type="button" onClick={() => onAction(item, "edit")}><Icon name="edit" />修改</button> : null}
            {item.status !== "APPLIED" && item.status !== "APPLYING" && item.status !== "DISMISSED" ? <button type="button" onClick={() => onAction(item, "dismiss")}><Icon name="close" />忽略</button> : null}
            {item.status === "DISMISSED" ? <span>已忽略</span> : null}
          </footer>
          {!compact ? <div className="customer-workbench-recommendation__feedback">
            <span>这条建议是否有帮助？</span>
            <button type="button" className={item.feedback?.rating === "HELPFUL" ? "is-active" : ""} onClick={() => onFeedback(item, "HELPFUL")}>有帮助</button>
            <button type="button" className={item.feedback?.rating === "NOT_HELPFUL" ? "is-active" : ""} onClick={() => onFeedback(item, "NOT_HELPFUL")}>需改进</button>
          </div> : null}
        </article>
      ))}
      {!items.length ? <p className="customer-workbench__muted">当前没有 CRM 落地建议。</p> : null}
    </div>
  );
}

function RecommendationEditor({ item, onClose, onSave }: {
  item: CustomerRecommendation;
  onClose: () => void;
  onSave: (draft: Partial<CustomerRecommendation>) => Promise<void>;
}) {
  const [title, setTitle] = useState(item.title);
  const [rationale, setRationale] = useState(item.rationale);
  const targetObject = item.targetObject || (item.type.includes("OPPORTUNITY") ? "Opportunity" : "Task");
  const [recordName, setRecordName] = useState(String(item.crmPayload?.name || item.crmPayload?.subject || item.title));
  const [expiredate, setExpiredate] = useState(String(item.crmPayload?.expiredate || ""));
  const [stage, setStage] = useState(String(item.crmPayload?.jieduan || "1-发现机会"));
  const [nextStep, setNextStep] = useState(String(item.crmPayload?.xyb || item.rationale));
  return (
    <div className="customer-workbench-dialog" role="dialog" aria-modal="true" aria-labelledby="recommendation-editor-title">
      <form onSubmit={(event) => {
        event.preventDefault();
        void onSave({
          title,
          rationale,
          targetObject: item.targetObject,
          targetRecordId: item.targetRecordId,
          crmPayload: targetObject === "Opportunity"
            ? { ...item.crmPayload, name: recordName, jieduan: stage, xyb: nextStep }
            : { ...item.crmPayload, subject: recordName, ...(expiredate ? { expiredate } : {}) },
        });
      }}>
        <header>
          <h3 id="recommendation-editor-title">修改 CRM 落地建议</h3>
          <button type="button" className="cici-product-icon-button" onClick={onClose} aria-label="关闭"><Icon name="close" /></button>
        </header>
        <label>建议标题<input value={title} onChange={(event) => setTitle(event.target.value)} required /></label>
        <label>建议依据<textarea value={rationale} onChange={(event) => setRationale(event.target.value)} required /></label>
        <label>{targetObject === "Opportunity" ? "业务机会名称" : "CRM 任务主题"}<input value={recordName} onChange={(event) => setRecordName(event.target.value)} required /></label>
        {targetObject === "Opportunity" ? <>
          <label>业务机会阶段<input value={stage} onChange={(event) => setStage(event.target.value)} required /></label>
          <label>下一步<textarea value={nextStep} onChange={(event) => setNextStep(event.target.value)} required /></label>
        </> : <label>到期日期<input type="date" value={expiredate} onChange={(event) => setExpiredate(event.target.value)} /></label>}
        <footer><button type="button" onClick={onClose}>取消</button><button type="submit">保存修改</button></footer>
      </form>
    </div>
  );
}

function InteractionEditor({ onClose, onSave }: {
  onClose: () => void;
  onSave: (draft: { sourceType: "WECHAT" | "PHONE" | "MEETING" | "CUSTOMER_FEEDBACK"; subject: string; content: string; occurredAt: string }) => Promise<void>;
}) {
  const [sourceType, setSourceType] = useState<"WECHAT" | "PHONE" | "MEETING" | "CUSTOMER_FEEDBACK">("WECHAT");
  const [subject, setSubject] = useState("");
  const [content, setContent] = useState("");
  const [occurredAt, setOccurredAt] = useState(() => {
    const now = new Date();
    now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
    return now.toISOString().slice(0, 16);
  });
  return (
    <div className="customer-workbench-dialog" role="dialog" aria-modal="true" aria-labelledby="interaction-editor-title">
      <form onSubmit={(event) => {
        event.preventDefault();
        void onSave({ sourceType, subject, content, occurredAt: new Date(occurredAt).toISOString() });
      }}>
        <header><h3 id="interaction-editor-title">整理互动记录</h3><button type="button" className="cici-product-icon-button" onClick={onClose} aria-label="关闭"><Icon name="close" /></button></header>
        <label>互动来源<select value={sourceType} onChange={(event) => setSourceType(event.target.value as typeof sourceType)}>
          <option value="WECHAT">微信</option><option value="PHONE">电话</option><option value="MEETING">会议</option><option value="CUSTOMER_FEEDBACK">客户反馈</option>
        </select></label>
        <label>发生时间<input type="datetime-local" value={occurredAt} onChange={(event) => setOccurredAt(event.target.value)} required /></label>
        <label>主题<input value={subject} onChange={(event) => setSubject(event.target.value)} placeholder="可留空，由系统按来源生成" /></label>
        <label>互动内容<textarea value={content} onChange={(event) => setContent(event.target.value)} minLength={10} maxLength={10000} required placeholder="粘贴微信聊天、电话纪要、会议摘要或客户反馈" /></label>
        <footer><button type="button" onClick={onClose}>取消</button><button type="submit">确认保存</button></footer>
      </form>
    </div>
  );
}

function List({ items, empty }: { items: string[]; empty: string }) {
  if (!items.length) return <p className="customer-workbench__muted">{empty}</p>;
  return (
    <ul>
      {items.map((item) => <li key={item}>{item}</li>)}
    </ul>
  );
}
