export type StreamChatBody = {
  sessionId: string;
  question: string;
  knowledgeBaseIds: string[];
  agentId?: string;
  /** When set, authorizes skill-scoped tools for this skill code for the session (see backend permission model). */
  activeSkillCode?: string;
};

export type StreamToolResultEvent = {
  toolName: string;
  payload: string;
};

export type StreamToolCallEvent = {
  toolName: string;
};

export type StreamPhaseEvent = {
  phase: "generating" | string;
};

export type SessionUpdateEvent = {
  sessionId?: string;
  scope?: "org" | "user";
  trigger?: "user_message" | "assistant_message";
  updatedAt?: string;
};

/** Align with backend AssistantContentSanitizer: drop visible CoT blocks from the final assistant message. */
export function stripThinkingSections(text: string): string {
  if (!text) {
    return text;
  }
  let cleaned = text.replace(
    /\n?#+\s*(Thinking\s*Process|思考过程|Chain[- ]?of[- ]?Thought|思维链|分析过程)[:：]?[^\n]*\n[\s\S]*?(?=\n#+\s|$)/gi,
    "\n",
  );
  cleaned = cleaned.replace(
    /\n?\*\*Thinking\s*Process\*\*[:：]?[^\n]*\n[\s\S]*?(?=\n#+\s|$)/gi,
    "\n",
  );
  return cleaned.replace(/\n{3,}/g, "\n\n").trim();
}

type ParsedSseEvent = {
  eventName: string;
  data: string;
};

async function consumeEventStream(
  stream: ReadableStream<Uint8Array>,
  onEvent: (event: ParsedSseEvent) => Promise<void> | void,
): Promise<void> {
  const reader = stream.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  while (true) {
    const { done, value } = await reader.read();
    if (done) {
      return;
    }
    buffer += decoder.decode(value, { stream: true });
    let sep: number;
    while ((sep = buffer.indexOf("\n\n")) >= 0) {
      const rawBlock = buffer.slice(0, sep);
      buffer = buffer.slice(sep + 2);
      const block = rawBlock.endsWith("\r") ? rawBlock.slice(0, -1) : rawBlock;
      let eventName = "message";
      const dataLines: string[] = [];
      for (const line of block.split("\n")) {
        const normalized = line.endsWith("\r") ? line.slice(0, -1) : line;
        if (!normalized || normalized.startsWith(":")) {
          continue;
        }
        if (normalized.startsWith("event:")) {
          eventName = normalized.slice(6).trim();
        } else if (normalized.startsWith("data:")) {
          dataLines.push(normalized.slice(5).trimStart());
        }
      }
      await onEvent({ eventName, data: dataLines.join("\n") });
    }
  }
}

/**
 * POST /ai/chat/stream (SSE). Invokes onDelta for each text fragment; resolves on `done` or rejects on `error` / HTTP failure.
 */
export async function streamAiChat(
  token: string,
  body: StreamChatBody,
  onDelta: (text: string) => void,
  onToolResult?: (event: StreamToolResultEvent) => void,
  onToolCall?: (event: StreamToolCallEvent) => void,
  onPhase?: (event: StreamPhaseEvent) => void,
): Promise<void> {
  const res = await fetch("/ai/chat/stream", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
      Accept: "text/event-stream",
    },
    body: JSON.stringify(body),
  });

  if (!res.ok) {
    const t = await res.text();
    throw new Error(t || `HTTP ${res.status}`);
  }

  const stream = res.body;
  if (!stream) {
    throw new Error("响应体为空");
  }

  await consumeEventStream(stream, async ({ eventName, data }) => {
    if (eventName === "delta") {
      try {
        const parsed = JSON.parse(data) as { text?: string };
        if (parsed.text) {
          onDelta(parsed.text);
          // Yield to the macrotask queue so React can flush this state update
          // before the next delta arrives, giving true character-by-character rendering.
          await new Promise<void>((r) => setTimeout(r, 0));
        }
      } catch {
        /* ignore malformed chunk */
      }
      return;
    }
    if (eventName === "done") {
      return;
    }
    if (eventName === "tool_call") {
      try {
        const parsed = JSON.parse(data) as { toolName?: string };
        if (parsed.toolName && onToolCall) {
          onToolCall({ toolName: parsed.toolName });
        }
      } catch {
        /* ignore */
      }
      return;
    }
    if (eventName === "phase") {
      try {
        const parsed = JSON.parse(data) as { phase?: string };
        if (parsed.phase && onPhase) {
          onPhase({ phase: parsed.phase });
        }
      } catch {
        /* ignore */
      }
      return;
    }
    if (eventName === "tool_result") {
      try {
        const parsed = JSON.parse(data) as { toolName?: string; payload?: string };
        if (parsed.toolName && typeof parsed.payload === "string" && onToolResult) {
          onToolResult({ toolName: parsed.toolName, payload: parsed.payload });
        }
      } catch {
        /* ignore malformed tool event */
      }
      return;
    }
    if (eventName === "error") {
      let message = data;
      try {
        const parsed = JSON.parse(data) as { message?: string };
        if (parsed.message) {
          message = parsed.message;
        }
      } catch {
        /* use raw */
      }
      throw new Error(message);
    }
  });
}

export async function streamSessionUpdates(
  token: string,
  onUpdate: (event: SessionUpdateEvent) => Promise<void> | void,
  signal?: AbortSignal,
): Promise<void> {
  const res = await fetch("/ai/sessions/stream", {
    method: "GET",
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: "text/event-stream",
    },
    signal,
  });

  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || `HTTP ${res.status}`);
  }

  const stream = res.body;
  if (!stream) {
    throw new Error("响应体为空");
  }

  await consumeEventStream(stream, async ({ eventName, data }) => {
    if (eventName === "connected") {
      return;
    }
    if (eventName !== "session_updated") {
      return;
    }
    try {
      await onUpdate(JSON.parse(data) as SessionUpdateEvent);
    } catch {
      /* ignore malformed realtime event */
    }
  });
}
