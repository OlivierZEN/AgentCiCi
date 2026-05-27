---
kind: task-status
task_id: TASK-137
status: ready
updated_at: 2026-05-27T03:37:58Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-137.yaml
spec_path: docs/specs/FEAT-056-custom-agent-delete.md
---

# TASK-137 - Custom Agent delete action

## Scope

- Implement `FEAT-056` so admins can delete custom, non-built-in Agents from Agent Builder.
- Add backend delete semantics, frontend list action, confirmation dialog, and list refresh.

## Source Feedback

- `R20260526-BV2U1` from the fixed `功能需求` document.

## Initial Analysis

- The closest existing pattern is custom Skill deletion with impact checking and historical preservation.
- Deletion must not remove system built-in Agents or cross organization boundaries.

## Acceptance

- Custom Agent rows expose delete.
- Built-in Agent rows cannot be deleted.
- Confirmation names the Agent and explains historical evidence retention.
- Success removes the Agent from the list without refresh.
- Backend tests cover deletion safety.

## Verification

- Pending implementation.

## Handoff

- Assigned to Owen / `MANAGER-001` on branch `codex/TASK-137-custom-agent-delete`. Run task-scoped `dev-login.py` before implementation.
