import { describe, expect, it } from "vitest";
import type { SkillDependencyGraphView } from "../../shared/SkillDependencyGraph";
import {
  buildPlatformSkillDependencyGraphUrl,
  isLatestPlatformSkillGraphRequest,
  preparePlatformSkillGraphForDisplay,
} from "./PlatformSkillsPage";

const unreferencedGraph: SkillDependencyGraphView = {
  scope: { type: "SKILL_IMPACT", id: "7", label: "订单查询" },
  sourceMode: "SKILL_IMPACT",
  nodes: [{
    id: "skill:7",
    type: "SKILL",
    label: "订单查询",
    detail: "order.lookup",
    status: "ACTIVE",
    layer: 0,
    metadata: { skillCode: "order.lookup" },
  }],
  edges: [],
  summary: {
    agentCount: 0,
    workflowVersionCount: 0,
    skillCount: 1,
    skillVersionCount: 0,
    toolCount: 0,
    knowledgeBaseCount: 0,
  },
  warnings: [],
};

describe("PlatformSkillsPage dependency graph", () => {
  it("uses the platform governance proxy route", () => {
    expect(buildPlatformSkillDependencyGraphUrl(27)).toBe("/api/platform/skills/27/dependency-graph");
  });

  it("accepts only the latest skill selection response", () => {
    expect(isLatestPlatformSkillGraphRequest(5, 5)).toBe(true);
    expect(isLatestPlatformSkillGraphRequest(4, 5)).toBe(false);
  });

  it("turns an unreferenced root-only graph into the governance empty state", () => {
    const prepared = preparePlatformSkillGraphForDisplay(unreferencedGraph);

    expect(prepared.nodes).toEqual([]);
    expect(prepared.edges).toEqual([]);
    expect(unreferencedGraph.nodes).toHaveLength(1);
  });
});
