---
kind: task-status
task_id: TASK-280
status: review
updated_at: 2026-08-11T05:49:57Z
updated_by: codex
assignee: codex
owner_role: fullstack-agent
assignment_path: n/a
spec_path: docs/specs/FEAT-168-admin-member-identity-reconciliation.md
---

# TASK-280 - 组织成员统一身份修复入口

## Current State

- 已完成成员统一身份状态、受控协调 API、手机号二次确认、幂等审计和页面修复按钮。
- 已随 `2.8.61-beta.3 / 47affe4086e5` 发布 UAT；真实验收发现邮件动作已完成但本地成员仍为 `PENDING_ACTIVATION`，现有重发接口只报“已激活”而不协调状态。
- UAT 只读证据确认 Keycloak 用户 enabled、邮箱已验证、required actions 为空且已有 password credential；本地 binding subject 一致，但目标用户没有成功 OIDC 会话。正在修复远端已激活时的受控状态同步。
- 修复已随 `2.8.61-beta.7 / 4f7ae57f0aec` 发布；当前没有可控 ORG_ADMIN 浏览器会话，未绕过认证执行正式同步，目标成员仍保持 `PENDING_ACTIVATION`。

## Scope

- 为成员列表增加脱敏统一身份状态。
- 增加同租户、手机号二次确认的身份协调 API。
- 在 CloudCC 账号绑定页签增加状态与修复按钮、确认弹窗和结果反馈。
- 完成后端、前端定向测试与桌面端页面验证。

## Next Action

- ORG_ADMIN 刷新 UAT 用户页后点击“检查激活状态”，确认成员变为有效；再以无既有 Keycloak SSO 的独立浏览器会话完成登录回归。

## Verification

- UAT 只读回读确认 Keycloak 激活事实、本地 pending 漂移与无目标登录会话；未读取或修改密码、凭据和邮件链接。
- 激活状态同步后端定向测试通过；后端 package、前端完整 42 文件/229 项和生产构建通过。
- beta.7 六容器 healthy、restart=0，health/version/Flyway V109/Nginx/公网/匿名 401 与 30 秒稳定窗口通过；状态服务 ID 哈希未变。
- 后端 `AdminUserServiceTest,KeycloakIdentityProvisioningServiceTest` 通过。
- 前端完整测试 41 个文件、225 项通过；身份修复定向测试 3 项通过。
- 后端 package 与前端生产构建通过；仅保留既有 Vite bundle-size warning。
- 本地无组织管理员登录态，桌面端受权页面截图与真实 Keycloak 发信留待 UAT。
- UAT 六容器 healthy；版本接口为 `2.8.61-beta.3 / 47affe4086e5`，Flyway V109、Nginx、公网 HTTPS/HTTP 跳转与匿名新接口 401 均通过，30 秒稳定窗口重启数和启动后错误数均为 0。
- 本次仅发布入口和接口，未调用真实协调 API、未发送激活邮件、未修改 `18611892001`。
