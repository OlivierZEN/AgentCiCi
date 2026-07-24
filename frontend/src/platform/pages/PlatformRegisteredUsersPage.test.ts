import { describe, expect, it } from "vitest";
import { formatRegisteredUserOrganizations, registeredUserDirectoryCopy } from "./PlatformRegisteredUsersPage";

describe("PlatformRegisteredUsersPage copy", () => {
  it("states that the directory includes all personal users once", () => {
    expect(registeredUserDirectoryCopy.subtitle).toContain("全部个人用户");
    expect(registeredUserDirectoryCopy.subtitle).toContain("已加入组织的用户同样显示");
    expect(registeredUserDirectoryCopy.subtitle).toContain("仅保留一条记录");
    expect(registeredUserDirectoryCopy.sectionLabel).toBe("全平台个人用户目录");
  });

  it("formats every current organization on one account row", () => {
    expect(formatRegisteredUserOrganizations([
      { id: "company-1", name: "第一组织" },
      { id: "company-2", name: "第二组织" },
    ])).toBe("第一组织、第二组织");
    expect(formatRegisteredUserOrganizations([])).toBe("未加入组织");
  });
});
