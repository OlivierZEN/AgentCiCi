---
kind: task-status
version: 1
task_id: TASK-129
title: Admin login organization-selection alignment
status: done
assignee: MANAGER-001
owner_role: project-manager
branch: codex/TASK-124-feat-046-platform-tenant-provisioning
spec_path: docs/specs/FEAT-024-account-tenant-lifecycle-and-data-retention.md
assignment_path: .claw/assignments/TASK-129.yaml
updated_at: 2026-05-21T12:24:00Z
updated_by: MANAGER-001
---

# TASK-129 - Admin login organization-selection alignment

## Scope

Align `/admin/login` with the existing account-first multi-organization auth flow:

- remove the visible orgId input from the admin login form
- first submit identifier + password without `orgId`
- when the backend returns `requiresOrganizationSelection`, show organization choices and enter the selected org
- keep the admin shell guarded by `OWNER` / `ORG_ADMIN` only

## Preflight

Before implementation edits, run task-scoped `dev-login.py` for `MANAGER-001` on branch `codex/TASK-124-feat-046-platform-tenant-provisioning`.

## Changed Files

- `frontend/src/admin/AdminLogin.tsx`
- `frontend/src/styles.css`
- `docs/specs/FEAT-024-account-tenant-lifecycle-and-data-retention.md`
- `.claw/test-report.md`

## Progress

- 2026-05-21T11:46:00Z: Opened TASK-129 to bring the admin login surface onto the same account-first organization-selection path already used by the assistant login.
- 2026-05-21T12:24:00Z: Completed the admin login alignment: removed the visible orgId field, added organization-choice handling for multi-org admins, updated the FEAT-024 spec note, and finished desktop/mobile browser QA.

## Verification

- `identity-bootstrap`: `python3 /Users/owenmacbook/.agents/skills/cloudcc-aidev-guidelines-common/scripts/dev-login.py /Volumes/AISpace/codehouse/cc-codeup-agentcici_PM/.claw --ssh-key /Users/owenmacbook/.ssh/id_ed25519_agentcici_pm --developer MANAGER-001 --git-username OwenZheng-Cloud --files .claw/current-status.md .claw/task-board.md .claw/assignments .claw/tasks docs/specs --no-cache --json` -> allowed.
- `identity-task`: task-scoped `dev-login.py` for `MANAGER-001` / `TASK-129` on `codex/TASK-124-feat-046-platform-tenant-provisioning` with intended admin frontend/spec/test-report files -> allowed.
- `frontend`: `npm run build` in `frontend/` -> success; existing Vite chunk-size warning remains.
- `diff`: `git diff --check` -> success.
- `state`: `python3 /Users/owenmacbook/.agents/skills/cloudcc-aidev-guidelines-common/scripts/validate-state.py /Volumes/AISpace/codehouse/cc-codeup-agentcici_PM/.claw` -> success.
- `browser-static`: Playwright desktop + mobile screenshots of `http://127.0.0.1:5173/admin/login` -> success; neither viewport contains a `组织 ID` label.
- `browser-flow`: Playwright mocked multi-org flow -> success; two organization options rendered after account-first login, selecting one stored `cici_admin_token`, and the page navigated to `/admin/kb`.
