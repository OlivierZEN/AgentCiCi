import { safeFetchJson } from "../../utils/http";
import type {
  CustomerInsightGenerateResult,
  CustomerInsightProject,
  CustomerInsightSection,
  CustomerInsightSectionCatalogItem,
} from "./customerInsightTypes";

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

export function listCustomerInsightProjects(token: string) {
  return requestJson<CustomerInsightProject[]>(token, "/ai/customer-insights/projects");
}

export function getCustomerInsightProject(token: string, projectId: string) {
  return requestJson<CustomerInsightProject>(token, `/ai/customer-insights/projects/${encodeURIComponent(projectId)}`);
}

export function getCustomerInsightCatalog(token: string) {
  return requestJson<CustomerInsightSectionCatalogItem[]>(token, "/ai/customer-insights/catalog");
}

export function createCustomerInsightProject(
  token: string,
  payload: { customerName: string; industry?: string; sourceType?: string },
) {
  return requestJson<CustomerInsightProject>(token, "/ai/customer-insights/projects", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function updateCustomerInsightProject(
  token: string,
  projectId: string,
  payload: { customerName: string; industry?: string; customerExternalId?: string; customerObjectApiName?: string; sourceType?: string },
) {
  return requestJson<CustomerInsightProject>(token, `/ai/customer-insights/projects/${encodeURIComponent(projectId)}`, {
    method: "PATCH",
    body: JSON.stringify(payload),
  });
}

export function refreshCustomerInsightSources(token: string, projectId: string) {
  return requestJson<{ project: CustomerInsightProject; snapshots?: unknown[] }>(
    token,
    `/ai/customer-insights/projects/${encodeURIComponent(projectId)}/refresh-sources`,
    { method: "POST" },
  );
}

export function saveCustomerInsightSection(
  token: string,
  projectId: string,
  sectionCode: string,
  payload: { input: Record<string, unknown>; markdown?: string },
) {
  return requestJson<CustomerInsightSection>(
    token,
    `/ai/customer-insights/projects/${encodeURIComponent(projectId)}/sections/${encodeURIComponent(sectionCode)}`,
    { method: "PUT", body: JSON.stringify(payload) },
  );
}

export function generateCustomerInsightSection(
  token: string,
  projectId: string,
  sectionCode: string,
  payload: { input: Record<string, unknown>; markdown?: string },
) {
  return requestJson<CustomerInsightGenerateResult>(
    token,
    `/ai/customer-insights/projects/${encodeURIComponent(projectId)}/sections/${encodeURIComponent(sectionCode)}/generate`,
    { method: "POST", body: JSON.stringify(payload) },
  );
}

export function generateCustomerInsightFull(token: string, projectId: string) {
  return requestJson<CustomerInsightGenerateResult>(
    token,
    `/ai/customer-insights/projects/${encodeURIComponent(projectId)}/generate-full`,
    { method: "POST" },
  );
}
