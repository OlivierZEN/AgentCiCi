---
kind: feature-spec
feature_id: FEAT-152
title: DEV Autopilot 产品经理确认式研发记录创建
status: in_progress
owner_role: backend-agent
task_ids: TASK-259
related_decisions: "OACT 同租户边界；对话写入必须明确确认"
related_issues: none
updated_at: 2026-07-31T03:00:00Z
updated_by: MANAGER-001
---

# FEAT-152 - DEV Autopilot 产品经理确认式研发记录创建

## 背景与目标

- 研发交付产品经理已可实时读取同租户 Semattice 研发交付模型，但用户在对话中提出“创建项目”时仍得到只读限制说明。
- 产品经理必须能代表当前登录成员创建项目、需求和任务记录，并将结果直接写入同租户已发布的 Semattice 模型。
- 写入必须避免自然语言误触发：先返回清晰草案，只有用户发送精确确认指令后才调用写入能力，并回执实际创建的记录编号。

## 范围

### In Scope

- 为 `dev-autopilot-pm` 增加受服务器控制的 Semattice `runtime.record.create` 原生能力。
- 对项目、需求、任务提供草案提示和明确确认语法；写入时根据当前用户 OACT 锁定公司和成员。
- 自动解析父项目/父需求，建立项目—需求—任务关系；记录创建回执和工具审计轨迹。
- 更新产品经理运行时策略、生产智能体提示词、定向测试和生产验证。

### Out Of Scope

- 不开放删除、任意字段更新、跨租户写入或由模型直接调用的自由写工具。
- 不变更 Semattice 的对象元数据、审批流程、独立 DEV Autopilot 前端或认证体系。

## 用户场景

1. 用户说“现在创建一个棕榈地的研发项目”，智能体给出名称、初始状态和确认指令，而不是称无权限。
2. 用户发送“确认创建项目：棕榈地”，服务端在当前租户创建记录并返回实际项目编号。
3. 用户按草案格式确认创建需求或任务；服务端查找父记录，创建并返回关联编号。找不到或歧义时不写入并要求补充编号。

## 现状与约束

- Semattice 研发交付模型已发布：5 个对象、37 个字段；运行时能力为 `runtime.record.create`。
- AgentCiCi 为当前成员签发短期 OACT，Semattice 从该令牌推导 tenant/actor，参数不可提供 tenant、user 或 token。
- 现有 `semattice_project_delivery_query` 保持只读事实查询。创建能力仅能由服务端识别的精确确认消息合成调用，不能进入模型的可选 function schema。

## 方案设计

1. 新增写入服务，严格验证对象、参数和字段白名单，使用当前成员 OACT 调用 Semattice 创建能力。
2. 聊天编排器在研发产品经理会话中确定性处理创建意图：非确认消息返回草案；确认消息才合成内部工具调用、记录审计并直接返回创建结果。
3. 需求和任务写入前先只读查询父对象；名称匹配不唯一或不存在即失败，不执行写入。
4. 智能体策略明确：可以创建，但不得绕过确认，也不得声称创建成功而没有 Semattice 回执。

## 接口与数据影响

- 新增内部 native tool：`semattice_project_delivery_create`，不暴露给模型 function-calling schema。
- 不新增 AgentCiCi 数据表或 Semattice 元数据；写入既有 `dev_project`、`dev_requirement`、`dev_task`。
- Semattice 请求使用 `runtime.record.create` 和幂等键；创建字段仅含已发布模型字段。

## 验收标准

- 非确认创建请求返回草案和可复制确认指令，不写入 Semattice。
- 三类确认创建都使用当前租户 OACT 成功写入，返回实际 code/record_id，并维持父子关联。
- 项目事实查询仍从 Semattice 实时读取；所有定向 JUnit、编译、状态校验与生产对话/记录核验通过。

## 风险与回滚

- 风险：模型或用户文本歧义导致错误父级关联；以精确确认语法、唯一匹配和失败不写入控制。
- 风险：Semattice 短暂故障；保留失败回执，不伪造成功。
- 回滚：回退 AgentCiCi backend/frontend 镜像至 `2.8.31`；已创建的演示记录可在后续受控流程中人工处理。

## 实现进展

- 已开始，待完成代码、生产智能体提示词和真实项目/需求/任务闭环验证。

## 交接说明

- 先查看 `SematticeProjectDeliveryToolService`、`ChatOrchestratorService` 和本任务卡。
- 生产验证必须使用 `org5nszpgj99jaysxv6y` 对应的线上 Semattice 租户，且不得记录可复用令牌。
