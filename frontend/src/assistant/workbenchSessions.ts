type WorkbenchHistoryLine = {
  role: "user" | "assistant";
  content: string;
  time?: string;
};

export function buildWorkbenchSessionId(agentKey: string): string {
  return `workbench:${agentKey}`;
}

export function buildWorkbenchSessionPrefix(agentKey: string): string {
  return `${buildWorkbenchSessionId(agentKey)}:`;
}

export function createWorkbenchSessionId(agentKey: string): string {
  return `${buildWorkbenchSessionPrefix(agentKey)}${Date.now().toString(36)}`;
}

export function isWorkbenchSessionIdForAgent(sessionId: string, agentKey: string): boolean {
  return sessionId === buildWorkbenchSessionId(agentKey) || sessionId.startsWith(buildWorkbenchSessionPrefix(agentKey));
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
