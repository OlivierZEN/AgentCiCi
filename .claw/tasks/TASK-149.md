---
kind: task-status
task_id: TASK-149
assignee: MANAGER-001
owner_role: project-manager
status: review
branch: codex/TASK-149-kb-docx-upload-parser
pr_url: n/a
spec_path: docs/specs/FEAT-008-knowledge-base-lifecycle-completion.md
assignment_path: .claw/assignments/TASK-149.yaml
updated_at: 2026-06-02T08:01:02Z
updated_by: MANAGER-001
---

# TASK-149 Knowledge Base DOCX Upload Parser

## Scope

修复知识库上传 `.docx` 后发布索引失败的问题。截图显示 Word 文档被记录为
`application/vnd.openxmlformats-officedocument.wordprocessingml.document` 后，P0 索引仍按
`txt/md/csv/json` 拒绝，导致管理员看到 `Unsupported file type`。

## Plan

- 后端在现有 KB 文件解析策略中支持 `.docx` / Word OpenXML MIME。
- 增加最小回归测试：上传 docx、发布、生成 chunk，并可通过 RAG 检索命中正文。
- 保持 P0 解析边界清晰，不扩大到未实现的二进制格式。

## Verification Target

- `mvn -q -Dmaven.repo.local=.m2 -Dtest=KnowledgeBaseLifecycleIntegrationTest test`
- `git diff --check`

## Progress

- 2026-06-02T07:48:18Z: User reported `.docx` upload indexing failure; task opened for a narrow parser fix.
- 2026-06-02T07:49:00Z: User requested reassignment to Owen; repository identity is `MANAGER-001` with display name `Owen`, so assignment remains on `MANAGER-001`.
- 2026-06-02T07:51:58Z: Implemented JDK-only DOCX OpenXML text extraction and added a KB lifecycle regression test for upload, publish, chunk creation, and RAG retrieval.
- 2026-06-02T07:59:56Z: Refreshed `FEAT-008` knowledge-base fact source with a current gap review: closed lifecycle/docx capabilities, remaining upload/Qdrant/UI/runtime retrieval gaps, and recommended follow-up task packages.
- 2026-06-02T08:01:02Z: Documentation follow-up validated with assignment scope check, `git diff --check`, and TASK-149 line-budget check.

## Verification

- `mvn -q -Dmaven.repo.local=.m2 -Dtest=KnowledgeBaseLifecycleIntegrationTest test` in `backend/` -> passed after fixing ZIP/XML entry handling; 9 tests passed.
- `git diff --check` -> passed.
- Documentation follow-up: `check-assignment.py` for FEAT-008/task/status/report files -> allowed; `git diff --check` -> passed; `git diff --check --no-index /dev/null .claw/tasks/TASK-149.md` -> no whitespace output; `wc -l .claw/tasks/TASK-149.md` -> 57 lines.

## Changed Files

- `backend/src/main/java/com/codehouse/ciciassistant/kb/service/KnowledgeBaseService.java`
- `backend/src/test/java/com/codehouse/ciciassistant/kb/KnowledgeBaseLifecycleIntegrationTest.java`
- `docs/specs/FEAT-008-knowledge-base-lifecycle-completion.md`
- `.claw/assignments/TASK-149.yaml`
- `.claw/tasks/TASK-149.md`
- `.claw/task-board.md`
- `.claw/current-status.md`
- `.claw/test-report.md`
