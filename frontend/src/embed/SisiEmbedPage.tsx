import {
  ArrowUp,
  Check,
  ChevronRight,
  CircleStop,
  FileText,
  History,
  Image as ImageIcon,
  Link2,
  LoaderCircle,
  Maximize2,
  Mic,
  Paperclip,
  PanelLeftClose,
  ShieldCheck,
  Sparkles,
  SquarePen,
  Wrench,
  X,
} from "lucide-react";
import { ChangeEvent, KeyboardEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import ChatMarkdown from "../components/ChatMarkdown";
import { useAsrVoiceInput } from "../shared/useAsrVoiceInput";
import { exactConfirmation } from "./sisiEmbedContract";
import { streamDeltaText } from "./sisiEmbedStream";
import { resolveSisiTheme } from "./sisiEmbedTheme";
import "./sisi-embed.css";

type Envelope<T> = { success?: boolean; data?: T; message?: string };
type HostMessage = { source?: string; type?: string; token?: string; payload?: Record<string, unknown> };
type SessionView = {
  sessionId: string;
  productName: string;
  agentId: string;
  source?: string;
  parentOrigin: string;
  permissions: string[];
  context?: Record<string, unknown>;
  recordName?: string;
  customerName?: string;
  themeCode?: string;
};
type Attachment = { id: string; name: string; contentType: string; sizeBytes: number; status: string };
type Evidence = { id: string; title: string; detail: string; kind: "source" | "tool" | "security"; href?: string };
type ChatMessage = {
  id: string;
  role: "user" | "assistant";
  content: string;
  createdAt?: string;
  attachments?: Attachment[];
  busy?: boolean;
  confirmation?: string;
};

const APP_CODE = "sisi";
const SDK_SOURCE = "agentcici-sisi-embed";
const ACCEPT = ".png,.jpg,.jpeg,.webp,.txt,.md,.csv,.json,.pdf,.docx";
const SUGGESTIONS = ["总结当前客户进展", "查找最近一次沟通", "生成下一步跟进建议"];

function parseHashToken() {
  const hash = new URLSearchParams(window.location.hash.replace(/^#/, ""));
  const value = hash.get("token")?.trim() ?? "";
  if (value) history.replaceState(null, "", `${window.location.pathname}${window.location.search}`);
  return value;
}

function decodeToken(token: string): Record<string, unknown> {
  try {
    const payload = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    const padded = payload.padEnd(Math.ceil(payload.length / 4) * 4, "=");
    const bytes = Uint8Array.from(atob(padded), (char) => char.charCodeAt(0));
    return JSON.parse(new TextDecoder().decode(bytes)) as Record<string, unknown>;
  } catch {
    return {};
  }
}

function text(value: unknown) {
  return typeof value === "string" ? value : value == null ? "" : String(value);
}

function previewSession(): SessionView {
  return {
    sessionId: "preview-sisi",
    productName: "思思",
    agentId: "customer-success-agent",
    parentOrigin: window.location.origin,
    permissions: ["chat:read", "chat:write", "attachment:write", "voice:input"],
    recordName: "云驰科技 · 续约机会",
    customerName: "云驰科技",
    themeCode: "gilded",
    context: { objectType: "Opportunity", stage: "商务谈判", owner: "王可", amount: "¥ 680,000" },
  };
}

const previewMessages: ChatMessage[] = [
  {
    id: "preview-user",
    role: "user",
    content: "帮我梳理一下这个客户的续约风险，并给出今天可以执行的动作。",
    createdAt: new Date().toISOString(),
  },
  {
    id: "preview-assistant",
    role: "assistant",
    content: "当前续约风险为**中等偏高**。主要信号是决策人两周未参与沟通、报价仍停留在上一版本，但使用活跃度保持稳定。\n\n建议今天完成三件事：\n1. 向业务负责人确认预算审批节点；\n2. 基于最新使用数据补充价值回顾；\n3. 约定本周内的决策人沟通。",
    createdAt: new Date().toISOString(),
  },
];

export default function SisiEmbedPage() {
  const mode = useMemo(() => new URLSearchParams(window.location.search).get("mode") === "float" ? "float" : "page", []);
  const isDevPreview = import.meta.env.DEV && new URLSearchParams(window.location.search).get("preview") === "1";
  const [token, setToken] = useState(parseHashToken);
  const [session, setSession] = useState<SessionView | null>(isDevPreview ? previewSession() : null);
  const [messages, setMessages] = useState<ChatMessage[]>(isDevPreview ? previewMessages : []);
  const [evidence, setEvidence] = useState<Evidence[]>(isDevPreview ? [
    { id: "source-1", kind: "source", title: "CRM 使用概览", detail: "近 30 天活跃度 82%" },
    { id: "tool-1", kind: "tool", title: "客户沟通记录", detail: "已读取最近 6 条记录" },
    { id: "security-1", kind: "security", title: "权限边界", detail: "只读分析，无外部写入" },
  ] : []);
  const [draft, setDraft] = useState("");
  const [attachments, setAttachments] = useState<Attachment[]>([]);
  const [loading, setLoading] = useState(!isDevPreview);
  const [sending, setSending] = useState(false);
  const [notice, setNotice] = useState(isDevPreview ? "原型预览 · 未连接真实业务数据" : "等待安全身份接入…");
  const [leftOpen, setLeftOpen] = useState(true);
  const [rightOpen, setRightOpen] = useState(true);
  const listRef = useRef<HTMLDivElement | null>(null);
  const fileRef = useRef<HTMLInputElement | null>(null);
  const tokenRef = useRef(token);
  const parentOriginRef = useRef("");
  const { listening, speechSupported, start: startAsr, stop: stopAsr, abort: abortAsr } = useAsrVoiceInput();

  useEffect(() => { tokenRef.current = token; }, [token]);
  useEffect(() => { parentOriginRef.current = session?.parentOrigin ?? text(decodeToken(token).parentOrigin); }, [session, token]);

  const postHost = useCallback((type: string, payload: Record<string, unknown> = {}) => {
    const origin = parentOriginRef.current;
    if (!origin || window.parent === window) return;
    window.parent.postMessage({ source: SDK_SOURCE, type, payload, sessionId: session?.sessionId ?? "", timestamp: new Date().toISOString() }, origin);
  }, [session?.sessionId]);

  const api = useCallback(async <T,>(path: string, init: RequestInit = {}) => {
    const activeToken = tokenRef.current;
    const response = await fetch(path, {
      ...init,
      headers: {
        Authorization: `Bearer ${activeToken}`,
        ...(init.body && !(init.body instanceof FormData) ? { "Content-Type": "application/json" } : {}),
        ...(init.headers ?? {}),
      },
    });
    const json = await response.json().catch(() => null) as Envelope<T> | null;
    if (!response.ok || !json?.success) {
      if (response.status === 401) postHost("embed:token-required", { reason: "expired" });
      throw new Error(json?.message || `请求失败（${response.status}）`);
    }
    return json.data as T;
  }, [postHost]);

  useEffect(() => {
    const onMessage = (event: MessageEvent) => {
      if (event.source !== window.parent) return;
      const message = event.data as HostMessage | null;
      if (!message || message.source !== SDK_SOURCE) return;
      const nextToken = text(message.token || message.payload?.token).trim();
      if (message.type === "host:init" || message.type === "host:update-token") {
        if (!nextToken) return;
        const claims = decodeToken(nextToken);
        if (text(claims.parentOrigin) !== event.origin || text(claims.appCode) !== APP_CODE) {
          setNotice("身份来源校验失败");
          return;
        }
        if (nextToken !== tokenRef.current) {
          setSession(null);
          setMessages([]);
          setEvidence([]);
          setAttachments([]);
          setLoading(true);
        }
        setToken(nextToken);
      } else if (message.type === "host:focus") {
        document.querySelector<HTMLTextAreaElement>(".sisi-composer__input")?.focus();
      } else if (message.type === "host:request-close") {
        postHost("embed:close");
      }
    };
    window.addEventListener("message", onMessage);
    postHost("embed:frame-ready", { mode });
    return () => window.removeEventListener("message", onMessage);
  }, [mode, postHost]);

  useEffect(() => {
    if (!token || isDevPreview) return;
    let cancelled = false;
    const initialize = async () => {
      setLoading(true);
      try {
        const next = await api<SessionView>(`/embed/v1/apps/${APP_CODE}/sessions`, { method: "POST" });
        if (cancelled) return;
        parentOriginRef.current = next.parentOrigin;
        setSession(next);
        const history = await api<{ messages: Array<Omit<ChatMessage, "id">> }>(
          `/embed/v1/apps/${APP_CODE}/sessions/${encodeURIComponent(next.sessionId)}/messages`);
        if (cancelled) return;
        const historyMessages = history.messages.map((item, index) => ({ ...item, id: `history-${index}-${item.createdAt ?? ""}` }));
        const welcomeMessage = text(next.context?.welcomeMessage).trim();
        setMessages(historyMessages.length > 0 || !welcomeMessage ? historyMessages : [{
          id: "widget-welcome",
          role: "assistant",
          content: welcomeMessage,
          createdAt: new Date().toISOString(),
        }]);
        setNotice(next.source === "website" ? "已建立安全访客会话" : "已通过 CloudCC 身份校验");
        postHost("embed:ready", { sessionId: next.sessionId, agentId: next.agentId });
      } catch (error) {
        if (!cancelled) {
          const message = error instanceof Error ? error.message : String(error);
          setNotice(message);
          postHost("embed:error", { message });
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    void initialize();
    return () => { cancelled = true; };
  }, [api, isDevPreview, postHost, token]);

  useEffect(() => {
    const element = listRef.current;
    if (element) element.scrollTo({ top: element.scrollHeight, behavior: "smooth" });
  }, [messages]);

  useEffect(() => () => abortAsr(), [abortAsr]);

  const appendEvidence = (item: Evidence) => {
    setEvidence((current) => current.some((entry) => entry.id === item.id) ? current : [item, ...current].slice(0, 12));
  };

  const consumeStream = async (response: Response, assistantId: string) => {
    if (!response.body) throw new Error("浏览器不支持流式响应");
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    while (true) {
      const { done, value } = await reader.read();
      buffer += decoder.decode(value, { stream: !done });
      const blocks = buffer.split("\n\n");
      buffer = blocks.pop() ?? "";
      for (const block of blocks) {
        let eventName = "message";
        const dataLines: string[] = [];
        for (const line of block.split("\n")) {
          if (line.startsWith("event:")) eventName = line.slice(6).trim();
          if (line.startsWith("data:")) dataLines.push(line.slice(5).trim());
        }
        if (!dataLines.length) continue;
        const raw = dataLines.join("\n");
        let payload: unknown = raw;
        try { payload = JSON.parse(raw); } catch { /* delta can be plain text */ }
        if (eventName === "delta") {
          const delta = streamDeltaText(payload);
          setMessages((current) => current.map((item) => item.id === assistantId ? { ...item, content: item.content + delta } : item));
        } else if (eventName === "tool_call" || eventName === "tool_result") {
          const details = payload as Record<string, unknown>;
          appendEvidence({
            id: `${eventName}-${text(details.callId || details.id || details.name)}-${Date.now()}`,
            kind: "tool",
            title: text(details.displayName || details.name || details.toolName || "智能工具"),
            detail: eventName === "tool_call" ? "正在安全执行" : "执行完成并已回读",
          });
        } else if (eventName === "phase") {
          const phase = payload as Record<string, unknown>;
          const sourceItems = ((phase.sources ?? (phase.payload as Record<string, unknown>)?.sources) as Array<Record<string, unknown>> | undefined) ?? [];
          sourceItems.forEach((source, index) => appendEvidence({
            id: `source-${text(source.id || source.url || index)}`,
            kind: "source",
            title: text(source.title || source.name || "知识来源"),
            detail: text(source.knowledgeBaseName || source.snippet || "已引用可信知识"),
            href: text(source.url) || undefined,
          }));
        } else if (eventName === "error") {
          throw new Error(text((payload as Record<string, unknown>)?.message || payload));
        }
      }
      if (done) break;
    }
  };

  const send = async (override?: string) => {
    const question = (override ?? draft).trim();
    if (!question || sending || !session) return;
    const selected = attachments.map((item) => item.id);
    const userMessage: ChatMessage = { id: crypto.randomUUID(), role: "user", content: question, attachments, createdAt: new Date().toISOString() };
    const assistantId = crypto.randomUUID();
    setMessages((current) => [...current, userMessage, { id: assistantId, role: "assistant", content: "", busy: true }]);
    setDraft("");
    setAttachments([]);
    setSending(true);
    postHost("embed:conversation-started", { sessionId: session.sessionId });
    try {
      if (isDevPreview) {
        await new Promise((resolve) => window.setTimeout(resolve, 550));
        setMessages((current) => current.map((item) => item.id === assistantId ? {
          ...item,
          busy: false,
          content: "这是交互原型预览。接入 Embed Token 后，我会在当前业务记录的权限边界内调用真实智能体、知识与工具。",
        } : item));
        return;
      }
      const response = await fetch(`/embed/v1/apps/${APP_CODE}/sessions/${encodeURIComponent(session.sessionId)}/chat/stream`, {
        method: "POST",
        headers: { Authorization: `Bearer ${tokenRef.current}`, "Content-Type": "application/json" },
        body: JSON.stringify({ question, attachmentIds: selected }),
      });
      if (!response.ok) {
        if (response.status === 401) postHost("embed:token-required", { reason: "expired" });
        const body = await response.json().catch(() => null) as Envelope<unknown> | null;
        throw new Error(body?.message || `请求失败（${response.status}）`);
      }
      await consumeStream(response, assistantId);
      setMessages((current) => current.map((item) => {
        if (item.id !== assistantId) return item;
        const confirmation = exactConfirmation(item.content);
        return { ...item, busy: false, confirmation };
      }));
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      setMessages((current) => current.map((item) => item.id === assistantId
        ? { ...item, busy: false, content: `本次请求未完成：${message}` }
        : item));
      postHost("embed:error", { message });
    } finally {
      setSending(false);
    }
  };

  const upload = async (event: ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files ?? []);
    event.target.value = "";
    if (!session || !files.length) return;
    for (const file of files.slice(0, Math.max(0, 10 - attachments.length))) {
      if (isDevPreview) {
        setAttachments((current) => [...current, { id: crypto.randomUUID(), name: file.name, contentType: file.type, sizeBytes: file.size, status: "READY" }]);
        continue;
      }
      const form = new FormData();
      form.append("clientAttachmentId", crypto.randomUUID());
      form.append("file", file);
      try {
        const item = await api<Attachment>(`/embed/v1/apps/${APP_CODE}/sessions/${encodeURIComponent(session.sessionId)}/attachments`, { method: "POST", body: form });
        setAttachments((current) => [...current, item]);
      } catch (error) {
        setNotice(`附件上传失败：${error instanceof Error ? error.message : String(error)}`);
      }
    }
  };

  const toggleVoice = async () => {
    if (listening) {
      stopAsr();
      return;
    }
    if (!speechSupported || !tokenRef.current) {
      setNotice(isDevPreview ? "预览模式不连接麦克风服务" : "当前浏览器不支持语音输入");
      return;
    }
    const prefix = draft;
    await startAsr({
      token: tokenRef.current,
      getPrefix: () => prefix,
      onLiveText: setDraft,
      onNotice: setNotice,
      onFinished: ({ fullText }) => setDraft(fullText),
    });
  };

  const onKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      void send();
    }
  };

  const contextRows = useMemo(() => {
    const values = session?.context ?? {};
    return Object.entries(values).filter(([, value]) => typeof value !== "object").slice(0, 5);
  }, [session?.context]);

  return (
    <main className={`sisi-shell sisi-shell--${mode} ${leftOpen ? "" : "sisi-shell--left-closed"} ${rightOpen ? "" : "sisi-shell--right-closed"}`} data-theme={resolveSisiTheme(mode, session)}>
      <header className="sisi-header">
        <div className="sisi-brand">
          <span className="sisi-seal" aria-hidden="true">Ci</span>
          <div><strong>{session?.productName || "AgentCiCi"}</strong><span>AI Agent</span></div>
        </div>
        <div className="sisi-header__context">
          <span className="sisi-status-dot" />
          <span>{loading ? "身份校验中" : notice}</span>
        </div>
        <div className="sisi-header__actions">
          {mode === "page" && <button className="sisi-icon-button" onClick={() => setLeftOpen((value) => !value)} title="切换业务侧栏"><PanelLeftClose size={17} /></button>}
          <button className="sisi-icon-button" onClick={() => postHost("embed:expand")} title="展开"><Maximize2 size={17} /></button>
          {mode === "float" && <button className="sisi-icon-button" onClick={() => postHost("embed:close")} title="关闭"><X size={18} /></button>}
        </div>
      </header>

      <section className="sisi-workspace">
        {mode === "page" && <aside className="sisi-context-rail" aria-label="业务上下文与历史">
          <section className="sisi-context-card">
            <span className="sisi-eyebrow">当前业务上下文</span>
            <h2>{session?.recordName || "等待业务记录"}</h2>
            <p>{session?.customerName || "身份接入后自动读取当前记录"}</p>
            <dl>{contextRows.map(([key, value]) => <div key={key}><dt>{key}</dt><dd>{text(value)}</dd></div>)}</dl>
          </section>
          <section className="sisi-history">
            <div className="sisi-section-title"><span><History size={15} /> 对话脉络</span><SquarePen size={15} /></div>
            {messages.filter((item) => item.role === "user").slice(-5).reverse().map((item) => (
              <button key={item.id} onClick={() => setDraft(item.content)}><span>{item.content}</span><ChevronRight size={14} /></button>
            ))}
            {!messages.some((item) => item.role === "user") && <p className="sisi-empty-copy">还没有历史提问</p>}
          </section>
          <div className="sisi-identity-note"><ShieldCheck size={16} /><span>CloudCC 身份安全接入<br />无需登录 AgentCiCi</span></div>
        </aside>}

        <section className="sisi-conversation" aria-label="思思对话">
          <div className="sisi-messages" ref={listRef} aria-live="polite">
            {loading && <div className="sisi-loading"><LoaderCircle className="spin" /><span>正在建立安全会话…</span></div>}
            {!loading && messages.length === 0 && <div className="sisi-welcome">
              <span className="sisi-seal sisi-seal--hero">思</span>
              <h1>你好，我是思思</h1>
              <p>我已进入当前业务场景，可以基于你有权访问的数据进行分析、检索和执行。</p>
              <div className="sisi-suggestions">{SUGGESTIONS.map((item) => <button key={item} onClick={() => void send(item)}><Sparkles size={14} />{item}</button>)}</div>
            </div>}
            {messages.map((message) => <article key={message.id} className={`sisi-message sisi-message--${message.role}`}>
              {message.role === "assistant" && <span className="sisi-seal sisi-seal--message">思</span>}
              <div className="sisi-message__body">
                {message.role === "assistant" && <span className="sisi-message__name">思思</span>}
                {message.attachments?.length ? <div className="sisi-message__attachments">{message.attachments.map((item) => <span key={item.id}>{item.contentType.startsWith("image/") ? <ImageIcon size={13} /> : <FileText size={13} />}{item.name}</span>)}</div> : null}
                <div className="sisi-bubble"><ChatMarkdown content={message.content} busy={message.busy} /></div>
                {message.confirmation && <button className="sisi-confirm" onClick={() => { postHost("embed:action-confirmed", { phrase: message.confirmation }); void send(message.confirmation); }}><ShieldCheck size={15} />确认并回复“{message.confirmation}”</button>}
              </div>
            </article>)}
          </div>

          <div className="sisi-composer-wrap">
            {attachments.length > 0 && <div className="sisi-attachment-strip">{attachments.map((item) => <span key={item.id}>{item.contentType.startsWith("image/") ? <ImageIcon size={13} /> : <FileText size={13} />}{item.name}<button onClick={() => setAttachments((current) => current.filter((entry) => entry.id !== item.id))}><X size={12} /></button></span>)}</div>}
            <div className={`sisi-composer ${listening ? "sisi-composer--listening" : ""}`}>
              <textarea className="sisi-composer__input" value={draft} onChange={(event) => setDraft(event.target.value)} onKeyDown={onKeyDown} placeholder={listening ? "正在聆听…" : "问思思，或交代一个任务…"} rows={1} disabled={!session || sending} />
              <div className="sisi-composer__tools">
                {mode === "page" && <>
                  <input ref={fileRef} type="file" accept={ACCEPT} multiple hidden onChange={upload} />
                  <button onClick={() => fileRef.current?.click()} disabled={!session || sending} title="上传附件"><Paperclip size={18} /></button>
                </>}
                <button onClick={() => void toggleVoice()} disabled={!session || sending} className={listening ? "is-active" : ""} title="语音输入">{listening ? <CircleStop size={18} /> : <Mic size={18} />}</button>
                <span className="sisi-composer__hint">Enter 发送 · Shift+Enter 换行</span>
                <button className="sisi-send" onClick={() => void send()} disabled={!draft.trim() || !session || sending} title="发送">{sending ? <LoaderCircle className="spin" size={18} /> : <ArrowUp size={18} />}</button>
              </div>
            </div>
            <p className="sisi-disclaimer">思思可能会出错。涉及写入与高风险操作时，请确认回读结果。</p>
          </div>
        </section>

        {mode === "page" && <aside className="sisi-evidence-rail" aria-label="来源与工具执行">
          <div className="sisi-section-title"><span><Link2 size={15} /> 证据与执行</span><button onClick={() => setRightOpen((value) => !value)}><PanelLeftClose size={15} /></button></div>
          {evidence.map((item) => <a key={item.id} className={`sisi-evidence sisi-evidence--${item.kind}`} href={item.href || undefined} target={item.href ? "_blank" : undefined} rel="noreferrer">
            <span>{item.kind === "source" ? <Link2 size={15} /> : item.kind === "tool" ? <Wrench size={15} /> : <ShieldCheck size={15} />}</span>
            <div><strong>{item.title}</strong><p>{item.detail}</p></div>
            {item.kind === "tool" ? <Check size={14} /> : <ChevronRight size={14} />}
          </a>)}
          {!evidence.length && <div className="sisi-evidence-empty"><Sparkles size={20} /><p>回答中引用的知识来源、工具步骤与权限确认会显示在这里。</p></div>}
          <section className="sisi-boundary"><span><ShieldCheck size={15} /> 本轮边界</span><p>智能体：{session?.agentId || "待接入"}</p><p>权限：{session?.permissions?.join(" · ") || "待校验"}</p></section>
        </aside>}
      </section>
    </main>
  );
}
