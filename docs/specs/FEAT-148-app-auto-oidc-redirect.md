---
kind: feature-spec
feature_id: FEAT-148
title: 应用未登录态自动跳转 OIDC
status: review
owner_role: frontend-agent
task_ids: TASK-255
related_decisions: FEAT-136 Keycloak 统一身份认证
related_issues: /app 未登录态停留在统一登录中间页
updated_at: 2026-07-30T09:10:00Z
updated_by: MANAGER-001
---

# FEAT-148 - 应用未登录态自动跳转 OIDC

## 目标

- 用户从 AgentCiCi 进入 `/app` 且没有有效本地会话时，自动跳转至 OIDC 入口，继续到 SSO 登录页。
- 不再停留在只含“统一账号登录”按钮的中间页要求用户再次点击。

## 设计与边界

- 复用既有 `/auth/oidc/login?return_to=...` 入口；后端仍负责规范 Host 跳转、state Cookie、PKCE 与 Keycloak 重定向。
- 仅对无会话、未在提交登录、且没有 `oidc_ticket` 或 CloudCC SSO ticket 的应用页面触发一次自动跳转。
- OIDC 完成票据、CloudCC SSO 票据和统一登录失败信息必须留在当前页面处理，避免自动跳转抢占回调。
- 保留登录按钮作为无障碍和异常回退入口；不改后端、SSO 配置、数据库、Semattice、主线或生产环境。

## 验收标准

- `/app` 的初始未登录态直接调用既有 OIDC 入口，携带当前 path/query 作为 `return_to`。
- 每次页面生命周期最多发起一次自动跳转。
- 带 OIDC 或 CloudCC SSO 回调票据时不会自动跳转。
- 定向单元测试、前端生产构建和静态检查通过。

## 实现进展

- 当前状态：实现与定向验证完成，等待用户授权合并主线或发布生产。
- 无会话应用页会直接进入既有 OIDC 入口；OIDC/CloudCC 回调票据和手动登录按钮保持不变。
