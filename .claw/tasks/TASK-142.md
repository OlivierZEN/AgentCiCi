---
kind: task-status
task_id: TASK-142
status: ready
updated_at: 2026-05-28T04:00:37Z
updated_by: MANAGER-001
assignee: DEV-fengchu
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-142.yaml
spec_path: docs/specs/FEAT-060-openapi-chat-messages-sse-streaming.md
---

# TASK-142 - OpenAPI chat-messages SSE streaming

## Scope

- Implement `FEAT-060` for true SSE streaming on OpenAPI `chat-messages`.
- Ensure `response_mode=streaming` and `responseMode=streaming` do not buffer the full answer before emitting `message` events.
- Preserve blocking mode, OpenAPI auth/scope semantics, conversation/message persistence, and stop-task scoping.
- Update OpenAPI docs examples so JSON requests use `Content-Type: application/json` and streaming responses use `Accept: text/event-stream`.

## Source Feedback

- `B20260527-SSE01` from the 飞书 `BUG反馈` document.
- Source doc: `https://zucfl0psd6.feishu.cn/docx/LsEHde0xiopy67xZuzqc9j59nug`.

## Verification Before Task Creation

- Confirmed from 飞书 doc on 2026-05-28: status was `待分配`.
- Confirmed by static code review on 2026-05-28.
- `AgentOpenApiConversationService.chatMessagesStream(...)` currently calls blocking `chatMessages(...)` and only sends SSE events after the complete answer is available.
- `TASK-140 / FEAT-058` is changing the canonical public route to `/openapi/v1/chat-messages`; implement this bugfix against that route shape.

## Acceptance

- Streaming mode emits incremental SSE `message` events before terminal `message_end`.
- Backend tests prove event order and more-than-one event streaming behavior.
- Blocking mode remains compatible.
- Stop endpoint remains scoped and deterministic for streaming task IDs.
- Docs examples use JSON request content type and SSE response accept/header guidance correctly.

## Verification

- Pending implementation.

## Handoff

- Assigned to `DEV-fengchu` on branch `codex/TASK-142-openapi-sse-streaming`.
- Run task-scoped `dev-login.py` before implementation.
- Coordinate with `TASK-140`; do not restore old `/openapi/v1/agents/{agentId}/...` public routes.
