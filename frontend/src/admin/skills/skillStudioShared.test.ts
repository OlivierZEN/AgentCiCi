import { describe, expect, it } from "vitest";
import { readSkillExportJobResponse } from "./skillStudioShared";

describe("skill export response handling", () => {
  it("returns a ready export job", async () => {
    const response = new Response(JSON.stringify({
      success: true,
      data: {
        exportId: "export-1",
        status: "READY",
        filename: "contact-email-generator-skill-package.zip",
        skillVersionId: 2,
        versionNo: 2,
        standardizationEngine: "model-standardizer",
        warnings: [],
      },
    }), { status: 200, headers: { "content-type": "application/json" } });

    await expect(readSkillExportJobResponse(response)).resolves.toMatchObject({
      exportId: "export-1",
      status: "READY",
    });
  });

  it("surfaces the backend validation reason", async () => {
    const response = new Response(JSON.stringify({
      success: false,
      message: "Export package validation failed: manifest format mismatch",
    }), { status: 400, headers: { "content-type": "application/json" } });

    await expect(readSkillExportJobResponse(response)).rejects.toThrow("manifest format mismatch");
  });

  it("reports non-json gateway responses instead of failing silently", async () => {
    const response = new Response("<html>Bad Gateway</html>", {
      status: 502,
      headers: { "content-type": "text/html" },
    });

    await expect(readSkillExportJobResponse(response)).rejects.toThrow("非 JSON 响应（HTTP 502）");
  });

  it("rejects export jobs that are not ready", async () => {
    const response = new Response(JSON.stringify({
      success: true,
      data: { exportId: "export-1", status: "PENDING", filename: "skill.zip" },
    }), { status: 200, headers: { "content-type": "application/json" } });

    await expect(readSkillExportJobResponse(response)).rejects.toThrow("导出任务未就绪");
  });
});
