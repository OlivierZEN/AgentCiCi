import { describe, expect, it } from "vitest";
import {
  applicationCatalogStatusLabel,
  providerConnectionStatusLabel,
  suggestedDependencyConstraint,
  validApplicationCode,
  validSemanticVersion,
  versionStatusLabel,
} from "./PlatformInternalApplicationsPage";

describe("internal tenant application registration validation", () => {
  it("accepts stable lowercase application codes only", () => {
    expect(validApplicationCode("sales-workbench")).toBe(true);
    expect(validApplicationCode("a1")).toBe(true);
    expect(validApplicationCode("SalesWorkbench")).toBe(false);
    expect(validApplicationCode("a")).toBe(false);
    expect(validApplicationCode("sales_workbench")).toBe(false);
  });

  it("accepts release versions without mutable pre-release suffixes", () => {
    expect(validSemanticVersion("1.0.0")).toBe(true);
    expect(validSemanticVersion("12.8.31")).toBe(true);
    expect(validSemanticVersion("1.0")).toBe(false);
    expect(validSemanticVersion("1.0.0-beta.1")).toBe(false);
  });

  it("labels provider connection lifecycle without exposing topology details", () => {
    expect(providerConnectionStatusLabel("ACTIVE")).toBe("已启用");
    expect(providerConnectionStatusLabel("DISABLED")).toBe("已停用");
    expect(providerConnectionStatusLabel("DRAFT")).toBe("草稿");
  });

  it("derives dependency constraints from the selected published version", () => {
    expect(suggestedDependencyConstraint("2.3.1")).toBe(">=2.3.1");
    expect(suggestedDependencyConstraint(null)).toBe(">=1.0.0");
  });
});

describe("internal tenant application status labels", () => {
  it("distinguishes catalog and immutable version lifecycle states", () => {
    expect(applicationCatalogStatusLabel("PUBLISHED")).toBe("已发布");
    expect(applicationCatalogStatusLabel("SUSPENDED")).toBe("已暂停");
    expect(versionStatusLabel("VALIDATED")).toBe("已验证");
    expect(versionStatusLabel("REVOKED")).toBe("已撤销");
  });
});
