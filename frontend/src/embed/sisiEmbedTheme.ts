export type SisiEmbedMode = "float" | "page";

type ThemeSession = {
  source?: string;
  themeCode?: string;
} | null;

export function resolveSisiTheme(mode: SisiEmbedMode, session: ThemeSession) {
  if (session?.source === "website" || (!session && mode === "float")) return "crm-blue";
  return session?.themeCode?.trim() || "gilded";
}
