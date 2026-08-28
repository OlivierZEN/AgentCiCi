import { describe, expect, it } from "vitest";
// @ts-expect-error Vitest executes this test in Node; production sources do not depend on Node types.
import { readFileSync } from "node:fs";

const stylesCss = readFileSync(new URL("../styles.css", import.meta.url), "utf8");
const assistantCss = readFileSync(new URL("../assistant/cici-ui.css", import.meta.url), "utf8");
const themeCss = readFileSync(new URL("./theme.css", import.meta.url), "utf8");
const embedCss = readFileSync(new URL("../embed/sisi-embed.css", import.meta.url), "utf8");
const skillGraphCss = readFileSync(new URL("../shared/skill-dependency-graph.css", import.meta.url), "utf8");
const embedSource = readFileSync(new URL("../embed/SisiEmbedPage.tsx", import.meta.url), "utf8");
const design = JSON.parse(readFileSync(new URL("../../../DESIGN.json", import.meta.url), "utf8")) as {
  extensions: { componentRules: { iconControls: string[] } };
};

const bareIconSelector = [
  "cici-product-icon-button",
  "sisi-icon-button",
  "sisi-composer__tools > button",
  "skill-dag__icon-button",
  "skill-governance__icon-button",
  "system-api-icon-button",
  "integration-icon-btn",
  "model-row-icon-btn",
  "cici-builder-resource__icon-btn",
  "cici-composer__mic",
  "customer-workbench__composer-icon",
  "ai-table-list__help",
  "ai-table-list__tool-icon",
  "ai-table-list__objects-heading button",
  "ai-table-list__detail-head > button",
  "ai-table-list__action-cell button",
];

function opaqueBareIconBackgrounds(css: string) {
  return [...css.matchAll(/([^{}]+)\{([^{}]*)\}/g)]
    .filter(([, selectors]) => bareIconSelector.some((selector) => selectors.includes(selector)))
    .flatMap(([, selectors, body]) => {
      const backgrounds = [...body.matchAll(/\bbackground(?:-color)?\s*:\s*([^;]+);/g)]
        .map((match) => match[1].trim())
        .filter((value) => !/^(transparent|none)(\s*!important)?$/.test(value));
      return backgrounds.map((background) => `${selectors.trim()} => ${background}`);
    });
}

describe("bare icon button visual contract", () => {
  it("keeps the shared primitive transparent in every pointer and keyboard state", () => {
    expect(themeCss).toContain("Shared bare-icon invariant");
    expect(themeCss).toMatch(/\.cici-product-icon-button:hover:not\(:disabled\)[^{]*\{[^}]*background:\s*transparent\s*!important;/s);
  });

  it("routes the Sisi header and composer microphone through the shared primitive", () => {
    expect(embedSource.match(/cici-product-icon-button/g)?.length ?? 0).toBeGreaterThanOrEqual(4);
    expect(embedCss).toMatch(/\.sisi-composer__tools > button:hover:not\(:disabled\)\s*\{[^}]*background:\s*transparent;/s);
    expect(embedCss).toMatch(/\.sisi-composer__tools > button\.is-active\s*\{[^}]*background:\s*transparent;/s);
  });

  it("does not reintroduce opaque backgrounds on audited bare icon controls", () => {
    expect(opaqueBareIconBackgrounds(stylesCss)).toEqual([]);
    expect(opaqueBareIconBackgrounds(assistantCss)).toEqual([]);
    expect(opaqueBareIconBackgrounds(embedCss)).toEqual([]);
    expect(opaqueBareIconBackgrounds(skillGraphCss)).toEqual([]);
  });

  it("records the cross-page transparent interaction rule in the design source", () => {
    const rules = design.extensions.componentRules.iconControls.join(" ");
    expect(rules).toContain("stay transparent");
    expect(rules).toContain("icon color");
  });
});
