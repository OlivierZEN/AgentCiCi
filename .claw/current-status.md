---
kind: current-status
version: 4
updated_at: 2026-06-22T02:00:26Z
updated_by: MANAGER-001
phase: maintenance
active_task: "TASK-156/TASK-157/TASK-158 production-readiness changes are validated and ready to merge to main."
next_action: "Commit local changes, merge codex/TASK-156-production-readiness-goal into main, rerun integration gates on main, then push origin/main."
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

- Current branch: `codex/TASK-156-production-readiness-goal`; user requested commit, merge to `main`, integration test, and push `origin/main`.
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
- `.claw/test-report.md` - latest verified commands.
