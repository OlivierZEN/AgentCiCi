---
kind: task-status
version: 1
task_id: TASK-133
title: Agent Builder no-model new-Agent model-config redirect
status: ready
assignee: DEV-fengchu
owner_role: fullstack-agent
branch: codex/TASK-133-agent-builder-new-agent-model-config-fix
spec_path: docs/specs/PROJECT-BASELINE.md
assignment_path: .claw/assignments/TASK-133.yaml
updated_at: 2026-05-22T10:30:01Z
updated_by: MANAGER-001
---

# TASK-133 - Agent Builder No-Model New-Agent Model-Config Redirect

## Source

- User feedback file: `/Volumes/AISpace/devops/dev_request/agent-builder-new-agent-model-config-fix-plan.md`
- Product symptom: On `/admin/agent-builder`, clicking `+ 新建 Agent` has no visible result when the organization has no available base model because the internal notice is not rendered in list mode.

## Scope

- Add an `onRequireModelConfig` callback from `AdminAgentBuilderPage` into `AgentBuilderShell`.
- In `createAgent()`, when the draft has no model, call the callback if present; otherwise keep a local notice fallback with concise copy `请先配置模型`.
- In the admin route, send the user to `/admin/models` with a one-time notice state.
- In `AdminModelsPage`, read the navigation state notice, show it through the existing notice/toast mechanism, and clear the state after display.
- Preserve the existing creation flow when a usable base model exists.

## Acceptance Criteria

- Entering `/admin/agent-builder` with no available base model and clicking `+ 新建 Agent` routes to `/admin/models`.
- The model page shows `请先配置模型` once using the existing model-page notice/toast pattern.
- No Agent is created in the no-model branch.
- With an available enabled model, `+ 新建 Agent` still enters the original creation flow.
- The task does not introduce backend API changes, new modal styles, or mobile-specific work.

## Preflight

- `DEV-fengchu` must run task-scoped `dev-login.py` for `TASK-133` on branch `codex/TASK-133-agent-builder-new-agent-model-config-fix` before implementation edits.
- Before UI edits, use the `impeccable` workflow and read `PRODUCT.md` plus `DESIGN.md`; read `DESIGN.json` only if detailed design-token or component rules are needed.

## Verification Target

- Focused frontend test for the no-model `createAgent()` path or the closest practical component/helper coverage.
- `npm run build` in `frontend/`.
- Desktop browser verification: no-model click redirects from `/admin/agent-builder` to `/admin/models` and shows `请先配置模型`.
- Desktop browser verification: after enabling/configuring a model, `+ 新建 Agent` still follows the original creation flow.
- `git diff --check`.

## Handoff Notes

- The user feedback proposes the target files: `frontend/src/admin/pages/AdminAgentBuilderPage.tsx`, `frontend/src/assistant/AgentBuilderShell.tsx`, and `frontend/src/admin/pages/AdminModelsPage.tsx`.
- Reuse existing notice state and visual treatment on the model page; do not add a separate toast system for this task.
- Background spec to preserve: `docs/specs/FEAT-035-local-model-providers.md`.

## Assignment History

- 2026-05-22T18:30:01+08:00: User assigned the Agent Builder new-Agent no-model feedback to `DEV-fengchu`; manager opened `TASK-133` with a narrow frontend scope.
