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
updated_at: 2026-05-21T15:47:23Z
updated_by: MANAGER-001
---

# TASK-114 FEAT-037 SaaS Billing Usage Ledger

## Scope

Implement FEAT-037 end to end:

- billing schema migration `V53__billing_usage_ledger.sql`
- billing package domain entities, repositories, DTOs, and services
- plan, subscription, usage meter event, rate card, quota, and credit ledger services
- organization admin and platform billing APIs
- `/admin/billing` and `/platform/billing` product UI
- runtime metering hooks for chat, RAG, tools, workflow, KB indexing, and Open API where they do not collide with active TASK-112 work
- deterministic default plan/subscription seed behavior
- focused backend tests, frontend build, and desktop visual QA

## Out Of Scope

- Payment provider, invoice tax, or external finance system integration.
- Editing TASK-112-owned Open API files while TASK-112 is active, unless MANAGER-001 explicitly updates both assignments.

## Preflight

Before editing, run task-scoped `dev-login.py` for `MANAGER-001` on branch `codex/TASK-114-feat-037-billing-ledger`.

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
