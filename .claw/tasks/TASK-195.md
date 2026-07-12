---
kind: task-status
task_id: TASK-195
title: 客户互动时间线完整年份显示
status: in_progress
owner_role: frontend-agent
assignee: MANAGER-001
spec_path: docs/specs/FEAT-081-customer-interaction-workbench.md
assignment_path: .claw/assignments/TASK-195.yaml
updated_at: 2026-07-12T03:50:00Z
updated_by: MANAGER-001
---

# TASK-195 - 客户互动时间线完整年份显示

## 目标

让新客户推进、老客户经营和完整互动时间线中的每条事件明确显示四位年份，避免跨年度记录只显示月日造成歧义。

## 验收

- 时间线日期固定显示 `YYYY-MM-DD`，时间显示 `HH:mm`。
- 紧凑概览与完整时间线共用同一格式。
- 日期列、图标和垂直时间轴保持对齐，不挤压事件正文。
- 自动化测试、生产构建和桌面端浏览器验证通过。
