---
kind: task-status
task_id: TASK-223
status: done
updated_at: 2026-07-22T09:39:00+08:00
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
- 已确认根因：`UserWorkflowService` 时钟正则未定义捕获组，`inferTrigger` 却读取时段、小时和分钟组，合法时间文本因而抛出 `IndexOutOfBoundsException`。
- 已修复为显式三组捕获，并覆盖每日 09:00 与下午 3:30 的 cron 及下次执行时间计算。

## Verification

- `mvn -q -Dtest=UserWorkflowServiceTest test` -> passed.
- `mvn -q -DskipTests compile` -> passed.
- `git diff --check` -> passed.
- 未执行生产发布；上线前应按 `docs/production-release-runbook.md` 完成完整发布验证。
