---
kind: task-status
task_id: TASK-236
status: ready
updated_at: 2026-07-23T04:50:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-236.yaml
spec_path: docs/specs/FEAT-133-agent-runtime-mixed-orchestration.md
---

# TASK-236 - 混合智能体运行时 P2：Chat/OpenAPI 受限灰度

## Scope

- 为精确 Agent 白名单提供默认关闭的 Plan-Exec 灰度配置，并由服务端决定是否启用；
- 在既有 Web 聊天、流式聊天和 OpenAPI 汇合路径接入同一运行事实与兼容响应扩展；
- 仅复用当前认证、组织隔离、RAG、模型、Tool Orchestrator、人工确认、Trace、计费与 OpenAPI 限流/幂等链路；
- 对计划解析、校验或执行失败提供无副作用的安全回退与脱敏原因。

## Non-goals

- 不实现通用模式路由、LLM 自由选模式、重规划、Reflect、并行、管理 UI 或生产放量；
- 不引入新工具执行、凭据解析、确认、审计、计费或 OpenAPI 身份机制；
- 不允许任何写工具进入灰度，也不修改前端或渠道适配器。

## Acceptance

- 关闭或白名单不命中时，Web 与 OpenAPI 保持既有 Direct/ReAct 结果与兼容字段；
- 命中时创建可追溯运行事实，流式/非流式和 OpenAPI 具有一致 `runtimeExecution` 语义；
- 计划/执行异常不触发新工具副作用，并留下最小脱敏回退原因；
- 认证、组织隔离、确认、Trace、OpenAPI 限流/幂等和账单回归通过；后端编译、定向集成测试、全新 PostgreSQL 迁移与 diff 检查通过。

## Next action

- 切换到 `codex/TASK-236-plan-exec-chat-canary`，通过任务级身份门禁与授权检查后，先以回归测试锁定现有 Web/OpenAPI 兼容契约。
