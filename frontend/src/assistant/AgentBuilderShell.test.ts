import { describe, expect, it } from "vitest";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
// @ts-expect-error Vitest executes this test in Node; production sources do not depend on Node types.
import { readFileSync } from "node:fs";
import AgentBuilderShell from "./AgentBuilderShell";
import {
  AGENT_BUILDER_EDITOR_LAYOUT,
  AGENT_BUILDER_LIFECYCLE_TABS,
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
  toPublishConfig,
  webWidgetInstallSnippet,
  type BaseModelOption,
} from "./AgentBuilderShell";

const assistantCss = readFileSync(new URL("./cici-ui.css", import.meta.url), "utf8");

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

  it("keeps the right editor column dedicated to the system prompt", () => {
    expect(AGENT_BUILDER_EDITOR_LAYOUT.rightColumn).toEqual(["systemPrompt"]);
    expect(AGENT_BUILDER_EDITOR_LAYOUT.showModelGovernanceNotice).toBe(false);
  });
});

describe("Agent Builder avatar editing", () => {
  it("resolves upload and remove actions from the current avatar draft", async () => {
    const agentBuilderModule = await import("./AgentBuilderShell") as unknown as {
      resolveAgentAvatarMenuActions?: (avatarBase64: string) => {
        primaryLabel: string;
        canRemove: boolean;
      };
    };

    expect(typeof agentBuilderModule.resolveAgentAvatarMenuActions).toBe("function");
    expect(agentBuilderModule.resolveAgentAvatarMenuActions?.("")).toEqual({
      primaryLabel: "上传头像",
      canRemove: false,
    });
    expect(agentBuilderModule.resolveAgentAvatarMenuActions?.("data:image/png;base64,avatar")).toEqual({
      primaryLabel: "更换头像",
      canRemove: true,
    });
  });

  it("renders the avatar itself as the only persistent edit entry", () => {
    const previousWindow = globalThis.window;
    Object.defineProperty(globalThis, "window", {
      configurable: true,
      value: { location: { origin: "http://localhost" } },
    });
    let html = "";
    try {
      html = renderToStaticMarkup(createElement(AgentBuilderShell, {
        kbs: [],
        companyId: "org-test",
        token: "",
      }));
    } finally {
      Object.defineProperty(globalThis, "window", {
        configurable: true,
        value: previousWindow,
      });
    }

    expect(html).toContain('aria-haspopup="menu"');
    expect(html).toContain('aria-expanded="false"');
    expect(html).toContain('aria-label="编辑 未命名 Agent 头像"');
    expect(html).not.toContain("上传图片");
    expect(html).not.toContain("清除头像");
  });
});

describe("Agent Builder guide presentation", () => {
  it("keeps the builder guide frameless with a compact page inset", () => {
    const guideBlocks = [...assistantCss.matchAll(/\.cici-builder__guide\s*\{([^}]*)\}/g)]
      .map((match) => match[1] ?? "");

    expect(guideBlocks.length).toBeGreaterThanOrEqual(2);
    expect(guideBlocks[0]).toContain("margin: 0 0 6px");
    expect(guideBlocks[0]).toContain("padding: 2px 4px 6px");
    expect(guideBlocks.every((block) => block.includes("background: transparent"))).toBe(true);
    expect(guideBlocks.every((block) => block.includes("border: 0"))).toBe(true);
    expect(guideBlocks.every((block) => block.includes("border-radius: 0"))).toBe(true);
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

describe("Web widget publish configuration", () => {
  it("hydrates persisted web channel fields without inventing a tenant or identity", () => {
    const config = toPublishConfig({
      web: {
        enabled: true,
        widgetKey: "ww_1234567890abcdef12345678",
        allowedOrigins: ["https://portal.example.test"],
        runAsUserId: "member-1",
        assistantName: "售前跟进智能体",
        launcherLabel: "咨询售前",
        welcomeMessage: "你好",
        defaultOpen: false,
        tokenTtlSeconds: 600,
        rateLimitPerMinute: 20,
      },
    });

    expect(config.web).toMatchObject({
      widgetKey: "ww_1234567890abcdef12345678",
      allowedOrigins: ["https://portal.example.test"],
      runAsUserId: "member-1",
      launcherLabel: "咨询售前",
      tokenTtlSeconds: 600,
    });
  });

  it("generates install code with a non-secret widget key and deployment-owned origin placeholder", () => {
    const config = toPublishConfig({ web: {
      widgetKey: "ww_1234567890abcdef12345678",
      launcherLabel: "咨询售前",
      defaultOpen: false,
    } });
    const snippet = webWidgetInstallSnippet(config.web);

    expect(snippet).toContain("{{AGENTCICI_ORIGIN}}/sdk/sisi@1.1.0.js");
    expect(snippet).toContain("ww_1234567890abcdef12345678");
    expect(snippet).toContain("/public/web-widgets/");
    expect(snippet).toContain("widgetConfig.agentAvatarBase64");
    expect(snippet).not.toContain("companyId");
    expect(snippet).not.toContain("runAsUserId");
    expect(snippet).not.toContain("Api-Key");
  });
});
