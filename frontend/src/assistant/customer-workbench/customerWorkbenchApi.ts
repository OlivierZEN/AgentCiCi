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
