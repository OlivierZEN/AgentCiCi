---
kind: feature-spec
feature_id: FEAT-024
title: Account, tenant lifecycle, and data retention
status: implemented
owner_role: product-architecture-auth
task_ids: TASK-069
related_decisions: DEC-024, DEC-025
related_issues: none
updated_at: 2026-05-21T12:24:00Z
updated_by: MANAGER-001
---

# FEAT-024 - Account, tenant lifecycle, and data retention

## 背景与目标

AgentCiCi 当前账号体系仍以组织内用户为主，`app_user` 同时承载 `org_id`、手机号和角色。这个模型适合早期内部验证，但不适合 AgentCiCi 作为独立企业级产品长期发展。

本规格沉淀一次产品架构调整：AgentCiCi 的身份体系应升级为“全局个人账号 + 多登录标识 + 组织成员关系 + 组织生命周期 + 数据保留与销毁”。目标是让 AgentCiCi 支持公开注册、受控开通、企业邀请、多组织切换、SSO/OAuth 扩展、组织级订阅、席位计费和到期数据回收，同时保持企业数据边界清楚。

核心结论：

- 一个自然人对应一个全局 `user_account`。
- 手机号、邮箱、用户名、Google/Gmail、飞书、钉钉、企微等只是这个人的登录标识或外部身份。
- 一个全局用户可以加入多个组织，也可以在规则允许下创建多个组织。
- 角色、席位、部门、组织内状态和企业权限属于 `organization_member`，不属于全局账号。
- 订阅约束组织空间和组织能力，不直接约束全局账号。
- 组织停止订阅后必须进入可审计的数据导出、冻结和最终销毁流程，组织业务数据不能长期默认保留。

## 范围

### In Scope

- 全局账号与组织成员关系的目标模型。
- 多登录标识和多认证方式的数据模型。
- 公开注册、登录、组织选择、创建组织、加入组织的产品流程。
- 组织 Owner、管理员、普通成员的权限边界。
- 订阅状态、组织状态和成员状态的生命周期。
- 组织到期后的导出、冻结、销毁和审计摘要保留策略。
- 从现有 `app_user(org_id, mobile, role_code)` 的迁移方向。
- 与 FEAT-003、FEAT-010、FEAT-020、FEAT-021、FEAT-022、FEAT-023 的边界关系。

### Out Of Scope

- 本规格不直接实现数据库迁移、接口或前端页面。
- 不定义最终商业价格、合同模板、发票流程或支付渠道。
- 不实现完整 SSO、SCIM、企业通讯录同步或 OAuth 提供商接入，只保留模型扩展点。
- 不设计个人消费版或 C 端社区产品。
- 不改变当前 FEAT-023 企业微信客服消息流的首版实现，只为后续账号域迁移提供身份边界。

## 用户场景

- 新客户首次访问 AgentCiCi，使用手机号或邮箱注册，系统创建全局账号、默认组织和创建者成员身份。
- 已注册用户再次使用同一手机号注册，系统不创建第二个全局账号，而是提示登录后创建新组织或加入已有组织。
- 一个用户同时属于 A 公司和 B 公司，登录后可选择进入哪个组织；在 A 公司是 Owner，在 B 公司只是普通成员。
- 组织管理员邀请已有 AgentCiCi 用户加入组织，系统只新增成员关系，不重复创建用户。
- 企业付费到期后，组织从正常使用进入宽限、冻结、待销毁和最终销毁状态；用户全局账号仍可访问其他组织。
- 组织被销毁后，组织业务数据、凭证、知识库、向量、会话、消息、Open API Key、企业微信客服数据被删除或匿名化；平台只保留合规和账务所需的最小摘要。
- 用户绑定 Google/Gmail 登录后，后续可用 Google OAuth 登录同一个全局账号；OAuth 身份以 provider subject 为准，不以邮箱字符串作为最终主键。

## 现状与约束

- 当前 `app_user` 是组织内用户表，字段包含 `org_id`、`mobile`、`role_code`，角色与组织强绑定。
- 当前登录阶段已支持账号级密码凭证最小闭环；未设置个人密码的历史账号仍兼容 FEAT-020 固定密码方案，不适合作为长期公网生产方案。
- README 与 `.claw/goals.md` 仍描述 `JWT with org_id and roles`、`org-scoped users in app_user.role_code`。
- 现有大量业务表以 `org_id + user_id` 作为数据边界，包括会话、消息、用户记忆、邮箱、个人工作流、Open API run-as 用户等。
- 所有租户敏感数据路径必须继续携带 `org_id`。账号域升级不能削弱组织隔离。
- 组织级计费和工作量模型已在 FEAT-003 与 FEAT-022 中确认，账单主体是组织，不是个人。
- 平台运营控制面 FEAT-010 已定义租户状态、套餐、订阅和平台审计雏形，本规格在此基础上补齐组织数据生命周期。

## 方案设计

### 1. 核心身份模型

目标模型：

```text
user_account
  全局自然人账号

account_login_identifier
  手机号、邮箱、用户名等可输入登录标识

account_auth_credential
  密码、短信验证码、邮箱验证码、Passkey、MFA 等认证凭证

account_external_identity
  Google、Microsoft、飞书、钉钉、企微、企业 SSO 等外部身份

organization
  企业、团队或租户空间

organization_member
  某个全局用户在某个组织内的成员身份

organization_subscription / entitlement
  组织级套餐、订阅、额度和可用能力

organization_retention_policy / purge_job
  组织到期、导出、冻结、销毁和审计任务
```

身份心智：

```text
全局账号存在 != 拥有某个组织的使用权
组织成员有效 != 组织订阅有效
手机号唯一 != 组织成员唯一
```

### 2. `user_account`

`user_account` 只承载全局个人主体和全局资料，不承载企业权限。

建议字段：

```text
user_account
- id
- display_name
- avatar_base64 或 avatar_asset_id
- locale
- timezone
- status: ACTIVE / LOCKED / DELETED / MERGED
- created_at
- updated_at
```

不要把 `org_id`、`role_code`、企业部门、企业职位、CloudCC 绑定凭证放在 `user_account`。

### 3. 多登录标识

手机号、邮箱、用户名等可输入标识放在 `account_login_identifier`：

```text
account_login_identifier
- id
- account_id
- identifier_type: MOBILE / EMAIL / USERNAME
- normalized_value
- display_value
- verified_at
- is_primary
- status: ACTIVE / PENDING_VERIFY / DISABLED / REMOVED
- created_at
- updated_at
```

唯一性：

```text
unique(identifier_type, normalized_value) where status = ACTIVE
```

规范化规则：

- 手机号需要国家码和纯数字规范化，避免 `138...` 与 `+86 138...` 被当成两个人。
- 邮箱需要小写、去空格，并保留原始展示值。
- 用户名应限制字符集、长度和保留字，不允许与手机号、邮箱格式混淆。
- 本阶段邮箱登录标识来源于个人简档中的邮箱字段：用户在 `PUT /auth/me/profile` 保存有效邮箱后，系统同步维护 `account_login_identifier(EMAIL, normalized_email)`，后续登录页同一个账号输入框自动识别手机号或邮箱，不拆分两个输入框。
- 清空或更换个人简档邮箱时，应同步删除或更新该账号的 ACTIVE EMAIL 标识；同一邮箱不能被另一个 ACTIVE 全局账号占用。

### 4. 认证凭证

认证凭证与登录标识分离：

```text
account_auth_credential
- id
- account_id
- credential_type: PASSWORD / SMS_OTP / EMAIL_OTP / PASSKEY / TOTP
- secret_hash
- public_key_json
- algorithm
- status: ACTIVE / DISABLED / ROTATED
- last_used_at
- created_at
- updated_at
```

设计原则：

- 登录标识回答“我是谁”，认证凭证回答“我如何证明我是我”。
- 同一个账号可同时拥有密码、短信验证码、邮箱验证码、Passkey 和 MFA。
- FEAT-020 的全局固定密码只能作为内部阶段兼容方案；新个人密码已进入 `account_auth_credential`，生产化时仍需补强密码策略、重置流程、MFA 或企业 SSO。

### 5. 外部身份绑定

OAuth 和企业身份源放在 `account_external_identity`：

```text
account_external_identity
- id
- account_id
- provider: GOOGLE / MICROSOFT / FEISHU / DINGTALK / WECOM / SAML / OIDC
- provider_subject
- provider_tenant_id
- email_snapshot
- mobile_snapshot
- display_name_snapshot
- profile_json
- verified_at
- status: ACTIVE / DISABLED / REMOVED
- created_at
- updated_at
```

唯一性：

```text
unique(provider, provider_tenant_id, provider_subject)
```

原则：

- Google/Gmail 登录的稳定主键是 Google 返回的 `sub`，不是邮箱字符串。
- 飞书、钉钉、企微要区分个人级 open id、union id 和企业 tenant/corp id。
- 邮箱匹配只能作为绑定辅助，不作为 OAuth 的最终身份主键。
- 第一次外部登录时，如果 provider identity 未绑定，可以根据已验证邮箱做候选匹配，但需要用户确认或安全策略兜底。

### 6. 组织成员关系

`organization_member` 是组织权限和席位的事实源：

```text
organization_member
- id
- org_id
- account_id
- role_code: OWNER / ORG_ADMIN / ORG_USER
- seat_type: NONE / OPERATOR / BUILDER
- status: INVITED / ACTIVE / SUSPENDED / LEFT / REMOVED / EXPIRED
- display_name
- department
- title
- employee_no
- invited_by
- joined_at
- left_at
- created_at
- updated_at
```

唯一性：

```text
unique(org_id, account_id) where status in (INVITED, ACTIVE, SUSPENDED)
```

角色边界：

- `OWNER`: 组织创建者或法定拥有者。可处理账单、转让组织、关闭组织、发起数据导出和销毁。
- `ORG_ADMIN`: 管理组织成员、Agent、知识库、模型、工具、集成、运行观测和审计。
- `ORG_USER`: 使用助手、被授权 Agent、个人设置和个人工作流。

`OWNER` 不应只是 `ORG_ADMIN` 的别名。组织销毁、订阅、付款主体和所有权转让应由 `OWNER` 或平台运营处理。

### 7. 组织创建与公开注册

公开注册时的首轮流程：

```text
用户输入手机号/邮箱/用户名 + 验证方式
  -> 创建 user_account
  -> 创建第一个 organization
  -> 创建 organization_member
  -> member.role_code = OWNER
  -> member.seat_type = BUILDER
  -> 创建试用 subscription / entitlement
  -> 签发当前组织 token
```

同一手机号或邮箱再次注册：

```text
识别 account_login_identifier 已存在
  -> 不创建第二个 user_account
  -> 提示登录
  -> 登录后可创建新组织或加入已有组织
```

允许同一个全局用户创建多个组织，但必须有上限和风控：

- 默认每个全局用户最多创建 1 到 3 个试用组织。
- 付费、人工认证、企业邮箱认证或平台运营批准后可提高上限。
- 同一手机号、邮箱、设备、IP、支付主体、OAuth subject 应有反滥用限制。
- 每个组织必须有唯一 `org_id` 或 `org_slug`。
- 创建组织时要写入组织来源、创建者、试用额度和数据保留策略。

### 8. 登录与组织选择

普通登录流程：

```text
输入手机号/邮箱/用户名
  -> 规范化 identifier
  -> 查 account_login_identifier
  -> 找到 user_account
  -> 校验 password / otp / passkey
  -> 查询 organization_member 列表
  -> 如果只有一个可进入组织，直接进入
  -> 如果有多个组织，展示组织选择器
  -> 选择组织后签发 org-scoped token
```

OAuth 登录流程：

```text
OAuth callback
  -> 用 provider + provider_tenant_id + provider_subject 查 account_external_identity
  -> 找到 user_account
  -> 如果未绑定，则进入安全绑定或创建流程
  -> 查询 organization_member 列表
  -> 选择组织
  -> 签发 org-scoped token
```

JWT 建议 claims：

```text
account_id
org_id
member_id
roles
seat_type
platform_roles
```

`org_id` 仍然必须存在，因为现有业务隔离依赖组织上下文。新增 `account_id` 与 `member_id` 是为了清楚区分自然人和组织成员身份。

### 9. 组织内个人数据归属

以下数据应归属于组织成员身份，而不是全局账号：

- 会话历史与消息。
- 用户记忆。
- 个人工作流、快捷指令和触发器。
- 邮箱账号绑定。
- CloudCC 用户绑定。
- run-as 用户。
- 审计日志中的操作者。

推荐语义：

```text
org_id + member_id
```

短期兼容可以继续使用旧 `user_id` 字段，但代码语义要逐步改成 `member_id`。不要让同一个全局账号在不同组织之间共享业务记忆、业务凭证或会话上下文。

### 10. 订阅与组织生命周期

组织状态建议：

```text
organization.status
- TRIALING
- ACTIVE
- PAST_DUE
- SUSPENDED
- PENDING_PURGE
- PURGED
- CLOSED
```

语义：

- `TRIALING`: 试用中，受低额度和能力限制。
- `ACTIVE`: 正常使用。
- `PAST_DUE`: 欠费或到期宽限期，可登录、导出、续费，限制高成本能力。
- `SUSPENDED`: 主要业务能力停止，组织管理员和 Owner 仍可登录处理续费、导出或关闭。
- `PENDING_PURGE`: 已进入销毁等待期，普通成员不可进入，平台运营或 Owner 可取消或确认销毁。
- `PURGED`: 组织业务数据已销毁，不可恢复。
- `CLOSED`: 主动关闭后的非运行状态，可与 `PENDING_PURGE` 配合。

成员状态与组织状态独立：

```text
user_account ACTIVE
organization A PURGED
organization B ACTIVE
member in A REMOVED / EXPIRED
member in B ACTIVE
```

用户仍然可登录并进入 B 组织，不能访问 A 组织数据。

### 11. 组织数据导出与销毁

组织停止订阅后不能长期默认保留业务数据。建议生命周期：

```text
ACTIVE
  正常使用

PAST_DUE
  到期宽限期，例如 7 到 15 天
  限制高成本能力，允许续费和导出

SUSPENDED
  冻结期，例如 15 到 30 天
  只允许 Owner / 管理员导出、续费、关闭组织

PENDING_PURGE
  销毁等待期，例如 7 天
  只允许平台运营取消销毁或客户确认导出

PURGED
  组织业务数据已销毁，不承诺恢复
```

试用组织可以更短：

```text
Trial 到期后 7 天 SUSPENDED
再 7 天 PENDING_PURGE
之后 PURGED
```

付费企业按合同：

```text
到期后 30 天导出期
再 30 天销毁等待期
也可购买更长数据保留策略
```

必须清理或匿名化的组织业务数据：

- `organization_member` 组织成员关系，保留最小销毁摘要。
- `chat_session`、`chat_message`。
- 用户记忆、个人工作流、快捷指令。
- `knowledge_base`、`kb_document`、`kb_chunk`、源文件、向量库 points。
- Agent、Skill、Workflow、发布版本和运行 trace 明细。
- 模型、工具、MCP、集成应用、邮箱、CloudCC 绑定等组织凭证。
- Open API Key、外部会话映射、调用日志明细。
- 企业微信客服账号、会话、消息。
- 文件存储、对象存储、Qdrant / memory vector store 中的 org scoped 数据。

可有限保留的最小摘要：

- 组织 ID、组织名称快照、销毁时间。
- 合同、订阅、发票、付款记录。
- 用量汇总：月份、credits、金额，不含 prompt、消息正文、工具参数。
- 审计摘要：谁在什么时候发起停用、导出、销毁。
- 销毁任务 manifest 和结果。

销毁任务模型：

```text
organization_retention_policy
- org_id
- grace_until
- suspend_until
- export_deadline
- purge_after
- legal_hold
- legal_hold_reason
- legal_hold_approved_by
- legal_hold_approved_at
- legal_hold_review_at
- policy_source
- created_at
- updated_at

organization_purge_job
- id
- org_id
- status: SCHEDULED / RUNNING / PARTIAL_FAILED / SUCCEEDED / CANCELED
- phase
- requested_by
- scheduled_at
- started_at
- finished_at
- error_message
- manifest_json
- source_dry_run_job_id
- confirmation_text
- manifest_version
- manifest_hash
- result_json
- created_at
- updated_at

organization_export_job
- id
- org_id
- status: RUNNING / SUCCEEDED / FAILED
- requested_by
- reason
- file_path
- manifest_json
- error_message
- started_at
- finished_at
- created_at
- updated_at
```

`manifest_json` 示例：

```json
{
  "deleted_chat_messages": 12422,
  "deleted_kb_documents": 86,
  "deleted_vectors": 18403,
  "deleted_files": 86,
  "deleted_api_keys": 12,
  "deleted_traces": 902
}
```

### 12. 安全与合规原则

- 全局账号不能绕过组织成员关系访问组织数据。
- 组织销毁不能删除用户在其他组织的数据。
- 组织销毁前必须提供明确通知、导出窗口和最终不可恢复提示。
- `PENDING_PURGE` 期间应阻断新业务写入，避免销毁竞态。
- `legal_hold=true` 时暂停自动销毁，但要记录原因、审批人和到期复核时间。
- 平台支持人员不能查看明文业务消息、工具参数或知识库正文，除非有单独的支持授权和审计。
- 所有删除任务必须幂等，失败可重试。
- 向量删除失败不能导致已删除数据继续被检索，RAG 必须以 DB 状态过滤作为最终闸门。

## 接口与数据影响

### 建议新表

```text
user_account
account_login_identifier
account_auth_credential
account_external_identity
organization_member
organization_retention_policy
organization_purge_job
```

### 开发期一次性迁移方向

现有：

```text
app_user(id, org_id, mobile, role_code, nickname, avatar_base64, ...)
```

目标：

```text
user_account(id, display_name, avatar, ...)
account_login_identifier(account_id, MOBILE, mobile)
organization_member(id, org_id, account_id, role_code, ...)
```

用户已确认当前仍是开发阶段系统、尚未上线，无需保留历史兼容。因此首轮实现不再保留 `app_user` 作为兼容成员表：

1. 认证初始化迁移直接创建 `user_account`、`account_login_identifier` 与 `organization_member`。
2. `UserRepository` 背后的持久化表切换为 `organization_member`。
3. 登录按手机号创建或复用 `user_account`，再按 `org_id + account_id` 创建或复用 `organization_member`。
4. JWT `sub` 继续放组织成员 ID，并新增 `account_id` 与 `member_id` claims。
5. 现有对外 `userId` 响应字段短期保留，但语义明确为 `organization_member.id`；新响应同步返回 `memberId` 与 `accountId`。
6. 现有业务表的 `user_id` 字段在本阶段不批量改名，语义统一改为 member id；后续可在无功能风险时再做字段命名清理。
7. Agent Open API 和企业微信客服中的 `run_as_user_id` 本阶段继续保留字段名，值必须是同组织 `organization_member.id`。

### 建议接口

认证与账号：

```text
POST /auth/register
POST /auth/login
GET  /auth/organizations
POST /auth/switch-organization
GET  /auth/me
POST /auth/identifiers
DELETE /auth/identifiers/{id}
POST /auth/external/google/callback
POST /auth/external/{provider}/bind
DELETE /auth/external/{id}
```

组织：

```text
POST /organizations
GET  /organizations/{orgId}
POST /organizations/{orgId}/members/invite
PATCH /organizations/{orgId}/members/{memberId}
POST /organizations/{orgId}/transfer-owner
POST /organizations/{orgId}/close
```

数据生命周期：

```text
GET  /admin/organization/export
POST /admin/organization/export-jobs
GET  /admin/organization/export-jobs/{id}
POST /admin/organization/close-request

GET  /platform/tenants/{orgId}/retention
PATCH /platform/tenants/{orgId}/retention
POST /platform/tenants/{orgId}/suspend
POST /platform/tenants/{orgId}/resume
POST /platform/tenants/{orgId}/purge-jobs
GET  /platform/tenants/{orgId}/purge-jobs/{jobId}
POST /platform/tenants/{orgId}/purge-jobs/{jobId}/cancel
POST /platform/tenants/{orgId}/purge-jobs/{jobId}/retry
```

### 与既有规格关系

- FEAT-003: 继续确认账单主体是组织，本规格补充组织账号和生命周期。
- FEAT-010: 平台运营控制面应承载租户状态、订阅、暂停、恢复和销毁任务。
- FEAT-020: 固定密码登录是内部阶段兼容方案，当前已由 `account_auth_credential` 承接用户修改后的个人密码。
- FEAT-021: Agent Open API 的 run-as user 应迁移为 run-as member。
- FEAT-022: 席位和 credits 归属于组织，席位应统计 `organization_member`。
- FEAT-023: 企业微信客户是外部身份和会话主体，不创建 AgentCiCi 内部全局账号。

## 任务拆分

- `TASK-069A`: 账号域 schema 设计与迁移计划。（已开始：开发期一次性下线 `app_user`）
- `TASK-069B`: 全局账号、多登录标识、密码凭证后端最小闭环。（已实现：固定密码登录创建/复用 mobile account，用户修改后使用 `account_auth_credential` 账号级密码）
- `TASK-069B-1`: 邮箱登录标识闭环。（已实现：个人简档邮箱同步 EMAIL 标识，登录账号输入框自动识别手机号或邮箱）
- `TASK-069C`: 组织成员关系与 org-scoped token 改造。（已开始：JWT 增加 `account_id/member_id`）
- `TASK-069D`: 注册、登录、组织选择、创建组织、切换组织前端流程。
- `TASK-069E`: 组织 Owner、邀请、成员停用、组织转让。（已实现首批管理端闭环）
- `TASK-069F`: 订阅状态与组织状态联动。
- `TASK-069G`: 数据导出与 purge job 后台任务。（已实现平台侧 dry-run manifest、组织导出 job、平台禁止下载、组织管理员下载、真实 purge 二次确认、排队状态、定时后台 worker、worker lease 抢占、死信标记、取消排队任务和执行摘要）
- `TASK-069H`: 组织数据清理覆盖 KB、向量、文件、会话、trace、Open API、企业微信和凭证域。（已实现首版 DB 删除、已登记 KB 文件删除、VectorStoreClient 删除与 `PARTIAL_FAILED` 摘要）
- `TASK-069I`: 旧 `app_user` 兼容层下线计划。

## 验收标准

- 同一手机号、邮箱或用户名不能创建多个全局账号。
- 一个全局账号可以创建多个组织，也可以加入多个组织。
- 登录后多组织用户必须进入组织选择或保留最近组织选择。
- JWT 同时包含 `account_id`、`member_id`、`org_id` 和当前组织角色。
- 同一个人在不同组织中的角色、席位、组织内资料互不影响。
- 组织到期后按状态限制功能，并允许在导出期内导出数据。
- 组织进入 `PURGED` 后，业务数据、向量、文件和凭证不再可访问或检索。
- 组织销毁不影响用户全局账号和其他组织数据。
- 平台保留的账务和审计摘要不包含 prompt、消息正文、知识库正文、工具参数或密钥。
- 所有 purge job 可重试、可审计、可输出 manifest。

## 风险与回滚

- 风险：一次性迁移 `app_user` 影响面大。缓解：当前项目未上线，允许直接改初始化迁移；验证重点放在认证、run-as、管理端用户和 org-scoped 查询。
- 风险：`user_id` 语义混乱导致跨组织数据泄露。缓解：新代码统一命名 `accountId` 与 `memberId`，所有 org scoped 查询必须带 `org_id`。
- 风险：组织销毁误删。缓解：状态机、导出期、二次确认、Owner 权限、平台审批、`legal_hold` 和 purge manifest。
- 风险：向量或文件清理失败。缓解：DB 状态过滤作为最终闸门，清理失败进入 `PARTIAL_FAILED` 并可重试。
- 风险：OAuth 邮箱匹配错误。缓解：provider subject 为主键，邮箱只做候选绑定，敏感场景需要用户确认。

回滚方式：

- 当前公网环境仍属于 UAT 公测阶段，尚未正式生产上线；如 UAT 环境需要回滚或重置，允许全新部署并重建数据库、文件存储和向量库，不要求保留历史业务数据迁移路径。
- 新 token 已携带 `account_id/member_id`；旧接口继续使用 `userId` 参数名时，其值必须解释为 `organization_member.id`。
- purge job 首期支持 dry run、manifest 预览、guarded real purge、排队 worker、worker lease 抢占、死信标记、取消排队任务和失败重试；生产化仍需补独立 worker 进程、更完整的外部存储巡检和运营告警。

## 实现进展

- 当前状态：账号多组织最小闭环、成员治理首批闭环、平台侧组织生命周期 dry-run manifest、组织导出 job、legal hold 审批元数据和 guarded real purge 首版已实现。
- 已完成项：产品架构、数据模型、登录流程、多组织流程、订阅生命周期、组织数据销毁策略；后端 `V1__init_auth_tables.sql` 已下线 `app_user` 并创建 `user_account`、`account_login_identifier`、`organization_member`；`UserEntity`/`UserRepository` 已映射到 `organization_member`；固定密码登录已创建/复用全局账号和组织成员；JWT 已新增 `account_id` 与 `member_id`；`/auth/me` 与登录响应已返回 `accountId/memberId`。
- 本批次个人账号完成项：新增 `V47__account_profile_and_password.sql`，`user_account` 支持 `first_name`、`last_name`、`email`；新增 `account_auth_credential` 账号级密码凭证；`PUT /auth/me/profile` 支持当前用户维护姓、名、显示名称、手机号和邮箱，`PUT /auth/me/password` 支持将账号切换到个人密码登录；无个人密码的账号继续兼容固定密码。
- 本批次邮箱登录完成项：`PUT /auth/me/profile` 保存邮箱时同步维护 `account_login_identifier` 的 ACTIVE EMAIL 标识；清空邮箱会删除该 EMAIL 标识；`POST /auth/password/login` 兼容旧 `mobile` 字段并新增 `identifier` 字段，服务端按单个账号输入值自动识别手机号或邮箱；邮箱按小写规范化匹配，登录页保持单个“电子邮件地址或手机号码”输入框，不拆分两个框。
- 本批次新增完成项：`POST /auth/register` 支持新手机号创建首个组织和 `OWNER` 成员；`POST /auth/password/login` 支持无 `orgId` 登录，单组织直接进入、多组织返回组织选择；`GET /auth/organizations`、`POST /auth/switch-organization`、`POST /auth/organizations` 支持登录态组织列表、切换组织和创建组织；助手端登录页已移除组织 ID 输入，支持注册创建组织、多组织选择和登录后轻量组织菜单；管理端 `/admin/login` 同步改为账号优先登录，多组织管理员先选组织再进入后台；`OWNER` 具备组织管理权限。
- 本批次追加完成项：`POST /admin/users/invitations` 支持按手机号添加组织成员并复用/创建全局账号；`POST /admin/users/{id}/suspend`、`/restore` 支持成员停用与恢复；`POST /admin/users/{id}/transfer-owner` 支持 Owner 转让，且停用唯一 Owner、停用当前登录成员和普通角色编辑 Owner 会被拒绝；登录、`/auth/me` 和组织切换只接受 `ACTIVE` 成员；管理端用户页已接入新增成员、停用/恢复和转让 Owner，移动端用户页改为上下结构避免详情面板横向溢出。
- 本批次生命周期完成项：新增 `organization_retention_policy` 与 `organization_purge_job`；新增平台 API `GET /platform/tenants`、`GET/PATCH /platform/tenants/{orgId}/retention`、`POST /platform/tenants/{orgId}/suspend`、`POST /platform/tenants/{orgId}/resume`、`POST /platform/tenants/{orgId}/purge-jobs` 和 `GET /platform/tenants/{orgId}/purge-jobs/{jobId}`；首版 dry-run manifest 只返回每个 org-scoped 数据域的表级计数与不支持域说明，不包含消息正文、记忆正文、工具参数或密钥；平台 `/platform/tenants` 页面已接入租户列表、保留策略、冻结/恢复、dry-run 生成、历史和 manifest 覆盖。
- 本批次执行闭环完成项：`V44__organization_lifecycle_execution.sql` 为 retention policy 增加 legal hold 原因、审批人与复核时间，为 purge job 增加 source dry-run、确认文本、manifest hash 和 result 摘要，并新增 `organization_export_job`；平台可创建组织导出 job 但不能下载业务内容归档；组织管理员可在 `/admin/organization/export-jobs/{jobId}/download` 下载脱敏 zip；真实 purge 仅允许 `PENDING_PURGE` 且无 legal hold 的组织执行，必须引用 24 小时内成功 dry-run 并输入 `PURGE {orgId}`，执行器删除 org scoped DB 数据、已登记 KB 文件、导出归档和 VectorStoreClient 已登记向量，成功后组织状态进入 `PURGED`，失败进入 `PARTIAL_FAILED` 并保留 result/failure 摘要。
- 前端追加完成项：平台 `/platform/tenants` 页面已接入组织导出列表、生成导出包、待销毁状态、真实销毁确认 modal、legal hold 原因/审批/复核字段，并兼容旧 manifest 缺少 `exportJobs`、`unsupported` 或 `tables` 的响应形状，避免旧本地数据导致页面崩溃。
- 本批次重试闭环完成项：新增平台 `POST /platform/tenants/{orgId}/purge-jobs/{jobId}/retry`；仅允许 `FAILED/PARTIAL_FAILED` 的真实 purge job 在组织仍为 `PENDING_PURGE`、legal hold 关闭、原 source dry-run 仍为 24 小时内成功清单且重新输入 `PURGE {orgId}` 时重试。重试会创建新的真实 purge job，重新生成 manifest/hash，复用原 source dry-run 作为审计依据，执行清理并在成功后把组织置为 `PURGED`；平台页在失败真实销毁行提供透明文本“重试”动作并复用阻塞确认 modal。
- 本批次排队执行完成项：真实 purge 和失败重试不再在请求线程内同步删除，而是创建 `QUEUED` 真实 purge job；`PlatformTenantLifecycleService.processQueuedPurgeJobs()` 作为 Spring scheduled worker 消费队列，运行前再次校验 `PENDING_PURGE`、legal hold、确认文本和 source dry-run 新鲜度，执行时进入 `RUNNING`，成功后进入 `SUCCEEDED` 并把组织置为 `PURGED`，校验或执行失败进入 `FAILED/PARTIAL_FAILED`。新增 `POST /platform/tenants/{orgId}/purge-jobs/{jobId}/cancel`，仅允许取消 `QUEUED` 真实 purge job；平台页为排队行显示透明文本“取消”，并在存在 `QUEUED/RUNNING` 真实任务时禁用新的真实销毁/重试入口。
- 本批次 orphan audit 完成项：dry-run manifest 新增 `orphanAudit.fileStorage` 与 `orphanAudit.vectorStore`；文件巡检扫描 KB 本地存储组织目录并识别未登记在 `kb_document.storage_path` 的孤儿文件，向量巡检通过 `VectorStoreClient.auditOrgVectors` 对比 org-scoped 点位和 DB 登记 `kb_chunk.vector_id`，只返回计数和最多 50 个样本，不返回业务正文。真实 purge 删除已登记 KB 文件后，会继续清理 `data/kb-files/{orgId}` 下的本地残留孤儿文件。
- 本批次 worker 生产化完成项：新增 `V46__organization_purge_worker_lease.sql`，真实 purge job 记录 `worker_id`、`locked_at`、`lock_expires_at`、`attempt_count` 和 `dead_letter_at`；scheduled worker 先用条件更新抢占 `QUEUED` job 为 `RUNNING` 并提交 lease，再在独立事务执行清理和完成态写入，避免多实例重复执行同一个 job；过期 `RUNNING` lease 会转入 `DEAD_LETTER`，保留 worker/lease/result 摘要，避免自动重复清理。
- 未完成项：密码重置/MFA/SSO、订阅状态联动、外部对象存储适配器巡检、生产 Qdrant/外部向量库巡检 smoke、Owner 侧关闭申请、运营告警和独立 worker 进程。
  - 说明：当前系统尚未正式生产上线，公网环境属于 UAT 公测阶段，可随时全新部署；因此不再把旧 `app_user` 到新账号模型的完整历史数据迁移作为 FEAT-024 必须交付项。开发期初始化 schema、账号多组织最小闭环和 UAT 环境重建部署口径已满足当前阶段。

## 交接说明

- 已确认并落地：当前开发阶段不保留 `app_user` 兼容层，`organization_member.id` 接管组织内身份；短期对外字段名 `userId` 保持，但语义为 member id。
- 已确认部署阶段口径：公网环境仍是 UAT 公测，不是正式生产；如账号模型或生命周期 schema 需要调整，优先采用全新部署/重建环境，不为历史测试数据设计完整生产级迁移。
- 新增 `OWNER` 后，组织管理权限检查接受 `OWNER/ORG_ADMIN`；普通角色编辑仍只允许 `ORG_ADMIN/ORG_USER`，Owner 变更走专用转让接口。
- 所有新业务域应停止把全局自然人 ID 当作组织内权限 ID 使用。
- 组织销毁已具备首版 guarded real purge、排队 worker、worker lease 抢占、死信标记、取消排队任务、失败重试和只读 orphan audit；后续生产化优先补外部对象存储适配器巡检、生产 Qdrant/外部向量库 smoke、订阅状态自动驱动、Owner 侧关闭申请流程、运营告警和独立 worker 进程。
