---
kind: feature-spec
feature_id: FEAT-168
title: 组织成员统一身份修复入口
status: implemented
owner_role: fullstack-agent
task_ids: TASK-280
related_decisions: "FEAT-145 HUMAN 统一身份；FEAT-166 Owner 身份协调"
related_issues: none
updated_at: 2026-08-11T05:40:53Z
updated_by: codex
---

# FEAT-168 - 组织成员统一身份修复入口

## 背景与目标

历史数据可能出现公司成员为 `ACTIVE`，但全局账号没有 `account_external_identity`，且 Keycloak 中没有对应 HUMAN 用户。用户管理页目前只展示成员状态，导致组织管理员看到“有效”却无法判断或修复统一登录身份。

本功能在“组织控制台 → 用户 → CloudCC账号绑定信息”中显示脱敏的统一身份状态，并为同租户 `ACTIVE + MISSING` 成员提供独立修复操作。修复复用受管 Keycloak HUMAN provisioning，不复用“添加成员”，不改变角色、昵称、手机号、邮箱或 CloudCC 绑定信息。

## 交互设计

- 统一身份状态分为：`未绑定`、`等待用户激活`、`已绑定，可登录`、`已停用`。
- `ACTIVE + MISSING` 时显示“修复统一身份”文本操作；点击后打开确认弹窗。
- 弹窗展示目标成员和脱敏邮箱，要求管理员输入该成员当前手机号作二次确认。
- 成功创建或恢复 Keycloak 用户且需要激活时，将成员转为 `PENDING_ACTIVATION`，发送 24 小时有效的邮箱验证和首次密码设置邮件。
- 若恢复到已经激活且归属证明一致的 Keycloak 用户，只补齐本地绑定并保持成员 `ACTIVE`。
- `PENDING_ACTIVATION` 使用“检查激活状态”入口：Keycloak 仍要求验证或设置密码时重发初始化邮件；Keycloak 已激活时，将本地成员安全同步为 `ACTIVE` 并明确提示从统一账号入口重新登录。
- `SUSPENDED` 和已绑定成员不显示修复操作。

## API 与安全

- `POST /api/admin/users/{userId}/identity-reconciliation`
- 请求体：`confirmMobile`、`idempotencyKey`；手机号必须与目标成员当前值完全一致，相同幂等键只执行一次。
- 沿用 `@RequireOrgAdmin` 和服务端 `TenantContext.company_id`；跨租户成员、状态不符、已存在绑定或确认信息不符均失败关闭。
- Keycloak 恢复必须继续满足不可变 `public_id + account_id + email` 归属校验；不得按手机号、昵称或邮箱单独自动合并。
- 不返回 Keycloak subject、激活链接、管理令牌或凭据，不生成和展示默认密码。
- 成功操作记录 `company_member.identity_reconciled` 平台审计，只保存成员技术标识、幂等键和是否仍需激活，不记录手机号、邮箱或凭据。
- `POST /api/admin/users/{userId}/activation-email` 仍只接受同租户 `PENDING_ACTIVATION` 成员。远端已激活时允许同步为 `ACTIVE`，记录 `company_member.identity_activation_synced` 审计；不读取、重置或返回密码，不接受未绑定或已停用身份。

## 验收标准

- 用户列表返回每个成员的脱敏 `identityState`，不返回 issuer/subject。
- `ACTIVE + MISSING` 成员可见修复按钮，其他状态不可见。
- 手机号确认错误、跨租户、已绑定、待激活或停用成员均不能执行修复。
- 修复不改变目标成员角色、资料和 CloudCC 绑定；需要激活时只把成员状态转为 `PENDING_ACTIVATION`。
- 邮件动作完成后再次检查，远端仍待激活则保持 `PENDING_ACTIVATION` 并重发；远端已激活则同步为 `ACTIVE`，角色、资料和绑定保持不变。
- 后端定向测试、前端定向测试与生产构建通过，并完成桌面端真实页面检查。

## 回滚

- 回滚前后端应用版本即可移除入口和接口；本功能不新增数据库迁移。
- 已由正式接口创建的 Keycloak 用户和本地身份绑定属于有效身份事实，不因 UI 回滚而删除。

## UAT 发布

- `2.8.61-beta.3 / 47affe4086e5` 已发布 UAT；运行、迁移、匿名鉴权与稳定性验证通过。
- 发布不等于真实成员修复：`18611892001` 的正式协调、邮件激活与登录回归仍需受权 ORG_ADMIN 会话执行。
