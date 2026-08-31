---
kind: feature-spec
feature_id: FEAT-206
title: OpenAPI 文件附件统一运行时
status: in_implementation
owner_role: backend-agent
task_ids: TASK-346
related_decisions: FEAT-021, FEAT-036, FEAT-188
related_issues: none
updated_at: 2026-08-31T04:15:00Z
updated_by: codex
---

# FEAT-206 - OpenAPI 文件附件统一运行时

## 来源与已验证根因

- 原始设计输入：`/Volumes/AISpace/datafiles/AgentCiCi/agentcici-openapi-file-attachments-design.md`，已登记于 `references/README.md`。
- 代码回读确认 `POST /openapi/v1/files/upload` 当前只保存 `agent_api_file` 元数据，`storage_key` 是不可读取的 `agent-open-api://...` 占位值，没有持久化上传二进制。
- `POST /openapi/v1/chat-messages` 当前只校验 `upload_file_id` 的 Key/Agent/user/conversation 归属并写入 metadata；`AgentOpenApiRunService.ChatCommand` 不携带附件，运行时必然看不到文件内容。
- 内部控制台、Embed 已统一使用 `ChatAttachmentService`：服务端检测文件签名，私有落盘，按会话/用户解析附件，并由 `ChatOrchestratorService` 构造模型视觉 content block 或受控文档文本。

因此本功能不新建第二套模型附件构造器，而是修复 OpenAPI 文件存储并在内部会话解析完成后桥接到现有共享附件运行时。

## 目标

1. 保持既有 `files[].upload_file_id` 请求兼容，上传文件真实落入受保护存储，聊天时真实进入模型图片或文档上下文。
2. 支持 `files[].url` 与 `POST /openapi/v1/files/import`，所有 URL 必须先经安全 HTTPS 下载、内容检测和私有归一化，禁止把外部 URL 直接交给模型。
3. blocking 与 streaming 使用同一附件解析与模型绑定路径。
4. 复用内部 `ChatAttachmentService` 的文件签名、解析、模型 content block 与 Vision 能力门禁；OpenAPI 不维护第二套类型判断和内容构造逻辑。
5. 对非法引用、越权、过期/损坏、类型不支持、URL 被禁止、下载失败和模型能力不匹配返回明确错误；声明附件后禁止静默按纯文本继续。
6. `GET /parameters` 返回实际支持来源、MIME、大小/数量和模型能力的机器可读摘要。
7. 普通日志、错误和 API 响应不得包含二进制、Base64、完整签名 URL、对象路径或 API Key。

## 范围

### 本期实现

- PNG、JPEG、WebP 真实视觉输入。
- TXT、Markdown、CSV、JSON、可提取文本 PDF、DOCX 复用现有文档解析能力。
- multipart 上传、HTTPS URL 即时导入、显式 `/files/import`。
- 同一 API Key、Agent、external user、conversation 四层作用域；未绑定 conversation 的文件在首次引用时原子绑定。
- 同一文件在相同作用域内可多轮复用，不重复远端下载；每轮都重新完成运行时可读性和模型能力门禁。
- 同一请求内保持附件顺序，拒绝重复 ID、重复规范化 URL、空元素以及同时带 `upload_file_id` 与 `url` 的元素。
- 文件记录补齐来源、可信 MIME、kind、SHA-256、状态、失败码、首次绑定和过期时间。
- 响应 metadata 返回归一化 `upload_file_id` 列表，不回显 URL 查询参数。

### 不在本期

- XLS/XLSX、旧 DOC、高风险归档格式、音视频。
- PDF 页面视觉渲染或供应商原生 PDF 文件接口；本期只复用可提取文本 PDF。
- 病毒扫描引擎和独立对象存储迁移；当前沿用受保护本地存储，保留后续替换边界。
- 调用方自定义下载 Header/Cookie、内网 URL、`file://`、用户信息 URL、非 HTTPS 协议。
- 前端 UI 改动。

## API 契约

### 上传与 URL 导入

`POST /openapi/v1/files/upload` 保持 multipart 参数 `file`、`user`、`conversation_id`。服务端流式限制 15 MiB，按文件签名确定可信类型，成功后只在文件和记录都就绪时返回 `id/name/mime_type/kind/size/status/created_at/expires_at`。

`POST /openapi/v1/files/import` 接受 `user`、`conversation_id`、`url`、可选 `name`。`Idempotency-Key` 在 Key/Agent/user/conversation 作用域内复用同一成功记录，避免重复下载。

`files[].url` 是“远程导入并立即引用”的便捷形式；结果必须形成等价的 `file_...` 记录后才进入运行时。

### 消息文件引用

每个 `files[]` 元素必须且只能包含 `upload_file_id` 或 `url`；URL 的 `name` 只是显示提示。`id`、`file_id` 仅作为旧客户端读取别名兼容；新文档只展示 `upload_file_id`。

## URL 安全边界

- 仅允许 `https`，禁止 user-info、fragment、非标准协议和空 host。
- 初始地址及最多 3 次重定向的每一跳都重新解析 DNS，并拒绝 loopback、link-local、site-local、multicast、unspecified、保留地址、CGNAT、云元数据地址和配置的内部网段。
- 不透传调用方 Header、Cookie 或凭据；下载器无实例凭据，连接、响应和总处理均有超时。
- 不能信任 `Content-Length`；读取过程中累计超过 15 MiB 立即失败。
- 下载结果继续使用与 multipart 相同的签名检测、类型白名单、SHA-256 和私有落盘流程。
- URL 只以规范化 host 与 SHA-256 哈希进入审计；响应和普通日志不保留完整查询参数。

## 运行时与持久化

1. OpenAPI 文件先保存为 `agent_api_file` READY 记录和私有文件。
2. `AgentOpenApiRunService` 解析 external conversation 为内部 session 后，附件桥接器按四层作用域读取文件。
3. 若文件上传时未指定 conversation，首次引用在事务内绑定当前 external conversation；已绑定后跨会话统一返回不可枚举的 `FILE_NOT_FOUND`。
4. 桥接器通过 `ChatAttachmentService` 生成或复用当前 internal session 的受管附件记录，取得内部 attachment ID。
5. blocking/streaming 都把 attachment ID 传给 `ChatOrchestratorService`；后者执行可读性、Vision、文档抽取、模型 content block、会话持久化和 Trace。
6. OpenAPI 多轮复用允许读取已绑定消息的同一受管附件，但不得改变控制台上传附件的一次性提交语义。

## 错误契约

| HTTP | code | 场景 |
|---:|---|---|
| 400 | `INVALID_FILE_REFERENCE` | 结构、格式、重复或互斥字段错误 |
| 400 | `INVALID_FILE_URL` | URL 协议/结构错误 |
| 403 | `REMOTE_URL_FORBIDDEN` | 内网、保留地址或安全策略拒绝 |
| 404 | `FILE_NOT_FOUND` | 不存在或任一作用域不匹配，避免枚举 |
| 409 | `FILE_NOT_READY` | 文件未就绪 |
| 410 | `FILE_EXPIRED` | 过期或删除 |
| 413 | `FILE_TOO_LARGE` | 上传、下载或请求总量超限 |
| 415 | `UNSUPPORTED_FILE_TYPE` | 文件签名不受支持 |
| 422 | `REMOTE_FILE_FETCH_FAILED` | 远端状态、超时、重定向或读取失败 |
| 422 | `MODEL_CAPABILITY_MISMATCH` | 图片模型缺少 Vision |
| 422 | `FILE_PROCESSING_FAILED` | 解码或文档提取失败 |
| 502 | `ATTACHMENT_BINDING_FAILED` | READY 文件无法绑定模型请求 |

streaming 在业务 SSE 开始前完成附件解析；若后续运行时失败，`event:error` 必须携带稳定 code/request id，不输出内部异常。

## 测试与验收

- 单元：PNG/JPEG/WebP、PDF、TXT、DOCX 签名；伪造扩展名/MIME；重复/互斥引用；四层越权；过期/损坏；非 Vision 模型失败关闭。
- URL：非 HTTPS、user-info、IPv4/IPv6 私网、loopback/link-local/metadata、逐跳重定向、超限、超时、非 2xx、伪 MIME；失败时模型调用为零。
- 集成：multipart 与 URL 同内容形成等价内部附件；blocking/streaming 都将真实图片 content block 传给模型；同文件同会话多轮复用；跨会话/用户/Key/Agent 返回 `FILE_NOT_FOUND`。
- 真实模型：固定采购图片识别出“10 个笔记本、10 个日历、2 个白板”；另用无文字图片验证真实视觉描述。记录模型、版本、trace 和附件绑定状态。
- 本地开发环境：只从本地 `main` 明确提交构建 `:local` 制品，最小重建 backend，回读 `https://cici.localhost/` 路由、容器健康、重启次数和版本/commit 指纹。

## 发布与回滚

- 使用 `openapi.attachment-runtime-v2` 开关；关闭时任何携带附件的请求必须明确返回暂不可用，不允许退回旧的静默忽略行为。
- 本次只修改 AgentCiCi；无新增跨项目契约。
- 回滚数据库迁移前先关闭新入口并停止创建新格式记录；回滚应用时保留私有文件，避免不可逆删除，后续由受管清理任务回收孤立对象。
