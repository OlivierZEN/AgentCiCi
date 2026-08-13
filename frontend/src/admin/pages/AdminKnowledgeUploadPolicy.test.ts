import { describe, expect, it } from "vitest";
import { validateKnowledgeUpload } from "./AdminKnowledgeUploadPolicy";

const policy = {
  maxFileSizeBytes: 25 * 1024 * 1024,
  allowedExtensions: ["csv", "docx", "json", "md", "pdf", "txt"],
};

describe("knowledge upload policy", () => {
  it("allows a text-based PDF to reach the server parser", () => {
    expect(validateKnowledgeUpload({ name: "产品手册.PDF", size: 1024 }, policy)).toBeNull();
  });

  it("rejects oversized files before upload", () => {
    expect(validateKnowledgeUpload({ name: "产品手册.pdf", size: policy.maxFileSizeBytes + 1 }, policy))
      .toBe("上传失败：文件超过 25 MB 限制");
  });

  it("rejects extensions outside the server policy", () => {
    expect(validateKnowledgeUpload({ name: "产品手册.exe", size: 1024 }, policy))
      .toBe("上传失败：仅支持 csv / docx / json / md / pdf / txt");
  });
});
