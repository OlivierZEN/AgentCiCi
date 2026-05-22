---
kind: feature-spec
feature_id: FEAT-053
title: Platform account orgless auth context
status: in_implementation
owner_role: platform-auth-governance
task_ids: TASK-131
related_decisions: none
related_issues: none
updated_at: 2026-05-22T05:10:00Z
updated_by: MANAGER-001
---

# FEAT-053 - Platform Account Orgless Auth Context

## 背景与目标

- 运营平台账号是平台底层专属账号，不属于任何客户组织或演示组织。
- 平台账号只与组织端账号共用统一登录认证能力，不复用 `organization_member` 成员身份。
- 当前 `/platform/login` 仍携带 `demo-org` 调用组织密码登录，平台侧栏与概览继续展示 `demo-org`，造成平台账号归属误导。

## 范围

### In Scope

- 新增或完成 `/auth/platform/password/login`，用 `platform_account` 认证平台账号。
- 新增或完成 `/auth/platform/me`，返回当前平台账号资料和平台角色。
- 平台 JWT 使用 `typ=platform` 与 `platform_account_id`，不携带 `org_id`、`member_id` 或组织 `account_id`。
- `/platform/*` API 接受平台 token；平台治理数据如仍需后端兼容分区，使用内部治理 scope，不作为账号组织返回。
- 前端平台登录不再提交 `orgId`，平台侧栏与概览不再展示“当前组织 / 组织”。

### Out Of Scope

- 组织端 `/auth/password/login`、`/admin/login`、组织切换和组织成员模型。
- 平台租户生命周期业务规则。
- 数据库迁移与历史平台数据重分区。
- 新增移动端布局、移动端截图或移动端自动化测试。

## 验收标准

- 平台账号可用邮箱或手机号登录 `/platform/login`。
- 登录成功的 payload 和 JWT 不包含组织归属字段。
- 平台 token 可以访问 `/auth/platform/me` 与 `/platform/bootstrap`。
- 平台 token 不能访问 `/auth/me` 或 `/admin/*` 组织端接口。
- 平台控制台左侧不再显示 `demo-org` 或任何“当前组织”信息。
- 平台概览不再展示组织行。
- 平台账号登录不会创建 `user_account` 或 `organization_member` 记录。

## 实现说明

- 使用 `platform_account` / `platform_account_credential` 作为平台账号存储。
- Bootstrap 平台账号来自 `app.auth.bootstrap-platform-account` 配置；未配置时沿用本地默认值。
- 平台治理模块内部仍可使用配置中的 `governanceOrgId` 作为旧表结构的内部数据 scope，但该值不得进入平台账号登录态或界面展示。

## 交接说明

- 本规格修正平台账号身份边界，不改变租户是组织实体这一事实。
- 后续若要彻底移除平台治理表中的 `org_id` 分区，需要单独规格和迁移计划。
- 2026-05-22 implementation pass complete: backend compile/test-compile, frontend build, desktop Playwright QA, `git diff --check`, and `.claw` validation passed. Focused backend integration is pending because local Docker/Postgres is unavailable.
