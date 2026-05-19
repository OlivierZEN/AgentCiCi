---
kind: task-status
task_id: TASK-118
assignee: MANAGER-001
status: done
branch: codex/TASK-118-admin-organization-profile
pr_url: n/a
spec_path: docs/specs/FEAT-040-admin-organization-profile.md
assignment_path: .claw/assignments/TASK-118.yaml
updated_at: 2026-05-19T08:38:37Z
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
- 2026-05-19T16:26:32+08:00: User requested an edit button on the organization profile page that opens an organization information edit page/dialog. Assignment reopened for this continuation.

## Progress

- Assignment and task status initialized.
- Implemented `organization_profile` persistence, current organization profile API, immutable `orgId` validation, organization name update audit, Owner/member/export summary, admin page, navigation display-name sync, and proxy rules that keep `/admin/organization` as an SPA route while API children go to backend.
- Reopened after user continuation request and changed `/admin/organization` from editable 「组织设置」 to read-only 「组织简档」 with an organization information block above a usage summary dashboard.
- Reopened again for user continuation request to add an 「编辑」 action that opens a blocking organization information editor from the read-only profile page.
- Completed the edit modal continuation and verified save, page refresh, left-nav organization-name sync, desktop modal layout, and mobile modal layout.

## Completed Work

- Added `backend/src/main/resources/db/migration/V56__organization_profile.sql`.
- Added `backend/src/main/java/com/codehouse/ciciassistant/organization/*` profile entity, repository, service, and controller.
- Updated `OrgEntity` and `UserRepository` for editable organization name and summary queries.
- Added `AdminOrganizationProfileIntegrationTest` for Owner update, `orgId` immutability, and `ORG_USER` authorization denial.
- Added `frontend/src/admin/pages/AdminOrganizationPage.tsx`, route registration, navigation entry, and `AdminShell` organization display-name sync.
- Updated `frontend/vite.config.ts`, `frontend/vite.config.js`, `deploy/nginx.cici.conf`, and `deploy/nginx.cici.ssl.conf` so direct `/admin/organization` loads the SPA and only profile/export job child paths proxy to backend.
- Extended `GET /admin/organization/profile` with `usageSummary` counts for active/created users, knowledge bases, knowledge documents, enabled skills, enabled agents, published agents, and export jobs.
- Renamed the admin navigation item to 「组织简档」 and removed the visible edit/save form from the `/admin/organization` page; the page now presents read-only organization information and a compact usage summary board.
- Added an 「编辑」 primary action to the organization profile header.
- Added a blocking organization information edit modal with `role="dialog"`, `aria-modal="true"`, a labelled heading, bare `×` close control, read-only organization ID, compact profile fields, and unified footer actions.
- Wired modal save to `PATCH /admin/organization/profile`, then updates the read-only page and dispatches the organization profile update event so the admin shell organization name refreshes immediately.

## Verification Evidence

- `identity`: task-scoped `dev-login.py` for `MANAGER-001` / `TASK-118` -> `allowed`.
- `backend`: `mvn -q -Dmaven.repo.local=../.m2 -Dtest=AdminOrganizationProfileIntegrationTest test` in `backend/` -> success, 2 tests / 0 failures / 0 errors.
- `frontend`: `npm run build` in `frontend/` -> success, with existing Vite chunk-size warning.
- `diff`: `git diff --check` -> success.
- `browser`: Playwright direct `/admin/organization` -> SPA page title `组织设置`; `/admin/organization/profile` remains proxied to backend.
- `desktop`: screenshot `output/playwright/feat40-admin-organization-desktop-final.png`.
- `mobile`: screenshot `output/playwright/feat40-admin-organization-mobile-final.png`, `documentElement.scrollWidth=clientWidth=390`.
- `browser-readonly-profile`: Playwright mock API `/admin/organization` -> title and nav `组织简档`, usage board present, no visible `保存` operation.
- `desktop-readonly-profile`: screenshot `output/playwright/feat40-org-profile-desktop.png`, `scrollWidth=clientWidth=1440`.
- `mobile-readonly-profile`: screenshot `output/playwright/feat40-org-profile-mobile.png`, `scrollWidth=clientWidth=390`.
- `browser-edit-modal`: in-app Browser with mock `/auth` and `/admin/organization/profile` APIs -> edit button opens the organization information modal; saving a new organization name closes the modal, shows success feedback, updates the read-only profile, and refreshes the left navigation organization name.
- `desktop-edit-modal`: screenshot `output/playwright/feat40-org-profile-edit-modal-desktop.png`, `scrollWidth=clientWidth=1440`, one `role="dialog"` with `aria-modal="true"`.
- `mobile-edit-modal`: screenshot `output/playwright/feat40-org-profile-edit-modal-mobile.png`, `scrollWidth=clientWidth=375`, one `role="dialog"` with `aria-modal="true"`.
- `browser-console`: in-app Browser console error logs -> none.
