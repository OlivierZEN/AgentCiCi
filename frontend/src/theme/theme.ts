export const PRODUCT_THEME_CODES = [
  "gilded",
  "crm-blue",
  "ocean",
  "sakura",
  "lavender",
  "avocado",
  "wine",
  "galaxy",
] as const;

export type ProductThemeCode = (typeof PRODUCT_THEME_CODES)[number];

export type ProductThemeDefinition = {
  code: ProductThemeCode;
  name: string;
  description: string;
  colors: readonly [string, string, string];
  dark?: boolean;
};

export const DEFAULT_PRODUCT_THEME: ProductThemeCode = "gilded";
export const PRODUCT_THEME_STORAGE_KEY = "cici-product-theme";
export const PLATFORM_THEME_STORAGE_KEY = "cici-platform-theme";
export const PRODUCT_THEME_EVENT = "cici-product-theme-changed";

export const PRODUCT_THEMES: readonly ProductThemeDefinition[] = [
  {
    code: "gilded",
    name: "鎏金账房",
    description: "暖象牙、墨色与香槟金，默认企业工作台主题",
    colors: ["#fffdf8", "#2b2217", "#a67c2f"],
  },
  {
    code: "crm-blue",
    name: "CRM 标准蓝",
    description: "清爽白蓝与标准蓝，适合 CloudCC 融合场景",
    colors: ["#f7faff", "#17233d", "#1677d2"],
  },
  {
    code: "ocean",
    name: "蓝色海洋",
    description: "晴空蔚蓝与青蓝结构色，清晰而轻盈",
    colors: ["#f4fbff", "#163247", "#0788b5"],
  },
  {
    code: "sakura",
    name: "樱花粉语",
    description: "低饱和樱花粉与莓红强调，柔和但不甜腻",
    colors: ["#fff9fb", "#3c2830", "#b64e72"],
  },
  {
    code: "lavender",
    name: "熏衣紫语",
    description: "薰衣草灰与深紫强调，安静而有秩序",
    colors: ["#fbf9ff", "#2e2939", "#7255a6"],
  },
  {
    code: "avocado",
    name: "牛油果小调",
    description: "清浅绿灰与牛油果绿，自然且克制",
    colors: ["#f8fbf6", "#253126", "#5f7f42"],
  },
  {
    code: "wine",
    name: "红酒醇香",
    description: "浅玫瑰灰与酒红强调，稳重而温润",
    colors: ["#fff9f9", "#352629", "#8e3c4b"],
  },
  {
    code: "galaxy",
    name: "星河幻境",
    description: "深炭蓝、冷白与蓝紫强调，专注型深色主题",
    colors: ["#151922", "#f4f6fb", "#7f91ef"],
    dark: true,
  },
] as const;

export function normalizeProductTheme(value: unknown): ProductThemeCode {
  const normalized = typeof value === "string" ? value.trim().toLowerCase() : "";
  return PRODUCT_THEME_CODES.includes(normalized as ProductThemeCode)
    ? (normalized as ProductThemeCode)
    : DEFAULT_PRODUCT_THEME;
}

export function readStoredProductTheme(storageKey = PRODUCT_THEME_STORAGE_KEY): ProductThemeCode {
  try {
    return normalizeProductTheme(window.localStorage.getItem(storageKey));
  } catch {
    return DEFAULT_PRODUCT_THEME;
  }
}

export function applyProductTheme(
  value: unknown,
  options: { persist?: boolean; emit?: boolean; storageKey?: string } = {},
): ProductThemeCode {
  const themeCode = normalizeProductTheme(value);
  document.documentElement.dataset.theme = themeCode;
  document.documentElement.style.colorScheme = themeCode === "galaxy" ? "dark" : "light";
  if (options.persist !== false) {
    try {
      window.localStorage.setItem(options.storageKey ?? PRODUCT_THEME_STORAGE_KEY, themeCode);
    } catch {
      // Theme application remains available when storage is restricted.
    }
  }
  if (options.emit !== false) {
    window.dispatchEvent(new CustomEvent(PRODUCT_THEME_EVENT, { detail: { themeCode, storageKey: options.storageKey ?? PRODUCT_THEME_STORAGE_KEY } }));
  }
  return themeCode;
}

export function initializeProductTheme(storageKey = PRODUCT_THEME_STORAGE_KEY): ProductThemeCode {
  return applyProductTheme(readStoredProductTheme(storageKey), { persist: false, emit: false, storageKey });
}
