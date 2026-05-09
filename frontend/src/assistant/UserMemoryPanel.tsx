import { useCallback, useEffect, useState } from "react";

type MemoryItem = {
  id: number;
  agentId: string;
  category: "FACT" | "PREFERENCE" | "CONTEXT" | "INSTRUCTION";
  source: "MANUAL" | "EXTRACTED";
  content: string;
  memoryKey?: string | null;
  confidence: number;
  enabled: boolean;
  pinned: boolean;
  createdAt: string;
  updatedAt: string;
};

type ApiEnvelope<T> = {
  success?: boolean;
  data?: T;
  message?: string;
};

async function apiFetch<T>(url: string, token: string, init?: RequestInit): Promise<ApiEnvelope<T>> {
  const res = await fetch(url, {
    ...init,
    headers: {
      Authorization: `Bearer ${token}`,
      ...(init?.body ? { "Content-Type": "application/json" } : {}),
      ...(init?.headers as Record<string, string> | undefined),
    },
  });
  try {
    return (await res.json()) as ApiEnvelope<T>;
  } catch {
    return { success: res.ok, message: `HTTP ${res.status}` };
  }
}

type Props = {
  token: string;
  agentId?: string;
};

const CATEGORY_META: Record<
  MemoryItem["category"],
  { label: string; color: string }
> = {
  INSTRUCTION: { label: "行为指令", color: "#b42318" },
  FACT:        { label: "用户事实", color: "#876223" },
  PREFERENCE:  { label: "个人偏好", color: "#a67c2f" },
  CONTEXT:     { label: "工作上下文", color: "#166534" },
};

const CATEGORY_ORDER: MemoryItem["category"][] = ["INSTRUCTION", "FACT", "PREFERENCE", "CONTEXT"];

const SOURCE_LABELS: Record<MemoryItem["source"], string> = {
  MANUAL:    "手动添加",
  EXTRACTED: "AI 提取",
};

const EMPTY_FORM = {
  category: "FACT" as MemoryItem["category"],
  content: "",
  memoryKey: "",
  enabled: true,
  pinned: false,
};

export default function UserMemoryPanel({ token, agentId = "cici-system" }: Props) {
  const [memories, setMemories] = useState<MemoryItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [filterCategory, setFilterCategory] = useState<MemoryItem["category"] | "ALL">("ALL");
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState({ ...EMPTY_FORM });
  const [saving, setSaving] = useState(false);
  const [confirmClearAll, setConfirmClearAll] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const apiBase = `/me/agents/${agentId}/memories`;

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiFetch<MemoryItem[]>(apiBase, token);
      if (res?.success && Array.isArray(res.data)) {
        setMemories(res.data);
      }
    } catch {
      setError("加载记忆列表失败，请稍后重试");
    } finally {
      setLoading(false);
    }
  }, [apiBase, token]);

  useEffect(() => {
    load();
  }, [load]);

  const openCreate = () => {
    setEditingId(null);
    setForm({ ...EMPTY_FORM });
    setShowForm(true);
  };

  const openEdit = (item: MemoryItem) => {
    setEditingId(item.id);
    setForm({
      category: item.category,
      content: item.content,
      memoryKey: item.memoryKey ?? "",
      enabled: item.enabled,
      pinned: item.pinned,
    });
    setShowForm(true);
  };

  const closeForm = () => {
    setShowForm(false);
    setEditingId(null);
    setForm({ ...EMPTY_FORM });
  };

  const handleSave = async () => {
    if (!form.content.trim()) return;
    setSaving(true);
    setError(null);
    try {
      const body = {
        category: form.category,
        content: form.content.trim(),
        memoryKey: form.memoryKey.trim() || null,
        enabled: form.enabled,
        pinned: form.pinned,
      };
      const url = editingId !== null ? `${apiBase}/${editingId}` : apiBase;
      const method = editingId !== null ? "PUT" : "POST";
      const res = await apiFetch<MemoryItem>(url, token, {
        method,
        body: JSON.stringify(body),
      });
      if (res?.success) {
        closeForm();
        await load();
      } else {
        setError(res?.message ?? "保存失败");
      }
    } catch {
      setError("保存失败，请稍后重试");
    } finally {
      setSaving(false);
    }
  };

  const handleToggleEnabled = async (item: MemoryItem) => {
    try {
      await apiFetch<MemoryItem>(`${apiBase}/${item.id}`, token, {
        method: "PUT",
        body: JSON.stringify({
          category: item.category,
          content: item.content,
          memoryKey: item.memoryKey,
          enabled: !item.enabled,
          pinned: item.pinned,
        }),
      });
      setMemories((prev) =>
        prev.map((m) => (m.id === item.id ? { ...m, enabled: !m.enabled } : m))
      );
    } catch {
      setError("操作失败");
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await apiFetch<null>(`${apiBase}/${id}`, token, { method: "DELETE" });
      setMemories((prev) => prev.filter((m) => m.id !== id));
    } catch {
      setError("删除失败");
    }
  };

  const handleClearAll = async () => {
    try {
      await apiFetch<null>(apiBase, token, { method: "DELETE" });
      setMemories([]);
      setConfirmClearAll(false);
    } catch {
      setError("清空失败");
    }
  };

  const filtered =
    filterCategory === "ALL"
      ? memories
      : memories.filter((m) => m.category === filterCategory);

  const grouped = CATEGORY_ORDER.reduce<Record<string, MemoryItem[]>>((acc, cat) => {
    const items = filtered.filter((m) => m.category === cat);
    if (items.length > 0) acc[cat] = items;
    return acc;
  }, {});

  const enabledCount = memories.filter((m) => m.enabled).length;

  return (
    <div className="memory-panel">
      {/* Header */}
      <div className="memory-panel__header">
        <div className="memory-panel__header-info">
          <span className="memory-panel__count">
            {memories.length > 0
              ? `${enabledCount} 条生效 · 共 ${memories.length} 条`
              : "暂无记忆"}
          </span>
        </div>
        <div className="memory-panel__header-actions">
          {memories.length > 0 && (
            <button
              type="button"
              className="memory-panel__btn memory-panel__btn--ghost memory-panel__btn--danger"
              onClick={() => setConfirmClearAll(true)}
            >
              清空全部
            </button>
          )}
          <button
            type="button"
            className="memory-panel__btn memory-panel__btn--primary"
            onClick={openCreate}
          >
            + 新增记忆
          </button>
        </div>
      </div>

      {/* Hint */}
      <div className="memory-panel__hint">
        这些记忆将在每次与思思对话时自动注入上下文，帮助思思更好地理解你的身份和需求。你也可以在对话中直接告诉思思信息，她会自动记住。
      </div>

      {/* Category filter */}
      <div className="memory-panel__filters">
        <button
          type="button"
          className={`memory-panel__filter-btn${filterCategory === "ALL" ? " is-active" : ""}`}
          onClick={() => setFilterCategory("ALL")}
        >
          全部{memories.length > 0 && ` (${memories.length})`}
        </button>
        {CATEGORY_ORDER.map((cat) => {
          const count = memories.filter((m) => m.category === cat).length;
          if (count === 0) return null;
          const meta = CATEGORY_META[cat];
          return (
            <button
              key={cat}
              type="button"
              className={`memory-panel__filter-btn${filterCategory === cat ? " is-active" : ""}`}
              onClick={() => setFilterCategory(cat)}
            >
              {meta.label} ({count})
            </button>
          );
        })}
      </div>

      {/* Error */}
      {error && (
        <div className="memory-panel__error">
          {error}
          <button type="button" onClick={() => setError(null)}>×</button>
        </div>
      )}

      {/* Loading */}
      {loading && <div className="memory-panel__loading">加载中...</div>}

      {/* Empty state */}
      {!loading && memories.length === 0 && (
        <div className="memory-panel__empty">
          <div className="memory-panel__empty-icon">专</div>
          <div className="memory-panel__empty-title">还没有任何专属记忆</div>
          <div className="memory-panel__empty-desc">
            你可以手动添加，或在对话中告诉思思你的身份信息，她会自动记住。
          </div>
          <button
            type="button"
            className="memory-panel__btn memory-panel__btn--primary"
            onClick={openCreate}
          >
            + 添加第一条记忆
          </button>
        </div>
      )}

      {/* Memory groups */}
      {!loading && Object.keys(grouped).length > 0 && (
        <div className="memory-panel__groups">
          {CATEGORY_ORDER.filter((c) => grouped[c]).map((cat) => {
            const meta = CATEGORY_META[cat];
            return (
              <div key={cat} className="memory-panel__group">
                <div
                  className="memory-panel__group-label"
                  style={{ color: meta.color }}
                >
                  <span>{meta.label}</span>
                </div>
                <div className="memory-panel__group-items">
                  {grouped[cat].map((item) => (
                    <MemoryCard
                      key={item.id}
                      item={item}
                      onToggle={handleToggleEnabled}
                      onEdit={openEdit}
                      onDelete={handleDelete}
                    />
                  ))}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Add/Edit form modal */}
      {showForm && (
        <div className="memory-panel__overlay" onClick={closeForm}>
          <div
            className="memory-panel__form-card"
            role="dialog"
            aria-modal="true"
            aria-labelledby="memory-panel-form-title"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="memory-panel__form-title" id="memory-panel-form-title">
              {editingId !== null ? "编辑记忆" : "新增记忆"}
            </div>

            <div className="memory-panel__form-field">
              <label>类别</label>
              <div className="memory-panel__category-btns">
                {CATEGORY_ORDER.map((cat) => {
                  const meta = CATEGORY_META[cat];
                  return (
                    <button
                      key={cat}
                      type="button"
                      className={`memory-panel__category-btn${form.category === cat ? " is-active" : ""}`}
                      style={
                        form.category === cat
                          ? { borderColor: meta.color, color: meta.color, background: meta.color + "15" }
                          : {}
                      }
                      onClick={() => setForm((f) => ({ ...f, category: cat }))}
                    >
                      {meta.label}
                    </button>
                  );
                })}
              </div>
            </div>

            <div className="memory-panel__form-field">
              <label>记忆内容</label>
              <textarea
                className="memory-panel__textarea"
                rows={3}
                placeholder="输入要记住的内容，简洁清晰..."
                value={form.content}
                onChange={(e) => setForm((f) => ({ ...f, content: e.target.value }))}
                maxLength={500}
              />
              <div className="memory-panel__char-count">{form.content.length}/500</div>
            </div>

            <div className="memory-panel__form-field">
              <label>
                语义键 <span className="memory-panel__label-hint">（可选，相同键值将覆盖旧记忆，如 user.role）</span>
              </label>
              <input
                type="text"
                className="memory-panel__input"
                placeholder="如 user.role、user.location"
                value={form.memoryKey}
                onChange={(e) => setForm((f) => ({ ...f, memoryKey: e.target.value }))}
                maxLength={128}
              />
            </div>

            <div className="memory-panel__form-toggles">
              <label className="memory-panel__toggle-row">
                <span>启用此记忆</span>
                <input
                  type="checkbox"
                  checked={form.enabled}
                  onChange={(e) => setForm((f) => ({ ...f, enabled: e.target.checked }))}
                />
              </label>
              <label className="memory-panel__toggle-row">
                <span>置顶显示</span>
                <input
                  type="checkbox"
                  checked={form.pinned}
                  onChange={(e) => setForm((f) => ({ ...f, pinned: e.target.checked }))}
                />
              </label>
            </div>

            {error && <div className="memory-panel__form-error">{error}</div>}

            <div className="memory-panel__form-actions">
              <button
                type="button"
                className="memory-panel__btn memory-panel__btn--ghost"
                onClick={closeForm}
                disabled={saving}
              >
                取消
              </button>
              <button
                type="button"
                className="memory-panel__btn memory-panel__btn--primary"
                onClick={handleSave}
                disabled={saving || !form.content.trim()}
              >
                {saving ? "保存中..." : "保存"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Confirm clear all */}
      {confirmClearAll && (
        <div className="memory-panel__overlay" onClick={() => setConfirmClearAll(false)}>
          <div
            className="memory-panel__form-card memory-panel__form-card--compact"
            role="dialog"
            aria-modal="true"
            aria-labelledby="memory-panel-clear-title"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="memory-panel__form-title" id="memory-panel-clear-title">确认清空全部记忆？</div>
            <p className="memory-panel__confirm-desc">
              此操作将删除所有 {memories.length} 条专属记忆，无法恢复。
            </p>
            <div className="memory-panel__form-actions">
              <button
                type="button"
                className="memory-panel__btn memory-panel__btn--ghost"
                onClick={() => setConfirmClearAll(false)}
              >
                取消
              </button>
              <button
                type="button"
                className="memory-panel__btn memory-panel__btn--danger"
                onClick={handleClearAll}
              >
                确认清空
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function MemoryCard({
  item,
  onToggle,
  onEdit,
  onDelete,
}: {
  item: MemoryItem;
  onToggle: (item: MemoryItem) => void;
  onEdit: (item: MemoryItem) => void;
  onDelete: (id: number) => void;
}) {
  const [confirmDelete, setConfirmDelete] = useState(false);
  const meta = CATEGORY_META[item.category];

  return (
    <div className={`memory-card${item.enabled ? "" : " memory-card--disabled"}`}>
      <div className="memory-card__main">
        <label className="memory-card__toggle" title={item.enabled ? "点击禁用" : "点击启用"}>
          <input
            type="checkbox"
            checked={item.enabled}
            onChange={() => onToggle(item)}
          />
        </label>
        <div className="memory-card__body">
          <div className="memory-card__content">{item.content}</div>
          <div className="memory-card__meta">
            {item.pinned && <span className="memory-card__badge memory-card__badge--pin">置顶</span>}
            <span className={`memory-card__badge memory-card__badge--source memory-card__badge--${item.source.toLowerCase()}`}>
              {SOURCE_LABELS[item.source]}
            </span>
            {item.memoryKey && (
              <span className="memory-card__badge memory-card__badge--key">{item.memoryKey}</span>
            )}
          </div>
        </div>
        <div className="memory-card__actions">
          <button
            type="button"
            className="memory-card__action-btn"
            title="编辑"
            onClick={() => onEdit(item)}
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
              <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
            </svg>
          </button>
          {confirmDelete ? (
            <div className="memory-card__delete-confirm">
              <span>确认删除?</span>
              <button type="button" className="memory-card__action-btn memory-card__action-btn--danger"
                onClick={() => onDelete(item.id)}>是</button>
              <button type="button" className="memory-card__action-btn"
                onClick={() => setConfirmDelete(false)}>否</button>
            </div>
          ) : (
            <button
              type="button"
              className="memory-card__action-btn memory-card__action-btn--danger"
              title="删除"
              onClick={() => setConfirmDelete(true)}
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <polyline points="3 6 5 6 21 6" />
                <path d="M19 6l-1 14H6L5 6" />
                <path d="M10 11v6M14 11v6" />
                <path d="M9 6V4h6v2" />
              </svg>
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
