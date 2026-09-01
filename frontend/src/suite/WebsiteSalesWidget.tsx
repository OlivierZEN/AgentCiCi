import { useEffect } from "react";

type WidgetConfig = {
  widgetKey: string;
  assistantName: string;
  agentAvatarBase64?: string;
  launcherLabel: string;
  welcomeMessage: string;
  defaultOpen: boolean;
  sdkUrl: string;
};

type WidgetInstance = { destroy: () => void };
type WidgetSdk = {
  create: (options: Record<string, unknown>) => WidgetInstance;
};

declare global {
  interface Window {
    AgentCiCiSisi?: WidgetSdk;
  }
}

function visitorId(): string {
  const key = "agentcici-website-widget-visitor";
  try {
    const existing = window.localStorage.getItem(key);
    if (existing) return existing;
    const created = crypto.randomUUID();
    window.localStorage.setItem(key, created);
    return created;
  } catch {
    return crypto.randomUUID();
  }
}

function loadSdk(src: string): Promise<WidgetSdk> {
  if (window.AgentCiCiSisi) return Promise.resolve(window.AgentCiCiSisi);
  return new Promise((resolve, reject) => {
    const existing = document.querySelector<HTMLScriptElement>(`script[data-agentcici-widget-sdk="${src}"]`);
    const script = existing ?? document.createElement("script");
    const ready = () => window.AgentCiCiSisi ? resolve(window.AgentCiCiSisi) : reject(new Error("Web widget SDK is unavailable"));
    script.addEventListener("load", ready, { once: true });
    script.addEventListener("error", () => reject(new Error("Web widget SDK failed to load")), { once: true });
    if (!existing) {
      script.src = src;
      script.async = true;
      script.dataset.agentciciWidgetSdk = src;
      document.head.appendChild(script);
    }
  });
}

export default function WebsiteSalesWidget() {
  useEffect(() => {
    let cancelled = false;
    let instance: WidgetInstance | null = null;
    const mount = async () => {
      try {
        const response = await fetch("/public/website-widget", { headers: { Accept: "application/json" } });
        const body = await response.json().catch(() => null) as { success?: boolean; data?: WidgetConfig } | null;
        if (!response.ok || !body?.success || !body.data?.widgetKey || cancelled) return;
        const config = body.data;
        const sdk = await loadSdk(config.sdkUrl);
        if (cancelled) return;
        const id = visitorId();
        const visitId = crypto.randomUUID();
        instance = sdk.create({
          mode: "float",
          open: config.defaultOpen,
          assistantName: config.assistantName,
          launcherAvatar: config.agentAvatarBase64,
          launcherLabel: config.launcherLabel,
          tokenProvider: async () => {
            const tokenResponse = await fetch(`/public/web-widgets/${encodeURIComponent(config.widgetKey)}/tokens`, {
              method: "POST",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify({
                visitorId: id,
                visitId,
                parentOrigin: window.location.origin,
                pagePath: window.location.pathname,
                locale: document.documentElement.lang || "zh-CN",
              }),
            });
            const tokenBody = await tokenResponse.json().catch(() => null) as { success?: boolean; data?: { embedToken?: string }; message?: string } | null;
            if (!tokenResponse.ok || !tokenBody?.success || !tokenBody.data?.embedToken) {
              throw new Error(tokenBody?.message || "售前智能体暂时不可用");
            }
            return tokenBody.data.embedToken;
          },
        });
      } catch {
        // The public website remains fully usable when the optional widget is not configured.
      }
    };
    void mount();
    return () => {
      cancelled = true;
      instance?.destroy();
    };
  }, []);

  return null;
}
