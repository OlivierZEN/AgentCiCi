import { useEffect, useMemo, useRef, useState } from "react";

type AgentOpenApiDocsDialogProps = {
  open: boolean;
  agentId: string;
  agentName: string;
  published: boolean;
  apiChannelEnabled: boolean;
  baseUrl: string;
  displayMode?: "dialog" | "page";
  keyManagementAvailable?: boolean;
  onOpenKeyManagement?: () => void;
  onClose?: () => void;
};

const SECTIONS = [
  { id: "base-url", label: "基础 URL" },
  { id: "auth", label: "鉴权" },
  { id: "chat", label: "发送对话消息" },
  { id: "stream", label: "流式对话" },
  { id: "health", label: "健康检查" },
  { id: "sessions", label: "会话与终端用户" },
  { id: "errors", label: "错误码" },
  { id: "security", label: "安全建议" },
];

function methodLine(method: string, path: string) {
  return `${method} ${path}`;
}

function safeFilenamePart(value: string) {
  return (value || "agent-api")
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9\u4e00-\u9fa5]+/gi, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 80) || "agent-api";
}

export default function AgentOpenApiDocsDialog({
  open,
  agentId,
  agentName,
  published,
  apiChannelEnabled,
  baseUrl,
  displayMode = "dialog",
  keyManagementAvailable = false,
  onOpenKeyManagement,
  onClose,
}: AgentOpenApiDocsDialogProps) {
  const [copiedKey, setCopiedKey] = useState("");
  const titleRef = useRef<HTMLHeadingElement | null>(null);
  const contentRef = useRef<HTMLDivElement | null>(null);
  const titleId = "agent-open-api-docs-title";
  const healthPath = `/agents/${agentId || "{agentId}"}/health`;
  const chatPath = `/agents/${agentId || "{agentId}"}/chat`;
  const streamPath = `/agents/${agentId || "{agentId}"}/chat/stream`;
  const status = published ? (apiChannelEnabled ? "运行中" : "未开放 API") : "未发布";
  const normalizedBaseUrl = useMemo(() => baseUrl.replace(/\/$/, ""), [baseUrl]);

  useEffect(() => {
    if (!open || displayMode !== "dialog" || !onClose) return;
    const timer = window.setTimeout(() => titleRef.current?.focus(), 0);
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => {
      window.clearTimeout(timer);
      window.removeEventListener("keydown", onKeyDown);
    };
  }, [displayMode, onClose, open]);

  useEffect(() => {
    if (!copiedKey) return;
    const timer = window.setTimeout(() => setCopiedKey(""), 1600);
    return () => window.clearTimeout(timer);
  }, [copiedKey]);

  if (!open) return null;

  const copyText = async (key: string, value: string) => {
    try {
      await navigator.clipboard.writeText(value);
      setCopiedKey(key);
    } catch {
      setCopiedKey("");
    }
  };

  const scrollToSection = (id: string) => {
    const target = contentRef.current?.querySelector<HTMLElement>(`#${id}`);
    target?.scrollIntoView({ block: "start", behavior: "smooth" });
  };

  const buildMarkdown = () => `# 对话型 Agent API

Agent: ${agentName || "未命名 Agent"}
Agent ID: ${agentId || "未保存"}
状态: ${status}
API 服务器: ${normalizedBaseUrl}

## 基础 URL

所有开放接口都以当前服务器地址为前缀。公网部署后，反向代理需要把 \`/openapi\` 转发到后端。

\`\`\`text
${normalizedBaseUrl}
\`\`\`

## 鉴权

服务端保存 API Key，调用时任选一种 Header。不要把 Key 放进浏览器、移动端包或前端源码。

\`\`\`http
Authorization: Bearer {API_KEY}
X-Cici-Api-Key: {API_KEY}
\`\`\`

## 发送对话消息

用于普通请求响应式对话。\`sessionId\` 由调用方定义，同一个 Key、Agent 和 session 会映射到同一内部会话。

\`\`\`text
${methodLine("POST", chatPath)}
\`\`\`

\`\`\`bash
curl -X POST "${normalizedBaseUrl}${chatPath}" \\
  -H "Authorization: Bearer {API_KEY}" \\
  -H "Content-Type: application/json" \\
  -d '{
    "sessionId": "crm-customer-001",
    "message": "汇总一下这个客户最近的跟进重点",
    "externalUser": {
      "id": "customer-001",
      "name": "张三",
      "type": "customer"
    },
    "knowledgeBaseIds": ["1"],
    "activeSkillCode": "lead-followup",
    "metadata": {
      "source": "crm",
      "objectId": "001xx000003DGbY"
    }
  }'
\`\`\`

| 字段 | 说明 |
| --- | --- |
| sessionId | 外部业务会话 ID，可选但建议传入。 |
| message | 用户本轮问题，默认最大 8000 字符。 |
| externalUser | 终端用户元数据，只进入审计和上下文摘要。 |
| knowledgeBaseIds | 必须是当前 Agent 已绑定知识库的子集。 |
| activeSkillCode | 必须是当前 Agent 已绑定且启用的 Skill。 |

## 流式对话

SSE 事件沿用内部流式语义，并在开始和结束事件中补充 requestId、session 和 trace 信息。

\`\`\`text
${methodLine("POST", streamPath)}
\`\`\`

\`\`\`bash
curl -N -X POST "${normalizedBaseUrl}${streamPath}" \\
  -H "Authorization: Bearer {API_KEY}" \\
  -H "Accept: text/event-stream" \\
  -H "Content-Type: application/json" \\
  -d '{"sessionId":"crm-customer-001","message":"继续分析下一步动作"}'
\`\`\`

| 事件 | 说明 |
| --- | --- |
| meta | requestId、agentId、外部 sessionId、内部 internalSessionId。 |
| phase | retrieving、rag_done、generating 等运行阶段。 |
| delta | 增量文本。 |
| done | ok、traceId、elapsedMs 和 runtime 摘要。 |
| error | requestId、code、message。 |

## 健康检查

用于上线前探测 Key、Agent、发布版本和 API channel 是否可以调用。

\`\`\`text
${methodLine("GET", healthPath)}
\`\`\`

\`\`\`bash
curl "${normalizedBaseUrl}${healthPath}" \\
  -H "Authorization: Bearer {API_KEY}"
\`\`\`

## 会话与终端用户

外部 session 不直接写入内部会话 ID。系统会按 Key、Agent 和 external session 做稳定映射，不同 Key 的同名 session 不共享上下文。

\`externalUser\` 不会创建内部用户，也不会提升权限。真正执行权限来自 API Key 绑定的 run-as 用户。

## 错误码

| 错误码 | 说明 |
| --- | --- |
| agent_api_key_missing | 缺少 Key。 |
| agent_api_key_invalid | Key 不存在、hash 不匹配或已撤销。 |
| agent_api_key_expired | Key 已过期。 |
| agent_api_ip_denied | 来源 IP 不在 allowlist。 |
| agent_channel_disabled | Agent 未开放 api channel。 |
| agent_not_published | Agent 尚未发布。 |
| rate_limit_exceeded | 每分钟调用超限。 |
| daily_quota_exceeded | 日调用额度耗尽。 |

## 安全建议

- API Key 只保存在后端服务或密钥管理系统。
- 生产 Key 设置过期时间、来源 IP、分钟限流和日配额。
- 泄露后立即撤销或轮换，明文只会在创建或轮换结果里出现一次。
- run-as 用户只授予这个 Agent 真实需要的系统和集成权限。
`;

  const downloadMarkdown = () => {
    const blob = new Blob([buildMarkdown()], { type: "text/markdown;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `${safeFilenamePart(agentName || agentId)}-openapi.md`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  };

  const renderCopyButton = (key: string, value: string, label = "复制") => (
    <button
      type="button"
      className="cici-openapi-docs__copy"
      onClick={() => void copyText(key, value)}
      aria-label={label}
      title={label}
    >
      {copiedKey === key ? "已复制" : "⧉"}
    </button>
  );

  const renderCodeBlock = (key: string, code: string) => (
    <div className="cici-openapi-docs__code">
      {renderCopyButton(key, code)}
      <pre>
        <code>{code}</code>
      </pre>
    </div>
  );

  const docs = (
    <section
      className={[
        "cici-openapi-docs",
        displayMode === "page" ? "cici-openapi-docs--page" : "",
      ].filter(Boolean).join(" ")}
      role={displayMode === "dialog" ? "dialog" : undefined}
      aria-modal={displayMode === "dialog" ? "true" : undefined}
      aria-labelledby={titleId}
    >
      <div className="cici-openapi-docs__chrome-actions" aria-label="文档操作">
        <button
          type="button"
          className="cici-openapi-docs__chrome-button"
          onClick={downloadMarkdown}
          aria-label="下载 Markdown 文档"
          title="下载 Markdown"
        >
          <svg viewBox="0 0 20 20" aria-hidden>
            <path d="M10 3.5v8" />
            <path d="M6.8 8.8 10 12l3.2-3.2" />
            <path d="M4 15.5h12" />
          </svg>
        </button>
        {displayMode === "dialog" ? (
          <button
            type="button"
            className="cici-openapi-docs__chrome-button cici-openapi-docs__close"
            onClick={onClose}
            aria-label="关闭"
            title="关闭"
          >
            ×
          </button>
        ) : null}
      </div>
      <header className="cici-openapi-docs__topbar">
        <div className="cici-openapi-docs__identity">
          <h2 id={titleId} ref={titleRef} tabIndex={-1}>对话型 Agent API</h2>
          <p>{agentName || "未命名 Agent"} · {agentId || "未保存"}</p>
        </div>
        <div className="cici-openapi-docs__server">
          <span>API 服务器</span>
          <strong>{normalizedBaseUrl}</strong>
          {renderCopyButton("baseUrl", normalizedBaseUrl, "复制 API 服务器")}
        </div>
        <div className="cici-openapi-docs__top-actions">
          <span className={`cici-openapi-docs__status${status === "运行中" ? " is-running" : ""}`}>
            {status}
          </span>
          <button
            type="button"
            className="cici-builder__action cici-builder__action--ghost cici-openapi-docs__key-action"
            disabled={!keyManagementAvailable}
            onClick={onOpenKeyManagement}
            title={keyManagementAvailable ? "打开 API Key 管理" : "API Key 管理待接入"}
          >
            API 密钥
          </button>
        </div>
      </header>

      <div className="cici-openapi-docs__layout">
        <main className="cici-openapi-docs__content" ref={contentRef}>
          <section id="base-url" className="cici-openapi-docs__section">
            <h3>基础 URL</h3>
            <p>所有开放接口都以当前服务器地址为前缀。公网部署后，反向代理需要把 `/openapi` 转发到后端。</p>
            <div className="cici-openapi-docs__inline-code">
              <code>{normalizedBaseUrl}</code>
              {renderCopyButton("inlineBaseUrl", normalizedBaseUrl, "复制基础 URL")}
            </div>
          </section>

          <section id="auth" className="cici-openapi-docs__section">
            <h3>鉴权</h3>
            <p>服务端保存 API Key，调用时任选一种 Header。不要把 Key 放进浏览器、移动端包或前端源码。</p>
            {renderCodeBlock("authCurl", `Authorization: Bearer {API_KEY}
X-Cici-Api-Key: {API_KEY}`)}
          </section>

          <section id="chat" className="cici-openapi-docs__section">
            <h3>发送对话消息</h3>
            <p>用于普通请求响应式对话。`sessionId` 由调用方定义，同一个 Key、Agent 和 session 会映射到同一内部会话。</p>
            <div className="cici-openapi-docs__method">{methodLine("POST", chatPath)}</div>
            {renderCodeBlock("chatCurl", `curl -X POST "${normalizedBaseUrl}${chatPath}" \\
  -H "Authorization: Bearer {API_KEY}" \\
  -H "Content-Type: application/json" \\
  -d '{
    "sessionId": "crm-customer-001",
    "message": "汇总一下这个客户最近的跟进重点",
    "externalUser": {
      "id": "customer-001",
      "name": "张三",
      "type": "customer"
    },
    "knowledgeBaseIds": ["1"],
    "activeSkillCode": "lead-followup",
    "metadata": {
      "source": "crm",
      "objectId": "001xx000003DGbY"
    }
  }'`)}
            <table className="cici-openapi-docs__table">
              <tbody>
                <tr><th>sessionId</th><td>外部业务会话 ID，可选但建议传入。</td></tr>
                <tr><th>message</th><td>用户本轮问题，默认最大 8000 字符。</td></tr>
                <tr><th>externalUser</th><td>终端用户元数据，只进入审计和上下文摘要。</td></tr>
                <tr><th>knowledgeBaseIds</th><td>必须是当前 Agent 已绑定知识库的子集。</td></tr>
                <tr><th>activeSkillCode</th><td>必须是当前 Agent 已绑定且启用的 Skill。</td></tr>
              </tbody>
            </table>
          </section>

          <section id="stream" className="cici-openapi-docs__section">
            <h3>流式对话</h3>
            <p>SSE 事件沿用内部流式语义，并在开始和结束事件中补充 requestId、session 和 trace 信息。</p>
            <div className="cici-openapi-docs__method">{methodLine("POST", streamPath)}</div>
            {renderCodeBlock("streamCurl", `curl -N -X POST "${normalizedBaseUrl}${streamPath}" \\
  -H "Authorization: Bearer {API_KEY}" \\
  -H "Accept: text/event-stream" \\
  -H "Content-Type: application/json" \\
  -d '{"sessionId":"crm-customer-001","message":"继续分析下一步动作"}'`)}
            <table className="cici-openapi-docs__table">
              <tbody>
                <tr><th>meta</th><td>requestId、agentId、外部 sessionId、内部 internalSessionId。</td></tr>
                <tr><th>phase</th><td>retrieving、rag_done、generating 等运行阶段。</td></tr>
                <tr><th>delta</th><td>增量文本。</td></tr>
                <tr><th>done</th><td>ok、traceId、elapsedMs 和 runtime 摘要。</td></tr>
                <tr><th>error</th><td>requestId、code、message。</td></tr>
              </tbody>
            </table>
          </section>

          <section id="health" className="cici-openapi-docs__section">
            <h3>健康检查</h3>
            <p>用于上线前探测 Key、Agent、发布版本和 API channel 是否可以调用。</p>
            <div className="cici-openapi-docs__method">{methodLine("GET", healthPath)}</div>
            {renderCodeBlock("healthCurl", `curl "${normalizedBaseUrl}${healthPath}" \\
  -H "Authorization: Bearer {API_KEY}"`)}
          </section>

          <section id="sessions" className="cici-openapi-docs__section">
            <h3>会话与终端用户</h3>
            <p>外部 session 不直接写入内部会话 ID。系统会按 Key、Agent 和 external session 做稳定映射，不同 Key 的同名 session 不共享上下文。</p>
            <p>`externalUser` 不会创建内部用户，也不会提升权限。真正执行权限来自 API Key 绑定的 run-as 用户。</p>
          </section>

          <section id="errors" className="cici-openapi-docs__section">
            <h3>错误码</h3>
            <table className="cici-openapi-docs__table">
              <tbody>
                <tr><th>agent_api_key_missing</th><td>缺少 Key。</td></tr>
                <tr><th>agent_api_key_invalid</th><td>Key 不存在、hash 不匹配或已撤销。</td></tr>
                <tr><th>agent_api_key_expired</th><td>Key 已过期。</td></tr>
                <tr><th>agent_api_ip_denied</th><td>来源 IP 不在 allowlist。</td></tr>
                <tr><th>agent_channel_disabled</th><td>Agent 未开放 api channel。</td></tr>
                <tr><th>agent_not_published</th><td>Agent 尚未发布。</td></tr>
                <tr><th>rate_limit_exceeded</th><td>每分钟调用超限。</td></tr>
                <tr><th>daily_quota_exceeded</th><td>日调用额度耗尽。</td></tr>
              </tbody>
            </table>
          </section>

          <section id="security" className="cici-openapi-docs__section">
            <h3>安全建议</h3>
            <ul className="cici-openapi-docs__list">
              <li>API Key 只保存在后端服务或密钥管理系统。</li>
              <li>生产 Key 设置过期时间、来源 IP、分钟限流和日配额。</li>
              <li>泄露后立即撤销或轮换，明文只会在创建或轮换结果里出现一次。</li>
              <li>run-as 用户只授予这个 Agent 真实需要的系统和集成权限。</li>
            </ul>
          </section>
        </main>

        <aside className="cici-openapi-docs__toc" aria-label="目录">
          <strong>目录</strong>
          {SECTIONS.map((section) => (
            <button key={section.id} type="button" onClick={() => scrollToSection(section.id)}>
              {section.label}
            </button>
          ))}
        </aside>
      </div>
    </section>
  );

  if (displayMode === "page") {
    return docs;
  }

  return (
    <div
      className="cici-openapi-docs-backdrop"
      role="presentation"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose?.();
      }}
    >
      {docs}
    </div>
  );
}
