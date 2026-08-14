---
kind: feature-spec
feature_id: FEAT-187
title: 产品经理按开发者花名转派排队任务
status: implemented
primary_project: agentcici
task_ids: TASK-307
related_integrations: INT-022
updated_at: 2026-08-14T09:45:00+08:00
updated_by: codex
---

# FEAT-187 - 产品经理按开发者花名转派排队任务

## 设计

产品经理从当前租户 active 的 DevAutopilot Developer Profile 解析“鲁班”“哪吒”等花名，不向用户暴露 Principal ID。用户提出转派时只生成草案；精确回复 `确认将鲁班的任务转交给哪吒` 后才执行。

只转派 `待开始` 或 `已批准待执行` 的任务。运行、设计确认、测试和发布中的任务保持原负责人，避免破坏执行实例和交付门禁。转派调用专用 `runtime.record.transfer`，同时改变 Semattice 的真实 `owner_principal_id`；它不复用通用字段更新，也不改变数据归属组织。

## 验收

- 中文花名能唯一解析为当前租户有效 Developer Profile。
- 草案不写入，且不显示内部主体 ID。
- 确认后只转派排队任务，并要求 owner/revision 的实时回读。
- 产品经理有独立 transfer 权限，开发者没有。

## 回滚

移除 AgentCiCi 的确定性路由后不再产生新转派；已经生效的 Owner 变更保留为 Semattice 审计事实。
