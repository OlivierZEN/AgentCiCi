---
kind: task-status
task_id: TASK-204
status: ready
updated_at: 2026-07-14T09:05:01Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: frontend-agent
assignment_path: .claw/assignments/TASK-204.yaml
spec_path: docs/specs/FEAT-110-agent-builder-guide-avatar-polish.md
---

# TASK-204 - 智能体构建说明与头像交互精修

## Scope

- 去除智能体构建说明的嵌套卡片外观并收紧页面外层间距。
- 将头像上传与移除收敛为点击头像打开的紧凑菜单。
- 复用现有裁剪和草稿数据流，补齐键盘、菜单与移除回退测试。
- 完成聚焦/全量测试、生产构建和真实桌面端截图验收。

## Current State

- 用户已确认采用方案 A：头像为唯一常驻入口，菜单承载上传/更换和条件显示的移除动作。
- 书面设计已写入 FEAT-110，等待用户复核后进入实现。

## Next Action

- 用户复核 FEAT-110 后编写实现计划，并在 TASK-204 分支完成代码与验证。

## Changed Files

- `docs/specs/FEAT-110-agent-builder-guide-avatar-polish.md`
- `.claw/tasks/TASK-204.md`
- `.claw/assignments/TASK-204.yaml`
- `.claw/task-board.md`
- `.claw/current-status.md`

## Handoff

- 目标分支：`codex/TASK-204-agent-builder-avatar-polish`。
- 保留未跟踪 `diagrams/`，本任务不读取、不修改、不提交。
