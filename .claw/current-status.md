---
kind: current-status
version: 4
updated_at: 2026-07-14T09:28:46Z
updated_by: MANAGER-001
phase: comprehensive-demo-data
active_task: "TASK-203"
next_action: "Continue the authorized TASK-203 comprehensive demo-data implementation and acceptance."
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

- TASK-204 is complete on `codex/TASK-204-agent-builder-avatar-polish`: the Agent Builder guide is transparent, borderless and compact, while the avatar is now the only persistent edit entry with an accessible upload/change/conditional-remove menu. Frontend 76 tests, production build, diff check and authenticated desktop browser acceptance passed with zero overflow or console warnings/errors.
- TASK-203 is in progress to expand the bound CloudCC CRM and AgentCiCi demo organization into a 16-customer V2 dataset covering every new-customer, existing-customer, interaction archive, memory, dynamic score and evidence-driven action scenario. Owen/SalesA currently has a valid CRM session but zero visible Accounts because the TASK-172 core records are all owned by SalesB.
- TASK-202 is complete in production `2.6.6 / 4caaa4800b3d`. The hotfix keeps agent bar, chat panel, sidebar metrics and machine lanes transparent and removes avatar scaling/shadows across all eight themes.
- TASK-202 evidence: 13 frontend test files / 73 tests, production build, diff check, eight-theme 2048×1152 browser matrix, Product Design QA and authenticated production browser acceptance all passed. Production showed fixed 42×42 avatars, zero outer overflow and zero console errors.
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
- Production currently runs `2.6.6 / 4caaa4800b3d`; backend/frontend and state services are healthy, Flyway is at V80, and the release backup is `/opt/cici/backups/20260714-142848-before-2.6.6-task202-theme-visual-hotfix`.
- TASK-182 now uses current-user CloudCC tokens and record permissions for Account/Contact/Opportunity/Task/Event/Case/Contract projection, server-side new/existing queues, real metrics/signals, follow/notifications, all business tabs, customer-level AI history/actions, manually confirmed interaction ingestion, and supervisor summaries.
- Task and Opportunity recommendations now support edit, dismiss, accept, confirm, idempotent CloudCC write, permission-scoped readback, failure/retry and audit. V73 stores signals/follows/write audit and V74 stores user recommendation feedback; demo fallback is explicit and write-disabled.
- Acceptance passed focused backend tests, 54 frontend tests, production build, Compose validation, desktop browser checks, CloudCC catalog/injection verification, AgentCiCi and CRM dual-entry identity/permission checks, and real Task write/readback verification through `cc-customization-expert-msapi`.
- TASK-170 security rules platform remains in progress and may resume after TASK-200 merge/release planning.
- Known DNS risk remains: this workstation cannot resolve `onechat.agentcici.com`; production-IP resolved smoke previously returned HTTP 200.

## Read Next

- `.claw/tasks/TASK-204.md` and `.claw/assignments/TASK-204.yaml` - approved Agent Builder polish task and authorization.
- `docs/specs/FEAT-110-agent-builder-guide-avatar-polish.md` - guide spacing, avatar interaction and acceptance source.
- `.claw/tasks/TASK-203.md` and `.claw/assignments/TASK-203.yaml` - active comprehensive demo-data task and authorization.
- `docs/specs/FEAT-109-customer-workbench-comprehensive-demo-scenarios.md` - scenario matrix, data scale, visibility and acceptance source.
- `.claw/task-board.md` - compact task index.
- `.claw/tasks/TASK-202.md`, `.claw/assignments/TASK-202.yaml` and `docs/specs/FEAT-108-user-selectable-product-themes.md` - completed theme task, authorization and design source.
- `.claw/tasks/TASK-201.md` and `.claw/assignments/TASK-201.yaml` - completed layout task and authorization.
- `.claw/tasks/TASK-200.md` and `.claw/assignments/TASK-200.yaml` - completed task evidence and authorization.
- `docs/specs/FEAT-106-multi-tenant-agent-evaluation-control-plane.md` - production-ready design and acceptance source.
- `.claw/test-report.md` - latest verified commands.
- `.claw/devops.md` and `docs/production-release-runbook.md` - production release facts if release is executed.
