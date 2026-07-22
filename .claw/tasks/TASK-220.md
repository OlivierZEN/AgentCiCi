---
kind: task-status
task_id: TASK-220
status: review
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
- 快捷指令/技能菜单、弹窗、输入区操作和会话历史已改为读取当前主题 token；任务授权复核、9 项定向主题测试与前端生产构建均通过。

## Next Action

- 使用已登录蓝色主题账号打开用户工作台，检查弹窗、菜单、工具按钮、会话历史及 keyboard focus；本地 Browser 仅到登录边界，未伪造认证态截图。
