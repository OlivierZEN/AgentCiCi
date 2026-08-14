---
kind: feature-spec
feature_id: FEAT-187
title: 产品经理按开发者花名转派排队任务
status: implemented
primary_project: agentcici
task_ids: TASK-307
related_integrations: INT-022
updated_at: 2026-08-14T07:35:00Z
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

## 故障修复

精确确认属于受治理写操作，必须在任意通用运行模式、执行计划或模型自由回复之前被识别并路由到服务端 transfer Service。复制自界面的 Markdown 包裹以及中英文终止标点不改变确认语义。不得在 transfer 调用后降级为只读查询或由模型补充内部 Principal ID。

当 `runtime.record.transfer` 未生效时，结果必须明确说明缺少该 scope 且未修改任务；不得返回泛化的“转派请求无效”。当结果声明转派成功时，必须包含 Semattice 返回并经读取验证的 `owner_principal_id` 与 `revision` 收据；无有效收据的成功文案一律拒绝输出。

受管 Semattice 授权 manifest 发生变更时，应用端必须使用新的 `devautopilot.authorization` 模板版本生成协调幂等键。这样相同版本的重复同步保持幂等，而新增 `dev_task / transfer` 权限的升级会真实重放到租户，不会被旧版本的幂等记录吞掉。

## 回滚

移除 AgentCiCi 的确定性路由后不再产生新转派；已经生效的 Owner 变更保留为 Semattice 审计事实。
