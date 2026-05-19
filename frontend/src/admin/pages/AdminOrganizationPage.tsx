import { type FormEvent, useEffect, useMemo, useState } from "react";
import { useAdminToken } from "../useAdminToken";

type ExportJobSummary = {
  id: number;
  status: string;
  reason?: string;
  createdAt?: string;
  finishedAt?: string;
  updatedAt?: string;
};

type OrganizationUsageSummary = {
  activeUserCount: number;
  createdUserCount: number;
  knowledgeBaseCount: number;
  knowledgeDocumentCount: number;
  skillCount: number;
  agentCount: number;
  publishedAgentCount: number;
  exportJobCount: number;
};

type OrganizationProfile = {
  orgId: string;
  name: string;
  shortName?: string;
  status: string;
  contactName?: string;
  contactPhone?: string;
  contactEmail?: string;
  website?: string;
  industry?: string;
  organizationSize?: string;
  timezone?: string;
  notes?: string;
  owner?: { memberId: string; displayName: string; mobile?: string } | null;
  memberCount: number;
  createdAt?: string | null;
  updatedAt?: string | null;
  updatedBy?: string;
  recentExportJobs?: ExportJobSummary[];
  usageSummary?: OrganizationUsageSummary;
};

type OrganizationProfileForm = {
  name: string;
  shortName: string;
  contactName: string;
  contactPhone: string;
  contactEmail: string;
  website: string;
  industry: string;
  organizationSize: string;
  timezone: string;
  notes: string;
};

type ProfileField = {
  label: string;
  value: string;
  mono?: boolean;
  action?: "copy-org-id";
};

function formatDateTime(value?: string | null): string {
  if (!value) return "暂无";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  })
    .format(date)
    .replace(/\//g, "-");
}

function formatText(value?: string | null): string {
  const normalized = value?.trim();
  return normalized ? normalized : "暂无";
}

function formatNumber(value?: number): string {
  return new Intl.NumberFormat("zh-CN").format(value ?? 0);
}

function statusLabel(status?: string): string {
  if (status === "ACTIVE") return "正常";
  if (status === "SUSPENDED") return "已冻结";
  if (status === "PENDING_PURGE") return "待销毁";
  if (status === "PURGED") return "已销毁";
  return status || "未知";
}

function exportStatusLabel(status: string): string {
  if (status === "SUCCEEDED") return "已完成";
  if (status === "RUNNING") return "生成中";
  if (status === "FAILED") return "失败";
  return status;
}

function profileToForm(profile: OrganizationProfile): OrganizationProfileForm {
  return {
    name: profile.name || "",
    shortName: profile.shortName || "",
    contactName: profile.contactName || "",
    contactPhone: profile.contactPhone || "",
    contactEmail: profile.contactEmail || "",
    website: profile.website || "",
    industry: profile.industry || "",
    organizationSize: profile.organizationSize || "",
    timezone: profile.timezone || "Asia/Shanghai",
    notes: profile.notes || "",
  };
}

export default function AdminOrganizationPage() {
  const token = useAdminToken();
  const [profile, setProfile] = useState<OrganizationProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [copied, setCopied] = useState(false);
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState<OrganizationProfileForm | null>(null);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState("");

  const usage = profile?.usageSummary;
  const exportJobs = profile?.recentExportJobs ?? [];

  const fields = useMemo<ProfileField[]>(
    () => [
      { label: "组织名称", value: formatText(profile?.name) },
      { label: "组织简称", value: formatText(profile?.shortName) },
      { label: "组织 ID", value: profile?.orgId ?? "暂无", mono: true, action: "copy-org-id" },
      { label: "当前状态", value: statusLabel(profile?.status) },
      { label: "Owner", value: profile?.owner?.displayName || "暂无" },
      { label: "Owner 手机", value: formatText(profile?.owner?.mobile) },
      { label: "联系人", value: formatText(profile?.contactName) },
      { label: "联系电话", value: formatText(profile?.contactPhone) },
      { label: "联系邮箱", value: formatText(profile?.contactEmail) },
      { label: "官网", value: formatText(profile?.website) },
      { label: "行业", value: formatText(profile?.industry) },
      { label: "组织规模", value: formatText(profile?.organizationSize) },
      { label: "时区", value: formatText(profile?.timezone || "Asia/Shanghai") },
      { label: "资料创建", value: formatDateTime(profile?.createdAt) },
      { label: "最近更新", value: formatDateTime(profile?.updatedAt) },
      { label: "更新人", value: formatText(profile?.updatedBy) },
    ],
    [profile],
  );

  const usageItems = [
    { label: "已创建用户", value: formatNumber(usage?.createdUserCount), hint: `活跃 ${formatNumber(usage?.activeUserCount)}` },
    { label: "知识库", value: formatNumber(usage?.knowledgeBaseCount), hint: `文档 ${formatNumber(usage?.knowledgeDocumentCount)}` },
    { label: "技能", value: formatNumber(usage?.skillCount), hint: "启用中" },
    { label: "智能体", value: formatNumber(usage?.agentCount), hint: `已发布 ${formatNumber(usage?.publishedAgentCount)}` },
    { label: "组织成员", value: formatNumber(profile?.memberCount), hint: statusLabel(profile?.status) },
    { label: "数据导出", value: formatNumber(usage?.exportJobCount), hint: exportJobs.length ? `最近 ${exportJobs.length} 条` : "暂无记录" },
  ];

  useEffect(() => {
    let ignore = false;
    const loadProfile = async () => {
      setLoading(true);
      setError("");
      try {
        const res = await fetch("/admin/organization/profile", { headers: { Authorization: `Bearer ${token}` } });
        const json = await res.json();
        if (!res.ok || !json.success) {
          if (!ignore) setError(json.message ?? "组织简档加载失败");
          return;
        }
        if (!ignore) {
          setProfile(json.data as OrganizationProfile);
          setSuccess("");
        }
      } catch {
        if (!ignore) setError("组织简档加载失败");
      } finally {
        if (!ignore) setLoading(false);
      }
    };
    void loadProfile();
    return () => {
      ignore = true;
    };
  }, [token]);

  useEffect(() => {
    if (!editing) return;
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !saving) {
        setEditing(false);
        setSaveError("");
      }
    };
    document.addEventListener("keydown", closeOnEscape);
    return () => document.removeEventListener("keydown", closeOnEscape);
  }, [editing, saving]);

  const copyOrgId = async () => {
    if (!profile?.orgId) return;
    await navigator.clipboard.writeText(profile.orgId);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1600);
  };

  const openEditor = () => {
    if (!profile) return;
    setForm(profileToForm(profile));
    setSaveError("");
    setSuccess("");
    setEditing(true);
  };

  const closeEditor = () => {
    if (saving) return;
    setEditing(false);
    setSaveError("");
  };

  const updateForm = (field: keyof OrganizationProfileForm, value: string) => {
    setForm((current) => (current ? { ...current, [field]: value } : current));
  };

  const saveProfile = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!form) return;
    setSaving(true);
    setSaveError("");
    setError("");
    setSuccess("");
    try {
      const res = await fetch("/admin/organization/profile", {
        method: "PATCH",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify(form),
      });
      const json = await res.json();
      if (!res.ok || !json.success) {
        setSaveError(json.message ?? "组织信息保存失败");
        return;
      }
      const nextProfile = json.data as OrganizationProfile;
      setProfile(nextProfile);
      setForm(profileToForm(nextProfile));
      setEditing(false);
      setSuccess("组织信息已更新");
      window.dispatchEvent(
        new CustomEvent("admin-organization-profile-updated", {
          detail: { orgId: nextProfile.orgId, name: nextProfile.name },
        }),
      );
    } catch {
      setSaveError("组织信息保存失败");
    } finally {
      setSaving(false);
    }
  };

  const canSave = Boolean(form && form.name.trim().length >= 2) && !saving;

  return (
    <div className="admin-page admin-organization-page">
      <header className="admin-organization-header">
        <div>
          <h1>组织简档</h1>
          <p className="subtle">查看当前组织的基础信息、系统标识和使用情况。</p>
        </div>
        <div className="admin-organization-header__actions">
          {!loading && profile ? (
            <button type="button" className="admin-organization-edit-button" onClick={openEditor}>
              编辑
            </button>
          ) : null}
          {error && <p className="admin-organization-feedback is-error">{error}</p>}
          {success && <p className="admin-organization-feedback">{success}</p>}
        </div>
      </header>

      {loading ? (
        <section className="admin-organization-panel" aria-label="正在加载组织简档">
          <div className="admin-organization-skeleton">
            {Array.from({ length: 12 }).map((_, index) => (
              <span key={index} />
            ))}
          </div>
        </section>
      ) : (
        <>
          <section className="admin-organization-panel" aria-label="组织信息">
            <div className="admin-organization-section admin-organization-section--intro">
              <h2>{formatText(profile?.name)}</h2>
              <span>{statusLabel(profile?.status)}</span>
            </div>
            <dl className="admin-organization-profile-grid">
              {fields.map((field) => (
                <div key={field.label}>
                  <dt>{field.label}</dt>
                  <dd>
                    <span className={field.mono ? "admin-organization-mono" : undefined}>{field.value}</span>
                    {field.action === "copy-org-id" ? (
                      <button type="button" className="admin-organization-text-action" onClick={() => void copyOrgId()} disabled={!profile?.orgId}>
                        {copied ? "已复制" : "复制"}
                      </button>
                    ) : null}
                  </dd>
                </div>
              ))}
            </dl>
            <div className="admin-organization-notes-readonly">
              <h2>备注</h2>
              <p>{formatText(profile?.notes)}</p>
            </div>
          </section>

          <section className="admin-organization-panel" aria-label="组织使用情况汇总">
            <div className="admin-organization-section admin-organization-section--intro">
              <h2>使用情况汇总</h2>
              <span>当前组织</span>
            </div>
            <div className="admin-organization-usage-grid">
              {usageItems.map((item) => (
                <div key={item.label} className="admin-organization-usage-item">
                  <span>{item.label}</span>
                  <strong>{item.value}</strong>
                  <small>{item.hint}</small>
                </div>
              ))}
            </div>
            <div className="admin-organization-export">
              <div className="admin-organization-export__head">
                <h2>最近数据导出</h2>
              </div>
              {exportJobs.length === 0 ? (
                <p className="admin-organization-empty">暂无导出记录</p>
              ) : (
                <ol className="admin-organization-export-list">
                  {exportJobs.map((job) => (
                    <li key={job.id}>
                      <span>{exportStatusLabel(job.status)}</span>
                      <strong>#{job.id}</strong>
                      <time>{formatDateTime(job.finishedAt || job.updatedAt || job.createdAt)}</time>
                    </li>
                  ))}
                </ol>
              )}
            </div>
          </section>
        </>
      )}

      {editing && form && profile ? (
        <div
          className="admin-organization-modal-backdrop"
          role="presentation"
          onClick={(event) => {
            if (event.target === event.currentTarget) closeEditor();
          }}
        >
          <form
            className="admin-organization-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="admin-organization-edit-title"
            onSubmit={(event) => void saveProfile(event)}
          >
            <div className="admin-organization-modal__head">
              <div>
                <h2 id="admin-organization-edit-title">编辑组织信息</h2>
                <p>组织 ID 保持只读，保存后会同步左侧组织名称。</p>
              </div>
              <button
                type="button"
                className="admin-organization-modal__close"
                aria-label="关闭组织信息编辑"
                onClick={closeEditor}
                disabled={saving}
              >
                ×
              </button>
            </div>

            <div className="admin-organization-modal__body">
              {saveError && <p className="admin-organization-modal__error">{saveError}</p>}
              <div className="admin-organization-modal__readonly">
                <span>组织 ID</span>
                <strong className="admin-organization-mono">{profile.orgId}</strong>
              </div>
              <div className="admin-organization-form-grid">
                <label className="admin-organization-field">
                  <span>组织名称</span>
                  <input
                    value={form.name}
                    onChange={(event) => updateForm("name", event.target.value)}
                    maxLength={128}
                    autoFocus
                    required
                  />
                </label>
                <label className="admin-organization-field">
                  <span>组织简称</span>
                  <input value={form.shortName} onChange={(event) => updateForm("shortName", event.target.value)} maxLength={64} />
                </label>
                <label className="admin-organization-field">
                  <span>联系人</span>
                  <input value={form.contactName} onChange={(event) => updateForm("contactName", event.target.value)} maxLength={128} />
                </label>
                <label className="admin-organization-field">
                  <span>联系电话</span>
                  <input value={form.contactPhone} onChange={(event) => updateForm("contactPhone", event.target.value)} maxLength={64} />
                </label>
                <label className="admin-organization-field">
                  <span>联系邮箱</span>
                  <input
                    type="email"
                    value={form.contactEmail}
                    onChange={(event) => updateForm("contactEmail", event.target.value)}
                    maxLength={256}
                  />
                </label>
                <label className="admin-organization-field">
                  <span>官网</span>
                  <input
                    type="url"
                    value={form.website}
                    onChange={(event) => updateForm("website", event.target.value)}
                    maxLength={256}
                    placeholder="https://example.com"
                  />
                </label>
                <label className="admin-organization-field">
                  <span>行业</span>
                  <input value={form.industry} onChange={(event) => updateForm("industry", event.target.value)} maxLength={128} />
                </label>
                <label className="admin-organization-field">
                  <span>组织规模</span>
                  <input value={form.organizationSize} onChange={(event) => updateForm("organizationSize", event.target.value)} maxLength={64} />
                </label>
                <label className="admin-organization-field">
                  <span>时区</span>
                  <input value={form.timezone} onChange={(event) => updateForm("timezone", event.target.value)} maxLength={64} />
                </label>
              </div>
              <label className="admin-organization-field admin-organization-field--wide">
                <span>备注</span>
                <textarea value={form.notes} onChange={(event) => updateForm("notes", event.target.value)} maxLength={4000} rows={4} />
              </label>
            </div>

            <div className="admin-organization-modal__foot">
              <button type="button" className="admin-organization-modal__secondary" onClick={closeEditor} disabled={saving}>
                取消
              </button>
              <button type="submit" className="admin-organization-modal__primary" disabled={!canSave}>
                {saving ? "保存中" : "保存"}
              </button>
            </div>
          </form>
        </div>
      ) : null}
    </div>
  );
}
