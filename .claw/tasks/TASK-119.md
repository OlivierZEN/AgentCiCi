---
kind: task-status
task_id: TASK-119
assignee: MANAGER-001
owner_role: project-manager
status: ready
branch: codex/TASK-119-agent-access-control
pr_url: n/a
spec_path: docs/specs/FEAT-042-agent-access-control.md
assignment_path: .claw/assignments/TASK-119.yaml
updated_at: 2026-05-20T12:47:44Z
updated_by: MANAGER-001
---

# TASK-119 Agent Access Control And User Authorization

## Scope

Own FEAT-042 implementation for first-phase Agent access control:

- `agent_access_grant` and `agent_permission_audit` persistence.
- `AgentAccessControlService` for `VIEW`, `RUN`, `DEBUG`, `EDIT`, `PUBLISH`, `MANAGE`, `OPENAPI`, and `LOG_VIEW`.
- First-phase principals: `ORG`, `USER`, `SYSTEM_ROLE`, and owner implicit permissions.
- Backend gates for Agent list/detail/run/debug/publish/Open API/log entry points.
- Open API Key run-as validation requiring target Agent `RUN`.
- Admin/Agent Builder permission management UI that follows product-register design rules.

## Out Of Scope

- Full custom role management.
- Department management.
- User group management UI or membership maintenance.
- Cross-organization authorization.
- Rebuilding business row-level data permissions for CRM, knowledge documents, or third-party systems.
- Expanding Tool permissions through Agent `RUN` grants.

## Preflight

Before implementation edits, run task-scoped `dev-login.py` for `MANAGER-001` on branch `codex/TASK-119-agent-access-control`.

## Verification Target

- Backend focused tests for `AgentAccessControlService`.
- Integration tests for ordinary member denial, explicit user grant allow, admin/owner implicit permissions, and grant revocation.
- Open API run-as denial test when the run-as user lacks target Agent `RUN`.
- Frontend build: `npm run build` in `frontend/`.
- Browser screenshots for the permission management UI on desktop and 390px mobile.
- `git diff --check`.
- `.claw` state validation.

## Assignment History

- 2026-05-20T20:47:44+08:00: User requested assigning the Agent access control task to Owen; task assigned to `MANAGER-001`.

## Progress

- Assignment and task status initialized.
- FEAT-042 design document already exists and is linked as the source spec.

## Completed Work

- None yet.

## Verification Evidence

- `identity`: `python3 /Users/owenmacbook/.agents/skills/cloudcc-aidev-guidelines-common/scripts/dev-login.py /Volumes/AISpace/codehouse/cc-codeup-agentcici_PM/.claw --ssh-key /Users/owenmacbook/.ssh/id_ed25519_agentcici_pm --developer MANAGER-001 --git-username OwenZheng-Cloud --files .claw/assignments/TASK-119.yaml .claw/tasks/TASK-119.md .claw/task-board.md .claw/current-status.md .claw/team-status.md --no-cache --json` -> allowed.
