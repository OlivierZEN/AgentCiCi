import { useEffect, useMemo, useRef, useState } from "react";
import {
  acceptCustomerRecommendation,
  applyCustomerRecommendation,
  askCustomerWorkbenchAssistant,
  getCustomerWorkbenchDetail,
  listCustomerWorkbenchAccounts,
  type CustomerAssistantResult,
  type CustomerRecommendation,
  type CustomerWorkbenchAccount,
  type CustomerWorkbenchDetail,
} from "./customerWorkbenchApi";

type ChatMessage = {
  role: "user" | "assistant";
  text: string;
  time: string;
};

const segmentLabels: Record<string, string> = {
  NEW: "新客户",
  EXISTING: "老客户",
  STRATEGIC: "战略客户",
  RISK: "风险客户",
};

function nowTime() {
  const now = new Date();
  return `${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}`;
}

function segmentLabel(segment: string) {
  return segmentLabels[segment] ?? segment;
}

function shortDate(value: string) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return `${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")} ${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
}

function formatConfidence(value: number) {
  const normalized = Number(value);
  if (!Number.isFinite(normalized)) return "—";
  const percent = normalized > 1 ? normalized : normalized * 100;
  return `${Math.round(percent)}%`;
}

type CustomerWorkbenchAppProps = {
  token: string;
  embedded?: boolean;
};

export function CustomerWorkbenchApp({ token, embedded = false }: CustomerWorkbenchAppProps) {
  const [accounts, setAccounts] = useState<CustomerWorkbenchAccount[]>([]);
  const [activeAccountId, setActiveAccountId] = useState("");
  const [detail, setDetail] = useState<CustomerWorkbenchDetail | null>(null);
  const [activeTab, setActiveTab] = useState<"overview" | "timeline" | "new" | "existing" | "recommendations">("overview");
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState<"all" | "new" | "existing" | "risk">("all");
  const [loading, setLoading] = useState(false);
  const [notice, setNotice] = useState("");
  const [assistantInput, setAssistantInput] = useState("");
  const [assistantMessages, setAssistantMessages] = useState<ChatMessage[]>([
    { role: "assistant", text: "我可以总结互动、查看风险、生成跟进建议，也能帮你切换客户。", time: "09:30" },
  ]);
  const recommendationRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!token) return;
    let ignore = false;
    setLoading(true);
    listCustomerWorkbenchAccounts(token)
      .then((items) => {
        if (ignore) return;
        setAccounts(items);
        setActiveAccountId((current) => current || items[0]?.accountId || "");
      })
      .catch((error) => setNotice(error instanceof Error ? error.message : String(error)))
      .finally(() => !ignore && setLoading(false));
    return () => {
      ignore = true;
    };
  }, [token]);

  useEffect(() => {
    if (!token || !activeAccountId) return;
    let ignore = false;
    getCustomerWorkbenchDetail(token, activeAccountId)
      .then((item) => {
        if (!ignore) setDetail(item);
      })
      .catch((error) => setNotice(error instanceof Error ? error.message : String(error)));
    return () => {
      ignore = true;
    };
  }, [token, activeAccountId]);

  const filteredAccounts = useMemo(() => {
    const text = query.trim().toLowerCase();
    return accounts.filter((item) => {
      if (filter === "new" && item.segment !== "NEW") return false;
      if (filter === "existing" && item.segment !== "EXISTING" && item.segment !== "STRATEGIC") return false;
      if (filter === "risk" && item.segment !== "RISK") return false;
      if (!text) return true;
      return `${item.name} ${item.owner} ${item.stage} ${item.tags?.join(" ")}`.toLowerCase().includes(text);
    });
  }, [accounts, filter, query]);

  const activeAccount = accounts.find((item) => item.accountId === activeAccountId) ?? accounts[0];

  const reloadDetail = async () => {
    if (!token || !activeAccountId) return;
    setDetail(await getCustomerWorkbenchDetail(token, activeAccountId));
    setAccounts(await listCustomerWorkbenchAccounts(token));
  };

  const handleRecommendation = async (item: CustomerRecommendation, action: "accept" | "apply") => {
    if (!token) return;
    try {
      if (action === "accept") {
        await acceptCustomerRecommendation(token, item.recommendationId);
        setNotice("建议已采纳，确认后可继续落地到 CRM。");
      } else {
        const result = await applyCustomerRecommendation(token, item.recommendationId);
        setNotice(result.message || "CRM 落地动作已记录。");
      }
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
  };

  const submitAssistant = async (preset?: string) => {
    const message = (preset ?? assistantInput).trim();
    if (!message || !token) return;
    setAssistantMessages((prev) => [...prev, { role: "user", text: message, time: nowTime() }]);
    setAssistantInput("");
    try {
      const result = await askCustomerWorkbenchAssistant(token, { accountId: activeAccountId, message });
      setAssistantMessages((prev) => [...prev, { role: "assistant", text: result.reply, time: nowTime() }]);
      handleAssistantResult(result);
    } catch (error) {
      setAssistantMessages((prev) => [...prev, { role: "assistant", text: error instanceof Error ? error.message : String(error), time: nowTime() }]);
    }
  };

  const startVoice = () => {
    const SpeechRecognition = (window as unknown as { SpeechRecognition?: any; webkitSpeechRecognition?: any }).SpeechRecognition
      || (window as unknown as { SpeechRecognition?: any; webkitSpeechRecognition?: any }).webkitSpeechRecognition;
    if (!SpeechRecognition) {
      setNotice("当前浏览器不支持语音识别，可直接输入指令。");
      return;
    }
    const recognition = new SpeechRecognition();
    recognition.lang = "zh-CN";
    recognition.interimResults = false;
    recognition.onresult = (event: any) => {
      const text = event.results?.[0]?.[0]?.transcript ?? "";
      setAssistantInput(text);
    };
    recognition.onerror = () => setNotice("语音识别失败，请检查麦克风权限。");
    recognition.start();
  };

  if (!token) {
    return <section className="customer-workbench-empty">请先登录后使用客户互动工作台。</section>;
  }

  return (
    <section className={`customer-workbench${embedded ? " customer-workbench--embedded" : ""}`}>
      <aside className="customer-workbench__queue" aria-label="客户推进队列">
        <header>
          <strong>客户推进队列</strong>
          <span>{accounts.length || "—"} 位客户</span>
        </header>
        <input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="搜索客户 / 负责人 / 标签"
          aria-label="搜索客户"
        />
        <nav aria-label="客户筛选">
          {[
            ["all", "全部"],
            ["new", "新客户"],
            ["existing", "老客户"],
            ["risk", "风险"],
          ].map(([key, label]) => (
            <button key={key} type="button" className={filter === key ? "is-active" : ""} onClick={() => setFilter(key as typeof filter)}>
              {label}
            </button>
          ))}
        </nav>
        <div className="customer-workbench__accounts">
          {filteredAccounts.map((item) => (
            <button
              key={item.accountId}
              type="button"
              className={`customer-workbench-account${item.accountId === activeAccountId ? " is-active" : ""}`}
              onClick={() => setActiveAccountId(item.accountId)}
            >
              <span className={`customer-workbench-account__dot is-${item.segment.toLowerCase()}`} />
              <span>
                <strong>{item.name}</strong>
                <small>{item.owner} · {item.stage}</small>
                <em>{segmentLabel(item.segment)} · 风险 {item.riskCount} · 建议 {item.pendingRecommendationCount}</em>
              </span>
            </button>
          ))}
          {loading ? <p className="customer-workbench__muted">正在加载客户...</p> : null}
        </div>
      </aside>

      <main className="customer-workbench__main">
        <header className="customer-workbench__head">
          <div>
            <p>{activeAccount ? segmentLabel(activeAccount.segment) : "客户"}</p>
            <h2>{detail?.name || activeAccount?.name || "客户互动工作台"}</h2>
            <span>{detail?.industry || "CRM 客户"} · {detail?.owner || activeAccount?.owner || "负责人"}</span>
          </div>
          <button type="button" onClick={() => setNotice("已打开 CRM 客户主页入口，演示环境使用工作台内联详情。")}>打开 CRM 客户主页</button>
        </header>

        {notice ? <div className="customer-workbench__notice">{notice}</div> : null}

        <section className="customer-workbench__metrics" aria-label="客户指标">
          <Metric label="新客户推进" value={detail?.progressScore ?? activeAccount?.progressScore ?? 0} suffix="分" />
          <Metric label="老客户健康" value={detail?.healthScore ?? activeAccount?.healthScore ?? 0} suffix="分" />
          <Metric label="风险信号" value={detail?.riskCount ?? activeAccount?.riskCount ?? 0} suffix="个" />
          <Metric label="下一步行动" value={detail?.nextActionCount ?? activeAccount?.nextActionCount ?? 0} suffix="项" />
        </section>

        <nav className="customer-workbench__tabs" aria-label="客户详情视图">
          {[
            ["overview", "概览"],
            ["timeline", "互动时间线"],
            ["new", "新客户推进"],
            ["existing", "老客户经营"],
            ["recommendations", "CRM 落地建议"],
          ].map(([key, label]) => (
            <button key={key} type="button" className={activeTab === key ? "is-active" : ""} onClick={() => setActiveTab(key as typeof activeTab)}>
              {label}
            </button>
          ))}
        </nav>

        <section className="customer-workbench__content">
          {activeTab === "overview" ? <Overview detail={detail} /> : null}
          {activeTab === "timeline" ? <Timeline detail={detail} /> : null}
          {activeTab === "new" ? <SignalPanel title="新客户推进" score={detail?.progressScore ?? 0} items={detail?.newCustomerSignals ?? []} actions={detail?.nextActions ?? []} /> : null}
          {activeTab === "existing" ? <SignalPanel title="老客户经营" score={detail?.healthScore ?? 0} items={detail?.existingCustomerSignals ?? []} actions={detail?.risks ?? []} /> : null}
          {activeTab === "recommendations" ? (
            <div ref={recommendationRef}>
              <Recommendations detail={detail} onAction={handleRecommendation} />
            </div>
          ) : null}
        </section>
      </main>

      <aside className="customer-workbench__assistant" aria-label="AI 客户助理">
        <header>
          <div>
            <strong>AI 客户助理</strong>
            <span>{detail?.crmConnection?.label || "工作台模式"}</span>
          </div>
        </header>
        <div className="customer-workbench__chat">
          {assistantMessages.map((message, index) => (
            <div key={`${message.time}-${index}`} className={`customer-workbench-message is-${message.role}`}>
              <p>{message.text}</p>
              <span>{message.time}</span>
            </div>
          ))}
        </div>
        <div className="customer-workbench__quick">
          {["总结最近互动", "查看风险", "生成跟进任务", "切到下一个客户"].map((item) => (
            <button key={item} type="button" onClick={() => void submitAssistant(item)}>{item}</button>
          ))}
        </div>
        <div className="customer-workbench__composer">
          <textarea value={assistantInput} onChange={(event) => setAssistantInput(event.target.value)} placeholder="输入问题或指令..." />
          <div>
            <button type="button" onClick={startVoice}>语音</button>
            <button type="button" onClick={() => void submitAssistant()}>发送</button>
          </div>
        </div>
      </aside>
    </section>
  );
}

function Metric({ label, value, suffix }: { label: string; value: number; suffix: string }) {
  return (
    <div>
      <span>{label}</span>
      <strong>{value}<small>{suffix}</small></strong>
    </div>
  );
}

function Overview({ detail }: { detail: CustomerWorkbenchDetail | null }) {
  return (
    <div className="customer-workbench-overview">
      <section>
        <h3>客户摘要</h3>
        <p>{detail?.summary || "正在整理客户摘要。"}</p>
        <dl>
          <dt>关键联系人</dt>
          <dd>{detail?.contact || "待补充"}</dd>
          <dt>最近互动</dt>
          <dd>{detail?.lastInteraction || "暂无互动"}</dd>
        </dl>
      </section>
      <section>
        <h3>风险与机会</h3>
        <List items={detail?.risks ?? []} empty="暂无高风险" />
        <List items={detail?.nextActions ?? []} empty="暂无下一步行动" />
      </section>
    </div>
  );
}

function Timeline({ detail }: { detail: CustomerWorkbenchDetail | null }) {
  return (
    <div className="customer-workbench-timeline">
      {(detail?.timeline ?? []).map((item) => (
        <article key={item.eventId}>
          <time>{shortDate(item.occurredAt)}</time>
          <div>
            <strong>{item.subject}</strong>
            <p>{item.summary}</p>
            <span>{item.sourceType} · {item.lifecycleArea}</span>
          </div>
        </article>
      ))}
    </div>
  );
}

function SignalPanel({ title, score, items, actions }: { title: string; score: number; items: string[]; actions: string[] }) {
  return (
    <div className="customer-workbench-signals">
      <header>
        <h3>{title}</h3>
        <strong>{score}<small>分</small></strong>
      </header>
      <section>
        <h4>识别信号</h4>
        <List items={items} empty="暂无明确信号" />
      </section>
      <section>
        <h4>建议动作</h4>
        <List items={actions} empty="暂无建议动作" />
      </section>
    </div>
  );
}

function Recommendations({ detail, onAction }: { detail: CustomerWorkbenchDetail | null; onAction: (item: CustomerRecommendation, action: "accept" | "apply") => void }) {
  const items = detail?.recommendations ?? [];
  return (
    <div className="customer-workbench-recommendations">
      {items.map((item) => (
        <article key={item.recommendationId}>
          <header>
            <div>
              <strong>{item.title}</strong>
              <span>{item.type} · 置信度 {formatConfidence(item.confidence)}</span>
            </div>
            <em>{item.status}</em>
          </header>
          <p>{item.rationale}</p>
          <footer>
            <button type="button" onClick={() => onAction(item, "accept")} disabled={item.status === "APPLIED"}>采纳</button>
            <button type="button" onClick={() => onAction(item, "apply")} disabled={item.status === "APPLIED"}>确认落地</button>
          </footer>
        </article>
      ))}
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
