---
kind: current-status
version: 4
updated_at: 2026-07-14T13:07:01Z
updated_by: MANAGER-001
phase: frontend-theme-consistency-and-alignment
active_task: "TASK-207"
next_action: "Audit and repair every authenticated frontend page for theme consistency, organization monogram correctness and desktop alignment."
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

- TASK-207 is ready for implementation: FEAT-113 covers the authenticated assistant shell, all front-office AI applications, personal settings and shared overlays across eight themes. The organization switcher will use the current organization name's first visible character, and every checked page must pass desktop alignment, overflow and console gates.
- TASK-206 is complete in production `2.6.11 / c540988655cb`. The pagecomponent now reads the current CRM session with `$CCDK.CCToken.getToken()`, the backend validates it through `/api/user/getUserInfo`, and strict session-user/page-user/AgentCiCi-member consistency remains in force. Real CRM initial load plus two refreshes produced three HTTP 200 ticket/consume pairs with no mapping error.
- TASK-205 is complete in production `2.6.8 / 095094300a25`: the platform-standard CRM business analysis Skill, deterministic intent gate and `crm_product_sales_rank` tool are live; `cici-system` published version 3 pins the Skill. Five new sessions returned the same quantity Top 5 and server logs show exactly five skill-scoped high-level tool calls with no atomic CRM exploration.
- CloudCC batch `TASK-205-CRM-ANALYTICS-DEMO-V1` is idempotently present with 12 products, 16 reused accounts, 24 opportunities, 72 opportunity products, 16 contracts, 48 orders and 144 order items. Final readback preserves quantity Top 5 `X1 130 / G5 110 / S2 95 / MP 75 / PA 65`, while amount ranking differs as designed.

- TASK-204 is ready: the approved design removes the nested frame and excess inset around the Agent Builder guide, then replaces the two persistent avatar buttons with an accessible avatar-triggered upload/change/remove menu. FEAT-110 awaits written user review before implementation.
- TASK-203 is in progress to expand the bound CloudCC CRM and AgentCiCi demo organization into a 16-customer V2 dataset covering every new-customer, existing-customer, interaction archive, memory, dynamic score and evidence-driven action scenario. Owen/SalesA currently has a valid CRM session but zero visible Accounts because the TASK-172 core records are all owned by SalesB.
- TASK-202 is complete in production `2.6.6 / 4caaa4800b3d`. The hotfix keeps agent bar, chat panel, sidebar metrics and machine lanes transparent and removes avatar scaling/shadows across all eight themes.
- TASK-200 is complete in production `2.6.4 / d88f4293759f`: V79, four-layer evaluation assets, deterministic assertions, real candidate execution, snapshots/comparison/staleness, publish gates, Trace regression capture, quality issues and platform/tenant/Builder/Ops product surfaces are live.
- TASK-199 is complete in production `2.6.2`: first-open fixed recommendations and demo action seeds are removed. Confirmed interactions produce AI action candidates governed by verbatim-evidence validation, confidence, business-key deduplication/refresh, seven-day cooldown, historical validity and the existing human-confirmed CRM write path.
- TASK-198 is complete in production `2.6.1`: V77 stores evidence-backed AI signals and versioned score snapshots; new interactions incrementally update the current customer with confidence gating, 90-day decay and lifecycle replacement. Queue filtering/sorting, detail metrics and the explanation drawer share one snapshot source.
- TASK-197 is done in production `2.5.11`: confirmed interactions now retain archive linkage, AI analysis, original materials and typed customer memory; timeline and assistant evidence open the same auditable archive.
- Production currently runs `2.6.11 / c540988655cb`; backend/frontend and four state services are healthy, Flyway remains at V80, and CloudCC pagecomponent V15/customPage V9 are the active embedded assets. The release backup is `/opt/cici/backups/20260714-202718-before-2.6.11-task206-cloudcc-session-sso`.
- TASK-182 now uses current-user CloudCC tokens and record permissions for Account/Contact/Opportunity/Task/Event/Case/Contract projection, server-side new/existing queues, real metrics/signals, follow/notifications, all business tabs, customer-level AI history/actions, manually confirmed interaction ingestion, and supervisor summaries.
- TASK-170 security rules platform remains in progress and may resume after TASK-200 merge/release planning.
- Known DNS risk remains: this workstation cannot resolve `onechat.agentcici.com`; production-IP resolved smoke previously returned HTTP 200.

## Read Next

- `.claw/tasks/TASK-207.md`, `.claw/assignments/TASK-207.yaml` and `docs/specs/FEAT-113-frontend-theme-consistency-and-alignment.md` - active full frontend theme and alignment governance.
- `.claw/tasks/TASK-206.md` and `docs/specs/FEAT-112-cloudcc-embed-sso-recovery.md` - completed CloudCC embed SSO recovery and verification evidence.
- `.claw/tasks/TASK-205.md` and `.claw/assignments/TASK-205.yaml` - active CRM analysis delivery and authorization.
- `docs/specs/FEAT-111-crm-business-analysis-skill.md` - approved architecture, object map, field dictionary, tool contract, demo-data design and acceptance source.

- `.claw/tasks/TASK-204.md` and `.claw/assignments/TASK-204.yaml` - approved Agent Builder polish task and authorization.
- `docs/specs/FEAT-110-agent-builder-guide-avatar-polish.md` - guide spacing, avatar interaction and acceptance source.
- `.claw/tasks/TASK-203.md` and `.claw/assignments/TASK-203.yaml` - active comprehensive demo-data task and authorization.
- `docs/specs/FEAT-109-customer-workbench-comprehensive-demo-scenarios.md` - scenario matrix, data scale, visibility and acceptance source.
- `.claw/task-board.md` - compact task index.
- `.claw/test-report.md` - latest verified commands.
