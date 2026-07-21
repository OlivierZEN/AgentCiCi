---
kind: task-status
task_id: TASK-217
status: in_progress
updated_at: 2026-07-21T00:00:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-217.yaml
spec_path: docs/specs/FEAT-122-runtime-execution-trace-correction.md
---

# TASK-217 - 智能体定时任务真实创建与链路事实纠偏

## Scope

- 实现当前用户/当前智能体范围内的真实个人定时任务创建与调度执行。
- 修正工作流解析和实际执行在 Trace 中混淆的问题，并校正 always-on 技能计数。
- 完成后端、前端和桌面端回归，按发布 Runbook 进行线上验证。

## Current State

- 生产记录 `df5e12f4` 已确认未创建 trigger，未调用工具；当前只返回定时获客参数 JSON。
- 已实现当前用户/当前 Agent 的 `workflow_schedule_create` 内置工具：它追加个人 workflow routine、发布版本并物化真实 trigger；周期无效或缺失时拒绝写入。
- 已让个人 workflow 在已授权时实际执行 Tavily 搜索；Trace 的工作流阶段改为“工作流定义检查”，并把 always-on Skill 计入已应用技能。
- 已在阻塞与流式会话入口加入定时任务周期追问保护：未提供周期不调用模型或写工具；提供明确周期时才向模型暴露创建工具。

## Next Action

- 完成桌面端验证与生产发布验收。

## Verification

- `mvn -q -DskipTests compile` -> passed.
- `mvn -q -Dtest=ToolOrchestratorServiceTest,ChatOrchestratorServiceModelIdentityTest,AgentRunTraceServiceTest,AgentWorkflowRuntimeSkillGovernanceTest test` -> passed.
- `mvn -q test` -> blocked by existing shared test database Flyway V81 checksum mismatch; no repair was applied.
