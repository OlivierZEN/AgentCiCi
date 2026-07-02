---
kind: task-status
task_id: TASK-164
status: review
updated_at: 2026-07-02T23:18:00+08:00
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-164.yaml
spec_path: docs/specs/FEAT-074-qdrant-dimension-repair.md
---

# TASK-164 - Qdrant 向量维度漂移修复

## Scope

- 修复线上知识库上传 Markdown 时 Qdrant collection 维度 `16` 与 embedding `1024` 不一致导致的 upsert 失败。
- 统一 Qdrant collection 默认维度为 1024，并在启动时检测 collection 维度漂移。
- 备份并修复生产 Qdrant collection，重建 KB 向量索引。

## Plan

- 建立 FEAT-074 规格和 TASK-164 授权。
- 补后端 Qdrant 维度默认值和 mismatch 检测。
- 跑 focused 后端测试、编译、静态检查。
- 生产侧备份 Qdrant，重建 1024 维 collection，重建索引并验证上传。
- 合并、推送、按发布 runbook 发布新版本；如能先通过配置/数据修复恢复线上，则记录热修步骤。

## Verification

- `mvn test -Dtest=QdrantVectorStoreClientTest` in `backend/` -> success, 3 tests passed.
- `mvn -DskipTests compile` in `backend/` -> success.
- `git diff --check` -> success.

## Changed Files

- `backend/src/main/java/com/codehouse/ciciassistant/kb/service/QdrantVectorStoreClient.java`
- `backend/src/test/java/com/codehouse/ciciassistant/kb/service/QdrantVectorStoreClientTest.java`
- `docs/specs/FEAT-074-qdrant-dimension-repair.md`
- `.claw/tasks/TASK-164.md`
- `.claw/assignments/TASK-164.yaml`
- `.claw/task-board.md`
- `.claw/current-status.md`
- `.claw/test-report.md`

## Handoff

- Branch: `codex/TASK-164-qdrant-dimension-repair`.
- 本地代码修复与验证已完成：Qdrant 默认 collection 维度改为 1024，并增加启动时 collection 维度 mismatch 诊断。
- 生产修复仍待执行：发布新版本后备份并重建 Qdrant collection，再重建 KB 向量索引。
