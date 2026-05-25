import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAdminToken } from "../useAdminToken";
import type { Skill, SkillExportJob, SkillImportPreview } from "../skills/skillStudioShared";
import { downloadSkillExportPackage, riskBadgeClass, riskLabel, skillSourceLabel } from "../skills/skillStudioShared";

function formatTs(iso: string | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString("zh-CN", { dateStyle: "short", timeStyle: "short" });
}

export default function AdminSkillsListPage() {
  const token = useAdminToken();
  const nav = useNavigate();
  const [notice, setNotice] = useState("");
  const [skills, setSkills] = useState<Skill[]>([]);
  const [search, setSearch] = useState("");
  const [scope, setScope] = useState<"all" | "platform" | "custom">("all");
  const [importing, setImporting] = useState(false);
  const [openActionMenuId, setOpenActionMenuId] = useState<number | null>(null);

  const flash = (msg: string) => {
    setNotice(msg);
    window.setTimeout(() => setNotice(""), 3200);
  };

  const loadSkills = async () => {
    const res = await fetch("/skills", { headers: { Authorization: `Bearer ${token}` } });
    const json = await res.json();
    if (!res.ok || !json.success) {
      flash(`加载失败：${json.message ?? `HTTP ${res.status}`}`);
      return;
    }
    setSkills((json.data ?? []) as Skill[]);
  };

  const exportSkill = async (skill: Skill) => {
    const res = await fetch(`/skills/${skill.id}/exports`, {
      method: "POST",
      headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
      body: JSON.stringify({ allowDraft: false }),
    });
    const json = await res.json();
    if (!res.ok || !json.success) {
      flash(`导出失败：${json.message ?? `HTTP ${res.status}`}`);
      return;
    }
    const job = json.data as SkillExportJob;
    try {
      await downloadSkillExportPackage(token, job);
      flash("已生成通用技能包");
    } catch (err) {
      flash(`下载失败：${err instanceof Error ? err.message : "未知错误"}`);
    }
  };

  const deleteSkill = async (skill: Skill) => {
    const impactRes = await fetch(`/skills/${skill.id}/delete-impact`, { headers: { Authorization: `Bearer ${token}` } });
    const impactJson = await impactRes.json();
    if (!impactRes.ok || !impactJson.success) {
      flash(`删除检查失败：${impactJson.message ?? `HTTP ${impactRes.status}`}`);
      return;
    }
    const blockers = impactJson.data?.blockers ?? [];
    if (blockers.length > 0) {
      flash(`不能删除：${blockers.join("；")}`);
      return;
    }
    const confirmed = window.confirm(`删除自定义技能「${skill.name}」？普通列表将不再显示，历史审计与运行时快照会保留。`);
    if (!confirmed) return;
    const reason = window.prompt("删除原因", "测试技能已废弃") ?? "";
    const res = await fetch(`/skills/${skill.id}`, {
      method: "DELETE",
      headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
      body: JSON.stringify({ reason }),
    });
    const json = await res.json();
    if (!res.ok || !json.success) {
      flash(`删除失败：${json.message ?? `HTTP ${res.status}`}`);
      return;
    }
    flash("已删除");
    void loadSkills();
  };

  const importZip = async (file: File) => {
    const data = new FormData();
    data.append("file", file);
    setImporting(true);
    try {
      const importRes = await fetch("/skills/imports", {
        method: "POST",
        headers: { Authorization: `Bearer ${token}` },
        body: data,
      });
      const importJson = await importRes.json();
      if (!importRes.ok || !importJson.success) {
        flash(`导入解析失败：${importJson.message ?? `HTTP ${importRes.status}`}`);
        return;
      }
      const preview = (importJson.data ?? null) as SkillImportPreview | null;
      if (!preview?.importId || !preview.draft) {
        flash("导入解析成功，但未返回有效预览");
        return;
      }
      const unmatchedResourceMessage = preview.resourceMapping?.hasUnmatchedResources
        ? (preview.warnings ?? []).slice(0, 2).join("；")
        : "";
      const importId = preview.importId;
      const createRes = await fetch(`/skills/imports/${importId}/create`, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({ draftOverride: preview.draft }),
      });
      const createJson = await createRes.json();
      if (!createRes.ok || !createJson.success) {
        flash(`导入创建失败：${createJson.message ?? `HTTP ${createRes.status}`}`);
        return;
      }
      const created = createJson.data as Skill;
      flash(
        unmatchedResourceMessage
          ? `已导入为自定义技能草稿，部分资源未匹配：${unmatchedResourceMessage}`
          : "已导入为自定义技能草稿",
      );
      await loadSkills();
      nav(`/admin/skills/${created.id}/edit`);
    } finally {
      setImporting(false);
    }
  };

  useEffect(() => {
    void loadSkills();
  }, [token]);

  useEffect(() => {
    const closeOnOutsideClick = (event: MouseEvent) => {
      const target = event.target;
      if (target instanceof Element && target.closest("[data-skills-row-menu]")) return;
      setOpenActionMenuId(null);
    };
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpenActionMenuId(null);
    };
    document.addEventListener("mousedown", closeOnOutsideClick);
    document.addEventListener("keydown", closeOnEscape);
    return () => {
      document.removeEventListener("mousedown", closeOnOutsideClick);
      document.removeEventListener("keydown", closeOnEscape);
    };
  }, []);

  const filtered = useMemo(() => {
      const keyword = search.trim().toLowerCase();
    return skills.filter((skill) => {
      if (scope === "platform" && skill.sourceType !== "PLATFORM_STANDARD") return false;
      if (scope === "custom" && skill.sourceType !== "TENANT_CUSTOM") return false;
      if (!keyword) return true;
      return (
        skill.skillCode.toLowerCase().includes(keyword) ||
        skill.name.toLowerCase().includes(keyword) ||
        (skill.description ?? "").toLowerCase().includes(keyword)
      );
    });
  }, [skills, search, scope]);

  return (
    <div className="admin-page skills-catalog">
      {notice ? <div className="cici-toast">{notice}</div> : null}

      <header className="skills-catalog__header">
        <div>
          <h1 className="skills-catalog__title">
            技能 <span className="skills-catalog__count">({skills.length})</span>
          </h1>
        </div>
        <div className="skills-catalog__actions">
          <label className={`skills-catalog__secondary skills-catalog__file-btn${importing ? " is-disabled" : ""}`}>
            {importing ? "导入中…" : "导入技能"}
            <input
              type="file"
              accept=".zip,application/zip,application/x-zip-compressed"
              disabled={importing}
              onChange={(event) => {
                const file = event.currentTarget.files?.[0];
                event.currentTarget.value = "";
                if (file) void importZip(file);
              }}
            />
          </label>
          <Link to="/admin/skills/new" className="skills-catalog__primary">
            新建技能
          </Link>
        </div>
      </header>

      <div className="skills-catalog__toolbar">
        <input
          className="skills-search"
          placeholder="按名称、技能代码、摘要搜索…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <div className="skills-scope-tabs">
          <button type="button" className={scope === "all" ? "active" : ""} onClick={() => setScope("all")}>
            全部
          </button>
          <button type="button" className={scope === "platform" ? "active" : ""} onClick={() => setScope("platform")}>
            平台标准
          </button>
          <button type="button" className={scope === "custom" ? "active" : ""} onClick={() => setScope("custom")}>
            自定义
          </button>
        </div>
      </div>

      <div className="skills-table-wrap skills-table-wrap--catalog">
        <table className="skills-data-table skills-data-table--catalog">
          <colgroup>
            <col className="skills-data-table__col-skill" />
            <col className="skills-data-table__col-summary" />
            <col className="skills-data-table__col-version" />
            <col className="skills-data-table__col-created" />
            <col className="skills-data-table__col-published" />
            <col className="skills-data-table__col-risk" />
            <col className="skills-data-table__col-actions" />
          </colgroup>
          <thead>
            <tr>
              <th>技能</th>
              <th>摘要</th>
              <th>版本</th>
              <th>创建时间</th>
              <th>最新发布</th>
              <th>风险</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {filtered.map((skill) => (
              <tr key={skill.id} className={openActionMenuId === skill.id ? "is-action-menu-open" : ""}>
                <td>
                  <div className="skills-data-table__skill-name">{skill.name}</div>
                  <div className="skills-data-table__skill-code" title={skill.skillCode}>
                    {skill.skillCode}
                  </div>
                  <div className="skills-data-table__flags">
                    <span className="skills-pill">{skillSourceLabel(skill.sourceType)}</span>
                    <span className="skills-pill">{skill.enabled ? "启用" : "停用"}</span>
                    {skill.templateCode && skill.templateCode !== skill.skillCode ? <span className="skills-pill">{skill.templateCode}</span> : null}
                  </div>
                </td>
                <td title={skill.description?.trim() || "—"}>
                  <div className="skills-data-table__summary-text">{skill.description?.trim() || "—"}</div>
                </td>
                <td className="skills-data-table__mono">
                  {skill.latestVersionNo != null ? `v${skill.latestVersionNo}` : "—"}
                  {skill.currentPublishedVersionId ? <div className="skills-data-table__sub">已发布</div> : <div className="skills-data-table__sub">仅草稿</div>}
                </td>
                <td className="skills-data-table__mono">{formatTs(skill.createdAt)}</td>
                <td className="skills-data-table__mono">{formatTs(skill.lastPublishedAt)}</td>
                <td>
                  <span className={riskBadgeClass(skill.riskLevel)}>{riskLabel(skill.riskLevel)}</span>
                </td>
                <td className="skills-data-table__actions-cell">
                  {skill.sourceType === "TENANT_CUSTOM" && skill.editPolicy === "EDITABLE" ? (
                    <div className={`skills-row-menu${openActionMenuId === skill.id ? " is-open" : ""}`} data-skills-row-menu>
                      <button
                        type="button"
                        className="skills-row-menu__trigger"
                        aria-haspopup="menu"
                        aria-expanded={openActionMenuId === skill.id}
                        aria-label={`打开「${skill.name}」操作菜单`}
                        onClick={() => setOpenActionMenuId((current) => (current === skill.id ? null : skill.id))}
                      >
                        <span aria-hidden="true">•••</span>
                      </button>
                      {openActionMenuId === skill.id ? (
                        <div className="skills-row-menu__panel" role="menu" aria-label={`${skill.name}操作`}>
                          <Link to={`/admin/skills/${skill.id}/edit`} className="skills-row-menu__item" role="menuitem" onClick={() => setOpenActionMenuId(null)}>
                            编辑
                          </Link>
                          <button
                            type="button"
                            className="skills-row-menu__item"
                            role="menuitem"
                            onClick={() => {
                              setOpenActionMenuId(null);
                              void exportSkill(skill);
                            }}
                          >
                            导出
                          </button>
                          <button
                            type="button"
                            className="skills-row-menu__item skills-row-menu__item--danger"
                            role="menuitem"
                            onClick={() => {
                              setOpenActionMenuId(null);
                              void deleteSkill(skill);
                            }}
                          >
                            删除
                          </button>
                        </div>
                      ) : null}
                    </div>
                  ) : null}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {filtered.length === 0 ? (
          <p className="subtle skills-catalog__empty">无匹配技能</p>
        ) : null}
      </div>
    </div>
  );
}
