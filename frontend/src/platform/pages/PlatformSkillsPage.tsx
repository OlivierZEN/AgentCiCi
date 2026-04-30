import { useEffect, useMemo, useState } from "react";
import { LS_PLATFORM_TOKEN, PLATFORM_API_BASE } from "../../constants";

type PlatformSkillImpact = {
  boundAgentCount: number;
  derivedSkillCount: number;
  publishedWorkflowCount: number;
  currentVersionPinnedWorkflowCount: number;
  historicalPinnedWorkflowCount: number;
  sampleAgentIds: string[];
  rolloutHint: string;
  rollbackHint: string;
};

type PlatformSkill = {
  id: number;
  skillCode: string;
  name: string;
  description?: string;
  enabled: boolean;
  riskLevel: string;
  sourceType: string;
  visibility: string;
  bindingPolicy: string;
  updatePolicy: string;
  templateCode?: string;
  currentTemplateVersionNo?: number;
  derivedSkillCount: number;
  agentBindingCount: number;
  versionCount: number;
  latestDraftVersionNo?: number | null;
  updatedAt: string;
  impact?: PlatformSkillImpact;
};

type PlatformSkillVersionImpact = {
  pinnedWorkflowCount: number;
  pinnedAgentCount: number;
  sampleAgentIds: string[];
  summaryLines: string[];
  rolloutStage: string;
  rollbackReady: boolean;
};

type PlatformSkillVersion = {
  id: number;
  versionNo: number;
  name: string;
  description?: string;
  promptFragment?: string;
  toolWhitelist: string[];
  kbWhitelist: string[];
  handoffRule?: string;
  outputContract?: string;
  riskLevel: string;
  publishStatus: string;
  changelog?: string;
  createdBy: string;
  createdAt: string;
  publishedAt?: string;
  impact?: PlatformSkillVersionImpact;
};

type PolicyBundleSummary = {
  bundleCode: string;
  versionNo: number;
  name: string;
  description?: string;
  publishStatus: string;
  sourceSkillCodes: string[];
  handoffRules: string[];
  livePublishedAgentCount: number;
  promptLineCount: number;
  versionCount: number;
  latestDraftVersionNo?: number | null;
  sampleAgentIds: string[];
  rolloutHint: string;
  rollbackHint: string;
  updatedAt: string;
};

type PolicyBundleVersionImpact = {
  livePublishedAgentCount: number;
  sampleAgentIds: string[];
  summaryLines: string[];
  rolloutStage: string;
  rollbackReady: boolean;
};

type PolicyBundleVersion = {
  id: number;
  versionNo: number;
  name: string;
  description?: string;
  promptFragment?: string;
  handoffRules: string[];
  sourceSkillCodes: string[];
  publishStatus: string;
  createdBy: string;
  createdAt: string;
  publishedAt?: string;
  impact?: PolicyBundleVersionImpact;
};

type DraftForm = {
  name: string;
  description: string;
  promptFragment: string;
  toolWhitelist: string;
  kbWhitelist: string;
  handoffRule: string;
  outputContract: string;
  riskLevel: string;
  changelog: string;
};

type GovernanceForm = {
  enabled: boolean;
  visibility: string;
  bindingPolicy: string;
};

type PolicyBundleDraftForm = {
  name: string;
  description: string;
  promptFragment: string;
  handoffRules: string;
  sourceSkillCodes: string;
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

function csvToArray(raw: string): string[] {
  return raw
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

function arrayToCsv(items?: string[]): string {
  return (items ?? []).join(", ");
}

function formatTs(ts?: string): string {
  if (!ts) return "—";
  const d = new Date(ts);
  if (Number.isNaN(d.getTime())) return ts;
  return d.toLocaleString();
}

function versionToDraft(version: PlatformSkillVersion): DraftForm {
  return {
    name: version.name ?? "",
    description: version.description ?? "",
    promptFragment: version.promptFragment ?? "",
    toolWhitelist: arrayToCsv(version.toolWhitelist),
    kbWhitelist: arrayToCsv(version.kbWhitelist),
    handoffRule: version.handoffRule ?? "",
    outputContract: version.outputContract ?? "",
    riskLevel: version.riskLevel ?? "MEDIUM",
    changelog: "",
  };
}

function policyVersionToDraft(version: PolicyBundleVersion): PolicyBundleDraftForm {
  return {
    name: version.name ?? "",
    description: version.description ?? "",
    promptFragment: version.promptFragment ?? "",
    handoffRules: (version.handoffRules ?? []).join("\n"),
    sourceSkillCodes: arrayToCsv(version.sourceSkillCodes),
  };
}

export default function PlatformSkillsPage() {
  const token = readToken();
  const [skills, setSkills] = useState<PlatformSkill[]>([]);
  const [policyBundle, setPolicyBundle] = useState<PolicyBundleSummary | null>(null);
  const [policyBundleVersions, setPolicyBundleVersions] = useState<PolicyBundleVersion[]>([]);
  const [selectedSkillId, setSelectedSkillId] = useState<number | null>(null);
  const [versions, setVersions] = useState<PlatformSkillVersion[]>([]);
  const [draft, setDraft] = useState<DraftForm>({
    name: "",
    description: "",
    promptFragment: "",
    toolWhitelist: "",
    kbWhitelist: "",
    handoffRule: "",
    outputContract: "",
    riskLevel: "MEDIUM",
    changelog: "",
  });
  const [governance, setGovernance] = useState<GovernanceForm>({
    enabled: true,
    visibility: "VISIBLE",
    bindingPolicy: "OPTIONAL",
  });
  const [policyDraft, setPolicyDraft] = useState<PolicyBundleDraftForm>({
    name: "",
    description: "",
    promptFragment: "",
    handoffRules: "",
    sourceSkillCodes: "",
  });
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  const selectedSkill = useMemo(
    () => skills.find((item) => item.id === selectedSkillId) ?? null,
    [skills, selectedSkillId],
  );

  async function loadSkills(preferredId?: number | null) {
    if (!token) return;
    setLoading(true);
    setError("");
    try {
      const res = await fetch(`${PLATFORM_API_BASE}/skills`, { headers: { Authorization: `Bearer ${token}` } });
      const json = await res.json();
      if (!res.ok || !json.success) throw new Error(json.message || "加载平台技能失败");
      const rows = (json.data ?? []) as PlatformSkill[];
      setSkills(rows);
      await loadPolicyBundle();
      const nextId = preferredId ?? selectedSkillId ?? rows[0]?.id ?? null;
      setSelectedSkillId(nextId);
      if (nextId != null) {
        await loadVersions(nextId, rows);
      } else {
        setVersions([]);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "加载平台技能失败");
    } finally {
      setLoading(false);
    }
  }

  async function loadPolicyBundle() {
    if (!token) return;
    const [summaryRes, versionsRes] = await Promise.all([
      fetch(`${PLATFORM_API_BASE}/policies/core`, {
        headers: { Authorization: `Bearer ${token}` },
      }),
      fetch(`${PLATFORM_API_BASE}/policies/core/versions`, {
        headers: { Authorization: `Bearer ${token}` },
      }),
    ]);
    const summaryJson = await summaryRes.json();
    if (!summaryRes.ok || !summaryJson.success) {
      throw new Error(summaryJson.message || "加载核心策略包失败");
    }
    const versionsJson = await versionsRes.json();
    if (!versionsRes.ok || !versionsJson.success) {
      throw new Error(versionsJson.message || "加载核心策略包版本失败");
    }
    const summary = (summaryJson.data ?? null) as PolicyBundleSummary | null;
    const rows = (versionsJson.data ?? []) as PolicyBundleVersion[];
    setPolicyBundle(summary);
    setPolicyBundleVersions(rows);
    const currentVersion =
      rows.find((item) => item.versionNo === summary?.versionNo) ??
      rows.find((item) => item.publishStatus === "PUBLISHED") ??
      rows[0];
    if (currentVersion) {
      setPolicyDraft(policyVersionToDraft(currentVersion));
    }
  }

  async function loadVersions(skillId: number, skillRows?: PlatformSkill[]) {
    if (!token) return;
    setError("");
    try {
      const res = await fetch(`${PLATFORM_API_BASE}/skills/${skillId}/versions`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      const json = await res.json();
      if (!res.ok || !json.success) throw new Error(json.message || "加载版本失败");
      const rows = (json.data ?? []) as PlatformSkillVersion[];
      setVersions(rows);
      const currentSkill = (skillRows ?? skills).find((item) => item.id === skillId) ?? null;
      const currentVersion =
        rows.find((item) => item.versionNo === currentSkill?.currentTemplateVersionNo) ??
        rows.find((item) => item.publishStatus === "PUBLISHED") ??
        rows[0];
      if (currentVersion) {
        setDraft(versionToDraft(currentVersion));
      }
      if (currentSkill) {
        setGovernance({
          enabled: currentSkill.enabled,
          visibility: currentSkill.visibility,
          bindingPolicy: currentSkill.bindingPolicy,
        });
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "加载版本失败");
    }
  }

  async function savePolicyDraft() {
    setSaving(true);
    setError("");
    setMessage("");
    try {
      const res = await fetch(`${PLATFORM_API_BASE}/policies/core/versions`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          name: policyDraft.name,
          description: policyDraft.description,
          promptFragment: policyDraft.promptFragment,
          handoffRules: policyDraft.handoffRules
            .split("\n")
            .map((item) => item.trim())
            .filter(Boolean),
          sourceSkillCodes: csvToArray(policyDraft.sourceSkillCodes),
        }),
      });
      const json = await res.json();
      if (!res.ok || !json.success) throw new Error(json.message || "保存策略草稿失败");
      setMessage("核心策略草稿版本已创建。");
      await loadSkills(selectedSkillId);
    } catch (err) {
      setError(err instanceof Error ? err.message : "保存策略草稿失败");
    } finally {
      setSaving(false);
    }
  }

  async function applyPolicyVersion(versionNo: number, action: "publish" | "rollback") {
    setSaving(true);
    setError("");
    setMessage("");
    try {
      const res = await fetch(`${PLATFORM_API_BASE}/policies/core/${action}`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ versionNo }),
      });
      const json = await res.json();
      if (!res.ok || !json.success) throw new Error(json.message || "应用策略版本失败");
      setMessage(action === "publish" ? `核心策略已发布 v${versionNo}` : `核心策略已回滚到 v${versionNo}`);
      await loadSkills(selectedSkillId);
    } catch (err) {
      setError(err instanceof Error ? err.message : "应用策略版本失败");
    } finally {
      setSaving(false);
    }
  }

  useEffect(() => {
    void loadSkills();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  useEffect(() => {
    if (!selectedSkillId) return;
    void loadVersions(selectedSkillId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedSkillId]);

  async function saveDraft() {
    if (!selectedSkill) return;
    setSaving(true);
    setError("");
    setMessage("");
    try {
      const res = await fetch(`${PLATFORM_API_BASE}/skills/${selectedSkill.id}/versions`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          name: draft.name,
          description: draft.description,
          promptFragment: draft.promptFragment,
          toolWhitelist: csvToArray(draft.toolWhitelist),
          kbWhitelist: csvToArray(draft.kbWhitelist),
          handoffRule: draft.handoffRule,
          outputContract: draft.outputContract,
          riskLevel: draft.riskLevel,
          changelog: draft.changelog,
        }),
      });
      const json = await res.json();
      if (!res.ok || !json.success) throw new Error(json.message || "保存草稿失败");
      setMessage("草稿版本已创建。");
      await loadSkills(selectedSkill.id);
    } catch (err) {
      setError(err instanceof Error ? err.message : "保存草稿失败");
    } finally {
      setSaving(false);
    }
  }

  async function applyVersion(versionNo: number, action: "publish" | "rollback") {
    if (!selectedSkill) return;
    setSaving(true);
    setError("");
    setMessage("");
    try {
      const res = await fetch(`${PLATFORM_API_BASE}/skills/${selectedSkill.id}/${action}`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          versionNo,
          enabled: governance.enabled,
          visibility: governance.visibility,
          bindingPolicy: governance.bindingPolicy,
        }),
      });
      const json = await res.json();
      if (!res.ok || !json.success) throw new Error(json.message || "应用版本失败");
      setMessage(action === "publish" ? `已发布 v${versionNo}` : `已回滚到 v${versionNo}`);
      await loadSkills(selectedSkill.id);
    } catch (err) {
      setError(err instanceof Error ? err.message : "应用版本失败");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="admin-page skills-catalog platform-page platform-skills-page">
      <header className="skills-catalog__header platform-page-head">
        <div className="platform-page-head__main">
          <p className="skills-catalog__kicker">Platform Skills</p>
          <h1 className="skills-catalog__title">平台标准技能</h1>
          <p className="subtle skills-catalog__subtitle">模板版本、治理字段、发布/回滚与影响范围在这里收口。</p>
        </div>
        <div className="platform-page-head__aside">
          <span className="platform-inline-stat">Skill {skills.length}</span>
          <span className="platform-inline-stat">版本 {versions.length}</span>
          <span className="platform-inline-stat">策略包 {policyBundle ? `v${policyBundle.versionNo}` : "—"}</span>
        </div>
      </header>

      {error ? <div className="platform-console__banner platform-console__banner--error">{error}</div> : null}
      {message ? <div className="platform-console__banner platform-console__banner--success">{message}</div> : null}

      <div className="platform-console__grid">
        {policyBundle ? (
          <section className="platform-console__panel platform-console__panel--full">
            <div className="platform-console__stack">
              <div className="platform-console__section">
                <p className="platform-section-label">核心策略</p>
                <h3 className="platform-console__subheading">Core Policy Bundle</h3>
                <p className="skills-data-table__summary">
                  {policyBundle.bundleCode}@v{policyBundle.versionNo} · {policyBundle.publishStatus} · {policyBundle.versionCount} 个版本 · 最近更新时间 {formatTs(policyBundle.updatedAt)}
                </p>
                <div className="platform-console__stats">
                  <article className="platform-console__stat">
                    <span>运行中已发布 Agent</span>
                    <strong>{policyBundle.livePublishedAgentCount}</strong>
                  </article>
                  <article className="platform-console__stat">
                    <span>策略提示行数</span>
                    <strong>{policyBundle.promptLineCount}</strong>
                  </article>
                  <article className="platform-console__stat">
                    <span>兜底规则数</span>
                    <strong>{policyBundle.handoffRules.length}</strong>
                  </article>
                  <article className="platform-console__stat">
                    <span>待发布草稿</span>
                    <strong>{policyBundle.latestDraftVersionNo ?? "—"}</strong>
                  </article>
                </div>
                <div className="platform-console__badges">
                  {policyBundle.sourceSkillCodes.map((skillCode) => (
                    <span key={skillCode} className="skills-pill">{skillCode}</span>
                  ))}
                </div>
                {policyBundle.sampleAgentIds.length > 0 ? (
                  <div className="platform-console__badges platform-console__badges--compact">
                    {policyBundle.sampleAgentIds.map((agentId) => (
                      <span key={agentId} className="skills-pill">{agentId}</span>
                    ))}
                  </div>
                ) : null}
                <ul className="platform-console__summary-list">
                  <li>{policyBundle.rolloutHint}</li>
                  <li>{policyBundle.rollbackHint}</li>
                </ul>
              </div>

              <div className="platform-console__section">
                <p className="platform-section-label">版本编辑器</p>
                <h3 className="platform-console__subheading">新建策略版本</h3>
                <div className="platform-console__form-grid">
                  <label>
                    名称
                    <input
                      value={policyDraft.name}
                      onChange={(e) => setPolicyDraft((prev) => ({ ...prev, name: e.target.value }))}
                    />
                  </label>
                  <label>
                    Source Skill Codes
                    <input
                      value={policyDraft.sourceSkillCodes}
                      onChange={(e) => setPolicyDraft((prev) => ({ ...prev, sourceSkillCodes: e.target.value }))}
                      placeholder="conversation-core, knowledge-first, safe-handoff"
                    />
                  </label>
                  <label className="platform-console__field--full">
                    描述
                    <textarea
                      rows={2}
                      value={policyDraft.description}
                      onChange={(e) => setPolicyDraft((prev) => ({ ...prev, description: e.target.value }))}
                    />
                  </label>
                  <label className="platform-console__field--full">
                    Prompt Fragment
                    <textarea
                      rows={6}
                      value={policyDraft.promptFragment}
                      onChange={(e) => setPolicyDraft((prev) => ({ ...prev, promptFragment: e.target.value }))}
                    />
                  </label>
                  <label className="platform-console__field--full">
                    Handoff Rules
                    <textarea
                      rows={4}
                      value={policyDraft.handoffRules}
                      onChange={(e) => setPolicyDraft((prev) => ({ ...prev, handoffRules: e.target.value }))}
                      placeholder={"每行一条兜底规则"}
                    />
                  </label>
                </div>
                <div className="platform-console__actions">
                  <button className="dify-btn dify-btn--primary" disabled={saving} onClick={() => void savePolicyDraft()}>
                    {saving ? "处理中…" : "保存为新策略草稿"}
                  </button>
                </div>
              </div>

              <div className="platform-console__section">
                <p className="platform-section-label">历史记录</p>
                <h3 className="platform-console__subheading">策略版本历史</h3>
                <div className="skills-table-wrap">
                  <table className="skills-data-table">
                    <thead>
                      <tr>
                        <th>版本</th>
                        <th>状态</th>
                        <th>影响</th>
                        <th>时间</th>
                        <th>动作</th>
                      </tr>
                    </thead>
                    <tbody>
                      {policyBundleVersions.map((version) => {
                        const isCurrent = version.versionNo === policyBundle.versionNo;
                        const action = version.versionNo < policyBundle.versionNo ? "rollback" : "publish";
                        return (
                          <tr key={version.id}>
                            <td className="skills-data-table__mono">v{version.versionNo}</td>
                            <td>{version.publishStatus}</td>
                            <td className="skills-data-table__summary">
                              <div>{version.description || "—"}</div>
                              {version.impact?.summaryLines?.slice(0, 2).map((line) => (
                                <div key={`${version.id}-${line}`} className="platform-console__inline-note">{line}</div>
                              ))}
                            </td>
                            <td className="skills-data-table__mono">{formatTs(version.publishedAt || version.createdAt)}</td>
                            <td className="skills-data-table__actions">
                              <div className="platform-console__badges platform-console__badges--compact">
                                <span className="skills-pill">{version.impact?.rolloutStage ?? "UNKNOWN"}</span>
                                <span className="skills-pill">{version.impact?.livePublishedAgentCount ?? 0} 个 Agent</span>
                              </div>
                              <button
                                className="dify-btn dify-btn--ghost"
                                onClick={() => setPolicyDraft(policyVersionToDraft(version))}
                              >
                                装载到编辑器
                              </button>
                              {!isCurrent ? (
                                <button
                                  className="dify-btn dify-btn--primary"
                                  disabled={saving}
                                  onClick={() => void applyPolicyVersion(version.versionNo, action)}
                                >
                                  {action === "rollback" ? "回滚到此版本" : "发布此版本"}
                                </button>
                              ) : (
                                <span className="skills-pill">当前生效</span>
                              )}
                              {version.impact?.sampleAgentIds?.length ? (
                                <div className="platform-console__badges platform-console__badges--compact">
                                  {version.impact.sampleAgentIds.map((agentId) => (
                                    <span key={`${version.id}-${agentId}`} className="skills-pill">{agentId}</span>
                                  ))}
                                </div>
                              ) : null}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          </section>
        ) : null}

        <section className="platform-console__panel skills-table-wrap">
          <div className="platform-panel__intro">
            <p className="platform-section-label">标准技能列表</p>
            <p className="skills-data-table__summary">选择左侧标准技能后，在右侧查看治理配置、影响摘要和版本历史。</p>
          </div>
          <table className="skills-data-table">
            <thead>
              <tr>
                <th>Skill</th>
                <th>当前模板</th>
                <th>派生</th>
                <th>绑定 Agent</th>
                <th>治理</th>
              </tr>
            </thead>
            <tbody>
              {skills.map((skill) => (
                <tr
                  key={skill.id}
                  className={skill.id === selectedSkillId ? "platform-console__row--active" : ""}
                  onClick={() => setSelectedSkillId(skill.id)}
                >
                  <td>
                    <div className="skills-data-table__skill-name">{skill.name}</div>
                    <div className="skills-data-table__skill-code">{skill.skillCode}</div>
                  </td>
                  <td className="skills-data-table__mono">
                    v{skill.currentTemplateVersionNo ?? 1}
                    <br />
                    <span className="subtle">{skill.versionCount} 个版本</span>
                  </td>
                  <td>{skill.derivedSkillCount}</td>
                  <td>{skill.agentBindingCount}</td>
                  <td>
                    <div className="skills-data-table__flags">
                      <span className="skills-pill">{skill.visibility}</span>
                      <span className="skills-pill">{skill.bindingPolicy}</span>
                      <span className="skills-pill">{skill.enabled ? "ENABLED" : "DISABLED"}</span>
                    </div>
                  </td>
                </tr>
              ))}
              {!loading && skills.length === 0 ? (
                <tr>
                  <td colSpan={5} className="skills-data-table__summary">
                    当前还没有平台标准技能。
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </section>

        <section className="platform-console__panel">
          {selectedSkill ? (
            <div className="platform-console__stack">
              <div className="platform-console__section">
                <p className="platform-section-label">当前技能</p>
                <h2 className="platform-console__heading">{selectedSkill.name}</h2>
                <p className="skills-data-table__summary">
                  模板 `{selectedSkill.templateCode}` · 当前版本 v{selectedSkill.currentTemplateVersionNo ?? 1} · 最近更新时间 {formatTs(selectedSkill.updatedAt)}
                </p>
                <div className="platform-console__badges">
                  <span className="skills-pill">{selectedSkill.visibility}</span>
                  <span className="skills-pill">{selectedSkill.bindingPolicy}</span>
                  <span className="skills-pill">{selectedSkill.enabled ? "ENABLED" : "DISABLED"}</span>
                </div>
              </div>

              <div className="platform-console__section">
                <p className="platform-section-label">治理配置</p>
                <h3 className="platform-console__subheading">治理设置</h3>
                <div className="platform-console__form-grid">
                  <label>
                    可见性
                    <select
                      value={governance.visibility}
                      onChange={(e) => setGovernance((prev) => ({ ...prev, visibility: e.target.value }))}
                    >
                      <option value="VISIBLE">VISIBLE</option>
                      <option value="HIDDEN">HIDDEN</option>
                    </select>
                  </label>
                  <label>
                    绑定策略
                    <select
                      value={governance.bindingPolicy}
                      onChange={(e) => setGovernance((prev) => ({ ...prev, bindingPolicy: e.target.value }))}
                    >
                      <option value="OPTIONAL">OPTIONAL</option>
                      <option value="DEFAULT_ON">DEFAULT_ON</option>
                      <option value="MANDATORY">MANDATORY</option>
                    </select>
                  </label>
                  <label className="platform-console__checkbox">
                    <input
                      type="checkbox"
                      checked={governance.enabled}
                      onChange={(e) => setGovernance((prev) => ({ ...prev, enabled: e.target.checked }))}
                    />
                    对租户启用
                  </label>
                </div>
              </div>

              {selectedSkill.impact ? (
                <div className="platform-console__section">
                  <p className="platform-section-label">影响范围</p>
                  <h3 className="platform-console__subheading">影响摘要</h3>
                  <div className="platform-console__stats">
                    <article className="platform-console__stat">
                      <span>绑定 Agent</span>
                      <strong>{selectedSkill.impact.boundAgentCount}</strong>
                    </article>
                    <article className="platform-console__stat">
                      <span>已发布 Workflow</span>
                      <strong>{selectedSkill.impact.publishedWorkflowCount}</strong>
                    </article>
                    <article className="platform-console__stat">
                      <span>当前版本命中</span>
                      <strong>{selectedSkill.impact.currentVersionPinnedWorkflowCount}</strong>
                    </article>
                    <article className="platform-console__stat">
                      <span>历史版本缓冲</span>
                      <strong>{selectedSkill.impact.historicalPinnedWorkflowCount}</strong>
                    </article>
                  </div>
                  {selectedSkill.impact.sampleAgentIds.length > 0 ? (
                    <div className="platform-console__badges">
                      {selectedSkill.impact.sampleAgentIds.map((agentId) => (
                        <span key={agentId} className="skills-pill">{agentId}</span>
                      ))}
                    </div>
                  ) : null}
                  <ul className="platform-console__summary-list">
                    <li>{selectedSkill.impact.rolloutHint}</li>
                    <li>{selectedSkill.impact.rollbackHint}</li>
                  </ul>
                </div>
              ) : null}

              <div className="platform-console__section">
                <p className="platform-section-label">模板编辑器</p>
                <h3 className="platform-console__subheading">新建模板版本</h3>
                <div className="platform-console__form-grid">
                  <label>
                    名称
                    <input value={draft.name} onChange={(e) => setDraft((prev) => ({ ...prev, name: e.target.value }))} />
                  </label>
                  <label>
                    风险等级
                    <select
                      value={draft.riskLevel}
                      onChange={(e) => setDraft((prev) => ({ ...prev, riskLevel: e.target.value }))}
                    >
                      <option value="LOW">LOW</option>
                      <option value="MEDIUM">MEDIUM</option>
                      <option value="HIGH">HIGH</option>
                    </select>
                  </label>
                  <label className="platform-console__field--full">
                    描述
                    <textarea
                      rows={2}
                      value={draft.description}
                      onChange={(e) => setDraft((prev) => ({ ...prev, description: e.target.value }))}
                    />
                  </label>
                  <label className="platform-console__field--full">
                    Prompt Fragment
                    <textarea
                      rows={8}
                      value={draft.promptFragment}
                      onChange={(e) => setDraft((prev) => ({ ...prev, promptFragment: e.target.value }))}
                    />
                  </label>
                  <label>
                    Tool Whitelist
                    <input
                      value={draft.toolWhitelist}
                      onChange={(e) => setDraft((prev) => ({ ...prev, toolWhitelist: e.target.value }))}
                      placeholder="a, b, c"
                    />
                  </label>
                  <label>
                    KB Whitelist
                    <input
                      value={draft.kbWhitelist}
                      onChange={(e) => setDraft((prev) => ({ ...prev, kbWhitelist: e.target.value }))}
                      placeholder="kb-1, kb-2"
                    />
                  </label>
                  <label className="platform-console__field--full">
                    Handoff Rule
                    <textarea
                      rows={2}
                      value={draft.handoffRule}
                      onChange={(e) => setDraft((prev) => ({ ...prev, handoffRule: e.target.value }))}
                    />
                  </label>
                  <label className="platform-console__field--full">
                    Output Contract
                    <textarea
                      rows={2}
                      value={draft.outputContract}
                      onChange={(e) => setDraft((prev) => ({ ...prev, outputContract: e.target.value }))}
                    />
                  </label>
                  <label className="platform-console__field--full">
                    Changelog
                    <textarea
                      rows={2}
                      value={draft.changelog}
                      onChange={(e) => setDraft((prev) => ({ ...prev, changelog: e.target.value }))}
                    />
                  </label>
                </div>
                <div className="platform-console__actions">
                  <button className="dify-btn dify-btn--primary" disabled={saving} onClick={() => void saveDraft()}>
                    {saving ? "处理中…" : "保存为新草稿版本"}
                  </button>
                </div>
              </div>

              <div className="platform-console__section">
                <p className="platform-section-label">历史记录</p>
                <h3 className="platform-console__subheading">版本历史</h3>
                <div className="skills-table-wrap">
                  <table className="skills-data-table">
                    <thead>
                      <tr>
                        <th>版本</th>
                        <th>状态</th>
                        <th>变更</th>
                        <th>时间</th>
                        <th>动作</th>
                      </tr>
                    </thead>
                    <tbody>
                      {versions.map((version) => {
                        const isCurrent = version.versionNo === selectedSkill.currentTemplateVersionNo;
                        const action = version.versionNo < (selectedSkill.currentTemplateVersionNo ?? 0) ? "rollback" : "publish";
                        return (
                          <tr key={version.id}>
                            <td className="skills-data-table__mono">v{version.versionNo}</td>
                            <td>{version.publishStatus}</td>
                            <td className="skills-data-table__summary">
                              <div>{version.changelog || version.description || "—"}</div>
                              {version.impact?.summaryLines?.slice(0, 2).map((line) => (
                                <div key={`${version.id}-${line}`} className="platform-console__inline-note">{line}</div>
                              ))}
                            </td>
                            <td className="skills-data-table__mono">{formatTs(version.publishedAt || version.createdAt)}</td>
                            <td className="skills-data-table__actions">
                              <div className="platform-console__badges platform-console__badges--compact">
                                <span className="skills-pill">{version.impact?.rolloutStage ?? "UNKNOWN"}</span>
                                <span className="skills-pill">{version.impact?.pinnedWorkflowCount ?? 0} 个 workflow</span>
                              </div>
                              <button
                                className="dify-btn dify-btn--ghost"
                                onClick={() => setDraft(versionToDraft(version))}
                              >
                                装载到编辑器
                              </button>
                              {!isCurrent ? (
                                <button
                                  className="dify-btn dify-btn--primary"
                                  disabled={saving}
                                  onClick={() => void applyVersion(version.versionNo, action)}
                                >
                                  {action === "rollback" ? "回滚到此版本" : "发布此版本"}
                                </button>
                              ) : (
                                <span className="skills-pill">当前生效</span>
                              )}
                              {version.impact?.sampleAgentIds?.length ? (
                                <div className="platform-console__badges platform-console__badges--compact">
                                  {version.impact.sampleAgentIds.map((agentId) => (
                                    <span key={`${version.id}-${agentId}`} className="skills-pill">{agentId}</span>
                                  ))}
                                </div>
                              ) : null}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          ) : (
            <p className="skills-data-table__summary">请选择一个平台标准技能。</p>
          )}
        </section>
      </div>
    </div>
  );
}
