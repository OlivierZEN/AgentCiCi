---
kind: task-status
task_id: TASK-257
status: done
updated_at: 2026-07-31T01:10:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: frontend-agent
assignment_path: .claw/assignments/TASK-257.yaml
spec_path: docs/specs/FEAT-150-dev-autopilot-launcher-entry.md
---

# TASK-257 - DEV Autopilot 启动器入口

## Current State

- Status: `done`
- 已完成：菜单项以现有启动器行样式呈现；点击使用当前页 `window.location.assign` 跳转独立应用，内置应用路由不变。
- 已验证：定向 Vitest（1/1）、全量前端 Vitest（32 文件 / 199 tests）、生产构建与 `git diff --check` 均通过；主线提交 `f2814efc3a07` 已发布为 `2.8.28`。
- 生产事实：backend/frontend 均为 `2.8.28` 并健康，后端 `/system/version` 返回 `2.8.28 / f2814efc3a07`；新版前端静态资源含 DEV Autopilot 文案，`https://x.agentcici.com/` 和 `https://x.agentcici.com/devautopilot/` 均为 200。
- Browser：无用户已登录会话时，生产 `/app` 正确显示“统一账号登录”边界；未伪造凭据。已登录用户可在截图所示的 AI 应用菜单点击新入口完成最终目测验收。
- Blocked: none

## Scope

- 仅修改 AI 应用启动器的数据定义与其定向测试。
- 不改认证、后端、Semattice、部署拓扑或现有内置应用路由。
