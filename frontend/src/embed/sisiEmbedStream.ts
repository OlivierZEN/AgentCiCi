function text(value: unknown) {
  return typeof value === "string" ? value : value == null ? "" : String(value);
}

/**
 * Extracts one assistant text fragment from the embed SSE delta payload.
 * The AgentCiCi runtime contract uses `{ text }`; `content` and `delta`
 * remain accepted for compatibility with earlier proxy implementations.
 */
export function streamDeltaText(payload: unknown) {
  if (typeof payload === "string") return payload;
  if (!payload || typeof payload !== "object") return "";
  const record = payload as Record<string, unknown>;
  return text(record.text ?? record.content ?? record.delta);
}
