---
kind: task-status
task_id: TASK-224
status: in_progress
updated_at: 2026-07-22T09:55:00+08:00
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-224.yaml
spec_path: docs/specs/FEAT-129-release-startup-constructor-injection-hotfix.md
---

# TASK-224 - 生产发布构造器注入启动热修

## Scope

- 修复 `AuditService` 与 `PlatformAuditService` 的 Spring 构造器选择，使生产容器可启动。
- 添加最小回归测试并用新版本完成发布验证。

## Current State

- `2.8.2` 的 V84 已成功迁移，但后端因 `AuditService` 无法选择构造器而重启；已回滚至健康的 `2.8.1`。

## Next Action

- 完成构造器注入修复与 Spring 上下文测试，走新的不可变发布版本。
