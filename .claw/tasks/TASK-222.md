---
kind: task-status
task_id: TASK-222
status: review
updated_at: 2026-07-22T08:55:00+08:00
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: project-manager
assignment_path: .claw/assignments/TASK-222.yaml
spec_path: docs/specs/FEAT-127-local-branch-integration.md
---

# TASK-222 - 本地遗留分支审查与主线整合

## Scope

- 审查并整合 TASK-160、TASK-203、TASK-204、TASK-210 的未合并提交。
- 保留当前主线的最新冲突侧，验证实际代码和测试价值。

## Current State

- 四个分支已在专用整合分支合并；状态文件冲突保留当前 `main`，代码冲突按最新实现兼容整合。
- TASK-204 补齐了被冲突丢失的头像菜单状态与测试 CSS 读取；TASK-160 在当前异常处理器中补入 `ResponseStatusException` 状态映射并有无 Spring 上下文的单元回归。
- 前端定向测试 25/25、生产构建、Python 编译与 `git diff --check` 通过。多租户 Spring 集成测试被既有共享库 Flyway V81 checksum 不匹配阻断，未执行 repair。

## Next Action

- 将已验证的整合分支快进合并并推送到 `main`。
