---
kind: task-status
task_id: TASK-218
status: in_progress
updated_at: 2026-07-21T12:00:00Z
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

- 已确认根因：OneKeyToken 使用 `static-catalog` 与三个 `defaultModels`，前后端将其当作“预设模型”显示。

## Next Action

- 完成服务端目录能力语义、前端空态和定向回归测试。
