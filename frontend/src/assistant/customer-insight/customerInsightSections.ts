import type { CustomerInsightSection, CustomerInsightSectionCatalogItem } from "./customerInsightTypes";

export const CUSTOMER_INSIGHT_CORE_SECTIONS = [
  "customer_info",
  "macro_environment",
  "signed_contracts",
  "customer_service",
  "one_customer_one_strategy",
];

export function groupSections(sections: CustomerInsightSection[] | CustomerInsightSectionCatalogItem[]) {
  const groups: Array<{ code: string; label: string; sections: typeof sections }> = [];
  for (const section of sections) {
    const code = "sectionGroup" in section ? section.sectionGroup : "";
    const label = section.groupLabel;
    const existing = groups.find((item) => item.code === code);
    if (existing) {
      (existing.sections as typeof sections).push(section as never);
    } else {
      groups.push({ code, label, sections: [section] as typeof sections });
    }
  }
  return groups;
}

export function statusLabel(status: string) {
  const normalized = (status || "").toUpperCase();
  if (normalized === "GENERATED") return "已生成";
  if (normalized === "GENERATING") return "生成中";
  if (normalized === "SUCCESS") return "成功";
  if (normalized === "RUNNING") return "运行中";
  if (normalized === "DRAFT") return "草稿";
  if (normalized === "ERROR" || normalized === "FAILED") return "错误";
  if (normalized === "READY") return "就绪";
  if (normalized === "ANALYZING") return "分析中";
  return "未开始";
}

export function statusTone(status: string) {
  const normalized = (status || "").toUpperCase();
  if (normalized === "GENERATED" || normalized === "READY" || normalized === "SUCCESS") return "success";
  if (normalized === "ERROR" || normalized === "FAILED") return "danger";
  if (normalized === "GENERATING" || normalized === "ANALYZING" || normalized === "RUNNING") return "active";
  return "muted";
}

export function compactDate(value: string) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" });
}

export function sourceTypeLabel(sourceType: string) {
  const normalized = (sourceType || "").toUpperCase();
  if (normalized === "MANUAL") return "人工录入";
  if (normalized === "CLOUDCC") return "业务系统";
  if (normalized === "MIXED") return "混合来源";
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

export function inputToText(input: Record<string, unknown>) {
  if (Object.keys(input ?? {}).length === 1 && typeof input.notes === "string") {
    return input.notes;
  }
  const text = Object.entries(input ?? {})
    .map(([key, value]) => `${key}: ${typeof value === "string" ? value : JSON.stringify(value)}`)
    .join("\n");
  return text.trim();
}

export function textToInput(text: string) {
  return {
    notes: text.trim(),
  };
}
