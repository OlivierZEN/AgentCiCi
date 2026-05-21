import { useEffect, useMemo, useState } from "react";
import { LS_PLATFORM_TOKEN, PLATFORM_API_BASE } from "../../constants";

type PlatformTool = {
  toolName: string;
  displayName: string;
  description?: string;
  riskLevel: string;
  category: string;
  enabled: boolean;
  dependentSkillCodes: string[];
  agentBindingCount: number;
  updatedAt: string;
};

function readToken(): string {
  const raw = localStorage.getItem(LS_PLATFORM_TOKEN);
  if (!raw) return "";
  try {
    return (JSON.parse(raw) as { token?: string }).token ?? "";
  } catch {
    return "";
  }
}

function formatTs(ts?: string): string {
  if (!ts) return "—";
  const d = new Date(ts);
  if (Number.isNaN(d.getTime())) return ts;
  return d.toLocaleString();
}

function riskLabel(level: string): string {
  switch (level) {
    case "LOW":
      return "低风险";
    case "MEDIUM":
      return "中风险";
    case "HIGH":
      return "高风险";
    default:
      return level || "未知";
  }
}

function categoryLabel(category: string): string {
  switch (category) {
    case "general":
      return "通用";
    case "builtin":
      return "内建";
    case "integration":
      return "集成";
    case "governance":
      return "治理";
    case "approval":
      return "审批";
    case "crm":
      return "CRM";
    case "email":
      return "邮件";
    case "knowledge":
      return "知识库";
    case "web":
      return "网页";
    default:
      return category || "未分类";
  }
}

export default function PlatformToolsPage() {
  const token = readToken();
  const [tools, setTools] = useState<PlatformTool[]>([]);
  const [selectedToolName, setSelectedToolName] = useState<string>("");
  const [form, setForm] = useState({
    displayName: "",
    description: "",
    riskLevel: "MEDIUM",
    category: "general",
    enabled: true,
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  const selectedTool = useMemo(
    () => tools.find((item) => item.toolName === selectedToolName) ?? null,
    [tools, selectedToolName],
  );

  async function loadTools(preferredToolName?: string) {
    if (!token) return;
    setError("");
    try {
      const res = await fetch(`${PLATFORM_API_BASE}/tools`, { headers: { Authorization: `Bearer ${token}` } });
      const json = await res.json();
      if (!res.ok || !json.success) throw new Error(json.message || "加载工具目录失败");
      const rows = (json.data ?? []) as PlatformTool[];
      setTools(rows);
      const nextName = preferredToolName || selectedToolName || rows[0]?.toolName || "";
      setSelectedToolName(nextName);
      const next = rows.find((item) => item.toolName === nextName) ?? null;
      if (next) {
        setForm({
          displayName: next.displayName,
          description: next.description ?? "",
          riskLevel: next.riskLevel,
          category: next.category,
          enabled: next.enabled,
        });
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "加载工具目录失败");
    }
  }

  useEffect(() => {
    void loadTools();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  useEffect(() => {
    if (!selectedTool) return;
    setForm({
      displayName: selectedTool.displayName,
      description: selectedTool.description ?? "",
      riskLevel: selectedTool.riskLevel,
      category: selectedTool.category,
      enabled: selectedTool.enabled,
    });
  }, [selectedTool]);

  async function saveTool() {
    if (!selectedTool) return;
    setSaving(true);
    setError("");
    setMessage("");
    try {
      const res = await fetch(`${PLATFORM_API_BASE}/tools/${selectedTool.toolName}`, {
        method: "PUT",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify(form),
      });
      const json = await res.json();
      if (!res.ok || !json.success) throw new Error(json.message || "更新工具治理失败");
      setMessage(`已更新 ${selectedTool.displayName} 的治理设置。`);
      await loadTools(selectedTool.toolName);
    } catch (err) {
      setError(err instanceof Error ? err.message : "更新工具治理失败");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="admin-page skills-catalog platform-page platform-tools-page">
      <header className="skills-catalog__header">
        <div className="platform-page-head__main">
          <h1 className="skills-catalog__title">内置工具治理</h1>
          <p className="subtle skills-catalog__subtitle">统一管理工具展示名、风险级别、分类、启停状态和依赖关系。</p>
        </div>
        <div className="platform-page-head__aside">
          <span className="platform-inline-stat">工具 {tools.length}</span>
          <span className="platform-inline-stat">已启用 {tools.filter((tool) => tool.enabled).length}</span>
        </div>
      </header>

      {error ? <div className="platform-console__banner platform-console__banner--error">{error}</div> : null}
      {message ? <div className="platform-console__banner platform-console__banner--success">{message}</div> : null}

      <div className="platform-console__grid">
        <section className="platform-console__panel skills-table-wrap">
          <table className="skills-data-table">
            <thead>
              <tr>
                <th>工具</th>
                <th>分类</th>
                <th>风险</th>
                <th>依赖技能</th>
              </tr>
            </thead>
            <tbody>
              {tools.map((tool) => (
                <tr
                  key={tool.toolName}
                  className={`platform-console__select-row${tool.toolName === selectedToolName ? " platform-console__row--active" : ""}`}
                  onClick={() => setSelectedToolName(tool.toolName)}
                >
                  <td>
                    <div className="skills-data-table__skill-name">{tool.displayName}</div>
                  </td>
                  <td>{categoryLabel(tool.category)}</td>
                  <td>{riskLabel(tool.riskLevel)}</td>
                  <td>{tool.dependentSkillCodes.length}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>

        <section className="platform-console__panel">
          {selectedTool ? (
            <div className="platform-console__stack">
              <div className="platform-console__section">
                <p className="platform-section-label">当前工具</p>
                <h2 className="platform-console__heading">{selectedTool.displayName}</h2>
                <p className="skills-data-table__summary">
                  最近更新时间 {formatTs(selectedTool.updatedAt)} · 已绑定 {selectedTool.agentBindingCount} 个智能体
                </p>
                <div className="platform-console__badges">
                  <span className="skills-pill">{riskLabel(selectedTool.riskLevel)}</span>
                  <span className="skills-pill">{categoryLabel(selectedTool.category)}</span>
                  <span className="skills-pill">{selectedTool.enabled ? "已启用" : "已停用"}</span>
                </div>
              </div>

              <div className="platform-console__section">
                <h3 className="platform-console__subheading">治理配置</h3>
                <div className="platform-console__form-grid">
                  <label>
                    展示名
                    <input
                      value={form.displayName}
                      onChange={(e) => setForm((prev) => ({ ...prev, displayName: e.target.value }))}
                    />
                  </label>
                  <label>
                    分类
                    <input value={form.category} onChange={(e) => setForm((prev) => ({ ...prev, category: e.target.value }))} />
                  </label>
                  <label>
                    风险等级
                    <select
                      value={form.riskLevel}
                      onChange={(e) => setForm((prev) => ({ ...prev, riskLevel: e.target.value }))}
                    >
                      <option value="LOW">低风险</option>
                      <option value="MEDIUM">中风险</option>
                      <option value="HIGH">高风险</option>
                    </select>
                  </label>
                  <label className="platform-console__checkbox">
                    <input
                      type="checkbox"
                      checked={form.enabled}
                      onChange={(e) => setForm((prev) => ({ ...prev, enabled: e.target.checked }))}
                    />
                    平台启用
                  </label>
                  <label className="platform-console__field--full">
                    说明
                    <textarea
                      rows={4}
                      value={form.description}
                      onChange={(e) => setForm((prev) => ({ ...prev, description: e.target.value }))}
                    />
                  </label>
                </div>
                <div className="platform-console__actions">
                  <button className="platform-button platform-button--primary" disabled={saving} onClick={() => void saveTool()}>
                    {saving ? "处理中…" : "保存治理配置"}
                  </button>
                </div>
              </div>

              <div className="platform-console__section">
                <h3 className="platform-console__subheading">关联标准技能</h3>
                <div className="skills-data-table__flags">
                  {selectedTool.dependentSkillCodes.length > 0 ? (
                    <span className="skills-data-table__summary">当前有 {selectedTool.dependentSkillCodes.length} 个标准技能依赖这个工具。</span>
                  ) : (
                    <span className="skills-data-table__summary">当前没有标准技能依赖这个工具。</span>
                  )}
                </div>
              </div>
            </div>
          ) : (
            <p className="skills-data-table__summary">请选择一个内置工具。</p>
          )}
        </section>
      </div>
    </div>
  );
}
