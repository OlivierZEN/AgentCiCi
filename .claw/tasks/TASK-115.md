---
kind: task-status
task_id: TASK-115
assignee: DEV-zhongda
owner_role: fullstack-agent
status: ready
branch: codex/TASK-115-kb-module-maintenance
pr_url: n/a
spec_path: docs/specs/FEAT-008-knowledge-base-lifecycle-completion.md
assignment_path: .claw/assignments/TASK-115.yaml
updated_at: 2026-05-21T15:47:23Z
updated_by: MANAGER-001
---

# TASK-115 Knowledge Base Module Maintenance

## Scope

Maintain and continue FEAT-008 from the current P0 baseline. Current verified baseline:

- document/KB delete, unpublish, and reindex lifecycle cleanup is implemented
- RAG rechecks DB state before returning vector hits
- chunk preview, retrieval test, retrieval logs, metadata fields, document metadata, chunk operations, and batch operations are implemented
- `KnowledgeBaseLifecycleIntegrationTest` passes locally: 8 tests, 0 failures, 0 errors

## Development Package

### P0 Hardening

- Add explicit upload limits: max file size, allowed content types, and batch count.
- Add predictable parser strategy for `txt/md/csv/json`, plus PDF/DOCX support or explicit product-facing rejection with reason.
- Add Qdrant lifecycle smoke/integration coverage for upsert, delete by ids, delete by document, delete by KB, and audit behavior.
- Replace `window.confirm` / `window.prompt` knowledge-base admin flows with project-standard modal dialogs when editing UI.
- Add focused regression tests for unsupported files, parser failures, stale vector filtering, metadata validation, and cleanup failure states.

### P1 Feature Completion

- Extend runtime RAG to accept metadata filters from agent/app/chat context, not only retrieval test.
- Return structured citation/source metadata from retrieval: KB, document, chunk, score, source, and optional metadata.
- Surface citations in chat/trace UI without exposing internal implementation codes by default.
- Add data source model skeletons for `LOCAL_FILE`, `EMPTY`, `WEB`, `NOTION`, and `EXTERNAL_API`, with sync status and source config shape.
- Add full-text/hybrid retrieval design or first implementation slice, preserving current vector behavior.

### P2 Expansion

- Add rerank provider/model extension points.
- Add Knowledge Service API/API access design or first safe slice for programmatic KB/document/chunk maintenance.
- Explore parent-child chunk data model and retrieval behavior after parser and citation work are stable.

## Out Of Scope

- Open API parity files owned by TASK-112 unless MANAGER-001 expands both assignments.
- Billing usage ledger files and migration `V53__billing_usage_ledger.sql` owned by TASK-114 unless MANAGER-001 expands both assignments.
- Payment, tenant billing, or non-KB runtime metering.

## Preflight

Before editing, run task-scoped `dev-login.py` for `DEV-zhongda` on branch `codex/TASK-115-kb-module-maintenance`.

## Verification Target

- `mvn -q -Dmaven.repo.local=.m2 -Dtest=KnowledgeBaseLifecycleIntegrationTest test`
- Focused new backend tests for the changed KB behavior
- Backend compile when backend dependencies or shared service contracts change
- `npm run build` when frontend changes are made
- Desktop screenshots for `/admin/kb` when product UI changes are made; do not add mobile compatibility implementation or mobile tests unless separately requested
- `.claw` state validation passes after handoff updates

## Assignment History

- 2026-05-19T10:01:14+08:00: User asked to organize the KB assessment into development tasks and assign overall knowledge-base module maintenance to `DEV-zhongda`.
- 2026-05-21T23:47:23+08:00: Verification scope aligned with the project rule that new feature work does not add mobile compatibility implementation, screenshots, or tests by default.
