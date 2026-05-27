---
kind: task-status
task_id: TASK-138
status: ready
updated_at: 2026-05-27T03:37:58Z
updated_by: MANAGER-001
assignee: DEV-fengchu
owner_role: frontend-agent
assignment_path: .claw/assignments/TASK-138.yaml
spec_path: docs/specs/FEAT-057-openapi-docs-copy-cleanup.md
---

# TASK-138 - OpenAPI docs copy cleanup

## Scope

- Implement `FEAT-057` for OpenAPI documentation copy and example cleanup.
- Remove duplicated auth explanation.
- Rename `CLOUDCC_PAGE_TOKEN` to `CLOUDCC_OPENAPI_TOKEN`.
- Add CloudCC help links for accessToken and baseUrl.

## Source Feedback

- `R20260527-5R8TN` and `R20260527-3M7KP` from the fixed `功能需求` document.

## Initial Analysis

- This should be a small frontend documentation task unless it lands after `TASK-140`, in which case it must use the new agentless OpenAPI routes.

## Acceptance

- No visible product docs/UI example uses `CLOUDCC_PAGE_TOKEN`.
- Sending-message examples use `CLOUDCC_OPENAPI_TOKEN`.
- CloudCC accessToken and baseUrl help links are present near the CloudCC context explanation.
- Duplicate auth explanation is removed.

## Verification

- Pending implementation.

## Handoff

- Assigned to `DEV-fengchu` on branch `codex/TASK-138-openapi-docs-copy-cleanup`. Coordinate with `TASK-140` if both are active.
