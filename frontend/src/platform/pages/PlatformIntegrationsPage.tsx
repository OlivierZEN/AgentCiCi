import { LS_PLATFORM_TOKEN } from "../../constants";
import { IntegrationSettingsPage } from "../../admin/pages/AdminIntegrationsPage";

export const PLATFORM_INTEGRATIONS_API_BASE = "/api/platform/integrations";

function readToken(): string {
  const raw = localStorage.getItem(LS_PLATFORM_TOKEN);
  if (!raw) return "";
  try {
    return (JSON.parse(raw) as { token?: string }).token ?? "";
  } catch {
    return "";
  }
}

export default function PlatformIntegrationsPage() {
  const token = readToken();
  return (
    <IntegrationSettingsPage
      token={token}
      apiBase={PLATFORM_INTEGRATIONS_API_BASE}
      title="平台集成配置"
      subtitle="统一控制 Tavily 搜索、讯飞实时转写和受管代码解释器等平台能力。"
      className="platform-page platform-integrations-page"
    />
  );
}
