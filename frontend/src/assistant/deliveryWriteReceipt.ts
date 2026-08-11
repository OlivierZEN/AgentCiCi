import type { StreamToolResultEvent } from "../chat/streamChat";

export type DeliveryWriteReceipt = {
  status: "SUCCESS" | "FAILED";
  objectApiName?: string;
  subject?: string;
  code?: string;
  recordId?: string;
  revision?: number;
  correlationId?: string;
  message?: string;
};

const DELIVERY_WRITE_TOOL = "semattice_project_delivery_create";

export function parseDeliveryWriteReceiptEvent(event: StreamToolResultEvent): DeliveryWriteReceipt | null {
  if (event.toolName.trim().toLowerCase() !== DELIVERY_WRITE_TOOL) {
    return null;
  }
  try {
    const payload = JSON.parse(event.payload) as Record<string, unknown>;
    if (payload.status === "SUCCESS") {
      const recordId = stringValue(payload.record_id);
      const correlationId = stringValue(payload.correlation_id);
      const revision = numberValue(payload.revision);
      if (!recordId || !correlationId || !revision || payload.readback_verified !== true) {
        return {
          status: "FAILED",
          message: "Semattice 成功回执不完整，不能确认创建成功。",
        };
      }
      return {
        status: "SUCCESS",
        objectApiName: stringValue(payload.object_api_name),
        subject: stringValue(payload.name) || stringValue(payload.title),
        code: stringValue(payload.code),
        recordId,
        revision,
        correlationId,
      };
    }
    return {
      status: "FAILED",
      message: stringValue(payload.message) || "Semattice 未返回成功回执。",
    };
  } catch {
    return {
      status: "FAILED",
      message: "Semattice 回执无法解析，不能确认创建成功。",
    };
  }
}

export function extractDeliveryWriteReceipt(content: string): DeliveryWriteReceipt | null {
  const match = content.match(
    /已在 Semattice 创建(项目|需求|任务|缺陷)：(.+?)(?:（([^）]+)）)?。记录 ID：([^；。]+)；revision：(\d+)；关联号：([^。]+)。/,
  );
  if (!match) {
    return null;
  }
  return {
    status: "SUCCESS",
    objectApiName: ({ 项目: "dev_project", 需求: "dev_requirement", 任务: "dev_task", 缺陷: "dev_defect" } as Record<string, string>)[match[1]],
    subject: match[2]?.trim(),
    code: match[3]?.trim(),
    recordId: match[4]?.trim(),
    revision: Number(match[5]),
    correlationId: match[6]?.trim(),
  };
}

function stringValue(value: unknown): string | undefined {
  return typeof value === "string" && value.trim() ? value.trim() : undefined;
}

function numberValue(value: unknown): number | undefined {
  return typeof value === "number" && Number.isInteger(value) && value > 0 ? value : undefined;
}
