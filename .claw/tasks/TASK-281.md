---
kind: task-status
task_id: TASK-281
status: in_progress
updated_at: 2026-08-11T08:00:00Z
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
- 正在先实现无回执不得声称成功，再消费 Semattice 缺陷契约。

## Done When

- 流式与非流式回复共用成功声明硬门禁。
- 缺陷创建、查询、状态流转 Tool 使用租户专属 PM SERVICE，并完成写后回读。
- 标准产品经理 Agent/Workflow 幂等补偿新 Tool。
- 定向测试、完整相关测试、构建和 UAT 正负向通过。

## Release Boundary

- UAT 候选沿下一生产版本 `2.8.61-beta.N` 递增。
- 未获得单独生产发布授权前不发布生产。
