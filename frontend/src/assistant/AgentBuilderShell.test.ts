import { describe, expect, it } from "vitest";
import { applyAgentDetailToList, resolveAgentDetailTarget } from "./AgentBuilderShell";

describe("AgentBuilderShell focused agent loading", () => {
  const firstAgent = {
    agentId: "agent-first",
    name: "First Agent",
    skillBindings: [],
  };
  const focusedAgentListItem = {
    agentId: "agent-focused",
    name: "Focused Agent",
    skillBindings: [],
  };

  it("loads the focused agent detail when the focused id exists in the list", () => {
    expect(resolveAgentDetailTarget([firstAgent, focusedAgentListItem], "agent-focused")).toBe("agent-focused");
  });

  it("falls back to the first agent detail when the focused id is absent", () => {
    expect(resolveAgentDetailTarget([firstAgent, focusedAgentListItem], "missing-agent")).toBe("agent-first");
  });

  it("keeps detail skill bindings on the matching focused agent only", () => {
    const focusedDetail = {
      ...focusedAgentListItem,
      summary: "Loaded from detail",
      skillBindings: [
        {
          skillId: 7,
          skillCode: "crm.lookup",
          skillName: "CRM Lookup",
          riskLevel: "LOW" as const,
          activationMode: "intent-route" as const,
          activationCondition: "需要查询客户",
          priority: 20,
          enabled: true,
        },
      ],
    };

    const merged = applyAgentDetailToList([firstAgent, focusedAgentListItem], focusedDetail);

    expect(merged[0]).toBe(firstAgent);
    expect(merged[1]).toEqual(focusedDetail);
    expect(merged[1]?.skillBindings).toHaveLength(1);
  });
});
