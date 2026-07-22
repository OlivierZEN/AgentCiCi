---
kind: current-status
version: 4
updated_at: 2026-07-06T16:55:00+08:00
updated_by: MANAGER-001
phase: validation
active_task: "TASK-170 安全规则平台与输入输出安全网关"
next_action: "Review TASK-170 implementation, then decide whether to merge/release after reconciling local/main migration lineage around V70."
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

- Current branch/worktree: `codex/TASK-170-security-rules-platform`; production is running release `2.1.12` from Git commit `caf4baf90575`.
- User opened a goal to补齐 AgentCiCi 安全规则平台能力，并达到生产就绪状态.
- TASK-170 implementation is complete in the isolated worktree and covers FEAT-080: sensitive data detection/redaction, sensitive lexicon maintenance, content moderation classification, prompt injection detection, input/output safety gateway, audit redaction, chat/RAG/tool runtime integration, and `/admin/security-rules`.
- Verification completed: focused backend security tests, backend package compile, frontend build, Playwright desktop route/screenshot, and `git diff --check` all passed.
- Full backend `mvn test` is blocked in this local database by Flyway validation because migration `V70` was previously applied to `agentcici_test` but is not present in this branch lineage; targeted tests and compile are clean.
- TASK-169 remains separate on branch `codex/TASK-169-kb-data-quality-annotation` and is not part of this isolated worktree.
- FEAT-067 remains the source for existing enterprise KB readiness capabilities: parser/PDF, ACL, eval, connector skeleton, drift audit, embedding metadata, Qdrant smoke, and `/admin/kb` desktop validation.
- TASK-168 is done in production release `2.1.12`; user should still retest AI 听记 and chat microphone from the browser when convenient.
- Production release source of truth remains `docs/production-release-runbook.md`; `scripts/release-acr.sh` owns numeric production versions and production-based beta test versions.

## Read Next

- `.claw/task-board.md` - compact index for live tasks.
- `.claw/tasks/TASK-170.md` - current security rules platform task state.
- `.claw/assignments/TASK-170.yaml` - current authorized write scope.
- `docs/specs/FEAT-080-security-rules-platform.md` - current security rules platform feature spec.
- `docs/specs/FEAT-067-enterprise-knowledge-platform-readiness.md` - existing KB platform readiness source.
- `.claw/test-report.md` - latest verified commands.
