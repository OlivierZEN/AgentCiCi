---
kind: task-status
task_id: TASK-124
assignee: MANAGER-001
owner_role: project-manager
status: in_progress
branch: codex/TASK-124-feat-046-platform-tenant-provisioning
pr_url: n/a
spec_path: docs/specs/FEAT-046-platform-tenant-manual-provisioning-and-lifecycle-entry.md
assignment_path: .claw/assignments/TASK-124.yaml
updated_at: 2026-05-21T08:57:18Z
updated_by: MANAGER-001
---

# TASK-124 FEAT-046 Platform tenant manual provisioning and lifecycle split

## Scope

Implement FEAT-046 end to end:

- split `/platform/tenants` into a list entry page plus `/platform/tenants/:orgId` detail route
- add the platform manual tenant provisioning modal and success redirect flow
- add backend `POST /platform/tenants` support with owner account reuse/new-account creation
- unify new organization ID generation to the `^org[a-z0-9]{17}$` rule across future creation entry points
- add focused backend tests, frontend build verification, and desktop/mobile visual QA

## Out Of Scope

- package, subscription, contract, invoice, or payment integration
- historical tenant-id backfill or migration
- unrelated `/admin/*` organization profile restructuring

## Preflight

Before editing, run task-scoped `dev-login.py` for `MANAGER-001` on branch `codex/TASK-124-feat-046-platform-tenant-provisioning`.

## Verification Target

- Platform tenant lifecycle backend integration tests pass.
- Backend compile passes for touched modules.
- Frontend build passes.
- Desktop and 390px mobile screenshots for `/platform/tenants` and `/platform/tenants/:orgId` are reviewed.
- `.claw` state validation passes after handoff updates.

## Assignment History

- 2026-05-21T08:48:00Z: User requested FEAT-046 completion; `MANAGER-001` opened TASK-124 and assigned full delivery on branch `codex/TASK-124-feat-046-platform-tenant-provisioning`.

## Progress

- Frontend route split is present: `/platform/tenants` now acts as the list entry page and `/platform/tenants/:orgId` carries the lifecycle detail workspace.
- Platform manual tenant provisioning UI is present, including modal structure, owner-account reuse copy, success redirect handling, and mobile-safe list layout.
- Backend FEAT-046 code paths are present in the current worktree: shared organization ID generation, shared provisioning service, `POST /platform/tenants`, and focused auth/platform integration-test coverage.

## Changed Files

- `frontend/src/App.tsx`
- `frontend/src/platform/pages/PlatformTenantsPage.tsx`
- `frontend/src/platform/pages/PlatformTenantDetailPage.tsx`
- `frontend/src/platform/pages/platformTenantsShared.ts`
- `frontend/src/styles.css`
- `backend/src/main/java/com/codehouse/ciciassistant/auth/service/AuthService.java`
- `backend/src/main/java/com/codehouse/ciciassistant/auth/service/OrganizationIdGenerator.java`
- `backend/src/main/java/com/codehouse/ciciassistant/auth/service/OrganizationProvisioningService.java`
- `backend/src/main/java/com/codehouse/ciciassistant/auth/service/PasswordHashService.java`
- `backend/src/main/java/com/codehouse/ciciassistant/platform/api/PlatformTenantLifecycleController.java`
- `backend/src/main/java/com/codehouse/ciciassistant/platform/service/PlatformTenantLifecycleService.java`
- `backend/src/test/java/com/codehouse/ciciassistant/auth/AuthFlowIntegrationTest.java`
- `backend/src/test/java/com/codehouse/ciciassistant/platform/PlatformTenantLifecycleIntegrationTest.java`
- `docs/specs/FEAT-046-platform-tenant-manual-provisioning-and-lifecycle-entry.md`

## Verification Notes

- `2026-05-21T08:48:00Z`: task-scoped `dev-login.py` passed for `MANAGER-001` on `codex/TASK-124-feat-046-platform-tenant-provisioning`.
- `npm run build` in `frontend/`: passed after FEAT-046 responsive list refinement.
- `mvn -q -DskipTests compile` in `backend/`: passed for the touched backend modules.
- Visual QA captured and reviewed against the current worktree frontend on port `4173` using mocked platform auth/API data:
  - desktop `/platform/tenants`
  - desktop `/platform/tenants` with provisioning modal
  - desktop `/platform/tenants/:orgId`
  - mobile 390px `/platform/tenants`
  - mobile 390px `/platform/tenants/:orgId`
- Mobile list regression found and fixed in this pass: the 4-column tenant table was too compressed at 390px, so the list now switches to a stacked row layout on narrow screens while desktop keeps the native table.

## Open Risk

- Targeted backend integration tests were not completed in this session. `mvn -q -Dtest=AuthFlowIntegrationTest,PlatformTenantLifecycleIntegrationTest test` did not produce a fast pass/fail result in the local environment before datasource bootstrap stalled, so real backend test evidence still needs a fresh rerun on a source-aligned local runtime.
