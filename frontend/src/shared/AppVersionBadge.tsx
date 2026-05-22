import { appVersion, appVersionLabel } from "./appVersion";

type AppVersionBadgeProps = {
  compact?: boolean;
};

export default function AppVersionBadge({ compact = false }: AppVersionBadgeProps) {
  const label = appVersionLabel();

  return (
    <div className={`app-version-badge${compact ? " app-version-badge--compact" : ""}`} title={label} aria-label={label}>
      <span className="app-version-badge__dot" aria-hidden />
      <span className="app-version-badge__text">{compact ? appVersion : label}</span>
    </div>
  );
}
