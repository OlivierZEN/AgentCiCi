---
kind: current-status
version: 4
updated_at: 2026-07-10T07:28:00+08:00
updated_by: MANAGER-001
phase: data-insight-ai-app-released
active_task: "TASK-174"
next_action: "Monitor production release 2.3.2 data insight dashboard and existing onechat DNS risk."
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

- Current branch: `main`; production is running release `2.3.2` from Git commit `d144149168ea`.
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
- `.claw/tasks/TASK-174.md` - data insight AI app production task state.
- `.claw/assignments/TASK-174.yaml` - current authorized write scope.
- `docs/specs/FEAT-084-data-insight-ai-app.md` - data insight feature spec.
- `.claw/tasks/TASK-173.md` - real assistant implementation and release task state.
- `.claw/assignments/TASK-173.yaml` - current authorized write scope.
- `docs/specs/FEAT-083-customer-workbench-real-agent-assistant.md` - real assistant feature spec.
- `.claw/test-report.md` - latest verified commands.
- `.claw/issue-list.md` - CloudCC skill and operations findings.
- `.claw/devops.md` - latest production release evidence.
