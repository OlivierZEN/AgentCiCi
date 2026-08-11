const rawAppVersion = import.meta.env.VITE_CICI_APP_VERSION || import.meta.env.VITE_APP_VERSION || "dev";

export const appVersion = rawAppVersion.trim() || "dev";

export function appVersionLabel() {
  return `版本 ${appVersion}`;
}

export function compactAppVersionLines(version = appVersion) {
  const [baseVersion, ...qualifier] = version.split("-");
  return {
    baseVersion,
    qualifier: qualifier.join("-"),
  };
}
