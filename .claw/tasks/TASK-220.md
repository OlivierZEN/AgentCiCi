---
kind: task-status
task_id: TASK-220
status: in_progress
updated_at: 2026-07-22T00:00:00+08:00
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: frontend-agent
assignment_path: .claw/assignments/TASK-220.yaml
spec_path: docs/specs/FEAT-125-user-workbench-surface-polish.md
---

# TASK-220 - 用户会话工作台浮层与操作面主题收敛

## Scope

- 统一快捷指令弹窗、快捷指令/技能菜单、输入区操作与会话历史选中态。
- 保持用户功能、API、权限及桌面布局不变。

## Current State

- 用户已确认当前选择蓝色主题，但弹窗和多个小型交互表面仍错误地显示鎏金账房色值。
- 根因与完整设计已写入 FEAT-125；与现有平台端未提交改动无文件重叠。

## Next Action

- 已通过 TASK-220 任务级身份与写入范围检查；实施基于现有 `--theme-*` token 的样式及必要的无障碍语义修复。
