---
kind: current-status
version: 4
updated_at: 2026-07-14T01:30:00Z
updated_by: MANAGER-001
phase: user-theme-preferences-complete
active_task: "none"
next_action: "TASK-202 is locally complete and verified; await an explicit production release request."
read_next:
  goals: false
  decisions: false
  issue_list: false
  task_board: true
  active_task_status: false
  test_report: true
  devops: true
---

# Project Current Status

`current-status.md` is the hot index. Rewrite it as the latest snapshot; do not append session history.

## Snapshot

- TASK-202 is complete locally: every organization user and platform account can select one of eight shared product themes, with immediate preview, local first-paint caching, account-level persistence and reload synchronization across the main app, management console, operations platform and CRM embedded customer workbench. The catalog is 鎏金账房、CRM 标准蓝、蓝色海洋、樱花粉语、熏衣紫语、牛油果小调、红酒醇香和星河幻境。
- TASK-202 evidence: V80 migrated a clean PostgreSQL schema; 22 authentication integration tests and 71 frontend tests passed; production build and diff check passed. Real desktop verification covered all theme choices plus app/admin/platform/customer-workbench surfaces, including a public-token fix for dark platform cards. No production release was performed.
- TASK-201 is complete and merged to `origin/main` at `f6be64e`; it was not released to production.
- TASK-201 is locally implemented and in review: the concrete model selector is removed while internal model compatibility remains; the definition form is balanced into equal columns; avatar actions and four strategy buttons share one row; evaluation and publish channels now live in the lower version-control lifecycle workspace. All 68 frontend tests, production build, diff check and real 1280x720 desktop browser verification passed; no production release was performed.
- TASK-200 is complete in production `2.6.4 / d88f4293759f`: V79, four-layer evaluation assets, deterministic assertions, real candidate execution, snapshots/comparison/staleness, publish gates, Trace regression capture, quality issues and platform/tenant/Builder/Ops product surfaces are live.
- FEAT-106 supersedes FEAT-031 as the delivered full-system design while preserving the existing V67 evaluation tables and compatibility APIs. Agent Builder now has an independent “评测” Tab；“发布渠道” contains only IM/Web/Open API delivery entries.
- TASK-200 evidence: 20 focused/adjacent backend tests and 67 frontend tests passed; production build, Compose, two release dry-runs, V79 migration, tenant/platform RBAC API smoke and desktop browser checks passed. Release `2.6.3` exposed an Nginx API routing gap and was immediately superseded by `2.6.4`; it is not a rollback target.
- TASK-199 is complete in production `2.6.2`: first-open fixed recommendations and demo action seeds are removed. Confirmed interactions produce AI action candidates governed by verbatim-evidence validation, confidence, business-key deduplication/refresh, seven-day cooldown, historical validity and the existing human-confirmed CRM write path.
- TASK-199 production evidence: a real old-customer interaction generated one 100%-confidence `CREATE_OPPORTUNITY` action for the independent mobile-inspection expansion, linked to its interaction event/batch and original sentence; repeated confirmation stayed idempotent at one action. The action was intentionally not written to CRM.
- TASK-198 is complete in production `2.6.1`: V77 stores evidence-backed AI signals and versioned score snapshots; new interactions incrementally update the current customer with confidence gating, 90-day decay and lifecycle replacement. Queue filtering/sorting, detail metrics and the explanation drawer share one snapshot source.
- TASK-198 follow-up closed the real-data history gap: recent confirmed archives missing the new `scoringSignals` contract are lazily backfilled as 60%-confidence `PENDING` evidence, never altering the score; archives already carrying new signals preserve their original values. Production generated 2 pending signals for the demo organization and 8 for the large organization while both scores stayed 50; repeated reads were idempotent.
- TASK-197 is done in production `2.5.11`: confirmed interactions now retain archive linkage, AI analysis, original materials and typed customer memory; timeline and assistant evidence open the same auditable archive.
- TASK-197 retrieval defaults to a compact customer snapshot, 90 days / 20 recent interactions and 8 relevant ACTIVE memories/evidence. Explicit history questions expand the window, and an explicit archive ID is ranked first.
- TASK-196 is done in production `2.5.9`: interaction confirmation no longer starts a full CRM refresh, queue reloads preserve the selected customer, the interaction editor freezes its Account context, and ordinary old-customer analysis no longer triggers mode navigation.
- TASK-195 is done in production `2.5.8`: compact and full customer interaction timelines display `YYYY-MM-DD` plus `HH:mm` on two stable lines, with no date-internal wrapping.
- TASK-194 is done in production `2.5.6`: customer-name search now queries all Accounts visible to the current CloudCC identity, bypasses new/existing mode, queue filter and projection-cache limits, loads cache-external detail on demand, and aligns the workspace to the matched customer's actual mode.
- Earlier TASK-191 through TASK-193 production evidence remains in `.claw/test-report.md` and `.claw/devops.md`; no regression was observed during this release.
- Production runs `2.6.4 / d88f4293759f`; backend/frontend are healthy, state services remain healthy on `2.3.4`, Flyway is at V79, and the release backup is `/opt/cici/backups/20260714-075215-before-2.6.4-task200-nginx-hotfix`.
- TASK-182 now uses current-user CloudCC tokens and record permissions for Account/Contact/Opportunity/Task/Event/Case/Contract projection, server-side new/existing queues, real metrics/signals, follow/notifications, all business tabs, customer-level AI history/actions, manually confirmed interaction ingestion, and supervisor summaries.
- Task and Opportunity recommendations now support edit, dismiss, accept, confirm, idempotent CloudCC write, permission-scoped readback, failure/retry and audit. V73 stores signals/follows/write audit and V74 stores user recommendation feedback; demo fallback is explicit and write-disabled.
- Acceptance passed focused backend tests, 54 frontend tests, production build, Compose validation, desktop browser checks, CloudCC catalog/injection verification, AgentCiCi and CRM dual-entry identity/permission checks, and real Task write/readback verification through `cc-customization-expert-msapi`.
- Releases `2.3.10` through `2.3.12` completed the production data path, all-existing-customer default queue and optimistic-lock/idempotent CRM write recovery. Two accepted CRM Task recommendations remain as intentional production acceptance records and both read back with the expected account, subject, status and due date.
- Release `2.4.1` fixes the AI customer assistant voice/send race and latest-message positioning. Real CRM embedded acceptance confirmed the composer clears immediately after send and remains empty after reply; a long reply produced `scrollHeight=2020`, `scrollTop=1460`, `clientHeight=560`, exactly at the latest message.
- The skill gap report records a verified same-component-ID `stale_component_reference` false positive when runtime version evidence is absent, nested create-ID parsing, unreliable expression lookup and unrelated script-scan 500 scoping.
- TASK-170 security rules platform remains in progress and may resume after TASK-200 merge/release planning.
- Known DNS risk remains: this workstation cannot resolve `onechat.agentcici.com`; production-IP resolved smoke previously returned HTTP 200.

## Read Next

- `.claw/task-board.md` - compact task index.
- `.claw/tasks/TASK-202.md` and `.claw/assignments/TASK-202.yaml` - active theme task and authorization.
- `docs/specs/FEAT-108-user-selectable-product-themes.md` - approved theme catalog, persistence, synchronization and acceptance source.
- `.claw/tasks/TASK-201.md` and `.claw/assignments/TASK-201.yaml` - completed layout task and authorization.
- `.claw/tasks/TASK-200.md` and `.claw/assignments/TASK-200.yaml` - completed task evidence and authorization.
- `docs/specs/FEAT-106-multi-tenant-agent-evaluation-control-plane.md` - production-ready design and acceptance source.
- `.claw/test-report.md` - latest verified commands.
- `.claw/devops.md` and `docs/production-release-runbook.md` - production release facts if release is executed.
