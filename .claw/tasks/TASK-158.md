---
kind: task-status
task_id: TASK-158
status: review
updated_at: 2026-06-22T02:00:26Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-158.yaml
spec_path: docs/specs/FEAT-068-agent-runtime-concurrency-hardening.md
---

# TASK-158 - Agent runtime concurrency hardening

## Scope

- 加固 Web 多用户和 OpenAPI 多客户端并发调用同一 Agent 的运行隔离。
- 覆盖同 session 串行、有界 async executor、runId、session state 乐观锁、基础限流和工具幂等键。
- 不新增移动端兼容实现。

## Initial Findings

- Agent 定义/版本是共享配置，每次调用会创建请求级 trace、RAG、工具和 workflow context。
- 不同用户和不同 session 正常隔离。
- 同一 session 并发请求目前没有显式串行队列或乐观锁，存在状态覆盖和回复顺序交错风险。
- Stream / OpenAPI async 当前存在默认 `CompletableFuture` 执行路径，需要改成有界 executor。

## Implementation Plan

- 创建 FEAT-068 和 TASK-158，并授权后端 runtime/concurrency 相关文件。
- 新增 session lock / limiter / bounded executor。
- 为 chat sync、chat stream blocking、OpenAPI async 路径接入 runId 和 executor。
- 为 `chat_session_state` 增加 optimistic lock version 迁移和实体字段。
- 增加 focused backend tests 覆盖核心并发语义。

## Verification

- `dev-login.py` for TASK-158 implementation files -> allowed.
- `mvn -q -Dmaven.repo.local=.m2 -Dtest=AgentRuntimeConcurrencyServiceTest test` in `backend/` -> success.
- `mvn -q -Dmaven.repo.local=.m2 -DskipTests test` in `backend/` -> success; main and test code compile.
- `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest test` in `backend/` -> failed on existing expectation drift:
  - `shouldRunChatWithRagAndToolsAndExposeOpsMetrics` expected `cici-default`, current routed model returned `qwen3.6-plus`.
  - `shouldKeepPublishedAgentPinnedToSkillVersionAfterSkillEdits` publish was blocked by the newer Agent readiness gate because the test Agent lacks a production entry.
  - The same run showed chat response now includes `runId` and workflow context includes `runId`, `publishedVersionId`, and `publishedVersionNo`.
- Updated `OrchestratorIntegrationTest` fixtures for current model routing, readiness gate, persistent PostgreSQL data, and RBAC grants.
- `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest test` in `backend/` -> success.
- `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest,AgentProductionReadinessIntegrationTest,KnowledgeBaseLifecycleIntegrationTest,AgentOpenApiIntegrationTest,AgentRuntimeConcurrencyServiceTest test` in `backend/` -> success.
- `npm run build` in `frontend/` -> success; existing Vite large chunk warning remains.
- `git diff --check` -> success.
- `check-assignment.py` for TASK-158 changed files -> allowed.

## Changed Files

- `docs/specs/FEAT-068-agent-runtime-concurrency-hardening.md`
- `.claw/tasks/TASK-158.md`
- `.claw/assignments/TASK-158.yaml`
- `.claw/task-board.md`
- `.claw/current-status.md`
- `.claw/test-report.md`
- `backend/src/main/java/com/codehouse/ciciassistant/agent/service/AgentRuntimeConcurrencyService.java`
- `backend/src/main/java/com/codehouse/ciciassistant/agent/service/AgentWorkflowRuntimeService.java`
- `backend/src/main/java/com/codehouse/ciciassistant/ai/config/AgentRuntimeAsyncConfig.java`
- `backend/src/main/java/com/codehouse/ciciassistant/ai/domain/ChatSessionStateEntity.java`
- `backend/src/main/java/com/codehouse/ciciassistant/ai/service/ChatOrchestratorService.java`
- `backend/src/main/java/com/codehouse/ciciassistant/openapi/service/AgentOpenApiRunService.java`
- `backend/src/main/resources/db/migration/V68__agent_runtime_concurrency_hardening.sql`
- `backend/src/test/java/com/codehouse/ciciassistant/ai/AgentRuntimeConcurrencyServiceTest.java`
- `backend/src/test/java/com/codehouse/ciciassistant/ai/OrchestratorIntegrationTest.java`

## Handoff

- Branch: `codex/TASK-156-production-readiness-goal`.
- Ready for merge to `main`.
- Implemented session serialization, bounded executor, runId propagation, optimistic session state versioning, lightweight concurrency limits, tool trace idempotency keys, and workflow version pinning metadata.
- Integration fixture drift is resolved; rerun integration gates after merging to `main` before pushing.
