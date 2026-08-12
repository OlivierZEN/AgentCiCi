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
