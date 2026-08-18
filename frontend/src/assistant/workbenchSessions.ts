type WorkbenchHistoryLine = {
  role: "user" | "assistant";
  content: string;
  time?: string;
};

/**
 * Browser-only cache key. Server-issued UUIDs are scoped again in memory so a
 * company switch cannot reuse stale React state from another authenticated tenant.
 */
export function buildCompanyScopedCacheKey(companyId: string | undefined, key: string): string {
  return `${companyId?.trim() || "__no_company__"}::${key}`;
}

export function pickRecentHistoryLines(
  messages: WorkbenchHistoryLine[],
  limit = 8,
): WorkbenchHistoryLine[] {
  if (limit <= 0) {
    return [];
  }
  return messages
    .filter((message) => message.content.trim())
    .slice(-limit);
}
