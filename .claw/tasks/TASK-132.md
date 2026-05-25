---
kind: task-status
version: 1
task_id: TASK-132
title: Agent Builder focused-agent skill binding refresh bugfix
status: review
assignee: DEV-fengchu
owner_role: fullstack-agent
branch: codex/TASK-132-agent-builder-skill-refresh-bugfix
spec_path: docs/specs/PROJECT-BASELINE.md
assignment_path: .claw/assignments/TASK-132.yaml
updated_at: 2026-05-25T09:22:00Z
updated_by: DEV-fengchu
---

# TASK-132 - Agent Builder Focused-Agent Skill Binding Refresh Bugfix

## Source

- User feedback file: `/Volumes/AISpace/devops/dev_request/agent-builder-skill-refresh-bugfix.md`
- Production symptom: `/admin/agent-builder/agent-611846` can show `Skill 范围 0/0 启用` after refresh even though compile/publish succeeded and the published workflow version has Skill snapshots.

## Scope

- Fix `frontend/src/assistant/AgentBuilderShell.tsx` initialization so a URL-focused Agent is the detail-fetch target when it exists in `GET /agents`.
- Keep the first-Agent fallback when the focused id is absent from the list.
- Ensure the detail response is used to populate the matching `AgentRecord.draft.skillBindings`.
- Keep edit-page draft bindings sourced from detail `skillBindings` / `agent_skill_binding`, not from published `agent_workflow_skill_ref`.
- Add focused frontend regression coverage for the non-first focused Agent case.

## Acceptance Criteria

- Refreshing a non-first Agent edit URL requests `GET /agents/{focusAgentId}`.
- The selected Agent after load is the focused Agent when present.
- The draft Skill list uses the focused Agent detail response and no longer collapses to `0/0` because the list payload omitted `skillBindings`.
- Refreshing the first Agent and a missing/invalid focused id still work through the existing fallback.
- Existing compile and publish flow behavior is preserved.

## Preflight

- `DEV-fengchu` must run task-scoped `dev-login.py` for `TASK-132` on branch `codex/TASK-132-agent-builder-skill-refresh-bugfix` before implementation edits.

## Verification Target

- `npm run test -- AgentBuilderShell` or the closest focused Vitest coverage added for the initialization helper/path.
- `npm run build` in `frontend/`.
- Desktop browser verification for `/admin/agent-builder/:agentId` with a non-first Agent and visible Skill bindings after refresh.
- Network check confirms refresh requests `GET /agents/{focusAgentId}` rather than only the first Agent detail.
- `git diff --check`.

## Handoff Notes

- Do not treat published Skill snapshots in `agent_workflow_skill_ref` as the edit-page source of truth.
- If the current component shape makes direct component testing too heavy, extract a small pure helper for resolving `detailAgentId` / merging the detail record and cover that helper with Vitest.
- No backend schema or API change is expected for this task.
- Background specs to preserve: `docs/specs/FEAT-009-skill-layering-and-governance.md` and `docs/specs/FEAT-014-skill-versioning-import-export.md`.

## Assignment History

- 2026-05-22T18:26:20+08:00: User assigned the Agent Builder skill refresh bugfix to `DEV-fengchu`; manager opened `TASK-132` with a narrow frontend scope.

## Progress

- `DEV-fengchu` completed the task-scoped identity gate on branch `codex/TASK-132-agent-builder-skill-refresh-bugfix`.
- Agent Builder initialization now resolves the detail-fetch target from the URL-focused Agent when that Agent exists in `GET /agents`; otherwise it keeps the previous first-Agent fallback.
- The loaded detail record now replaces only the matching list record before building the local library, so focused Agent draft `skillBindings` survive a refresh even when the list payload omits them.

## Changed Files

- `frontend/src/assistant/AgentBuilderShell.tsx`
- `frontend/src/assistant/AgentBuilderShell.test.ts`
- `.claw/tasks/TASK-132.md`

## Verification Notes

- `npm run test -- AgentBuilderShell` in `frontend/`: passed, 3 tests.
- `npm run build` in `frontend/`: passed; Vite reported the existing large chunk warning.
- `git diff --check`: passed.
- Local API precheck: `GET /agents` returned list items with `0` `skillBindings`, while `GET /agents/cici-system` returned 3 detail `skillBindings`.
- Desktop Google Chrome verification on `http://127.0.0.1:5173/admin/agent-builder/cici-system`: after refresh, the page selected `思思（CiCi）` and showed `Skill 范围 3/3 启用` with `通用助手`, `AI 听记`, and `Web 搜索`.
