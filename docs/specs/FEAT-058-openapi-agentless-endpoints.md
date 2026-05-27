---
kind: feature-spec
feature_id: FEAT-058
title: OpenAPI agentless endpoints
status: approved
owner_role: fullstack-agent
task_ids: TASK-140
related_decisions: none
related_issues: B20260527-RANDOM
updated_at: 2026-05-27T03:32:12Z
updated_by: MANAGER-001
---

# FEAT-058 - OpenAPI Agentless Endpoints

## Metadata

- source_feedback: `B20260527-RANDOM`
- status: `validated-bug-ready`
- owner_role: `fullstack-agent`
- created_at: 2026-05-27
- task: `TASK-140`

## Bug Verification

Static verification on 2026-05-27 confirmed the bug:

- `backend/src/main/java/com/codehouse/ciciassistant/openapi/api/AgentOpenApiController.java` maps all public conversation endpoints under `/openapi/v1/agents/{agentId}/...`.
- `AgentOpenApiAuthService.authenticate(agentId, request)` still requires the path Agent ID and compares it with the API Key's bound Agent.
- `frontend/src/assistant/AgentOpenApiDocsDialog.tsx` builds every public endpoint example with `/agents/${agentId}/...`.
- `docs/specs/FEAT-021-agent-open-api.md` and `docs/specs/FEAT-036-agent-open-api-dify-parity.md` also document Agent ID in the public OpenAPI path.

## Problem

The public OpenAPI URL structure exposes the Agent ID in every endpoint even though the API Key is already bound to an Agent. This creates redundant URL structure and makes external integration less stable. The feedback explicitly states this is still test phase, so backward compatibility with existing customers is not required.

## Goals

- Remove Agent ID from public OpenAPI conversation endpoint paths.
- Resolve the target Agent from the authenticated API Key.
- Keep admin key-management endpoints unchanged unless implementation finds a direct conflict.
- Update frontend OpenAPI docs and durable specs to show the new canonical paths.

## Non Goals

- No compatibility alias for `/openapi/v1/agents/{agentId}/...` unless needed for internal tests during migration.
- No change to admin `/agents/{agentId}/api-keys` or `/agents/{agentId}/api-calls` management APIs.
- No change to API Key generation or storage format.

## Proposed Public Routes

- `GET /openapi/v1/parameters`
- `POST /openapi/v1/chat-messages`
- `POST /openapi/v1/chat-messages/{taskId}/stop`
- `GET /openapi/v1/conversations`
- `GET /openapi/v1/messages`
- `POST /openapi/v1/conversations/{conversationId}/name`
- `DELETE /openapi/v1/conversations/{conversationId}`
- `POST /openapi/v1/messages/{messageId}/feedbacks`
- `GET /openapi/v1/messages/{messageId}/suggested`
- `POST /openapi/v1/files/upload`

## Backend Design

- Change public controller request mapping from `/openapi/v1/agents` to `/openapi/v1`, removing `@PathVariable agentId` from public handlers.
- Change auth service to authenticate from the API Key alone and return the bound Agent ID through `AuthenticatedCredential`.
- Conversation service methods should use `auth.credential().getAgentId()` or equivalent authenticated context rather than a path parameter.
- Preserve org, credential, Agent, external user, and conversation isolation checks.
- Update integration tests that call public OpenAPI endpoints.

## Frontend And Docs Design

- `AgentOpenApiDocsDialog` should build path constants without Agent ID.
- Standalone `/admin/agent-builder/:agentId/openapi-docs` can keep the admin page route because it selects which Agent's docs and keys to manage.
- Update examples in help content and specs that describe public call URLs.

## Acceptance Criteria

- Public OpenAPI examples no longer contain `/openapi/v1/agents/{agentId}` or concrete Agent IDs in the URL path.
- API Key bound Agent determines execution target.
- Calls with a valid key for a published API-enabled Agent succeed on the new paths.
- Calls with invalid, inactive, expired, IP-denied, unpublished, or API-disabled keys keep existing error semantics.
- Focused backend OpenAPI integration tests pass with the new route shape.
- Frontend docs build and show the new path shape.

## Verification Plan

- Backend focused test: `AgentOpenApiIntegrationTest`.
- Frontend build: `npm run build`.
- Targeted `rg` for old public path examples.
- Desktop browser smoke for the OpenAPI docs route if local frontend can run.
- `git diff --check`.

## Handoff Notes

- This is a contract change. Keep it separate from smaller docs-copy cleanup so review can focus on route and auth semantics.
