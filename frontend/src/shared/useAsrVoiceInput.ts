import { useCallback, useEffect, useRef, useState } from "react";
import { downsampleTo16k } from "./asrPcm";

const ASR_STOP_CLOSE_GRACE_MS = 1500;
const ASR_START_TIMEOUT_MS = 8000;

export function mergePrefixAsr(prefix: string, asr: string): string {
  const a = asr.trim();
  const p = prefix;
  if (!a) return p;
  if (!p.trim()) return a;
  const join = /\s$/.test(p) ? "" : " ";
  return `${p}${join}${a}`;
}

export type AsrWsMessage = {
  type?: string;
  text?: unknown;
  message?: string;
  speakerId?: string;
  speakerName?: string;
  transcript?: unknown;
  result?: unknown;
  payload?: unknown;
  sentence?: unknown;
  data?: unknown;
};

function objectValue(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value) ? (value as Record<string, unknown>) : null;
}

function stringValue(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function firstNonBlank(...values: unknown[]): string {
  for (const value of values) {
    const text = stringValue(value).trim();
    if (text) {
      return text;
    }
  }
  return "";
}

export function extractAsrMessageText(message: AsrWsMessage): string {
  const payload = objectValue(message.payload);
  const sentence = objectValue(message.sentence);
  const data = objectValue(message.data);
  const payloadOutput = objectValue(payload?.output);
  const payloadSentence = objectValue(payloadOutput?.sentence) ?? objectValue(payload?.sentence);
  const dataOutput = objectValue(data?.output);
  const dataSentence = objectValue(dataOutput?.sentence) ?? objectValue(data?.sentence);
  const result = objectValue(message.result);
  const resultSentence = objectValue(result?.sentence);

  return firstNonBlank(
    message.text,
    message.transcript,
    message.result,
    payload?.text,
    payloadSentence?.text,
    sentence?.text,
    data?.text,
    data?.transcript,
    dataSentence?.text,
    result?.text,
    result?.transcript,
    resultSentence?.text,
  );
}

export function isAsrStartedMessage(message: AsrWsMessage): boolean {
  return message.type === "status" && message.message === "started";
}

export function asrStatusNotice(message: AsrWsMessage): string {
  if (message.type === "status" && message.message === "speaker-diarization-unavailable") {
    return "实时听写中...（当前组织未配置讯飞实时转写，本次无法自动区分发言人）";
  }
  return "";
}

type AsrLifecycleSocket = Pick<WebSocket, "addEventListener" | "removeEventListener">;

export function waitForAsrStarted(
  websocket: AsrLifecycleSocket,
  timeoutMs = ASR_START_TIMEOUT_MS,
): Promise<void> {
  return new Promise((resolve, reject) => {
    let settled = false;
    const cleanup = () => {
      globalThis.clearTimeout(timeout);
      websocket.removeEventListener("message", onMessage as EventListener);
      websocket.removeEventListener("error", onError as EventListener);
      websocket.removeEventListener("close", onClose as EventListener);
    };
    const settle = (callback: () => void) => {
      if (settled) return;
      settled = true;
      cleanup();
      callback();
    };
    const onMessage = (event: MessageEvent) => {
      try {
        const message = JSON.parse(String(event.data)) as AsrWsMessage;
        if (isAsrStartedMessage(message)) {
          settle(resolve);
        } else if (message.type === "error") {
          settle(() => reject(new Error(message.message || "实时语音服务启动失败")));
        }
      } catch {
        // Ignore unrelated or malformed messages while waiting for the upstream ready signal.
      }
    };
    const onError = () => settle(() => reject(new Error("实时语音服务连接失败")));
    const onClose = () => settle(() => reject(new Error("实时语音服务已关闭")));
    const timeout = globalThis.setTimeout(() => {
      settle(() => reject(new Error("实时语音服务启动超时")));
    }, timeoutMs);

    websocket.addEventListener("message", onMessage as EventListener);
    websocket.addEventListener("error", onError as EventListener, { once: true });
    websocket.addEventListener("close", onClose as EventListener, { once: true });
  });
}

export type AsrTranscriptEvent = {
  type: "partial" | "final";
  text: string;
  speakerId?: string;
  speakerName?: string;
};

export type AsrVoiceStartOptions = {
  token: string;
  /** Text before this session (e.g. existing field). Workbench passes () => "". */
  getPrefix: () => string;
  /** Live full text = merge(prefix, partial+final ASR). */
  onLiveText: (fullText: string) => void;
  onNotice: (msg: string) => void;
  onTranscriptEvent?: (event: AsrTranscriptEvent) => void;
  /** Invoked when the ASR WebSocket has closed. */
  onFinished?: (p: { asrText: string; fullText: string }) => void | Promise<void>;
  /** Stop automatically after this many milliseconds without audible input. */
  autoStopAfterNoSpeechMs?: number;
  provider?: "aliyun" | "iflytek" | "auto";
  speakerDiarization?: boolean;
};

/**
 * Real-time speech → text via backend `/ws/asr` (same pipeline as assistant workbench).
 */
export function useAsrVoiceInput() {
  const [listening, setListening] = useState(false);
  const [speechSupported, setSpeechSupported] = useState(false);

  const asrWsRef = useRef<WebSocket | null>(null);
  const audioContextRef = useRef<AudioContext | null>(null);
  const sourceNodeRef = useRef<MediaStreamAudioSourceNode | null>(null);
  const processorNodeRef = useRef<ScriptProcessorNode | null>(null);
  const mediaStreamRef = useRef<MediaStream | null>(null);
  const finalAsrTextRef = useRef("");
  const partialAsrTextRef = useRef("");
  const prefixSnapshotRef = useRef("");
  const silenceTimerRef = useRef<number | null>(null);
  const silenceTimeoutMsRef = useRef<number | null>(null);
  const liveHandlerRef = useRef<(text: string) => void>(() => {});
  const noticeHandlerRef = useRef<(msg: string) => void>(() => {});
  const transcriptHandlerRef = useRef<(event: AsrTranscriptEvent) => void>(() => {});
  const finishHandlerRef = useRef<(p: { asrText: string; fullText: string }) => void | Promise<void>>(async () => {});
  const asrReadyRef = useRef(false);
  const finishCallbackEnabledRef = useRef(false);
  const sessionStatusNoticeRef = useRef("");

  useEffect(() => {
    try {
      const w = window;
      setSpeechSupported(
        Boolean(
          w.navigator?.mediaDevices &&
            "WebSocket" in w &&
            ("AudioContext" in w || "webkitAudioContext" in w),
        ),
      );
    } catch {
      setSpeechSupported(false);
    }
  }, []);

  const disconnectAudio = useCallback(() => {
    try {
      processorNodeRef.current?.disconnect();
      sourceNodeRef.current?.disconnect();
    } catch {
      /* ignore */
    }
    processorNodeRef.current = null;
    sourceNodeRef.current = null;
    if (audioContextRef.current) {
      void audioContextRef.current.close();
      audioContextRef.current = null;
    }
    if (mediaStreamRef.current) {
      mediaStreamRef.current.getTracks().forEach((track) => track.stop());
      mediaStreamRef.current = null;
    }
  }, []);

  const clearSilenceTimer = useCallback(() => {
    if (silenceTimerRef.current != null) {
      window.clearTimeout(silenceTimerRef.current);
      silenceTimerRef.current = null;
    }
  }, []);

  const pushLive = () => {
    const asr = (finalAsrTextRef.current + partialAsrTextRef.current).trim();
    const full = mergePrefixAsr(prefixSnapshotRef.current, asr);
    if (full) {
      liveHandlerRef.current(full);
    }
  };

  const finishSession = useCallback(async () => {
    clearSilenceTimer();
    asrReadyRef.current = false;
    disconnectAudio();
    setListening(false);
    const shouldRunFinished = finishCallbackEnabledRef.current;
    finishCallbackEnabledRef.current = false;
    if (!shouldRunFinished) {
      return;
    }
    const asrText = (finalAsrTextRef.current + partialAsrTextRef.current).trim();
    const fullText = mergePrefixAsr(prefixSnapshotRef.current, asrText);
    if (fullText) {
      liveHandlerRef.current(fullText);
    }
    await finishHandlerRef.current({ asrText, fullText });
  }, [clearSilenceTimer, disconnectAudio]);

  const stop = useCallback(() => {
    clearSilenceTimer();
    setListening(false);
    const websocket = asrWsRef.current;
    asrReadyRef.current = false;
    disconnectAudio();
    if (websocket && websocket.readyState === 1) {
      websocket.send(JSON.stringify({ type: "stop" }));
    }
    if (websocket) {
      window.setTimeout(() => {
        if (websocket.readyState === 0 || websocket.readyState === 1) {
          websocket.close();
        }
        void finishSession();
      }, ASR_STOP_CLOSE_GRACE_MS);
    } else {
      void finishSession();
    }
  }, [clearSilenceTimer, disconnectAudio, finishSession]);

  const abort = useCallback(() => {
    clearSilenceTimer();
    finishCallbackEnabledRef.current = false;
    try {
      asrWsRef.current?.close();
    } catch {
      /* ignore */
    }
    asrWsRef.current = null;
    asrReadyRef.current = false;
    disconnectAudio();
    setListening(false);
  }, [clearSilenceTimer, disconnectAudio]);

  const armSilenceTimer = useCallback(() => {
    clearSilenceTimer();
    const timeoutMs = silenceTimeoutMsRef.current;
    if (!timeoutMs || timeoutMs <= 0) {
      return;
    }
    silenceTimerRef.current = window.setTimeout(() => {
      noticeHandlerRef.current("5 秒未检测到语音，已结束识别。");
      stop();
    }, timeoutMs);
  }, [clearSilenceTimer, stop]);

  useEffect(() => {
    return () => {
      abort();
    };
  }, [abort]);

  const start = useCallback(
    async (options: AsrVoiceStartOptions) => {
      if (asrWsRef.current) {
        abort();
      }
      if (!speechSupported) {
        options.onNotice("当前浏览器不支持录音。");
        return;
      }
      liveHandlerRef.current = options.onLiveText;
      noticeHandlerRef.current = options.onNotice;
      transcriptHandlerRef.current = options.onTranscriptEvent ?? (() => {});
      finishHandlerRef.current = options.onFinished ?? (async () => {});
      prefixSnapshotRef.current = options.getPrefix();
      silenceTimeoutMsRef.current =
        typeof options.autoStopAfterNoSpeechMs === "number" && options.autoStopAfterNoSpeechMs > 0
          ? options.autoStopAfterNoSpeechMs
          : null;
      finalAsrTextRef.current = "";
      partialAsrTextRef.current = "";
      asrReadyRef.current = false;
      finishCallbackEnabledRef.current = false;
      sessionStatusNoticeRef.current = "";

      try {
        const websocket = new WebSocket(
          `${location.protocol === "https:" ? "wss" : "ws"}://${location.host}/ws/asr?token=${encodeURIComponent(options.token)}&provider=${encodeURIComponent(options.provider ?? "aliyun")}&speakerDiarization=${options.speakerDiarization ? "true" : "false"}`,
        );
        websocket.binaryType = "arraybuffer";
        asrWsRef.current = websocket;

        websocket.onmessage = (event) => {
          try {
            const message = JSON.parse(String(event.data)) as AsrWsMessage;
            const text = extractAsrMessageText(message);
            if (message.type === "partial") {
              partialAsrTextRef.current = text;
              if (partialAsrTextRef.current.trim()) {
                armSilenceTimer();
                transcriptHandlerRef.current({
                  type: "partial",
                  text: partialAsrTextRef.current,
                  speakerId: message.speakerId,
                  speakerName: message.speakerName,
                });
              }
              pushLive();
            } else if (message.type === "final") {
              finalAsrTextRef.current += text;
              partialAsrTextRef.current = "";
              if (text.trim()) {
                armSilenceTimer();
                transcriptHandlerRef.current({
                  type: "final",
                  text,
                  speakerId: message.speakerId,
                  speakerName: message.speakerName,
                });
              }
              pushLive();
            } else if (message.type === "error") {
              noticeHandlerRef.current(`实时识别失败：${message.message ?? "unknown"}`);
            } else if (isAsrStartedMessage(message)) {
              asrReadyRef.current = true;
            } else if (message.type === "status") {
              const statusNotice = asrStatusNotice(message);
              if (statusNotice) {
                sessionStatusNoticeRef.current = statusNotice;
                noticeHandlerRef.current(statusNotice);
              }
            } else if (message.type === "finished") {
              asrReadyRef.current = false;
              disconnectAudio();
              setListening(false);
              if (websocket.readyState === 0 || websocket.readyState === 1) {
                websocket.close(1000, "asr finished");
              }
              void finishSession();
            }
          } catch {
            /* ignore malformed */
          }
        };
        websocket.onerror = () => {
          noticeHandlerRef.current("实时语音连接异常，请重试。");
        };
        websocket.onclose = async () => {
          asrWsRef.current = null;
          await finishSession();
        };

        await new Promise<void>((resolve, reject) => {
          let settled = false;
          const finish = (callback: () => void) => {
            if (settled) {
              return;
            }
            settled = true;
            window.clearTimeout(timeout);
            callback();
          };
          const timeout = window.setTimeout(() => {
            finish(() => reject(new Error("实时语音服务连接超时")));
          }, 8000);
          websocket.onopen = () => finish(resolve);
          websocket.addEventListener("error", () => {
            finish(() => reject(new Error("实时语音服务连接失败")));
          }, { once: true });
          websocket.addEventListener("close", () => {
            finish(() => reject(new Error("实时语音服务已关闭")));
          }, { once: true });
        });

        websocket.send(JSON.stringify({
          type: "start",
          sampleRate: 16000,
          provider: options.provider ?? "aliyun",
          speakerDiarization: Boolean(options.speakerDiarization),
        }));
        await waitForAsrStarted(websocket);
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        mediaStreamRef.current = stream;
        if (websocket.readyState !== 1 || !asrReadyRef.current) {
          throw new Error("实时语音服务已关闭");
        }

        const AudioContextCtor = (window.AudioContext ||
          (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext) as
          | (new () => AudioContext)
          | undefined;
        if (!AudioContextCtor) {
          throw new Error("不支持AudioContext");
        }

        const context = new AudioContextCtor();
        audioContextRef.current = context;
        const source = context.createMediaStreamSource(stream);
        sourceNodeRef.current = source;
        const processor = context.createScriptProcessor(4096, 1, 1);
        processorNodeRef.current = processor;
        processor.onaudioprocess = (ev) => {
          const currentSocket = asrWsRef.current;
          if (!currentSocket || currentSocket.readyState !== 1 || !asrReadyRef.current) {
            return;
          }
          const channelData = ev.inputBuffer.getChannelData(0);
          for (let i = 0; i < channelData.length; i += 32) {
            if (Math.abs(channelData[i] ?? 0) > 0.018) {
              armSilenceTimer();
              break;
            }
          }
          const downsampled = downsampleTo16k(channelData, context.sampleRate);
          if (downsampled.byteLength > 0) {
            currentSocket.send(downsampled);
          }
        };
        source.connect(processor);
        processor.connect(context.destination);

        finishCallbackEnabledRef.current = true;
        setListening(true);
        noticeHandlerRef.current(sessionStatusNoticeRef.current || "实时听写中...（边说边出字）");
        armSilenceTimer();
      } catch (error) {
        noticeHandlerRef.current(`实时语音启动失败：${error instanceof Error ? error.message : String(error)}`);
        abort();
      }
    },
    [speechSupported, abort, armSilenceTimer, disconnectAudio, finishSession],
  );

  return { listening, speechSupported, start, stop, abort };
}
