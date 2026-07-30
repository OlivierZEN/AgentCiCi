---
kind: task-status
task_id: TASK-255
status: in_progress
updated_at: 2026-07-30T09:00:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: frontend-agent
assignment_path: .claw/assignments/TASK-255.yaml
spec_path: docs/specs/FEAT-148-app-auto-oidc-redirect.md
---

# TASK-255 - 应用未登录态自动跳转 SSO

## Current State

- Status: `in_progress`
- Next action: 为 `/app` 无会话状态添加一次性 OIDC 自动跳转，并覆盖回调票据保护。
- Blocked: none

## Evidence

- `AssistantApp.tsx` 已有 `startUnifiedLogin`，但仅由中间页按钮点击触发。
- 截图中的会话过期页因此停留在 `/app`，没有自动进入 SSO。

## Scope

- 仅修改前端应用登录跳转决策及其定向测试。
- 不改 OIDC 后端入口、Keycloak、数据库、部署配置或生产环境。
