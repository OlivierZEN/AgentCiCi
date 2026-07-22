---
kind: task-status
task_id: TASK-223
status: in_progress
updated_at: 2026-07-22T00:40:00+08:00
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-223.yaml
spec_path: docs/specs/FEAT-128-schedule-cadence-parser-repair.md
---

# TASK-223 - 定时任务周期解析越界修复

## Scope

- 修复“每天 09:00”创建个人定时任务时的 `IndexOutOfBoundsException`。
- 添加后端回归测试，并以定向验证证明真实 trigger 创建路径不再越界。

## Current State

- 截图已确认周期补充为“每天 09:00”后创建失败。
- 已定位到 `UserWorkflowService` 时钟正则与其捕获组读取不一致；尚未修改实现。

## Verification

- 待实现后记录真实 Maven 定向测试与编译结果。
