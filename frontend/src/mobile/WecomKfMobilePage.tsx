import { useEffect, useMemo, useState } from "react";
import { AlertTriangle, ArrowRight, CheckCircle2, RefreshCw, ShieldCheck, UserRoundCheck } from "lucide-react";
import "./wecom-kf-mobile.css";

type JsSdkBundle = {
  corpId: string;
  agentId: string;
  timestamp: number;
  nonce: string;
  corpSignature: string;
  agentSignature: string;
};

type Conversation = {
  conversationId: string;
  customerLabel: string;
  lastCustomerSummary: string;
  externalUserId: string;
  serviceState: number;
  ownerMode: "AI" | "HANDOFF" | "PENDING" | "HUMAN" | "ENDED";
  servicerUserId?: string | null;
  revision: number;
  checkedAt?: string | null;
  lastCustomerMessageAt?: string | null;
  handoffReason?: string | null;
};

type MobileContext = {
  accountName: string;
  operatorUserId: string;
  openKfId: string;
  jsSdk: JsSdkBundle;
  conversations: Conversation[];
  generatedAt: string;
};

type HandoffReceipt = {
  operationId: string;
  status: "IN_PROGRESS" | "SUCCEEDED" | "FAILED" | "CONFLICT";
  correlationId: string;
  resultingRevision?: number | null;
  readbackState?: number | null;
  errorCode?: string | null;
  state?: { serviceState: number; ownerMode: Conversation["ownerMode"]; servicerUserId?: string | null; revision: number } | null;
};

type WecomSdk = {
  config: (options: Record<string, unknown>) => void;
  ready: (callback: () => void) => void;
  error: (callback: (error: unknown) => void) => void;
  agentConfig: (options: Record<string, unknown>) => void;
  invoke: (name: string, payload: Record<string, unknown>, callback: (result: { err_msg?: string }) => void) => void;
};

declare global {
  interface Window {
    wx?: WecomSdk;
  }
}

const stateLabel: Record<Conversation["ownerMode"], string> = {
  AI: "AI 接待中",
  HANDOFF: "接管提交中",
  PENDING: "等待人工",
  HUMAN: "人工接待中",
  ENDED: "会话已结束",
};

function apiPath(path: string) {
  return `/wecom/kf/mobile/api${path}`;
}

function pageUrl() {
  return window.location.href.split("#", 1)[0];
}

function requestId() {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) return crypto.randomUUID();
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function formatTime(value?: string | null) {
  if (!value) return "尚未同步";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "尚未同步";
  return new Intl.DateTimeFormat("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hour12: false }).format(date);
}

async function loadWecomSdk() {
  if (window.wx) return;
  await new Promise<void>((resolve, reject) => {
    const existing = document.querySelector<HTMLScriptElement>('script[data-wecom-kf-sdk="true"]');
    if (existing) {
      existing.addEventListener("load", () => resolve(), { once: true });
      existing.addEventListener("error", () => reject(new Error("企业微信 JS-SDK 加载失败")), { once: true });
      return;
    }
    const script = document.createElement("script");
    script.src = "https://res.wx.qq.com/open/js/jweixin-1.2.0.js";
    script.async = true;
    script.dataset.wecomKfSdk = "true";
    script.onload = () => resolve();
    script.onerror = () => reject(new Error("企业微信 JS-SDK 加载失败"));
    document.head.appendChild(script);
  });
}

async function configureSdk(config: JsSdkBundle) {
  await loadWecomSdk();
  const wx = window.wx;
  if (!wx) throw new Error("当前环境不支持企业微信 JS-SDK");
  await new Promise<void>((resolve, reject) => {
    wx.error((error) => reject(new Error(`企业微信签名校验失败：${JSON.stringify(error)}`)));
    wx.ready(() => {
      wx.agentConfig({
        corpid: config.corpId,
        agentid: config.agentId,
        timestamp: config.timestamp,
        nonceStr: config.nonce,
        signature: config.agentSignature,
        jsApiList: ["navigateToKfChat"],
        success: () => resolve(),
        fail: (error: unknown) => reject(new Error(`企业微信应用鉴权失败：${JSON.stringify(error)}`)),
      });
    });
    wx.config({
      beta: true,
      debug: false,
      appId: config.corpId,
      timestamp: config.timestamp,
      nonceStr: config.nonce,
      signature: config.corpSignature,
      jsApiList: ["agentConfig", "navigateToKfChat"],
    });
  });
}

export default function WecomKfMobilePage() {
  const [context, setContext] = useState<MobileContext | null>(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [filter, setFilter] = useState<"active" | "pending" | "human">("active");
  const [busyId, setBusyId] = useState("");
  const [confirmId, setConfirmId] = useState("");
  const [sdkReady, setSdkReady] = useState(false);

  const load = async (initializeSdk = true) => {
    setError("");
    const response = await fetch(`${apiPath("/context")}?pageUrl=${encodeURIComponent(pageUrl())}`, { credentials: "include" });
    const payload = await response.json().catch(() => null);
    if (!response.ok || !payload?.success) throw new Error(payload?.message || "坐席工作台加载失败");
    const next = payload.data as MobileContext;
    setContext(next);
    if (initializeSdk) {
      setSdkReady(false);
      try {
        await configureSdk(next.jsSdk);
        setSdkReady(true);
      } catch (sdkError) {
        setNotice(sdkError instanceof Error ? sdkError.message : "企业微信原生会话暂不可用");
      }
    }
  };

  useEffect(() => {
    void load().catch((loadError) => setError(loadError instanceof Error ? loadError.message : "坐席工作台加载失败"));
    const refreshVisible = () => {
      if (document.visibilityState === "visible") {
        void load(false).catch((loadError) => setNotice(loadError instanceof Error ? loadError.message : "会话状态增量刷新失败"));
      }
    };
    const interval = window.setInterval(refreshVisible, 20_000);
    document.addEventListener("visibilitychange", refreshVisible);
    return () => {
      window.clearInterval(interval);
      document.removeEventListener("visibilitychange", refreshVisible);
    };
  }, []);

  const conversations = useMemo(() => {
    const rows = context?.conversations ?? [];
    if (filter === "pending") return rows.filter((row) => row.ownerMode === "PENDING" || row.ownerMode === "HANDOFF");
    if (filter === "human") return rows.filter((row) => row.ownerMode === "HUMAN");
    return rows.filter((row) => row.ownerMode !== "ENDED");
  }, [context?.conversations, filter]);

  const replaceConversation = (next: Conversation) => {
    setContext((current) => current ? { ...current, conversations: current.conversations.map((row) => row.conversationId === next.conversationId ? next : row) } : current);
  };

  const refresh = async (conversation: Conversation) => {
    setBusyId(conversation.conversationId);
    setNotice("");
    try {
      const response = await fetch(apiPath(`/conversations/${conversation.conversationId}/refresh`), {
        method: "POST",
        credentials: "include",
        headers: { "X-Wecom-Kf-Request": "1" },
      });
      const payload = await response.json().catch(() => null);
      if (!response.ok || !payload?.success) throw new Error(payload?.message || "状态刷新失败");
      replaceConversation(payload.data as Conversation);
      setNotice("已回读企业微信最新接待状态");
    } catch (refreshError) {
      setNotice(refreshError instanceof Error ? refreshError.message : "状态刷新失败");
    } finally {
      setBusyId("");
    }
  };

  const openNativeChat = (conversation: Conversation) => {
    if (!sdkReady || !window.wx) {
      setNotice("企业微信原生会话尚未就绪，请刷新后重试");
      return;
    }
    window.wx.invoke("navigateToKfChat", {
      openKfId: context?.openKfId,
      externalUserId: conversation.externalUserId,
    }, (result) => {
      if (result?.err_msg && !result.err_msg.endsWith(":ok")) setNotice(`打开客服会话失败：${result.err_msg}`);
    });
  };

  const takeover = async (conversation: Conversation) => {
    if (confirmId !== conversation.conversationId) {
      setConfirmId(conversation.conversationId);
      setNotice("再次点击确认接管；确认后 AI 发送将立即受 revision fence 阻断");
      return;
    }
    setBusyId(conversation.conversationId);
    setNotice("");
    const correlationId = requestId();
    try {
      const response = await fetch(apiPath(`/conversations/${conversation.conversationId}/takeover`), {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json", "X-Wecom-Kf-Request": "1" },
        body: JSON.stringify({
          expectedRevision: conversation.revision,
          idempotencyKey: `mobile:${correlationId}`,
          correlationId,
        }),
      });
      const payload = await response.json().catch(() => null);
      if (!response.ok || !payload?.success) throw new Error(payload?.message || "接管失败");
      const receipt = payload.data as HandoffReceipt;
      if (receipt.status !== "SUCCEEDED" || receipt.readbackState !== 3 || !receipt.state) {
        await refresh(conversation);
        throw new Error(receipt.status === "CONFLICT" ? "状态已变化，已刷新，请重新确认" : `接管未完成：${receipt.errorCode || receipt.status}`);
      }
      const next = { ...conversation, serviceState: receipt.state.serviceState, ownerMode: receipt.state.ownerMode, servicerUserId: receipt.state.servicerUserId, revision: receipt.state.revision };
      replaceConversation(next);
      setConfirmId("");
      setNotice(`接管成功 · 回执 ${receipt.operationId.slice(0, 8)} · revision ${receipt.state.revision}`);
      openNativeChat(next);
    } catch (takeoverError) {
      setNotice(takeoverError instanceof Error ? takeoverError.message : "接管失败");
    } finally {
      setBusyId("");
    }
  };

  if (error) {
    return <main className="kf-mobile-shell"><section className="kf-mobile-state"><AlertTriangle size={28} /><h1>无法进入坐席工作台</h1><p>{error}</p><small>请从企业微信工作台中的正式入口重新进入。</small></section></main>;
  }
  if (!context) {
    return <main className="kf-mobile-shell"><section className="kf-mobile-state"><RefreshCw className="is-spinning" size={26} /><h1>正在校验坐席身份</h1><p>正在从企业微信回读账号与会话状态。</p></section></main>;
  }

  return (
    <main className="kf-mobile-shell">
      <header className="kf-mobile-header">
        <div><span>微信客服 · 人工接管</span><h1>{context.accountName}</h1></div>
        <div className="kf-mobile-identity"><ShieldCheck size={16} />{context.operatorUserId}</div>
      </header>

      <section className="kf-mobile-summary" aria-label="接待概况">
        <div><strong>{context.conversations.filter((row) => row.ownerMode === "PENDING" || row.ownerMode === "HANDOFF").length}</strong><span>等待人工</span></div>
        <div><strong>{context.conversations.filter((row) => row.ownerMode === "AI").length}</strong><span>AI 接待</span></div>
        <div><strong>{context.conversations.filter((row) => row.ownerMode === "HUMAN").length}</strong><span>人工接待</span></div>
      </section>

      {notice ? <div className="kf-mobile-notice" role="status">{notice}</div> : null}

      <nav className="kf-mobile-tabs" aria-label="会话筛选">
        <button className={filter === "active" ? "is-active" : ""} onClick={() => setFilter("active")}>进行中</button>
        <button className={filter === "pending" ? "is-active" : ""} onClick={() => setFilter("pending")}>待接管</button>
        <button className={filter === "human" ? "is-active" : ""} onClick={() => setFilter("human")}>我方接待</button>
      </nav>

      <section className="kf-mobile-list" aria-label="客户会话">
        {conversations.map((conversation) => {
          const busy = busyId === conversation.conversationId;
          const human = conversation.ownerMode === "HUMAN" && conversation.servicerUserId === context.operatorUserId;
          return (
            <article key={conversation.conversationId} className={`kf-mobile-card is-${conversation.ownerMode.toLowerCase()}`}>
              <div className="kf-mobile-card__head">
                <div><strong>{conversation.customerLabel}</strong><span>{formatTime(conversation.lastCustomerMessageAt)} 有新消息</span></div>
                <em>{stateLabel[conversation.ownerMode]}</em>
              </div>
              <dl>
                {conversation.lastCustomerSummary ? <div className="kf-mobile-card__summary">“{conversation.lastCustomerSummary}”</div> : null}
                <div><dt>权威状态</dt><dd>{conversation.serviceState} · revision {conversation.revision}</dd></div>
                <div><dt>最近回读</dt><dd>{formatTime(conversation.checkedAt)}</dd></div>
                {conversation.servicerUserId ? <div><dt>当前坐席</dt><dd>{conversation.servicerUserId}</dd></div> : null}
              </dl>
              <div className="kf-mobile-card__risk">
                {conversation.ownerMode === "AI" ? <><AlertTriangle size={15} />接管成功回读前，请勿在两个端同时回复。</> : <><CheckCircle2 size={15} />AI 发送已由接待状态与 revision fence 阻断。</>}
              </div>
              <div className="kf-mobile-card__actions">
                <button className="kf-mobile-secondary" disabled={busy} onClick={() => void refresh(conversation)}><RefreshCw size={16} />刷新</button>
                {human ? (
                  <button className="kf-mobile-primary" disabled={!sdkReady} onClick={() => openNativeChat(conversation)}><UserRoundCheck size={17} />进入企业微信会话<ArrowRight size={16} /></button>
                ) : (
                  <button className={`kf-mobile-primary${confirmId === conversation.conversationId ? " is-confirm" : ""}`} disabled={busy || conversation.ownerMode === "ENDED"} onClick={() => void takeover(conversation)}>
                    <UserRoundCheck size={17} />{busy ? "接管中…" : confirmId === conversation.conversationId ? "确认强制接管" : "强制接管"}
                  </button>
                )}
              </div>
            </article>
          );
        })}
        {conversations.length === 0 ? <div className="kf-mobile-empty">当前筛选下没有会话</div> : null}
      </section>
    </main>
  );
}
