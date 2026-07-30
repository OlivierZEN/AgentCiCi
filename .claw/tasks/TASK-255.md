---
kind: task-status
task_id: TASK-255
status: review
updated_at: 2026-07-30T09:10:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: frontend-agent
assignment_path: .claw/assignments/TASK-255.yaml
spec_path: docs/specs/FEAT-148-app-auto-oidc-redirect.md
---

# TASK-255 - 应用未登录态自动跳转 SSO

## Current State

- Status: `review`
- Next action: 等待用户授权合并主线或发布生产。
- Blocked: none

## Evidence

- `AssistantApp.tsx` 已有 `startUnifiedLogin`，但仅由中间页按钮点击触发。
- 截图中的会话过期页因此停留在 `/app`，没有自动进入 SSO。
- 已增加一次性自动跳转决策；普通 guest 自动进入 OIDC，OIDC/CloudCC 回调票据、登录提交和已有会话均不会触发。
- `oidcAutoRedirect.test.ts` 5/5 与前端生产构建通过。

## Scope

- 仅修改前端应用登录跳转决策及其定向测试。
- 不改 OIDC 后端入口、Keycloak、数据库、部署配置或生产环境。
