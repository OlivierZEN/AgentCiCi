---
kind: feature-spec
feature_id: FEAT-184
title: DevAutopilot 已确认需求规范状态
status: implemented
primary_project: agentcici
task_ids: TASK-303
related_integrations: INT-020
updated_at: 2026-08-14T00:52:00Z
updated_by: codex
---

# FEAT-184 - DevAutopilot 已确认需求规范状态

## 问题

`semattice_project_delivery_create` 只有在 HUMAN 用户明确确认后才会创建需求，并已在 `intake.confirmed_at`、`confirmed_by_principal_id` 保存确认事实，但当前创建载荷仍写入 `status=待确认`。DevAutopilot 将该状态解释为尚未确认并禁止制定任务，造成确认创建与任务派发之间断链。

## 契约

- `create_requirement` 只在受控确认路径执行，因此新记录的规范状态为 `已确认`。
- 未收到明确确认时继续只生成草案，零写入 Semattice。
- `intake` 继续保存确认人、确认时间、对话和 correlation；状态字段不替代这些审计事实。
- 不批量或直接修改历史数据；历史记录由 DevAutopilot 的 HUMAN 确认入口通过 `runtime.record.update` 逐条恢复。
- 项目、任务、缺陷和变更的既有初始状态不随本特性变化。

## 验收

1. 确认式需求创建请求发送到 Semattice 的 `status` 为 `已确认`。
2. 写后回读必须精确匹配 `已确认`，否则不能返回成功。
3. 草案、短确认恢复、字段保真、父项目解析和幂等行为不回退。
4. 定向测试和 backend package 通过；本地开发环境从 AgentCiCi 本地 `main` 构建并回读版本。

## 回滚

回滚本任务提交后，新需求恢复旧的 `待确认` 写入语义；已经成功写入 `已确认` 的业务记录不自动降级。
