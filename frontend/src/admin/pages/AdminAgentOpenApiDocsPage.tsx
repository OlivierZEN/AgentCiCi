import { useEffect, useMemo, useState } from "react";
import { useParams } from "react-router-dom";
import AgentOpenApiDocsDialog from "../../assistant/AgentOpenApiDocsDialog";
import AgentOpenApiKeysDialog from "../../assistant/AgentOpenApiKeysDialog";
import { safeFetchJson } from "../../utils/http";
import { useAdminToken } from "../useAdminToken";

type AgentOpenApiSummary = {
  agentId: string;
  name: string;
  publishedVersionId?: number | null;
  channels?: string[];
};

export default function AdminAgentOpenApiDocsPage() {
  const { agentId } = useParams<{ agentId?: string }>();
  const token = useAdminToken();
  const [agent, setAgent] = useState<AgentOpenApiSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [keysOpen, setKeysOpen] = useState(false);
  const openApiBaseUrl = useMemo(() => `${window.location.origin}/openapi/v1`, []);

  useEffect(() => {
    if (!token || !agentId) {
      setLoading(false);
      setError("缺少 Agent ID，无法打开 API 文档。");
      return;
    }
    let cancelled = false;
    const loadAgent = async () => {
      setLoading(true);
      setError("");
      try {
        const response = await fetch(`/agents/${encodeURIComponent(agentId)}`, {
          headers: { Authorization: `Bearer ${token}` },
        });
        const { body } = await safeFetchJson<AgentOpenApiSummary>(response);
        if (!response.ok || !body?.success || !body.data) {
          throw new Error(body?.message ?? `HTTP ${response.status}`);
        }
        if (!cancelled) {
          setAgent(body.data);
        }
      } catch (err) {
        if (!cancelled) {
          setAgent(null);
          setError(err instanceof Error ? err.message : "API 文档加载失败。");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };
    void loadAgent();
    return () => {
      cancelled = true;
    };
  }, [agentId, token]);

  if (loading || error) {
    return (
      <section className="cici-openapi-docs-page cici-openapi-docs-page--state" aria-live="polite">
        <div className="cici-openapi-docs-page__state">
          <strong>{loading ? "正在加载 API 文档" : "API 文档无法打开"}</strong>
          <span>{loading ? "请稍候，正在读取 Agent 发布与开放状态。" : error}</span>
        </div>
      </section>
    );
  }

  const resolvedAgentId = agent?.agentId || agentId || "";

  return (
    <section className="cici-openapi-docs-page" aria-label="Agent API 文档">
      <AgentOpenApiDocsDialog
        open
        displayMode="page"
        agentId={resolvedAgentId}
        agentName={agent?.name ?? "未命名 Agent"}
        published={Boolean(agent?.publishedVersionId)}
        apiChannelEnabled={(agent?.channels ?? []).includes("api")}
        baseUrl={openApiBaseUrl}
        keyManagementAvailable={Boolean(resolvedAgentId && token)}
        onOpenKeyManagement={() => setKeysOpen(true)}
      />
      <AgentOpenApiKeysDialog
        open={keysOpen}
        agentId={resolvedAgentId}
        agentName={agent?.name ?? "未命名 Agent"}
        token={token}
        onClose={() => setKeysOpen(false)}
      />
    </section>
  );
}
