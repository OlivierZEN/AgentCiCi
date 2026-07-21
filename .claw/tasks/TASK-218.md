---
kind: task-status
task_id: TASK-218
status: review
updated_at: 2026-07-21T12:15:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-218.yaml
spec_path: docs/specs/FEAT-123-provider-catalog-capability.md
---

# TASK-218 - 厂商模型目录能力边界

## Scope

- 移除 OneKeyToken 的本地预设目录，未开放远程枚举时返回并显示空目录。
- 保留显式保存的选择，不改动其他厂商的远程枚举逻辑。

## Current State

- 已移除 OneKeyToken 的本地默认模型及静态目录回退。厂商未开放远程枚举时，接口返回空目录和 `catalogSource: unavailable`；已显式保存的模型选择不做删除。

## Next Action

- 等待审阅或新的合并、发布授权；本次未部署生产。
