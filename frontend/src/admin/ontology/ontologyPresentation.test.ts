import { describe, expect, it } from "vitest";
import {
  ontologyAggregationLabel,
  ontologySourceStatusLabel,
  presentOntologyAiDiagnostic,
  presentOntologyDiffItem,
  presentOntologyValidationIssue,
  SUPPORTED_ONTOLOGY_CONNECTORS,
} from "./ontologyPresentation";
import type { OntologyDocument } from "./ontologyTypes";

const document: OntologyDocument = {
  key: "delivery",
  name: "项目交付",
  description: null,
  concepts: [{
    key: "project",
    name: "项目",
    pluralName: "项目",
    description: null,
    conceptType: "ENTITY",
    displayPropertyKey: "name",
    positionX: 80,
    positionY: 80,
    queryable: true,
    enabled: true,
    properties: [{ key: "name", name: "项目名称", description: null, dataType: "TEXT", required: true, multiple: false, sensitive: false, queryable: true, enumValues: [] }],
  }],
  relations: [], metrics: [], actions: [], dataSources: [], mappings: [],
};

describe("ontology business presentation", () => {
  it("localizes implementation enums and exposes connector business names only", () => {
    expect(ontologyAggregationLabel("COUNT")).toBe("数量");
    expect(ontologySourceStatusLabel("ACTIVE")).toBe("已连接");
    expect(SUPPORTED_ONTOLOGY_CONNECTORS).toEqual([{ value: "cloudcc", label: "CloudCC CRM（组织已配置连接）" }]);
  });

  it("turns validation paths, codes and English messages into business guidance", () => {
    const presented = presentOntologyValidationIssue({
      severity: "ERROR",
      code: "SENSITIVE_PROPERTY_QUERYABLE",
      path: "$.concepts[0].properties[0].queryable",
      message: "Sensitive properties cannot be queryable",
    }, document);

    expect(presented).toEqual({ severityLabel: "错误", location: "业务对象“项目”的属性“项目名称”", message: "敏感信息不能用于业务查询，请关闭“可查询”。" });
    expect(JSON.stringify(presented)).not.toContain("SENSITIVE_PROPERTY_QUERYABLE");
    expect(JSON.stringify(presented)).not.toContain("$.concepts");
    expect(JSON.stringify(presented)).not.toContain("Sensitive properties");
  });

  it("never exposes provider diagnostics to business users", () => {
    expect(presentOntologyAiDiagnostic("UPSTREAM_TIMEOUT", "provider x timed out at /v1/chat")).toBe("AI 暂时不可用，请稍后重试或继续手工编辑。");
    expect(presentOntologyAiDiagnostic("AI_PROPOSAL_INVALID", "$.concepts[0] invalid")).toBe("提案未通过业务结构检查，请调整业务描述后重试。");
  });

  it("turns proposal stable-element keys into business names", () => {
    const candidate = {
      ...document,
      concepts: [...document.concepts, {
        ...document.concepts[0],
        key: "owner",
        name: "负责人",
      }],
    };

    expect(presentOntologyDiffItem("concept:owner", document, candidate)).toBe("业务对象“负责人”");
    expect(presentOntologyDiffItem("property:project.name", document, candidate)).toBe("业务属性“项目 / 项目名称”");
    expect(presentOntologyDiffItem("unknown:raw-internal-code", document, candidate)).toBe("业务定义项");
  });
});
