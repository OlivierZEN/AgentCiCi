---
kind: current-status
version: 4
updated_at: 2026-07-14T06:55:00Z
updated_by: MANAGER-001
phase: user-theme-visual-hotfix-release
active_task: "TASK-202"
next_action: "Publish the verified theme visual hotfix as 2.6.6 and complete production browser acceptance."
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

- TASK-203 is complete: Owen/SalesA now sees 16 V2 CRM customers split 8 new/8 existing, all eight queue filters are nonzero, and 30 interaction archives, 30 memories, 30 five-dimensional signals and 12 evidence-driven pending actions cover FEAT-109. AgentCiCi backup is `/opt/cici/backups/20260714-065319-before-task203-demo-v2`; Contract/Case read permission rollback source is `ope202682B741D7w0fRu`.
- TASK-202 theme preferences are live in `2.6.5`; a user-reported visual regression showed structural wrappers repainted as background boxes. The verified `2.6.6` hotfix keeps agent bar, chat panel, sidebar metrics and machine lanes transparent and removes avatar scaling/shadows across all eight themes.
- TASK-202 hotfix evidence: 13 frontend test files / 73 tests, production build, diff check, eight-theme 2048×1152 browser matrix, zero horizontal overflow and Product Design QA all passed. Production release `2.6.6` is in progress.
- TASK-200 is complete in production `2.6.4 / d88f4293759f`: V79, four-layer evaluation assets, deterministic assertions, real candidate execution, snapshots/comparison/staleness, publish gates, Trace regression capture, quality issues and platform/tenant/Builder/Ops product surfaces are live.
- FEAT-106 supersedes FEAT-031 as the delivered full-system design while preserving the existing V67 evaluation tables and compatibility APIs. Agent Builder now has an independent “评测” Tab；“发布渠道” contains only IM/Web/Open API delivery entries.
- TASK-200 evidence: 20 focused/adjacent backend tests and 67 frontend tests passed; production build, Compose, two release dry-runs, V79 migration, tenant/platform RBAC API smoke and desktop browser checks passed. Release `2.6.3` exposed an Nginx API routing gap and was immediately superseded by `2.6.4`; it is not a rollback target.
- TASK-199 is complete in production `2.6.2`: first-open fixed recommendations and demo action seeds are removed. Confirmed interactions produce AI action candidates governed by verbatim-evidence validation, confidence, business-key deduplication/refresh, seven-day cooldown, historical validity and the existing human-confirmed CRM write path.
- TASK-199 production evidence: a real old-customer interaction generated one 100%-confidence `CREATE_OPPORTUNITY` action for the independent mobile-inspection expansion, linked to its interaction event/batch and original sentence; repeated confirmation stayed idempotent at one action. The action was intentionally not written to CRM.
- TASK-198 is complete in production `2.6.1`: V77 stores evidence-backed AI signals and versioned score snapshots; new interactions incrementally update the current customer with confidence gating, 90-day decay and lifecycle replacement. Queue filtering/sorting, detail metrics and the explanation drawer share one snapshot source.
- TASK-197 is done in production `2.5.11`: confirmed interactions now retain archive linkage, AI analysis, original materials and typed customer memory; timeline and assistant evidence open the same auditable archive.
- TASK-197 retrieval defaults to a compact customer snapshot, 90 days / 20 recent interactions and 8 relevant ACTIVE memories/evidence. Explicit history questions expand the window, and an explicit archive ID is ranked first.
- TASK-196 is done in production `2.5.9`: interaction confirmation no longer starts a full CRM refresh, queue reloads preserve the selected customer, the interaction editor freezes its Account context, and ordinary old-customer analysis no longer triggers mode navigation.
- TASK-195 is done in production `2.5.8`: compact and full customer interaction timelines display `YYYY-MM-DD` plus `HH:mm` on two stable lines, with no date-internal wrapping.
- TASK-194 is done in production `2.5.6`: customer-name search now queries all Accounts visible to the current CloudCC identity, bypasses new/existing mode, queue filter and projection-cache limits, loads cache-external detail on demand, and aligns the workspace to the matched customer's actual mode.
- Production currently runs `2.6.5 / fc8a6d117bde`; backend/frontend and state services are healthy, Flyway is at V80, and the release backup is `/opt/cici/backups/20260714-102055-before-2.6.5-task202-user-themes`.
- TASK-182 now uses current-user CloudCC tokens and record permissions for Account/Contact/Opportunity/Task/Event/Case/Contract projection, server-side new/existing queues, real metrics/signals, follow/notifications, all business tabs, customer-level AI history/actions, manually confirmed interaction ingestion, and supervisor summaries.
- Task and Opportunity recommendations now support edit, dismiss, accept, confirm, idempotent CloudCC write, permission-scoped readback, failure/retry and audit. V73 stores signals/follows/write audit and V74 stores user recommendation feedback; demo fallback is explicit and write-disabled.
- Acceptance passed focused backend tests, 54 frontend tests, production build, Compose validation, desktop browser checks, CloudCC catalog/injection verification, AgentCiCi and CRM dual-entry identity/permission checks, and real Task write/readback verification through `cc-customization-expert-msapi`.
- Releases `2.3.10` through `2.3.12` completed the production data path, all-existing-customer default queue and optimistic-lock/idempotent CRM write recovery. Two accepted CRM Task recommendations remain as intentional production acceptance records and both read back with the expected account, subject, status and due date.
- Release `2.4.1` fixes the AI customer assistant voice/send race and latest-message positioning. Real CRM embedded acceptance confirmed the composer clears immediately after send and remains empty after reply; a long reply produced `scrollHeight=2020`, `scrollTop=1460`, `clientHeight=560`, exactly at the latest message.
- TASK-170 security rules platform remains in progress and may resume after TASK-200 merge/release planning.
- Known DNS risk remains: this workstation cannot resolve `onechat.agentcici.com`; production-IP resolved smoke previously returned HTTP 200.

## Read Next

- `.claw/tasks/TASK-203.md` and `.claw/assignments/TASK-203.yaml` - completed comprehensive demo-data task and authorization.
- `docs/specs/FEAT-109-customer-workbench-comprehensive-demo-scenarios.md` - scenario matrix, data scale, visibility and acceptance source.
- `.claw/task-board.md` - compact task index.
- `.claw/tasks/TASK-202.md` and `.claw/assignments/TASK-202.yaml` - active theme task and authorization.
- `docs/specs/FEAT-108-user-selectable-product-themes.md` - approved theme catalog, persistence, synchronization and acceptance source.
- `.claw/tasks/TASK-201.md` and `.claw/assignments/TASK-201.yaml` - completed layout task and authorization.
- `.claw/tasks/TASK-200.md` and `.claw/assignments/TASK-200.yaml` - completed task evidence and authorization.
- `docs/specs/FEAT-106-multi-tenant-agent-evaluation-control-plane.md` - production-ready design and acceptance source.
- `.claw/test-report.md` - latest verified commands.
- `.claw/devops.md` and `docs/production-release-runbook.md` - production release facts if release is executed.
