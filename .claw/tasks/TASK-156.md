---
kind: task-status
task_id: TASK-156
status: review
updated_at: 2026-06-21T15:30:34Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-156.yaml
spec_path: docs/specs/FEAT-066-agent-builder-production-readiness.md
---

# TASK-156 - Agent Builder production readiness closure

## Scope

- 收口 Agent Builder 的生产闭环：readiness gate、最小评测门禁、发布证据、运行入口、观测和前端发布体验。
- 以 FEAT-066 为事实源，优先交付 P0 发布阻塞和最小评测能力。
- 不新增移动端兼容实现。

## Initial Findings

- Agent Builder 已有创建、保存、编译、版本、发布、回滚、权限、Open API、触发器和执行记录。
- 当前发布路径主要是切换 `publishedVersionId`，缺少统一 readiness gate 和评测阻塞。
- FEAT-031 评测系统仍是 designed，未成为发布流程事实。

## Implementation Plan

- 创建 FEAT-066 并登记任务边界。
- 后端 readiness summary、发布 gate 和最小 evaluation 数据模型/断言先收口。
- 前端随后接入发布检查清单、评测入口和发布证据摘要。
- 最后跑后端集成测试、前端 build、`git diff --check` 和桌面截图。

## Verification

- `dev-login.py .claw --developer MANAGER-001 --task TASK-156 --branch codex/TASK-156-production-readiness-goal --files ...` -> allowed.
- `check-assignment.py .claw --developer MANAGER-001 --task TASK-156 --branch codex/TASK-156-production-readiness-goal --files ...` -> allowed.
- `cd backend && mvn -q -Dmaven.repo.local=.m2 -DskipTests compile` -> success.
- `cd backend && mvn -q -Dmaven.repo.local=.m2 -Dtest=AgentProductionReadinessIntegrationTest test` -> blocked by local PostgreSQL connection refused on `localhost:5432`; Docker daemon was not running, so `docker compose up -d postgres` could not start the dependency.
- `cd backend && mvn -q -Dmaven.repo.local=.m2 -DskipTests test` -> success; main and test code compile.
- `git diff --check` -> success.
- `dev-login.py` rerun with TASK-156 assignment scope update from V63 to V67 migration -> allowed.
- `dev-login.py` / `check-assignment.py` rerun for TASK-156 evaluation gate files including V67 migration, eval domain/repositories, `AgentEvaluationService`, runtime/readiness/controller, and integration test -> allowed.
- `cd backend && mvn -q -Dmaven.repo.local=.m2 -DskipTests compile` -> success.
- `cd backend && mvn -q -Dmaven.repo.local=.m2 -DskipTests test` -> success; main and test code compile with minimal evaluation gate changes.
- `cd backend && mvn -q -Dmaven.repo.local=.m2 -Dtest=AgentProductionReadinessIntegrationTest test` -> blocked by local PostgreSQL connection refused on `localhost:5432`; Spring/Flyway could not create the test ApplicationContext.
- `git diff --check` -> success.
- `check-assignment.py` for TASK-156 changed files -> allowed.
- `dev-login.py` rerun for TASK-156 frontend production gate UX files and status docs -> allowed.
- `cd frontend && npm run build` -> success; existing Vite large chunk warning remains.
- Desktop Playwright with mocked admin auth/API on `http://127.0.0.1:5173/admin/agent-builder/support-agent` -> success; screenshot `output/playwright/task156-agent-builder-production-gate.png`, `overflowX=false`, production gate/evaluation gate/blocker text present.
- `docker compose up -d --remove-orphans postgres redis rabbitmq qdrant` -> success; local PostgreSQL/Redis/RabbitMQ healthy and Qdrant running.
- `docker exec cici-postgres pg_isready -U cici -d agentcici_test` -> success; `localhost:5432` and `localhost:6333` reachable.
- `cd backend && mvn -q -Dmaven.repo.local=.m2 -Dtest=AgentProductionReadinessIntegrationTest test` -> initially failed with a Spring circular dependency across readiness/evaluation/runtime services; fixed by making `AgentEvaluationService` lazily obtain `AgentWorkflowRuntimeService`.
- `cd backend && mvn -q -Dmaven.repo.local=.m2 -Dtest=AgentProductionReadinessIntegrationTest test` -> success after the lazy runtime fix.
- `git diff --check` -> success.
- `check-assignment.py` for TASK-156 changed files -> allowed.
- `cd backend && mvn -q -Dmaven.repo.local=.m2 -Dtest=AgentProductionReadinessIntegrationTest test` -> success after TASK-157 Rabbit listener fix.
- `cd frontend && npm run build` -> success; existing Vite large chunk warning remains.
- Local backend `mvn -Dmaven.repo.local=.m2 spring-boot:run -Dspring-boot.run.profiles=local` -> success on port 8080 with PostgreSQL/Flyway schema v67 and RabbitMQ connection.
- Real login API `POST /auth/password/login` with `13900009999 / szyd1234` -> success, `ORG_ADMIN`.
- Authenticated desktop Playwright on `http://127.0.0.1:5173/admin/agent-builder/support-agent` -> success; screenshot `output/playwright/task156-agent-builder-real-backend-production-gate.png`, `overflowX=false`, console errors 0.
- Agent Builder production gate backend calls returned 200: `/agents/cici-system/readiness?versionNo=47`, `/agents/cici-system/evaluation/suites`; `刷新检查` and `同步评测` actions stayed error-free.

## Changed Files

- `docs/specs/FEAT-066-agent-builder-production-readiness.md`
- `.claw/tasks/TASK-156.md`
- `.claw/assignments/TASK-156.yaml`
- `.claw/task-board.md`
- `.claw/current-status.md`
- `.claw/test-report.md`
- `backend/src/main/java/com/codehouse/ciciassistant/agent/api/AgentDefinitionController.java`
- `backend/src/main/resources/db/migration/V67__agent_evaluation_gate.sql`
- `backend/src/main/java/com/codehouse/ciciassistant/agent/domain/AgentEvalSuiteEntity.java`
- `backend/src/main/java/com/codehouse/ciciassistant/agent/domain/AgentEvalSuiteRepository.java`
- `backend/src/main/java/com/codehouse/ciciassistant/agent/domain/AgentEvalCaseEntity.java`
- `backend/src/main/java/com/codehouse/ciciassistant/agent/domain/AgentEvalCaseRepository.java`
- `backend/src/main/java/com/codehouse/ciciassistant/agent/domain/AgentEvalRunEntity.java`
- `backend/src/main/java/com/codehouse/ciciassistant/agent/domain/AgentEvalRunRepository.java`
- `backend/src/main/java/com/codehouse/ciciassistant/agent/domain/AgentEvalCaseResultEntity.java`
- `backend/src/main/java/com/codehouse/ciciassistant/agent/domain/AgentEvalCaseResultRepository.java`
- `backend/src/main/java/com/codehouse/ciciassistant/agent/service/AgentEvaluationService.java`
- `backend/src/main/java/com/codehouse/ciciassistant/agent/service/AgentDefinitionService.java`
- `backend/src/main/java/com/codehouse/ciciassistant/agent/service/AgentProductionReadinessService.java`
- `backend/src/main/java/com/codehouse/ciciassistant/agent/service/AgentWorkflowRuntimeService.java`
- `backend/src/test/java/com/codehouse/ciciassistant/agent/AgentProductionReadinessIntegrationTest.java`
- `frontend/src/assistant/AgentBuilderShell.tsx`
- `frontend/src/assistant/cici-ui.css`

## Handoff

- Branch: `codex/TASK-156-production-readiness-goal`.
- Backend readiness gate is implemented and focused integration-verified against local PostgreSQL.
- Minimal evaluation gate backend is implemented and compile-verified: suites, cases, runs, results, deterministic assertions, candidate-version evaluation runtime marker, readiness summary, and publish-time blocking.
- Agent Builder publish tab now shows production readiness checks, blocker/warning counts, evaluation suite/run status, P0 case creation, evaluation run action, and pre-publish readiness refresh.
- Focused integration test covers P0 evaluation failure blocking publish and now passes locally.
- Mocked and real-backend desktop browser validation both passed for the frontend production gate layout.
- Ready for review: Agent Builder production readiness gate, minimal evaluation gate, publish-tab UX, focused backend integration, frontend build, and authenticated desktop validation are complete.
- Note: the existing `cici-system` demo Agent shows a readiness warning because no evaluation suite is configured, but blockers are 0; blocking behavior is covered by `AgentProductionReadinessIntegrationTest`.
