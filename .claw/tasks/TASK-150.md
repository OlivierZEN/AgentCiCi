---
kind: task-status
task_id: TASK-150
assignee: MANAGER-001
owner_role: fullstack-agent
status: review
branch: codex/TASK-149-kb-docx-upload-parser
pr_url: n/a
spec_path: docs/specs/FEAT-008-knowledge-base-lifecycle-completion.md
assignment_path: .claw/assignments/TASK-150.yaml
updated_at: 2026-06-02T23:20:41Z
updated_by: MANAGER-001
---

# TASK-150 Knowledge Base Production Readiness

## Scope

完成 FEAT-008 2026-06-02 缺口复盘里的生产就绪缺口：上传准入、PDF 策略、Qdrant 可验证性、管理端弹窗治理、运行时 metadata filter、结构化引用归因、数据源/API 安全扩展点。

## Plan

- 后端：集中上传限制与 parser 错误映射；PDF 明确策略；Qdrant audit/smoke hook；RAG 返回结构化 source；运行时 metadata filter 契约。
- 前端：管理端知识库删除、重命名、metadata、chunk 编辑等原生 confirm/prompt 替换为项目 modal。
- 规格与状态：更新 FEAT-008、任务板、测试报告和当前状态。

## Verification Target

- `mvn -q -Dmaven.repo.local=.m2 -Dtest=KnowledgeBaseLifecycleIntegrationTest test`
- `npm run build`
- `git diff --check`
- `/admin/kb` 桌面端浏览器 smoke，如本地服务可用。

## Progress

- 2026-06-02T08:11:08Z: Task opened and assigned to `MANAGER-001` after user requested production-ready completion of FEAT-008 gaps.
- 2026-06-02T13:31:00Z: Implemented production-readiness gap closure across backend KB APIs, runtime RAG contract, admin KB UX, focused integration coverage, and FEAT-008/test state.
- 2026-06-02T23:20:41Z: Resolved local PostgreSQL blocker by starting Docker Desktop and `cici-postgres`; focused KB lifecycle integration test now passes.

## Verification

- `npm run build` in `frontend/` -> **success**; existing Vite large chunk warning remains.
- `mvn -q -Dmaven.repo.local=.m2 -DskipTests test` in `backend/` -> **success**, backend main/test compile gate passed.
- `mvn -q -Dmaven.repo.local=.m2 -Dtest=KnowledgeBaseLifecycleIntegrationTest test` in `backend/` -> **success** after starting local Docker PostgreSQL; Flyway connected to `agentcici_test` and confirmed schema up to date.
- `./scripts/verify-qdrant-stack.sh` -> **success** after starting `cici-qdrant`; smoke collection create/upsert/filter-search passed.
- Playwright desktop smoke on `http://127.0.0.1:4179/admin/kb` with mocked admin/KB APIs -> **success**; upload policy, runtime status panel, vector audit result, and console error checks passed.
- `check-assignment.py` from the loaded `cc-aidev-guidelines-common` skill package -> **allowed** for representative TASK-150 files.

## Changed Files

- `backend/src/main/java/com/codehouse/ciciassistant/kb/domain/KbChunkRepository.java`
- `backend/src/main/java/com/codehouse/ciciassistant/kb/service/KnowledgeBaseService.java`
- `backend/src/main/java/com/codehouse/ciciassistant/kb/api/KnowledgeBaseController.java`
- `backend/src/main/java/com/codehouse/ciciassistant/ai/service/RagService.java`
- `backend/src/main/java/com/codehouse/ciciassistant/ai/service/ChatOrchestratorService.java`
- `backend/src/main/java/com/codehouse/ciciassistant/ai/api/ChatController.java`
- `backend/src/main/java/com/codehouse/ciciassistant/ai/service/AgentRunTraceService.java`
- `backend/src/test/java/com/codehouse/ciciassistant/kb/KnowledgeBaseLifecycleIntegrationTest.java`
- `frontend/src/admin/pages/AdminKnowledgePage.tsx`
- `frontend/src/styles.css`
- `docs/specs/FEAT-008-knowledge-base-lifecycle-completion.md`
- `.claw/task-board.md`
- `.claw/current-status.md`
- `.claw/test-report.md`
