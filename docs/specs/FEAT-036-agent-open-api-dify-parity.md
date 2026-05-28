---
kind: feature-spec
feature_id: FEAT-036
title: Agent Open API conversation service enhancement
status: implemented
owner_role: fullstack-agent
task_ids: TASK-112
related_decisions: FEAT-021, FEAT-015, FEAT-019, FEAT-022, FEAT-032
related_issues: none
updated_at: 2026-05-18T09:48:26Z
updated_by: DEV-fengchu
---

# FEAT-036 - Agent Open API conversation service enhancement

## 背景与目标

FEAT-021 已经让外部系统可以通过 API Key 调用已发布 Agent，具备 run-as、external session、调用日志、usage daily、trace metadata、CORS 和本地/测试环境 smoke。当前项目仍处开发阶段，FEAT-036 将 Open API 收口为统一会话服务接口，在 AgentCiCi 自有平台内提供更完整的会话、文件、反馈和运行控制能力，方便 CloudCC、CRM、客户门户、嵌入式页面和第三方后端接入。

本轮目标：

- 补齐会话服务常用 API：参数发现、发送消息、停止生成、会话列表/消息列表、会话重命名/删除。
- 补齐消息级体验 API：反馈、建议问题、引用/检索资源摘要、agent thoughts/tool events 的可观测输出。
- 补齐文件输入能力的首版：文件上传、文件引用到 chat 请求、权限绑定到同一 Key/Agent/external user/session。
- 明确 blocking/streaming 的统一请求契约，统一到 AgentCiCi 自有 `/chat-messages` 会话服务入口和参数别名。
- 收口安全与生产化：资源越权校验、幂等/重试语义、超时/响应大小/并发保护、生产 env 示例和公网 smoke。

## 参考能力事实源

本规格引用 2026-05-18 检索到的 Dify 官方文档作为能力参考来源；实现目标是在 AgentCiCi 平台内提供自己的开放 API，不对外宣称或承诺第三方平台协议、生态或替代关系。

- Dify 应用可以作为后端 API 服务直接集成，Conversational Applications 通过 `chat-messages` 发起对话，并使用 `conversation_id` 延续会话；Service API conversations 与 WebApp conversations 隔离。来源：https://docs.dify.ai/en/use-dify/publish/developing-with-apis
- Dify `POST /chat-messages` 常用请求字段包括 `query`、`inputs`、`user`、`response_mode`、`conversation_id`、`files`、`auto_generate_name`；响应会返回 `task_id`、`message_id`、`conversation_id`、`answer`、`metadata`，streaming 使用 SSE。来源：https://docs.dify.ai/api-reference/chats/send-chat-message
- Dify `POST /files/upload` 先上传文件，再在发送消息时以 file id 引用；文件属于当前 end-user。来源：https://docs.dify.ai/api-reference/files/upload-file
- Dify `GET /messages` 支持按 `conversation_id`、`user`、`first_id`、`limit` 滚动加载历史消息，并返回 feedback、retriever resources、agent thoughts 等结构。来源：https://docs.dify.ai/api-reference/conversations/list-conversation-messages
- Dify `GET /parameters` 返回 opening statement、suggested questions、file upload、TTS/STT、retriever resource、user input form 和系统限制。来源：https://docs.dify.ai/api-reference/applications/get-app-parameters
- Dify 还提供 TTS/STT API：`POST /text-to-audio` 和 `POST /audio-to-text`。来源：https://docs.dify.ai/api-reference/tts/convert-text-to-audio 与 https://docs.dify.ai/api-reference/tts/convert-audio-to-text

## 范围

### In Scope

- 新增或扩展 Open API runtime endpoints：
  - `GET /openapi/v1/parameters`
  - `POST /openapi/v1/chat-messages`
  - `POST /openapi/v1/chat-messages/{taskId}/stop`
  - `GET /openapi/v1/conversations`
  - `GET /openapi/v1/messages`
  - `POST /openapi/v1/conversations/{conversationId}/name`
  - `DELETE /openapi/v1/conversations/{conversationId}`
  - `POST /openapi/v1/messages/{messageId}/feedbacks`
  - `GET /openapi/v1/messages/{messageId}/suggested`
  - `POST /openapi/v1/files/upload`
- 移除开发期旧 Open API 入口 `health`、`chat`、`chat/stream`，只保留会话服务接口作为对外调用面。
- 请求支持会话服务常用字段：
  - `query` 作为 `message` 别名。
  - `inputs` 作为外部变量对象，进入运行上下文和日志摘要，但不得覆盖系统提示词、模型、工具、真实 URL/Header/Token。
  - `user` 映射为 `externalUser.id` 的简写字段；若同时传 `externalUser.id`，二者必须一致或拒绝。
  - `conversation_id` 映射 FEAT-021 external `sessionId`。
  - `response_mode=blocking|streaming` 统一入口，streaming 返回 SSE。
  - `files` 引用上传文件，首版支持文档/图片元数据入库和运行上下文注入；真实多模态模型处理按现有模型能力降级。
- 响应覆盖会话服务常用概念：
  - 返回 `task_id`、`message_id`、`conversation_id`、`answer`、`metadata.usage`、`metadata.retriever_resources`、`metadata.agent_thoughts`。
  - SSE 包含 `message`、`agent_thought`、`message_end`、`error` 等可消费事件；不对外暴露内部 `phase/tool/delta/done` 事件。
- 管理端文档弹窗和 Markdown 下载更新为增强版 API 文档，示例不展示真实 API Key。
- API Key 管理新增能力开关或 scopes，至少区分 `chat`、`files`、`feedback`、`history`、`audio`。
- 补齐 FEAT-021 既有缺口：
  - `knowledgeBaseIds` 和 `activeSkillCode` 必须校验为当前 Agent 已绑定且可用资源子集。
  - `Idempotency-Key` 明确实现幂等，或在文档/API 响应中明确仅用于审计追踪；本任务优先实现 chat-message 级幂等。
  - 执行超时、响应大小、并发保护要落到运行层或明确可观测降级。
  - production deploy 示例补 `APP_AGENT_OPEN_API_ENABLED`、`APP_AGENT_OPEN_API_KEY_PEPPER` 和推荐 CORS/限流配置。

### Out Of Scope

- 不开放 Agent 创建、编辑、发布、Skill 管理、知识库管理、模型配置管理等后台管理 API。
- 不复刻第三方平台全量产品/API，包括 workflow-run、completion-messages、datasets、annotation、admin console API。
- 不承诺首版直接处理所有文件内容；文档抽取、多模态模型输入和音视频解析可以分阶段，但文件权限、元数据、引用链路必须先落地。
- 不把 API Key 暴露给浏览器作为推荐模式；允许浏览器直调只是现有 CORS 行为，不改变安全建议。

## 用户场景

- CloudCC 页面后端通过 AgentCiCi API Key 调 `chat-messages`，用 `user` 和 `conversation_id` 维持客户页面内会话。
- 客户门户上传问题附件，拿到 file id 后随消息传入 Agent，调用日志能回溯附件元数据和 message id。
- 外部系统收到 stream 的 `task_id` 后，用户点击停止生成，后端调用 stop API。
- 集成方展示历史会话和消息列表，并对某条回答提交 like/dislike 与文字反馈。
- 集成方先调用 `parameters`，读取开场白、建议问题、文件上传限制和系统参数，自动渲染自己的聊天入口。

## 现状与约束

- FEAT-021 当前已实现 `AgentOpenApiController`、`AgentOpenApiAuthService`、`AgentOpenApiRunService`、`AgentOpenApiCredentialService`、`AgentOpenApiCallLogService` 和相关实体。
- 当前 `ChatOrchestratorService.chat(...)` / `chatStream(...)` 是运行时事实源；增强 API 仍必须复用它，不复制 RAG、Skill、工具和模型逻辑。
- 当前 `ChatCommand` 只包含 `sessionId`、`message`、`externalUser`、`knowledgeBaseIds`、`activeSkillCode`、`metadata`；新增会话服务字段需要转换层。
- 当前 `agent_api_call_log` 以 requestId 为主键，缺 message/conversation/feedback/file upload 的完整模型；本任务大概率需要新增迁移。
- 当前 stream wrapper 通过查询最新 trace 回填 traceId；实现时可先复用，但若要稳定输出 agent thoughts，建议让运行时更直接暴露 trace/message/task 关联。

## 方案设计

### 1. API 分层

- 对外只保留会话服务接口：
  - `/parameters`
  - `/chat-messages`
  - `/chat-messages/{taskId}/stop`
  - `/conversations`
  - `/messages`
  - `/messages/{messageId}/feedbacks`
  - `/messages/{messageId}/suggested`
  - `/files/upload`

新接口进入 `AgentOpenApiCompatController` 或同等模块，转换成内部 command 后复用现有 auth/session/rate-limit/call-log/run service。

### 2. 数据模型建议

新增迁移从当前最新版本之后继续编号，建议包含：

- `agent_api_message`
  - `message_id`
  - `request_id`
  - `task_id`
  - `org_id`
  - `credential_id`
  - `agent_id`
  - `external_user_id`
  - `external_session_id`
  - `internal_session_id`
  - `query`
  - `answer`
  - `status`
  - `error_code`
  - `metadata_json`
  - `created_at`
  - `completed_at`
- `agent_api_feedback`
  - `message_id`
  - `rating`
  - `content`
  - `created_at`
- `agent_api_file`
  - `file_id`
  - `org_id`
  - `credential_id`
  - `agent_id`
  - `external_user_id`
  - `external_session_id`
  - `name`
  - `size`
  - `mime_type`
  - `storage_key`
  - `created_at`
- 可选 `agent_api_task`
  - 用于 stop/cancel 和 stream task 状态；如运行时无法真正取消，必须返回清晰的 `not_cancellable` 或 `cancel_requested` 状态。

### 3. 安全与权限

- 所有新增 endpoint 继续复用 FEAT-021 API Key auth。
- 每个 file/message/conversation 查询必须同时校验 `org_id`、`credential_id`、`agent_id` 和 `external_user_id`，避免同 Agent 不同调用方串线。
- `knowledgeBaseIds` 必须是 Agent 绑定知识库子集；`activeSkillCode` 必须是 Agent 绑定且启用 Skill 子集。
- `inputs`、`metadata`、file metadata 只作为上下文和审计数据，不能覆盖模型、工具白名单、系统提示词、真实 URL/Header/Token。
- 完整 API Key 不得进入日志、trace、异常、测试断言或前端列表。

### 4. 文档与前端

- 更新 `AgentOpenApiDocsDialog`：
  - 顶部状态仍保持 `鎏金账房` 产品 register。
  - 新增 `会话服务调用`、`参数发现`、`会话/消息`、`文件上传`、`反馈与建议问题`、`停止生成`、`错误码` 章节。
  - 示例同时给出 AgentCiCi 原生字段和会话服务字段映射。
- 更新 `AgentOpenApiKeysDialog`：
  - API Key scopes 展示和编辑。
  - 调用日志可搜索 message id / task id / conversation id。

## 任务拆分

### TASK-112 - Agent Open API conversation service enhancement

负责人：`DEV-fengchu`。

建议顺序：

1. 阅读 FEAT-021 现状和本规格，确认当前迁移最新版本。
2. 设计并落地 message/task/file/feedback 数据模型。
3. 实现会话服务 controller 和 request/response DTO。
4. 补资源越权校验、幂等、超时/响应大小/并发保护。
5. 更新前端 API 文档弹窗和 Key scopes 管理。
6. 补集成测试、CORS/代理回归、本地真实 Open API smoke。
7. 更新 `.claw/tasks/TASK-112.md` 和测试记录，提交 PR。

## 验收标准

- `GET /openapi/v1/parameters` 返回 opening statement、suggested questions、file upload、TTS/STT、retriever resource、user input form 和 system parameters 的 AgentCiCi 等价结构。
- `POST /openapi/v1/chat-messages` 支持 `response_mode=blocking|streaming`，并返回/发送 `task_id`、`message_id`、`conversation_id`、`answer`、usage、retriever resources、agent thoughts；JSON 请求体始终使用 `Content-Type: application/json`，streaming 客户端通过 `Accept: text/event-stream` 表达 SSE 响应期望。
- `POST /chat-messages/{taskId}/stop` 对可停止任务生效；如底层暂不支持真实取消，必须可观测地标记 cancel requested 并返回稳定错误/状态。
- `GET /conversations`、`GET /messages`、rename/delete conversation 可按 API Key、Agent、external user 隔离访问。
- `POST /files/upload` 可上传允许类型文件，返回 file id；chat-messages 可引用本 Key/Agent/user 下的文件；越权引用被拒绝。
- `POST /messages/{messageId}/feedbacks` 与 `GET /messages/{messageId}/suggested` 可用，调用日志可追溯。
- `knowledgeBaseIds` / `activeSkillCode` 越权专项测试覆盖成功和拒绝场景。
- API Key scopes、生产 env 示例、Open API 文档弹窗和 Markdown 下载均已更新。
- 验证至少包括：
  - 后端 Open API 集成测试。
  - 后端 compile。
  - 前端 build。
  - targeted `git diff --check`。
  - 本地真实 `cici-system` 或等价 Agent 的 blocking 和 streaming smoke。
  - 如涉及 UI，按 `DESIGN.md` 页面质量流程截图桌面和移动端。

## 风险与回滚

- 风险：过度照搬参考平台字段导致 AgentCiCi 自有概念被扭曲。
  - 缓解：新增独立转换层，收口为 AgentCiCi 自有会话服务响应。
- 风险：文件上传扩大攻击面。
  - 缓解：限制大小、类型、存储路径、Key/Agent/user 绑定和保留期；不在首版自动执行不安全文件解析。
- 风险：stop API 与当前运行时取消能力不匹配。
  - 缓解：先设计 task state 和可观测 cancel requested，后续再接入真正取消机制。
- 风险：新增会话/消息 API 泄露跨调用方数据。
  - 缓解：所有查询使用 org + credential + agent + external user/session 联合约束。
- 回滚：
  - 新增会话服务 endpoint 可通过 `app.agent-open-api.conversation-api-enabled=false` 或等价开关关闭。
  - 新增表保留历史数据，不影响管理端 API Key 与调用日志能力。

## 实现进展

- 2026-05-18T04:04:58Z：规格创建，并由 `MANAGER-001` 分配 `TASK-112` 给 `DEV-fengchu`。
- 2026-05-18T07:11:02Z：`DEV-fengchu` 已完成首版实现：新增 V57 message/task/file/feedback 持久化模型；新增会话服务 endpoints，包括 `parameters`、`chat-messages`、`stop`、`conversations`、`messages`、`feedbacks`、`suggested`、`files/upload`；补 `knowledgeBaseIds` 和 `activeSkillCode` 绑定校验；API Key 新增 `scopes` 创建/更新；生产 deploy 示例补 `APP_AGENT_OPEN_API_ENABLED`、`APP_AGENT_OPEN_API_CONVERSATION_API_ENABLED`、`APP_AGENT_OPEN_API_KEY_PEPPER`；前端 Open API 文档和 Key 管理弹窗已更新。验证通过：`mvn -q -Dmaven.repo.local=.m2 -DskipTests compile`、`npm run build`、`git diff --check`、`mvn -q -Dtest=AgentOpenApiIntegrationTest test`。
- 2026-05-19T16:05:00Z：合并前集成检查发现当前 `main` 已包含 `V56__organization_profile.sql`，原 V53 迁移会在已应用 V56 的环境触发 Flyway out-of-order 校验失败；已将本功能迁移重编号为 `V57__agent_open_api_dify_parity.sql`。
- 2026-05-18T08:51:04Z：根据产品语义调整，明确本功能是参考成熟会话 API 能力形态在 AgentCiCi 实现自有开放 API；前端文档、部署开关、错误码和规格表述均改为“会话服务 API / Open API 增强”。
- 2026-05-18T09:30:42Z：按验收标准补齐请求字段别名、streaming `chat-messages`、消息分页、历史 feedback 回显、文件类型校验、启用状态下的 Skill 绑定校验、运行超时保护和响应长度保护。验证通过：`/Users/xuhm/Documents/apache-maven-3.9.9/bin/mvn -q -DskipTests compile`、`/Users/xuhm/Documents/apache-maven-3.9.9/bin/mvn -q -Dtest=AgentOpenApiIntegrationTest test`、`npm run build`、`git diff --check`、`python3 /Users/xuhm/.codex/skills/cc-aidev-guidelines-common/scripts/validate-state.py .claw`。
- 2026-05-18T09:48:26Z：根据开发阶段无兼容负担的产品决策，删除旧公开 Open API 入口 `health`、`chat`、`chat/stream`，API Key 默认 scopes 移除 `health`，前端文档和集成测试统一收口到会话服务接口。

## 交接说明

- 凤雏接手前必须运行 `dev-login.py`，使用 `.claw/assignments/TASK-112.yaml` 中授权 branch 和 scope。
- 先读 `docs/specs/FEAT-021-agent-open-api.md`、本文件、`AgentOpenApiRunService`、`AgentOpenApiAuthService`、`ChatOrchestratorService`、`AgentRunTraceService`、`AgentOpenApiDocsDialog.tsx` 和 `AgentOpenApiKeysDialog.tsx`。
- 不要把参考平台的后台管理 API 或数据集管理 API 混入本任务；本任务只做面向外部调用方的 Agent Service API 常用能力。
