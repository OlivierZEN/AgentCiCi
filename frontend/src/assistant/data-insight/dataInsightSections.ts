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
