---
kind: feature-spec
feature_id: FEAT-018
title: Chat conditional knowledge retrieval
status: completed
owner_role: frontend-backend-chat-runtime
task_ids: TASK-049
related_decisions: none
related_issues: none
updated_at: 2026-05-06T08:14:52+08:00
updated_by: ai
---

# FEAT-018 - Chat Conditional Knowledge Retrieval

## 背景与目标

- 用户观察到与智能体的每次对话都会先检索知识库，导致寒暄、纯业务工具查询和轻量写作也进入 RAG 链路。
- 目标是把运行时从“默认先检索”调整为“先做轻量意图判断，再按需检索、调用工具或直接回答”。
- 本次交付后，只有明确知识问答、用户显式选择知识库，或问题文本指向制度/文档/知识库依据时才触发 RAG；前端只在后端真实进入 RAG 时显示“检索中”。

## 范围

### In Scope

- 后端 `/ai/chat` 与 `/ai/chat/stream` 在 RAG 前增加轻量意图门控。
- 保留已有 RAG phase 事件；未触发 RAG 时不发送 `retrieving` / `rag_done`。
- 调整内置 Agent 默认提示，避免继续表达“回答前先检索知识库”的绝对策略。
- 调整工作台初始状态文案，避免前端仅凭“审批/客户/报价/线索”关键词先展示“检索中”。
- 增加后端单元测试覆盖 RAG 门控策略。

### Out Of Scope

- 不新增独立 LLM router、模型分类接口或数据库配置项。
- 不改变知识库索引、向量召回、切片、权限过滤和 SSE 协议字段。
- 不改变业务工具 schema 或 CloudCC 查询流程。

## 用户场景

- 用户说“你好”“上才艺”“帮我写一段通知”：直接进入生成或工具边界判断，不先查知识库。
- 用户问“根据知识库说明一下报销制度”“这份产品文档里怎么说”：先检索知识库再回答。
- 用户问“看下今天的潜在客户”“查订阅台账明细”：优先进入业务工具判断，知识库只在问题明确要求制度、文档或依据时介入。
- 用户手动勾选知识库后提问：尊重显式选择，触发 RAG。

## 现状与约束

- 当前 `ChatOrchestratorService` 在模型与工具循环前直接调用 `ragService.retrieveDetailed(...)`。
- `SkillResolverService` 在 Agent/Skill 有默认知识库且请求未指定知识库时，会返回默认知识库。
- 前端工作台会用关键词把部分问题预设成“检索中”，这可能早于真实后端 RAG。

## 方案设计

- 在后端新增 deterministic 轻量门控：
  - 无有效知识库：不检索。
  - 请求显式携带知识库 ID：检索。
  - 寒暄、闲聊、轻量创作、常识解释：不检索。
  - 业务数据查询/审批/客户/台账/明细等操作型请求：默认不检索。
  - 知识库、文档、制度、流程、规则、手册、依据、政策、FAQ 等知识型信号：检索。
- 将门控结果统一用于普通与流式聊天，保证行为一致。
- 前端初始状态改为“正在分析任务”，后续只响应后端 `retrieving` phase 切换为“检索中”。

## 接口与数据影响

- API 请求与响应结构不变。
- SSE 事件结构不变；区别是非知识型任务不再发送 RAG phase。
- 数据库无迁移。

## 任务拆分

- `TASK-049 Chat conditional knowledge retrieval`

## 验收标准

- 寒暄、闲聊、轻量创作类输入不会先触发 `ragService.retrieveDetailed`。
- 显式知识库选择或知识型问题会触发 RAG，并保持现有 `retrieving` / `rag_done` 可观测性。
- 工作台初始状态不再在真实 RAG 前显示“检索中”。
- 聚焦测试和构建通过。

## 风险与回滚

- 风险：关键词门控可能漏掉某些应检索的问题。
- 降级：用户显式勾选知识库仍强制检索；可继续补充知识型关键词。
- 回滚：移除门控判断，恢复原先只要有效知识库非空就检索。

## 实现进展

- 状态：已完成。
- 已完成项：
  - `ChatOrchestratorService` 新增条件 RAG 门控并接入普通/流式聊天。
  - 工具边界提示新增知识库使用边界。
  - 内置 Agent 和 Agent Builder 默认提示取消“回答前先检索知识库”的绝对表达。
  - 工作台初始状态不再预显示“检索中”。
  - 已补 `ChatOrchestratorServiceModelIdentityTest` 覆盖门控行为。
- 未完成项：真实登录态人工复测。

## 交接说明

- 先看 `ChatOrchestratorService` 的 RAG 门控 helper，再看 `AssistantApp.tsx` 的工作台初始状态。
