import { useEffect, useState, useRef, useCallback } from "react";
import { useAdminToken } from "../useAdminToken";

type KnowledgeBase = {
  id: number;
  name: string;
  description: string;
  status: string;
  documentCount?: number;
  createdAt?: string;
  updatedAt?: string;
};
type KbDocument = {
  id: number;
  name: string;
  contentType: string;
  status: string;
  createdAt: string;
  wordCount?: number;
  chunkCount?: number;
};

type ViewMode = "grid" | "detail";

const STATUS_LABEL: Record<string, string> = {
  UPLOADED: "待发布",
  INDEXING: "索引中",
  PUBLISHED: "可用",
  FAILED: "失败",
};

const FILE_ICON: Record<string, string> = {
  "text/markdown": "📝",
  "text/plain": "📄",
  "application/pdf": "📕",
  "text/csv": "📊",
  default: "📎",
};

function fileIcon(ct: string) {
  return FILE_ICON[ct] || FILE_ICON.default;
}

function formatDate(iso?: string) {
  if (!iso) return "-";
  const d = new Date(iso);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

export default function AdminKnowledgePage() {
  const token = useAdminToken();
  const [notice, setNotice] = useState("");
  const [kbs, setKbs] = useState<KnowledgeBase[]>([]);
  const [docs, setDocs] = useState<KbDocument[]>([]);
  const [selectedKb, setSelectedKb] = useState<KnowledgeBase | null>(null);
  const [viewMode, setViewMode] = useState<ViewMode>("grid");
  const [search, setSearch] = useState("");
  const [docSearch, setDocSearch] = useState("");
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [kbName, setKbName] = useState("");
  const [kbDescription, setKbDescription] = useState("");
  const [editingKbId, setEditingKbId] = useState<number | null>(null);
  const [detailTab, setDetailTab] = useState<"documents" | "settings">("documents");
  const fileInputRef = useRef<HTMLInputElement>(null);

  const flash = (msg: string) => {
    setNotice(msg);
    setTimeout(() => setNotice(""), 3000);
  };

  const headers = useCallback(
    () => ({ Authorization: `Bearer ${token}` }),
    [token],
  );

  const listKnowledgeBases = useCallback(async () => {
    const res = await fetch("/kb", { headers: headers() });
    const json = await res.json();
    setKbs((json.data ?? []) as KnowledgeBase[]);
  }, [headers]);

  const listDocuments = useCallback(
    async (kbId: number) => {
      const res = await fetch(`/kb/${kbId}/documents`, { headers: headers() });
      const json = await res.json();
      setDocs((json.data ?? []) as KbDocument[]);
    },
    [headers],
  );

  const createOrUpdateKb = async () => {
    if (!kbName.trim()) return;
    const isEdit = editingKbId !== null;
    const url = isEdit ? `/kb/${editingKbId}` : "/kb";
    const method = isEdit ? "PUT" : "POST";
    const res = await fetch(url, {
      method,
      headers: { "Content-Type": "application/json", ...headers() },
      body: JSON.stringify({ name: kbName, description: kbDescription }),
    });
    const json = await res.json();
    if (json.success) {
      flash(isEdit ? "知识库已更新" : "知识库创建成功");
      setShowCreateModal(false);
      setKbName("");
      setKbDescription("");
      setEditingKbId(null);
      await listKnowledgeBases();
    } else {
      flash(`操作失败：${json.message}`);
    }
  };

  const deleteKnowledgeBase = async (id: number) => {
    if (!window.confirm("确认删除该知识库？所有文档将一并删除。")) return;
    const res = await fetch(`/kb/${id}`, { method: "DELETE", headers: headers() });
    const json = await res.json();
    if (json.success) {
      flash("知识库已删除");
      if (selectedKb?.id === id) {
        setSelectedKb(null);
        setViewMode("grid");
        setDocs([]);
      }
      await listKnowledgeBases();
    } else {
      flash(`删除失败：${json.message}`);
    }
  };

  const uploadDocument = async (file: File) => {
    if (!selectedKb) return;
    const form = new FormData();
    form.append("knowledgeBaseId", String(selectedKb.id));
    form.append("file", file);
    const res = await fetch("/kb/documents/upload", {
      method: "POST",
      headers: headers(),
      body: form,
    });
    const json = await res.json();
    if (!json.success) {
      flash(`上传失败：${json.message}`);
      return;
    }
    flash("文档上传成功");
    await listDocuments(selectedKb.id);
    const docId = json.data?.id;
    if (docId) {
      await publishDocument(docId);
    }
  };

  const publishDocument = async (id: number) => {
    if (!selectedKb) return;
    const res = await fetch(`/kb/documents/${id}/publish`, {
      method: "POST",
      headers: headers(),
    });
    const json = await res.json();
    if (json.success) {
      flash("文档已提交索引");
      await listDocuments(selectedKb.id);
    } else {
      flash(`发布失败：${json.message}`);
    }
  };

  const deleteDocument = async (id: number) => {
    if (!selectedKb) return;
    if (!window.confirm("确认删除该文档？")) return;
    const res = await fetch(`/kb/documents/${id}`, {
      method: "DELETE",
      headers: headers(),
    });
    const json = await res.json();
    if (json.success) {
      flash("文档已删除");
      await listDocuments(selectedKb.id);
    } else {
      flash(`删除失败：${json.message}`);
    }
  };

  const toggleDocStatus = async (doc: KbDocument) => {
    if (doc.status === "PUBLISHED") {
      // no unpublish endpoint yet — just flash
      flash("暂不支持停用已发布文档");
    } else if (doc.status === "UPLOADED" || doc.status === "FAILED") {
      await publishDocument(doc.id);
    }
  };

  const openKbDetail = (kb: KnowledgeBase) => {
    setSelectedKb(kb);
    setViewMode("detail");
    setDetailTab("documents");
    setDocSearch("");
    void listDocuments(kb.id);
  };

  useEffect(() => {
    void listKnowledgeBases();
  }, [listKnowledgeBases]);

  useEffect(() => {
    if (!selectedKb) return;
    const hasIndexing = docs.some((d) => d.status === "INDEXING");
    if (!hasIndexing) return;
    const t = window.setInterval(() => void listDocuments(selectedKb.id), 2000);
    return () => window.clearInterval(t);
  }, [selectedKb, docs, listDocuments]);

  const filteredKbs = kbs.filter(
    (kb) =>
      kb.name.toLowerCase().includes(search.toLowerCase()) ||
      (kb.description ?? "").toLowerCase().includes(search.toLowerCase()),
  );

  const filteredDocs = docs.filter((d) =>
    d.name.toLowerCase().includes(docSearch.toLowerCase()),
  );

  /* ─── KB Grid View ─── */
  if (viewMode === "grid") {
    return (
      <div className="dify-kb-page">
        {notice && <div className="dify-toast">{notice}</div>}

        {/* top bar */}
        <div className="dify-kb-topbar">
          <div className="dify-kb-topbar__left">
            <h1 className="dify-kb-topbar__title">知识库</h1>
          </div>
          <div className="dify-kb-topbar__right">
            <div className="dify-search">
              <svg className="dify-search__icon" viewBox="0 0 20 20" fill="none">
                <circle cx="9" cy="9" r="6" stroke="currentColor" strokeWidth="1.6" />
                <path d="M13.5 13.5 17 17" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
              </svg>
              <input
                className="dify-search__input"
                placeholder="搜索知识库..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </div>
          </div>
        </div>

        {/* card grid */}
        <div className="dify-kb-grid">
          {/* create card */}
          <button
            type="button"
            className="dify-kb-card dify-kb-card--create"
            onClick={() => {
              setEditingKbId(null);
              setKbName("");
              setKbDescription("");
              setShowCreateModal(true);
            }}
          >
            <span className="dify-kb-card__plus">+</span>
            <span className="dify-kb-card__create-label">创建知识库</span>
          </button>

          {filteredKbs.map((kb) => (
            <div
              key={kb.id}
              className="dify-kb-card"
              onClick={() => openKbDetail(kb)}
            >
              <div className="dify-kb-card__header">
                <div className="dify-kb-card__icon">
                  <svg viewBox="0 0 24 24" fill="none" width="28" height="28">
                    <rect x="3" y="3" width="18" height="18" rx="4" stroke="currentColor" strokeWidth="1.5" />
                    <path d="M7 8h10M7 12h6M7 16h8" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
                  </svg>
                </div>
                <button
                  type="button"
                  className="dify-kb-card__menu"
                  onClick={(e) => {
                    e.stopPropagation();
                    if (window.confirm(`删除知识库「${kb.name}」？`)) {
                      void deleteKnowledgeBase(kb.id);
                    }
                  }}
                  title="删除"
                >
                  ···
                </button>
              </div>
              <h3 className="dify-kb-card__name">{kb.name}</h3>
              <p className="dify-kb-card__desc">
                {kb.description || "暂无描述"}
              </p>
              <div className="dify-kb-card__meta">
                <span className="dify-kb-card__tag">通用</span>
                <span className="dify-kb-card__tag">向量检索</span>
              </div>
              <div className="dify-kb-card__footer">
                <span className="dify-kb-card__stat">
                  <svg viewBox="0 0 16 16" width="14" height="14" fill="none">
                    <path d="M3 3h10v10H3z" stroke="currentColor" strokeWidth="1.2" rx="1.5" />
                    <path d="M5.5 6.5h5M5.5 9h3" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" />
                  </svg>
                  {kb.documentCount ?? docs.length ?? 0}
                </span>
                <span className="dify-kb-card__time">
                  {kb.updatedAt ? formatDate(kb.updatedAt) : kb.createdAt ? formatDate(kb.createdAt) : ""}
                </span>
              </div>
            </div>
          ))}
        </div>

        {/* create/edit modal */}
        {showCreateModal && (
          <div className="dify-modal-overlay" onClick={() => setShowCreateModal(false)}>
            <div className="dify-modal" onClick={(e) => e.stopPropagation()}>
              <h2 className="dify-modal__title">
                {editingKbId ? "编辑知识库" : "创建知识库"}
              </h2>
              <div className="dify-modal__body">
              <label className="dify-field">
                <span className="dify-field__label">知识库名称</span>
                <input
                  className="dify-field__input"
                  value={kbName}
                  onChange={(e) => setKbName(e.target.value)}
                  placeholder="输入知识库名称"
                  autoFocus
                />
              </label>
              <label className="dify-field">
                <span className="dify-field__label">描述</span>
                <textarea
                  className="dify-field__textarea"
                  value={kbDescription}
                  onChange={(e) => setKbDescription(e.target.value)}
                  placeholder="描述知识库的用途和内容范围"
                  rows={3}
                />
              </label>
              </div>
              <div className="dify-modal__actions">
                <button
                  type="button"
                  className="dify-btn dify-btn--ghost"
                  onClick={() => setShowCreateModal(false)}
                >
                  取消
                </button>
                <button
                  type="button"
                  className="dify-btn dify-btn--primary"
                  onClick={createOrUpdateKb}
                >
                  {editingKbId ? "保存" : "创建"}
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    );
  }

  /* ─── KB Detail View ─── */
  return (
    <div className="dify-kb-page">
      {notice && <div className="dify-toast">{notice}</div>}

      <div className="dify-kb-detail">
        {/* sidebar */}
        <aside className="dify-kb-sidebar">
          <div className="dify-kb-sidebar__head">
            <div className="dify-kb-sidebar__icon">
              <svg viewBox="0 0 24 24" fill="none" width="32" height="32">
                <rect x="3" y="3" width="18" height="18" rx="4" stroke="currentColor" strokeWidth="1.5" />
                <path d="M7 8h10M7 12h6M7 16h8" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
              </svg>
            </div>
            <h2 className="dify-kb-sidebar__name">{selectedKb?.name}</h2>
            <p className="dify-kb-sidebar__desc">
              {selectedKb?.description || "暂无描述"}
            </p>
          </div>
          <nav className="dify-kb-sidebar__nav">
            <button
              type="button"
              className={`dify-kb-sidebar__link ${detailTab === "documents" ? "active" : ""}`}
              onClick={() => setDetailTab("documents")}
            >
              <svg viewBox="0 0 20 20" width="18" height="18" fill="none">
                <rect x="3" y="2" width="14" height="16" rx="2" stroke="currentColor" strokeWidth="1.4" />
                <path d="M6 6h8M6 9.5h5M6 13h6" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" />
              </svg>
              文档
            </button>
            <button
              type="button"
              className={`dify-kb-sidebar__link ${detailTab === "settings" ? "active" : ""}`}
              onClick={() => setDetailTab("settings")}
            >
              <svg viewBox="0 0 20 20" width="18" height="18" fill="none">
                <circle cx="10" cy="10" r="2.5" stroke="currentColor" strokeWidth="1.4" />
                <path d="M10 2v2.5M10 15.5V18M2 10h2.5M15.5 10H18M4.2 4.2l1.8 1.8M14 14l1.8 1.8M15.8 4.2 14 6M6 14l-1.8 1.8" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
              </svg>
              设置
            </button>
          </nav>
          <div className="dify-kb-sidebar__footer">
            <button
              type="button"
              className="dify-btn dify-btn--ghost dify-btn--sm dify-btn--full"
              onClick={() => {
                setViewMode("grid");
                setSelectedKb(null);
                setDocs([]);
              }}
            >
              ← 返回知识库列表
            </button>
          </div>
        </aside>

        {/* main */}
        <main className="dify-kb-main">
          {detailTab === "documents" && (
            <>
              <div className="dify-kb-main__header">
                <div>
                  <h2 className="dify-kb-main__title">文档</h2>
                  <p className="dify-kb-main__subtitle">
                    知识库的所有文件都在这里显示，上传后自动发布索引。
                  </p>
                </div>
                <div className="dify-kb-main__actions">
                  <div className="dify-search dify-search--sm">
                    <svg className="dify-search__icon" viewBox="0 0 20 20" fill="none">
                      <circle cx="9" cy="9" r="6" stroke="currentColor" strokeWidth="1.6" />
                      <path d="M13.5 13.5 17 17" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
                    </svg>
                    <input
                      className="dify-search__input"
                      placeholder="搜索文档..."
                      value={docSearch}
                      onChange={(e) => setDocSearch(e.target.value)}
                    />
                  </div>
                  <input
                    ref={fileInputRef}
                    type="file"
                    hidden
                    onChange={(e) => {
                      const f = e.target.files?.[0];
                      if (f) void uploadDocument(f);
                      e.target.value = "";
                    }}
                  />
                  <button
                    type="button"
                    className="dify-btn dify-btn--primary"
                    onClick={() => fileInputRef.current?.click()}
                  >
                    + 添加文件
                  </button>
                </div>
              </div>

              {/* document table */}
              <div className="dify-doc-table-wrap">
                <table className="dify-doc-table">
                  <thead>
                    <tr>
                      <th className="dify-doc-table__th--num">#</th>
                      <th>名称</th>
                      <th>类型</th>
                      <th>上传时间</th>
                      <th>状态</th>
                      <th className="dify-doc-table__th--actions">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredDocs.length === 0 && (
                      <tr>
                        <td colSpan={6} className="dify-doc-table__empty">
                          暂无文档，点击「+ 添加文件」上传
                        </td>
                      </tr>
                    )}
                    {filteredDocs.map((doc, i) => (
                      <tr key={doc.id}>
                        <td className="dify-doc-table__num">{i + 1}</td>
                        <td>
                          <div className="dify-doc-name">
                            <span className="dify-doc-name__icon">{fileIcon(doc.contentType)}</span>
                            <span className="dify-doc-name__text">{doc.name}</span>
                          </div>
                        </td>
                        <td className="dify-doc-table__type">{doc.contentType?.split("/")[1] ?? "-"}</td>
                        <td className="dify-doc-table__time">{formatDate(doc.createdAt)}</td>
                        <td>
                          <div className="dify-doc-status">
                            <button
                              type="button"
                              className={`dify-toggle ${doc.status === "PUBLISHED" ? "dify-toggle--on" : ""} ${doc.status === "INDEXING" ? "dify-toggle--loading" : ""}`}
                              onClick={() => void toggleDocStatus(doc)}
                              disabled={doc.status === "INDEXING"}
                            />
                            <span
                              className={`dify-doc-badge dify-doc-badge--${doc.status.toLowerCase()}`}
                            >
                              {doc.status === "INDEXING" && (
                                <span className="dify-spinner" />
                              )}
                              {STATUS_LABEL[doc.status] ?? doc.status}
                            </span>
                          </div>
                        </td>
                        <td className="dify-doc-table__actions">
                          {(doc.status === "UPLOADED" || doc.status === "FAILED") && (
                            <button
                              type="button"
                              className="dify-btn dify-btn--text dify-btn--xs"
                              onClick={() => void publishDocument(doc.id)}
                            >
                              发布
                            </button>
                          )}
                          <button
                            type="button"
                            className="dify-btn dify-btn--text dify-btn--xs dify-btn--danger"
                            onClick={() => void deleteDocument(doc.id)}
                          >
                            删除
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <div className="dify-kb-main__footer">
                <span>{filteredDocs.length} 文档</span>
              </div>
            </>
          )}

          {detailTab === "settings" && selectedKb && (
            <div className="dify-kb-settings">
              <h2 className="dify-kb-main__title">知识库设置</h2>
              <label className="dify-field">
                <span className="dify-field__label">名称</span>
                <input
                  className="dify-field__input"
                  defaultValue={selectedKb.name}
                  onBlur={(e) => setKbName(e.target.value)}
                  onFocus={(e) => {
                    setKbName(e.target.value);
                    setEditingKbId(selectedKb.id);
                  }}
                />
              </label>
              <label className="dify-field">
                <span className="dify-field__label">描述</span>
                <textarea
                  className="dify-field__textarea"
                  defaultValue={selectedKb.description}
                  rows={3}
                  onBlur={(e) => setKbDescription(e.target.value)}
                  onFocus={(e) => {
                    setKbDescription(e.target.value);
                    setEditingKbId(selectedKb.id);
                  }}
                />
              </label>
              <div className="dify-kb-settings__actions">
                <button
                  type="button"
                  className="dify-btn dify-btn--primary"
                  onClick={async () => {
                    setEditingKbId(selectedKb.id);
                    await createOrUpdateKb();
                    await listKnowledgeBases();
                    const updated = kbs.find((k) => k.id === selectedKb.id);
                    if (updated) setSelectedKb({ ...updated, name: kbName || updated.name, description: kbDescription || updated.description });
                  }}
                >
                  保存设置
                </button>
                <button
                  type="button"
                  className="dify-btn dify-btn--danger"
                  onClick={() => void deleteKnowledgeBase(selectedKb.id)}
                >
                  删除知识库
                </button>
              </div>
            </div>
          )}
        </main>
      </div>
    </div>
  );
}
