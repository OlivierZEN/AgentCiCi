export type KnowledgeUploadPolicy = {
  maxFileSizeBytes: number;
  allowedExtensions: string[];
};

export type KnowledgeUploadCandidate = {
  name: string;
  size: number;
};

export function validateKnowledgeUpload(
  file: KnowledgeUploadCandidate,
  policy: KnowledgeUploadPolicy | null | undefined,
): string | null {
  if (!policy) return null;
  if (file.size > policy.maxFileSizeBytes) {
    return `上传失败：文件超过 ${Math.round(policy.maxFileSizeBytes / 1024 / 1024)} MB 限制`;
  }
  const ext = file.name.split(".").pop()?.toLowerCase() ?? "";
  if (!policy.allowedExtensions.includes(ext)) {
    return `上传失败：仅支持 ${policy.allowedExtensions.join(" / ")}`;
  }
  return null;
}
