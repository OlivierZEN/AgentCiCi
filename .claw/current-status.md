---
kind: current-status
version: 4
updated_at: 2026-06-26T06:36:00Z
updated_by: MANAGER-001
phase: maintenance
active_task: "TASK-162 continuous email-body task execution fix is locally validated on branch codex/TASK-162-continuous-email-tool-execution."
next_action: "Merge TASK-162 to main and publish a production hotfix release."
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

- Current branch: `main`; production is running release `2.1.5` from Git commit `947e47ddbe5a`.
- TASK-162 is locally validated on `codex/TASK-162-continuous-email-tool-execution`: after `2.1.5`, user confirmed “是的” to expand the only email, but assistant only said “让我读取正文内容” and stopped without calling `email_get_message`.
- TASK-162 fixes: `email_search` unique hits now persist `pending_email_message_id`; confirmation turns like “是的/好的/继续/展开正文” automatically execute `email_get_message` before final generation; “让我读取/查看/打开” is treated as an unfinished tool promise.
- TASK-162 verification passed: task-scoped identity/assignment checks, `ChatOrchestratorServiceModelIdentityTest`, backend compile, and `git diff --check`.
- TASK-161 is deployed in `2.1.5`: user reported that asking to view a matched email did not display body content, and voice input ended with `未识别到有效语音内容`.
- TASK-161 fixes: chat tool-planning no longer treats a single successful `email_search` as complete when the user asked for email body/content/detail; the stop prompt explicitly requires `email_get_message` when only `messageId` is available; ASR frontend now normalizes common transcript fields and waits 1.5s before closing the websocket after stop.
- TASK-161 verification passed: task-scoped identity/assignment checks, `ChatOrchestratorServiceModelIdentityTest`, `useAsrVoiceInput.test.ts`, backend compile, frontend build, and `git diff --check`.
- Release `2.1.5` was built and pushed with `./scripts/release-acr.sh --version 2.1.5`; Git tag `2.1.5`, backend image, frontend image, `CICI_IMAGE_TAG`, and `/system/version` all use `2.1.5`.
- ECS backup before the release is `/opt/cici/backups/20260626-135931-before-2.1.5`, containing env, PostgreSQL dump, KB files, and Qdrant volume snapshots.
- Six production Compose containers are healthy on tag `2.1.5`; backend `/actuator/health` is `UP`, `/system/version` returns `version=2.1.5`, `imageTag=2.1.5`, `gitCommit=947e47ddbe5a`; frontend Nginx config passed; public `https://x.agentcici.com/` returned `200`; `/auth/me` returned expected `401`; recent backend error scan is empty.
- TASK-159 is deployed: production `2.1.3` chat failed at `2026-06-26 12:12:12 CST` with `duplicate key value violates unique constraint "chat_session_state_pkey"` for `session_id=workbench:cici-system`.
- Root cause was table-model drift: application reads session state by `session_id + org_id`, but `chat_session_state` primary key was only `session_id`; the same workbench session id is reused across orgs.
- Hotfix `2.1.4` applied V69, changing `chat_session_state` primary key to `(session_id, org_id)`, and updated JPA to a composite id.
- Production backup before deploy: `/opt/cici/backups/20260626-124138-before-2.1.4`.
- Release note: ACR push for `2.1.4` blocked twice at registry push/manifest; to restore service, backend/frontend images were built locally on ECS with tag `2.1.4`, infra images were locally tagged as `2.1.4`, Git tag `2.1.4` was pushed, and `/opt/cici/deploy/acr.env` now uses `CICI_IMAGE_TAG=2.1.4` and `CICI_APP_VERSION=2.1.4`.
- Production verification passed: six containers healthy, `/system/version` returns `version=2.1.4`, `imageTag=2.1.4`, `gitCommit=d40d53d0a228`; Flyway latest row is `69|chat session state tenant primary key|true`; primary key columns are `session_id, org_id`; real `/ai/chat` smoke returned 200; no new `chat_session_state_pkey` logs after deploy.
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
- `.claw/tasks/TASK-162.md` - current continuous email-body tool execution fix.
- `docs/specs/FEAT-072-continuous-tool-execution-confirmation.md` - current task spec.
- `.claw/tasks/TASK-161.md` - current mail body and voice input fix.
- `docs/specs/FEAT-071-mail-body-and-voice-input-fix.md` - current task spec.
- `.claw/tasks/TASK-159.md` - production chat session state hotfix.
- `docs/specs/FEAT-069-chat-session-state-tenant-key-hotfix.md` - hotfix spec.
- `.claw/tasks/TASK-144.md` - public website and demo booking release state.
- `docs/specs/FEAT-061-agentcici-public-website-restructure.md` - public website spec.
- `.claw/test-report.md` - latest verified commands.
