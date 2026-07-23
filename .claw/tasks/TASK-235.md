---
kind: task-status
task_id: TASK-235
status: ready
updated_at: 2026-07-23T04:30:00Z
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

## Next action

- 完成分配提交与主线推送后，切换到 `codex/TASK-235-agent-task-runtime-foundation` 并通过任务级身份与授权检查。
