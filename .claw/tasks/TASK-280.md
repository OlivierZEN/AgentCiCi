---
kind: task-status
task_id: TASK-280
status: review
updated_at: 2026-08-11T06:26:32Z
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
- 用户提供的 UAT 页面证据显示目标成员已同步为“有效”且统一身份为“已绑定，可登录”；验收同时发现统一身份信息被错误放入 CloudCC 页签，现已调整到成员顶部整体信息栏。
- 信息归位已随 `2.8.61-beta.9 / 500ea8981b7d` 发布 UAT；受权页面已证明待激活成员在基本信息与 CloudCC 页签都从顶部整体信息区读取统一身份，CloudCC 页签只保留连接器字段。

## Scope

- 为成员列表增加脱敏统一身份状态。
- 增加同租户、手机号二次确认的身份协调 API。
- 在成员顶部整体信息栏增加状态与修复/激活检查按钮、确认弹窗和结果反馈；CloudCC 页签只保留 CloudCC 连接器字段。
- 完成后端、前端定向测试与桌面端页面验证。

## Next Action

- Demo Company 管理员刷新 beta.9 页面确认 `18611892001` 的信息归位；再以无既有 Keycloak SSO 的独立浏览器完成登录回归。

## Verification

- 信息归位前端定向 4 项测试、完整 42 文件/232 项测试与生产构建通过；构建仅保留既有 bundle-size warning。
- 通过本地真实浏览器在 `2048 × 1024` 下分别检查基本信息和 CloudCC 页签：统一身份摘要固定在成员整体信息区，CloudCC 页签只含连接器字段，控制台 error/warning 均为 0。截图位于 `output/playwright/task280-member-overview-basic.png` 与 `output/playwright/task280-member-overview-cloudcc.png`，不纳入产品提交。
- UAT 只读回读确认 Keycloak 激活事实、本地 pending 漂移与无目标登录会话；未读取或修改密码、凭据和邮件链接。
- 激活状态同步后端定向测试通过；后端 package、前端完整 42 文件/229 项和生产构建通过。
- beta.7 六容器 healthy、restart=0，health/version/Flyway V109/Nginx/公网/匿名 401 与 30 秒稳定窗口通过；状态服务 ID 哈希未变。
- beta.9 六容器 healthy、restart=0，版本 `2.8.61-beta.9 / 500ea8981b7d`、health、Flyway V109、Nginx、HTTPS/HTTP、匿名 401 与 30 秒稳定窗口通过；状态服务 ID 哈希仍为 `b5dca5759af2a9cfb0ed4285fdb3b01c9af02db33eb2bfbabfa347fe728de2bc`。
- UAT 受权浏览器选择待激活成员并切换 CloudCC 页签，确认身份状态与检查动作位于页签上方、CloudCC 只含绑定字段，console error/warning 为 0；未执行检查、修复或保存。
- 后端 `AdminUserServiceTest,KeycloakIdentityProvisioningServiceTest` 通过。
- 前端完整测试 41 个文件、225 项通过；身份修复定向测试 3 项通过。
- 后端 package 与前端生产构建通过；仅保留既有 Vite bundle-size warning。
- 本地无组织管理员登录态，桌面端受权页面截图与真实 Keycloak 发信留待 UAT。
- UAT 六容器 healthy；版本接口为 `2.8.61-beta.3 / 47affe4086e5`，Flyway V109、Nginx、公网 HTTPS/HTTP 跳转与匿名新接口 401 均通过，30 秒稳定窗口重启数和启动后错误数均为 0。
- 本次仅发布入口和接口，未调用真实协调 API、未发送激活邮件、未修改 `18611892001`。
