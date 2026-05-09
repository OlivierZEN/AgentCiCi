# AgentCiCi 智能体平台实现设计方案（Java 21 / Qdrant / PostgreSQL / RabbitMQ / React）

## 1. 建设目标与边界

本项目目标是建设 AgentCiCi 企业级多组织智能体运行与治理平台，支持手机号登录、多模型接入、skills/MCP 工具编排、Agent Builder、RAG 知识增强、运行观测和外部系统集成，并满足企业级安全与隔离需求。AgentCiCi 是独立产品品牌，CloudCC、Salesforce、企业微信、飞书等均作为可接入的业务系统或渠道。

核心目标：

- 模型层可插拔：通过配置接入主流大模型（阿里云百炼、OpenAI、通义、DeepSeek 等）
- 能力层可扩展：复用现有 `skills` 与 `MCP` 服务处理系统内业务数据和流程
- 知识层可运营：后台管理知识库，支持文档上传与手工录入，通过 RAG 检索增强回答
- 多组织隔离：以组织为维度隔离用户、知识库和部分工具
- 安全可控：手机号 + 短信验证码登录、权限控制、审计、成本管理

## 2. 技术栈与部署形态

- 后端：Java 21 + Spring Boot 3.x（REST API）
- 编排与网关：自研 AI Orchestrator（与 **Spring Boot Web MVC** 同进程，REST 控制器调用编排服务；非 WebFlux 独立网关）
- 向量库：Qdrant（存储段落向量，HTTP REST、过滤检索简单，单机运维成本低）
- 元数据存储：PostgreSQL（多组织 + 业务元数据）
- 缓存：Redis（会话状态、验证码、短期上下文）
- 异步处理：RabbitMQ（文档解析/索引、审计异步写入）
- 前端：React + Vite（**助手端**与**管理后台**分路由、分登录入口，见 §4.5）
- 对外依赖：阿里云短信服务、各大模型 API（阿里云百炼等）

## 3. 总体架构

采用前后端分离、多组织支持的分层架构：

### 3.1 接入层（Client / Channel）

- **助手端（`/`）**：面向员工 / 普通用户；会话聊天、只读知识库列表（勾选参与 RAG）；与管理功能隔离。
- **管理后台（`/admin/login` → `/admin/*`）**：面向 **组织管理员（`ORG_ADMIN`）**；知识库维护、模型/工具/运维、用户与角色管理；独立登录与 Token 存储，不与助手端混用同一界面。
- 后续可扩展：内嵌助手组件（抽屉/悬浮）仍应对接同一套助手 API 与权限模型。
- 所有调用统一通过后端 API（Spring Boot）进入。

### 3.2 身份与多组织层

- 登录方式：手机号 + 阿里云短信验证码
- Token 机制：JWT；实现侧 claims 为 **`sub`**（用户 id）、**`org_id`**、**`roles`**（字符串角色列表，MVP 中单元素 `ORG_ADMIN` 或 `ORG_USER`）
- 多组织模型：
  - 单数据库多组织：所有数据存在同一个 PostgreSQL 实例
  - 通过 `org_id` 字段在表级隔离（知识库、文档、会话、工具授权等均带 `org_id`）
- 前后端所有接口必须携带并校验组织上下文（从 JWT 解出）

### 3.3 编排层（AI Gateway + Orchestrator）

- 会话管理：负责将用户消息、历史、工具调用结果等组织成模型输入
- 模型路由：
  - 按组织和场景选择默认模型
  - 支持不同组织配置不同模型和额度
- 工具编排：
  - 接收模型的工具调用意图（function calling）
  - 统一调度 `skills` / `MCP` / RAG 检索
- 策略控制：
  - 工具白名单 / 黑名单
  - Token 限制、超时、重试、降级（仅回答部分、不调用工具）

### 3.4 能力层（Tools：Skills / MCP / 内部 API）

- Skills Registry：
  - 注册每个工具的 `name`、`description`、`input_schema`、`output_schema`
  - 声明所属组织或是否为公共工具
  - 声明权限与风险等级（只读 / 写入 / 高风险）
- MCP Gateway：
  - 对接已有 MCP 协议服务
  - 负责鉴权、超时、日志
- 内部业务适配器：
  - 将现有 Rest/RPC API 封装成工具
  - 所有调用附带 `org_id` 与 `user_id`

### 3.5 知识层（RAG）

- 文档处理管道（异步，基于 RabbitMQ）：
  - 上传 -> 发送任务到 MQ -> Worker 解析与切片 -> 调用向量服务入库
- 检索服务：
  - 基于 Qdrant 执行向量检索
  - 结合 PostgreSQL 的关键字/标签过滤
  - 支持按 `org_id`、知识库 ID、标签过滤
- 知识后台（管理端）：
  - 多组织的知识库管理与文档管理（创建/改删 KB、上传/发布/删文档等）仅 **组织管理员（`ORG_ADMIN`）** 可调对应写接口。
- 助手端：
  - 普通用户（`ORG_USER`）可 **只读** `GET /kb`、`GET /kb/{kbId}/documents`，用于勾选参与对话 RAG；不可执行知识库写操作。

### 3.6 治理层（Security / Ops）

- 权限模型：RBAC；当前 MVP 落地为 **`ORG_ADMIN`（组织管理员）** 与 **`ORG_USER`（组织普通用户）**（JWT `roles` claim；接口侧通过注解/AOP 校验）。系统级「平台管理员」可在后续版本单独扩展。
- 审计：
  - 登录日志、会话日志、工具调用日志、知识命中记录
  - 异步写入 PostgreSQL 或日志系统
- 可观测性：
  - 接口延迟、成功率、token 消耗、模型调用错误
  - 按组织与模型维度汇总成本

## 4. 关键功能模块设计

### 4.1 用户登录与多组织

#### 4.1.1 手机号 + 短信验证码登录流程

1. 用户在登录页输入手机号、组织标识和当前开发阶段固定密码
2. 前端调用 `POST /auth/password/login`，后端：
   - 校验手机号格式、组织可用性与固定密码
   - 按手机号创建或复用全局 `user_account` 与 `account_login_identifier`
   - 按 `org_id + account_id` 创建或复用 `organization_member`
   - 生成 JWT（包含 `org_id`、`account_id`、`member_id`、`roles` 等；`sub` 当前为 `organization_member.id`）
   - 返回给前端；当前 SPA 将登录态存 **`localStorage`**（助手端键名 `cici_assistant_token`，管理端键名 `cici_admin_token`，互不覆盖）。生产环境可演进为 `httpOnly` Cookie + CSRF 等更强方案。
   - **Bootstrap 管理员手机号**：配置项 `app.auth.bootstrap-admin-mobiles`（逗号分隔）。**新建成员**时：命中则 `ORG_ADMIN`，否则 `ORG_USER`。**已存在成员**若当前为 `ORG_USER` 且手机号在名单内，**每次密码登录成功时会升为** `ORG_ADMIN`（便于先注册后补名单的场景）；名单内成员不会仅因登录被自动降级（降级需管理员在后台修改角色）。

#### 4.1.2 相关核心表

**当前 MVP 实现（Flyway `V1__init_auth_tables.sql` 等）**

- `org`：`id`、`name`、`status`
- `user_account`：全局自然人账号，当前以 `primary_mobile` 作为开发阶段主登录标识
- `account_login_identifier`：手机号等登录标识，当前已落地 `MOBILE`
- `organization_member`：`id`、`org_id`、`account_id`、**`role_code`**（`ORG_ADMIN` / `ORG_USER`）、`member_status`、组织内资料与 CloudCC 绑定字段；现有业务中的 `user_id` 值语义为 `organization_member.id`

**远期扩展（设计示意，尚未落库）**

- `org_setting`：每组织模型默认、RAG 策略等
- `user_role` / `role_permission`：更细粒度 RBAC 时再接

### 4.2 模型接入与配置中心

- `model_provider`：记录各模型提供商（如阿里云百炼、OpenAI）
- `model_instance`：具体可用模型（模型名、endpoint、apikey 引用）
- `org_model_config`：每个组织可用的模型及默认模型配置
- 功能：
  - 系统管理员可配置全局模型
  - 组织管理员可在允许范围内选择/调整本组织使用的模型
  - 支持设定调用上限与成本预警

### 4.3 Skills + MCP 工具编排

#### 4.3.1 工具统一规范

每个工具建议具备：

- `id` / `name` / `description`
- `org_scope`：`global` / `org-specific`
- `input_schema` / `output_schema`（JSON Schema）
- `auth_scope`（所需角色）
- `timeout_ms`
- `idempotent`
- `risk_level`（read / write / high）

#### 4.3.2 内置标准 skills 与组织自定义 skills/MCP

- 系统内置标准 skills（对最终用户不可见）：
  - 由平台统一维护，`org_scope = global`，用于时间日期、通用文本处理、统一用户画像查询等共性能力。
  - 在后台不可被组织管理员直接编辑，仅系统管理员可管理版本和启停。
- 组织自定义 skills / MCP：
  - 每个组织可以注册自己的业务工具，记录所属 `org_id`，`org_scope = org-specific`。
  - 仅在本组织后台可见与可配置，可绑定到特定场景或角色（如仅销售角色可用）。
- 调用可见性与覆盖策略：
  - Orchestrator 构造工具列表时：`工具集合 = 全局工具 + 当前组织工具`。
  - 若名称冲突，组织级工具优先，用于覆盖平台默认实现。
  - 所有工具调用统一附带 `org_id` 与 `user_id`，保证跨组织隔离与审计。
- 权限控制：
  - 系统管理员可查看/管理全部全局工具和所有组织的工具配置。
  - 组织管理员仅能管理本组织的自定义 tools/MCP，不可见其他组织内容与密钥。

#### 4.3.3 执行链路

1. Orchestrator 收到用户请求和历史上下文
2. 模型产生工具调用请求（function calling）
3. Orchestrator 校验：
   - 工具是否对当前组织与用户开放
   - 参数是否合法
4. 调用后端对应的 `skill` / MCP 服务
5. 将结果整理为结构化 JSON，并可选生成简要自然语言说明
6. 再次调用模型生成整合后的最终回复

### 4.4 RAG 知识库系统（多组织隔离）

#### 4.4.1 多组织知识库模型

- `knowledge_base`：
  - `id`
  - `org_id`
  - 名称、描述
  - 可见范围（全组织 / 指定角色 / 指定用户组）
  - 状态（草稿 / 发布 / 下线）
- `kb_document`：
  - `id`
  - `org_id`
  - `knowledge_base_id`
  - 源文件信息（名称、类型、存储路径）
  - 解析状态
- `kb_chunk`（文本分段）：
  - `id`
  - `org_id`
  - `knowledge_base_id`
  - `document_id`
  - 文本内容、标题、页码、tags
  - `vector_id`（Qdrant 中 point id）

所有表均以 `org_id` 进行逻辑隔离，RAG 检索时必须加上 `org_id` 和 `knowledge_base_id` 过滤条件。

#### 4.4.2 文档索引流程（异步）

1. 用户上传文档：
   - `POST /kb/documents/upload`，保存元信息到 PostgreSQL
   - 文件上传到对象存储（本地或 OSS）
   - 发送索引任务到 RabbitMQ（包含 `org_id`、documentId）
2. Worker 服务从 MQ 取任务：
   - 下载并解析文档（保留标题结构）
   - 文本清洗与切片
   - 调用向量化模型生成 embedding
   - 写入 Qdrant，并将 `vector_id` 等信息回写至 `kb_chunk`
3. 更新文档与知识库状态（已索引，可检索）

#### 4.4.3 检索与回答流程

1. Orchestrator 在处理用户问题时，根据会话配置决定是否启用 RAG
2. 通过 `org_id` + 当前会话绑定的知识库列表查询可用 KB
3. 生成查询 embedding，到 Qdrant 做向量召回
4. 在 PostgreSQL 中根据召回结果做权限与标签过滤
5. 对候选分段进行重排与截断，并拼接成上下文
6. 与用户问题一起输入到模型中，让模型生成带引用回答

### 4.5 前端 React 模块（双入口，与实现对齐）

| 入口 | 路由 | 受众 | 能力概要 |
|------|------|------|----------|
| 助手端 | `/` | `ORG_USER` / `ORG_ADMIN` 均可登录使用助手 | 手机号验证码登录；对话；**只读**知识库列表 + 多选参与 RAG |
| 管理后台 | `/admin/login`，业务页 `/admin/kb`、`/admin/models`、`/admin/tools`、`/admin/ops`、`/admin/users` | 仅 **`ORG_ADMIN`** | 知识库与文档全生命周期管理；模型/工具配置；观测运维（智能体运行、成本、审计）；**用户列表与角色变更** |

**鉴权与体验约定**

- 管理后台登录成功后，前端根据登录响应中的 `roles` 判断是否包含 `ORG_ADMIN`；不包含则拒绝写入管理 Token 并提示错误。
- 访问 `/admin` 下受保护路由时，用已存管理 Token 调用 `GET /auth/me` 再次校验 `ORG_ADMIN`，不通过则清除管理 Token 并跳转 `/admin/login`。
- 助手端与管理端 **不使用同一 Tab 混排控制台**；两侧通过页脚/文案链结互相跳转（`/admin/login` ↔ `/`）。
- 开发环境下 Vite 将 `/auth`、`/kb`、`/ai`、`/models`、`/tools`、`/ops`、**`/admin`** 等代理至后端（见 `frontend/vite.config.ts`）；前端本地存储键名见 `frontend/src/constants.ts`（`cici_assistant_token` / `cici_admin_token`）。

**后续扩展**

- 组织管理（仅平台/系统管理员）、内嵌助手组件（抽屉/悬浮）、会话列表与工具/知识命中可视化等，可在现路由结构上增量增加页面或子应用。

## 5. 接口设计（示例）

### 5.1 认证与组织

- `POST /auth/sms/send`：发送登录验证码
- `POST /auth/sms/login`：验证码登录，返回 token 及 `orgId`、`userId`、**`roles`** 等（与 JWT claims 一致）
- `GET /auth/me`：获取当前用户信息与组织信息（含 **`roles`**，供前端区分助手 / 管理权限）

### 5.2 会话与聊天

- `POST /ai/chat`：统一会话入口（包含会话 ID、问题、可选知识库列表），JSON 一次性返回 `answer`
- `POST /ai/chat/stream`：`Content-Type: text/event-stream`（SSE），按 token 推送 `event:delta`，`data` 为 JSON `{"text":"片段"}`，结束前 `event:done`；模型系统提示要求输出 **Markdown** 以便前端渲染
- `GET /ai/sessions`：查询当前用户的会话列表

### 5.3 知识库管理

- `GET /kb`：查询本组织知识库列表（登录用户；助手端与管理端均可用）
- `GET /kb/{kbId}/documents`：查询文档列表（同上）
- `POST /kb`、`PUT /kb/{id}`、`DELETE /kb/{id}`：知识库写操作（**需 `ORG_ADMIN`**）
- `POST /kb/documents/upload`、`POST /kb/documents/{id}/publish`、`DELETE /kb/documents/{id}` 及 chunk 写入等：**需 `ORG_ADMIN`**

### 5.4 工具与模型配置

- `GET /models` / `POST /models` / `DELETE /models`：模型配置（当前实现为控制器级 **`ORG_ADMIN`**，含列表查询）
- `GET /tools` / `POST /tools` / `DELETE /tools`：工具注册与配置（同上，**`ORG_ADMIN`**）

### 5.5 运维与审计

- `GET /ops/audit/logs`：审计日志（**需 `ORG_ADMIN`**）
- `GET /ops/metrics/cost`：调用量与成本（**需 `ORG_ADMIN`**）
- `GET /admin/agents/run-logs`：组织级智能体最近 7 天运行日志（**需 `ORG_ADMIN`**）
- `GET /admin/agents/run-logs/{traceId}`：组织级单次运行链路详情（**需 `ORG_ADMIN`**）

### 5.6 组织用户管理（管理后台）

- `GET /admin/users`：本组织用户列表（**需 `ORG_ADMIN`**）
- `PUT /admin/users/{userId}/role`：变更用户角色，body 示例 `{ "roleCode": "ORG_ADMIN" }` 或 `"ORG_USER"`（**需 `ORG_ADMIN`**；后端禁止唯一管理员将自己降为普通用户）

## 6. 非功能与安全要求

- 性能：
  - 首 token < 2s（普通问答）
  - 含工具调用 / RAG 的问答 < 6s（优化目标）
- 可靠性：
  - 所有外部调用（模型、短信、MCP）通过客户端封装，具备超时、重试和熔断
  - 文档索引采用 MQ 保证削峰与重试
- 安全：
  - 短信验证码存储在 Redis，采用限流和错误次数上限
  - API Key、短信密钥、模型密钥统一通过配置中心或 KMS 管理
  - 敏感字段脱敏存储与展示
  - Prompt Injection 防护：系统提示词与用户输入分离、仅向模型暴露必要上下文
- 多组织隔离：
  - 所有数据库查询必须带 `org_id` 约束
  - 知识库、工具、会话、审计等均以组织维度隔离

## 7. AI 视角开发步骤（全 AI 开发模式）

以下步骤以“AI 主导研发”为原则：需求拆解、代码生成、测试生成、评审、回归与文档同步均由 AI 深度参与，人类负责业务决策、验收与风险把关。

### Step 0：AI 开发基线搭建

- 建立统一 AI 开发规范：提示词模板、代码风格、接口命名、提交规范。
- 建立 AI 上下文资产：系统术语表、领域模型词典、现有 MCP/skills 能力清单。
- 接入 AI 编码与评审流水线：生成代码、生成测试、静态检查、自动评审报告。
- 产出可执行交付清单：按“能力切片”拆成可由 AI 独立完成的任务包。

### Step 1：AI 先行的核心骨架交付

- AI 生成后端基础工程（Java 21）、鉴权中间件、`org_id` 上下文拦截器、统一异常与审计骨架。
- AI 生成登录模块：手机号验证码登录、阿里云短信客户端封装、限流与风控规则。
- AI 生成知识库基础链路：文档上传、RabbitMQ 任务投递、Qdrant/PostgreSQL 最小打通。
- AI 生成 React 基础界面：助手端登录与会话页；管理后台独立登录与分栏布局（知识库/模型/工具/运维/用户管理）。
- 人工只做关键验收：登录安全、组织隔离、核心接口契约正确性。

### Step 2：AI 编排能力与业务可用化

- AI 完成 Orchestrator 主流程：会话编排、模型路由、工具调用、降级策略。
- AI 完成“内置标准 skills + 组织自定义 skills/MCP”注册中心与覆盖策略。
- AI 完成 RAG 检索增强：向量召回、过滤、重排、引用输出。
- AI 批量生成接口测试与集成测试（登录、RAG、tools、org 隔离）。
- 人工进行场景验收：典型业务问答、工具调用稳定性、答案可追溯性。

### Step 3：AI 驱动质量收敛与上线

- AI 自动执行回归并生成缺陷聚类报告（按模块、严重级、复现率）。
- AI 执行性能调优建议：慢查询、高延迟接口、模型超时路径优化。
- AI 执行安全巡检：越权访问、注入风险、敏感信息泄漏检查。
- AI 自动整理上线文档：部署手册、运维手册、故障应急 Runbook、变更说明。
- 人工完成最终 Go/No-Go 决策与灰度发布。

### Step 4：AI 运营与持续进化

- AI 监控生产数据并输出周报：命中率、未解决问题、成本趋势、工具成功率。
- AI 根据日志自动提出优化 PR：提示词、检索参数、重排策略、缓存策略。
- AI 驱动知识库健康治理：低质量文档识别、过期内容提醒、缺失知识补全建议。
- AI 支持新组织快速开通：自动初始化模型配置、默认工具、权限模板。

## 8. 下一步可交付项与本地验收

**文档同步约定**：本文件与仓库根目录 `README.md`、`.claw/current-status.md` 及 `scripts/e2e-local-business.sh` 说明应同时更新，避免入口 URL、权限与配置描述漂移。

### 8.1 仍可按优先级补强的文档与规范

- 详细数据库表结构文档（字段、索引、多组织策略）
- 关键时序图（登录、聊天+RAG、工具调用）
- MVP 版本 OpenAPI 规范（可由 SpringDoc 等工具生成）
- 安全与合规检查清单（短信服务、防刷、密钥管理）；仓库内可参考 `scripts/` 与 `.claw/` 中的运维与测试记录

### 8.2 本地全链路业务验收（已实现）

前置：`docker compose up -d`（PostgreSQL、Redis、RabbitMQ、Qdrant），后端 `local` Profile（默认 `app.kb.vector-store=qdrant`、`indexing.mode=mq`）。

| 步骤 | 说明 |
|------|------|
| 一键拉起并验收 | `./scripts/run-full-demo.sh`（启动依赖、后台启动后端、执行 E2E、可选启动前端） |
| 仅 API 验收 | 后端已在 `8080` 时执行 `./scripts/e2e-local-business.sh` |
| 向量库单测 | `./scripts/verify-qdrant-stack.sh`（需 Qdrant 监听 `6333`） |
| 构建门禁 | `./scripts/quality-check.sh` |

验收脚本覆盖：**短信登录（默认使用 `bootstrap-admin-mobiles` 中的手机号，保证新建用户为 `ORG_ADMIN`，从而可通过 KB 写接口）→ 创建知识库 → 上传文本 → 发布 → MQ 异步索引至 PUBLISHED → 带 `knowledgeBaseIds` 的聊天并校验 RAG 上下文命中唯一标记串**。

### 8.3 演示组织与登录方式

- 组织 `demo-org` 由后端启动时种子数据创建（`AuthBootstrapData`）。
- 任意**未触发短信频控**的手机号均可完成短信登录；响应中的 **`devCode` 即为验证码**（本地/联调用；生产应对接真实短信且勿返回 `devCode`）。
- **助手端**（`/`）默认示例：`demo-org` + `13800138111`。
- **管理后台**（`/admin/login`）默认示例：`demo-org` + `13900009999`（与 `application-local.yml` 中 `app.auth.bootstrap-admin-mobiles` 列表示例一致，便于首登即为 `ORG_ADMIN`）。
- 若某手机号已曾注册为 `ORG_USER`，仅改配置不会自动升为管理员，需由现有管理员在 **用户管理** 中改角色，或使用未注册过的 bootstrap 手机号新建用户。
