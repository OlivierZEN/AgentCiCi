---
kind: current-status
version: 4
updated_at: 2026-06-20T16:01:12Z
updated_by: MANAGER-001
phase: maintenance
active_task: "TASK-156/TASK-157 production-readiness goal started on codex/TASK-156-production-readiness-goal."
next_action: "Validate assignments, then implement Agent Builder readiness/evaluation gate and enterprise KB parser/ACL/drift foundations."
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
- TASK-156 is active for Agent Builder readiness gate, minimal evaluation gate, publish evidence, runtime entry readiness, observability, and authenticated desktop UI.
- TASK-157 is active for enterprise KB parser stability, connector sync, document/chunk ACL, retrieval evaluation, citation trust, rebuild index, and drift checks.
- New specs: `docs/specs/FEAT-066-agent-builder-production-readiness.md` and `docs/specs/FEAT-067-enterprise-knowledge-platform-readiness.md`.
- Current implementation should start with assignment validation, then P0 backend correctness gates before UI expansion.
- Production release source of truth remains `docs/production-release-runbook.md`; `scripts/release-acr.sh` owns numeric production versions and production-based beta test versions.

## Read Next

- `.claw/task-board.md` - compact index for live tasks.
- `.claw/tasks/TASK-156.md` - Agent Builder production readiness state.
- `.claw/tasks/TASK-157.md` - enterprise knowledge platform readiness state.
- `docs/specs/FEAT-066-agent-builder-production-readiness.md` - Agent Builder production readiness spec.
- `docs/specs/FEAT-067-enterprise-knowledge-platform-readiness.md` - enterprise KB production readiness spec.
- `.claw/test-report.md` - latest verified commands.
