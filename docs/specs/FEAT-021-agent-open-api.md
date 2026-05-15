---
kind: feature-spec
feature_id: FEAT-021
title: Agent Open API
status: in_progress
owner_role: backend-openapi-runtime
task_ids: TASK-062
related_decisions: FEAT-015, FEAT-019, FEAT-020
related_issues: none
updated_at: 2026-05-07T17:22:30+08:00
updated_by: ai
---

# FEAT-021 - Agent API 开放能力

## 背景与目标

AgentCiCi 当前已经支持组织内用户通过助手工作台调用 Agent，也已经具备 Agent Builder、Skill 绑定、知识库、工具调用、运行 trace、固定密码登录、当前 `cici.cloudcc.cn` 部署环境以及多渠道会话能力。下一步要把已发布 Agent 作为稳定服务能力开放给外部业务系统调用，例如 CRM、客户门户、低代码页面、Salesforce、CloudCC、飞书之外的第三方渠道或客户自有系统。品牌域名已确定为 `agentcici.com`；公网 API 基础地址迁移需在部署/DNS 方案中单独确认。

本功能目标是新增一套 **Agent Open API**：

- 组织管理员可以为指定 Agent 创建、停用、轮换和撤销 API Key。
- 外部系统可以通过 REST 或 SSE 调用已发布 Agent。
- 外部调用复用现有 `ChatOrchestratorService`，不复制聊天、RAG、Skill、工具、模型和治理逻辑。
- API Key 有独立的凭证、run-as 用户、配额、IP 限制、会话映射、调用日志和错误码，不混用普通用户 JWT。
- 外部调用产生的日志进入现有 `agent_run_trace` 体系，监控页和后续管理页能定位 requestId、traceId、外部 session 和调用方。

## 范围

### In Scope

- 新增 Agent API Key 数据模型、管理接口和只返回一次的明文 Key。
- 新增外部调用入口：
  - `POST /openapi/v1/agents/{agentId}/chat`
  - `POST /openapi/v1/agents/{agentId}/chat/stream`
  - `GET /openapi/v1/agents/{agentId}/health`
- 支持 Key 级别的 Agent 绑定、run-as 用户、状态、过期时间、IP allowlist、每分钟限流和日配额。
- 支持外部 `sessionId` 到内部 `chat_session.id` 的稳定映射，保证多轮上下文可恢复且不同 Key 不串线。
- 支持外部用户元数据 `externalUser`，写入调用日志和 trace detail。
- 支持请求中的 `knowledgeBaseIds` 和 `activeSkillCode`，但必须是当前 Agent 已绑定资源的子集。
- 支持 `channel=api` 的 trace、调用日志、requestId、traceId、耗时、错误码和脱敏摘要。
- 支持请求大小、响应大小、工具轮次、总超时和并发基础保护。
- 在管理端智能体构建页面增加“开放API文档”按钮，点击后以模态文档页展示当前 Agent 的 API 服务器、运行状态、API 密钥入口、基础 URL、鉴权说明、每个开放 API 的请求/响应/错误示例和右侧目录。
- 更新部署 Nginx 配置，使 `/openapi` 能在当前部署域名代理到后端；后续切换到 `agentcici.com` 时需同步更新文档示例、Nginx 与证书配置。

### Out Of Scope

- 不开放创建、编辑、发布 Agent 的外部 API。
- 不开放 Skill、知识库、工具、模型配置或平台治理管理 API。
- 不做 OAuth2 授权码、开发者门户、客户自助注册或多租户市场化文档站。
- 不支持文件上传、语音、ASR WebSocket 或多模态附件。
- 不把 API Key 当作组织管理员 JWT 使用；Key 只能调用绑定 Agent。
- 不允许外部请求覆盖模型、系统提示词、工具白名单、真实 URL、Header 或服务端凭据。
- 首版不承诺 OpenAI Chat Completions 完全兼容格式；如需要，后续单独做兼容适配层。

## 用户场景

- CRM 在客户详情页嵌入“客户跟进建议 Agent”，后端服务持有 API Key，按客户 ID 传入会话和问题。
- 客户门户调用“售前问答 Agent”，每个访客用自己的 `externalUser.id` 和 `sessionId` 保持多轮上下文。
- 实施人员为某个 Agent 发放生产 Key，限制来源 IP，仅允许每天 1000 次调用。
- 管理员在监控页查看某次外部 API 调用，能看到 requestId、traceId、外部 session、调用方、模型耗时、工具耗时和失败原因。
- 管理员在智能体构建页配置完 Agent 后，点击“开放API文档”即可查看类似开发者文档的弹窗页面，复制 API 服务器地址、鉴权 Header 和每个接口的示例请求，不需要离开当前构建流程。
- 某个外部系统泄露 Key 后，管理员立即撤销该 Key，不影响同一 Agent 的其他渠道和其他 Key。

## 现状与约束

### Verified Facts

- 内部聊天入口位于 `backend/src/main/java/com/codehouse/ciciassistant/ai/api/ChatController.java`，当前接口需要用户 JWT 上下文，并调用 `ChatOrchestratorService.chat(...)` / `chatStream(...)`。
- `ChatOrchestratorService` 已接受显式 `orgId`、`userId`、`sessionId`、`question`、`knowledgeBaseIds`、`agentId` 和 `activeSkillCode`，适合被 Open API wrapper 复用。
- `TenantContextFilter` 当前会把所有 `Authorization: Bearer ...` 当作 JWT 解析。外部 API Key 若也走 Bearer，需要让 `/openapi/v1/**` 的 `cici_ak_` 前缀跳过 JWT 解析，或使用独立 Header。
- `agent_definition` 已有 `enabled` 和 `published_version_id`；`agent_channel_binding` / `agent_publish_config` 已支持按渠道记录发布配置，可扩展 `api` channel 作为是否允许开放调用的发布开关。
- `agent_run_trace` 已记录 `org_id`、`user_id`、`session_id`、`agent_id`、`channel`、状态、耗时、模型、工具、RAG、Skill 和节点详情。
- 当前最新迁移已到 `V40__fixed_password_login.sql`，Agent Open API 后续迁移应从 `V41` 开始。
- `SecretCipherService` 已提供 AES-GCM 可逆加密能力，但 API Key 不应可逆存储，推荐只保存 hash 和 prefix。
- `ChatSessionEntity.id` 和 `ChatSessionEntity.userId` 均限制为 64 字符，外部 session 和 external user 不能直接无限长写入现有列。

### Inferred Requirements

- API Key 应绑定到一个真实组织和一个 Agent，不能只靠请求里的 `agentId` 决定权限。
- 需要一个 `runAsUserId` 来承接运行时中依赖用户身份的能力，例如用户记忆、CloudCC 用户 token、个人邮箱等。
- 外部终端用户身份应作为 metadata 保存，不应伪造成系统内 `app_user`。
- 外部 API 的错误模型要比内部 `ApiResponse` 更稳定，需要固定 `error.code` 和 `requestId`，方便第三方系统排障。

## 设计原则

- **复用运行时，不复制 Agent 逻辑**：Open API 只做鉴权、会话映射、输入输出契约和审计，核心回答仍走 `ChatOrchestratorService`。
- **Key 最小权限**：一个 Key 默认只能调用一个 Agent；跨 Agent 调用必须显式创建多个 Key，后续再考虑 scoped key。
- **发布态优先**：外部调用只允许命中已启用、已发布、已开放 `api` channel 的 Agent。
- **外部身份不污染内部用户表**：`externalUser` 写入 call log / trace，不创建 `app_user`。
- **可撤销、可观测、可限流**：每次调用都能定位到 Key、Agent、requestId、sessionId 和 traceId。
- **安全默认关闭**：通过 `app.agent-open-api.enabled` 控制总开关，未启用时开放入口直接拒绝。

## 方案设计

### 1. 总体流程

```text
组织管理员创建 Agent API Key
  ↓
系统生成 cici_ak_live_... 明文 key，仅返回一次
  ↓
外部系统携带 key 调用 /openapi/v1/agents/{agentId}/chat
  ↓
AgentOpenApiAuthService 校验 key、Agent、状态、过期、IP、配额
  ↓
AgentOpenApiSessionService 映射 externalSessionId -> internalSessionId
  ↓
AgentOpenApiRunService 以 runAsUserId 调用 ChatOrchestratorService
  ↓
ChatOrchestratorService 完成 RAG / Skill / Tool / Model / Trace
  ↓
Open API 返回 answer、sessionId、requestId、traceId 和 runtime 摘要
  ↓
AgentOpenApiCallLogService 写调用日志和用量
```

### 2. API Key 模型

明文格式：

```text
cici_ak_live_{publicId}_{secret}
```

- `publicId`：16 到 24 位短 ID，用于快速定位记录和展示 prefix。
- `secret`：至少 32 bytes 随机值，base64url 编码。
- 数据库只保存 `key_hash`，不保存明文，也不使用可逆加密。
- 管理端只展示 `key_prefix`，例如 `cici_ak_live_ab12cd34...`。
- 创建或轮换时明文只返回一次。

Hash 规则：

```text
key_hash = HMAC_SHA256(app.agent-open-api.key-pepper, full_plain_key)
```

生产环境必须配置 `app.agent-open-api.key-pepper`；开发环境可使用 `app.security.secret-key` 派生，但日志必须明确提示。

### 3. run-as 与 externalUser

每个 Key 必须配置 `runAsUserId`：

- `runAsUserId` 必须是同组织真实 `app_user.id`。
- Open API 调用内部运行时时使用 `runAsUserId` 作为 `ChatOrchestratorService` 的 `userId`。
- 需要 CloudCC、邮箱、个人记忆等用户态能力时，运行时自然复用该 run-as 用户的凭证和上下文。
- `externalUser` 只作为外部终端用户 metadata，不参与内部权限提升。

请求中的 `externalUser`：

```json
{
  "id": "customer-001",
  "name": "张三",
  "type": "customer",
  "metadata": {
    "source": "crm",
    "accountId": "acc-10086"
  }
}
```

处理规则：

- `externalUser.id` 最大 128 字符，超长拒绝。
- `externalUser.metadata` 最大 20 个 key，整体序列化不超过 4KB。
- 写入 `agent_api_call_log.external_user_id` 和 trace detail。
- 可作为只读运行上下文注入系统提示，格式为“外部调用上下文”，但不得覆盖系统提示词或权限配置。

### 4. 会话映射

外部调用可以传 `sessionId`，它是调用方可见的业务会话 ID。系统不直接写入 `chat_session.id`，而是通过映射表生成内部会话 ID。

内部会话 ID 规则：

```text
api:{publicId12}:{hash20}
```

- 总长度小于 64，满足 `ChatSessionEntity.id` 限制。
- `hash20 = base64url(sha256(orgId + credentialId + agentId + externalSessionId)).substring(0, 20)`。
- 未传 `sessionId` 时，每次请求生成一次性内部 session：`api:{publicId12}:{requestId20}`。

映射规则：

- 同一个 Key、Agent、externalSessionId 总是映射到同一个 internalSessionId。
- 不同 Key 即使 externalSessionId 一样，也不会共享上下文。
- 删除或撤销 Key 不删除历史 session 和 trace，只禁止后续调用。

### 5. Open API 鉴权

推荐 Header：

```http
Authorization: Bearer cici_ak_live_xxx
```

兼容 Header：

```http
X-Cici-Api-Key: cici_ak_live_xxx
```

实现要点：

- 新增 `AgentOpenApiAuthService` 做控制器级鉴权，避免给 Open API 设置组织管理员角色。
- 调整 `TenantContextFilter`：当请求路径是 `/openapi/v1/**` 且 Bearer token 以 `cici_ak_` 开头时，不按 JWT 解析。
- Open API 鉴权成功后在 `AgentOpenApiContext` 中保存 `orgId`、`agentId`、`credentialId`、`runAsUserId`、scope 和 limits。
- 管理端 API Key 管理接口仍使用现有 JWT 和 `@RequireOrgAdmin`。

### 6. 外部调用接口

#### Non-stream Chat

```http
POST /openapi/v1/agents/{agentId}/chat
Authorization: Bearer cici_ak_live_xxx
Content-Type: application/json
Idempotency-Key: optional-client-key
```

Request:

```json
{
  "sessionId": "crm-customer-001",
  "message": "汇总一下这个客户最近的跟进重点",
  "externalUser": {
    "id": "customer-001",
    "name": "张三",
    "type": "customer"
  },
  "knowledgeBaseIds": ["1", "5"],
  "activeSkillCode": "lead-followup",
  "metadata": {
    "source": "crm",
    "objectId": "001xx000003DGbY"
  }
}
```

Response:

```json
{
  "success": true,
  "data": {
    "requestId": "req_01HV...",
    "agentId": "sales-agent",
    "sessionId": "crm-customer-001",
    "internalSessionId": "api:ab12cd34ef56:Qk9h...",
    "traceId": "5c6d6a2a-...",
    "answer": "这个客户近期重点是...",
    "status": "completed",
    "model": {
      "modelName": "gpt-4.1"
    },
    "runtime": {
      "activatedSkillCodes": ["lead-followup"],
      "boundSkillCodes": ["general-assistant", "lead-followup"],
      "toolCallCount": 2,
      "ragContextCount": 3
    },
    "elapsedMs": 8420
  },
  "message": "OK"
}
```

#### Stream Chat

```http
POST /openapi/v1/agents/{agentId}/chat/stream
Authorization: Bearer cici_ak_live_xxx
Accept: text/event-stream
```

事件沿用内部 SSE 语义，并补充 request / trace 元数据：

| Event | Data |
|---|---|
| `meta` | `requestId`、`agentId`、外部 `sessionId`、内部 `internalSessionId` |
| `phase` | 复用内部 `phase`，如 `retrieving`、`rag_done` |
| `tool_call` | 工具名摘要，敏感信息不下发 |
| `tool_result` | 工具结果摘要，按 trace 脱敏 |
| `delta` | 增量文本 |
| `done` | `ok`、`traceId`、`elapsedMs`、runtime 摘要 |
| `error` | `requestId`、`code`、`message` |

#### Health

```http
GET /openapi/v1/agents/{agentId}/health
Authorization: Bearer cici_ak_live_xxx
```

返回 Key 与 Agent 当前是否可调用：

```json
{
  "success": true,
  "data": {
    "agentId": "sales-agent",
    "enabled": true,
    "published": true,
    "apiChannelEnabled": true,
    "credentialStatus": "ACTIVE",
    "serverTime": "2026-05-07T07:37:32Z"
  },
  "message": "OK"
}
```

### 7. 管理端接口

沿用现有 `/agents` 管理命名空间，要求 `@RequireOrgAdmin`。

```http
GET    /agents/{agentId}/api-keys
POST   /agents/{agentId}/api-keys
PUT    /agents/{agentId}/api-keys/{credentialId}
POST   /agents/{agentId}/api-keys/{credentialId}/rotate
POST   /agents/{agentId}/api-keys/{credentialId}/revoke
GET    /agents/{agentId}/api-calls?from=&to=&credentialId=&status=&q=&limit=
```

Create request:

```json
{
  "name": "CRM 生产调用",
  "runAsUserId": "1f9b...",
  "expiresAt": "2026-12-31T15:59:59Z",
  "allowedIps": ["203.0.113.10/32"],
  "rateLimitPerMinute": 60,
  "dailyQuota": 10000,
  "maxPromptChars": 8000,
  "maxResponseChars": 12000,
  "allowStream": true,
  "allowTraceRead": false
}
```

Create response:

```json
{
  "success": true,
  "data": {
    "credential": {
      "id": 12,
      "publicId": "ab12cd34ef56",
      "name": "CRM 生产调用",
      "keyPrefix": "cici_ak_live_ab12cd34ef56...",
      "status": "ACTIVE"
    },
    "plainKey": "cici_ak_live_ab12cd34ef56_xxx"
  },
  "message": "API key created. Store the plain key now."
}
```

### 8. Agent Builder API 文档弹窗

在智能体构建页面新增一个二级操作按钮：

- 文案：`开放API文档`
- 推荐图标：`BookOpen` 或 `FileText`
- 位置：Agent Builder 编辑页头部操作区，与保存、编译、发布等主流程操作同一行，但使用 secondary 按钮样式，避免抢占发布主操作。
- 可见条件：组织管理员可见；Agent 未保存时按钮 disabled；Agent 未发布或未开放 `api` channel 时仍可打开文档，但顶部状态显示“未开放”或“未发布”，接口示例保留，调用状态明确不可用。

点击后打开模式窗口，而不是跳转新页面。该弹窗必须遵守项目弹窗规范：

- blocking overlay。
- `role="dialog"`。
- `aria-modal="true"`。
- 标题与 `aria-labelledby` 关联。
- 不透明暖象牙表面。
- 右上角关闭 `×` 为无边框纯字形，hover/focus 只允许浅色背景。
- 底部可不放确认按钮；如有动作，只放统一 footer，使用 secondary “关闭”。

#### 文档页视觉与布局

参考用户给出的 Dify 文档截图的信息结构，但不照搬黑白/营销式视觉。落地时沿用 `鎏金账房` product register：

- 模态尺寸：桌面使用接近全屏的文档窗口，建议 `min(1180px, calc(100vw - 64px))` 宽、`calc(100vh - 64px)` 高；移动端降级为全屏 modal。
- 顶部 sticky 工具条：
  - 左侧：文档标题 `对话型 Agent API`、当前 Agent 名称、当前 Agent ID。
  - 中间：`API 服务器` 只读地址，例如 `https://cici.cloudcc.cn/openapi/v1`，右侧带 copy icon。
  - 右侧：状态文字 `运行中` / `未发布` / `未开放 API`，以及 `API 密钥` 入口按钮。
- 主体两栏：
  - 左侧为文档内容，最大行宽 72ch，包含说明、参数表和代码块。
  - 右侧为 `目录` rail，使用单条左分隔线和文本链接，不做悬浮卡片、背景块或 chip。移动端目录折叠到顶部。
- 代码块：
  - 用语义化 code panel 展示，不使用纯黑；建议深墨色背景、暖白代码文字、1px 金雾边线。
  - 每个代码块右上角使用 copy icon button，不使用大号文字按钮。
  - 示例中的 Key 必须显示 `{API_KEY}` 或 `cici_ak_live_xxx`，不得展示真实 Key。
- 目录内容：
  - 基础 URL
  - 鉴权
  - 发送对话消息
  - 流式对话
  - 健康检查
  - 会话与终端用户
  - 错误码
  - 安全建议

#### 文档内容结构

每个接口使用同一模板，方便开发者扫读：

```text
接口名称
用途说明
Method + Path
鉴权要求
请求字段表
请求示例
响应字段表
响应示例
常见错误
```

首版必须覆盖：

| 章节 | 内容 |
|---|---|
| 基础 URL | `https://cici.cloudcc.cn/openapi/v1`，支持复制 |
| 鉴权 | `Authorization: Bearer {API_KEY}` 和 `X-Cici-Api-Key: {API_KEY}` 两种方式，强调后端保存 Key |
| 发送对话消息 | `POST /agents/{agentId}/chat`，说明 `sessionId`、`message`、`externalUser`、`knowledgeBaseIds`、`activeSkillCode`、`metadata` |
| 流式对话 | `POST /agents/{agentId}/chat/stream`，说明 SSE 的 `meta`、`phase`、`delta`、`done`、`error` |
| 健康检查 | `GET /agents/{agentId}/health`，说明可用于上线前探测 Key 与 Agent 状态 |
| 会话与终端用户 | 说明 external session 映射、多 Key 隔离、externalUser 只做 metadata |
| 错误码 | 展示首版固定错误码和排查建议 |
| 安全建议 | 不把 Key 放在浏览器、移动端或前端源码；泄露后立刻撤销或轮换 |

#### API 密钥入口

弹窗顶部的 `API 密钥` 操作不直接展示明文 Key。点击后：

- 如果 API Key 管理接口已实现，打开同一个 Agent 的 Key 管理 modal 或跳转到 Agent Builder 的 API Key 分区。
- 如果 Key 管理尚未实现，按钮 disabled，并显示简短 tooltip：`API Key 管理待接入`。
- 创建或轮换 Key 的明文只允许在创建/轮换结果中展示一次，不能出现在文档示例、trace 或日志里。

#### 可访问性与交互

- 弹窗打开后焦点进入标题或第一个可操作按钮。
- `Esc` 关闭弹窗。
- 目录链接只滚动弹窗内部文档区域，不滚动整个页面。
- copy 成功后使用轻量 toast 或按钮旁短状态文字，不弹二次 modal。
- 长代码块和参数表必须在 modal 内部横向不溢出页面；移动端优先横向滚动代码块，文档主体保持单列。

### 9. 错误模型

Open API 使用稳定错误码。为了兼容现有响应习惯，外层仍可保持 `success/message`，但机器判断使用 `error.code`。

```json
{
  "success": false,
  "data": null,
  "message": "API key is invalid or revoked",
  "error": {
    "code": "agent_api_key_invalid",
    "requestId": "req_01HV...",
    "details": {}
  }
}
```

首版错误码：

| HTTP | code | 说明 |
|---|---|---|
| 400 | `invalid_request` | JSON、字段长度、schema 或必填字段错误 |
| 401 | `agent_api_key_missing` | 缺少 Key |
| 401 | `agent_api_key_invalid` | Key 不存在、hash 不匹配、已撤销 |
| 403 | `agent_api_key_expired` | Key 已过期 |
| 403 | `agent_api_ip_denied` | 来源 IP 不在 allowlist |
| 403 | `agent_channel_disabled` | Agent 未开放 `api` channel |
| 404 | `agent_not_found` | Agent 不存在或不属于该 Key |
| 409 | `agent_not_published` | Agent 未发布，外部 API 不可调用 |
| 429 | `rate_limit_exceeded` | 每分钟限流 |
| 429 | `daily_quota_exceeded` | 日调用上限 |
| 502 | `model_or_tool_failed` | 模型或工具链路失败 |
| 504 | `agent_run_timeout` | Open API 总超时 |

### 10. 权限边界

Open API 请求中的可变项必须被约束：

- `agentId` 必须和 Key 绑定 Agent 一致。
- `knowledgeBaseIds` 必须是当前 Agent 已启用绑定知识库的子集。
- `activeSkillCode` 必须是当前 Agent 已启用绑定 Skill 的子集。
- `message` 默认最大 8000 字符，超过直接拒绝。
- `metadata` 和 `externalUser.metadata` 只进入审计和上下文摘要，不参与权限判断。
- 不允许请求传入模型名、系统提示词、工具列表、HTTP Header、真实 API URL 或服务端 token。
- 高风险工具和 Skill API 仍按现有运行时治理执行；Open API 不绕过确认策略。

### 11. Trace 与监控集成

需要扩展 `agent_run_trace` 或 detail payload，使外部调用可被筛选：

- `channel=api`
- `sourceType=open_api`
- `requestId`
- `credentialId`
- `credentialName`
- `externalSessionId`
- `externalUserId`
- `clientIp`

建议 `AgentRunTraceService.recordChatRun(...)` 返回 `traceId`，并允许传入 `ChatRunMetadata`：

```java
public record ChatRunMetadata(
        String sourceType,
        String requestId,
        Long credentialId,
        String externalSessionId,
        String externalUserId,
        String clientIp,
        Map<String, Object> requestMetadata
) {}
```

内部 `/ai/chat` 传空 metadata，保持兼容；Open API wrapper 传入 metadata。监控页后续可以新增 `type=api` 或 `channel=api` 筛选。

## 接口与数据影响

### 数据库迁移建议

当前迁移已使用 `V40__fixed_password_login.sql`，Agent Open API 后续建议从 `V41__agent_open_api.sql` 开始。

```sql
CREATE TABLE IF NOT EXISTS agent_api_credential (
    id BIGSERIAL PRIMARY KEY,
    public_id VARCHAR(32) NOT NULL,
    org_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    key_prefix VARCHAR(64) NOT NULL,
    key_hash VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    run_as_user_id VARCHAR(64) NOT NULL,
    allowed_ips_json TEXT NOT NULL,
    scopes_json TEXT NOT NULL,
    rate_limit_per_minute INTEGER NOT NULL,
    daily_quota INTEGER NOT NULL,
    max_prompt_chars INTEGER NOT NULL,
    max_response_chars INTEGER NOT NULL,
    allow_stream BOOLEAN NOT NULL DEFAULT TRUE,
    allow_trace_read BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMP,
    last_used_at TIMESTAMP,
    created_by VARCHAR(64) NOT NULL,
    revoked_by VARCHAR(64),
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_api_credential_public_id
    ON agent_api_credential(public_id);

CREATE INDEX IF NOT EXISTS idx_agent_api_credential_org_agent
    ON agent_api_credential(org_id, agent_id, status);

CREATE TABLE IF NOT EXISTS agent_api_session_map (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    credential_id BIGINT NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    external_session_id VARCHAR(160) NOT NULL,
    internal_session_id VARCHAR(64) NOT NULL,
    external_user_id VARCHAR(128),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_api_session_external
    ON agent_api_session_map(org_id, credential_id, agent_id, external_session_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_api_session_internal
    ON agent_api_session_map(internal_session_id);

CREATE TABLE IF NOT EXISTS agent_api_call_log (
    request_id VARCHAR(64) PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    credential_id BIGINT NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    run_as_user_id VARCHAR(64) NOT NULL,
    external_session_id VARCHAR(160),
    internal_session_id VARCHAR(64) NOT NULL,
    external_user_id VARCHAR(128),
    client_ip VARCHAR(64),
    idempotency_key VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    http_status INTEGER NOT NULL,
    error_code VARCHAR(64),
    trace_id VARCHAR(64),
    prompt_chars INTEGER NOT NULL DEFAULT 0,
    response_chars INTEGER NOT NULL DEFAULT 0,
    elapsed_ms INTEGER NOT NULL DEFAULT 0,
    request_summary TEXT,
    response_summary TEXT,
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_agent_api_call_log_org_agent_created
    ON agent_api_call_log(org_id, agent_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_agent_api_call_log_credential_created
    ON agent_api_call_log(credential_id, created_at DESC);

CREATE TABLE IF NOT EXISTS agent_api_usage_daily (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    credential_id BIGINT NOT NULL,
    usage_date DATE NOT NULL,
    call_count INTEGER NOT NULL DEFAULT 0,
    success_count INTEGER NOT NULL DEFAULT 0,
    failure_count INTEGER NOT NULL DEFAULT 0,
    total_elapsed_ms BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_api_usage_daily
    ON agent_api_usage_daily(org_id, credential_id, usage_date);
```

可选地为 trace 表增加查询列：

```sql
ALTER TABLE agent_run_trace ADD COLUMN IF NOT EXISTS source_type VARCHAR(32) DEFAULT 'internal';
ALTER TABLE agent_run_trace ADD COLUMN IF NOT EXISTS request_id VARCHAR(64);
ALTER TABLE agent_run_trace ADD COLUMN IF NOT EXISTS credential_id BIGINT;
ALTER TABLE agent_run_trace ADD COLUMN IF NOT EXISTS external_user_id VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_agent_run_trace_api
    ON agent_run_trace(org_id, credential_id, started_at DESC);
```

### 后端新增模块

建议新增 package：

```text
backend/src/main/java/com/codehouse/ciciassistant/openapi/
├── api/
│   ├── AgentOpenApiController.java
│   └── AgentOpenApiCredentialController.java
├── domain/
│   ├── AgentApiCredentialEntity.java
│   ├── AgentApiCredentialRepository.java
│   ├── AgentApiSessionMapEntity.java
│   ├── AgentApiSessionMapRepository.java
│   ├── AgentApiCallLogEntity.java
│   ├── AgentApiCallLogRepository.java
│   ├── AgentApiUsageDailyEntity.java
│   └── AgentApiUsageDailyRepository.java
├── service/
│   ├── AgentApiKeyGenerator.java
│   ├── AgentOpenApiAuthService.java
│   ├── AgentOpenApiRateLimitService.java
│   ├── AgentOpenApiSessionService.java
│   ├── AgentOpenApiRunService.java
│   └── AgentOpenApiCallLogService.java
└── config/
    ├── AgentOpenApiProperties.java
    └── AgentOpenApiContext.java
```

### 前端新增组件

建议在现有 Agent Builder 壳层内新增文档弹窗组件，而不是另建路由：

```text
frontend/src/assistant/
├── AgentBuilderShell.tsx
└── AgentOpenApiDocsDialog.tsx
```

`AgentBuilderShell.tsx` 负责：

- 在编辑页头部操作区展示 `开放API文档` secondary 按钮。
- 将当前 `agentId`、Agent 名称、发布状态、api channel 状态、base URL 和 API Key 管理状态传给弹窗。
- 控制弹窗打开/关闭。

`AgentOpenApiDocsDialog.tsx` 负责：

- 渲染模式窗口、sticky 顶部工具条、文档主体、目录 rail、代码块和 copy 交互。
- 提供固定文档章节和接口示例。
- 在 API Key 管理接口未完成时展示 disabled 密钥入口。
- 使用项目现有 `鎏金账房` token，不新增页面局部大字号、厚按钮、卡片化目录或蓝/黑/渐变按钮。

### 运行时改造点

- `TenantContextFilter`
  - 对 `/openapi/v1/**` 的 `cici_ak_` Bearer token 跳过 JWT 解析。
- `ChatOrchestratorService`
  - 新增 `ChatRunOptions` 或 `ChatRunMetadata` overload。
  - 支持 `channelOverride=api`、`sourceType=open_api` 和外部上下文 prompt block。
  - 返回或暴露 `traceId`。
- `AgentRunTraceService`
  - `recordChatRun(...)` 返回 traceId。
  - trace detail 写入外部 request metadata。
  - `channelOf(sessionId)` 支持 `api:` 前缀返回 `api`。
- `AgentDefinitionService`
  - 支持 `api` channel 绑定和 publish config。
  - Open API 调用前要求 `enabled=true`、`publishedVersionId != null`、`api` channel enabled。
- `AgentSkillBindingService` / 知识库绑定校验
  - 提供只校验请求子集的服务方法，避免 Open API 传入未绑定资源。
- `deploy/nginx.cici.conf` 和 `deploy/nginx.cici.ssl.conf`
  - 补充 `/openapi` 到后端的代理规则，确保公网 `https://cici.cloudcc.cn/openapi/v1/...` 可用。

## 任务拆分

### TASK-062A - 规格与数据模型

- 新增 `V41__agent_open_api.sql`。
- 新增 Credential、SessionMap、CallLog、UsageDaily 实体与 repository。
- 新增 properties：`app.agent-open-api.enabled`、`key-pepper`、默认限流、默认超时。

### TASK-062B - API Key 管理

- 新增管理端 credential controller。
- 创建 Key 时返回明文一次。
- 支持列表、更新限制、轮换、撤销。
- 补 org admin 权限测试和 key hash 不可逆存储测试。

### TASK-062C - 外部调用鉴权与限流

- 新增 `AgentOpenApiAuthService` 和 request context。
- 支持 Bearer / `X-Cici-Api-Key`。
- 校验状态、过期、Agent、IP allowlist、rate limit、daily quota。
- 定义统一错误码。

### TASK-062D - Chat / Stream wrapper

- 新增 `/openapi/v1/agents/{agentId}/chat` 和 `/chat/stream`。
- 实现 external session 映射、run-as 用户调用、请求字段校验、Idempotency-Key。
- stream 事件补 `meta` 和 `done.traceId`。
- 修改 `ChatOrchestratorService` 返回 traceId 和外部 metadata。

### TASK-062E - Trace / Call Log / 监控接入

- 写 `agent_api_call_log` 和 `agent_api_usage_daily`。
- trace 增加 `channel=api`、requestId、credentialId、externalUserId。
- 管理端先提供接口数据，UI 可后续接入。

### TASK-062F - Agent Builder API 文档弹窗

- 在智能体构建编辑页头部增加 `开放API文档` secondary 按钮。
- 新增 `AgentOpenApiDocsDialog`，按本规格展示 API 服务器、运行状态、API 密钥入口、基础 URL、鉴权、对话、流式对话、健康检查、会话、错误码和安全建议。
- 弹窗实现 blocking overlay、`role="dialog"`、`aria-modal="true"`、labelled heading、裸 `×` 关闭、内部目录滚动和 copy 反馈。
- 视觉遵守 `鎏金账房`：暖象牙表面、墨色正文、香槟金结构线、13px 产品文本、右侧目录 rail 不做悬浮卡片或逐行背景块。

### TASK-062G - 部署与安全回归

- Nginx 配置补 `/openapi` 代理。
- 集成测试覆盖成功调用、stream、Key 撤销、过期、IP 拒绝、Agent 未发布、知识库越权、Skill 越权、限流。
- 验证敏感字段脱敏。
- 验证 Agent Builder API 文档弹窗在桌面与移动端无内容溢出，目录和代码块可用。
- 验证内部 `/ai/chat` JWT 路径不受影响。

## 验收标准

- 组织管理员可以为已发布 Agent 创建 API Key，明文只返回一次，数据库不存明文。
- 外部系统可以用 API Key 调用 non-stream chat 并拿到 answer、requestId、sessionId、traceId。
- 外部系统可以用 API Key 调用 stream chat，并收到 `meta`、`phase`、`delta`、`done` 或 `error` 事件。
- 同一个 external session 多次调用能保留上下文，不同 Key 的同名 session 不串线。
- Key 绑定 Agent 之外的 `agentId` 调用会被拒绝。
- 未启用、未发布、未开放 `api` channel 的 Agent 不能被外部调用。
- 请求中的 `knowledgeBaseIds` / `activeSkillCode` 不能越过 Agent 绑定范围。
- 撤销、过期、IP 拒绝、限流和日配额耗尽均返回稳定错误码。
- 每次调用都写入 call log；聊天完成后可通过 trace 看到 `channel=api` 和外部 request metadata。
- 智能体构建页有 `开放API文档` 按钮；点击后弹出符合项目 modal 规范的 API 文档页，内容覆盖基础 URL、鉴权、发送对话、流式对话、健康检查、会话、错误码和安全建议。
- API 文档弹窗的 `API 服务器`、鉴权 Header 和代码示例支持复制；示例不展示真实 API Key。
- 公网部署后 `https://cici.cloudcc.cn/openapi/v1/agents/{agentId}/health` 能被 Nginx 正确代理到后端。
- 内部 `/ai/chat`、`/ai/chat/stream`、`/me/agents/run-logs` 的现有行为保持兼容。

## 风险与回滚

- 风险：外部 API Key 与现有 Bearer JWT 解析冲突。
  - 缓解：明确 `cici_ak_` 前缀，并让 `TenantContextFilter` 对 `/openapi/v1/**` 的该前缀跳过 JWT 解析。
- 风险：run-as 用户导致外部调用拿到过多用户态工具权限。
  - 缓解：Key 必须绑定单 Agent，并叠加 Agent 本身的 Skill、工具、知识库白名单；后续可扩展 Key 级工具禁用列表。
- 风险：外部请求高频调用拖慢模型或工具链路。
  - 缓解：Key 级限流、日配额、总超时、并发保护和 feature flag。
- 风险：外部 session 无限增长导致数据膨胀。
  - 缓解：session map 和 call log 增加保留策略；首版先提供按创建时间清理脚本或后续定时归档。
- 风险：trace 暴露敏感业务数据。
  - 缓解：沿用 `AgentRunTraceService` 的敏感字段脱敏，并对 Open API 管理查询默认只展示摘要。
- 回滚：
  - 关闭 `app.agent-open-api.enabled`。
  - 将所有 `agent_api_credential.status` 批量置为 `PAUSED`。
  - 保留数据表和历史日志，不影响内部聊天入口。

## 实现进展

- 已重新创建 Agent API 开放能力实现设计文档。
- 已补充用户要求的 Agent Builder `开放API文档` 按钮与弹窗文档页设计，包含参考图式的信息结构和项目内视觉约束。
- 已实现 `TASK-062A` 数据模型初版：新增 `V41__agent_open_api.sql`，落地 Credential、SessionMap、CallLog、UsageDaily 实体与 repository，新增 `app.agent-open-api.*` 配置。
- 已实现 `TASK-062B` API Key 管理初版：组织管理员可列表、创建、更新、轮换、撤销 Key；明文只在创建/轮换响应中返回一次，数据库仅保存 HMAC hash 与 prefix。
- 已实现 `TASK-062C` 的核心鉴权入口：Open API 支持 `Authorization: Bearer cici_ak_live_...` 与 `X-Cici-Api-Key`，并校验总开关、Key 状态、过期时间、绑定 Agent、来源 IP、发布态和 `api` channel。
- 已实现 `TASK-062D` 的 non-stream chat 首版：`POST /openapi/v1/agents/{agentId}/chat` 会完成 external session 映射、run-as 调用 `ChatOrchestratorService.chat(...)`、稳定响应 `answer/requestId/sessionId/internalSessionId/traceId/runtime/elapsedMs`。
- 已实现 `TASK-062D` 的 stream chat wrapper：`POST /openapi/v1/agents/{agentId}/chat/stream` 会完成鉴权、external session 映射、run-as 调用 `ChatOrchestratorService.chatStream(...)`，Open API 层补 `meta`，透传内部 `phase/tool/delta`，并在完成事件补 `requestId`、`traceId`、`elapsedMs` 与 runtime 摘要。
- 已实现 `TASK-062E` 的首版调用记录：non-stream 和 stream chat 写入 `agent_api_call_log`、`agent_api_usage_daily`，并将最新 trace 标记为 `channel=api`、`sourceType=open_api`、`requestId`、`credentialId`、`externalUserId`。
- 已新增 `GET /agents/{agentId}/api-calls` 供管理端查询 Open API 调用日志。
- 已修复 Key 格式细节：`publicId` 不再生成 `_` 或 `-`，避免与明文 Key 的 `_` 分隔符冲突导致 Key 无法反查。
- 已实现 `TASK-062F` Agent Builder 文档弹窗初版：编辑页头部有 `开放API文档` secondary 按钮，弹窗覆盖 API 服务器、状态、API 密钥入口、基础 URL、鉴权、对话、流式对话、健康检查、会话、错误码和安全建议。
- 已实现 `TASK-062F` 的 Key 管理入口：文档弹窗中的 `API 密钥` 按钮打开 `AgentOpenApiKeysDialog`，支持创建、轮换、撤销 Key，明文 Key 只在创建/轮换后显示一次，并可查看/搜索调用日志。
- 已按 API Key 管理可用性反馈修正弹窗语义：列表展示每个 Key 绑定的 run-as 执行用户；创建/重新生成后的完整 Key 使用可选中、可复制的一次性区域展示；列表只展示 Key 前缀并明确不可用于调用；操作语义改为“停用/启用”“重新生成”“删除”，其中停用可恢复、重新生成会让旧 Key 立即失效、删除会永久作废并保留历史日志。
- 已继续按截图优化 API Key 管理弹窗：移除表单区和说明区多余横向分隔线；执行用户列默认只显示名称，鼠标指向时显示手机号、角色和用户 ID；Key 列表每行收为单行展示，调整列宽与操作列间距；移除前缀复制入口，避免误解为可复制完整 Key。
- 已按最新 UI 反馈移除 API Key 管理弹窗 tab 和行操作的弧形边框背景按钮样式，强制恢复为无背景、无边框、无圆角、无阴影的文本 tab / 文本命令；同时将“产品面板内部禁止带弧形边框背景伪按钮”写入 `DESIGN.md`、`DESIGN.json`、`AGENTS.md` 和 `README.md`。
- 已按截图反馈移除 API Key 管理弹窗表单下方“当前执行身份”文字，避免与上方 run-as 用户选择器重复；已有 Key 的执行用户仍在列表列中展示。
- 已补 `/openapi/` Nginx 代理到 `deploy/nginx.cici.conf` 和 `deploy/nginx.cici.ssl.conf`。
- 2026-05-15T15:12:35+08:00 已修复浏览器从 CloudCC 页面直接调用 Open API 的 CORS 预检问题：新增 `/openapi/v1/**` 专用 CORS filter，支持配置 `app.agent-open-api.cors-allowed-origins` / `cors-allowed-origin-patterns`，允许 `Authorization`、`Content-Type`、`X-Cici-Api-Key`、`Idempotency-Key` 等请求头。2026-05-15T15:14:00+08:00 按用户要求将默认与部署示例调整为 `APP_AGENT_OPEN_API_CORS_ALLOWED_ORIGINS=*`，即所有浏览器 Origin 均可调用；安全建议仍是 API Key 优先服务端持有，前端直传 Key 代表接受暴露风险。
- 当前未完成：知识库/Skill 越权专项测试、真实模型链路 smoke 与公网 `/openapi` 部署验证。

## 交接说明

- 接手实现前先看本文件、`ChatController.java`、`ChatOrchestratorService.java`、`TenantContextFilter.java`、`AgentRunTraceService.java`、`AgentDefinitionService.java`、`deploy/nginx.cici.conf` 和 `deploy/nginx.cici.ssl.conf`。
- 第一版不要复刻内部聊天逻辑；只做 Open API wrapper 并调用现有 orchestrator。
- Key 的明文只允许创建或轮换响应返回一次，任何日志、trace、异常和测试断言都不得输出完整 Key。
- API Key 管理前端入口已接入 Agent Builder 文档弹窗；后续如果调用量增大，可把调用日志升级为分页表格或独立治理页。
