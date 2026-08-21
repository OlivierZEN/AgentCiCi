import { describe, expect, it } from "vitest";
import { isPlatformNavigationItemActive, PLATFORM_NAVIGATION_GROUPS } from "./PlatformShell";

describe("platform model navigation", () => {
  const capabilityItems = PLATFORM_NAVIGATION_GROUPS.find((group) => group.id === "capability")?.items ?? [];
  const modelItems = capabilityItems.filter((item) => item.to.startsWith("/platform/models"));

  it("exposes one model configuration entry", () => {
    expect(modelItems).toEqual([
      {
        to: "/platform/models/providers",
        label: "模型配置",
        activePrefixes: ["/platform/models"],
      },
    ]);
  });

  it.each([
    "/platform/models",
    "/platform/models/providers",
    "/platform/models/routes",
  ])("keeps the unified entry active for %s", (pathname) => {
    expect(isPlatformNavigationItemActive(modelItems[0], pathname)).toBe(true);
  });
});

describe("platform skill governance navigation", () => {
  const capabilityItems = PLATFORM_NAVIGATION_GROUPS.find((group) => group.id === "capability")?.items ?? [];
  const skillItems = capabilityItems.filter((item) => item.to.startsWith("/platform/skills"));

  it("exposes one unified skill governance entry", () => {
    expect(skillItems).toEqual([
      {
        to: "/platform/skills",
        label: "技能治理",
        activePrefixes: ["/platform/skills"],
      },
    ]);
  });

  it.each([
    "/platform/skills",
    "/platform/skills/policies",
    "/platform/skills/27",
    "/platform/skills/27/edit",
    "/platform/skills/27/preview",
    "/platform/skills/policy/edit",
  ])("keeps the unified entry active for %s", (pathname) => {
    expect(isPlatformNavigationItemActive(skillItems[0], pathname)).toBe(true);
  });
});

describe("platform system API navigation", () => {
  const capabilityItems = PLATFORM_NAVIGATION_GROUPS.find((group) => group.id === "capability")?.items ?? [];
  const systemApiItems = capabilityItems.filter((item) => item.to === "/platform/system-apis");

  it("exposes one governed catalog with two provider children", () => {
    expect(systemApiItems).toEqual([
      {
        to: "/platform/system-apis",
        label: "系统 API",
        activePrefixes: ["/platform/system-apis"],
        children: [
          { to: "/platform/system-apis/agentcici", label: "AgentCiCi" },
          { to: "/platform/system-apis/semattice", label: "Semattice" },
        ],
      },
    ]);
  });

  it.each([
    "/platform/system-apis",
    "/platform/system-apis/agentcici",
    "/platform/system-apis/semattice/runtime.record.query/docs",
  ])("keeps the parent active for %s", (pathname) => {
    expect(isPlatformNavigationItemActive(systemApiItems[0], pathname)).toBe(true);
  });
});

describe("platform tenant application catalog navigation", () => {
  const capabilityItems = PLATFORM_NAVIGATION_GROUPS.find((group) => group.id === "capability")?.items ?? [];
  const applicationItems = capabilityItems.filter((item) => item.to === "/platform/internal-applications");

  it("exposes one governed internal application catalog entry", () => {
    expect(applicationItems).toEqual([
      {
        to: "/platform/internal-applications",
        label: "应用中心",
        activePrefixes: ["/platform/internal-applications"],
      },
    ]);
  });

  it("keeps the entry active on application detail routes", () => {
    expect(isPlatformNavigationItemActive(applicationItems[0], "/platform/internal-applications/devautopilot")).toBe(true);
  });
});

describe("platform operations center navigation", () => {
  const operationsCenter = PLATFORM_NAVIGATION_GROUPS.find((group) => group.id === "operations_center");

  it("exposes deployment installation under an independent operations center", () => {
    expect(operationsCenter).toEqual({
      id: "operations_center",
      label: "运维中心",
      items: [
        {
          to: "/platform/operations/deployment-installation",
          label: "部署安装",
          activePrefixes: ["/platform/operations/deployment-installation"],
        },
      ],
    });
  });

  it("keeps the deployment entry active for direct sections", () => {
    const deploymentItem = operationsCenter?.items[0];
    expect(deploymentItem).toBeDefined();
    expect(isPlatformNavigationItemActive(deploymentItem!, "/platform/operations/deployment-installation")).toBe(true);
  });
});
