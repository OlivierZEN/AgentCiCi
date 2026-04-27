import { useCallback, useEffect, useRef, useState } from "react";
import { downsampleTo16k } from "./asrPcm";

export function mergePrefixAsr(prefix: string, asr: string): string {
  const a = asr.trim();
  const p = prefix;
  if (!a) return p;
  if (!p.trim()) return a;
  const join = /\s$/.test(p) ? "" : " ";
  return `${p}${join}${a}`;
}

type AsrWsMessage = { type?: string; text?: string; message?: string };

export type AsrVoiceStartOptions = {
  token: string;
  /** Text before this session (e.g. existing field). Workbench passes () => "". */
  getPrefix: () => string;
  /** Live full text = merge(prefix, partial+final ASR). */
  onLiveText: (fullText: string) => void;
  onNotice: (msg: string) => void;
  /** Invoked when the ASR WebSocket has closed. */
  onFinished?: (p: { asrText: string; fullText: string }) => void | Promise<void>;
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
  const liveHandlerRef = useRef<(text: string) => void>(() => {});
  const noticeHandlerRef = useRef<(msg: string) => void>(() => {});
  const finishHandlerRef = useRef<(p: { asrText: string; fullText: string }) => void | Promise<void>>(async () => {});

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

  const pushLive = () => {
    const asr = (finalAsrTextRef.current + partialAsrTextRef.current).trim();
    const full = mergePrefixAsr(prefixSnapshotRef.current, asr);
    if (full) {
      liveHandlerRef.current(full);
    }
  };

  const stop = useCallback(() => {
    setListening(false);
    const websocket = asrWsRef.current;
    if (websocket && websocket.readyState === 1) {
      websocket.send(JSON.stringify({ type: "stop" }));
    }
    disconnectAudio();
    if (websocket) {
      window.setTimeout(() => {
        if (websocket.readyState === 0 || websocket.readyState === 1) {
          websocket.close();
        }
      }, 300);
    }
  }, [disconnectAudio]);

  const abort = useCallback(() => {
    try {
      asrWsRef.current?.close();
    } catch {
      /* ignore */
    }
    asrWsRef.current = null;
    disconnectAudio();
    setListening(false);
  }, [disconnectAudio]);

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
      finishHandlerRef.current = options.onFinished ?? (async () => {});
      prefixSnapshotRef.current = options.getPrefix();
      finalAsrTextRef.current = "";
      partialAsrTextRef.current = "";

      try {
        const websocket = new WebSocket(
          `${location.protocol === "https:" ? "wss" : "ws"}://${location.host}/ws/asr?token=${encodeURIComponent(options.token)}`,
        );
        websocket.binaryType = "arraybuffer";
        asrWsRef.current = websocket;

        websocket.onmessage = (event) => {
          try {
            const message = JSON.parse(String(event.data)) as AsrWsMessage;
            if (message.type === "partial") {
              partialAsrTextRef.current = message.text ?? "";
              pushLive();
            } else if (message.type === "final") {
              finalAsrTextRef.current += message.text ?? "";
              partialAsrTextRef.current = "";
              pushLive();
            } else if (message.type === "error") {
              noticeHandlerRef.current(`阿里云实时识别失败：${message.message ?? "unknown"}`);
            }
          } catch {
            /* ignore malformed */
          }
        };
        websocket.onerror = () => {
          noticeHandlerRef.current("实时语音连接异常，请重试。");
        };
        websocket.onclose = async () => {
          disconnectAudio();
          asrWsRef.current = null;
          setListening(false);
          const asrText = (finalAsrTextRef.current + partialAsrTextRef.current).trim();
          const fullText = mergePrefixAsr(prefixSnapshotRef.current, asrText);
          if (fullText) {
            liveHandlerRef.current(fullText);
          }
          await finishHandlerRef.current({ asrText, fullText });
        };

        await new Promise<void>((resolve, reject) => {
          websocket.onopen = () => resolve();
          const timeout = window.setTimeout(() => reject(new Error("超时")), 8000);
          websocket.addEventListener("open", () => window.clearTimeout(timeout), { once: true });
        });

        websocket.send(JSON.stringify({ type: "start", sampleRate: 16000 }));
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        mediaStreamRef.current = stream;

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
          if (!currentSocket || currentSocket.readyState !== 1) {
            return;
          }
          const downsampled = downsampleTo16k(ev.inputBuffer.getChannelData(0), context.sampleRate);
          if (downsampled.byteLength > 0) {
            currentSocket.send(downsampled);
          }
        };
        source.connect(processor);
        processor.connect(context.destination);

        setListening(true);
        noticeHandlerRef.current("实时听写中...（边说边出字）");
      } catch (error) {
        noticeHandlerRef.current(`实时语音启动失败：${error instanceof Error ? error.message : String(error)}`);
        abort();
      }
    },
    [speechSupported, abort, disconnectAudio],
  );

  return { listening, speechSupported, start, stop, abort };
}
