import { describe, expect, it } from "vitest";
import { registeredUserDirectoryCopy } from "./PlatformRegisteredUsersPage";

describe("PlatformRegisteredUsersPage copy", () => {
  it("states that the directory includes all personal users once", () => {
    expect(registeredUserDirectoryCopy.subtitle).toContain("全部个人用户");
    expect(registeredUserDirectoryCopy.subtitle).toContain("已加入组织的用户同样显示");
    expect(registeredUserDirectoryCopy.subtitle).toContain("仅保留一条记录");
    expect(registeredUserDirectoryCopy.sectionLabel).toBe("全平台个人用户目录");
  });
});
