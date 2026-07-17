import type {
  OntologyAggregation,
  OntologyDocument,
  OntologyValidationIssue,
} from "./ontologyTypes";

export const SUPPORTED_ONTOLOGY_CONNECTORS = [
  { value: "cloudcc", label: "CloudCC CRM（组织已配置连接）" },
] as const;

export function ontologyAggregationLabel(aggregation: OntologyAggregation): string {
  if (aggregation === "COUNT") return "数量";
  if (aggregation === "SUM") return "求和";
  if (aggregation === "AVG") return "平均值";
  if (aggregation === "MIN") return "最小值";
  return "最大值";
}

export function ontologySourceStatusLabel(status: string): string {
  const normalized = status.trim().toUpperCase();
  if (normalized === "ACTIVE" || normalized === "VALID") return "已连接";
  if (normalized === "PENDING" || normalized === "DRAFT") return "待确认";
  if (normalized === "STALE") return "需重新验证";
  if (normalized === "ERROR" || normalized === "FAILED") return "连接异常";
  if (normalized === "DISABLED" || normalized === "ARCHIVED") return "已停用";
  return "状态待确认";
}

function ontologyBusinessTargetLabel(
  targetType: string,
  targetKey: string,
  documents: readonly OntologyDocument[],
): string | null {
  if (targetType === "PROPERTY") {
    const separator = targetKey.indexOf(".");
    const conceptKey = separator >= 0 ? targetKey.slice(0, separator) : "";
    const propertyKey = separator >= 0 ? targetKey.slice(separator + 1) : "";
    for (const document of documents) {
      const concept = document.concepts.find((item) => item.key === conceptKey);
      const property = concept?.properties.find((item) => item.key === propertyKey);
      if (concept && property) return `业务属性“${concept.name} / ${property.name}”`;
    }
    return null;
  }

  const groups: Array<[string, keyof Pick<OntologyDocument, "concepts" | "relations" | "metrics" | "actions">, string]> = [
    ["CONCEPT", "concepts", "业务对象"],
    ["RELATION", "relations", "业务关系"],
    ["METRIC", "metrics", "业务指标"],
    ["ACTION", "actions", "业务动作"],
  ];
  for (const [type, group, label] of groups) {
    if (targetType !== type) continue;
    for (const document of documents) {
      const item = document[group].find((entry) => entry.key === targetKey);
      if (item) return `${label}“${item.name}”`;
    }
  }
  return null;
}

export function presentOntologyDiffItem(
  stableKey: string,
  current: OntologyDocument,
  candidate: OntologyDocument,
): string {
  const separator = stableKey.indexOf(":");
  const kind = separator >= 0 ? stableKey.slice(0, separator) : "";
  const identity = separator >= 0 ? stableKey.slice(separator + 1) : "";
  const documents = [candidate, current];

  if (kind === "document") {
    const document = documents.find((item) => item.key === identity) ?? candidate ?? current;
    return document?.name ? `业务本体“${document.name}”` : "业务本体设置";
  }
  if (kind === "property") {
    return ontologyBusinessTargetLabel("PROPERTY", identity, documents) ?? "业务属性";
  }
  if (kind === "concept" || kind === "relation" || kind === "metric" || kind === "action") {
    return ontologyBusinessTargetLabel(kind.toUpperCase(), identity, documents) ?? {
      concept: "业务对象",
      relation: "业务关系",
      metric: "业务指标",
      action: "业务动作",
    }[kind];
  }
  if (kind === "dataSource") {
    const sourceKey = identity.slice(identity.indexOf(":") + 1);
    for (const document of documents) {
      const source = document.dataSources.find((item) => item.key === sourceKey || String(item.id) === identity);
      if (source) return `数据来源“${source.name}”`;
    }
    return "数据来源";
  }
  if (kind === "mapping") {
    const [targetType = "", targetKey = ""] = identity.split(":");
    const target = ontologyBusinessTargetLabel(targetType, targetKey, documents);
    return target ? `数据映射 · ${target}` : "数据映射";
  }
  return "业务定义项";
}

function issueSeverityLabel(severity: OntologyValidationIssue["severity"]): string {
  if (severity === "ERROR") return "错误";
  if (severity === "WARNING") return "提醒";
  return "说明";
}

function issueLocation(path: string, document: OntologyDocument): string {
  const conceptIndex = Number(path.match(/\$\.concepts\[(\d+)]/)?.[1]);
  if (Number.isInteger(conceptIndex)) {
    const concept = document.concepts[conceptIndex];
    const conceptLabel = concept?.name || `第 ${conceptIndex + 1} 个业务对象`;
    const propertyIndex = Number(path.match(/\.properties\[(\d+)]/)?.[1]);
    if (Number.isInteger(propertyIndex)) {
      const property = concept?.properties[propertyIndex];
      const propertyLabel = property?.name || `第 ${propertyIndex + 1} 个属性`;
      return `业务对象“${conceptLabel}”的属性“${propertyLabel}”`;
    }
    return `业务对象“${conceptLabel}”`;
  }

  const indexedGroups: Array<[RegExp, readonly { name?: string }[], string]> = [
    [/\$\.relations\[(\d+)]/, document.relations, "业务关系"],
    [/\$\.metrics\[(\d+)]/, document.metrics, "业务指标"],
    [/\$\.actions\[(\d+)]/, document.actions, "业务动作"],
    [/\$\.dataSources\[(\d+)]/, document.dataSources, "数据来源"],
  ];
  for (const [pattern, items, label] of indexedGroups) {
    const index = Number(path.match(pattern)?.[1]);
    if (!Number.isInteger(index)) continue;
    return `${label}“${items[index]?.name || `第 ${index + 1} 项`}”`;
  }
  const mappingIndex = Number(path.match(/\$\.mappings\[(\d+)]/)?.[1]);
  if (Number.isInteger(mappingIndex)) return `第 ${mappingIndex + 1} 条数据映射`;
  return "本体整体设置";
}

function issueMessage(code: string): string {
  if (code === "SENSITIVE_PROPERTY_QUERYABLE") return "敏感信息不能用于业务查询，请关闭“可查询”。";
  if (code === "ENUM_VALUES_REQUIRED") return "选项属性至少需要填写一个业务选项。";
  if (code === "ENUM_VALUE_BLANK") return "业务选项不能为空，请删除空项或补充名称。";
  if (code === "METRIC_MEASURE_TYPE_INVALID") return "求和或平均值只能使用数字类型的属性。";
  if (code === "METRIC_TIME_PROPERTY_INVALID") return "时间维度必须选择日期或日期时间属性。";
  if (code === "MAPPING_CONFIDENCE_INVALID") return "映射可信度不在允许范围内，请重新确认。";
  if (code === "MAPPING_TRANSFORM_NOT_ALLOWED") return "当前数据转换方式不受支持，请重新选择。";
  if (code === "QUERYABLE_MAPPING_REQUIRED") return "可查询的业务定义需要先完成并验证数据映射。";
  if (code.includes("DUPLICATE") || code === "GRAPHQL_NAME_COLLISION") return "存在重复的业务定义，请调整名称后重试。";
  if (code.includes("NOT_FOUND")) return "引用的业务内容不存在，请重新选择。";
  if (code.includes("NOT_QUERYABLE")) return "关联内容尚未启用业务查询，请调整查询设置。";
  if (code.includes("REQUIRED")) return "缺少必填的业务信息，请补充后重试。";
  if (code === "INVALID_KEY") return "业务标识格式不正确，请使用稳定且不重复的名称。";
  if (code.startsWith("MAPPING_")) return "数据映射不完整，请重新选择来源、对象和字段。";
  if (code.startsWith("GRAPHQL_")) return "当前业务定义无法生成技术契约，请检查对象和查询设置。";
  return "该业务定义需要调整，请按定位内容检查后重试。";
}

export type OntologyBusinessIssue = {
  severityLabel: string;
  location: string;
  message: string;
};

export function presentOntologyValidationIssue(
  issue: OntologyValidationIssue,
  document: OntologyDocument,
): OntologyBusinessIssue {
  return {
    severityLabel: issueSeverityLabel(issue.severity),
    location: issueLocation(issue.path, document),
    message: issueMessage(issue.code),
  };
}

export function presentOntologyAiDiagnostic(code: string | null | undefined, _message: string | null | undefined): string {
  const normalized = (code || "").toUpperCase();
  if (normalized.includes("INVALID") || normalized.includes("VALIDATION") || normalized.includes("SCHEMA")) {
    return "提案未通过业务结构检查，请调整业务描述后重试。";
  }
  return "AI 暂时不可用，请稍后重试或继续手工编辑。";
}
