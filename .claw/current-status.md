---
kind: current-status
version: 4
updated_at: 2026-06-20T16:42:00Z
updated_by: MANAGER-001
phase: maintenance
active_task: "TASK-156 readiness gate implemented; TASK-157 PDF parser, ACL, and drift audit foundations implemented."
next_action: "Rerun focused integration tests when Docker/PostgreSQL is available; continue TASK-157 retrieval evaluation, citation trust, connector sync, and embedding metadata drift; then TASK-156 minimal evaluation gate."
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

- Current branch: `codex/TASK-156-production-readiness-goal`.
- User opened a goal to finish two production-readiness tracks: Agent Builder production closure and enterprise knowledge platform readiness.
- TASK-156 first implementation added Agent production readiness checks, `GET /agents/{agentId}/readiness`, and a publish-time readiness gate.
- TASK-156 compile validation passed; focused integration test is blocked until local Docker/PostgreSQL is available.
- TASK-157 first implementations added PDFBox text-based PDF parsing, document/chunk ACL, RAG/Chat permission filtering, and drift audit/repair; retrieval evaluation, citation trust, connector sync, and embedding metadata drift remain.
- Production release source of truth remains `docs/production-release-runbook.md`; `scripts/release-acr.sh` owns numeric production versions and production-based beta test versions.

## Read Next

- `.claw/task-board.md` - compact index for live tasks.
- `.claw/tasks/TASK-156.md` - Agent Builder production readiness state.
- `.claw/tasks/TASK-157.md` - enterprise knowledge platform readiness state.
- `docs/specs/FEAT-066-agent-builder-production-readiness.md` - Agent Builder production readiness spec.
- `docs/specs/FEAT-067-enterprise-knowledge-platform-readiness.md` - enterprise KB production readiness spec.
- `.claw/test-report.md` - latest verified commands.
