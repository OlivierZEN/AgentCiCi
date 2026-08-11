---
kind: task-status
task_id: TASK-280
status: review
updated_at: 2026-08-11T03:48:46Z
updated_by: codex
assignee: codex
owner_role: fullstack-agent
assignment_path: n/a
spec_path: docs/specs/FEAT-168-admin-member-identity-reconciliation.md
---

# TASK-280 - 组织成员统一身份修复入口

## Current State

- 已完成成员统一身份状态、受控协调 API、手机号二次确认、幂等审计和页面修复按钮。
- 已随 `2.8.61-beta.3 / 47affe4086e5` 发布 UAT；尚未对 `18611892001` 执行真实修复。

## Scope

- 为成员列表增加脱敏统一身份状态。
- 增加同租户、手机号二次确认的身份协调 API。
- 在 CloudCC 账号绑定页签增加状态与修复按钮、确认弹窗和结果反馈。
- 完成后端、前端定向测试与桌面端页面验证。

## Next Action

- 以真实 ORG_ADMIN 会话检查页面；只有获得单独业务操作授权后，才对目标成员执行正式修复与登录回归。

## Verification

- 后端 `AdminUserServiceTest,KeycloakIdentityProvisioningServiceTest` 通过。
- 前端完整测试 41 个文件、225 项通过；身份修复定向测试 3 项通过。
- 后端 package 与前端生产构建通过；仅保留既有 Vite bundle-size warning。
- 本地无组织管理员登录态，桌面端受权页面截图与真实 Keycloak 发信留待 UAT。
- UAT 六容器 healthy；版本接口为 `2.8.61-beta.3 / 47affe4086e5`，Flyway V109、Nginx、公网 HTTPS/HTTP 跳转与匿名新接口 401 均通过，30 秒稳定窗口重启数和启动后错误数均为 0。
- 本次仅发布入口和接口，未调用真实协调 API、未发送激活邮件、未修改 `18611892001`。
