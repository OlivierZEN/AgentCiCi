import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Tenant,
  TenantProvisionPayload,
  createTenant,
  fetchTenantList,
  jobLabel,
  readPlatformToken,
  statusLabel,
} from "./platformTenantsShared";

type ProvisionFormState = {
  tenantName: string;
  ownerMobile: string;
  ownerDisplayName: string;
  ownerEmail: string;
  initialPassword: string;
  provisionNote: string;
};

const EMPTY_FORM: ProvisionFormState = {
  tenantName: "",
  ownerMobile: "",
  ownerDisplayName: "",
  ownerEmail: "",
  initialPassword: "",
  provisionNote: "",
};

export default function PlatformTenantsPage() {
  const token = readPlatformToken();
  const navigate = useNavigate();
  const [tenants, setTenants] = useState<Tenant[]>([]);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [provisionModalOpen, setProvisionModalOpen] = useState(false);
  const [form, setForm] = useState<ProvisionFormState>(EMPTY_FORM);

  useEffect(() => {
    if (!token) return;
    void loadTenants().catch((err) => setError(err instanceof Error ? err.message : "加载租户失败"));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  async function loadTenants() {
    setError("");
    const rows = await fetchTenantList(token);
    setTenants(rows);
  }

  function openProvisionModal() {
    setForm(EMPTY_FORM);
    setError("");
    setProvisionModalOpen(true);
  }

  async function submitProvision() {
    setBusy(true);
    setError("");
    try {
      const payload: TenantProvisionPayload = {
        tenantName: form.tenantName,
        ownerMobile: form.ownerMobile,
        ownerDisplayName: form.ownerDisplayName || null,
        ownerEmail: form.ownerEmail || null,
        initialPassword: form.initialPassword || null,
        provisionNote: form.provisionNote || null,
      };
      const created = await createTenant(token, payload);
      setProvisionModalOpen(false);
      setMessage(created.reusedExistingAccount ? "新租户已开通，已复用既有 Owner 账号。" : "新租户已开通。");
      navigate(`/platform/tenants/${created.orgId}`, { state: { flash: "新租户已开通" } });
    } catch (err) {
      setError(err instanceof Error ? err.message : "开通新租户失败");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="admin-page skills-catalog platform-page platform-tenants-page">
      <header className="skills-catalog__header platform-page-head">
        <div className="platform-page-head__main">
          <h1 className="skills-catalog__title">租户生命周期</h1>
          <p className="subtle skills-catalog__subtitle">按租户进入独立治理页，处理保留策略、导出、预演与销毁动作。</p>
        </div>
        <div className="platform-page-head__aside">
          <span className="platform-inline-stat">租户 {tenants.length}</span>
          <span className="platform-inline-stat">冻结 {tenants.filter((tenant) => tenant.status === "SUSPENDED").length}</span>
          <button type="button" className="platform-button platform-button--primary" onClick={openProvisionModal}>
            开通新租户
          </button>
        </div>
      </header>

      {error ? <div className="platform-console__banner platform-console__banner--error">{error}</div> : null}
      {message ? <div className="platform-console__banner platform-console__banner--success">{message}</div> : null}

      <div className="tenant-lifecycle tenant-lifecycle--list">
        <section className="platform-console__panel skills-table-wrap tenant-lifecycle__list" aria-label="租户列表">
          <div className="tenant-lifecycle-list__panel-head">
            <div>
              <p className="platform-section-label">租户目录</p>
            </div>
          </div>
          <table className="skills-data-table tenant-lifecycle__table">
            <colgroup>
              <col className="tenant-lifecycle__col-org" />
              <col className="tenant-lifecycle__col-status" />
              <col className="tenant-lifecycle__col-members" />
              <col className="tenant-lifecycle__col-job" />
            </colgroup>
            <thead>
              <tr>
                <th>租户</th>
                <th>状态</th>
                <th>成员</th>
                <th>最近生命周期记录</th>
              </tr>
            </thead>
            <tbody>
              {tenants.map((tenant) => (
                <tr
                  key={tenant.orgId}
                  tabIndex={0}
                  onClick={() => navigate(`/platform/tenants/${tenant.orgId}`)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter" || event.key === " ") {
                      event.preventDefault();
                      navigate(`/platform/tenants/${tenant.orgId}`);
                    }
                  }}
                >
                  <td>
                    <div className="skills-data-table__skill-name">{tenant.name}</div>
                    <div className="skills-data-table__skill-code">{tenant.orgId}</div>
                  </td>
                  <td>
                    <span className={`tenant-lifecycle__status tenant-lifecycle__status--${tenant.status.toLowerCase()}`}>
                      {statusLabel(tenant.status)}
                    </span>
                  </td>
                  <td className="skills-data-table__mono">{tenant.memberCount}</td>
                  <td className="skills-data-table__summary">
                    {tenant.latestJob ? `${jobLabel(tenant.latestJob.status)} · ${tenant.latestJob.totalRows ?? 0} 行` : "尚未生成"}
                  </td>
                </tr>
              ))}
              {tenants.length === 0 ? (
                <tr>
                  <td colSpan={4} className="skills-data-table__summary">当前还没有可管理租户。</td>
                </tr>
              ) : null}
            </tbody>
          </table>
          <div className="tenant-lifecycle-mobile-list">
            {tenants.map((tenant) => (
              <button
                key={`${tenant.orgId}-mobile`}
                type="button"
                className="tenant-lifecycle-mobile-list__row"
                onClick={() => navigate(`/platform/tenants/${tenant.orgId}`)}
              >
                <div className="tenant-lifecycle-mobile-list__head">
                  <div>
                    <div className="skills-data-table__skill-name">{tenant.name}</div>
                    <div className="skills-data-table__skill-code">{tenant.orgId}</div>
                  </div>
                  <span className={`tenant-lifecycle__status tenant-lifecycle__status--${tenant.status.toLowerCase()}`}>
                    {statusLabel(tenant.status)}
                  </span>
                </div>
                <div className="tenant-lifecycle-mobile-list__meta">
                  <div>
                    <span>成员</span>
                    <strong>{tenant.memberCount}</strong>
                  </div>
                  <div>
                    <span>最近记录</span>
                    <strong>{tenant.latestJob ? `${jobLabel(tenant.latestJob.status)} · ${tenant.latestJob.totalRows ?? 0} 行` : "尚未生成"}</strong>
                  </div>
                </div>
              </button>
            ))}
            {tenants.length === 0 ? <p className="skills-data-table__summary">当前还没有可管理租户。</p> : null}
          </div>
        </section>
      </div>

      {provisionModalOpen ? (
        <div className="tenant-lifecycle__modal-backdrop" role="presentation">
          <div className="tenant-lifecycle__modal tenant-lifecycle__modal--provision" role="dialog" aria-modal="true" aria-labelledby="tenant-provision-title">
            <div className="tenant-lifecycle__modal-head">
              <div>
                <p className="platform-section-label">Tenant Provisioning</p>
                <h2 id="tenant-provision-title" className="platform-console__heading">开通新租户</h2>
              </div>
              <button type="button" className="tenant-lifecycle__modal-close" onClick={() => setProvisionModalOpen(false)} aria-label="关闭">
                ×
              </button>
            </div>
            <div className="tenant-lifecycle__modal-body">
              <p className="skills-data-table__summary">填写必要信息后即可直接开通租户；若手机号已绑定账号，系统会复用该账号并补充租户 Owner 关系。</p>
              <div className="platform-console__form-grid tenant-lifecycle__provision-form">
                <label>
                  <span>租户名称</span>
                  <input value={form.tenantName} onChange={(event) => setForm((prev) => ({ ...prev, tenantName: event.target.value }))} placeholder="华东售后中心" />
                </label>
                <label>
                  <span>Owner 手机号</span>
                  <input value={form.ownerMobile} onChange={(event) => setForm((prev) => ({ ...prev, ownerMobile: event.target.value }))} placeholder="13800138000" />
                </label>
                <label>
                  <span>Owner 显示名称</span>
                  <input value={form.ownerDisplayName} onChange={(event) => setForm((prev) => ({ ...prev, ownerDisplayName: event.target.value }))} placeholder="张三" />
                </label>
                <label>
                  <span>Owner 邮箱</span>
                  <input value={form.ownerEmail} onChange={(event) => setForm((prev) => ({ ...prev, ownerEmail: event.target.value }))} placeholder="zhangsan@example.com" />
                </label>
                <label className="tenant-lifecycle__field--full">
                  <span>初始密码</span>
                  <input
                    type="password"
                    value={form.initialPassword}
                    onChange={(event) => setForm((prev) => ({ ...prev, initialPassword: event.target.value }))}
                    placeholder="仅首次创建全局账号时需要"
                  />
                  <small className="tenant-lifecycle__field-help">若手机号已存在，全局账号将被复用，原密码不会被覆盖。</small>
                </label>
                <label className="tenant-lifecycle__field--full">
                  <span>平台开通备注</span>
                  <input value={form.provisionNote} onChange={(event) => setForm((prev) => ({ ...prev, provisionNote: event.target.value }))} placeholder="平台人工开通" />
                </label>
              </div>
            </div>
            <div className="tenant-lifecycle__modal-foot">
              <button type="button" className="platform-button platform-button--secondary" onClick={() => setProvisionModalOpen(false)}>
                取消
              </button>
              <button
                type="button"
                className="platform-button platform-button--primary"
                onClick={() => void submitProvision()}
                disabled={busy || !form.tenantName.trim() || !/^1\d{10}$/.test(form.ownerMobile.trim())}
              >
                确认开通
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}
