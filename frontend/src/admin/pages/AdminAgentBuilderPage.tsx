import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import AgentBuilderShell from "../../assistant/AgentBuilderShell";
import { LS_ADMIN_TOKEN } from "../../constants";
import { safeFetchJson } from "../../utils/http";
import { useAdminToken } from "../useAdminToken";

type KnowledgeBase = {
  id: number;
  name: string;
  description: string;
  status: string;
};

type AdminTokenPayload = { orgId?: string };

function readOrgIdFromAdminToken(): string {
  const raw = localStorage.getItem(LS_ADMIN_TOKEN);
  if (!raw) return "demo-org";
  try {
    const parsed = JSON.parse(raw) as AdminTokenPayload;
    return parsed.orgId?.trim() || "demo-org";
  } catch {
    return "demo-org";
  }
}

export default function AdminAgentBuilderPage() {
  const nav = useNavigate();
  const { agentId } = useParams<{ agentId?: string }>();
  const token = useAdminToken();
  const orgId = useMemo(() => readOrgIdFromAdminToken(), []);
  const [kbs, setKbs] = useState<KnowledgeBase[]>([]);

  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    const loadKbs = async () => {
      try {
        const response = await fetch("/kb", { headers: { Authorization: `Bearer ${token}` } });
        const { body } = await safeFetchJson<KnowledgeBase[]>(response);
        if (!response.ok || !body?.success) {
          return;
        }
        if (!cancelled) {
          setKbs((body.data ?? []) as KnowledgeBase[]);
        }
      } catch {
        if (!cancelled) {
          setKbs([]);
        }
      }
    };
    void loadKbs();
    return () => {
      cancelled = true;
    };
  }, [token]);

  return (
    <AgentBuilderShell
      kbs={kbs}
      orgId={orgId}
      token={token}
      pageMode={agentId ? "editor" : "list"}
      focusAgentId={agentId}
      onOpenAgent={(id) => nav(`/admin/agent-builder/${encodeURIComponent(id)}`)}
      onBackToList={() => nav("/admin/agent-builder")}
      onRequireModelConfig={(message) => nav("/admin/billing", { state: { notice: `${message} 请联系平台运营在模型厂商治理中启用可用模型。` } })}
    />
  );
}
