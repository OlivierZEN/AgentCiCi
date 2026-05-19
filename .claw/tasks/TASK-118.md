---
kind: task-status
task_id: TASK-118
assignee: MANAGER-001
status: done
branch: codex/TASK-118-admin-organization-profile
pr_url: n/a
spec_path: docs/specs/FEAT-040-admin-organization-profile.md
assignment_path: .claw/assignments/TASK-118.yaml
updated_at: 2026-05-19T06:18:30Z
updated_by: MANAGER-001
---

# TASK-118 Admin Organization Profile

## Scope

Own FEAT-040 P0 implementation:

- `organization_profile` persistence for supplemental organization profile fields.
- `GET/PATCH /admin/organization/profile` for current organization profile.
- Immutable `orgId` semantics and editable `org.name` display name.
- Owner, member count, status, and recent export summary for `/admin/organization`.
- Admin navigation entry and shell organization display name refresh.
- Product-register visual QA for desktop and 390px mobile.

## Out Of Scope

- Platform tenant lifecycle controls, purge, suspend, resume, or cross-tenant governance.
- Full billing center, subscription management, SSO, SCIM, logo upload, or legal entity fields.
- Editing active TASK-112, TASK-114, TASK-115, TASK-116, or TASK-117 implementation files beyond already-shared route/style files authorized by this assignment.

## Preflight

Before implementation edits, run task-scoped `dev-login.py` for `MANAGER-001` on branch `codex/TASK-118-admin-organization-profile`.

## Verification Target

- Backend integration test: `AdminOrganizationProfileIntegrationTest`.
- Frontend build: `npm run build` in `frontend/`.
- Browser screenshots for `/admin/organization` desktop and 390px mobile.
- No horizontal overflow on mobile.
- `.claw` state validation passes after task closeout updates.

## Assignment History

- 2026-05-19T14:22:00+08:00: User requested FEAT-40 implementation; task assigned to `MANAGER-001` for this local session.

## Progress

- Assignment and task status initialized.
- Implemented `organization_profile` persistence, current organization profile API, immutable `orgId` validation, organization name update audit, Owner/member/export summary, admin page, navigation display-name sync, and proxy rules that keep `/admin/organization` as an SPA route while API children go to backend.

## Completed Work

- Added `backend/src/main/resources/db/migration/V56__organization_profile.sql`.
- Added `backend/src/main/java/com/codehouse/ciciassistant/organization/*` profile entity, repository, service, and controller.
- Updated `OrgEntity` and `UserRepository` for editable organization name and summary queries.
- Added `AdminOrganizationProfileIntegrationTest` for Owner update, `orgId` immutability, and `ORG_USER` authorization denial.
- Added `frontend/src/admin/pages/AdminOrganizationPage.tsx`, route registration, navigation entry, and `AdminShell` organization display-name sync.
- Updated `frontend/vite.config.ts`, `frontend/vite.config.js`, `deploy/nginx.cici.conf`, and `deploy/nginx.cici.ssl.conf` so direct `/admin/organization` loads the SPA and only profile/export job child paths proxy to backend.

## Verification Evidence

- `identity`: task-scoped `dev-login.py` for `MANAGER-001` / `TASK-118` -> `allowed`.
- `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=AdminOrganizationProfileIntegrationTest test` -> success.
- `frontend`: `npm run build` in `frontend/` -> success, with existing Vite chunk-size warning.
- `diff`: `git diff --check` -> success.
- `browser`: Playwright direct `/admin/organization` -> SPA page title `组织设置`; `/admin/organization/profile` remains proxied to backend.
- `desktop`: screenshot `output/playwright/feat40-admin-organization-desktop-final.png`.
- `mobile`: screenshot `output/playwright/feat40-admin-organization-mobile-final.png`, `documentElement.scrollWidth=clientWidth=390`.
