---
kind: task-status
task_id: TASK-280
status: review
updated_at: 2026-08-11T03:35:00Z
updated_by: codex
assignee: codex
owner_role: fullstack-agent
assignment_path: n/a
spec_path: docs/specs/FEAT-168-admin-member-identity-reconciliation.md
---

# TASK-280 - 组织成员统一身份修复入口

## Current State

- 已完成成员统一身份状态、受控协调 API、手机号二次确认、幂等审计和页面修复按钮。
- 尚未发布 UAT，也未对 `18611892001` 执行真实修复。

## Scope

- 为成员列表增加脱敏统一身份状态。
- 增加同租户、手机号二次确认的身份协调 API。
- 在 CloudCC 账号绑定页签增加状态与修复按钮、确认弹窗和结果反馈。
- 完成后端、前端定向测试与桌面端页面验证。

## Next Action

- 经用户授权后发布 UAT，以真实 ORG_ADMIN 会话检查页面并对目标成员执行一次正式修复与登录回归。

## Verification

- 后端 `AdminUserServiceTest,KeycloakIdentityProvisioningServiceTest` 通过。
- 前端完整测试 41 个文件、225 项通过；身份修复定向测试 3 项通过。
- 后端 package 与前端生产构建通过；仅保留既有 Vite bundle-size warning。
- 本地无组织管理员登录态，桌面端受权页面截图与真实 Keycloak 发信留待 UAT。
