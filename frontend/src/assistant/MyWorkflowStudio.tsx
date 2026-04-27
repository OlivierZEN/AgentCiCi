import { useCallback, useEffect, useMemo, useState } from "react";
import { safeFetchJson } from "../utils/http";

type Props = {
  token: string;
  active: boolean;
};

type WorkflowVersion = {
  id: number;
  versionNo: number;
  versionLabel: string;
  publishStatus: string;
  createdAt: string;
};

type WorkflowTrigger = {
  id: number;
  routineKey: string;
  routineName: string;
  triggerType: string;
  cronExpr: string;
  timezone: string;
  intervalSeconds: number;
  enabled: boolean;
  nextFireAt: string;
  lastTriggeredAt: string;
};

type WorkflowExecution = {
  id: number;
  routineKey: string;
  triggerSource: string;
  status: string;
  scheduledAt: string;
  startedAt: string;
  finishedAt: string;
  outputSummary: string;
  trace: Array<Record<string, unknown>>;
  errorCode: string;
  errorMessage: string;
};

type WorkflowProfile = {
  timezone: string;
  locale: string;
  notificationTarget?: { type?: string; value?: string };
  personalContext?: Record<string, unknown>;
  enabled: boolean;
};

type WorkflowSpec = {
  sourceText: string;
  status: string;
  draftVersionNo: number;
  publishedVersionId: number;
};

type WorkflowCompileArtifact = {
  id: number;
  versionNo: number;
  versionLabel: string;
  publishStatus: string;
  createdAt: string;
  workflowCode?: string;
  workflowManifest?: Record<string, unknown>;
  workflowPreview?: Record<string, unknown>;
  compileSummary?: string[];
  warnings?: string[];
  dependencies?: string[];
};

type WorkflowBundle = {
  agent: {
    agentId: string;
    name: string;
    publishedVersionId?: number | null;
    allowedToolIds?: string[];
  };
  profile: WorkflowProfile;
  spec: WorkflowSpec;
  versions: WorkflowVersion[];
  triggers: WorkflowTrigger[];
  executions: WorkflowExecution[];
  latestDraftVersion?: WorkflowCompileArtifact;
};

type FeishuBindingStatus = {
  paired?: boolean;
  agentCode?: string;
  tenantKey?: string;
  openId?: string;
  pairedAt?: string;
  lastMessageAt?: string;
};

type FeishuPairingCode = {
  code: string;
  agentCode: string;
  expiresInSeconds: number;
  command: string;
};

type ApiEnvelope<T> = {
  success?: boolean;
  data?: T;
  message?: string;
};

async function fetchJson<T>(input: RequestInfo, init?: RequestInit): Promise<ApiEnvelope<T>> {
  const res = await fetch(input, init);
  const { body } = await safeFetchJson<T>(res);
  if (body) return body;
  return { success: res.ok, message: `HTTP ${res.status}` };
}

export default function MyWorkflowStudio({ token, active }: Props) {
  const [bundle, setBundle] = useState<WorkflowBundle | null>(null);
  const [specText, setSpecText] = useState("");
  const [timezone, setTimezone] = useState("Asia/Shanghai");
  const [notificationType, setNotificationType] = useState("log_only");
  const [notificationValue, setNotificationValue] = useState("");
  const [workflowEnabled, setWorkflowEnabled] = useState(true);
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [compileArtifact, setCompileArtifact] = useState<WorkflowCompileArtifact | null>(null);
  const [feishuBinding, setFeishuBinding] = useState<FeishuBindingStatus | null>(null);
  const [pairingCode, setPairingCode] = useState<FeishuPairingCode | null>(null);

  const headers = useMemo(
    () => ({ Authorization: `Bearer ${token}`, "Content-Type": "application/json" }),
    [token],
  );

  const refresh = useCallback(async () => {
    if (!token) return;
    setBusy(true);
    try {
      const [workflowRes, bindingRes] = await Promise.all([
        fetchJson<WorkflowBundle>("/me/agents/cici-system/workflow", { headers }),
        fetchJson<FeishuBindingStatus>("/feishu/bot/pairing/me", { headers }),
      ]);
      if (!workflowRes.success || !workflowRes.data) {
        setNotice(workflowRes.message ?? "加载个人工作流失败");
        return;
      }
      setBundle(workflowRes.data);
      setSpecText(workflowRes.data.spec?.sourceText ?? "");
      setTimezone(workflowRes.data.profile?.timezone ?? "Asia/Shanghai");
      setNotificationType(workflowRes.data.profile?.notificationTarget?.type ?? "log_only");
      setNotificationValue(workflowRes.data.profile?.notificationTarget?.value ?? "");
      setWorkflowEnabled(workflowRes.data.profile?.enabled ?? true);
      setCompileArtifact(workflowRes.data.latestDraftVersion ?? null);
      if (bindingRes.success && bindingRes.data) {
        setFeishuBinding(bindingRes.data);
      }
    } catch (error) {
      setNotice(`加载失败：${error instanceof Error ? error.message : String(error)}`);
    } finally {
      setBusy(false);
    }
  }, [token, headers]);

  useEffect(() => {
    if (active) {
      void refresh();
    }
  }, [active, refresh]);

  const saveProfile = async () => {
    setBusy(true);
    setNotice("");
    try {
      const res = await fetchJson<WorkflowProfile>("/me/agents/cici-system/workflow/profile", {
        method: "PUT",
        headers,
        body: JSON.stringify({
          timezone,
          locale: "zh-CN",
          enabled: workflowEnabled,
          notificationTarget: { type: notificationType, value: notificationValue },
          personalContext: {},
        }),
      });
      if (!res.success) {
        setNotice(res.message ?? "保存个人设置失败");
        return;
      }
      setNotice("个人工作流设置已保存");
      await refresh();
    } finally {
      setBusy(false);
    }
  };

  const saveSpec = async () => {
    setBusy(true);
    setNotice("");
    try {
      const res = await fetchJson<WorkflowSpec>("/me/agents/cici-system/workflow/spec", {
        method: "PUT",
        headers,
        body: JSON.stringify({ sourceText: specText }),
      });
      if (!res.success) {
        setNotice(res.message ?? "保存 Spec 失败");
        return;
      }
      setNotice("个人工作流草稿已保存");
      await refresh();
    } finally {
      setBusy(false);
    }
  };

  const compile = async () => {
    setBusy(true);
    setNotice("");
    try {
      const res = await fetchJson<WorkflowCompileArtifact>("/me/agents/cici-system/workflow/compile", {
        method: "POST",
        headers,
        body: JSON.stringify({ sourceText: specText }),
      });
      if (!res.success || !res.data) {
        setNotice(res.message ?? "编译失败");
        return;
      }
      setCompileArtifact(res.data);
      setNotice(`编译完成，生成版本 v${res.data.versionNo}`);
      await refresh();
    } finally {
      setBusy(false);
    }
  };

  const publish = async (versionNo?: number) => {
    const targetVersionNo = versionNo ?? compileArtifact?.versionNo ?? bundle?.versions?.[0]?.versionNo;
    if (!targetVersionNo) {
      setNotice("暂无可发布的版本，请先编译");
      return;
    }
    setBusy(true);
    setNotice("");
    try {
      const res = await fetchJson<WorkflowVersion>("/me/agents/cici-system/workflow/publish", {
        method: "POST",
        headers,
        body: JSON.stringify({ versionNo: targetVersionNo }),
      });
      if (!res.success) {
        setNotice(res.message ?? "发布失败");
        return;
      }
      setNotice(`已发布个人工作流 v${targetVersionNo}`);
      await refresh();
    } finally {
      setBusy(false);
    }
  };

  const rollback = async (versionNo: number) => {
    setBusy(true);
    setNotice("");
    try {
      const res = await fetchJson<WorkflowVersion>("/me/agents/cici-system/workflow/rollback", {
        method: "POST",
        headers,
        body: JSON.stringify({ versionNo }),
      });
      if (!res.success) {
        setNotice(res.message ?? "回滚失败");
        return;
      }
      setNotice(`已回滚到 v${versionNo}`);
      await refresh();
    } finally {
      setBusy(false);
    }
  };

  const toggleTrigger = async (trigger: WorkflowTrigger) => {
    setBusy(true);
    setNotice("");
    try {
      const res = await fetchJson<WorkflowTrigger>(`/me/agents/cici-system/workflow/triggers/${trigger.id}`, {
        method: "PUT",
        headers,
        body: JSON.stringify({ enabled: !trigger.enabled }),
      });
      if (!res.success) {
        setNotice(res.message ?? "更新触发器失败");
        return;
      }
      setNotice(`${trigger.routineName} 已${trigger.enabled ? "停用" : "启用"}`);
      await refresh();
    } finally {
      setBusy(false);
    }
  };

  const runNow = async (routineKey?: string) => {
    setBusy(true);
    setNotice("");
    try {
      const res = await fetchJson<WorkflowExecution>("/me/agents/cici-system/workflow/run-now", {
        method: "POST",
        headers,
        body: JSON.stringify({ routineKey }),
      });
      if (!res.success) {
        setNotice(res.message ?? "执行失败");
        return;
      }
      setNotice("已触发一次手动执行");
      await refresh();
    } finally {
      setBusy(false);
    }
  };

  const generatePairingCode = async () => {
    setBusy(true);
    setNotice("");
    try {
      const res = await fetchJson<FeishuPairingCode>("/feishu/bot/pairing/code", {
        method: "POST",
        headers,
        body: JSON.stringify({ agentCode: "cici" }),
      });
      if (!res.success || !res.data) {
        setNotice(res.message ?? "生成配对码失败");
        return;
      }
      setPairingCode(res.data);
      setNotice("已生成飞书配对码，请复制命令到飞书机器人单聊发送。");
    } finally {
      setBusy(false);
    }
  };

  const unbindFeishu = async () => {
    if (!window.confirm("确认解除当前飞书绑定？")) return;
    setBusy(true);
    setNotice("");
    try {
      const res = await fetchJson<void>("/feishu/bot/pairing/me", {
        method: "DELETE",
        headers,
      });
      if (!res.success) {
        setNotice(res.message ?? "解除绑定失败");
        return;
      }
      setPairingCode(null);
      setNotice("已解除当前飞书绑定");
      await refresh();
    } finally {
      setBusy(false);
    }
  };

  const copyPairingCommand = async () => {
    if (!pairingCode?.command) return;
    try {
      await navigator.clipboard.writeText(pairingCode.command);
      setNotice("配对指令已复制");
    } catch {
      setNotice("复制失败，请手动复制配对指令");
    }
  };

  return (
    <div className="cici-workflow-studio">
      <p className="cici-modal__intro">
        这里配置的是“思思”的个人工作流 Overlay。共享助手能力保持系统统一维护，你只管理属于自己的日程、提醒和个人执行流程。
      </p>
      <p className="cici-email-list__meta">
        选择“飞书私信”后，如未填写通知目标，系统会优先尝试使用你当前已绑定的飞书 open_id 主动发送执行结果。
      </p>

      {notice ? <div className="cici-modal__notice">{notice}</div> : null}

      <section className="cici-modal__section">
        <header className="cici-modal__section-head">
          <h4>个人工作流设置</h4>
          <button type="button" className="cici-btn cici-btn--ghost" onClick={() => void refresh()} disabled={busy}>
            刷新
          </button>
        </header>
        <div className="cici-form-grid">
          <label>
            <span>时区</span>
            <input value={timezone} onChange={(e) => setTimezone(e.target.value)} placeholder="Asia/Shanghai" />
          </label>
          <label>
            <span>通知方式</span>
            <select value={notificationType} onChange={(e) => setNotificationType(e.target.value)}>
              <option value="log_only">仅记录执行结果</option>
              <option value="feishu_dm">飞书私信</option>
            </select>
          </label>
          <label>
            <span>通知目标</span>
            <input
              value={notificationValue}
              onChange={(e) => setNotificationValue(e.target.value)}
              placeholder="留空时自动尝试当前飞书绑定 open_id"
            />
          </label>
          <label className="cici-form-grid__inline">
            <input type="checkbox" checked={workflowEnabled} onChange={(e) => setWorkflowEnabled(e.target.checked)} />
            <span>启用个人工作流总开关</span>
          </label>
        </div>
        <footer className="cici-modal__footer">
          <button type="button" className="cici-btn cici-btn--primary" onClick={() => void saveProfile()} disabled={busy}>
            保存设置
          </button>
        </footer>
      </section>

      <section className="cici-modal__section">
        <header className="cici-modal__section-head">
          <h4>飞书配对</h4>
          <button type="button" className="cici-btn cici-btn--ghost" onClick={() => void refresh()} disabled={busy}>
            刷新状态
          </button>
        </header>
        <div className="cici-workflow-list">
          <div className="cici-workflow-list__item">
            <div className="cici-workflow-list__row">
              <div>
                <strong>{feishuBinding?.paired ? "已绑定飞书" : "未绑定飞书"}</strong>
                <div className="cici-email-list__meta">
                  {feishuBinding?.paired
                    ? `agent=${feishuBinding.agentCode || "cici"} · openId=${feishuBinding.openId || "—"}`
                    : "生成配对码后，到飞书机器人单聊发送“配对 xxxxxx”完成绑定。"}
                </div>
                {feishuBinding?.pairedAt ? <div className="cici-email-list__meta">绑定时间：{feishuBinding.pairedAt}</div> : null}
                {feishuBinding?.lastMessageAt ? <div className="cici-email-list__meta">最近消息：{feishuBinding.lastMessageAt}</div> : null}
              </div>
              <div className="cici-email-list__ops">
                <button type="button" className="cici-btn cici-btn--ghost" onClick={() => void generatePairingCode()} disabled={busy}>
                  生成配对码
                </button>
                {feishuBinding?.paired ? (
                  <button type="button" className="cici-btn cici-btn--danger" onClick={() => void unbindFeishu()} disabled={busy}>
                    解除绑定
                  </button>
                ) : null}
              </div>
            </div>
            {pairingCode ? (
              <div className="cici-pairing-card">
                <div className="cici-pairing-card__code">{pairingCode.code}</div>
                <div className="cici-email-list__meta">有效期：{Math.round(pairingCode.expiresInSeconds / 60)} 分钟</div>
                <pre className="cici-workflow-code">{pairingCode.command}</pre>
                <div className="cici-modal__footer">
                  <button type="button" className="cici-btn cici-btn--ghost" onClick={() => void copyPairingCommand()} disabled={busy}>
                    复制配对指令
                  </button>
                </div>
              </div>
            ) : null}
          </div>
        </div>
      </section>

      <section className="cici-modal__section">
        <header className="cici-modal__section-head">
          <h4>我的工作流 Spec</h4>
          <span className="cici-workflow-studio__meta">
            共享助手：{bundle?.agent?.name ?? "思思"} · 已授权工具：{bundle?.agent?.allowedToolIds?.join("、") ?? "—"}
          </span>
        </header>
        <textarea
          className="cici-workflow-studio__textarea"
          value={specText}
          onChange={(e) => setSpecText(e.target.value)}
          placeholder="例如：上午9点检查今天和昨天的新邮件，并将摘要发送到我的飞书。"
        />
        <footer className="cici-modal__footer">
          <button type="button" className="cici-btn cici-btn--ghost" onClick={() => void saveSpec()} disabled={busy}>
            保存草稿
          </button>
          <button type="button" className="cici-btn cici-btn--ghost" onClick={() => void compile()} disabled={busy}>
            编译
          </button>
          <button type="button" className="cici-btn cici-btn--primary" onClick={() => void publish()} disabled={busy}>
            发布最新版本
          </button>
        </footer>
      </section>

      <section className="cici-modal__section">
        <header className="cici-modal__section-head">
          <h4>最新编译结果</h4>
          <span className="cici-workflow-studio__meta">
            {compileArtifact ? `v${compileArtifact.versionNo} · ${compileArtifact.publishStatus}` : "尚未编译"}
          </span>
        </header>
        {compileArtifact ? (
          <div className="cici-workflow-list">
            <div className="cici-workflow-list__item">
              <strong>编译摘要</strong>
              <ul>
                {(compileArtifact.compileSummary ?? []).map((item, index) => (
                  <li key={`summary-${index}`}>{item}</li>
                ))}
              </ul>
            </div>
            <div className="cici-workflow-list__item">
              <strong>Warnings</strong>
              <ul>
                {(compileArtifact.warnings ?? []).map((item, index) => (
                  <li key={`warning-${index}`}>{item}</li>
                ))}
              </ul>
            </div>
            <div className="cici-workflow-list__item">
              <strong>依赖</strong>
              <ul>
                {(compileArtifact.dependencies ?? []).map((item, index) => (
                  <li key={`dep-${index}`}>{item}</li>
                ))}
              </ul>
            </div>
            <div className="cici-workflow-list__item">
              <strong>workflow.ts</strong>
              <pre className="cici-workflow-code">{compileArtifact.workflowCode ?? ""}</pre>
            </div>
          </div>
        ) : (
          <div className="cici-modal__empty">先写 Spec 并点击编译，系统会生成个人 workflow draft。</div>
        )}
      </section>

      <section className="cici-modal__section">
        <header className="cici-modal__section-head">
          <h4>版本</h4>
          <span className="cici-workflow-studio__meta">发布只对当前登录用户生效</span>
        </header>
        {bundle?.versions?.length ? (
          <ul className="cici-workflow-list">
            {bundle.versions.map((item) => (
              <li key={item.id} className="cici-workflow-list__row">
                <div>
                  <strong>v{item.versionNo}</strong>
                  <span className="cici-email-list__tag">{item.publishStatus}</span>
                </div>
                <div className="cici-email-list__ops">
                  <button type="button" className="cici-btn cici-btn--ghost" onClick={() => void publish(item.versionNo)} disabled={busy}>
                    发布
                  </button>
                  <button type="button" className="cici-btn cici-btn--ghost" onClick={() => void rollback(item.versionNo)} disabled={busy}>
                    回滚到此
                  </button>
                </div>
              </li>
            ))}
          </ul>
        ) : (
          <div className="cici-modal__empty">还没有版本，先编译一次。</div>
        )}
      </section>

      <section className="cici-modal__section">
        <header className="cici-modal__section-head">
          <h4>触发器</h4>
          <span className="cici-workflow-studio__meta">发布后自动从 routine 物化</span>
        </header>
        {bundle?.triggers?.length ? (
          <ul className="cici-workflow-list">
            {bundle.triggers.map((item) => (
              <li key={item.id} className="cici-workflow-list__row">
                <div>
                  <strong>{item.routineName}</strong>
                  <div className="cici-email-list__meta">
                    {item.triggerType}
                    {item.cronExpr ? ` · ${item.cronExpr}` : ""}
                    {item.intervalSeconds ? ` · 每 ${Math.round(item.intervalSeconds / 60)} 分钟` : ""}
                  </div>
                </div>
                <div className="cici-email-list__ops">
                  <button type="button" className="cici-btn cici-btn--ghost" onClick={() => void toggleTrigger(item)} disabled={busy}>
                    {item.enabled ? "停用" : "启用"}
                  </button>
                  <button type="button" className="cici-btn cici-btn--ghost" onClick={() => void runNow(item.routineKey)} disabled={busy}>
                    立即执行
                  </button>
                </div>
              </li>
            ))}
          </ul>
        ) : (
          <div className="cici-modal__empty">发布后会在这里看到定时/周期触发器。</div>
        )}
      </section>

      <section className="cici-modal__section">
        <header className="cici-modal__section-head">
          <h4>最近执行记录</h4>
          <button type="button" className="cici-btn cici-btn--ghost" onClick={() => void runNow()} disabled={busy}>
            执行默认 routine
          </button>
        </header>
        {bundle?.executions?.length ? (
          <ul className="cici-workflow-list">
            {bundle.executions.map((item) => (
              <li key={item.id} className="cici-workflow-list__item">
                <div className="cici-workflow-list__row">
                  <strong>{item.routineKey}</strong>
                  <span className="cici-email-list__tag">{item.status}</span>
                </div>
                <div className="cici-email-list__meta">
                  来源：{item.triggerSource} · 开始：{item.startedAt || item.scheduledAt || "—"}
                </div>
                {item.outputSummary ? <pre className="cici-workflow-code">{item.outputSummary}</pre> : null}
                {item.errorMessage ? <div className="cici-email-list__meta">错误：{item.errorMessage}</div> : null}
              </li>
            ))}
          </ul>
        ) : (
          <div className="cici-modal__empty">还没有执行记录，发布后可手动执行或等待调度触发。</div>
        )}
      </section>
    </div>
  );
}
