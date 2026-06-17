---
kind: task-status
task_id: TASK-154
status: review
updated_at: 2026-06-18T00:08:00+08:00
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-154.yaml
spec_path: docs/specs/FEAT-037-saas-billing-usage-ledger.md
---

# TASK-154 - Credits metering production readiness sweep

## Scope

- Audit user-side and admin/platform-side runtime functions against FEAT-037 Credits billing domains.
- Keep existing chat, RAG, tool-call, workflow-run, and AI meeting-minutes billing behavior intact.
- Add missing production-ready usage and ledger entries where the runtime has stable facts.
- Avoid schema, payment, invoice, tax, renewal, production release, and mobile QA work.

## Initial Findings

- Chat and stream chat already call `BillingUsageMeteringService.recordChatRunSafely()` and include assistant chat, model usage, RAG chunks, tool calls, and workflow elapsed facts.
- Embedded AI meeting-minutes summary is covered by TASK-152 work through `recordMeetingMinutesRunSafely()`.
- Open API chat invokes the same chat runtime, but the API request itself lacks an `open_api_chat` usage event keyed by credential/request/idempotency.
- Knowledge-base document publishing, reindexing, and manual chunk indexing create embeddings/vectors but do not emit `kb_indexing` usage facts or ledger debits.
- Personal workflow `run-now` and due-trigger execution can run tools outside chat and need a successful `workflow_run` usage fact.
- `BillingMeteringService` still carries an older rate-card path; production work should converge new runtime hooks onto `BillingUsageMeteringService` to avoid dual billing semantics.

## Changes

- Extended `BillingUsageMeteringService` with idempotent Open API chat, KB indexing, and standalone workflow-run metering methods.
- Open API blocking and streaming success paths now record `open_api_chat` request Credits after call-log success; replayed idempotent messages do not run billing again.
- Knowledge-base document publish/reindex, manual chunk add, chunk update, and missing-vector re-enable now record `kb_indexing` usage after successful vector work.
- Personal workflow manual and due-trigger success paths now record `workflow_run`; disabled/failed workflows and platform schedule stubs do not debit Credits.
- Added focused Open API and KB integration assertions for usage meter events and append-only credits ledger entries.

## Verification

- `dev-login.py .claw --developer MANAGER-001 --task TASK-154 --branch codex/TASK-152-ai-minutes-billing-timeout ...` -> allowed.
- `check-assignment.py .claw --developer MANAGER-001 --task TASK-154 --branch codex/TASK-152-ai-minutes-billing-timeout ...` -> allowed.
- `cd backend && mvn -Dmaven.repo.local=.m2 -Dtest=AgentOpenApiIntegrationTest,KnowledgeBaseLifecycleIntegrationTest,AdminBillingIntegrationTest test` -> success, 29 tests passed.
- `git diff --check` -> success.

## Handoff

- Branch: `codex/TASK-152-ai-minutes-billing-timeout`.
- Existing dirty worktree contains TASK-152/TASK-153 changes; TASK-154 must preserve them and avoid reverting unrelated edits.
