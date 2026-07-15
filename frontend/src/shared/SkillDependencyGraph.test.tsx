import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import SkillDependencyGraph, {
  buildSkillDependencyLayout,
  prepareSkillDependencyGraph,
  type SkillDependencyGraphView,
} from "./SkillDependencyGraph";

const graph: SkillDependencyGraphView = {
  scope: {
    type: "AGENT_WORKFLOW",
    id: "support-agent",
    label: "售后助手",
    workflowVersionId: 81,
    versionNo: 4,
    publishStatus: "PUBLISHED",
  },
  sourceMode: "PINNED_WORKFLOW_VERSION",
  nodes: [
    {
      id: "agent:support-agent",
      type: "AGENT",
      label: "售后助手",
      detail: "support-agent",
      status: "ENABLED",
      layer: 0,
      metadata: { agentId: "support-agent" },
    },
    {
      id: "workflow-version:81",
      type: "WORKFLOW_VERSION",
      label: "工作流 v4",
      detail: "候选版本",
      status: "PUBLISHED",
      layer: 1,
      metadata: { versionNo: 4 },
    },
    {
      id: "skill:7",
      type: "SKILL",
      label: "订单查询",
      detail: "order.lookup",
      status: "ACTIVE",
      layer: 2,
      metadata: { skillCode: "order.lookup", riskLevel: "LOW" },
    },
  ],
  edges: [
    {
      id: "edge:compiled",
      source: "agent:support-agent",
      target: "workflow-version:81",
      type: "COMPILED_AS",
      label: "编译为",
    },
    {
      id: "edge:skill",
      source: "workflow-version:81",
      target: "skill:7",
      type: "USES_SKILL",
      label: "引用 Skill",
    },
  ],
  summary: {
    agentCount: 1,
    workflowVersionCount: 1,
    skillCount: 1,
    skillVersionCount: 0,
    toolCount: 0,
    knowledgeBaseCount: 0,
  },
  warnings: ["历史知识库元数据不可用。"],
};

describe("SkillDependencyGraph", () => {
  it("builds a deterministic left-to-right layered layout", () => {
    const first = buildSkillDependencyLayout(graph.nodes, graph.edges);
    const second = buildSkillDependencyLayout([...graph.nodes].reverse(), [...graph.edges].reverse());

    expect(first).toEqual(second);
    expect(first.nodes.map((node) => [node.id, node.layer, node.x])).toEqual([
      ["agent:support-agent", 0, 36],
      ["workflow-version:81", 1, 300],
      ["skill:7", 2, 564],
    ]);
    expect(first.edges).toHaveLength(2);
    expect(first.edges[0]?.path).toContain("C");
  });

  it("drops invalid edges and exposes a warning instead of rendering a broken path", () => {
    const prepared = prepareSkillDependencyGraph({
      ...graph,
      edges: [
        ...graph.edges,
        {
          id: "edge:missing",
          source: "skill:missing",
          target: "skill:7",
          type: "VERSION_OF",
          label: "缺失端点",
        },
      ],
    });

    expect(prepared.edges).toHaveLength(2);
    expect(prepared.warnings).toContain("依赖关系 edge:missing 的端点不存在，已忽略。");
  });

  it("renders accessible nodes, controls, summary and warnings", () => {
    const html = renderToStaticMarkup(
      <SkillDependencyGraph graph={graph} ariaLabel="售后助手 Skill 依赖图" />,
    );

    expect(html).toContain('aria-label="售后助手 Skill 依赖图"');
    expect(html).toContain('aria-label="放大依赖图"');
    expect(html).toContain('aria-label="适配依赖图"');
    expect(html).toContain("售后助手");
    expect(html).toContain("订单查询");
    expect(html).toContain("1 个 Skill");
    expect(html).toContain("历史知识库元数据不可用。");
  });

  it("renders loading, error and empty states with a retry command", () => {
    const loading = renderToStaticMarkup(<SkillDependencyGraph graph={null} loading />);
    const error = renderToStaticMarkup(
      <SkillDependencyGraph graph={null} error="依赖图加载失败" onRetry={() => undefined} />,
    );
    const empty = renderToStaticMarkup(<SkillDependencyGraph graph={{ ...graph, nodes: [], edges: [] }} />);

    expect(loading).toContain("正在加载 Skill 依赖");
    expect(error).toContain("依赖图加载失败");
    expect(error).toContain("重试");
    expect(empty).toContain("当前没有可展示的 Skill 依赖");
  });
});
