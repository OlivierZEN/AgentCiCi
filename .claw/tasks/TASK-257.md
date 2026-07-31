---
kind: task-status
task_id: TASK-257
status: in_progress
updated_at: 2026-07-31T00:25:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: frontend-agent
assignment_path: .claw/assignments/TASK-257.yaml
spec_path: docs/specs/FEAT-150-dev-autopilot-launcher-entry.md
---

# TASK-257 - DEV Autopilot 启动器入口

## Current State

- Status: `in_progress`
- 已完成：菜单项以现有启动器行样式呈现；点击使用当前页 `window.location.assign` 跳转独立应用，内置应用路由不变。
- 已验证：定向 Vitest（1/1）、全量前端 Vitest（32 文件 / 199 tests）、生产构建与 `git diff --check` 均通过。
- Next action: 合并主线、构建镜像并完成生产健康与公网跳转目标验证。
- Blocked: none

## Scope

- 仅修改 AI 应用启动器的数据定义与其定向测试。
- 不改认证、后端、Semattice、部署拓扑或现有内置应用路由。
