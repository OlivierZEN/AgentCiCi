---
kind: current-status
version: 4
updated_at: 2026-06-26T04:20:00Z
updated_by: MANAGER-001
phase: maintenance
active_task: "TASK-159 production chat hotfix is in progress for 2.1.3 chat_session_state primary-key failures."
next_action: "Fix chat_session_state tenant primary key, run focused backend validation, then prepare a production hotfix release."
read_next:
  goals: false
  decisions: false
  issue_list: false
  task_board: true
  active_task_status: true
  test_report: true
  devops: false
---

# Project Current Status

`current-status.md` is the hot index. Rewrite it as the latest snapshot; do not append session history.

## Snapshot

- Current branch: `codex/TASK-159-chat-session-state-tenant-key-hotfix`; production is running release `2.1.3` from Git commit `916ee5f48d7a`.
- TASK-159 is active: production `2.1.3` chat failed at `2026-06-26 12:12:12 CST` with `duplicate key value violates unique constraint "chat_session_state_pkey"` for `session_id=workbench:cici-system`.
- Production log root cause is table-model drift: application reads session state by `session_id + org_id`, but `chat_session_state` primary key is only `session_id`; the same workbench session id is reused across orgs.
- Planned hotfix: append a Flyway migration changing `chat_session_state` primary key to `(session_id, org_id)`, update the JPA entity to a composite id, and add focused backend coverage.
- TASK-144 public website feedback is live: homepage/Solutions hero no longer shows the screenshot-marked `预约演示 / SkillsHub / 登录` button group.
- Public demo booking now posts real records through `/api/autoservice/demo-requests`; the operations console `/platform/website-leads` can query the submitted records.
- Release `2.1.3` was built and pushed with `./scripts/release-acr.sh --version 2.1.3`; Git tag `2.1.3`, backend image, frontend image, `CICI_IMAGE_TAG`, and `/system/version` all use `2.1.3`.
- ECS backup before the release is `/opt/cici/backups/20260624-111422-before-2.1.3`, containing env, PostgreSQL dump, KB files, and Qdrant volume snapshots.
- Six production Compose containers are healthy on tag `2.1.3`; backend `/actuator/health` is `UP`, `/system/version` returns `version=2.1.3`, `imageTag=2.1.3`, `gitCommit=916ee5f48d7a`.
- Production smoke passed on `https://x.agentcici.com/`; `onechat.agentcici.com` still has the pre-existing DNS NXDOMAIN from this resolver, while explicit production-IP HTTPS resolve returns `200`.
- Core org APIs, platform APIs, `/api/platform/audit/logs?limit=100`, and production browser demo-booking validation passed. Production test lead record: `id=8`, status `NEW`, sourcePath `/global/docs`.
- Screenshots: `output/playwright/release-2.1.3-public-home.png`, `output/playwright/release-2.1.3-demo-submit.png`, `output/playwright/release-2.1.3-platform-website-leads.png`.
- Known follow-up: `AutoServiceDemoRequestIntegrationTest` still uses an organization token for a platform endpoint and fails with expected `403`; runtime platform-account validation passed.
- User opened a goal to finish two production-readiness tracks: Agent Builder production closure and enterprise knowledge platform readiness.
- TASK-156 added Agent production readiness checks, `GET /agents/{agentId}/readiness`, a publish-time readiness gate, a minimal Agent evaluation gate with suites/cases/runs/results, and Agent Builder publish-tab production gate UX.
- Local Docker/PostgreSQL blocker is resolved: `postgres`, `redis`, `rabbitmq`, and `qdrant` are running; `agentcici_test` is reachable on `localhost:5432`.
- TASK-156 focused backend integration now passes; a Spring circular dependency found during the first rerun was fixed in `AgentEvaluationService`.
- TASK-156 authenticated real-backend desktop validation passed on Agent Builder publish tab: readiness/evaluation endpoints returned 200, refresh/sync actions stayed error-free, blockers were 0, and screenshot evidence is in `output/playwright/task156-agent-builder-real-backend-production-gate.png`.
- TASK-157 first implementations added PDFBox text-based PDF parsing, document/chunk ACL, RAG/Chat permission filtering, drift audit/repair including embedding metadata comparison, RAG citation trust fields, retrieval evaluation backend model/API/metrics, and connector sync skeleton.
- TASK-157 `KnowledgeBaseLifecycleIntegrationTest` now passes locally against PostgreSQL/Flyway schema version 67.
- TASK-157 authenticated `/admin/kb` desktop validation passed for list and detail pages, with screenshot evidence in `output/playwright/task157-kb-real-backend-desktop.png` and `output/playwright/task157-kb-detail-real-backend-desktop.png`.
- TASK-157 real Qdrant smoke passed after correcting local collection dimension drift and enabling Rabbit listeners: MQ indexing consumed, Qdrant upsert produced a point, vector retrieval hit, audit returned OK, and delete removed the point.
- TASK-158 added session-level runtime serialization, bounded Agent runtime executor, runId propagation, `chat_session_state` optimistic locking, lightweight runtime concurrency limits, tool trace idempotency keys, and workflow version metadata.
- The latest `main` was pushed before the production release; combined backend integration, frontend production build, and production smoke passed for the release path.
- TASK-158 focused unit test, backend test compilation, `OrchestratorIntegrationTest`, combined production-readiness backend integration, and frontend production build now pass.
- `OrchestratorIntegrationTest` fixtures were refreshed for current model routing, Agent readiness gate, persistent PostgreSQL test data, and RBAC grants.
- Production release source of truth remains `docs/production-release-runbook.md`; `scripts/release-acr.sh` owns numeric production versions and production-based beta test versions.

## Read Next

- `.claw/task-board.md` - compact index for live tasks.
- `.claw/tasks/TASK-159.md` - production chat session state hotfix.
- `docs/specs/FEAT-069-chat-session-state-tenant-key-hotfix.md` - hotfix spec.
- `.claw/tasks/TASK-144.md` - public website and demo booking release state.
- `docs/specs/FEAT-061-agentcici-public-website-restructure.md` - public website spec.
- `.claw/test-report.md` - latest verified commands.
