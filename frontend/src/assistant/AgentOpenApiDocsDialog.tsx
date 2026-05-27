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
  { id: "parameters", label: "参数发现" },
  { id: "chat-messages", label: "发送消息" },
  { id: "stop-task", label: "停止生成" },
  { id: "file-upload", label: "文件上传" },
  { id: "message-feedbacks", label: "消息反馈" },
  { id: "suggested-questions", label: "建议问题" },
  { id: "messages", label: "消息历史" },
  { id: "conversations", label: "会话列表" },
  { id: "conversation-name", label: "会话命名" },
  { id: "conversation-delete", label: "删除会话" },
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
  const parametersPath = "/parameters";
  const chatMessagesPath = "/chat-messages";
  const stopTaskPath = "/chat-messages/{taskId}/stop";
  const filesPath = "/files/upload";
  const feedbacksPath = "/messages/{messageId}/feedbacks";
  const suggestedPath = "/messages/{messageId}/suggested";
  const messagesPath = "/messages";
  const conversationsPath = "/conversations";
  const conversationNamePath = "/conversations/{conversationId}/name";
  const conversationDeletePath = "/conversations/{conversationId}";
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

API Key 类型分为 \`standard\` 与 \`cloudcc\`。\`standard\` 保持默认 run-as 行为；\`cloudcc\` 用于 CloudCC 嵌入页，发送消息时必须传入当前 CloudCC 用户的 \`cloudccContext.accessToken\`。

\`\`\`http
Authorization: Bearer {API_KEY}
X-Cici-Api-Key: {API_KEY}
\`\`\`

## 参数发现

\`\`\`text
${methodLine("GET", parametersPath)}
\`\`\`

返回开场白、建议问题、文件上传限制、retriever resource 和系统参数。集成方可以先用它渲染自有聊天入口。

## 发送消息

\`\`\`text
${methodLine("POST", chatMessagesPath)}
\`\`\`

\`\`\`bash
curl -X POST "${normalizedBaseUrl}${chatMessagesPath}" \\
  -H "Authorization: Bearer {API_KEY}" \\
  -H "Content-Type: application/json" \\
  -H "Idempotency-Key: crm-msg-001" \\
  -d '{
    "query": "汇总一下这个客户最近的跟进重点",
    "user": "customer-001",
    "responseMode": "blocking",
    "conversationId": "crm-customer-001",
    "inputs": {
      "source": "crm"
    },
    "cloudccContext": {
      "accessToken": "{CLOUDCC_PAGE_TOKEN}",
      "baseUrl": "https://szyd.apis.cloudcc.cn/lightningapi"
    }
  }'
\`\`\`

会话服务字段说明：\`query\` 是用户本轮问题，\`user\` 是终端用户标识，\`conversationId\` 是外部业务会话 ID，\`responseMode=streaming\` 返回 SSE \`message / agent_thought / message_end / error\`。仅 \`cloudcc\` Key 可以使用 \`cloudccContext\`，且 token 不会写入调用日志或 trace。

## 停止生成

\`\`\`text
${methodLine("POST", stopTaskPath)}
\`\`\`

\`\`\`bash
curl -X POST "${normalizedBaseUrl}${stopTaskPath}" \\
  -H "Authorization: Bearer {API_KEY}"
\`\`\`

对正在生成的任务标记取消请求，并返回稳定任务状态。可停止任务由发送消息返回的 \`task_id\` 定位。

## 文件上传

\`\`\`text
${methodLine("POST", filesPath)}
\`\`\`

\`\`\`bash
curl -X POST "${normalizedBaseUrl}${filesPath}" \\
  -H "Authorization: Bearer {API_KEY}" \\
  -F "user=customer-001" \\
  -F "conversation_id=crm-customer-001" \\
  -F "file=@case-note.txt"
\`\`\`

文件上传后返回 \`id\`，再通过 \`chat-messages.files[].upload_file_id\` 引用。文件按 API Key、Agent、终端用户和会话隔离。

## 消息反馈

\`\`\`text
${methodLine("POST", feedbacksPath)}
\`\`\`

\`\`\`bash
curl -X POST "${normalizedBaseUrl}${feedbacksPath}" \\
  -H "Authorization: Bearer {API_KEY}" \\
  -H "Content-Type: application/json" \\
  -d '{
    "rating": "like",
    "content": "回答准确"
  }'
\`\`\`

对指定回答提交 \`like\` / \`dislike\` 和可选文字反馈。

## 建议问题

\`\`\`text
${methodLine("GET", suggestedPath)}
\`\`\`

\`\`\`bash
curl "${normalizedBaseUrl}${suggestedPath}" \\
  -H "Authorization: Bearer {API_KEY}"
\`\`\`

读取指定回答后的下一步建议问题，用于在外部聊天入口渲染快捷追问。

## 消息历史

\`\`\`text
${methodLine("GET", messagesPath)}
\`\`\`

\`\`\`bash
curl "${normalizedBaseUrl}${messagesPath}?conversation_id=crm-customer-001&user=customer-001&limit=20" \\
  -H "Authorization: Bearer {API_KEY}"
\`\`\`

按 \`conversation_id\`、\`user\` 查询消息历史，可用 \`first_id\` 和 \`limit\` 做滚动加载。

## 会话列表

\`\`\`text
${methodLine("GET", conversationsPath)}
\`\`\`

\`\`\`bash
curl "${normalizedBaseUrl}${conversationsPath}?user=customer-001" \\
  -H "Authorization: Bearer {API_KEY}"
\`\`\`

按当前 API Key 和 Agent 查询外部会话列表，可选按终端用户过滤。

## 会话命名

\`\`\`text
${methodLine("POST", conversationNamePath)}
\`\`\`

\`\`\`bash
curl -X POST "${normalizedBaseUrl}${conversationNamePath}" \\
  -H "Authorization: Bearer {API_KEY}" \\
  -H "Content-Type: application/json" \\
  -d '{"name": "客户 001 售后跟进"}'
\`\`\`

为指定外部会话写入展示名称，便于外部系统同步会话标题。

## 删除会话

\`\`\`text
${methodLine("DELETE", conversationDeletePath)}
\`\`\`

\`\`\`bash
curl -X DELETE "${normalizedBaseUrl}${conversationDeletePath}" \\
  -H "Authorization: Bearer {API_KEY}"
\`\`\`

删除指定外部会话映射和可见历史。不同 Key 的同名会话不会互相影响。

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
| unsupported_key_type | Key 类型不受支持。 |
| cloudcc_token_required | CloudCC 嵌入 Key 缺少 \`cloudccContext.accessToken\`。 |
| cloudcc_context_not_allowed | 标准 Key 不允许传入 CloudCC 身份上下文。 |
| cloudcc_context_invalid | CloudCC 上下文字段格式不合法。 |
| cloudcc_base_url_denied | CloudCC 网关地址不在允许范围内。 |
| cloudcc_token_rejected | CloudCC token 已过期或被拒绝。 |

## 安全建议

- API Key 只保存在后端服务或密钥管理系统。
- 生产 Key 设置过期时间、来源 IP、分钟限流和日配额。
- 泄露后立即撤销或轮换，明文只会在创建或轮换结果里出现一次。
- run-as 用户只授予这个 Agent 真实需要的系统和集成权限。
- CloudCC 嵌入 Key 仍需要 AgentCiCi API Key 鉴权，CloudCC token 只作为当前 CRM 用户访问凭证，不替代 AgentCiCi 鉴权。
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
            <p>`standard` Key 保持默认 run-as 行为；`cloudcc` Key 用于 CloudCC 嵌入页，发送消息时必须传入当前 CloudCC 用户的 `cloudccContext.accessToken`。</p>
            {renderCodeBlock("authCurl", `Authorization: Bearer {API_KEY}
X-Cici-Api-Key: {API_KEY}`)}
          </section>

          <section id="parameters" className="cici-openapi-docs__section">
            <h3>参数发现</h3>
            <p>集成方可先读取开场白、建议问题、文件上传限制、retriever resource 和系统参数，再渲染自己的聊天入口。</p>
            <div className="cici-openapi-docs__method">{methodLine("GET", parametersPath)}</div>
            {renderCodeBlock("parametersCurl", `curl "${normalizedBaseUrl}${parametersPath}" \\
  -H "Authorization: Bearer {API_KEY}"`)}
          </section>

          <section id="chat-messages" className="cici-openapi-docs__section">
            <h3>发送消息</h3>
            <p>`chat-messages` 支持 `query`、`user`、`conversationId`、`inputs`、`files` 和 `responseMode`，用于外部系统接入 AgentCiCi 的多轮会话服务。</p>
            <div className="cici-openapi-docs__method">{methodLine("POST", chatMessagesPath)}</div>
            {renderCodeBlock("chatMessagesCurl", `curl -X POST "${normalizedBaseUrl}${chatMessagesPath}" \\
  -H "Authorization: Bearer {API_KEY}" \\
  -H "Content-Type: application/json" \\
  -H "Idempotency-Key: crm-msg-001" \\
  -d '{
    "query": "汇总一下这个客户最近的跟进重点",
    "user": "customer-001",
    "responseMode": "blocking",
    "conversationId": "crm-customer-001",
    "inputs": {
      "source": "crm"
    },
    "cloudccContext": {
      "accessToken": "{CLOUDCC_PAGE_TOKEN}",
      "baseUrl": "https://szyd.apis.cloudcc.cn/lightningapi"
    }
  }'`)}
            <table className="cici-openapi-docs__table">
              <tbody>
                <tr><th>query</th><td>用户本轮问题；也支持 `message` 作为别名。</td></tr>
                <tr><th>user</th><td>终端用户标识；也支持 `externalUser.id`，两者同时传入时必须一致。</td></tr>
                <tr><th>conversationId</th><td>外部业务会话 ID；也支持 `conversation_id` 和 `sessionId`。</td></tr>
                <tr><th>responseMode</th><td>`blocking` 返回 JSON，`streaming` 返回 SSE `message / agent_thought / message_end / error`。</td></tr>
                <tr><th>cloudccContext</th><td>仅 `cloudcc` Key 可用；`accessToken` 必填，`baseUrl` 可传 CloudCC 组织 API 网关。</td></tr>
                <tr><th>Idempotency-Key</th><td>同一 Key 下成功请求会按消息级幂等回放。</td></tr>
              </tbody>
            </table>
          </section>

          <section id="stop-task" className="cici-openapi-docs__section">
            <h3>停止生成</h3>
            <p>用发送消息返回的 `task_id` 定位正在生成的任务，提交后会标记取消请求并返回稳定状态。</p>
            <div className="cici-openapi-docs__method">{methodLine("POST", stopTaskPath)}</div>
            {renderCodeBlock("stopTaskCurl", `curl -X POST "${normalizedBaseUrl}${stopTaskPath}" \\
  -H "Authorization: Bearer {API_KEY}"`)}
          </section>

          <section id="file-upload" className="cici-openapi-docs__section">
            <h3>文件上传</h3>
            <p>上传文件后会返回 `id`，后续在 `chat-messages.files[].upload_file_id` 中引用。文件按 API Key、Agent、终端用户和会话隔离。</p>
            <div className="cici-openapi-docs__method">{methodLine("POST", filesPath)}</div>
            {renderCodeBlock("fileCurl", `curl -X POST "${normalizedBaseUrl}${filesPath}" \\
  -H "Authorization: Bearer {API_KEY}" \\
  -F "user=customer-001" \\
  -F "conversation_id=crm-customer-001" \\
  -F "file=@case-note.txt"`)}
            <table className="cici-openapi-docs__table">
              <tbody>
                <tr><th>user</th><td>终端用户标识，应与后续发送消息时的 `user` 保持一致。</td></tr>
                <tr><th>conversation_id</th><td>外部业务会话 ID；传入后文件只能被同会话消息引用。</td></tr>
                <tr><th>file</th><td>multipart 文件字段，支持文档和图片类型。</td></tr>
              </tbody>
            </table>
          </section>

          <section id="message-feedbacks" className="cici-openapi-docs__section">
            <h3>消息反馈</h3>
            <p>对指定回答提交 `like` / `dislike` 和可选文字反馈，便于外部系统沉淀满意度和人工标注。</p>
            <div className="cici-openapi-docs__method">{methodLine("POST", feedbacksPath)}</div>
            {renderCodeBlock("feedbackCurl", `curl -X POST "${normalizedBaseUrl}${feedbacksPath}" \\
  -H "Authorization: Bearer {API_KEY}" \\
  -H "Content-Type: application/json" \\
  -d '{
    "rating": "like",
    "content": "回答准确"
  }'`)}
            <table className="cici-openapi-docs__table">
              <tbody>
                <tr><th>rating</th><td>`like` 或 `dislike`。</td></tr>
                <tr><th>content</th><td>可选文字反馈。</td></tr>
              </tbody>
            </table>
          </section>

          <section id="suggested-questions" className="cici-openapi-docs__section">
            <h3>建议问题</h3>
            <p>读取指定回答后的下一步建议问题，用于在外部聊天入口渲染快捷追问。</p>
            <div className="cici-openapi-docs__method">{methodLine("GET", suggestedPath)}</div>
            {renderCodeBlock("suggestedCurl", `curl "${normalizedBaseUrl}${suggestedPath}" \\
  -H "Authorization: Bearer {API_KEY}"`)}
          </section>

          <section id="messages" className="cici-openapi-docs__section">
            <h3>消息历史</h3>
            <p>按外部会话和终端用户查询消息历史。可用 `first_id` 和 `limit` 做滚动加载。</p>
            <div className="cici-openapi-docs__method">{methodLine("GET", messagesPath)}</div>
            {renderCodeBlock("messagesCurl", `curl "${normalizedBaseUrl}${messagesPath}?conversation_id=crm-customer-001&user=customer-001&limit=20" \\
  -H "Authorization: Bearer {API_KEY}"`)}
            <table className="cici-openapi-docs__table">
              <tbody>
                <tr><th>conversation_id</th><td>外部业务会话 ID。</td></tr>
                <tr><th>user</th><td>终端用户标识。</td></tr>
                <tr><th>first_id</th><td>可选，上一页最后一条消息 ID，用于继续加载。</td></tr>
                <tr><th>limit</th><td>可选，单页数量。</td></tr>
              </tbody>
            </table>
          </section>

          <section id="conversations" className="cici-openapi-docs__section">
            <h3>会话列表</h3>
            <p>按当前 API Key 和 Agent 查询外部会话列表，可选按终端用户过滤。</p>
            <div className="cici-openapi-docs__method">{methodLine("GET", conversationsPath)}</div>
            {renderCodeBlock("conversationsCurl", `curl "${normalizedBaseUrl}${conversationsPath}?user=customer-001" \\
  -H "Authorization: Bearer {API_KEY}"`)}
          </section>

          <section id="conversation-name" className="cici-openapi-docs__section">
            <h3>会话命名</h3>
            <p>为指定外部会话写入展示名称，便于外部系统同步会话标题。</p>
            <div className="cici-openapi-docs__method">{methodLine("POST", conversationNamePath)}</div>
            {renderCodeBlock("conversationNameCurl", `curl -X POST "${normalizedBaseUrl}${conversationNamePath}" \\
  -H "Authorization: Bearer {API_KEY}" \\
  -H "Content-Type: application/json" \\
  -d '{"name": "客户 001 售后跟进"}'`)}
          </section>

          <section id="conversation-delete" className="cici-openapi-docs__section">
            <h3>删除会话</h3>
            <p>删除指定外部会话映射和可见历史。不同 Key 的同名会话不会互相影响。</p>
            <div className="cici-openapi-docs__method">{methodLine("DELETE", conversationDeletePath)}</div>
            {renderCodeBlock("conversationDeleteCurl", `curl -X DELETE "${normalizedBaseUrl}${conversationDeletePath}" \\
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
                <tr><th>agent_api_scope_denied</th><td>API Key scope 不允许当前操作。</td></tr>
                <tr><th>knowledge_base_not_allowed</th><td>请求知识库不是当前 Agent 绑定子集。</td></tr>
                <tr><th>skill_not_allowed</th><td>请求 Skill 未绑定到当前 Agent。</td></tr>
                <tr><th>file_not_allowed</th><td>文件不属于当前 Key、Agent、用户或会话。</td></tr>
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
