---
kind: task-status
task_id: TASK-143
assignee: MANAGER-001
owner_role: fullstack-agent
status: done
branch: codex/TASK-143-billing-edition-config
pr_url: n/a
spec_path: docs/specs/FEAT-037-saas-billing-usage-ledger.md
assignment_path: .claw/assignments/TASK-143.yaml
updated_at: 2026-05-28T09:45:06Z
updated_by: MANAGER-001
---

# TASK-143 Billing Editions Configurable In Platform Operations

## Goal

Implement the platform-operations configuration layer for AgentCiCi billing editions:

- SaaS editions: `saas_team`, `saas_business`, `saas_enterprise`
- private-deployment editions: `private_department`, `private_enterprise`, `private_group`
- capacity packs, module packs, service packs, SLA tiers, and credits policies as separately configurable items

The platform operations console must become the source of truth for edition control indicators, rather than hard-coded limits in frontend copy or backend constants.

## Scope

- Backend billing domain model and migration for configurable plans/editions and related packages.
- Platform APIs for listing, creating, editing, enabling/disabling, and versioning billing editions.
- Configurable indicators: operation seats, builder seats, Agent count, Skill/Workflow count, knowledge capacity, Open API QPS/concurrency/credential count, connector count, meeting-minutes concurrency, trace/audit retention, environment count, included credits, overage mode, `billing_type` policy, SLA/service tier.
- Platform UI under `/platform/billing` or equivalent platform navigation entry for dense table/form management.
- Read APIs suitable for future admin billing overview and runtime quota/rating usage.
- Focused backend tests and frontend build/unit coverage for mode-aware edition configuration.

## Out Of Scope

- Payment provider, invoices, tax, external finance reconciliation, and automatic renewal.
- Final runtime quota enforcement before TASK-114 metering/rating/ledger foundations are stable.
- Tenant self-service edition switching.
- Charging customer-owned local model tokens by default in private deployment mode.

## Dependencies

- TASK-114 billing mode switch and usage ledger foundation remain the upstream engineering base.
- Before implementation, PM must create `.claw/assignments/TASK-143.yaml` with the next valid migration file, not the stale TASK-114 `V53__billing_usage_ledger.sql` scope.
- Implementation must preserve the deployment-level billing mode boundary: `private_deployment` and `saas` use different default edition lines and token/credits policies.

## Acceptance Criteria

- Platform operators can configure edition indicators for both SaaS and private-deployment modes.
- Edition definitions use stable internal codes and Chinese display names from FEAT-003/FEAT-037.
- Capacity, module, service, and SLA packs are modeled as add-ons rather than embedded in version names.
- Private deployment defaults do not double-charge customer-owned local model token usage.
- SaaS editions can configure Work Credits allowances, top-up posture, and platform-paid resource policies.
- All mutable platform billing configuration changes are auditable and require explicit reason text for high-risk changes.

## Verification

- 2026-05-28T08:19:11Z: task-scoped `dev-login.py` for `MANAGER-001` / `TASK-143` on `codex/TASK-143-billing-edition-config` -> allowed.
- 2026-05-28T08:19:11Z: `mvn -q -Dtest='com.codehouse.ciciassistant.billing.**.*Test' test` in `backend/` -> success.
- 2026-05-28T08:19:11Z: `npm test -- billingMode.test.ts` in `frontend/` -> success, 3 tests passed.
- 2026-05-28T09:45:06Z: task-scoped `dev-login.py` and manager state `dev-login.py` for `MANAGER-001` on `codex/TASK-143-billing-edition-config` -> allowed.
- 2026-05-28T09:45:06Z: `check-assignment.py` for actual TASK-143 backend/frontend/spec/task files -> allowed.
- 2026-05-28T09:45:06Z: `mvn -q -Dtest='com.codehouse.ciciassistant.billing.**.*Test' test` in `backend/` -> success.
- 2026-05-28T09:45:06Z: `mvn -q -DskipTests compile` in `backend/` -> success.
- 2026-05-28T09:45:06Z: `npm test -- PlatformBillingPage.test.tsx` in `frontend/` -> success, 2 tests passed.
- 2026-05-28T09:45:06Z: `npm run build` in `frontend/` -> success; existing Vite chunk-size warning remains.
- 2026-05-28T09:45:06Z: local Spring Boot run on `18080` with PostgreSQL over `::1` -> success; Flyway applied `V61__billing_edition_configuration.sql`.
- 2026-05-28T09:45:06Z: in-app browser desktop QA at `/platform/billing` through real platform login and API -> success; page rendered 12 billing config rows, no horizontal overflow (`scrollWidth=clientWidth=1280`), console errors `0`.
- 2026-05-28T09:45:06Z: `git diff --check` -> success.

## Changed Files

- Added `V61__billing_edition_configuration.sql` and `billing_edition_config` as the platform-owned configuration table for SaaS/private plans, capacity packs, module packs, service packs, SLA tiers, and credits policies.
- Added backend billing configuration entity/repository/service and `/platform/billing/plans` APIs for list, draft creation, draft update, publish, and enable/disable with explicit change reason auditing.
- Added `/platform/billing` UI under the platform console with dense table filtering, right-side edit panel, version draft actions, publish, enable/disable, and TASK-143 focused frontend helper tests.
- Preserved private-deployment policy in seeds and UI/API fields: customer-owned local model token usage is `customer_paid` and `localModelTokenDoubleCharge=false` by default.

## Handoff

- Implementation is complete on `codex/TASK-143-billing-edition-config`.
- Ready for PM/product acceptance and merge review.
