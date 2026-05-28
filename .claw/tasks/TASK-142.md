---
kind: task-status
task_id: TASK-142
status: review
updated_at: 2026-05-28T06:08:52Z
updated_by: DEV-fengchu
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

- Passed: task-scoped `dev-login.py` for `DEV-fengchu` on branch `codex/TASK-142-openapi-sse-streaming`.
- Passed: `mvn -DskipTests compile` in `backend/`.
- Passed: `mvn -Dtest=AgentOpenApiIntegrationTest test` in `backend/` after local Flyway state was repaired.
- Passed: `npm run build` in `frontend/`; Vite large chunk warning unchanged.
- Passed: `git diff --check`.
- Passed: merged to `dev` and pushed as `e30421b` for test-environment integration.
- Passed: Codeup change request to `main` created: `https://codeup.aliyun.com/627b18115b46541dd2ff340e/cloudcc-aidev-projects/cc-agentcici/change/23`.
- Note: first focused test run failed before assertions because the local test database had resolved migration `53` missing from `flyway_schema_history`; one repair run with `-Dspring.flyway.out-of-order=true` applied the already-present migration metadata, then the normal command passed.

## Progress

- Created branch `codex/TASK-142-openapi-sse-streaming`.
- Reused `ChatOrchestratorService` streaming runtime instead of wrapping the blocking OpenAPI chat path.
- Added an OpenAPI SSE bridge that maps internal `delta` events to incremental OpenAPI `message` events, then emits one terminal `message_end`.
- Preserved blocking chat behavior, OpenAPI auth/scope/session/message persistence, idempotent replay, and stop task scoping.
- Updated OpenAPI docs copy to clarify JSON request content type and `Accept: text/event-stream` for streaming responses.
- Created Codeup change request `change/23` targeting `main`.

## Changed Files

- `backend/src/main/java/com/codehouse/ciciassistant/ai/service/ChatOrchestratorService.java`
- `backend/src/main/java/com/codehouse/ciciassistant/openapi/service/AgentOpenApiRunService.java`
- `backend/src/main/java/com/codehouse/ciciassistant/openapi/service/AgentOpenApiConversationService.java`
- `backend/src/test/java/com/codehouse/ciciassistant/openapi/AgentOpenApiIntegrationTest.java`
- `frontend/src/assistant/AgentOpenApiDocsDialog.tsx`
- `docs/specs/FEAT-036-agent-open-api-dify-parity.md`

## Handoff

- Assigned to `DEV-fengchu` on branch `codex/TASK-142-openapi-sse-streaming`.
- Run task-scoped `dev-login.py` before implementation.
- Coordinate with `TASK-140`; do not restore old `/openapi/v1/agents/{agentId}/...` public routes.
- Implementation is under Codeup review: `https://codeup.aliyun.com/627b18115b46541dd2ff340e/cloudcc-aidev-projects/cc-agentcici/change/23`.
