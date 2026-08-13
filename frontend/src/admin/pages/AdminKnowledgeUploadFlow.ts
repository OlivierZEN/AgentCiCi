export type KnowledgeApiEnvelope<T> = {
  success: boolean;
  data?: T;
  message?: string;
};

export type UploadTerminalStatus = "PUBLISHED" | "FAILED" | "CLEANUP_FAILED";

export async function readKnowledgeApiResponse<T>(
  response: Pick<Response, "ok" | "status" | "text">,
  actionLabel: string,
): Promise<KnowledgeApiEnvelope<T>> {
  const body = await response.text();
  if (response.status === 413) {
    throw new Error(
      `${actionLabel}被上传网关拒绝：请确认文件不超过 25 MB；如果文件符合限制，请联系管理员检查网关上传上限（HTTP 413）`,
    );
  }
  let payload: KnowledgeApiEnvelope<T>;
  try {
    payload = JSON.parse(body) as KnowledgeApiEnvelope<T>;
  } catch {
    throw new Error(`${actionLabel}失败：服务返回了无法识别的响应（HTTP ${response.status}）`);
  }
  if (!response.ok || !payload.success) {
    throw new Error(`${actionLabel}失败：${payload.message?.trim() || `HTTP ${response.status}`}`);
  }
  return payload;
}

export function uploadFailureMessage(error: unknown, fallback: string): string {
  if (error instanceof Error && error.message.trim()) return error.message.trim();
  return fallback;
}

export function isUploadTerminalStatus(status: string): status is UploadTerminalStatus {
  return status === "PUBLISHED" || status === "FAILED" || status === "CLEANUP_FAILED";
}
