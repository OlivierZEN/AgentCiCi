---
kind: task-status
task_id: TASK-140
status: in_progress
updated_at: 2026-05-27T06:02:52Z
updated_by: DEV-fengchu
assignee: DEV-fengchu
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-140.yaml
spec_path: docs/specs/FEAT-058-openapi-agentless-endpoints.md
---

# TASK-140 - Remove Agent ID from public OpenAPI routes

## Scope

- Implement `FEAT-058` to remove Agent ID from public OpenAPI endpoint paths.
- Resolve target Agent from API Key authentication.
- Update backend tests and frontend docs examples.

## Source Feedback

- `B20260527-RANDOM` from the fixed `BUG反馈` document.

## Verification Before Task Creation

- Confirmed by static code review on 2026-05-27.
- Public backend controller is rooted at `/openapi/v1/agents` and every conversation endpoint includes `/{agentId}`.
- Frontend OpenAPI docs build all public paths with `/agents/${agentId}/...`.
- The auth service already validates that path Agent ID matches the API Key bound Agent, making the path segment redundant for the requested contract.

## Acceptance

- Canonical public routes are `/openapi/v1/parameters`, `/chat-messages`, `/conversations`, `/messages`, `/files/upload`, and related child routes without Agent ID.
- Valid API Key determines the Agent.
- Existing error semantics for bad keys, disabled channels, unpublished Agents, and scope denial remain stable.
- Frontend OpenAPI docs show the new route shape.

## Verification

- `python3 /Users/xuhm/.codex/skills/cc-aidev-guidelines-common/scripts/dev-login.py .claw --ssh-key /Users/xuhm/.ssh/id_ed25519_agentcici_fengchu --developer DEV-fengchu --git-username Bimo --task TASK-140 --branch codex/TASK-140-openapi-agentless-endpoints --files backend/src/main/java/com/codehouse/ciciassistant/openapi backend/src/test/java/com/codehouse/ciciassistant/openapi frontend/src docs/specs/FEAT-058-openapi-agentless-endpoints.md docs/specs/FEAT-021-agent-open-api.md docs/specs/FEAT-036-agent-open-api-dify-parity.md .claw/tasks/TASK-140.md --no-cache --json` -> allowed.
- `mvn -DskipTests compile` in `backend/` -> passed.
- `mvn -DskipTests test-compile` in `backend/` -> passed.
- `npm run build` in `frontend/` -> passed; Vite reported the existing large chunk warning.
- `git diff --check` -> passed.
- Service startup and browser smoke intentionally not run; user requested no service testing before unified validation.

## Handoff

- Assigned to `DEV-fengchu` on branch `codex/TASK-140-openapi-agentless-endpoints`. This is an API contract change and should be reviewed separately from the docs-copy cleanup task.

## Progress

- Created branch `codex/TASK-140-openapi-agentless-endpoints`.
- Public OpenAPI controller routes now use `/openapi/v1/...` without path Agent ID.
- OpenAPI runtime auth now resolves the target Agent from the API Key credential.
- Conversation service public methods no longer accept path Agent ID and continue using the authenticated credential for Agent, org, credential, session, task, message, feedback, and file isolation.
- Updated focused OpenAPI integration test request paths and added old public conversation-path 404 coverage.
- Updated CORS test paths, OpenAPI docs dialog examples, help quickstart examples, and related durable specs for the agentless public route shape.

## Changed Files

- `backend/src/main/java/com/codehouse/ciciassistant/openapi/api/AgentOpenApiController.java`
- `backend/src/main/java/com/codehouse/ciciassistant/openapi/service/AgentOpenApiAuthService.java`
- `backend/src/main/java/com/codehouse/ciciassistant/openapi/service/AgentOpenApiConversationService.java`
- `backend/src/test/java/com/codehouse/ciciassistant/openapi/AgentOpenApiIntegrationTest.java`
- `backend/src/test/java/com/codehouse/ciciassistant/openapi/config/AgentOpenApiCorsConfigTest.java`
- `frontend/src/assistant/AgentOpenApiDocsDialog.tsx`
- `frontend/src/help/helpContent.ts`
- `docs/specs/FEAT-021-agent-open-api.md`
- `docs/specs/FEAT-036-agent-open-api-dify-parity.md`
- `.claw/tasks/TASK-140.md`
