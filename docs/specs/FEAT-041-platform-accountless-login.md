---
kind: feature-spec
feature_id: FEAT-041
title: Platform accountless login
status: implemented
owner_role: platform-auth
task_ids:
  - TASK-120
related_decisions: none
related_issues: none
updated_at: 2026-05-20T13:36:12Z
updated_by: ai
---

# FEAT-041 - Platform Accountless Login

## Background

当前 `/platform/login` 复用组织登录接口 `POST /auth/password/login`，登录页要求输入 `orgId`，后端必须找到或创建 `organization_member` 后才能签发 JWT。平台角色由手机号命中 `app.auth.platform-*-mobiles` 后动态追加，因此平台账号实际仍依附某个组织成员。

新的平台运营账号体系要把平台控制面从租户成员身份中拆出来：平台运营人员登录平台后台时不属于任何组织，不应带入 `orgId`、`orgName`、`memberId` 或组织成员角色。平台登录使用专属 `platform_account`，只借用通用密码校验算法和固定密码兜底能力，校验通过后按平台账号角色签发平台态 token。

## Goals

- `/platform/login` 不再要求输入组织 ID。
- 创建专属平台账号 `admin@cloudcc.com`，绑定手机号 `18611892001`。
- `admin@cloudcc.com` 不写入普通 `user_account`，不创建、不查询、不依赖任何 `organization_member`。
- 平台 token 只表达平台身份与平台角色，不能携带租户组织信息。
- 平台角色从平台账号记录读取，不再从组织成员或手机号白名单临时叠加。
- 保留通用密码校验机制，当前阶段继续兼容固定密码或平台账号个人密码。

## Non Goals

- 本次不改变助手端 `/` 和组织管理端 `/admin/login` 的组织登录流程。
- 本次不引入 SSO、MFA、找回密码、平台用户邀请或完整平台账号管理页。
- 本次不让 `ORG_ADMIN` 自动拥有平台后台权限。
- 本次不把平台账号和租户用户账号做身份合并。

## Current Problems

- 平台账号必须输入 `orgId`，用户心智上会误以为平台后台属于某个租户。
- `issueLoginForMember` 返回 `orgId/orgName/memberId`，平台端 token 天然带租户上下文。
- 平台角色由手机号附加，无法表达“专属平台账号 + 绑定手机号 + 平台角色”的独立身份域。
- `TenantContextFilter` 当前把普通 JWT 的 `org_id` 当作主租户上下文，平台无组织 token 需要明确识别，避免误用。

## Proposed Design

### Platform Account Model

新增独立平台账号表作为平台登录事实源：

```text
platform_account(
  id,
  email,
  mobile,
  display_name,
  roles_json,
  status,
  created_at,
  updated_at
)

platform_account_credential(
  id,
  platform_account_id,
  credential_type,
  password_hash,
  salt,
  iterations,
  algorithm,
  status,
  created_at,
  updated_at
)
```

设计约束：

- `platform_account.email` 唯一，默认值 `admin@cloudcc.com`。
- `platform_account.mobile` 唯一，默认值 `18611892001`。
- `roles_json` 首版保存 `["PLATFORM_ADMIN"]`。
- `platform_account` 不引用 `org`、`organization_member` 或 `user_account`。
- `platform_account_credential` 使用与 `account_auth_credential` 一致的 PBKDF2 字段，校验逻辑抽成共享 verifier，避免复制密码算法。
- 若平台账号没有个人密码凭证，当前内部阶段可继续 fallback 到 `auth_password.default` 固定密码；后续生产化可强制每个 `platform_account` 拥有独立密码或接入 SSO。

配置只用于种子默认账号：

```yaml
app:
  auth:
    bootstrap-platform-account:
      email: "admin@cloudcc.com"
      mobile: "18611892001"
      roles: ["PLATFORM_ADMIN"]
      display-name: "CloudCC Platform Admin"
      enabled: true
```

`platform-*-mobiles` 标记为 deprecated。平台登录不再读取这些手机号白名单决定角色。

### Account Seed

启动时通过新增 `PlatformAccountBootstrapData` 确保默认平台账号存在：

- `platform_account.email = admin@cloudcc.com`
- `platform_account.mobile = 18611892001`
- `platform_account.display_name = CloudCC Platform Admin`
- `platform_account.roles_json = ["PLATFORM_ADMIN"]`
- `platform_account.status = ACTIVE`
- 不创建 `user_account`
- 不创建 `organization_member`
- 不创建任何 `org` 绑定关系

如数据库中已经存在同名邮箱或手机号的 `user_account`，不做合并；平台账号与组织用户账号是两个身份域。后续若要合并身份，需要单独设计迁移和审计规则。

### Login API

新增平台专用登录接口：

```http
POST /auth/platform/password/login
Content-Type: application/json

{
  "identifier": "admin@cloudcc.com",
  "password": "szyd1234"
}
```

登录输入支持邮箱或绑定手机号。`admin@cloudcc.com` 与 `18611892001` 都应定位到同一个 `platform_account`。

响应示例：

```json
{
  "token": "<jwt>",
  "platformAccountId": "<platform_account.id>",
  "email": "admin@cloudcc.com",
  "mobile": "18611892001",
  "displayName": "CloudCC Platform Admin",
  "roles": ["PLATFORM_ADMIN"],
  "tokenType": "platform",
  "issuedAt": "2026-05-20T00:00:00Z"
}
```

响应中不得包含：

- `orgId`
- `orgName`
- `userId`
- `memberId`
- `accountId`
- `OWNER`
- `ORG_ADMIN`
- `ORG_USER`

失败规则：

- 平台账号不存在：`Invalid account or password`
- 密码错误：`Invalid account or password`
- 平台账号状态非 ACTIVE：`Platform account is disabled`
- 角色为空或不是 `PLATFORM_*`：`Platform account is not allowed`

### Token Claims

平台 token 使用独立 claim：

```json
{
  "typ": "platform",
  "sub": "<platformAccountId>",
  "platform_account_id": "<platformAccountId>",
  "roles": ["PLATFORM_ADMIN"],
  "platform_email": "admin@cloudcc.com",
  "platform_mobile": "18611892001"
}
```

平台 token 不写入 `org_id`、`member_id` 或 `account_id`。这能让平台后台、平台审计和跨租户操作显式知道当前操作者是平台身份，而不是某个租户成员或普通账号。

### Backend Context

`TenantContextFilter` 需要识别 `typ=platform`：

- 若请求路径是 `/platform/**` 或允许的平台 API，写入 `TenantContext.roles` 与新增的 `TenantContext.platformAccountId`，不写入 `TenantContext.orgId`。
- 若平台 token 调用组织侧 API（如 `/kb`、`/admin/**`、`/ai/**`），返回 401 或 403。
- 若组织 token 调用 `/platform/**`，仍必须由 `RequirePlatformRole` 拒绝。

平台 API 中需要租户目标时，必须从路径或查询参数读取目标组织，例如 `/platform/tenants/{orgId}`，不能从登录 token 的 `org_id` 推断。

### Authorization

`RequirePlatformRole` 继续按 `roles` 判断，但应明确接受 `typ=platform` token：

- 默认平台 API：任意 `PLATFORM_*` 可访问。
- 高风险操作：继续使用注解参数限制，如 `@RequirePlatformRole({RoleCodes.PLATFORM_ADMIN})`。
- 平台审计 `actorUserId` 改用 `platform:<platformAccountId>`，展示名可取 `admin@cloudcc.com`，角色取 `PLATFORM_ADMIN` 等平台角色。

### Frontend Changes

`/platform/login` 表单调整：

- 删除组织 ID 输入。
- 默认账号输入改为 `admin@cloudcc.com`。
- 登录请求改为 `/auth/platform/password/login`，body 只包含 `identifier` 与 `password`。
- 登录成功后继续写入 `localStorage.cici_platform_token`。
- `PlatformGuard` 用平台 token 调 `/auth/platform/me` 或 `/platform/bootstrap` 校验平台角色，不再调用需要组织上下文的 `/auth/me`。
- 平台壳层不展示当前组织身份；租户上下文只在具体租户治理页面中作为目标对象展示。

### Platform Me API

新增：

```http
GET /auth/platform/me
Authorization: Bearer <platform-token>
```

返回：

```json
{
  "platformAccountId": "<platform_account.id>",
  "email": "admin@cloudcc.com",
  "mobile": "18611892001",
  "displayName": "CloudCC Platform Admin",
  "roles": ["PLATFORM_ADMIN"],
  "tokenType": "platform"
}
```

该接口只接受 `typ=platform` token，不读取 `TenantContext.requireOrgId()`。

## Data Model Impact

新增迁移，例如 `V59__platform_account.sql`：

- `platform_account`
- `platform_account_credential`
- 唯一索引：`ux_platform_account_email`
- 唯一索引：`ux_platform_account_mobile`
- 普通索引：`idx_platform_account_status`

不改动 `organization_member.role_code`，不增加普通组织用户的 role 字段。平台身份和组织身份在数据模型上彻底分离。

## Migration Plan

1. 新增 `platform_account` / `platform_account_credential` 迁移与实体、Repository。
2. 新增 `PlatformAccountProperties` 读取 `app.auth.bootstrap-platform-account`。
3. 启动种子确保 `admin@cloudcc.com` / `18611892001` 平台账号存在。
4. 抽出共享密码校验器，供普通账号和平台账号复用 PBKDF2 校验与固定密码兜底。
5. 新增平台密码登录 service 方法，只查询 `platform_account`，不查询 `organization_member`，不调用 `issueLoginForMember`。
6. 新增平台 JWT 签发方法，`typ=platform` 且不包含组织 claims。
7. 调整 `TenantContextFilter` 对平台 token 的解析与隔离规则。
8. 新增 `/auth/platform/me`。
9. 前端 `/platform/login` 删除组织 ID，切换接口和默认账号。
10. 调整 `PlatformGuard`，不再用 `/auth/me` 校验平台 token。
11. 后端集成测试覆盖平台账号无组织登录、组织 token 不能进平台、平台 token 不能进组织 API。

## Acceptance Criteria

- 使用 `admin@cloudcc.com` + 当前密码可登录 `/platform/login`。
- 使用 `18611892001` + 当前密码也能登录到同一个平台账号。
- 平台登录请求不需要 `orgId`。
- 登录响应和 JWT 不包含 `orgId/orgName/memberId/accountId`。
- 数据库中默认平台账号存在于 `platform_account`，且没有任何 `organization_member` 记录。
- 数据库中默认平台账号不要求存在对应 `user_account`。
- 平台 token 可以访问 `/platform/**`。
- 平台 token 访问 `/admin/**`、`/kb`、`/ai/**` 等组织 API 被拒绝。
- 组织管理员 `ORG_ADMIN` 即使手机号在 `bootstrap-admin-mobiles`，也不能访问 `/platform/**`，除非该操作者使用专属平台账号登录。

## Test Plan

- 后端新增 `PlatformAuthIntegrationTest`：
  - `admin@cloudcc.com` 登录成功，返回 `PLATFORM_ADMIN`。
  - `18611892001` 登录成功，返回同一 `platformAccountId`。
  - 登录响应不含组织字段和 `accountId`。
  - 登录后数据库无默认平台账号的 `organization_member` 也能访问 `/platform/bootstrap`。
  - 删除或不存在同邮箱 `user_account` 不影响平台登录。
  - 非平台账号邮箱密码正确也无法登录平台。
  - 平台 token 调 `/auth/me` 或 `/admin/users` 被拒绝。
- 前端构建通过。
- 用 Playwright 验证 `/platform/login` 无组织 ID 输入，默认账号为 `admin@cloudcc.com`，登录后进入 `/platform`。

## Implementation Status

- 2026-05-20T13:36:12Z: Implemented in `TASK-120` on branch `codex/TASK-120-platform-accountless-login`.
- Delivered backend pieces: `V59__platform_account.sql`, `platform_account` / `platform_account_credential` JPA model, bootstrap default account, shared password verifier, platform password login, platform me API, `typ=platform` JWT, platform token isolation in `TenantContextFilter`, and platform audit actor IDs based on `platform:<platformAccountId>`.
- Delivered frontend pieces: `/platform/login` no longer asks for organization ID, defaults the account field to `admin@cloudcc.com`, calls `/auth/platform/password/login`, persists `cici_platform_token`, and `PlatformGuard` validates platform identity through `/auth/platform/me`.
- Verified with backend compile, focused platform-auth tests, adjacent auth/platform regression tests, frontend build, `git diff --check`, and Playwright desktop/mobile/login-flow checks. See `.claw/test-report.md` for exact commands and screenshot paths.

## Rollback

- 保留旧 `/auth/password/login` 组织登录能力不变。
- 若平台无组织 token 出现问题，可临时恢复 `/platform/login` 调旧接口和 `orgId=demo-org` 输入。
- 回滚时不得删除已创建的 `platform_account`，只需让 `/platform/login` 临时恢复旧接口；平台账号表可保留为未使用数据。

## Open Questions

- 平台账号是否允许设置独立个人密码，还是继续使用内部固定密码直到接入 SSO？
- 平台审计中的 actor 展示应使用 `admin@cloudcc.com`，还是 `platform:<platformAccountId>`？
- 生产环境是否需要禁止手机号登录平台，只允许 `admin@cloudcc.com` 邮箱登录？
