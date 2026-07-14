import { useCallback, useEffect, useMemo, useState } from "react";
import AvatarView from "../components/AvatarView";
import AvatarCropperDialog from "../components/AvatarCropperDialog";
import { getDisplayInitial, readAvatarFileAsDataUrl } from "../shared/avatar";
import CommunicationChannelBinding from "./CommunicationChannelBinding";
import MyWorkflowStudio from "./MyWorkflowStudio";
import UserMemoryPanel from "./UserMemoryPanel";
import ThemePreferencePanel from "../theme/ThemePreferencePanel";
import { applyProductTheme } from "../theme/theme";

type Props = {
  open: boolean;
  token: string;
  onClose?: () => void;
  variant?: "modal" | "page";
  surface?: "settings" | "profile";
  title?: string;
};

type MeProfile = {
  userId?: string;
  nickname?: string;
  mobile?: string;
  firstName?: string;
  lastName?: string;
  displayName?: string;
  email?: string;
  avatarBase64?: string;
  themeCode?: string;
};

type SettingsTab = "workflow" | "channels" | "email" | "memory" | "appearance";
type ProfileTab = "info" | "password";

type ProfileForm = {
  firstName: string;
  lastName: string;
  displayName: string;
  mobile: string;
  email: string;
};

type PasswordForm = {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
};

type ProviderPreset = {
  code: string;
  displayLabel: string;
  pop3: { host: string | null; port: number; ssl: boolean };
  smtp: { host: string | null; port: number; sslMode: string };
  defaultAuthType: string;
};

type AccountPayload = {
  id: number;
  providerCode: string;
  displayName?: string;
  emailAddress: string;
  loginUsername: string;
  authType: string;
  pop3: { host: string; port: number; ssl: boolean };
  smtp: { host: string; port: number; sslMode: string };
  requireSendConfirm: boolean;
  enabled: boolean;
  lastVerifiedAt?: string | null;
  lastVerifyError?: string | null;
};

type AccountForm = {
  providerCode: string;
  emailAddress: string;
  loginUsername: string;
  displayName: string;
  authType: string;
  secret: string;
  pop3Host: string;
  pop3Port: string;
  pop3Ssl: boolean;
  smtpHost: string;
  smtpPort: string;
  smtpSslMode: string;
  requireSendConfirm: boolean;
  enabled: boolean;
};

type ApiResponse<T> = { success: boolean; data?: T; message?: string };



const LOCAL_PROVIDER_FALLBACKS: Record<string, ProviderPreset> = {
  aliyun_mail: {
    code: "aliyun_mail",
    displayLabel: "阿里云企业邮箱",
    pop3: { host: "pop.qiye.aliyun.com", port: 995, ssl: true },
    smtp: { host: "smtp.qiye.aliyun.com", port: 465, sslMode: "ssl" },
    defaultAuthType: "password",
  },
  hotmail: {
    code: "hotmail",
    displayLabel: "Hotmail / Outlook",
    pop3: { host: "outlook.office365.com", port: 995, ssl: true },
    smtp: { host: "smtp-mail.outlook.com", port: 587, sslMode: "starttls" },
    defaultAuthType: "app_password",
  },
  gmail: {
    code: "gmail",
    displayLabel: "Gmail",
    pop3: { host: "pop.gmail.com", port: 995, ssl: true },
    smtp: { host: "smtp.gmail.com", port: 465, sslMode: "ssl" },
    defaultAuthType: "app_password",
  },
};

const DEFAULT_FORM: AccountForm = {
  providerCode: "gmail",
  emailAddress: "",
  loginUsername: "",
  displayName: "",
  authType: "app_password",
  secret: "",
  pop3Host: "",
  pop3Port: "",
  pop3Ssl: true,
  smtpHost: "",
  smtpPort: "",
  smtpSslMode: "ssl",
  requireSendConfirm: true,
  enabled: true,
};

async function fetchJson<T>(input: RequestInfo, init?: RequestInit): Promise<ApiResponse<T>> {
  const res = await fetch(input, init);
  try {
    return (await res.json()) as ApiResponse<T>;
  } catch {
    return { success: res.ok, message: `HTTP ${res.status}` };
  }
}

export default function MyEmailAccountsModal({ open, token, onClose, variant = "modal", surface = "settings", title }: Props) {
  const isProfileSurface = surface === "profile";
  const [tab, setTab] = useState<SettingsTab>("workflow");
  const [profileTab, setProfileTab] = useState<ProfileTab>("info");
  const [meProfile, setMeProfile] = useState<MeProfile>({});
  const [profileForm, setProfileForm] = useState<ProfileForm>({
    firstName: "",
    lastName: "",
    displayName: "",
    mobile: "",
    email: "",
  });
  const [passwordForm, setPasswordForm] = useState<PasswordForm>({
    currentPassword: "",
    newPassword: "",
    confirmPassword: "",
  });
  const [avatarPreview, setAvatarPreview] = useState("");
  const [providers, setProviders] = useState<ProviderPreset[]>([]);
  const [accounts, setAccounts] = useState<AccountPayload[]>([]);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<AccountForm>(DEFAULT_FORM);
  const [avatarCropSource, setAvatarCropSource] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);

  const headers = useMemo(
    () => ({ Authorization: `Bearer ${token}`, "Content-Type": "application/json" }),
    [token],
  );

  const refresh = useCallback(async () => {
    if (!token) return;
    setBusy(true);
    try {
      const meRes = await fetchJson<MeProfile>("/auth/me", { headers: { Authorization: `Bearer ${token}` } });
      if (meRes.success && meRes.data) {
        setMeProfile(meRes.data);
        applyProductTheme(meRes.data.themeCode);
        setAvatarPreview(meRes.data.avatarBase64 ?? "");
        setProfileForm({
          firstName: meRes.data.firstName ?? "",
          lastName: meRes.data.lastName ?? "",
          displayName: meRes.data.displayName ?? meRes.data.nickname ?? "",
          mobile: meRes.data.mobile ?? "",
          email: meRes.data.email ?? "",
        });
      }
      if (isProfileSurface) {
        return;
      }
      const [providersRes, listRes] = await Promise.all([
        fetchJson<ProviderPreset[]>("/me/email-accounts/providers", { headers }),
        fetchJson<AccountPayload[]>("/me/email-accounts", { headers }),
      ]);
      if (providersRes.success && providersRes.data) {
        setProviders(providersRes.data);
      }
      if (listRes.success && Array.isArray(listRes.data)) {
        setAccounts(listRes.data);
      } else if (!listRes.success) {
        setNotice(listRes.message ?? "加载邮箱列表失败");
      }
    } catch (error) {
      setNotice(`加载失败：${error instanceof Error ? error.message : String(error)}`);
    } finally {
      setBusy(false);
    }
  }, [token, headers, isProfileSurface]);

  useEffect(() => {
    if (open) {
      void refresh();
    }
  }, [open, refresh]);

  const applyPreset = (code: string) => {
    const preset = LOCAL_PROVIDER_FALLBACKS[code] ?? providers.find((p) => p.code === code);
    if (!preset) {
      setForm((current) => ({ ...current, providerCode: code }));
      return;
    }
    setForm((current) => ({
      ...current,
      providerCode: code,
      authType: preset.defaultAuthType || current.authType,
      pop3Host: preset.pop3.host ?? "",
      pop3Port: preset.pop3.port ? String(preset.pop3.port) : "",
      pop3Ssl: preset.pop3.ssl,
      smtpHost: preset.smtp.host ?? "",
      smtpPort: preset.smtp.port ? String(preset.smtp.port) : "",
      smtpSslMode: preset.smtp.sslMode || "ssl",
    }));
  };

  const resetForm = () => {
    setEditingId(null);
    setForm(DEFAULT_FORM);
    setNotice("");
  };

  const beginEdit = (item: AccountPayload) => {
    setEditingId(item.id);
    setForm({
      providerCode: item.providerCode,
      emailAddress: item.emailAddress,
      loginUsername: item.loginUsername ?? item.emailAddress,
      displayName: item.displayName ?? "",
      authType: item.authType,
      secret: "",
      pop3Host: item.pop3.host,
      pop3Port: String(item.pop3.port),
      pop3Ssl: item.pop3.ssl,
      smtpHost: item.smtp.host,
      smtpPort: String(item.smtp.port),
      smtpSslMode: item.smtp.sslMode,
      requireSendConfirm: item.requireSendConfirm,
      enabled: item.enabled,
    });
    setNotice("修改时 密码 字段留空将保留原有凭据。");
  };

  const buildPayload = () => {
    const trim = (s: string) => s.trim();
    const parsePort = (value: string): number | null => {
      if (!value.trim()) return null;
      const n = Number(value);
      return Number.isFinite(n) ? n : null;
    };
    return {
      providerCode: form.providerCode,
      emailAddress: trim(form.emailAddress),
      loginUsername: trim(form.loginUsername) || trim(form.emailAddress),
      displayName: trim(form.displayName),
      authType: form.authType,
      secret: form.secret,
      pop3Host: trim(form.pop3Host) || null,
      pop3Port: parsePort(form.pop3Port),
      pop3Ssl: form.pop3Ssl,
      smtpHost: trim(form.smtpHost) || null,
      smtpPort: parsePort(form.smtpPort),
      smtpSslMode: form.smtpSslMode,
      requireSendConfirm: form.requireSendConfirm,
      enabled: form.enabled,
    };
  };

  const submit = async () => {
    setBusy(true);
    setNotice("");
    try {
      const payload = buildPayload();
      if (!payload.emailAddress) {
        setNotice("请填写邮箱地址");
        return;
      }
      if (editingId == null && !payload.secret) {
        setNotice("请填写密码或 App Password");
        return;
      }
      const url = editingId == null ? "/me/email-accounts" : `/me/email-accounts/${editingId}`;
      const method = editingId == null ? "POST" : "PUT";
      const res = await fetchJson<AccountPayload>(url, {
        method,
        headers,
        body: JSON.stringify(payload),
      });
      if (!res.success) {
        setNotice(res.message ?? "保存失败");
        return;
      }
      setNotice(editingId == null ? "邮箱已添加" : "邮箱配置已更新");
      resetForm();
      await refresh();
    } finally {
      setBusy(false);
    }
  };

  const verify = async (id: number) => {
    setBusy(true);
    setNotice("");
    try {
      const res = await fetchJson<{ success: boolean; message: string }>(
        `/me/email-accounts/${id}/verify`,
        { method: "POST", headers },
      );
      if (!res.success) {
        setNotice(res.message ?? "连接测试失败");
        return;
      }
      const payload = res.data ?? { success: false, message: "" };
      setNotice(payload.success ? `连接成功：${payload.message || "OK"}` : `连接失败：${payload.message}`);
      await refresh();
    } finally {
      setBusy(false);
    }
  };

  const remove = async (id: number) => {
    if (!window.confirm("确认删除此邮箱配置？")) return;
    setBusy(true);
    setNotice("");
    try {
      const res = await fetchJson(`/me/email-accounts/${id}`, { method: "DELETE", headers });
      if (!res.success) {
        setNotice(res.message ?? "删除失败");
        return;
      }
      setNotice("已删除");
      if (editingId === id) resetForm();
      await refresh();
    } finally {
      setBusy(false);
    }
  };

  const saveMyAvatar = async () => {
    if (!token) return;
    setBusy(true);
    setNotice("");
    try {
      const res = await fetchJson<MeProfile>("/auth/me/avatar", {
        method: "PUT",
        headers,
        body: JSON.stringify({ avatarBase64: avatarPreview }),
      });
      if (!res.success || !res.data) {
        setNotice(res.message ?? "头像保存失败，请稍后重试");
        return;
      }
      setMeProfile(res.data);
      setAvatarPreview(res.data.avatarBase64 ?? "");
      setNotice("头像已更新");
      window.dispatchEvent(
        new CustomEvent("assistant-current-user-updated", {
          detail: {
            userId: res.data.userId,
            mobile: res.data.mobile,
            nickname: res.data.displayName ?? res.data.nickname,
            displayName: res.data.displayName,
            avatarBase64: res.data.avatarBase64,
          },
        }),
      );
    } finally {
      setBusy(false);
    }
  };

  const saveProfile = async () => {
    if (!token) return;
    setBusy(true);
    setNotice("");
    try {
      const res = await fetchJson<MeProfile>("/auth/me/profile", {
        method: "PUT",
        headers,
        body: JSON.stringify(profileForm),
      });
      if (!res.success || !res.data) {
        setNotice(res.message ?? "个人信息保存失败");
        return;
      }
      setMeProfile(res.data);
      setProfileForm({
        firstName: res.data.firstName ?? "",
        lastName: res.data.lastName ?? "",
        displayName: res.data.displayName ?? res.data.nickname ?? "",
        mobile: res.data.mobile ?? "",
        email: res.data.email ?? "",
      });
      setNotice("个人信息已更新");
      window.dispatchEvent(
        new CustomEvent("assistant-current-user-updated", {
          detail: {
            userId: res.data.userId,
            mobile: res.data.mobile,
            nickname: res.data.displayName ?? res.data.nickname,
            displayName: res.data.displayName,
            avatarBase64: res.data.avatarBase64,
          },
        }),
      );
    } finally {
      setBusy(false);
    }
  };

  const changePassword = async () => {
    if (!token) return;
    setNotice("");
    if (passwordForm.newPassword !== passwordForm.confirmPassword) {
      setNotice("两次输入的新密码不一致");
      return;
    }
    setBusy(true);
    try {
      const res = await fetchJson<{ updated: boolean }>("/auth/me/password", {
        method: "PUT",
        headers,
        body: JSON.stringify({
          currentPassword: passwordForm.currentPassword,
          newPassword: passwordForm.newPassword,
        }),
      });
      if (!res.success) {
        setNotice(res.message ?? "密码修改失败");
        return;
      }
      setPasswordForm({ currentPassword: "", newPassword: "", confirmPassword: "" });
      setNotice("密码已更新，下次登录请使用新密码");
    } finally {
      setBusy(false);
    }
  };

  const beginAvatarCrop = async (file: File) => {
    try {
      const dataUrl = await readAvatarFileAsDataUrl(file);
      setAvatarCropSource(dataUrl);
      setNotice("");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "头像处理失败，请稍后重试");
    }
  };

  if (!open) return null;

  const profileInfoContent = (
    <>
      <p className="cici-modal__intro">头像和个人信息会用于工作台、会话消息和个人入口展示，仅你本人可修改。</p>
      {notice ? <div className="cici-modal__notice">{notice}</div> : null}
      <section className="cici-modal__section" aria-label="头像设置">
        <div className="cici-profile-avatar-block">
          <AvatarView
            src={avatarPreview}
            fallback={getDisplayInitial(profileForm.displayName || meProfile.nickname || meProfile.mobile || "我", "我")}
            className="cici-profile-avatar"
            alt="当前用户头像"
          />
          <div className="cici-profile-avatar-actions">
            <label className="cici-btn cici-btn--ghost cici-profile-avatar-upload">
              上传图片
              <input
                type="file"
                accept="image/png,image/jpeg,image/webp"
                onChange={(event) => {
                  const file = event.target.files?.[0];
                  event.currentTarget.value = "";
                  if (!file) return;
                  void beginAvatarCrop(file);
                }}
              />
            </label>
            <button type="button" className="cici-btn cici-btn--ghost" onClick={() => setAvatarPreview("")} disabled={busy}>
              清除头像
            </button>
            <button type="button" className="cici-btn cici-btn--primary" onClick={() => void saveMyAvatar()} disabled={busy}>
              保存头像
            </button>
          </div>
        </div>
      </section>

      <section className="cici-modal__section" aria-label="个人信息表单">
        <div className="cici-form-grid cici-profile-form">
          <label>
            <span>姓</span>
            <input value={profileForm.lastName} onChange={(e) => setProfileForm((c) => ({ ...c, lastName: e.target.value }))} />
          </label>
          <label>
            <span>名</span>
            <input value={profileForm.firstName} onChange={(e) => setProfileForm((c) => ({ ...c, firstName: e.target.value }))} />
          </label>
          <label>
            <span>显示名称</span>
            <input value={profileForm.displayName} onChange={(e) => setProfileForm((c) => ({ ...c, displayName: e.target.value }))} />
          </label>
          <label>
            <span>手机号</span>
            <input value={profileForm.mobile} onChange={(e) => setProfileForm((c) => ({ ...c, mobile: e.target.value }))} />
          </label>
          <label>
            <span>邮箱</span>
            <input type="email" value={profileForm.email} onChange={(e) => setProfileForm((c) => ({ ...c, email: e.target.value }))} />
          </label>
        </div>
        <footer className="cici-modal__footer">
          <button type="button" className="cici-btn cici-btn--primary" onClick={() => void saveProfile()} disabled={busy || !profileForm.mobile.trim()}>
            保存个人信息
          </button>
        </footer>
      </section>
    </>
  );

  const profilePasswordContent = (
    <>
      {notice ? <div className="cici-modal__notice">{notice}</div> : null}
      <section className="cici-modal__section">
        <header className="cici-modal__section-head">
          <h4>修改密码</h4>
        </header>
        <div className="cici-form-grid cici-profile-form">
          <label>
            <span>当前密码</span>
            <input
              type="password"
              autoComplete="current-password"
              value={passwordForm.currentPassword}
              onChange={(e) => setPasswordForm((c) => ({ ...c, currentPassword: e.target.value }))}
            />
          </label>
          <label>
            <span>新密码</span>
            <input
              type="password"
              autoComplete="new-password"
              value={passwordForm.newPassword}
              onChange={(e) => setPasswordForm((c) => ({ ...c, newPassword: e.target.value }))}
            />
          </label>
          <label>
            <span>确认新密码</span>
            <input
              type="password"
              autoComplete="new-password"
              value={passwordForm.confirmPassword}
              onChange={(e) => setPasswordForm((c) => ({ ...c, confirmPassword: e.target.value }))}
            />
          </label>
        </div>
        <footer className="cici-modal__footer">
          <button
            type="button"
            className="cici-btn cici-btn--primary"
            onClick={() => void changePassword()}
            disabled={busy || !passwordForm.currentPassword || !passwordForm.newPassword || !passwordForm.confirmPassword}
          >
            更新密码
          </button>
        </footer>
      </section>
    </>
  );

  const content = (
      <div
        className={`cici-modal cici-modal--wide${variant === "page" ? " cici-settings-page__panel" : ""}${isProfileSurface ? " cici-settings-page__panel--profile" : ""}`}
        role={variant === "modal" ? "dialog" : undefined}
        aria-modal={variant === "modal" ? "true" : undefined}
        aria-labelledby="cici-settings-title"
        onClick={variant === "modal" ? (e) => e.stopPropagation() : undefined}
      >
        <header className="cici-modal__header">
          <h3 id="cici-settings-title">{title ?? (isProfileSurface ? "个人简档" : "个人设置")}</h3>
          {variant === "modal" && onClose ? (
            <button type="button" className="cici-modal__close" onClick={onClose} aria-label="关闭">
              ×
            </button>
          ) : null}
        </header>
        {isProfileSurface ? (
          <div className="cici-settings-tabs cici-profile-tabs" role="tablist" aria-label="个人简档设置">
            <button
              type="button"
              className={`cici-settings-tabs__item${profileTab === "info" ? " is-active" : ""}`}
              onClick={() => setProfileTab("info")}
              role="tab"
              aria-selected={profileTab === "info"}
            >
              个人信息
            </button>
            <button
              type="button"
              className={`cici-settings-tabs__item${profileTab === "password" ? " is-active" : ""}`}
              onClick={() => setProfileTab("password")}
              role="tab"
              aria-selected={profileTab === "password"}
            >
              修改密码
            </button>
          </div>
        ) : (
          <div className="cici-settings-tabs">
          <button
            type="button"
            className={`cici-settings-tabs__item${tab === "workflow" ? " is-active" : ""}`}
            onClick={() => setTab("workflow")}
          >
            我的工作流
          </button>
          <button
            type="button"
            className={`cici-settings-tabs__item${tab === "channels" ? " is-active" : ""}`}
            onClick={() => setTab("channels")}
          >
            绑定沟通渠道
          </button>
          <button
            type="button"
            className={`cici-settings-tabs__item${tab === "email" ? " is-active" : ""}`}
            onClick={() => setTab("email")}
          >
            我的邮箱
          </button>
          <button
            type="button"
            className={`cici-settings-tabs__item${tab === "memory" ? " is-active" : ""}`}
            onClick={() => setTab("memory")}
          >
            专属记忆
          </button>
          <button
            type="button"
            className={`cici-settings-tabs__item${tab === "appearance" ? " is-active" : ""}`}
            onClick={() => setTab("appearance")}
          >
            界面主题
          </button>
          </div>
        )}

        <div className={`cici-settings-content${isProfileSurface ? " cici-settings-content--profile" : ""}`}>
          {isProfileSurface ? (
            profileTab === "info" ? profileInfoContent : profilePasswordContent
          ) : tab === "workflow" ? (
            <MyWorkflowStudio token={token} active={tab === "workflow"} />
          ) : tab === "channels" ? (
            <CommunicationChannelBinding token={token} active={tab === "channels"} />
          ) : tab === "memory" ? (
            <UserMemoryPanel token={token} agentId="cici-system" />
          ) : tab === "appearance" ? (
            <ThemePreferencePanel
              token={token}
              initialTheme={meProfile.themeCode}
              onSaved={(themeCode) => setMeProfile((current) => ({ ...current, themeCode }))}
            />
          ) : (
            <>
            <p className="cici-modal__intro">
              这里配置的邮箱账号将用于邮件工具（<code>email_*</code>）。智能体会在被授权使用邮件工具时以你本人的身份读取与发送邮件。仅保存 AES 加密后的密码。
            </p>

            {notice ? <div className="cici-modal__notice">{notice}</div> : null}

            <section className="cici-modal__section">
              <header className="cici-modal__section-head">
                <h4>已绑定邮箱</h4>
                <button type="button" className="cici-btn cici-btn--ghost" onClick={() => void refresh()} disabled={busy}>
                  刷新
                </button>
              </header>
              {accounts.length === 0 ? (
                <div className="cici-modal__empty">还没有邮箱，使用下面的表单添加一个。</div>
              ) : (
                <ul className="cici-email-list">
                  {accounts.map((item) => (
                    <li key={item.id} className="cici-email-list__item">
                      <div className="cici-email-list__main">
                        <div className="cici-email-list__title">
                          <strong>{item.emailAddress}</strong>
                          <span className="cici-email-list__tag">{item.providerCode}</span>
                          {!item.enabled ? <span className="cici-email-list__tag cici-email-list__tag--warn">已停用</span> : null}
                          {item.requireSendConfirm ? (
                            <span className="cici-email-list__tag">发送需确认</span>
                          ) : (
                            <span className="cici-email-list__tag cici-email-list__tag--danger">发送不确认</span>
                          )}
                        </div>
                        <div className="cici-email-list__meta">
                          POP3 {item.pop3.host}:{item.pop3.port} · SMTP {item.smtp.host}:{item.smtp.port} ({item.smtp.sslMode})
                        </div>
                        <div className="cici-email-list__meta">
                          {item.lastVerifiedAt ? `上次测试成功：${item.lastVerifiedAt}` : "未测试"}
                          {item.lastVerifyError ? ` · 最近错误：${item.lastVerifyError}` : ""}
                        </div>
                      </div>
                      <div className="cici-email-list__ops">
                        <button type="button" className="cici-btn cici-btn--ghost" onClick={() => void verify(item.id)} disabled={busy}>
                          测试连接
                        </button>
                        <button type="button" className="cici-btn cici-btn--ghost" onClick={() => beginEdit(item)} disabled={busy}>
                          编辑
                        </button>
                        <button type="button" className="cici-btn cici-btn--danger" onClick={() => void remove(item.id)} disabled={busy}>
                          删除
                        </button>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <section className="cici-modal__section">
              <header className="cici-modal__section-head">
                <h4>{editingId == null ? "添加邮箱" : `编辑邮箱 #${editingId}`}</h4>
                {editingId != null ? (
                  <button type="button" className="cici-btn cici-btn--ghost" onClick={resetForm} disabled={busy}>
                    取消编辑
                  </button>
                ) : null}
              </header>

              <div className="cici-form-grid cici-form-grid--email">
                <div className="cici-form-grid__divider">基础信息</div>
                <label>
                  <span>邮箱类型</span>
                  <select value={form.providerCode} onChange={(e) => applyPreset(e.target.value)}>
                    {providers.map((p) => (
                      <option key={p.code} value={p.code}>
                        {p.displayLabel}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  <span>邮箱地址</span>
                  <input
                    type="email"
                    value={form.emailAddress}
                    onChange={(e) => setForm((c) => ({ ...c, emailAddress: e.target.value }))}
                    placeholder="foo@example.com"
                  />
                </label>
                <label>
                  <span>登录名（默认同邮箱）</span>
                  <input
                    value={form.loginUsername}
                    onChange={(e) => setForm((c) => ({ ...c, loginUsername: e.target.value }))}
                    placeholder="默认与邮箱一致"
                  />
                </label>
                <label>
                  <span>发件显示名</span>
                  <input
                    value={form.displayName}
                    onChange={(e) => setForm((c) => ({ ...c, displayName: e.target.value }))}
                    placeholder="例如：Owen"
                  />
                </label>
                <label>
                  <span>密码 / App Password</span>
                  <input
                    type="password"
                    value={form.secret}
                    onChange={(e) => setForm((c) => ({ ...c, secret: e.target.value }))}
                    placeholder={editingId == null ? "必填" : "留空=不修改"}
                  />
                </label>
                <label>
                  <span>认证类型</span>
                  <select value={form.authType} onChange={(e) => setForm((c) => ({ ...c, authType: e.target.value }))}>
                    <option value="password">密码</option>
                    <option value="app_password">App Password</option>
                  </select>
                </label>

                <div className="cici-form-grid__divider">收信 POP3</div>
                <label>
                  <span>POP3 主机</span>
                  <input
                    value={form.pop3Host}
                    onChange={(e) => setForm((c) => ({ ...c, pop3Host: e.target.value }))}
                    placeholder="pop.example.com"
                  />
                </label>
                <label>
                  <span>POP3 端口</span>
                  <input
                    value={form.pop3Port}
                    onChange={(e) => setForm((c) => ({ ...c, pop3Port: e.target.value }))}
                    placeholder="995"
                  />
                </label>
                <label className="cici-form-grid__inline">
                  <input
                    type="checkbox"
                    checked={form.pop3Ssl}
                    onChange={(e) => setForm((c) => ({ ...c, pop3Ssl: e.target.checked }))}
                  />
                  <span>POP3 使用 SSL</span>
                </label>

                <div className="cici-form-grid__divider">发信 SMTP</div>
                <label>
                  <span>SMTP 主机</span>
                  <input
                    value={form.smtpHost}
                    onChange={(e) => setForm((c) => ({ ...c, smtpHost: e.target.value }))}
                    placeholder="smtp.example.com"
                  />
                </label>
                <label>
                  <span>SMTP 端口</span>
                  <input
                    value={form.smtpPort}
                    onChange={(e) => setForm((c) => ({ ...c, smtpPort: e.target.value }))}
                    placeholder="465"
                  />
                </label>
                <label>
                  <span>SMTP 加密方式</span>
                  <select
                    value={form.smtpSslMode}
                    onChange={(e) => setForm((c) => ({ ...c, smtpSslMode: e.target.value }))}
                  >
                    <option value="ssl">SSL</option>
                    <option value="starttls">STARTTLS</option>
                    <option value="plain">明文（不推荐）</option>
                  </select>
                </label>

                <div className="cici-form-grid__divider">发送策略</div>
                <label className="cici-form-grid__inline">
                  <input
                    type="checkbox"
                    checked={form.requireSendConfirm}
                    onChange={(e) => setForm((c) => ({ ...c, requireSendConfirm: e.target.checked }))}
                  />
                  <span>发送前需用户二次确认</span>
                </label>
                <label className="cici-form-grid__inline">
                  <input
                    type="checkbox"
                    checked={form.enabled}
                    onChange={(e) => setForm((c) => ({ ...c, enabled: e.target.checked }))}
                  />
                  <span>启用该账号</span>
                </label>
              </div>

              <footer className="cici-modal__footer">
                <button type="button" className="cici-btn cici-btn--ghost" onClick={resetForm} disabled={busy}>
                  重置
                </button>
                <button type="button" className="cici-btn cici-btn--primary" onClick={() => void submit()} disabled={busy}>
                  {editingId == null ? "添加邮箱" : "保存修改"}
                </button>
              </footer>
            </section>
            </>
          )}
        </div>

        <AvatarCropperDialog
          open={Boolean(avatarCropSource)}
          sourceDataUrl={avatarCropSource}
          title="裁剪我的头像"
          onCancel={() => setAvatarCropSource("")}
          onConfirm={async (avatarBase64) => {
            setAvatarPreview(avatarBase64);
            setAvatarCropSource("");
            setNotice("");
          }}
        />
      </div>
  );

  if (variant === "page") {
    return <main className="cici-settings-page">{content}</main>;
  }

  return (
    <div className="cici-modal-backdrop" onClick={onClose}>
      {content}
    </div>
  );
}
