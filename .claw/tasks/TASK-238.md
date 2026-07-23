---
kind: task-status
task_id: TASK-238
status: ready
updated_at: 2026-07-23T06:00:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: backend-agent
assignment_path: .claw/assignments/TASK-238.yaml
spec_path: docs/specs/FEAT-133-agent-runtime-mixed-orchestration.md
---

# TASK-238 - 混合智能体运行时 P4：受控 Reflect 与评测门禁

## Scope

- 新增组织隔离的审查事实、默认关闭的精确 Agent 灰度和确定性 Gate；
- 仅对 P2/P3 已真实启动、且 P3 决定需要审查的运行投影 `PASS`、`REVISE` 或 `HANDOFF`；
- 将脱敏 Gate/审查事实接入现有 Chat、Trace 和 FEAT-106 评测/发布门禁读取路径。

## Non-goals

- 不新增工具、写入、凭据、确认、自由重规划、并行、管理 UI 或生产放量；
- 不暴露或保存模型思维链，不允许 reviewer 覆盖确定性安全、权限或确认阻断。

## Acceptance

- 开关/白名单不命中时不改变既有路径；Gate 阻断时 reviewer 不可放行；
- 审查 Schema 严格受限、修订只限无副作用最终回答，超出上限转人工；
- 组织隔离、规则/评测断言、后端编译、全新 PostgreSQL 迁移与静态 diff 检查通过。
