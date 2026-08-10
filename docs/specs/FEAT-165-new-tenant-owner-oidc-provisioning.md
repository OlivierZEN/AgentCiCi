---
kind: feature-spec
feature_id: FEAT-165
title: 新租户 Owner 统一身份开通
status: in_implementation
owner_role: backend-agent
task_ids: TASK-276
related_decisions: none
related_issues: ISSUE-2026-08-10-new-tenant-owner-missing-oidc
updated_at: 2026-08-10T11:18:25Z
updated_by: codex
---

# FEAT-165 - 新租户 Owner 统一身份开通

## 背景与目标

生产 `2.8.58` 的平台“开通新租户”路径只创建本地账号、密码凭据和 Owner 成员，没有调用 Keycloak HUMAN provisioning。生产登录入口已经使用 OIDC，因此新租户 Owner 即使完成平台开通，也不能在 Keycloak 中通过邮箱或手机号登录。

本功能使平台开通的新租户 Owner 与普通成员邀请遵循同一统一身份生命周期：建立 Keycloak 用户和 AgentCiCi issuer/subject 绑定，发送邮箱验证与设置密码动作，并在首次成功 OIDC 登录前保持 `PENDING_ACTIVATION`。

## 范围

### In Scope

- 统一认证启用时，新 Owner 邮箱必填，不再要求或写入本地初始密码。
- 新建或复用全局账号后调用受管 Keycloak HUMAN provisioning。
- 根据 Keycloak 激活状态将 Owner 成员置为 `PENDING_ACTIVATION` 或 `ACTIVE`。
- 保持现有租户 ID、Owner 角色、保留策略和平台审计语义。
- 为统一认证启用、兼容模式、复用既有账号和 provisioning 失败补充自动化测试。
- 发布到 UAT 并通过真实 Keycloak/AgentCiCi 回读验证。
- 为“新租户唯一 Owner 未完成激活、租户内没有可用管理员”的启动死锁提供平台管理员受控恢复：复用一个已经完成统一身份激活的全局账号作为新 Owner。

### Out Of Scope

- 不修改生产租户或生产用户数据。
- 不直接写 Keycloak 或 AgentCiCi 数据库修复用户。
- 不改变普通租户成员邀请和首次 OIDC 激活协议。
- 不修改 Semattice 或 DevAutopilot 仓库及其业务数据库。
- 不由平台管理员设置、查看或重置 HUMAN 密码；不把待激活成员直接改成有效身份。

## 用户场景

- 平台管理员输入租户名称、Owner 手机号、邮箱和显示名开通租户。
- 系统创建租户及 Owner 后，向 Owner 邮箱发送统一账号初始化邮件。
- Owner 完成邮箱验证和设置密码，通过 OIDC 首次登录后成员从 `PENDING_ACTIVATION` 转为 `ACTIVE`。
- 若手机号已属于既有统一账号，则复用已验证身份；不得创建重复 Keycloak 用户或重置已激活用户密码。
- Keycloak 创建或邀请失败时 API 必须失败关闭，不得返回“新租户已开通”的假成功。
- 若测试或客户交付时原 Owner 无法接收激活邮件，平台管理员可指定一个已激活统一账号恢复 Owner；系统只在目标租户没有有效 Owner 时执行，原待激活 Owner 降为普通管理员并保留审计链。

## 现状与约束

- `PlatformTenantLifecycleService.createTenant` 当前调用 `assignPasswordCredential`，但不调用 `KeycloakIdentityProvisioningService`。
- `AdminUserService.inviteMember` 已具备 HUMAN provisioning 与 `PENDING_ACTIVATION` 状态语义，应复用相同服务，不复制 Keycloak 管理协议。
- 浏览器、日志、Git 和状态文档不得包含管理员 token、Client Secret、邮件动作链接或用户密码。
- 正式版本 `2.8.59` 已于 `2026-08-10` 建立 Git production tag；本项后续 UAT 必须使用下一生产目标的不可变 `2.8.60-beta.N`，只重建 backend/frontend。

## 方案设计

1. `PlatformTenantLifecycleService` 注入 `KeycloakIdentityProvisioningService`。
2. 当 HUMAN provisioning 启用时：
   - 要求 Owner 邮箱非空且格式有效；
   - 新账号不创建本地密码凭据；
   - 在同一租户开通事务中调用 `ensureHumanIdentity`；
   - 依据 `activationRequired` 设置 Owner 成员状态。
   - 新账号插入后显式 flush/refresh，确保同一事务中的 provisioning 能读取数据库 trigger 生成的不可变 `public_id`。
3. provisioning 未启用时保留原本地兼容路径：新账号仍要求至少 8 位初始密码并写入本地凭据。
4. 复用既有账号时由 provisioning 服务核验既有绑定、邮箱和不可变 public ID；已激活账号保持 `ACTIVE`，待激活账号保持 `PENDING_ACTIVATION`。
5. Keycloak 异常向上返回，Spring 事务回滚本地租户、账号、成员、密码和审计写入，禁止部分本地租户被展示为成功。
6. 新增独立的 Owner 恢复服务，不复用“恢复成员”或数据库补丁：
   - 仅 `PLATFORM_ADMIN` 可调用；
   - 目标租户必须为 `ACTIVE`，且不能存在其他有效 Owner；
   - 替代账号必须已经绑定且远端 Keycloak 用户为 enabled、邮箱已验证、无 `VERIFY_EMAIL`/`UPDATE_PASSWORD` 动作；
   - 替代账号已有租户成员关系时复用该关系，否则建立新关系；将其设为 `OWNER/ACTIVE`；
   - 原待激活 Owner 降为 `ORG_ADMIN` 并保持原生命周期状态，不删除账号或身份绑定；
   - 相同目标重复调用幂等返回；不同有效 Owner 存在时失败关闭；
   - 写入 `platform.tenant.owner.recover` 平台审计，不记录手机号、邮箱、密码或 Token。

## 接口与数据影响

- `POST /platform/tenants` 请求结构保持兼容。
- 统一认证启用时 `ownerEmail` 从业务可选变为必填；`initialPassword` 可省略且不落本地密码凭据。
- 响应新增向后兼容布尔字段 `ownerActivationRequired`，平台页据此提示 Owner 查收统一账号激活邮件；成员状态仍通过既有成员查询接口回读。
- 新增 `POST /platform/tenants/{companyId}/owner-recoveries`，请求仅包含 `replacementOwnerMobile`；响应返回租户、Owner 成员/账号公共标识及是否复用既有成员关系，不返回凭据。
- 不新增数据库迁移。

## 任务拆分

- `TASK-276`：实现、测试、UAT 发布和验收。

## 验收标准

- 定向测试证明统一认证启用时调用 HUMAN provisioning、Owner 状态正确且不写本地密码。
- provisioning 失败时租户开通请求失败，本地事务不提交。
- 兼容模式仍要求并验证初始密码。
- UAT 使用全新隔离手机号/邮箱开通租户后：
  - `user_account`、EMAIL/MOBILE identifier、`company_member`、`account_external_identity` 均存在；
  - Keycloak 用户 enabled，subject 与绑定一致；
  - 未完成邮件动作前成员为 `PENDING_ACTIVATION`；
  - 不输出密码、token、secret 或完整邮件动作链接。
- backend/frontend healthy，版本、Git commit 和 image tag 一致；匿名受保护接口仍为 401。
- Owner 恢复定向测试证明：未激活账号、已存在其他有效 Owner、跨租户未知账号均失败；已激活账号可原子接管，旧 Owner 不被删除；相同请求幂等。
- UAT 使用正式平台接口恢复第二测试租户后，现有测试账号可通过 OIDC 选择该组织，成员为 `OWNER/ACTIVE`，再继续 DevAutopilot 开通和双租户隔离验收。

## 风险与回滚

- 风险：外部 Keycloak 写入不能由本地数据库事务天然回滚。当前 provisioning 服务先保存绑定再发送动作邮件；UAT 必须验证失败重试不会创建重复用户。后续如发现远端已创建、本地事务回滚的窗口，需增加受治理 reconciliation，而不是直接改库。
- 风险：既有平台调用方仍传 `initialPassword`。接口继续接受该字段，但统一认证模式不将其作为 Owner 登录凭据。
- 回滚：UAT 仅将 backend/frontend 切回 `2.8.59-beta.3`；无数据库迁移。隔离验收租户按正式租户生命周期清理，不直接删除表记录。

## 实现进展

- 已完成生产只读诊断和生产提交代码核对。
- 已完成失败测试复现、统一认证/兼容模式实现、Owner provisioning 定向测试、相关身份服务回归、后端打包和前端定向测试/构建。
- `2.8.59-beta.4` 真实调用暴露并验证了 `public_id` trigger 列未回填到当前 persistence context 的缺口；API 失败关闭且目标本地五类记录均为 0。已补 flush/refresh 与时序测试。
- `2.8.59-beta.6` 真实开通和独立回读已证明账号、EMAIL/MOBILE 标识、Owner 待激活状态、外部 subject、Keycloak enabled/Required Actions 与无本地密码凭据；等待 Owner 邮件激活和首次登录后将规格转为 `verified`。
- 双租户验收暴露了测试租户 Owner 激活邮件没有可交付接收方的问题。该问题不再由直接写库、共享 Keycloak 管理凭据或伪造激活状态处理；按本规格补充“无有效 Owner”的平台受控恢复闭环。
- 受控恢复服务与定向测试已完成：只接受远端已激活 HUMAN，串行锁定当前 Owner，保留并降级原待激活 Owner，相同目标幂等；等待 UAT 发布后通过正式平台接口执行。
- 本机数据库集成测试仍受 PostgreSQL 未启动限制，不扩写为全量通过；UAT 真实数据库/Keycloak 已作为正向运行证据。

## 交接说明

- 先读 `TASK-276`、本规格、`docs/production-release-runbook.md` §7.0。
- 生产目标用户尚未创建，不在本任务中写入生产。
