import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useAdminToken } from "../useAdminToken";
import type { Skill } from "../skills/skillStudioShared";
import { riskBadgeClass, riskLabel } from "../skills/skillStudioShared";

function formatTs(iso: string | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString("zh-CN", { dateStyle: "short", timeStyle: "short" });
}

export default function AdminSkillsListPage() {
  const token = useAdminToken();
  const [notice, setNotice] = useState("");
  const [skills, setSkills] = useState<Skill[]>([]);
  const [search, setSearch] = useState("");
  const [scope, setScope] = useState<"all" | "crm" | "builtin">("all");

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

  useEffect(() => {
    void loadSkills();
  }, [token]);

  const filtered = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    return skills.filter((skill) => {
      const isCrm = skill.skillCode.startsWith("crm-") || skill.skillCode.includes("sales");
      if (scope === "crm" && !isCrm) return false;
      if (scope === "builtin" && !skill.builtin) return false;
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
      {notice ? <div className="dify-toast">{notice}</div> : null}

      <header className="skills-catalog__header">
        <div>
          <p className="skills-catalog__kicker">Skill Studio</p>
          <h1 className="skills-catalog__title">
            技能 <span className="skills-catalog__count">({skills.length})</span>
          </h1>
          <p className="subtle skills-catalog__subtitle">技能列表 · Skills</p>
        </div>
        <Link to="/admin/skills/new" className="skills-catalog__primary">
          新建技能 · New
        </Link>
      </header>

      <div className="skills-catalog__toolbar">
        <input
          className="skills-search"
          placeholder="按名称、skillCode、摘要搜索…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <div className="skills-scope-tabs">
          <button type="button" className={scope === "all" ? "active" : ""} onClick={() => setScope("all")}>
            全部
          </button>
          <button type="button" className={scope === "crm" ? "active" : ""} onClick={() => setScope("crm")}>
            CRM
          </button>
          <button type="button" className={scope === "builtin" ? "active" : ""} onClick={() => setScope("builtin")}>
            内置
          </button>
        </div>
      </div>

      <div className="skills-table-wrap">
        <table className="skills-data-table">
          <thead>
            <tr>
              <th>技能 / Skill</th>
              <th>摘要 / Summary</th>
              <th>版本 / Ver.</th>
              <th>创建时间 / Created</th>
              <th>最新发布 / Published</th>
              <th>风险 / Risk</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {filtered.map((skill) => (
              <tr key={skill.id}>
                <td>
                  <div className="skills-data-table__skill-name">{skill.name}</div>
                  <div className="skills-data-table__skill-code">{skill.skillCode}</div>
                  <div className="skills-data-table__flags">
                    {skill.builtin ? <span className="skills-pill">内置</span> : null}
                    <span className="skills-pill">{skill.enabled ? "启用" : "停用"}</span>
                  </div>
                </td>
                <td className="skills-data-table__summary">{skill.description?.trim() || "—"}</td>
                <td className="skills-data-table__mono">
                  {skill.latestVersionNo != null ? `v${skill.latestVersionNo}` : "—"}
                </td>
                <td className="skills-data-table__mono">{formatTs(skill.createdAt)}</td>
                <td className="skills-data-table__mono">{formatTs(skill.lastPublishedAt)}</td>
                <td>
                  <span className={riskBadgeClass(skill.riskLevel)}>{riskLabel(skill.riskLevel)}</span>
                </td>
                <td className="skills-data-table__actions">
                  <Link to={`/admin/skills/${skill.id}/edit`} className="text-link">
                    编辑
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {filtered.length === 0 ? (
          <p className="subtle skills-catalog__empty">无匹配技能 · No matching skills</p>
        ) : null}
      </div>
    </div>
  );
}
