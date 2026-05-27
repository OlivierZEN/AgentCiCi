---
kind: task-status
task_id: TASK-140
status: ready
updated_at: 2026-05-27T03:37:58Z
updated_by: MANAGER-001
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

- Pending implementation.

## Handoff

- Assigned to `DEV-fengchu` on branch `codex/TASK-140-openapi-agentless-endpoints`. This is an API contract change and should be reviewed separately from the docs-copy cleanup task.
