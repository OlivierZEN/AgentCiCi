// @ts-expect-error Vitest executes this test in Node; production sources do not depend on Node types.
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

function relativeLuminance(hex: string): number {
  const channels = hex.match(/[a-f\d]{2}/gi)?.map((pair) => Number.parseInt(pair, 16) / 255) ?? [];
  const linear = channels.map((channel) => channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4);
  return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2];
}

function contrast(first: string, second: string): number {
  const [light, dark] = [relativeLuminance(first), relativeLuminance(second)].sort((a, b) => b - a);
  return (light + 0.05) / (dark + 0.05);
}

describe("ontology workbench release contracts", () => {
  const appSource = readFileSync(new URL("../../App.tsx", import.meta.url), "utf8");
  const pageSource = readFileSync(new URL("../pages/AdminOntologyPage.tsx", import.meta.url), "utf8");
  const cssSource = readFileSync(new URL("../../styles/admin-ontology.css", import.meta.url), "utf8");
  const themeCssSource = readFileSync(new URL("../../theme/theme.css", import.meta.url), "utf8");

  it("uses the official data router blocker for browser history navigation", () => {
    expect(appSource).toContain("createBrowserRouter");
    expect(appSource).toContain("<RouterProvider");
    expect(appSource).not.toContain("<BrowserRouter>");
    expect(pageSource).toContain("useBlocker");
    expect(pageSource).toMatch(/useLayoutEffect\(\(\) => \(\) => \{\s*invalidateOntologyAsyncContext\(\);/);
  });

  it("focuses cancel first and uses complete roving tab semantics", () => {
    expect(pageSource).toMatch(/data-dialog-initial-focus[^>]*>取消<\/button>/);
    expect(pageSource).toContain('aria-controls={`ontology-panel-${tab.id}`}');
    expect(pageSource).toContain('role="tabpanel"');
    expect(pageSource).toContain("nextOntologyTabIndex");
    expect(pageSource).toContain("WIZARD_TABS.filter((tab) => tab.id !== wizardMode)");
    expect(pageSource).toContain("WORKSPACE_TABS.filter((tab) => draftStatus !== \"ready\" || !draft || tab.id !== activeTab)");
    expect(pageSource).toContain("TECHNICAL_TABS.filter((tab) => tab.id !== technicalTab)");
  });

  it("meets the ontology primary-button and warning contrast across light and Galaxy surfaces", () => {
    const primary = cssSource.match(/--ontology-primary:\s*(#[\da-f]{6})/i)?.[1];
    const onPrimary = cssSource.match(/--ontology-on-primary:\s*(#[\da-f]{6})/i)?.[1];
    const warning = cssSource.match(/--ontology-warning:\s*(#[\da-f]{6})/i)?.[1];
    const warningSoft = cssSource.match(/--ontology-warning-soft:\s*(#[\da-f]{6})/i)?.[1];
    expect(primary).toBeDefined();
    expect(onPrimary).toBeDefined();
    expect(warning).toBeDefined();
    expect(warningSoft).toBeDefined();
    expect(contrast(primary!, onPrimary!)).toBeGreaterThanOrEqual(4.5);
    expect(contrast(warning!, warningSoft!)).toBeGreaterThanOrEqual(4.5);
    expect(contrast(warning!, onPrimary!)).toBeGreaterThanOrEqual(4.5);

    const galaxyOverride = cssSource.match(
      /:root\[data-theme="galaxy"\] \.admin-main > \.ontology-page\s*\{([^}]*)}/,
    )?.[1] ?? "";
    const galaxyTheme = themeCssSource.match(/:root\[data-theme="galaxy"\]\s*\{([^}]*)}/)?.[1] ?? "";
    const galaxyWarning = galaxyTheme.match(/--theme-warning:\s*(#[\da-f]{6})/i)?.[1];
    const galaxyBackgrounds = [
      "--theme-canvas",
      "--theme-surface",
      "--theme-surface-muted",
      "--theme-surface-strong",
      "--theme-warning-soft",
    ].map((token) => galaxyTheme.match(new RegExp(`${token}:\\s*(#[\\da-f]{6})`, "i"))?.[1]);
    expect(galaxyOverride).toContain("--ontology-warning: var(--theme-warning");
    expect(galaxyOverride).toContain("--ontology-warning-soft: var(--theme-warning-soft");
    expect(galaxyWarning).toBeDefined();
    expect(galaxyBackgrounds.every(Boolean)).toBe(true);
    for (const background of galaxyBackgrounds) {
      expect(contrast(galaxyWarning!, background!)).toBeGreaterThanOrEqual(4.5);
    }
    expect(cssSource).not.toMatch(/font-size:\s*(?:9|10)px/);
  });

  it("does not render raw metric enums or validation implementation details", () => {
    expect(pageSource).not.toContain("{metric.aggregation}");
    expect(pageSource).not.toContain("<code>{issue.path}</code>");
    expect(pageSource).not.toContain("{issue.code}");
  });

  it("loads authoritative mappings before compile and keeps failed mapping reloads dirty", () => {
    const compileBlock = pageSource.slice(
      pageSource.indexOf("const loadCompilePreview"),
      pageSource.indexOf("const switchWorkspaceTab"),
    );
    expect(compileBlock.indexOf("api.listMappings(workspaceId)")).toBeGreaterThanOrEqual(0);
    expect(compileBlock.indexOf("api.listMappings(workspaceId)")).toBeLessThan(compileBlock.indexOf("api.compilePreview"));
    expect(compileBlock).toContain("isOntologyCompilePreviewResponseBound");
    expect(compileBlock).not.toContain("setMappingLoaded(true)");

    const saveBlock = pageSource.slice(
      pageSource.indexOf("const saveMappings"),
      pageSource.indexOf("const validateMappings"),
    );
    expect(saveBlock.indexOf("setMappingDirty(false)")).toBeGreaterThan(saveBlock.indexOf("await api.listMappings(workspaceId)"));
  });

  it("does not let internal mapping reloads or AI proposals overwrite unsaved mapping rows", () => {
    const loadBlock = pageSource.slice(
      pageSource.indexOf("const loadMappings"),
      pageSource.indexOf("const reloadMappings"),
    );
    expect(loadBlock).toContain("mappingDirty && !allowDirtyOverwrite");

    const reloadBlock = pageSource.slice(
      pageSource.indexOf("const reloadMappings"),
      pageSource.indexOf("const loadProposals"),
    );
    expect(reloadBlock).toContain("loadMappings(true)");
    expect(pageSource).toContain("if (!catalog && !mappingDirty) void loadMappings()");

    const generateBlock = pageSource.slice(
      pageSource.indexOf("const generateProposal"),
      pageSource.indexOf("const applyAiProposal"),
    );
    const applyBlock = pageSource.slice(
      pageSource.indexOf("const applyAiProposal"),
      pageSource.indexOf("const publishDraft"),
    );
    expect(generateBlock).toContain("if (mappingDirty)");
    expect(applyBlock).toContain("if (mappingDirty)");
    expect(pageSource).toMatch(/const acceptDraft[\s\S]*?setMappingLoaded\(false\)[\s\S]*?\}, \[invalidateCompilePreview\]\);/);
  });

  it("authoritatively reconciles workspace creation and locks unresolved retries", () => {
    const createBlock = pageSource.slice(
      pageSource.indexOf("const createWorkspaceRecoverably"),
      pageSource.indexOf("const createDataSourceRecoverably"),
    );
    expect(createBlock).toContain("isOntologyWorkspaceCreateReconciliationError");
    expect(createBlock).toContain("api.listWorkspaces()");
    expect(createBlock).toContain("findOntologyWorkspaceByCreateIdentity");
    expect(createBlock).toContain("setWorkspaceCreateLocked(true)");
    expect(createBlock).toContain("createdBy: userId");
    expect(pageSource).toContain("workspaceCreateLocked || Boolean(busyAction)");
    expect(pageSource).toContain("创建结果尚未确认，请先返回列表并刷新核对，勿重复创建。");
  });

  it("reconciles reference package installs by package identity and locks unresolved retries", () => {
    const installBlock = pageSource.slice(
      pageSource.indexOf("const installReferencePackageRecoverably"),
      pageSource.indexOf("const createWorkspaceRecoverably"),
    );
    expect(installBlock).toContain("isOntologyReferencePackageInstallReconciliationError");
    expect(installBlock).toContain("api.listWorkspaces()");
    expect(installBlock).toContain("findOntologyWorkspaceByReferencePackageIdentity");
    expect(installBlock).toContain("setReferencePackageInstallLocked(true)");
    expect(installBlock).toContain("userId");
    expect(pageSource).toContain("referencePackageInstallLocked || Boolean(busyAction)");
    expect(pageSource).toContain("参考包安装结果尚未确认，请先刷新业务本体列表核对，勿重复安装。");
  });
});
