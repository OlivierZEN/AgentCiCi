import { useEffect, useState } from "react";
import { useAdminToken } from "../useAdminToken";

type UserRow = {
  id: string;
  mobile: string;
  roleCode: string;
  createdAt: string;
  nickname?: string;
  ccUsername?: string;
  ccSafetymark?: string;
  avatarBase64?: string;
};

export default function AdminUsersPage() {
  const token = useAdminToken();
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

  useEffect(() => {
    void load();
  }, [token]);

  const filteredUsers = users.filter((u) => {
    const q = keyword.trim().toLowerCase();
    if (!q) return true;
    return u.mobile.toLowerCase().includes(q) || (u.nickname ?? "").toLowerCase().includes(q);
  });
  const selected = filteredUsers.find((u) => u.id === selectedUserId) ?? filteredUsers[0] ?? null;

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

  const processAvatar = async (file: File) => {
    const dataUrl = await new Promise<string>((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result ?? ""));
      reader.onerror = () => reject(reader.error);
      reader.readAsDataURL(file);
    });
    const img = await new Promise<HTMLImageElement>((resolve, reject) => {
      const node = new Image();
      node.onload = () => resolve(node);
      node.onerror = reject;
      node.src = dataUrl;
    });

    const size = 256;
    const canvas = document.createElement("canvas");
    canvas.width = size;
    canvas.height = size;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    const srcSide = Math.min(img.width, img.height);
    const sx = Math.floor((img.width - srcSide) / 2);
    const sy = Math.floor((img.height - srcSide) / 2);

    ctx.clearRect(0, 0, size, size);
    ctx.save();
    ctx.beginPath();
    ctx.arc(size / 2, size / 2, size / 2, 0, Math.PI * 2);
    ctx.closePath();
    ctx.clip();
    ctx.drawImage(img, sx, sy, srcSide, srcSide, 0, 0, size, size);
    ctx.restore();

    const compressed = canvas.toDataURL("image/webp", 0.82);
    setAvatarPreview(compressed);
  };

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
      <header className="chat-header">
        <h1>用户管理</h1>
        <p className="subtle">本组织用户与角色（ORG_ADMIN / ORG_USER）</p>
        {notice && <p className="notice">{notice}</p>}
      </header>

      <div className="user-admin-layout">
        <aside className="user-list-panel">
          <div className="row admin-row--tight">
            <input
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              placeholder="搜索用户（手机号或昵称）"
            />
            <button type="button" className="user-btn-soft" onClick={() => void load()}>
              搜索
            </button>
          </div>
          <div className="user-list-scroll">
            {filteredUsers.map((u) => (
              <button
                key={u.id}
                type="button"
                className={`user-list-item ${selected?.id === u.id ? "active" : ""}`}
                onClick={() => setSelectedUserId(u.id)}
              >
                <div className="user-list-item__mobile">{u.mobile}</div>
                <div className="user-list-item__meta">角色：{u.roleCode}</div>
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
                  <h3 className="user-detail-name">{selected.nickname || selected.mobile}</h3>
                  <p className="subtle">用户详情</p>
                </div>
                <div className="user-detail-topbox">
                  <div className="user-detail-topbox__item">
                    <span className="subtle">当前角色</span>
                    <strong>{pending[selected.id] ?? selected.roleCode}</strong>
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
                          if (file) void processAvatar(file);
                        }}
                      />
                    </div>
                    <div className="subtle">角色设置</div>
                    <div className="row admin-row--tight">
                      <select
                        className="field-select"
                        value={pending[selected.id] ?? selected.roleCode}
                        onChange={(e) => setRoleLocal(selected.id, e.target.value)}
                      >
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
    </div>
  );
}
