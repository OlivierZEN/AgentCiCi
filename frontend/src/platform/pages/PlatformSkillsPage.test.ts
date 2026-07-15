import { describe, expect, it } from "vitest";
import type { SkillDependencyGraphView } from "../../shared/SkillDependencyGraph";
import {
  buildPlatformSkillDependencyGraphUrl,
  canStartPlatformSkillWriteOperation,
  isCurrentPlatformSkillRequest,
  isLatestPlatformSkillGraphRequest,
  preparePlatformSkillGraphForDisplay,
  resolvePlatformSkillRefreshTarget,
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

  it("rejects a late response after the user switches skills or starts a newer load", () => {
    expect(isCurrentPlatformSkillRequest(5, 5, 27, 27, 8, 8)).toBe(true);
    expect(isCurrentPlatformSkillRequest(5, 5, 27, 28, 8, 8)).toBe(false);
    expect(isCurrentPlatformSkillRequest(5, 5, 27, 27, 7, 8)).toBe(false);
  });

  it("refreshes the user's current Skill after an older Skill operation completes", () => {
    expect(resolvePlatformSkillRefreshTarget(27, 28)).toBe(28);
    expect(resolvePlatformSkillRefreshTarget(27, 27)).toBe(27);
    expect(resolvePlatformSkillRefreshTarget(27, null)).toBe(27);
  });

  it("blocks save and publish until the newly selected Skill detail is loaded", () => {
    expect(canStartPlatformSkillWriteOperation(27, 28, true)).toBe(false);
    expect(canStartPlatformSkillWriteOperation(28, 28, true)).toBe(false);
    expect(canStartPlatformSkillWriteOperation(28, 28, false)).toBe(true);
  });

  it("turns an unreferenced root-only graph into the governance empty state", () => {
    const prepared = preparePlatformSkillGraphForDisplay(unreferencedGraph);

    expect(prepared.nodes).toEqual([]);
    expect(prepared.edges).toEqual([]);
    expect(unreferencedGraph.nodes).toHaveLength(1);
  });

  it("preserves warnings when an unreferenced graph becomes an empty state", () => {
    const prepared = preparePlatformSkillGraphForDisplay({
      ...unreferencedGraph,
      warnings: ["工作流引用不完整。"],
    });

    expect(prepared.nodes).toEqual([]);
    expect(prepared.warnings).toEqual(["工作流引用不完整。"]);
  });
});
