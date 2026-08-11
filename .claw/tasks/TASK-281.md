---
kind: task-status
task_id: TASK-281
status: done
updated_at: 2026-08-11T14:08:00+08:00
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
- UAT `2.8.61-beta.7` 已完成真实缺陷正负向：草案与字段补充均不写入，只有完整精确确认触发 PM SERVICE 写入；回执与 Semattice 回读一致为 `BUG-11164588 / revision 1`，短确认负向不新增记录。
- DevAutopilot `1.0.4-beta.3` 已读取同一缺陷并完成 `new → confirmed`、负责人分配与 revision `1 → 2`；Semattice 回读状态、负责人和 correlation 一致。
- `2.8.61-beta.8 / 9a37f5d6036a` 将 Owner 身份查询与租户应用管理解耦。Demo Company 缺 Owner 时只显示治理告警，不再阻断平台管理员同步标准模板；同步后 A/B 两租户均为 7 对象，A=0 缺陷、B=1 缺陷，正式 handoff 页面隔离一致。

## Done When

- 流式与非流式回复共用成功声明硬门禁。
- 缺陷创建、查询、状态流转 Tool 使用租户专属 PM SERVICE，并完成写后回读。
- 标准产品经理 Agent/Workflow 幂等补偿新 Tool。
- 定向测试、完整相关前端测试、构建和 UAT 正负向通过；完整 Maven 的本机 PostgreSQL/Hikari 边界保持如实记录。

## Release Boundary

- UAT 候选沿下一生产版本 `2.8.61-beta.N` 递增。
- 未获得单独生产发布授权前不发布生产。
