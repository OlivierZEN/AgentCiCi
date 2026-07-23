---
kind: task-status
task_id: TASK-237
status: ready
updated_at: 2026-07-23T05:30:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: backend-agent
assignment_path: .claw/assignments/TASK-237.yaml
spec_path: docs/specs/FEAT-133-agent-runtime-mixed-orchestration.md
---

# TASK-237 - 混合智能体运行时 P3：规则优先模式路由

## Scope

- 实现默认关闭、精确 Agent 白名单的服务端规则路由器，输出稳定的模式、原因码、风险、预算、确认与审查需求；
- 在既有 Chat、流式和 OpenAPI 汇合路径中使用同一决策；`PLAN_EXEC` 必须同时命中 P3 路由与 P2 灰度；
- 保持会话、组织隔离、工具确认、审计、计费和 Trace 的现有边界。

## Non-goals

- 不引入模型自由选模式、任何新工具或写入、确认机制、Reflect/reviewer、重规划、并行、管理 UI、评测发布门禁或生产放量；
- 不放宽 P2 默认关闭、精确白名单与无工具固定计划。

## Acceptance

- 简单无工具请求为 `DIRECT`，独立只读请求为 `REACT`，显式依赖多源请求为 `PLAN_EXEC`，且具有稳定原因码；
- P3 关闭、白名单不命中、特征解析异常或 P2 未命中时保持既有链路并不创建新计划；
- 规则测试、既有聊天回归、后端编译、全新 PostgreSQL 集成迁移和静态 diff 检查通过。
