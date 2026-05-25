---
kind: task-status
version: 1
task_id: TASK-120
title: Platform accountless login
status: done
assignee: MANAGER-001
owner_role: project-manager
branch: codex/TASK-120-platform-accountless-login
spec_path: docs/specs/FEAT-041-platform-accountless-login.md
assignment_path: .claw/assignments/TASK-120.yaml
updated_at: 2026-05-21T02:10:00Z
updated_by: ai
---

# TASK-120 - Platform Accountless Login

## Scope

Implement FEAT-041 so `/platform/login` authenticates against an independent platform account identity domain, without requiring `orgId` or depending on `user_account` / `organization_member`.

## Plan

1. Add platform account schema, entities, repositories, bootstrap seed, and shared password verification.
2. Add platform password login and platform me APIs with `typ=platform` token claims.
3. Adjust platform authorization/context handling so platform tokens are accepted only by platform surfaces and rejected by organization APIs.
4. Update `/platform/login` and `PlatformGuard` to use platform-only auth.
5. Add focused backend tests and frontend build/browser verification.

## Coordination

- Before implementation edits, run task-scoped `dev-login.py` for `MANAGER-001` on branch `codex/TASK-120-platform-accountless-login`.
- Do not change assistant `/` or admin `/admin/login` organization login flows.
- Existing dirty worktree includes state/UI changes from earlier tasks; preserve them and avoid unrelated rewrites.

## Progress

- 2026-05-20T13:03:48Z: Created TASK-120 assignment and status slice for FEAT-041.
- 2026-05-20T13:08:00Z: Expanded assignment to include `tenant` context files because FEAT-041 requires platform token isolation in `TenantContextFilter`.
- 2026-05-20T13:36:12Z: Implemented FEAT-041 end to end: platform account schema/entity/repository/bootstrap, shared password verifier, platform password login, `typ=platform` JWT, `/auth/platform/me`, platform token isolation in `TenantContextFilter`, platform audit actor update, `/platform/login` account-only frontend, and `PlatformGuard` validation through `/auth/platform/me`.
- 2026-05-21T02:10:00Z: Trimmed redundant copy from `/platform/login`: removed intro lede, decorative chips, right-side guidance card, and the default idle notice so the login surface stays focused on the account/password action.

## Verification

- `identity`: `python3 /Users/owenmacbook/.agents/skills/cloudcc-aidev-guidelines-common/scripts/dev-login.py /Volumes/AISpace/codehouse/cc-codeup-agentcici_PM/.claw --ssh-key /Users/owenmacbook/.ssh/id_ed25519_agentcici_pm --developer MANAGER-001 --git-username OwenZheng-Cloud --files .claw/assignments/TASK-120.yaml .claw/tasks/TASK-120.md .claw/task-board.md .claw/current-status.md .claw/team-status.md docs/specs/FEAT-041-platform-accountless-login.md --no-cache --json` -> allowed.
- `identity-scope-expansion`: manager `dev-login.py` for `.claw/assignments/TASK-120.yaml` / `.claw/tasks/TASK-120.md` -> allowed.
- `identity-implementation`: task-scoped `dev-login.py` for `MANAGER-001` / `TASK-120` on `codex/TASK-120-platform-accountless-login` with intended backend/frontend/spec/task files -> allowed.
- `backend-compile`: `mvn -q -Dmaven.repo.local=../.m2 -DskipTests compile` in `backend/` -> success.
- `backend-platform-auth`: `TEST_DATABASE_URL=jdbc:postgresql://localhost:5432/cici_assistant_feat041 mvn -q -Dmaven.repo.local=../.m2 -Dtest=PlatformAuthIntegrationTest test` in `backend/` -> success.
- `backend-regression`: `TEST_DATABASE_URL=jdbc:postgresql://localhost:5432/cici_assistant_feat041_suite mvn -q -Dmaven.repo.local=../.m2 -Dtest=PlatformAuthIntegrationTest,AuthFlowIntegrationTest,PlatformGovernanceIntegrationTest test` in `backend/` -> success.
- `frontend`: `npm run build` in `frontend/` -> success; existing Vite chunk-size warning remains.
- `diff`: `git diff --check` -> success.
- `browser-desktop`: Playwright `/platform/login` at 1440x1000 -> screenshot `output/playwright/feat041-platform-login-desktop.png`; inputs are account and password only, default account is `admin@cloudcc.com`, `scrollWidth=clientWidth=1440`.
- `browser-mobile`: Playwright `/platform/login` at 390x844 -> screenshot `output/playwright/feat041-platform-login-mobile.png`; `inputCount=2`, no `组织 ID` / `orgId` label, `scrollWidth=clientWidth=390`.
- `browser-login-flow`: Playwright with mocked `/auth/platform/password/login`, `/auth/platform/me`, and `/platform/bootstrap` -> login stores `cici_platform_token` with `tokenType=platform` and navigates to `/platform`; screenshot `output/playwright/feat041-platform-login-success-desktop.png`; console error count after the flow is 0.
- `frontend-build-copy-trim`: `npm run build` in `frontend/` -> success; existing Vite chunk-size warning remains.
- `browser-copy-trim-desktop`: Playwright `/platform/login` at 1440x1000 -> screenshot `output/playwright/platform-login-clean-desktop.png`; page shows only title plus login form, with removed descriptive chips/aside copy.
- `browser-copy-trim-mobile`: Playwright `/platform/login` at 390x844 -> screenshot `output/playwright/platform-login-clean-mobile.png`; single-column form remains intact after removing the extra description blocks.
- `note`: The default local `cici_assistant_test` database currently has a Flyway history entry for migration 57 that is not present in the working tree, so FEAT-041 backend tests were run against fresh task-specific PostgreSQL databases instead of repairing shared local history.
