---
kind: task-status
task_id: TASK-118
assignee: MANAGER-001
status: done
branch: codex/TASK-118-admin-organization-profile
pr_url: n/a
spec_path: docs/specs/FEAT-040-admin-organization-profile.md
assignment_path: .claw/assignments/TASK-118.yaml
updated_at: 2026-05-19T15:48:27Z
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
- 2026-05-19T17:00:00+08:00: User requested the organization profile details area be simplified from a table-like layout into concise text display.
- 2026-05-19T23:28:29+08:00: User requested the crossed-out read-only profile fields be removed, remaining basic information be shown in one row of three columns, the whole desktop page avoid scrollbars, and the basic information section occupy about 25% of page height.
- 2026-05-19T23:35:30+08:00: User requested all table-like background lines on the page be removed, with concise text display and no added lines.
- 2026-05-19T23:43:29+08:00: User requested the organization profile header status text selected in the browser diff comment be removed.
- 2026-05-19T23:48:27+08:00: User requested follow-up browser diff comment changes: remove the usage header `当前组织`, remove the recent export block, add a divider above usage summary, and increase page padding.
- 2026-05-20T10:14:29+08:00: User requested the organization profile page usage summary be displayed as an independent card.
- 2026-05-20T10:19:06+08:00: User clarified the usage summary should not look like one large table; each metric should be an independent card without a large enclosing frame behind it.

## Progress

- Assignment and task status initialized.
- Implemented `organization_profile` persistence, current organization profile API, immutable `orgId` validation, organization name update audit, Owner/member/export summary, admin page, navigation display-name sync, and proxy rules that keep `/admin/organization` as an SPA route while API children go to backend.
- Reopened after user continuation request and changed `/admin/organization` from editable 「组织设置」 to read-only 「组织简档」 with an organization information block above a usage summary dashboard.
- Reopened again for user continuation request to add an 「编辑」 action that opens a blocking organization information editor from the read-only profile page.
- Completed the edit modal continuation and verified save, page refresh, left-nav organization-name sync, desktop modal layout, and mobile modal layout.
- Reopened for the organization profile details visual simplification.
- Completed the text-display continuation and verified desktop/mobile organization profile screenshots without horizontal overflow.
- Reopened for the compact three-column basic information continuation.
- Completed the compact three-column basic information continuation and verified the desktop page has no document/body/admin-main scrollbars.
- Reopened for the no-lines concise text presentation continuation.
- Completed the no-lines concise text presentation continuation and verified organization page content borders are all 0px.
- Reopened for the browser diff comment that removes the profile header status text.
- Completed the browser diff comment follow-up adjustments and verified the selected elements were removed.
- Reopened for the independent usage summary card adjustment.
- Completed the usage summary card adjustment with a warm ivory outer card and simple internal 1px dividers, preserving compact product-page density.
- Reopened for the no-large-frame metric card clarification.
- Completed the clarification by removing the usage summary outer frame and rendering the six usage metrics as separate warm ivory cards.

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
- Replaced the table-like read-only organization profile grid with a concise text display that removes per-cell borders and the vertical divider while preserving compact two-column desktop and one-column mobile layouts.
- Removed the crossed-out read-only fields from the profile display: organization short name, body-level status, Owner, Owner phone, profile created time, latest update, updater, and notes.
- Reworked the remaining nine basic information fields into one desktop row with three columns and three items per column; the profile panel is constrained to 25% of the page content height on desktop.
- Removed organization page content panel borders, section dividers, usage summary grid lines, export list lines, and the route-specific right content outer border so the page reads as concise text without added lines.
- Removed the organization profile header status text and the usage header `当前组织` text.
- Removed the recent data export block from the organization profile page.
- Added one divider line above the usage summary section and increased organization page padding.
- Changed the usage summary section from a transparent/divider-only region into an independent card with gold-mist border, warm ivory surface, 14px radius, and text-first metric rows separated by 1px rules.
- Removed the usage summary outer card/frame and converted each metric item into its own standalone card, with grid gaps replacing table-like divider lines.

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
- `identity-text-display`: task-scoped `dev-login.py` for `MANAGER-001` / `TASK-118` -> `allowed`.
- `frontend-text-display`: `npm run build` in `frontend/` -> success, with existing Vite chunk-size warning.
- `diff-text-display`: `git diff --check` -> success.
- `browser-text-display-desktop`: Playwright CLI mocked `/auth/me` and `/admin/organization/profile`, `/admin/organization` 1440x1000 -> screenshot `output/playwright/feat40-org-profile-text-desktop.png`, `scrollWidth=clientWidth=1440`, no old `.admin-organization-profile-grid`.
- `browser-text-display-mobile`: Playwright CLI mocked APIs, `/admin/organization` 390x844 -> screenshot `output/playwright/feat40-org-profile-text-mobile.png`, `scrollWidth=clientWidth=390`, one-column profile details.
- `browser-text-display-console`: Playwright console error logs -> 0 errors.
- `frontend-compact-three-column`: `npm run build` in `frontend/` -> success, with existing Vite chunk-size warning.
- `diff-compact-three-column`: `git diff --check` -> success.
- `browser-compact-three-column-desktop`: Playwright CLI mocked `/auth/me` and `/admin/organization/profile`, `/admin/organization` 1440x1000 -> screenshot `output/playwright/feat40-org-profile-compact-three-column-desktop.png`; `profileRatio=0.25`, `columnCount=3`, `columnFieldCounts=[3,3,3]`, crossed-out fields absent, `documentScroll=false`, `bodyScroll=false`, `mainScroll=false`, `scrollWidth=clientWidth=1440`.
- `browser-compact-three-column-mobile`: Playwright CLI mocked APIs, `/admin/organization` 390x844 -> screenshot `output/playwright/feat40-org-profile-compact-three-column-mobile.png`, `scrollWidth=clientWidth=390`.
- `frontend-no-lines`: `npm run build` in `frontend/` -> success, with existing Vite chunk-size warning.
- `diff-no-lines`: `git diff --check` -> success.
- `browser-no-lines-desktop`: Playwright CLI mocked `/auth/me` and `/admin/organization/profile`, `/admin/organization` 1440x1000 -> screenshot `output/playwright/feat40-org-profile-no-lines-desktop-final.png`; content panel, section, profile list, profile columns/items, usage grid/items, export and route-specific `.admin-main` borders all measured as `0px`; panel backgrounds transparent; `mainScroll=false`; `scrollWidth=clientWidth=1440`.
- `browser-no-lines-mobile`: Playwright CLI mocked APIs, `/admin/organization` 390x844 -> screenshot `output/playwright/feat40-org-profile-no-lines-mobile.png`, `scrollWidth=clientWidth=390`.
- `browser-no-lines-console`: Playwright console error logs -> 0 errors.
- `frontend-diff-comments`: `npm run build` in `frontend/` -> success, with existing Vite chunk-size warning.
- `diff-comments`: `git diff --check` -> success.
- `browser-diff-comments`: Playwright CLI mocked `/auth/me` and `/admin/organization/profile`, `/admin/organization` 1058x773 -> screenshot `output/playwright/feat40-org-profile-comments-1-4.png`; profile header `spanCount=0`; usage header text is only `使用情况汇总`; usage header `spanCount=0`; recent export block absent; usage section top border is `1px`; page padding is `18px 22px 20px`; `mainScroll=false`; `scrollWidth=clientWidth=1058`; console error logs -> 0 errors.
- `frontend-usage-card`: `npm run build` in `frontend/` -> success, with existing Vite chunk-size warning.
- `browser-usage-card-desktop`: Playwright mocked `/auth/me` and `/admin/organization/profile`, `/admin/organization` 1440x900 -> screenshot `output/playwright/admin-organization-desktop.png`; usage card border `1px solid`, background `rgb(255, 253, 248)`, radius `14px`, `overflowX=false`.
- `browser-usage-card-mobile`: Playwright mocked APIs, `/admin/organization` 390x844 -> screenshot `output/playwright/admin-organization-mobile.png`; usage card border `1px solid`, background `rgb(255, 253, 248)`, radius `14px`, `overflowX=false`.
- `frontend-usage-metric-cards`: `npm run build` in `frontend/` -> success, with existing Vite chunk-size warning.
- `browser-usage-metric-cards-desktop`: Playwright mocked `/auth/me` and `/admin/organization/profile`, `/admin/organization` 1440x900 -> screenshot `output/playwright/admin-organization-cards-desktop.png`; usage outer panel border `0px none`, background transparent, radius `0px`; six metric cards each have `1px solid` border, warm ivory background, `14px` radius, and `overflowX=false`.
- `browser-usage-metric-cards-mobile`: Playwright mocked APIs, `/admin/organization` 390x844 -> screenshot `output/playwright/admin-organization-cards-mobile.png`; same outer-panel/card measurements and `overflowX=false`.
