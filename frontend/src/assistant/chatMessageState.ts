export type ChatMessageBubble = {
  role: "user" | "assistant";
  content: string;
  time?: string;
  modelName?: string;
};

export function shouldKeepLocalStreamingMessages(
  local: ChatMessageBubble[],
  remote: ChatMessageBubble[],
): boolean {
  const localLast = local[local.length - 1];
  if (localLast?.role !== "assistant") {
    return false;
  }
  const remoteLast = remote[remote.length - 1];
  if (remoteLast?.role === "assistant" && remoteLast.content.trim()) {
    return false;
  }
  return local.length >= remote.length || Boolean(localLast.content.trim());
}

export function appendAssistantDelta(
  messages: ChatMessageBubble[],
  delta: string,
  time: string,
): ChatMessageBubble[] {
  const next = [...messages];
  const last = next[next.length - 1];
  if (last?.role === "assistant") {
    next[next.length - 1] = { ...last, role: "assistant", content: last.content + delta };
  } else {
    next.push({ role: "assistant", content: delta, time });
  }
  return next;
}

export function replaceTrailingAssistant(
  messages: ChatMessageBubble[],
  content: string,
  time?: string,
): ChatMessageBubble[] {
  const next = [...messages];
  const last = next[next.length - 1];
  if (last?.role === "assistant") {
    next[next.length - 1] = { ...last, role: "assistant", content, time: time ?? last.time };
  } else {
    next.push({ role: "assistant", content, time });
  }
  return next;
}

export function markTrailingAssistantModel(
  messages: ChatMessageBubble[],
  modelName: string,
  time: string,
): ChatMessageBubble[] {
  const cleanModelName = modelName.trim();
  if (!cleanModelName) {
    return messages;
  }
  const next = [...messages];
  const last = next[next.length - 1];
  if (last?.role === "assistant") {
    next[next.length - 1] = { ...last, modelName: cleanModelName };
  } else {
    next.push({ role: "assistant", content: "", time, modelName: cleanModelName });
  }
  return next;
}

export function preserveAssistantModelNames(
  local: ChatMessageBubble[],
  remote: ChatMessageBubble[],
): ChatMessageBubble[] {
  const localAssistantModels = local
    .filter((item) => item.role === "assistant")
    .map((item) => item.modelName?.trim() ?? "");
  let assistantIndex = 0;
  return remote.map((item) => {
    if (item.role !== "assistant") {
      return item;
    }
    const modelName = item.modelName?.trim() || localAssistantModels[assistantIndex] || "";
    assistantIndex += 1;
    return modelName ? { ...item, modelName } : item;
  });
}
