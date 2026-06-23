---
kind: current-status
version: 4
updated_at: 2026-06-23T00:48:00Z
updated_by: MANAGER-001
phase: maintenance
active_task: "Platform audit-log 500 is fixed locally on TASK-151 branch; production 2.1.1 still needs redeploy."
next_action: "Review, merge, and deploy the TASK-151 audit query fix; continue onechat.agentcici.com DNS NXDOMAIN follow-up."
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

- Current branch: `codex/TASK-151-rbac-production-readiness`; production is running release `2.1.1` from Git commit `17ec11b404a8`.
- Production release `2.1.1` was built with `./scripts/release-acr.sh`, pushed to ACR for backend/frontend, deployed to ECS `47.97.119.160`, and verified via `/system/version`.
- ECS backup before the release is `/opt/cici/backups/20260622-212252-before-2.1.1`, containing env, PostgreSQL dump, KB files, and Qdrant volume snapshots.
- Six production Compose containers are healthy on tag `2.1.1`; infra images were locally retagged on ECS for the shared Compose `CICI_IMAGE_TAG`.
- Public smoke: `https://x.agentcici.com/` returns `200`; server-local HTTPS vhost smoke for `onechat.agentcici.com` returns `200`, but direct DNS for `onechat.agentcici.com` returned NXDOMAIN from the current resolver and needs DNS follow-up.
- Core org login and APIs passed on `https://x.agentcici.com`; platform login and `/api/platform/skills` / `/api/platform/tools` passed.
- Known release follow-up: `/api/platform/audit/logs` returned backend `500` in production because the audit query applied `LIKE` to a bytea-bound nullable keyword value; TASK-151 now fixes this locally by using a no-keyword query path, but production still needs the fix deployed.
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
- On local `main`, combined backend integration and frontend production build pass after the merge and before the production release.
- TASK-158 focused unit test, backend test compilation, `OrchestratorIntegrationTest`, combined production-readiness backend integration, and frontend production build now pass.
- `OrchestratorIntegrationTest` fixtures were refreshed for current model routing, Agent readiness gate, persistent PostgreSQL test data, and RBAC grants.
- Production release source of truth remains `docs/production-release-runbook.md`; `scripts/release-acr.sh` owns numeric production versions and production-based beta test versions.

## Read Next

- `.claw/task-board.md` - compact index for live tasks.
- `.claw/tasks/TASK-156.md` - Agent Builder production readiness state.
- `.claw/tasks/TASK-157.md` - enterprise knowledge platform readiness state.
- `.claw/tasks/TASK-158.md` - Agent runtime concurrency hardening state.
- `docs/specs/FEAT-066-agent-builder-production-readiness.md` - Agent Builder production readiness spec.
- `docs/specs/FEAT-067-enterprise-knowledge-platform-readiness.md` - enterprise KB production readiness spec.
- `docs/specs/FEAT-068-agent-runtime-concurrency-hardening.md` - Agent runtime concurrency hardening spec.
- `.claw/tasks/TASK-151.md` - platform audit query fix state.
- `.claw/test-report.md` - latest verified commands.
