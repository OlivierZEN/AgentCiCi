---
kind: task-status
task_id: TASK-167
status: review
updated_at: 2026-07-03T09:12:00+08:00
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-167.yaml
spec_path: docs/specs/FEAT-077-rag-router-policy.md
---

# TASK-167 - RAG 检索路由策略化改造

## Scope

- 将 `ChatOrchestratorService.shouldUseKnowledgeRetrieval(...)` 的零散关键词判断抽取为可观测的 RAG Router。
- 为触发/跳过知识库检索提供稳定 reason、category、term 和 policy version。
- 保留已修复的产品、部署类知识触发，以及闲聊/普通业务工具查询跳过行为。

## Plan

- 建立 FEAT-077 规格和 TASK-167 授权。
- 先补 RED 测试覆盖路由原因、知识意图类别、负向业务工具意图和企业微信客服策略。
- 实现独立 `KnowledgeRetrievalRouter`，并让 `ChatOrchestratorService` 使用路由决策。
- 将 RAG trace metadata 写入路由原因，便于后续线上排查。
- 运行 focused 后端测试、编译和静态检查。

## Verification

- `dev-login.py` for `MANAGER-001` before TASK-167 assignment creation -> allowed.
- `dev-login.py` for `MANAGER-001` / `TASK-167` covering router, orchestrator, focused tests, task, and spec files -> allowed.
- `check-assignment.py` for TASK-167 intended implementation files -> allowed.
- RED: `mvn test -Dtest=KnowledgeRetrievalRouterTest,ChatOrchestratorServiceModelIdentityTest -DskipITs` in `backend/` -> failed as expected because `KnowledgeRetrievalRouter` and decision metadata API did not exist.
- GREEN: `mvn test -Dtest=KnowledgeRetrievalRouterTest,ChatOrchestratorServiceModelIdentityTest -DskipITs` in `backend/` -> success, 34 tests passed.
- `mvn -DskipTests compile` in `backend/` -> success.
- `git diff --check` -> success.

## Changed Files

- `backend/src/main/java/com/codehouse/ciciassistant/ai/service/KnowledgeRetrievalRouter.java`
- `backend/src/main/java/com/codehouse/ciciassistant/ai/service/ChatOrchestratorService.java`
- `backend/src/test/java/com/codehouse/ciciassistant/ai/service/KnowledgeRetrievalRouterTest.java`
- `backend/src/test/java/com/codehouse/ciciassistant/ai/service/ChatOrchestratorServiceModelIdentityTest.java`
- `docs/specs/FEAT-077-rag-router-policy.md`
- `.claw/tasks/TASK-167.md`
- `.claw/assignments/TASK-167.yaml`
- `.claw/task-board.md`
- `.claw/current-status.md`
- `.claw/test-report.md`

## Handoff

- Branch: `codex/TASK-167-rag-router-policy`.
- This task is an architectural hardening follow-up after production releases `2.1.9` and `2.1.10`.
- Implementation is ready for review. It has not been merged to `main` or released to production in this task.
