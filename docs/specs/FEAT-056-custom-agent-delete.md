---
kind: feature-spec
feature_id: FEAT-056
title: Custom Agent delete
status: implemented
owner_role: fullstack-agent
task_ids: TASK-137
related_decisions: none
related_issues: R20260526-BV2U1
updated_at: 2026-05-27T07:39:41Z
updated_by: MANAGER-001
---

# FEAT-056 - Custom Agent Delete

## Metadata

- source_feedback: `R20260526-BV2U1`
- status: `implemented`
- owner_role: `fullstack-agent`
- created_at: 2026-05-27
- task: `TASK-137`

## Problem

Organization admins can create custom Agents in Agent Builder, but the list has no delete action. Test or abandoned Agents remain visible and make the builder harder to scan.

## Goals

- Allow organization admins to delete custom, non-built-in Agents from the Agent Builder list.
- Confirm the destructive action before execution.
- Update the list immediately after successful deletion.
- Preserve historical audit, run trace, and OpenAPI call evidence as required by governance.

## Non Goals

- No deletion of system built-in Agents.
- No bulk delete in the first pass.
- No restore UI unless the implementation chooses soft delete and the product later asks for recovery.
- No mobile-specific layout or testing.

## Design

### Deletion Semantics

Prefer a backend soft delete or disabled-and-hidden model if the existing data model can support it without a risky migration. The normal Agent Builder list should hide deleted Agents. Historical runtime records, traces, OpenAPI logs, and audit evidence must remain queryable.

If hard delete is selected during implementation, the task must first document and test cascade behavior for:

- Agent definition and spec.
- Knowledge, tool, skill, channel, and publish config bindings.
- Workflow versions and published version pointer.
- Runtime schedule triggers and execution logs.
- OpenAPI credentials, call logs, conversations, messages, files, and feedback.

### API

Add an admin-only endpoint under the existing Agent definition controller:

- `DELETE /agents/{agentId}`

Expected behavior:

- Reject built-in Agents with a clear 409 or 400 response.
- Return 404 for unknown or already hidden Agents.
- Return a concise impact summary where useful, either inline or through a separate impact endpoint if implementation risk warrants it.
- Never delete across org boundaries.

### Frontend

Agent Builder list cards should expose a compact row action for custom Agents. The action must use the product design baseline:

- Use a text or compact icon action, not a large decorative button.
- Use a modal confirmation with `role="dialog"`, `aria-modal="true"`, a concrete title, and a clear danger action.
- Confirmation copy should name the Agent and explain that it disappears from the list while historical evidence is retained.

After success:

- Remove the Agent from local list state or reload `/agents`.
- If the deleted Agent was selected, select the next available Agent or return to the list empty state.
- Show a small success notice.

## Acceptance Criteria

- Custom Agent list rows show a delete action.
- Built-in Agent rows do not show delete, or show it disabled with a clear reason.
- Deleting a custom Agent requires confirmation.
- Successful delete removes the Agent from the list without page refresh.
- Unknown, built-in, or cross-org delete attempts fail safely.
- Backend tests cover success, built-in rejection, org isolation, and list hiding.
- Frontend tests or desktop browser smoke cover confirmation, success removal, and selected-Agent fallback.

## Verification Plan

- Backend focused tests for the new delete endpoint.
- Frontend build and focused UI test or Playwright desktop smoke.
- Static search to ensure no external product names are introduced into implementation identifiers.
- `git diff --check`.

## Handoff Notes

- The nearest existing pattern is custom Skill delete: it checks impact, confirms with the user, hides from normal lists, and preserves historical context.
- Keep any modal styling aligned with `DESIGN.md` and `DESIGN.json`.
- Implementation uses soft delete through the existing `enabled=false` field; disabled Agents are hidden from admin list/detail while database rows, workflow versions, runtime traces, OpenAPI call logs, and audit evidence remain intact.
- Verification passed frontend focused tests/build, backend compile, and whitespace diff check. Focused backend integration test class is present but local execution was blocked by unavailable PostgreSQL during Spring context startup.
