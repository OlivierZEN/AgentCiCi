import { safeFetchJson } from "../../utils/http";

export type CustomerWorkbenchAccount = {
  accountId: string;
  name: string;
  owner: string;
  segment: "NEW" | "EXISTING" | "STRATEGIC" | "RISK" | string;
  healthScore: number;
  progressScore: number;
  riskCount: number;
  nextActionCount: number;
  pendingRecommendationCount: number;
  opportunityCount?: number;
  interactionCount?: number;
  renewalDays?: number;
  customerMode?: "NEW" | "EXISTING" | string;
  followed?: boolean;
  lastInteraction: string;
  lastInteractionType?: string;
  stage: string;
  tags: string[];
  updatedAt?: string;
  dataAsOf?: string;
  source?: string;
};

export type CustomerQueueResult = {
  items: CustomerWorkbenchAccount[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  filterCounts: Record<string, number>;
  dataAsOf?: string;
  source: string;
  mode: string;
};

export type CustomerInteractionEvent = {
  eventId: string;
  accountId: string;
  sourceType: string;
  occurredAt: string;
  subject: string;
  summary: string;
  sentiment: string;
  intentTags: string[];
  lifecycleArea: string;
};

export type CustomerInteractionAsset = {
  assetId: string;
  inputType: "AUDIO" | "IMAGE" | "DOCUMENT" | string;
  name: string;
  contentType: string;
  size: number;
  sha256: string;
  sortOrder: number;
  status: "STORED" | "PROCESSING" | "READY" | "FAILED" | string;
  extractedText: string;
  errorMessage: string;
};

export type CustomerInteractionAnalysis = {
  summary?: string;
  facts?: string[];
  customerNeeds?: string[];
  risks?: string[];
  opportunities?: string[];
  commitments?: string[];
  nextActions?: string[];
  pendingQuestions?: string[];
  sentiment?: string;
  degraded?: boolean;
};

export type CustomerInteractionBatch = {
  batchId: string;
  accountId: string;
  sourceType: "WECHAT" | "PHONE" | "MEETING" | "CUSTOMER_FEEDBACK" | string;
  occurredAt: string;
  subject: string;
  narrationText: string;
  pastedText: string;
  status: "QUEUED" | "PROCESSING" | "READY" | "PARTIAL" | "FAILED" | "CONFIRMED" | string;
  combinedText: string;
  analysis: CustomerInteractionAnalysis;
  errorMessage: string;
  confirmedEventId: string;
  createdAt: string;
  updatedAt: string;
  assets: CustomerInteractionAsset[];
  deduplicated?: boolean;
  event?: CustomerInteractionEvent;
};

export type CustomerRecommendation = {
  recommendationId: string;
  accountId: string;
  type: string;
  title: string;
  rationale: string;
  confidence: number;
  status: "PENDING" | "ACCEPTED" | "CONFIRMED" | "APPLYING" | "FAILED" | "DISMISSED" | "APPLIED" | string;
  crmPayload: Record<string, unknown>;
  appliedCrmId?: string;
  targetObject?: string;
  targetRecordId?: string;
  evidence?: unknown[];
  dismissalReason?: string;
  lastErrorMessage?: string;
  confirmedAt?: string;
  appliedAt?: string;
  message?: string;
  verified?: boolean;
  readback?: Record<string, unknown>;
  feedback?: { rating: "HELPFUL" | "NOT_HELPFUL" | string; comment?: string; updatedAt?: string };
};

export type CustomerMetric = { value: number; definition: string; source: string; lastCalculatedAt: string; drilldownTarget: string };
export type CustomerSignal = { mode: string; type: string; title: string; detail: string; severity: string; evidence: string[] };
export type ServiceIssue = { id: string; number: string; title: string; status: string; priority: string; dueAt: string; description: string };
export type ValueItem = { id: string; title: string; status: string; amount: number; source: string };
export type RelationshipContact = { id: string; name: string; title: string; role: string; owner: string; lastContactAt: string };

export type CustomerWorkbenchDetail = CustomerWorkbenchAccount & {
  industry: string;
  contact: string;
  summary: string;
  risks: string[];
  newCustomerSignals: string[];
  existingCustomerSignals: string[];
  nextActions: string[];
  metrics?: Record<string, CustomerMetric>;
  signals?: CustomerSignal[];
  serviceIssues?: ServiceIssue[];
  valueItems?: ValueItem[];
  renewal?: { days: number; contracts: ValueItem[]; opportunities: Array<Record<string, unknown>> };
  relationshipMap?: RelationshipContact[];
  opportunities?: Array<Record<string, unknown>>;
  timeline: CustomerInteractionEvent[];
  recommendations: CustomerRecommendation[];
  crmConnection?: { ready: boolean; mode: string; label: string };
};

export type CustomerAssistantResult = {
  reply: string;
  action: "NONE" | "SWITCH_ACCOUNT" | "FOCUS_RECOMMENDATIONS" | string;
  actionPayload?: { accountId?: string; mode?: "new" | "existing"; tab?: string; recommendationType?: string };
  uiActions?: Array<{ type: string; payload: Record<string, unknown>; requiresConfirmation: boolean }>;
  agentId?: string;
  sessionId?: string;
  runId?: string;
  model?: Record<string, unknown>;
  resolvedSkills?: string[];
  activeSkillCode?: string;
};

export type CustomerAssistantHistoryMessage = { role: "user" | "assistant" | string; content: string; createdAt: string };

export type CustomerAssistantStreamEvent =
  | { type: "workbench"; result: CustomerAssistantResult }
  | { type: "phase"; phase: string; modelName?: string }
  | { type: "tool_call"; toolName: string }
  | { type: "delta"; text: string }
  | { type: "done"; runId?: string }
  | { type: "error"; message: string; runId?: string };

export function parseCustomerAssistantStreamEvent(eventName: string, data: string): CustomerAssistantStreamEvent | null {
  let payload: Record<string, unknown> = {};
  try {
    payload = data ? JSON.parse(data) as Record<string, unknown> : {};
  } catch {
    if (eventName === "error") return { type: "error", message: data || "流式回复失败" };
    return null;
  }
  if (eventName === "workbench") return { type: "workbench", result: payload as CustomerAssistantResult };
  if (eventName === "phase" && typeof payload.phase === "string") {
    return { type: "phase", phase: payload.phase, modelName: typeof payload.modelName === "string" ? payload.modelName : undefined };
  }
  if (eventName === "tool_call" && typeof payload.toolName === "string") return { type: "tool_call", toolName: payload.toolName };
  if (eventName === "delta" && typeof payload.text === "string") return { type: "delta", text: payload.text };
  if (eventName === "done") return { type: "done", runId: typeof payload.runId === "string" ? payload.runId : undefined };
  if (eventName === "error") {
    return {
      type: "error",
      message: typeof payload.message === "string" ? payload.message : "流式回复失败",
      runId: typeof payload.runId === "string" ? payload.runId : undefined,
    };
  }
  return null;
}

async function requestJson<T>(token: string, input: string, init?: RequestInit): Promise<T> {
  const response = await fetch(input, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
      ...(init?.headers ?? {}),
    },
  });
  const { body, rawText } = await safeFetchJson<T>(response);
  if (!response.ok || body?.success === false) {
    throw new Error(body?.message || rawText || `请求失败：${response.status}`);
  }
  return (body?.data ?? null) as T;
}

export function listCustomerWorkbenchAccounts(token: string) {
  return requestJson<CustomerWorkbenchAccount[]>(token, "/customer-workbench/accounts");
}

export function getCustomerWorkbenchQueue(token: string, query: {
  mode: "new" | "existing"; filter: string; sort: string; direction: string; query: string;
  page: number; size: number; refresh?: boolean;
}) {
  const params = new URLSearchParams(Object.entries(query).map(([key, value]) => [key, String(value)]));
  return requestJson<CustomerQueueResult>(token, `/customer-workbench/queue?${params.toString()}`);
}

export function getCustomerWorkbenchDetail(token: string, accountId: string) {
  return requestJson<CustomerWorkbenchDetail>(token, `/customer-workbench/accounts/${encodeURIComponent(accountId)}`);
}

export function getCustomerAssistantHistory(token: string, accountId: string) {
  return requestJson<CustomerAssistantHistoryMessage[]>(token, `/customer-workbench/accounts/${encodeURIComponent(accountId)}/assistant-history`);
}

export function saveCustomerInteraction(token: string, accountId: string, payload: {
  sourceType: "WECHAT" | "PHONE" | "MEETING" | "CUSTOMER_FEEDBACK";
  subject: string;
  content: string;
  occurredAt?: string;
}) {
  return requestJson<CustomerInteractionEvent & { deduplicated: boolean }>(
    token,
    `/customer-workbench/accounts/${encodeURIComponent(accountId)}/interactions`,
    { method: "POST", body: JSON.stringify(payload) },
  );
}

async function requestMultipart<T>(token: string, input: string, form: FormData): Promise<T> {
  const response = await fetch(input, { method: "POST", headers: { Authorization: `Bearer ${token}` }, body: form });
  const { body, rawText } = await safeFetchJson<T>(response);
  if (!response.ok || body?.success === false) throw new Error(body?.message || rawText || `请求失败：${response.status}`);
  return (body?.data ?? null) as T;
}

export function createCustomerInteractionBatch(token: string, accountId: string, payload: {
  sourceType: string;
  occurredAt: string;
  subject: string;
  narrationText: string;
  pastedText: string;
  files: File[];
}) {
  const form = new FormData();
  form.set("sourceType", payload.sourceType);
  form.set("occurredAt", payload.occurredAt);
  form.set("subject", payload.subject);
  form.set("narrationText", payload.narrationText);
  form.set("pastedText", payload.pastedText);
  payload.files.forEach((file) => form.append("files", file, file.name));
  return requestMultipart<CustomerInteractionBatch>(
    token,
    `/customer-workbench/accounts/${encodeURIComponent(accountId)}/interaction-batches`,
    form,
  );
}

export function listCustomerInteractionBatches(token: string, accountId: string) {
  return requestJson<CustomerInteractionBatch[]>(token, `/customer-workbench/accounts/${encodeURIComponent(accountId)}/interaction-batches`);
}

export function getCustomerInteractionBatch(token: string, batchId: string) {
  return requestJson<CustomerInteractionBatch>(token, `/customer-workbench/interaction-batches/${encodeURIComponent(batchId)}`);
}

export async function viewCustomerInteractionAsset(token: string, batchId: string, assetId: string) {
  const response = await fetch(
    `/customer-workbench/interaction-batches/${encodeURIComponent(batchId)}/assets/${encodeURIComponent(assetId)}`,
    { headers: { Authorization: `Bearer ${token}` } },
  );
  if (!response.ok) {
    const { body, rawText } = await safeFetchJson<unknown>(response);
    throw new Error(body?.message || rawText || `原件读取失败：${response.status}`);
  }
  const objectUrl = URL.createObjectURL(await response.blob());
  const preview = window.open(objectUrl, "_blank", "noopener,noreferrer");
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
  if (!preview) throw new Error("浏览器阻止了原件预览窗口，请允许当前站点打开新窗口。");
}

export function retryCustomerInteractionBatch(token: string, batchId: string) {
  return requestJson<CustomerInteractionBatch>(token, `/customer-workbench/interaction-batches/${encodeURIComponent(batchId)}/retry`, { method: "POST" });
}

export function confirmCustomerInteractionBatch(token: string, batchId: string, payload: {
  sourceType: string;
  subject: string;
  content: string;
  occurredAt: string;
}) {
  return requestJson<CustomerInteractionBatch>(token, `/customer-workbench/interaction-batches/${encodeURIComponent(batchId)}/confirm`, {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function acceptCustomerRecommendation(token: string, recommendationId: string) {
  return requestJson<CustomerRecommendation>(token, `/customer-workbench/recommendations/${encodeURIComponent(recommendationId)}/accept`, {
    method: "POST",
  });
}

export function updateCustomerRecommendation(token: string, recommendationId: string, payload: Partial<CustomerRecommendation>) {
  return requestJson<CustomerRecommendation>(token, `/customer-workbench/recommendations/${encodeURIComponent(recommendationId)}`, {
    method: "PATCH", body: JSON.stringify(payload),
  });
}

export function dismissCustomerRecommendation(token: string, recommendationId: string, reason: string) {
  return requestJson<CustomerRecommendation>(token, `/customer-workbench/recommendations/${encodeURIComponent(recommendationId)}/dismiss`, {
    method: "POST", body: JSON.stringify({ reason }),
  });
}

export function confirmCustomerRecommendation(token: string, recommendationId: string) {
  return requestJson<CustomerRecommendation>(token, `/customer-workbench/recommendations/${encodeURIComponent(recommendationId)}/confirm`, {
    method: "POST",
  });
}

export function applyCustomerRecommendation(token: string, recommendationId: string) {
  return requestJson<CustomerRecommendation & { message?: string }>(
    token,
    `/customer-workbench/recommendations/${encodeURIComponent(recommendationId)}/apply`,
    { method: "POST" },
  );
}

export function submitCustomerRecommendationFeedback(token: string, recommendationId: string,
                                                     rating: "HELPFUL" | "NOT_HELPFUL", comment = "") {
  return requestJson<{ recommendationId: string; rating: string; comment: string; updatedAt: string }>(
    token,
    `/customer-workbench/recommendations/${encodeURIComponent(recommendationId)}/feedback`,
    { method: "POST", body: JSON.stringify({ rating, comment }) },
  );
}

export function askCustomerWorkbenchAssistant(token: string, payload: { accountId?: string; message: string }) {
  return requestJson<CustomerAssistantResult>(token, "/customer-workbench/assistant", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export async function streamCustomerWorkbenchAssistant(
  token: string,
  payload: { accountId?: string; message: string },
  onEvent: (event: CustomerAssistantStreamEvent) => void | Promise<void>,
  signal?: AbortSignal,
) {
  const response = await fetch("/customer-workbench/assistant/stream", {
    method: "POST",
    headers: {
      Accept: "text/event-stream",
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(payload),
    signal,
  });
  if (!response.ok) {
    const raw = await response.text();
    throw new Error(raw || `请求失败：${response.status}`);
  }
  if (!response.body) throw new Error("流式响应为空");

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, "\n");
    let boundary = buffer.indexOf("\n\n");
    while (boundary >= 0) {
      const block = buffer.slice(0, boundary);
      buffer = buffer.slice(boundary + 2);
      let eventName = "message";
      const dataLines: string[] = [];
      block.split("\n").forEach((line) => {
        if (line.startsWith("event:")) eventName = line.slice(6).trim();
        if (line.startsWith("data:")) dataLines.push(line.slice(5).trimStart());
      });
      const event = parseCustomerAssistantStreamEvent(eventName, dataLines.join("\n"));
      if (event) await onEvent(event);
      boundary = buffer.indexOf("\n\n");
    }
  }
}

export function setCustomerFollowed(token: string, accountId: string, followed: boolean) {
  return requestJson<{ accountId: string; followed: boolean }>(token, `/customer-workbench/accounts/${encodeURIComponent(accountId)}/follow`, {
    method: "POST", body: JSON.stringify({ followed }),
  });
}

export function getCustomerWorkbenchNotifications(token: string) {
  return requestJson<Array<{ accountId: string; accountName: string; severity: string; title: string; occurredAt: string; customerMode?: string }>>(
    token, "/customer-workbench/notifications",
  );
}

export function getCustomerWorkbenchIntegrationStatus(token: string) {
  return requestJson<{ ready: boolean; status?: string; label: string; baseUrl?: string; dataAsOf?: string; visibleAccounts?: number; message?: string }>(
    token, "/customer-workbench/integration-status",
  );
}

export function getCustomerWorkbenchSupervisorSummary(token: string) {
  return requestJson<{ visibleAccounts: number; riskAccounts: number; followedAccounts: number; openActions: number;
    pendingRecommendations: number; writeSucceeded: number; writeFailed: number; writeSuccessRate: number; dataAsOf: string }>(
    token, "/customer-workbench/supervisor-summary",
  );
}
