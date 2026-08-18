---
kind: task-status
task_id: TASK-322
feature_id: FEAT-197
status: review
priority: high
owner_role: backend-agent
claimed_by: codex
updated_at: 2026-08-18T14:36:46Z
updated_by: codex
---

# TASK-322 - Agent Definition 对外身份一致性

## 范围

- 以 `AgentDefinition.name` 作为 Agent 唯一对外称呼。
- 移除平台基础提示对所有 Agent 的 `You are CiCi` 身份声明。
- 将 Definition 名称注入普通聊天、流式聊天和候选评测的统一运行时提示。
- 增加默认 Agent 与自定义 Agent 身份回归，提交本地 `main` 并更新 `cici.localhost` backend。

## 完成条件

- Agent 自我介绍和回答“你是谁”时精确使用 Definition 名称。
- `CiCi / AgentCiCi` 只表示承载平台；只有 Definition 名称自身包含该品牌的 Agent 才能这样自称。
- 角色、Skill、工具、模型供应商、模型名称和内部 `agentId` 不得覆盖对外名称。
- 聚焦测试、package、diff check 和本地真实产品经理问候通过。
- 不修改 UAT、生产、DevAutopilot 或 Semattice。

## 当前证据

- 故障 Trace 命中 `devautopilot-pm`，Tool 与 RAG 均为 0；输出“我是 CiCi”来自全局 `You are CiCi` 基础提示，而不是 Agent 定义或业务数据。
- 当前产品经理 `AgentDefinition.name=研发产品经理`，该值是用户确认的唯一对外称呼。
- 代码提交 `e91b28d6` 已将 Definition 名称加入统一技能上下文和权威身份提示；身份聚焦 48 项、合并聚焦 65 项、package、diff check 和本地 backend 技术门禁通过。
- 本地运行版本为 `2.8.61-dev.e91b28d`，healthy/restart=0，正式 DevAutopilot 路由 200；真实“你好”消息待用户在发送动作前即时确认。

## 回滚

- 回滚本任务代码提交并从上一 AgentCiCi 本地 `main` 提交重建 backend。
- 不修改 Agent Definition、工作流版本或业务数据。
