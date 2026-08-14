---
kind: feature-spec
feature_id: FEAT-185
title: DevAutopilot 用户确认后的产品经理委托执行
status: implemented
primary_project: agentcici
task_ids: TASK-304
related_integrations: INT-020
updated_at: 2026-08-14T01:14:07Z
updated_by: codex
---

# FEAT-185 - DevAutopilot 用户确认后的产品经理委托执行

## 问题

DevAutopilot 浏览器会话代表 HUMAN，Semattice 中该 HUMAN 按应用管理员模板只有业务只读权限；正式研发写入属于产品经理 SERVICE。若 DevAutopilot 把 HUMAN OACT 直接转发给 `runtime.record.update/create`，对象授权会正确拒绝，需求确认和任务创建无法闭环。

## 契约

- 新增受认证后端接口 `POST /api/devautopilot/execution-authorizations`；公司和成员只从 AgentCiCi 签发的 ecosystem HUMAN token 推导。
- 请求只能选择 `REQUIREMENT_CONFIRM` 或 `TASK_PLAN_CONFIRM`，调用方不能提交 scope、Agent ID、SERVICE Principal ID、tenant 或 company。
- AgentCiCi 复用现有 `AgentServicePrincipalExecutionService` 校验 HUMAN 成员、Agent RUN、DevAutopilot 应用角色、产品经理绑定/生命周期、负责人和 SERVICE scope。
- `REQUIREMENT_CONFIRM` 固定签发 `runtime.record.read/update`；`TASK_PLAN_CONFIRM` 固定签发 `runtime.record.read/create`。
- 返回短时 Semattice OACT 前，必须核对实际 SERVICE 与激活记录中的 primary 产品经理 Principal 一致，并记录实际委托人和机器执行人审计。
- OACT 只允许 DevAutopilot 服务端短时消费，不进入浏览器 JavaScript、URL、仓库、日志或业务记录。

## 验收

1. 未获得 CONTRIBUTOR 或更高应用角色的 HUMAN 不能委托写入。
2. 产品经理 Agent、SERVICE、绑定、owner、tenant、scope 或生命周期任一不一致时失败关闭。
3. 未知操作不能自选 scope。
4. 聚焦测试和 backend package 通过；真实需求写入的 Semattice actor 是产品经理 SERVICE，确认人单独保留为 HUMAN。

## 回滚

回滚接口和 DevAutopilot 消费提交后，HUMAN 仍保持只读，受影响写操作恢复为失败关闭；不扩大 HUMAN Semattice 权限，也不直接修改授权表。
