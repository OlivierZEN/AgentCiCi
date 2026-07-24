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

type CompanyUsageSummary = {
  activeUserCount: number;
  createdUserCount: number;
  knowledgeBaseCount: number;
  knowledgeDocumentCount: number;
  skillCount: number;
  agentCount: number;
  publishedAgentCount: number;
  exportJobCount: number;
};

type CompanyProfile = {
  companyId: string;
  name: string;
  shortName?: string;
  status: string;
  contactName?: string;
  contactPhone?: string;
  contactEmail?: string;
  website?: string;
  industry?: string;
  companySize?: string;
  timezone?: string;
  notes?: string;
  owner?: { memberId: string; displayName: string; mobile?: string } | null;
  memberCount: number;
  createdAt?: string | null;
  updatedAt?: string | null;
  updatedBy?: string;
  recentExportJobs?: ExportJobSummary[];
  usageSummary?: CompanyUsageSummary;
};

type CompanyProfileForm = {
  name: string;
  shortName: string;
  contactName: string;
  contactPhone: string;
  contactEmail: string;
  website: string;
  industry: string;
  companySize: string;
  timezone: string;
  notes: string;
};

type ProfileField = {
  label: string;
  value: string;
  mono?: boolean;
  action?: "copy-org-id";
};

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

function profileToForm(profile: CompanyProfile): CompanyProfileForm {
  return {
    name: profile.name || "",
    shortName: profile.shortName || "",
    contactName: profile.contactName || "",
    contactPhone: profile.contactPhone || "",
    contactEmail: profile.contactEmail || "",
    website: profile.website || "",
    industry: profile.industry || "",
    companySize: profile.companySize || "",
    timezone: profile.timezone || "Asia/Shanghai",
    notes: profile.notes || "",
  };
}

export default function AdminCompanyPage() {
  const token = useAdminToken();
  const [profile, setProfile] = useState<CompanyProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [copied, setCopied] = useState(false);
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState<CompanyProfileForm | null>(null);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState("");

  const usage = profile?.usageSummary;

  const fields = useMemo<ProfileField[]>(
    () => [
      { label: "组织名称", value: formatText(profile?.name) },
      { label: "组织 ID", value: profile?.companyId ?? "暂无", mono: true, action: "copy-org-id" },
      { label: "联系人", value: formatText(profile?.contactName) },
      { label: "联系电话", value: formatText(profile?.contactPhone) },
      { label: "联系邮箱", value: formatText(profile?.contactEmail) },
      { label: "官网", value: formatText(profile?.website) },
      { label: "行业", value: formatText(profile?.industry) },
      { label: "组织规模", value: formatText(profile?.companySize) },
      { label: "时区", value: formatText(profile?.timezone || "Asia/Shanghai") },
    ],
    [profile],
  );

  const fieldColumns = useMemo(
    () => [fields.slice(0, 3), fields.slice(3, 6), fields.slice(6, 9)],
    [fields],
  );

  const usageItems = [
    { label: "已创建用户", value: formatNumber(usage?.createdUserCount), hint: `活跃 ${formatNumber(usage?.activeUserCount)}` },
    { label: "知识库", value: formatNumber(usage?.knowledgeBaseCount), hint: `文档 ${formatNumber(usage?.knowledgeDocumentCount)}` },
    { label: "技能", value: formatNumber(usage?.skillCount), hint: "启用中" },
    { label: "智能体", value: formatNumber(usage?.agentCount), hint: `已发布 ${formatNumber(usage?.publishedAgentCount)}` },
    { label: "组织成员", value: formatNumber(profile?.memberCount), hint: statusLabel(profile?.status) },
    { label: "数据导出", value: formatNumber(usage?.exportJobCount), hint: "暂无记录" },
  ];

  useEffect(() => {
    let ignore = false;
    const loadProfile = async () => {
      setLoading(true);
      setError("");
      try {
        const res = await fetch("/admin/company/profile", { headers: { Authorization: `Bearer ${token}` } });
        const json = await res.json();
        if (!res.ok || !json.success) {
          if (!ignore) setError(json.message ?? "组织简档加载失败");
          return;
        }
        if (!ignore) {
          setProfile(json.data as CompanyProfile);
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

  const copyCompanyId = async () => {
    if (!profile?.companyId) return;
    await navigator.clipboard.writeText(profile.companyId);
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

  const updateForm = (field: keyof CompanyProfileForm, value: string) => {
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
      const res = await fetch("/admin/company/profile", {
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
      const nextProfile = json.data as CompanyProfile;
      setProfile(nextProfile);
      setForm(profileToForm(nextProfile));
      setEditing(false);
      setSuccess("组织信息已更新");
      window.dispatchEvent(
        new CustomEvent("admin-company-profile-updated", {
          detail: { companyId: nextProfile.companyId, name: nextProfile.name },
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
    <div className="admin-page admin-company-page">
      <header className="admin-company-header">
        <div>
          <h1>组织简档</h1>
          <p className="subtle">查看当前组织的基础信息、系统标识和使用情况。</p>
        </div>
        <div className="admin-company-header__actions">
          {!loading && profile ? (
            <button type="button" className="admin-company-edit-button" onClick={openEditor}>
              编辑
            </button>
          ) : null}
          {error && <p className="admin-company-feedback is-error">{error}</p>}
          {success && <p className="admin-company-feedback">{success}</p>}
        </div>
      </header>

      {loading ? (
        <section className="admin-company-panel" aria-label="正在加载组织简档">
          <div className="admin-company-skeleton">
            {Array.from({ length: 12 }).map((_, index) => (
              <span key={index} />
            ))}
          </div>
        </section>
      ) : (
        <>
          <section className="admin-company-panel admin-company-panel--profile" aria-label="组织信息">
            <div className="admin-company-section admin-company-section--intro">
              <h2>{formatText(profile?.name)}</h2>
            </div>
            <dl className="admin-company-profile-list">
              {fieldColumns.map((column, index) => (
                <div className="admin-company-profile-column" key={index}>
                  {column.map((field) => (
                    <div className="admin-company-profile-item" key={field.label}>
                      <dt>{field.label}</dt>
                      <dd>
                        <span className={field.mono ? "admin-company-mono" : undefined}>{field.value}</span>
                        {field.action === "copy-org-id" ? (
                          <button type="button" className="admin-company-text-action" onClick={() => void copyCompanyId()} disabled={!profile?.companyId}>
                            {copied ? "已复制" : "复制"}
                          </button>
                        ) : null}
                      </dd>
                    </div>
                  ))}
                </div>
              ))}
            </dl>
          </section>

          <section className="admin-company-panel admin-company-panel--usage" aria-label="组织使用情况汇总">
            <div className="admin-company-section admin-company-section--intro">
              <h2>使用情况汇总</h2>
            </div>
            <div className="admin-company-usage-grid">
              {usageItems.map((item) => (
                <div key={item.label} className="admin-company-usage-item">
                  <span>{item.label}</span>
                  <strong>{item.value}</strong>
                  <small>{item.hint}</small>
                </div>
              ))}
            </div>
          </section>
        </>
      )}

      {editing && form && profile ? (
        <div
          className="admin-company-modal-backdrop"
          role="presentation"
          onClick={(event) => {
            if (event.target === event.currentTarget) closeEditor();
          }}
        >
          <form
            className="admin-company-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="admin-company-edit-title"
            onSubmit={(event) => void saveProfile(event)}
          >
            <div className="admin-company-modal__head">
              <div>
                <h2 id="admin-company-edit-title">编辑组织信息</h2>
                <p>组织 ID 保持只读，保存后会同步左侧组织名称。</p>
              </div>
              <button
                type="button"
                className="admin-company-modal__close"
                aria-label="关闭组织信息编辑"
                onClick={closeEditor}
                disabled={saving}
              >
                ×
              </button>
            </div>

            <div className="admin-company-modal__body">
              {saveError && <p className="admin-company-modal__error">{saveError}</p>}
              <div className="admin-company-modal__readonly">
                <span>组织 ID</span>
                <strong className="admin-company-mono">{profile.companyId}</strong>
              </div>
              <div className="admin-company-form-grid">
                <label className="admin-company-field">
                  <span>组织名称</span>
                  <input
                    value={form.name}
                    onChange={(event) => updateForm("name", event.target.value)}
                    maxLength={128}
                    autoFocus
                    required
                  />
                </label>
                <label className="admin-company-field">
                  <span>组织简称</span>
                  <input value={form.shortName} onChange={(event) => updateForm("shortName", event.target.value)} maxLength={64} />
                </label>
                <label className="admin-company-field">
                  <span>联系人</span>
                  <input value={form.contactName} onChange={(event) => updateForm("contactName", event.target.value)} maxLength={128} />
                </label>
                <label className="admin-company-field">
                  <span>联系电话</span>
                  <input value={form.contactPhone} onChange={(event) => updateForm("contactPhone", event.target.value)} maxLength={64} />
                </label>
                <label className="admin-company-field">
                  <span>联系邮箱</span>
                  <input
                    type="email"
                    value={form.contactEmail}
                    onChange={(event) => updateForm("contactEmail", event.target.value)}
                    maxLength={256}
                  />
                </label>
                <label className="admin-company-field">
                  <span>官网</span>
                  <input
                    type="url"
                    value={form.website}
                    onChange={(event) => updateForm("website", event.target.value)}
                    maxLength={256}
                    placeholder="https://example.com"
                  />
                </label>
                <label className="admin-company-field">
                  <span>行业</span>
                  <input value={form.industry} onChange={(event) => updateForm("industry", event.target.value)} maxLength={128} />
                </label>
                <label className="admin-company-field">
                  <span>组织规模</span>
                  <input value={form.companySize} onChange={(event) => updateForm("companySize", event.target.value)} maxLength={64} />
                </label>
                <label className="admin-company-field">
                  <span>时区</span>
                  <input value={form.timezone} onChange={(event) => updateForm("timezone", event.target.value)} maxLength={64} />
                </label>
              </div>
              <label className="admin-company-field admin-company-field--wide">
                <span>备注</span>
                <textarea value={form.notes} onChange={(event) => updateForm("notes", event.target.value)} maxLength={4000} rows={4} />
              </label>
            </div>

            <div className="admin-company-modal__foot">
              <button type="button" className="admin-company-modal__secondary" onClick={closeEditor} disabled={saving}>
                取消
              </button>
              <button type="submit" className="admin-company-modal__primary" disabled={!canSave}>
                {saving ? "保存中" : "保存"}
              </button>
            </div>
          </form>
        </div>
      ) : null}
    </div>
  );
}
