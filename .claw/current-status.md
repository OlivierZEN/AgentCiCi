---
kind: current-status
version: 4
updated_at: 2026-06-18T07:10:58+08:00
updated_by: MANAGER-001
phase: maintenance
active_task: "TASK-152/TASK-153/TASK-154/TASK-155 local work has been committed on codex/TASK-152-ai-minutes-billing-timeout and merged into local main."
next_action: "Push local main to origin/main when ready; no uncommitted local work remains after the merge/status commits."
read_next:
  goals: false
  decisions: false
  issue_list: false
  task_board: true
  active_task_status: true
  test_report: true
  devops: true
---

# Project Current Status

`current-status.md` is the hot index. Rewrite it as the latest snapshot; do not append session history.

## Snapshot

- Current branch: `main`.
- Local `main` includes merge commit `Merge TASK-152 through TASK-155 readiness work`, which merged commit `6b6b50f` from `codex/TASK-152-ai-minutes-billing-timeout`.
- Other local task branches checked during this session had no commits ahead of `main`; their work was already included or they were behind `main`.
- TASK-152 fixes local AI 听记 defects: successful embedded meeting-minutes summaries now record usage and credits debits; local/default AI 听记 start no longer hard-forces disabled Iflytek; WebSocket start failures report immediately.
- TASK-153 moves Tavily Search and Iflytek realtime ASR configuration to platform operations, adds `/platform/integrations`, and adds OneKeyToken as a platform model provider with a static preset catalog.
- TASK-154 records Credits usage for remaining production-readiness runtime gaps: Open API chat, KB indexing, and personal workflow runs outside chat.
- TASK-155 completes the `/platform/*` desktop UI polish sweep using the Gilded Ledger product style and records the audit API 500 as an out-of-scope backend limitation.
- Recorded validation for TASK-152 through TASK-155 remains in `.claw/test-report.md`; this session additionally reran `git diff --check` before committing.
- Local `main` is ahead of `origin/main`; no push has been performed in this session.
- Production release source of truth remains `docs/production-release-runbook.md`; `scripts/release-acr.sh` owns numeric production versions and production-based beta test versions.

## Read Next

- `.claw/task-board.md` - compact index for live tasks.
- `.claw/tasks/TASK-152.md` - AI 听记 credits and start-timeout hotfix state.
- `.claw/tasks/TASK-153.md` - platform-governed Tavily, Iflytek, and OneKeyToken provider state.
- `.claw/tasks/TASK-154.md` - Credits metering sweep state.
- `.claw/tasks/TASK-155.md` - platform console UI polish state.
- `.claw/test-report.md` - latest verified commands.
- `.claw/devops.md` - local restart and smoke commands.
