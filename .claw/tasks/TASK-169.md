---
kind: task-status
task_id: TASK-169
status: in_progress
updated_at: 2026-07-06T15:38:52+08:00
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-169.yaml
spec_path: docs/specs/FEAT-079-kb-data-quality-annotation.md
---

# TASK-169 - 知识库数据清洗与智能标注平台能力

## Scope

- 补齐客户要求中的数据清洗与智能标注能力，并达到生产就绪标准。
- 采用知识库内嵌质量治理方案，复用现有 KB、metadata、Qdrant、ACL、eval、审计和后台权限。
- 覆盖扫描、复核、规则预览/应用、智能标注建议、审核入库、前端工作区和验证证据。

## Initial Findings

- FEAT-067 已完成企业知识平台的 parser、ACL、eval、connector、drift audit、embedding metadata 和 Qdrant smoke。
- 当前缺口集中在质量扫描、重复/无效数据识别、正则清洗、人审队列和标注工作流。
- `kb_chunk.content_hash`、`KbChunkEntity.updateContent(...)`、文档 metadata 和 vector upsert 可以作为清洗/标注实现基础。

## Implementation Plan

- 创建 FEAT-079、TASK-169 和授权边界。
- 新增 V70 迁移及质量治理/标注数据模型。
- 实现质量扫描、规则 preview/apply、人审队列和审计。
- 实现智能标注建议、接受/拒绝、文档 metadata 与 chunk annotation 入库。
- 在 `/admin/kb` 补质量治理工作区并完成桌面端验证。

## Verification

- `dev-login.py` for `MANAGER-001` covering FEAT-079/TASK-169 assignment and state files -> allowed.
- `check-assignment.py` for TASK-169 representative spec, state, V70 migration, KB backend, tenant lifecycle, integration test, and admin KB frontend files -> allowed.
- `dev-login.py` for `MANAGER-001` / `TASK-169` covering the same representative files on `codex/TASK-169-kb-data-quality-annotation` -> allowed.
- `git diff --check` -> success for TASK-169 setup files.

## Changed Files

- `docs/specs/FEAT-079-kb-data-quality-annotation.md`
- `.claw/tasks/TASK-169.md`
- `.claw/assignments/TASK-169.yaml`
- `.claw/task-board.md`
- `.claw/current-status.md`

## Handoff

- Branch for implementation: `codex/TASK-169-kb-data-quality-annotation`.
- No implementation code has been changed yet at task creation time.
- Next step is assignment validation, task-scoped login, then V70/data model implementation.
