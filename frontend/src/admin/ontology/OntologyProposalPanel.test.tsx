import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import OntologyProposalPanel from "./OntologyProposalPanel";
import type { OntologyDocument, OntologyProposalRecord } from "./ontologyTypes";

const candidate: OntologyDocument = {
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
    properties: [{
      key: "name",
      name: "项目名称",
      description: null,
      dataType: "TEXT",
      required: true,
      multiple: false,
      sensitive: true,
      queryable: true,
      enumValues: [],
    }],
  }],
  relations: [],
  metrics: [],
  actions: [],
  dataSources: [],
  mappings: [],
};

describe("OntologyProposalPanel", () => {
  it("shows a locked composer without pretending an AI request is still running", () => {
    const markup = renderToStaticMarkup(
      <OntologyProposalPanel
        currentDocument={candidate}
        sources={[]}
        catalog={null}
        proposals={[]}
        activeProposal={null}
        loading={false}
        busy={false}
        locked
        error="提案已应用，重新加载失败；请重新加载草稿，勿重复应用。"
        generateDisabledReason="草稿修订已变化，请先重新加载。"
        applyDisabledReason="草稿修订已变化，请先重新加载。"
        onReload={() => undefined}
        onSelect={() => undefined}
        onGenerate={() => undefined}
        onApply={() => undefined}
        onContinueManually={() => undefined}
      />,
    );

    expect(markup).toContain('<textarea rows="4" disabled=""');
    expect(markup).toContain("AI 操作需要处理");
    expect(markup).toContain("生成可审阅提案");
    expect(markup).not.toContain("正在生成");
  });

  it("presents failed diagnostics and validation issues in business language", () => {
    const proposalCandidate: OntologyDocument = {
      ...candidate,
      concepts: [...candidate.concepts, {
        ...candidate.concepts[0],
        key: "owner",
        name: "负责人",
        displayPropertyKey: "name",
        properties: [{ ...candidate.concepts[0].properties[0], key: "name", name: "负责人姓名" }],
      }],
    };
    const proposal: OntologyProposalRecord = {
      id: 8,
      workspaceId: 3,
      proposalType: "DOMAIN_FIRST",
      status: "FAILED",
      candidate: proposalCandidate,
      diff: {
        baseRevision: 4,
        candidateHash: "candidate-hash",
        added: ["concept:owner"],
        changed: ["property:project.name"],
        removed: [],
      },
      validation: [{
        severity: "ERROR",
        code: "SENSITIVE_PROPERTY_QUERYABLE",
        path: "$.concepts[0].properties[0].queryable",
        message: "Sensitive properties cannot be queryable",
      }],
      diagnosticCode: "AI_PROPOSAL_INVALID",
      diagnosticMessage: "provider schema failed at $.concepts[0]",
      createdAt: "2026-07-17T01:00:00Z",
      updatedAt: "2026-07-17T01:00:00Z",
      appliedAt: null,
    };

    const markup = renderToStaticMarkup(
      <OntologyProposalPanel
        currentDocument={candidate}
        sources={[]}
        catalog={null}
        proposals={[proposal]}
        activeProposal={proposal}
        loading={false}
        busy={false}
        locked={false}
        error=""
        generateDisabledReason=""
        applyDisabledReason=""
        onReload={() => undefined}
        onSelect={() => undefined}
        onGenerate={() => undefined}
        onApply={() => undefined}
        onContinueManually={() => undefined}
      />,
    );

    expect(markup).toContain("提案未通过业务结构检查，请调整业务描述后重试。");
    expect(markup).toContain("业务对象“项目”的属性“项目名称”");
    expect(markup).toContain("敏感信息不能用于业务查询，请关闭“可查询”。");
    expect(markup).toContain("业务对象“负责人”");
    expect(markup).toContain("业务属性“项目 / 项目名称”");
    expect(markup).not.toContain("AI_PROPOSAL_INVALID");
    expect(markup).not.toContain("concept:owner");
    expect(markup).not.toContain("property:project.name");
    expect(markup).not.toContain("$.concepts");
    expect(markup).not.toContain("Sensitive properties");
    expect(markup).not.toContain("provider schema");
  });
});
