---
kind: task-status
task_id: TASK-118
assignee: MANAGER-001
owner_role: project-manager
status: done
branch: codex/TASK-124-feat-046-platform-tenant-provisioning
pr_url: n/a
spec_path: docs/specs/FEAT-040-admin-organization-profile.md
assignment_path: .claw/assignments/TASK-118.yaml
updated_at: 2026-05-21T11:27:41Z
updated_by: MANAGER-001
---

# TASK-118 - Admin Organization Profile

## Current State

- Status: `done`
- Next action: none.
- Blocked: none
- Spec: `docs/specs/FEAT-040-admin-organization-profile.md`
- Assignment: `.claw/assignments/TASK-118.yaml`

## Progress

- Implemented FEAT-040 P0 on `codex/TASK-118-admin-organization-profile`, including `organization_profile` persistence, current-organization profile APIs, immutable `orgId` handling, organization-name sync, and `/admin/organization` routing/proxy behavior.
- The page evolved from an editable form to a read-only `组织简档` view with a blocking edit modal, then through several visual simplification passes requested by the user.
- Final UI state removes table-like presentation, keeps concise text-first basic information, and renders usage metrics as standalone cards without reintroducing dense framed layout.
- 2026-05-21T18:21:11+08:00: User reported the FEAT-40 changes were lost. Task reopened on the current branch to restore the read-only profile page and organization information edit modal.
- Restored the lost read-only profile page and blocking edit modal from local Git object `27fbadf`, preserving current TASK-124 platform work outside the organization profile files.
- 2026-05-21T19:15:09+08:00: User reported the later visual simplification changes were lost again on the current branch. Task reopened to restore the final no-table-lines organization profile layout.
- Restored the final visual simplification changes again on the current branch: removed table-like profile fields, restored three-column basic information, removed profile and usage header extra status/scope text, removed recent data export, removed table-like lines, kept one divider above usage summary, and increased page padding.
- 2026-05-21T19:21:51+08:00: User reported the standalone usage metric card styling was still missing on the current branch. Restored the no-large-frame usage layout: the usage section outer panel is transparent and unframed, while all six usage metrics render as separate warm ivory cards.
- 2026-05-21T19:27:41+08:00: User asked why usage summary had no data. Root cause was the current branch backend `OrganizationProfileView` no longer returned `usageSummary`, so the frontend formatted missing values as `0`. Restored the API field and aggregation.

## Changed Files

- `backend/src/main/resources/db/migration/V56__organization_profile.sql`
- `backend/src/main/java/com/codehouse/ciciassistant/organization/**`
- `backend/src/test/java/com/codehouse/ciciassistant/organization/AdminOrganizationProfileIntegrationTest.java`
- `frontend/src/admin/pages/AdminOrganizationPage.tsx`
- `frontend/src/admin/AdminShell.tsx`
- `frontend/vite.config.ts`
- `frontend/vite.config.js`
- `deploy/nginx.cici.conf`
- `deploy/nginx.cici.ssl.conf`
- `docs/specs/FEAT-040-admin-organization-profile.md`

## Verification

- Status: `passed`
- Evidence:
  - task-scoped `dev-login.py` for `MANAGER-001` / `TASK-118` -> `allowed`
  - `mvn -q -Dmaven.repo.local=../.m2 -Dtest=AdminOrganizationProfileIntegrationTest test` -> `passed`
  - `npm run build` -> `passed` with the existing Vite chunk-size warning
  - `git diff --check` -> `passed`
  - Playwright and in-app Browser checks for desktop/mobile read-only profile, edit modal, simplified text layout, and standalone usage metric cards -> `passed`
  - 2026-05-21 restore: task-scoped `dev-login.py` for `MANAGER-001` / `TASK-118` on `codex/TASK-124-feat-046-platform-tenant-provisioning` -> `allowed`
  - 2026-05-21 restore: `npm run build` in `frontend/` -> `passed` with existing Vite chunk-size warning
  - 2026-05-21 restore: in-app Browser mock API `/admin/organization` -> edit modal opens, save closes modal, success feedback appears, organization name syncs, `scrollWidth=clientWidth=1280`
  - 2026-05-21 final visual restore: `npm run build` in `frontend/` -> `passed` with existing Vite chunk-size warning
  - 2026-05-21 final visual restore: `git diff --check` -> `passed`
  - 2026-05-21 final visual restore: Playwright mock API `/admin/organization` at 1058x773 -> profile header `spanCount=0`, usage header `spanCount=0`, recent export absent, no old profile grid, three columns of three fields restored, usage divider restored, page padding `18px 22px 20px`, `.admin-main` border `0px`, `scrollWidth=clientWidth=1058`, console errors `0`; screenshot `output/playwright/feat40-org-profile-restored-current-branch.png`
  - 2026-05-21 usage metric card restore: `npm run build` in `frontend/` -> `passed` with existing Vite chunk-size warning
  - 2026-05-21 usage metric card restore: Playwright mock API `/admin/organization` at 1440x900 -> usage outer panel border `0px none`, transparent background, radius `0px`; six usage cards each have `1px solid` border, warm ivory background, `14px` radius, grid gap `12px`, `overflowX=false`; screenshot `output/playwright/admin-organization-cards-current-branch.png`
  - 2026-05-21 usage summary data restore: `mvn -q -Dmaven.repo.local=../.m2 -DskipTests compile` in `backend/` -> `passed`
  - 2026-05-21 usage summary data restore: `mvn -q -Dmaven.repo.local=../.m2 -Dtest=AdminOrganizationProfileIntegrationTest test` -> `blocked before assertions by existing duplicate Flyway migration version 58` (`V58__platform_account.sql` and `V58__agent_open_api_cloudcc_key_type.sql`)
  - 2026-05-21 usage summary data restore: `git diff --check` -> `passed`

## Handoff

- Detailed visual iterations and screenshots now live in `docs/specs/FEAT-040-admin-organization-profile.md` and `.claw/test-report.md`.
- Future organization self-service changes should continue to follow the product-page rules in `PRODUCT.md`, `DESIGN.md`, and `AGENTS.md`.
