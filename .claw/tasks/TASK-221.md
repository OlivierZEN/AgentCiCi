---
kind: task-status
task_id: TASK-221
status: ready
updated_at: 2026-07-22T00:30:00+08:00
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: frontend-agent
assignment_path: .claw/assignments/TASK-221.yaml
spec_path: docs/specs/FEAT-126-admin-theme-completeness-audit.md
---

# TASK-221 - 组织管理端全页面主题一致性治理

## Scope

- 审查并收敛 `/admin/*` 的页面主体、二级页面、弹窗、抽屉、折叠详情、菜单、表格和表单主题。
- 保留全部 Admin 功能、权限、API、数据和桌面布局。

## Current State

- 已确认 Admin 外壳 token 化不完整，后续资源页样式和工具页内联类别色仍可能覆盖当前主题。
- FEAT-126 已记录完整页面清单、实施原则和验收标准。

## Next Action

- 切换到专用分支，完成任务级门禁后实施共享 Admin theme 覆盖和必要页面内联样式修复。
