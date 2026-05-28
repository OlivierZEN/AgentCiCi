---
kind: task-status
task_id: TASK-139
status: review
updated_at: 2026-05-27T10:03:59Z
updated_by: DEV-fengchu
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

- `git diff --check`: passed.
- `npm run test -- AgentBuilderShell` in `frontend/`: not completed because this new worktree has no local `vitest` binary.
- `mvn -Dtest=AgentDefinitionListIntegrationTest test` in `backend/`: not completed because local PostgreSQL database `agentcici_test` does not exist.
- Per user instruction on 2026-05-27, no further tests were run.
- After the card-click/delete-placement follow-up, `git diff --check` passed again.

## Handoff

- Created branch/worktree `codex/TASK-139-agent-list-openapi-badge` at `/Users/xuhm/Documents/cc-agentcici-task139`.
- `/agents` list payload now includes each Agent's enabled `channels`.
- Frontend Agent mapping now preserves an empty `channels` array from the API instead of replacing it with default channels.
- Agent Builder grid cards now make the card body click target fill the whole card area above the delete action, and custom-Agent delete stays at the lower-right corner.
- Added focused backend list coverage and frontend channel-normalization coverage, but full test execution is pending a prepared frontend dependency install and local test database.
- Ready for merge to `dev` per user request.
