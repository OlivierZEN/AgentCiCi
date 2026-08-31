type PhasePayload = Record<string, unknown>;

export function sisiPhaseLabel(payload: PhasePayload): string {
  const phase = String(payload.phase ?? "").trim();
  const outputMode = String(payload.outputMode ?? "").trim();
  const shouldPlan = payload.shouldPlan === true;

  switch (phase) {
    case "run":
    case "model":
      return "正在理解问题";
    case "retrieving":
      return "正在检索可信知识";
    case "rag_done":
      return "已完成知识检索";
    case "tool_routing":
      return shouldPlan ? "正在选择所需工具" : "正在组织回复";
    case "generating":
      return outputMode === "buffered" ? "正在完成安全校验" : "正在生成回复";
    case "runtime_completed":
      return "正在整理执行结果";
    case "review_completed":
      return "正在复核回复";
    default:
      return "正在处理";
  }
}
