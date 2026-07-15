import { describe, expect, it } from "vitest";
import {
  AGENT_BUILDER_LIFECYCLE_TABS,
  AGENT_MODEL_GOVERNANCE_NOTICE,
  applyAgentDetailToList,
  buildAgentSkillDagUrl,
  buildDebugSkillResolutionChain,
  canSelectAgentDuringOperation,
  canStartAgentWriteOperation,
  isCurrentAgentOperation,
  isCurrentAgentSelection,
  isLatestSkillDagRequest,
  MODEL_CONFIG_REQUIRED_NOTICE,
  resolveAgentCreationModel,
  resolveAgentAfterDelete,
  resolveAgentChannels,
  resolveAgentDetailTarget,
  type BaseModelOption,
} from "./AgentBuilderShell";

describe("Agent Builder Skill DAG lifecycle", () => {
  it("requests an explicit compiled version without leaking the raw agent id", () => {
    expect(buildAgentSkillDagUrl("agent /售后", 12)).toBe("/agents/agent%20%2F%E5%94%AE%E5%90%8E/skill-dag?versionNo=12");
    expect(buildAgentSkillDagUrl("agent-current", null)).toBe("/agents/agent-current/skill-dag");
  });

  it("accepts only the latest dependency graph response", () => {
    expect(isLatestSkillDagRequest(8, 8)).toBe(true);
    expect(isLatestSkillDagRequest(7, 8)).toBe(false);
  });

  it("rejects compile follow-up work after the selected Agent changes", () => {
    expect(isCurrentAgentOperation("agent-a", "agent-a", 4, 4)).toBe(true);
    expect(isCurrentAgentOperation("agent-a", "agent-b", 4, 4)).toBe(false);
    expect(isCurrentAgentOperation("agent-a", "agent-a", 3, 4)).toBe(false);
  });

  it("rejects an older Agent detail response after a newer selection starts", () => {
    expect(isCurrentAgentSelection(6, 6, "agent-b", "agent-b")).toBe(true);
    expect(isCurrentAgentSelection(5, 6, "agent-a", "agent-b")).toBe(false);
    expect(isCurrentAgentSelection(6, 6, "agent-a", "agent-b")).toBe(false);
  });

  it("blocks save and compile while the selected Agent detail is changing", () => {
    expect(canStartAgentWriteOperation("agent-a", "agent-b", true)).toBe(false);
    expect(canStartAgentWriteOperation("agent-b", "agent-b", true)).toBe(false);
    expect(canStartAgentWriteOperation("agent-b", "agent-b", false)).toBe(true);
  });

  it("blocks Agent selection while target-bound operations are running", () => {
    expect(canSelectAgentDuringOperation(false, false, false, false)).toBe(true);
    expect(canSelectAgentDuringOperation(true, false, false, false)).toBe(false);
    expect(canSelectAgentDuringOperation(false, true, false, false)).toBe(false);
    expect(canSelectAgentDuringOperation(false, false, true, false)).toBe(false);
    expect(canSelectAgentDuringOperation(false, false, false, true)).toBe(false);
  });

  it("builds a structured pinned Skill resolution chain for debug", () => {
    expect(buildDebugSkillResolutionChain(
      [{
        skillCode: "crm.lookup",
        skillName: "客户查询",
        skillVersionNo: 3,
        templateCode: "crm-standard",
        templateVersionNo: 2,
        referenceMode: "PINNED_VERSION",
        riskLevel: "LOW",
      }],
      [{
        skillId: 7,
        skillCode: "crm.lookup",
        skillName: "客户查询",
        riskLevel: "HIGH",
        activationMode: "intent-route",
        activationCondition: "需要客户信息",
        priority: 10,
        enabled: true,
      }],
    )).toEqual([{
      id: "crm.lookup:3",
      name: "客户查询",
      code: "crm.lookup",
      versionLabel: "v3",
      referenceLabel: "模板 crm-standard@v2",
      riskLabel: "低风险",
      activationLabel: "工作流钉住",
    }]);
  });

  it("uses draft governance only for capability fallback resolution", () => {
    expect(buildDebugSkillResolutionChain(
      [{
        skillCode: "crm.lookup",
        skillName: "客户查询",
        referenceMode: "capability-fallback",
        riskLevel: null,
      }],
      [{
        skillId: 7,
        skillCode: "crm.lookup",
        skillName: "客户查询",
        riskLevel: "HIGH",
        activationMode: "intent-route",
        activationCondition: "需要客户信息",
        priority: 10,
        enabled: true,
      }],
    )[0]).toMatchObject({
      referenceLabel: "当前绑定",
      riskLabel: "高风险",
      activationLabel: "意图路由",
    });
  });
});

describe("Agent Builder information architecture", () => {
  it("places evaluation and delivery channels in the lower version lifecycle", () => {
    expect(AGENT_BUILDER_LIFECYCLE_TABS.map((tab) => tab.id)).toEqual([
      "preview",
      "triggers",
      "debug",
      "evaluation",
      "history",
      "publish",
      "executions",
      "summary",
      "code",
      "manifest",
    ]);
    expect(AGENT_BUILDER_LIFECYCLE_TABS.find((tab) => tab.id === "evaluation")?.purpose).toBe("quality-governance");
    expect(AGENT_BUILDER_LIFECYCLE_TABS.find((tab) => tab.id === "publish")?.purpose).toBe("delivery-channels");
    expect(AGENT_BUILDER_LIFECYCLE_TABS.some((tab) => tab.id === ("definition" as never))).toBe(false);
  });

  it("keeps concrete model choice under platform governance", () => {
    expect(AGENT_MODEL_GOVERNANCE_NOTICE).toContain("平台统一策略自动选择");
    expect(AGENT_MODEL_GOVERNANCE_NOTICE).toContain("运营方集中管理");
  });
});

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

describe("resolveAgentChannels", () => {
  it("preserves an empty channel list returned by the API", () => {
    expect(resolveAgentChannels([], ["wechat", "dingtalk"])).toEqual([]);
  });

  it("uses fallback channels only when the API omits the channels field", () => {
    expect(resolveAgentChannels(undefined, ["wechat", "dingtalk"])).toEqual(["wechat", "dingtalk"]);
  });

  it("keeps valid API channels and drops unknown values", () => {
    expect(resolveAgentChannels(["api", "unknown", "web"], ["wechat"])).toEqual(["api", "web"]);
  });
});
