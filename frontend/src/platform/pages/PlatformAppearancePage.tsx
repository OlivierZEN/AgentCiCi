import { Palette } from "lucide-react";
import { readAuthPayload, writeAuthPayload } from "../../auth/authStorage";
import { LS_PLATFORM_TOKEN } from "../../constants";
import ThemePreferencePanel from "../../theme/ThemePreferencePanel";
import { PLATFORM_THEME_STORAGE_KEY, type ProductThemeCode } from "../../theme/theme";

type PlatformAuth = { token?: string; themeCode?: string };

export default function PlatformAppearancePage() {
  const auth = readAuthPayload<PlatformAuth>(LS_PLATFORM_TOKEN);
  const token = auth?.token ?? "";

  const saveTheme = (themeCode: ProductThemeCode) => {
    if (!auth) return;
    writeAuthPayload(LS_PLATFORM_TOKEN, { ...auth, themeCode });
  };

  return (
    <section className="admin-page skills-catalog platform-page platform-appearance-page" aria-labelledby="platform-appearance-title">
      <header className="skills-catalog__header platform-page-head">
        <div className="platform-page-head__main">
          <p className="platform-section-label"><Palette size={14} aria-hidden /> 平台偏好</p>
          <h1 id="platform-appearance-title" className="skills-catalog__title">界面主题</h1>
          <p className="subtle skills-catalog__subtitle">主题只影响当前平台账号的运营工作区，不会改变用户端或 Admin 管理端的偏好。</p>
        </div>
      </header>
      <section className="platform-console__panel platform-appearance-page__panel">
        <ThemePreferencePanel
          token={token}
          endpoint="/auth/platform/me/theme"
          initialTheme={auth?.themeCode}
          storageKey={PLATFORM_THEME_STORAGE_KEY}
          onSaved={saveTheme}
        />
      </section>
    </section>
  );
}
