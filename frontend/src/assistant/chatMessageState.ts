import type { DeliveryWriteReceipt } from "./deliveryWriteReceipt";

export type ChatMessageBubble = {
  role: "user" | "assistant";
  content: string;
  time?: string;
  modelName?: string;
  deliveryReceipt?: DeliveryWriteReceipt;
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

export function attachTrailingAssistantReceipt(
  messages: ChatMessageBubble[],
  deliveryReceipt: DeliveryWriteReceipt,
  time: string,
): ChatMessageBubble[] {
  const next = [...messages];
  const last = next[next.length - 1];
  if (last?.role === "assistant") {
    next[next.length - 1] = { ...last, deliveryReceipt };
  } else {
    next.push({ role: "assistant", content: "", time, deliveryReceipt });
  }
  return next;
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

export function assistantResponseNeedsUserFollowup(content: string): boolean {
  const normalized = content.replace(/\s+/g, "");
  if (!normalized) {
    return false;
  }
  return /参数(问题|错误|缺失|不正确)|缺少必需参数|查询失败|调用失败|执行失败|无法|未能|出错|错误|请补充|请确认|需要(你|您)?(提供|补充|确认)|未找到|无权限|令牌.*失败/.test(normalized)
    || /工具已返回.*模型本轮未能生成最终自然语言总结|本次工具调用已完成.*模型本轮未能生成/.test(normalized)
    || /后续(继续|再|将)?(处理|查询|检索|调用|获取|抽取|整理|分析|生成|补充|展示|展现|输出)/.test(normalized)
    || /接下来.*(我|会|将|再).*(查询|检索|调用|获取|处理|尝试|抽取|整理|分析|生成|补充|展示|展现|输出)/.test(normalized)
    || /(让我|我来|我会|我再|将)(继续|重新|再)?(查询|检索|调用|获取|处理|尝试|抽取|整理|分析|生成|补充|展示|展现|输出)/.test(normalized)
    || /(继续|重新|再)(查询|检索|调用|获取|处理|尝试|抽取|整理|分析|生成|补充|展示|展现|输出)/.test(normalized);
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
