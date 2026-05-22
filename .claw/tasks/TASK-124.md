---
kind: task-status
task_id: TASK-124
assignee: MANAGER-001
owner_role: project-manager
status: in_progress
branch: codex/TASK-124-feat-046-platform-tenant-provisioning
pr_url: https://codeup.aliyun.com/627b18115b46541dd2ff340e/cloudcc-aidev-projects/cc-agentcici/change/5
spec_path: docs/specs/FEAT-046-platform-tenant-manual-provisioning-and-lifecycle-entry.md
assignment_path: .claw/assignments/TASK-124.yaml
updated_at: 2026-05-21T15:47:23Z
updated_by: MANAGER-001
---

# TASK-124 FEAT-046 Platform tenant manual provisioning and lifecycle split

## Scope

Implement FEAT-046 end to end:

- split `/platform/tenants` into a list entry page plus `/platform/tenants/:orgId` detail route
- add the platform manual tenant provisioning modal and success redirect flow
- add backend `POST /platform/tenants` support with owner account reuse/new-account creation
- unify new organization ID generation to the `^org[a-z0-9]{17}$` rule across future creation entry points
- add focused backend tests, frontend build verification, and desktop visual QA

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
- Desktop screenshots for `/platform/tenants` and `/platform/tenants/:orgId` are reviewed; do not add further mobile compatibility implementation or mobile tests unless separately requested.
- `.claw` state validation passes after handoff updates.

## Assignment History

- 2026-05-21T08:48:00Z: User requested FEAT-046 completion; `MANAGER-001` opened TASK-124 and assigned full delivery on branch `codex/TASK-124-feat-046-platform-tenant-provisioning`.

## Progress

- Frontend route split is present: `/platform/tenants` now acts as the list entry page and `/platform/tenants/:orgId` carries the lifecycle detail workspace.
- Platform manual tenant provisioning UI is present, including modal structure, owner-account reuse copy, success redirect handling, and desktop list layout.
- Platform theme token regression is fixed: the tenant provisioning modal now resolves its opaque surface color again instead of falling back to a transparent dialog shell.
- Backend FEAT-046 code paths are present in the current worktree: shared organization ID generation, shared provisioning service, `POST /platform/tenants`, and focused auth/platform integration-test coverage.
- Codeup merge request created: https://codeup.aliyun.com/627b18115b46541dd2ff340e/cloudcc-aidev-projects/cc-agentcici/change/5

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
- `npm run build` in `frontend/`: passed after FEAT-046 list refinement.
- `mvn -q -DskipTests compile` in `backend/`: passed for the touched backend modules.
- `2026-05-21T11:15:23Z`: modal transparency regression fix verified on the live local page at `127.0.0.1:5173/platform/tenants`; the "开通新租户" dialog surface is opaque again after restoring the missing `--platform-surface` token in `frontend/src/styles.css`.
- `2026-05-21T12:32:27Z`: focused backend gate passed on the integrated branch with `mvn clean -Dtest=AuthFlowIntegrationTest,PlatformTenantLifecycleIntegrationTest test` after TASK-127 renumbered the inherited platform-account migration to `V59__platform_account.sql` and reset the local `agentcici_test` database.
- Visual QA captured and reviewed against the current worktree frontend on port `4173` using mocked platform auth/API data:
  - desktop `/platform/tenants`
  - desktop `/platform/tenants` with provisioning modal
  - desktop `/platform/tenants/:orgId`
- Previous mobile screenshots were historical evidence from before the no-new-mobile-scope rule. Do not add further mobile compatibility work or mobile tests unless separately requested.

## Open Risk

- No open backend verification blocker remains for the focused FEAT-046 auth/platform integration gate; remaining work is feature completion and any broader regression coverage outside this task's targeted scope.
