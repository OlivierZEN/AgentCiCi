---
kind: feature-spec
feature_id: FEAT-137
title: OIDC 统一登录入口与 state Cookie 一致性
status: in_implementation
owner_role: integration-agent
task_ids: TASK-244
related_decisions: FEAT-136 Keycloak-first-party-access design
related_issues: ISSUE-2026-07-24-oidc-state-cross-origin
updated_at: 2026-07-24T12:10:00Z
updated_by: MANAGER-001
---

# FEAT-137 - OIDC 统一登录入口与 state Cookie 一致性

## 背景与目标

用户完成 Keycloak SSO 登录后被回调至 `https://x.agentcici.com/auth/oidc/callback`，页面返回 `Invalid OIDC login state`，未进入系统。

已验证：`agentcici.com` 与 `x.agentcici.com` 都可访问 `/auth/oidc/login`，但授权客户端的回调 URI 固定为 `x.agentcici.com`。现有 `CICI_OIDC_STATE` 是 host-only Cookie；从主站域发起时，Cookie 只属于 `agentcici.com`，不会随 `x.agentcici.com` 回调提交，服务端的 CSRF state 比较因此必然失败。

目标是在开始 OIDC 事务前把所有入口收敛至由 `app.auth.oidc.redirect-uri` 定义的规范应用源站，保持 Cookie host-only 和现有 PKCE/state/nonce 语义，不扩大 Cookie 到整个父域。

## 范围与设计

- `GET /auth/oidc/login` 若请求源站不是配置回调 URI 的源站，先以 302 跳转到规范源站同一路径，并仅携带经过既有 allow-list 校验的 `return_to`。
- 仅规范源站创建 Redis OIDC transaction 和 `CICI_OIDC_STATE` Cookie；Keycloak 回调仍固定到同一规范源站。
- 规范源站请求保留现有 Authorization Code + PKCE、5 分钟 transaction TTL、HttpOnly/Secure/`SameSite=Lax` host-only Cookie 和一次性 completion ticket。
- 增加定向 MVC/服务回归，覆盖主站入口重定向、规范入口产生 state Cookie，以及 state Cookie 不匹配仍 fail closed。

## 非范围

- 不放宽 callback URI，不将 Cookie 的 `Domain` 扩展为 `.agentcici.com`，不降低 Secure/SameSite/HttpOnly 属性。
- 不修改 Keycloak realm/client、用户绑定、token exchange、OACT、数据库迁移或生产配置。

## 验收标准

- 从 `https://agentcici.com/auth/oidc/login?return_to=/...` 进入时，浏览器先到 `https://x.agentcici.com/auth/oidc/login?...`，随后由该源站向 Keycloak 发起授权。
- 在回调 URI 相同源站发起并回调时，`state` 与 Cookie 可匹配；缺失、过期或不一致 Cookie 仍返回认证失败。
- 后端定向测试、编译、静态 diff 检查通过；生产仅在单独发布授权后执行。

## 实现与交接

- TASK-244 仅处理入口规范化与相关回归，不改变任何身份授权边界。
- 根因依据：2026-07-24 生产只读请求已确认两个 host 都返回 `Set-Cookie: CICI_OIDC_STATE`，但 `Location.redirect_uri` 始终为 `https://x.agentcici.com/auth/oidc/callback`。
