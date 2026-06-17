import { KeyboardEvent, useEffect, useMemo, useRef, useState } from "react";
import { MeetingMinutesPanel, type MeetingPanelStatus, type MeetingPanelTranscriptSegment, type MeetingPanelWritebackItem } from "../meeting/MeetingMinutesPanel";
import { useAsrVoiceInput } from "../shared/useAsrVoiceInput";
import { appendMeetingTranscriptSegment, speakerDisplayName } from "../assistant/meetingTranscript";

type ApiEnvelope<T> = {
  success?: boolean;
  message?: string;
  data?: T;
};

type EmbedClaims = {
  appCode?: string;
  parentOrigin?: string;
  source?: string;
  objectType?: string;
  objectId?: string;
  recordName?: string;
  customerName?: string;
  permissions?: string[];
  context?: Record<string, unknown>;
};

type SessionView = {
  sessionId: string;
  source: string;
  objectType: string;
  objectId: string;
  recordName: string;
  customerName: string;
  parentOrigin: string;
  status: string;
  context?: Record<string, unknown>;
  summary?: string;
  writebackPreview?: { items?: MeetingPanelWritebackItem[] };
  writebackResult?: Record<string, unknown>;
};

type SpeakerEdit = {
  speakerId: string;
  lineId: string;
  value: string;
};

const APP_CODE = "meeting-minutes";
const SDK_SOURCE = "agentcici-meeting-embed";

export default function EmbedMeetingMinutesPage() {
  const token = useMemo(() => new URLSearchParams(window.location.search).get("token")?.trim() ?? "", []);
  const claims = useMemo(() => decodeClaims(token), [token]);
  const parentOrigin = claims?.parentOrigin ?? "";
  const [session, setSession] = useState<SessionView | null>(null);
  const [status, setStatus] = useState<MeetingPanelStatus>("idle");
  const [notice, setNotice] = useState("正在校验嵌入 token...");
  const [transcript, setTranscript] = useState<MeetingPanelTranscriptSegment[]>([]);
  const [partial, setPartial] = useState<MeetingPanelTranscriptSegment | null>(null);
  const [summary, setSummary] = useState("");
  const [writebackItems, setWritebackItems] = useState<MeetingPanelWritebackItem[]>([]);
  const [selectedWritebackItemIds, setSelectedWritebackItemIds] = useState<string[]>([]);
  const [writebackResultMessage, setWritebackResultMessage] = useState("");
  const [speakerNames, setSpeakerNames] = useState<Record<string, string>>({});
  const [speakerEdit, setSpeakerEdit] = useState<SpeakerEdit | null>(null);
  const transcriptRef = useRef<MeetingPanelTranscriptSegment[]>([]);
  const speakerNamesRef = useRef<Record<string, string>>({});
  const shouldSummarizeRef = useRef(false);
  const initializedRef = useRef(false);
  const transcriptScrollRef = useRef<HTMLDivElement | null>(null);
  const speakerEditInputRef = useRef<HTMLInputElement | null>(null);
  const { listening, speechSupported, start, stop, abort } = useAsrVoiceInput();

  const recordName = session?.recordName || claims?.recordName || stringValue(claims?.context?.recordName);
  const customerName = session?.customerName || claims?.customerName || stringValue(claims?.context?.customerName);

  const postHostMessage = (type: string, payload: Record<string, unknown> = {}) => {
    if (!parentOrigin || window.parent === window) {
      return;
    }
    window.parent.postMessage({
      source: SDK_SOURCE,
      type,
      requestId: crypto.randomUUID?.() ?? `req_${Date.now()}`,
      sessionId: session?.sessionId ?? "",
      payload,
      timestamp: new Date().toISOString(),
    }, parentOrigin);
  };

  const requestJson = async <T,>(path: string, init?: RequestInit): Promise<T> => {
    const res = await fetch(path, {
      ...init,
      headers: {
        ...(init?.body ? { "Content-Type": "application/json" } : {}),
        Authorization: `Bearer ${token}`,
        ...(init?.headers ?? {}),
      },
    });
    const json = (await res.json().catch(() => null)) as ApiEnvelope<T> | null;
    if (!res.ok || !json?.success) {
      throw new Error(json?.message ?? `HTTP ${res.status}`);
    }
    return json.data as T;
  };

  const createSession = async () => {
    if (!token) {
      throw new Error("缺少 embed token");
    }
    const next = await requestJson<SessionView>(`/embed/v1/apps/${APP_CODE}/sessions`, { method: "POST" });
    setSession(next);
    setSummary(next.summary ?? "");
    setWritebackItems(next.writebackPreview?.items ?? []);
    setSelectedWritebackItemIds((next.writebackPreview?.items ?? []).map((item) => item.id));
    setStatus(next.summary ? "ready_to_writeback" : "idle");
    setNotice(next.summary ? "会议 session 已恢复，可继续确认写回。" : "会议 session 已就绪，可开始听记。");
    postHostMessage("embed:ready", { session: next });
    return next;
  };

  useEffect(() => {
    if (initializedRef.current) {
      return;
    }
    initializedRef.current = true;
    void createSession().catch((error) => {
      setStatus("error");
      setNotice(`初始化失败：${error instanceof Error ? error.message : String(error)}`);
      postHostMessage("embed:error", { message: error instanceof Error ? error.message : String(error) });
    });
  }, [token]);

  useEffect(() => {
    const onMessage = (event: MessageEvent) => {
      if (parentOrigin && event.origin !== parentOrigin) {
        return;
      }
      const message = event.data as { source?: string; type?: string; payload?: Record<string, unknown> } | null;
      if (!message || typeof message !== "object") {
        return;
      }
      if (message.type === "host:update-context") {
        setNotice("父页面上下文已更新，写回仍以 token 绑定记录为准。");
      }
      if (message.type === "host:request-close") {
        closeEmbed();
      }
      if (message.type === "host:focus") {
        document.querySelector<HTMLButtonElement>(".cici-meeting-drawer__btn--primary")?.focus();
      }
    };
    window.addEventListener("message", onMessage);
    return () => window.removeEventListener("message", onMessage);
  }, [parentOrigin, status]);

  useEffect(() => {
    const element = transcriptScrollRef.current;
    if (element) {
      element.scrollTop = element.scrollHeight;
    }
  }, [partial?.text, status, transcript]);

  useEffect(() => {
    if (!speakerEdit) {
      return;
    }
    window.setTimeout(() => speakerEditInputRef.current?.focus(), 0);
  }, [speakerEdit?.speakerId, speakerEdit?.lineId]);

  useEffect(() => {
    speakerNamesRef.current = speakerNames;
  }, [speakerNames]);

  useEffect(() => () => abort(), [abort]);

  const updateTranscript = (updater: (prev: MeetingPanelTranscriptSegment[]) => MeetingPanelTranscriptSegment[]) => {
    setTranscript((prev) => {
      const next = updater(prev);
      transcriptRef.current = next;
      return next;
    });
  };

  const buildSegment = (text: string, speakerId?: string, speakerName?: string): MeetingPanelTranscriptSegment => {
    const safeSpeakerId = speakerId?.trim() || "1";
    const safeSpeakerName = speakerNamesRef.current[safeSpeakerId]?.trim() || speakerName?.trim() || speakerDisplayName(safeSpeakerId);
    return {
      id: `embed-meeting-${Date.now()}-${Math.random().toString(16).slice(2)}`,
      speakerId: safeSpeakerId,
      speakerName: safeSpeakerName,
      text: text.trim(),
      time: new Date().toLocaleTimeString("zh-CN", { hour12: false, hour: "2-digit", minute: "2-digit" }),
    };
  };

  const startRecording = async () => {
    if (!speechSupported) {
      setStatus("error");
      setNotice("当前浏览器不支持录音。");
      return;
    }
    const activeSession = session ?? await createSession();
    setStatus("permission");
    setNotice("正在请求麦克风权限...");
    setPartial(null);
    setSummary("");
    setWritebackItems([]);
    setSelectedWritebackItemIds([]);
    setWritebackResultMessage("");
    shouldSummarizeRef.current = true;
    transcriptRef.current = [];
    setTranscript([]);
    await start({
      token,
      provider: "aliyun",
      speakerDiarization: false,
      getPrefix: () => "",
      onLiveText: () => {},
      onNotice: (message) => {
        const setupMessage =
          message.includes("Iflytek realtime ASR credentials are missing") ||
          message.includes("Iflytek realtime ASR is disabled")
            ? "讯飞实时转写未配置或未启用，请联系管理员完成集成配置。"
            : message;
        setNotice(setupMessage);
        if (setupMessage.includes("失败") || message.includes("missing") || message.includes("disabled")) {
          setStatus("error");
          postHostMessage("embed:error", { message: setupMessage });
        } else if (setupMessage.includes("实时听写")) {
          setStatus("recording");
          postHostMessage("embed:meeting-started", { sessionId: activeSession.sessionId });
        }
      },
      onTranscriptEvent: (event) => {
        if (!event.text.trim()) return;
        const segment = buildSegment(event.text, event.speakerId, event.speakerName);
        if (event.type === "partial") {
          setPartial(segment);
          return;
        }
        setPartial(null);
        updateTranscript((prev) => appendMeetingTranscriptSegment(prev, segment));
        postHostMessage("embed:transcript-final", { segment });
      },
      onFinished: async ({ asrText }) => {
        if (!shouldSummarizeRef.current) return;
        shouldSummarizeRef.current = false;
        await summarizeTranscript(asrText);
      },
    });
  };

  const summarizeTranscript = async (fallbackText = "") => {
    const activeSession = session ?? await createSession();
    let segments = transcriptRef.current.filter((segment) => segment.text.trim());
    if (segments.length === 0 && fallbackText.trim()) {
      segments = [buildSegment(fallbackText)];
      transcriptRef.current = segments;
      setTranscript(segments);
    }
    if (segments.length === 0) {
      setStatus("error");
      setNotice("没有可生成纪要的转写内容。");
      return;
    }
    setStatus("summarizing");
    setNotice("正在生成会议纪要...");
    try {
      const summarized = await requestJson<SessionView & { skillName?: string }>(
        `/embed/v1/apps/${APP_CODE}/sessions/${encodeURIComponent(activeSession.sessionId)}/summary`,
        {
          method: "POST",
          body: JSON.stringify({
            title: recordName || customerName || "会议纪要",
            transcript: segments.map((segment) => ({
              speakerId: segment.speakerId,
              speakerName: segment.speakerName,
              text: segment.text,
              startMs: segment.startMs,
              endMs: segment.endMs,
            })),
          }),
        },
      );
      setSession(summarized);
      setSummary(summarized.summary ?? "");
      setStatus("ready_to_writeback");
      setNotice(`${summarized.skillName || "AI 听记"}已生成会议纪要。`);
      postHostMessage("embed:summary-generated", { summary: summarized.summary ?? "", sessionId: activeSession.sessionId });
      await loadWritebackPreview(activeSession.sessionId);
    } catch (error) {
      setStatus("error");
      const message = error instanceof Error ? error.message : String(error);
      setNotice(`会议纪要生成失败：${message}`);
      postHostMessage("embed:error", { message });
    }
  };

  const loadWritebackPreview = async (sessionId: string) => {
    try {
      const data = await requestJson<SessionView & { preview?: { items?: MeetingPanelWritebackItem[] } }>(
        `/embed/v1/apps/${APP_CODE}/sessions/${encodeURIComponent(sessionId)}/writeback-preview`,
        { method: "POST" },
      );
      const items = data.preview?.items ?? data.writebackPreview?.items ?? [];
      setWritebackItems(items);
      setSelectedWritebackItemIds(items.map((item) => item.id));
      postHostMessage("embed:writeback-preview", { items });
    } catch (error) {
      setNotice(`会议纪要已生成，写回候选暂不可用：${error instanceof Error ? error.message : String(error)}`);
    }
  };

  const confirmWriteback = async () => {
    if (!session?.sessionId || selectedWritebackItemIds.length === 0) {
      return;
    }
    setStatus("writing_back");
    setNotice("正在确认写回候选...");
    try {
      const data = await requestJson<SessionView & { writeback?: { status?: string; message?: string } }>(
        `/embed/v1/apps/${APP_CODE}/sessions/${encodeURIComponent(session.sessionId)}/writeback`,
        {
          method: "POST",
          body: JSON.stringify({ selectedItemIds: selectedWritebackItemIds }),
        },
      );
      const result = data.writeback ?? {};
      setSession(data);
      setStatus(result.status === "FAILED" ? "error" : "done");
      setWritebackResultMessage(result.message || "写回请求已记录。");
      setNotice(result.status === "FAILED" ? `写回失败：${result.message || "请稍后重试。"}` : "写回完成。");
      postHostMessage("embed:writeback-success", { result, selectedItemIds: selectedWritebackItemIds });
    } catch (error) {
      setStatus("error");
      const message = error instanceof Error ? error.message : String(error);
      setNotice(`写回失败：${message}`);
      postHostMessage("embed:error", { message });
    }
  };

  const closeEmbed = () => {
    if (status === "recording" || status === "stopping" || listening) {
      const confirmed = window.confirm("会议仍在录音中，关闭会停止录音且不生成纪要。");
      if (!confirmed) return;
      shouldSummarizeRef.current = false;
      stop();
    }
    postHostMessage("embed:close", { sessionId: session?.sessionId ?? "" });
  };

  const primaryAction = () => {
    if (status === "recording") {
      setStatus("stopping");
      setNotice("正在结束录音...");
      stop();
      return;
    }
    if (status === "error" && transcriptRef.current.length > 0) {
      shouldSummarizeRef.current = false;
      void summarizeTranscript();
      return;
    }
    if (status === "ready_to_writeback" && transcriptRef.current.length > 0) {
      void summarizeTranscript();
      return;
    }
    void startRecording();
  };

  const startSpeakerEdit = (lineId: string, speakerId: string, speakerName: string) => {
    setSpeakerEdit({ lineId, speakerId, value: speakerNames[speakerId] || speakerName || speakerDisplayName(speakerId) });
  };

  const commitSpeakerEdit = () => {
    if (!speakerEdit) return;
    const nextName = speakerEdit.value.trim() || speakerDisplayName(speakerEdit.speakerId);
    speakerNamesRef.current = { ...speakerNamesRef.current, [speakerEdit.speakerId]: nextName };
    setSpeakerNames((prev) => ({ ...prev, [speakerEdit.speakerId]: nextName }));
    updateTranscript((prev) => prev.map((segment) => segment.speakerId === speakerEdit.speakerId ? { ...segment, speakerName: nextName } : segment));
    setPartial((prev) => prev?.speakerId === speakerEdit.speakerId ? { ...prev, speakerName: nextName } : prev);
    setSpeakerEdit(null);
  };

  const handleSpeakerEditKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "Enter") {
      event.preventDefault();
      commitSpeakerEdit();
    }
    if (event.key === "Escape") {
      event.preventDefault();
      setSpeakerEdit(null);
    }
  };

  const toggleWritebackItem = (itemId: string) => {
    setSelectedWritebackItemIds((prev) => prev.includes(itemId) ? prev.filter((item) => item !== itemId) : [...prev, itemId]);
  };

  return (
    <main className="cici-embed-page">
      <section className="cici-meeting-drawer cici-meeting-drawer--embed is-open" aria-label="嵌入式会议纪要">
        <MeetingMinutesPanel
          eyebrow="EMBEDDED MEETING"
          title="会议纪要"
          recordName={recordName}
          customerName={customerName}
          status={status}
          notice={notice}
          transcript={transcript}
          partial={partial}
          summary={summary}
          speakerEdit={speakerEdit}
          transcriptScrollRef={transcriptScrollRef}
          speakerEditInputRef={speakerEditInputRef}
          onClose={closeEmbed}
          onSecondaryAction={closeEmbed}
          secondaryActionLabel="关闭"
          onPrimaryAction={primaryAction}
          primaryActionLabel={primaryLabel(status)}
          primaryActionDisabled={status === "permission" || status === "stopping" || status === "summarizing" || status === "writing_back"}
          onSpeakerEditStart={startSpeakerEdit}
          onSpeakerEditValueChange={(value) => setSpeakerEdit((prev) => prev ? { ...prev, value } : prev)}
          onSpeakerEditCommit={commitSpeakerEdit}
          onSpeakerEditKeyDown={handleSpeakerEditKeyDown}
          writebackItems={writebackItems}
          selectedWritebackItemIds={selectedWritebackItemIds}
          onToggleWritebackItem={toggleWritebackItem}
          onConfirmWriteback={confirmWriteback}
          confirmWritebackDisabled={status === "writing_back" || !selectedWritebackItemIds.length}
          writebackResultMessage={writebackResultMessage}
        />
      </section>
    </main>
  );
}

function primaryLabel(status: MeetingPanelStatus): string {
  if (status === "recording") return "结束并生成纪要";
  if (status === "error") return "重试生成纪要";
  if (status === "ready_to_writeback") return "重新生成纪要";
  if (status === "done") return "重新开始";
  return "开始听记";
}

function decodeClaims(token: string): EmbedClaims | null {
  if (!token || !token.includes(".")) {
    return null;
  }
  try {
    const payload = token.split(".")[1] ?? "";
    const base64 = payload.replaceAll("-", "+").replaceAll("_", "/").padEnd(Math.ceil(payload.length / 4) * 4, "=");
    const raw = window.atob(base64);
    const bytes = Uint8Array.from(raw, (char) => char.charCodeAt(0));
    return JSON.parse(new TextDecoder().decode(bytes)) as EmbedClaims;
  } catch {
    try {
      return JSON.parse(window.atob(token.split(".")[1] ?? "")) as EmbedClaims;
    } catch {
      return null;
    }
  }
}

function stringValue(value: unknown): string {
  return typeof value === "string" ? value : "";
}
