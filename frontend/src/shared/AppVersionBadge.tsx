import { appVersion, appVersionLabel, compactAppVersionLines } from "./appVersion";

type AppVersionBadgeProps = {
  compact?: boolean;
};

export default function AppVersionBadge({ compact = false }: AppVersionBadgeProps) {
  const label = appVersionLabel();
  const compactLines = compactAppVersionLines();

  return (
    <div className={`app-version-badge${compact ? " app-version-badge--compact" : ""}`} title={label} aria-label={label}>
      <span className="app-version-badge__dot" aria-hidden />
      {compact ? (
        <span className="app-version-badge__text app-version-badge__text--compact">
          <span>{compactLines.baseVersion}</span>
          {compactLines.qualifier ? <span>{compactLines.qualifier}</span> : null}
        </span>
      ) : <span className="app-version-badge__text">{label}</span>}
    </div>
  );
}
