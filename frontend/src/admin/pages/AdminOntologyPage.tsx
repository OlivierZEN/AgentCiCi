import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useAdminToken } from "../useAdminToken";
import {
  createOntologyApi,
  isOntologyRevisionConflict,
  OntologyApiError,
} from "../ontology/ontologyApi";
import type { OntologyDraftView, OntologyWorkspaceView } from "../ontology/ontologyTypes";

type LoadStatus = "idle" | "loading" | "ready" | "error";

function workspaceStatusLabel(status: OntologyWorkspaceView["status"]): string {
  if (status === "PUBLISHED") return "已发布";
  if (status === "ARCHIVED") return "已归档";
  return "草稿";
}

function formatDateTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value || "-";
  return new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(date);
}

export function formatOntologyError(error: unknown): string {
  if (isOntologyRevisionConflict(error)) return "草稿已被更新，请重新加载";
  if (error instanceof OntologyApiError) {
    return `${error.code}：${error.message}`;
  }
  return "业务本体服务暂时不可用，请稍后重试。";
}

export default function AdminOntologyPage() {
  const token = useAdminToken();
  const api = useMemo(() => createOntologyApi(token), [token]);
  const listRequestId = useRef(0);
  const draftRequestId = useRef(0);
  const [workspaces, setWorkspaces] = useState<OntologyWorkspaceView[]>([]);
  const [listStatus, setListStatus] = useState<LoadStatus>("idle");
  const [listError, setListError] = useState("");
  const [selectedWorkspace, setSelectedWorkspace] = useState<OntologyWorkspaceView | null>(null);
  const [draft, setDraft] = useState<OntologyDraftView | null>(null);
  const [draftStatus, setDraftStatus] = useState<LoadStatus>("idle");
  const [draftError, setDraftError] = useState("");

  const loadWorkspaces = useCallback(async () => {
    const requestId = ++listRequestId.current;
    setListStatus("loading");
    setListError("");
    try {
      const next = await api.listWorkspaces();
      if (requestId !== listRequestId.current) return;
      setWorkspaces(next);
      setListStatus("ready");
    } catch (error) {
      if (requestId !== listRequestId.current) return;
      setListError(formatOntologyError(error));
      setListStatus("error");
    }
  }, [api]);

  const openWorkspace = useCallback(async (workspace: OntologyWorkspaceView) => {
    const requestId = ++draftRequestId.current;
    setSelectedWorkspace(workspace);
    setDraft(null);
    setDraftStatus("loading");
    setDraftError("");
    try {
      const next = await api.getDraft(workspace.id);
      if (requestId !== draftRequestId.current) return;
      setDraft(next);
      setSelectedWorkspace(next.workspace);
      setDraftStatus("ready");
    } catch (error) {
      if (requestId !== draftRequestId.current) return;
      setDraftError(formatOntologyError(error));
      setDraftStatus("error");
    }
  }, [api]);

  useEffect(() => {
    void loadWorkspaces();
  }, [loadWorkspaces]);

  const closeWorkspace = () => {
    draftRequestId.current += 1;
    setSelectedWorkspace(null);
    setDraft(null);
    setDraftStatus("idle");
    setDraftError("");
  };

  if (selectedWorkspace) {
    return (
      <div className="admin-page" aria-busy={draftStatus === "loading"}>
        <header className="chat-header">
          <h1>{selectedWorkspace.name}</h1>
          <p className="subtle">
            业务本体工作区 · 草稿修订 {draft?.draftRevision ?? selectedWorkspace.draftRevision}
            {selectedWorkspace.publishedVersion == null ? " · 尚未发布" : ` · 线上版本 ${selectedWorkspace.publishedVersion}`}
          </p>
        </header>

        <div className="row">
          <button type="button" className="cici-btn cici-btn--ghost" onClick={closeWorkspace}>
            返回本体列表
          </button>
          <button
            type="button"
            className="cici-btn cici-btn--ghost"
            disabled={draftStatus === "loading"}
            onClick={() => void openWorkspace(selectedWorkspace)}
          >
            重新载入
          </button>
        </div>

        {draftStatus === "loading" && <p className="subtle" role="status">正在载入业务对象与数据来源...</p>}
        {draftStatus === "error" && (
          <div role="alert">
            <p>{draftError}</p>
            <button type="button" className="cici-btn cici-btn--ghost" onClick={() => void openWorkspace(selectedWorkspace)}>
              重试
            </button>
          </div>
        )}
        {draftStatus === "ready" && draft && (
          <section className="list-box" aria-labelledby="ontology-workspace-summary">
            <h2 id="ontology-workspace-summary">业务对象概览</h2>
            <p className="subtle">
              {draft.document.concepts.length} 个业务对象，{draft.document.relations.length} 条关系，
              {draft.document.metrics.length} 个业务指标，{draft.sources.length} 个数据来源。
            </p>
            {draft.document.concepts.length === 0 ? (
              <p className="subtle">草稿中还没有业务对象，可以在建模工作台中从领域描述开始补充。</p>
            ) : (
              <div className="cici-doc-table-wrap">
                <table className="cici-doc-table" aria-label="业务对象概览">
                  <thead>
                    <tr>
                      <th>业务对象</th>
                      <th>类型</th>
                      <th>业务属性</th>
                      <th>查询状态</th>
                    </tr>
                  </thead>
                  <tbody>
                    {draft.document.concepts.map((concept) => (
                      <tr key={concept.key}>
                        <td>{concept.name}</td>
                        <td>{concept.conceptType === "EVENT" ? "业务事件" : "业务实体"}</td>
                        <td>{concept.properties.length}</td>
                        <td>{concept.enabled && concept.queryable ? "可查询" : "未开放"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        )}
      </div>
    );
  }

  return (
    <div className="admin-page" aria-busy={listStatus === "loading"}>
      <header className="chat-header">
        <h1>业务本体</h1>
        <p className="subtle">用统一业务语言组织对象、关系、指标与数据来源。</p>
      </header>

      <div className="row">
        <button
          type="button"
          className="cici-btn cici-btn--ghost"
          disabled={listStatus === "loading"}
          onClick={() => void loadWorkspaces()}
        >
          刷新列表
        </button>
      </div>

      {listStatus === "loading" && <p className="subtle" role="status">正在加载业务本体...</p>}
      {listStatus === "error" && (
        <div role="alert">
          <p>{listError}</p>
          <button type="button" className="cici-btn cici-btn--ghost" onClick={() => void loadWorkspaces()}>
            重试
          </button>
        </div>
      )}
      {listStatus === "ready" && workspaces.length === 0 && (
        <div className="list-box" role="status">
          <h2>还没有业务本体</h2>
          <p className="subtle">从业务领域描述或已接入的数据来源开始，AI 会生成可审阅的建模草稿。</p>
        </div>
      )}
      {listStatus === "ready" && workspaces.length > 0 && (
        <div className="cici-doc-table-wrap">
          <table className="cici-doc-table" aria-label="业务本体列表">
            <thead>
              <tr>
                <th>名称</th>
                <th>状态</th>
                <th>草稿修订</th>
                <th>线上版本</th>
                <th>最近更新</th>
                <th className="cici-doc-table__th--actions">操作</th>
              </tr>
            </thead>
            <tbody>
              {workspaces.map((workspace) => (
                <tr key={workspace.id}>
                  <td>
                    <strong>{workspace.name}</strong>
                    {workspace.description && <div className="subtle">{workspace.description}</div>}
                  </td>
                  <td>{workspaceStatusLabel(workspace.status)}</td>
                  <td>{workspace.draftRevision}</td>
                  <td>{workspace.publishedVersion ?? "-"}</td>
                  <td className="cici-doc-table__time">{formatDateTime(workspace.updatedAt)}</td>
                  <td className="cici-doc-table__actions">
                    <button
                      type="button"
                      className="cici-btn cici-btn--ghost"
                      onClick={() => void openWorkspace(workspace)}
                      aria-label={`进入${workspace.name}工作台`}
                    >
                      进入工作台
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
