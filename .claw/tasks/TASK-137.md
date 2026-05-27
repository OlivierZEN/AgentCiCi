---
kind: task-status
task_id: TASK-137
status: completed
updated_at: 2026-05-27T07:39:41Z
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

- Passed: task-scoped `dev-login.py` and `check-assignment.py` for TASK-137 after fixing assignment write roots to recursive globs.
- Passed: `npm test -- AgentBuilderShell.test.ts` in `frontend/` (9 tests).
- Passed: `npm run build` in `frontend/` (Vite large chunk warning unchanged).
- Passed: `mvn -Dmaven.repo.local=../.m2 -DskipTests compile` in `backend/`.
- Passed: `git diff --check`.
- Blocked: `mvn -Dmaven.repo.local=../.m2 -Dtest=AgentDefinitionDeleteIntegrationTest test` compiled main/test classes, then failed during Spring context startup because local PostgreSQL connection was unavailable (`Unable to obtain connection from database`, SQLState `08001`); no test assertions ran.
- Partial desktop browser check: Vite route opened in the in-app browser; authenticated Agent Builder visual smoke was blocked by missing backend `/auth/me` because local database was unavailable.

## Handoff

- Implemented on branch `codex/TASK-137-custom-agent-delete`.
- Backend adds `DELETE /agents/{agentId}` for custom non-built-in Agent soft delete (`enabled=false`), hides disabled Agents from normal `/agents` list/detail, rejects built-in deletion with 409, and preserves historical rows/evidence.
- Frontend Agent Builder list now exposes delete only for custom Agents, confirms with an accessible modal naming the Agent, removes deleted Agents from local list state, and falls back to the next available Agent or list empty state.
