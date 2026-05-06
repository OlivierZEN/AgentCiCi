import { useCallback, useEffect, useMemo, useState } from "react";
import { safeFetchJson } from "../utils/http";

type Props = {
  token: string;
  active: boolean;
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

export default function CommunicationChannelBinding({ token, active }: Props) {
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
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
      const res = await fetchJson<FeishuBindingStatus>("/feishu/bot/pairing/me", { headers });
      if (!res.success) {
        setNotice(res.message ?? "加载飞书绑定状态失败");
        return;
      }
      setFeishuBinding(res.data ?? null);
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
    <div className="cici-channel-binding">
      <p className="cici-modal__intro">
        在这里绑定个人沟通渠道。绑定后，个人工作流选择“飞书私信”且未填写通知目标时，会优先使用当前飞书 open_id 发送执行结果。
      </p>

      {notice ? <div className="cici-modal__notice">{notice}</div> : null}

      <section className="cici-modal__section">
        <header className="cici-modal__section-head">
          <h4>绑定沟通渠道</h4>
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
    </div>
  );
}
