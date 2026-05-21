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
updated_at: 2026-05-21T10:31:00Z
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

## Handoff

- Detailed visual iterations and screenshots now live in `docs/specs/FEAT-040-admin-organization-profile.md` and `.claw/test-report.md`.
- Future organization self-service changes should continue to follow the product-page rules in `PRODUCT.md`, `DESIGN.md`, and `AGENTS.md`.
