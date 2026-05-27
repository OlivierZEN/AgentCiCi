import { authFetch, readAuthToken } from "../../auth/authStorage";
import { LS_PLATFORM_TOKEN, PLATFORM_API_BASE } from "../../constants";
import { safeFetchJson } from "../../utils/http";

export type RetentionPolicy = {
  orgId: string;
  graceUntil?: string | null;
  suspendUntil?: string | null;
  exportDeadline?: string | null;
  purgeAfter?: string | null;
  legalHold: boolean;
  policySource: string;
  legalHoldReason?: string | null;
  legalHoldApprovedBy?: string | null;
  legalHoldApprovedAt?: string | null;
  legalHoldReviewAt?: string | null;
  updatedAt?: string | null;
};

export type ManifestTable = {
  table: string;
  rows: number;
};

export type ManifestDomain = {
  domain: string;
  label: string;
  rows: number;
  tables: ManifestTable[];
};

export type PurgeManifest = {
  orgId: string;
  dryRun: boolean;
  generatedAt: string;
  totals: {
    rows: number;
    domains: number;
    unsupported: number;
  };
  domains: ManifestDomain[];
  unsupported: Array<{ domain: string; label: string; reason: string }>;
};

export type PurgeJob = {
  id: number;
  orgId: string;
  dryRun: boolean;
  status: string;
  phase: string;
  requestedBy: string;
  reason?: string | null;
  startedAt?: string | null;
  finishedAt?: string | null;
  totalRows?: number | null;
  unsupportedCount?: number | null;
  manifest?: PurgeManifest | null;
  result?: Record<string, unknown> | null;
  errorMessage?: string | null;
  sourceDryRunJobId?: number | null;
  manifestHash?: string | null;
  workerId?: string | null;
  lockExpiresAt?: string | null;
  attemptCount?: number | null;
  deadLetterAt?: string | null;
  createdAt?: string | null;
};

export type ExportJob = {
  id: number;
  orgId: string;
  status: string;
  requestedBy: string;
  reason?: string | null;
  manifest?: Record<string, unknown> | null;
  errorMessage?: string | null;
  startedAt?: string | null;
  finishedAt?: string | null;
  createdAt?: string | null;
};

export type Tenant = {
  orgId: string;
  name: string;
  status: string;
  memberCount: number;
  retention?: RetentionPolicy | null;
  latestJob?: PurgeJob | null;
};

export type TenantDetail = {
  tenant: Tenant;
  retention: RetentionPolicy;
  jobs: PurgeJob[];
  exportJobs: ExportJob[];
};

export type TenantProvisionPayload = {
  tenantName: string;
  ownerMobile: string;
  ownerDisplayName?: string | null;
  ownerEmail?: string | null;
  initialPassword?: string | null;
  provisionNote?: string | null;
};

export type TenantProvisionResult = {
  orgId: string;
  orgName: string;
  status: string;
  ownerMemberId: string;
  ownerAccountId: string;
  reusedExistingAccount: boolean;
};

export function readPlatformToken(): string {
  return readAuthToken(LS_PLATFORM_TOKEN);
}

export function formatTs(ts?: string | null): string {
  if (!ts) return "—";
  const date = new Date(ts);
  if (Number.isNaN(date.getTime())) return ts;
  return date.toLocaleString();
}

export function toDateInput(ts?: string | null): string {
  if (!ts) return "";
  const date = new Date(ts);
  if (Number.isNaN(date.getTime())) return "";
  return date.toISOString().slice(0, 10);
}

export function fromDateInput(value: string): string | null {
  if (!value) return null;
  return `${value}T00:00:00Z`;
}

export function statusLabel(status: string): string {
  switch (status.toUpperCase()) {
    case "ACTIVE":
      return "正常";
    case "SUSPENDED":
      return "已冻结";
    case "PAST_DUE":
      return "宽限";
    case "PENDING_PURGE":
      return "待销毁";
    case "PURGED":
      return "已销毁";
    default:
      return status || "未知";
  }
}

export function jobLabel(status?: string | null): string {
  switch ((status ?? "").toUpperCase()) {
    case "SUCCEEDED":
      return "已完成";
    case "QUEUED":
      return "排队中";
    case "RUNNING":
      return "执行中";
    case "PARTIAL_FAILED":
      return "部分失败";
    case "FAILED":
      return "失败";
    case "CANCELED":
      return "已取消";
    case "DEAD_LETTER":
      return "死信";
    default:
      return status || "无记录";
  }
}

export async function fetchTenantList(token: string): Promise<Tenant[]> {
  const response = await authFetch(LS_PLATFORM_TOKEN, `${PLATFORM_API_BASE}/tenants`);
  const { body } = await safeFetchJson<Tenant[]>(response);
  if (!response.ok || !body?.success) {
    throw new Error(body?.message ?? `HTTP ${response.status}`);
  }
  return body.data ?? [];
}

export async function fetchTenantDetail(token: string, orgId: string): Promise<TenantDetail> {
  const response = await authFetch(LS_PLATFORM_TOKEN, `${PLATFORM_API_BASE}/tenants/${encodeURIComponent(orgId)}/retention`);
  const { body } = await safeFetchJson<TenantDetail>(response);
  if (!response.ok || !body?.success || !body.data) {
    throw new Error(body?.message ?? `HTTP ${response.status}`);
  }
  return body.data;
}

export async function createTenant(token: string, payload: TenantProvisionPayload): Promise<TenantProvisionResult> {
  const response = await authFetch(LS_PLATFORM_TOKEN, `${PLATFORM_API_BASE}/tenants`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
  });
  const { body } = await safeFetchJson<TenantProvisionResult>(response);
  if (!response.ok || !body?.success || !body.data) {
    throw new Error(body?.message ?? `HTTP ${response.status}`);
  }
  return body.data;
}
