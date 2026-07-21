---
kind: task-status
task_id: TASK-217
status: in_progress
updated_at: 2026-07-21T00:00:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-217.yaml
spec_path: docs/specs/FEAT-122-runtime-execution-trace-correction.md
---

# TASK-217 - 智能体定时任务真实创建与链路事实纠偏

## Scope

- 实现当前用户/当前智能体范围内的真实个人定时任务创建与调度执行。
- 修正工作流解析和实际执行在 Trace 中混淆的问题，并校正 always-on 技能计数。
- 完成后端、前端和桌面端回归，按发布 Runbook 进行线上验证。

## Current State

- 生产记录 `df5e12f4` 已确认未创建 trigger，未调用工具；当前只返回定时获客参数 JSON。
- 已确认个人 workflow 已有真实 trigger 与 scheduler，但聊天运行时没有创建入口；当前 workflow runtime 仅解析代码并伪报执行事实。

## Next Action

- 完成授权文档推送后实现工具、调度执行、Trace 事实纠偏和管理端文案。

## Verification

- 待实施。
