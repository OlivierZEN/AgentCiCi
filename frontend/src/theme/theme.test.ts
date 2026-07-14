import { describe, expect, it } from "vitest";
import {
  DEFAULT_PRODUCT_THEME,
  normalizeProductTheme,
  PRODUCT_THEME_CODES,
  PRODUCT_THEMES,
} from "./theme";

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
});
