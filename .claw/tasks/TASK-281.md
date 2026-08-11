---
kind: task-status
task_id: TASK-281
status: review
updated_at: 2026-08-11T08:45:00Z
updated_by: codex
assignee: codex
owner_role: integration-agent
assignment_path: n/a
spec_path: docs/specs/FEAT-169-devautopilot-defect-trusted-receipt.md
integration_id: INT-009
---

# TASK-281 - DevAutopilot 缺陷 Tool 与可信写入回执

## Current State

- 已确认根因为缺少 `dev_defect` 对象/Tool，且模型成功声明没有服务端回执门禁。
- 本地实现已完成：确定性缺陷草稿/确认、租户 PM SERVICE 写入、`runtime.record.get` 写后回读、字段/revision/correlation 校验、流式结构化回执和前端独立回执卡片均已落地。
- 定向后端、完整前端测试和前端构建已通过；完整 Maven 套件因本机 PostgreSQL/Hikari 连接重试被人工停止，不能记为全绿。
- 等待 Semattice `TASK-073` 先发布 UAT 后，再发布 AgentCiCi `2.8.61-beta.4` 并执行正负向业务验收。

## Done When

- 流式与非流式回复共用成功声明硬门禁。
- 缺陷创建、查询、状态流转 Tool 使用租户专属 PM SERVICE，并完成写后回读。
- 标准产品经理 Agent/Workflow 幂等补偿新 Tool。
- 定向测试、完整相关测试、构建和 UAT 正负向通过。

## Release Boundary

- UAT 候选沿下一生产版本 `2.8.61-beta.N` 递增。
- 未获得单独生产发布授权前不发布生产。
