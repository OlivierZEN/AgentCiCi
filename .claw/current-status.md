---
kind: current-status
version: 4
updated_at: 2026-07-10T00:15:08+08:00
updated_by: MANAGER-001
phase: customer-workbench-real-agent-review
active_task: "TASK-173 客户互动工作台真实智能体助理"
next_action: "Review TASK-173 and decide whether to publish a production release; production deployment has not been performed."
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

- Current branch: `main`; production is running release `2.2.11` from Git commit `d251a2661602`.
- TASK-173 implementation is ready for review: customer workbench right assistant no longer renders the crossed top voice bar or quick action button area.
- TASK-173 backend routes `/customer-workbench/assistant` through `ChatOrchestratorService.chat(...)` with `agentId=cici-system` and `activeSkillCode=customer-interaction-workbench`; response payload includes agent/model/run audit fields.
- TASK-173 frontend microphone now reuses `useAsrVoiceInput` and `/ws/asr` with Aliyun provider instead of browser `SpeechRecognition`.
- TASK-173 validation passed: `mvn -q -f backend/pom.xml -DskipTests compile`, `CustomerWorkbenchServiceTest`, `OrchestratorIntegrationTest#shouldExposeCloudccDiscoveryToolsForDefaultCiciAgent`, `npm --prefix frontend run build`, `npm --prefix frontend run test -- useAsrVoiceInput`, `git diff --check`, and desktop visual check at 1440x900.
- Production deployment for TASK-173 has not been performed.
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
- `.claw/tasks/TASK-173.md` - real assistant implementation task state.
- `.claw/assignments/TASK-173.yaml` - current authorized write scope.
- `docs/specs/FEAT-083-customer-workbench-real-agent-assistant.md` - real assistant feature spec.
- `.claw/test-report.md` - latest verified commands.
- `.claw/issue-list.md` - CloudCC skill and operations findings.
- `.claw/devops.md` - latest production release evidence.
