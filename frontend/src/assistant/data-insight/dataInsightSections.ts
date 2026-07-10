export function sourceTypeLabel(sourceType: string) {
  const normalized = (sourceType || "").toUpperCase();
  if (normalized === "REAL_CRM_DEMO") return "CRM 演示数据";
  if (normalized === "REAL_AGGREGATE") return "CRM 聚合数据";
  if (normalized === "MOCK") return "演示样例";
  return sourceType || "未标记";
}

export function segmentLabel(segment: string) {
  const normalized = (segment || "").toUpperCase();
  if (normalized === "NEW") return "新客户";
  if (normalized === "EXISTING") return "老客户";
  if (normalized === "RISK") return "风险";
  if (normalized === "STRATEGIC") return "战略";
  return segment || "客户";
}

export function compactMoney(value: number) {
  if (!Number.isFinite(value)) return "¥0";
  if (Math.abs(value) >= 100000000) return `¥${(value / 100000000).toFixed(1)}亿`;
  if (Math.abs(value) >= 10000) return `¥${Math.round(value / 10000)}万`;
  return `¥${Math.round(value).toLocaleString("zh-CN")}`;
}

export function compactNumber(value: number) {
  if (!Number.isFinite(value)) return "0";
  if (Math.abs(value) >= 1000000) return `${(value / 1000000).toFixed(1)}M`;
  return Math.round(value).toLocaleString("zh-CN");
}

export function compactDateTime(value: string) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

