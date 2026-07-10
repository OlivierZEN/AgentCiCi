---
kind: current-status
version: 4
updated_at: 2026-07-10T08:35:00+08:00
updated_by: MANAGER-001
phase: crm-workbench-voice-input-hotfix
active_task: "TASK-178"
next_action: "Fix CRM embedded customer workbench voice input by adding iframe microphone permission and preserving ASR startup errors."
read_next:
  goals: false
  decisions: false
  issue_list: true
  task_board: true
  active_task_status: true
  test_report: true
  devops: true
---

# Project Current Status

`current-status.md` is the hot index. Rewrite it as the latest snapshot; do not append session history.

## Snapshot

- Current branch: `main`; production is running release `2.3.4` from Git commit `22f91cc38a3e`.
- TASK-178 is active: user reported CloudCC CRM embedded customer workbench AI assistant voice input shows “未识别到有效的语音内容”; suspected causes are missing cross-origin iframe microphone permission and ASR startup errors being overwritten by empty completion.
- TASK-175 is done: removed the “打开 CRM 客户主页” button and prevented browser-level right-side scrolling in both AgentCiCi and CloudCC CRM embedded customer workbench views.
- TASK-175 production evidence: release `2.3.3` carried the implementation, later release `2.3.4` includes it; authenticated Playwright at `https://x.agentcici.com/app?aiApp=customer-workbench` and `...&embed=crm` returned `documentScrollable=false`, `bodyScrollable=false`, `hasCrmHomeButton=false`, with internal workbench regions still locally scrollable.
- TASK-175 CloudCC evidence: pagecomponent `component-customer-workbench` published as id `6a50377ce4b0a577cbba1f86`, apiName `custc_202607YmKkL7PO`, version `9`; custom page `customer_interaction_workbench` updated to id `6a503a55e4b0a577cbba1f87`, `renderVersion=V3.0`, component ref id `6a50377ce4b0a577cbba1f86`.
- TASK-175 CloudCC operational note: `cloudcc bind pagecomponent` and object-array customPage update returned CloudCC `500`; saving succeeded only after sending service-required stringified `pageContent`.
- TASK-175 production browser screenshots: `output/playwright/task175-prod-platform-workbench-2.3.4.png`, `output/playwright/task175-prod-embed-workbench-2.3.4.png`.
- TASK-176 is done and production released: decoupled customer insight from data insight in release `2.3.4`.
- TASK-174 is done and production released: upgraded the existing customer insight AI app into “数据洞察”, added CRM data dashboard charts for leads, opportunities, customers, contract/order, sales performance, and kept no-data Mock fallback clearly labeled.
- TASK-174 CloudCC standard-catalog scan confirmed standard CRM objects including `Account`, `Contact`, `cloudcclead`, `Opportunity`, `contract`, `cloudccorder`, `product`, `Task`, and `Event`.
- TASK-174 local validation passed: task-scoped identity gate, assignment check, backend `CustomerInsightIntegrationTest`, frontend `npm run build`, `git diff --check`, and desktop Playwright visual/overflow check for `/app?aiApp=customer-insight`.
- TASK-174 dashboard source behavior: demo org `org2sva14i4udjmi2t4s` uses CRM-backed AgentCiCi aggregate rows and labels the source as real demo CRM data; other organizations use aggregate data when present, otherwise clearly labeled Mock display data.
- TASK-174 production release `2.3.2` passed: ACR image release, Git tag push, ECS backup, deployment, six-container health, `/system/version`, public smoke, authenticated dashboard API smoke, and production browser UI smoke.
- Current production backup: `/opt/cici/backups/20260710-072126-before-2.3.2-task174-data-insight`.
- Production browser evidence: `output/playwright/task174-prod-data-insight-2.3.2.png`; main panel `scrollWidth=clientWidth=1306`, dashboard offender count `0`.
- Known DNS risk remains: local workstation DNS cannot resolve `onechat.agentcici.com`; production-IP resolved smoke for `onechat.agentcici.com` returned HTTP 200.
- TASK-173 is done and production released: customer workbench right assistant no longer renders the crossed top voice bar or quick action button area.
- TASK-173 backend routes `/customer-workbench/assistant` through `ChatOrchestratorService.chat(...)` with `agentId=cici-system` and `activeSkillCode=customer-interaction-workbench`; response payload includes agent/model/run audit fields, and workbench session ids are stable 55-character ids within the `chat_session_state.session_id` limit.
- TASK-173 frontend microphone now reuses `useAsrVoiceInput` and `/ws/asr` with Aliyun provider instead of browser `SpeechRecognition`.
- TASK-173 validation passed locally and in production: focused backend tests, backend compile, frontend build, ASR hook tests, `git diff --check`, release dry-runs, production backups, production health checks, public smoke, authenticated demo-org smoke, and real `/customer-workbench/assistant` model call.
- Production release trace:
  - `2.2.12` was built and deployed for commit `82e32845ecc2`, but authenticated assistant smoke caught a `chat_session_state.session_id varchar(64)` overflow before closure.
  - Hotfix `2.3.1` was built, tagged, deployed, and verified for commit `ff9b9cc7cc4a`.
  - Current `/system/version`: `version=2.3.1`, `imageTag=2.3.1`, `gitCommit=ff9b9cc7cc4a`.
  - Current production backup: `/opt/cici/backups/20260710-065556-before-2.3.1-task173-session-id-hotfix`.
- TASK-172 created FEAT-082 and a reusable seed script for the dual demo environment.
- Confirmed target environments:
  - AgentCiCi org `org2sva14i4udjmi2t4s` = “智能体平台演示环境”.
  - CloudCC CRM org `org0720f814430017229`.
- CloudCC standard-catalog verified standard objects including `Account`, `Contact`, `cloudcclead`, `Opportunity`, `Task`, and `Event`.
- Seed script `scripts/seed-demo-environment.py` created/reused real CRM records for batch `TASK-172-DEMO-V1`: 10 accounts, 10 contacts, 6 leads, 10 opportunities, 10 tasks, and 20 events.
- AgentCiCi production PostgreSQL backup before local aggregate refresh: `/opt/cici/backups/20260709-153648-before-task172-demo-data`.
- AgentCiCi workbench aggregate tables for org `org2sva14i4udjmi2t4s` now contain 10 CRM-backed snapshots, 30 interaction events, and 20 recommendations; old `demo-account-xxx` workbench rows were removed for that org.
- Common demo login `13900009999` is now the single AgentCiCi member bound to the CloudCC demo user; the previous member binding was cleared to avoid SSO ambiguity.
- Verification passed: login to `https://x.agentcici.com` as `org2sva14i4udjmi2t4s / 13900009999` returns org name “智能体平台演示环境”; `/customer-workbench/accounts` returns 10 accounts with real CRM ids (`001...`) and no `demo-account` ids; account detail reports `crmConnection.ready=true`.

## Read Next

- `.claw/task-board.md` - compact index for live tasks.
- `.claw/tasks/TASK-178.md` - active CRM embedded workbench voice input hotfix task state.
- `.claw/assignments/TASK-178.yaml` - current authorized write scope.
- `docs/specs/FEAT-088-crm-workbench-voice-input-hotfix.md` - active hotfix spec.
- `.claw/tasks/TASK-175.md` - completed customer workbench scroll cleanup task state.
- `.claw/assignments/TASK-175.yaml` - current authorized write scope.
- `docs/specs/FEAT-085-customer-workbench-scroll-cleanup.md` - active feature spec.
- `.claw/tasks/TASK-173.md` - real assistant implementation and release task state.
- `.claw/assignments/TASK-173.yaml` - current authorized write scope.
- `docs/specs/FEAT-083-customer-workbench-real-agent-assistant.md` - real assistant feature spec.
- `.claw/test-report.md` - latest verified commands.
- `.claw/issue-list.md` - CloudCC skill and operations findings.
- `.claw/devops.md` - latest production release evidence.
