---
kind: current-status
version: 4
updated_at: 2026-07-10T12:05:00+08:00
updated_by: MANAGER-001
phase: ai-apps-workbench-ui-refactor
active_task: "TASK-180"
next_action: "Implement TASK-180 compact AI apps shell and customer workbench desktop UI refactor, then run desktop visual validation."
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

- Current branch: `main`; production is running release `2.3.7` from Git commit `01a5df8cb919`.
- TASK-180 is active: user requested an AI 应用 page UI refactor from screenshot, with a floating vertical AI app menu, fewer outer borders, tighter customer workbench density, and scrollbars hidden until user interaction.
- TASK-179 is done in production `2.3.7`: AI 听记 realtime uses `auto`; configured organizations select Iflytek with `role_type=2`, while unconfigured organizations keep Aliyun transcription with an explicit diarization-degraded notice.
- TASK-179 evidence: backend 7 focused tests, frontend 7 tests, production build, compose validation, local fallback flow, production configured-Iflytek flow, health/version/public smoke, and zero browser console errors passed. Full backend baseline has unrelated fixture/connection failures recorded in `.claw/test-report.md`.
- TASK-178 is done in production `2.3.5`: CRM embedded customer-workbench microphone permission and ASR startup-error reporting were fixed.
- TASK-175/TASK-176 are done in production `2.3.4`: customer-workbench scroll cleanup and customer/data insight separation.
- TASK-174 data insight is done in production `2.3.2`; demo organization `org2sva14i4udjmi2t4s` uses real CRM-backed aggregate rows.
- TASK-173 real customer-workbench assistant is done in production `2.3.1`.
- TASK-170 security rules platform remains in progress and resumes after the newer production hotfix.
- Known DNS risk remains: this workstation cannot resolve `onechat.agentcici.com`; production-IP resolved smoke previously returned HTTP 200.

## Read Next

- `.claw/task-board.md` - compact task index.
- `.claw/tasks/TASK-180.md` - active UI refactor task state.
- `.claw/assignments/TASK-180.yaml` - authorized write scope.
- `docs/specs/FEAT-090-ai-apps-workbench-ui-refactor.md` - design and acceptance criteria.
- `.claw/test-report.md` - latest verified commands.
- `.claw/devops.md` and `docs/production-release-runbook.md` - production release facts if release is executed.
