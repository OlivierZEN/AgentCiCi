import { useEffect, useMemo, useState } from "react";
import { LS_ADMIN_TOKEN } from "../../constants";
import { useAdminToken } from "../useAdminToken";

type ExportJobSummary = {
  id: number;
  status: string;
  reason?: string;
  createdAt?: string;
  finishedAt?: string;
  updatedAt?: string;
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
};

type OrganizationForm = Pick<
  OrganizationProfile,
  "name" | "shortName" | "contactName" | "contactPhone" | "contactEmail" | "website" | "industry" | "organizationSize" | "timezone" | "notes"
>;

const emptyForm: OrganizationForm = {
  name: "",
  shortName: "",
  contactName: "",
  contactPhone: "",
  contactEmail: "",
  website: "",
  industry: "",
  organizationSize: "",
  timezone: "Asia/Shanghai",
  notes: "",
};

const organizationSizes = ["1-10", "11-50", "51-200", "201-500", "501-1000", "1000+"];
const timezones = ["Asia/Shanghai", "Asia/Hong_Kong", "Asia/Singapore", "Asia/Tokyo", "UTC", "America/Los_Angeles", "Europe/London"];

function asForm(profile: OrganizationProfile | null): OrganizationForm {
  if (!profile) return emptyForm;
  return {
    name: profile.name ?? "",
    shortName: profile.shortName ?? "",
    contactName: profile.contactName ?? "",
    contactPhone: profile.contactPhone ?? "",
    contactEmail: profile.contactEmail ?? "",
    website: profile.website ?? "",
    industry: profile.industry ?? "",
    organizationSize: profile.organizationSize ?? "",
    timezone: profile.timezone || "Asia/Shanghai",
    notes: profile.notes ?? "",
  };
}

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

export default function AdminOrganizationPage() {
  const token = useAdminToken();
  const [profile, setProfile] = useState<OrganizationProfile | null>(null);
  const [form, setForm] = useState<OrganizationForm>(emptyForm);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [copied, setCopied] = useState(false);

  const isDirty = useMemo(() => JSON.stringify(form) !== JSON.stringify(asForm(profile)), [form, profile]);

  const loadProfile = async () => {
    setLoading(true);
    setError("");
    try {
      const res = await fetch("/admin/organization/profile", { headers: { Authorization: `Bearer ${token}` } });
      const json = await res.json();
      if (!res.ok || !json.success) {
        setError(json.message ?? "组织资料加载失败");
        return;
      }
      const data = json.data as OrganizationProfile;
      setProfile(data);
      setForm(asForm(data));
    } catch {
      setError("组织资料加载失败");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadProfile();
  }, [token]);

  const updateField = (field: keyof OrganizationForm, value: string) => {
    setForm((prev) => ({ ...prev, [field]: value }));
    setNotice("");
    setError("");
  };

  const saveProfile = async () => {
    setSaving(true);
    setNotice("");
    setError("");
    try {
      const res = await fetch("/admin/organization/profile", {
        method: "PATCH",
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
        body: JSON.stringify(form),
      });
      const json = await res.json();
      if (!res.ok || !json.success) {
        setError(json.message ?? "组织资料保存失败");
        return;
      }
      const data = json.data as OrganizationProfile;
      setProfile(data);
      setForm(asForm(data));
      setNotice("组织资料已保存");
      try {
        const raw = localStorage.getItem(LS_ADMIN_TOKEN);
        const auth = raw ? JSON.parse(raw) : null;
        if (auth?.orgId === data.orgId) {
          localStorage.setItem(LS_ADMIN_TOKEN, JSON.stringify({ ...auth, orgName: data.name }));
        }
      } catch {
        // token metadata refresh is best effort only
      }
      window.dispatchEvent(
        new CustomEvent("admin-organization-profile-updated", {
          detail: { orgId: data.orgId, name: data.name, shortName: data.shortName },
        }),
      );
    } catch {
      setError("组织资料保存失败");
    } finally {
      setSaving(false);
    }
  };

  const createExportJob = async () => {
    setExporting(true);
    setNotice("");
    setError("");
    try {
      const res = await fetch("/admin/organization/export-jobs", {
        method: "POST",
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
        body: JSON.stringify({ reason: "组织设置页创建的数据导出" }),
      });
      const json = await res.json();
      if (!res.ok || !json.success) {
        setError(json.message ?? "创建导出任务失败");
        return;
      }
      setNotice("数据导出任务已创建");
      await loadProfile();
    } catch {
      setError("创建导出任务失败");
    } finally {
      setExporting(false);
    }
  };

  const copyOrgId = async () => {
    if (!profile?.orgId) return;
    await navigator.clipboard.writeText(profile.orgId);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1600);
  };

  const resetForm = () => {
    setForm(asForm(profile));
    setNotice("");
    setError("");
  };

  const exportJobs = profile?.recentExportJobs ?? [];

  return (
    <div className="admin-page admin-organization-page">
      <header className="admin-organization-header">
        <div>
          <h1>组织设置</h1>
          <p className="subtle">维护组织展示名称和基础资料，系统标识保持只读。</p>
        </div>
        {(notice || error) && <p className={`admin-organization-feedback${error ? " is-error" : ""}`}>{error || notice}</p>}
      </header>

      <div className="admin-organization-layout">
        <section className="admin-organization-panel" aria-label="组织资料表单">
          {loading ? (
            <div className="admin-organization-skeleton" aria-label="正在加载组织资料">
              {Array.from({ length: 9 }).map((_, index) => (
                <span key={index} />
              ))}
            </div>
          ) : (
            <>
              <div className="admin-organization-section">
                <h2>基本信息</h2>
                <div className="admin-organization-form-grid">
                  <label>
                    <span>组织名称</span>
                    <input value={form.name} onChange={(event) => updateField("name", event.target.value)} maxLength={128} />
                  </label>
                  <label>
                    <span>组织简称</span>
                    <input value={form.shortName} onChange={(event) => updateField("shortName", event.target.value)} maxLength={64} />
                  </label>
                  <label>
                    <span>行业</span>
                    <input value={form.industry} onChange={(event) => updateField("industry", event.target.value)} maxLength={128} />
                  </label>
                  <label>
                    <span>组织规模</span>
                    <select value={form.organizationSize} onChange={(event) => updateField("organizationSize", event.target.value)}>
                      <option value="">未设置</option>
                      {organizationSizes.map((size) => (
                        <option key={size} value={size}>
                          {size}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label>
                    <span>官网</span>
                    <input value={form.website} onChange={(event) => updateField("website", event.target.value)} placeholder="https://example.com" maxLength={256} />
                  </label>
                  <label>
                    <span>时区</span>
                    <select value={form.timezone} onChange={(event) => updateField("timezone", event.target.value)}>
                      {timezones.map((timezone) => (
                        <option key={timezone} value={timezone}>
                          {timezone}
                        </option>
                      ))}
                    </select>
                  </label>
                </div>
              </div>

              <div className="admin-organization-section">
                <h2>联系信息</h2>
                <div className="admin-organization-form-grid">
                  <label>
                    <span>联系人</span>
                    <input value={form.contactName} onChange={(event) => updateField("contactName", event.target.value)} maxLength={128} />
                  </label>
                  <label>
                    <span>联系电话</span>
                    <input value={form.contactPhone} onChange={(event) => updateField("contactPhone", event.target.value)} maxLength={64} />
                  </label>
                  <label>
                    <span>联系邮箱</span>
                    <input value={form.contactEmail} onChange={(event) => updateField("contactEmail", event.target.value)} maxLength={256} />
                  </label>
                </div>
              </div>

              <div className="admin-organization-section">
                <h2>备注</h2>
                <label className="admin-organization-notes">
                  <span>管理员内部备注</span>
                  <textarea value={form.notes} onChange={(event) => updateField("notes", event.target.value)} rows={4} maxLength={4000} />
                </label>
              </div>

              <div className="admin-organization-actions">
                <button type="button" className="secondary" onClick={resetForm} disabled={!isDirty || saving}>
                  取消
                </button>
                <button type="button" className="admin-organization-primary" onClick={() => void saveProfile()} disabled={!isDirty || saving}>
                  {saving ? "保存中" : "保存"}
                </button>
              </div>
            </>
          )}
        </section>

        <aside className="admin-organization-panel admin-organization-summary" aria-label="组织摘要">
          <div className="admin-organization-summary__head">
            <h2>只读摘要</h2>
            <span>{profile ? statusLabel(profile.status) : "加载中"}</span>
          </div>

          <dl className="admin-organization-facts">
            <div>
              <dt>组织 ID</dt>
              <dd>
                <code>{profile?.orgId ?? "..."}</code>
                <button type="button" className="admin-organization-text-action" onClick={() => void copyOrgId()} disabled={!profile?.orgId}>
                  {copied ? "已复制" : "复制"}
                </button>
              </dd>
            </div>
            <div>
              <dt>Owner</dt>
              <dd>{profile?.owner?.displayName || "暂无"}</dd>
            </div>
            <div>
              <dt>成员数</dt>
              <dd>{profile?.memberCount ?? 0}</dd>
            </div>
            <div>
              <dt>资料创建</dt>
              <dd>{formatDateTime(profile?.createdAt)}</dd>
            </div>
            <div>
              <dt>最近更新</dt>
              <dd>{formatDateTime(profile?.updatedAt)}</dd>
            </div>
          </dl>

          <div className="admin-organization-export">
            <div className="admin-organization-export__head">
              <h2>最近数据导出</h2>
              <button type="button" className="admin-organization-text-action" onClick={() => void createExportJob()} disabled={exporting}>
                {exporting ? "创建中" : "创建导出"}
              </button>
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
        </aside>
      </div>
    </div>
  );
}
