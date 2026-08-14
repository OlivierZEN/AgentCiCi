export const MAX_CHAT_IMAGE_BYTES = 20 * 1024 * 1024;
export const MAX_CHAT_IMAGES_PER_SESSION = 10;

export const SUPPORTED_CHAT_IMAGE_TYPES = ["image/png", "image/jpeg", "image/webp"] as const;

export type ChatImageCandidate = Pick<File, "name" | "size" | "type">;

export type ChatImageRejection = {
  file: ChatImageCandidate;
  reason: string;
};

export function validateChatImages<T extends ChatImageCandidate>(
  files: readonly T[],
  usedCount: number,
): { accepted: T[]; rejected: ChatImageRejection[] } {
  const accepted: T[] = [];
  const rejected: ChatImageRejection[] = [];
  let remaining = Math.max(0, MAX_CHAT_IMAGES_PER_SESSION - Math.max(0, usedCount));

  for (const file of files) {
    if (!SUPPORTED_CHAT_IMAGE_TYPES.includes(file.type as (typeof SUPPORTED_CHAT_IMAGE_TYPES)[number])) {
      rejected.push({ file, reason: `${file.name} 不是支持的 PNG、JPG 或 WebP 图片` });
      continue;
    }
    if (file.size <= 0) {
      rejected.push({ file, reason: `${file.name} 是空文件` });
      continue;
    }
    if (file.size > MAX_CHAT_IMAGE_BYTES) {
      rejected.push({ file, reason: `${file.name} 超过 20MB` });
      continue;
    }
    if (remaining <= 0) {
      rejected.push({ file, reason: "每个会话最多上传 10 张图片" });
      continue;
    }
    accepted.push(file);
    remaining -= 1;
  }
  return { accepted, rejected };
}

export function createClientAttachmentId(): string {
  const random = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return `web-${random}`;
}

export function composerCanSubmit(question: string, states: readonly string[]): boolean {
  const hasReadyImage = states.includes("ready");
  const hasPendingOrFailed = states.some((state) => state === "uploading" || state === "error");
  return !hasPendingOrFailed && (question.trim().length > 0 || hasReadyImage);
}
