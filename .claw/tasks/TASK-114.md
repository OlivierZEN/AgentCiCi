---
kind: task-status
task_id: TASK-114
assignee: MANAGER-001
owner_role: project-manager
status: ready
branch: codex/TASK-114-feat-037-billing-ledger
pr_url: n/a
spec_path: docs/specs/FEAT-037-saas-billing-usage-ledger.md
assignment_path: .claw/assignments/TASK-114.yaml
updated_at: 2026-05-28T09:35:00Z
updated_by: MANAGER-001
---

# TASK-114 FEAT-037 SaaS Billing Usage Ledger

## Scope

Implement FEAT-037 end to end:

- billing schema migration using the next valid mainline migration version, currently expected as `V60__billing_usage_ledger.sql` after assignment refresh
- billing package domain entities, repositories, DTOs, and services
- plan, subscription, usage meter event, rate card, quota, and credit ledger services
- organization admin and platform billing APIs
- `/admin/billing` and `/platform/billing` product UI
- runtime metering hooks in slices: chat/model first, then RAG, tools, workflow, KB indexing, and Open API where they do not collide with active Open API work
- private deployment billing posture: annual license, active seats, module/capacity packs, and services first; Work Credits for governance/platform-paid usage, not local-model token double charging
- deployment billing mode switch: backend `BillingModeProperties` + `/billing/mode`, frontend `billingMode.ts` normalization helper
- edition lines: SaaS `团队版/商业版/企业版`; private deployment `部门版/企业版/集团版`; capacity and service packs are separate add-ons
- deterministic default plan/subscription seed behavior
- focused backend tests, frontend build, and desktop visual QA

## Out Of Scope

- Payment provider, invoice tax, or external finance system integration.
- Editing TASK-112-owned Open API files while TASK-112 is active, unless MANAGER-001 explicitly updates both assignments.
- Full contract pricing, sales discounting, automatic renewal, external finance reconciliation, and final quota enforcement before metering and ledger correctness are proven.
- Charging customer-owned local model token usage as a default private-deployment billing item.

## Preflight

Before editing, run task-scoped `dev-login.py` for `MANAGER-001` on branch `codex/TASK-114-feat-037-billing-ledger`.

Before implementation creates the migration, update `.claw/assignments/TASK-114.yaml` because it still authorizes only `backend/src/main/resources/db/migration/V53__billing_usage_ledger.sql`. Current local and `origin/main` migration head is `V59`, so `V53` would be out of order for Flyway-managed environments.

## Design Reassessment 2026-05-28

- FEAT-037 remains the engineering delivery spec for FEAT-003 and FEAT-022, but first delivery should optimize for billing facts correctness, not complete commercial operations.
- First slice should prove `usage_meter_event`, real model token usage, deterministic rating, and append-only `billing_credit_ledger`.
- `/admin/billing` and `/platform/billing` should be read-oriented audit and explanation surfaces first. Mutation-heavy plan editing, payment, invoice, contract, and renewal workflows stay out of scope.
- Runtime metering should be staged to reduce conflict risk: chat/model first, then RAG/tool/Open API/workflow/KB indexing.
- Tool billing requires a `billing_type` classification before charging tool calls.
- Private deployment pricing should use annual license, active operation/build seats, module/capacity packs, and implementation/support services. Credits remain for usage governance, budget controls, contract quotas, and platform-paid resources.
- Code switching mechanism is deployment-level, not tenant self-service: `private_deployment` remains the default; `saas` can be selected by Spring configuration.
- Plan seed should use stable codes: `saas_team`, `saas_business`, `saas_enterprise`, `private_department`, `private_enterprise`, `private_group`; `trial` remains a trial plan/status.

## Verification Target

- Backend focused billing tests pass.
- Backend compile passes.
- Frontend build passes.
- Desktop screenshots for `/admin/billing` and `/platform/billing` are reviewed; do not add mobile compatibility implementation or mobile tests unless separately requested.
- `.claw` state validation passes after handoff updates.

## Assignment History

- 2026-05-18T23:20:36Z: Initial draft split FEAT-037 into separate backend/UI tasks.
- 2026-05-19T07:20:36+08:00: User assigned FEAT-037 full development to `DEV-nezha`; TASK-114 now owns the whole feature.
- 2026-05-20T09:54:39+08:00: User reassigned TASK-114 from `DEV-nezha` to Owen (`MANAGER-001`) and requested no further task assignment to `DEV-nezha`.
- 2026-05-21T23:47:23+08:00: Verification scope aligned with the project rule that new feature work does not add mobile compatibility implementation, screenshots, or tests by default.
- 2026-05-28T16:40:00+08:00: Reassessed billing design; narrowed first delivery to metering, rating, and ledger correctness before broad UI and quota enforcement.
- 2026-05-28T17:05:00+08:00: Added private-deployment commercial posture: no default local-model token double charging; use license, seats, capacity/modules, and services as primary revenue model.
- 2026-05-28T17:20:00+08:00: Added deployment billing mode switch scope and initial code target: backend billing mode properties/API plus frontend normalization helper.
- 2026-05-28T17:35:00+08:00: Added edition lines for SaaS and private deployment, with capacity/service packs kept as add-ons rather than version names.
