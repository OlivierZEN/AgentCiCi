import { describe, expect, it } from "vitest";
// @ts-expect-error Vitest executes this test in Node; production sources do not depend on Node types.
import { readFileSync } from "node:fs";
import {
  DEFAULT_PRODUCT_THEME,
  normalizeProductTheme,
  PRODUCT_THEME_CODES,
  PRODUCT_THEMES,
} from "./theme";

const themeCss = readFileSync(new URL("./theme.css", import.meta.url), "utf8");
const assistantCss = readFileSync(new URL("../assistant/cici-ui.css", import.meta.url), "utf8");
const dataInsightSource = readFileSync(new URL("../assistant/data-insight/DataInsightAppPanel.tsx", import.meta.url), "utf8");
const assistantSource = readFileSync(new URL("../assistant/AssistantApp.tsx", import.meta.url), "utf8");

describe("product theme catalog", () => {
  it("exposes the approved eight stable themes", () => {
    expect(PRODUCT_THEME_CODES).toEqual([
      "gilded",
      "crm-blue",
      "ocean",
      "sakura",
      "lavender",
      "avocado",
      "wine",
      "galaxy",
    ]);
    expect(PRODUCT_THEMES).toHaveLength(8);
    expect(new Set(PRODUCT_THEMES.map((theme) => theme.code)).size).toBe(8);
  });

  it("normalizes known values and safely falls back to gilded", () => {
    expect(normalizeProductTheme(" CRM-BLUE ")).toBe("crm-blue");
    expect(normalizeProductTheme("galaxy")).toBe("galaxy");
    expect(normalizeProductTheme("custom-css")).toBe(DEFAULT_PRODUCT_THEME);
    expect(normalizeProductTheme(null)).toBe(DEFAULT_PRODUCT_THEME);
  });

  it("keeps galaxy as the only dark theme", () => {
    expect(PRODUCT_THEMES.filter((theme) => theme.dark).map((theme) => theme.code)).toEqual(["galaxy"]);
  });

  it("provides theme-owned data colors and covers every authenticated frontend surface", () => {
    for (const token of ["--theme-data-1", "--theme-data-2", "--theme-data-3", "--theme-data-4"]) {
      expect((themeCss.match(new RegExp(token, "g")) ?? []).length).toBeGreaterThanOrEqual(8);
    }
    for (const selector of [
      ".cici-ai-apps-flyout",
      ".cici-data-board",
      ".zhiwei-demo",
      ".memory-panel",
      ".customer-workbench-ingestion",
      ".cici-org-menu",
    ]) {
      expect(themeCss).toContain(selector);
    }
  });

  it("keeps workbench structure transparent instead of turning sections into theme cards", () => {
    expect(themeCss).toContain(":root[data-theme] .cici-workbench__layout,");
    expect(themeCss).toContain(":root[data-theme] .cici-workbench__sidebar-card {");
    expect(themeCss).toContain(":root[data-theme] .cici-workbench__machine-lane,");
    expect(themeCss).not.toContain(":root[data-theme] .cici-workbench__machine-lane.is-current");
    expect(themeCss).not.toContain(":root[data-theme] .cici-workbench__agent-chip.is-active,\n:root[data-theme] .cici-composer-tool:hover");
  });

  it("keeps agent avatars geometrically stable across hover and selected states", () => {
    expect(assistantCss).not.toContain("transition: transform 140ms ease, box-shadow 140ms ease");
    expect(assistantCss).toContain(".cici-workbench__agent-chip.is-active .cici-workbench__agent-avatar {\n  transform: none;\n  box-shadow: none;");
  });

  it("routes dashboard series through theme-owned classes", () => {
    expect(dataInsightSource).not.toContain("background: item.color");
    expect(dataInsightSource).toContain("is-series-${(index % 4) + 1}");
  });

  it("routes agent identity colors through theme-owned classes", () => {
    expect(assistantSource).not.toContain("style={{ background: agent.accent }}");
    expect(assistantSource).not.toContain("style={{ background: activeAgent.accent }}");
    expect(assistantSource).not.toContain("style={{ background: agent.color }}");
    expect(assistantSource).not.toContain("style={{ background: activeWorkbenchAgent.color }}");
    expect(assistantSource).not.toContain("getAvatarColor(thread.participantName)");
    expect(assistantSource).toContain("getThemeSeriesClass");
  });
});
