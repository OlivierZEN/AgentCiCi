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
  lastInteraction: string;
  stage: string;
  tags: string[];
  updatedAt?: string;
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
  status: "PENDING" | "ACCEPTED" | "DISMISSED" | "APPLIED" | string;
  crmPayload: Record<string, unknown>;
  appliedCrmId?: string;
};

export type CustomerWorkbenchDetail = CustomerWorkbenchAccount & {
  industry: string;
  contact: string;
  summary: string;
  risks: string[];
  newCustomerSignals: string[];
  existingCustomerSignals: string[];
  nextActions: string[];
  timeline: CustomerInteractionEvent[];
  recommendations: CustomerRecommendation[];
  crmConnection?: { ready: boolean; mode: string; label: string };
};

export type CustomerAssistantResult = {
  reply: string;
  action: "NONE" | "SWITCH_ACCOUNT" | "FOCUS_RECOMMENDATIONS" | string;
  actionPayload?: { accountId?: string };
  agentId?: string;
  sessionId?: string;
  runId?: string;
  model?: Record<string, unknown>;
  resolvedSkills?: string[];
  activeSkillCode?: string;
};

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

export function getCustomerWorkbenchDetail(token: string, accountId: string) {
  return requestJson<CustomerWorkbenchDetail>(token, `/customer-workbench/accounts/${encodeURIComponent(accountId)}`);
}

export function acceptCustomerRecommendation(token: string, recommendationId: string) {
  return requestJson<CustomerRecommendation>(token, `/customer-workbench/recommendations/${encodeURIComponent(recommendationId)}/accept`, {
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

export function askCustomerWorkbenchAssistant(token: string, payload: { accountId?: string; message: string }) {
  return requestJson<CustomerAssistantResult>(token, "/customer-workbench/assistant", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}
