---
kind: feature-spec
feature_id: FEAT-197
title: Agent Definition 对外身份一致性
status: in_implementation
owner_role: backend-agent
task_ids: TASK-322
related_decisions: none
related_issues: none
updated_at: 2026-08-18T14:36:00Z
updated_by: codex
---

# FEAT-197 Agent Definition 对外身份一致性

## 背景与目标

DevAutopilot 产品经理真实问候命中了正确的 `devautopilot-pm`，但回答“我是 CiCi”。只读 Trace 显示该轮没有 Tool 或 RAG；根因是 AgentCiCi 全局基础提示把所有 Agent 都声明为 `CiCi`，而 Agent 专属提示只描述职责，没有覆盖平台身份。

用户确认 `AgentDefinition.name` 就是 Agent 的唯一对外称呼。本功能把平台品牌、内部路由标识、Agent 名称、职责和模型执行资源分离，避免自定义 Agent 继承默认平台身份。

## 身份契约

- `AgentDefinition.name`：唯一对外名称。自我介绍、自称和回答身份问题时必须精确使用该值。
- `agentId`：仅用于内部路由、审计和关联，不作为对外称呼。
- `CiCi / AgentCiCi`：承载平台名称，不是自定义 Agent 名称。
- `systemPrompt`：职责和行为边界，不得覆盖 Definition 名称。
- Skill、Tool、模型供应商和模型名称：能力或执行资源，不得覆盖 Definition 名称。
- 内置默认 Agent 继续通过自身 Definition 名称 `思思（CiCi）` 使用品牌，不依赖平台全局冒充。

## 实现设计

- 平台基础提示改为“运行在 CiCi 平台上的企业数字员工”，删除 `You are CiCi`。
- `SkillResolverService` 从当前租户的 `AgentDefinition.name` 解析 `agentName` 并写入统一 `ResolvedSkillContext`。
- `SkillPromptAssembler` 在平台政策、Agent 专属政策和 Skill 之前注入权威身份块；名称中的换行和制表符先归一为单行数据。
- 普通聊天、流式聊天和候选评测共用该上下文；没有有效名称时只回退到内部 `agentId`，不得回退为平台品牌。
- 会议纪要、客户洞察和 Skill 预览等手工构造上下文显式提供对应 Agent/预览名称，并移除其局部 `You are CiCi` 声明。

## 验收标准

- 业务运行源码除默认 Agent 自身 Definition 欢迎语外，不存在 `You are CiCi` 或等价的全局自我身份声明。
- 产品经理上下文包含 `AgentDefinition.name=研发产品经理` 的权威身份提示。
- 默认 Agent 上下文使用 `思思（CiCi）`；自定义 Agent 不得被平台名覆盖。
- 聚焦测试、后端 package、`git diff --check` 和本地真实问候通过。

## 风险与回滚

- 模型仍可能生成非精确称呼；通过前置权威提示和真实问候回归控制风险。
- Definition 名称由受治理配置维护；运行时仅归一控制字符，不解释名称为指令。
- 回滚代码并从上一主线提交重建 backend 即可；无数据库迁移或业务数据变更。
