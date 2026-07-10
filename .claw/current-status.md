---
kind: current-status
version: 4
updated_at: 2026-07-10T11:31:43+08:00
updated_by: MANAGER-001
phase: ai-minutes-speaker-diarization-release-ready
active_task: "TASK-179"
next_action: "Commit TASK-179 locally verified implementation, run release dry-run, then publish and verify the next production version."
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

- Current branch: `main`; production is running release `2.3.6` from Git commit `aac3080c103c`.
- TASK-179 local implementation and desktop validation passed: AI 听记 realtime uses `auto`; configured organizations select Iflytek with `role_type=2`, while unconfigured organizations keep Aliyun transcription with an explicit diarization-degraded notice.
- TASK-179 verified local gates: backend 7 tests, frontend 7 tests, frontend production build, real local start/stop listening flow, fallback notice visibility, and zero browser console errors.
- TASK-178 is done in production `2.3.5`: CRM embedded customer-workbench microphone permission and ASR startup-error reporting were fixed.
- TASK-175/TASK-176 are done in production `2.3.4`: customer-workbench scroll cleanup and customer/data insight separation.
- TASK-174 data insight is done in production `2.3.2`; demo organization `org2sva14i4udjmi2t4s` uses real CRM-backed aggregate rows.
- TASK-173 real customer-workbench assistant is done in production `2.3.1`.
- TASK-170 security rules platform remains in progress and resumes after the newer production hotfix.
- Known DNS risk remains: this workstation cannot resolve `onechat.agentcici.com`; production-IP resolved smoke previously returned HTTP 200.

## Read Next

- `.claw/task-board.md` - compact task index.
- `.claw/tasks/TASK-179.md` - active hotfix state.
- `.claw/assignments/TASK-179.yaml` - authorized write scope.
- `docs/specs/FEAT-089-ai-minutes-speaker-diarization-hotfix.md` - design and acceptance criteria.
- `docs/specs/FEAT-029-meeting-minutes-live-transcription.md` - existing realtime diarization contract.
- `.claw/test-report.md` - latest verified commands.
- `.claw/devops.md` and `docs/production-release-runbook.md` - production release facts if release is executed.
