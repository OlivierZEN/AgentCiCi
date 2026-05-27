---
kind: task-status
task_id: TASK-139
status: ready
updated_at: 2026-05-27T03:37:58Z
updated_by: MANAGER-001
assignee: DEV-fengchu
owner_role: frontend-agent
assignment_path: .claw/assignments/TASK-139.yaml
spec_path: docs/specs/PROJECT-BASELINE.md
---

# TASK-139 - Agent list OpenAPI badge shows only first Agent

## Scope

- Fix Agent Builder list state so every Agent shows the correct Open API channel status.
- Keep the existing Agent Builder visual language and list density.

## Source Feedback

- `B20260527-XK9RQ` from the fixed `BUG反馈` document.

## Verification Before Task Creation

- Confirmed by static code review on 2026-05-27.
- `backend/src/main/java/com/codehouse/ciciassistant/agent/api/AgentDefinitionController.java` list endpoint maps each Agent through `toDefinitionPayload`, which does not include `channels`.
- `frontend/src/assistant/AgentBuilderShell.tsx` then fetches detail only for the first Agent in the list and uses that detail to populate `channels`.
- Other Agents are mapped from summary payloads without channel data, so their list badges cannot reliably show the `api` channel.

## Proposed Fix

- Preferred: include enabled `channels` in the `/agents` list payload.
- Alternative: frontend loads details for every listed Agent, but this is less efficient and should be avoided unless backend scope is unavailable.
- Ensure `toAgentRecordFromApi` does not replace an empty real channel list with fallback default channels for persisted Agents.

## Acceptance

- Multiple Agents with API enabled all show Open API status in the list.
- Agents without API enabled do not falsely show Open API.
- Focused test covers at least two Agents where only the second has API enabled.

## Verification

- Pending implementation.

## Handoff

- Assigned to `DEV-fengchu` on branch `codex/TASK-139-agent-list-openapi-badge`. This is a small bugfix but likely touches both backend list payload and frontend mapping tests.
