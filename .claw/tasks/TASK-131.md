---
kind: task-status
version: 1
task_id: TASK-131
title: Platform Account Orgless Auth Context
status: review
assignee: MANAGER-001
owner_role: project-manager
branch: codex/local-uncommitted-feature-mr
spec_path: docs/specs/FEAT-053-platform-account-orgless-auth-context.md
assignment_path: .claw/assignments/TASK-131.yaml
updated_at: 2026-05-22T05:10:00Z
updated_by: MANAGER-001
---

# TASK-131 - Platform Account Orgless Auth Context

## Scope

- Treat `/platform/*` operators as dedicated platform accounts, not organization members.
- Add or finish platform-account password login and current-account APIs.
- Remove visible organization information from the platform shell and platform overview.
- Keep organization admin and tenant lifecycle behavior unchanged.

## Preflight

- Manager bootstrap `dev-login.py` returned `allowed` for `MANAGER-001` on branch `main`.
- Run task-scoped `dev-login.py` for `TASK-131` with intended code/state/spec paths before implementation edits.

## Changed Files

- backend platform auth context: `AuthController`, `PlatformAuthController`, `PlatformAuthService`, `JwtService`, `AuthBootstrapData`, `PlatformAccountProperties`, `PlatformRoleAuthorizationAspect`, `TenantContext`, `TenantContextFilter`.
- backend platform governance surface: `PlatformController`, `PlatformGovernanceService`.
- backend regression coverage: `PlatformAuthIntegrationTest`, `AuthFlowIntegrationTest`, `PlatformGovernanceIntegrationTest`, `PlatformTenantLifecycleIntegrationTest`.
- frontend platform surface: `PlatformLogin`, `PlatformGuard`, `PlatformShell`, `PlatformHomePage`.
- state/spec evidence: `TASK-131`, `task-board`, `current-status`, `test-report`, `FEAT-053`.

## Progress

- 2026-05-22T04:22:41Z: Opened TASK-131 and FEAT-053 for the platform-account orgless-auth correction requested from the platform console screenshot.
- 2026-05-22T05:10:00Z: Completed the orgless platform auth implementation pass: platform login now uses `/auth/platform/password/login`, platform guard validates `/auth/platform/me`, platform JWT/context uses `typ=platform` and `platform_account_id`, platform APIs use an internal governance scope instead of token `org_id`, org tokens are rejected from `/platform/*`, and platform console no longer displays `demo-org` or current-organization metadata.
- 2026-05-22T05:10:00Z: Fixed platform overview table layout so the orgless account/status values stay inside the desktop panel.
- 2026-05-22T10:45:33Z: Moved the previously local uncommitted feature work onto `codex/local-uncommitted-feature-mr` for Codeup review.

## Verification

- `dev-login.py` for `MANAGER-001` / `TASK-131` on branch `main` with intended backend/frontend/spec/state files -> **allowed**.
- `mvn -q -Dmaven.repo.local=../.m2 -DskipTests compile` in `backend/` -> **success**.
- `mvn -q -Dmaven.repo.local=../.m2 -DskipTests test-compile` in `backend/` -> **success**.
- `npm run build` in `frontend/` -> **success**; existing Vite chunk-size warning remains.
- Playwright desktop check at `1365x900` with mocked platform token and platform bootstrap -> **success**; no console errors, no page text containing `当前组织` or `demo-org`, no horizontal page overflow, screenshot `output/playwright/feat053-platform-orgless-desktop.png`.
- `git diff --check` -> **success**.
- `.claw` state validation -> **success**.
- Focused backend integration command `mvn -q -Dmaven.repo.local=../.m2 -Dtest=PlatformAuthIntegrationTest test` -> **blocked by local environment**; Spring context could not connect to `localhost:5432` because Docker/Postgres is not running (`docker ps` also cannot connect to the Docker API socket). Rerun when the local database is available.
