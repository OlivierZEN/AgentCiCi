---
kind: task-status
task_id: TASK-235
status: review
updated_at: 2026-07-23T04:35:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-235.yaml
spec_path: docs/specs/FEAT-133-agent-runtime-mixed-orchestration.md
---

# TASK-235 - 混合智能体运行时 P1：计划状态机基础

## Scope

- 新建组织隔离的任务运行、计划、步骤和事件持久化模型及 PostgreSQL 迁移；
- 实现严格计划 Schema 校验、顺序步骤状态机、乐观锁与恢复租约；
- 提供只读、无工具执行的服务端创建/推进入口及真实事件投影；
- 为后续聊天接入保留稳定服务契约，但本任务不改变聊天请求路径。

## Non-goals

- 不选择 Direct/ReAct/Plan-Exec 模式，不调用模型、RAG、记忆或任何工具；
- 不执行、确认或重试外部副作用，不接入 OpenAPI、评测、费用或前端；
- 不实现重规划、Reflect、并行步骤、计划编辑器或任意工作流代码执行。

## Acceptance

- 计划拒绝循环依赖、未知步骤、空目标和超过预算的步骤；
- 在 PostgreSQL 上可持久化并按依赖顺序推进步骤，非法状态迁移和并发更新安全拒绝；
- 运行重领只允许失效租约，步骤事件与持久化状态一致；
- 定向单元/集成测试、后端编译、迁移验证和 diff 检查通过。

## Implementation result

- V91 建立 `agent_task_run`、`agent_task_plan`、`agent_task_step`、`agent_task_event`，并为组织、会话、Agent、状态与恢复查询建立索引。
- `AgentTaskRuntimeService` 仅实现只读计划事实：创建运行、附加受限 JSON 计划、按依赖认领/完成步骤、失败、乐观锁冲突拒绝和失效租约恢复；没有接入模型、工具、聊天路径或外部副作用。
- 集成测试覆盖依赖推进与事件事实、循环计划/过期版本拒绝、仅失效租约可恢复。

## Next action

- 进行代码复核；通过后以独立任务将该稳定契约灰度接入 Chat/OpenAPI，仍不得绕过现有工具确认与审计边界。
