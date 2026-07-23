---
kind: task-status
task_id: TASK-238
status: review
updated_at: 2026-07-23T06:30:00Z
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

## Implementation result

- V92 新增 `agent_task_review`，按组织、运行和审查轮次保存 Gate/审查状态、问题码和脱敏结果摘要；`REFLECT_GATE` 事件与 P1 运行事实同源。
- `AgentTaskReflectService` 在精确 Agent 白名单命中后执行确定性 Gate：组织与 Agent 一致、Plan-Exec 成功终态、全部步骤成功、步骤/审查轮次预算、确认和非空输出。首期 reviewer 为固定 Schema 的确定性审查，只有 `PASS` 或 `HANDOFF`，不调用模型、工具、凭据或写入。
- Chat/流式只投影最小审查状态；评测支持 `RUNTIME_MODE_EQUALS`、`REFLECT_STATUS_EQUALS` 与 `NO_WRITE_BEFORE_CONFIRMATION` 三类确定性断言。

## Verification

- `AgentTaskReflectServiceTest`、`AgentEvaluationAssertionEngineTest`、P2/P3 与 Chat 定向回归通过；后端 `compile` 与 `git diff --check` 通过。
- 新建后删除 PostgreSQL 16 临时库完整迁移 V1→V92，`AgentTaskRuntimeIntegrationTest` 5/5 通过，覆盖审查记录持久化、`REFLECT_GATE` 事件和跨组织拒绝。

## Next action

- 复核后集成至 `main`；随后使用设计治理创建独立 TASK 实施 P5 Trace 管理界面与桌面端验收。
