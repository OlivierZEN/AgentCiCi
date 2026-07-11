---
kind: current-status
version: 4
updated_at: 2026-07-11T04:03:47Z
updated_by: MANAGER-001
phase: customer-workbench-queue-clipping-hotfix
active_task: "TASK-184"
next_action: "Fix left queue horizontal overflow, verify narrow and wide desktop layouts, then publish the production hotfix."
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

- TASK-184 is active after production screenshot evidence showed the left customer queue wider than its visible column, causing right-side controls and customer content to be clipped by the center pane. The hotfix is limited to queue-internal sizing and desktop layout verification.
- TASK-183 is done in production `2.4.2` for screenshot-driven UI cleanup and customer-assistant streaming. A single inline queue-settings control, explicit read-only CRM demo status, Lucide icons, SSE phases/deltas, safe Markdown rendering, immediate input clearing and automatic latest-message following are live.
- TASK-183 local gates passed 56 frontend tests, Vite build, focused backend tests/compile, 1920x960 browser interaction checks and zero console errors. The browser exposed and verified a seven-row queue-grid fix when settings are expanded; send showed a processing state within 60ms and the completed long conversation remained exactly at the bottom.
- TASK-183 production evidence: release commit/tag/image/version `49402ae8f3a0` / `2.4.2`; six services healthy; V72-V74 successful; public routes 200; SSE emitted 40 deltas without error; AgentCiCi and real CloudCC iframe both showed immediate status/input clearing and stayed at the latest message; injection verification returned `issues=[]`.
- Current branch: `main`; production is running release `2.4.2` from Git commit `49402ae8f3a0`; TASK-182/TASK-183 are complete and FEAT-081/FEAT-092 are production ready.
- TASK-182 now uses current-user CloudCC tokens and record permissions for Account/Contact/Opportunity/Task/Event/Case/Contract projection, server-side new/existing queues, real metrics/signals, follow/notifications, all business tabs, customer-level AI history/actions, manually confirmed interaction ingestion, and supervisor summaries.
- Task and Opportunity recommendations now support edit, dismiss, accept, confirm, idempotent CloudCC write, permission-scoped readback, failure/retry and audit. V73 stores signals/follows/write audit and V74 stores user recommendation feedback; demo fallback is explicit and write-disabled.
- Acceptance passed focused backend tests, 54 frontend tests, production build, Compose validation, desktop browser checks, CloudCC catalog/injection verification, AgentCiCi and CRM dual-entry identity/permission checks, and real Task write/readback verification through `cc-customization-expert-msapi`.
- Releases `2.3.10` through `2.3.12` completed the production data path, all-existing-customer default queue and optimistic-lock/idempotent CRM write recovery. Two accepted CRM Task recommendations remain as intentional production acceptance records and both read back with the expected account, subject, status and due date.
- Release `2.4.1` fixes the AI customer assistant voice/send race and latest-message positioning. Real CRM embedded acceptance confirmed the composer clears immediately after send and remains empty after reply; a long reply produced `scrollHeight=2020`, `scrollTop=1460`, `clientHeight=560`, exactly at the latest message.
- The skill gap report records a same-component-ID `stale_component_reference` false positive, nested create-ID parsing, unreliable expression lookup and unrelated script-scan 500 scoping.
- TASK-181 is done in production `2.3.9`: customer workbench left customer list alignment hotfix passed local and production desktop Chrome validation. Rows are stable at `104px`, with no row overflow, no adjacent overlap, no outer document/body scrollbar, and console errors `0`.
- TASK-180 is done in production `2.3.8`: AI 应用常驻大列表 has been replaced by a click-triggered floating vertical app list; customer workbench density and border treatment have been tightened; outer document/body scrollbars are absent in production desktop validation.
- TASK-180 evidence: assignment/login gates, local frontend build, compose config, release dry-run, ACR image push, Git tag, production backup/deploy/health/public smoke, authenticated production browser workbench/flyout checks for `org2sva14i4udjmi2t4s`, customer workbench API smoke, and zero browser console errors passed. Screenshots: `output/playwright/task180-prod-workbench-demo-org2-2.3.8.png` / `output/playwright/task180-prod-flyout-demo-org2-2.3.8.png`.
- TASK-179 is done in production `2.3.7`: AI 听记 realtime uses `auto`; configured organizations select Iflytek with `role_type=2`, while unconfigured organizations keep Aliyun transcription with an explicit diarization-degraded notice.
- TASK-179 evidence: backend 7 focused tests, frontend 7 tests, production build, compose validation, local fallback flow, production configured-Iflytek flow, health/version/public smoke, and zero browser console errors passed. Full backend baseline has unrelated fixture/connection failures recorded in `.claw/test-report.md`.
- TASK-178 is done in production `2.3.5`: CRM embedded customer-workbench microphone permission and ASR startup-error reporting were fixed.
- TASK-175/TASK-176 are done in production `2.3.4`: customer-workbench scroll cleanup and customer/data insight separation.
- TASK-174 data insight is done in production `2.3.2`; demo organization `org2sva14i4udjmi2t4s` uses real CRM-backed aggregate rows.
- TASK-173 real customer-workbench assistant is done in production `2.3.1`.
- TASK-170 security rules platform remains in progress and resumes after the newer production hotfix.
- Known DNS risk remains: this workstation cannot resolve `onechat.agentcici.com`; production-IP resolved smoke previously returned HTTP 200.

## Read Next

- `.claw/task-board.md` - compact task index.
- `.claw/tasks/TASK-182.md` - completed customer workbench production closure state.
- `.claw/assignments/TASK-182.yaml` - authorized write scope.
- `docs/specs/FEAT-081-customer-interaction-workbench.md` - design, gap matrix, APIs, data model, and acceptance criteria.
- `.claw/test-report.md` - latest verified commands.
- `.claw/devops.md` and `docs/production-release-runbook.md` - production release facts if release is executed.
