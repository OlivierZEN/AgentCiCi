---
kind: current-status
version: 4
updated_at: 2026-07-14T10:34:25Z
updated_by: MANAGER-001
phase: cloudcc-embed-sso-recovery
active_task: "TASK-206"
next_action: "Implement and publish bounded CloudCC pagecomponent SSO recovery, then verify ticket/consume and CRM data in the real embedded page."
read_next:
  goals: false
  decisions: false
  issue_list: false
  task_board: true
  active_task_status: true
  test_report: false
  devops: false
---

# Project Current Status

`current-status.md` is the hot index. Rewrite it as the latest snapshot; do not append session history.

## Snapshot

- TASK-206 is in progress for a real CloudCC CRM embedded identity-sync failure. Online component/customPage binding is correct and the same test user now completes ticket/consume/data reads with HTTP 200; the verified defect is that a single transient failure leaves the pagecomponent SSO lock set forever with no retry or token refresh.
- TASK-205 is ready: the user approved a platform-standard CRM business analysis Skill plus deterministic `crm_product_sales_rank` tool and requested rich high-fidelity data in the bound CloudCC demo tenant, ending with repeated real-chat acceptance for “销量最好的产品有哪些”.
- FEAT-111 is the design source. A read-only CloudCC `standard-catalog` scan confirmed `product`, `cloudccorder`, `cloudccorderitem`, `contract`, `Opportunity`, `opportunitypdt` and their required quantity/amount/date/status/reference fields; no metadata change is planned.

- TASK-204 is ready: the approved design removes the nested frame and excess inset around the Agent Builder guide, then replaces the two persistent avatar buttons with an accessible avatar-triggered upload/change/remove menu. FEAT-110 awaits written user review before implementation.
- TASK-203 is in progress to expand the bound CloudCC CRM and AgentCiCi demo organization into a 16-customer V2 dataset covering every new-customer, existing-customer, interaction archive, memory, dynamic score and evidence-driven action scenario. Owen/SalesA currently has a valid CRM session but zero visible Accounts because the TASK-172 core records are all owned by SalesB.
- TASK-202 is complete in production `2.6.6 / 4caaa4800b3d`. The hotfix keeps agent bar, chat panel, sidebar metrics and machine lanes transparent and removes avatar scaling/shadows across all eight themes.
- TASK-200 is complete in production `2.6.4 / d88f4293759f`: V79, four-layer evaluation assets, deterministic assertions, real candidate execution, snapshots/comparison/staleness, publish gates, Trace regression capture, quality issues and platform/tenant/Builder/Ops product surfaces are live.
- TASK-199 is complete in production `2.6.2`: first-open fixed recommendations and demo action seeds are removed. Confirmed interactions produce AI action candidates governed by verbatim-evidence validation, confidence, business-key deduplication/refresh, seven-day cooldown, historical validity and the existing human-confirmed CRM write path.
- TASK-198 is complete in production `2.6.1`: V77 stores evidence-backed AI signals and versioned score snapshots; new interactions incrementally update the current customer with confidence gating, 90-day decay and lifecycle replacement. Queue filtering/sorting, detail metrics and the explanation drawer share one snapshot source.
- TASK-197 is done in production `2.5.11`: confirmed interactions now retain archive linkage, AI analysis, original materials and typed customer memory; timeline and assistant evidence open the same auditable archive.
- Production currently runs `2.6.6 / 4caaa4800b3d`; backend/frontend and state services are healthy, Flyway is at V80, and the release backup is `/opt/cici/backups/20260714-142848-before-2.6.6-task202-theme-visual-hotfix`.
- TASK-182 now uses current-user CloudCC tokens and record permissions for Account/Contact/Opportunity/Task/Event/Case/Contract projection, server-side new/existing queues, real metrics/signals, follow/notifications, all business tabs, customer-level AI history/actions, manually confirmed interaction ingestion, and supervisor summaries.
- TASK-170 security rules platform remains in progress and may resume after TASK-200 merge/release planning.
- Known DNS risk remains: this workstation cannot resolve `onechat.agentcici.com`; production-IP resolved smoke previously returned HTTP 200.

## Read Next

- `.claw/tasks/TASK-206.md` and `.claw/assignments/TASK-206.yaml` - active CloudCC embed SSO recovery and authorization.
- `docs/specs/FEAT-112-cloudcc-embed-sso-recovery.md` - root cause, retry policy, identity boundary and acceptance source.
- `.claw/tasks/TASK-205.md` and `.claw/assignments/TASK-205.yaml` - active CRM analysis delivery and authorization.
- `docs/specs/FEAT-111-crm-business-analysis-skill.md` - approved architecture, object map, field dictionary, tool contract, demo-data design and acceptance source.

- `.claw/tasks/TASK-204.md` and `.claw/assignments/TASK-204.yaml` - approved Agent Builder polish task and authorization.
- `docs/specs/FEAT-110-agent-builder-guide-avatar-polish.md` - guide spacing, avatar interaction and acceptance source.
- `.claw/tasks/TASK-203.md` and `.claw/assignments/TASK-203.yaml` - active comprehensive demo-data task and authorization.
- `docs/specs/FEAT-109-customer-workbench-comprehensive-demo-scenarios.md` - scenario matrix, data scale, visibility and acceptance source.
- `.claw/task-board.md` - compact task index.
- `.claw/test-report.md` - latest verified commands.
