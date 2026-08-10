---
kind: feature-spec
feature_id: FEAT-166
title: 平台租户 Owner 统一身份协调
status: in_implementation
owner_role: fullstack-agent
task_ids: TASK-277
related_decisions: none
related_issues: ISSUE-2026-08-10-new-tenant-owner-missing-oidc
updated_at: 2026-08-10T11:18:25Z
updated_by: codex
---

# FEAT-166 - 平台租户 Owner 统一身份协调

## 背景与目标

FEAT-165 已修复新租户创建链路，但历史租户仍可能存在“本地 Owner 已创建，Keycloak HUMAN 身份或本地 issuer/subject 绑定缺失”的异常记录。本功能在平台“租户应用”页提供当前 Owner 的脱敏身份状态与受治理协调入口，让平台管理员复用既有 provisioning 修复同一 Owner，而不是直接写库、设置本地密码或重建租户。

## 范围

### In Scope

- 展示唯一 Owner 的脱敏邮箱、脱敏手机号、公共编号、成员状态与统一身份状态。
- 仅 `PLATFORM_ADMIN` 可提交协调；请求必须包含精确公共编号和幂等键。
- 复用 `KeycloakIdentityProvisioningService.ensureHumanIdentity` 创建、重建或协调同一 Owner 的受管身份。
- 按远端激活要求把成员置为 `PENDING_ACTIVATION` 或 `ACTIVE`，保留租户、账号和 Owner 角色。
- 记录不含凭据的 `platform.tenant.owner_identity.reconcile` 审计。
- 在正式租户应用页实现确认 modal、禁用/加载/成功/错误状态并补自动化测试。

### Out Of Scope

- 不直接写生产数据库或 Keycloak 数据库，不读取或设置密码。
- 不转让 Owner，不替换当前 Owner，不删除成员或业务数据。
- 不自动发布 UAT/生产，不修改 Semattice 或 DevAutopilot 仓库。

## 用户场景

1. 平台管理员打开目标租户的“租户应用”页。
2. 页面显示 `MISSING`、`PENDING_ACTIVATION` 或 `ACTIVE` 的 Owner 身份状态。
3. 管理员打开确认框，核对影响范围并输入完整公共编号。
4. 服务端再次校验唯一 Owner 与公共编号，调用受管 HUMAN provisioning。
5. 页面更新为“等待用户激活”或“身份正常”；失败时保留原状态并展示错误。

## 现状与约束

- `PlatformTenantApplicationsPage` 是平台租户级应用与身份故障的可达控制面。
- `ensureHumanIdentity` 已处理本地绑定复用、远端用户恢复、不可变 public ID ownership 校验与激活邮件。
- 无法证明远端身份 ownership 时必须失败关闭，不能降级为邮箱或手机号弱匹配。
- 正式页面沿用 `鎏金账房` 与现有平台 modal 语汇；已确认原型只作为交互依据，不引入新的全局 token。
- 工作树中另有“替换为已激活 Owner”的并发实现，本功能不修改或复用该高风险所有权转让语义。

## 方案设计

- 新增独立 `PlatformTenantOwnerIdentityService`，负责只读状态、脱敏、唯一 Owner 校验、幂等协调与审计。
- `GET` 只读取本地权威绑定，不在页面加载时调用 Keycloak Admin API。
- `POST` 对同一 Owner 串行处理；相同幂等键返回当前结果，不重复发送邮件。
- 前端 Owner 区位于租户摘要与应用中心之间；确认框要求输入公共编号后才能提交。

## 接口与数据影响

- `GET /platform/tenants/{companyId}/owner-identity`
- `POST /platform/tenants/{companyId}/owner-identity/reconciliations`
  - request: `publicId`, `idempotencyKey`
  - response: `companyId`, `memberId`, `displayName`, `maskedEmail`, `maskedMobile`, `publicId`, `memberStatus`, `identityState`, `recoverable`
- 不新增数据库迁移。

## 任务拆分

- `TASK-277`：受治理 API、正式页面、自动化测试、构建和桌面验收。

## 验收标准

- 无绑定 Owner 可协调，待激活 Owner 可重发，已激活 Owner 不显示恢复主操作。
- 错误公共编号、非唯一 Owner、非管理员角色、provisioning 失败均失败关闭。
- 相同幂等键重复提交不会再次调用 provisioning。
- 成功操作不改变租户、Owner 账号、Owner 角色或业务数据。
- 后端/前端定向测试、前端生产构建、桌面浏览器交互与控制台检查通过。

## 风险与回滚

- Keycloak 外部写入不由本地事务天然回滚；沿用既有 provisioning 的可重试与严格 ownership 证明。
- 回滚 backend/frontend 即可，无数据库迁移；已建立的合法统一身份不因应用回滚删除。

## 实现进展

- 用户已确认页面位置与交互原型。
- 正式 API、页面、脱敏状态、确认 modal、幂等审计与定向测试已完成；常规协调不使用危险操作语汇，不把重发激活邮件包装成复杂安全流程。等待 UAT 发布和桌面验收。

## 交接说明

- 先读本规格、FEAT-165、TASK-276/277 与相关 issue。
- 生产修复必须从发布后的正式页面调用受治理 API；禁止直接写库。
