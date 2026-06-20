---
kind: task-status
task_id: TASK-157
status: in_progress
updated_at: 2026-06-20T16:40:13Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-157.yaml
spec_path: docs/specs/FEAT-067-enterprise-knowledge-platform-readiness.md
---

# TASK-157 - Enterprise knowledge platform readiness

## Scope

- 把知识库从 FEAT-008 维护期推进到企业知识平台生产就绪。
- 覆盖多格式稳定解析、连接器同步、文档/片段级权限、召回评测、引用可信度、重建索引与漂移检查。
- 不新增移动端兼容实现。

## Initial Findings

- FEAT-008 已完成 P0 生命周期、向量删除、切片设置、检索测试、metadata、运行时 filter、结构化来源和上传准入。
- 当前缺口集中在 PDF/parser 稳定性、真实 Qdrant 验证、文档/chunk ACL、drift audit/repair、KB 评测、连接器同步和引用可信度。

## Implementation Plan

- 创建 FEAT-067 并登记任务边界。
- 先实现 P0 parser/PDF、ACL、drift/rebuild 和真实 Qdrant smoke。
- 再推进 retrieval evaluation、source confidence 和 connector sync skeleton。
- 每一批都扩展 `KnowledgeBaseLifecycleIntegrationTest`，并对 `/admin/kb` 做桌面端检查。

## Verification

- `dev-login.py .claw --developer MANAGER-001 --task TASK-157 --branch codex/TASK-156-production-readiness-goal --files ...` -> allowed.
- `check-assignment.py .claw --developer MANAGER-001 --task TASK-157 --branch codex/TASK-156-production-readiness-goal --files ...` -> allowed.
- `dev-login.py` / `check-assignment.py` rerun after adding `backend/pom.xml` to assignment scope -> allowed.
- `cd backend && mvn -q -Dmaven.repo.local=.m2 -DskipTests test` -> success; main and test code compile with PDFBox.
- `git diff --check` -> success.
- `dev-login.py` / `check-assignment.py` rerun for TASK-157 ACL files including V63 migration, KB domain/service/controller, `RagService`, `ChatOrchestratorService`, and integration test -> allowed.
- `cd backend && mvn -q -Dmaven.repo.local=.m2 -DskipTests test` -> success; main and test code compile with ACL changes.
- `dev-login.py` / `check-assignment.py` rerun for TASK-157 drift audit files -> allowed.
- `cd backend && mvn -q -Dmaven.repo.local=.m2 -DskipTests test` -> success; main and test code compile with drift audit changes.
- `dev-login.py` / `check-assignment.py` rerun for TASK-157 citation trust files -> allowed.
- `cd backend && mvn -q -Dmaven.repo.local=.m2 -DskipTests test` -> success; main and test code compile with citation trust fields.
- `dev-login.py` / `check-assignment.py` rerun for TASK-157 retrieval evaluation files, V64 migration, and tenant lifecycle purge integration -> allowed.
- `cd backend && mvn -q -Dmaven.repo.local=.m2 -DskipTests test` -> success; main and test code compile with retrieval evaluation changes.
- `git diff --check` -> success.
- `docker ps` -> blocked; Docker daemon socket `/Users/owenmacbook/.docker/run/docker.sock` is unavailable.
- `nc -z localhost 5432` -> `postgres-closed`; focused integration tests still cannot run locally.
- `dev-login.py` / `check-assignment.py` rerun for TASK-157 connector sync files and V65 migration -> allowed.
- `cd backend && mvn -q -Dmaven.repo.local=.m2 -DskipTests test` -> success; main and test code compile with connector sync changes.
- `git diff --check` -> success.
- `dev-login.py` / `check-assignment.py` rerun for TASK-157 embedding metadata drift files and V66 migration -> allowed.
- `cd backend && mvn -q -Dmaven.repo.local=.m2 -DskipTests test` -> success; main and test code compile with embedding metadata drift changes.
- `git diff --check` -> success.

## Changed Files

- `docs/specs/FEAT-067-enterprise-knowledge-platform-readiness.md`
- `.claw/tasks/TASK-157.md`
- `.claw/assignments/TASK-157.yaml`
- `.claw/task-board.md`
- `.claw/current-status.md`
- `.claw/test-report.md`
- `backend/pom.xml`
- `backend/src/main/resources/db/migration/V63__kb_document_chunk_acl.sql`
- `backend/src/main/java/com/codehouse/ciciassistant/kb/domain/KbAccessGrantEntity.java`
- `backend/src/main/java/com/codehouse/ciciassistant/kb/domain/KbAccessGrantRepository.java`
- `backend/src/main/java/com/codehouse/ciciassistant/kb/domain/KbDocumentRepository.java`
- `backend/src/main/resources/db/migration/V64__kb_retrieval_evaluation.sql`
- `backend/src/main/java/com/codehouse/ciciassistant/kb/domain/KbEvalSuiteEntity.java`
- `backend/src/main/java/com/codehouse/ciciassistant/kb/domain/KbEvalSuiteRepository.java`
- `backend/src/main/java/com/codehouse/ciciassistant/kb/domain/KbEvalCaseEntity.java`
- `backend/src/main/java/com/codehouse/ciciassistant/kb/domain/KbEvalCaseRepository.java`
- `backend/src/main/java/com/codehouse/ciciassistant/kb/domain/KbEvalRunEntity.java`
- `backend/src/main/java/com/codehouse/ciciassistant/kb/domain/KbEvalRunRepository.java`
- `backend/src/main/java/com/codehouse/ciciassistant/kb/domain/KbEvalCaseResultEntity.java`
- `backend/src/main/java/com/codehouse/ciciassistant/kb/domain/KbEvalCaseResultRepository.java`
- `backend/src/main/resources/db/migration/V65__kb_connector_sync.sql`
- `backend/src/main/java/com/codehouse/ciciassistant/kb/domain/KbDataSourceEntity.java`
- `backend/src/main/java/com/codehouse/ciciassistant/kb/domain/KbDataSourceRepository.java`
- `backend/src/main/java/com/codehouse/ciciassistant/kb/domain/KbSyncJobEntity.java`
- `backend/src/main/java/com/codehouse/ciciassistant/kb/domain/KbSyncJobRepository.java`
- `backend/src/main/java/com/codehouse/ciciassistant/kb/domain/KbSourceDocumentMapEntity.java`
- `backend/src/main/java/com/codehouse/ciciassistant/kb/domain/KbSourceDocumentMapRepository.java`
- `backend/src/main/resources/db/migration/V66__kb_chunk_embedding_metadata.sql`
- `backend/src/main/java/com/codehouse/ciciassistant/kb/domain/KbChunkEntity.java`
- `backend/src/main/java/com/codehouse/ciciassistant/kb/service/KbAccessControlService.java`
- `backend/src/main/java/com/codehouse/ciciassistant/kb/service/KnowledgeBaseService.java`
- `backend/src/main/java/com/codehouse/ciciassistant/kb/api/KnowledgeBaseController.java`
- `backend/src/main/java/com/codehouse/ciciassistant/platform/service/PlatformTenantLifecycleService.java`
- `backend/src/main/java/com/codehouse/ciciassistant/ai/service/RagService.java`
- `backend/src/main/java/com/codehouse/ciciassistant/ai/service/ChatOrchestratorService.java`
- `backend/src/test/java/com/codehouse/ciciassistant/kb/KnowledgeBaseLifecycleIntegrationTest.java`

## Handoff

- Branch: `codex/TASK-156-production-readiness-goal`.
- Text-based PDF parser support is implemented and compile-verified.
- Document/chunk ACL data model, management API, RAG filtering, Chat principal propagation, and permission-filtered trace count are implemented and compile-verified.
- Drift audit/repair endpoint is implemented and compile-verified; embedding drift remains explicitly not available until chunk embedding metadata is persisted.
- Citation trust fields are exposed in RAG source payloads and compile-verified.
- Retrieval evaluation backend model, API, metrics, evidence persistence, and tenant purge coverage are implemented and compile-verified.
- Connector sync backend skeleton, WEB/EXTERNAL_API minimal sync path, sync jobs, source-document mapping, and tenant purge coverage are implemented and compile-verified.
- Embedding metadata is persisted on chunks and drift audit now compares chunk metadata against current KB embedding config.
- Rerun `KnowledgeBaseLifecycleIntegrationTest` after Docker/PostgreSQL is available.
- Next P0: Agent Builder minimal evaluation gate and full environment validation.
