import { safeFetchJson } from "../../utils/http";
import type { DataInsightDashboard } from "./dataInsightTypes";

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

export function getDataInsightDashboard(token: string) {
  return requestJson<DataInsightDashboard>(token, "/ai/data-insights/dashboard");
}

