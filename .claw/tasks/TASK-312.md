---
kind: task-status
task_id: TASK-312
status: in_progress
updated_at: 2026-08-17T17:30:00+08:00
updated_by: codex
assignee: codex
owner_role: frontend-agent
spec_path: docs/specs/FEAT-148-app-auto-oidc-redirect.md
---

# TASK-312 - 登录中转页移除手动触发区

## Current State

- Status: `in_progress`
- Next action: 删除当前登录中转页的说明卡片和按钮，验证进入 `/app` 后自动发起统一登录。
- Blocked: none

## Scope

- 仅修改 AgentCiCi 前端登录中转页结构、局部样式和定向测试。
- 复用既有同源 OIDC 入口和一次性自动跳转决策。
- 不修改后端、Keycloak、环境地址、数据库、UAT 或生产环境。

## Acceptance

- 正常未登录中转态只显示现有主视觉，不出现说明、按钮、退出状态或联系管理员文案。
- `/app` 无有效会话时无需点击即可进入统一身份登录。
- 回调票据继续由专用流程消费，失败时显示无手动按钮的最小错误提示。
- 定向测试、前端全量测试、生产构建、本地主线和 `cici.localhost` 桌面路由验证通过。
