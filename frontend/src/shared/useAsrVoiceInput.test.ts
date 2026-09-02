import { describe, expect, it } from "vitest";
import {
  asrStatusNotice,
  ensureAudioContextRunning,
  extractAsrMessageText,
  isAsrAuthenticatedMessage,
  isAsrStartedMessage,
  mergePrefixAsr,
  waitForAsrAuthenticated,
  waitForAsrStarted,
} from "./useAsrVoiceInput";

describe("useAsrVoiceInput helpers", () => {
  it("merges existing prefix and recognized speech text", () => {
    expect(mergePrefixAsr("帮我", "看看今天的邮件")).toBe("帮我 看看今天的邮件");
    expect(mergePrefixAsr("帮我 ", "看看今天的邮件")).toBe("帮我 看看今天的邮件");
    expect(mergePrefixAsr("", "看看今天的邮件")).toBe("看看今天的邮件");
  });

  it("extracts text from common ASR websocket message shapes", () => {
    expect(extractAsrMessageText({ type: "final", text: "直接文本" })).toBe("直接文本");
    expect(extractAsrMessageText({ type: "partial", transcript: "转写文本" })).toBe("转写文本");
    expect(extractAsrMessageText({ type: "final", result: "结果文本" })).toBe("结果文本");
    expect(extractAsrMessageText({ type: "partial", payload: { text: "payload 文本" } })).toBe("payload 文本");
    expect(extractAsrMessageText({ type: "partial", payload: { output: { sentence: { text: "阿里云句子" } } } })).toBe(
      "阿里云句子",
    );
    expect(extractAsrMessageText({ type: "final", sentence: { text: "句子文本" } })).toBe("句子文本");
    expect(extractAsrMessageText({ type: "final", data: { output: { sentence: { text: "嵌套数据文本" } } } })).toBe(
      "嵌套数据文本",
    );
  });

  it("detects ASR started status exactly", () => {
    expect(isAsrStartedMessage({ type: "status", message: "started" })).toBe(true);
    expect(isAsrStartedMessage({ type: "status", message: "connected" })).toBe(false);
  });

  it("distinguishes websocket authentication from upstream readiness", () => {
    expect(isAsrAuthenticatedMessage({ type: "status", message: "authenticated" })).toBe(true);
    expect(isAsrAuthenticatedMessage({ type: "status", message: "connected" })).toBe(false);
    expect(isAsrAuthenticatedMessage({ type: "status", message: "started" })).toBe(false);
  });

  it("explains when realtime speaker diarization is unavailable", () => {
    expect(asrStatusNotice({ type: "status", message: "speaker-diarization-unavailable" })).toContain(
      "无法自动区分发言人",
    );
    expect(asrStatusNotice({ type: "status", message: "connected" })).toBe("");
  });

  it("waits for the upstream started signal instead of treating the browser socket as ready", async () => {
    const socket = new EventTarget();
    const ready = waitForAsrStarted(socket as unknown as WebSocket, 1000);

    socket.dispatchEvent(new MessageEvent("message", { data: JSON.stringify({ type: "status", message: "connected" }) }));
    socket.dispatchEvent(new MessageEvent("message", { data: JSON.stringify({ type: "status", message: "started" }) }));

    await expect(ready).resolves.toBeUndefined();
  });

  it("surfaces an upstream startup error before microphone capture begins", async () => {
    const socket = new EventTarget();
    const ready = waitForAsrStarted(socket as unknown as WebSocket, 1000);

    socket.dispatchEvent(new MessageEvent("message", { data: JSON.stringify({ type: "error", message: "invalid endpoint" }) }));

    await expect(ready).rejects.toThrow("invalid endpoint");
  });

  it("waits for websocket authentication before starting an upstream provider", async () => {
    const socket = new EventTarget();
    const ready = waitForAsrAuthenticated(socket as unknown as WebSocket, 1000);

    socket.dispatchEvent(new MessageEvent("message", { data: JSON.stringify({ type: "status", message: "connected" }) }));
    socket.dispatchEvent(new MessageEvent("message", { data: JSON.stringify({ type: "status", message: "authenticated" }) }));

    await expect(ready).resolves.toBeUndefined();
  });

  it("resumes a suspended audio context before declaring the microphone ready", async () => {
    const context = {
      state: "suspended" as AudioContextState,
      resume: async () => {
        context.state = "running";
      },
    };

    await expect(ensureAudioContextRunning(context)).resolves.toBeUndefined();
    expect(context.state).toBe("running");
  });

  it("fails explicitly when the browser refuses to run the audio context", async () => {
    const context = {
      state: "suspended" as AudioContextState,
      resume: async () => undefined,
    };

    await expect(ensureAudioContextRunning(context)).rejects.toThrow("麦克风音频流未启动");
  });
});
