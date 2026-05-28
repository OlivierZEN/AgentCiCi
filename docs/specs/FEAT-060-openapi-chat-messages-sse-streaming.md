---
kind: feature-spec
feature_id: FEAT-060
title: OpenAPI chat-messages SSE streaming
status: approved
owner_role: fullstack-agent
task_ids: TASK-142
related_decisions: none
related_issues: B20260527-SSE01
updated_at: 2026-05-28T04:00:37Z
updated_by: MANAGER-001
---

# FEAT-060 - OpenAPI chat-messages SSE Streaming

## Metadata

- source_feedback: `B20260527-SSE01`
- source_doc: `https://zucfl0psd6.feishu.cn/docx/LsEHde0xiopy67xZuzqc9j59nug`
- status: `validated-bug-ready`
- owner_role: `fullstack-agent`
- created_at: 2026-05-28
- task: `TASK-142`

## Source Feedback

飞书 `BUG反馈` 文档记录：

- 模块：OpenAPI - 流式返回
- 描述：请求 `https://autoservice.agentcici.com/openapi/v1/agents/cici-system/chat-messages` 时，已配置 `content-type: text/event-stream`，但没有按流式方式返回，而是一次性全部返回。
- 状态：待分配
- 提出人：Owen
- 提出日期：2026-05-27

说明：`TASK-140 / FEAT-058` 已将公开 OpenAPI 路由调整为不包含 Agent ID。本文档的验收以新的 canonical 路由 `POST /openapi/v1/chat-messages` 为准；旧路径只作为反馈来源证据，不要求兼容。

## Bug Verification

2026-05-28 静态核查确认当前实现存在一次性返回风险：

- `AgentOpenApiController.chatMessages(...)` 在 `responseMode=streaming` 时返回 `SseEmitter`。
- `AgentOpenApiConversationService.chatMessagesStream(...)` 内部异步调用 blocking 的 `chatMessages(...)`，等待完整回答生成、保存消息后，才依次发送 `agent_thought`、单条 `message` 和 `message_end`。
- 因此即使 HTTP 响应使用 `text/event-stream`，对外表现仍接近“完整回答生成后一次性吐出”，不能满足真实流式体验。

## Problem

OpenAPI `chat-messages` 对外承诺 `response_mode=streaming` 时返回 SSE。外部系统通常会用它驱动客服窗口、CRM 嵌入页或 CloudCC 页面中的逐字输出。如果服务端只在模型完成后发送一条完整消息，前端无法展示生成过程，也无法可靠配合停止生成接口。

## Goals

- `POST /openapi/v1/chat-messages` 在 `response_mode=streaming` 或 `responseMode=streaming` 时真正按 SSE 增量发送。
- 流式事件必须在最终回答完成前发送至少一条可见增量，不能只在末尾发送完整 answer。
- 保持 blocking 模式现有响应结构和持久化语义。
- 保持 API Key 鉴权、scope、Agent 发布状态、OpenAPI channel、user/conversation/file 隔离语义。
- 让 `POST /openapi/v1/chat-messages/{taskId}/stop` 与 streaming 任务状态保持可观测；若底层暂不能真正中断模型，也必须稳定标记 `cancel_requested` 并停止后续可控输出。
- 更新 OpenAPI 文档示例，明确 streaming 请求需要使用 `Accept: text/event-stream`，请求体仍使用 JSON。

## Non Goals

- 不恢复或兼容 `/openapi/v1/agents/{agentId}/...` 旧公开路由。
- 不改变 admin API Key 管理接口。
- 不引入新的 OpenAPI Key 格式、计费模型或消息表结构，除非实现证明没有现有字段可承载 task/message 状态。
- 不要求本任务解决所有模型供应商的底层取消能力；取消能力不足时记录清晰限制。
- 不新增移动端专门适配或移动端自动化验收。

## Design

### Route And Request Contract

Canonical endpoint:

- `POST /openapi/v1/chat-messages`

Streaming request:

```json
{
  "query": "请介绍保修政策",
  "user": "customer-001",
  "conversation_id": "crm-session-001",
  "response_mode": "streaming"
}
```

Recommended headers:

```text
Authorization: Bearer <AGENTCICI_API_KEY>
Content-Type: application/json
Accept: text/event-stream
```

`responseMode` camelCase alias must continue to work.

### Streaming Runtime

Implementation should avoid wrapping the blocking `chatMessages(...)` path as the streaming path. The preferred shape is:

- Authenticate and validate request before starting model work.
- Create task/session/message context early enough to emit stable `task_id` and `message_id`.
- Reuse the assistant runtime's existing streaming capability where practical.
- Forward model deltas as SSE `message` events with incremental `answer` text or delta text that external clients can append.
- Emit `agent_thought` / tool events when available, without blocking the first message delta on the whole run.
- Emit exactly one terminal `message_end` event with metadata, usage, trace, and final identifiers.
- Persist the final assistant message after stream completion, preserving history and feedback APIs.

If the internal runtime can only expose final text for some provider, the implementation must either extend the provider streaming path or explicitly mark that provider as non-streaming; it must not silently advertise streaming and then buffer the whole answer.

### Event Semantics

Expected event names remain aligned with existing docs:

- `message`: one or more incremental answer events.
- `agent_thought`: optional runtime/tool/thought events.
- `message_end`: terminal success event.
- `error`: terminal failure event.

Each event should include stable identifiers when available:

- `task_id`
- `message_id`
- `conversation_id`
- `event`

### Stop Semantics

The stop endpoint must continue to locate the task by credential, org, Agent, and task ID. For streaming tasks:

- If true cancellation is available, request it and stop emitting further deltas.
- If true cancellation is unavailable, mark `cancel_requested` and stop all output that the AgentCiCi layer can still control.
- The response must be deterministic and covered by tests.

### Frontend And Docs

Update OpenAPI docs surfaces if needed:

- `AgentOpenApiDocsDialog`
- help content that describes OpenAPI streaming
- related durable specs (`FEAT-036`, and `FEAT-058` if route text changes during implementation)

Documentation must not imply that `Content-Type: text/event-stream` is the request content type for a JSON body. Use `Accept: text/event-stream` for the response expectation.

## Acceptance Criteria

- `response_mode=streaming` returns `Content-Type` compatible with `text/event-stream`.
- A focused backend test proves the streaming response sends more than one SSE event and at least one `message` event is emitted before `message_end`.
- The streaming implementation no longer waits for the blocking `chatMessages(...)` result before sending the first answer event.
- `responseMode=streaming` and `response_mode=streaming` both work.
- Blocking mode continues to return the existing JSON response.
- Auth/scope/channel/published-Agent failures keep stable OpenAPI error semantics.
- Stop endpoint remains scoped to the authenticated API Key and returns stable task status for streaming tasks.
- OpenAPI docs no longer describe the request as `content-type: text/event-stream`; they show JSON request body plus SSE response.

## Verification Plan

- Backend focused test: `AgentOpenApiIntegrationTest` or a dedicated OpenAPI streaming test covering streaming event order and aliases.
- Backend compile/test: affected OpenAPI and AI runtime tests.
- Frontend build if docs UI/help content changes.
- Targeted `rg` for misleading `Content-Type: text/event-stream` request examples.
- Optional live smoke with `curl -N` against a local or test environment when credentials and model provider are available.

## Handoff Notes

- Coordinate with `TASK-140`: implement against `POST /openapi/v1/chat-messages` without Agent ID in the public path.
- Start inspection from `AgentOpenApiController`, `AgentOpenApiConversationService.chatMessagesStream(...)`, `AgentOpenApiRunService`, and `ChatOrchestratorService.chatStream(...)`.
- Keep this task focused on streaming behavior; do not bundle unrelated OpenAPI route cleanup or docs copy cleanup.
