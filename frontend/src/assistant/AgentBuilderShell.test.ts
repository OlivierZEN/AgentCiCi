import { describe, expect, it } from "vitest";
import {
  applyAgentDetailToList,
  MODEL_CONFIG_REQUIRED_NOTICE,
  resolveAgentCreationModel,
  resolveAgentAfterDelete,
  resolveAgentDetailTarget,
  type BaseModelOption,
} from "./AgentBuilderShell";

const modelOptions: BaseModelOption[] = [
  { value: "qwen3.6-plus", label: "qwen3.6-plus · Bailian", note: "Bailian" },
];

describe("resolveAgentCreationModel", () => {
  it("requires model configuration when no draft model or fallback model exists", () => {
    expect(resolveAgentCreationModel("", [])).toEqual({ model: "", requiresModelConfig: true });
    expect(MODEL_CONFIG_REQUIRED_NOTICE).toBe("请先配置模型");
  });

  it("uses the first available base model for new Agent creation", () => {
    expect(resolveAgentCreationModel("", modelOptions)).toEqual({ model: "qwen3.6-plus", requiresModelConfig: false });
  });

  it("preserves an existing draft model", () => {
    expect(resolveAgentCreationModel("deepseek-chat", modelOptions)).toEqual({
      model: "deepseek-chat",
      requiresModelConfig: false,
    });
  });
});

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

describe("resolveAgentAfterDelete", () => {
  const agents = [
    { id: "agent-a", name: "A" },
    { id: "agent-b", name: "B" },
    { id: "agent-c", name: "C" },
  ];

  it("keeps the current selection when deleting another agent", () => {
    const result = resolveAgentAfterDelete(agents, "agent-a", "agent-b");

    expect(result.nextAgents.map((item) => item.id)).toEqual(["agent-b", "agent-c"]);
    expect(result.fallbackAgentId).toBe("agent-b");
  });

  it("falls back to the next available agent when deleting the selected one", () => {
    const result = resolveAgentAfterDelete(agents, "agent-a", "agent-a");

    expect(result.nextAgents.map((item) => item.id)).toEqual(["agent-b", "agent-c"]);
    expect(result.fallbackAgentId).toBe("agent-b");
  });

  it("returns an empty fallback when the last agent is deleted", () => {
    const result = resolveAgentAfterDelete([{ id: "agent-a" }], "agent-a", "agent-a");

    expect(result.nextAgents).toEqual([]);
    expect(result.fallbackAgentId).toBe("");
  });
});
