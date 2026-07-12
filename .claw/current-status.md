---
kind: current-status
version: 4
updated_at: 2026-07-12T11:50:00Z
updated_by: MANAGER-001
phase: platform-security-rules
active_task: "TASK-198"
next_action: "Release and verify the safe historical interaction signal backfill hotfix, then resume TASK-170."
read_next:
  goals: false
  decisions: false
  issue_list: false
  task_board: true
  active_task_status: true
  test_report: true
  devops: true
---

# Project Current Status

`current-status.md` is the hot index. Rewrite it as the latest snapshot; do not append session history.

## Snapshot

- TASK-198 is in production hotfix validation after `2.5.12`: V77 stores evidence-backed AI signals and versioned score snapshots; new interactions incrementally update the current customer with confidence gating, 90-day decay and lifecycle replacement. Queue filtering/sorting, detail metrics and the explanation drawer share one snapshot source.
- TASK-198 follow-up closes a real-data gap: recent confirmed archives missing the new `scoringSignals` contract are lazily backfilled as 60%-confidence `PENDING` evidence, never altering the score; archives already carrying new signals preserve their original values. Focused backend validation now passes 18 tests and repeated explanation reads are idempotent.

- TASK-197 is done in production `2.5.11`: confirmed interactions now retain archive linkage, AI analysis, original materials and typed customer memory; timeline and assistant evidence open the same auditable archive.
- TASK-197 retrieval defaults to a compact customer snapshot, 90 days / 20 recent interactions and 8 relevant ACTIVE memories/evidence. Explicit history questions expand the window, and an explicit archive ID is ranked first.
- TASK-197 evidence: focused backend tests, 64 frontend tests and production build passed; real Beijing Smart Manufacturing data produced 10 memory items, assistant context returned 7 evidence items without history expansion, original material opened in a new browser tab, and final browser console had zero errors or warnings.

- TASK-196 is done in production `2.5.9`: interaction confirmation no longer starts a full CRM refresh, queue reloads preserve the selected customer, the interaction editor freezes its Account context, and ordinary old-customer analysis no longer triggers mode navigation.
- TASK-196 evidence: frontend 64 tests/build and 12 backend focused tests passed; real “奔驰” search retained 4 results and the selected Mercedes-Benz customer after confirmation and a 35-second poll, with no `refresh=true`, browser error, backend target error or Nginx 5xx.

- TASK-195 is done in production `2.5.8`: compact and full customer interaction timelines display `YYYY-MM-DD` plus `HH:mm` on two stable lines, with no date-internal wrapping.
- TASK-195 evidence: frontend 62 tests/build passed; a real 22-event timeline included visible 2026 and 2023 records, all sampled labels had four-digit years, the icon/vertical-axis delta was 0px, and the page had no outer overflow or business error.

- TASK-194 is done in production `2.5.6`: customer-name search now queries all Accounts visible to the current CloudCC identity, bypasses new/existing mode, queue filter and projection-cache limits, loads cache-external detail on demand, and aligns the workspace to the matched customer's actual mode.
- TASK-194 evidence: backend 11 focused tests and frontend 60 tests/build passed; the real large organization found “青岛海信商用显示股份有限公司” in about 0.76 seconds and loaded detail in about 0.22 seconds. Browser verification showed one 1px wrapper focus border, no inner input border/shadow, no business error, and correct old-customer operations UI.

- TASK-193 is done in production `2.5.3`: new-customer advancement and existing-customer operations both default to recent-interaction descending order; missing interaction timestamps stay last and ties use deterministic account ordering.
- TASK-193 evidence: backend 10 tests, frontend 59 tests/build, release backup/health/public smoke and real large-organization default-query checks passed; both modes returned 12 rows in descending timestamp order with no post-release errors.

- TASK-192 is done in production `2.5.2`: the large CRM organization now initializes through per-user asynchronous single-flight projection with stale-while-revalidate, bounded parallel reads, indexed aggregation and bulk recommendation lookup. Cold-cache startup requests return `200/SYNCING` in about one second instead of four 60-second 504 responses.
- TASK-192 evidence: the real organization background sync completed in 46.21 seconds with 10,000 visible accounts; READY queue read returned in 0.68 seconds, no post-release 504/upstream timeout remained, and raw gateway HTML is normalized. The current 10,000-record OpenAPI ceiling is explicitly surfaced and remains a separate incremental-projection follow-up.

- TASK-191 is done in production `2.4.12`: pagecomponent V11 observes delayed/reused CloudCC host nodes and remounts a missing iframe; customer signals use atomic PostgreSQL UPSERT with a repository-level short transaction.
- TASK-191 evidence: 8 focused/integration tests passed; real CloudCC loaded CRM data and assistant history after three consecutive refreshes; no post-release duplicate-key, transaction-required or unexpected-server errors were logged. Screenshot: `output/playwright/task191-prod-cloudcc-refresh-stable.png`.

- Current branch: `main`; production is running release `2.5.12` from Git commit `4adbd3bf2d3a`; TASK-198 historical signal backfill is under hotfix validation, while TASK-182 through TASK-197 remain complete.
- TASK-182 now uses current-user CloudCC tokens and record permissions for Account/Contact/Opportunity/Task/Event/Case/Contract projection, server-side new/existing queues, real metrics/signals, follow/notifications, all business tabs, customer-level AI history/actions, manually confirmed interaction ingestion, and supervisor summaries.
- Task and Opportunity recommendations now support edit, dismiss, accept, confirm, idempotent CloudCC write, permission-scoped readback, failure/retry and audit. V73 stores signals/follows/write audit and V74 stores user recommendation feedback; demo fallback is explicit and write-disabled.
- Acceptance passed focused backend tests, 54 frontend tests, production build, Compose validation, desktop browser checks, CloudCC catalog/injection verification, AgentCiCi and CRM dual-entry identity/permission checks, and real Task write/readback verification through `cc-customization-expert-msapi`.
- Releases `2.3.10` through `2.3.12` completed the production data path, all-existing-customer default queue and optimistic-lock/idempotent CRM write recovery. Two accepted CRM Task recommendations remain as intentional production acceptance records and both read back with the expected account, subject, status and due date.
- Release `2.4.1` fixes the AI customer assistant voice/send race and latest-message positioning. Real CRM embedded acceptance confirmed the composer clears immediately after send and remains empty after reply; a long reply produced `scrollHeight=2020`, `scrollTop=1460`, `clientHeight=560`, exactly at the latest message.
- The skill gap report records a verified same-component-ID `stale_component_reference` false positive when runtime version evidence is absent, nested create-ID parsing, unreliable expression lookup and unrelated script-scan 500 scoping.
- TASK-170 security rules platform remains in progress and is the active task after the completed production hotfix.
- Known DNS risk remains: this workstation cannot resolve `onechat.agentcici.com`; production-IP resolved smoke previously returned HTTP 200.

## Read Next

- `.claw/task-board.md` - compact task index.
- `.claw/tasks/TASK-182.md` - completed customer workbench production closure state.
- `.claw/assignments/TASK-182.yaml` - authorized write scope.
- `docs/specs/FEAT-081-customer-interaction-workbench.md` - design, gap matrix, APIs, data model, and acceptance criteria.
- `.claw/test-report.md` - latest verified commands.
- `.claw/devops.md` and `docs/production-release-runbook.md` - production release facts if release is executed.
