import { Check } from "lucide-react";
import { useEffect, useState } from "react";
import {
  applyProductTheme,
  normalizeProductTheme,
  PRODUCT_THEME_EVENT,
  PRODUCT_THEMES,
  readStoredProductTheme,
  type ProductThemeCode,
} from "./theme";

type Props = {
  token: string;
  initialTheme?: string;
  endpoint?: string;
  compact?: boolean;
  onSaved?: (themeCode: ProductThemeCode) => void;
};

type ApiResponse = { success?: boolean; data?: { themeCode?: string }; message?: string };

export default function ThemePreferencePanel({
  token,
  initialTheme,
  endpoint = "/auth/me/theme",
  compact = false,
  onSaved,
}: Props) {
  const [selected, setSelected] = useState<ProductThemeCode>(() =>
    normalizeProductTheme(initialTheme ?? readStoredProductTheme()),
  );
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState("");

  useEffect(() => {
    if (!initialTheme) return;
    const next = applyProductTheme(initialTheme);
    setSelected(next);
  }, [initialTheme]);

  useEffect(() => {
    const onThemeChanged = (event: Event) => {
      const themeCode = (event as CustomEvent<{ themeCode?: string }>).detail?.themeCode;
      if (themeCode) setSelected(normalizeProductTheme(themeCode));
    };
    window.addEventListener(PRODUCT_THEME_EVENT, onThemeChanged);
    return () => window.removeEventListener(PRODUCT_THEME_EVENT, onThemeChanged);
  }, []);

  const selectTheme = async (themeCode: ProductThemeCode) => {
    if (saving) return;
    setSelected(themeCode);
    applyProductTheme(themeCode);
    setSaving(true);
    setNotice("正在同步主题偏好...");
    try {
      const response = await fetch(endpoint, {
        method: "PUT",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({ themeCode }),
      });
      const body = (await response.json().catch(() => ({}))) as ApiResponse;
      if (!response.ok || !body.success) {
        throw new Error(body.message || `HTTP ${response.status}`);
      }
      const saved = applyProductTheme(body.data?.themeCode ?? themeCode);
      setSelected(saved);
      setNotice("主题偏好已同步");
      onSaved?.(saved);
    } catch (error) {
      setNotice(`当前设备已应用，账号同步失败：${error instanceof Error ? error.message : "请稍后重试"}`);
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className={`theme-preference${compact ? " theme-preference--compact" : ""}`} aria-labelledby="theme-preference-title">
      {!compact ? (
        <header className="theme-preference__head">
          <div>
            <h4 id="theme-preference-title">界面主题</h4>
            <p>选择会立即预览，并同步到你的 AgentCiCi 与 CRM 嵌入工作台。</p>
          </div>
          <span className="theme-preference__status" role="status" aria-live="polite">{notice}</span>
        </header>
      ) : null}
      <div className="theme-preference__options" role="radiogroup" aria-label="界面主题">
        {PRODUCT_THEMES.map((theme) => {
          const active = selected === theme.code;
          return (
            <button
              key={theme.code}
              type="button"
              className={`theme-preference__option${active ? " is-active" : ""}`}
              role="radio"
              aria-checked={active}
              disabled={saving}
              onClick={() => void selectTheme(theme.code)}
            >
              <span className="theme-preference__swatches" aria-hidden>
                {theme.colors.map((color) => <i key={color} style={{ backgroundColor: color }} />)}
              </span>
              <span className="theme-preference__copy">
                <strong>{theme.name}</strong>
                {!compact ? <small>{theme.description}</small> : null}
              </span>
              <span className="theme-preference__check" aria-hidden>{active ? <Check size={15} /> : null}</span>
            </button>
          );
        })}
      </div>
      {compact ? <span className="theme-preference__status" role="status" aria-live="polite">{notice}</span> : null}
    </section>
  );
}
