---
kind: task-status
task_id: TASK-234
status: done
updated_at: 2026-07-23T12:15:00+08:00
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: project-manager
assignment_path: .claw/assignments/TASK-234.yaml
spec_path: docs/specs/FEAT-132-release-version-patch-limit.md
---

# TASK-234 - 发布修订版本号上限调整为365

## Scope

- 修改统一发布脚本的生产版本校验与递增逻辑，使修订段最大为 365。
- 更新发布文档并增加脚本级回归。

## Completion

- 已将修订版本上限设为 365，主、次版本上限仍为 12。
- 脚本回归验证 `2.8.364 → 2.8.365`、`2.8.365 → 2.9.1`、`2.12.365 → 3.0.1`，并拒绝 `2.8.366`。
- 未执行生产发布。
