import { describe, expect, it } from "vitest";
import {
  isUploadTerminalStatus,
  readKnowledgeApiResponse,
  uploadFailureMessage,
} from "./AdminKnowledgeUploadFlow";

function response(body: string, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => body,
  };
}

describe("knowledge upload flow feedback", () => {
  it("returns a successful JSON envelope", async () => {
    await expect(readKnowledgeApiResponse(response('{"success":true,"data":{"id":7}}'), "上传文档"))
      .resolves.toEqual({ success: true, data: { id: 7 } });
  });

  it("turns HTML responses into an actionable Chinese error", async () => {
    await expect(readKnowledgeApiResponse(response("<html>gateway error</html>", 502), "上传文档"))
      .rejects.toThrow("上传文档失败：服务返回了无法识别的响应（HTTP 502）");
  });

  it("preserves API failure messages", async () => {
    await expect(readKnowledgeApiResponse(response('{"success":false,"message":"文件超过限制"}', 400), "上传文档"))
      .rejects.toThrow("上传文档失败：文件超过限制");
  });

  it("recognizes upload terminal states", () => {
    expect(isUploadTerminalStatus("PUBLISHED")).toBe(true);
    expect(isUploadTerminalStatus("FAILED")).toBe(true);
    expect(isUploadTerminalStatus("INDEXING")).toBe(false);
    expect(uploadFailureMessage(new Error("网络中断"), "上传失败")).toBe("网络中断");
  });
});
