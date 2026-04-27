---
updated_at: 2026-04-24T12:51:17Z
status: active
feature_id: FEAT-002
---

# FEAT-002 CiCi Session Continuity And State

## Goal

- 让思思在同一会话中具备稳定的多轮连续理解能力，避免“上一轮已确认，下一轮又重新追问”。
- 明确区分“长期用户记忆”和“当前会话执行状态”，避免把本轮任务状态误写进长期记忆。
- 为后续 Agent runtime 与工具编排提供稳定的会话状态底座。

## Problem

- 当前前端会持续复用同一个 `sessionId`，用户看到的是连续会话，但后端聊天编排在组 prompt 时没有回灌该 `sessionId` 的历史消息。
- `ChatOrchestratorService.buildInitialMessages(...)` 当前仅注入：
  - system prompt
  - 当前用户问题
  - RAG 参考内容
  - 用户长期记忆
- 现有 `UserMemoryService` 和 `memory_remember` 更适合存储“跨会话的长期信息”，不适合表达“本会话已确认动作 / 当前对象 / 缺失字段 / 下一步动作”。
- 因此在类似“先添加名单，不发邮件”的场景里，思思第二轮可能重新索要上一轮已确认的信息，造成协作中断。

## Verified Current Behavior

- 前端发送消息时，真实会话场景使用当前 `conversationId` 作为 `sessionId`，说明 UI 已经具备会话连续性。
- `chat_message` 已持久化 `session_id / role_code / content / created_at`，说明消息历史已落库。
- `sessionMessages(...)` 会读取会话历史给 UI 展示，但这些历史消息没有参与下一轮模型调用。
- `UserMemoryService.listForInjection(...)` 只注入长期用户记忆，不包含当前会话状态。

## Design

### 1. Session History Injection

- 在 `ChatOrchestratorService` 中新增会话历史装配逻辑。
- 每次处理新问题时，读取当前 `sessionId` 最近 N 条历史消息，按时间正序注入模型上下文。
- 建议默认窗口：
  - 最近 12 到 20 条消息
  - 必要时按 token/字符长度截断
- 需要排除刚刚持久化的“当前用户输入”，避免重复注入。
- 建议消息装配顺序：
  - system prompt
  - 长期用户记忆块
  - 当前会话状态摘要
  - 最近历史消息
  - 当前用户问题

### 2. Session State Layer

- 新增 `chat_session_state` 持久层，作用域为 `org_id + session_id`。
- 该层只保存“当前会话的执行事实”，不保存跨会话偏好。
- 建议字段：
  - `session_id`
  - `org_id`
  - `agent_id`
  - `summary`
  - `state_json`
  - `updated_at`
- `state_json` 建议结构：

```json
{
  "intent": "campaign_member_add",
  "current_object_type": "campaign",
  "current_object_id": "xxx",
  "current_object_name": "春季金融行业触达活动",
  "target_segment_summary": "保险经纪/财富管理/商业银行/融资租赁",
  "candidate_lead_count": 20,
  "confirmed_actions": ["add_members"],
  "deferred_actions": ["send_email"],
  "missing_fields": [],
  "next_action": "call add_campaign_member"
}
```

### 3. State Injection Rules

- 在 system prompt 前部增加“当前会话执行状态”块。
- 该块应使用稳定字段，不依赖自然语言长摘要。
- 推荐注入格式：

```text
## 当前会话执行状态
- 已确认动作：先添加名单
- 暂缓动作：暂不发送邮件
- 当前对象：春季金融行业触达活动
- 当前目标范围：保险经纪/财富管理/商业银行/融资租赁
- 缺失字段：无

规则：
- 已确认的信息不得重复追问。
- 只有 missing_fields 非空或状态冲突时才允许追问。
- 追问时只问缺失项，不要回退到重新澄清整段需求。
```

### 4. State Update Strategy

- 不把 session state 更新完全交给模型自觉完成。
- 状态更新拆成两路：
  - 工具结果驱动的确定性更新
  - 用户确认语句驱动的语义更新

- 工具结果驱动：
  - 调用 `get_lead_data` 后，更新 `candidate_lead_count`、`target_segment_summary`
  - 调用 `add_campaign_member` 后，更新 `confirmed_actions`、`next_action`
  - 调用发信相关工具后，更新 `deferred_actions` 或发送状态

- 用户语义驱动：
  - 当用户表达“先做 A，不做 B”“按刚才那批客户处理”“继续上一步”等确认/拒绝/承接语义时，更新 session state
  - 这一步可以先用轻量规则抽取，复杂场景再补 LLM 抽取器

### 5. Boundary With User Memory

- `UserMemoryService` 继续承担长期记忆：
  - 用户身份
  - 持久偏好
  - 长期行为指令
  - 稳定工作背景
- `ChatSessionStateService` 承担会话内状态：
  - 当前任务意图
  - 当前对象
  - 已确认动作
  - 暂缓动作
  - 缺失字段
  - 下一步动作
- 不允许把“先添加名单，暂不发邮件”直接写成长期用户记忆，避免污染后续会话。

### 6. Runtime Rule For CiCi

- 在思思主聊天 prompt 中增加硬性规则：
  - 如果会话状态中已有 `confirmed_actions / current_object / target_segment / missing_fields`，不得重复询问相同信息。
  - 仅当 `missing_fields` 非空、上下文冲突、或执行所需主键无法推导时，才允许追问。
  - 若只缺主键，则只能追问该主键，不得整体回退到重新澄清需求。

## Implementation Plan

1. 在 `ai` 域新增 `ChatSessionStateEntity / Repository / Service`。
2. 为 `ChatMessageRepository` 增加“读取最近 N 条历史消息”的查询能力。
3. 改造 `ChatOrchestratorService.buildInitialMessages(...)`，把会话历史和 session state 一并注入。
4. 在工具调用完成后追加 session state reducer，基于工具名和结果做确定性更新。
5. 在用户输入进入编排前增加一层轻量会话状态抽取，识别“确认/拒绝/承接”语义。
6. 补一个只读接口，供管理端或调试页查看当前 session state。

## Implementation Progress

- 已完成（2026-04-24）：
  - `V22__chat_session_state.sql`：新增会话状态表。
  - `ChatSessionStateEntity / Repository / Service`：已支持状态持久化、状态注入块组装、用户输入与工具结果基础 reducer。
  - `ChatMessageRepository`：已支持按窗口读取最近消息。
  - `ChatOrchestratorService`：`buildInitialMessages(...)` 现已注入会话历史与会话状态块，并在工具调用后写回 session state。
  - `ChatController`：新增 `GET /ai/sessions/{sessionId}/state`。
  - 测试：`OrchestratorIntegrationTest` 新增 `shouldPersistSessionStateAfterUserIntentHint` 并通过；`ChatRealtimeIntegrationTest` 回归通过。
- 待完成：
  - 增强 reducer 的语义抽取精度，补齐 `current_object_*`、`missing_fields`、`next_action` 等字段的稳定更新。
  - 增补“同一 session 第二轮不重复追问已确认信息”的专项集成测试断言。

## Acceptance

- 在同一个 `sessionId` 下，第二轮回复应能利用上一轮明确结论，不重复询问相同信息。
- 当用户明确说“先做 A，不做 B”后，后续回复应优先执行 A，并把 B 视为暂缓动作。
- 当执行所需字段已在历史消息或 session state 中存在时，思思不得重复索要。
- 当执行所需字段确实缺失时，思思只追问缺失字段，不重新做整段需求澄清。
- 长期用户记忆与当前会话状态边界清晰，不能相互污染。

## Verification

- 后端集成测试：
  - 同会话两轮对话，第二轮可读取上一轮已确认动作
  - session state 注入后，缺失字段为空时不再重复追问
  - 工具调用后可正确更新 `state_json`
- 管理端 / 助手端人工回归：
  - 复现“先添加名单，先不要发邮件”场景
  - 验证第二轮不会重新询问活动范围与筛选标准

## Handoff Notes

- 本特性是“思思稳定多轮协作”的基础能力，优先级高于继续打磨单一话术。
- 若后续开始实现“运行时绑定 published workflow”，应优先复用本特性的 session state 层，而不是再造一套临时上下文缓存。
