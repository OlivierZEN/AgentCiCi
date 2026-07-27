import { useEffect, useState } from "react";
import { useAdminToken } from "../useAdminToken";
import { processAvatarFile } from "../../shared/avatar";
import { LS_ADMIN_TOKEN } from "../../constants";

const ROLE_OWNER = "OWNER";
const STATUS_SUSPENDED = "SUSPENDED";

type UserRow = {
  id: string;
  mobile: string;
  email?: string;
  roleCode: string;
  memberStatus?: string;
  createdAt: string;
  nickname?: string;
  ccUsername?: string;
  ccSafetymark?: string;
  avatarBase64?: string;
};

type AdminAuthPayload = {
  userId?: string;
  roles?: string[];
};

function readAdminAuth(): AdminAuthPayload | null {
  try {
    const raw = localStorage.getItem(LS_ADMIN_TOKEN);
    return raw ? (JSON.parse(raw) as AdminAuthPayload) : null;
  } catch {
    return null;
  }
}

function userRoleLabel(roleCode: string): string {
  return roleCode === ROLE_OWNER ? "组织创建者" : roleCode;
}

export default function AdminUsersPage() {
  const token = useAdminToken();
  const adminAuth = readAdminAuth();
  const [notice, setNotice] = useState("");
  const [users, setUsers] = useState<UserRow[]>([]);
  const [pending, setPending] = useState<Record<string, string>>({});
  const [selectedUserId, setSelectedUserId] = useState<string>("");
  const [keyword, setKeyword] = useState("");
  const [activeTab, setActiveTab] = useState<"basic" | "cloudcc">("basic");
  const [profileForm, setProfileForm] = useState<{ mobile: string; nickname: string; ccUsername: string; ccSafetymark: string }>({
    mobile: "",
    nickname: "",
    ccUsername: "",
    ccSafetymark: "",
  });
  const [inviteForm, setInviteForm] = useState({ mobile: "", email: "", nickname: "", roleCode: "ORG_USER" });
  const [inviteModalOpen, setInviteModalOpen] = useState(false);
  const [inviteSubmitting, setInviteSubmitting] = useState(false);
  const [avatarPreview, setAvatarPreview] = useState("");

  const load = async () => {
    setNotice("");
    const res = await fetch("/admin/users", { headers: { Authorization: `Bearer ${token}` } });
    const json = await res.json();
    if (!res.ok || !json.success) {
      setNotice(json.message ?? "加载失败");
      return;
    }
    const list = (json.data ?? []) as UserRow[];
    setUsers(list);
    if (!selectedUserId && list.length > 0) {
      setSelectedUserId(list[0].id);
    }
  };

  const setRoleLocal = (userId: string, roleCode: string) => {
    setPending((p) => ({ ...p, [userId]: roleCode }));
  };

  const saveRole = async (userId: string, options?: { reload?: boolean }) => {
    const reload = options?.reload ?? true;
    const roleCode = pending[userId];
    if (!roleCode) return;
    const res = await fetch(`/admin/users/${encodeURIComponent(userId)}/role`, {
      method: "PUT",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
      body: JSON.stringify({ roleCode }),
    });
    const json = await res.json();
    if (!res.ok || !json.success) {
      setNotice(json.message ?? "更新失败");
      return;
    }
    setNotice("角色已更新");
    setPending((p) => {
      const next = { ...p };
      delete next[userId];
      return next;
    });
    if (reload) {
      await load();
    }
  };

  const saveProfile = async (userId: string) => {
    const res = await fetch(`/admin/users/${encodeURIComponent(userId)}/profile`, {
      method: "PUT",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
      body: JSON.stringify({ ...profileForm, avatarBase64: avatarPreview }),
    });
    const json = await res.json();
    if (!res.ok || !json.success) {
      setNotice(json.message ?? "资料更新失败");
      return;
    }
    window.dispatchEvent(
      new CustomEvent("admin-current-user-updated", {
        detail: {
          userId,
          mobile: profileForm.mobile,
          nickname: profileForm.nickname,
          avatarBase64: avatarPreview,
        },
      }),
    );
    setNotice("资料已更新");
    await load();
  };

  const inviteMember = async () => {
    if (!inviteForm.mobile.trim()) {
      setNotice("请输入成员手机号");
      return;
    }
    setInviteSubmitting(true);
    try {
      const res = await fetch("/admin/users/invitations", {
        method: "POST",
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
        body: JSON.stringify(inviteForm),
      });
      const json = await res.json();
      if (!res.ok || !json.success) {
        setNotice(json.message ?? "添加成员失败");
        return;
      }
      setNotice(json.data?.memberStatus === "PENDING_ACTIVATION" ? "邀请已发送，成员完成统一账号激活后即可加入组织" : "成员已加入组织");
      setInviteForm({ mobile: "", email: "", nickname: "", roleCode: "ORG_USER" });
      setInviteModalOpen(false);
      setSelectedUserId(json.data?.id ?? "");
      await load();
    } catch {
      setNotice("添加成员失败");
    } finally {
      setInviteSubmitting(false);
    }
  };

  const setMemberStatus = async (userId: string, action: "suspend" | "restore") => {
    const res = await fetch(`/admin/users/${encodeURIComponent(userId)}/${action}`, {
      method: "POST",
      headers: { Authorization: `Bearer ${token}` },
    });
    const json = await res.json();
    if (!res.ok || !json.success) {
      setNotice(json.message ?? "成员状态更新失败");
      return;
    }
    setNotice(action === "suspend" ? "成员已停用" : "成员已恢复");
    await load();
  };

  const transferOwner = async (userId: string) => {
    const res = await fetch(`/admin/users/${encodeURIComponent(userId)}/transfer-owner`, {
      method: "POST",
      headers: { Authorization: `Bearer ${token}` },
    });
    const json = await res.json();
    if (!res.ok || !json.success) {
      setNotice(json.message ?? "Owner 转让失败");
      return;
    }
    setNotice("Owner 已转让");
    await load();
  };

  useEffect(() => {
    void load();
  }, [token]);

  useEffect(() => {
    if (!inviteModalOpen) return;
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !inviteSubmitting) {
        setInviteModalOpen(false);
      }
    };
    document.addEventListener("keydown", closeOnEscape);
    return () => document.removeEventListener("keydown", closeOnEscape);
  }, [inviteModalOpen, inviteSubmitting]);

  const filteredUsers = users.filter((u) => {
    const q = keyword.trim().toLowerCase();
    if (!q) return true;
    return u.mobile.toLowerCase().includes(q) || (u.nickname ?? "").toLowerCase().includes(q);
  });
  const selected = filteredUsers.find((u) => u.id === selectedUserId) ?? filteredUsers[0] ?? null;
  const currentMember = users.find((u) => u.id === adminAuth?.userId);
  const currentAdminIsOwner = currentMember?.roleCode === ROLE_OWNER || (!currentMember && adminAuth?.roles?.includes(ROLE_OWNER));
  const selectedIsOwner = selected?.roleCode === ROLE_OWNER;
  const selectedIsSuspended = selected?.memberStatus === STATUS_SUSPENDED;

  useEffect(() => {
    if (!selected) return;
    setProfileForm({
      mobile: selected.mobile ?? "",
      nickname: selected.nickname ?? "",
      ccUsername: selected.ccUsername ?? "",
      ccSafetymark: selected.ccSafetymark ?? "",
    });
    setAvatarPreview(selected.avatarBase64 ?? "");
  }, [selected?.id]);

  const formatBeijingDateTime = (value: string) => {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;
    return new Intl.DateTimeFormat("zh-CN", {
      timeZone: "Asia/Shanghai",
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      hour12: false,
    })
      .format(date)
      .replace(/\//g, "-");
  };

  return (
    <div className="admin-page">
      <header className="user-admin-header">
        <div>
          <h1>用户管理</h1>
        </div>
        {notice && <p className="notice">{notice}</p>}
      </header>

      <div className="user-admin-layout">
        <aside className="user-list-panel">
          <div className="user-list-toolbar">
            <button type="button" className="user-add-member-button" onClick={() => setInviteModalOpen(true)}>
              添加成员
            </button>
          </div>
          <div className="user-search">
            <svg className="user-search__icon" viewBox="0 0 24 24" aria-hidden>
              <circle cx="11" cy="11" r="6.5" />
              <path d="m16 16 4 4" />
            </svg>
            <input
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              placeholder="搜索用户（手机号或昵称）"
              aria-label="搜索用户"
            />
          </div>
          <div className="user-list-scroll">
            {filteredUsers.map((u) => (
              <button
                key={u.id}
                type="button"
                className={`user-list-item ${selected?.id === u.id ? "active" : ""}`}
                onClick={() => setSelectedUserId(u.id)}
              >
                <div className="user-list-item__mobile">{u.nickname || u.mobile}</div>
                <div className="user-list-item__meta">
                  <span>{u.nickname ? u.mobile : "未设置昵称"}</span>
                  <span>{u.memberStatus === STATUS_SUSPENDED ? "已停用" : userRoleLabel(u.roleCode)}</span>
                </div>
              </button>
            ))}
            {filteredUsers.length === 0 && <p className="subtle">暂无匹配用户</p>}
          </div>
        </aside>

        <section className="user-detail-panel">
          {!selected && <p className="subtle">请选择左侧用户</p>}
          {selected && (
            <>
              <div className="user-detail-head">
                <div className="user-avatar-placeholder" aria-hidden>
                  {avatarPreview ? (
                    <img src={avatarPreview} alt="avatar" className="user-avatar-image" />
                  ) : (
                    <svg viewBox="0 0 24 24" width="24" height="24" fill="none">
                      <circle cx="12" cy="8" r="4" stroke="currentColor" strokeWidth="1.6" />
                      <path d="M4.5 20a7.5 7.5 0 0 1 15 0" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
                    </svg>
                  )}
                </div>
                <div>
                  <div className="user-detail-title-row">
                    <h3 className="user-detail-name">{selected.nickname || selected.mobile}</h3>
                    {selectedIsOwner && <span className="user-owner-tag">组织创建者</span>}
                  </div>
                  <p className="subtle">{selected.mobile}</p>
                </div>
                <div className="user-detail-topbox">
                  <div className="user-detail-topbox__item">
                    <span className="subtle">当前角色</span>
                    <strong>{userRoleLabel(pending[selected.id] ?? selected.roleCode)}</strong>
                  </div>
                  <div className="user-detail-topbox__item">
                    <span className="subtle">成员状态</span>
                    <button
                      type="button"
                      className={`user-status-switch${selectedIsSuspended ? "" : " is-on"}`}
                      role="switch"
                      aria-checked={!selectedIsSuspended}
                      aria-label={selectedIsSuspended ? "恢复成员" : "停用成员"}
                      onClick={() => void setMemberStatus(selected.id, selectedIsSuspended ? "restore" : "suspend")}
                    >
                      <span className="user-status-switch__track" aria-hidden>
                        <span className="user-status-switch__thumb" />
                      </span>
                      <span>{selectedIsSuspended ? "已停用" : "有效"}</span>
                    </button>
                  </div>
                  <div className="user-detail-topbox__item">
                    <span className="subtle">创建时间</span>
                    <strong>{formatBeijingDateTime(selected.createdAt)}</strong>
                  </div>
                </div>
              </div>

              <div className="user-detail-tabs">
                <button
                  type="button"
                  className={`user-detail-tab ${activeTab === "basic" ? "active" : ""}`}
                  onClick={() => setActiveTab("basic")}
                >
                  基本信息
                </button>
                <button
                  type="button"
                  className={`user-detail-tab ${activeTab === "cloudcc" ? "active" : ""}`}
                  onClick={() => setActiveTab("cloudcc")}
                >
                  CloudCC账号绑定信息
                </button>
              </div>

              {activeTab === "basic" && (
                <>
                  <div className="user-detail-grid">
                    <div className="subtle">手机号</div>
                    <div>
                      <input
                        value={profileForm.mobile}
                        onChange={(e) => setProfileForm((p) => ({ ...p, mobile: e.target.value }))}
                        placeholder="请输入手机号"
                      />
                    </div>
                    <div className="subtle">昵称（支持中文）</div>
                    <div>
                      <input
                        value={profileForm.nickname}
                        onChange={(e) => setProfileForm((p) => ({ ...p, nickname: e.target.value }))}
                        placeholder="请输入昵称"
                      />
                    </div>
                    <div className="subtle">头像</div>
                    <div className="row admin-row--tight">
                      <input
                        type="file"
                        accept="image/*"
                        onChange={(e) => {
                          const file = e.target.files?.[0];
                          if (!file) return;
                          void (async () => {
                            try {
                              const avatar = await processAvatarFile(file);
                              setAvatarPreview(avatar);
                              setNotice("");
                            } catch (error) {
                              setNotice(error instanceof Error ? error.message : "头像处理失败，请稍后重试");
                            }
                          })();
                        }}
                      />
                    </div>
                    <div className="subtle">角色设置</div>
                    <div className="row admin-row--tight">
                      <select
                        className="field-select"
                        value={pending[selected.id] ?? selected.roleCode}
                        onChange={(e) => setRoleLocal(selected.id, e.target.value)}
                        disabled={selected.roleCode === "OWNER"}
                      >
                        {selected.roleCode === ROLE_OWNER ? <option value={ROLE_OWNER}>组织创建者</option> : null}
                        <option value="ORG_ADMIN">ORG_ADMIN</option>
                        <option value="ORG_USER">ORG_USER</option>
                      </select>
                    </div>
                  </div>
                  <div className="row">
                    <button
                      type="button"
                      className="user-btn-soft"
                      onClick={() =>
                        void (async () => {
                          const roleChanged = (pending[selected.id] ?? selected.roleCode) !== selected.roleCode;
                          if (roleChanged) {
                            await saveRole(selected.id, { reload: false });
                          }
                          await saveProfile(selected.id);
                        })()
                      }
                    >
                      保存基本信息
                    </button>
                    {currentAdminIsOwner && !selectedIsOwner && !selectedIsSuspended && (
                      <button type="button" className="user-text-action" onClick={() => void transferOwner(selected.id)}>
                        转让创建者
                      </button>
                    )}
                  </div>
                </>
              )}

              {activeTab === "cloudcc" && (
                <>
                  <div className="user-detail-grid">
                    <div className="subtle">CloudCC用户名</div>
                    <div>
                      <input
                        value={profileForm.ccUsername}
                        onChange={(e) => setProfileForm((p) => ({ ...p, ccUsername: e.target.value }))}
                        placeholder="请输入 CloudCC 用户名"
                      />
                    </div>
                    <div className="subtle">CloudCC用户安全标记</div>
                    <div>
                      <input
                        value={profileForm.ccSafetymark}
                        onChange={(e) => setProfileForm((p) => ({ ...p, ccSafetymark: e.target.value }))}
                        placeholder="请输入 CloudCC 安全标记"
                      />
                    </div>
                  </div>
                  <div className="row">
                    <button type="button" className="user-btn-soft" onClick={() => void saveProfile(selected.id)}>
                      保存CloudCC绑定信息
                    </button>
                  </div>
                </>
              )}
            </>
          )}
        </section>
      </div>

      {inviteModalOpen && (
        <div
          className="user-invite-modal-backdrop"
          role="presentation"
          onClick={(event) => {
            if (event.target === event.currentTarget && !inviteSubmitting) {
              setInviteModalOpen(false);
            }
          }}
        >
          <form
            className="user-invite-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="user-invite-modal-title"
            onSubmit={(event) => {
              event.preventDefault();
              void inviteMember();
            }}
          >
            <div className="user-invite-modal__head">
              <h2 id="user-invite-modal-title">添加成员</h2>
              <button
                type="button"
                className="user-invite-modal__close"
                aria-label="关闭添加成员"
                onClick={() => setInviteModalOpen(false)}
                disabled={inviteSubmitting}
              >
                ×
              </button>
            </div>
            <div className="user-invite-modal__body">
              <label className="user-invite-field">
                <span>成员手机号</span>
                <input
                  value={inviteForm.mobile}
                  onChange={(e) => setInviteForm((form) => ({ ...form, mobile: e.target.value }))}
                  placeholder="请输入成员手机号"
                  autoFocus
                  required
                />
              </label>
              <label className="user-invite-field">
                <span>邮箱（统一账号激活必填）</span>
                <input
                  type="email"
                  value={inviteForm.email}
                  onChange={(e) => setInviteForm((form) => ({ ...form, email: e.target.value }))}
                  placeholder="用于接收统一账号激活邮件"
                />
              </label>
              <label className="user-invite-field">
                <span>昵称</span>
                <input
                  value={inviteForm.nickname}
                  onChange={(e) => setInviteForm((form) => ({ ...form, nickname: e.target.value }))}
                  placeholder="请输入昵称"
                />
              </label>
              <label className="user-invite-field">
                <span>成员角色</span>
                <select
                  value={inviteForm.roleCode}
                  onChange={(e) => setInviteForm((form) => ({ ...form, roleCode: e.target.value }))}
                >
                  <option value="ORG_USER">ORG_USER</option>
                  <option value="ORG_ADMIN">ORG_ADMIN</option>
                </select>
              </label>
            </div>
            <div className="user-invite-modal__foot">
              <button
                type="button"
                className="user-invite-modal__secondary"
                onClick={() => setInviteModalOpen(false)}
                disabled={inviteSubmitting}
              >
                取消
              </button>
              <button type="submit" className="user-invite-modal__primary" disabled={!inviteForm.mobile.trim() || inviteSubmitting}>
                {inviteSubmitting ? "添加中" : "添加成员"}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
