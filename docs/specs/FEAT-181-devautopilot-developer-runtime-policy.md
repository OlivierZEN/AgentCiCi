---
kind: feature-spec
feature_id: FEAT-181
title: DevAutopilot 机器开发者实例上限控制面
status: implemented
primary_project: agentcici
task_ids: TASK-300
related_integrations: INT-018
---

# FEAT-181 - DevAutopilot 机器开发者实例上限控制面

AgentCiCi 是 Developer Profile 及其运行策略的权威提供方。每个 developer `SERVICE_PRINCIPAL` 资源保存 `max_instances`（1–64）与 `runtime_policy_revision`；创建默认为 1，组织管理员可用乐观锁更新，审计只记录前后数值和版本。

`GET /api/admin/devautopilot/team` 与官方 activation 读取契约返回这两个字段。`PUT /api/admin/devautopilot/team/developers/{principalId}/runtime-policy` 仅允许当前租户 ORG_ADMIN 更新当前激活应用内的 developer，冲突返回 409。

机器账户交换 Semattice OACT 时，服务端从当前激活资源查询并签入 `max_instances`。请求方不能提交或覆盖该声明；非 developer SERVICE 不签入。应用、开发者或责任人失效时既有失败关闭链不变。

验收：创建与编辑 UI 支持 1–64；策略版本冲突失败；签名 OACT 含权威上限；非法上限拒绝；不保存、显示或审计 Secret。
