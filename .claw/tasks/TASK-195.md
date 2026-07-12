---
kind: task-status
task_id: TASK-195
title: 客户互动时间线完整年份显示
status: done
owner_role: frontend-agent
assignee: MANAGER-001
spec_path: docs/specs/FEAT-081-customer-interaction-workbench.md
assignment_path: .claw/assignments/TASK-195.yaml
updated_at: 2026-07-12T04:11:00Z
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

## 完成结果

- 时间线专用格式固定为 `YYYY-MM-DD` 与 `HH:mm` 两行，日期内部禁止折行。
- 完整时间线 22 条、概览 5 条真实 CRM 记录均显示四位年份；其中包含 `2026` 与 `2023` 跨年记录。
- 日期列、图标中心与垂直轴偏差为 `0px`，页面无外层溢出。
- 前端 62 项测试及生产构建通过，已发布生产 `2.5.8 / a016c165fd95`。
- 截图：`output/playwright/task195-prod-timeline-full-year-2.5.8.png`。
